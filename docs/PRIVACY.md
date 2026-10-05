# Privacy and data handling

Xichu Finance is a personal bookkeeping application. It stores user-defined account names and transactions; it does not provide fields for full bank card numbers, CVV, payment passwords, identity numbers, or banking login credentials.

## Current local application

Android stores accounts, categories, and transactions in Room on the device. Bundled demo data and repository screenshots are fictional. The local ledger is never uploaded automatically. Choosing cloud login/register enables server storage and synchronization for that separate user ledger; the login screen explains this choice. Room databases are unencrypted; anyone with access to the application's private files could read them.

The session, including email/token, is encrypted before persistence and is removed on logout. Passwords are not stored by the app. Logout hides the user's cache but retains its database for later login to the same user. Backups are disabled.

## Backend

Registration stores an email address, an Argon2 password hash, and account creation time. Bookkeeping records are associated with the authenticated user and isolated from other users through server-side checks. The local PostgreSQL database is held in a Docker named volume on the developer's machine.

The HTTP validation script creates fictional users at `example.com`. It cleans up its transactions and account, while those test users and categories remain in the local database. It does not print credentials. Real financial records are not used in tests, examples, or screenshots.

## Features still being developed

CSV selection uploads the chosen content to the Backend for preview. Raw files and original filenames are not retained after preview. The signed, unencrypted preview token contains validated rows and is held only in Android memory. Confirmed rows are stored as transactions; invalid and duplicate rows are skipped. Production transport must use HTTPS. See [CSV_FORMAT.md](CSV_FORMAT.md).

Classification uses user-owned rules and built-in keywords. The shipped NoAIProvider sends nothing to an external AI service. Ask sends questions to the user's Backend for database/template answers; questions and answers are not stored as chat history. Any future external AI adapter needs an explicit configuration and documented data flow with Backend-only credentials.

User/account deletion, export, server retention, and production backup/restore behavior are not yet a complete privacy workflow. A deployed service must document those behaviors before being presented as ready for real users. No production data has been created or altered during the current local phases.
