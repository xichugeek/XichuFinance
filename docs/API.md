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

For analytics endpoints, optional `month=YYYY-MM-DD` selects the month containing that date. Money values are decimal strings in JSON. Transaction creation accepts `account_id`, `category_id`, `type` (`income` or `expense`), positive `amount` with up to two fractional digits, `currency` (`CNY`), `description`, and `transaction_date` (`YYYY-MM-DD`). Referenced accounts and categories must belong to the logged-in user.

CSV preview/commit, smart classification, and Ask Finance endpoints are planned for later phases and are not advertised as available yet. The production HTTPS API is not deployed.
