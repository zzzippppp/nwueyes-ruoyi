#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=/etc/nwueyes/nwueyes.env
[[ -f "${ENV_FILE}" ]] || { echo "Missing ${ENV_FILE}" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

db_user="${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required}"
db_password="${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required}"
[[ "${db_user}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || { echo "Unsafe database username" >&2; exit 1; }

sudo -u postgres psql -v ON_ERROR_STOP=1 \
  -v db_user="${db_user}" \
  -v db_password="${db_password}" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user') \gexec
SQL

if ! sudo -u postgres psql -Atqc "SELECT 1 FROM pg_database WHERE datname = 'nwueyes'" | grep -q 1; then
  sudo -u postgres createdb -O "${db_user}" nwueyes
fi

sudo -u postgres psql -d nwueyes -v ON_ERROR_STOP=1 -c 'CREATE EXTENSION IF NOT EXISTS vector;'
echo "Database role, database, and pgvector extension are ready."
