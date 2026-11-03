"""Publish one bounded daily editorial report to authorized X/Facebook accounts."""
from __future__ import annotations

import base64
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SITE_URL = "https://sozanhnedy-bot.github.io/-"
REPORTS_FILE = ROOT / "data" / "reports.json"
STATE_FILE = ROOT / "data" / "social-publish-state.json"
REPORT_ID_RE = re.compile(r"^[a-f0-9]{20,40}$")
MAX_X_IMAGE_BYTES = 5 * 1024 * 1024


def short_code(report_id: str, report_ids: list[str]) -> str:
    length = 6
    while length < len(report_id) and any(
        other != report_id and other.startswith(report_id[:length]) for other in report_ids
    ):
        length += 2
    return report_id[:length]


def report_content(report: dict, report_ids: list[str], today: str) -> dict:
    report_id = str(report.get("id") or "")
    if not REPORT_ID_RE.fullmatch(report_id):
        raise ValueError("invalid report id")
    image = "assets/brand-cover.jpg"
    caption = str(report.get("social_caption") or report.get("title") or "").strip()
    caption = f"ثريدز وثائقي | {caption}" if caption else "ثريدز وثائقي"
    caption += "\n(صورة المنشور غلاف القناة؛ تفاصيل الخبر وصورته داخل التقرير.)"
    link = f"{SITE_URL}/r/{short_code(report_id, report_ids)}.html"
    return {"key": f"report:{report_id}", "report_id": report_id, "caption": caption, "url": link, "image": image}


def choose_content(platform_state: dict, reports: list[dict], today: str) -> dict | None:
    ids = [str(item.get("id") or "") for item in reports if isinstance(item, dict)]
    posted = set(platform_state.get("posted_report_ids", []))
    for report in reports:
        if not isinstance(report, dict):
            continue
        report_id = str(report.get("id") or "")
        if REPORT_ID_RE.fullmatch(report_id) and report_id not in posted:
            return report_content(report, ids, today)
    return None


def social_text(content: dict) -> str:
    suffix = " " + content["url"]
    available = max(40, 275 - len(suffix))
    caption = content["caption"].strip()
    if len(caption) > available:
        caption = caption[: available - 1].rstrip() + "…"
    return caption + suffix


def request_json(url: str, body: dict, *, token: str, method: str = "POST") -> dict:
    encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=encoded,
        method=method,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            payload = response.read(1_000_000)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"social API returned HTTP {exc.code}") from None
    parsed = json.loads(payload.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise RuntimeError("social API returned an unexpected response")
    return parsed


def post_to_x(content: dict, token: str) -> str:
    image_path = ROOT / content["image"]
    if not image_path.is_file():
        raise RuntimeError("social image is missing")
    image_bytes = image_path.read_bytes()
    if len(image_bytes) > MAX_X_IMAGE_BYTES:
        raise RuntimeError("X image exceeds 5 MB")
    media_body = {
        "media": base64.b64encode(image_bytes).decode("ascii"),
        "media_category": "tweet_image",
    }
    media_response = request_json("https://api.x.com/2/media/upload", media_body, token=token)
    media_id = str((media_response.get("data") or {}).get("id") or "")
    if not media_id:
        raise RuntimeError("X media upload returned no media id")
    post_body = {"text": social_text(content), "media": {"media_ids": [media_id]}}
    post_response = request_json("https://api.x.com/2/tweets", post_body, token=token)
    post_id = str((post_response.get("data") or {}).get("id") or "")
    if not post_id:
        raise RuntimeError("X post response contained no id")
    return post_id


def post_to_facebook(content: dict, page_id: str, token: str, version: str) -> str:
    if not re.fullmatch(r"v\d+\.0", version):
        raise ValueError("Meta Graph API version must look like v26.0")
    url = f"https://graph.facebook.com/{version}/{urllib.parse.quote(page_id, safe='')}/feed"
    body = {"message": content["caption"], "link": content["url"], "access_token": token}
    encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=encoded, method="POST", headers={"Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            payload = response.read(1_000_000)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"Meta API returned HTTP {exc.code}") from None
    parsed = json.loads(payload.decode("utf-8"))
    post_id = str(parsed.get("id") or "") if isinstance(parsed, dict) else ""
    if not post_id:
        raise RuntimeError("Meta post response contained no id")
    return post_id


def main() -> int:
    if os.environ.get("SOCIAL_AUTOPUBLISH", "").strip().lower() != "true":
        print("Social publisher is disabled; no external API calls were made.")
        return 0
    reports_data = json.loads(REPORTS_FILE.read_text(encoding="utf-8"))
    reports = reports_data.get("items", []) if isinstance(reports_data, dict) else []
    state = json.loads(STATE_FILE.read_text(encoding="utf-8")) if STATE_FILE.exists() else {"schema_version": 1, "platforms": {}}
    platforms = state.setdefault("platforms", {})
    today = datetime.now(timezone.utc).date().isoformat()
    errors: list[str] = []
    changed = False

    x_token = os.environ.get("X_USER_ACCESS_TOKEN", "").strip()
    if x_token:
        x_state = platforms.setdefault("x", {"posted_report_ids": []})
        if x_state.get("date") != today:
            content = choose_content(x_state, reports, today)
            if content:
                try:
                    post_id = post_to_x(content, x_token)
                    x_state.update({"date": today, "content_key": content["key"], "post_id": post_id})
                    if content.get("report_id"):
                        posted_ids = list(x_state.get("posted_report_ids", []))
                        if content["report_id"] not in posted_ids:
                            posted_ids.append(content["report_id"])
                        x_state["posted_report_ids"] = posted_ids[-500:]
                    changed = True
                    print("X daily publication succeeded.")
                except Exception as exc:
                    errors.append(f"X: {type(exc).__name__}")
        else:
            print("X already has a post recorded for today; skipped.")
    else:
        print("X publishing is not configured; skipped.")

    page_id = os.environ.get("FB_PAGE_ID", "").strip()
    fb_token = os.environ.get("FB_PAGE_ACCESS_TOKEN", "").strip()
    if page_id and fb_token:
        fb_state = platforms.setdefault("facebook", {"posted_report_ids": []})
        if fb_state.get("date") != today:
            content = choose_content(fb_state, reports, today)
            if content:
                version = os.environ.get("META_GRAPH_VERSION", "v26.0").strip()
                try:
                    post_id = post_to_facebook(content, page_id, fb_token, version)
                    fb_state.update({"date": today, "content_key": content["key"], "post_id": post_id})
                    if content.get("report_id"):
                        posted_ids = list(fb_state.get("posted_report_ids", []))
                        if content["report_id"] not in posted_ids:
                            posted_ids.append(content["report_id"])
                        fb_state["posted_report_ids"] = posted_ids[-500:]
                    changed = True
                    print("Facebook Page daily publication succeeded.")
                except Exception as exc:
                    errors.append(f"Facebook: {type(exc).__name__}")
        else:
            print("Facebook already has a post recorded for today; skipped.")
    else:
        print("Facebook Page publishing is not configured; skipped.")

    if changed:
        state["updated_at"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        STATE_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if errors:
        print("One or more social platforms failed: " + "; ".join(errors), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
