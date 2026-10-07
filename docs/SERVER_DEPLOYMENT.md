# Production deployment guide

**PRODUCTION_API = PASS**, last verified 2026-10-07 (Asia/Shanghai). [Read-only server audit](SERVER_PREFLIGHT.md) preceded the user's explicit approval for DNS, server deployment, acceptance data and signing. The first deployment and subsequent category management update are recorded separately below.

## Change scope and approval

The user's original section 40 and final safety requirements require confirmation before production containers, databases, proxy/DNS changes, signing secrets or production data writes. Obtain that confirmation before executing this guide.

| Path / object | Approved change |
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

## First deployment verification (2026-10-06)

| Gate | Actual evidence |
| --- | --- |
| Approved DNS | Namecheap BasicDNS A record `finance-api.demo` → `117.55.232.77`; public DNS returned this address. [Saved UI evidence](screenshots/production-dns.png). Existing host records preserved. |
| Deployed Backend | Git commit `e8b9561f5238a36083f08e0082d3491ecaf6d870`; image `xichufinance-api:e8b9561f5238a36083f08e0082d3491ecaf6d870` |
| Runtime | API and PostgreSQL healthy; migration `0002_classification_rules (head)`; API runs as `appuser` |
| Image identity | API `sha256:37d6455609269a0252c828bbdd27005c4a8b23254e31bd9857fd9680dacb6bd7`; PostgreSQL `sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24` |
| Isolation | Both host port maps `{}`; database only on `xichufinance-prod_finance_database`; both memory limits 536,870,912 bytes |
| HTTPS | `/health` HTTP 200, `{"status":"healthy"}`; normal certificate/hostname validation; TLS 1.3 |
| Certificate | Let's Encrypt YE2; valid from 2026-10-06 09:14:02 UTC to 2027-01-04 09:14:01 UTC; renewal managed by existing Caddy |
| Core API | `BACKEND_PRODUCTION = PASS`: real HTTPS auth, CRUD, cross-user read/write/delete isolation, money precision, CSV preview/commit/deduplication, Analytics, Classification and Ask |
| Existing sites | `xichugeek.com`, `www.xichugeek.com` after redirect, and `enterprise.demo.xichugeek.com` each returned HTTPS HTTP 200 after reload |
| Gateway preservation | Existing bytes retained as prefix; file inode/owner/group/mode preserved; validate/reload passed |
| Gateway rollback copy | Private `/opt/xichugeek/backups/finance/gateway-Caddyfile-before-20261006T101229Z`, verified byte-for-byte before modification |
| PostgreSQL volume | `xichufinance-prod_postgres_data`, `/var/lib/docker/volumes/xichufinance-prod_postgres_data/_data` |
| Actual backup | Initial private `finance-20261006T101639Z-3BGRjU.dump` plus final post-Release `finance-20261006T105828Z-YTdOVi.dump`; scripts verified archive contents and SHA256 sidecars |
| Actual restore | Restored to separate `finance_restore_check_20261006`; migration version and all checked values matched: users 4, accounts 1, categories 57, transactions 1, sum `9.87` (fictional) |

The restore rehearsal did not replace the live database. Its fictional live transaction/account were removed after the matching dump was verified; test users/categories and the private recovery archive remain. No unrelated containers, databases or proxy routes were replaced.

The first production API image failed because a private Linux checkout's files were mode 600/700 and root-owned, so the non-root application could not read Alembic configuration. Commit `e8b9561` fixes runtime `COPY --chown=appuser:appuser`; the rebuilt image completed migration and health checks. A Windows HTTPS script run encountered an intermittent TLS handshake timeout; the same complete validator then passed on the server against the public HTTPS domain with normal certificate verification. Final Android production-network validation belongs to the signed Release gate.

Local Compose parsing/isolation/resource checks, Bash syntax and target guards also passed. Final server recheck found all five original containers plus two Finance containers healthy; production health remained 200. Backup scheduling/off-server copying are not installed. Signed APK acceptance is recorded separately in the [Release gate](APK_RELEASE_VALIDATION.md).

## 2026-10-07 category management update

The owner's standing authorization to finish the server deployment, followed by the explicit category edit/delete request, covered this Finance API update. A fresh read-only inventory and clean repository check preceded the change. Android [v1.0.3](RELEASE_NOTES_v1.0.3.md) adds the corresponding controls.

| Check | Actual result |
| --- | --- |
| Deployed source / tag | `a11314027f75f6f850b9e12011ca5bbbe504ed15` / `xichufinance-api:a11314027f75f6f850b9e12011ca5bbbe504ed15` |
| API image | `sha256:5dbee7c5d3087d03f6c315e52bbbfeff2c9b3fbd2d6a679fb680534afbe0b4e5`; healthy, no OOM, zero restarts |
| New API scope | Authenticated `PUT /categories/{id}` and `DELETE /categories/{id}`; rename preserves category ID/type; own-user access only; transaction/rule references prevent deletion |
| Before-update backup | Private `/opt/xichugeek/backups/finance/finance-20261007T051343Z-NESpAa.dump`; `pg_restore --list` and SHA256 verification passed; this dump alone is not a new restore rehearsal |
| Private env backup | `/opt/xichugeek/backups/finance/env-before-category-20261007T051311Z`, byte-verified, mode 600 |
| Runtime change | Built Finance API, then `up -d --no-deps --wait finance-api`; only the API container was recreated |
| Preservation | PostgreSQL and all five unrelated containers retained their IDs/images/health/restart counts; gateway checksum/inode unchanged; env update changed only `FINANCE_RELEASE_TAG`, preserving existing secrets and mode 600 |
| Database | Existing volume preserved; migration remains `0002_classification_rules (head)`; no new schema migration |
| HTTPS acceptance | Full `BACKEND_PRODUCTION = PASS`, including category cross-user PUT/DELETE rejection, invalid/duplicate names, fixed type, rename retention and used/disabled-rule deletion protection; fictional validation records only |
| Existing sites | `xichugeek.com`, `www.xichugeek.com`, `enterprise.demo.xichugeek.com`: normal HTTPS certificate checks, HTTP 200 |
| Android | Signed production workflow passed in 33.579s, including category actions and retained ledger; see v1.0.3 evidence |

Previous image/tag `e8b9561f5238a36083f08e0082d3491ecaf6d870` was retained for a compatible API-only rollback. DNS and Caddy configuration were not changed during this update. No backup schedule, off-server copy or new restore test is claimed.
