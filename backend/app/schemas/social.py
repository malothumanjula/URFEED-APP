from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

Visibility = Literal["PUBLIC", "FOLLOWERS"]


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class TagOut(ORMModel):
    id: UUID
    slug: str
    name: str
    category: str
    subcategory: str | None
    description: str
    confidence: float = 1.0
    source: str = "AI"


class UserOut(ORMModel):
    id: UUID
    username: str
    email: EmailStr | None = None
    display_name: str
    bio: str
    profile_image_url: str | None
    interests: list[str] = []
    followers_count: int = 0
    following_count: int = 0
    posts_count: int = 0
    created_at: datetime


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    username: str | None = Field(default=None, min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.]+$")
    bio: str | None = Field(default=None, max_length=500)
    profile_image_url: str | None = Field(default=None, max_length=2048)
    interests: list[str] | None = None


class FollowState(BaseModel):
    following: bool


class PostCreate(BaseModel):
    content: str = Field(min_length=1, max_length=10000)
    image_url: str | None = Field(default=None, max_length=2048)
    visibility: Visibility = "PUBLIC"
    tags: list[str] = []

    @field_validator("content")
    @classmethod
    def content_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Content cannot be blank")
        return value.strip()


class CommentCreate(BaseModel):
    content: str = Field(min_length=1, max_length=2000)

    @field_validator("content")
    @classmethod
    def comment_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Comment cannot be blank")
        return value.strip()


class StatusCreate(PostCreate):
    pass


class PostOut(BaseModel):
    id: UUID
    author: UserOut
    content: str
    image_url: str | None
    visibility: Visibility
    created_at: datetime
    tags: list[TagOut]
    likes_count: int = 0
    liked_by_me: bool = False
    comments_count: int = 0


class StatusOut(BaseModel):
    id: UUID
    author: UserOut
    content: str
    image_url: str | None
    visibility: Visibility
    created_at: datetime
    expires_at: datetime
    tags: list[TagOut]


class AuthRegister(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.]+$")
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=1, max_length=100)


class AuthLogin(BaseModel):
    username_or_email: str = Field(min_length=1, max_length=320)
    password: str = Field(min_length=1, max_length=128)


class TokenOut(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    user: UserOut


class SearchOut(BaseModel):
    query: str
    posts: list[PostOut]
    statuses: list[StatusOut]
    matched_tags: list[TagOut]
    categories: list[str]


class HealthOut(BaseModel):
    status: Literal["ok"]
    ai_provider: str
    semantic_search: bool
