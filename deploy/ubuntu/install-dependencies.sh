#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash install-dependencies.sh" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y \
  ca-certificates curl gnupg git rsync \
  openjdk-17-jdk maven \
  python3 python3-venv python3-pip python3-dev \
  build-essential ffmpeg \
  nginx redis-server \
  postgresql postgresql-contrib libpq-dev

# Node.js 20 is needed for the Vite 5 production build.
node_major=0
if command -v node >/dev/null 2>&1; then
  node_major="$(node -p 'process.versions.node.split(".")[0]')"
fi
if [[ "${node_major}" -lt 18 ]]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

# pgvector package availability differs by Ubuntu/PostgreSQL version.
vector_package="$(apt-cache search --names-only '^postgresql-[0-9]+-pgvector$' | awk 'NR==1 { print $1 }')"
if [[ -z "${vector_package}" ]]; then
  echo "pgvector package is not available from the configured APT repositories." >&2
  echo "Install the package for your PostgreSQL major version (for example postgresql-16-pgvector), then rerun." >&2
  exit 1
else
  apt-get install -y "${vector_package}"
fi

systemctl enable --now redis-server nginx postgresql
echo "Dependencies installed. Next create /opt/nwueyes and configure /etc/nwueyes."
