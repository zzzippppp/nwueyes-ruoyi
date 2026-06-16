#!/usr/bin/env python3
"""列出萤石账号下所有设备。"""
import json
import urllib.parse
import urllib.request

APP_KEY = "f202d6c2be564c948e3910c14d83b85d"
APP_SECRET = "ff9b8ea98de91e17cac9842aa75662a6"
BASE = "https://open.ys7.com"


def post(path, params):
    body = urllib.parse.urlencode(params).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


tok = post("/api/lapp/token/get", {"appKey": APP_KEY, "appSecret": APP_SECRET})
token = tok["data"]["accessToken"]
devices = post(
    "/api/lapp/device/list",
    {"accessToken": token, "pageStart": "0", "pageSize": "50"},
)
print(json.dumps(devices, ensure_ascii=False, indent=2))
