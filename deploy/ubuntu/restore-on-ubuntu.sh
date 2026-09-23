#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/nwueyes
ENV_FILE=/etc/nwueyes/nwueyes.env
input_dir=
replace=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input) input_dir="$2"; shift 2 ;;
    --replace) replace=true; shift ;;
    *) echo "Usage: $0 --input /tmp/nwueyes-migration/<timestamp> --replace" >&2; exit 2 ;;
  esac
done

[[ "${EUID}" -eq 0 ]] || { echo "Run with sudo." >&2; exit 1; }
[[ -n "${input_dir}" && -d "${input_dir}" ]] || { echo "Migration input directory is required." >&2; exit 1; }
[[ "${replace}" == true ]] || { echo "Refusing to overwrite data without --replace." >&2; exit 1; }
[[ -f "${ENV_FILE}" ]] || { echo "Missing ${ENV_FILE}" >&2; exit 1; }

database_dump="$(find "${input_dir}" -maxdepth 1 -name '*.dump' -type f -print -quit)"
media_archive="$(find "${input_dir}" -maxdepth 1 -name '*-media-*.tar.gz' -type f -print -quit)"
[[ -n "${database_dump}" ]] || { echo "No database dump found." >&2; exit 1; }
[[ -n "${media_archive}" ]] || { echo "No media archive found." >&2; exit 1; }

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${SPRING_DATASOURCE_USERNAME:?database username required}"
: "${SPRING_DATASOURCE_PASSWORD:?database password required}"
export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD}"

systemctl stop nwueyes || true
pg_restore --host=127.0.0.1 --username="${SPRING_DATASOURCE_USERNAME}" \
  --dbname=nwueyes --clean --if-exists --no-owner --exit-on-error "${database_dump}"

rm -rf "${APP_ROOT}/data"
install -d -o nwueyes -g nwueyes "${APP_ROOT}/data"
tar -xzf "${media_archive}" -C "${APP_ROOT}/data" --no-same-owner
chown -R nwueyes:nwueyes "${APP_ROOT}/data"

unset PGPASSWORD
systemctl start nwueyes
echo "Database and persistent media restored."
