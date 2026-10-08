from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.dependencies import get_current_user
from app.db.database import get_db
from app.models import User
from app.schemas import SearchOut
from app.services.search_service import search_content
from app.services.tag_catalog import seed_canonical_tags

router = APIRouter(prefix="/api/search", tags=["search"])


@router.get("", response_model=SearchOut)
async def search(
    q: str = Query(default="", max_length=300),
    content_type: str = Query(default="all", pattern="^(all|posts|statuses)$"),
    tag: str | None = Query(default=None, max_length=160),
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=20, ge=1, le=100),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    seed_canonical_tags(db)
    db.commit()
    return await search_content(db, current, q, content_type, tag, page, limit)
