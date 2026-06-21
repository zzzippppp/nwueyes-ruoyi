#!/usr/bin/env python3
"""
最小识别事件上报脚本（Phase 1 验证）

用途：
1) 先上报 enter（eventTime=过去 10 秒）
2) 暂停后上报 exit（eventTime=过去 2 秒）
3) 验证后端是否按 eventTime 记账，而不是按收到请求时间记账
"""

import argparse
import datetime as dt
import json
import time
from urllib import request


def post_event(base_url: str, ingest_key: str, payload: dict):
    url = f"{base_url.rstrip('/')}/ingest/presence/event"
    body = json.dumps(payload).encode("utf-8")
    req = request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    if ingest_key:
        req.add_header("X-Ingest-Key", ingest_key)
    with request.urlopen(req, timeout=10) as resp:
        text = resp.read().decode("utf-8")
        return resp.status, text


def iso_now(offset_seconds: int):
    t = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))) + dt.timedelta(seconds=offset_seconds)
    return t.isoformat(timespec="seconds")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--ingest-key", default="local-presence-key")
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--track-key", default="mock_track_001")
    parser.add_argument("--person-id", type=int, default=1)
    parser.add_argument("--sleep-seconds", type=int, default=3)
    parser.add_argument("--enter-offset-seconds", type=int, default=0)
    parser.add_argument("--exit-offset-seconds", type=int, default=0)
    args = parser.parse_args()

    enter_payload = {
        "eventType": "enter",
        "cameraId": args.camera_id,
        "trackKey": args.track_key,
        "personId": args.person_id,
        "eventTime": iso_now(args.enter_offset_seconds),
        "faceImageUrl": "/dashboard/data-board/file/face/face_850cbff6b83b4cdea2161333f6a41f62.jpg",
        "bestMatchScore": 0.92,
    }
    code, text = post_event(args.base_url, args.ingest_key, enter_payload)
    print("enter:", code, text)

    time.sleep(args.sleep_seconds)

    exit_payload = {
        "eventType": "exit",
        "cameraId": args.camera_id,
        "trackKey": args.track_key,
        "personId": args.person_id,
        "eventTime": iso_now(args.exit_offset_seconds),
        "bestMatchScore": 0.88,
    }
    code, text = post_event(args.base_url, args.ingest_key, exit_payload)
    print("exit:", code, text)


if __name__ == "__main__":
    main()
