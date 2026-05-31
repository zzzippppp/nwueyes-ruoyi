#!/usr/bin/env python3
"""Embedding 脚本公共 I/O 与向量归一化。"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

import cv2
import numpy as np

EMBEDDING_DIM = 512


def read_image_bgr(image_path: str) -> np.ndarray:
    path = Path(image_path)
    if not path.exists():
        raise FileNotFoundError(f"图片不存在: {image_path}")
    img = cv2.imread(str(path))
    if img is None or img.size == 0:
        raise ValueError(f"无法读取图片: {image_path}")
    return img


def l2_normalize(vec: np.ndarray) -> np.ndarray:
    arr = np.asarray(vec, dtype=np.float32).reshape(-1)
    norm = float(np.linalg.norm(arr))
    if norm <= 1e-8:
        return arr
    return arr / norm


def validate_dim(vec: np.ndarray, dim: int = EMBEDDING_DIM) -> np.ndarray:
    arr = l2_normalize(vec)
    if arr.shape[0] != dim:
        raise ValueError(f"向量维度错误: expected={dim}, actual={arr.shape[0]}")
    return arr


def success_payload(
    kind: str,
    image_path: str,
    embedding: np.ndarray,
    model: str,
    quality: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    vec = validate_dim(embedding)
    return {
        "ok": True,
        "kind": kind,
        "imagePath": str(Path(image_path).resolve()).replace("\\", "/"),
        "dim": int(vec.shape[0]),
        "embedding": [round(float(x), 6) for x in vec.tolist()],
        "model": model,
        "quality": quality or {},
        "error": None,
    }


def error_payload(kind: str, image_path: str, message: str, model: str = "") -> Dict[str, Any]:
    return {
        "ok": False,
        "kind": kind,
        "imagePath": str(Path(image_path).resolve()).replace("\\", "/") if image_path else "",
        "dim": EMBEDDING_DIM,
        "embedding": [],
        "model": model,
        "quality": {},
        "error": message,
    }


def dump_result(payload: Dict[str, Any], output_path: Optional[str] = None) -> None:
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if output_path:
        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        Path(output_path).write_text(text, encoding="utf-8")
    else:
        print(text)


def main_fail(message: str, code: int = 1) -> None:
    print(message, file=sys.stderr)
    sys.exit(code)
