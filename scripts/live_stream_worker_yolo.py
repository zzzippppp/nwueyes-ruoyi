#!/usr/bin/env python3
"""
萤石/RTSP/HLS 直播识别 Worker：限帧 + 丢缓冲 + 过线异步 ingest。

- 输入：--stream-url（RTSP 或 HLS）
- 检测：YOLO + ByteTrack，默认约 3fps
- 过线：即时抓拍落 log_library，async ingest（Java 侧线程池处理 embedding）
"""

from __future__ import annotations

import argparse
from collections import deque
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Dict, Optional
from urllib import request
from urllib.parse import urlparse

import cv2

from line_crossing import (
    PerTrackDoorGate,
    exit_hysteresis_margin,
    min_track_hits_for_event,
    tight_infer_margin,
)
from roi_scale import REF_HEIGHT, REF_WIDTH, scale_roi_and_line
from snapshot_quality import LiveEventCapture, save_live_event_snapshot


def is_local_media_url(url: str) -> bool:
    """本地文件回放（自测）时按视频帧率读帧，避免瞬间扫完导致漏事件。"""
    if not url:
        return False
    lower = url.lower()
    if lower.startswith(("rtsp://", "http://", "https://")):
        return False
    path = urlparse(url).path if "://" in url else url
    return os.path.isfile(path)


RTSP_OPEN_FAILED = 2
STREAM_CODEC_NOT_H264 = 4


def load_yolo(model_path: str):
    """延迟加载 YOLO，避免 import ultralytics 阻塞进程启动与首条日志输出。"""
    try:
        from ultralytics import YOLO
    except Exception as ex:  # pragma: no cover
        print(f"[fatal] 缺少 ultralytics 依赖: {ex}", file=sys.stderr, flush=True)
        sys.exit(2)
    return YOLO(model_path)


def post_event_async(
    executor: ThreadPoolExecutor,
    base_url: str,
    ingest_key: str,
    payload: dict,
):
    def _send():
        url = f"{base_url.rstrip('/')}/ingest/presence/event"
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = request.Request(url, data=body, method="POST")
        req.add_header("Content-Type", "application/json")
        if ingest_key:
            req.add_header("X-Ingest-Key", ingest_key)
        try:
            with request.urlopen(req, timeout=5) as resp:
                text = resp.read().decode("utf-8")
                print(f"[ingest-async] event={payload.get('eventType')} track={payload.get('trackKey')} code={resp.status}")
                if resp.status >= 400:
                    print(f"[ingest-async] resp={text}", file=sys.stderr)
        except Exception as ex:
            print(f"[ingest-async] failed track={payload.get('trackKey')}: {ex}", file=sys.stderr)

    executor.submit(_send)


def post_clip_async(executor: ThreadPoolExecutor, base_url: str, ingest_key: str, payload: dict):
    def _send():
        url = f"{base_url.rstrip('/')}/ingest/presence/clip"
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = request.Request(url, data=body, method="POST")
        req.add_header("Content-Type", "application/json")
        if ingest_key:
            req.add_header("X-Ingest-Key", ingest_key)
        try:
            with request.urlopen(req, timeout=8) as resp:
                print(
                    f"[clip-ingest] type={payload.get('clipType')} key={payload.get('clipKey')} "
                    f"scene={payload.get('sceneGroupId')} code={resp.status}",
                    flush=True,
                )
        except Exception as ex:
            print(f"[clip-ingest] failed key={payload.get('clipKey')}: {ex}", file=sys.stderr, flush=True)

    executor.submit(_send)


def clip_url_for(storage_root: str, event_time: dt.datetime, file_name: str) -> tuple[str, str]:
    day_dir = os.path.join(
        storage_root,
        "log_library",
        "clips",
        event_time.strftime("%Y"),
        event_time.strftime("%m"),
        event_time.strftime("%d"),
    )
    os.makedirs(day_dir, exist_ok=True)
    path = os.path.join(day_dir, file_name)
    url = (
        "/dashboard/storage/file/clip/"
        f"{event_time.strftime('%Y')}/{event_time.strftime('%m')}/{event_time.strftime('%d')}/{file_name}"
    )
    return path, url


def build_clip_file_name(
    task_id: str,
    clip_type: str,
    key: str,
    ts: float,
    max_len: int = 99,
) -> str:
    """
    生成短视频文件名，保证 <100 字符。

    旧格式会把 taskId 与 sceneGroupId 叠写两遍，轻松超过 100：
      live_<uuid>_scene_group_live_<uuid>_scene_12_<ts>.mp4
    新格式示例：
      0d21c6b7_sg_s12_1784858082.mp4
    """
    type_map = {
        "scene_group": "sg",
        "person_session": "ps",
    }
    type_short = type_map.get(str(clip_type or "clip"), "clip")

    raw_task = str(task_id or "task")
    if raw_task.startswith("live_"):
        raw_task = raw_task[5:]
    # 取任务号前 8 位（uuid 十六进制），足够区分同日片段
    task_short = "".join(ch for ch in raw_task if ch.isalnum())[:8] or "task"

    raw_key = str(key or "k")
    scene_match = re.search(r"(?:^|_)scene_(\d+)$", raw_key)
    if scene_match:
        key_short = f"s{scene_match.group(1)}"
    else:
        key_short = "".join(ch for ch in raw_key if ch.isalnum())[-12:] or "k"

    stamp = int(ts) if ts and ts > 0 else int(time.time())
    name = f"{task_short}_{type_short}_{key_short}_{stamp}.mp4"
    if len(name) <= max_len:
        return name
    # 极端兜底：压缩到 max_len
    stem_budget = max(8, max_len - 4)
    return name[:stem_budget] + ".mp4"


class FrameRingBuffer:
    """旧版 OpenCV 全帧环形缓冲（高分辨率易 OOM，仅保留兼容）。"""

    def __init__(self, max_seconds: float, fps_hint: float):
        self.max_seconds = max(1.0, max_seconds)
        self.frames = deque()
        self.max_frames = max(30, int(round(self.max_seconds * max(fps_hint, 10.0))) + 30)

    def append(self, ts: float, frame):
        self.frames.append((ts, frame.copy()))
        cutoff = ts - self.max_seconds
        while self.frames and (self.frames[0][0] < cutoff or len(self.frames) > self.max_frames):
            self.frames.popleft()

    def window(self, start_ts: float, end_ts: float):
        return [(ts, frame) for ts, frame in self.frames if start_ts <= ts <= end_ts]


class CopySegmentRing:
    """
    持续从 RTSP 用 ffmpeg -c copy 写短 TS 分段（尽量在关键帧处切开）。
    事件切片时：拼 TS 后 **libx264 重编码** 成可正常解码的 MP4。
    仅关键帧 copy 在长 GOP / RTSP 分段场景仍会花屏，故导出改为重编码。

    ffmpeg 退出或长时间无新数据时自动重启，避免认人还在跑、录像静默停住后导出旧残片。
    """

    def __init__(
        self,
        ffmpeg_bin: str,
        stream_url: str,
        work_dir: str,
        segment_sec: float = 2.0,
        keep_sec: float = 320.0,
        log_fn=None,
        stall_sec: float = 0.0,
        restart_backoff_sec: float = 3.0,
        max_restart_backoff_sec: float = 60.0,
    ):
        self.ffmpeg_bin = ffmpeg_bin
        self.stream_url = stream_url
        self.work_dir = work_dir
        self.segment_sec = max(1.0, float(segment_sec))
        self.keep_sec = max(self.segment_sec * 3.0, float(keep_sec))
        # 超过该时间无文件增长 / 无新分段，视为录像卡住
        self.stall_sec = float(stall_sec) if stall_sec and stall_sec > 0 else max(12.0, self.segment_sec * 5.0)
        self.restart_backoff_sec = max(1.0, float(restart_backoff_sec))
        self.max_restart_backoff_sec = max(self.restart_backoff_sec, float(max_restart_backoff_sec))
        self._log_fn = log_fn
        self.lock = threading.RLock()
        self.proc: Optional[subprocess.Popen] = None
        self._stop = threading.Event()
        self._poller: Optional[threading.Thread] = None
        self.segments: list[dict] = []  # {path, start_ts, end_ts, closed, size, mtime}
        self._started_at = 0.0
        self._last_progress_at = 0.0
        self._last_progress_sig: Optional[tuple] = None
        self._next_restart_at = 0.0
        self._restart_count = 0
        self._spawn_generation = 0

    def _log(self, stage: str, **fields):
        if self._log_fn:
            self._log_fn(stage, **fields)
            return
        parts = [f"[clip-ring] {stage}"]
        for key, value in fields.items():
            if value is not None:
                parts.append(f"{key}={value}")
        print(" ".join(parts), flush=True)

    def start(self) -> bool:
        if not self.stream_url:
            self._log("ring-start-skip", reason="empty-stream-url")
            return False
        self._stop.clear()
        self._clear_disk_segments(reason="start")
        ok = self._spawn_ffmpeg(reason="start")
        if self._poller is None or not self._poller.is_alive():
            self._poller = threading.Thread(target=self._poll_loop, name="clip-ring-poller", daemon=True)
            self._poller.start()
        return ok

    def stop(self):
        self._stop.set()
        self._kill_ffmpeg(graceful=True)
        if self._poller is not None:
            self._poller.join(timeout=2.0)
            self._poller = None
        self._clear_disk_segments(reason="stop")
        try:
            if os.path.isdir(self.work_dir) and not os.listdir(self.work_dir):
                os.rmdir(self.work_dir)
        except Exception:
            pass
        self._log("ring-stopped")

    def healthy(self) -> bool:
        """近期是否仍有录像数据写入。"""
        if self._stop.is_set():
            return False
        if self.proc is None or self.proc.poll() is not None:
            return False
        if self._last_progress_at <= 0:
            return False
        return (time.time() - self._last_progress_at) <= self.stall_sec

    def _safe_unlink(self, path: Optional[str]):
        if not path:
            return
        try:
            if os.path.isfile(path):
                os.remove(path)
        except Exception:
            pass

    def _clear_disk_segments(self, reason: str = ""):
        with self.lock:
            for seg in list(self.segments):
                self._safe_unlink(seg.get("path"))
            self.segments.clear()
            self._last_progress_sig = None
        try:
            if os.path.isdir(self.work_dir):
                for name in os.listdir(self.work_dir):
                    if name.startswith("seg_") and name.endswith(".ts"):
                        self._safe_unlink(os.path.join(self.work_dir, name))
                    elif name.endswith(".concat.txt"):
                        self._safe_unlink(os.path.join(self.work_dir, name))
        except FileNotFoundError:
            pass
        if reason:
            self._log("ring-cleared", reason=reason)

    def _build_ffmpeg_cmd(self) -> list[str]:
        os.makedirs(self.work_dir, exist_ok=True)
        pattern = os.path.join(self.work_dir, "seg_%06d.ts")
        common_tail = [
            "-map",
            "0:v:0",
            "-an",
            "-c",
            "copy",
            "-f",
            "segment",
            "-segment_time",
            f"{self.segment_sec:.3f}",
            "-reset_timestamps",
            "1",
            # 只在关键帧处分段，保证每段 TS 从 IDR 起，copy 拼接才不花屏
            "-break_non_keyframes",
            "0",
            "-strftime",
            "0",
            pattern,
        ]
        if self.stream_url.lower().startswith("rtsp://"):
            return [
                self.ffmpeg_bin,
                "-hide_banner",
                "-loglevel",
                "error",
                "-rtsp_transport",
                "tcp",
                "-i",
                self.stream_url,
                *common_tail,
            ]
        return [
            self.ffmpeg_bin,
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            self.stream_url,
            *common_tail,
        ]

    def _kill_ffmpeg(self, graceful: bool = False):
        proc = self.proc
        self.proc = None
        if proc is None:
            return
        if proc.poll() is not None:
            return
        try:
            if graceful and proc.stdin:
                proc.stdin.write(b"q")
                proc.stdin.flush()
        except Exception:
            pass
        try:
            proc.wait(timeout=8 if graceful else 2)
            return
        except Exception:
            pass
        try:
            proc.kill()
            proc.wait(timeout=3)
        except Exception:
            pass

    def _spawn_ffmpeg(self, reason: str = "start") -> bool:
        self._kill_ffmpeg(graceful=False)
        cmd = self._build_ffmpeg_cmd()
        try:
            self.proc = subprocess.Popen(
                cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0,
            )
        except Exception as ex:
            self.proc = None
            self._log("ring-start-failed", reason=reason, error=str(ex))
            return False
        now = time.time()
        self._started_at = now
        self._last_progress_at = now
        self._spawn_generation += 1
        self._log(
            "ring-started",
            reason=reason,
            pid=self.proc.pid,
            generation=self._spawn_generation,
            dir=self.work_dir,
            segment=f"{self.segment_sec:.1f}s",
            keep=f"{self.keep_sec:.0f}s",
            stall=f"{self.stall_sec:.0f}s",
            mode="ffmpeg-copy-segment-keyframe",
        )
        return True

    def _poll_loop(self):
        while not self._stop.is_set():
            try:
                self._refresh_segments()
                self._purge_old()
                self._maybe_restart()
            except Exception as ex:
                self._log("ring-poll-error", error=str(ex))
            self._stop.wait(0.4)

    def _maybe_restart(self):
        if self._stop.is_set():
            return
        now = time.time()
        if now < self._next_restart_at:
            return

        proc = self.proc
        exited = proc is None or proc.poll() is not None
        grace_ok = self._started_at > 0 and (now - self._started_at) >= self.stall_sec
        stalled = grace_ok and self._last_progress_at > 0 and (now - self._last_progress_at) >= self.stall_sec
        if not exited and not stalled:
            # 恢复正常后衰减重试计数
            if self._restart_count > 0 and (now - self._last_progress_at) < self.segment_sec * 2:
                self._restart_count = 0
            return

        exit_code = None
        if proc is not None:
            exit_code = proc.poll()
        reason = "ffmpeg-exited" if exited else "no-progress"
        self._restart_count += 1
        backoff = min(
            self.max_restart_backoff_sec,
            self.restart_backoff_sec * (2 ** min(self._restart_count - 1, 5)),
        )
        self._log(
            "ring-restart",
            reason=reason,
            exit=exit_code,
            stalled_sec=f"{max(0.0, now - self._last_progress_at):.1f}",
            attempt=self._restart_count,
            backoff=f"{backoff:.1f}s",
        )
        self._kill_ffmpeg(graceful=False)
        self._clear_disk_segments(reason=f"restart-{reason}")
        ok = self._spawn_ffmpeg(reason=f"restart-{reason}")
        # 无论成败都进入冷却，避免狂重启打爆 CPU / 摄像头
        self._next_restart_at = time.time() + (2.0 if ok else backoff)
        if not ok:
            self._next_restart_at = time.time() + backoff

    def _refresh_segments(self):
        now = time.time()
        try:
            names = sorted(
                n for n in os.listdir(self.work_dir) if n.startswith("seg_") and n.endswith(".ts")
            )
        except FileNotFoundError:
            return

        progress_sizes: list[int] = []
        with self.lock:
            known = {s["path"]: s for s in self.segments}
            ordered_paths = [os.path.join(self.work_dir, n) for n in names]
            grew = False
            for path in ordered_paths:
                try:
                    st = os.stat(path)
                    size = int(st.st_size)
                    mtime = float(st.st_mtime)
                except OSError:
                    continue
                if size < 64:
                    continue
                progress_sizes.append(size)
                seg = known.get(path)
                if seg is None:
                    seg = {
                        "path": path,
                        "start_ts": now,
                        "end_ts": now,
                        "closed": False,
                        "size": size,
                        "mtime": mtime,
                    }
                    if self.segments:
                        prev = self.segments[-1]
                        if not prev.get("closed"):
                            prev["end_ts"] = max(prev.get("start_ts", now), prev.get("end_ts", now))
                            prev["closed"] = True
                    self.segments.append(seg)
                    grew = True
                else:
                    prev_size = int(seg.get("size") or 0)
                    seg["mtime"] = mtime
                    if size > prev_size:
                        seg["size"] = size
                        seg["end_ts"] = now
                        grew = True
                    else:
                        seg["size"] = size
                        # 文件不再增长：不要把 end_ts 刷成 now（否则旧残片会伪装成新录像）
                        if not seg.get("closed") and path != ordered_paths[-1]:
                            seg["closed"] = True

            sig = (tuple(names), tuple(progress_sizes))
            if progress_sizes and (grew or sig != self._last_progress_sig):
                self._last_progress_at = now
                self._last_progress_sig = sig
            elif not progress_sizes:
                # 目录空了不刷新进度时间，便于 stall 检测触发重启
                self._last_progress_sig = None

    def _purge_old(self):
        now = time.time()
        cutoff = now - self.keep_sec
        # 尾部分段若长时间不增长，也当成过期丢掉，避免永久残留旧 TS
        stale_tail_cutoff = now - max(self.stall_sec, self.segment_sec * 3.0)
        with self.lock:
            keep: list[dict] = []
            total = len(self.segments)
            for idx, seg in enumerate(self.segments):
                end_ts = float(seg.get("end_ts") or 0.0)
                is_tail = idx >= total - 2
                fresh_enough = end_ts >= cutoff
                if is_tail and end_ts < stale_tail_cutoff:
                    self._safe_unlink(seg.get("path"))
                    continue
                if is_tail or fresh_enough:
                    keep.append(seg)
                else:
                    self._safe_unlink(seg.get("path"))
            self.segments = keep

    def _ffprobe_bin(self) -> str:
        bin_path = self.ffmpeg_bin
        lower = bin_path.lower()
        if lower.endswith("ffmpeg.exe"):
            return bin_path[: -len("ffmpeg.exe")] + "ffprobe.exe"
        if lower.endswith("ffmpeg"):
            return bin_path[: -len("ffmpeg")] + "ffprobe"
        probed = shutil.which("ffprobe")
        return probed or "ffprobe"

    def _run_ffmpeg(self, cmd: list[str], timeout: float = 90.0) -> tuple[bool, str]:
        try:
            completed = subprocess.run(
                cmd,
                capture_output=True,
                timeout=timeout,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0,
            )
        except Exception as ex:
            return False, str(ex)
        if completed.returncode != 0:
            err = (completed.stderr or b"").decode("utf-8", errors="ignore")[:400]
            return False, err or f"code={completed.returncode}"
        return True, ""

    def _probe_keyframe_times(self, media_path: str) -> list[float]:
        """返回媒体时间轴上的关键帧 pts（秒）。"""
        cmd = [
            self._ffprobe_bin(),
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "packet=pts_time,flags",
            "-of",
            "csv=p=0",
            media_path,
        ]
        try:
            completed = subprocess.run(
                cmd,
                capture_output=True,
                timeout=60,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0,
            )
        except Exception:
            return []
        if completed.returncode != 0:
            return []
        text = (completed.stdout or b"").decode("utf-8", errors="ignore")
        times: list[float] = []
        for line in text.splitlines():
            parts = [p.strip() for p in line.split(",")]
            if len(parts) < 2:
                continue
            try:
                pts = float(parts[0])
            except ValueError:
                continue
            if "K" in parts[1].upper():
                times.append(pts)
        return times

    def _choose_keyframe_start(self, key_times: list[float], desired_sec: float) -> float:
        """选 <= desired 的最近关键帧；没有则用第一个关键帧。"""
        if not key_times:
            return 0.0
        best = key_times[0]
        for t in key_times:
            if t <= desired_sec + 1e-3:
                best = t
            else:
                break
        return max(0.0, best)

    def export_clip(self, start_ts: float, end_ts: float, out_path: str) -> bool:
        if end_ts <= start_ts:
            end_ts = start_ts + 0.5
        # 给正在写入的分段一点落盘时间
        time.sleep(min(0.8, self.segment_sec * 0.35))
        self._refresh_segments()

        now = time.time()
        if not self.healthy():
            self._log(
                "ring-export-unhealthy",
                start=f"{start_ts:.3f}",
                end=f"{end_ts:.3f}",
                last_progress=f"{max(0.0, now - self._last_progress_at):.1f}s-ago",
                segs=len(self.segments),
            )
            self._next_restart_at = min(self._next_restart_at, now)
            return False

        # 尽量等覆盖 end 的尾段闭合
        wait_deadline = time.time() + min(2.5, self.segment_sec * 1.2)
        while time.time() < wait_deadline:
            self._refresh_segments()
            with self.lock:
                if not self.segments:
                    break
                tail = self.segments[-1]
                covers_end = float(tail.get("start_ts") or 0.0) <= end_ts
                if (not covers_end) or tail.get("closed"):
                    break
            time.sleep(0.25)

        fresh_cutoff = now - max(self.keep_sec, end_ts - start_ts + self.segment_sec * 2.0)
        with self.lock:
            chosen = []
            for seg in self.segments:
                if seg.get("end_ts", 0.0) < start_ts or seg.get("start_ts", 0.0) > end_ts:
                    continue
                if float(seg.get("end_ts") or 0.0) < fresh_cutoff:
                    continue
                mtime = float(seg.get("mtime") or 0.0)
                if mtime > 0 and mtime < fresh_cutoff:
                    continue
                path = seg.get("path")
                if not path or not os.path.isfile(path):
                    continue
                chosen.append(dict(seg))

        if not chosen:
            self._log(
                "ring-export-empty",
                start=f"{start_ts:.3f}",
                end=f"{end_ts:.3f}",
                segs=len(self.segments),
                healthy=self.healthy(),
            )
            return False

        wall_origin = float(chosen[0].get("start_ts") or start_ts)
        desired_media_start = max(0.0, start_ts - wall_origin)
        desired_duration = max(0.5, end_ts - start_ts)

        list_path = out_path + ".concat.txt"
        temp_ts = out_path + ".src.ts"
        os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
        try:
            with open(list_path, "w", encoding="utf-8") as fp:
                for seg in chosen:
                    path = seg["path"].replace("\\", "/")
                    fp.write(f"file '{path}'\n")

            # 1) 先 copy 拼成临时 TS（可能仍有时间戳/GOP 问题，下一步重编码消化）
            ok, err = self._run_ffmpeg(
                [
                    self.ffmpeg_bin,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "concat",
                    "-safe",
                    "0",
                    "-i",
                    list_path,
                    "-c",
                    "copy",
                    "-f",
                    "mpegts",
                    temp_ts,
                ],
                timeout=90,
            )
            if not ok:
                self._log("ring-export-concat-failed", error=err)
                self._safe_unlink(out_path)
                return False

            # 2) 重编码为正常 MP4：彻底消除缺参考帧花屏
            #    -ss 放在 -i 后做精确裁切；ultrafast + CRF18 兼顾速度与后续识别清晰度
            cut_duration = max(0.5, desired_duration + 0.3)
            ok, err = self._run_ffmpeg(
                [
                    self.ffmpeg_bin,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-fflags",
                    "+genpts+discardcorrupt",
                    "-err_detect",
                    "ignore_err",
                    "-i",
                    temp_ts,
                    "-ss",
                    f"{desired_media_start:.3f}",
                    "-t",
                    f"{cut_duration:.3f}",
                    "-an",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "ultrafast",
                    "-crf",
                    "18",
                    "-pix_fmt",
                    "yuv420p",
                    "-movflags",
                    "+faststart",
                    out_path,
                ],
                timeout=180,
            )
            if not ok:
                self._log("ring-export-reencode-failed", error=err)
                self._safe_unlink(out_path)
                return False

            size = os.path.getsize(out_path) if os.path.isfile(out_path) else 0
            min_bytes = 8 * 1024
            if size < min_bytes:
                self._log("ring-export-too-small", bytes=size, file=out_path)
                self._safe_unlink(out_path)
                return False
            self._log(
                "ring-exported",
                mode="reencode-x264",
                file=out_path,
                bytes=size,
                segs=len(chosen),
                start=f"{desired_media_start:.3f}s",
                duration=f"{cut_duration:.3f}s",
            )
            return True
        except Exception as ex:
            self._log("ring-export-error", error=str(ex))
            self._safe_unlink(out_path)
            return False
        finally:
            self._safe_unlink(list_path)
            self._safe_unlink(temp_ts)


def resolve_ffmpeg_bin(configured: str = "") -> str:
    candidates = []
    if configured and str(configured).strip():
        candidates.append(str(configured).strip())
    which = shutil.which("ffmpeg")
    if which:
        candidates.append(which)
    candidates.extend(
        [
            r"C:\ffmpeg\bin\ffmpeg.exe",
            r"C:\ProgramData\chocolatey\bin\ffmpeg.exe",
        ]
    )
    for path in candidates:
        if path and os.path.isfile(path):
            return path
        if path and shutil.which(path):
            return path
    return which or "ffmpeg"


class ClipRecorder:
    """人体触发切片：后台持续 -c copy 环形缓冲，事件时按预录/后录切出原画质 MP4。"""

    def __init__(self, args, fps: float, width: int, height: int, http_pool: ThreadPoolExecutor):
        self.args = args
        self.source_fps = max(1.0, fps if fps > 0 else 25.0)
        self.writer_fps = self.source_fps
        self.width = width
        self.height = height
        self.http_pool = http_pool
        self.lock = threading.RLock()
        self.enabled = str(args.clip_enabled).lower() == "true"
        self.local_recording_enabled = str(args.clip_local_recording_enabled).lower() == "true"
        self.pre_roll = max(0.0, float(args.clip_pre_roll_sec))
        self.post_roll = max(0.0, args.clip_post_roll_sec)
        self.track_lost = max(0.5, args.clip_track_lost_sec)
        self.scene_gap = max(0.5, args.clip_scene_merge_gap_sec)
        self.max_duration = max(30.0, float(getattr(args, "clip_max_duration_sec", 300.0) or 300.0))
        self.ffmpeg_bin = resolve_ffmpeg_bin(getattr(args, "clip_ffmpeg_path", "") or "")
        self.stream_url = str(getattr(args, "stream_url", "") or "")
        self.segment_sec = max(1.0, float(getattr(args, "clip_ring_segment_sec", 2.0) or 2.0))
        self.buffer = None
        self.ring: Optional[CopySegmentRing] = None
        self.sessions: Dict[str, dict] = {}
        self.pending: list[dict] = []
        self.scene: Optional[dict] = None
        self.scene_counter = 0
        if self.enabled and self.local_recording_enabled:
            keep_sec = self.pre_roll + self.max_duration + self.post_roll + self.segment_sec * 3.0
            ring_parent = os.path.join(
                str(args.storage_root),
                "log_library",
                "clips",
                "_ring",
            )
            current_task = str(getattr(args, "task_id", "live"))
            self._cleanup_stale_ring_dirs(ring_parent, current_task)
            ring_dir = os.path.join(ring_parent, current_task)
            self.ring = CopySegmentRing(
                self.ffmpeg_bin,
                self.stream_url,
                ring_dir,
                segment_sec=self.segment_sec,
                keep_sec=keep_sec,
                log_fn=lambda stage, **fields: self._log(stage, **fields),
            )
            self.ring.start()
        if self.enabled:
            self._log(
                "recorder-ready",
                mode="copy-segment-ring" if self.ring is not None else "timer-only",
                source_fps=f"{self.source_fps:.2f}",
                frame_size=f"{self.width}x{self.height}",
                pre_roll=f"{self.pre_roll:.1f}s",
                post_roll=f"{self.post_roll:.1f}s",
                track_lost=f"{self.track_lost:.1f}s",
                scene_gap=f"{self.scene_gap:.1f}s",
                max_duration=f"{self.max_duration:.0f}s",
                segment=f"{self.segment_sec:.1f}s",
                ffmpeg=self.ffmpeg_bin if self.local_recording_enabled else "-",
                stream=("rtsp" if self.stream_url.lower().startswith("rtsp://") else "other"),
            )

    def _cleanup_stale_ring_dirs(self, ring_parent: str, current_task: str):
        """启动时清理非当前 task_id 的残留 _ring 子目录，防止异常退出后磁盘泄漏。"""
        if not os.path.isdir(ring_parent):
            return
        import shutil
        for name in os.listdir(ring_parent):
            if name == current_task:
                continue
            stale = os.path.join(ring_parent, name)
            if os.path.isdir(stale):
                try:
                    shutil.rmtree(stale)
                    print(f"[clip] cleanup-stale-ring dir={stale}", flush=True)
                except Exception as exc:
                    print(f"[clip] cleanup-stale-ring-err dir={stale} err={exc}", flush=True)

    def _log(self, stage: str, **fields):
        parts = [f"[clip] {stage}"]
        for key, value in fields.items():
            if value is None:
                continue
            parts.append(f"{key}={value}")
        print(" ".join(parts), flush=True)

    def _iso(self, ts: Optional[float]) -> str:
        if ts is None:
            return "-"
        return iso_from_ts(ts)

    def _pop_pending_session(self, track_key: str) -> Optional[dict]:
        for idx, sess in enumerate(self.pending):
            if sess.get("track_key") == track_key:
                return self.pending.pop(idx)
        return None

    def current_scene_group_id(self) -> Optional[str]:
        with self.lock:
            if self.scene is None:
                return None
            return self.scene.get("scene_group_id")

    def add_frame(self, ts: float, frame):
        """帧由识别线程读取；本地录像走独立 copy 环形缓冲，不再缓存全帧。"""
        if not self.enabled:
            return
        return

    def update_seen(self, track_key: str, ts: float):
        if not self.enabled:
            return
        with self.lock:
            if self.scene is None:
                self.scene_counter += 1
                scene_group_id = f"{self.args.task_id}_scene_{self.scene_counter}"
                self.scene = {
                    "scene_group_id": scene_group_id,
                    "start_ts": ts,
                    "last_seen_ts": ts,
                    "tracks": set(),
                }
                self._open_clip(self.scene, "scene_group", scene_group_id, ts)
                self._log(
                    "scene-start",
                    scene=scene_group_id,
                    start=self._iso(ts),
                    source_track=track_key,
                )
            self.scene["last_seen_ts"] = max(self.scene["last_seen_ts"], ts)
            if track_key not in self.scene["tracks"]:
                self.scene["tracks"].add(track_key)
                self._log(
                    "scene-track-join",
                    scene=self.scene["scene_group_id"],
                    track=track_key,
                    active_tracks=len(self.scene["tracks"]),
                    seen_at=self._iso(ts),
                )

            sess = self.sessions.get(track_key)
            if sess is None:
                resumed = self._pop_pending_session(track_key)
                if resumed is not None:
                    last_seen_ts = resumed.get("last_seen_ts", ts)
                    resumed["last_seen_ts"] = ts
                    resumed.pop("finalize_after_ts", None)
                    resumed["scene_group_id"] = self.scene["scene_group_id"]
                    self.sessions[track_key] = resumed
                    self._log(
                        "session-resume",
                        track=track_key,
                        scene=resumed["scene_group_id"],
                        resumed_at=self._iso(ts),
                        gap=f"{max(0.0, ts - last_seen_ts):.1f}s",
                    )
                else:
                    sess = {
                        "track_key": track_key,
                        "scene_group_id": self.scene["scene_group_id"],
                        "start_ts": ts,
                        "last_seen_ts": ts,
                    }
                    # 只录场景片，不再为每个人单独导出 person_session（避免重复 MP4）
                    self.sessions[track_key] = sess
                    self._log(
                        "session-start",
                        track=track_key,
                        scene=sess["scene_group_id"],
                        start=self._iso(ts),
                        export="scene-only",
                    )
            else:
                sess["last_seen_ts"] = ts

    def tick(self, ts: float):
        if not self.enabled:
            return
        with self.lock:
            for track_key, sess in list(self.sessions.items()):
                opened = sess.get("opened_at_ts", sess.get("start_ts", ts))
                if ts - opened >= self.max_duration:
                    self.sessions.pop(track_key)
                    sess["finalize_after_ts"] = ts
                    self.pending.append(sess)
                    self._log(
                        "session-max-duration",
                        track=track_key,
                        scene=sess.get("scene_group_id"),
                        duration=f"{ts - opened:.1f}s",
                        limit=f"{self.max_duration:.0f}s",
                    )
                    continue
                if ts - sess["last_seen_ts"] >= self.track_lost:
                    self.sessions.pop(track_key)
                    sess["finalize_after_ts"] = sess["last_seen_ts"] + self.post_roll
                    self.pending.append(sess)
                    self._log(
                        "session-wait-post",
                        track=track_key,
                        scene=sess.get("scene_group_id"),
                        last_seen=self._iso(sess["last_seen_ts"]),
                        finalize_after=self._iso(sess["finalize_after_ts"]),
                        post_roll=f"{self.post_roll:.1f}s",
                    )

            ready = [sess for sess in self.pending if ts >= sess["finalize_after_ts"]]
            self.pending = [sess for sess in self.pending if ts < sess["finalize_after_ts"]]
            for sess in ready:
                self._finalize_session(sess)

            if self.scene is not None:
                opened = self.scene.get("opened_at_ts", self.scene.get("start_ts", ts))
                if ts - opened >= self.max_duration:
                    self._log(
                        "scene-max-duration",
                        scene=self.scene["scene_group_id"],
                        duration=f"{ts - opened:.1f}s",
                        limit=f"{self.max_duration:.0f}s",
                    )
                    for track_key, sess in list(self.sessions.items()):
                        self.sessions.pop(track_key)
                        self._finalize_session(sess)
                    for sess in list(self.pending):
                        self._finalize_session(sess)
                    self.pending.clear()
                    self._finalize_scene(self.scene)
                    self.scene = None
                elif not self.sessions and not self.pending:
                    if ts - self.scene["last_seen_ts"] >= self.scene_gap:
                        self._log(
                            "scene-wait-close-done",
                            scene=self.scene["scene_group_id"],
                            last_seen=self._iso(self.scene["last_seen_ts"]),
                            idle_for=f"{ts - self.scene['last_seen_ts']:.1f}s",
                            active_tracks=len(self.scene.get("tracks", set())),
                        )
                        self._finalize_scene(self.scene)
                        self.scene = None

    def finalize_all(self):
        if not self.enabled:
            return
        with self.lock:
            now = time.time()
            self._log(
                "finalize-all",
                active_sessions=len(self.sessions),
                pending_sessions=len(self.pending),
                scene_open="yes" if self.scene is not None else "no",
            )
            for sess in list(self.sessions.values()) + self.pending:
                sess["finalize_after_ts"] = min(now, sess.get("last_seen_ts", now) + self.post_roll)
                self._finalize_session(sess)
            self.sessions.clear()
            self.pending.clear()
            if self.scene is not None:
                self._finalize_scene(self.scene)
                self.scene = None
        if self.ring is not None:
            self.ring.stop()
            self.ring = None

    def _finalize_session(self, sess: dict):
        # 个人会话仅用于场景归属与时长统计，不再导出/入库独立 MP4
        start_ts = max(0.0, sess["start_ts"] - self.pre_roll)
        end_ts = sess["last_seen_ts"] + self.post_roll
        self._log(
            "session-finalized",
            track=sess["track_key"],
            scene=sess.get("scene_group_id"),
            start=self._iso(start_ts),
            end=self._iso(end_ts),
            duration=f"{max(0.0, end_ts - start_ts):.1f}s",
            export="skipped-person-session",
        )

    def _finalize_scene(self, scene: dict):
        start_ts = max(0.0, scene["start_ts"] - self.pre_roll)
        end_ts = scene["last_seen_ts"] + self.post_roll
        scene_id = scene["scene_group_id"]
        self._export_clip(scene, start_ts, end_ts)
        url = scene.get("url", "")
        payload = self._payload("scene_group", f"scene:{scene_id}", scene_id, "", start_ts, end_ts, url)
        self._log(
            "scene-finalized",
            scene=scene_id,
            clip_key=payload["clipKey"],
            start=payload["startTime"],
            end=payload["endTime"],
            duration=f"{max(0.0, end_ts - start_ts):.1f}s",
            tracks=len(scene.get("tracks", set())),
            file=scene.get("path", ""),
            url=url or "-",
        )
        post_clip_async(self.http_pool, self.args.ingest_base_url, self.args.ingest_key, payload)

    def _payload(self, clip_type: str, clip_key: str, scene_group_id: str, track_key: str,
                 start_ts: float, end_ts: float, url: str) -> dict:
        has_local = bool(url)
        payload = {
            "clipKey": clip_key,
            "clipType": clip_type,
            "sceneGroupId": scene_group_id,
            "cameraId": self.args.camera_id,
            "trackKey": track_key,
            "startTime": iso_from_ts(start_ts),
            "endTime": iso_from_ts(end_ts),
            "preRollSec": self.pre_roll,
            "postRollSec": self.post_roll,
            "videoUrl": url,
            "status": "local_recorded" if has_local else "pending_playback",
            "preferLocal": True,
        }
        # 已有本地 MP4 时不再带萤石设备信息，避免 Java 再去拉回放覆盖本地片
        if not has_local:
            payload["deviceSerial"] = self.args.device_serial
            payload["channelNo"] = self.args.channel_no
            payload["validCode"] = self.args.valid_code
        return payload

    def _open_clip(self, state: dict, clip_type: str, key: str, ts: float):
        state["clip_type"] = clip_type
        state["clip_key"] = key
        state["opened_at_ts"] = ts
        state["frames"] = 0
        state["source_frames"] = 0
        state["path"] = ""
        state["url"] = ""
        if not self.local_recording_enabled:
            self._log(
                "clip-timer-open",
                clip_type=clip_type,
                key=key,
                start=self._iso(ts),
                source="timer-only",
            )
            return

        if self.ring is None:
            self._log("clip-open-skip", clip_type=clip_type, key=key, reason="ring-not-ready")
            return

        stamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
        file_name = build_clip_file_name(self.args.task_id, clip_type, key, ts)
        path, url = clip_url_for(self.args.storage_root, stamp, file_name)
        state["path"] = path
        state["url"] = url
        self._log(
            "clip-armed",
            clip_type=clip_type,
            key=key,
            file=path,
            file_name=file_name,
            name_len=len(file_name),
            url=url,
            start=self._iso(ts),
            mode="copy-segment-ring",
            pre_roll=f"{self.pre_roll:.1f}s",
        )

    def _export_clip(self, state: dict, start_ts: float, end_ts: float):
        if not self.local_recording_enabled:
            self._log(
                "clip-timer-closed",
                clip_type=state.get("clip_type"),
                key=state.get("clip_key"),
                source="timer-only",
            )
            return
        path = state.get("path") or ""
        url = state.get("url") or ""
        if not path:
            stamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
            file_name = build_clip_file_name(
                self.args.task_id,
                state.get("clip_type", "clip"),
                str(state.get("clip_key") or "clip"),
                start_ts,
            )
            path, url = clip_url_for(self.args.storage_root, stamp, file_name)
            state["path"] = path
            state["url"] = url
        if self.ring is None:
            state["url"] = ""
            self._log("clip-export-skip", key=state.get("clip_key"), reason="ring-missing")
            return
        ok = self.ring.export_clip(start_ts, end_ts, path)
        if not ok:
            state["url"] = ""
            self._log(
                "clip-closed-empty",
                clip_type=state.get("clip_type"),
                key=state.get("clip_key"),
                file=path,
            )
            return
        state["url"] = url
        self._log(
            "clip-closed",
            clip_type=state.get("clip_type"),
            key=state.get("clip_key"),
            file=path,
            bytes=os.path.getsize(path) if os.path.isfile(path) else 0,
            mode="copy-segment-ring",
            start=self._iso(start_ts),
            end=self._iso(end_ts),
        )


def finalize_live_capture(
    capture: LiveEventCapture,
    args,
    http_pool: ThreadPoolExecutor,
    conf: float,
    scene_group_id: str = "",
):
    face_img, body_img = capture.finalize_images()
    quality = capture.quality_flag()
    face_url, body_url, snapshot_url = save_live_event_snapshot(
        args.storage_root,
        args.task_id,
        capture.track_key,
        capture.event_type,
        face_img,
        body_img,
        capture.finalize_snapshot_frame(),
    )
    payload = {
        "eventType": capture.event_type,
        "cameraId": args.camera_id,
        "trackKey": capture.track_key,
        "eventTime": iso_now(),
        "faceImageUrl": face_url,
        "bodyImageUrl": body_url,
        "snapshotUrl": snapshot_url,
        "bestMatchScore": conf,
        "async": True,
        "qualityFlag": quality,
    }
    snapshot_bbox = capture.finalize_snapshot_bbox()
    if snapshot_bbox:
        payload["snapshotBbox"] = snapshot_bbox
    if scene_group_id:
        payload["sceneGroupId"] = scene_group_id
    post_event_async(http_pool, args.ingest_base_url, args.ingest_key, payload)
    hunt_note = ""
    if capture.event_type == "enter":
        hunt_note = (
            f" huntSec={capture.hunt_elapsed_sec(time.time()):.1f}"
            f" faceFound={'yes' if capture.best_face_img is not None else 'no'}"
        )
    print(
        f"[capture-done] {capture.track_key} {capture.event_type} samples={capture.samples} "
        f"face={'yes' if face_url else 'no'} body={'yes' if body_url else 'no'} quality={quality}{hunt_note}",
        flush=True,
    )


def flush_expired_captures(
    active_captures: Dict[str, LiveEventCapture],
    now: float,
    args,
    http_pool: ThreadPoolExecutor,
    track_conf: Dict[str, float],
    clip_recorder: Optional["ClipRecorder"] = None,
) -> tuple[int, int]:
    enter_delta = 0
    exit_delta = 0
    expired_keys = [k for k, cap in active_captures.items() if cap.expired(now)]
    scene_id = clip_recorder.current_scene_group_id() if clip_recorder is not None else ""
    for track_key in expired_keys:
        cap = active_captures.pop(track_key)
        conf = track_conf.pop(track_key, 0.0)
        finalize_live_capture(cap, args, http_pool, conf, scene_group_id=scene_id or "")
        if cap.event_type == "enter":
            enter_delta += 1
        else:
            exit_delta += 1
    return enter_delta, exit_delta


def iso_now() -> str:
    tz = dt.timezone(dt.timedelta(hours=8))
    return dt.datetime.now(tz).isoformat(timespec="seconds")


def iso_from_ts(ts: float) -> str:
    tz = dt.timezone(dt.timedelta(hours=8))
    return dt.datetime.fromtimestamp(ts, tz).isoformat(timespec="seconds")


def configure_ffmpeg_options(stream_protocol: str):
    if stream_protocol == "rtsp":
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = (
            "rtsp_transport;tcp|stimeout;5000000|fflags;nobuffer|max_delay;500000"
        )
    elif stream_protocol == "flv":
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "fflags;nobuffer|max_delay;500000"
    else:
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "fflags;nobuffer|probesize;5000000|analyzeduration;5000000"


def open_capture(stream_url: str, stream_protocol: str, rtsp_buffer_size: int) -> cv2.VideoCapture:
    configure_ffmpeg_options(stream_protocol)
    cap = cv2.VideoCapture(stream_url, cv2.CAP_FFMPEG)
    if stream_protocol == "rtsp":
        try:
            cap.set(cv2.CAP_PROP_BUFFERSIZE, rtsp_buffer_size)
        except Exception:
            pass
    return cap


def wait_first_frame(cap: cv2.VideoCapture, timeout_sec: float, stream_protocol: str):
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        if stream_protocol in ("hls", "flv"):
            ret, frame = cap.read()
        else:
            ret, frame = cap.read()
        if ret and frame is not None and frame.size > 0:
            return True, frame
        time.sleep(0.3)
    return False, None


def try_open_stream(
    stream_urls: list[str],
    stream_protocol: str,
    rtsp_buffer_size: int,
    open_timeout_sec: float,
) -> tuple[Optional[cv2.VideoCapture], Optional[object], int]:
    """在一个超时窗口内尝试打开流；失败返回 (None, None, last_url_index)。"""
    started_at = time.time()
    open_deadline = started_at + max(5.0, open_timeout_sec)
    url_index = 0
    last_progress_at = 0.0
    video_cap = open_capture(stream_urls[url_index], stream_protocol, rtsp_buffer_size)
    probe = None
    while time.time() < open_deadline:
        now = time.time()
        if now - last_progress_at >= 10.0:
            print(
                f"[live] connecting... elapsed={int(now - started_at)}s protocol={stream_protocol} "
                f"urlTry={url_index + 1}/{len(stream_urls)}",
                flush=True,
            )
            last_progress_at = now

        if not video_cap.isOpened():
            video_cap.release()
            video_cap = open_capture(stream_urls[url_index], stream_protocol, rtsp_buffer_size)
            time.sleep(0.3)
            continue

        if stream_protocol in ("flv", "hls"):
            remaining = open_deadline - time.time()
            if remaining > 0:
                probe = collect_probe_frame(
                    video_cap,
                    time.time() + min(8.0, remaining),
                    stream_protocol,
                )
            if probe is not None and not is_ezviz_codec_error_frame(probe):
                ph, pw = probe.shape[:2]
                print(f"[live] probe warmup selected {pw}x{ph}", flush=True)
                return video_cap, probe, url_index
            if probe is not None and is_ezviz_codec_error_frame(probe):
                video_cap.release()
                return None, probe, url_index
        else:
            ret, frame = video_cap.read()
            if ret and frame is not None and frame.size > 0 and not is_ezviz_codec_error_frame(frame):
                if url_index > 0:
                    print(
                        f"[live] rtsp fallback ok index={url_index} url={stream_urls[url_index][:80]}",
                        flush=True,
                    )
                return video_cap, frame, url_index

        video_cap.release()
        url_index += 1
        if url_index >= len(stream_urls):
            url_index = 0
            time.sleep(0.5)
            continue
        print(f"[live] rtsp retry path={stream_urls[url_index]}", flush=True)
        video_cap = open_capture(stream_urls[url_index], stream_protocol, rtsp_buffer_size)

    try:
        video_cap.release()
    except Exception:
        pass
    return None, None, url_index


def open_stream_with_retry(
    stream_urls: list[str],
    stream_protocol: str,
    rtsp_buffer_size: int,
    open_timeout_sec: float,
    backoff_max_sec: float,
    stream_host: str,
) -> tuple[cv2.VideoCapture, object, int]:
    """无限重试打开流（支持断网一小时后再恢复）；仅 H264 编码错误永久退出。"""
    backoff = 3.0
    while True:
        video_cap, probe, url_index = try_open_stream(
            stream_urls, stream_protocol, rtsp_buffer_size, open_timeout_sec
        )
        if probe is not None and is_ezviz_codec_error_frame(probe):
            print(
                "[fatal] STREAM_CODEC_NOT_H264: 萤石云流返回「视频编码非H264」提示页，"
                "请在萤石 App / 摄像头设置中将视频编码改为 H264，或改用局域网 RTSP",
                file=sys.stderr,
                flush=True,
            )
            sys.exit(STREAM_CODEC_NOT_H264)
        if video_cap is not None and probe is not None:
            return video_cap, probe, url_index
        print(
            f"[live] reconnecting open-failed host={stream_host} backoff={backoff:.0f}s "
            f"(will keep retrying until stream returns)",
            flush=True,
        )
        time.sleep(backoff)
        backoff = min(backoff * 2.0, max(3.0, backoff_max_sec))


def read_latest_frame(cap: cv2.VideoCapture, grab_flush: int) -> tuple[bool, Optional[object]]:
    """连续 read，返回最后一帧有效画面（FLV/HLS/RTSP 通用）。"""
    frame = None
    reads = max(1, grab_flush + 1)
    for _ in range(reads):
        ret, candidate = cap.read()
        if ret and candidate is not None and candidate.size > 0:
            frame = candidate
        elif frame is None:
            break
    return frame is not None, frame


def read_frame_batch(cap: cv2.VideoCapture, grab_flush: int) -> tuple[bool, Optional[object], list[tuple[float, object]]]:
    """Read a small batch: newest frame for detection, all valid frames for recording."""
    frame = None
    frames: list[tuple[float, object]] = []
    reads = max(1, grab_flush + 1)
    for _ in range(reads):
        ret, candidate = cap.read()
        if ret and candidate is not None and candidate.size > 0:
            frame_ts = time.time()
            frames.append((frame_ts, candidate))
            frame = candidate
        elif frame is None:
            break
    return frame is not None, frame, frames


class ContinuousFrameReader:
    def __init__(
        self,
        cap: cv2.VideoCapture,
        clip_recorder: ClipRecorder,
        stop_event: threading.Event,
        local_file_mode: bool,
        frame_pace_sec: float,
        reconnect_idle_sec: float = 15.0,
    ):
        self.cap = cap
        self.clip_recorder = clip_recorder
        self.stop_event = stop_event
        self.local_file_mode = local_file_mode
        self.frame_pace_sec = max(0.0, frame_pace_sec)
        self.reconnect_idle_sec = max(3.0, reconnect_idle_sec)
        self.cond = threading.Condition()
        self.latest_frame = None
        self.latest_ts = 0.0
        self.frame_count = 0
        self.eof = False
        self.needs_reconnect = False
        self.error: Optional[Exception] = None
        self.thread = threading.Thread(target=self._run, name="live-frame-reader", daemon=True)

    def start(self):
        self.thread.start()

    def join(self, timeout: Optional[float] = None):
        self.thread.join(timeout=timeout)

    def get_latest_after(self, last_ts: float, timeout: float = 0.5):
        deadline = time.time() + timeout
        with self.cond:
            while (
                not self.stop_event.is_set()
                and self.latest_ts <= last_ts
                and not self.eof
                and not self.needs_reconnect
                and self.error is None
            ):
                remaining = deadline - time.time()
                if remaining <= 0:
                    break
                self.cond.wait(timeout=remaining)
            return self.latest_ts, self.latest_frame

    def _run(self):
        try:
            eof_streak = 0
            empty_since: Optional[float] = None
            while not self.stop_event.is_set():
                ret, frame = self.cap.read()
                if not ret or frame is None or frame.size == 0:
                    if self.local_file_mode:
                        eof_streak += 1
                        if eof_streak >= 30:
                            with self.cond:
                                self.eof = True
                                self.cond.notify_all()
                            print("[live] local file ended", flush=True)
                            return
                    now = time.time()
                    if empty_since is None:
                        empty_since = now
                    elif now - empty_since >= self.reconnect_idle_sec:
                        self.needs_reconnect = True
                        with self.cond:
                            self.cond.notify_all()
                        print(
                            f"[live] reconnecting idle={now - empty_since:.1f}s no valid frames",
                            flush=True,
                        )
                        return
                    time.sleep(0.03)
                    continue
                eof_streak = 0
                empty_since = None
                if self.frame_pace_sec > 0:
                    time.sleep(self.frame_pace_sec)
                ts = time.time()
                self.clip_recorder.add_frame(ts, frame)
                self.clip_recorder.tick(ts)
                with self.cond:
                    self.latest_ts = ts
                    self.latest_frame = frame
                    self.frame_count += 1
                    self.cond.notify_all()
        except Exception as ex:
            self.error = ex
            with self.cond:
                self.cond.notify_all()
            print(f"[fatal] frame reader crashed: {ex}", file=sys.stderr, flush=True)


class FootpointTracker:
    """ByteTrack 未分配 ID 时，用脚点最近邻维持轨迹。"""

    def __init__(self, max_dist: float = 180.0, stale_sec: float = 4.0):
        self.max_dist = max_dist
        self.stale_sec = stale_sec
        self._next_id = 1
        self._tracks: Dict[int, tuple[float, float, float]] = {}

    def assign(self, footpoints: list[tuple[float, float]], now: float) -> list[int]:
        stale = [tid for tid, (_, _, ts) in self._tracks.items() if now - ts > self.stale_sec]
        for tid in stale:
            del self._tracks[tid]

        assigned: list[int] = []
        used: set[int] = set()
        for cx, cy in footpoints:
            best_tid: Optional[int] = None
            best_d = self.max_dist
            for tid, (pcx, pcy, _) in self._tracks.items():
                if tid in used:
                    continue
                d = ((cx - pcx) ** 2 + (cy - pcy) ** 2) ** 0.5
                if d < best_d:
                    best_d = d
                    best_tid = tid
            if best_tid is None:
                best_tid = self._next_id
                self._next_id += 1
            used.add(best_tid)
            self._tracks[best_tid] = (cx, cy, now)
            assigned.append(best_tid)
        return assigned


def rtsp_candidate_urls(stream_url: str) -> list[str]:
  """同一摄像头尝试萤石/海康常见 RTSP 路径。"""
  parsed = urlparse(stream_url)
  if parsed.scheme.lower() != "rtsp":
    return [stream_url]
  userinfo = ""
  hostport = parsed.netloc
  if "@" in parsed.netloc:
    userinfo, hostport = parsed.netloc.split("@", 1)
  if not hostport:
    return [stream_url]
  configured = (parsed.path or "").strip()
  paths = []
  if configured:
    paths.append(configured)
  for path in (
    "/h264/ch1/main/av_stream",
    "/Streaming/Channels/101",
  ):
    if path not in paths:
      paths.append(path)
  urls: list[str] = []
  for path in paths:
    if not path:
      continue
    if not path.startswith("/"):
      path = "/" + path
    url = f"rtsp://{userinfo + '@' if userinfo else ''}{hostport}{path}"
    if url not in urls:
      urls.append(url)
  return urls or [stream_url]


def is_ezviz_codec_error_frame(frame) -> bool:
    """萤石 FLV/HLS 在设备非 H264 时会返回白底提示页，YOLO 无法识别人。"""
    if frame is None or frame.size == 0:
        return False
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    mean = float(gray.mean())
    std = float(gray.std())
    h = frame.shape[0]
    return mean > 215 and std < 75 and h <= 400


def collect_probe_frame(cap: cv2.VideoCapture, deadline: float, stream_protocol: str):
    """
    萤石 FLV/HLS 首帧常为低清预览（如 768x432），需在 warmup 内取最大分辨率帧。
    RTSP 仍用首帧即可。
    """
    best = None
    best_area = 0
    started = time.time()
    while time.time() < deadline:
        ret, frame = cap.read()
        if not ret or frame is None or frame.size == 0:
            time.sleep(0.15)
            continue
        if is_ezviz_codec_error_frame(frame):
            time.sleep(0.15)
            continue
        h, w = frame.shape[:2]
        area = w * h
        if area > best_area:
            best = frame
            best_area = area
        if stream_protocol not in ("flv", "hls"):
            return best
        elapsed = time.time() - started
        if elapsed >= 5.0:
            return best
        if best_area >= 1920 * 1080 and elapsed >= 1.0:
            return best
        time.sleep(0.05)
    return best


def sample_enter_face_hunts(
    frame,
    now: float,
    active_captures: Dict[str, LiveEventCapture],
    capture_last_box: Dict[str, tuple],
    capture_last_conf: Dict[str, float],
    capture_last_box_ts: Dict[str, float],
    frame_area: int,
    max_box_age_sec: float,
) -> int:
    """进门追脸阶段全帧率采样：复用最近 YOLO 人体框，仅跑 InsightFace 择优。"""
    sampled = 0
    for track_key, cap in active_captures.items():
        if cap.event_type != "enter":
            continue
        box = capture_last_box.get(track_key)
        if box is None:
            continue
        if now - capture_last_box_ts.get(track_key, 0.0) > max_box_age_sec:
            continue
        cap.try_sample(frame, box, capture_last_conf.get(track_key, 0.0), frame_area)
        sampled += 1
    return sampled


def prune_capture_box_cache(
    active_captures: Dict[str, LiveEventCapture],
    capture_last_box: Dict[str, tuple],
    capture_last_conf: Dict[str, float],
    capture_last_box_ts: Dict[str, float],
):
    stale = [k for k in capture_last_box if k not in active_captures]
    for key in stale:
        capture_last_box.pop(key, None)
        capture_last_conf.pop(key, None)
        capture_last_box_ts.pop(key, None)


def save_probe_frame(
    frame,
    storage_root: str,
    task_id: str,
    x1: int,
    y1: int,
    x2: int,
    y2: int,
    line_y: int,
    width: int,
    height: int,
) -> tuple[str, str]:
    """保存首帧：_probe_raw.jpg 原图（供人工标定），_probe.jpg 带 ROI/过线。"""
    import os

    out_dir = os.path.join(storage_root, "log_library")
    os.makedirs(out_dir, exist_ok=True)
    raw_path = os.path.join(out_dir, "_probe_raw.jpg")
    cv2.imwrite(raw_path, frame, [int(cv2.IMWRITE_JPEG_QUALITY), 95])

    out_path = os.path.join(out_dir, "_probe.jpg")
    vis = frame.copy()
    cv2.rectangle(vis, (x1, y1), (x2, y2), (0, 255, 0), 2)
    cv2.line(vis, (x1, line_y), (x2, line_y), (0, 0, 255), 2)
    label = f"probe task={task_id} {width}x{height} lineY={line_y}"
    cv2.putText(vis, label, (8, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
    cv2.imwrite(out_path, vis)
    return raw_path, out_path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stream-url", required=True)
    parser.add_argument("--stream-protocol", choices=["rtsp", "hls", "flv"], default="rtsp")
    parser.add_argument("--task-id", required=True)
    parser.add_argument("--storage-root", required=True)
    parser.add_argument("--ingest-base-url", default="http://localhost:8080")
    parser.add_argument("--ingest-key", default="")
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--line-y", type=int, default=520)
    parser.add_argument("--roi", default="620,170,1290,760")
    parser.add_argument("--ref-width", type=int, default=REF_WIDTH)
    parser.add_argument("--ref-height", type=int, default=REF_HEIGHT)
    parser.add_argument("--model", default="yolov8n.pt")
    parser.add_argument("--conf", type=float, default=0.35)
    parser.add_argument("--iou", type=float, default=0.6)
    parser.add_argument("--track-prefix", default="live")
    parser.add_argument("--target-detect-fps", type=float, default=3.0)
    parser.add_argument("--grab-flush-frames", type=int, default=5)
    parser.add_argument("--rtsp-buffer-size", type=int, default=1)
    parser.add_argument("--event-cooldown-sec", type=float, default=2.0)
    parser.add_argument("--enter-infer-margin", type=int, default=80)
    parser.add_argument("--exit-infer-margin", type=int, default=80)
    parser.add_argument("--imgsz", type=int, default=1280, help="YOLO 推理尺寸，高分辨率远景人体需更大")
    parser.add_argument("--snapshot-window-sec", type=float, default=2.5, help="出门过线后择优抓拍窗口（秒）")
    parser.add_argument(
        "--enter-face-hunt-max-sec",
        type=float,
        default=10.0,
        help="进门穿线后持续追脸最长时间（秒），超时仍无脸则按 low/missing 入库",
    )
    parser.add_argument(
        "--enter-face-grace-sec",
        type=float,
        default=4.0,
        help="进门首次检出脸后继续择优的宽限时间（秒）",
    )
    parser.add_argument("--face-min-det-score", type=float, default=0.45, help="InsightFace 人脸检测最低分")
    parser.add_argument(
        "--event-ingest-enabled",
        default="true",
        help="true=过线后追脸并上报 /event；false=仅检人+门线/场景录像（AB 轻直播）",
    )
    parser.add_argument("--cross-confirm-frames", type=int, default=1, help="连续几帧同向穿线才确认")
    parser.add_argument("--open-timeout-sec", type=float, default=30.0)
    parser.add_argument(
        "--frame-reconnect-idle-sec",
        type=float,
        default=15.0,
        help="持续无有效帧超过该秒数则重建 VideoCapture（支持长时断网）",
    )
    parser.add_argument(
        "--reconnect-backoff-max-sec",
        type=float,
        default=60.0,
        help="断流重连退避上限秒数",
    )
    parser.add_argument("--clip-enabled", default="true")
    parser.add_argument("--clip-pre-roll-sec", type=float, default=3.0)
    parser.add_argument("--clip-post-roll-sec", type=float, default=3.0)
    parser.add_argument("--clip-track-lost-sec", type=float, default=2.0)
    parser.add_argument("--clip-scene-merge-gap-sec", type=float, default=5.0)
    parser.add_argument("--clip-local-recording-enabled", default="false")
    parser.add_argument("--clip-ffmpeg-path", default="ffmpeg", help="ffmpeg 可执行文件路径")
    parser.add_argument(
        "--clip-ring-segment-sec",
        type=float,
        default=2.0,
        help="环形缓冲分段时长（秒），-c copy 源码流；越小预录越准、分段越多",
    )
    parser.add_argument(
        "--clip-max-duration-sec",
        type=float,
        default=300.0,
        help="单段本地录像最长秒数，防止异常一直录",
    )
    parser.add_argument("--device-serial", default="")
    parser.add_argument("--channel-no", type=int, default=1)
    parser.add_argument("--valid-code", default="")
    args = parser.parse_args()
    args.event_ingest_enabled = str(args.event_ingest_enabled).lower() == "true"
    print(f"[live] worker starting task={args.task_id}", flush=True)
    print(
        f"[live] eventIngest={'on' if args.event_ingest_enabled else 'off (light: detect+clip only)'}",
        flush=True,
    )

    stream_host = urlparse(args.stream_url).netloc or args.stream_url[:48]
    stream_tag = args.stream_url.split("/")[-1].split("?")[0]
    print(
        f"[live] opening stream protocol={args.stream_protocol} host={stream_host} tag={stream_tag}",
        flush=True,
    )
    started_at = time.time()

    model_holder: dict = {"model": None, "error": None}

    def _load_model():
        try:
            print("[live] loading yolo model...", flush=True)
            model_holder["model"] = load_yolo(args.model)
            print("[live] yolo model ready", flush=True)
        except Exception as ex:
            model_holder["error"] = ex

    model_thread = threading.Thread(target=_load_model, name="yolo-preload", daemon=True)
    model_thread.start()

    stream_urls = rtsp_candidate_urls(args.stream_url) if args.stream_protocol == "rtsp" else [args.stream_url]
    video_cap, probe, url_index = open_stream_with_retry(
        stream_urls,
        args.stream_protocol,
        args.rtsp_buffer_size,
        args.open_timeout_sec,
        args.reconnect_backoff_max_sec,
        stream_host,
    )

    height, width = probe.shape[:2]
    fps = float(video_cap.get(cv2.CAP_PROP_FPS) or 25.0)
    ref_w = max(1, int(args.ref_width))
    ref_h = max(1, int(args.ref_height))
    x1, y1, x2, y2, line_y, scaled_roi = scale_roi_and_line(
        args.roi, args.line_y, width, height, ref_width=ref_w, ref_height=ref_h
    )
    probe_raw_path, probe_path = save_probe_frame(
        probe, args.storage_root, args.task_id, x1, y1, x2, y2, line_y, width, height
    )
    print(f"[live] probe raw={probe_raw_path} overlay={probe_path}", flush=True)
    enter_inside_px = max(25, int(round(35 * height / ref_h)))
    tight_margin = tight_infer_margin(height, ref_h)
    exit_margin = exit_hysteresis_margin(height, ref_h)
    min_track_hits = min_track_hits_for_event(height, ref_h, base_hits=4)
    door_gate = PerTrackDoorGate(line_y, tight_margin, exit_margin)
    track_hits: Dict[int, int] = {}
    detect_interval = 1.0 / max(args.target_detect_fps, 0.5)
    max_capture_box_age_sec = max(1.0, detect_interval * 2.5)
    local_file_mode = is_local_media_url(args.stream_url)
    corridor_margin_x = max(24, int((x2 - x1) * 0.10))
    frame_pace_sec = (1.0 / fps) if local_file_mode else 0.0

    print(
        f"[live] stream ready task={args.task_id} protocol={args.stream_protocol} "
        f"size={width}x{height} targetFps={args.target_detect_fps} flush={args.grab_flush_frames} "
        f"conf={args.conf} imgsz={args.imgsz} crossConfirm={args.cross_confirm_frames} "
        f"snapshotWindow={args.snapshot_window_sec}s enterFaceHuntMax={args.enter_face_hunt_max_sec}s "
        f"enterFaceGrace={args.enter_face_grace_sec}s faceMinDet={args.face_min_det_score} "
        f"enterFaceFullRate=yes maxBoxAge={max_capture_box_age_sec:.2f}s "
        f"tightInferPx={tight_margin} exitMarginPx={exit_margin} minTrackHits={min_track_hits} "
        f"enterInsidePx={enter_inside_px} corridorMarginX={corridor_margin_x} "
        f"localFile={local_file_mode} "
        f"connectSec={time.time() - started_at:.1f}",
        flush=True,
    )
    print(
        f"[roi] ref={ref_w}x{ref_h} video={width}x{height} "
        f"scaled={scaled_roi} lineY={line_y}"
    )

    model_thread.join(timeout=max(args.open_timeout_sec, 30.0))
    if model_holder["error"] is not None:
        raise model_holder["error"]
    model = model_holder["model"]
    if model is None:
        print("[live] yolo preload timeout, loading inline", flush=True)
        model = load_yolo(args.model)
    prev_cy: Dict[int, float] = {}
    seen_tracks: set[int] = set()
    cross_pending: Dict[int, tuple[str, int]] = {}
    last_event_at: Dict[str, float] = {}
    active_captures: Dict[str, LiveEventCapture] = {}
    capture_last_box: Dict[str, tuple] = {}
    capture_last_conf: Dict[str, float] = {}
    capture_last_box_ts: Dict[str, float] = {}
    track_conf: Dict[str, float] = {}
    foot_tracker = FootpointTracker(max_dist=max(120.0, width * 0.06))
    frame_area = width * height
    enter_count = 0
    exit_count = 0
    frame_idx = 0
    detect_pass = 0
    last_detect_at = 0.0
    stop_event = threading.Event()
    http_pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix="live-ingest")
    clip_recorder = ClipRecorder(args, fps, width, height, http_pool)
    frame_reader = ContinuousFrameReader(
        video_cap,
        clip_recorder,
        stop_event,
        local_file_mode,
        frame_pace_sec,
        reconnect_idle_sec=args.frame_reconnect_idle_sec,
    )

    try:
        # FLV/HLS 适当丢帧；本地文件自测不丢帧并按 fps  pacing
        frame_reader.start()
        # The detection loop consumes the freshest captured frame; the reader owns stream pacing and clip timing.
        last_frame_ts = 0.0
        while not stop_event.is_set():
            now = time.time()
            if frame_reader.needs_reconnect or frame_reader.error is not None:
                reason = "error" if frame_reader.error is not None else "idle"
                print(f"[live] reconnecting reason={reason}", flush=True)
                stop_event.set()
                frame_reader.join(timeout=2.0)
                try:
                    video_cap.release()
                except Exception:
                    pass
                if not local_file_mode:
                    video_cap, probe, url_index = open_stream_with_retry(
                        stream_urls,
                        args.stream_protocol,
                        args.rtsp_buffer_size,
                        args.open_timeout_sec,
                        args.reconnect_backoff_max_sec,
                        stream_host,
                    )
                    stop_event.clear()
                    frame_reader = ContinuousFrameReader(
                        video_cap,
                        clip_recorder,
                        stop_event,
                        local_file_mode,
                        frame_pace_sec,
                        reconnect_idle_sec=args.frame_reconnect_idle_sec,
                    )
                    frame_reader.start()
                    last_frame_ts = 0.0
                    print(
                        f"[live] reconnect ok task={args.task_id} size={probe.shape[1]}x{probe.shape[0]}",
                        flush=True,
                    )
                    print(
                        f"[live] stream ready task={args.task_id} protocol={args.stream_protocol} "
                        f"size={probe.shape[1]}x{probe.shape[0]} (reconnected)",
                        flush=True,
                    )
                    continue
                if frame_reader.error is not None:
                    raise frame_reader.error
                print("[live] local file reconnect skipped", flush=True)
                break

            frame_ts, frame = frame_reader.get_latest_after(last_frame_ts, timeout=max(0.3, detect_interval))
            if frame_reader.error is not None:
                raise frame_reader.error
            if frame_reader.needs_reconnect:
                continue
            if frame is None:
                if frame_reader.eof:
                    print("[live] detection loop reached EOF", flush=True)
                    break
                continue
            if frame_ts <= last_frame_ts:
                continue
            last_frame_ts = frame_ts
            frame_idx += 1
            e_delta, x_delta = flush_expired_captures(
                active_captures, now, args, http_pool, track_conf, clip_recorder
            )
            enter_count += e_delta
            exit_count += x_delta
            prune_capture_box_cache(
                active_captures, capture_last_box, capture_last_conf, capture_last_box_ts
            )

            will_detect = (now - last_detect_at) >= detect_interval
            if not will_detect and args.event_ingest_enabled:
                sample_enter_face_hunts(
                    frame,
                    now,
                    active_captures,
                    capture_last_box,
                    capture_last_conf,
                    capture_last_box_ts,
                    frame_area,
                    max_capture_box_age_sec,
                )

            if not will_detect:
                continue
            last_detect_at = now
            detect_pass += 1

            results = model.track(
                frame,
                persist=True,
                tracker="bytetrack.yaml",
                classes=[0],
                conf=args.conf,
                iou=args.iou,
                imgsz=args.imgsz,
                verbose=False,
            )
            if not results:
                if detect_pass % 15 == 0:
                    print(f"[detect] pass={detect_pass} persons=0 in_corridor=0 (no result)", flush=True)
                continue
            result = results[0]
            boxes = result.boxes
            if boxes is None or len(boxes) == 0:
                if detect_pass % 15 == 0:
                    print(f"[detect] pass={detect_pass} persons=0 in_corridor=0", flush=True)
                continue

            xyxy = boxes.xyxy.cpu().numpy()
            confs = boxes.conf.cpu().numpy() if boxes.conf is not None else None
            footpoints = [((row[0] + row[2]) / 2.0, row[3]) for row in xyxy]

            if boxes.id is not None:
                ids = boxes.id.int().cpu().numpy()
            else:
                ids = foot_tracker.assign(footpoints, now)
                if detect_pass <= 3 or detect_pass % 10 == 0:
                    print(
                        f"[detect] byteTrack id missing, fallback footpoints={len(footpoints)}",
                        flush=True,
                    )

            persons_total = len(ids)
            persons_corridor = 0

            for idx, tid in enumerate(ids):
                bx1, by1, bx2, by2 = xyxy[idx]
                cx = (bx1 + bx2) / 2.0
                cy = by2
                tid_i = int(tid)
                track_key = f"{args.track_prefix}_{tid_i}"
                capture_sess = active_captures.get(track_key) if args.event_ingest_enabled else None
                in_enter_face_hunt = (
                    args.event_ingest_enabled
                    and capture_sess is not None
                    and capture_sess.event_type == "enter"
                )
                if not in_enter_face_hunt and (
                    cx < x1 - corridor_margin_x or cx > x2 + corridor_margin_x
                ):
                    continue
                persons_corridor += 1
                clip_recorder.update_seen(track_key, now)
                track_hits[tid_i] = track_hits.get(tid_i, 0) + 1

                conf = float(confs[idx]) if confs is not None else 0.0
                box = (bx1, by1, bx2, by2)

                if not args.event_ingest_enabled:
                    # AB 轻直播：只驱动场景录像，不追脸、不报 /event
                    continue

                if capture_sess is not None:
                    capture_last_box[track_key] = box
                    capture_last_conf[track_key] = conf
                    capture_last_box_ts[track_key] = now
                    capture_sess.try_sample(frame, box, conf, frame_area)
                    continue

                is_new_track = tid_i not in seen_tracks
                if is_new_track:
                    seen_tracks.add(tid_i)
                    prev_cy[tid_i] = cy
                    old = cy
                    door_gate.on_new_track(tid_i, cy)
                else:
                    old = prev_cy[tid_i]
                    prev_cy[tid_i] = cy

                if now - last_event_at.get(track_key, 0.0) < args.event_cooldown_sec:
                    continue

                event_type = door_gate.try_cross(
                    cross_pending, tid_i, old, cy, args.cross_confirm_frames
                )
                inferred = False
                if event_type is None:
                    infer_ev = door_gate.try_infer_enter(tid_i, cy, is_new_track)
                    if infer_ev:
                        event_type = infer_ev
                        inferred = True

                if not event_type:
                    if detect_pass % 10 == 0:
                        print(
                            f"[detect] track={track_key} foot=({cx:.0f},{cy:.0f}) lineY={line_y} "
                            f"oldY={old:.0f} conf={conf:.2f} (no cross yet)",
                            flush=True,
                        )
                    continue

                if track_hits.get(tid_i, 0) < min_track_hits:
                    continue

                if track_key in active_captures:
                    continue

                print(
                    f"[cross] confirmed {event_type} track={track_key} foot=({cx:.0f},{cy:.0f}) "
                    f"oldY={old:.0f} lineY={line_y} side={door_gate.side(tid_i)} inferred={inferred}",
                    flush=True,
                )
                if event_type == "enter":
                    print(
                        f"[capture-hunt] enter track={track_key} maxSec={args.enter_face_hunt_max_sec} "
                        f"graceSec={args.enter_face_grace_sec}",
                        flush=True,
                    )
                door_gate.commit(tid_i, event_type)
                active_captures[track_key] = LiveEventCapture(
                    track_key=track_key,
                    event_type=event_type,
                    tid_i=tid_i,
                    line_y=line_y,
                    window_sec=args.snapshot_window_sec,
                    min_face_det_score=args.face_min_det_score,
                    enter_inside_px=enter_inside_px,
                    enter_face_hunt_max_sec=args.enter_face_hunt_max_sec,
                    enter_face_grace_sec=args.enter_face_grace_sec,
                )
                capture_last_box[track_key] = box
                capture_last_conf[track_key] = conf
                capture_last_box_ts[track_key] = now
                active_captures[track_key].try_sample(frame, box, conf, frame_area)
                track_conf[track_key] = conf
                last_event_at[track_key] = now
                cross_pending.pop(tid_i, None)

            if detect_pass % 15 == 0:
                print(
                    f"[detect] pass={detect_pass} persons={persons_total} in_corridor={persons_corridor} "
                    f"enter={enter_count} exit={exit_count}",
                    flush=True,
                )

            if frame_idx % 90 == 0:
                print(
                    f"[live] heartbeat detectFrames={frame_idx} sourceFrames={frame_reader.frame_count} "
                    f"detectPass={detect_pass} enter={enter_count} exit={exit_count}",
                    flush=True,
                )

    except KeyboardInterrupt:
        print("[live] interrupted", flush=True)
    except Exception as ex:
        print(f"[fatal] live loop crashed: {ex}", file=sys.stderr, flush=True)
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        stop_event.set()
        frame_reader.join(timeout=2.0)
        scene_id = clip_recorder.current_scene_group_id() or ""
        clip_recorder.finalize_all()
        now = time.time()
        for track_key in list(active_captures.keys()):
            capture_sess = active_captures.pop(track_key)
            conf = track_conf.pop(track_key, 0.0)
            finalize_live_capture(capture_sess, args, http_pool, conf, scene_group_id=scene_id)
            if capture_sess.event_type == "enter":
                enter_count += 1
            else:
                exit_count += 1
        http_pool.shutdown(wait=True, cancel_futures=False)
        video_cap.release()
        print(
            f"[live] stopped enter={enter_count} exit={exit_count} "
            f"detectFrames={frame_idx} sourceFrames={frame_reader.frame_count}",
            flush=True,
        )


if __name__ == "__main__":
    main()
