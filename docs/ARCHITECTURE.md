# Architecture and current implementation

Xichu Finance uses one Android application, one FastAPI service, and one PostgreSQL database. The repository is built and accepted in phases; [PHASE_STATUS.md](PHASE_STATUS.md) records which parts are actually verified.

## Android local application

The current app uses Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, coroutines/Flow, a repository, and Room. UI events go through the ViewModel and repository to Room. Account names, categories, and transactions are stored locally, and fictional demo data is inserted on the first launch. The Room schema is exported under `android/app/schemas/`.

Android money is stored as integer minor units (`Long`, cents for CNY). User input is converted through decimal parsing rather than floating-point arithmetic. Local month summaries are derived from stored transactions.

Remote API integration and synchronization are PHASE 4 work and are not yet present. The current local MVP must not be described as a cloud-synchronized application.

## Backend

| File | Responsibility |
| --- | --- |
| `backend/app/main.py` | HTTP routes, ownership checks, month analytics |
| `backend/app/schemas.py` | Request validation and response schemas |
| `backend/app/models.py` | SQLAlchemy users, accounts, categories, transactions |
| `backend/app/auth.py` | Argon2 password hashing and JWT authentication |
| `backend/app/db.py` | Engine, session lifecycle, database dependency |
| `backend/alembic/` | Versioned database migrations |
| `backend/tests/` | API unit/integration checks with isolated SQLite test databases |
| `scripts/validate_backend.py` | Real HTTP checks against the local container API |

Every user data route derives the user ID from the authenticated token. Requests cannot choose their owning user. Referenced accounts and categories must also belong to that user. Money uses PostgreSQL `NUMERIC(18,2)` and Python `Decimal`; JSON money values are decimal strings. The initial release currently supports CNY.

The API container applies `alembic upgrade head` before starting Uvicorn. Startup does not drop or recreate existing tables. Analytics currently sum selected user transactions using `Decimal`; later analytics work may move larger aggregations into SQL without changing the money contract.

## Local Docker deployment

Compose runs `api` and `db` under project name `xichufinance`. The API is published at `127.0.0.1:8000`; PostgreSQL has no published host port. Database files live in the named volume `xichufinance_postgres_data`. Normal container rebuilds preserve this volume. Removing it deletes the local database and is not part of routine restart instructions.

`POSTGRES_PASSWORD` and `JWT_SECRET` are generated into ignored `.env` by `scripts/setup_local_env.py`. Docker Hub is the default image source. Optional `POSTGRES_IMAGE` and `PYTHON_IMAGE` settings support the Docker verified account's images on AWS ECR Public when Docker Hub cannot be reached.

## Later phases

Android remote calls, token storage, synchronization, CSV import, richer charts, classification, and Ask Finance remain separate phase gates. The intended v1.0 sync contract is server data as the source for synced records and Room as the local cache/work copy, using timestamps and documented limitations. This contract is not claimed as implemented yet.

Production deployment requires a separate read-only server audit and the user's confirmation before writes. The production URL, HTTPS verification, release signing, and APK installation are not implied by a passing local Docker or Debug build.
