from datetime import datetime, timedelta, timezone
import jwt

from app.auth import token_secret
from app.csv_import import MAX_BYTES, MAX_ROWS
from tests.test_api import register_and_login


def fixture(api, email="csv@example.com"):
    headers = register_and_login(api, email)
    for name in ("支付宝", "银行卡", "微信"):
        assert api.post("/accounts", headers=headers, json={"name": name, "kind": "other"}).status_code == 201
    return headers


def upload(api, headers, text):
    return api.post("/imports/csv/preview", headers=headers, files={"file": ("sample.csv", text.encode("utf-8"), "text/csv")})


SAMPLE = "date,description,amount,type,account\n2026-09-01,早餐,15.00,expense,支付宝\n2026-09-01,工资,6000.00,income,银行卡\n2026-09-02,地铁,4.00,expense,微信\n"


def test_csv_preview_commit_and_repeat(client):
    api, _ = client
    headers = fixture(api)
    result = upload(api, headers, "\ufeff" + SAMPLE)
    assert result.status_code == 200, result.text
    preview = result.json()
    assert (preview["total_rows"], preview["valid_rows"], preview["error_rows"], preview["duplicate_rows"]) == (3, 3, 0, 0)
    assert api.get("/transactions", headers=headers).json() == []  # Preview never inserts.
    commit = {"preview_token": preview["preview_token"]}
    assert api.post("/imports/csv/commit", headers=headers, json=commit).json() == {"imported": 3, "duplicates": 0}
    assert api.post("/imports/csv/commit", headers=headers, json=commit).json() == {"imported": 0, "duplicates": 3}
    second = upload(api, headers, SAMPLE).json()
    assert second["valid_rows"] == 0 and second["duplicate_rows"] == 3
    assert len(api.get("/transactions", headers=headers).json()) == 3
    summary = api.get("/analytics/summary?month=2026-09-01", headers=headers).json()
    assert summary["income"] == "6000.00" and summary["expense"] == "19.00"
    assert all(row["source"] == "csv" for row in api.get("/transactions", headers=headers).json())


def test_csv_errors_duplicates_and_quotes(client):
    api, _ = client
    headers = fixture(api)
    text = 'date,description,amount,type,account,category\n2026-09-01,"早餐,咖啡",0.1,expense,支付宝,餐饮\n2026-09-01,"早餐,咖啡",0.10,expense,支付宝,餐饮\n2026-09-01,invalid,-1,expense,支付宝,餐饮\n2026-99-01,bad date,1.00,expense,支付宝,餐饮\n2026-09-01,unknown account,1.00,expense,陌生账户,餐饮\n2026-09-01,bad precision,1.001,expense,支付宝,餐饮\n2026-09-01,bad number,NaN,expense,支付宝,餐饮\n2026-09-01,bad category,1.00,income,支付宝,餐饮\n2026-09-01,missing fields\n'
    preview = upload(api, headers, text).json()
    assert preview["valid_rows"] == 1 and preview["duplicate_rows"] == 1 and preview["error_rows"] == 7
    assert preview["rows"][0]["description"] == "早餐,咖啡"
    assert preview["rows"][0]["amount"] == "0.10"
    assert api.post("/imports/csv/commit", headers=headers, json={"preview_token": preview["preview_token"]}).json()["imported"] == 1


def test_csv_isolation_signature_and_stale_preview(client):
    api, _ = client
    a, b = fixture(api, "a@example.com"), fixture(api, "b@example.com")
    preview = upload(api, a, SAMPLE).json()
    token = preview["preview_token"]
    assert api.post("/imports/csv/commit", headers=b, json={"preview_token": token}).status_code == 404
    assert api.get("/transactions", headers=b).json() == []
    assert api.get("/me", headers={"Authorization": f"Bearer {token}"}).status_code == 401
    pieces = token.split(".")
    pieces[1] = pieces[1][:-1] + ("A" if pieces[1][-1] != "A" else "B")
    assert api.post("/imports/csv/commit", headers=a, json={"preview_token": ".".join(pieces)}).status_code == 400
    payload = jwt.decode(token, token_secret(), algorithms=["HS256"], audience="csv-preview")
    payload["exp"] = datetime.now(timezone.utc) - timedelta(minutes=1)
    expired = jwt.encode(payload, token_secret(), algorithm="HS256")
    assert api.post("/imports/csv/commit", headers=a, json={"preview_token": expired}).status_code == 400
    account_id = api.get("/accounts", headers=a).json()[0]["id"]
    assert api.delete(f"/accounts/{account_id}", headers=a).status_code == 204
    assert api.post("/imports/csv/commit", headers=a, json={"preview_token": token}).status_code == 409
    assert api.get("/transactions", headers=a).json() == []


def test_csv_format_and_size_limits(client):
    api, _ = client
    headers = fixture(api)
    for text in ("", "foo,bar\n1,2", 'date,description,amount,type,account\n2026-09-01,"unclosed', "date,description,amount,type,account,account\n", SAMPLE.replace("早餐", "\x00")):
        assert upload(api, headers, text).status_code == 422
    assert upload(api, headers, "x" * (MAX_BYTES + 1)).status_code == 413
    many = "date,description,amount,type,account\n" + "2026-09-01,早餐,1.00,expense,支付宝\n" * (MAX_ROWS + 1)
    assert upload(api, headers, many).status_code == 413
    invalid_encoding = api.post("/imports/csv/preview", headers=headers, files={"file": ("bad.csv", b"\xff\xfe", "text/csv")})
    assert invalid_encoding.status_code == 422
    assert api.post("/imports/csv/preview", files={"file": ("sample.csv", SAMPLE)}).status_code == 401
