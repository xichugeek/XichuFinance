"""User rules, shared keywords, then an optional provider. No paid API is required."""
import json
from pathlib import Path
from typing import Protocol

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth import current_user
from app.db import get_db
from app.models import Category, ClassificationRule, User
from app.schemas import TransactionType

router = APIRouter(tags=["Classification"])
KEYWORDS = json.loads((Path(__file__).parent / "data" / "classification_keywords.json").read_text(encoding="utf-8"))


class AIClassificationProvider(Protocol):
    enabled: bool

    def choose(self, description: str, kind: str, categories: list[Category]) -> int | None: ...


class NoAIProvider:
    enabled = False

    def choose(self, description: str, kind: str, categories: list[Category]) -> int | None:
        return None


def get_provider() -> AIClassificationProvider:
    # v1 ships without an external adapter or credential requirement. An adapter
    # must be explicitly configured on the Backend before any external data flow.
    return NoAIProvider()


class ClassifyIn(BaseModel):
    description: str = Field(min_length=1, max_length=500)
    type: TransactionType


class RuleIn(BaseModel):
    keyword: str = Field(min_length=1, max_length=100)
    category_id: int
    type: TransactionType
    priority: int = Field(default=100, ge=0, le=1000)
    enabled: bool = True

    @field_validator("keyword")
    @classmethod
    def normalized_keyword(cls, value: str) -> str:
        value = value.strip().lower()
        if not value:
            raise ValueError("Keyword cannot be blank")
        return value


class RuleOut(RuleIn):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int


def load_categories(db: Session, user_id: int) -> list[Category]:
    return list(db.scalars(select(Category).where(Category.user_id == user_id).order_by(Category.id)))


def load_rules(db: Session, user_id: int) -> list[ClassificationRule]:
    return list(db.scalars(select(ClassificationRule).where(ClassificationRule.user_id == user_id)
                           .order_by(ClassificationRule.priority, ClassificationRule.id)))


def classify(description: str, kind: str, categories: list[Category], rules: list[ClassificationRule], provider: AIClassificationProvider) -> dict:
    candidates = {row.id: row for row in categories if row.type == kind}
    names = {row.name: row for row in candidates.values()}
    text = description.lower()
    chosen, source = None, "fallback"
    for rule in sorted(rules, key=lambda row: (row.priority, row.id)):
        if rule.enabled and rule.type == kind and rule.category_id in candidates and rule.keyword.lower() in text:
            chosen, source = candidates[rule.category_id], "rule"
            break
    if chosen is None:
        for entry in KEYWORDS:
            if entry["type"] == kind and entry["category"] in names and any(word.lower() in text for word in entry["keywords"]):
                chosen, source = names[entry["category"]], "keyword"
                break
    if chosen is None and provider.enabled:
        try:
            category_id = provider.choose(description, kind, list(candidates.values()))
            if category_id in candidates:
                chosen, source = candidates[category_id], "ai"
        except Exception:
            # Provider failures never prevent bookkeeping; never expose requests/keys.
            pass
    if chosen is None:
        chosen = names.get("其他") or next(iter(candidates.values()), None)
    if chosen is None:
        raise HTTPException(422, "Create a category for this transaction type first")
    return {"category_id": chosen.id, "category": chosen.name, "type": kind, "source": source,
            "ai_enabled": provider.enabled, "ai_status": "AI Enhancement Enabled" if provider.enabled else "AI Enhancement Disabled"}


@router.get("/ai/status")
def ai_status(provider: AIClassificationProvider = Depends(get_provider)):
    return {"ai_enabled": provider.enabled, "ai_status": "AI Enhancement Enabled" if provider.enabled else "AI Enhancement Disabled"}


@router.post("/ai/classify")
def classify_transaction(item: ClassifyIn, db: Session = Depends(get_db), user: User = Depends(current_user), provider: AIClassificationProvider = Depends(get_provider)):
    return classify(item.description, item.type, load_categories(db, user.id), load_rules(db, user.id), provider)


@router.get("/rules", response_model=list[RuleOut])
def list_rules(db: Session = Depends(get_db), user: User = Depends(current_user)):
    return load_rules(db, user.id)


def validate_category(db: Session, user_id: int, item: RuleIn):
    category = db.scalar(select(Category).where(Category.id == item.category_id, Category.user_id == user_id))
    if category is None:
        raise HTTPException(404, "Category not found")
    if category.type != item.type:
        raise HTTPException(422, "Category type does not match rule")


def save_rule(db: Session, rule: ClassificationRule):
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "Rule keyword already exists for this type") from None
    db.refresh(rule)
    return rule


def owned_rule(db: Session, user_id: int, rule_id: int):
    rule = db.scalar(select(ClassificationRule).where(ClassificationRule.id == rule_id, ClassificationRule.user_id == user_id))
    if rule is None:
        raise HTTPException(404, "Rule not found")
    return rule


@router.post("/rules", response_model=RuleOut, status_code=201)
def create_rule(item: RuleIn, db: Session = Depends(get_db), user: User = Depends(current_user)):
    validate_category(db, user.id, item)
    rule = ClassificationRule(user_id=user.id, **item.model_dump())
    db.add(rule)
    return save_rule(db, rule)


@router.put("/rules/{rule_id}", response_model=RuleOut)
def update_rule(rule_id: int, item: RuleIn, db: Session = Depends(get_db), user: User = Depends(current_user)):
    rule = owned_rule(db, user.id, rule_id)
    validate_category(db, user.id, item)
    for field, value in item.model_dump().items():
        setattr(rule, field, value)
    return save_rule(db, rule)


@router.delete("/rules/{rule_id}", status_code=204)
def delete_rule(rule_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    db.delete(owned_rule(db, user.id, rule_id))
    db.commit()
