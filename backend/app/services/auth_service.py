from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.core.security import create_access_token, hash_password, verify_password
from app.models import User
from app.schemas import AuthLogin, AuthRegister


class RegistrationConflict(Exception):
    pass


class InvalidCredentials(Exception):
    pass


def register_user(db: Session, payload: AuthRegister) -> tuple[User, str]:
    existing = db.scalar(select(User).where(or_(User.username == payload.username, User.email == str(payload.email).lower())))
    if existing is not None:
        if existing.username.lower() == payload.username.lower():
            raise RegistrationConflict("Username is already in use")
        raise RegistrationConflict("Email is already registered")
    user = User(
        username=payload.username.lower(),
        email=str(payload.email).lower(),
        display_name=payload.display_name.strip(),
        password_hash=hash_password(payload.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user, create_access_token(user.id)


def login_user(db: Session, payload: AuthLogin) -> tuple[User, str]:
    identity = payload.username_or_email.strip().lower()
    user = db.scalar(select(User).where(or_(User.username == identity, User.email == identity)))
    if user is None or not verify_password(payload.password, user.password_hash):
        raise InvalidCredentials("Invalid username/email or password")
    return user, create_access_token(user.id)
