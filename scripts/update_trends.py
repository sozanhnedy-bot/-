"""Fetch Google Trends RSS radar snapshots for Egypt, Saudi Arabia and UAE."""
from __future__ import annotations

import hashlib
import json
import re
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data" / "trends.json"
FEEDS = {"EG": "مصر", "SA": "السعودية", "AE": "الإمارات"}
FEED_URL = "https://trends.google.com/trending/rss?geo={geo}"
MAX_BYTES = 2_000_000
MAX_ITEMS_PER_REGION = 50
MAX_AGE = timedelta(hours=36)
USER_AGENT = "WorldNewsRadar/1.0 (+https://sozanhnedy-bot.github.io/-/)"


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].lower()


def child_text(node: ET.Element, name: str) -> str:
    for child in list(node):
        if local_name(child.tag) == name.lower():
            return (child.text or "").strip()
    return ""


def safe_https_url(value: str) -> str:
    try:
        parsed = urlsplit(value.strip())
    except ValueError:
        return ""
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        return ""
    return value.strip()[:2000]


def parse_date(value: str) -> datetime | None:
    if not value:
        return None
    try:
        parsed = parsedate_to_datetime(value)
    except (TypeError, ValueError, OverflowError):
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def fetch_feed(geo: str, now: datetime) -> list[dict]:
    request = urllib.request.Request(
        FEED_URL.format(geo=geo),
        headers={"User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml, text/xml"},
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            payload = response.read(MAX_BYTES + 1)
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"{geo} feed fetch failed: {type(exc).__name__}") from None
    if len(payload) > MAX_BYTES:
        raise ValueError(f"{geo} feed exceeded the size limit")
    prefix = payload[:4096].upper()
    if b"<!DOCTYPE" in prefix or b"<!ENTITY" in prefix:
        raise ValueError(f"{geo} feed contains a forbidden DTD/entity declaration")
    root = ET.fromstring(payload)
    cutoff = now - MAX_AGE
    items: list[dict] = []
    for node in root.iter():
        if local_name(node.tag) != "item":
            continue
        title = child_text(node, "title")
        published = parse_date(child_text(node, "pubdate"))
        if not title or not published or published < cutoff or published > now + timedelta(hours=2):
            continue
        traffic = ""
        news_items = []
        for child in list(node):
            tag = local_name(child.tag)
            if tag == "approx_traffic":
                traffic = (child.text or "").strip()
            elif tag == "news_item":
                news_title = child_text(child, "news_item_title")
                news_url = safe_https_url(child_text(child, "news_item_url"))
                news_source = child_text(child, "news_item_source")
                news_image = safe_https_url(child_text(child, "news_item_picture"))
                if news_title and news_url:
                    news_items.append({
                        "title": news_title[:500],
                        "url": news_url,
                        "source": news_source[:200],
                        "image_url": news_image,
                    })
        identity = f"{geo}|{title.casefold()}|{published.isoformat()}"
        items.append({
            "id": hashlib.sha256(identity.encode("utf-8")).hexdigest()[:24],
            "query": title[:500],
            "region_code": geo,
            "region": FEEDS[geo],
            "approx_traffic": traffic[:80],
            "published_at": published.isoformat().replace("+00:00", "Z"),
            "news_items": news_items[:5],
            "source": "Google Trends RSS",
            "source_url": FEED_URL.format(geo=geo),
            "is_trend_signal_only": True,
        })
    items.sort(key=lambda item: item["published_at"], reverse=True)
    return items[:MAX_ITEMS_PER_REGION]


def main() -> int:
    now = datetime.now(timezone.utc)
    result = {
        "schema_version": 1,
        "updated_at": now.isoformat().replace("+00:00", "Z"),
        "note": "Trends indicate search interest, not proof that a news claim is true.",
        "regions": {},
    }
    errors = []
    for geo in FEEDS:
        try:
            result["regions"][geo] = fetch_feed(geo, now)
            print(f"{geo}: {len(result['regions'][geo])} recent trends")
        except Exception as exc:
            errors.append(f"{geo}: {type(exc).__name__}")
            result["regions"][geo] = []
    if not any(result["regions"].values()):
        raise RuntimeError("No regional trends were fetched; refusing to overwrite the last good snapshot.")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(OUTPUT)
    if errors:
        print("Partial feed failures: " + ", ".join(errors))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
