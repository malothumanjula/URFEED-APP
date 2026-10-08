import os
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.dialects import mysql
from sqlalchemy.schema import CreateTable

from app.ai import embeddings
from app.ai.tagging import DeterministicTaggingService
from app.ai.vector_search import cosine_similarity
from app.core.config import Settings
from app.db.database import Base
from conftest import _create_test_engine, auth, register

BACKEND_DIR = Path(__file__).resolve().parents[1]


def test_health_reports_database_connectivity_without_credentials(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["database"] == "ok"
    assert body["database_backend"] in {"sqlite", "mysql"}
    assert "password" not in response.text.lower() and "://" not in response.text


def test_timestamps_are_utc_aware_with_microseconds(client):
    token, _ = register(client, "alice")
    status = client.post("/api/statuses", headers=auth(token), json={"content": "Morning run"}).json()
    created = datetime.fromisoformat(status["created_at"].replace("Z", "+00:00"))
    expires = datetime.fromisoformat(status["expires_at"].replace("Z", "+00:00"))
    assert created.utcoffset() == timedelta(0)
    assert expires - created == timedelta(hours=24)
    fetched = client.get(f"/api/statuses/{status['id']}", headers=auth(token)).json()
    assert datetime.fromisoformat(fetched["created_at"].replace("Z", "+00:00")) == created


def test_feed_orders_posts_created_within_the_same_second(client):
    token, _ = register(client, "alice")
    ids = [client.post("/api/posts", headers=auth(token), json={"content": f"Note {index}"}).json()["id"] for index in range(3)]
    assert [post["id"] for post in client.get("/api/posts/feed", headers=auth(token)).json()] == list(reversed(ids))


def test_unlike_and_follow_counts(client):
    alice_token, alice_id = register(client, "alice")
    bob_token, _ = register(client, "bob")
    post_id = client.post("/api/posts", headers=auth(alice_token), json={"content": "Hello"}).json()["id"]
    assert client.post(f"/api/posts/{post_id}/like", headers=auth(bob_token)).status_code == 204
    assert client.get(f"/api/posts/{post_id}", headers=auth(bob_token)).json()["liked_by_me"] is True
    assert client.delete(f"/api/posts/{post_id}/like", headers=auth(bob_token)).status_code == 204
    after = client.get(f"/api/posts/{post_id}", headers=auth(bob_token)).json()
    assert after["likes_count"] == 0 and after["liked_by_me"] is False
    client.post(f"/api/users/{alice_id}/follow", headers=auth(bob_token))
    assert client.get("/api/users/alice", headers=auth(bob_token)).json()["followers_count"] == 1


def test_mock_ai_tagging_stores_canonical_ai_tags(client):
    assert ("Cricket", 0.99) in DeterministicTaggingService().analyze_sync("Cricket final tonight")
    token, _ = register(client, "alice")
    post = client.post("/api/posts", headers=auth(token), json={"content": "Learning LangGraph for AI agents"}).json()
    tags = {tag["name"]: tag for tag in post["tags"]}
    assert {"Artificial Intelligence", "AI Agents", "LangGraph"} <= set(tags)
    assert all(tags[name]["source"] == "AI" for name in ("Artificial Intelligence", "LangGraph"))
    tag_feed = client.get("/api/posts/feed", headers=auth(token), params={"tag": "langgraph"}).json()
    assert [item["id"] for item in tag_feed] == [post["id"]]


def test_vector_search_uses_stored_embeddings_and_respects_visibility(client, monkeypatch):
    vectors = {"gardening": [1.0, 0.0, 0.0], "growing tomatoes at home": [0.9, 0.1, 0.0], "secret tomato plans": [0.95, 0.05, 0.0]}

    async def fake_embed(self, text):
        return next((vector for key, vector in vectors.items() if text.startswith(key)), [0.0, 0.0, 1.0])

    monkeypatch.setattr(embeddings.EmbeddingService, "embed", fake_embed)
    alice_token, _ = register(client, "alice")
    bob_token, _ = register(client, "bob")
    public = client.post("/api/posts", headers=auth(alice_token), json={"content": "growing tomatoes at home"}).json()
    private = client.post("/api/posts", headers=auth(alice_token), json={"content": "secret tomato plans", "visibility": "FOLLOWERS"}).json()

    found = [post["id"] for post in client.get("/api/search", headers=auth(bob_token), params={"q": "gardening"}).json()["posts"]]
    assert found == [public["id"]]
    assert private["id"] not in found
    assert cosine_similarity([1.0, 0.0], [1.0, 0.0]) == 1.0 and cosine_similarity([1.0], [1.0, 0.0]) == 0.0


def test_search_works_without_embedding_provider(client):
    token, _ = register(client, "alice")
    client.post("/api/posts", headers=auth(token), json={"content": "Weekend trip to the hills", "tags": ["Travel"]})
    assert embeddings.get_settings().ai_provider == "mock"
    result = client.get("/api/search", headers=auth(token), params={"q": "travel"}).json()
    assert result["posts"] and "Travel" in result["categories"]


def test_env_aliases_for_jwt_secret_and_openai_key(monkeypatch):
    monkeypatch.delenv("JWT_SECRET_KEY", raising=False)
    monkeypatch.setenv("JWT_SECRET", "alias-secret")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test-placeholder")
    settings = Settings(_env_file=None)
    assert settings.jwt_secret_key == "alias-secret"
    assert settings.ai_api_key == "sk-test-placeholder"


def test_models_compile_to_valid_mysql_ddl():
    dialect = mysql.dialect()
    ddl = {name: str(CreateTable(table).compile(dialect=dialect)) for name, table in Base.metadata.tables.items()}
    assert "CHECK" not in ddl["follows"]
    assert "ck_posts_visibility" in ddl["posts"]
    assert "DATETIME(6)" in ddl["statuses"] and "CHAR(32)" in ddl["users"]
    assert "embedding JSON" in ddl["content_embeddings"]
    assert all("VECTOR" not in sql.upper() for sql in ddl.values())


def _alembic(*args: str, database_url: str) -> subprocess.CompletedProcess:
    env = {**os.environ, "DATABASE_URL": database_url}
    return subprocess.run([sys.executable, "-m", "alembic", *args], cwd=BACKEND_DIR, env=env, capture_output=True, text=True, timeout=120)


def test_alembic_upgrade_and_downgrade_on_real_database(tmp_path):
    url = os.environ.get("URFEED_TEST_DATABASE_URL") or f"sqlite:///{(tmp_path / 'migration.db').as_posix()}"
    if os.environ.get("URFEED_TEST_DATABASE_URL"):
        engine = _create_test_engine()  # refuses databases not named *_test
        Base.metadata.drop_all(engine)
        with engine.begin() as connection:
            connection.execute(text("DROP TABLE IF EXISTS alembic_version"))
        engine.dispose()
    upgrade = _alembic("upgrade", "head", database_url=url)
    assert upgrade.returncode == 0, upgrade.stderr
    downgrade = _alembic("downgrade", "base", database_url=url)
    assert downgrade.returncode == 0, downgrade.stderr


def test_alembic_generates_mysql_sql_offline():
    result = _alembic("upgrade", "head", "--sql", database_url="mysql+pymysql://user:pass@localhost:3306/urfeed")
    assert result.returncode == 0, result.stderr
    sql = result.stdout
    assert "CREATE TABLE follows" in sql and "ck_follow_not_self" not in sql
    assert "DATETIME(6)" in sql and "EXTENSION" not in sql and "hnsw" not in sql
