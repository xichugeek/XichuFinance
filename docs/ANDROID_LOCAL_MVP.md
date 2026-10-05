# Android Local MVP validation

Validation date: 2026-10-05 (Asia/Shanghai)

Device: `Pixel_7` Android 35 emulator (`emulator-5554`)
Package: `com.xichugeek.finance`

**ANDROID_LOCAL_MVP = PASS** for the local Android stage. This is a Debug build. It is not a signed release APK and does not connect to a Backend.

| Check | Evidence |
| --- | --- |
| Android build | `android/gradlew.bat :app:assembleDebug --no-daemon` finished `BUILD SUCCESSFUL` after the final status bar fix. |
| Unit tests | `android/gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon` finished `BUILD SUCCESSFUL`; `MoneyTest` XML reports 3 tests, 0 failures, 0 errors. |
| Emulator | `Pixel_7` started; `adb devices -l` listed `emulator-5554` as `device`; `sys.boot_completed` returned `1`. |
| Install and launch | `adb install -r app-debug.apk` returned `Success`; `am start -n com.xichugeek.finance/.MainActivity` launched; `pidof` returned a live process and `topResumedActivity` was `MainActivity`. |
| Navigation and dashboard | Overview, transactions, accounts, and categories were opened in the emulator. The overview rendered month totals, account balance, and recent fictional transactions. See [dashboard screenshot](screenshots/android-dashboard.png). |
| Transaction CRUD | Added fictional `TestLunch` for ¥12.34, opened its detail, edited it to ¥10.50, then deleted it. The list showed the added amount, updated detail showed ¥10.50, and the deleted row disappeared. |
| Accounts and categories | Added fictional `TestWallet` and `TestCategory`; both appeared as list cards after the save action. |
| Room persistence | After reinstalling the updated Debug APK with `adb install -r`, `TestWallet` and `TestCategory` were still present in their lists. Room generated `android/app/schemas/com.xichugeek.finance.data.FinanceDatabase/1.json`. |
| Demo data | First launch showed fictional salary, breakfast, subway, supermarket, and coffee transactions. |

## Current scope

Amounts are stored as integer minor units (`Long`) in Room. The app currently uses a local workbook with fictional first-run data. Account and category creation/listing work; their edit/delete flows and multi-user synchronization belong to later stages. No network permission, production URL, token, or release signing material is present in this MVP.

The remaining project acceptance labels (`BACKEND_LOCAL`, `PRODUCTION_API`, `SIGNED_APK`, `ADB_INSTALL` of the **release** APK, and others) are **NOT VERIFIED**.
