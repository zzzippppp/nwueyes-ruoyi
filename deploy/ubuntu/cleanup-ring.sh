#!/usr/bin/env bash
# 定期清理直播环形缓冲 _ring，防止异常退出后磁盘泄漏。
# 策略：保留当前正在运行的 live task 目录；其余全部删除。
# 可选：CLIP_MAX_AGE_DAYS>0 时删除过期导出的场景 mp4。
set -euo pipefail

DATA_ROOT="${PRESENCE_STORAGE_ROOT:-/opt/nwueyes/data}"
if [[ -L "${DATA_ROOT}" ]]; then
  DATA_ROOT="$(readlink -f "${DATA_ROOT}")"
fi
RING_ROOT="${DATA_ROOT}/log_library/clips/_ring"
CLIP_MAX_AGE_DAYS="${CLIP_MAX_AGE_DAYS:-0}"

log() { printf '[cleanup-ring] %s\n' "$*"; }

if [[ ! -d "${RING_ROOT}" ]]; then
  log "ring root missing, skip: ${RING_ROOT}"
  exit 0
fi

active_task=""
while IFS= read -r line; do
  if [[ "${line}" =~ --task-id[[:space:]]+([^[:space:]]+) ]]; then
    active_task="${BASH_REMATCH[1]}"
    break
  fi
done < <(ps -eo args= 2>/dev/null | grep -F 'live_stream_worker_yolo.py' | grep -v grep || true)

log "ring_root=${RING_ROOT} active_task=${active_task:-none}"

deleted=0
kept=0
shopt -s nullglob
for path in "${RING_ROOT}"/*; do
  [[ -e "${path}" ]] || continue
  name="$(basename "${path}")"
  if [[ -n "${active_task}" && "${name}" == "${active_task}" ]]; then
    log "keep active ${name}"
    kept=$((kept + 1))
    continue
  fi
  rm -rf -- "${path}"
  log "deleted ${name}"
  deleted=$((deleted + 1))
done

find "${RING_ROOT}" -maxdepth 1 -type f \( -name 'seg_*.ts' -o -name '*.tmp' \) -delete 2>/dev/null || true

if [[ "${CLIP_MAX_AGE_DAYS}" =~ ^[1-9][0-9]*$ ]]; then
  clips_dir="${DATA_ROOT}/log_library/clips"
  if [[ -d "${clips_dir}" ]]; then
    mapfile -t old_clips < <(find "${clips_dir}" -type f -name '*.mp4' -mtime "+${CLIP_MAX_AGE_DAYS}" -print)
    if ((${#old_clips[@]} > 0)); then
      rm -f -- "${old_clips[@]}"
    fi
    log "deleted exported mp4 older than ${CLIP_MAX_AGE_DAYS}d count=${#old_clips[@]}"
  fi
fi

df -h / 2>/dev/null | awk 'NR==2{print "[cleanup-ring] disk "$0}' || true
log "done deleted=${deleted} kept=${kept}"
