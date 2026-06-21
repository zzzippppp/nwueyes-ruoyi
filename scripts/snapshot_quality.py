#!/usr/bin/env python3
"""抓拍质量评分：InsightFace 检脸 + 体态质量门槛 + 直播择优窗口。"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Dict, Optional, Tuple

import cv2
import numpy as np


def sharpness(bgr) -> float:
    if bgr is None or bgr.size == 0:
        return 0.0
    if len(bgr.shape) == 2:
        gray = bgr
    else:
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def _clamp_box(box, frame_w: int, frame_h: int) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = [int(v) for v in box]
    x1 = max(0, min(frame_w - 1, x1))
    x2 = max(x1 + 1, min(frame_w, x2))
    y1 = max(0, min(frame_h - 1, y1))
    y2 = max(y1 + 1, min(frame_h, y2))
    return x1, y1, x2, y2


def body_shape_score(box, frame_w: int, frame_h: int) -> float:
    """惩罚贴边裁切、过窄竖条（门槛半遮挡）。"""
    x1, y1, x2, y2 = _clamp_box(box, frame_w, frame_h)
    bw = x2 - x1
    bh = max(1, y2 - y1)
    aspect = bw / bh
    if aspect < 0.22:
        return 0.0

    edge_margin = max(6, int(min(frame_w, frame_h) * 0.008))
    touches = 0
    if x1 <= edge_margin:
        touches += 1
    if x2 >= frame_w - edge_margin:
        touches += 1
    if y1 <= edge_margin:
        touches += 1
    if y2 >= frame_h - edge_margin:
        touches += 1
    if touches >= 2:
        return 0.15
    if touches == 1:
        return 0.55
    return 1.0


def crop_face_body(frame, box):
    """兼容旧逻辑：人体框 + 启发式顶部（分析/回放仍可用，直播优先 extract_quality_crops）。"""
    h, w = frame.shape[:2]
    x1, y1, x2, y2 = _clamp_box(box, w, h)
    body = frame[y1:y2, x1:x2].copy()
    fh = max(1, int((y2 - y1) * 0.42))
    face = frame[y1 : y1 + fh, x1:x2].copy()
    return face, body


def person_upper_region(frame, box, upper_ratio: float = 0.72) -> np.ndarray:
    """人体框上半部（含脸），用于 InsightFace。"""
    h, w = frame.shape[:2]
    x1, y1, x2, y2 = _clamp_box(box, w, h)
    bh = y2 - y1
    upper_y2 = y1 + max(1, int(bh * upper_ratio))
    pad_x = int((x2 - x1) * 0.08)
    rx1 = max(0, x1 - pad_x)
    rx2 = min(w, x2 + pad_x)
    return frame[y1:upper_y2, rx1:rx2].copy()


def extract_quality_crops(
    frame,
    box,
    conf: float,
    frame_area: int,
    min_face_det_score: float = 0.45,
    require_face: bool = False,
) -> tuple[Optional[np.ndarray], Optional[np.ndarray], float, float]:
    """
    从当前帧提取合格的人脸/体态 crop。
    人脸：InsightFace 检测；体态：完整人框 + 形状/清晰度评分。
    """
    h, w = frame.shape[:2]
    x1, y1, x2, y2 = _clamp_box(box, w, h)
    body = frame[y1:y2, x1:x2].copy()
    if body.size == 0:
        return None, None, 0.0, 0.0

    shape = body_shape_score((x1, y1, x2, y2), w, h)
    body_score = score_image(body, conf, frame_area) * shape
    if body_score <= 0.05:
        return None, None, 0.0, body_score

    face_img = None
    face_score = 0.0
    try:
        from face_embedder import detect_best_face_crop

        region = person_upper_region(frame, (x1, y1, x2, y2))
        face_img, det_score = detect_best_face_crop(region, min_det_score=min_face_det_score)
        if face_img is not None and face_img.size > 0:
            face_score = score_image(face_img, max(conf, det_score), frame_area)
    except Exception:
        face_img = None
        face_score = 0.0

    if require_face and (face_img is None or face_score <= 0.08):
        return None, body if body_score > 0.08 else None, face_score, body_score

    return face_img, body, face_score, body_score


def score_image(crop, conf: float, frame_area: int) -> float:
    if crop is None or crop.size == 0:
        return 0.0
    area = crop.shape[0] * crop.shape[1]
    sh = sharpness(crop)
    sh_n = min(sh / 180.0, 1.0)
    area_n = min(area / max(frame_area * 0.12, 1), 1.0)
    conf_n = max(0.0, min(float(conf), 1.0))
    return conf_n * 0.35 + sh_n * 0.45 + area_n * 0.20


class TrackBestShots:
    def __init__(self, track_id: int, first_frame: int, window_end_frame: int):
        self.track_id = track_id
        self.first_frame = first_frame
        self.window_end_frame = window_end_frame
        self.sampled_frames = 0
        self.best_face_score = -1.0
        self.best_body_score = -1.0
        self.best_face_img = None
        self.best_body_img = None

    def in_window(self, frame_idx: int) -> bool:
        return self.first_frame <= frame_idx <= self.window_end_frame

    def update(
        self,
        frame,
        box,
        conf: float,
        frame_area: int,
        min_face_det_score: float = 0.45,
    ):
        face, body, fs, bs = extract_quality_crops(
            frame, box, conf, frame_area, min_face_det_score=min_face_det_score
        )
        self.sampled_frames += 1
        if face is not None and fs > self.best_face_score:
            self.best_face_score = fs
            self.best_face_img = face.copy()
        if body is not None and bs > self.best_body_score:
            self.best_body_score = bs
            self.best_body_img = body.copy()


@dataclass
class LiveEventCapture:
    """过线确认后择优抓拍再 ingest；进门会持续追脸直到检出或超时。"""

    track_key: str
    event_type: str
    tid_i: int
    line_y: int
    window_sec: float
    min_face_det_score: float = 0.45
    enter_inside_px: int = 15
    enter_face_hunt_max_sec: float = 10.0
    enter_face_grace_sec: float = 4.0
    started_at: float = field(default_factory=time.time)
    deadline: float = field(default_factory=lambda: 0.0)
    face_first_seen_at: Optional[float] = None
    samples: int = 0
    best_face_score: float = -1.0
    best_body_score: float = -1.0
    best_face_img: Optional[np.ndarray] = None
    best_body_img: Optional[np.ndarray] = None
    best_face_frame_img: Optional[np.ndarray] = None
    best_body_frame_img: Optional[np.ndarray] = None

    def __post_init__(self):
        if self.deadline <= 0:
            self.deadline = self.started_at + self.window_sec

    def try_sample(self, frame, box, conf: float, frame_area: int) -> bool:
        h, w = frame.shape[:2]
        _, _, _, y2 = _clamp_box(box, w, h)
        foot_y = float(y2)

        if self.event_type == "exit" and foot_y > self.line_y + self.enter_inside_px:
            return False

        face, body, fs, bs = extract_quality_crops(
            frame,
            box,
            conf,
            frame_area,
            min_face_det_score=self.min_face_det_score,
            require_face=False,
        )
        self.samples += 1
        updated = False
        if face is not None and fs > self.best_face_score:
            self.best_face_score = fs
            self.best_face_img = face.copy()
            self.best_face_frame_img = frame.copy()
            if self.face_first_seen_at is None:
                self.face_first_seen_at = time.time()
            updated = True
        if body is not None and bs > self.best_body_score:
            self.best_body_score = bs
            self.best_body_img = body.copy()
            self.best_body_frame_img = frame.copy()
            updated = True
        return updated

    def finalize_snapshot_frame(self) -> Optional[np.ndarray]:
        """返回用于向量择优的那一帧整幅监控画面。"""
        if self.event_type == "enter":
            if self.best_face_frame_img is not None:
                return self.best_face_frame_img
            return self.best_body_frame_img
        return self.best_body_frame_img

    def expired(self, now: float) -> bool:
        if self.event_type == "enter":
            if now >= self.started_at + self.enter_face_hunt_max_sec:
                return True
            if self.best_face_img is not None and self.face_first_seen_at is not None:
                return now >= self.face_first_seen_at + self.enter_face_grace_sec
            return False
        return now >= self.deadline

    def hunt_elapsed_sec(self, now: float) -> float:
        return max(0.0, now - self.started_at)

    def quality_flag(self) -> str:
        if self.event_type == "enter":
            if self.best_face_img is not None:
                return "normal"
            if self.best_body_img is not None:
                return "low"
            return "missing"
        if self.best_body_img is not None:
            return "normal"
        return "missing"

    def finalize_images(self) -> tuple[Optional[np.ndarray], Optional[np.ndarray]]:
        face = self.best_face_img if self.event_type == "enter" else None
        body = self.best_body_img
        return face, body


@dataclass
class VideoEventCapture:
    """视频分析用过线抓拍：与 LiveEventCapture 逻辑一致，但用视频时间轴（秒）而非墙钟。"""

    track_key: str
    event_type: str
    tid_i: int
    line_y: int
    window_sec: float
    min_face_det_score: float = 0.45
    enter_inside_px: int = 15
    enter_face_hunt_max_sec: float = 10.0
    enter_face_grace_sec: float = 4.0
    video_started_at: float = 0.0
    face_first_seen_at: Optional[float] = None
    samples: int = 0
    best_face_score: float = -1.0
    best_body_score: float = -1.0
    best_face_img: Optional[np.ndarray] = None
    best_body_img: Optional[np.ndarray] = None
    best_face_frame_img: Optional[np.ndarray] = None
    best_body_frame_img: Optional[np.ndarray] = None

    def try_sample(self, frame, box, conf: float, frame_area: int) -> bool:
        h, w = frame.shape[:2]
        _, _, _, y2 = _clamp_box(box, w, h)
        foot_y = float(y2)
        if self.event_type == "exit" and foot_y > self.line_y + self.enter_inside_px:
            return False
        face, body, fs, bs = extract_quality_crops(
            frame, box, conf, frame_area,
            min_face_det_score=self.min_face_det_score,
            require_face=False,
        )
        self.samples += 1
        updated = False
        if face is not None and fs > self.best_face_score:
            self.best_face_score = fs
            self.best_face_img = face.copy()
            self.best_face_frame_img = frame.copy()
            if self.face_first_seen_at is None:
                self.face_first_seen_at = self.video_started_at
            updated = True
        if body is not None and bs > self.best_body_score:
            self.best_body_score = bs
            self.best_body_img = body.copy()
            self.best_body_frame_img = frame.copy()
            updated = True
        return updated

    def finalize_snapshot_frame(self) -> Optional[np.ndarray]:
        if self.event_type == "enter":
            if self.best_face_frame_img is not None:
                return self.best_face_frame_img
            return self.best_body_frame_img
        return self.best_body_frame_img

    def expired(self, video_sec: float) -> bool:
        if self.event_type == "enter":
            if video_sec >= self.video_started_at + self.enter_face_hunt_max_sec:
                return True
            if self.best_face_img is not None and self.face_first_seen_at is not None:
                return video_sec >= self.face_first_seen_at + self.enter_face_grace_sec
            return False
        return video_sec >= self.video_started_at + self.window_sec

    def quality_flag(self) -> str:
        if self.event_type == "enter":
            if self.best_face_img is not None:
                return "normal"
            if self.best_body_img is not None:
                return "low"
            return "missing"
        if self.best_body_img is not None:
            return "normal"
        return "missing"

    def finalize_images(self) -> tuple[Optional[np.ndarray], Optional[np.ndarray]]:
        face = self.best_face_img if self.event_type == "enter" else None
        body = self.best_body_img
        return face, body


def save_analyze_event_snapshot(
    storage_root: str,
    task_id: str,
    track_key: str,
    event_type: str,
    face_img,
    body_img,
    frame_img=None,
) -> tuple[str, str, str]:
    """视频过线事件落盘，返回 face_url, body_url, snapshot_url。"""
    import os
    import time as _time

    now = _time.localtime()
    yyyy = _time.strftime("%Y", now)
    mm = _time.strftime("%m", now)
    dd = _time.strftime("%d", now)
    face_dir = os.path.join(storage_root, "log_library", "face", yyyy, mm, dd)
    body_dir = os.path.join(storage_root, "log_library", "body", yyyy, mm, dd)
    snap_dir = os.path.join(storage_root, "snapshot_library", yyyy, mm, dd)
    os.makedirs(face_dir, exist_ok=True)
    os.makedirs(body_dir, exist_ok=True)
    os.makedirs(snap_dir, exist_ok=True)
    date_url = f"{yyyy}/{mm}/{dd}"
    ts = int(_time.time() * 1000)
    face_url = ""
    body_url = ""
    snapshot_url = ""
    safe_key = track_key.replace("/", "_")
    if face_img is not None and face_img.size > 0 and event_type == "enter":
        face_name = f"analyze_{task_id}_{safe_key}_{event_type}_{ts}_face.jpg"
        cv2.imwrite(os.path.join(face_dir, face_name), face_img)
        face_url = f"/dashboard/storage/file/log/face/{date_url}/{face_name}"
    if body_img is not None and body_img.size > 0:
        body_name = f"analyze_{task_id}_{safe_key}_{event_type}_{ts}_body.jpg"
        cv2.imwrite(os.path.join(body_dir, body_name), body_img)
        body_url = f"/dashboard/storage/file/log/body/{date_url}/{body_name}"
    if frame_img is not None and getattr(frame_img, "size", 0) > 0:
        snap_name = f"analyze_{task_id}_{safe_key}_{event_type}_{ts}_snap.jpg"
        cv2.imwrite(
            os.path.join(snap_dir, snap_name),
            frame_img,
            [int(cv2.IMWRITE_JPEG_QUALITY), 92],
        )
        snapshot_url = f"/dashboard/storage/file/snapshot/{date_url}/{snap_name}"
    return face_url, body_url, snapshot_url


def finalize_video_event_capture(
    capture: VideoEventCapture,
    storage_root: str,
    task_id: str,
    event: dict,
) -> None:
    face_img, body_img = capture.finalize_images()
    quality = capture.quality_flag()
    face_url, body_url, snapshot_url = save_analyze_event_snapshot(
        storage_root,
        task_id,
        capture.track_key,
        capture.event_type,
        face_img,
        body_img,
        capture.finalize_snapshot_frame(),
    )
    event["faceImageUrl"] = face_url
    event["bodyImageUrl"] = body_url
    event["snapshotUrl"] = snapshot_url
    event["qualityFlag"] = quality
    event["captureSamples"] = capture.samples
    print(
        f"[capture-done] {capture.track_key} {capture.event_type} samples={capture.samples} "
        f"face={'yes' if face_url else 'no'} body={'yes' if body_url else 'no'} quality={quality}",
        flush=True,
    )


def save_track_snapshots(
    track_shots: dict[int, "TrackBestShots"],
    task_id: str,
    storage_root: str,
    track_prefix: str = "yolo",
    window_sec: float = 5.0,
) -> list[dict]:
    """落盘最佳人脸/人体图到 log_library，并返回 manifest 轨迹列表。"""
    import json
    import os
    from datetime import datetime

    now = datetime.now()
    yyyy = now.strftime("%Y")
    mm = now.strftime("%m")
    dd = now.strftime("%d")
    face_dir = os.path.join(storage_root, "log_library", "face", yyyy, mm, dd)
    body_dir = os.path.join(storage_root, "log_library", "body", yyyy, mm, dd)
    os.makedirs(face_dir, exist_ok=True)
    os.makedirs(body_dir, exist_ok=True)
    date_url = f"{yyyy}/{mm}/{dd}"

    manifest_tracks = []
    for tid, shot in sorted(track_shots.items(), key=lambda x: x[0]):
        track_key = f"{track_prefix}_{tid}"
        face_url = ""
        body_url = ""
        if shot.best_face_img is not None and shot.best_face_img.size > 0:
            face_name = f"analyze_{task_id}_{track_key}.jpg"
            cv2.imwrite(os.path.join(face_dir, face_name), shot.best_face_img)
            face_url = f"/dashboard/storage/file/log/face/{date_url}/{face_name}"
        if shot.best_body_img is not None and shot.best_body_img.size > 0:
            body_name = f"analyze_{task_id}_{track_key}.jpg"
            cv2.imwrite(os.path.join(body_dir, body_name), shot.best_body_img)
            body_url = f"/dashboard/storage/file/log/body/{date_url}/{body_name}"
        manifest_tracks.append(
            {
                "trackId": tid,
                "trackKey": track_key,
                "faceImageUrl": face_url,
                "bodyImageUrl": body_url,
                "faceScore": round(shot.best_face_score, 3) if shot.best_face_score >= 0 else None,
                "bodyScore": round(shot.best_body_score, 3) if shot.best_body_score >= 0 else None,
                "sampledFrames": shot.sampled_frames,
                "windowSec": window_sec,
            }
        )
        print(
            f"[capture {track_key}] samples={shot.sampled_frames} "
            f"face={shot.best_face_score:.3f} body={shot.best_body_score:.3f}"
        )

    out_dir = os.path.join(storage_root, "capture_manifest")
    os.makedirs(out_dir, exist_ok=True)
    manifest_path = os.path.join(out_dir, f"{task_id}.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"taskId": task_id, "tracks": manifest_tracks}, f, ensure_ascii=False, indent=2)
    print(f"[capture] manifest={manifest_path} tracks={len(manifest_tracks)}")
    return manifest_tracks


def save_live_event_snapshot(
    storage_root: str,
    task_id: str,
    track_key: str,
    event_type: str,
    face_img,
    body_img,
    frame_img=None,
) -> tuple[str, str, str]:
    """直播过线事件落盘，返回 face_url, body_url, snapshot_url。"""
    import os
    import time as _time

    now = _time.localtime()
    yyyy = _time.strftime("%Y", now)
    mm = _time.strftime("%m", now)
    dd = _time.strftime("%d", now)
    face_dir = os.path.join(storage_root, "log_library", "face", yyyy, mm, dd)
    body_dir = os.path.join(storage_root, "log_library", "body", yyyy, mm, dd)
    snap_dir = os.path.join(storage_root, "snapshot_library", yyyy, mm, dd)
    os.makedirs(face_dir, exist_ok=True)
    os.makedirs(body_dir, exist_ok=True)
    os.makedirs(snap_dir, exist_ok=True)
    date_url = f"{yyyy}/{mm}/{dd}"
    ts = int(_time.time() * 1000)
    face_url = ""
    body_url = ""
    snapshot_url = ""
    if face_img is not None and face_img.size > 0 and event_type == "enter":
        face_name = f"live_{task_id}_{track_key}_{ts}_face.jpg"
        cv2.imwrite(os.path.join(face_dir, face_name), face_img)
        face_url = f"/dashboard/storage/file/log/face/{date_url}/{face_name}"
    if body_img is not None and body_img.size > 0:
        body_name = f"live_{task_id}_{track_key}_{ts}_body.jpg"
        cv2.imwrite(os.path.join(body_dir, body_name), body_img)
        body_url = f"/dashboard/storage/file/log/body/{date_url}/{body_name}"
    if frame_img is not None and getattr(frame_img, "size", 0) > 0:
        snap_name = f"live_{task_id}_{track_key}_{ts}_snap.jpg"
        cv2.imwrite(
            os.path.join(snap_dir, snap_name),
            frame_img,
            [int(cv2.IMWRITE_JPEG_QUALITY), 92],
        )
        snapshot_url = f"/dashboard/storage/file/snapshot/{date_url}/{snap_name}"
    return face_url, body_url, snapshot_url
