from tests.test_api import register_and_login


def test_database_month_boundaries_categories_trend_largest_and_isolation(client):
    api, _ = client
    a, b = register_and_login(api, "analytics-a@example.com"), register_and_login(api, "analytics-b@example.com")
    account = api.post("/accounts", headers=a, json={"name": "Fictional cash", "kind": "cash"}).json()["id"]
    categories = api.get("/categories", headers=a).json()
    food = next(row["id"] for row in categories if row["name"] == "餐饮")
    transport = next(row["id"] for row in categories if row["name"] == "交通")
    salary = next(row["id"] for row in categories if row["name"] == "工资")
    for date, amount, kind, category in (("2026-09-30", "25.00", "expense", food), ("2026-10-01", "0.01", "expense", food),
        ("2026-10-01", "0.10", "expense", food), ("2026-10-02", "0.20", "expense", transport),
        ("2026-10-02", "100000000.00", "income", salary), ("2026-11-01", "50.00", "expense", food)):
        assert api.post("/transactions", headers=a, json={"account_id": account, "category_id": category, "type": kind,
            "amount": amount, "description": "Fictional month test", "transaction_date": date}).status_code == 201
    summary = api.get("/analytics/summary?month=2026-10-01", headers=a).json()
    assert summary == {"month": "2026-10-01", "income": "100000000.00", "expense": "0.31", "balance": "99999999.69", "previous_month_expense": "25.00", "expense_change": "-24.69"}
    ranked = api.get("/analytics/categories?month=2026-10-01", headers=a).json()
    assert [(row["category"], row["amount"]) for row in ranked] == [("交通", "0.20"), ("餐饮", "0.11")]
    assert api.get("/analytics/trend?month=2026-10-01", headers=a).json() == [{"date": "2026-10-01", "expense": "0.11"}, {"date": "2026-10-02", "expense": "0.20"}]
    assert [row["amount"] for row in api.get("/analytics/largest?month=2026-10-01", headers=a).json()] == ["0.20", "0.10", "0.01"]
    for path in ("categories", "trend", "largest"):
        assert api.get(f"/analytics/{path}?month=2026-10-01", headers=b).json() == []
    assert api.get("/analytics/summary?month=2026-10-01", headers=b).json()["expense"] == "0.00"
    assert api.get("/analytics/largest?limit=1000", headers=a).status_code == 422
