#!/usr/bin/env bash
set -euo pipefail

APP_ROOT=/opt/nwueyes
BACKEND_DIR="${APP_ROOT}/ruoyi"
FRONTEND_DIR="${APP_ROOT}/RuoYi-Vue3"
CONFIG_DIR=/etc/nwueyes
WEB_ROOT=/var/www/nwueyes

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash deploy.sh" >&2
  exit 1
fi

for path in \
  "${BACKEND_DIR}/pom.xml" \
  "${FRONTEND_DIR}/package.json" \
  "${CONFIG_DIR}/nwueyes.env" \
  "${CONFIG_DIR}/application-local.yml" \
  "${APP_ROOT}/go2rtc/go2rtc" \
  "${APP_ROOT}/go2rtc/go2rtc.yaml"; do
  [[ -f "${path}" ]] || { echo "Missing required file: ${path}" >&2; exit 1; }
done

install -d -o nwueyes -g nwueyes "${APP_ROOT}/data" "${APP_ROOT}/backups" "${WEB_ROOT}"

if [[ ! -x "${BACKEND_DIR}/.venv/bin/python" ]]; then
  sudo -u nwueyes python3 -m venv "${BACKEND_DIR}/.venv"
fi
sudo -u nwueyes "${BACKEND_DIR}/.venv/bin/pip" install --upgrade pip
sudo -u nwueyes "${BACKEND_DIR}/.venv/bin/pip" install \
  -r "${BACKEND_DIR}/scripts/requirements.txt" \
  -r "${BACKEND_DIR}/scripts/requirements-embedding.txt"

sudo -u nwueyes mvn -f "${BACKEND_DIR}/pom.xml" clean package -DskipTests
sudo -u nwueyes npm --prefix "${FRONTEND_DIR}" ci
sudo -u nwueyes npm --prefix "${FRONTEND_DIR}" run build:prod

rsync -a --delete "${FRONTEND_DIR}/dist/" "${WEB_ROOT}/"
chown -R www-data:www-data "${WEB_ROOT}"

install -m 644 "${BACKEND_DIR}/deploy/ubuntu/nwueyes.service" /etc/systemd/system/nwueyes.service
install -m 644 "${BACKEND_DIR}/deploy/ubuntu/go2rtc.service" /etc/systemd/system/go2rtc.service
install -m 644 "${BACKEND_DIR}/deploy/ubuntu/nwueyes-backup.service" /etc/systemd/system/nwueyes-backup.service
install -m 644 "${BACKEND_DIR}/deploy/ubuntu/nwueyes-backup.timer" /etc/systemd/system/nwueyes-backup.timer
install -m 644 "${BACKEND_DIR}/deploy/ubuntu/nwueyes.nginx.conf" /etc/nginx/sites-available/nwueyes
ln -sfn /etc/nginx/sites-available/nwueyes /etc/nginx/sites-enabled/nwueyes
rm -f /etc/nginx/sites-enabled/default

nginx -t
systemctl daemon-reload
systemctl enable nwueyes go2rtc nginx nwueyes-backup.timer
systemctl restart go2rtc
systemctl restart nwueyes
systemctl reload nginx

echo "Deployment completed."
systemctl --no-pager --full status nwueyes go2rtc nginx
bash "${BACKEND_DIR}/deploy/ubuntu/verify.sh"
