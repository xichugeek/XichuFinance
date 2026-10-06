# Project phase status

Last checked: 2026-10-06 (Asia/Shanghai)

| Phase | Status | Evidence / gate |
| --- | --- | --- |
| PHASE 0 — PROJECT_002_PREFLIGHT | PASS | [Environment audit](PROJECT_002_PREFLIGHT.md) |
| PHASE 1 — Android Local MVP | PASS | [Build, unit tests, emulator and CRUD evidence](ANDROID_LOCAL_MVP.md) |
| PHASE 2 — Room + Demo Data | PASS | Room schema v1 is exported; fictional data appeared on first launch; account/category data remained after APK update. |
| PHASE 3 — Local Backend + PostgreSQL | PASS | [Actual Docker/API/PostgreSQL evidence](BACKEND_PHASE_3.md); complete `BACKEND_LOCAL` also passed with PHASE 5 CSV checks. |
| PHASE 4 — Android ↔ Backend | PASS for local Debug integration | [3 passing device tests, unit tests, ADB install, and offline UI screenshot](ANDROID_BACKEND_PHASE_4.md). Production integration remains PHASE 10–11. |
| PHASE 5 — CSV Import | PASS locally | [Backend/PostgreSQL, parser and Compose preview/confirm/duplicate evidence](CSV_PHASE_5.md). |
| PHASE 6 — Analytics | PASS locally | [Exact money, SQL aggregates, month switching and emulator charts](ANALYTICS_PHASE_6.md). |
| PHASE 7 — Smart Classification | PASS locally | [Rules, shared keywords, disabled AI fallback and real device test](CLASSIFICATION_PHASE_7.md). |
| PHASE 8 — Ask Finance | PASS locally | [Owned database queries, seven intents, disabled AI templates and device test](ASK_PHASE_8.md). |
| PHASE 9 — Testing | PASS locally | [Clean build, 16 Backend / 11 JVM / 9 device tests, Lint, migrations and secret review](TESTING_PHASE_9.md). |
| PHASE 10 — Production Deployment | PASS | [HTTPS, API, existing sites, backup/restore and isolation evidence](SERVER_DEPLOYMENT.md). DNS/server changes were explicitly approved. |
| PHASE 11 — Release APK | IN PROGRESS | Signing creation approved; production HTTPS verified. Signed clean build and Release runtime acceptance remain pending. |

Production changes are isolated to the approved Finance deployment and its added DNS/proxy route. Acceptance data is fictional; no existing financial data was used.
