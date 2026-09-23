#!/usr/bin/env bash
# Read-only inventory for a pre-existing nwueyes/RuoYi deployment.
# It deliberately redacts values from environment/configuration files.
set -uo pipefail

section() {
  printf '\n========== %s ==========\n' "$1"
}

run() {
  "$@" 2>&1 || true
}

redact() {
  sed -E \
    -e 's/((password|secret|token|api[_-]?key|appsecret|verify[_-]?code)[[:space:]]*[:=][[:space:]]*).*/\1***REDACTED***/I' \
    -e 's#(rtsp://)[^/@[:space:]]+@#\1***:***@#Ig'
}

section "OS and resources"
run cat /etc/os-release
run uname -a
run df -hT
run free -h

section "Running application services"
run systemctl list-units --type=service --all --no-pager
run systemctl list-timers --all --no-pager
run pgrep -af 'java|go2rtc|python.*yolo|pm2|node'

section "Listening ports"
run sudo ss -ltnp
run sudo ss -lunp

section "Project and build candidates"
for root in /opt /srv /home /var/www /data; do
  [[ -d "${root}" ]] || continue
  run sudo find "${root}" -maxdepth 5 \
    \( -name 'ruoyi-admin.jar' -o -name 'pom.xml' -o -name 'package.json' -o -name 'application*.yml' \) \
    -printf '%TY-%Tm-%Td %TT %s %p\n'
done

section "Git repositories"
for git_dir in $(sudo find /opt /srv /home -maxdepth 5 -type d -name .git -print 2>/dev/null); do
  repo="${git_dir%/.git}"
  printf '\n-- %s --\n' "${repo}"
  run sudo git -C "${repo}" status --short --branch
  run sudo git -C "${repo}" log -1 --oneline
  run sudo git -C "${repo}" remote -v
done

section "Nginx routing"
run sudo nginx -T

section "PostgreSQL and Redis"
run sudo -u postgres psql -Atqc 'SELECT version()'
run sudo -u postgres psql -Atqc "SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database ORDER BY pg_database_size(datname) DESC"
run sudo -u postgres psql -d nwueyes -Atqc "SELECT extname, extversion FROM pg_extension ORDER BY extname"
run redis-cli INFO server

section "Likely persistent-data directories"
for candidate in \
  /opt/nwueyes/data /opt/ruoyi/data /srv/nwueyes/data \
  /data /home/ruoyi/data; do
  [[ -d "${candidate}" ]] && run sudo du -sh "${candidate}"
done

section "Configuration names only (secret values redacted)"
for config in $(sudo find /opt /srv /etc -maxdepth 7 \
  \( -name 'application-local.yml' -o -name 'application.yml' -o -name '*.env' \) -type f -print 2>/dev/null); do
  printf '\n-- %s --\n' "${config}"
  sudo grep -E '^[[:space:]]*([A-Za-z0-9_.-]+[[:space:]]*[:=])' "${config}" 2>/dev/null | redact || true
done

section "Finished"
echo "This inventory is read-only. Review it before stopping or replacing any old service."
