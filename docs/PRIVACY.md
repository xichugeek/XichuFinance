# Privacy and data handling

Xichu Finance stores user-defined account names and bookkeeping records. It has no fields for full bank card numbers, CVV, payment passwords, identity numbers or bank login credentials. Do not put those values in free-text descriptions either. This is personal bookkeeping, not a banking system.

## Local and cloud choice

The local ledger stores accounts, categories, rules and transactions in Room on the device. Fictional first-run examples are local only and never uploaded automatically. Choosing cloud login/register opens a separate ledger stored on the configured Backend and cached in a separate database for each server/user pair. The login screen explains that choice.

Room records are unencrypted inside the app sandbox. Someone with access to the private app files could read them. The session/email/token are encrypted with Android Keystore AES-GCM before persistence; the app does not persist the login password. Logout removes the session and hides, but retains, that user's cache for later login. Local ledger files remain independent.

Android cloud backup and device-transfer exclusions are configured. Emulator restart/update retention was verified, but OEM-specific transfers and physical devices were not. Uninstalling generally removes local app data; do not uninstall to resolve signing errors without preserving needed records.

## Backend and production

Registration stores email, Argon2 password hash and creation time. Bookkeeping data is associated with the authenticated user and protected by server ownership checks. The owner's production service is `https://finance-api.demo.xichugeek.com/`, on the approved server. Standard certificate validation protects transport; local development HTTP is not production transport.

PostgreSQL resides in a private Finance Docker volume. Server administrators with sufficient privileges can access the database and backups; user isolation is not protection against a trusted administrator. No analytics SDK, advertising or external AI provider is shipped.

Only fictional example.com users/records were used in validation. Some test users, categories and fictional Release records remain for acceptance evidence. Validators do not print passwords/tokens. Screenshots and sample CSV contain no real finances.

## CSV, classification and Ask

Selecting a cloud CSV uploads its content for preview. Raw files and original filenames are not retained after preview. The signed, unencrypted preview token contains validated rows and remains in Android memory; do not log/share it. It expires after 15 minutes. Confirmed valid rows become transactions; invalid and duplicate rows are skipped. See [CSV format](CSV_FORMAT.md).

Classification uses owned rules/shared keywords. NoAIProvider sends nothing to an external AI service. Ask sends the question to the configured Backend for owned database/template answers; questions/answers are not saved as chat history. Any future AI adapter requires explicit configuration, Backend-only credentials and an updated data-flow disclosure.

## Retention, deletion and backups

Transactions and unused accounts can be deleted through the app/API. v1 has no complete account deletion, bulk export or automated retention interface; records, user identities and hidden caches can remain until an authorized administrator/device owner removes them. Do not assume logout deletes records.

Production backups are private custom-format PostgreSQL dumps with SHA256 sidecars. A restore to a separate temporary database was verified. Backups include private records and may retain deleted records until the archive is deliberately removed. The current script does not automatically delete old dumps. There is no Finance backup schedule or off-server backup service; the owner must maintain these before depending on the service for irreplaceable records. No remote deletion request workflow is implemented in v1.

This is a factual disclosure of v1 behavior, not a claim of comprehensive privacy-law compliance. See [deployment](SERVER_DEPLOYMENT.md), [security](SECURITY.md) and [release limits](RELEASE_NOTES_v1.0.0.md).
