"""Fetch current Google Trends RSS data for the portal, without following article URLs."""
from __future__ import annotations

import hashlib
import json
import math
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "data" / "trend_sources.json"
OUTPUT = ROOT / "data" / "trending.json"
USER_AGENT = "WathaiqNews/1.0 (Google Trends RSS monitor)"
MAX_BYTES = 1_500_000
MAX_AGE = timedelta(hours=48)
GENERIC_TREND_TITLES = {"a", "an", "and", "as", "at", "for", "in", "it", "of", "on", "or", "the", "to"}


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].lower()


def text(node: ET.Element, names: set[str]) -> str:
    for child in list(node):
        if local_name(child.tag) in names and child.text:
            return child.text.strip()
    return ""


def parse_date(value: str) -> datetime | None:
    if not value:
        return None
    try:
        result = parsedate_to_datetime(value)
    except (TypeError, ValueError, OverflowError):
        return None
    if result.tzinfo is None:
        result = result.replace(tzinfo=timezone.utc)
    return result.astimezone(timezone.utc)


def traffic_number(value: str) -> int:
    match = re.search(r"([\d,.]+)\s*([KMB])?", value.upper())
    if not match:
        return 0
    amount = float(match.group(1).replace(",", ""))
    multiplier = {None: 1, "K": 1_000, "M": 1_000_000, "B": 1_000_000_000}[match.group(2)]
    return int(amount * multiplier)


def parse_feed(payload: bytes, region: str, region_label: str, now: datetime) -> list[dict]:
    if len(payload) > MAX_BYTES:
        raise ValueError("feed exceeds size limit")
    prefix = payload[:4096].upper()
    if b"<!DOCTYPE" in prefix or b"<!ENTITY" in prefix:
        raise ValueError("DTD/entity declarations are not accepted")
    root = ET.fromstring(payload)
    output: list[dict] = []
    for node in root.iter():
        if local_name(node.tag) != "item":
            continue
        title = text(node, {"title"})
        if title.casefold() in GENERIC_TREND_TITLES:
            continue
        published = parse_date(text(node, {"pubdate"}))
        if not title or not published or published < now - MAX_AGE or published > now + timedelta(hours=1):
            continue
        traffic = text(node, {"approx_traffic"})
        related: list[dict] = []
        for child in node.iter():
            if local_name(child.tag) != "news_item":
                continue
            fields = {local_name(part.tag): (part.text or "").strip() for part in list(child)}
            item_title = fields.get("news_item_title", "")
            source = fields.get("news_item_source", "")
            snippet = fields.get("news_item_snippet", "")
            if item_title:
                related.append({"title": item_title[:240], "source": source[:100], "snippet": snippet[:500]})
        deduped = []
        seen = set()
        for item in related:
            key = (item["title"].casefold(), item["source"].casefold())
            if key not in seen:
                seen.add(key)
                deduped.append(item)
        if not deduped:
            continue
        volume = traffic_number(traffic)
        age_hours = max(0.0, (now - published).total_seconds() / 3600)
        source_count = len({item["source"] for item in deduped if item["source"]})
        score = round(math.log10(volume + 1) * 10 + max(0, 8 - age_hours) * 2 + min(source_count, 6) * 3, 1)
        digest = hashlib.sha256(f"{region}:{title.casefold()}".encode("utf-8")).hexdigest()[:18]
        output.append({
            "id": digest,
            "title": title[:240],
            "region": region,
            "region_label": region_label,
            "approx_traffic": traffic,
            "score": score,
            "published_at": published.isoformat().replace("+00:00", "Z"),
            "source": "Google Trends",
            "source_count": source_count,
            "news_items": deduped[:5],
            "alert": age_hours <= 0.5 and (volume >= 5_000 or source_count >= 3),
        })
    output.sort(key=lambda item: (item["score"], item["published_at"]), reverse=True)
    return output


def fetch(url: str) -> bytes:
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "trends.google.com" or parsed.path != "/trending/rss":
        raise ValueError("feed URL is outside the Google Trends RSS allowlist")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml, text/xml", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(req, timeout=25) as response:
        body = response.read(MAX_BYTES + 1)
    if len(body) > MAX_BYTES:
        raise ValueError("feed exceeds size limit")
    return body


def fetch_x_trends(token: str, woeid: str, now: datetime, previous_items: list[dict]) -> list[dict]:
    if not re.fullmatch(r"\d{1,12}", woeid):
        raise ValueError("X Trends WOEID must contain digits only")
    url = f"https://api.x.com/2/trends/by/woeid/{woeid}"
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json", "User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=25) as response:
        payload = response.read(MAX_BYTES + 1)
    if len(payload) > MAX_BYTES:
        raise ValueError("X Trends response exceeds size limit")
    parsed = json.loads(payload.decode("utf-8"))
    if not isinstance(parsed, dict) or not isinstance(parsed.get("data"), list):
        raise ValueError("X Trends returned an unexpected response")
    old_by_id = {item.get("id"): item for item in previous_items if isinstance(item, dict)}
    output: list[dict] = []
    for rank, trend in enumerate(parsed["data"][:30]):
        if not isinstance(trend, dict) or not isinstance(trend.get("trend_name"), str):
            continue
        title = trend["trend_name"].strip()[:240]
        if not title:
            continue
        digest = hashlib.sha256(f"x:{woeid}:{title.casefold()}".encode("utf-8")).hexdigest()[:18]
        old = old_by_id.get(digest, {})
        first_seen = str(old.get("first_seen_at") or old.get("published_at") or now.isoformat().replace("+00:00", "Z"))
        try:
            first_seen_dt = datetime.fromisoformat(first_seen.replace("Z", "+00:00")).astimezone(timezone.utc)
        except (TypeError, ValueError):
            first_seen_dt = now
            first_seen = now.isoformat().replace("+00:00", "Z")
        output.append({
            "id": digest,
            "title": title,
            "region": "egypt" if woeid == "23424802" else "world",
            "region_label": "X · مصر" if woeid == "23424802" else "X · عالمي",
            "approx_traffic": "",
            "score": float(max(1, 30 - rank)),
            "published_at": first_seen,
            "first_seen_at": first_seen,
            "source": "X Trends",
            "source_count": 0,
            "news_items": [],
            "alert": 0 <= (now - first_seen_dt).total_seconds() <= 30 * 60,
        })
    return output


def main() -> int:
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    now = datetime.now(timezone.utc)
    previous = json.loads(OUTPUT.read_text(encoding="utf-8")) if OUTPUT.exists() else {"schema_version": 1, "updated_at": None, "items": []}
    previous_items = previous.get("items", [])
    collected: list[dict] = []
    errors: list[str] = []
    for feed in config.get("feeds", []):
        if not feed.get("enabled", True):
            continue
        try:
            parsed = parse_feed(fetch(feed["url"]), feed["region"], feed["region_label"], now)
            collected.extend(parsed[:int(config.get("max_items_per_feed", 30))])
            print(f"{feed['region_label']}: {len(parsed)} current trends")
        except Exception as exc:
            errors.append(f"{feed.get('region_label', 'feed')}: {type(exc).__name__}")
    x_token = os.environ.get("X_TRENDS_BEARER_TOKEN", "").strip()
    x_enabled = os.environ.get("X_TRENDS_ENABLED", "").strip().lower() == "true"
    if x_enabled and x_token:
        try:
            woeid = os.environ.get("X_TRENDS_WOEID", "23424802").strip()
            x_items = fetch_x_trends(x_token, woeid, now, previous_items)
            collected.extend(x_items)
            print(f"X Trends: {len(x_items)} current topics")
        except Exception as exc:
            errors.append(f"X Trends: {type(exc).__name__}")
    elif x_enabled:
        print("X Trends enabled but no read token is configured; Google Trends remains active.")
    if not collected:
        if errors:
            print("Trend refresh failed; preserving previous trends: " + "; ".join(errors), file=sys.stderr)
        else:
            print("No recent news-linked trends; preserving previous trends.")
        return 0
    unique: dict[str, dict] = {}
    for item in collected:
        current = unique.get(item["id"])
        if current is None or item["score"] > current["score"]:
            unique[item["id"]] = item
    items = sorted(unique.values(), key=lambda item: (item["score"], item["published_at"]), reverse=True)[:60]
    old_items = previous_items
    if items == old_items:
        print("Trend list unchanged; no file update is needed.")
        return 0
    data = {"schema_version": 1, "updated_at": now.isoformat().replace("+00:00", "Z"), "items": items}
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(items)} current trend items.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, urllib.error.URLError, ET.ParseError) as exc:
        print(f"Trend update stopped safely: {type(exc).__name__}", file=sys.stderr)
        raise SystemExit(1)
