import logging

import httpx
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models import ContentEmbedding

logger = logging.getLogger(__name__)


class EmbeddingService:
    async def embed(self, text: str) -> list[float] | None:
        settings = get_settings()
        if settings.ai_provider.lower() != "openai" or not settings.ai_api_key:
            return None
        try:
            async with httpx.AsyncClient(timeout=20) as client:
                response = await client.post(
                    f"{settings.ai_base_url.rstrip('/')}/embeddings",
                    headers={"Authorization": f"Bearer {settings.ai_api_key}"},
                    json={"model": settings.embedding_model, "input": text, "dimensions": settings.embedding_dimensions},
                )
                response.raise_for_status()
                vector = response.json()["data"][0]["embedding"]
            if len(vector) != settings.embedding_dimensions or not all(isinstance(value, (int, float)) for value in vector):
                raise ValueError("Invalid embedding vector")
            return [float(value) for value in vector]
        except (httpx.HTTPError, ValueError, KeyError, IndexError, TypeError):
            logger.warning("Embedding provider failed; keyword and canonical tag search remain available")
            return None

    async def save_for_content(self, db: Session, content_type: str, content_id, text: str) -> None:
        vector = await self.embed(text)
        if vector is None:
            return
        record = db.query(ContentEmbedding).filter_by(content_type=content_type, content_id=content_id).one_or_none()
        if record is None:
            record = ContentEmbedding(content_type=content_type, content_id=content_id, embedding=vector)
            db.add(record)
        else:
            record.embedding = vector


embedding_service = EmbeddingService()
