# PHASE 7 — Smart classification

Verified locally on 2026-10-06 (Asia/Shanghai).

## Behavior

1. Enabled user rules match literal keywords without case sensitivity. Lower numeric priority wins, then lower rule ID. Income and expense rules are separate.
2. Built-in keywords select an owned category of the same type. One catalog, `backend/app/data/classification_keywords.json`, is packaged into both Backend and Android assets.
3. `AIClassificationProvider` defines the optional adapter contract. The shipped `NoAIProvider` is disabled and needs no credential or external network. Unmatched descriptions fall back to `其他` (or the first category of that type).

The Android Settings page shows **AI Enhancement Disabled** and opens rule management. Rules can be created, edited, disabled or deleted in either the local ledger or a cloud ledger. The transaction editor's auto-classify button suggests a category without saving a transaction; users can change it before saving.

CSV previews preserve a supplied valid category. When it is absent, they apply rules and keywords. Preview results must still be confirmed before database writes. A changed rule does not silently recategorize past transactions or an existing signed preview.

No external AI adapter ships in v1. The interface permits a later Backend-only adapter; it must validate returned category IDs, limit timeouts, protect secrets and document data sent to its provider. No external AI requests or paid API credentials were used. Provider exceptions/foreign category suggestions must fall back safely; a failing adapter is covered by tests. Setting an environment flag alone does not configure an adapter.

## Actual evidence

- Backend: **13 pytest tests passed**, including rule disable/delete, keyword/fallback, CSV explicit-category precedence, invalid inputs and cross-user rule access.
- Real Docker/PostgreSQL HTTP checks: user rules override keywords; another user cannot list/update/delete those rules; disabled AI fallback works. `BACKEND_LOCAL = PASS`.
- Alembic reached `0002_classification_rules`; repeated `upgrade head` succeeded and `alembic check` detected no new operations. The change only adds a rule table.
- Android JVM: **10 tests passed**. Debug APK and instrumentation APK built and were installed with ADB.
- `Phase7ClassificationUiTest`: **OK (1 test)**. It creates a cloud rule through Compose, checks the Room cache and shared local classifier against the real API, then verifies an editor suggestion with zero transaction writes.
- Room schema v2 adds rules through `MIGRATION_1_2`; no destructive migration is used. Full migration preservation testing belongs to PHASE 9.

`SMART_CLASSIFICATION_LOCAL = PASS`; `AI_DISABLED_FALLBACK = PASS`.
