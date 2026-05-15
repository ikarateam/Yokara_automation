#!/usr/bin/env python3
"""
Gửi tóm tắt kết quả test (passed / failed / broken / skipped / unknown) của
Jenkins build hiện tại vào Slack qua HTTP proxy:
    https://timetracking.ikara.co/slack/send?channelId=...&message=...

Nguồn dữ liệu (sau khi pipeline gọi `allure([...])` ở post.always):
- {BUILD_URL}/allure/widgets/summary.json  → overall 5 statuses
- {BUILD_URL}/allure/data/suites.json      → tree phân cấp Devices → branch
- {BUILD_URL}/allure/data/test-cases/<uuid>.json → labels device.platform/name/udid

Template message (1 device hoặc nhiều device parallel):
    🚨 Build #N
    📊 Tổng: T  |  ✅ Pass: P  |  ❌ Fail: F  |  🐛 Broken: B  |  ⏭️ Skip: S  |  ❓ Unknown: U
    🔗 Allure: <link>

    📱 ios — Apple iPhone 13 Pro Max — 00008110-…
       Tổng: T  |  ✅ P  ❌ F  🐛 B  ⏭️ S  ❓ U
       🔗 <link-#suites/uid>

Script không bao giờ exit non-zero để tránh làm vỡ build chỉ vì lỗi notify.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.parse
import urllib.request

SLACK_PROXY = "https://timetracking.ikara.co/slack/send"

HTTP_TIMEOUT = 20
RETRY_COUNT = 5
RETRY_DELAY_SEC = 3

# Bộ 5 status của Allure 2 (xem widgets/summary.json).
STATUSES = ("passed", "failed", "broken", "skipped", "unknown")
ZERO_STATS = {s: 0 for s in STATUSES}


def http_get_json(url, auth_header):
    last_err = None
    for i in range(RETRY_COUNT):
        try:
            req = urllib.request.Request(url, method="GET")
            if auth_header:
                req.add_header("Authorization", auth_header)
            with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            last_err = e
            print(f"[slack] retry {i + 1}/{RETRY_COUNT} GET {url}: {e}")
            time.sleep(RETRY_DELAY_SEC)
    raise RuntimeError(f"GET {url} failed after {RETRY_COUNT} retries: {last_err}")


def http_get_json_quiet(url, auth_header):
    """Như http_get_json nhưng không retry, không throw — dùng cho test-case JSON
    (mỗi device chỉ fetch 1 lần, fail thì fallback display branchName thô)."""
    try:
        req = urllib.request.Request(url, method="GET")
        if auth_header:
            req.add_header("Authorization", auth_header)
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"[slack] (quiet) GET {url}: {e}")
        return None


# ============================================================
# Tree walking
# ============================================================

def find_devices_node(node):
    """DFS tìm node {name=='Devices'} — parentSuite do AllureDeviceLabels gắn."""
    if not isinstance(node, dict):
        return None
    if node.get("name") == "Devices":
        return node
    for c in node.get("children") or []:
        r = find_devices_node(c)
        if r:
            return r
    return None


def collect_leaves(node, leaves):
    """Thu hết test leaf bên dưới node (mỗi leaf = 1 test result)."""
    if not isinstance(node, dict):
        return
    children = node.get("children")
    if children:
        for c in children:
            collect_leaves(c, leaves)
        return
    if (node.get("name") or "").startswith("Device Summary"):
        return
    leaves.append({
        "uid": node.get("uid"),
        "status": (node.get("status") or "unknown").lower(),
    })


def stats_from_leaves(leaves):
    stats = dict(ZERO_STATS)
    for lf in leaves:
        s = lf["status"]
        if s not in stats:
            s = "unknown"
        stats[s] += 1
    stats["total"] = sum(stats[s] for s in STATUSES)
    return stats


# ============================================================
# Per-device data
# ============================================================

def fetch_device_labels(base_url, leaves, auth_header):
    """Đọc labels device.platform / device.name / device.udid từ test-case JSON
    của leaf đầu tiên (cùng device → cùng labels)."""
    for lf in leaves:
        uid = lf.get("uid")
        if not uid:
            continue
        url = f"{base_url}/allure/data/test-cases/{uid}.json"
        data = http_get_json_quiet(url, auth_header)
        if not isinstance(data, dict):
            continue
        labels = {l.get("name"): l.get("value") for l in data.get("labels") or []
                  if isinstance(l, dict)}
        return {
            "platform": labels.get("device.platform"),
            "name": labels.get("device.name"),
            "udid": labels.get("device.udid"),
        }
    return {}


def build_device_blocks(base_url, suites, auth_header):
    """Trả list device dicts:
       [{branchName, uid, platform, name, udid, stats}, ...]"""
    devices_node = find_devices_node(suites)
    if not devices_node:
        # Fallback: tree không có 'Devices' parentSuite (vd test chạy không qua
        # BaseDriver) → coi root là 1 nhóm chung.
        leaves = []
        collect_leaves(suites, leaves)
        return [{
            "branchName": "(không có nhóm Devices)",
            "uid": "",
            "platform": "?",
            "name": "?",
            "udid": "?",
            "stats": stats_from_leaves(leaves),
        }]

    result = []
    for child in devices_node.get("children") or []:
        leaves = []
        collect_leaves(child, leaves)
        labels = fetch_device_labels(base_url, leaves, auth_header)
        result.append({
            "branchName": child.get("name") or "",
            "uid": child.get("uid") or "",
            "platform": labels.get("platform") or "?",
            "name": labels.get("name") or "?",
            "udid": labels.get("udid") or "?",
            "stats": stats_from_leaves(leaves),
        })
    return result


# ============================================================
# Message
# ============================================================

def overall_from_summary(summary_json):
    """Đọc statistic từ widgets/summary.json — đủ 5 statuses + total."""
    stat = (summary_json or {}).get("statistic") or {}
    out = {s: int(stat.get(s) or 0) for s in STATUSES}
    out["total"] = int(stat.get("total") or sum(out[s] for s in STATUSES))
    return out


def overall_from_devices(devices):
    """Fallback nếu summary.json không lấy được."""
    out = dict(ZERO_STATS)
    out["total"] = 0
    for d in devices:
        for k in STATUSES:
            out[k] += d["stats"].get(k, 0)
        out["total"] += d["stats"].get("total", 0)
    return out


def fmt_stats_line(stats):
    return (
        f"Total: {stats['total']} | "
        f"Pass: {stats['passed']} | "
        f"Fail: {stats['failed']} | "
        f"Broken: {stats['broken']} | "
        f"Skip: {stats['skipped']} | "
        f"Unknown: {stats['unknown']}"
    )


def build_message(overall, devices, build_number, base_url):
    """Tối giản: 1 dòng overall, 1 dòng / device, link Allure cuối."""
    lines = [f"Build #{build_number} - {fmt_stats_line(overall)}"]
    for d in devices:
        lines.append(
            f"{d['platform']} - {d['name']} - {d['udid']} - {fmt_stats_line(d['stats'])}"
        )
    lines.append(f"{base_url}/allure/")
    return "\n".join(lines)


def send_slack(channel, message):
    qs = urllib.parse.urlencode({"channelId": channel, "message": message})
    url = f"{SLACK_PROXY}?{qs}"
    print(f"[slack] GET proxy ({len(message)} chars in message)")
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as resp:
        body = resp.read().decode("utf-8", errors="replace")
        print(f"[slack] HTTP {resp.status}: {body[:300]}")


# ============================================================
# Main
# ============================================================

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build-url", default=os.environ.get("BUILD_URL", ""))
    ap.add_argument("--build-number", default=os.environ.get("BUILD_NUMBER", "?"))
    ap.add_argument("--channel", default=os.environ.get("SLACK_CHANNEL", "C0B2EMXH70E"))
    ap.add_argument("--user", default=os.environ.get("JENKINS_USER", ""))
    ap.add_argument("--password", default=os.environ.get("JENKINS_PASS", ""))
    ap.add_argument("--dry-run", action="store_true", default=False,
                    help="Chỉ in message preview, không gọi Slack.")
    args = ap.parse_args()

    if not args.build_url:
        print("[slack] BUILD_URL trống, bỏ qua.")
        return 0

    base = args.build_url.rstrip("/")

    auth_header = None
    if args.user and args.password:
        token = base64.b64encode(f"{args.user}:{args.password}".encode()).decode()
        auth_header = f"Basic {token}"
    else:
        print("[slack] không có Jenkins credential, sẽ thử fetch unauthenticated.")

    overall = None
    devices = []
    try:
        summary = http_get_json(f"{base}/allure/widgets/summary.json", auth_header)
        overall = overall_from_summary(summary)
    except Exception as e:
        print(f"[slack] không lấy được summary.json: {e}")

    try:
        suites = http_get_json(f"{base}/allure/data/suites.json", auth_header)
        devices = build_device_blocks(base, suites, auth_header)
    except Exception as e:
        print(f"[slack] không lấy được suites.json: {e}")

    if overall is None:
        overall = overall_from_devices(devices) if devices else dict(ZERO_STATS, total=0)

    print(f"[slack] overall: {overall}")
    print(f"[slack] devices: {len(devices)}")
    for d in devices:
        print(f"  - {d['platform']} {d['name']} {d['udid']} → {d['stats']}")

    message = build_message(overall, devices, args.build_number, base)
    print("[slack] message preview:\n" + "-" * 60 + f"\n{message}\n" + "-" * 60)

    if args.dry_run:
        print("[slack] dry-run: không gọi Slack.")
        return 0

    try:
        send_slack(args.channel, message)
    except Exception as e:
        print(f"[slack] gửi Slack lỗi: {e}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
