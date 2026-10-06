# Xichu Finance v1.0.0

Locally accepted signed build, 2026-10-06. Source: [xichugeek/XichuFinance](https://github.com/xichugeek/XichuFinance). Package `com.xichugeek.finance`, version code 1, Android 8.0 or later.

## Included

- Kotlin / Compose Android app with separate local/cloud ledgers, accounts, categories and transaction CRUD.
- Room offline reading, explicit migrations and Keystore encrypted login session.
- Email/password authentication, owned data checks, FastAPI, SQLAlchemy, Alembic and PostgreSQL.
- Standard CSV preview, confirmation and duplicate skipping; fictional sample CSV included.
- Monthly totals, expense trends, category charts and largest expenses using exact money arithmetic.
- Personal classification rules, shared keywords and seven Chinese Ask Finance intents using database totals/templates.
- AI Enhancement Disabled / NoAIProvider: no paid AI key required, no external AI calls.
- Isolated production API/database, Caddy HTTPS, manual backups and verified separate-database restore.
- MIT source license, beginner/deployment/build guides and actual acceptance screenshots.

## Artifacts

Accepted files are in ignored local `dist/`; binaries and signing secrets are not Git source files. No Play submission or public GitHub Release publication is claimed.

| File | SHA256 |
| --- | --- |
| `XichuFinance-v1.0.0.apk` | `1d535f986794148a1f5489e37f39d25d0b95a1ef1c87a5ade78f053c834e1813` |
| `XichuFinance-v1.0.0.aab` (optional) | `19d7ae878548787221c3bf79e74de92e70b06443d4f9559097fbfd30b26e59e5` |

Public API: `https://finance-api.demo.xichugeek.com/`. Local-ledger use needs no server. See [acceptance](APK_RELEASE_VALIDATION.md) for signer/evidence and [Android guide](ANDROID_RELEASE.md) for installation and independent builds.

## Verification

16 Backend tests, 11 Android JVM tests and 9 local Debug device tests passed during development. Final clean signed build passed 11 Release JVM tests and Lint with no errors. Signed Release device workflow passed against production HTTPS: authentication, CRUD, CSV, statistics, Ask and retained data. Backup/restore and existing server routes were separately verified. These are recorded runs, not exhaustive testing.

## v1 limits

CNY only. Cloud writes/import/Ask require connectivity; offline mode has cached reads, no write queue. Intermittent network failures can require manual refresh. Local records never automatically merge into cloud. Standard CSV only; raw bank/WeChat/Alipay exports are not verified. Seven limited Chinese question intents; no external AI provider.

Room records are unencrypted. JWTs last 12 hours; no refresh/revocation, password reset, email verification, MFA or dedicated registration rate limiting. No complete account deletion/export/retention UI. Backup scheduling/off-server backup service are not configured. Android 15 emulator verified; physical devices, Play submission and every supported Android version are not verified. See [security](SECURITY.md) and [privacy](PRIVACY.md).
