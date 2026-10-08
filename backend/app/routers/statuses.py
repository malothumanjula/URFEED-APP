from datetime import UTC, datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload, selectinload

from app.core.dependencies import get_current_user
from app.db.database import get_db
from app.models import Follow, Status, StatusTag, User
from app.schemas import StatusCreate, StatusOut
from app.services.social_service import as_utc, can_view, create_status, load_status, serialize_status, visible_statuses_query

router = APIRouter(prefix="/api/statuses", tags=["statuses"])


@router.post("", response_model=StatusOut, status_code=status.HTTP_201_CREATED)
async def publish_status(payload: StatusCreate, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    status_item = await create_status(db, current, payload)
    return serialize_status(db, status_item)


@router.get("/feed", response_model=list[StatusOut])
def status_feed(
    following: bool = False,
    content_type: str = Query(default="all", pattern="^(all|statuses|status)$"),
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=50, ge=1, le=100),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    now = datetime.now(UTC)
    query = select(Status).options(joinedload(Status.author).selectinload(User.interests), selectinload(Status.tag_links).joinedload(StatusTag.tag)).where(visible_statuses_query(current.id, now))
    if following:
        query = query.where(Status.author_id.in_(select(Follow.following_id).where(Follow.follower_id == current.id)))
    statuses = db.scalars(query.order_by(Status.created_at.desc()).offset((page - 1) * limit).limit(limit)).unique().all()
    return [serialize_status(db, item) for item in statuses]


@router.get("/{status_id}", response_model=StatusOut)
def get_status(status_id: UUID, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    item = load_status(db, status_id)
    if item is None or as_utc(item.expires_at) <= datetime.now(UTC):
        raise HTTPException(status_code=404, detail="Status not found or expired")
    if not can_view(db, item.author_id, item.visibility, current.id):
        raise HTTPException(status_code=403, detail="You cannot view this status")
    return serialize_status(db, item)


@router.delete("/{status_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_status(status_id: UUID, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    item = db.get(Status, status_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Status not found")
    if item.author_id != current.id:
        raise HTTPException(status_code=403, detail="Only the author can delete this status")
    db.delete(item)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
