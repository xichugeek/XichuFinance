"""Create local-only Docker secrets without displaying them or replacing existing secrets."""

import argparse
from pathlib import Path
import secrets


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ecr", action="store_true", help="Use Docker Official Images from AWS ECR Public")
parser.add_argument("--tuna", action="store_true", help="Use Tsinghua's public HTTPS PyPI mirror")
arguments = parser.parse_args()
root = Path(__file__).resolve().parents[1]
path = root / ".env"

if path.exists():
    print("Existing .env preserved. Keep its passwords when reusing a database volume.")
else:
    values = {
        "POSTGRES_PASSWORD": secrets.token_hex(24),
        "JWT_SECRET": secrets.token_hex(32),
    }
    if arguments.ecr:
        values.update({
            "POSTGRES_IMAGE": "public.ecr.aws/docker/library/postgres:17-alpine",
            "PYTHON_IMAGE": "public.ecr.aws/docker/library/python:3.12-slim",
        })
    if arguments.tuna:
        values["PIP_INDEX_URL"] = "https://pypi.tuna.tsinghua.edu.cn/simple"
    with path.open("x", encoding="utf-8", newline="\n") as file:
        for key, value in values.items():
            file.write(f"{key}={value}\n")
    print("Created .env with random secrets. Values were not displayed; .env must remain ignored by Git.")
