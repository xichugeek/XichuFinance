"""User-scoped database aggregates; decimal strings are the public money contract."""

from datetime import date, timedelta
from decimal import Decimal

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth import current_user
from app.db import get_db
from app.models import Category, Transaction, User
from app.schemas import TransactionOut

router = APIRouter(prefix="/analytics", tags=["Analytics"])


def month_bounds(day: date) -> tuple[date, date]:
    start = day.replace(day=1)
    end = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    return start, end


def month_filter(user_id: int, month: date):
    start, end = month_bounds(month)
    return (Transaction.user_id == user_id, Transaction.transaction_date >= start, Transaction.transaction_date < end)


def total(db: Session, user_id: int, month: date, kind: str, category_id: int | None = None) -> Decimal:
    statement = select(func.coalesce(func.sum(Transaction.amount), 0)).where(*month_filter(user_id, month), Transaction.type == kind)
    if category_id is not None:
        statement = statement.where(Transaction.category_id == category_id)
    return Decimal(db.scalar(statement)).quantize(Decimal("0.01"))


def summary(db: Session, user_id: int, month: date) -> dict:
    start, _ = month_bounds(month)
    income, expense = total(db, user_id, month, "income"), total(db, user_id, month, "expense")
    previous = total(db, user_id, start - timedelta(days=1), "expense")
    return {"month": start.isoformat(), "income": str(income), "expense": str(expense),
            "balance": str(income - expense), "previous_month_expense": str(previous), "expense_change": str(expense - previous)}


def category_totals(db: Session, user_id: int, month: date) -> list[dict]:
    amount = func.sum(Transaction.amount)
    statement = select(Category.id, Category.name, amount).select_from(Transaction).join(Category, Category.id == Transaction.category_id)
    statement = statement.where(*month_filter(user_id, month), Transaction.type == "expense", Category.user_id == user_id)
    statement = statement.group_by(Category.id, Category.name).order_by(amount.desc(), Category.id)
    return [{"category_id": key, "category": name, "amount": str(value.quantize(Decimal("0.01")))} for key, name, value in db.execute(statement)]


def largest(db: Session, user_id: int, month: date, limit: int = 5) -> list[Transaction]:
    return list(db.scalars(select(Transaction).where(*month_filter(user_id, month), Transaction.type == "expense")
                           .order_by(Transaction.amount.desc(), Transaction.transaction_date.desc(), Transaction.id.desc()).limit(limit)))


@router.get("/summary")
def analytics_summary(month: date | None = None, db: Session = Depends(get_db), user: User = Depends(current_user)) -> dict:
    return summary(db, user.id, month or date.today())


@router.get("/categories")
def analytics_categories(month: date | None = None, db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[dict]:
    return category_totals(db, user.id, month or date.today())


@router.get("/trend")
def analytics_trend(month: date | None = None, db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[dict]:
    statement = select(Transaction.transaction_date, func.sum(Transaction.amount)).where(
        *month_filter(user.id, month or date.today()), Transaction.type == "expense",
    ).group_by(Transaction.transaction_date).order_by(Transaction.transaction_date)
    return [{"date": day.isoformat(), "expense": str(amount.quantize(Decimal("0.01")))} for day, amount in db.execute(statement)]


@router.get("/largest", response_model=list[TransactionOut])
def analytics_largest(month: date | None = None, limit: int = Query(5, ge=1, le=20), db: Session = Depends(get_db), user: User = Depends(current_user)):
    return largest(db, user.id, month or date.today(), limit)
