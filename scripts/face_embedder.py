#!/usr/bin/env python3
"""InsightFace ArcFace 512 维向量（默认识别-only，适配 YOLO 人脸 crop）。"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

import cv2
import numpy as np

_REC_MODEL = None
_REC_CFG: Dict[str, Any] = {}
_FACE_APP = None


def _default_model_candidates() -> list[Path]:
    home = Path.home()
    storage_root = os.environ.get("PRESENCE_STORAGE_ROOT", "E:/nwueyes")
    return [
        Path(storage_root) / "models" / "w600k_r50.onnx",
        home / ".insightface" / "models" / "buffalo_l" / "w600k_r50.onnx",
        home / ".insightface" / "models" / "buffalo_sc" / "w600k_r50.onnx",
    ]


def _resolve_model_path(model_path: str = "") -> Path:
    if model_path:
        path = Path(model_path)
        if path.exists():
            return path
        raise FileNotFoundError(f"人脸模型不存在: {model_path}")
    for candidate in _default_model_candidates():
        if candidate.exists():
            return candidate
    raise FileNotFoundError(
        "未找到 w600k_r50.onnx。请将模型放到 {storageRoot}/models/w600k_r50.onnx "
        "或安装 insightface 后运行一次 FaceAnalysis(buffalo_l) 自动下载。"
    )


def _get_rec_model(model_path: str = ""):
    global _REC_MODEL, _REC_CFG
    resolved = str(_resolve_model_path(model_path))
    if _REC_MODEL is not None and _REC_CFG.get("model_path") == resolved:
        return _REC_MODEL
    try:
        from insightface.model_zoo.arcface_onnx import ArcFaceONNX
    except Exception as ex:
        raise RuntimeError(f"缺少 insightface/onnxruntime: {ex}") from ex
    model = ArcFaceONNX(resolved)
    model.prepare(ctx_id=-1)
    _REC_MODEL = model
    _REC_CFG = {"model_path": resolved}
    return model


def _get_detect_app(model_name: str, det_size: Tuple[int, int]):
    global _FACE_APP
    if _FACE_APP is not None:
        return _FACE_APP
    from insightface.app import FaceAnalysis

    app = FaceAnalysis(
        name=model_name,
        providers=["CPUExecutionProvider"],
        allowed_modules=["detection", "recognition"],
    )
    app.prepare(ctx_id=-1, det_size=det_size)
    _FACE_APP = app
    return app


def _embed_crop(img_bgr: np.ndarray, model_path: str = "") -> Tuple[np.ndarray, Dict[str, Any]]:
    rec = _get_rec_model(model_path)
    resized = cv2.resize(img_bgr, (112, 112))
    feat = rec.get_feat(resized)
    if isinstance(feat, list):
        feat = feat[0]
    vec = np.asarray(feat, dtype=np.float32).reshape(-1)
    quality = {
        "detected": False,
        "detScore": 0.0,
        "mode": "arcface_crop",
        "modelPath": _REC_CFG.get("model_path", ""),
    }
    return vec, quality


def detect_best_face_crop(
    img_bgr: np.ndarray,
    model_name: str = "buffalo_l",
    det_size: Tuple[int, int] = (640, 640),
    min_det_score: float = 0.45,
) -> Tuple[Optional[np.ndarray], float]:
    """在图像中检测人脸并返回最佳 crop；检不到则 (None, 0)。"""
    if img_bgr is None or img_bgr.size == 0:
        return None, 0.0
    h, w = img_bgr.shape[:2]
    if min(h, w) < 48:
        return None, 0.0

    app = _get_detect_app(model_name, det_size)
    faces = app.get(img_bgr)
    if not faces:
        return None, 0.0

    best = max(faces, key=lambda f: float(getattr(f, "det_score", 0.0)))
    det_score = float(getattr(best, "det_score", 0.0))
    if det_score < min_det_score:
        return None, det_score

    bbox = np.asarray(best.bbox, dtype=np.float32).reshape(-1)
    x1, y1, x2, y2 = [int(v) for v in bbox[:4]]
    x1 = max(0, min(w - 1, x1))
    y1 = max(0, min(h - 1, y1))
    x2 = max(x1 + 1, min(w, x2))
    y2 = max(y1 + 1, min(h, y2))
    crop = img_bgr[y1:y2, x1:x2].copy()
    if crop.size == 0:
        return None, det_score
    return crop, det_score


def embed_face_image(
    img_bgr: np.ndarray,
    model_name: str = "buffalo_l",
    det_size: Tuple[int, int] = (640, 640),
    min_det_score: float = 0.45,
    model_path: str = "",
    mode: str = "crop",
) -> Tuple[np.ndarray, Dict[str, Any]]:
    if img_bgr is None or img_bgr.size == 0:
        raise ValueError("face 图片为空")

    if mode == "crop":
        return _embed_crop(img_bgr, model_path=model_path)

    app = _get_detect_app(model_name, det_size)
    faces = app.get(img_bgr)
    if faces:
        best = max(faces, key=lambda f: float(getattr(f, "det_score", 0.0)))
        det_score = float(getattr(best, "det_score", 0.0))
        if det_score >= min_det_score:
            vec = np.asarray(best.embedding, dtype=np.float32).reshape(-1)
            quality = {
                "detected": True,
                "detScore": round(det_score, 4),
                "mode": "detection",
            }
            return vec, quality
    return _embed_crop(img_bgr, model_path=model_path)
