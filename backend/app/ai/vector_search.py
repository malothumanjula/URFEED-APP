import math
from typing import Protocol
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ContentEmbedding


class VectorSearchBackend(Protocol):
    def similarity_scores(self, db: Session, content_type: str, content_ids: list[UUID], query_vector: list[float]) -> dict[UUID, float]: ...


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if len(left) != len(right):
        return 0.0
    dot = sum(a * b for a, b in zip(left, right))
    norm = math.sqrt(sum(a * a for a in left)) * math.sqrt(sum(b * b for b in right))
    return dot / norm if norm else 0.0


class InMemoryVectorSearch:
    """Database-agnostic vector scoring over embeddings stored as JSON.

    Only already visibility-filtered candidate ids are scored, so this never widens what a
    viewer can see. Replace with a native vector store (e.g. a dedicated vector DB) later by
    implementing VectorSearchBackend.
    """

    def similarity_scores(self, db: Session, content_type: str, content_ids: list[UUID], query_vector: list[float]) -> dict[UUID, float]:
        if not content_ids or not query_vector:
            return {}
        rows = db.execute(
            select(ContentEmbedding.content_id, ContentEmbedding.embedding)
            .where(ContentEmbedding.content_type == content_type, ContentEmbedding.content_id.in_(content_ids))
        ).all()
        return {content_id: max(0.0, cosine_similarity(query_vector, embedding)) for content_id, embedding in rows if isinstance(embedding, list)}


vector_search: VectorSearchBackend = InMemoryVectorSearch()
