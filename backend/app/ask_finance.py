"""Limited Chinese finance intents -> owned SQL queries -> deterministic templates."""
from datetime import date, timedelta
from decimal import Decimal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.analytics import category_totals, largest, month_bounds, summary, total
from app.auth import current_user
from app.db import get_db
from app.models import Category, User

router = APIRouter(prefix="/ai", tags=["Ask Finance"])


class AskIn(BaseModel):
    question: str = Field(min_length=1, max_length=300)
    month: date | None = None


def answer(db: Session, user_id: int, item: AskIn) -> dict:
    text = "".join(item.question.lower().split())
    month, _ = month_bounds(item.month or date.today())
    comparing = any(word in text for word in ("比上", "比较", "高了", "低了", "变化"))
    if not comparing and any(word in text for word in ("上月", "上个月")):
        month, _ = month_bounds(month - timedelta(days=1))
    label = f"{month.year}年{month.month}月"
    categories = list(db.scalars(select(Category).where(Category.user_id == user_id, Category.type == "expense")))
    category = next((row for row in sorted(categories, key=lambda row: (-len(row.name), row.id)) if row.name in text), None)
    intent, data = "unsupported", {}
    message = "可以问月支出、收入、结余、分类排行、某分类金额、最大五笔支出，或某分类与上月的比较。"
    if comparing and category:
        current = total(db, user_id, month, "expense", category.id)
        previous = total(db, user_id, month - timedelta(days=1), "expense", category.id)
        change = current - previous
        direction = "增加" if change > 0 else "减少" if change < 0 else "持平"
        intent = "category_comparison"
        data = {"category": category.name, "current": str(current), "previous": str(previous), "change": str(change)}
        message = f"{label}{category.name}支出 {current:.2f} 元，上月 {previous:.2f} 元，{direction}" + (f" {abs(change):.2f} 元。" if change else "。")
    elif any(word in text for word in ("五笔", "5笔")) or ("笔" in text and any(word in text for word in ("最大", "最高"))):
        intent = "largest_expenses"
        rows = largest(db, user_id, month, 5)
        data = {"transactions": [{"id": row.id, "description": row.description, "amount": f"{row.amount:.2f}", "date": row.transaction_date.isoformat()} for row in rows]}
        message = f"{label}最大的 {len(rows)} 笔支出：\n" + "\n".join(f"{index}. {row.transaction_date} · {row.description} · {row.amount:.2f} 元" for index, row in enumerate(rows, 1)) if rows else f"{label}暂无支出。"
    elif any(word in text for word in ("主要", "花在哪", "排行", "占比")):
        intent = "category_ranking"
        rows = category_totals(db, user_id, month)
        data = {"categories": rows}
        message = f"{label}支出分类排行：\n" + "\n".join(f"{row['category']}：{row['amount']} 元" for row in rows) if rows else f"{label}暂无支出。"
    elif any(word in text for word in ("收入", "赚了")):
        intent = "income"
        value = total(db, user_id, month, "income")
        data, message = {"amount": str(value)}, f"{label}收入 {value:.2f} 元。"
    elif any(word in text for word in ("结余", "剩下")):
        intent = "balance"
        data = summary(db, user_id, month)
        message = f"{label}收入 {data['income']} 元，支出 {data['expense']} 元，结余 {data['balance']} 元。"
    elif category and any(word in text for word in ("花", "多少", "支出", "费")):
        intent = "category_expense"
        value = total(db, user_id, month, "expense", category.id)
        data, message = {"category": category.name, "amount": str(value)}, f"{label}{category.name}支出 {value:.2f} 元。"
    elif any(word in text for word in ("花", "支出", "消费", "开销")) and not comparing:
        intent = "expense"
        value = total(db, user_id, month, "expense")
        data, message = {"amount": str(value)}, f"{label}支出 {value:.2f} 元。"
    return {"question": item.question, "month": month.isoformat(), "intent": intent, "data": data, "answer": message,
            "ai_enabled": False, "ai_status": "AI Enhancement Disabled", "source": "database_template"}


@router.post("/ask")
def ask_finance(item: AskIn, db: Session = Depends(get_db), user: User = Depends(current_user)):
    return answer(db, user.id, item)
