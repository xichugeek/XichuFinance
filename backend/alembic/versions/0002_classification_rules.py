"""Add user-owned literal classification rules without changing ledger records."""
from alembic import op
import sqlalchemy as sa

revision = "0002_classification_rules"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("classification_rules",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("category_id", sa.Integer(), sa.ForeignKey("categories.id", ondelete="RESTRICT"), nullable=False),
        sa.Column("type", sa.String(10), nullable=False),
        sa.Column("keyword", sa.String(100), nullable=False),
        sa.Column("priority", sa.Integer(), nullable=False),
        sa.Column("enabled", sa.Boolean(), nullable=False),
        sa.UniqueConstraint("user_id", "type", "keyword", name="uq_rule_user_type_keyword"))
    op.create_index("ix_classification_rules_user_id", "classification_rules", ["user_id"])
    op.create_index("ix_classification_rules_category_id", "classification_rules", ["category_id"])


def downgrade():
    op.drop_table("classification_rules")
