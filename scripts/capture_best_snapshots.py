#!/usr/bin/env python3
"""
在轨迹首次出现后的时间窗口内，挑选最佳人脸/人体抓拍并落盘到 face_library / body_library。

说明：
- 不在过线单帧上硬截，而是在「人出现在画面后的前几秒」内选质量最好的图
- 质量 = 清晰度 + YOLO 置信度 + 目标大小
- 行为日志仅展示用，不参与陌生人研判
"""

from __future__ import annotations

import argparse
import json
import os
import sys

import cv2

from snapshot_quality import TrackBestShots

try:
    from ultralytics import YOLO
except Exception as ex:
    print(f"[fatal] 缺少 ultralytics: {ex}", file=sys.stderr)
    sys.exit(2)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video-path", required=True)
    parser.add_argument("--result-json", required=True)
    parser.add_argument("--task-id", required=True)
    parser.add_argument("--storage-root", required=True, help="face_library/body_library 所在项目根目录")
    parser.add_argument("--window-sec", type=float, default=5.0, help="首次出现后采样窗口（秒）")
    parser.add_argument("--track-prefix", default="yolo")
    args = parser.parse_args()

    if not os.path.exists(args.video_path):
        raise FileNotFoundError(f"视频不存在: {args.video_path}")
    if not os.path.exists(args.result_json):
        raise FileNotFoundError(f"结果 JSON 不存在: {args.result_json}")

    with open(args.result_json, "r", encoding="utf-8") as f:
        payload = json.load(f)

    fps = float(payload.get("fps") or 25.0)
    tracks_meta = payload.get("tracks") or []
    if not tracks_meta:
        print("[warn] 无轨迹信息，跳过抓拍")
        _write_manifest(args.storage_root, args.task_id, [])
        return

    window_frames = max(1, int(round(args.window_sec * fps)))
    track_shots: dict[int, TrackBestShots] = {}
    for t in tracks_meta:
        tid = int(t["trackId"])
        first_f = int(t["firstFrame"])
        last_f = int(t.get("lastFrame") or first_f)
        end_f = min(last_f, first_f + window_frames)
        track_shots[tid] = TrackBestShots(tid, first_f, end_f)

    cap = cv2.VideoCapture(args.video_path)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开视频: {args.video_path}")
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)) or 1920
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)) or 1080
    frame_area = width * height
    cap.release()

    model = YOLO("yolov8n.pt")
    results_iter = model.track(
        source=args.video_path,
        stream=True,
        persist=True,
        tracker="bytetrack.yaml",
        classes=[0],
        conf=0.30,
        verbose=False,
    )

    frame_idx = 0
    active = True
    for result in results_iter:
        frame_idx += 1
        if not any(s.in_window(frame_idx) for s in track_shots.values()):
            if frame_idx > max(s.window_end_frame for s in track_shots.values()):
                active = False
            if not active:
                break
            continue

        frame = result.orig_img
        boxes = result.boxes
        if boxes is None or boxes.id is None:
            continue
        xyxy = boxes.xyxy.cpu().numpy()
        ids = boxes.id.int().cpu().numpy()
        confs = boxes.conf.cpu().numpy() if boxes.conf is not None else None
        for idx, tid in enumerate(ids):
            tid_i = int(tid)
            shot = track_shots.get(tid_i)
            if shot is None or not shot.in_window(frame_idx):
                continue
            conf = float(confs[idx]) if confs is not None else 0.0
            shot.update(frame, xyxy[idx], conf, frame_area)

    face_dir = os.path.join(args.storage_root, "face_library")
    body_dir = os.path.join(args.storage_root, "body_library")
    os.makedirs(face_dir, exist_ok=True)
    os.makedirs(body_dir, exist_ok=True)

    manifest_tracks = []
    for tid, shot in sorted(track_shots.items(), key=lambda x: x[0]):
        track_key = f"{args.track_prefix}_{tid}"
        face_url = ""
        body_url = ""
        if shot.best_face_img is not None and shot.best_face_img.size > 0:
            face_name = f"face_{args.task_id}_{track_key}.jpg"
            cv2.imwrite(os.path.join(face_dir, face_name), shot.best_face_img)
            face_url = f"/dashboard/data-board/file/face/{face_name}"
        if shot.best_body_img is not None and shot.best_body_img.size > 0:
            body_name = f"body_{args.task_id}_{track_key}.jpg"
            cv2.imwrite(os.path.join(body_dir, body_name), shot.best_body_img)
            body_url = f"/dashboard/data-board/file/body/{body_name}"
        manifest_tracks.append(
            {
                "trackId": tid,
                "trackKey": track_key,
                "faceImageUrl": face_url,
                "bodyImageUrl": body_url,
                "faceScore": round(shot.best_face_score, 3) if shot.best_face_score >= 0 else None,
                "bodyScore": round(shot.best_body_score, 3) if shot.best_body_score >= 0 else None,
                "sampledFrames": shot.sampled_frames,
                "windowSec": args.window_sec,
            }
        )
        print(
            f"[track {track_key}] samples={shot.sampled_frames} "
            f"face={shot.best_face_score:.3f} body={shot.best_body_score:.3f}"
        )

    _write_manifest(args.storage_root, args.task_id, manifest_tracks)
    print(f"[done] tracks={len(manifest_tracks)}")


def _write_manifest(storage_root: str, task_id: str, tracks: list):
    out_dir = os.path.join(storage_root, "capture_manifest")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{task_id}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"taskId": task_id, "tracks": tracks}, f, ensure_ascii=False, indent=2)
    print(f"[manifest] {path}")


if __name__ == "__main__":
    main()
