#!/usr/bin/env python3
"""
Gửi báo cáo Allure (test fail / broken) của Jenkins build hiện tại vào Slack
qua HTTP proxy:
    https://timetracking.ikara.co/slack/send?channelId=...&message=...

Nguồn dữ liệu: Jenkins Allure Plugin endpoints sau khi pipeline đã gọi
allure([...]) trong post.always:
    {BUILD_URL}allure/data/suites.json
    {BUILD_URL}allure/data/test-cases/<uid>.json
    {BUILD_URL}allure/data/attachments/<source>

Script không bao giờ exit non-zero để tránh làm vỡ build chỉ vì lỗi notify.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

SLACK_PROXY = "https://timetracking.ikara.co/slack/send"

MAX_TESTS_IN_MESSAGE = 8
MAX_STEPS_PER_TEST = 3
MAX_ATTACHMENTS_PER_TEST = 3
MAX_REASON_CHARS = 220
MAX_MESSAGE_LEN = 7000

HTTP_TIMEOUT = 20
RETRY_COUNT = 5
RETRY_DELAY_SEC = 3

DEVICE_SUFFIX_RE = re.compile(r"\s*\[[^\]]+\]\s*$")


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


def collect_failed_leaves(node, leaves):
    if not isinstance(node, dict):
        return
    children = node.get("children")
    if children:
        for c in children:
            collect_failed_leaves(c, leaves)
        return
    status = (node.get("status") or "").lower()
    if status in ("failed", "broken"):
        leaves.append({
            "uid": node.get("uid"),
            "name": node.get("name") or "",
            "status": status,
        })


def is_synthetic_device_summary(leaf):
    return (leaf.get("name") or "").startswith("Device Summary")


def device_info(test_case):
    info = {"branch": "", "platform": "", "udid": "", "appium_port": ""}
    for lab in test_case.get("labels") or []:
        n = lab.get("name") or ""
        v = (lab.get("value") or "").strip()
        if not v:
            continue
        if n in ("device", "device.branch") and not info["branch"]:
            info["branch"] = v
        elif n in ("device.platform", "platform") and not info["platform"]:
            info["platform"] = v
        elif n == "device.udid":
            info["udid"] = v
        elif n in ("device.appiumPort", "appiumPort") and not info["appium_port"]:
            info["appium_port"] = v
    return info


def collect_failed_steps(steps, out, depth=0):
    for s in steps or []:
        status = (s.get("status") or "").lower()
        if status in ("failed", "broken"):
            msg = (s.get("statusMessage") or "").strip()
            if not msg:
                sd = s.get("statusDetails")
                if isinstance(sd, dict):
                    msg = (sd.get("message") or "").strip()
            out.append({
                "depth": depth,
                "name": (s.get("name") or "").strip(),
                "message": msg,
            })
        collect_failed_steps(s.get("steps"), out, depth + 1)


def gather_failed_attachments(test_case):
    """Attachments thuộc step fail/broken + attachment top-level của testStage."""
    seen = set()
    out = []

    def add(att):
        src = (att.get("source") or "").strip()
        if not src or src in seen:
            return
        seen.add(src)
        out.append(att)

    ts = test_case.get("testStage") or {}
    for a in ts.get("attachments") or []:
        add(a)

    def walk(steps):
        for s in steps or []:
            if (s.get("status") or "").lower() in ("failed", "broken"):
                for a in s.get("attachments") or []:
                    add(a)
            walk(s.get("steps"))

    walk(ts.get("steps"))
    return out


def attachment_label(att):
    name = (att.get("name") or "").lower()
    typ = (att.get("type") or "").lower()
    if "page source" in name:
        return "Page source"
    if typ.startswith("image/"):
        return "Screenshot"
    if typ.startswith("video/"):
        return "Video"
    if typ.startswith("text/"):
        return "Log"
    return "File"


def trim_test_name(raw):
    """testCreatePrivateRoom [ios-... · A:4710 ...] → testCreatePrivateRoom"""
    name = (raw or "").strip()
    return DEVICE_SUFFIX_RE.sub("", name).strip() or name


def first_line(text, limit):
    if not text:
        return ""
    line = text.splitlines()[0].strip()
    return line[:limit] + ("…" if len(line) > limit else "")


def build_message(failed_tests, build_number, allure_url):
    if not failed_tests:
        return f"✅ Build #{build_number} — 0 test fail."

    header = (
        f"🚨 *Build #{build_number}* — {len(failed_tests)} test fail/broken\n"
        f"🔗 Allure: {allure_url}"
    )
    blocks = [header]

    for idx, t in enumerate(failed_tests[:MAX_TESTS_IN_MESSAGE], 1):
        lines = []
        dev = t["device"]
        dev_bits = []
        if dev.get("udid"):
            dev_bits.append(f"udid {dev['udid']}")
        if dev.get("appium_port"):
            dev_bits.append(f"port {dev['appium_port']}")
        dev_tail = f"  ({', '.join(dev_bits)})" if dev_bits else ""
        dev_label = dev.get("branch") or dev.get("platform") or "unknown"
        status_icon = "💥" if t["status"] == "broken" else "❌"

        lines.append(f"{idx}. {status_icon} *{t['name']}*  —  📱 {dev_label}{dev_tail}")

        steps = t["failed_steps"][:MAX_STEPS_PER_TEST]
        if steps:
            for s in steps:
                step_name = s["name"] or "(no name)"
                lines.append(f"   ↳ Step fail: {step_name}")
                reason = first_line(s["message"], MAX_REASON_CHARS)
                if reason:
                    lines.append(f"      reason: {reason}")
        else:
            reason = first_line(t["status_message"], MAX_REASON_CHARS)
            if reason:
                lines.append(f"   ↳ reason: {reason}")

        atts = t["attachments"][:MAX_ATTACHMENTS_PER_TEST]
        for label, url in atts:
            lines.append(f"   📎 {label}: {url}")

        blocks.append("\n".join(lines))

    remaining = len(failed_tests) - MAX_TESTS_IN_MESSAGE
    if remaining > 0:
        blocks.append(f"… và {remaining} test fail khác — xem Allure report.")

    msg = "\n\n".join(blocks)
    if len(msg) > MAX_MESSAGE_LEN:
        msg = msg[: MAX_MESSAGE_LEN - 50].rstrip() + "\n…(cắt bớt do quá dài)"
    return msg


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
    ap.add_argument(
        "--notify-on-pass",
        action="store_true",
        default=os.environ.get("SLACK_NOTIFY_ON_PASS", "").lower() in ("1", "true", "yes"),
    )
    ap.add_argument("--dry-run", action="store_true", default=False,
                    help="Chỉ in message preview, không gọi Slack.")
    args = ap.parse_args()

    if not args.build_url:
        print("[slack] BUILD_URL trống, bỏ qua.")
        return 0

    base = args.build_url.rstrip("/")
    suites_url = f"{base}/allure/data/suites.json"
    test_case_url_tpl = f"{base}/allure/data/test-cases/{{uid}}.json"
    attachment_base = f"{base}/allure/data/attachments"
    allure_report_url = f"{base}/allure/#suites"

    auth_header = None
    if args.user and args.password:
        token = base64.b64encode(f"{args.user}:{args.password}".encode()).decode()
        auth_header = f"Basic {token}"
    else:
        print("[slack] không có Jenkins credential, sẽ thử fetch unauthenticated.")

    try:
        suites = http_get_json(suites_url, auth_header)
    except Exception as e:
        print(f"[slack] không lấy được suites.json: {e}")
        return 0

    leaves = []
    collect_failed_leaves(suites, leaves)
    leaves_clean = [l for l in leaves if not is_synthetic_device_summary(l)]
    print(f"[slack] failed leaves total={len(leaves)} after-filter={len(leaves_clean)}")

    failed_tests = []
    for leaf in leaves_clean:
        uid = leaf.get("uid")
        if not uid:
            continue
        try:
            tc = http_get_json(test_case_url_tpl.format(uid=uid), auth_header)
        except Exception as e:
            print(f"[slack] skip test {uid}: {e}")
            continue

        steps_out = []
        ts = tc.get("testStage") or {}
        collect_failed_steps(ts.get("steps"), steps_out)

        atts_raw = gather_failed_attachments(tc)
        atts = [
            (attachment_label(a), f"{attachment_base}/{a.get('source', '').strip()}")
            for a in atts_raw if (a.get("source") or "").strip()
        ]

        failed_tests.append({
            "name": trim_test_name(tc.get("name") or leaf.get("name")),
            "status": (tc.get("status") or leaf.get("status") or "failed").lower(),
            "device": device_info(tc),
            "failed_steps": steps_out,
            "status_message": (tc.get("statusMessage") or "").strip(),
            "attachments": atts,
        })

    failed_tests.sort(key=lambda x: (x["device"].get("branch", ""), x["name"]))

    if not failed_tests and not args.notify_on_pass:
        print("[slack] không có test fail và notify-on-pass=off, bỏ qua.")
        return 0

    message = build_message(failed_tests, args.build_number, allure_report_url)
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
