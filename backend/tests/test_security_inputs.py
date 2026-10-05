from datetime import datetime, timedelta, timezone
import jwt

from app.auth import token_secret
from tests.test_api import register_and_login


def test_missing_expired_claims_and_blank_inputs(client):
    api, _ = client
    headers = register_and_login(api, "claims@example.com")
    user = api.get("/me", headers=headers).json()
    now = datetime.now(timezone.utc)
    for payload in ({"sub": str(user["id"])}, {"sub": str(user["id"]), "iat": now - timedelta(days=2), "exp": now - timedelta(days=1)}):
        token = jwt.encode(payload, token_secret(), algorithm="HS256")
        assert api.get("/me", headers={"Authorization": f"Bearer {token}"}).status_code == 401
    assert api.post("/auth/login", json={"email": user["email"], "password": "x" * 129}).status_code == 422
    assert api.post("/accounts", headers=headers, json={"name": "   ", "kind": "cash"}).status_code == 422
    assert api.post("/categories", headers=headers, json={"name": "   ", "type": "expense"}).status_code == 422
