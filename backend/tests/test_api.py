from datetime import date

from sqlalchemy.exc import SQLAlchemyError

from app.auth import verify_password
from app.db import get_db
from app.models import User


def register_and_login(client, email: str):
    response = client.post("/auth/register", json={"email": email, "password": "correct-horse-123"})
    assert response.status_code == 201, response.text
    login = client.post("/auth/login", json={"email": email, "password": "correct-horse-123"})
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


def test_health_and_authentication(client):
    api, factory = client
    assert api.get("/health").json() == {"status": "healthy"}
    assert api.get("/docs").status_code == 200
    assert api.get("/me").status_code == 401
    headers = register_and_login(api, "A@example.com")
    assert api.get("/me", headers=headers).json()["email"] == "a@example.com"
    assert api.post("/auth/register", json={"email": "a@example.com", "password": "correct-horse-123"}).status_code == 409
    assert api.post("/auth/login", json={"email": "a@example.com", "password": "wrong"}).status_code == 401
    with factory() as db:
        user = db.query(User).filter_by(email="a@example.com").one()
        assert user.password_hash != "correct-horse-123"
        assert user.password_hash.startswith("$argon2")
        assert verify_password("correct-horse-123", user.password_hash)


def test_health_reports_unavailable_database(client):
    api, _ = client

    class UnavailableDatabase:
        def execute(self, _statement):
            raise SQLAlchemyError("connection failed")

    original = api.app.dependency_overrides[get_db]
    api.app.dependency_overrides[get_db] = lambda: UnavailableDatabase()
    try:
        response = api.get("/health")
        assert response.status_code == 503
        assert response.json()["detail"] == "Database unavailable"
    finally:
        api.app.dependency_overrides[get_db] = original


def test_accounts_transactions_and_analytics(client):
    api, _ = client
    headers = register_and_login(api, "user@example.com")
    account = api.post("/accounts", headers=headers, json={"name": "现金", "kind": "cash"})
    assert account.status_code == 201, account.text
    account_id = account.json()["id"]
    assert len(api.get("/accounts", headers=headers).json()) == 1
    category = next(row for row in api.get("/categories", headers=headers).json() if row["name"] == "餐饮")
    transaction = {
        "account_id": account_id,
        "category_id": category["id"],
        "type": "expense",
        "amount": "0.10",
        "currency": "CNY",
        "description": "早餐",
        "transaction_date": date.today().isoformat(),
    }
    created = api.post("/transactions", headers=headers, json=transaction)
    assert created.status_code == 201, created.text
    transaction_id = created.json()["id"]
    assert created.json()["amount"] == "0.10"
    assert api.get(f"/transactions/{transaction_id}", headers=headers).status_code == 200
    assert api.delete(f"/accounts/{account_id}", headers=headers).status_code == 409

    transaction["amount"] = "0.20"
    updated = api.put(f"/transactions/{transaction_id}", headers=headers, json=transaction)
    assert updated.status_code == 200
    assert updated.json()["amount"] == "0.20"
    summary = api.get("/analytics/summary", headers=headers).json()
    assert summary["expense"] == "0.20"
    assert summary["balance"] == "-0.20"
    assert api.get("/analytics/categories", headers=headers).json()[0]["amount"] == "0.20"
    assert api.get("/analytics/trend", headers=headers).json()[0]["expense"] == "0.20"

    assert api.delete(f"/transactions/{transaction_id}", headers=headers).status_code == 204
    assert api.get(f"/transactions/{transaction_id}", headers=headers).status_code == 404
    assert api.delete(f"/accounts/{account_id}", headers=headers).status_code == 204


def test_cross_user_isolation(client):
    api, _ = client
    a = register_and_login(api, "a@example.com")
    b = register_and_login(api, "b@example.com")
    account_a = api.post("/accounts", headers=a, json={"name": "A cash", "kind": "cash"}).json()["id"]
    category_a = next(row for row in api.get("/categories", headers=a).json() if row["name"] == "餐饮")["id"]
    item = {
        "account_id": account_a,
        "category_id": category_a,
        "type": "expense",
        "amount": "25.00",
        "description": "private",
        "transaction_date": date.today().isoformat(),
    }
    tx_a = api.post("/transactions", headers=a, json=item).json()["id"]
    assert api.get("/accounts", headers=b).json() == []
    assert api.get("/transactions", headers=b).json() == []
    assert api.get(f"/transactions/{tx_a}", headers=b).status_code == 404
    assert api.put(f"/transactions/{tx_a}", headers=b, json=item).status_code == 404
    assert api.delete(f"/transactions/{tx_a}", headers=b).status_code == 404
    assert api.put(f"/accounts/{account_a}", headers=b, json={"name": "stolen", "kind": "cash"}).status_code == 404
    assert api.delete(f"/accounts/{account_a}", headers=b).status_code == 404
    assert api.post("/transactions", headers=b, json=item).status_code == 404
    assert api.get("/categories", headers=b).json() != api.get("/categories", headers=a).json()
    assert api.get("/analytics/summary", headers=b).json()["expense"] == "0.00"
    assert api.get(f"/transactions/{tx_a}", headers=a).status_code == 200


def test_invalid_money_and_category_type(client):
    api, _ = client
    headers = register_and_login(api, "user@example.com")
    account_id = api.post("/accounts", headers=headers, json={"name": "现金", "kind": "cash"}).json()["id"]
    income_category = next(row for row in api.get("/categories", headers=headers).json() if row["name"] == "工资")["id"]
    item = {
        "account_id": account_id,
        "category_id": income_category,
        "type": "expense",
        "amount": "1.00",
        "description": "bad",
        "transaction_date": date.today().isoformat(),
    }
    assert api.post("/transactions", headers=headers, json=item).status_code == 422
    item["type"] = "income"
    for amount in ("0", "-1", "1.001"):
        item["amount"] = amount
        assert api.post("/transactions", headers=headers, json=item).status_code == 422


def test_decimal_money_totals_exactly(client):
    api, _ = client
    headers = register_and_login(api, "money@example.com")
    account_id = api.post("/accounts", headers=headers, json={"name": "现金", "kind": "cash"}).json()["id"]
    categories = api.get("/categories", headers=headers).json()
    expense_category = next(row["id"] for row in categories if row["name"] == "餐饮")
    income_category = next(row["id"] for row in categories if row["name"] == "工资")
    for amount, transaction_type, category_id in (
        ("0.01", "expense", expense_category),
        ("0.10", "expense", expense_category),
        ("0.20", "expense", expense_category),
        ("100000000.00", "income", income_category),
    ):
        result = api.post("/transactions", headers=headers, json={
            "account_id": account_id,
            "category_id": category_id,
            "type": transaction_type,
            "amount": amount,
            "description": "precision check",
            "transaction_date": date.today().isoformat(),
        })
        assert result.status_code == 201, result.text
    summary = api.get("/analytics/summary", headers=headers).json()
    assert summary["expense"] == "0.31"
    assert summary["income"] == "100000000.00"
    assert summary["balance"] == "99999999.69"
