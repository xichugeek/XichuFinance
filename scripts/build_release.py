"""Build with an existing private keystore; never display signing credentials."""

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--credentials", type=Path, default=Path.home() / ".xichufinance/signing/signing.credentials.json")
    parser.add_argument("--instrumentation", action="store_true", help="Also build tests targeting the signed Release")
    parser.add_argument("--bundle", action="store_true", help="Also build an optional signed AAB")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    version_match = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', (root / "android/app/build.gradle.kts").read_text(encoding="utf-8"))
    if version_match is None:
        parser.error("Expected a numeric semantic versionName in app/build.gradle.kts")
    version = version_match.group(1)
    private_file = args.credentials.resolve()
    if private_file.is_relative_to(root):
        parser.error("Keep the credential file outside the repository")
    values = json.loads(private_file.read_text(encoding="utf-8"))
    names = {"FINANCE_KEYSTORE_FILE", "FINANCE_KEYSTORE_PASSWORD", "FINANCE_KEY_ALIAS", "FINANCE_KEY_PASSWORD"}
    if not names.issubset(values) or any(not isinstance(values[name], str) or not values[name] for name in names):
        parser.error("Private credentials must contain the four FINANCE_KEYSTORE/KEY environment variables")
    environment = os.environ.copy()
    environment.update({name: values[name] for name in names})
    unit_task = ":app:testReleaseUnitTest" if args.instrumentation else ":app:testDebugUnitTest"
    tasks = ["clean", unit_task, ":app:assembleRelease", ":app:lintRelease"]
    if args.instrumentation:
        tasks.extend([":app:assembleReleaseAndroidTest", "-PfinanceReleaseValidation=true"])
    if args.bundle:
        tasks.append(":app:bundleRelease")
    command = ["cmd.exe", "/d", "/c", "gradlew.bat"] if os.name == "nt" else ["./gradlew"]
    subprocess.run(command + ["--no-daemon"] + tasks, cwd=root / "android", env=environment, check=True)
    dist = root / "dist"
    dist.mkdir(exist_ok=True)
    artifacts = [(root / "android/app/build/outputs/apk/release/app-release.apk", dist / f"XichuFinance-v{version}.apk")]
    if args.bundle:
        artifacts.append((root / "android/app/build/outputs/bundle/release/app-release.aab", dist / f"XichuFinance-v{version}.aab"))
    for source, destination in artifacts:
        shutil.copyfile(source, destination)
        checksum = hashlib.sha256(destination.read_bytes()).hexdigest()
        destination.with_suffix(destination.suffix + ".sha256").write_text(f"{checksum}  {destination.name}\n", encoding="utf-8")
        print(f"RELEASE_BUILD_ARTIFACT={destination.name}; SHA256={checksum}")
    print("Build completed. Verify signing and install/test the signed APK before claiming APK_RELEASE = PASS.")


if __name__ == "__main__":
    main()
