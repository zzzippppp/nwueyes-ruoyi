#!/usr/bin/env bash
set -euo pipefail

failed=0

check() {
  local label="$1"
  shift
  if "$@"; then
    printf 'PASS %s\n' "${label}"
  else
    printf 'FAIL %s\n' "${label}" >&2
    failed=1
  fi
}

check "backend service" systemctl is-active --quiet nwueyes
check "go2rtc service" systemctl is-active --quiet go2rtc
check "nginx service" systemctl is-active --quiet nginx
check "redis service" systemctl is-active --quiet redis-server
check "backend HTTP" curl -fsS --max-time 10 http://127.0.0.1:8080/
check "frontend HTTP" curl -fsSI --max-time 10 http://127.0.0.1/
if sudo -u postgres psql -d nwueyes -Atqc \
  "SELECT 1 FROM pg_extension WHERE extname = 'vector'" | grep -qx 1; then
  printf 'PASS pgvector extension\n'
else
  printf 'FAIL pgvector extension\n' >&2
  failed=1
fi
check "persistent data directory" test -d /opt/nwueyes/data
check "backup timer enabled" systemctl is-enabled --quiet nwueyes-backup.timer

if (( failed )); then
  echo "Deployment verification failed. Inspect: journalctl -u nwueyes -u go2rtc -n 200 --no-pager" >&2
  exit 1
fi

echo "Platform services are healthy. Complete an end-to-end RTSP/door-line test in the UI next."
