#!/usr/bin/env python3
"""全画面人脸检测 + 多维质量评分与扩边裁剪。"""

from __future__ import annotations

import math
import threading
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np

_ANALYZER = None
_ANALYZER_LOCK = threading.Lock()

# 可用脸检测分门槛：低于此不参与选优
MIN_USABLE_DET = 0.55


@dataclass
class ScoredFace:
    score: float
    sharpness: float
    detection_score: float
    size_score: float
    exposure_score: float
    pose_score: float
    bbox: Tuple[int, int, int, int]  # x1,y1,x2,y2 原框
    crop: np.ndarray  # 扩边后的人脸
    landmarks_plausible: bool = True
    embedding: Optional[np.ndarray] = None
    usable: bool = True


def _get_analyzer(model_name: str = "buffalo_l", det_size: Tuple[int, int] = (640, 640)):
    global _ANALYZER
    if _ANALYZER is not None:
        return _ANALYZER
    with _ANALYZER_LOCK:
        if _ANALYZER is not None:
            return _ANALYZER
        from insightface.app import FaceAnalysis

        app = FaceAnalysis(
            name=model_name,
            allowed_modules=["detection", "recognition"],
            providers=["CPUExecutionProvider"],
        )
        # 与可用脸检测分门槛保持一致，减少弱误检
        app.prepare(ctx_id=-1, det_size=det_size, det_thresh=0.55)
        _ANALYZER = app
        return app


def landmarks_plausible(keypoints, bbox: Tuple[float, float, float, float]) -> bool:
    """
    五官几何是否合理，用作质量诊断：
    - 双眼间距相对脸宽过小
    - 眼/鼻/嘴垂直顺序混乱
    - 关键点挤成一团
    """
    if keypoints is None or len(keypoints) < 5:
        return False
    points = np.asarray(keypoints, dtype=np.float32)
    left_eye, right_eye, nose, left_mouth, right_mouth = points[:5]
    x1, y1, x2, y2 = bbox
    face_w = max(1.0, x2 - x1)
    face_h = max(1.0, y2 - y1)

    eye_dist = float(np.linalg.norm(right_eye - left_eye))
    mouth_dist = float(np.linalg.norm(right_mouth - left_mouth))
    if eye_dist < face_w * 0.18:
        return False
    if eye_dist < 12.0:
        return False
    # 关键点整体跨度应覆盖脸宽相当比例
    xs = points[:5, 0]
    ys = points[:5, 1]
    if float(xs.max() - xs.min()) < face_w * 0.28:
        return False
    if float(ys.max() - ys.min()) < face_h * 0.22:
        return False

    eye_y = (float(left_eye[1]) + float(right_eye[1])) / 2.0
    mouth_y = (float(left_mouth[1]) + float(right_mouth[1])) / 2.0
    nose_y = float(nose[1])
    # 眼在上、嘴在下；鼻大致在中间带
    if not (eye_y + face_h * 0.04 < nose_y < mouth_y - face_h * 0.02):
        return False
    if mouth_y <= eye_y:
        return False
    # 嘴宽不应远大于眼距（乱关键点常见）
    if mouth_dist > eye_dist * 2.2:
        return False
    # 双眼高度差过大通常不是可用脸
    if abs(float(left_eye[1] - right_eye[1])) > eye_dist * 0.55:
        return False
    return True


def frontal_score(keypoints) -> float:
    if keypoints is None or len(keypoints) < 5:
        return 0.0
    points = np.asarray(keypoints, dtype=np.float32)
    left_eye, right_eye, nose, left_mouth, right_mouth = points[:5]
    eye_distance = float(np.linalg.norm(right_eye - left_eye))
    if eye_distance < 1:
        return 0.0
    eye_mid = (left_eye + right_eye) / 2
    mouth_mid = (left_mouth + right_mouth) / 2
    # 鼻尖相对中线偏移越大，姿态评分越低
    nose_eye_offset = abs(float(nose[0] - eye_mid[0])) / eye_distance
    nose_mouth_offset = abs(float(nose[0] - mouth_mid[0])) / eye_distance
    eye_tilt = abs(float(left_eye[1] - right_eye[1])) / eye_distance
    # 鼻应大致落在双眼水平中段下方；偏移过大再罚
    nose_below = float(nose[1] - eye_mid[1]) / eye_distance
    vertical_penalty = 0.0
    if nose_below < 0.15:
        vertical_penalty += 0.45
    elif nose_below > 1.8:
        vertical_penalty += 0.35
    penalty = min(
        1.0,
        nose_eye_offset * 2.4 + nose_mouth_offset * 1.5 + eye_tilt * 0.9 + vertical_penalty,
    )
    return max(0.0, 1.0 - penalty)


def evaluate_face(frame: np.ndarray, face, min_size: int = 40) -> Optional[ScoredFace]:
    frame_h, frame_w = frame.shape[:2]
    x1, y1, x2, y2 = [float(v) for v in face.bbox]
    x1 = max(0.0, min(x1, frame_w - 1.0))
    y1 = max(0.0, min(y1, frame_h - 1.0))
    x2 = max(0.0, min(x2, float(frame_w)))
    y2 = max(0.0, min(y2, float(frame_h)))
    width, height = x2 - x1, y2 - y1
    if width < min_size or height < min_size:
        return None

    kps = getattr(face, "kps", None)
    bbox = (x1, y1, x2, y2)

    # 五点缺失时无法可靠计算姿态，仍然不参与评分；
    # 严格五官几何关系只作为诊断，不再硬过滤。
    if kps is None or len(kps) < 5:
        return None
    landmark_geometry_ok = landmarks_plausible(kps, bbox)

    detection_score = float(getattr(face, "det_score", 0.5))
    pose = frontal_score(kps)
    if detection_score < MIN_USABLE_DET:
        return None

    pad_x, pad_y = width * 0.28, height * 0.32
    crop_x1 = max(0, int(x1 - pad_x))
    crop_y1 = max(0, int(y1 - pad_y))
    crop_x2 = min(frame_w, int(x2 + pad_x))
    crop_y2 = min(frame_h, int(y2 + pad_y))
    crop = frame[crop_y1:crop_y2, crop_x1:crop_x2].copy()
    face_region = frame[int(y1) : int(y2), int(x1) : int(x2)]
    if crop.size == 0 or face_region.size == 0:
        return None

    gray = cv2.cvtColor(face_region, cv2.COLOR_BGR2GRAY)
    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    sharpness_score = 1.0 - math.exp(-sharpness / 240.0)

    brightness = float(np.mean(gray))
    brightness_score = max(0.0, 1.0 - abs(brightness - 135.0) / 135.0)
    clipped_ratio = float(np.mean((gray < 12) | (gray > 245)))
    exposure_score = brightness_score * max(0.0, 1.0 - clipped_ratio * 2.5)

    relative_face = math.sqrt((width * height) / max(1.0, frame_w * frame_h))
    size_score = min(1.0, relative_face / 0.32)

    # 使用归一化后的清晰度分，与尺寸、检测分、曝光和姿态综合评分。
    score = (
        pose * 0.003
        + sharpness_score * 0.115
        + size_score * 0.472
        + detection_score * 0.315
        + exposure_score * 0.095
    )

    embedding = None
    emb = getattr(face, "embedding", None)
    if emb is not None:
        embedding = np.asarray(emb, dtype=np.float32).reshape(-1)

    return ScoredFace(
        score=float(score),
        sharpness=sharpness,
        detection_score=detection_score,
        size_score=float(size_score),
        exposure_score=float(exposure_score),
        pose_score=float(pose),
        bbox=(int(x1), int(y1), int(x2), int(y2)),
        crop=crop,
        landmarks_plausible=landmark_geometry_ok,
        embedding=embedding,
        usable=True,
    )


def should_replace_best_face(
    best_score: float,
    best_pose: float,
    candidate: ScoredFace,
    best_sharpness: Optional[float] = None,
) -> bool:
    """直接按照新综合质量分选择最佳脸。

    best_pose 和 best_sharpness 仅为兼容现有调用方保留，
    不再参与最佳脸决策。
    """
    if candidate is None or not candidate.usable:
        return False
    if best_score < 0:
        return True
    return candidate.score > best_score


def detect_scored_faces(
    frame: np.ndarray,
    min_det_score: float = MIN_USABLE_DET,
    min_size: int = 40,
    model_name: str = "buffalo_l",
) -> List[ScoredFace]:
    """全画面检脸并打分；达到检测门槛的脸按 score 降序。"""
    if frame is None or frame.size == 0:
        return []
    try:
        analyzer = _get_analyzer(model_name=model_name)
        faces = analyzer.get(frame)
    except Exception:
        return []

    scored: List[ScoredFace] = []
    for face in faces or []:
        det = float(getattr(face, "det_score", 0.0))
        if det < min_det_score:
            continue
        item = evaluate_face(frame, face, min_size=min_size)
        if item is not None:
            scored.append(item)
    scored.sort(key=lambda f: f.score, reverse=True)
    return scored


def _face_person_affinity(
    face_bbox: Tuple[int, int, int, int],
    person_box: tuple,
    max_center_dist: float,
) -> Optional[float]:
    """
    脸与人体框亲和度（越大越好）；不合格返回 None。
    硬约束：脸中心必须落在人体框内，且不能落到躯干下半（防跨人借脸）。
    """
    fx1, fy1, fx2, fy2 = [float(v) for v in face_bbox]
    bx1, by1, bx2, by2 = [float(v) for v in person_box]
    fcx = (fx1 + fx2) * 0.5
    fcy = (fy1 + fy2) * 0.5
    if not (bx1 <= fcx <= bx2 and by1 <= fcy <= by2):
        return None

    person_h = max(1.0, by2 - by1)
    person_w = max(1.0, bx2 - bx1)
    upper_y2 = by1 + person_h * 0.55
    mid_y = by1 + person_h * 0.65
    # 脸中心落到人框下半 → 几乎不可能是本人人脸
    if fcy > mid_y:
        return None
    # 脸中心贴在人体框水平边缘 → 多为两人重叠时借到隔壁的脸，拒绝
    rel_x = (fcx - bx1) / person_w
    if rel_x < 0.12 or rel_x > 0.88:
        return None

    in_upper = fcy <= upper_y2
    pcx = (bx1 + bx2) * 0.5
    pcy = by1 + person_h * 0.18
    dist = math.hypot(fcx - pcx, fcy - pcy)
    if dist > max_center_dist and not in_upper:
        return None

    # 脸框与头肩区 IoU，重叠越大越像「这人的脸」
    ux1, uy1, ux2, uy2 = bx1, by1, bx2, upper_y2
    ix1 = max(fx1, ux1)
    iy1 = max(fy1, uy1)
    ix2 = min(fx2, ux2)
    iy2 = min(fy2, uy2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    face_area = max(1.0, (fx2 - fx1) * (fy2 - fy1))
    upper_area = max(1.0, person_w * (upper_y2 - by1))
    union = face_area + upper_area - inter
    iou = inter / union if union > 1e-6 else 0.0

    # 人脸宽度相对人框宽度：过窄/过宽略罚，避免远处别人的小脸误挂
    face_w = max(1.0, fx2 - fx1)
    width_ratio = face_w / person_w
    width_term = 1.0 - min(1.0, abs(width_ratio - 0.45) / 0.55)

    affinity = (1.0 / (1.0 + dist / max(40.0, person_h * 0.25))) + iou * 2.2 + width_term * 0.35
    if in_upper:
        affinity += 1.25
    return float(affinity)


def match_faces_to_person_boxes(
    face_bboxes: List[Tuple[int, int, int, int]],
    person_boxes: dict[int, tuple],
    max_center_dist: float = 220.0,
) -> Dict[int, int]:
    """
    一帧内脸↔人一对一互斥匹配。
    返回 face_index -> track_id；同一张脸不会分给多人，同一人一帧最多一张脸。
    """
    if not face_bboxes or not person_boxes:
        return {}
    pairs: List[Tuple[float, int, int]] = []
    for fi, fb in enumerate(face_bboxes):
        for tid, pb in person_boxes.items():
            aff = _face_person_affinity(fb, pb, max_center_dist)
            if aff is not None:
                pairs.append((aff, fi, int(tid)))
    # 亲和度高优先；同分时保持稳定顺序
    pairs.sort(key=lambda x: (-x[0], x[1], x[2]))
    used_faces: set[int] = set()
    used_tids: set[int] = set()
    assigned: Dict[int, int] = {}
    for _aff, fi, tid in pairs:
        if fi in used_faces or tid in used_tids:
            continue
        used_faces.add(fi)
        used_tids.add(tid)
        assigned[fi] = tid
    return assigned


def match_face_to_person_box(
    face_bbox: Tuple[int, int, int, int],
    person_boxes: dict[int, tuple],
    max_center_dist: float = 220.0,
) -> Optional[int]:
    """
    单张脸关联（兼容旧调用）。多人场景请用 match_faces_to_person_boxes 做互斥匹配。
    """
    mapping = match_faces_to_person_boxes([face_bbox], person_boxes, max_center_dist)
    return mapping.get(0)


def write_jpeg(path: str, image: np.ndarray, quality: int = 95) -> bool:
    if image is None or image.size == 0:
        return False
    ok, encoded = cv2.imencode(".jpg", image, [int(cv2.IMWRITE_JPEG_QUALITY), quality])
    if not ok:
        return False
    with open(path, "wb") as fp:
        fp.write(encoded.tobytes())
    return True
