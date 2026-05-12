#!/usr/bin/env python3
"""
Gửi tóm tắt kết quả test (pass / fail / skip) của Jenkins build hiện tại
vào Slack qua HTTP proxy:
    https://timetracking.ikara.co/slack/send?channelId=...&message=...

Nguồn dữ liệu: Jenkins Allure Plugin sau khi pipeline đã gọi
allure([...]) trong post.always:
    {BUILD_URL}allure/data/suites.json

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


def http_get_json(url: str, auth_header: str | None) -> dict:
    last_err: Exception | None = None
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


def collect_leaves(node, leaves):
    if not isinstance(node, dict):
        return
    children = node.get("children")
    if children:
        for c in children:
            collect_leaves(c, leaves)
        return
    # Bỏ qua node tổng hợp "Device Summary" do listener tự sinh — không phải test thật.
    if (node.get("name") or "").startswith("Device Summary"):
        return
    leaves.append((node.get("status") or "unknown").lower())


def compute_stats(statuses):
    total = len(statuses)
    passed = sum(1 for s in statuses if s == "passed")
    failed = sum(1 for s in statuses if s in ("failed", "broken"))
    skipped = sum(1 for s in statuses if s == "skipped")
    return total, passed, failed, skipped


def build_message(total, passed, failed, skipped, build_number, allure_url):
    if total == 0:
        header = f"⚠️ *Build #{build_number}*"
    elif failed:
        header = f"🚨 *Build #{build_number}*"
    else:
        header = f"✅ *Build #{build_number}*"
    return (
        f"{header}\n"
        f"📊 Tổng: {total}  |  ✅ Pass: {passed}  |  ❌ Fail: {failed}  |  ⏭️ Skip: {skipped}\n"
        f"🔗 Allure: {allure_url}"
    )


def send_slack(channel, message):
    qs = urllib.parse.urlencode({"channelId": channel, "message": message})
    url = f"{SLACK_PROXY}?{qs}"
    print(f"[slack] GET proxy ({len(message)} chars in message)")
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as resp:
        body = resp.read().decode("utf-8", errors="replace")
        print(f"[slack] HTTP {resp.status}: {body[:300]}")


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
    suites_url = f"{base}/allure/data/suites.json"
    allure_report_url = f"{base}/allure/"

    auth_header = None
    if args.user and args.password:
        token = base64.b64encode(f"{args.user}:{args.password}".encode()).decode()
        auth_header = f"Basic {token}"
    else:
        print("[slack] không có Jenkins credential, sẽ thử fetch unauthenticated.")

    try:
        suites = http_get_json(suites_url, auth_header)
        statuses = []
        collect_leaves(suites, statuses)
        total, passed, failed, skipped = compute_stats(statuses)
    except Exception as e:
        # Build có thể vỡ trước stage Allure → suites.json không tồn tại.
        # Vẫn gửi notify (yêu cầu: lúc nào cũng gửi) với count = 0 + header ⚠️.
        print(f"[slack] không lấy được suites.json: {e} — gửi fallback notify total=0.")
        total, passed, failed, skipped = 0, 0, 0, 0

    print(f"[slack] stats total={total} pass={passed} fail={failed} skip={skipped}")

    message = build_message(total, passed, failed, skipped, args.build_number, allure_report_url)
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
