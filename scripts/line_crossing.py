#!/usr/bin/env python3
"""过线判定：轨迹线段穿线 + 每轨迹门区状态(C)，抑制转身/换 ID 重复进门。"""

from __future__ import annotations

from collections import deque
from typing import Deque, Dict, List, Optional, Tuple

SIDE_OUTSIDE = "outside"
SIDE_INSIDE = "inside"


def detect_segment_cross(
    y0: float,
    y1: float,
    line_y: int,
    exit_margin: int = 0,
    min_dy: float = 4.0,
) -> Optional[str]:
    """
    脚点轨迹线段穿门槛线 + 方向判定。
    向下穿过 = 进门；向上穿过 = 出门。
    """
    dy = y1 - y0
    if abs(dy) < min_dy:
        return None

    if dy > 0:
        if y0 <= line_y < y1:
            return "enter"
        if (y0 - line_y) * (y1 - line_y) < 0:
            return "enter"
        return None

    if y0 >= line_y and y1 < line_y:
        if exit_margin > 0 and y1 > line_y - exit_margin:
            return None
        return "exit"
    if (y0 - line_y) * (y1 - line_y) < 0:
        if exit_margin > 0 and y1 > line_y - exit_margin:
            return None
        return "exit"
    return None


def detect_window_cross(
    points: List[float],
    line_y: int,
    exit_margin: int = 0,
    min_dy: float = 10.0,
    min_points: int = 4,
    monotonic_slop: float = 3.0,
) -> Optional[str]:
    """窗口首尾连线穿线且整体单调移动（慢速过线补检）。"""
    if len(points) < min_points:
        return None
    y0, y1 = points[0], points[-1]
    dy = y1 - y0
    if abs(dy) < min_dy:
        return None

    if dy > 0:
        if not all(points[i + 1] >= points[i] - monotonic_slop for i in range(len(points) - 1)):
            return None
        if y0 <= line_y < y1:
            return "enter"
        return None

    if not all(points[i + 1] <= points[i] + monotonic_slop for i in range(len(points) - 1)):
        return None
    if y0 >= line_y and y1 < line_y:
        if exit_margin > 0 and y1 > line_y - exit_margin:
            return None
        return "exit"
    return None


def detect_line_cross(
    old_cy: float,
    cy: float,
    line_y: int,
    exit_margin: int = 0,
) -> Optional[str]:
    """兼容旧接口：相邻两点线段穿线。"""
    return detect_segment_cross(old_cy, cy, line_y, exit_margin)


def update_cross_pending(
    pending: Dict[int, Tuple[str, int]],
    track_id: int,
    event: str,
    confirm_frames: int,
) -> Optional[str]:
    """同向穿线需连续 confirm_frames 帧才生效。"""
    prev_dir, count = pending.get(track_id, ("", 0))
    if prev_dir == event:
        count += 1
    else:
        count = 1
    pending[track_id] = (event, count)
    if count >= max(1, confirm_frames):
        pending.pop(track_id, None)
        return event
    return None


def scaled_infer_margin(base_margin: int, height: int, ref_height: int = 1080) -> int:
    if ref_height <= 0:
        return base_margin
    return max(base_margin, int(round(base_margin * height / ref_height)))


def tight_infer_margin(
    height: int,
    ref_height: int = 1080,
    base_px: int = 45,
    max_height_ratio: float = 0.045,
) -> int:
    """D：补推断只允许「刚越过门槛」的窄带，避免门内深处/转身误报。"""
    if height <= 0:
        return base_px
    scaled = int(round(base_px * height / ref_height))
    cap = int(round(height * max_height_ratio))
    return max(28, min(scaled, cap))


def exit_hysteresis_margin(height: int, ref_height: int = 1080, base_px: int = 28) -> int:
    """出门滞回：脚点需明显高于过线，避免门槛处转身误报 exit。"""
    if height <= 0:
        return base_px
    return max(18, int(round(base_px * height / ref_height)))


def min_track_hits_for_event(height: int, ref_height: int = 1080, base_hits: int = 8) -> int:
    """轨迹命中帧数不足时不产出 enter/exit（抑制 ByteTrack 闪 ID）。"""
    if height <= 0:
        return base_hits
    return max(4, int(round(base_hits * height / ref_height)))


class PerTrackDoorGate:
    """C：每条 ByteTrack 轨迹维护 outside/inside + 脚点历史，禁止同轨迹重复 enter。"""

    def __init__(
        self,
        line_y: int,
        tight_margin: int,
        exit_margin: int = 0,
        history_len: int = 8,
        min_segment_dy: float = 4.0,
    ):
        self.line_y = line_y
        self.tight_margin = tight_margin
        self.exit_margin = max(0, exit_margin)
        self.history_len = max(3, history_len)
        self.min_segment_dy = min_segment_dy
        self._side: Dict[int, str] = {}
        self._real_enter: Dict[int, bool] = {}
        self._silent_from_other: Dict[int, bool] = {}
        self._foot_history: Dict[int, Deque[float]] = {}
        self._seg_watermark: Dict[int, int] = {}

    def side(self, track_id: int) -> str:
        return self._side.get(track_id, SIDE_OUTSIDE)

    def had_real_enter(self, track_id: int) -> bool:
        return self._real_enter.get(track_id, False)

    def mark_inside_silent(self, track_id: int, *, from_other: bool = False) -> None:
        """换 ID / 已在门内深处首次检出：记 inside，不发出 enter。"""
        self._side[track_id] = SIDE_INSIDE
        if from_other:
            self._silent_from_other[track_id] = True

    def _reset_history(self, track_id: int, cy: float) -> None:
        self._foot_history[track_id] = deque([cy], maxlen=self.history_len)
        self._seg_watermark[track_id] = 0

    def on_new_track(self, track_id: int, cy: float) -> None:
        """新轨迹首次出现时的静默状态同步。"""
        self._reset_history(track_id, cy)
        if self.side(track_id) == SIDE_INSIDE:
            return
        if cy > self.line_y + self.tight_margin:
            self.mark_inside_silent(track_id, from_other=False)
            return
        if cy > self.line_y and self.other_inside(track_id):
            self.mark_inside_silent(track_id, from_other=True)

    def other_inside(self, track_id: int) -> bool:
        return any(
            side == SIDE_INSIDE for tid, side in self._side.items() if tid != track_id
        )

    def filter_event(self, track_id: int, event: Optional[str]) -> Optional[str]:
        if event is None:
            return None
        if event == "enter" and self.side(track_id) == SIDE_INSIDE:
            return None
        if event == "exit":
            if self.had_real_enter(track_id):
                if self.side(track_id) == SIDE_OUTSIDE:
                    return None
            elif self._silent_from_other.get(track_id, False):
                return None
        return event

    def _exit_margin_for(self, track_id: int) -> int:
        if not self.had_real_enter(track_id) and not self._silent_from_other.get(track_id, False):
            return 0
        return self.exit_margin

    def observe_foot(self, track_id: int, cy: float, *, seed_y: Optional[float] = None) -> None:
        """记录脚点；seed_y 用于首帧与上一采样点衔接。"""
        hist = self._foot_history.get(track_id)
        if hist is None:
            hist = deque(maxlen=self.history_len)
            self._foot_history[track_id] = hist
            self._seg_watermark.setdefault(track_id, 0)
            if seed_y is not None and abs(seed_y - cy) >= 0.5:
                hist.append(seed_y)
        if hist and abs(hist[-1] - cy) < 0.5:
            return
        hist.append(cy)

    def try_cross_trajectory(
        self,
        pending: Dict[int, Tuple[str, int]],
        track_id: int,
        confirm_frames: int,
    ) -> Optional[str]:
        """扫描轨迹历史中的线段穿线（含窗口首尾补检）。"""
        hist = self._foot_history.get(track_id)
        if hist is None or len(hist) < 2:
            return None

        use_margin = self._exit_margin_for(track_id)
        points = list(hist)
        start = self._seg_watermark.get(track_id, 0)
        start = max(0, min(start, len(points) - 2))

        for i in range(start, len(points) - 1):
            cross = detect_segment_cross(
                points[i],
                points[i + 1],
                self.line_y,
                use_margin,
                self.min_segment_dy,
            )
            if cross is None:
                continue
            raw = update_cross_pending(pending, track_id, cross, confirm_frames)
            if raw:
                self._seg_watermark[track_id] = i + 1
                return self.filter_event(track_id, raw)

        window_ev = detect_window_cross(points, self.line_y, use_margin)
        if window_ev:
            raw = update_cross_pending(pending, track_id, window_ev, confirm_frames)
            if raw:
                self._seg_watermark[track_id] = max(0, len(points) - 1)
                return self.filter_event(track_id, raw)

        self._seg_watermark[track_id] = max(0, len(points) - 2)
        return None

    def try_cross(
        self,
        pending: Dict[int, Tuple[str, int]],
        track_id: int,
        old_cy: float,
        cy: float,
        confirm_frames: int,
    ) -> Optional[str]:
        self.observe_foot(track_id, cy, seed_y=old_cy)
        return self.try_cross_trajectory(pending, track_id, confirm_frames)

    def try_infer_enter(self, track_id: int, cy: float, is_new_track: bool) -> Optional[str]:
        """D：仅新轨迹、仍在门外侧状态、脚点紧贴红线、且门区无他人 inside 时补推断。"""
        if not is_new_track:
            return None
        if self.side(track_id) == SIDE_INSIDE:
            return None
        if cy <= self.line_y:
            return None
        if (cy - self.line_y) > self.tight_margin:
            return None
        if self.other_inside(track_id):
            return None
        return "enter"

    def commit(self, track_id: int, event: str) -> None:
        if event == "enter":
            self._side[track_id] = SIDE_INSIDE
            self._real_enter[track_id] = True
            self._silent_from_other.pop(track_id, None)
        elif event == "exit":
            self._side[track_id] = SIDE_OUTSIDE
            self._silent_from_other.pop(track_id, None)
        hist = self._foot_history.get(track_id)
        if hist:
            self._foot_history[track_id] = deque([hist[-1]], maxlen=self.history_len)
            self._seg_watermark[track_id] = 0


# 兼容旧调用
def infer_first_seen_event(
    cy: float,
    line_y: int,
    enter_margin: int,
    exit_margin: int,
) -> Optional[str]:
    _ = exit_margin
    if cy > line_y and (cy - line_y) <= enter_margin:
        return "enter"
    return None


def effective_infer_margin(
    base_margin: int,
    height: int,
    ref_height: int = 1080,
    min_height_ratio: float = 0.05,
) -> int:
    scaled = scaled_infer_margin(base_margin, height, ref_height)
    ratio_floor = int(round(height * min_height_ratio)) if height > 0 else base_margin
    return max(scaled, ratio_floor)
