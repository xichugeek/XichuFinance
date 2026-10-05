from tests.test_api import register_and_login


def test_all_supported_intents_precision_and_isolation(client):
    api, _ = client
    a, b = register_and_login(api, "ask-a@example.com"), register_and_login(api, "ask-b@example.com")
    cats = {(row["name"], row["type"]): row["id"] for row in api.get("/categories", headers=a).json()}
    account = api.post("/accounts", headers=a, json={"name": "虚构查询钱包", "kind": "cash"}).json()["id"]
    for amount, category, kind, day in (("0.01", "餐饮", "expense", "2026-10-01"), ("0.10", "餐饮", "expense", "2026-10-01"), ("0.20", "交通", "expense", "2026-10-02"), ("100000000.00", "工资", "income", "2026-10-01"), ("0.10", "交通", "expense", "2026-09-30")):
        assert api.post("/transactions", headers=a, json={"account_id": account, "category_id": cats[category, kind], "type": kind, "amount": amount, "description": "虚构查询数据", "transaction_date": day}).status_code == 201
    expected = [("这个月花了多少钱？", "expense", "0.31"), ("这个月钱主要花在哪？", "category_ranking", "0.20"), ("餐饮花了多少？", "category_expense", "0.11"), ("本月最大的五笔支出是什么？", "largest_expenses", "0.20"), ("这个月收入多少？", "income", "100000000.00"), ("这个月结余多少？", "balance", "99999999.69"), ("交通费比上个月高了吗？", "category_comparison", "0.10")]
    for question, intent, value in expected:
        result = api.post("/ai/ask", headers=a, json={"question": question, "month": "2026-10-01"}).json()
        assert result["intent"] == intent
        assert value in result["answer"]
        assert result["source"] == "database_template" and result["ai_enabled"] is False
    assert api.post("/ai/ask", headers=a, json={"question": "上个月支出多少", "month": "2026-10-01"}).json()["data"]["amount"] == "0.10"
    for question in ("本月花了多少", "本月餐饮花了多少", "交通费比上个月高了吗", "本月最大五笔", "本月分类排行"):
        result = api.post("/ai/ask", headers=b, json={"question": question, "month": "2026-10-01"}).json()
        assert "虚构查询数据" not in result["answer"]
        assert result["data"].get("amount", "0.00") == "0.00"
        assert result["data"].get("transactions", []) == [] and result["data"].get("categories", []) == []


def test_ask_invalid_and_unknown_intent(client):
    api, _ = client
    headers = register_and_login(api, "ask-invalid@example.com")
    assert api.post("/ai/ask", json={"question": "本月收入"}).status_code == 401
    for item in ({"question": ""}, {"question": "x" * 301}, {"question": "本月收入", "month": "2026-99-01"}):
        assert api.post("/ai/ask", headers=headers, json=item).status_code == 422
    result = api.post("/ai/ask", headers=headers, json={"question": "DROP TABLE transactions"}).json()
    assert result["intent"] == "unsupported" and result["data"] == {}
    assert api.get("/transactions", headers=headers).status_code == 200
