from uuid import UUID
import re

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session, selectinload

from app.core.dependencies import get_current_user
from app.db.database import get_db
from app.models import Follow, User, UserInterest
from app.schemas import FollowState, UserOut, UserUpdate
from app.services.social_service import NotFound, follow_user, get_user_out, update_user

router = APIRouter(prefix="/api/users", tags=["users"])


@router.get("/search", response_model=list[UserOut])
def search_users(q: str = Query(default="", max_length=120), db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    if not q.strip():
        users = db.scalars(select(User).options(selectinload(User.interests)).where(User.id != current.id).limit(100)).all()
        return [get_user_out(db, user) for user in users]
    terms = [word for word in re.findall(r"[a-zA-Z0-9_.]+", q.lstrip("@")) if word.lower() not in {"people", "learning", "learners", "who", "about", "interested", "in", "the"}]
    if not terms:
        terms = [q.strip().lstrip("@")] 
    conditions = [condition for term in terms for condition in (
        User.username.ilike(f"%{term}%"), User.display_name.ilike(f"%{term}%"), UserInterest.interest.ilike(f"%{term}%")
    )]
    users = db.scalars(
        select(User).options(selectinload(User.interests)).outerjoin(UserInterest)
        .where(User.id != current.id, or_(*conditions))
        .distinct().limit(30)
    ).all()
    return [get_user_out(db, user) for user in users]


@router.get("/me", response_model=UserOut)
def my_profile(current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return get_user_out(db, current, include_email=True)


@router.put("/me", response_model=UserOut)
def edit_profile(payload: UserUpdate, current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    try:
        user = update_user(db, current, payload)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    return get_user_out(db, user, include_email=True)


@router.get("/{username}", response_model=UserOut)
def user_by_username(username: str, db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    user = db.scalar(select(User).options(selectinload(User.interests)).where(User.username == username))
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    return get_user_out(db, user)


@router.post("/{user_id}/follow", response_model=FollowState)
def follow(user_id: UUID, current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    try:
        follow_user(db, current, user_id, True)
    except NotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    return {"following": True}


@router.delete("/{user_id}/follow", response_model=FollowState)
def unfollow(user_id: UUID, current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    try:
        follow_user(db, current, user_id, False)
    except NotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    return {"following": False}


@router.get("/{user_id}/followers", response_model=list[UserOut])
def followers(user_id: UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    if db.get(User, user_id) is None:
        raise HTTPException(status_code=404, detail="User not found")
    users = db.scalars(select(User).options(selectinload(User.interests)).join(Follow, Follow.follower_id == User.id).where(Follow.following_id == user_id)).all()
    return [get_user_out(db, user) for user in users]


@router.get("/{user_id}/following", response_model=list[UserOut])
def following(user_id: UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    if db.get(User, user_id) is None:
        raise HTTPException(status_code=404, detail="User not found")
    users = db.scalars(select(User).options(selectinload(User.interests)).join(Follow, Follow.following_id == User.id).where(Follow.follower_id == user_id)).all()
    return [get_user_out(db, user) for user in users]
