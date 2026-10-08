from datetime import UTC, datetime, timedelta
from uuid import UUID

from app.models import Status
from conftest import auth, register


def test_registration_duplicate_email_username_and_password_hash(client):
    token, user_id = register(client, "alice")
    me = client.get("/api/auth/me", headers=auth(token))
    assert me.status_code == 200
    assert me.json()["id"] == user_id
    assert me.json()["email"] == "alice@example.com"
    assert "password_hash" not in me.json()
    assert client.post("/api/auth/register", json={"username": "other", "email": "alice@example.com", "password": "correct-horse-9", "display_name": "Other"}).status_code == 409
    assert client.post("/api/auth/register", json={"username": "alice", "email": "other@example.com", "password": "correct-horse-9", "display_name": "Other"}).status_code == 409


def test_login_invalid_password_and_jwt_protection(client):
    token, _ = register(client, "alice")
    assert client.post("/api/auth/login", json={"username_or_email": "alice", "password": "wrong-password"}).status_code == 401
    login = client.post("/api/auth/login", json={"username_or_email": "alice@example.com", "password": "correct-horse-9"})
    assert login.status_code == 200
    assert client.get("/api/auth/me").status_code == 401
    assert client.get("/api/auth/me", headers=auth(token)).status_code == 200


def test_follow_duplicate_self_follow_unfollow_and_lists(client):
    alice_token, alice_id = register(client, "alice")
    bob_token, bob_id = register(client, "bob")
    headers = auth(alice_token)
    assert client.post(f"/api/users/{bob_id}/follow", headers=headers).status_code == 200
    assert client.post(f"/api/users/{bob_id}/follow", headers=headers).status_code == 200
    followers = client.get(f"/api/users/{bob_id}/followers", headers=headers).json()
    assert [user["id"] for user in followers] == [alice_id]
    assert client.post(f"/api/users/{alice_id}/follow", headers=headers).status_code == 400
    assert client.get(f"/api/users/{alice_id}/following", headers=headers).json()[0]["id"] == bob_id
    assert client.delete(f"/api/users/{bob_id}/follow", headers=headers).status_code == 200
    assert client.delete(f"/api/users/{bob_id}/follow", headers=headers).status_code == 200
    assert client.get(f"/api/users/{bob_id}/followers", headers=headers).json() == []


def test_profile_update_and_user_search(client):
    alice_token, _ = register(client, "alice")
    bob_token, bob_id = register(client, "bob")
    client.put("/api/users/me", headers=auth(bob_token), json={"interests": ["Java"]})
    updated = client.put("/api/users/me", headers=auth(alice_token), json={"bio": "Learning Python", "interests": ["Python", "AI"]})
    assert updated.status_code == 200
    assert updated.json()["interests"] == ["Python", "AI"]
    assert client.get("/api/users/search?q=bo", headers=auth(alice_token)).json()[0]["id"] == bob_id
    assert client.get("/api/users/search?q=people%20learning%20Java", headers=auth(alice_token)).json()[0]["id"] == bob_id


def test_post_create_feed_visibility_like_and_delete(client):
    alice_token, alice_id = register(client, "alice")
    bob_token, bob_id = register(client, "bob")
    public_post = client.post("/api/posts", headers=auth(alice_token), json={"content": "Built a Python AI agent", "tags": ["AI", "Python"]})
    private_post = client.post("/api/posts", headers=auth(alice_token), json={"content": "Private update about autonomous work", "visibility": "FOLLOWERS"})
    assert public_post.status_code == 201
    assert private_post.status_code == 201
    public_payload = public_post.json()
    assert public_payload["author"]["email"] is None
    assert {tag["name"] for tag in public_payload["tags"]} >= {"Artificial Intelligence", "AI Agents", "Python"}
    assert client.get(f"/api/posts/{private_post.json()['id']}", headers=auth(bob_token)).status_code == 403
    assert all(item["id"] != private_post.json()["id"] for item in client.get("/api/search", headers=auth(bob_token), params={"q": "autonomous"}).json()["posts"])
    assert client.get("/api/posts/feed", headers=auth(bob_token)).json()[0]["id"] == public_payload["id"]
    assert client.post(f"/api/users/{alice_id}/follow", headers=auth(bob_token)).status_code == 200
    assert client.get(f"/api/posts/{private_post.json()['id']}", headers=auth(bob_token)).status_code == 200
    assert client.post(f"/api/posts/{public_payload['id']}/like", headers=auth(bob_token)).status_code == 204
    assert client.post(f"/api/posts/{public_payload['id']}/like", headers=auth(bob_token)).status_code == 204
    assert client.get(f"/api/posts/{public_payload['id']}", headers=auth(bob_token)).json()["likes_count"] == 1
    assert client.post(f"/api/posts/{public_payload['id']}/comments", headers=auth(bob_token), json={"content": "Helpful build notes!"}).status_code == 201
    assert client.get(f"/api/posts/{public_payload['id']}", headers=auth(alice_token)).json()["comments_count"] == 1
    assert client.delete(f"/api/posts/{public_payload['id']}", headers=auth(bob_token)).status_code == 403
    assert client.delete(f"/api/posts/{public_payload['id']}", headers=auth(alice_token)).status_code == 204


def test_status_expiry_and_visibility_for_feed_lookup_search(client):
    owner_token, _ = register(client, "owner")
    other_token, _ = register(client, "other")
    active = client.post("/api/statuses", headers=auth(owner_token), json={"content": "Cricket match day", "visibility": "FOLLOWERS"})
    assert active.status_code == 201
    data = active.json()
    assert {tag["name"] for tag in data["tags"]} >= {"Sports", "Cricket"}
    created = datetime.fromisoformat(data["created_at"])
    expires = datetime.fromisoformat(data["expires_at"])
    assert expires - created == timedelta(hours=24)
    assert client.get(f"/api/statuses/{data['id']}", headers=auth(other_token)).status_code == 403
    assert client.get("/api/search?q=cricket", headers=auth(other_token)).json()["statuses"] == []
    assert client.get("/api/statuses/feed", headers=auth(owner_token)).json()[0]["id"] == data["id"]


def test_expired_status_excluded_from_active_feed_and_search(client):
    token, user_id = register(client, "alice")
    created = client.post("/api/statuses", headers=auth(token), json={"content": "Cricket update"})
    assert created.status_code == 201
    with client.app.state.test_session_factory() as db:
        status = db.get(Status, UUID(created.json()["id"]))
        status.expires_at = datetime.now(UTC) - timedelta(seconds=1)
        db.commit()
    assert client.get("/api/statuses/feed", headers=auth(token)).json() == []
    assert client.get("/api/search", headers=auth(token), params={"q": "cricket"}).json()["statuses"] == []
    assert client.get(f"/api/statuses/{created.json()['id']}", headers=auth(token)).status_code == 404


def test_search_includes_public_posts_and_canonical_tags(client):
    token, _ = register(client, "alice")
    client.post("/api/posts", headers=auth(token), json={"content": "An autonomous assistant built with LangChain", "tags": ["AI Agent", "LangChain"]})
    result = client.get("/api/search", headers=auth(token), params={"q": "AI agents"})
    assert result.status_code == 200
    assert result.json()["posts"]
    assert "AI Agents" in {tag["name"] for tag in result.json()["matched_tags"]}


def test_semantic_fallback_finds_python_and_placement_topics(client):
    token, _ = register(client, "alice")
    client.post("/api/posts", headers=auth(token), json={"content": "A small command line tool", "tags": ["Python"]})
    client.post("/api/posts", headers=auth(token), json={"content": "Preparing for the interview season", "tags": ["Placements"]})
    python_result = client.get("/api/search", headers=auth(token), params={"q": "Python projects"}).json()
    placement_result = client.get("/api/search", headers=auth(token), params={"q": "college placement preparation"}).json()
    assert python_result["posts"]
    assert placement_result["posts"]


def test_search_requires_relevance_evidence_and_supports_text_matches(client):
    token, _ = register(client, "alice")
    client.post("/api/posts", headers=auth(token), json={"content": "Autonomous agent orchestration", "tags": ["AI Agents"]})
    client.post("/api/posts", headers=auth(token), json={"content": "Pottery techniques for beginners"})
    client.post("/api/posts", headers=auth(token), json={"content": "A recent unrelated cooking note"})
    semantic = client.get("/api/search", headers=auth(token), params={"q": "AI agents"}).json()
    text = client.get("/api/search", headers=auth(token), params={"q": "pottery"}).json()

    assert "Autonomous agent orchestration" in {post["content"] for post in semantic["posts"]}
    assert "Pottery techniques for beginners" in {post["content"] for post in text["posts"]}
    assert "A recent unrelated cooking note" not in {post["content"] for post in text["posts"]}


def test_ai_fallback_empty_unknown_aliases_and_visibility_not_a_tag(client):
    token, _ = register(client, "alice")
    empty = client.post("/api/posts", headers=auth(token), json={"content": "A quiet afternoon.", "tags": ["AI", "A.I.", "Artificial-Intelligence", "PUBLIC"]})
    assert empty.status_code == 201
    names = [tag["name"] for tag in empty.json()["tags"]]
    assert names.count("Artificial Intelligence") == 1
    assert "PUBLIC" not in names
