from datetime import UTC, datetime
import re
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, joinedload, selectinload

from app.ai.embeddings import embedding_service
from app.ai.vector_search import vector_search
from app.core.config import get_settings
from app.models import Follow, Post, PostTag, Status, StatusTag, Tag, User
from app.services.social_service import as_utc, can_view, serialize_post, serialize_status, visible_posts_query, visible_statuses_query
from app.services.tag_catalog import alias_key, normalize_tag


async def search_content(
    db: Session,
    viewer: User,
    query: str,
    content_type: str = "all",
    tag_value: str | None = None,
    page: int = 1,
    limit: int = 20,
) -> dict:
    clean_query = query.strip()
    if not clean_query and not tag_value:
        return {"query": query, "posts": [], "statuses": [], "matched_tags": [], "categories": []}
    seed_query = tag_value or clean_query
    resolved = normalize_tag(db, seed_query, allow_custom=False)
    candidates = db.scalars(select(Tag).where(Tag.is_canonical.is_(True))).all()
    matched_tags: list[Tag] = []
    if resolved is not None:
        matched_tags = [resolved]
    elif clean_query:
        query_key = alias_key(clean_query)
        query_tokens = set(re.findall(r"[a-z0-9]+", clean_query.lower()))
        stop_words = {"a", "an", "the", "in", "on", "for", "to", "of", "about", "things", "people", "learning", "preparation", "preparing", "projects", "project", "with", "and", "or", "how", "what", "programming"}
        query_tokens -= stop_words
        for candidate in candidates:
            aliases = [candidate.name, candidate.slug, *(candidate.aliases or "").split("|")]
            alias_keys = {alias_key(alias) for alias in aliases if alias}
            alias_tokens = set(re.findall(r"[a-z0-9]+", " ".join(aliases).lower())) - stop_words
            if query_key in alias_keys or (query_tokens and query_tokens.intersection(alias_tokens)):
                matched_tags.append(candidate)

    semantic_groups = {
        "AI Agents": ["Artificial Intelligence", "AI Agents", "Agentic AI", "AI Automation", "LangChain", "LangGraph", "Large Language Models"],
        "Artificial Intelligence": ["Artificial Intelligence", "AI Agents", "Agentic AI", "Generative AI", "AI Automation", "LangChain", "LangGraph"],
        "Python": ["Python", "Programming", "Data Structures", "Algorithms", "Machine Learning", "Data Science"],
        "Programming": ["Python", "Programming", "Data Structures", "Algorithms", "Machine Learning", "Data Science"],
        "Placements": ["College", "Placements", "Interviews", "Career", "Resume", "Internships", "Data Structures"],
        "College": ["College", "Placements", "Interviews", "Career", "Resume", "Internships"],
        "Cricket": ["Sports", "Cricket"],
        "Travel": ["Travel", "Destinations", "Adventure", "Solo Travel", "Budget Travel", "Travel Photography"],
    }
    expanded_names = {name for tag in matched_tags for name in semantic_groups.get(tag.name, [])}
    if expanded_names:
        matched_tags.extend(tag for tag in candidates if tag.name in expanded_names)
    matched_tags = list({tag.id: tag for tag in matched_tags}.values())

    now = datetime.now(UTC)
    include_posts = content_type in ("all", "posts", "post")
    include_statuses = content_type in ("all", "statuses", "status")
    accessible_posts = []
    accessible_statuses = []
    if include_posts:
        post_query = select(Post).options(joinedload(Post.author).selectinload(User.interests), selectinload(Post.tag_links).joinedload(PostTag.tag), selectinload(Post.likes)).where(visible_posts_query(viewer.id))
        accessible_posts = db.scalars(post_query.order_by(Post.created_at.desc()).limit(300)).unique().all()
    if include_statuses:
        status_query = select(Status).options(joinedload(Status.author).selectinload(User.interests), selectinload(Status.tag_links).joinedload(StatusTag.tag)).where(visible_statuses_query(viewer.id, now))
        accessible_statuses = db.scalars(status_query.order_by(Status.created_at.desc()).limit(300)).unique().all()

    # Without an embedding provider query_vector is None and ranking relies on canonical tags,
    # related-topic expansion and keywords below.
    query_vector = await embedding_service.embed(clean_query) if clean_query and get_settings().semantic_search_enabled else None
    vector_scores: dict[tuple[str, UUID], float] = {}
    if query_vector is not None:
        if include_posts:
            scores = vector_search.similarity_scores(db, "POST", [post.id for post in accessible_posts], query_vector)
            vector_scores.update({("POST", content_id): score for content_id, score in scores.items()})
        if include_statuses:
            scores = vector_search.similarity_scores(db, "STATUS", [item.id for item in accessible_statuses], query_vector)
            vector_scores.update({("STATUS", content_id): score for content_id, score in scores.items()})

    tag_ids = {tag.id for tag in matched_tags}
    canonical_match_id = resolved.id if resolved is not None else None
    words = [word for word in clean_query.lower().split() if len(word) > 2]

    def rank(entity, kind: str, links) -> float:
        link_tag_ids = {link.tag_id for link in links}
        tag_score = 1.0 if canonical_match_id is not None and canonical_match_id in link_tag_ids else (0.82 if link_tag_ids & tag_ids else 0.0)
        content = entity.content.lower()
        keyword_score = sum(1 for word in words if word in content) / max(1, len(words))
        vector_score = vector_scores.get((kind, entity.id), 0.0)
        age_days = max(0.0, (now - as_utc(entity.created_at)).total_seconds() / 86400)
        recency_score = max(0.0, 1.0 - age_days / 30.0)
        evidence_score = vector_score * 0.55 + tag_score * 0.30 + keyword_score * 0.10
        if evidence_score == 0:
            return 0.0
        return evidence_score + recency_score * 0.05

    ranked_posts = [(post, rank(post, "POST", post.tag_links)) for post in accessible_posts]
    ranked_statuses = [(status, rank(status, "STATUS", status.tag_links)) for status in accessible_statuses]
    ranked_posts = sorted((item for item in ranked_posts if item[1] > 0), key=lambda item: item[1], reverse=True)
    ranked_statuses = sorted((item for item in ranked_statuses if item[1] > 0), key=lambda item: item[1], reverse=True)
    offset = (page - 1) * limit
    return {
        "query": clean_query or (resolved.name if resolved else tag_value),
        "posts": [serialize_post(db, item, viewer.id) for item, _ in ranked_posts[offset:offset + limit]],
        "statuses": [serialize_status(db, item) for item, _ in ranked_statuses[offset:offset + limit]],
        "matched_tags": [{"id": tag.id, "slug": tag.slug, "name": tag.name, "category": tag.category, "subcategory": tag.subcategory, "description": tag.description, "confidence": 1.0, "source": "CANONICAL"} for tag in matched_tags],
        "categories": list(dict.fromkeys(tag.category for tag in matched_tags)),
    }
