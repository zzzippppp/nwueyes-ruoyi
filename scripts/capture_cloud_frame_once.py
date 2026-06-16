#!/usr/bin/env python3
"""从萤石公网云转发拉一帧并保存到桌面。"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime

import cv2

APP_KEY = "f202d6c2be564c948e3910c14d83b85d"
APP_SECRET = "ff9b8ea98de91e17cac9842aa75662a6"
BASE = "https://open.ys7.com"
DEFAULT_DEVICE = "BK4225491"
CHANNEL = 1
PROTOCOL = 4  # FLV
QUALITY = 1
TIMEOUT_SEC = 90


def post(path: str, params: dict) -> dict:
    body = urllib.parse.urlencode(params).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def desktop_dir() -> str:
    userprofile = os.environ.get("USERPROFILE")
    if userprofile:
        return os.path.join(userprofile, "Desktop")
    return os.path.join(os.path.expanduser("~"), "Desktop")


def write_image(path: str, frame) -> bool:
    ok, buf = cv2.imencode(".jpg", frame)
    if not ok:
        return False
    with open(path, "wb") as f:
        f.write(buf.tobytes())
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="萤石公网云转发截帧")
    parser.add_argument("--device", default=DEFAULT_DEVICE, help="设备序列号")
    parser.add_argument("--channel", type=int, default=CHANNEL, help="通道号")
    parser.add_argument("--valid-code", default="", help="设备验证码（加密设备必填）")
    args = parser.parse_args()

    desktop = desktop_dir()
    os.makedirs(desktop, exist_ok=True)
    out_path = os.path.join(
        desktop,
        f"cloud_stream_{args.device}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg",
    )

    tok = post("/api/lapp/token/get", {"appKey": APP_KEY, "appSecret": APP_SECRET})
    if tok.get("code") != "200":
        print(f"token failed: {tok}", file=sys.stderr)
        return 1
    access_token = tok["data"]["accessToken"]
    print("token ok")

    live_params = {
        "accessToken": access_token,
        "deviceSerial": args.device,
        "channelNo": str(args.channel),
        "protocol": str(PROTOCOL),
        "type": "1",
        "expireTime": "3600",
        "quality": str(QUALITY),
        "supportH265": "0",
    }
    if args.valid_code:
        live_params["code"] = args.valid_code

    live = post(
        "/api/lapp/v2/live/address/get",
        live_params,
    )
    if live.get("code") != "200":
        print(f"live address failed: {live}", file=sys.stderr)
        return 1

    data = live.get("data") or {}
    stream_url = data.get("hdUrl") or data.get("url") or data.get("rtmpUrl")
    if not stream_url:
        print(f"no stream url in response: {data}", file=sys.stderr)
        return 1
    print(f"stream url: {stream_url[:100]}...")

    os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "fflags;nobuffer|max_delay;500000"
    cap = cv2.VideoCapture(stream_url, cv2.CAP_FFMPEG)
    if not cap.isOpened():
        print("failed to open stream", file=sys.stderr)
        return 2

    frame = None
    deadline = time.time() + TIMEOUT_SEC
    while time.time() < deadline:
        ret, candidate = cap.read()
        if ret and candidate is not None and candidate.size > 0:
            frame = candidate
            break
        time.sleep(0.3)

    cap.release()
    if frame is None:
        print("timeout: no frame received", file=sys.stderr)
        return 3

    if not write_image(out_path, frame):
        print(f"failed to write {out_path}", file=sys.stderr)
        return 4

    h, w = frame.shape[:2]
    print(f"saved: {out_path}")
    print(f"size: {w}x{h}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
