#!/usr/bin/env python3
"""
Phase 2: 录像回放主测试（最小可用）

作用：
- 读取本地视频（可直接使用若依 /common/upload 返回的 fileName）
- 在给定 ROI 内做运动目标检测 + 简单跟踪
- 目标穿越 line_y 时上报 enter/exit 到后端 ingest 接口

说明：
- 这是主测试链路的“可运行骨架”，用于打通事件入库与看板联调；
- 人脸识别/ReID 后续再替换进来。
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import sys
import time
from dataclasses import dataclass
from typing import Dict, List, Tuple
from urllib import request

import cv2


@dataclass
class Track:
    track_id: int
    cx: float
    cy: float
    prev_cy: float
    last_frame: int


def parse_roi(raw: str, width: int, height: int) -> Tuple[int, int, int, int]:
    if not raw:
        return (0, 0, width, height)
    parts = [int(x.strip()) for x in raw.split(",")]
    if len(parts) != 4:
        raise ValueError("roi 格式应为 x1,y1,x2,y2")
    x1, y1, x2, y2 = parts
    x1 = max(0, min(width - 1, x1))
    x2 = max(1, min(width, x2))
    y1 = max(0, min(height - 1, y1))
    y2 = max(1, min(height, y2))
    if x2 <= x1 or y2 <= y1:
        raise ValueError("roi 坐标无效")
    return x1, y1, x2, y2


def resolve_video_path(video_path: str, uploaded_file_name: str, profile_root: str) -> str:
    if video_path:
        return video_path
    if not uploaded_file_name:
        raise ValueError("video_path 和 uploaded_file_name 不能同时为空")
    normalized = uploaded_file_name.replace("\\", "/")
    if normalized.startswith("http://") or normalized.startswith("https://"):
        raise ValueError("uploaded_file_name 请传 /common/upload 返回的 fileName，而不是完整 URL")
    # /profile/upload/xxx.mp4 -> upload/xxx.mp4
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


def distance(a: Tuple[float, float], b: Tuple[float, float]) -> float:
    return math.sqrt((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video-path", default="")
    parser.add_argument("--uploaded-file-name", default="")
    parser.add_argument("--profile-root", default="D:/ruoyi/uploadPath")
    parser.add_argument("--ingest-base-url", default="http://localhost:8080")
    parser.add_argument("--ingest-key", default="local-presence-key")
    parser.add_argument("--location-id", type=int, default=1)
    parser.add_argument("--line-y", type=int, default=520, help="过线 y 坐标（向下=enter，向上=exit）")
    parser.add_argument("--roi", default="", help="x1,y1,x2,y2")
    parser.add_argument("--min-area", type=int, default=1600)
    parser.add_argument("--max-distance", type=int, default=90)
    parser.add_argument("--max-miss-frames", type=int, default=20)
    parser.add_argument("--realtime", action="store_true")
    parser.add_argument("--show", action="store_true")
    parser.add_argument("--debug-out", default="")
    parser.add_argument("--track-prefix", default="replay")
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
    roi = parse_roi(args.roi, width, height)
    x1, y1, x2, y2 = roi
    line_y = args.line_y

    writer = None
    if args.debug_out:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(args.debug_out, fourcc, fps, (width, height))

    back_sub = cv2.createBackgroundSubtractorMOG2(history=400, varThreshold=25, detectShadows=False)
    tracks: Dict[int, Track] = {}
    next_track_id = 1

    base_time = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
    frame_idx = 0
    enter_count = 0
    exit_count = 0

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_idx += 1
        roi_frame = frame[y1:y2, x1:x2]
        mask = back_sub.apply(roi_frame)
        mask = cv2.GaussianBlur(mask, (5, 5), 0)
        _, mask = cv2.threshold(mask, 210, 255, cv2.THRESH_BINARY)
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
        mask = cv2.dilate(mask, kernel, iterations=2)

        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        detections: List[Tuple[float, float, Tuple[int, int, int, int]]] = []
        for cnt in contours:
            area = cv2.contourArea(cnt)
            if area < args.min_area:
                continue
            rx, ry, rw, rh = cv2.boundingRect(cnt)
            cx = x1 + rx + rw / 2.0
            cy = y1 + ry + rh  # 框底中心更接近“脚点”
            detections.append((cx, cy, (x1 + rx, y1 + ry, rw, rh)))

        # 简单最近邻匹配
        unmatched_tracks = set(tracks.keys())
        matched_track_ids = set()
        for cx, cy, _bbox in detections:
            best_id = None
            best_dist = 1e9
            for tid in list(unmatched_tracks):
                d = distance((cx, cy), (tracks[tid].cx, tracks[tid].cy))
                if d < best_dist and d <= args.max_distance:
                    best_dist = d
                    best_id = tid
            if best_id is None:
                tid = next_track_id
                next_track_id += 1
                tracks[tid] = Track(tid, cx, cy, cy, frame_idx)
                matched_track_ids.add(tid)
            else:
                tr = tracks[best_id]
                tr.prev_cy = tr.cy
                tr.cx = cx
                tr.cy = cy
                tr.last_frame = frame_idx
                matched_track_ids.add(best_id)
                unmatched_tracks.remove(best_id)

        # 清理长期失联 track
        to_delete = [tid for tid, tr in tracks.items() if frame_idx - tr.last_frame > args.max_miss_frames]
        for tid in to_delete:
            tracks.pop(tid, None)

        # 过线触发
        for tid in sorted(matched_track_ids):
            tr = tracks[tid]
            crossed_down = tr.prev_cy <= line_y < tr.cy
            crossed_up = tr.prev_cy >= line_y > tr.cy
            if not crossed_down and not crossed_up:
                continue
            event_type = "enter" if crossed_down else "exit"
            payload = {
                "eventType": event_type,
                "locationId": args.location_id,
                "trackKey": f"{args.track_prefix}_{tid}",
                "eventTime": iso_from_base(base_time, frame_idx, fps),
            }
            try:
                code, text = post_event(args.ingest_base_url, args.ingest_key, payload)
                print(f"[frame={frame_idx}] track={tid} event={event_type} code={code} resp={text}")
                if event_type == "enter":
                    enter_count += 1
                else:
                    exit_count += 1
            except Exception as ex:
                print(f"[frame={frame_idx}] track={tid} event={event_type} send_failed={ex}", file=sys.stderr)

        # 调试绘制
        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 180, 255), 2)
        cv2.line(frame, (x1, line_y), (x2, line_y), (255, 255, 0), 2)
        for tid, tr in tracks.items():
            cv2.circle(frame, (int(tr.cx), int(tr.cy)), 4, (0, 255, 0), -1)
            cv2.putText(
                frame,
                f"id:{tid}",
                (int(tr.cx) + 4, int(tr.cy) - 6),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.45,
                (0, 255, 0),
                1,
                cv2.LINE_AA,
            )
        cv2.putText(
            frame,
            f"enter={enter_count} exit={exit_count} frame={frame_idx}",
            (14, 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (60, 255, 60),
            2,
            cv2.LINE_AA,
        )

        if writer is not None:
            writer.write(frame)
        if args.show:
            cv2.imshow("video_replay_worker", frame)
            key = cv2.waitKey(1) & 0xFF
            if key == 27:
                break
        if args.realtime and fps > 0:
            time.sleep(1.0 / fps)

    cap.release()
    if writer is not None:
        writer.release()
    if args.show:
        cv2.destroyAllWindows()

    print(f"done. enter={enter_count}, exit={exit_count}, frames={frame_idx}")


if __name__ == "__main__":
    main()
