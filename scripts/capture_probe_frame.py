#!/usr/bin/env python3
"""从萤石/RTSP 拉流并保存一帧原图，供门线标定。"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

import cv2


def load_yaml_simple(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s*([A-Za-z0-9_.-]+):\s*(.+?)\s*(?:#.*)?$", line)
        if not m:
            continue
        key, val = m.group(1), m.group(2).strip()
        if val in ("", "null", "~"):
            continue
        if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
            val = val[1:-1]
        out[key] = val
    return out


def ezviz_post(path: str, data: dict) -> dict:
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(
        "https://open.ys7.com" + path,
        data=body,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def resolve_stream_url(app_key: str, app_secret: str, device_serial: str, channel: int) -> tuple[str, str]:
    token = ezviz_post("/api/lapp/token/get", {"appKey": app_key, "appSecret": app_secret})["data"]["accessToken"]
    info = ezviz_post(
        "/api/lapp/device/info",
        {"accessToken": token, "deviceSerial": device_serial},
    )["data"]
    print(
        f"[info] name={info.get('deviceName')} local={info.get('localAddress')} "
        f"status={info.get('status')} encrypt={info.get('isEncrypt')}",
        flush=True,
    )

    for proto, name in ((4, "flv"), (2, "hls")):
        live = ezviz_post(
            "/api/lapp/v2/live/address/get",
            {
                "accessToken": token,
                "deviceSerial": device_serial,
                "channelNo": channel,
                "protocol": proto,
                "type": 1,
                "expireTime": 3600,
                "quality": 1,
                "supportH265": 0,
            },
        )
        data = live.get("data") or {}
        url = data.get("hdUrl") or data.get("url") or data.get("rtmpUrl")
        print(f"[stream] {name} code={live.get('code')} url={(url or '')[:100]}", flush=True)
        if url:
            return url, name

    ip = info.get("localAddress") or info.get("localIp")
    if ip:
        return f"rtsp://admin@{ip}:554/h264/ch1/main/av_stream", "rtsp"
    raise RuntimeError("无法获取直播地址")


def capture_best_frame(stream_url: str, stream_proto: str, timeout_sec: float = 45):
    if stream_proto == "hls":
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "fflags;nobuffer|probesize;5000000|analyzeduration;5000000"
    elif stream_proto == "flv":
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "fflags;nobuffer|max_delay;500000"
    else:
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "fflags;nobuffer|rtsp_transport;tcp"

    cap = cv2.VideoCapture(stream_url, cv2.CAP_FFMPEG)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开流: {stream_url[:120]}")

    best = None
    best_area = 0
    deadline = time.time() + timeout_sec
    try:
        while time.time() < deadline:
            ret, frame = cap.read()
            if not ret or frame is None or frame.size == 0:
                time.sleep(0.2)
                continue
            h, w = frame.shape[:2]
            area = w * h
            if area > best_area:
                best = frame
                best_area = area
                print(f"[frame] {w}x{h}", flush=True)
            if stream_proto == "rtsp" and best is not None:
                break
            if best_area >= 1920 * 1080 and time.time() > deadline - timeout_sec + 5:
                break
            time.sleep(0.05)
    finally:
        cap.release()
    if best is None:
        raise RuntimeError("超时未读到有效帧")
    return best


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="ruoyi-admin/src/main/resources/application-local.yml")
    parser.add_argument("--device-serial", default="BK4225491", help="实验室正门")
    parser.add_argument("--channel", type=int, default=1)
    parser.add_argument("--output", required=True)
    parser.add_argument("--timeout-sec", type=float, default=45)
    args = parser.parse_args()

    cfg = load_yaml_simple(Path(args.config))
    app_key = cfg.get("appKey")
    app_secret = cfg.get("appSecret")
    if not app_key or not app_secret:
        raise SystemExit("application-local.yml 缺少 ezviz appKey/appSecret")

    stream_url, stream_proto = resolve_stream_url(app_key, app_secret, args.device_serial, args.channel)
    frame = capture_best_frame(stream_url, stream_proto, args.timeout_sec)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(out), frame, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
    h, w = frame.shape[:2]
    print(f"[saved] {out} ({w}x{h})", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as ex:
        print(f"[fatal] {ex}", file=sys.stderr)
        sys.exit(1)
