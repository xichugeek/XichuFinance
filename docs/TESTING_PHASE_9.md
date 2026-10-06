# PHASE 9 — Local testing and review

This is the historical record for this phase. Later production and Release results are in [current phase status](PHASE_STATUS.md).

Verified on 2026-10-06 (Asia/Shanghai). `LOCAL_TESTING = PASS`; production and signed-release gates remain open.

## Actual results

| Check | Actual result |
| --- | --- |
| Backend `pytest -q` | 16 passed, one Starlette/httpx deprecation warning |
| Android `clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | BUILD SUCCESSFUL; 11 JVM tests; Lint 0 errors / 9 warnings |
| ADB install app and instrumentation APK | Success / Success |
| Full direct device instrumentation suite | OK (9 tests), 24.023 seconds |
| Real Docker/PostgreSQL HTTP acceptance | BACKEND_LOCAL = PASS, all local features and OpenAPI schema included |
| Migration | PostgreSQL head `0002_classification_rules`, repeat upgrade/check passed; Room original v1 → v3 and reopen passed |
| Release API URL guard | Default public HTTPS domain passed; HTTP loopback override was rejected |
| Secret pattern scan and staged-file review | PASS; no `.env`, signing key, credentials file or build artifacts tracked |

Remaining Lint warnings concern newer dependencies/target SDK and a redundant adaptive-icon version folder qualifier. The tested version combination is retained. Runtime device coverage is Pixel 7 / Android 15; the minimum API 26 was checked by Lint, not a second device.

## Tests that matter

- Backend: authentication and Argon2, expired/missing JWT claims, account/transaction CRUD, foreign references and user isolation, money precision, invalid inputs, CSV parsing/preview/deduplication, SQL analytics, rules, AI-disabled/provider-failure fallback, seven Ask intents.
- Android JVM: `0.01`, `0.1`, `0.2`, large/invalid/negative money, aggregates beyond `Long`, CSV quoting/limits, calendar boundaries/leap February, user-rule priority and time-zone-independent dates.
- Device: real authenticated API/Room/session integration, registration and transaction UI workflow, offline cached UI, CSV confirmation/repeat, analytic totals/charts/month switching, classification rule/suggestion UI and Ask UI.
- Error-injection device test: HTTP 400/401/500, malformed JSON, timeout and disconnection. Each preserves the prior Room snapshot, resets loading/busy and shows a normal ViewModel error; 401 also marks login expired. Checked IO failures are resumed through a suspend continuation to model Retrofit faithfully. These are injected errors, not claims that the production server returned them.
- Room migration test builds a v1 database from the exported original schema, inserts fictional account/category/transaction data, opens v3, verifies IDs/amount/date/timestamps, validates new rules and foreign keys, then reopens and verifies persistence.

## Fixes made during acceptance

Transaction dates now preserve calendar days across time-zone changes. The migration preserves the date displayed in the device zone at upgrade, while cloud refresh restores the authoritative server date. Amount input matches Backend `NUMERIC(18,2)` limits. Blank names/descriptions and oversized login passwords are rejected. JWT claims are required.

The first Lint run found a Java date API requiring API 34 and three missing Debug network attributes. The migration now uses the API-26-compatible `Instant.atZone().toLocalDate()`; Debug domains explicitly exclude subdomains. The final clean build/Lint passed. Launcher artwork and explicit Android backup exclusions were added. No release signing secret or production write was performed.

## Scope

This review covers repository secrets, local isolation/privacy behavior and the accepted local workflows. It is not a penetration test or a claim of banking/regulatory certification. The final production security, protected database backups, public HTTPS and signed APK must be verified in PHASE 10–11. The beginner guide records actual commands, expected output and troubleshooting; its signed-release steps remain marked pending.
