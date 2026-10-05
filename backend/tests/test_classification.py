from app.classification import get_provider
from tests.test_api import register_and_login
from tests.test_csv import upload


def test_rules_keywords_fallback_and_csv(client):
    api, _ = client
    headers = register_and_login(api, "classify@example.com")
    cats = {(row["name"], row["type"]): row["id"] for row in api.get("/categories", headers=headers).json()}
    def classify(text, kind="expense"):
        response = api.post("/ai/classify", headers=headers, json={"description": text, "type": kind})
        assert response.status_code == 200
        assert response.json()["ai_enabled"] is False
        return response.json()
    assert classify("虚构咖啡")["category"] == "餐饮"
    assert classify("地铁")["category"] == "交通"
    assert classify("SALARY", "income")["category"] == "工资"
    assert classify("未知商户")["category"] == "其他"
    assert api.get("/ai/status").json()["ai_status"] == "AI Enhancement Disabled"
    item = {"keyword": " 咖啡 ", "category_id": cats["娱乐", "expense"], "type": "expense", "priority": 50}
    response = api.post("/rules", headers=headers, json=item)
    assert response.status_code == 201
    rule = response.json()
    assert rule["keyword"] == "咖啡"
    assert classify("虚构咖啡")["source"] == "rule"
    assert classify("虚构咖啡")["category"] == "娱乐"
    assert api.post("/rules", headers=headers, json=item).status_code == 409
    assert api.put(f"/rules/{rule['id']}", headers=headers, json=dict(item, enabled=False)).status_code == 200
    assert classify("虚构咖啡")["source"] == "keyword"
    assert api.put(f"/rules/{rule['id']}", headers=headers, json=item).status_code == 200
    api.post("/accounts", headers=headers, json={"name": "虚构钱包", "kind": "cash"})
    preview = upload(api, headers, "date,description,amount,type,account,category\n2026-10-01,咖啡,1.00,expense,虚构钱包,\n2026-10-01,咖啡,2.00,expense,虚构钱包,餐饮\n").json()
    assert [row["category"] for row in preview["rows"]] == ["娱乐", "餐饮"]
    assert api.get("/transactions", headers=headers).json() == []
    assert api.post("/imports/csv/commit", headers=headers, json={"preview_token": preview["preview_token"]}).json()["imported"] == 2
    assert api.delete(f"/rules/{rule['id']}", headers=headers).status_code == 204
    assert classify("咖啡")["category"] == "餐饮"


def test_rule_isolation_input_and_optional_provider_failure(client):
    api, _ = client
    a, b = register_and_login(api, "rule-a@example.com"), register_and_login(api, "rule-b@example.com")
    cats = api.get("/categories", headers=a).json()
    expense = next(row["id"] for row in cats if row["type"] == "expense")
    income = next(row["id"] for row in cats if row["type"] == "income")
    item = {"keyword": "custom", "category_id": expense, "type": "expense"}
    rule = api.post("/rules", headers=a, json=item).json()
    assert api.get("/rules", headers=b).json() == []
    assert api.put(f"/rules/{rule['id']}", headers=b, json=item).status_code == 404
    assert api.delete(f"/rules/{rule['id']}", headers=b).status_code == 404
    assert api.post("/rules", headers=b, json=item).status_code == 404
    for invalid in (dict(item, keyword="  "), dict(item, priority=-1), dict(item, category_id=income)):
        assert api.post("/rules", headers=a, json=invalid).status_code == 422
    assert api.post("/ai/classify", json={"description": "咖啡", "type": "expense"}).status_code == 401
    assert api.post("/ai/classify", headers=a, json={"description": "", "type": "expense"}).status_code == 422
    class FailedProvider:
        enabled = True
        def choose(self, *_):
            raise TimeoutError("provider unavailable")
    api.app.dependency_overrides[get_provider] = lambda: FailedProvider()
    try:
        result = api.post("/ai/classify", headers=a, json={"description": "unmatched", "type": "expense"})
        assert result.status_code == 200 and result.json()["source"] == "fallback"
    finally:
        api.app.dependency_overrides.pop(get_provider, None)
