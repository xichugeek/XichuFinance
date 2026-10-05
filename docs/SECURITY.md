# Security status

This document describes the current implementation. Production and release acceptance are still open; see [phase status](PHASE_STATUS.md).

## Implemented

- Backend passwords are stored as Argon2 hashes. Passwords are not returned by user endpoints.
- JWT access tokens expire after 12 hours. The signing secret must be at least 32 characters and must not be an example placeholder.
- User data routes filter by the current authenticated user. Account/category references are checked for ownership before transaction writes.
- Cross-user tests cover transaction reads, updates, deletion, creation with foreign references, account updates/deletion, and analytics.
- Money uses decimal arithmetic and fixed-scale database columns. Nonpositive transactions and amounts with more than two fractional digits are rejected.
- PostgreSQL is accessible only inside the Compose network. The local API port binds to loopback.
- `.env`, signing keys, local Android SDK properties, and build outputs are ignored by Git. `.env.example` contains placeholders only.
- Local setup generates random secrets without displaying them. Validation scripts use fictional data and do not print tokens or passwords.

## Current limits

The access-token design has no refresh token or server-side token revocation. A valid token remains usable until expiration. Android logout clears its local session; it does not revoke copied tokens on the server. The session is encrypted with Android Keystore AES-GCM before DataStore persistence, including its email and server binding. Device validation is part of PHASE 4.

Room databases are not encrypted. The app sandbox, disabled backups, and device access controls protect local bookkeeping data. Cloud caches are separate for each server/user pair. Logout hides the cached data and preserves its files for later login; local ledger data is independent.

The local HTTP API is for development. Production requires HTTPS, the separate server preflight, explicit deployment approval, and runtime security verification. No production server or DNS has been changed.

CSV imports, optional AI providers, release signing, and their security checks are not yet implemented. An eventual AI provider key must remain on the Backend, outside source code, Docker images, and Android APKs.

## Before commits and releases

Review staged files for secrets and unexpected files before committing. Pattern scans help find recognizable keys, but also inspect configuration and changed code manually. Never commit a real `.env`, token, database password, signing key, or keystore password. Do not publish Docker diagnostic bundles without reviewing their contents.

The complete project security and secret-scan PASS labels are reserved for the final verified release; they are not claimed by this document.
