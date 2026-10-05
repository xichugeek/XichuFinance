# PHASE 3 — Local Backend and PostgreSQL

Status: **IN PROGRESS** (2026-10-05, Asia/Shanghai)

The first Backend implementation is in `backend/`. It provides FastAPI `/docs`, email/password registration and login, Argon2 password hashes, 12-hour JWT access tokens, user-scoped accounts, categories, transactions, and month analytics. Money uses `NUMERIC(18,2)` and Python `Decimal`. Alembic revision `0001_initial` creates the database schema. The local Compose file defines an API and PostgreSQL 17 with a named volume; PostgreSQL has no published host port, and the API binds only to `127.0.0.1:8000`.

## Verified so far

- `backend/.venv/Scripts/python.exe -m pytest -q`: **6 passed**, covering live database health reporting, `/docs`, registration/login, Argon2, account and transaction CRUD, user isolation, analytics, decimal money precision, and invalid money.
- Alembic `upgrade head` and a second `upgrade head` succeeded against a temporary SQLite database; `current` reported `0001_initial (head)`. PostgreSQL migration remains **NOT VERIFIED**.
- Docker Desktop 4.93.0 was installed in per-user mode from a Docker Inc-signed installer. Docker CLI `29.8.1` and Compose `v5.5.1` run; `docker compose config --quiet` returned success with temporary placeholder environment values.

## Runtime gate

The Docker Engine is **NOT RUNNING**. Docker Desktop's own log reports `Virtual Machine Platform not enabled` and `No virtualization available`; `docker desktop status` is `stopped`, and `wsl --version` did not return a version. Enabling Windows virtualization/WSL features is a system configuration change and may require administrator access and a restart. The user was asked how to proceed before that change. Therefore **BACKEND_LOCAL, POSTGRESQL, and DOCKER_RUNTIME are NOT VERIFIED**, and PHASE 3 cannot be marked PASS.

The API has not been connected to Android, and CSV import and AI endpoints have not yet been implemented. Their later phase gates remain open.
