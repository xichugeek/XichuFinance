from collections import defaultdict
from datetime import date, timedelta
from decimal import Decimal

from fastapi import Depends, FastAPI, HTTPException, status
from sqlalchemy import select, text
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from app.auth import create_access_token, current_user, hash_password, verify_password
from app.db import get_db
from app.csv_import import router as csv_router
from app.models import Account, Category, Transaction, User
from app.schemas import (
    AccountIn,
    AccountOut,
    CategoryIn,
    CategoryOut,
    LoginIn,
    RegisterIn,
    TokenOut,
    TransactionIn,
    TransactionOut,
    UserOut,
)


app = FastAPI(title="Xichu Finance API", version="0.1.0")
app.include_router(csv_router)

DEFAULT_CATEGORIES = {
    "expense": ("餐饮", "交通", "购物", "住房", "娱乐", "医疗", "教育", "通讯", "旅行", "其他"),
    "income": ("工资", "奖金", "投资", "其他"),
}


def save_or_conflict(db: Session, message: str) -> None:
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail=message) from None


def owned_account(db: Session, user_id: int, account_id: int) -> Account:
    account = db.scalar(select(Account).where(Account.id == account_id, Account.user_id == user_id))
    if account is None:
        raise HTTPException(status_code=404, detail="Account not found")
    return account


def owned_category(db: Session, user_id: int, category_id: int) -> Category:
    category = db.scalar(select(Category).where(Category.id == category_id, Category.user_id == user_id))
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    return category


def owned_transaction(db: Session, user_id: int, transaction_id: int) -> Transaction:
    transaction = db.scalar(select(Transaction).where(Transaction.id == transaction_id, Transaction.user_id == user_id))
    if transaction is None:
        raise HTTPException(status_code=404, detail="Transaction not found")
    return transaction


def validate_transaction_refs(db: Session, user_id: int, item: TransactionIn) -> None:
    owned_account(db, user_id, item.account_id)
    category = owned_category(db, user_id, item.category_id)
    if category.type != item.type:
        raise HTTPException(status_code=422, detail="Category type does not match transaction type")


@app.get("/health")
def health(db: Session = Depends(get_db)) -> dict[str, str]:
    try:
        db.execute(text("SELECT 1"))
    except SQLAlchemyError:
        raise HTTPException(status_code=503, detail="Database unavailable") from None
    return {"status": "healthy"}


@app.post("/auth/register", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def register(item: RegisterIn, db: Session = Depends(get_db)) -> User:
    email = str(item.email).lower()
    if db.scalar(select(User.id).where(User.email == email)) is not None:
        raise HTTPException(status_code=409, detail="Email already registered")
    user = User(email=email, password_hash=hash_password(item.password))
    db.add(user)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Email already registered") from None
    for category_type, names in DEFAULT_CATEGORIES.items():
        for name in names:
            db.add(Category(user_id=user.id, name=name, type=category_type))
    save_or_conflict(db, "Email already registered")
    db.refresh(user)
    return user


@app.post("/auth/login", response_model=TokenOut)
def login(item: LoginIn, db: Session = Depends(get_db)) -> TokenOut:
    user = db.scalar(select(User).where(User.email == str(item.email).lower()))
    if user is None or not verify_password(item.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    return TokenOut(access_token=create_access_token(user.id))


@app.get("/me", response_model=UserOut)
def me(user: User = Depends(current_user)) -> User:
    return user


@app.get("/accounts", response_model=list[AccountOut])
def list_accounts(db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[Account]:
    return list(db.scalars(select(Account).where(Account.user_id == user.id).order_by(Account.id)))


@app.post("/accounts", response_model=AccountOut, status_code=status.HTTP_201_CREATED)
def create_account(item: AccountIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> Account:
    account = Account(user_id=user.id, **item.model_dump())
    db.add(account)
    save_or_conflict(db, "Account name already exists")
    db.refresh(account)
    return account


@app.put("/accounts/{account_id}", response_model=AccountOut)
def update_account(account_id: int, item: AccountIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> Account:
    account = owned_account(db, user.id, account_id)
    for field, value in item.model_dump().items():
        setattr(account, field, value)
    save_or_conflict(db, "Account name already exists")
    db.refresh(account)
    return account


@app.delete("/accounts/{account_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_account(account_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)) -> None:
    account = owned_account(db, user.id, account_id)
    if db.scalar(select(Transaction.id).where(Transaction.user_id == user.id, Transaction.account_id == account_id).limit(1)):
        raise HTTPException(status_code=409, detail="Account has transactions")
    db.delete(account)
    save_or_conflict(db, "Account has transactions")


@app.get("/categories", response_model=list[CategoryOut])
def list_categories(db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[Category]:
    return list(db.scalars(select(Category).where(Category.user_id == user.id).order_by(Category.type, Category.id)))


@app.post("/categories", response_model=CategoryOut, status_code=status.HTTP_201_CREATED)
def create_category(item: CategoryIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> Category:
    category = Category(user_id=user.id, **item.model_dump())
    db.add(category)
    save_or_conflict(db, "Category already exists")
    db.refresh(category)
    return category


@app.get("/transactions", response_model=list[TransactionOut])
def list_transactions(db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[Transaction]:
    return list(db.scalars(select(Transaction).where(Transaction.user_id == user.id).order_by(Transaction.transaction_date.desc(), Transaction.id.desc())))


@app.post("/transactions", response_model=TransactionOut, status_code=status.HTTP_201_CREATED)
def create_transaction(item: TransactionIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> Transaction:
    validate_transaction_refs(db, user.id, item)
    transaction = Transaction(user_id=user.id, **item.model_dump())
    db.add(transaction)
    save_or_conflict(db, "Transaction could not be saved")
    db.refresh(transaction)
    return transaction


@app.get("/transactions/{transaction_id}", response_model=TransactionOut)
def get_transaction(transaction_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)) -> Transaction:
    return owned_transaction(db, user.id, transaction_id)


@app.put("/transactions/{transaction_id}", response_model=TransactionOut)
def update_transaction(transaction_id: int, item: TransactionIn, db: Session = Depends(get_db), user: User = Depends(current_user)) -> Transaction:
    transaction = owned_transaction(db, user.id, transaction_id)
    validate_transaction_refs(db, user.id, item)
    for field, value in item.model_dump().items():
        setattr(transaction, field, value)
    save_or_conflict(db, "Transaction could not be saved")
    db.refresh(transaction)
    return transaction


@app.delete("/transactions/{transaction_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_transaction(transaction_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)) -> None:
    transaction = owned_transaction(db, user.id, transaction_id)
    db.delete(transaction)
    db.commit()


def month_bounds(day: date) -> tuple[date, date]:
    start = day.replace(day=1)
    end = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    return start, end


def month_transactions(db: Session, user_id: int, month: date) -> list[Transaction]:
    start, end = month_bounds(month)
    return list(db.scalars(select(Transaction).where(
        Transaction.user_id == user_id,
        Transaction.transaction_date >= start,
        Transaction.transaction_date < end,
    )))


@app.get("/analytics/summary")
def analytics_summary(month: date | None = None, db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    month = month or date.today()
    start, _ = month_bounds(month)
    previous = start - timedelta(days=1)
    items = month_transactions(db, user.id, month)
    prev_items = month_transactions(db, user.id, previous)
    income = sum((row.amount for row in items if row.type == "income"), Decimal("0.00"))
    expense = sum((row.amount for row in items if row.type == "expense"), Decimal("0.00"))
    prev_expense = sum((row.amount for row in prev_items if row.type == "expense"), Decimal("0.00"))
    return {
        "month": start.isoformat(),
        "income": str(income),
        "expense": str(expense),
        "balance": str(income - expense),
        "previous_month_expense": str(prev_expense),
        "expense_change": str(expense - prev_expense),
    }


@app.get("/analytics/categories")
def analytics_categories(month: date | None = None, db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[dict]:
    items = month_transactions(db, user.id, month or date.today())
    names = {category.id: category.name for category in db.scalars(select(Category).where(Category.user_id == user.id))}
    totals: dict[int, Decimal] = defaultdict(lambda: Decimal("0.00"))
    for row in items:
        if row.type == "expense":
            totals[row.category_id] += row.amount
    return [
        {"category_id": category_id, "category": names[category_id], "amount": str(amount)}
        for category_id, amount in sorted(totals.items(), key=lambda value: value[1], reverse=True)
    ]


@app.get("/analytics/trend")
def analytics_trend(month: date | None = None, db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[dict]:
    items = month_transactions(db, user.id, month or date.today())
    totals: dict[date, Decimal] = defaultdict(lambda: Decimal("0.00"))
    for row in items:
        if row.type == "expense":
            totals[row.transaction_date] += row.amount
    return [{"date": day.isoformat(), "expense": str(amount)} for day, amount in sorted(totals.items())]
