# Xichu Finance v1.0.1

Android UI refresh in the existing XichuFinance repository. Package `com.xichugeek.finance`, version code **2**, Android 8.0 or later. Uses the same Release signing certificate as v1.0.0, so it can be installed as an update.

## Changes

- White cards, charcoal headings, red expense/action accents and green income amounts, with consistent spacing and rounded shapes.
- Original vector icons replace the repeated Chinese initials in bottom navigation. Each destination has one label, a selected indicator and system navigation inset spacing.
- Dashboard prioritizes monthly income/expense, account balance and adding a transaction, with compact import/analytics/Ask shortcuts.
- Transaction rows include category icons, distinct income/expense colors and responsive single-line amounts.
- Account cards show the type and balance; the existing edit/add/delete actions remain available.
- Analytics uses paired totals, a filled daily trend, category ring and percentage bars, including clear empty states.
- Forms/content respect the keyboard and scrollable viewport. Transaction details can scroll on small screens.
- The build script names artifacts using the Gradle version instead of overwriting v1.0.0.

See [actual UI and Release verification](UI_REFRESH_v1.0.1.md) for screenshots, checksums and test results. The Backend, money calculations, database schemas and production infrastructure are unchanged by this UI release. Existing [v1 limitations](RELEASE_NOTES_v1.0.0.md#v1-limits) still apply.

## Install

Use `dist/XichuFinance-v1.0.1.apk` and its SHA256 sidecar. Copy it to the phone and install over the owner's signed v1.0.0. Keep the existing app installed so Android can retain its data. A differently signed Debug/fork build cannot be updated with this APK. See the [signed installation guide](ANDROID_RELEASE.md).

APK/AAB files are delivered locally and kept out of Git; no Play submission or public GitHub Release publication is claimed.
