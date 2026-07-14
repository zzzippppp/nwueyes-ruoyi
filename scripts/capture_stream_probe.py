#!/usr/bin/env python3
"""拉主码流抽一帧，供门线/ROI 人工标定（不写库、不跑 YOLO）。"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time

import cv2

from live_stream_worker_yolo import (
    collect_probe_frame,
    is_ezviz_codec_error_frame,
    open_capture,
    rtsp_candidate_urls,
)
from roi_scale import scale_roi_and_line


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stream-url", required=True)
    parser.add_argument("--stream-protocol", choices=["rtsp", "hls", "flv"], default="hls")
    parser.add_argument("--storage-root", required=True)
    parser.add_argument("--camera-id", type=int, required=True)
    parser.add_argument("--line-y", type=int, default=520)
    parser.add_argument("--roi", default="620,170,1290,760")
    parser.add_argument("--open-timeout-sec", type=float, default=60.0)
    parser.add_argument("--rtsp-buffer-size", type=int, default=1)
    args = parser.parse_args()

    stream_urls = (
        rtsp_candidate_urls(args.stream_url) if args.stream_protocol == "rtsp" else [args.stream_url]
    )
    open_deadline = time.time() + args.open_timeout_sec
    url_index = 0
    video_cap = open_capture(stream_urls[url_index], args.stream_protocol, args.rtsp_buffer_size)
    probe = None

    while time.time() < open_deadline:
        if not video_cap.isOpened():
            video_cap.release()
            video_cap = open_capture(stream_urls[url_index], args.stream_protocol, args.rtsp_buffer_size)
            time.sleep(0.3)
            continue

        if args.stream_protocol in ("flv", "hls"):
            remaining = open_deadline - time.time()
            if remaining > 0:
                probe = collect_probe_frame(
                    video_cap,
                    time.time() + min(8.0, remaining),
                    args.stream_protocol,
                )
            if probe is not None:
                break
        else:
            ret, frame = video_cap.read()
            if ret and frame is not None and frame.size > 0 and not is_ezviz_codec_error_frame(frame):
                probe = frame
                break

        video_cap.release()
        url_index += 1
        if url_index >= len(stream_urls):
            url_index = 0
            time.sleep(0.5)
            continue
        video_cap = open_capture(stream_urls[url_index], args.stream_protocol, args.rtsp_buffer_size)

    video_cap.release()

    if probe is None:
        print(
            json.dumps({"ok": False, "message": "无法打开直播流或超时未读到有效帧"}, ensure_ascii=False),
            flush=True,
        )
        return 3

    if is_ezviz_codec_error_frame(probe):
        print(
            json.dumps(
                {"ok": False, "message": "视频编码非 H264，请在萤石 App 改为 H264 或改用局域网 RTSP"},
                ensure_ascii=False,
            ),
            flush=True,
        )
        return 4

    height, width = probe.shape[:2]
    x1, y1, x2, y2, line_y, _ = scale_roi_and_line(args.roi, args.line_y, width, height)

    probe_dir = os.path.join(args.storage_root, "log_library", "probe")
    os.makedirs(probe_dir, exist_ok=True)
    raw_name = f"camera_{args.camera_id}_raw.jpg"
    overlay_name = f"camera_{args.camera_id}_overlay.jpg"
    raw_path = os.path.join(probe_dir, raw_name)
    overlay_path = os.path.join(probe_dir, overlay_name)

    cv2.imwrite(raw_path, probe, [int(cv2.IMWRITE_JPEG_QUALITY), 95])

    vis = probe.copy()
    cv2.rectangle(vis, (x1, y1), (x2, y2), (0, 255, 0), 2)
    cv2.line(vis, (x1, line_y), (x2, line_y), (0, 0, 255), 2)
    label = f"camera={args.camera_id} {width}x{height} lineY={line_y}"
    cv2.putText(vis, label, (8, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
    cv2.imwrite(overlay_path, vis, [int(cv2.IMWRITE_JPEG_QUALITY), 90])

    print(
        json.dumps(
            {
                "ok": True,
                "rawFileName": raw_name,
                "overlayFileName": overlay_name,
                "width": width,
                "height": height,
                "lineY": args.line_y,
                "roi": args.roi,
            },
            ensure_ascii=False,
        ),
        flush=True,
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:
        print(json.dumps({"ok": False, "message": str(ex)}, ensure_ascii=False), flush=True)
        sys.exit(1)
