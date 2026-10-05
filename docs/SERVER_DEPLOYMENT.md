# Production deployment guide

Production deployment: **NOT VERIFIED**. The intended API domain is `finance-api.demo.xichugeek.com`. Local Docker success does not imply server readiness.

## Before any production writes

1. Audit CPU, memory, disk, Docker/Compose, running containers, ports, existing proxy/sites/domains and available backups through read-only SSH.
2. Preserve the existing xichugeek.com, Forge, demos, databases and proxy architecture. Use an independent Compose project, network and named database volume.
3. Prepare the deployment files and report exact changed paths, resource/port impact, DNS/certificate work and rollback steps. Obtain the user's confirmation before server/DNS writes.
4. Use the existing Nginx or Caddy. Bind API only to loopback or the proxy's internal Docker network; do not publish PostgreSQL 5432.
5. Generate production secrets into a private server env file; do not copy the developer's local database or signing keystore.

## Acceptance after approved deployment

Verify the public HTTPS `/health` with certificate validation, then create only explicitly approved fictional acceptance data for registration/login, owned transactions, CSV duplicate checks, Analytics and Ask. Check existing public sites again after deploying. Record [SERVER_PREFLIGHT.md](SERVER_PREFLIGHT.md) and production validation evidence before enabling the release gate.

## Backups and recovery

The production phase will add a `pg_dump` backup script, exact Compose commands, private backup location, restore procedure and verified volume names after the existing server architecture is known. Do not remove or replace an existing database volume. A rollback should stop only Xichu Finance and restore only its added proxy file/configuration; it must preserve its data volume and all unrelated services.

The exact server-specific files/commands remain pending the required read-only preflight.
