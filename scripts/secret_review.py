"""Review Git-tracked files without displaying matched secret values.

This catches recognizable credentials and private-file paths, not every possible
secret. Always inspect changed configuration and files before committing.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "provider key": re.compile(r"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{30,}"),
    "GitHub token": re.compile(r"\b(?:ghp_|github_pat_)[A-Za-z0-9_]{30,}"),
    "JWT token": re.compile(r"\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{20,}"),
    "cloud access key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
}


def review():
    files = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    issues = []
    for name in filter(None, files):
        path = Path(name)
        if path.name != ".env.example" and (path.name == ".env" or path.name.startswith(".env.") or path.name in {"keystore.properties", "local.properties", "signing.credentials.json"} or path.suffix.lower() in {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".apk", ".aab"}):
            issues.append((name, "private file must not be tracked"))
            continue
        data = (ROOT / path).read_bytes()
        if b"\0" in data or path.suffix.lower() in {".png", ".jpg", ".jar"}:
            continue
        text = data.decode("utf-8", errors="replace")
        for label, pattern in PATTERNS.items():
            if pattern.search(text):
                issues.append((name, label))
    for name, issue in issues:
        print(f"FAIL: {name}: {issue} (value withheld)")
    if issues:
        raise SystemExit(1)
    print(f"SECRET_PATTERN_REVIEW = PASS ({len(list(filter(None, files)))} tracked files; manual review still required)")


if __name__ == "__main__":
    review()
