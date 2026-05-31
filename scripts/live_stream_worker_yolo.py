#!/usr/bin/env python3
"""
萤石/RTSP/HLS 直播识别 Worker：限帧 + 丢缓冲 + 过线异步 ingest。

- 输入：--stream-url（RTSP 或 HLS）
- 检测：YOLO + ByteTrack，默认约 3fps
- 过线：即时抓拍落 log_library，async ingest（Java 侧线程池处理 embedding）
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
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


try:
    from ultralytics import YOLO
except Exception as ex:  # pragma: no cover
    print(f"[fatal] 缺少 ultralytics 依赖: {ex}", file=sys.stderr)
    sys.exit(2)

RTSP_OPEN_FAILED = 2
STREAM_CODEC_NOT_H264 = 4


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


def finalize_live_capture(
    capture: LiveEventCapture,
    args,
    http_pool: ThreadPoolExecutor,
    conf: float,
):
    face_img, body_img = capture.finalize_images()
    quality = capture.quality_flag()
    face_url, body_url = save_live_event_snapshot(
        args.storage_root,
        args.task_id,
        capture.track_key,
        capture.event_type,
        face_img,
        body_img,
    )
    payload = {
        "eventType": capture.event_type,
        "locationId": args.location_id,
        "trackKey": capture.track_key,
        "eventTime": iso_now(),
        "faceImageUrl": face_url,
        "bodyImageUrl": body_url,
        "bestMatchScore": conf,
        "async": True,
        "qualityFlag": quality,
    }
    post_event_async(http_pool, args.ingest_base_url, args.ingest_key, payload)
    print(
        f"[capture-done] {capture.track_key} {capture.event_type} samples={capture.samples} "
        f"face={'yes' if face_url else 'no'} body={'yes' if body_url else 'no'} quality={quality}",
        flush=True,
    )


def flush_expired_captures(
    active_captures: Dict[str, LiveEventCapture],
    now: float,
    args,
    http_pool: ThreadPoolExecutor,
    track_conf: Dict[str, float],
) -> tuple[int, int]:
    enter_delta = 0
    exit_delta = 0
    expired_keys = [k for k, cap in active_captures.items() if cap.expired(now)]
    for track_key in expired_keys:
        cap = active_captures.pop(track_key)
        conf = track_conf.pop(track_key, 0.0)
        finalize_live_capture(cap, args, http_pool, conf)
        if cap.event_type == "enter":
            enter_delta += 1
        else:
            exit_delta += 1
    return enter_delta, exit_delta


def iso_now() -> str:
    tz = dt.timezone(dt.timedelta(hours=8))
    return dt.datetime.now(tz).isoformat(timespec="seconds")


def configure_ffmpeg_options(stream_protocol: str):
    if stream_protocol == "rtsp":
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "rtsp_transport;tcp|fflags;nobuffer|max_delay;500000"
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
  paths = [
    "/h264/ch1/sub/av_stream",
    "/Streaming/Channels/102",
    parsed.path or "",
    "/h264/ch1/main/av_stream",
    "/Streaming/Channels/101",
  ]
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
    parser.add_argument("--location-id", type=int, default=1)
    parser.add_argument("--line-y", type=int, default=520)
    parser.add_argument("--roi", default="620,170,1290,760")
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
    parser.add_argument("--snapshot-window-sec", type=float, default=2.5, help="过线确认后择优抓拍窗口（秒）")
    parser.add_argument("--face-min-det-score", type=float, default=0.45, help="InsightFace 人脸检测最低分")
    parser.add_argument("--cross-confirm-frames", type=int, default=1, help="连续几帧同向穿线才确认")
    parser.add_argument("--open-timeout-sec", type=float, default=30.0)
    args = parser.parse_args()

    stream_host = urlparse(args.stream_url).netloc or args.stream_url[:48]
    print(f"[live] opening stream protocol={args.stream_protocol} host={stream_host}", flush=True)
    started_at = time.time()

    model_holder: dict = {"model": None, "error": None}

    def _load_model():
        try:
            print("[live] loading yolo model...", flush=True)
            model_holder["model"] = YOLO(args.model)
            print("[live] yolo model ready", flush=True)
        except Exception as ex:
            model_holder["error"] = ex

    model_thread = threading.Thread(target=_load_model, name="yolo-preload", daemon=True)
    model_thread.start()

    open_deadline = started_at + args.open_timeout_sec
    last_progress_at = 0.0

    stream_urls = rtsp_candidate_urls(args.stream_url) if args.stream_protocol == "rtsp" else [args.stream_url]
    url_index = 0
    video_cap = open_capture(stream_urls[url_index], args.stream_protocol, args.rtsp_buffer_size)
    probe = None
    while time.time() < open_deadline:
        now = time.time()
        if now - last_progress_at >= 10.0:
            print(
                f"[live] connecting... elapsed={int(now - started_at)}s protocol={args.stream_protocol} "
                f"urlTry={url_index + 1}/{len(stream_urls)}",
                flush=True,
            )
            last_progress_at = now

        if not video_cap.isOpened():
            video_cap.release()
            video_cap = open_capture(stream_urls[url_index], args.stream_protocol, args.rtsp_buffer_size)
            time.sleep(0.3)
            continue

        ret, frame = video_cap.read()
        if ret and frame is not None and frame.size > 0:
            probe = frame
            if url_index > 0:
                print(f"[live] rtsp fallback ok index={url_index} url={stream_urls[url_index][:80]}", flush=True)
            break

        video_cap.release()
        url_index += 1
        if url_index >= len(stream_urls):
            url_index = 0
            time.sleep(0.5)
            continue
        print(f"[live] rtsp retry path={stream_urls[url_index]}", flush=True)
        video_cap = open_capture(stream_urls[url_index], args.stream_protocol, args.rtsp_buffer_size)

    if probe is None:
        if args.stream_protocol == "rtsp":
            print(
                "[fatal] RTSP_OPEN_FAILED: 无法打开 RTSP 流，请确认服务器与摄像头在同一局域网",
                file=sys.stderr,
                flush=True,
            )
            sys.exit(RTSP_OPEN_FAILED)
        print(
            f"[fatal] STREAM_OPEN_FAILED: 无法打开 {args.stream_protocol} 流 host={stream_host}，"
            "请确认设备已开启直播、验证码正确，且本机可访问萤石云",
            file=sys.stderr,
            flush=True,
        )
        sys.exit(3)

    if is_ezviz_codec_error_frame(probe):
        print(
            "[fatal] STREAM_CODEC_NOT_H264: 萤石云流返回「视频编码非H264」提示页，"
            "请在萤石 App / 摄像头设置中将视频编码改为 H264，或改用局域网 RTSP",
            file=sys.stderr,
            flush=True,
        )
        sys.exit(STREAM_CODEC_NOT_H264)

    height, width = probe.shape[:2]
    fps = float(video_cap.get(cv2.CAP_PROP_FPS) or 25.0)
    x1, y1, x2, y2, line_y, scaled_roi = scale_roi_and_line(args.roi, args.line_y, width, height)
    probe_raw_path, probe_path = save_probe_frame(
        probe, args.storage_root, args.task_id, x1, y1, x2, y2, line_y, width, height
    )
    print(f"[live] probe raw={probe_raw_path} overlay={probe_path}", flush=True)
    enter_inside_px = max(25, int(round(35 * height / REF_HEIGHT)))
    tight_margin = tight_infer_margin(height, REF_HEIGHT)
    exit_margin = exit_hysteresis_margin(height, REF_HEIGHT)
    min_track_hits = min_track_hits_for_event(height, REF_HEIGHT, base_hits=4)
    door_gate = PerTrackDoorGate(line_y, tight_margin, exit_margin)
    track_hits: Dict[int, int] = {}
    detect_interval = 1.0 / max(args.target_detect_fps, 0.5)
    local_file_mode = is_local_media_url(args.stream_url)
    corridor_margin_x = max(24, int((x2 - x1) * 0.10))
    frame_pace_sec = (1.0 / fps) if local_file_mode else 0.0

    print(
        f"[live] stream ready task={args.task_id} protocol={args.stream_protocol} "
        f"size={width}x{height} targetFps={args.target_detect_fps} flush={args.grab_flush_frames} "
        f"conf={args.conf} imgsz={args.imgsz} crossConfirm={args.cross_confirm_frames} "
        f"snapshotWindow={args.snapshot_window_sec}s faceMinDet={args.face_min_det_score} "
        f"tightInferPx={tight_margin} exitMarginPx={exit_margin} minTrackHits={min_track_hits} "
        f"enterInsidePx={enter_inside_px} corridorMarginX={corridor_margin_x} "
        f"localFile={local_file_mode} "
        f"connectSec={time.time() - started_at:.1f}",
        flush=True,
    )
    print(
        f"[roi] ref={REF_WIDTH}x{REF_HEIGHT} video={width}x{height} "
        f"scaled={scaled_roi} lineY={line_y}"
    )

    model_thread.join(timeout=max(args.open_timeout_sec, 30.0))
    if model_holder["error"] is not None:
        raise model_holder["error"]
    model = model_holder["model"]
    if model is None:
        print("[live] yolo preload timeout, loading inline", flush=True)
        model = YOLO(args.model)
    prev_cy: Dict[int, float] = {}
    seen_tracks: set[int] = set()
    cross_pending: Dict[int, tuple[str, int]] = {}
    last_event_at: Dict[str, float] = {}
    active_captures: Dict[str, LiveEventCapture] = {}
    track_conf: Dict[str, float] = {}
    foot_tracker = FootpointTracker(max_dist=max(120.0, width * 0.06))
    frame_area = width * height
    enter_count = 0
    exit_count = 0
    frame_idx = 0
    detect_pass = 0
    last_detect_at = 0.0
    local_eof_streak = 0
    stop_event = threading.Event()
    http_pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix="live-ingest")

    try:
        # FLV/HLS 适当丢帧；本地文件自测不丢帧并按 fps  pacing
        if local_file_mode:
            flush_count = 0
        elif args.stream_protocol in ("hls", "flv"):
            flush_count = 2
        else:
            flush_count = args.grab_flush_frames
        while not stop_event.is_set():
            ret, frame = read_latest_frame(video_cap, flush_count)
            if not ret or frame is None:
                if local_file_mode:
                    local_eof_streak += 1
                    if local_eof_streak >= 30:
                        print("[live] local file ended", flush=True)
                        break
                time.sleep(0.1)
                continue
            local_eof_streak = 0

            if frame_pace_sec > 0:
                time.sleep(frame_pace_sec)

            frame_idx += 1
            now = time.time()
            e_delta, x_delta = flush_expired_captures(
                active_captures, now, args, http_pool, track_conf
            )
            enter_count += e_delta
            exit_count += x_delta

            if now - last_detect_at < detect_interval:
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
                if cx < x1 - corridor_margin_x or cx > x2 + corridor_margin_x:
                    continue
                persons_corridor += 1
                track_hits[tid_i] = track_hits.get(tid_i, 0) + 1

                track_key = f"{args.track_prefix}_{tid_i}"
                conf = float(confs[idx]) if confs is not None else 0.0
                box = (bx1, by1, bx2, by2)

                capture_sess = active_captures.get(track_key)
                if capture_sess is not None:
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
                door_gate.commit(tid_i, event_type)
                active_captures[track_key] = LiveEventCapture(
                    track_key=track_key,
                    event_type=event_type,
                    tid_i=tid_i,
                    line_y=line_y,
                    window_sec=args.snapshot_window_sec,
                    min_face_det_score=args.face_min_det_score,
                    enter_inside_px=enter_inside_px,
                )
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
                print(f"[live] heartbeat frame={frame_idx} enter={enter_count} exit={exit_count}", flush=True)

    except KeyboardInterrupt:
        print("[live] interrupted", flush=True)
    except Exception as ex:
        print(f"[fatal] live loop crashed: {ex}", file=sys.stderr, flush=True)
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        stop_event.set()
        now = time.time()
        for track_key in list(active_captures.keys()):
            capture_sess = active_captures.pop(track_key)
            conf = track_conf.pop(track_key, 0.0)
            finalize_live_capture(capture_sess, args, http_pool, conf)
            if capture_sess.event_type == "enter":
                enter_count += 1
            else:
                exit_count += 1
        http_pool.shutdown(wait=False, cancel_futures=True)
        video_cap.release()
        print(f"[live] stopped enter={enter_count} exit={exit_count} frames={frame_idx}", flush=True)


if __name__ == "__main__":
    main()
