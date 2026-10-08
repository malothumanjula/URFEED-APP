from datetime import UTC, datetime, timedelta
from uuid import UUID

from sqlalchemy import and_, delete, exists, func, or_, select
from sqlalchemy.orm import Session, joinedload, selectinload

from app.ai.embeddings import embedding_service
from app.ai.tagging import AiTaggingService, tagging_service
from app.core.config import get_settings
from app.models import Comment, ContentEmbedding, Follow, Like, Post, PostTag, Status, StatusTag, Tag, User, UserInterest
from app.schemas import CommentCreate, PostCreate, StatusCreate, UserUpdate
from app.services.tag_catalog import normalize_tag, seed_canonical_tags


class NotFound(Exception):
    pass


class Forbidden(Exception):
    pass


def as_utc(value: datetime) -> datetime:
    return value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)


def active_following_ids(db: Session, user_id: UUID) -> set[UUID]:
    return set(db.scalars(select(Follow.following_id).where(Follow.follower_id == user_id)).all())


def can_view(db: Session, author_id: UUID, visibility: str, viewer_id: UUID) -> bool:
    if author_id == viewer_id or visibility == "PUBLIC":
        return True
    return db.scalar(select(exists().where(Follow.follower_id == viewer_id, Follow.following_id == author_id))) is True


def get_user_out(db: Session, user: User, include_email: bool = False) -> dict:
    return {
        "id": user.id,
        "username": user.username,
        "email": user.email if include_email else None,
        "display_name": user.display_name,
        "bio": user.bio,
        "profile_image_url": user.profile_image_url,
        "interests": [item.interest for item in user.interests],
        "followers_count": db.scalar(select(func.count()).select_from(Follow).where(Follow.following_id == user.id)) or 0,
        "following_count": db.scalar(select(func.count()).select_from(Follow).where(Follow.follower_id == user.id)) or 0,
        "posts_count": db.scalar(select(func.count()).select_from(Post).where(Post.author_id == user.id)) or 0,
        "created_at": user.created_at,
    }


def get_user(db: Session, user_id: UUID) -> User | None:
    return db.scalar(select(User).options(selectinload(User.interests)).where(User.id == user_id))


def update_user(db: Session, user: User, payload: UserUpdate) -> User:
    values = payload.model_dump(exclude_unset=True)
    interests = values.pop("interests", None)
    if "username" in values and values["username"] is not None:
        values["username"] = values["username"].lower()
        duplicate = db.scalar(select(User).where(User.username == values["username"], User.id != user.id))
        if duplicate:
            raise ValueError("Username is already in use")
    for field, value in values.items():
        setattr(user, field, value)
    if interests is not None:
        user.interests.clear()
        user.interests.extend(UserInterest(interest=item.strip()) for item in dict.fromkeys(interests) if item.strip())
    db.commit()
    return get_user(db, user.id) or user


def follow_user(db: Session, follower: User, followed_id: UUID, follow: bool) -> None:
    if follower.id == followed_id:
        raise ValueError("You cannot follow yourself")
    followed = db.get(User, followed_id)
    if followed is None:
        raise NotFound("User not found")
    relation = db.scalar(select(Follow).where(Follow.follower_id == follower.id, Follow.following_id == followed_id))
    if follow and relation is None:
        db.add(Follow(follower_id=follower.id, following_id=followed_id))
    elif not follow and relation is not None:
        db.delete(relation)
    db.commit()


def _attach_tags(db: Session, entity, names: list[str], kind: str, analyzed_tags: list, allow_custom: bool = True) -> list:
    links = []
    seen: set[UUID] = set()
    by_name = {item.tag.name.lower(): item for item in analyzed_tags}
    for name in names:
        tag = normalize_tag(db, name, allow_custom=allow_custom)
        if tag is None or tag.id in seen:
            continue
        seen.add(tag.id)
        analyzed = by_name.get(tag.name.lower())
        confidence = analyzed.confidence if analyzed else 1.0
        source = "AI" if analyzed else "USER"
        link = PostTag(post=entity, tag=tag, confidence=confidence, source=source) if kind == "POST" else StatusTag(status=entity, tag=tag, confidence=confidence, source=source)
        db.add(link)
        links.append(link)
    return links


async def create_post(db: Session, author: User, payload: PostCreate, ai_service: AiTaggingService = tagging_service) -> Post:
    seed_canonical_tags(db)
    analysis = await ai_service.analyze(payload.content, db)
    post = Post(author_id=author.id, content=payload.content, image_url=payload.image_url, visibility=payload.visibility)
    db.add(post)
    db.flush()
    names = [item.tag.name for item in analysis] + payload.tags
    links = _attach_tags(db, post, list(dict.fromkeys(names)), "POST", analysis)
    db.commit()
    db.refresh(post)
    try:
        await embedding_service.save_for_content(db, "POST", post.id, post.content + " " + " ".join(link.tag.name for link in links))
        db.commit()
    except Exception:
        db.rollback()
    return load_post(db, post.id)


async def create_status(db: Session, author: User, payload: StatusCreate, ai_service: AiTaggingService = tagging_service) -> Status:
    seed_canonical_tags(db)
    analysis = await ai_service.analyze(payload.content, db)
    now = datetime.now(UTC)
    status = Status(
        author_id=author.id, content=payload.content, image_url=payload.image_url, visibility=payload.visibility,
        created_at=now, expires_at=now + timedelta(hours=24),
    )
    db.add(status)
    db.flush()
    names = [item.tag.name for item in analysis] + payload.tags
    links = _attach_tags(db, status, list(dict.fromkeys(names)), "STATUS", analysis)
    db.commit()
    db.refresh(status)
    try:
        await embedding_service.save_for_content(db, "STATUS", status.id, status.content + " " + " ".join(link.tag.name for link in links))
        db.commit()
    except Exception:
        db.rollback()
    return load_status(db, status.id)


def load_post(db: Session, post_id: UUID) -> Post | None:
    return db.scalar(select(Post).options(joinedload(Post.author).selectinload(User.interests), selectinload(Post.tag_links).joinedload(PostTag.tag), selectinload(Post.likes)).where(Post.id == post_id))


def load_status(db: Session, status_id: UUID) -> Status | None:
    return db.scalar(select(Status).options(joinedload(Status.author).selectinload(User.interests), selectinload(Status.tag_links).joinedload(StatusTag.tag)).where(Status.id == status_id))


def serialize_tag_links(links: list) -> list[dict]:
    return [{
        "id": link.tag.id, "slug": link.tag.slug, "name": link.tag.name,
        "category": link.tag.category, "subcategory": link.tag.subcategory,
        "description": link.tag.description, "confidence": link.confidence, "source": link.source,
    } for link in links]


def serialize_post(db: Session, post: Post, viewer_id: UUID) -> dict:
    return {
        "id": post.id, "author": get_user_out(db, post.author), "content": post.content,
        "image_url": post.image_url, "visibility": post.visibility, "created_at": post.created_at,
        "tags": serialize_tag_links(post.tag_links), "likes_count": len(post.likes),
        "liked_by_me": any(like.user_id == viewer_id for like in post.likes),
        "comments_count": db.scalar(select(func.count()).select_from(Comment).where(Comment.post_id == post.id)) or 0,
    }


def create_comment(db: Session, post: Post, author: User, payload: CommentCreate) -> Comment:
    comment = Comment(post_id=post.id, user_id=author.id, content=payload.content)
    db.add(comment)
    db.commit()
    db.refresh(comment)
    return comment


def serialize_status(db: Session, status: Status) -> dict:
    return {
        "id": status.id, "author": get_user_out(db, status.author), "content": status.content,
        "image_url": status.image_url, "visibility": status.visibility,
        "created_at": status.created_at, "expires_at": status.expires_at,
        "tags": serialize_tag_links(status.tag_links),
    }


def visible_posts_query(user_id: UUID):
    followed_author = exists().where(Follow.follower_id == user_id, Follow.following_id == Post.author_id)
    return or_(Post.visibility == "PUBLIC", Post.author_id == user_id, followed_author)


def visible_statuses_query(user_id: UUID, now: datetime):
    followed_author = exists().where(Follow.follower_id == user_id, Follow.following_id == Status.author_id)
    return and_(Status.expires_at > now, or_(Status.visibility == "PUBLIC", Status.author_id == user_id, followed_author))
