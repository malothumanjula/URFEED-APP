# URFeed Backend

FastAPI + SQLAlchemy 2 + Alembic backend for the existing URFeed Android app. MySQL 8 is the production database. SQLite is used only by the default isolated API test fixture.

Architecture: Android app -> FastAPI (this backend) -> MySQL. The Android app never connects to MySQL and never holds database credentials or AI keys.

## Requirements

- Python 3.11 or later (tested with Python 3.13)
- MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16)
- Android emulator/device and the backend reachable over the LAN for mobile testing

## MySQL Setup

Create the database once (this never drops or recreates an existing database):

```sql
CREATE DATABASE IF NOT EXISTS urfeed CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Optionally create a dedicated user instead of using `root`:

```sql
CREATE USER 'urfeed_user'@'localhost' IDENTIFIED BY 'replace_with_local_password';
GRANT ALL PRIVILEGES ON urfeed.* TO 'urfeed_user'@'localhost';
```

## Configure the Backend

PowerShell, from `backend/`:

```powershell
py -3 -m venv ..\.venv
..\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Edit the local `.env`: set `DATABASE_URL=mysql+pymysql://USER:PASSWORD@localhost:3306/urfeed?charset=utf8mb4`, generate a long random `JWT_SECRET`, and keep `AI_PROVIDER=mock` to use deterministic local tagging without an AI credential. URL-encode special characters in the password (for example `@` becomes `%40`). `.env` is ignored by Git.

## Migrate and Start

From `backend/` with the virtual environment activated:

```powershell
alembic upgrade head
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Open `http://127.0.0.1:8000/docs` for the OpenAPI interface and `http://127.0.0.1:8000/health` for a health response, which includes `"database": "ok"` when MySQL is reachable.

## AI Tagging and Embeddings

Mock tagging is the default and does not require credentials. To opt into the OpenAI-compatible Chat Completions and Embeddings API, set `AI_PROVIDER=openai`, provide `OPENAI_API_KEY` in backend `.env`, and configure `AI_BASE_URL`, `AI_MODEL`, `EMBEDDING_MODEL`, and `EMBEDDING_DIMENSIONS`. Credentials are read only by the backend, never by the APK. Structured AI output is canonicalized against the server taxonomy. Provider errors fall back to deterministic tags.

Semantic search: MySQL has no pgvector equivalent, so embeddings are stored as JSON in `content_embeddings` and cosine similarity is computed in `app/ai/vector_search.py` over content the viewer is already allowed to see. Without an embedding provider, search ranks by canonical tags, related-topic expansion and keywords. A native vector store can be added later by implementing `VectorSearchBackend`.

## Android API Address

Gradle defaults to `http://10.0.2.2:8000/` for the Android emulator. To override it:

```powershell
.\gradlew.bat :app:assembleDebug -PURFEED_API_BASE_URL=http://10.0.2.2:8000/
```

For a physical device, use the computer's reachable LAN address, for example `http://192.168.1.20:8000/`, and allow that port through the host firewall. Debug builds permit local cleartext HTTP; release builds do not opt into cleartext. Native Android CORS is not enforced, but browser API tools may need a suitable `CORS_ORIGINS` entry.

## Tests and Checks

From `backend/`:

```powershell
pytest -q
python -m compileall -q app migrations tests
alembic upgrade head --sql
```

By default the suite uses in-memory SQLite. To run the same suite plus the Alembic migration test against MySQL, create a separate test database (its tables are dropped after each test; the name must end in `_test`):

```sql
CREATE DATABASE IF NOT EXISTS urfeed_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```powershell
$env:URFEED_TEST_DATABASE_URL = "mysql+pymysql://USER:PASSWORD@localhost:3306/urfeed_test?charset=utf8mb4"
pytest -q
Remove-Item Env:URFEED_TEST_DATABASE_URL
```

Remote AI provider credentials are never exercised by the tests.
