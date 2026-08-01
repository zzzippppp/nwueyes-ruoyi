#!/usr/bin/env python3
"""
离线视频分析（门线行为 + FaceFrame 级全画面选脸）

- YOLO + ByteTrack：脚点相对门线判定 enter / exit / pass（不再使用门框 ROI）
- 全画面 InsightFace 密采样检脸 + 多维质量分，每人保留综合分最高的一张脸
- 最佳脸所在整帧作为日志监控画面（snapshot）；无人脸时改用「最清晰体态所在整帧 / 人在画面中央」整帧
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np

from face_quality import (
    detect_scored_faces,
    match_faces_to_person_boxes,
    should_replace_best_face,
    write_jpeg,
)
from line_crossing import (
    SIDE_INSIDE,
    PerTrackDoorGate,
    exit_hysteresis_margin,
    min_track_hits_for_event,
    tight_infer_margin,
)
from roi_scale import REF_HEIGHT, REF_WIDTH
from snapshot_quality import body_shape_score, score_image, sharpness, normalize_bbox

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


def scale_line_y(line_y: int, height: int, ref_height: int) -> int:
    if height <= 0 or ref_height <= 0:
        return max(0, line_y)
    scaled = int(round(line_y * (height / ref_height)))
    return max(0, min(height - 1, scaled))


@dataclass
class TrackState:
    track_id: int
    first_frame: int
    last_frame: int
    min_conf: float
    max_conf: float
    sum_conf: float
    hits: int = 1
    min_line_dist: float = 1e9
    ever_near_line: bool = False
    event_type: Optional[str] = None
    event_frame: Optional[int] = None
    event_time_sec: Optional[float] = None
    event_conf: float = 0.0
    event_inferred: bool = False
    best_face_score: float = -1.0
    best_face_crop: Optional[np.ndarray] = None
    best_face_frame: Optional[np.ndarray] = None
    best_face_bbox: Optional[Tuple[int, int, int, int]] = None
    best_face_meta: Dict[str, float] = field(default_factory=dict)
    best_body_score: float = -1.0
    best_body_crop: Optional[np.ndarray] = None
    # 无人脸时的监控画面：优先最清晰体态所在整帧，其次人最靠近画面中央的整帧
    best_body_frame: Optional[np.ndarray] = None
    best_body_box: Optional[Tuple[int, int, int, int]] = None
    best_center_score: float = -1.0
    best_center_frame: Optional[np.ndarray] = None
    best_center_box: Optional[Tuple[int, int, int, int]] = None
    best_face_person_box: Optional[Tuple[int, int, int, int]] = None
    last_box: Optional[tuple] = None


def center_person_score(box, width: int, height: int, conf: float, body_crop=None) -> float:
    """人框越靠画面中央、越大、越清晰、置信度越高 → 分数越高。"""
    bx1, by1, bx2, by2 = box
    cx = (float(bx1) + float(bx2)) * 0.5
    cy = (float(by1) + float(by2)) * 0.5
    nx = abs(cx - width * 0.5) / max(1.0, width * 0.5)
    ny = abs(cy - height * 0.5) / max(1.0, height * 0.5)
    center_penalty = min(1.0, (nx * nx + ny * ny) ** 0.5)
    area_ratio = max(0.0, (float(bx2) - float(bx1)) * (float(by2) - float(by1))) / max(1.0, float(width * height))
    size_term = 0.35 + min(0.65, area_ratio * 8.0)
    sharp_term = 1.0
    if body_crop is not None and getattr(body_crop, "size", 0) > 0:
        # Laplacian 越高越清晰；压到约 0.55~1.2，避免完全压过几何项
        sh = sharpness(body_crop)
        sharp_term = 0.55 + min(0.65, sh / 400.0)
    return float(conf) * size_term * (1.0 - center_penalty) * sharp_term


def save_event_assets(
    storage_root: str,
    task_id: str,
    track_key: str,
    event_type: str,
    face_img: Optional[np.ndarray],
    body_img: Optional[np.ndarray],
    frame_img: Optional[np.ndarray],
) -> Tuple[str, str, str]:
    now = time.localtime()
    yyyy = time.strftime("%Y", now)
    mm = time.strftime("%m", now)
    dd = time.strftime("%d", now)
    face_dir = os.path.join(storage_root, "log_library", "face", yyyy, mm, dd)
    body_dir = os.path.join(storage_root, "log_library", "body", yyyy, mm, dd)
    snap_dir = os.path.join(storage_root, "snapshot_library", yyyy, mm, dd)
    os.makedirs(face_dir, exist_ok=True)
    os.makedirs(body_dir, exist_ok=True)
    os.makedirs(snap_dir, exist_ok=True)
    date_url = f"{yyyy}/{mm}/{dd}"
    ts = int(time.time() * 1000)
    safe_key = track_key.replace("/", "_")
    face_url = body_url = snapshot_url = ""

    if face_img is not None and getattr(face_img, "size", 0) > 0:
        face_name = f"analyze_{task_id}_{safe_key}_{event_type}_{ts}_face.jpg"
        if write_jpeg(os.path.join(face_dir, face_name), face_img, 95):
            face_url = f"/dashboard/storage/file/log/face/{date_url}/{face_name}"

    if body_img is not None and getattr(body_img, "size", 0) > 0:
        body_name = f"analyze_{task_id}_{safe_key}_{event_type}_{ts}_body.jpg"
        if write_jpeg(os.path.join(body_dir, body_name), body_img, 92):
            body_url = f"/dashboard/storage/file/log/body/{date_url}/{body_name}"

    # 监控画面：优先最佳脸所在整帧
    if frame_img is not None and getattr(frame_img, "size", 0) > 0:
        snap_name = f"analyze_{task_id}_{safe_key}_{event_type}_{ts}_snap.jpg"
        if write_jpeg(os.path.join(snap_dir, snap_name), frame_img, 92):
            snapshot_url = f"/dashboard/storage/file/snapshot/{date_url}/{snap_name}"

    return face_url, body_url, snapshot_url


def quality_flag_for(face_img, body_img) -> str:
    if face_img is not None and getattr(face_img, "size", 0) > 0:
        return "normal"
    if body_img is not None and getattr(body_img, "size", 0) > 0:
        return "low"
    return "missing"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--uploaded-file-name",
        default="",
        help="uploadPath 下相对路径；与 --video-path 二选一",
    )
    parser.add_argument(
        "--video-path",
        default="",
        help="本地视频绝对/相对路径（场景片等不在 uploadPath 时使用）",
    )
    parser.add_argument("--profile-root", default="E:/nwueyes/uploadPath")
    parser.add_argument("--line-y", type=int, default=520)
    parser.add_argument("--roi", default="", help="已弃用：离线分析不再使用门框 ROI")
    parser.add_argument("--ref-width", type=int, default=REF_WIDTH)
    parser.add_argument("--ref-height", type=int, default=REF_HEIGHT)
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
    parser.add_argument("--min-face-det-score", type=float, default=0.55)
    parser.add_argument("--track-prefix", default="yolo")
    parser.add_argument("--face-sample-fps", type=float, default=15.0, help="全画面检脸采样帧率")
    parser.add_argument(
        "--pass-near-band-px",
        type=int,
        default=0,
        help="仅用于「门内截断→补 exit」推断的近门线带宽；路过(pass)不要求贴近门线。0=按分辨率自动",
    )
    args = parser.parse_args()

    video_path = resolve_video_path(args.video_path, args.uploaded_file_name, args.profile_root)
    if not os.path.exists(video_path):
        raise FileNotFoundError(f"视频不存在: {video_path}")

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开视频: {video_path}")
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 25.0)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)) or 1920
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)) or 1080
    total_hint = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    cap.release()

    ref_w = max(1, int(args.ref_width))
    ref_h = max(1, int(args.ref_height))
    line_y = scale_line_y(args.line_y, height, ref_h)
    tight_margin = tight_infer_margin(height, ref_h)
    exit_margin = exit_hysteresis_margin(height, ref_h)
    min_track_hits = min_track_hits_for_event(height, ref_h)
    door_gate = PerTrackDoorGate(line_y, tight_margin, exit_margin)
    near_band = (
        max(40, int(args.pass_near_band_px))
        if args.pass_near_band_px and args.pass_near_band_px > 0
        else max(60, int(round(height * 0.08)))
    )

    sample_fps = max(0.5, float(args.face_sample_fps))
    sample_every = max(1, int(round(fps / sample_fps)))
    frame_area = width * height
    capture_enabled = bool(args.task_id and args.storage_root)

    print(
        f"[line] ref={ref_w}x{ref_h} video={width}x{height} "
        f"lineYBase={args.line_y} -> lineY={line_y} nearBand={near_band}px "
        f"tightInferPx={tight_margin} exitMarginPx={exit_margin} minTrackHits={min_track_hits} "
        f"faceSampleEvery={sample_every} (~{sample_fps}fps) roi=disabled",
        flush=True,
    )
    if capture_enabled:
        print(f"[capture] storage={args.storage_root} task={args.task_id}", flush=True)

    os.makedirs(os.path.dirname(os.path.abspath(args.debug_out)), exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.result_json)), exist_ok=True)

    model = YOLO(args.model)
    prev_cy: Dict[int, float] = {}
    seen_tracks: set[int] = set()
    last_event_frame: Dict[str, int] = {}
    cross_pending: Dict[int, tuple[str, int]] = {}
    track_hits: Dict[int, int] = {}
    tracks: Dict[int, TrackState] = {}
    frame_idx = 0
    face_sample_count = 0
    faces_seen = 0

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

    for result in results_iter:
        frame_idx += 1
        video_sec = time_sec(frame_idx, fps)
        raw = result.orig_img
        frame = raw.copy()
        # 只画门线（全宽），不再画门框 ROI
        cv2.line(frame, (0, line_y), (width - 1, line_y), (0, 0, 255), 2)
        cv2.putText(
            frame,
            "down=enter up=exit | no-cross=pass",
            (12, line_y - 12),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (0, 255, 255),
            2,
            cv2.LINE_AA,
        )

        person_boxes: Dict[int, tuple] = {}
        boxes = result.boxes
        if boxes is not None and boxes.id is not None:
            xyxy = boxes.xyxy.cpu().numpy()
            ids = boxes.id.int().cpu().numpy()
            confs = boxes.conf.cpu().numpy() if boxes.conf is not None else None
            for idx, tid in enumerate(ids):
                bx1, by1, bx2, by2 = xyxy[idx]
                cx = (bx1 + bx2) / 2.0
                cy = float(by2)
                conf = float(confs[idx]) if confs is not None else 0.0
                tid_i = int(tid)
                person_boxes[tid_i] = (bx1, by1, bx2, by2)
                track_hits[tid_i] = track_hits.get(tid_i, 0) + 1

                is_new = tid_i not in seen_tracks
                if is_new:
                    seen_tracks.add(tid_i)
                    prev_cy[tid_i] = cy
                    door_gate.on_new_track(tid_i, cy)
                    tracks[tid_i] = TrackState(
                        track_id=tid_i,
                        first_frame=frame_idx,
                        last_frame=frame_idx,
                        min_conf=conf,
                        max_conf=conf,
                        sum_conf=conf,
                    )
                else:
                    old = prev_cy[tid_i]
                    prev_cy[tid_i] = cy
                    st = tracks[tid_i]
                    st.last_frame = frame_idx
                    st.min_conf = min(st.min_conf, conf)
                    st.max_conf = max(st.max_conf, conf)
                    st.sum_conf += conf
                    st.hits += 1

                st = tracks[tid_i]
                st.last_box = (bx1, by1, bx2, by2)
                dist = abs(cy - line_y)
                st.min_line_dist = min(st.min_line_dist, dist)
                if dist <= near_band:
                    st.ever_near_line = True

                # 体态备选（用人框，不用于裁脸）
                body = raw[int(by1) : int(by2), int(bx1) : int(bx2)].copy()
                if body.size > 0:
                    shape = body_shape_score((bx1, by1, bx2, by2), width, height)
                    bs = score_image(body, conf, frame_area) * shape
                    if bs > st.best_body_score:
                        st.best_body_score = bs
                        st.best_body_crop = body
                        st.best_body_frame = raw.copy()
                        st.best_body_box = (int(bx1), int(by1), int(bx2), int(by2))

                # 出门/路过/进门无人脸时的监控画面：保留人最居中且更清晰的整帧
                cs = center_person_score((bx1, by1, bx2, by2), width, height, conf, body if body.size > 0 else None)
                if cs > st.best_center_score:
                    st.best_center_score = cs
                    st.best_center_frame = raw.copy()
                    st.best_center_box = (int(bx1), int(by1), int(bx2), int(by2))

                cooldown_key = f"t{tid_i}"
                if st.event_type is None and frame_idx - last_event_frame.get(cooldown_key, -999999) >= args.event_cooldown_frames:
                    event_type = door_gate.try_cross(
                        cross_pending,
                        tid_i,
                        old if not is_new else cy,
                        cy,
                        1,
                    )
                    inferred = False
                    if event_type is None:
                        infer_ev = door_gate.try_infer_enter(tid_i, cy, is_new)
                        if infer_ev:
                            event_type = infer_ev
                            inferred = True
                    if event_type and track_hits.get(tid_i, 0) < min_track_hits:
                        event_type = None
                    if event_type:
                        door_gate.commit(tid_i, event_type)
                        st.event_type = event_type
                        st.event_frame = frame_idx
                        st.event_time_sec = video_sec
                        st.event_conf = conf
                        st.event_inferred = inferred
                        last_event_frame[cooldown_key] = frame_idx
                        color = (0, 0, 255) if event_type == "enter" else (255, 128, 0)
                        cv2.putText(
                            frame,
                            f"{event_type.upper()}{'*' if inferred else ''}",
                            (int(bx1), max(20, int(by1) - 22)),
                            cv2.FONT_HERSHEY_SIMPLEX,
                            0.7,
                            color,
                            2,
                            cv2.LINE_AA,
                        )

                cv2.rectangle(frame, (int(bx1), int(by1)), (int(bx2), int(by2)), (0, 255, 0), 2)
                cv2.circle(frame, (int(cx), int(cy)), 5, (0, 255, 0), -1)
                cv2.putText(
                    frame,
                    f"ID:{tid_i} {conf:.2f}",
                    (int(bx1), max(0, int(by1) - 8)),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.55,
                    (0, 255, 0),
                    2,
                    cv2.LINE_AA,
                )

        # 全画面密采样检脸；一帧内脸↔人一对一互斥，再按人做全局选优
        if person_boxes and frame_idx % sample_every == 0:
            face_sample_count += 1
            scored_faces = detect_scored_faces(
                raw,
                min_det_score=args.min_face_det_score,
            )
            faces_seen += len(scored_faces)
            face_to_tid = match_faces_to_person_boxes(
                [face.bbox for face in scored_faces],
                person_boxes,
                max_center_dist=max(180.0, width * 0.08),
            )
            for fi, face in enumerate(scored_faces):
                tid = face_to_tid.get(fi)
                if tid is None or tid not in tracks:
                    fx1, fy1, fx2, fy2 = face.bbox
                    cv2.rectangle(frame, (fx1, fy1), (fx2, fy2), (120, 120, 120), 1)
                    continue
                st = tracks[tid]
                best_pose = float(st.best_face_meta.get("poseScore", 0.0)) if st.best_face_meta else 0.0
                best_sharp = float(st.best_face_meta.get("sharpness", 0.0)) if st.best_face_meta else 0.0
                if should_replace_best_face(st.best_face_score, best_pose, face, best_sharpness=best_sharp):
                    st.best_face_score = face.score
                    st.best_face_crop = face.crop.copy()
                    st.best_face_frame = raw.copy()
                    st.best_face_bbox = face.bbox
                    st.best_face_person_box = st.last_box
                    st.best_face_meta = {
                        "sharpness": round(face.sharpness, 2),
                        "detScore": round(face.detection_score, 4),
                        "sizeScore": round(face.size_score, 4),
                        "exposureScore": round(face.exposure_score, 4),
                        "poseScore": round(face.pose_score, 4),
                        "qualityScore": round(face.score, 4),
                        "frame": frame_idx,
                        "timeSec": video_sec,
                    }
                fx1, fy1, fx2, fy2 = face.bbox
                cv2.rectangle(frame, (fx1, fy1), (fx2, fy2), (255, 180, 0), 2)
                cv2.putText(
                    frame,
                    f"face{tid} {face.score:.2f}",
                    (fx1, max(16, fy1 - 6)),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.5,
                    (255, 180, 0),
                    1,
                    cv2.LINE_AA,
                )

        active = len(person_boxes)
        cv2.putText(
            frame,
            f"line-only f={frame_idx}/{total_hint or '?'} persons={active} facesSampled={face_sample_count}",
            (14, 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (60, 255, 60),
            2,
            cv2.LINE_AA,
        )
        writer.write(frame)
        if frame_idx % 50 == 0:
            print(
                f"[progress] frame={frame_idx} tracks={len(tracks)} "
                f"faceSamples={face_sample_count} facesSeen={faces_seen}",
                flush=True,
            )

    writer.release()

    # 收尾：门内开拍且曾贴近门线但未完成穿线 → 补 exit；
    # 其余未过门的有效轨迹一律记 pass（不要求贴近门线，远距离路过也直接出事件）
    events: List[dict] = []
    enter_count = exit_count = pass_count = 0
    min_pass_hits = max(3, min_track_hits // 2)
    for tid, st in sorted(tracks.items(), key=lambda x: x[0]):
        if st.event_type is None:
            if st.hits < min_pass_hits:
                continue
            # 场景片常从「人已在门内往外走」开拍，片尾截断时脚点未完整穿出
            # 门内先出现且曾贴近门线 → 记出门；否则一律路过（与距门线无关）
            if door_gate.side(tid) == SIDE_INSIDE and st.ever_near_line:
                st.event_type = "exit"
                st.event_inferred = True
                door_gate.commit(tid, "exit")
                print(
                    f"[gate] finalize-exit track={tid} nearLine=1 minDist={st.min_line_dist:.1f} "
                    f"(clip truncated / incomplete cross)",
                    flush=True,
                )
            else:
                st.event_type = "pass"
            st.event_frame = st.last_frame
            st.event_time_sec = time_sec(st.last_frame, fps)
            st.event_conf = st.max_conf

        track_prefix = (args.task_id or args.track_prefix or "yolo").strip() or "yolo"
        track_key = f"{track_prefix}_t{tid}"
        face_url = body_url = snapshot_url = ""
        quality = quality_flag_for(st.best_face_crop, st.best_body_crop)
        snap_frame = None
        snap_bbox = None
        if capture_enabled:
            # 监控画面：有脸用最佳脸帧；无人脸（含进门）用最清晰体态帧，再退居中清晰帧
            snap_frame = st.best_face_frame
            if snap_frame is None:
                snap_frame = st.best_body_frame if st.best_body_frame is not None else st.best_center_frame
            face_url, body_url, snapshot_url = save_event_assets(
                args.storage_root,
                args.task_id,
                track_key,
                st.event_type,
                st.best_face_crop,
                st.best_body_crop,
                snap_frame,
            )
            quality = quality_flag_for(st.best_face_crop, st.best_body_crop)

        if snap_frame is not None and getattr(snap_frame, "size", 0) > 0:
            fh, fw = snap_frame.shape[:2]
            box = None
            source = "body"
            if snap_frame is st.best_face_frame and st.best_face_person_box is not None:
                box = st.best_face_person_box
                source = "face_frame"
            elif snap_frame is st.best_body_frame and st.best_body_box is not None:
                box = st.best_body_box
                source = "body_frame"
            elif snap_frame is st.best_center_frame and st.best_center_box is not None:
                box = st.best_center_box
                source = "center_frame"
            elif st.last_box is not None:
                box = st.last_box
            snap_bbox = normalize_bbox(box, fw, fh, source)

        ev = {
            "frame": st.event_frame,
            "timeSec": st.event_time_sec,
            "trackId": tid,
            "trackKey": track_key,
            "eventType": st.event_type,
            "confidence": round(float(st.event_conf), 3),
            "inferred": bool(st.event_inferred),
            "faceImageUrl": face_url,
            "bodyImageUrl": body_url,
            "snapshotUrl": snapshot_url,
            "qualityFlag": quality,
            "bestFaceScore": round(st.best_face_score, 4) if st.best_face_score >= 0 else None,
            "bestFaceMeta": st.best_face_meta or None,
            "minLineDistPx": None if st.min_line_dist > 1e8 else round(st.min_line_dist, 1),
            "everNearLine": bool(st.ever_near_line),
        }
        if snap_bbox:
            ev["snapshotBbox"] = snap_bbox
        events.append(ev)
        if st.event_type == "enter":
            enter_count += 1
        elif st.event_type == "exit":
            exit_count += 1
        else:
            pass_count += 1
        print(
            f"[event] {st.event_type} track={track_key} t={st.event_time_sec}s "
            f"face={'yes' if face_url else 'no'} snap={'yes' if snapshot_url else 'no'} "
            f"qFace={st.best_face_score:.3f} nearLine={st.ever_near_line}",
            flush=True,
        )

    track_rows = []
    for tid, st in sorted(tracks.items(), key=lambda x: x[0]):
        hits = st.hits or 1
        track_rows.append(
            {
                "trackId": tid,
                "firstFrame": st.first_frame,
                "lastFrame": st.last_frame,
                "firstTimeSec": time_sec(st.first_frame, fps),
                "lastTimeSec": time_sec(st.last_frame, fps),
                "avgConfidence": round(st.sum_conf / hits, 3),
                "hitFrames": hits,
                "eventType": st.event_type,
                "bestFaceScore": round(st.best_face_score, 4) if st.best_face_score >= 0 else None,
                "everNearLine": st.ever_near_line,
                "faceImageUrl": next(
                    (e.get("faceImageUrl") for e in events if e["trackId"] == tid),
                    "",
                ),
                "bodyImageUrl": next(
                    (e.get("bodyImageUrl") for e in events if e["trackId"] == tid),
                    "",
                ),
                "snapshotUrl": next(
                    (e.get("snapshotUrl") for e in events if e["trackId"] == tid),
                    "",
                ),
            }
        )

    payload = {
        "algorithm": "YOLOv8+ByteTrack + full-frame InsightFace quality",
        "model": args.model,
        "sourceVideo": video_path,
        "totalFrames": frame_idx,
        "fps": round(fps, 2),
        "width": width,
        "height": height,
        "refWidth": ref_w,
        "refHeight": ref_h,
        "lineY": line_y,
        "lineYBase": args.line_y,
        "roi": "",
        "roiBase": "",
        "roiDisabled": True,
        "nearBandPx": near_band,
        "faceSampleFps": sample_fps,
        "faceSampleEvery": sample_every,
        "faceSampleCount": face_sample_count,
        "facesSeen": faces_seen,
        "tightInferPx": tight_margin,
        "exitMarginPx": exit_margin,
        "minTrackHits": min_track_hits,
        "uniqueTracks": len(tracks),
        "maxPersonsInRoi": 0,
        "enterCount": enter_count,
        "exitCount": exit_count,
        "passCount": pass_count,
        "events": events,
        "tracks": track_rows,
        "captureMode": "best-face-frame",
        "captureEventCount": sum(1 for e in events if e.get("snapshotUrl") or e.get("faceImageUrl")),
        "debugVideoPath": args.debug_out.replace("\\", "/"),
        "debugVideoUrl": args.debug_video_url,
    }

    with open(args.result_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(
        f"[done] frames={frame_idx} tracks={len(tracks)} "
        f"enter={enter_count} exit={exit_count} pass={pass_count} "
        f"faceSamples={face_sample_count}",
        flush=True,
    )
    print(f"[result] {args.result_json}")
    print(f"[debug] {args.debug_out}")


if __name__ == "__main__":
    main()
