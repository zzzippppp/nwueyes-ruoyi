#!/usr/bin/env python3
"""
测试「识别服务器 → 摄像头」网络连通性（ping / RTSP 554 / 可选 RTSP 握手）。

服务器用法（SSH 登录后）:
  python3 /opt/safetyguard/backend/scripts/test_camera_reachability.py \\
    --config /opt/safetyguard/backend/config/application-local.yml

本地用法:
  python scripts/test_camera_reachability.py \\
    --config ruoyi-admin/src/main/resources/application-local.yml

也可只测指定 IP（跳过萤石 API）:
  python3 test_camera_reachability.py --camera-ip 10.9.0.231
"""

from __future__ import annotations

import argparse
import json
import platform
import re
import socket
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


def load_yaml_simple(path: Path) -> dict[str, str]:
    """Minimal key:value reader (no PyYAML dependency)."""
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


def cfg_get(cfg: dict[str, str], *keys: str) -> str | None:
    for k in keys:
        if k in cfg and cfg[k]:
            return cfg[k]
    return None


def local_ips() -> list[str]:
    ips: list[str] = []
    try:
        import socket as sock

        for info in sock.getaddrinfo(sock.gethostname(), None, sock.AF_INET):
            ip = info[4][0]
            if ip not in ips and not ip.startswith("127."):
                ips.append(ip)
    except OSError:
        pass
    # UDP trick: does not send packets, only picks outbound interface
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip not in ips:
            ips.insert(0, ip)
    except OSError:
        pass
    return ips or ["(unknown)"]


def ping_host(ip: str, timeout_sec: int = 3) -> tuple[bool, str]:
    system = platform.system().lower()
    if "windows" in system:
        cmd = ["ping", "-n", "2", "-w", str(timeout_sec * 1000), ip]
    else:
        cmd = ["ping", "-c", "2", "-W", str(timeout_sec), ip]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout_sec + 5)
        ok = proc.returncode == 0
        tail = (proc.stdout or proc.stderr or "").strip().splitlines()
        return ok, tail[-1] if tail else f"exit={proc.returncode}"
    except subprocess.TimeoutExpired:
        return False, "ping timeout"
    except FileNotFoundError:
        return False, "ping command not found"


def tcp_probe(ip: str, port: int, timeout_sec: float = 5.0) -> tuple[bool, str]:
    try:
        with socket.create_connection((ip, port), timeout=timeout_sec):
            return True, f"TCP {port} open"
    except socket.timeout:
        return False, f"TCP {port} timeout after {timeout_sec}s"
    except ConnectionRefusedError:
        return False, f"TCP {port} refused (host reachable, port closed)"
    except OSError as exc:
        return False, f"TCP {port} failed: {exc}"


def ezviz_post(path: str, data: dict[str, Any]) -> dict[str, Any]:
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(
        "https://open.ys7.com" + path,
        data=body,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def fetch_ezviz_devices(app_key: str, app_secret: str) -> list[dict[str, Any]]:
    token = ezviz_post("/api/lapp/token/get", {"appKey": app_key, "appSecret": app_secret})
    access = token["data"]["accessToken"]
    lst = ezviz_post("/api/lapp/device/list", {"accessToken": access, "pageStart": 0, "pageSize": 50})
    data = lst.get("data")
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict):
        items = data.get("list") or data.get("devices") or []
    else:
        items = []
    out: list[dict[str, Any]] = []
    for item in items:
        sn = item.get("deviceSerial") or ""
        info = ezviz_post("/api/lapp/device/info", {"accessToken": access, "deviceSerial": sn})["data"]
        out.append(
            {
                "deviceSerial": sn,
                "deviceName": info.get("deviceName") or item.get("deviceName"),
                "status": info.get("status"),
                "localAddress": info.get("localAddress"),
                "ssid": info.get("ssid"),
                "netType": info.get("netType"),
                "isEncrypt": info.get("isEncrypt"),
            }
        )
    return out


def parse_rtsp_host(url: str | None) -> str | None:
    if not url or not url.lower().startswith("rtsp://"):
        return None
    m = re.match(r"rtsp://(?:[^@/]+@)?([^:/]+)", url, re.I)
    return m.group(1) if m else None


def print_section(title: str) -> None:
    print()
    print("=" * 60)
    print(title)
    print("=" * 60)


def test_one_target(label: str, ip: str, rtsp_port: int) -> dict[str, Any]:
    print_section(f"测试目标: {label} ({ip})")
    ping_ok, ping_msg = ping_host(ip)
    tcp_ok, tcp_msg = tcp_probe(ip, rtsp_port)
    print(f"  ping      : {'PASS' if ping_ok else 'FAIL'}  ({ping_msg})")
    print(f"  RTSP {rtsp_port}  : {'PASS' if tcp_ok else 'FAIL'}  ({tcp_msg})")
    if ping_ok and not tcp_ok:
        print("  >> ping 通但 554 不通：常见原因 — RTSP 未开启 / 防火墙封 554 / 跨网段隔离")
    if not ping_ok and not tcp_ok:
        print("  >> ping 与 554 均失败：服务器与摄像头不在同一可达网段")
    if ping_ok and tcp_ok:
        print("  >> 网络层可达，可尝试局域网 RTSP 识别")
    return {"ip": ip, "ping": ping_ok, "tcp554": tcp_ok}


def main() -> int:
    parser = argparse.ArgumentParser(description="Test server/camera network reachability for RTSP")
    parser.add_argument(
        "--config",
        default="/opt/safetyguard/backend/config/application-local.yml",
        help="application-local.yml path (server default)",
    )
    parser.add_argument("--camera-ip", help="Skip Ezviz API; test this IP only")
    parser.add_argument("--device-serial", help="Only test this Ezviz device serial")
    parser.add_argument("--rtsp-port", type=int, default=554)
    args = parser.parse_args()

    cfg_path = Path(args.config)
    cfg = load_yaml_simple(cfg_path) if cfg_path.is_file() else {}

    print_section("本机信息")
    print(f"  hostname  : {platform.node()}")
    print(f"  local IPs : {', '.join(local_ips())}")
    print(f"  config    : {cfg_path} ({'found' if cfg_path.is_file() else 'NOT FOUND'})")

    lan_rtsp = cfg_get(cfg, "lanRtspUrl")
    lan_ip = parse_rtsp_host(lan_rtsp)
    if lan_rtsp:
        print(f"  lanRtspUrl: {lan_rtsp}")

    results: list[dict[str, Any]] = []

    if args.camera_ip:
        results.append(test_one_target("manual", args.camera_ip, args.rtsp_port))
    else:
        app_key = cfg_get(cfg, "appKey")
        app_secret = cfg_get(cfg, "appSecret")
        if not app_key or not app_secret:
            print_section("萤石 API")
            print("  SKIP: config 中未找到 ezviz.appKey / appSecret")
            if lan_ip:
                results.append(test_one_target("from lanRtspUrl", lan_ip, args.rtsp_port))
        else:
            print_section("萤石 API — 设备局域网地址")
            try:
                devices = fetch_ezviz_devices(app_key, app_secret)
            except Exception as exc:
                print(f"  ERROR: {exc}")
                if lan_ip:
                    results.append(test_one_target("from lanRtspUrl", lan_ip, args.rtsp_port))
                devices = []

            for dev in devices:
                sn = dev.get("deviceSerial") or ""
                if args.device_serial and sn != args.device_serial:
                    continue
                ip = dev.get("localAddress") or ""
                name = dev.get("deviceName") or sn
                online = dev.get("status") == 1
                print(f"  - {sn} ({name})")
                print(f"      online       : {'yes' if online else 'no'}")
                print(f"      localAddress : {ip or '(empty)'}")
                print(f"      ssid         : {dev.get('ssid')}")
                print(f"      isEncrypt    : {dev.get('isEncrypt')}")
                if ip:
                    results.append(test_one_target(f"{sn} {name}", ip, args.rtsp_port))

            if lan_ip and not any(r.get("ip") == lan_ip for r in results):
                results.append(test_one_target("from lanRtspUrl (extra)", lan_ip, args.rtsp_port))

    print_section("汇总")
    if not results:
        print("  没有可测试的目标。请 --camera-ip 10.x.x.x 或检查 config / 萤石账号。")
        return 1

    rtsp_ready = [r for r in results if r["ping"] and r["tcp554"]]
    print(f"  测试项数     : {len(results)}")
    print(f"  RTSP 可用数  : {len(rtsp_ready)}")
    if rtsp_ready:
        print("  结论         : 至少有一台摄像头可从本机 RTSP 直连 → 可用「局域网 RTSP」")
        return 0
    print("  结论         : 本机无法 RTSP 直连摄像头 → 请用「公网云转发」或联系网管放通 554")
    return 2


if __name__ == "__main__":
    sys.exit(main())
