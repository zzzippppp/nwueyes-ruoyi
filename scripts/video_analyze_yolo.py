#!/usr/bin/env python3
"""
YOLO + ByteTrack 视频分析（仅输出结果，不入库）

与直播链路一致：每次过线事件单独择优抓拍（进门追脸、出门体态），写入 events[]。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Dict, List, Tuple

import cv2

from roi_scale import REF_HEIGHT, REF_WIDTH, scale_roi_and_line
from line_crossing import (
    PerTrackDoorGate,
    exit_hysteresis_margin,
    min_track_hits_for_event,
    tight_infer_margin,
)
from snapshot_quality import VideoEventCapture, finalize_video_event_capture

try:
    from ultralytics import YOLO
except Exception as ex:
    print(f"[fatal] 缺少 ultralytics: {ex}", file=sys.stderr)
    sys.exit(2)


def resolve_video_path(video_path: str, uploaded_file_name: str, profile_root: str) -> str:
    if video_path:
        return video_path
    if not uploaded_file_name:
        raise ValueError("video_path 和 uploaded_file_name 不能同时为空")
    normalized = uploaded_file_name.replace("\\", "/")
    relative = normalized
    if relative.startswith("/profile/"):
        relative = relative[len("/profile/") :]
    relative = relative.lstrip("/")
    return os.path.normpath(os.path.join(profile_root, relative))


def time_sec(frame_idx: int, fps: float) -> float:
    return round(frame_idx / fps, 2) if fps > 0 else 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--uploaded-file-name", required=True)
    parser.add_argument("--profile-root", default="E:/nwueyes/uploadPath")
    parser.add_argument("--line-y", type=int, default=520)
    parser.add_argument("--roi", default="620,170,1290,760")
    parser.add_argument("--model", default="yolov8n.pt")
    parser.add_argument("--conf", type=float, default=0.35)
    parser.add_argument("--iou", type=float, default=0.6)
    parser.add_argument("--debug-out", required=True)
    parser.add_argument("--result-json", required=True)
    parser.add_argument("--event-cooldown-frames", type=int, default=20)
    parser.add_argument("--enter-infer-margin", type=int, default=80)
    parser.add_argument("--exit-infer-margin", type=int, default=80)
    parser.add_argument("--debug-video-url", default="")
    parser.add_argument("--task-id", default="")
    parser.add_argument("--storage-root", default="")
    parser.add_argument("--snapshot-window-sec", type=float, default=5.0)
    parser.add_argument("--enter-face-hunt-max-sec", type=float, default=10.0)
    parser.add_argument("--enter-face-grace-sec", type=float, default=4.0)
    parser.add_argument("--min-face-det-score", type=float, default=0.45)
    parser.add_argument("--track-prefix", default="yolo")
    args = parser.parse_args()

    video_path = resolve_video_path("", args.uploaded_file_name, args.profile_root)
    if not os.path.exists(video_path):
        raise FileNotFoundError(f"视频不存在: {video_path}")

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开视频: {video_path}")
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 25.0)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)) or 1920
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)) or 1080
    cap.release()

    x1, y1, x2, y2, line_y, scaled_roi = scale_roi_and_line(args.roi, args.line_y, width, height)
    tight_margin = tight_infer_margin(height, REF_HEIGHT)
    exit_margin = exit_hysteresis_margin(height, REF_HEIGHT)
    min_track_hits = min_track_hits_for_event(height, REF_HEIGHT)
    door_gate = PerTrackDoorGate(line_y, tight_margin, exit_margin)
    track_hits: Dict[int, int] = {}
    cross_pending: Dict[int, tuple[str, int]] = {}
    frame_area = width * height
    capture_enabled = bool(args.task_id and args.storage_root)
    if capture_enabled:
        print(
            f"[capture] event-level task={args.task_id} window={args.snapshot_window_sec}s "
            f"hunt={args.enter_face_hunt_max_sec}s grace={args.enter_face_grace_sec}s "
            f"storage={args.storage_root}",
            flush=True,
        )
    print(
        f"[roi] ref={REF_WIDTH}x{REF_HEIGHT} video={width}x{height} "
        f"base={args.roi} lineY={args.line_y} -> scaled={scaled_roi} lineY={line_y} "
        f"tightInferPx={tight_margin} exitMarginPx={exit_margin} minTrackHits={min_track_hits}"
    )

    os.makedirs(os.path.dirname(os.path.abspath(args.debug_out)), exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.result_json)), exist_ok=True)

    model = YOLO(args.model)
    prev_cy: Dict[int, float] = {}
    seen_tracks: set[int] = set()
    last_event_frame: Dict[str, int] = {}
    track_stats: Dict[int, dict] = {}
    events: List[dict] = []
    pending_captures: List[Tuple[dict, VideoEventCapture]] = []
    frame_idx = 0
    enter_count = 0
    exit_count = 0
    max_persons = 0

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(args.debug_out, fourcc, fps, (width, height))

    results_iter = model.track(
        source=video_path,
        stream=True,
        persist=True,
        tracker="bytetrack.yaml",
        classes=[0],
        conf=args.conf,
        iou=args.iou,
        verbose=False,
    )

    def flush_expired_captures(video_sec: float) -> None:
        if not pending_captures:
            return
        still_pending: List[Tuple[dict, VideoEventCapture]] = []
        for ev, cap in pending_captures:
            if cap.expired(video_sec):
                finalize_video_event_capture(cap, args.storage_root, args.task_id, ev)
            else:
                still_pending.append((ev, cap))
        pending_captures[:] = still_pending

    def start_event_capture(ev: dict, tid_i: int, event_type: str, video_sec: float) -> None:
        if not capture_enabled:
            return
        track_key = f"{args.track_prefix}_{tid_i}"
        cap = VideoEventCapture(
            track_key=track_key,
            event_type=event_type,
            tid_i=tid_i,
            line_y=line_y,
            window_sec=args.snapshot_window_sec,
            min_face_det_score=args.min_face_det_score,
            enter_face_hunt_max_sec=args.enter_face_hunt_max_sec,
            enter_face_grace_sec=args.enter_face_grace_sec,
            video_started_at=video_sec,
        )
        pending_captures.append((ev, cap))
        print(
            f"[capture-start] {track_key} {event_type} at {video_sec}s",
            flush=True,
        )

    for result in results_iter:
        frame_idx += 1
        video_sec = time_sec(frame_idx, fps)
        frame = result.orig_img.copy()
        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 180, 255), 2)
        cv2.line(frame, (x1, line_y), (x2, line_y), (255, 255, 0), 2)
        cv2.putText(
            frame,
            "down=enter up=exit",
            (x1 + 8, line_y - 24),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (0, 255, 255),
            2,
            cv2.LINE_AA,
        )

        persons_in_roi = 0
        track_boxes: Dict[int, tuple] = {}
        track_confs: Dict[int, float] = {}
        boxes = result.boxes
        if boxes is not None and boxes.id is not None:
            xyxy = boxes.xyxy.cpu().numpy()
            ids = boxes.id.int().cpu().numpy()
            confs = boxes.conf.cpu().numpy() if boxes.conf is not None else None
            for idx, tid in enumerate(ids):
                bx1, by1, bx2, by2 = xyxy[idx]
                cx = (bx1 + bx2) / 2.0
                cy = by2
                conf = float(confs[idx]) if confs is not None else 0.0
                tid_i = int(tid)
                in_corridor = x1 <= cx <= x2
                in_roi = in_corridor and y1 <= cy <= y2
                if not in_corridor:
                    continue
                if in_roi:
                    persons_in_roi += 1

                track_boxes[tid_i] = xyxy[idx]
                track_confs[tid_i] = conf
                track_hits[tid_i] = track_hits.get(tid_i, 0) + 1

                is_new_track = tid_i not in seen_tracks
                if is_new_track:
                    seen_tracks.add(tid_i)
                    prev_cy[tid_i] = cy
                    door_gate.on_new_track(tid_i, cy)
                else:
                    old = prev_cy[tid_i]
                    prev_cy[tid_i] = cy

                st = track_stats.get(tid_i)
                if st is None:
                    st = {
                        "trackId": tid_i,
                        "firstFrame": frame_idx,
                        "lastFrame": frame_idx,
                        "minConf": conf,
                        "maxConf": conf,
                        "sumConf": conf,
                        "hits": 1,
                    }
                    track_stats[tid_i] = st
                else:
                    st["lastFrame"] = frame_idx
                    st["minConf"] = min(st["minConf"], conf)
                    st["maxConf"] = max(st["maxConf"], conf)
                    st["sumConf"] += conf
                    st["hits"] += 1

                cooldown_key = f"t{tid_i}"
                event_type = None
                inferred = False
                if frame_idx - last_event_frame.get(cooldown_key, -999999) >= args.event_cooldown_frames:
                    event_type = door_gate.try_cross(cross_pending, tid_i, old if not is_new_track else cy, cy, 1)
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
                        ev = {
                            "frame": frame_idx,
                            "timeSec": video_sec,
                            "trackId": tid_i,
                            "trackKey": f"{args.track_prefix}_{tid_i}",
                            "eventType": event_type,
                            "confidence": round(conf, 3),
                            "inferred": inferred,
                        }
                        events.append(ev)
                        start_event_capture(ev, tid_i, event_type, video_sec)
                        if event_type == "enter":
                            enter_count += 1
                        else:
                            exit_count += 1
                        last_event_frame[cooldown_key] = frame_idx
                        label = f"{event_type.upper()}{'*' if inferred else ''}"
                        cv2.putText(
                            frame,
                            label,
                            (int(bx1), int(by1) - 22),
                            cv2.FONT_HERSHEY_SIMPLEX,
                            0.7,
                            (0, 0, 255) if event_type == "enter" else (255, 128, 0),
                            2,
                            cv2.LINE_AA,
                        )

                color = (0, 255, 0)
                cv2.rectangle(frame, (int(bx1), int(by1)), (int(bx2), int(by2)), color, 2)
                cv2.circle(frame, (int(cx), int(cy)), 5, color, -1)
                label = f"ID:{tid_i} {conf:.2f}"
                cv2.putText(
                    frame,
                    label,
                    (int(bx1), max(0, int(by1) - 8)),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.55,
                    color,
                    2,
                    cv2.LINE_AA,
                )

        if capture_enabled and pending_captures:
            for _ev, cap in pending_captures:
                box = track_boxes.get(cap.tid_i)
                if box is not None:
                    conf = track_confs.get(cap.tid_i, 0.0)
                    cap.try_sample(result.orig_img, box, conf, frame_area)
            flush_expired_captures(video_sec)

        max_persons = max(max_persons, persons_in_roi)
        cv2.putText(
            frame,
            f"YOLO+ByteTrack f={frame_idx} persons={persons_in_roi} enter={enter_count} exit={exit_count}",
            (14, 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.65,
            (60, 255, 60),
            2,
            cv2.LINE_AA,
        )
        writer.write(frame)
        if frame_idx % 50 == 0:
            print(f"[progress] frame={frame_idx} tracks={len(track_stats)} events={len(events)}")

    writer.release()

    final_sec = time_sec(frame_idx, fps)
    for ev, cap in list(pending_captures):
        finalize_video_event_capture(cap, args.storage_root, args.task_id, ev)
    pending_captures.clear()

    capture_events = sum(
        1 for ev in events if ev.get("faceImageUrl") or ev.get("bodyImageUrl") or ev.get("snapshotUrl")
    )

    tracks = []
    for tid, st in sorted(track_stats.items(), key=lambda x: x[0]):
        hits = st["hits"] or 1
        tracks.append(
            {
                "trackId": tid,
                "firstFrame": st["firstFrame"],
                "lastFrame": st["lastFrame"],
                "firstTimeSec": time_sec(st["firstFrame"], fps),
                "lastTimeSec": time_sec(st["lastFrame"], fps),
                "avgConfidence": round(st["sumConf"] / hits, 3),
                "hitFrames": hits,
            }
        )

    payload = {
        "algorithm": "YOLOv8 + ByteTrack",
        "model": args.model,
        "sourceVideo": video_path,
        "totalFrames": frame_idx,
        "fps": round(fps, 2),
        "width": width,
        "height": height,
        "refWidth": REF_WIDTH,
        "refHeight": REF_HEIGHT,
        "lineY": line_y,
        "roi": scaled_roi,
        "roiBase": args.roi,
        "lineYBase": args.line_y,
        "tightInferPx": tight_margin,
        "exitMarginPx": exit_margin,
        "minTrackHits": min_track_hits,
        "uniqueTracks": len(track_stats),
        "maxPersonsInRoi": max_persons,
        "enterCount": enter_count,
        "exitCount": exit_count,
        "events": events,
        "tracks": tracks,
        "captureMode": "event",
        "captureEventCount": capture_events,
        "debugVideoPath": args.debug_out.replace("\\", "/"),
        "debugVideoUrl": args.debug_video_url,
    }

    with open(args.result_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"[done] frames={frame_idx} tracks={len(tracks)} events={len(events)} captures={capture_events}")
    print(f"[result] {args.result_json}")
    print(f"[debug] {args.debug_out}")


if __name__ == "__main__":
    main()
