from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.dependencies import get_current_user
from app.db.database import get_db
from app.models import User
from app.schemas import AuthLogin, AuthRegister, TokenOut, UserOut
from app.services.auth_service import InvalidCredentials, RegistrationConflict, login_user, register_user
from app.services.social_service import get_user_out

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register", response_model=TokenOut, status_code=status.HTTP_201_CREATED)
def register(payload: AuthRegister, db: Session = Depends(get_db)):
    try:
        user, token = register_user(db, payload)
    except RegistrationConflict as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    return {"access_token": token, "user": get_user_out(db, user, include_email=True)}


@router.post("/login", response_model=TokenOut)
def login(payload: AuthLogin, db: Session = Depends(get_db)):
    try:
        user, token = login_user(db, payload)
    except InvalidCredentials as error:
        raise HTTPException(status_code=401, detail=str(error), headers={"WWW-Authenticate": "Bearer"}) from error
    return {"access_token": token, "user": get_user_out(db, user, include_email=True)}


@router.get("/me", response_model=UserOut)
def current_user(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return get_user_out(db, user, include_email=True)
