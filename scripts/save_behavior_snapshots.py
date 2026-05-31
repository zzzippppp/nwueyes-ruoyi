#!/usr/bin/env python3
"""从视频分析结果截取事件帧的人脸/人体图，供行为日志入库。"""

from __future__ import annotations

import argparse
import json
import os
import sys

import cv2

try:
    from ultralytics import YOLO
except Exception as ex:
    print(f"[fatal] 缺少 ultralytics: {ex}", file=sys.stderr)
    sys.exit(2)


def pick_person_box(result, track_id: int | None):
    boxes = result.boxes
    if boxes is None or len(boxes) == 0:
        return None
    if track_id is not None and boxes.id is not None:
        ids = boxes.id.int().cpu().numpy()
        xyxy = boxes.xyxy.cpu().numpy()
        for idx, tid in enumerate(ids):
            if int(tid) == int(track_id):
                return xyxy[idx]
    return boxes.xyxy[0].cpu().numpy()


def crop_face_body(frame, box):
    h, w = frame.shape[:2]
    x1, y1, x2, y2 = [int(v) for v in box]
    x1 = max(0, min(w - 1, x1))
    x2 = max(x1 + 1, min(w, x2))
    y1 = max(0, min(h - 1, y1))
    y2 = max(y1 + 1, min(h, y2))
    body = frame[y1:y2, x1:x2].copy()
    fh = max(1, int((y2 - y1) * 0.45))
    face = frame[y1 : y1 + fh, x1:x2].copy()
    return face, body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video-path", required=True)
    parser.add_argument("--result-json", required=True)
    parser.add_argument("--task-id", required=True)
    parser.add_argument("--profile-root", required=True)
    parser.add_argument("--url-prefix", default="/profile/behavior_snapshots")
    args = parser.parse_args()

    if not os.path.exists(args.video_path):
        raise FileNotFoundError(f"视频不存在: {args.video_path}")
    if not os.path.exists(args.result_json):
        raise FileNotFoundError(f"结果 JSON 不存在: {args.result_json}")

    with open(args.result_json, "r", encoding="utf-8") as f:
        payload = json.load(f)
    events = payload.get("events") or []

    rel_dir = os.path.join("behavior_snapshots", args.task_id)
    out_dir = os.path.join(args.profile_root, rel_dir)
    os.makedirs(out_dir, exist_ok=True)

    model = YOLO("yolov8n.pt")
    cap = cv2.VideoCapture(args.video_path)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开视频: {args.video_path}")

    manifest = []
    for idx, event in enumerate(events):
        frame_no = int(event.get("frame") or 0)
        track_id = event.get("trackId")
        cap.set(cv2.CAP_PROP_POS_FRAMES, max(0, frame_no - 1))
        ok, frame = cap.read()
        face_url = ""
        body_url = ""
        if ok:
            result = model.predict(frame, classes=[0], conf=0.25, verbose=False)[0]
            box = pick_person_box(result, track_id)
            if box is not None:
                face_img, body_img = crop_face_body(frame, box)
                face_name = f"e{idx}_face.jpg"
                body_name = f"e{idx}_body.jpg"
                cv2.imwrite(os.path.join(out_dir, face_name), face_img)
                cv2.imwrite(os.path.join(out_dir, body_name), body_img)
                face_url = f"{args.url_prefix}/{args.task_id}/{face_name}"
                body_url = f"{args.url_prefix}/{args.task_id}/{body_name}"
            else:
                full_name = f"e{idx}_frame.jpg"
                cv2.imwrite(os.path.join(out_dir, full_name), frame)
                face_url = f"{args.url_prefix}/{args.task_id}/{full_name}"
                body_url = face_url
        manifest.append(
            {
                "index": idx,
                "frame": frame_no,
                "trackId": track_id,
                "eventType": event.get("eventType"),
                "faceImageUrl": face_url.replace("\\", "/"),
                "bodyImageUrl": body_url.replace("\\", "/"),
            }
        )

    cap.release()
    manifest_path = os.path.join(out_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"events": manifest}, f, ensure_ascii=False, indent=2)
    print(f"[done] snapshots={len(manifest)} dir={out_dir}")


if __name__ == "__main__":
    main()
