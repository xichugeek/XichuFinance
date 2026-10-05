# Android Release guide

Current signed release: **NOT VERIFIED**. No release keystore has been created during local phases. See [APK validation](APK_RELEASE_VALIDATION.md).

1. Complete local tests and read-only production server preflight.
2. Deploy and verify the production HTTPS API before release acceptance. Default domain: `https://finance-api.demo.xichugeek.com/`.
3. Confirm release signing secret creation/reuse. Store a dedicated keystore outside the repository on the developer's computer, with a separate secure backup. Losing it prevents compatible app updates.
4. Configure signing credentials through local secret storage/environment, without source-code values. Signing configuration and the exact build command will be recorded when PHASE 11 is verified.
5. Clean-build the signed release, copy the accepted artifact to `dist/XichuFinance-v1.0.0.apk`, verify with Android SDK `apksigner`, inspect the package/version and compute SHA256.
6. ADB-install the **signed release** and test registration/login, transaction changes, CSV, Analytics, Ask, closing/reopening and data retention against production HTTPS.

`validateProductionApi` rejects HTTP, credentials in URLs, loopback and IP literals. The Release manifest disables cleartext traffic. A successful URL guard does not prove the API exists or has valid HTTPS. The production URL is centralized in `android/app/build.gradle.kts`; self-hosters may provide `-PfinanceProductionApiUrl=https://their-public-domain/`.

Debug and Release signatures differ. Replacing Debug with Release can produce `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Preserve needed data before removing Debug; only fictional emulator test data is disposable. Never bypass release signing by using the Debug signing key.

Keystore files/passwords must not enter Git, Docker images, Backend or the server. Do not paste them into chat. APKs/build outputs are ignored; publish only the final reviewed artifact with checksum and release notes after acceptance.
