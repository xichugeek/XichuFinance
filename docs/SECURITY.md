# Security status

This document describes the accepted v1 implementation. Production HTTPS and signed Release runtime checks passed; see [phase status](PHASE_STATUS.md) and [APK evidence](APK_RELEASE_VALIDATION.md). Security review concerns the checks below, not a penetration-test or banking-grade security claim.

## Implemented

- Backend passwords are stored as Argon2 hashes. Passwords are not returned by user endpoints.
- JWT access tokens expire after 12 hours; subject, issued-at and expiry claims are required. The signing secret must be at least 32 characters and must not be an example placeholder.
- User data routes filter by the current authenticated user. Account/category references are checked for ownership before transaction writes.
- Cross-user tests cover transactions, accounts, CSV imports, analytics, classification rules and Ask results, including writes with foreign category references.
- Money uses decimal arithmetic and fixed-scale database columns. Nonpositive transactions and amounts with more than two fractional digits are rejected.
- PostgreSQL is accessible only inside the Compose network. The local API port binds to loopback.
- `.env`, signing keys, local Android SDK properties, and build outputs are ignored by Git. `.env.example` contains placeholders only.
- Local setup generates random secrets without displaying them. Validation scripts use fictional data and do not print tokens or passwords.

## Current limits

The access-token design has no refresh token or server-side token revocation. A valid token remains usable until expiration. Android logout clears its local session; it does not revoke copied tokens on the server. The session is encrypted with Android Keystore AES-GCM before DataStore persistence, including its email and server binding. Device validation is part of PHASE 4.

Room databases are not encrypted. The app sandbox and device access controls protect local bookkeeping data. Cloud caches are separate for each server/user pair. Logout hides the cached data and preserves its files for later login; local ledger data is independent. `allowBackup=false`, `fullBackupContent=false` and explicit Android 12 cloud/device-transfer exclusions are configured, following [Android's backup documentation](https://developer.android.com/identity/data/autobackup). Manufacturer-specific or future transfer mechanisms have not been runtime-verified.

The local HTTP API is for development. Production HTTPS, the separate server preflight, explicit deployment approval and runtime isolation checks have passed; see [actual server acceptance](SERVER_DEPLOYMENT.md). Production uses two isolated Finance containers without published host ports, a database-only internal network, private mode-600 secrets and mode-700 backup storage. The existing Caddy terminates valid public HTTPS.

CSV previews validate ownership and use signed, expiring tokens with a separate audience from login tokens. Commit checks owned references again and enforces database deduplication. Classification defaults to NoAIProvider; no external credential or data transfer is configured. Ask uses fixed intents and database/templates. An eventual external provider key must remain on the Backend, outside source code, Docker images, and Android APKs.

Registration has no email verification, password reset, MFA or dedicated rate limiting. Caddy's 2 MB request limit, private backup permissions and server isolation were checked; this does not prevent all public registration abuse or denial of service. JWT logout does not revoke a copied token. Room is unencrypted, and there is no complete account deletion/export/retention UI. Backup scheduling and off-server copying are not configured.

## Before commits and releases

Review staged files for secrets and unexpected files before committing. Pattern scans help find recognizable keys, but also inspect configuration and changed code manually. Never commit a real `.env`, token, database password, signing key, or keystore password. Do not publish Docker diagnostic bundles without reviewing their contents.

## Release review

The dedicated RSA-3072 signing key and credential file are outside the repository with restricted ACLs and a verified private backup. Missing signing credentials fail the build; Debug signing is never substituted. No signing key exists on the server. Actual APK verification confirmed the public HTTPS URL, no Debug HTTP URL, disabled cleartext/backup/debugging and a valid Release signature. Unpacked entries contained no private key/env/credential file or known local signing/database/JWT secret values.

Manual review covered authentication/ownership, money handling, CSV tokens, fixed Ask queries, NoAIProvider, production networks/permissions, signing/build helper and accepted APK configuration. Runtime tests cover user isolation and the signed production workflow. Pattern scanning and known-secret comparisons complement that review; neither proves the absence of every possible secret or vulnerability. Final scan/Git evidence is recorded in [PROJECT_READY](PROJECT_READY.md). Test screenshots contain only fictional records.
