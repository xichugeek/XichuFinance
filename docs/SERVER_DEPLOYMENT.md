# Production deployment guide

Production deployment: **NOT VERIFIED**. [Read-only server audit](SERVER_PREFLIGHT.md) is complete. The Finance DNS record is absent. The files below are a proposed deployment; they have not been applied to the server.

## Change scope and approval

The user's original section 40 and final safety requirements require confirmation before production containers, databases, proxy/DNS changes, signing secrets or production data writes. Obtain that confirmation before executing this guide.

| Path / object | Proposed change |
| --- | --- |
| `/opt/xichufinance` | Finance repository checkout at the accepted Git commit |
| `/opt/xichufinance/.env.production` | New random Finance-only secrets; root-owned, mode 600 |
| `/opt/xichugeek/backups/finance` | Dedicated private Finance dumps, mode 700 |
| `xichufinance-prod` | Independent Compose project: API + PostgreSQL |
| `xichufinance-prod_postgres_data` | Finance-only named database volume |
| `xichufinance-prod_finance_database` | Finance-only internal network |
| `xg_edge` | Attach only Finance API, with alias `xichufinance-api` |
| `/opt/xichugeek/gateway/Caddyfile` | Append the reviewed Finance site block; preserve existing blocks |
| DNS | Add `finance-api.demo.xichugeek.com` A record to the existing server |

Both Finance containers have 512 MiB memory and one CPU limits. No new host port is published. The database is only on the internal Finance network; the API also joins the existing gateway network. These network settings follow [Docker Compose's official network documentation](https://docs.docker.com/reference/compose-file/networks/).

## Approved first deployment

Run these steps **only after approval**, through the audited SSH connection. Never paste secrets into chat or print rendered Compose configuration.

1. Recheck existing site health, free disk/RAM, gateway configuration checksum and container health. If the gateway config changed, review the current file before applying the proposal.
2. Add the Finance DNS A record. Confirm public resolution and check any existing AAAA record or CDN proxy. DNS access must be supplied by the user or the record can be added by the user.
3. Create `/opt/xichufinance` with mode 700 and checkout the accepted repository commit. Keep `.git`, env and backups outside any public web root. Do not copy local financial data.
4. In this server checkout, set `release_tag` to the exact deployed Git commit, then generate new production secrets:

```bash
cd /opt/xichufinance
release_tag="$(git rev-parse HEAD)"
python3 scripts/setup_production_env.py --output /opt/xichufinance/.env.production --release-tag "$release_tag"
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml config --quiet
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml up -d --build --wait --wait-timeout 180
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml ps
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml exec -T finance-api alembic current
```

The env generator refuses to overwrite an existing file. Retain its database password whenever reusing a volume. The API starts as a non-root user and runs the Alembic migration before serving. Record the final container image IDs/digests and Git commit for later rollback; keep the accepted image locally.

5. Save the current Caddyfile bytes and permission metadata in a private, timestamped gateway backup. Compare its checksum with the read-only baseline. Append [deploy/Caddyfile.finance](../deploy/Caddyfile.finance) to a candidate and inspect the diff. This block applies a 2 MB [request body limit](https://caddyserver.com/docs/caddyfile/directives/request_body) and proxies to the unique Finance alias.
6. Adapt the complete candidate via stdin first. Adaptation alone checks conversion to JSON; it is not certificate or upstream validation. The audited mount is a single file, so **write the approved candidate into the existing file inode**; do not rename a new file over it. Validate before reload, and immediately restore the saved bytes if validation fails:

```bash
docker exec xg-gateway-caddy-1 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker exec xg-gateway-caddy-1 caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
```

Use the [official Caddy commands](https://caddyserver.com/docs/command-line). A successful reload is followed by runtime checks; it does not alone establish acceptance.

## Production acceptance

```bash
curl --fail --show-error https://finance-api.demo.xichugeek.com/health
python3 scripts/validate_backend.py --base-url https://finance-api.demo.xichugeek.com --production --confirm-fictional-writes
bash scripts/backup_db.sh
```

Use normal certificate validation, never `curl -k`. Expected health response is `{"status":"healthy"}`. The API validator creates two fictional users and tests auth, ownership, money precision, transaction changes, CSV preview/commit/deduplication, Analytics, Classification and Ask. It cleans its first user's transactions/account; fictional users and categories remain. This flag must only be used after approval for acceptance data.

Verify the original `xichugeek.com`, `www.xichugeek.com` and `enterprise.demo.xichugeek.com` HTTPS routes again. Compare the existing containers and configuration against the baseline. Record certificate issuer/expiry, deployed commit/images, migration head, health and acceptance results. Signed APK acceptance is a separate [Release gate](ANDROID_RELEASE.md).

## Backups and restore

[scripts/backup_db.sh](../scripts/backup_db.sh) uses `pg_dump --format=custom`, verifies the archive table of contents, and creates a SHA256 sidecar. It checks env mode 600 and backup directory mode 700, uses restrictive file permissions, and never removes old dumps. Run manually or through a separately approved timer. No Finance backup timer is installed yet.

The database volume name is `xichufinance-prod_postgres_data`. Obtain its host location with `docker volume inspect xichufinance-prod_postgres_data --format '{{.Mountpoint}}'`; do not edit database files directly. Backups contain private data and must stay outside public web roots, Git, images and APKs. Keep a protected off-server copy; this proposal does not configure an off-server service.

Before a restore, make a fresh backup and check the archive checksum. A safe rehearsal creates a **new temporary database**, without replacing the live database. Run only after approval for server writes; substitute the actual dump path:

```bash
cd /opt/xichufinance
sha256sum --check /opt/xichugeek/backups/finance/finance-UTC-RANDOM.dump.sha256
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml exec -T finance-db createdb -U xichu finance_restore_check
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml exec -T finance-db pg_restore -U xichu -d finance_restore_check --no-owner --no-privileges --exit-on-error < /opt/xichugeek/backups/finance/finance-UTC-RANDOM.dump
docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml exec -T finance-db psql -U xichu -d finance_restore_check -c 'SELECT version_num FROM alembic_version; SELECT count(*) FROM transactions;'
```

Compare migration head and row counts with the source. Keep the rehearsal database until its result is recorded; remove only that rehearsal database after explicit authorization if required. A dump/list command alone is **not a verified restore**. Restoring the live database requires a separate reviewed maintenance plan and data-write approval.

## Rollback

1. Restore the saved Caddyfile bytes into its existing inode, validate and reload. Confirm the three original routes are healthy. Restore exact predeployment content only if no unrelated operator changes occurred; otherwise remove only the Finance block after review.
2. Stop only Finance: `docker compose --project-name xichufinance-prod --env-file .env.production -f docker-compose.prod.yml stop`. Keep its containers, network, secrets, dumps and data volume for investigation. **Never use `down -v`, prune or remove another project's services.**
3. After a later app-only update, reselect the saved image tag and start it only when its schema is compatible. Database migration rollback is not automatic; use a reviewed recovery plan if data/schema changed.
4. Removing a newly added DNS record can be considered separately after approval; account for DNS caches. Do not alter existing DNS records or certificates.

## Current verification

Read-only resource/network/proxy inventory and backup metadata are verified. All three existing HTTPS routes returned HTTP 200. The existing Caddy adapted both original and complete proposed configuration from stdin; the three original route hosts were preserved and the Finance host was added. No Caddy config write, validate/reload or certificate request was performed.

Local Compose parsing and isolation/resource assertions, Bash syntax and Python/validation-target guards passed. The local API acceptance script also passed after adding the guarded production mode. The backup script has not yet been run against production. Production HTTPS, new containers, database volume, real backup/restore and signed APK remain **NOT VERIFIED** pending approval and DNS.
