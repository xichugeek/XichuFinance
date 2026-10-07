# Android signed Release guide

**APK_RELEASE = PASS**, verified 2026-10-07. Accepted APK: `dist/XichuFinance-v1.0.3.apk`, display name **西楚记账**; see [category management, login/sync and production workflow evidence and hashes](RELEASE_NOTES_v1.0.3.md). Package `com.xichugeek.finance`, version `1.0.3` / code `4`, minimum Android 8.0 (API 26). Branding and layout history remains in [v1.0.2](RELEASE_NOTES_v1.0.2.md) and [v1.0.1](UI_REFRESH_v1.0.1.md).

## Install the accepted APK

On the development computer, from the repository root with Platform Tools on PATH:

```powershell
Get-FileHash dist/XichuFinance-v1.0.3.apk -Algorithm SHA256
adb devices
adb -s emulator-5554 install -r dist/XichuFinance-v1.0.3.apk
adb -s emulator-5554 shell am start -W -n com.xichugeek.finance/.MainActivity
```

Use your actual device serial. On a phone, copy the APK and permit installation from the file manager when Android asks. The independent local ledger needs no Docker or server login. Cloud login uses `https://finance-api.demo.xichugeek.com/`; it requires no Docker on the phone or user's PC.

Debug and Release signatures differ. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed app uses another key. Preserve needed data before removing that app. Only fictional Debug data on a verified emulator was removed during acceptance. Future updates must retain the Release key and increase `versionCode`.

The v1.0.3 category management and login/sync update retains the same Release signer as previous versions. Install over them to preserve app data; do not uninstall for this update. Historical v1.0.0 acceptance remains in [its original record](APK_RELEASE_VALIDATION.md).

## Signing key storage

The owner's dedicated RSA-3072 PKCS12 key and credentials are outside the repository under `%USERPROFILE%\.xichufinance\signing`. A separate, byte-verified private backup is on another NTFS drive. Both directories have restricted ACLs for the owner and SYSTEM. No key/password is in Git, Backend, Docker images or server. Losing the key prevents compatible updates; keep a protected offline copy.

Public signer certificate SHA256:

```text
8f69ab65fbbebf6fd1b715a842d2af82e69113c43fbd037161ba1e1280ccf160
```

This is public identity metadata, not a signing secret. Public clones do not contain the owner's key.

## Build using existing private configuration

Requires Python 3.12, JDK 21, Android SDK 36.1 and the repository's Gradle wrapper. From the repository root on Windows:

```powershell
python scripts/build_release.py --instrumentation --bundle
```

The script reads `%USERPROFILE%\.xichufinance\signing\signing.credentials.json`, supplies signing values to a single-use Gradle process, then runs clean, Release JVM tests, APK assembly, Release Lint, signed Release Android test assembly and optional AAB assembly. It copies APK/AAB and SHA256 sidecars into ignored `dist/`. Passwords are never displayed. Without `--instrumentation`, it runs Debug JVM tests and builds the signed Release; without `--bundle`, it omits AAB.

Use `--credentials C:/private/location/signing.credentials.json` for another location. The JSON has four string keys: `FINANCE_KEYSTORE_FILE`, `FINANCE_KEYSTORE_PASSWORD`, `FINANCE_KEY_ALIAS`, `FINANCE_KEY_PASSWORD`. Restrict its filesystem permissions and keep it outside the repository. Never put real values in a checked-in example or terminal command. Build success alone is not runtime acceptance.

## Independently signed fork

Create your own key outside the checkout. JDK keytool prompts for passwords:

```powershell
keytool -genkeypair -keystore C:/private/xichufinance/release.p12 -storetype PKCS12 -alias xichufinance-release -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000
```

Create that private directory first, restrict its permissions and back up the key/passwords. An independently signed fork cannot update the owner's APK. Configure signing in a private shell without echoing passwords:

```powershell
$env:FINANCE_KEYSTORE_FILE = 'C:/private/xichufinance/release.p12'
$env:FINANCE_KEY_ALIAS = 'xichufinance-release'
$env:FINANCE_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Keystore password' -AsSecureString)).Password
$env:FINANCE_KEY_PASSWORD = $env:FINANCE_KEYSTORE_PASSWORD
Set-Location android
.\gradlew.bat --no-daemon clean :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
Remove-Item Env:FINANCE_KEYSTORE_PASSWORD
Remove-Item Env:FINANCE_KEY_PASSWORD
```

Use the appropriate private alias/password if they differ. Environment variables contain plaintext in process memory; use a trusted machine. Alternatively save the four fields in private JSON and use the build script. For self-hosting, add `-PfinanceProductionApiUrl=https://your-public-domain/` to Gradle and verify HTTPS first.

## Verify and run Release tests

From the repository root; substitute your SDK path:

```powershell
& D:/Android/Sdk/build-tools/36.1.0/apksigner.bat verify --verbose --print-certs dist/XichuFinance-v1.0.3.apk
& D:/Android/Sdk/build-tools/36.1.0/aapt2.exe dump badging dist/XichuFinance-v1.0.3.apk
adb -s emulator-5554 install -r dist/XichuFinance-v1.0.3.apk
adb -s emulator-5554 install -r -t android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.xichugeek.finance.ReleaseWorkflowTest com.xichugeek.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: signature verification succeeds, correct package/version, both installs return Success, instrumentation returns `OK (1 test)`. **This test writes fictional users/records to the configured production API. Run it only with the server owner's authorization.** The owner's approval covered the recorded run.

Missing signing variables or a keystore inside the repository fail the signing guard; no Debug fallback exists. The URL guard rejects HTTP, credentials, loopback and IP literals. Actual Release debugging, cleartext and Android backup are disabled. Guards do not substitute for runtime checks.

Common failures: missing key/config → restore approved private files; SDK/JDK error → correct Android Studio SDK/Gradle JDK; certificate/network timeout → check connectivity and valid HTTPS, never disable TLS validation; expired login → log in again; signature conflict → preserve data and choose a deliberate migration. Build outputs, credentials and test APKs remain ignored by Git.
