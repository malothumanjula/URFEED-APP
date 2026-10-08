from datetime import UTC, datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload, selectinload

from app.core.dependencies import get_current_user
from app.db.database import get_db
from app.models import Follow, Like, Post, PostTag, User
from app.schemas import CommentCreate, PostCreate, PostOut
from app.services.social_service import can_view, create_comment, create_post, load_post, serialize_post, visible_posts_query

router = APIRouter(prefix="/api/posts", tags=["posts"])


@router.post("", response_model=PostOut, status_code=status.HTTP_201_CREATED)
async def publish_post(payload: PostCreate, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    post = await create_post(db, current, payload)
    return serialize_post(db, post, current.id)


@router.get("/feed", response_model=list[PostOut])
def feed(
    following: bool = False,
    tag: str | None = None,
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=20, ge=1, le=100),
    content_type: str = Query(default="all", pattern="^(all|posts|post)$"),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    query = select(Post).options(joinedload(Post.author).selectinload(User.interests), selectinload(Post.tag_links).joinedload(PostTag.tag), selectinload(Post.likes))
    if following:
        query = query.join(Follow, Follow.following_id == Post.author_id).where(Follow.follower_id == current.id)
    else:
        query = query.where(visible_posts_query(current.id))
    if tag:
        query = query.join(PostTag).join(PostTag.tag).where(PostTag.tag.has(slug=tag.lower().lstrip("#")))
    posts = db.scalars(query.order_by(Post.created_at.desc()).offset((page - 1) * limit).limit(limit)).unique().all()
    posts = [post for post in posts if can_view(db, post.author_id, post.visibility, current.id)]
    return [serialize_post(db, post, current.id) for post in posts]


@router.get("/{post_id}", response_model=PostOut)
def get_post(post_id: UUID, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    post = load_post(db, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if not can_view(db, post.author_id, post.visibility, current.id):
        raise HTTPException(status_code=403, detail="You cannot view this post")
    return serialize_post(db, post, current.id)


@router.delete("/{post_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_post(post_id: UUID, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if post.author_id != current.id:
        raise HTTPException(status_code=403, detail="Only the author can delete this post")
    db.delete(post)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/{post_id}/like", status_code=status.HTTP_204_NO_CONTENT)
def like_post(post_id: UUID, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    post = load_post(db, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if not can_view(db, post.author_id, post.visibility, current.id):
        raise HTTPException(status_code=403, detail="You cannot interact with this post")
    if db.scalar(select(Like).where(Like.user_id == current.id, Like.post_id == post_id)) is None:
        db.add(Like(user_id=current.id, post_id=post_id))
        db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete("/{post_id}/like", status_code=status.HTTP_204_NO_CONTENT)
def unlike_post(post_id: UUID, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if not can_view(db, post.author_id, post.visibility, current.id):
        raise HTTPException(status_code=403, detail="You cannot interact with this post")
    like = db.scalar(select(Like).where(Like.user_id == current.id, Like.post_id == post_id))
    if like is not None:
        db.delete(like)
        db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/{post_id}/comments", status_code=status.HTTP_201_CREATED)
def comment_post(post_id: UUID, payload: CommentCreate, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    post = load_post(db, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if not can_view(db, post.author_id, post.visibility, current.id):
        raise HTTPException(status_code=403, detail="You cannot interact with this post")
    comment = create_comment(db, post, current, payload)
    return {"id": comment.id, "post_id": comment.post_id, "user_id": comment.user_id, "content": comment.content, "created_at": comment.created_at}
