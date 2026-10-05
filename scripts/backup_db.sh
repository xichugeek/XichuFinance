#!/usr/bin/env bash
# Run on the approved production Linux server. Dumps contain private user data.
set -euo pipefail
umask 077

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
env_file="${FINANCE_ENV_FILE:-${project_root}/.env.production}"
backup_dir="${1:-/opt/xichugeek/backups/finance}"
if [[ ! -f "${env_file}" ]]; then
  printf 'Private production env file is missing.\n' >&2
  exit 1
fi
if [[ "${backup_dir}" != /* || "${backup_dir}" == / ]]; then
  printf 'Use an absolute dedicated backup directory.\n' >&2
  exit 1
fi
if [[ "$(stat -c '%a' -- "${env_file}")" != 600 ]]; then
  printf 'Production env file must have mode 600.\n' >&2
  exit 1
fi
mkdir -p -- "${backup_dir}"
if [[ -L "${backup_dir}" || "$(stat -c '%a' -- "${backup_dir}")" != 700 ]]; then
  printf 'Backup directory must be a dedicated directory with mode 700.\n' >&2
  exit 1
fi
compose=(docker compose --project-name xichufinance-prod --env-file "${env_file}" -f "${project_root}/docker-compose.prod.yml")
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
temporary="$(mktemp "${backup_dir}/.finance-${stamp}-XXXXXX.partial")"
trap 'rm -f -- "${temporary}"' EXIT
"${compose[@]}" exec -T finance-db pg_dump -U xichu -d xichu_finance --format=custom > "${temporary}"
test -s "${temporary}"
"${compose[@]}" exec -T finance-db pg_restore --list < "${temporary}" > /dev/null
destination="${temporary%.partial}.dump"
destination="${backup_dir}/$(basename -- "${destination}" | sed 's/^\.//')"
mv -- "${temporary}" "${destination}"
chmod 600 -- "${destination}"
trap - EXIT
sha256sum -- "${destination}" > "${destination}.sha256"
printf 'BACKUP_CREATED=%s\n' "${destination}"
printf 'Restore has not been verified by creating a backup alone.\n'
