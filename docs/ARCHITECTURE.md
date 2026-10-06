# Architecture and current implementation

Xichu Finance uses one Android application, one FastAPI service, and one PostgreSQL database. The repository is built and accepted in phases; [PHASE_STATUS.md](PHASE_STATUS.md) records which parts are actually verified.

## Android local application

The app uses Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, coroutines/Flow, a repository, and Room. UI events go through the ViewModel and repository to local/remote data sources. Account names, categories, transactions and classification rules are stored locally. Fictional demo data is inserted only in the independent local ledger. Room schemas are exported under `android/app/schemas/`; explicit migrations upgrade v1 → v2 (rules) → v3 (calendar-date encoding), without deleting ledger data.

Android money is stored as integer minor units (`Long`, cents for CNY), with `BigInteger` for aggregates. User input is converted through decimal parsing rather than floating-point arithmetic. Local month summaries and charts are derived from cached transactions.

Transaction dates encode a calendar day as UTC midnight milliseconds, decoded without the device time zone. The v2 → v3 migration preserves the date displayed in the current device zone at upgrade; later zone changes do not alter it. `created_at` / `updated_at` remain real timestamps. A cloud refresh uses the authoritative server `DATE` value.

Remote API integration passed local acceptance in [PHASE 4](ANDROID_BACKEND_PHASE_4.md). Retrofit/OkHttp with kotlinx.serialization connects cloud-mode operations to FastAPI; DataStore persists a Keystore-encrypted session. Each server/user pair has a separate Room cache. The original local ledger is an independent mode and is never uploaded automatically.

API instances reuse one OkHttp connection pool; Authorization is an explicit per-request header, never shared client state. Public-network timeouts are bounded at 15 seconds connect, 30 seconds read and 60 seconds per call. Reuse follows [OkHttp's own client guidance](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt); TLS/hostname verification remains standard, with no added write replay.

## Backend

| File | Responsibility |
| --- | --- |
| `backend/app/main.py` | Core HTTP routes and ownership checks |
| `backend/app/analytics.py` | User-scoped SQL month aggregates and rankings |
| `backend/app/classification.py` | User rules, keywords and optional NoAIProvider contract |
| `backend/app/ask_finance.py` | Limited Chinese intents, SQL queries and templates |
| `backend/app/schemas.py` | Request validation and response schemas |
| `backend/app/models.py` | SQLAlchemy users, accounts, categories, rules, transactions |
| `backend/app/auth.py` | Argon2 password hashing and JWT authentication |
| `backend/app/db.py` | Engine, session lifecycle, database dependency |
| `backend/alembic/` | Versioned database migrations |
| `backend/tests/` | API unit/integration checks with isolated SQLite test databases |
| `scripts/validate_backend.py` | Real local HTTP or explicitly approved production HTTPS checks |

Every user data route derives the user ID from the authenticated token. Requests cannot choose their owning user. Referenced accounts and categories must also belong to that user. Money uses PostgreSQL `NUMERIC(18,2)` and Python `Decimal`; JSON money values are decimal strings. The initial release currently supports CNY.

The API container applies `alembic upgrade head` before starting Uvicorn. Startup does not drop or recreate existing tables. Analytics use SQL sums/grouping and return `Decimal` strings without floating point money calculations.

## Local Docker deployment

Compose runs `api` and `db` under project name `xichufinance`. The API is published at `127.0.0.1:8000`; PostgreSQL has no published host port. Database files live in the named volume `xichufinance_postgres_data`. Normal container rebuilds preserve this volume. Removing it deletes the local database and is not part of routine restart instructions.

`POSTGRES_PASSWORD` and `JWT_SECRET` are generated into ignored `.env` by `scripts/setup_local_env.py`. Docker Hub is the default image source. Optional `POSTGRES_IMAGE` and `PYTHON_IMAGE` settings support the Docker verified account's images on AWS ECR Public when Docker Hub cannot be reached.

## CSV, classification and production

CSV, charts, classification and Ask Finance passed local acceptance. Android integration uses server data as the source for cloud records and Room for cached/offline reading, with full refreshes and server timestamps. Its verification and synchronization limits are recorded in the PHASE 4 document. User rules precede shared keywords; external AI is optional and not configured in v1. Ask calculates values in the database and formats templates. No question is executed as SQL.

Production deployment followed a read-only server audit and explicit user approval. Two Finance containers have no host ports: PostgreSQL uses only an internal Finance network, while the non-root API also joins the existing Caddy network as `xichufinance-api`. Caddy terminates valid public HTTPS. Actual production/backup/restore evidence is in [SERVER_DEPLOYMENT](SERVER_DEPLOYMENT.md); signed APK runtime evidence is in [APK_RELEASE_VALIDATION](APK_RELEASE_VALIDATION.md). Network failures preserve the cache; refresh is explicit, with no automatic write replay.
