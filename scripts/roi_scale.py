#!/usr/bin/env python3
"""按参考分辨率将 ROI 与过线 Y 缩放到实际视频尺寸。

参考分辨率优先使用摄像头标定保存的 ref_width/ref_height；
未提供时默认 1920x1080（兼容旧数据）。
"""

from __future__ import annotations

REF_WIDTH = 1920
REF_HEIGHT = 1080


def parse_roi(roi: str) -> tuple[int, int, int, int]:
    parts = [int(x.strip()) for x in roi.split(",")]
    if len(parts) != 4:
        raise ValueError("roi 格式应为 x1,y1,x2,y2")
    return parts[0], parts[1], parts[2], parts[3]


def scale_roi_and_line(
    roi: str,
    line_y: int,
    width: int,
    height: int,
    ref_width: int = REF_WIDTH,
    ref_height: int = REF_HEIGHT,
) -> tuple[int, int, int, int, int, str]:
    """
    将参考坐标缩放到当前视频，并 clamp 到画面内。

    - 若标定参考分辨率与视频一致（如 2880x1620 标定用于同尺寸视频），则不放大/缩小
    - 水平按 width/ref_width 缩放
    - 纵向按 height/ref_height 缩放
    - 过线 Y：按 height/ref_height 比例缩放（与 ROI 一致）
    """
    if width <= 0 or height <= 0:
        raise ValueError(f"无效视频尺寸: {width}x{height}")
    if ref_width <= 0 or ref_height <= 0:
        raise ValueError(f"无效参考尺寸: {ref_width}x{ref_height}")

    x1, y1, x2, y2 = parse_roi(roi)
    sx = width / ref_width
    sy = height / ref_height

    x1 = int(round(x1 * sx))
    x2 = int(round(x2 * sx))
    y1 = int(round(y1 * sy))
    y2 = int(round(y2 * sy))
    scaled_line = int(round(line_y * sy))

    # 仅当参考高度明显小于视频（旧 1080 标定用到更高视频）时，才把 ROI 底边拉到接近底部
    if ref_height < height * 0.9:
        y2 = max(y2, int(height * 0.95))

    x1 = max(0, min(width - 1, x1))
    x2 = max(x1 + 1, min(width, x2))
    y1 = max(0, min(height - 1, y1))
    y2 = max(y1 + 1, min(height, y2))

    # 过线 Y 只钳到画面内；标定可能故意把门线放在 ROI 外缘附近
    line_y_out = max(0, min(height - 1, scaled_line))

    scaled_roi = f"{x1},{y1},{x2},{y2}"
    return x1, y1, x2, y2, line_y_out, scaled_roi
