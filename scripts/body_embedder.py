#!/usr/bin/env python3
"""OSNet 体态 ReID 512 维向量。"""

from __future__ import annotations

from typing import Any, Dict, Tuple

import cv2
import numpy as np
import torch
from PIL import Image
from torchvision import transforms

_BODY_MODEL = None
_BODY_CFG: Dict[str, Any] = {}

_IMAGENET_MEAN = [0.485, 0.456, 0.406]
_IMAGENET_STD = [0.229, 0.224, 0.225]


def _get_body_model(model_name: str = "osnet_x0_25"):
    global _BODY_MODEL, _BODY_CFG
    if _BODY_MODEL is not None and _BODY_CFG.get("model_name") == model_name:
        return _BODY_MODEL
    try:
        from torchreid.reid.models.osnet import osnet_x0_25, osnet_x1_0
    except Exception as ex:
        raise RuntimeError(f"缺少 torchreid: {ex}") from ex

    builders = {
        "osnet_x0_25": osnet_x0_25,
        "osnet_x1_0": osnet_x1_0,
    }
    builder = builders.get(model_name)
    if builder is None:
        raise ValueError(f"不支持的 body 模型: {model_name}")
    model = builder(num_classes=1, pretrained=True)
    model.eval()
    _BODY_MODEL = model
    _BODY_CFG = {"model_name": model_name}
    return model


def _preprocess(img_bgr: np.ndarray) -> torch.Tensor:
    rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
    transform = transforms.Compose(
        [
            transforms.Resize((256, 128)),
            transforms.ToTensor(),
            transforms.Normalize(mean=_IMAGENET_MEAN, std=_IMAGENET_STD),
        ]
    )
    return transform(Image.fromarray(rgb)).unsqueeze(0)


def embed_body_image(
    img_bgr: np.ndarray,
    model_name: str = "osnet_x0_25",
) -> Tuple[np.ndarray, Dict[str, Any]]:
    if img_bgr is None or img_bgr.size == 0:
        raise ValueError("body 图片为空")
    h, w = img_bgr.shape[:2]
    model = _get_body_model(model_name)
    tensor = _preprocess(img_bgr)
    with torch.no_grad():
        feat = model(tensor).squeeze(0).cpu().numpy()
    vec = np.asarray(feat, dtype=np.float32).reshape(-1)
    quality = {
        "width": int(w),
        "height": int(h),
        "mode": "osnet_reid",
    }
    return vec, quality
