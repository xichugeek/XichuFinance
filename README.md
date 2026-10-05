# Xichu Finance

Xichu Finance is an open source personal finance tracker. Local Android/Backend integration, offline caching, standard CSV import, analytics, classification rules and Ask Finance have passed real runtime validation. Production HTTPS and the signed release APK are still being developed; see [verified phase status](docs/PHASE_STATUS.md).

The app offers separate local and cloud ledgers, with Room caching for offline reading. Local-ledger data is never uploaded automatically; cloud mode stores that user's records on the Backend. All bundled examples are fictional. It never asks for a real card number, CVV, bank password, or identity number. This is a personal bookkeeping project, not a banking system.

![Android local MVP dashboard](docs/screenshots/android-dashboard.png)

## Current Android stage

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, and Room.
- Local dashboard, transaction create/read/update/delete, accounts, and categories.
- Monthly analytics, daily expense line chart, category donut chart and standard CSV preview/confirm import.
- Email/password registration and login, encrypted Android session, isolated per-user cache.
- User classification rules and shared keywords, with **AI Enhancement Disabled** by default.
- Seven basic Chinese Ask Finance intents with database-calculated amounts and template answers.
- Integer minor units for money; no floating point database amounts.
- A fictional data set is inserted on first launch.

![Analytics](docs/screenshots/android-analytics.png)

![Ask Finance](docs/screenshots/android-ask.png)

## Architecture

Android UI → ViewModel → Repository → Room / Retrofit → FastAPI → PostgreSQL.

The Backend uses SQLAlchemy 2, Pydantic, Alembic, Argon2 password hashes and JWT access tokens. Docker Compose runs one API and one internal database. Money uses integer cents on Android and `NUMERIC`/`Decimal` on the Backend. Core functionality requires no paid AI API; v1 ships NoAIProvider and no external AI adapter.

To build locally on Windows, install Android Studio and the Android SDK, then run from `android/`:

```powershell
.\gradlew.bat :app:assembleDebug
```

The project is configured for JDK 21, AGP 9.1.1, Gradle 9.3.1, and Android SDK 36.1. See [the environment audit](docs/PROJECT_002_PREFLIGHT.md) for what was present on the development machine. The Debug APK is an interim development artifact and is not the required signed release APK.

## Local Backend

With Python and Docker Engine running, execute from the repository root:

```powershell
python scripts/setup_local_env.py
docker compose --project-name xichufinance up -d --build --wait
python scripts/validate_backend.py
```

The API docs are at `http://127.0.0.1:8000/docs`. Setup generates ignored local secrets without displaying them. See [Backend setup and validation](docs/BACKEND_PHASE_3.md) for network mirror options and the exact acceptance scope. PostgreSQL has no published host port. Windows Docker Desktop needs its WSL 2 engine and hardware virtualization; Android-only local mode does not require Docker.

## Learn and use

- [Step-by-step beginner guide / 小白教程](docs/BEGINNER_GUIDE.md)
- [Standard CSV format and import steps](docs/CSV_FORMAT.md): only this format is formally supported; raw bank/WeChat/Alipay formats are not verified.
- [Classification rules and optional AI limits](docs/CLASSIFICATION_PHASE_7.md)
- [Ask Finance supported questions](docs/ASK_PHASE_8.md)
- [Server deployment and safety gates](docs/SERVER_DEPLOYMENT.md)
- [Signed APK build guide](docs/ANDROID_RELEASE.md) and [actual Release acceptance](docs/APK_RELEASE_VALIDATION.md)

## Tests

Backend: from `backend`, create a Python venv, install `requirements-dev.txt`, and run `python -m pytest -q`. Android: from `android`, run ` .\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest` with the local API/emulator running. The real HTTP script only writes fictional data to a loopback HTTP API. Run `python scripts/secret_review.py` from the repository root before commits, together with manual secret review.

## v1 limits

Cloud writes/import/Ask need a network connection. Cached records remain readable offline; there is no pending-write queue. Refresh replaces a user's cache with a complete server snapshot. Local and cloud ledgers are separate. CNY is the only supported currency. JWTs have a 12-hour lifetime without refresh/revocation; no password reset or email verification is implemented. Room data is not encrypted. Questions use limited phrase recognition, and external AI is not configured. See [security](docs/SECURITY.md) and [privacy](docs/PRIVACY.md).

## Status and documentation

See [phase status](docs/PHASE_STATUS.md), [Android validation](docs/ANDROID_LOCAL_MVP.md), [analytics validation](docs/ANALYTICS_PHASE_6.md), [architecture](docs/ARCHITECTURE.md), [API](docs/API.md), [security](docs/SECURITY.md), and [privacy](docs/PRIVACY.md). Local Backend/CSV/analytics acceptance passed; production and final release acceptance remain open.
