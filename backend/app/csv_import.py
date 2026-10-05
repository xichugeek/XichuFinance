"""Standard CSV preview/commit. Preview never writes bookkeeping records."""

import csv
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import io
import json
import re

import jwt
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel, Field, ValidationError
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.orm import Session

from app.auth import current_user, token_secret
from app.db import get_db
from app.models import Account, Category, Transaction, User
from app.schemas import TransactionIn


router = APIRouter(prefix="/imports/csv", tags=["CSV import"])
MAX_BYTES = 512 * 1024
MAX_ROWS = 500
REQUIRED = {"date", "description", "amount", "type", "account"}
ALLOWED = REQUIRED | {"category"}


class CommitIn(BaseModel):
    preview_token: str = Field(min_length=1, max_length=4 * 1024 * 1024)


def fingerprint(user_id: int, item: dict) -> str:
    values = [user_id, item["transaction_date"], item["account_id"], item["type"],
              f"{Decimal(item['amount']):.2f}", item["description"], "CNY", "csv"]
    return hashlib.sha256(json.dumps(values, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()


def preview(data: bytes, db: Session, user_id: int) -> dict:
    if len(data) > MAX_BYTES:
        raise HTTPException(413, "CSV exceeds 512 KiB")
    try:
        decoded = data.decode("utf-8-sig")
    except UnicodeDecodeError:
        raise HTTPException(422, "CSV must be UTF-8; export as CSV UTF-8") from None
    if "\x00" in decoded:
        raise HTTPException(422, "CSV contains invalid null characters")
    reader = csv.reader(io.StringIO(decoded, newline=""), strict=True)
    try:
        headers = [value.strip() for value in next(reader)]
    except (StopIteration, csv.Error):
        raise HTTPException(422, "CSV header is missing or malformed") from None
    if len(set(headers)) != len(headers) or not REQUIRED.issubset(headers) or not set(headers).issubset(ALLOWED):
        raise HTTPException(422, "Expected date,description,amount,type,account and optional category")

    accounts = {row.name: row for row in db.scalars(select(Account).where(Account.user_id == user_id))}
    categories = {(row.name, row.type): row for row in db.scalars(select(Category).where(Category.user_id == user_id))}
    existing = set(db.scalars(select(Transaction.external_id).where(
        Transaction.user_id == user_id, Transaction.source == "csv", Transaction.external_id.is_not(None),
    )))
    seen = set()
    output = []
    accepted = []
    try:
        for fields in reader:
            if not fields or all(not field.strip() for field in fields):
                continue
            if len(output) >= MAX_ROWS:
                raise HTTPException(413, "CSV exceeds 500 data rows")
            row = {"row_number": reader.line_num, "status": "error", "message": "", "description": "", "amount": "", "type": "", "account": "", "category": ""}
            output.append(row)
            if len(fields) != len(headers):
                row["message"] = "列数与表头不一致"
                continue
            values = dict(zip(headers, (field.strip() for field in fields)))
            row.update({key: values[key][:limit] for key, limit in (("description", 500), ("amount", 64), ("type", 16), ("account", 100))})
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", values["date"]):
                row["message"] = "日期必须为 YYYY-MM-DD"
                continue
            account = accounts.get(values["account"])
            if account is None:
                row["message"] = "账户不存在，请先在账户页创建同名账户"
                continue
            category_name = values.get("category") or "其他"
            category = categories.get((category_name, values["type"]))
            if category is None:
                row["message"] = "分类不存在，或收入/支出类型不匹配"
                continue
            try:
                validated = TransactionIn(account_id=account.id, category_id=category.id,
                    transaction_date=date.fromisoformat(values["date"]), amount=values["amount"],
                    description=values["description"], type=values["type"])
            except (ValidationError, ValueError):
                row["message"] = "日期、类型、描述或金额无效；金额须为正数且最多两位小数"
                continue
            item = validated.model_dump(mode="json")
            item["amount"] = f"{validated.amount:.2f}"
            identity = fingerprint(user_id, item)
            row.update(amount=item["amount"], category=category_name)
            if identity in seen or identity in existing:
                row.update(status="duplicate", message="该 CSV 交易已存在或在文件中重复")
            else:
                row.update(status="valid", message="待确认导入")
                accepted.append(item)
            seen.add(identity)
    except csv.Error:
        raise HTTPException(422, "Malformed CSV quoting") from None
    now = datetime.now(timezone.utc)
    token = jwt.encode({"sub": str(user_id), "aud": "csv-preview", "purpose": "csv-preview",
                        "iat": now, "exp": now + timedelta(minutes=15), "rows": accepted}, token_secret(), algorithm="HS256")
    return {"preview_token": token, "total_rows": len(output), "valid_rows": len(accepted),
            "error_rows": sum(row["status"] == "error" for row in output),
            "duplicate_rows": sum(row["status"] == "duplicate" for row in output), "rows": output}


@router.post("/preview")
def preview_csv(file: UploadFile = File(...), db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    try:
        data = file.file.read(MAX_BYTES + 1)
    finally:
        file.file.close()
    return preview(data, db, user.id)


@router.post("/commit")
def commit_csv(request: CommitIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    try:
        payload = jwt.decode(request.preview_token, token_secret(), algorithms=["HS256"], audience="csv-preview")
        if payload.get("purpose") != "csv-preview" or not isinstance(payload.get("rows"), list) or len(payload["rows"]) > MAX_ROWS:
            raise ValueError("Invalid preview")
    except (jwt.PyJWTError, ValueError):
        raise HTTPException(400, "Preview expired or invalid; upload again") from None
    if payload.get("sub") != str(user.id):
        raise HTTPException(404, "Preview not found")
    accounts = set(db.scalars(select(Account.id).where(Account.user_id == user.id)))
    categories = {row.id: row.type for row in db.scalars(select(Category).where(Category.user_id == user.id))}
    try:
        rows = [TransactionIn.model_validate(row) for row in payload["rows"]]
    except ValidationError:
        raise HTTPException(400, "Preview is invalid; upload again") from None
    for row in rows:
        if row.account_id not in accounts or categories.get(row.category_id) != row.type:
            raise HTTPException(409, "Account/category changed; upload again")

    insert = pg_insert if db.bind.dialect.name == "postgresql" else sqlite_insert
    imported = 0
    for row in rows:
        item = row.model_dump()
        identity = fingerprint(user.id, row.model_dump(mode="json"))
        statement = insert(Transaction).values(user_id=user.id, source="csv", external_id=identity, **item)
        statement = statement.on_conflict_do_nothing(index_elements=["user_id", "source", "external_id"]).returning(Transaction.id)
        if db.scalar(statement) is not None:
            imported += 1
    db.commit()
    return {"imported": imported, "duplicates": len(rows) - imported}
