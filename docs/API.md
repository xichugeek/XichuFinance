# API (implementation in progress)

FastAPI serves interactive documentation at `/docs`. The local Compose configuration binds the API to `http://127.0.0.1:8000`. Send the login token as `Authorization: Bearer <access_token>` for all user data endpoints.

| Method | Path | Current behavior |
| --- | --- | --- |
| GET | `/health` | Returns `{"status":"healthy"}` when the database responds; otherwise HTTP 503 |
| POST | `/auth/register` | Creates an email/password user and default categories |
| POST | `/auth/login` | Returns a 12-hour access token |
| GET | `/me` | Returns the authenticated user |
| GET, POST | `/accounts` | Lists or creates only the current user's accounts |
| PUT, DELETE | `/accounts/{id}` | Updates or deletes an owned account; deletion rejects accounts with transactions |
| GET, POST | `/categories` | Lists or creates only the current user's categories |
| GET, POST | `/transactions` | Lists or creates only the current user's transactions |
| GET, PUT, DELETE | `/transactions/{id}` | Reads, updates, or deletes an owned transaction |
| GET | `/analytics/summary` | Month income, expense, balance, and previous month comparison |
| GET | `/analytics/categories` | Ranked expense totals by category |
| GET | `/analytics/trend` | Daily expense totals |
| GET | `/analytics/largest` | Largest expenses in the selected month; optional limit 1–20, default 5 |
| POST | `/imports/csv/preview` | Multipart `file`; validates owned references and returns counts, row messages, and a 15-minute preview token; no transaction writes |
| POST | `/imports/csv/commit` | JSON `preview_token`; confirms that user's valid rows and returns `imported` / `duplicates` counts |
| GET, POST | `/rules` | Lists or creates owned keyword rules; lower priority wins |
| PUT, DELETE | `/rules/{id}` | Updates or deletes an owned rule |
| GET | `/ai/status` | Reports optional enhancement status; shipped provider is disabled |
| POST | `/ai/classify` | JSON `description` / `type`; returns suggested owned category, source and AI status; no transaction writes |

For analytics endpoints, optional `month=YYYY-MM-DD` selects the month containing that date. Money values are decimal strings in JSON. Transaction creation accepts `account_id`, `category_id`, `type` (`income` or `expense`), positive `amount` with up to two fractional digits, `currency` (`CNY`), `description`, and `transaction_date` (`YYYY-MM-DD`). Referenced accounts and categories must belong to the logged-in user.

See [CSV format and privacy](CSV_FORMAT.md). Preview tokens contain private row data and are signed, not encrypted; do not log them. Invalid/expired previews return HTTP 400, another user's preview returns 404, and deleted/changed references return 409. See [classification behavior](CLASSIFICATION_PHASE_7.md). Ask Finance remains the next phase. The production HTTPS API is not deployed.
