from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field


MoneyValue = Decimal
TransactionType = Literal["income", "expense"]


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    email: EmailStr
    created_at: datetime


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


class AccountIn(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    kind: Literal["cash", "bank", "credit", "alipay", "wechat", "other"]
    opening_balance: MoneyValue = Field(default=Decimal("0.00"), max_digits=18, decimal_places=2)


class AccountOut(AccountIn):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int
    created_at: datetime
    updated_at: datetime


class CategoryIn(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    type: TransactionType


class CategoryOut(CategoryIn):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int


class TransactionIn(BaseModel):
    account_id: int
    category_id: int
    type: TransactionType
    amount: MoneyValue = Field(gt=0, max_digits=18, decimal_places=2)
    currency: Literal["CNY"] = "CNY"
    description: str = Field(min_length=1, max_length=500)
    transaction_date: date


class TransactionOut(TransactionIn):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int
    source: str
    external_id: str | None
    created_at: datetime
    updated_at: datetime
