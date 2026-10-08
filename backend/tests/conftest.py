import os
import uuid

os.environ.setdefault("DATABASE_URL", "sqlite:///./test_urfeed.db")
os.environ.setdefault("JWT_SECRET_KEY", "test-only-secret-not-for-production")
os.environ.setdefault("AI_PROVIDER", "mock")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, make_url
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.db.database import Base, get_db
from app.main import app
from app.models import User

# Optional MySQL integration run: set URFEED_TEST_DATABASE_URL to a dedicated *_test database.
TEST_DATABASE_URL = os.environ.get("URFEED_TEST_DATABASE_URL")


def _create_test_engine():
    if not TEST_DATABASE_URL:
        return create_engine(
            "sqlite://",
            connect_args={"check_same_thread": False},
            poolclass=StaticPool,
        )
    database = make_url(TEST_DATABASE_URL).database or ""
    if not database.endswith("_test"):
        raise RuntimeError("URFEED_TEST_DATABASE_URL must point to a database whose name ends with '_test'; its tables are dropped after each test")
    return create_engine(TEST_DATABASE_URL, pool_pre_ping=True)


@pytest.fixture
def client(monkeypatch):
    engine = _create_test_engine()
    Base.metadata.drop_all(engine)
    TestSession = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    Base.metadata.create_all(engine)
    app.state.test_session_factory = TestSession

    def override_get_db():
        db = TestSession()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
    Base.metadata.drop_all(engine)
    engine.dispose()


def register(client: TestClient, username: str, email: str | None = None, password: str = "correct-horse-9") -> tuple[str, str]:
    response = client.post("/api/auth/register", json={
        "username": username,
        "email": email or f"{username}@example.com",
        "password": password,
        "display_name": username.title(),
    })
    assert response.status_code == 201, response.text
    payload = response.json()
    return payload["access_token"], payload["user"]["id"]


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}
