# PHASE 3 — Local Backend and PostgreSQL

Status: **local API/PostgreSQL foundation PASS** (2026-10-05, Asia/Shanghai). Complete **BACKEND_LOCAL = PASS** was subsequently verified with [PHASE 5 CSV checks](CSV_PHASE_5.md).

The first Backend implementation is in `backend/`. It provides FastAPI `/docs`, email/password registration and login, Argon2 password hashes, 12-hour JWT access tokens, user-scoped accounts, categories, transactions, and month analytics. Money uses `NUMERIC(18,2)` and Python `Decimal`. Alembic revision `0001_initial` creates the database schema. The local Compose file defines an API and PostgreSQL 17 with a named volume; PostgreSQL has no published host port, and the API binds only to `127.0.0.1:8000`.

## Verified so far

- `backend/.venv/Scripts/python.exe -m pytest -q`: **6 passed**, covering live database health reporting, `/docs`, registration/login, Argon2, account and transaction CRUD, user isolation, analytics, decimal money precision, and invalid money.
- Actual Compose services `xichufinance-api-1` and `xichufinance-db-1` both report **healthy**. The database reports **PostgreSQL 17.11**.
- `python scripts/validate_backend.py` returned **LOCAL_API_CORE = PASS** against the running API/PostgreSQL containers: health/docs, registration/login/authentication, accounts/categories, transaction CRUD, analytics, invalid money, and cross-user read/write/delete isolation.
- Live decimal totals matched `0.01 + 0.10 + 0.20 = 0.31`; income `100000000.00` minus those expenses returned `99999999.69`.
- Alembic `current` reported `0001_initial (head)` on PostgreSQL. A repeated `upgrade head` returned success, and `alembic check` reported `No new upgrade operations detected`.
- PostgreSQL container recreation preserved the named volume: the fictional user count remained **2** before and after recreation, and both services became healthy again.
- Docker CLI `29.8.1`, Compose `v5.5.1`, the signed official installer, and a real Linux smoke container were verified.

## Runtime gate

**DOCKER_RUNTIME = PASS.** After the user installed WSL **3.0.1.0** (default version **2**), Docker Desktop's per-user launcher failed because its startup registry information was missing. The user explicitly approved uninstall/reinstall. Settings were backed up outside the repository first; no Docker distribution or VHD file existed at that point. The official signed installer successfully installed Docker Desktop **4.93.0** for all users in `C:\Program Files\Docker\Docker`. `docker version` now returns the Linux server version **29.8.1**, and an actual container printed `Hello from Docker!` and exited with code 0.

Docker Hub image pulls encountered a TLS handshake timeout. The Docker verified account on [AWS ECR Public](https://gallery.ecr.aws/docker/) successfully supplied `public.ecr.aws/docker/library/hello-world:latest`, digest `sha256:5e23090353324d887c48ad5e5c56d294eab81588df9605b07d1afe895f9cc8f8`. Compose supports optional `POSTGRES_IMAGE` and `PYTHON_IMAGE` settings for this distribution source while retaining Docker Hub defaults. Both abandoned upgrade download jobs were cancelled after the successful reinstall.

**POSTGRESQL, MIGRATION, DOCKER_RUNTIME, and LOCAL_API_CORE = PASS.** The original PHASE 3 check covered the core API. Android integration subsequently passed PHASE 4 and CSV passed PHASE 5, completing **BACKEND_LOCAL = PASS**. Classification, Ask Finance, production HTTPS, and signed APK acceptance remain later gates.

## Local commands

From the repository root, with Python installed and Docker Engine running:

```powershell
python scripts/setup_local_env.py
docker compose --project-name xichufinance up -d --build --wait
docker compose --project-name xichufinance ps
python scripts/validate_backend.py
```

If Docker Hub and PyPI are unreachable, create the first `.env` using `python scripts/setup_local_env.py --ecr --tuna` instead. `--ecr` uses Docker Official Images on AWS ECR Public; `--tuna` uses [Tsinghua's public HTTPS PyPI mirror](https://mirrors.tuna.tsinghua.edu.cn/help/pypi/). Both overrides were used successfully on this machine after the original endpoints failed. The script preserves an existing `.env`; it never regenerates passwords for an existing database. To adjust an existing file, use the commented settings in `.env.example`. Package-index build arguments must contain only public URLs, never credentials. Open a new terminal after installing Docker so the CLI and its credential helper are on PATH.

The HTTP validation script uses only loopback addresses and fictional users. It deletes its transactions and account afterward. Two test users and their categories remain per run because user/category deletion is not part of the current API. It does not print passwords or tokens. The script was extended in PHASE 5 to include real PostgreSQL CSV preview/commit/duplicate checks.

Android local integration subsequently passed [PHASE 4](ANDROID_BACKEND_PHASE_4.md). CSV import and AI endpoints remain later phase gates.
