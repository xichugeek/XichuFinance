# Xichu Finance

Xichu Finance is an open source personal finance tracker in development. Local Android/Backend integration, offline cache, and standard CSV preview/confirm/duplicate checks have passed real runtime validation. Production HTTPS and the signed release APK are still being developed.

The app offers separate local and cloud ledgers, with Room caching for offline reading. Local-ledger data is never uploaded automatically; cloud mode stores that user's records on the Backend. All bundled examples are fictional. It never asks for a real card number, CVV, bank password, or identity number. This is a personal bookkeeping project, not a banking system.

![Android local MVP dashboard](docs/screenshots/android-dashboard.png)

## Current Android stage

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, and Room.
- Local dashboard, transaction create/read/update/delete, accounts, and categories.
- Monthly analytics, daily expense line chart, category donut chart and standard CSV preview/confirm import.
- Integer minor units for money; no floating point database amounts.
- A fictional data set is inserted on first launch.

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

The API docs are at `http://127.0.0.1:8000/docs`. Setup generates ignored local secrets without displaying them. See [Backend setup and validation](docs/BACKEND_PHASE_3.md) for network mirror options and the exact acceptance scope. PostgreSQL has no published host port.

## Status and documentation

See [phase status](docs/PHASE_STATUS.md), [Android validation](docs/ANDROID_LOCAL_MVP.md), [analytics validation](docs/ANALYTICS_PHASE_6.md), [architecture](docs/ARCHITECTURE.md), [API](docs/API.md), [security](docs/SECURITY.md), and [privacy](docs/PRIVACY.md). Local Backend/CSV/analytics acceptance passed; production and final release acceptance remain open.
