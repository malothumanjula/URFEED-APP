import json
import logging
import re
from dataclasses import dataclass
from typing import Protocol

import httpx
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models import Tag
from app.services.tag_catalog import normalize_tag

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class AnalyzedTag:
    tag: Tag
    confidence: float
    source: str = "AI"


class AiTaggingService(Protocol):
    async def analyze(self, content: str, db: Session) -> list[AnalyzedTag]: ...


class DeterministicTaggingService:
    def analyze_sync(self, content: str) -> list[tuple[str, float]]:
        text = content.lower()
        candidates: dict[str, float] = {}

        def add(name: str, confidence: float) -> None:
            candidates.setdefault(name, confidence)

        def has(*terms: str) -> bool:
            return any(re.search(rf"(?<![a-z0-9]){re.escape(term.lower())}(?![a-z0-9])", text) for term in terms)

        if not text.strip():
            return []
        if has("cricket"):
            add("Sports", 0.97); add("Cricket", 0.99)
        if has("placement", "placements", "campus placement", "interview preparation"):
            add("Education", 0.94); add("Career", 0.92); add("Placements", 0.98); add("Programming", 0.84)
        ai_context = has("ai", "a.i.", "artificial intelligence", "agent", "agents", "assistant", "autonomous", "langchain", "langgraph", "llm")
        if ai_context:
            add("Artificial Intelligence", 0.97)
            if has("agent", "agents", "assistant", "autonomous", "langchain", "langgraph", "intelligent agents"):
                add("AI Agents", 0.94)
            if has("agentic", "autonomous", "agent", "agents", "langgraph"):
                add("Agentic AI", 0.91)
            if has("langchain"):
                add("LangChain", 0.90)
            if has("langgraph"):
                add("LangGraph", 0.91)
            if has("llm", "llms", "large language model"):
                add("Large Language Models", 0.90)
            if has("automation", "automate", "workflow"):
                add("AI Automation", 0.86)
        if has("python"):
            add("Python", 0.91)
        if has("java"):
            add("Java", 0.90)
        if has("data structures", "dsa"):
            add("Data Structures", 0.91); add("Programming", 0.84)
        if has("algorithm", "algorithms"):
            add("Algorithms", 0.89); add("Programming", 0.84)
        if has("college", "campus", "university"):
            add("College", 0.87); add("Education", 0.82)
        if has("travel", "trip", "destination"):
            add("Travel", 0.90)
        if has("photography", "photograph"):
            add("Photography", 0.86)
        if has("startup", "startups", "entrepreneur"):
            add("Business", 0.86); add("Startups", 0.92)
        if has("food", "recipe", "cooking"):
            add("Food", 0.89)
        return list(candidates.items())


class ConfigurableAiTaggingService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.fallback = DeterministicTaggingService()

    async def analyze(self, content: str, db: Session) -> list[AnalyzedTag]:
        outputs = self.fallback.analyze_sync(content)
        if self.settings.ai_provider.lower() == "openai" and self.settings.ai_api_key:
            try:
                outputs = await self._request_structured_tags(content)
            except (httpx.HTTPError, ValueError, KeyError, TypeError):
                logger.warning("AI tag provider failed; deterministic fallback used")
        results: list[AnalyzedTag] = []
        seen: set[str] = set()
        for name, confidence in outputs:
            tag = normalize_tag(db, name, allow_custom=False)
            if tag is not None and tag.id not in seen:
                seen.add(tag.id)
                results.append(AnalyzedTag(tag=tag, confidence=max(0.0, min(1.0, float(confidence)))))
        return results

    async def _request_structured_tags(self, content: str) -> list[tuple[str, float]]:
        settings = self.settings
        prompt = (
            "Analyze this social content. Return JSON only with tags as objects containing name and confidence. "
            "Only use canonical concepts from the existing taxonomy. Never return visibility or audience labels.\n"
            f"Content: {content}"
        )
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.post(
                f"{settings.ai_base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.ai_api_key}"},
                json={
                    "model": settings.ai_model,
                    "response_format": {"type": "json_object"},
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0,
                },
            )
            response.raise_for_status()
            payload = response.json()
        content_json = payload["choices"][0]["message"]["content"]
        parsed = json.loads(content_json)
        raw_tags = parsed.get("tags", [])
        if not isinstance(raw_tags, list):
            raise ValueError("Invalid structured tag result")
        result = []
        for item in raw_tags:
            if isinstance(item, dict) and isinstance(item.get("name"), str):
                result.append((item["name"], float(item.get("confidence", 0.5))))
        return result


tagging_service: AiTaggingService = ConfigurableAiTaggingService()
