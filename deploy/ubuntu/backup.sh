#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/nwueyes
BACKUP_ROOT="${APP_ROOT}/backups"
ENV_FILE=/etc/nwueyes/nwueyes.env
timestamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="${BACKUP_ROOT}/${timestamp}"

[[ -f "${ENV_FILE}" ]] || { echo "Missing ${ENV_FILE}" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${SPRING_DATASOURCE_USERNAME:?database username required}"
: "${SPRING_DATASOURCE_PASSWORD:?database password required}"
install -d -m 700 "${backup_dir}"
export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD}"

pg_dump --host=127.0.0.1 --username="${SPRING_DATASOURCE_USERNAME}" \
  --format=custom --file="${backup_dir}/nwueyes.dump" nwueyes
tar -C "${APP_ROOT}/data" -czf "${backup_dir}/persistent-media.tar.gz" \
  uploadPath log_library face_library body_library snapshot_library capture_manifest 2>/dev/null || true
unset PGPASSWORD

find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime +14 -exec rm -rf {} +
echo "Backup created: ${backup_dir}"
