"""Create private server secrets only after the production deployment is approved."""

import argparse
import os
from pathlib import Path
import re
import secrets


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--release-tag", required=True, help="The deployed Git commit ID")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{7,40}", args.release_tag):
        parser.error("release-tag must be a hexadecimal Git commit ID")
    if not args.output.is_absolute() or args.output.name != ".env.production":
        parser.error("output must be an absolute path ending in .env.production")
    if os.name != "posix":
        parser.error("Run this script on the approved Linux server")
    os.umask(0o077)
    values = {
        "POSTGRES_PASSWORD": secrets.token_hex(24),
        "JWT_SECRET": secrets.token_hex(32),
        "FINANCE_RELEASE_TAG": args.release_tag,
        "POSTGRES_IMAGE": "public.ecr.aws/docker/library/postgres:17.11-alpine",
        "PYTHON_IMAGE": "public.ecr.aws/docker/library/python:3.12-slim",
        "PIP_INDEX_URL": "https://pypi.org/simple",
    }
    # O_EXCL preserves an existing password when its database volume is reused.
    descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as file:
        for key, value in values.items():
            file.write(f"{key}={value}\n")
    print("Created private .env.production; secret values were not displayed.")


if __name__ == "__main__":
    main()
