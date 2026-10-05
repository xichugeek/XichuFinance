"""Validate the local Docker API with fictional users; never print credentials."""

import argparse
from datetime import date
import json
import secrets
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import uuid


def validate(base_url: str) -> None:
    parsed = urlsplit(base_url)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
        raise ValueError("This script only writes to a local HTTP API")
    base_url = base_url.rstrip("/")
    today = date.today().isoformat()
    month_query = f"?month={today}"

    def call(method, path, token=None, payload=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        body = json.dumps(payload).encode() if payload is not None else None
        request = Request(base_url + path, data=body, headers=headers, method=method)
        try:
            response = urlopen(request, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            status = response.status
            raw = response.read().decode("utf-8")
        if status != expected:
            raise AssertionError(f"{method} {path}: expected HTTP {expected}, received {status}")
        if not raw:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return raw

    def user(label):
        email = f"validation-{label}-{uuid.uuid4().hex}@example.com"
        password = secrets.token_urlsafe(24)
        credentials = {"email": email, "password": password}
        call("POST", "/auth/register", payload=credentials, expected=201)
        call("POST", "/auth/register", payload=credentials, expected=409)
        call("POST", "/auth/login", payload={"email": email, "password": "wrong-password"}, expected=401)
        token = call("POST", "/auth/login", payload=credentials)["access_token"]
        assert call("GET", "/me", token)["email"] == email
        return token

    assert call("GET", "/health") == {"status": "healthy"}
    assert "Swagger UI" in call("GET", "/docs")
    call("GET", "/me", expected=401)
    print("PASS: database health and API docs")
    token_a, token_b = user("a"), user("b")
    print("PASS: registration, login, authentication")

    account = call("POST", "/accounts", token_a, {"name": "Fictional validation cash", "kind": "cash"}, 201)
    account_id = account["id"]
    tx_ids = []
    try:
        call("PUT", f"/accounts/{account_id}", token_a, {"name": "Fictional renamed cash", "kind": "cash"})
        assert call("GET", "/accounts", token_a)[0]["id"] == account_id
        categories = call("GET", "/categories", token_a)
        expense_id = next(row["id"] for row in categories if row["name"] == "餐饮")
        income_id = next(row["id"] for row in categories if row["name"] == "工资")
        call("POST", "/categories", token_a, {"name": "Fictional validation category", "type": "expense"}, 201)
        item = {
            "account_id": account_id, "category_id": expense_id, "type": "expense",
            "amount": "0.01", "description": "Fictional precision check",
            "transaction_date": today,
        }
        tx_id = call("POST", "/transactions", token_a, item, 201)["id"]
        tx_ids.append(tx_id)
        assert call("GET", f"/transactions/{tx_id}", token_a)["amount"] == "0.01"
        item["amount"] = "0.10"
        assert call("PUT", f"/transactions/{tx_id}", token_a, item)["amount"] == "0.10"
        for amount, kind, category_id in (("0.20", "expense", expense_id), ("0.01", "expense", expense_id), ("100000000.00", "income", income_id)):
            extra = dict(item, amount=amount, type=kind, category_id=category_id)
            tx_ids.append(call("POST", "/transactions", token_a, extra, 201)["id"])
        call("DELETE", f"/accounts/{account_id}", token_a, expected=409)
        summary = call("GET", "/analytics/summary" + month_query, token_a)
        assert summary["expense"] == "0.31"
        assert summary["income"] == "100000000.00"
        assert summary["balance"] == "99999999.69"
        assert call("GET", "/analytics/categories" + month_query, token_a)[0]["amount"] == "0.31"
        assert call("GET", "/analytics/trend" + month_query, token_a)[0]["expense"] == "0.31"
        print("PASS: account/category operations, transaction read/update, decimal analytics")

        for method in ("GET", "PUT", "DELETE"):
            call(method, f"/transactions/{tx_id}", token_b, item if method == "PUT" else None, 404)
        call("PUT", f"/accounts/{account_id}", token_b, {"name": "unauthorized", "kind": "cash"}, 404)
        call("DELETE", f"/accounts/{account_id}", token_b, expected=404)
        call("POST", "/transactions", token_b, item, 404)
        assert call("GET", "/accounts", token_b) == []
        assert call("GET", "/transactions", token_b) == []
        assert call("GET", "/analytics/summary" + month_query, token_b)["expense"] == "0.00"
        assert call("GET", "/analytics/categories" + month_query, token_b) == []
        assert call("GET", "/analytics/trend" + month_query, token_b) == []
        print("PASS: cross-user read/write/delete and analytics isolation")

        for amount in ("0", "-1", "1.001", "NaN", "Infinity"):
            call("POST", "/transactions", token_a, dict(item, amount=amount), 422)
        call("DELETE", f"/transactions/{tx_id}", token_a, expected=204)
        tx_ids.remove(tx_id)
        call("GET", f"/transactions/{tx_id}", token_a, expected=404)
        print("PASS: invalid money and transaction deletion")
    finally:
        for tx_id in tx_ids:
            call("DELETE", f"/transactions/{tx_id}", token_a, expected=204)
        call("DELETE", f"/accounts/{account_id}", token_a, expected=204)
    print("LOCAL_API_CORE = PASS (CSV and later phases are not tested here)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    arguments = parser.parse_args()
    try:
        validate(arguments.base_url)
    except (AssertionError, URLError, ValueError, KeyError, StopIteration) as error:
        raise SystemExit(f"LOCAL_API_CORE = FAIL: {error}") from None
