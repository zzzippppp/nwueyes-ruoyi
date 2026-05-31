#!/usr/bin/env python3
"""
Phase 2.5: 正式算法回放测试（YOLO + ByteTrack）

说明：
- 输入：本地视频路径或 /common/upload 返回的 fileName
- 检测：Ultralytics YOLO（person 类）
- 跟踪：ByteTrack（通过 model.track + bytetrack.yaml）
- 事件：目标底边中心穿越 line_y 时上报 enter/exit
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
from typing import Dict
from urllib import request

import cv2

from roi_scale import REF_HEIGHT, REF_WIDTH, scale_roi_and_line
from line_crossing import (
    PerTrackDoorGate,
    exit_hysteresis_margin,
    min_track_hits_for_event,
    scaled_infer_margin,
    tight_infer_margin,
)

try:
    from ultralytics import YOLO
except Exception as ex:  # pragma: no cover
    print(f"[fatal] 缺少 ultralytics 依赖: {ex}", file=sys.stderr)
    print("[hint] pip install ultralytics", file=sys.stderr)
    sys.exit(2)


def resolve_video_path(video_path: str, uploaded_file_name: str, profile_root: str) -> str:
    if video_path:
        return video_path
    if not uploaded_file_name:
        raise ValueError("video_path 和 uploaded_file_name 不能同时为空")
    normalized = uploaded_file_name.replace("\\", "/")
    if normalized.startswith("http://") or normalized.startswith("https://"):
        raise ValueError("uploaded_file_name 请传 /common/upload 返回的 fileName，而不是完整 URL")
    relative = normalized
    if relative.startswith("/profile/"):
        relative = relative[len("/profile/") :]
    relative = relative.lstrip("/")
    return os.path.normpath(os.path.join(profile_root, relative))


def post_event(base_url: str, ingest_key: str, payload: dict):
    url = f"{base_url.rstrip('/')}/ingest/presence/event"
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    if ingest_key:
        req.add_header("X-Ingest-Key", ingest_key)
    with request.urlopen(req, timeout=10) as resp:
        text = resp.read().decode("utf-8")
        return resp.status, text


def iso_from_base(base_time: dt.datetime, frame_idx: int, fps: float) -> str:
    seconds = frame_idx / fps if fps > 0 else 0.0
    t = base_time + dt.timedelta(seconds=seconds)
    return t.isoformat(timespec="seconds")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video-path", default="")
    parser.add_argument("--uploaded-file-name", default="")
    parser.add_argument("--profile-root", default="D:/ruoyi/uploadPath")
    parser.add_argument("--ingest-base-url", default="http://localhost:8080")
    parser.add_argument("--ingest-key", default="local-presence-key")
    parser.add_argument("--location-id", type=int, default=1)
    parser.add_argument("--line-y", type=int, default=520)
    parser.add_argument("--roi", default="620,170,1290,760", help="x1,y1,x2,y2")
    parser.add_argument("--model", default="yolov8n.pt")
    parser.add_argument("--conf", type=float, default=0.35)
    parser.add_argument("--iou", type=float, default=0.6)
    parser.add_argument("--debug-out", default="")
    parser.add_argument("--track-prefix", default="yolo")
    parser.add_argument("--event-cooldown-frames", type=int, default=20)
    parser.add_argument("--enter-infer-margin", type=int, default=80)
    parser.add_argument("--exit-infer-margin", type=int, default=80)
    args = parser.parse_args()

    video_path = resolve_video_path(args.video_path, args.uploaded_file_name, args.profile_root)
    if not os.path.exists(video_path):
        raise FileNotFoundError(f"视频不存在: {video_path}")

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开视频: {video_path}")
    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)) or 1920
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)) or 1080
    cap.release()

    x1, y1, x2, y2, line_y, scaled_roi = scale_roi_and_line(args.roi, args.line_y, width, height)
    tight_margin = tight_infer_margin(height, REF_HEIGHT)
    exit_margin = exit_hysteresis_margin(height, REF_HEIGHT)
    min_track_hits = min_track_hits_for_event(height, REF_HEIGHT)
    door_gate = PerTrackDoorGate(line_y, tight_margin, exit_margin)
    track_hits: Dict[int, int] = {}
    print(
        f"[roi] ref={REF_WIDTH}x{REF_HEIGHT} video={width}x{height} "
        f"base={args.roi} lineY={args.line_y} -> scaled={scaled_roi} lineY={line_y} "
        f"tightInferPx={tight_margin} exitMarginPx={exit_margin} minTrackHits={min_track_hits}"
    )

    model = YOLO(args.model)
    base_time = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
    prev_cy: Dict[int, float] = {}
    seen_tracks: set[int] = set()
    cross_pending: Dict[int, tuple[str, int]] = {}
    last_event_frame: Dict[str, int] = {}
    enter_count = 0
    exit_count = 0
    frame_idx = 0

    writer = None
    if args.debug_out:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(args.debug_out, fourcc, fps, (width, height))

    results_iter = model.track(
        source=video_path,
        stream=True,
        persist=True,
        tracker="bytetrack.yaml",
        classes=[0],  # person
        conf=args.conf,
        iou=args.iou,
        verbose=False,
    )

    for result in results_iter:
        frame_idx += 1
        frame = result.orig_img
        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 180, 255), 2)
        cv2.line(frame, (x1, line_y), (x2, line_y), (255, 255, 0), 2)

        boxes = result.boxes
        if boxes is not None and boxes.id is not None:
            xyxy = boxes.xyxy.cpu().numpy()
            ids = boxes.id.int().cpu().numpy()
            confs = boxes.conf.cpu().numpy() if boxes.conf is not None else None
            for idx, tid in enumerate(ids):
                bx1, by1, bx2, by2 = xyxy[idx]
                cx = (bx1 + bx2) / 2.0
                cy = by2  # 框底中心作为脚点近似
                tid_i = int(tid)
                if cx < x1 or cx > x2:
                    continue
                track_hits[tid_i] = track_hits.get(tid_i, 0) + 1
                is_new_track = tid_i not in seen_tracks
                if is_new_track:
                    seen_tracks.add(tid_i)
                    prev_cy[tid_i] = cy
                    old = cy
                    door_gate.on_new_track(tid_i, cy)
                else:
                    old = prev_cy[tid_i]
                    prev_cy[tid_i] = cy

                track_key = f"{args.track_prefix}_{tid_i}"
                cooldown_key = f"{track_key}"
                last_f = last_event_frame.get(cooldown_key, -999999)
                if frame_idx - last_f < args.event_cooldown_frames:
                    continue

                event_type = door_gate.try_cross(cross_pending, tid_i, old, cy, 1)
                inferred = False
                if event_type is None:
                    infer_ev = door_gate.try_infer_enter(tid_i, cy, is_new_track)
                    if infer_ev:
                        event_type = infer_ev
                        inferred = True

                if event_type:
                    if track_hits.get(tid_i, 0) < min_track_hits:
                        event_type = None
                if event_type:
                    door_gate.commit(tid_i, event_type)
                    payload = {
                        "eventType": event_type,
                        "locationId": args.location_id,
                        "trackKey": track_key,
                        "eventTime": iso_from_base(base_time, frame_idx, fps),
                        "bestMatchScore": float(confs[idx]) if confs is not None else None,
                    }
                    try:
                        code, text = post_event(args.ingest_base_url, args.ingest_key, payload)
                        print(
                            f"[frame={frame_idx}] id={tid} event={event_type}{'*' if inferred else ''} conf={payload['bestMatchScore']:.3f} code={code} resp={text}"
                        )
                        if event_type == "enter":
                            enter_count += 1
                        else:
                            exit_count += 1
                        last_event_frame[cooldown_key] = frame_idx
                    except Exception as ex:
                        print(f"[frame={frame_idx}] id={tid} event={event_type} send_failed={ex}", file=sys.stderr)

                color = (0, 255, 0)
                cv2.rectangle(frame, (int(bx1), int(by1)), (int(bx2), int(by2)), color, 2)
                cv2.circle(frame, (int(cx), int(cy)), 4, color, -1)
                text = f"id:{tid}"
                if confs is not None:
                    text += f" {float(confs[idx]):.2f}"
                cv2.putText(frame, text, (int(bx1), int(by1) - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1, cv2.LINE_AA)

        cv2.putText(
            frame,
            f"YOLO+ByteTrack enter={enter_count} exit={exit_count} frame={frame_idx}",
            (14, 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (60, 255, 60),
            2,
            cv2.LINE_AA,
        )
        if writer is not None:
            writer.write(frame)

    if writer is not None:
        writer.release()

    print(f"done. enter={enter_count}, exit={exit_count}, frames={frame_idx}")


if __name__ == "__main__":
    main()
