from datetime import date

from tests.test_api import register_and_login


def test_category_rename_validation_deletion_and_ownership(client):
    api, _ = client
    owner = register_and_login(api, "category-owner@example.com")
    other = register_and_login(api, "category-other@example.com")
    category = api.post("/categories", headers=owner, json={"name": "虚构分类", "type": "expense"}).json()
    path = f"/categories/{category['id']}"
    renamed = {"name": " 虚构分类改名 ", "type": "expense"}
    assert api.put(path, json=renamed).status_code == 401
    assert api.delete(path).status_code == 401
    assert api.put(path, headers=other, json=renamed).status_code == 404
    assert api.delete(path, headers=other).status_code == 404
    for invalid in ({"name": "  ", "type": "expense"}, {"name": "x" * 101, "type": "expense"}, {"name": "虚构分类", "type": "income"}):
        assert api.put(path, headers=owner, json=invalid).status_code == 422
    assert api.put(path, headers=owner, json={"name": "餐饮", "type": "expense"}).status_code == 409
    response = api.put(path, headers=owner, json=renamed)
    assert response.status_code == 200
    assert response.json() == dict(category, name="虚构分类改名")
    assert api.delete(path, headers=owner).status_code == 204
    assert api.delete(path, headers=owner).status_code == 404
    assert api.put(path, headers=owner, json=renamed).status_code == 404
    assert all(row["id"] != category["id"] for row in api.get("/categories", headers=owner).json())


def test_category_rename_keeps_transaction_and_blocks_used_deletion(client):
    api, _ = client
    headers = register_and_login(api, "category-transaction@example.com")
    category = next(row for row in api.get("/categories", headers=headers).json() if row["name"] == "餐饮")
    path = f"/categories/{category['id']}"
    account = api.post("/accounts", headers=headers, json={"name": "虚构钱包", "kind": "cash"}).json()
    transaction = api.post("/transactions", headers=headers, json={
        "account_id": account["id"], "category_id": category["id"], "type": "expense",
        "amount": "0.10", "description": "虚构午餐", "transaction_date": date.today().isoformat(),
    }).json()
    assert api.put(path, headers=headers, json={"name": "一日三餐", "type": "expense"}).status_code == 200
    assert api.get(f"/transactions/{transaction['id']}", headers=headers).json() == transaction
    assert api.get("/analytics/categories", headers=headers).json()[0]["category"] == "一日三餐"
    assert api.delete(path, headers=headers).status_code == 409
    assert api.get("/analytics/summary", headers=headers).json()["expense"] == "0.10"
    assert api.delete(f"/transactions/{transaction['id']}", headers=headers).status_code == 204
    assert api.delete(path, headers=headers).status_code == 204


def test_category_rename_keeps_rules_and_disabled_rule_still_blocks_deletion(client):
    api, _ = client
    headers = register_and_login(api, "category-rule@example.com")
    category = api.post("/categories", headers=headers, json={"name": "虚构自选", "type": "expense"}).json()
    path = f"/categories/{category['id']}"
    item = {"keyword": "虚构专用", "category_id": category["id"], "type": "expense"}
    rule = api.post("/rules", headers=headers, json=item).json()
    assert api.put(path, headers=headers, json={"name": "改名后自选", "type": "expense"}).status_code == 200
    suggestion = api.post("/ai/classify", headers=headers, json={"description": "虚构专用早餐", "type": "expense"}).json()
    assert (suggestion["category_id"], suggestion["category"], suggestion["source"]) == (category["id"], "改名后自选", "rule")
    assert api.delete(path, headers=headers).status_code == 409
    assert api.put(f"/rules/{rule['id']}", headers=headers, json=dict(item, enabled=False)).status_code == 200
    assert api.delete(path, headers=headers).status_code == 409
    assert api.delete(f"/rules/{rule['id']}", headers=headers).status_code == 204
    assert api.delete(path, headers=headers).status_code == 204
