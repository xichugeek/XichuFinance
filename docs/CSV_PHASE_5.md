# PHASE 5 — CSV import acceptance

This is the historical record for this phase. Later production and Release results are in [current phase status](PHASE_STATUS.md).

Status: **CSV_IMPORT = PASS**, **CSV_DUPLICATE = PASS**, and **BACKEND_LOCAL = PASS** for local development acceptance. Production and final Release acceptance remain open.

## Actual checks

- Backend pytest: **10 passed**, including CSV headers/BOM/quoting, invalid dates/amounts/accounts/categories, file/row limits, preview without writes, commit/replay, normalized duplicate identities, cross-user commit rejection, invalid/expired signatures, and stale account references.
- `python scripts/validate_backend.py` against the running Docker/PostgreSQL API returned **BACKEND_LOCAL = PASS**. Three previewed rows produced zero writes; confirmation imported three; replay imported zero; a second preview reported three duplicates. Imported expense/income totals increased by exactly `19.00` / `6000.00`.
- Android Debug and test APKs built. **6 JVM unit tests** passed: 3 money tests and 3 CSV parser/input-limit tests.
- The dedicated `Phase5CsvUiTest` passed on Pixel_7 / Android 15. A real fixture file URI was read through ContentResolver, uploaded, and previewed in Compose. The test checked 2 valid / 1 error / 1 duplicate, zero writes before confirmation, 2 writes after tapping confirmation, and 0 valid / 3 duplicate on repeat. The system document-picker UI itself was not automated in this test.
- Alembic check on PostgreSQL found no new upgrade operations: existing transaction import-identity constraints support this feature without a schema change.

## Implementation

`backend/app/csv_import.py` handles the standard-format parser and preview/commit routes. Preview validates current-user account/category names and issues a user-bound signed token lasting 15 minutes. It does not insert transactions or retain the file. Commit checks the current user and references again, and PostgreSQL `ON CONFLICT DO NOTHING` protects duplicate identities. The token's audience prevents use as a login bearer token.

Android uses the document picker, bounded UTF-8 reading, structural parsing, and Backend value/ownership validation. Preview/result state stays in memory. Cloud import requires connectivity; Room is refreshed after a confirmed commit. Local-ledger data is never merged automatically.

See [CSV_FORMAT.md](CSV_FORMAT.md) for the guaranteed format, privacy, setup, duplicate limitations, and sample data.
