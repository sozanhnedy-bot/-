"""Build a public, read-only trend radar from Google Trends RSS and configured news RSS.

The radar is a discovery aid, not proof of a story or a promise of reach.
It never publishes to social accounts.
"""
from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode

from update_news import clean_text, fetch_feed, parse_feed

ROOT = Path(__file__).resolve().parents[1]
SOURCES_PATH = ROOT / "data" / "news_sources.json"
OUTPUT_PATH = ROOT / "data" / "trends.json"
USER_AGENT = "LokaNewsroomTrendRadar/1.0"
TREND_REGIONS = (("مصر", "EG"), ("السعودية", "SA"), ("الإمارات", "AE"), ("الولايات المتحدة", "US"))
MAX_TREND_BYTES = 1_000_000


def fetch_google_trends(geo: str, region_name: str, now: datetime | None = None) -> list[dict]:
    url = "https://trends.google.com/trending/rss?" + urlencode({"geo": geo})
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml"})
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = response.read(MAX_TREND_BYTES + 1)
    if len(payload) > MAX_TREND_BYTES:
        raise ValueError("Google Trends feed exceeded size limit")
    items = parse_feed(payload, region_name, "Google Trends", now=now, feed_source_url=url)
    for rank, item in enumerate(items, start=1):
        item["trend_rank"] = rank
        item["signal"] = "google_trends_rss"
    return items


def normalize_topic(title: str) -> str:
    value = clean_text(title).casefold()
    value = re.sub(r"https?://\S+", " ", value)
    value = re.sub(r"[^\w\s]", " ", value, flags=re.UNICODE)
    return " ".join(value.split())[:180]


def score_trend(item: dict, rank: int | None = None, corroborations: int = 0) -> int:
    """Transparent heuristic score; not a search-volume or view prediction."""
    rank = rank or item.get("trend_rank") or 20
    rank_score = max(0, 45 - (int(rank) - 1) * 3)
    corroboration_score = min(30, max(0, corroborations) * 10)
    recency_score = 15 if item.get("published_at") else 5
    return min(100, rank_score + corroboration_score + recency_score)


def build_radar(trend_items: list[dict], news_items: list[dict], now: datetime | None = None) -> dict:
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    news_titles = [normalize_topic(item.get("title", "")) for item in news_items]
    results = []
    seen = set()
    for item in trend_items:
        title = clean_text(item.get("title", ""))
        key = normalize_topic(title)
        if not key or key in seen:
            continue
        seen.add(key)
        matches = sum(1 for news_title in news_titles if key in news_title or news_title in key)
        rank = int(item.get("trend_rank") or len(results) + 1)
        score = score_trend(item, rank, matches)
        results.append({
            "title": title,
            "region": item.get("region", "unknown"),
            "rank": rank,
            "score": score,
            "priority": "strong" if score >= 65 else "medium" if score >= 40 else "low",
            "signal": item.get("signal", "unknown"),
            "corroborating_news_items": matches,
            "source_name": item.get("source_name", "Google Trends"),
            "source_url": item.get("source_url", ""),
            "url": item.get("url", ""),
            "published_at": item.get("published_at"),
            "assessment_note": "مؤشر ترتيب وظهور في مصدر الترند مع تقاطع عناوين الأخبار؛ ليس حجم بحث رقميًا ولا توقعًا للمشاهدات.",
        })
    results.sort(key=lambda x: (x["score"], -x["rank"]), reverse=True)
    return {
        "schema_version": 1,
        "generated_at": now.isoformat().replace("+00:00", "Z"),
        "disclaimer": "النتائج مؤشرات رصد عامة وليست بيانات API رسمية لكل المنصات أو ضمانًا للوصول والمشاهدات.",
        "regions_checked": [],
        "items": results[:50],
    }


def main() -> int:
    trend_items: list[dict] = []
    news_items: list[dict] = []
    warnings: list[str] = []
    checked: list[str] = []

    for region_name, geo in TREND_REGIONS:
        try:
            items = fetch_google_trends(geo, region_name)
            trend_items.extend(items)
            checked.append(region_name)
            print(f"Google Trends {region_name}: {len(items)} entries")
        except Exception as exc:
            warnings.append(f"{region_name}: {type(exc).__name__}")
            print(f"Google Trends {region_name} unavailable: {type(exc).__name__}", file=sys.stderr)

    try:
        config = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))
        for feed in config.get("feeds", []):
            if not feed.get("enabled", True):
                continue
            try:
                news_items.extend(fetch_feed(feed["url"], feed["region"], feed["name"], feed_source_url=feed.get("source_url", ""))[:8])
            except Exception as exc:
                print(f"News signal {feed.get('name', 'RSS')} unavailable: {type(exc).__name__}", file=sys.stderr)
    except Exception as exc:
        warnings.append(f"news_sources: {type(exc).__name__}")

    if not trend_items:
        print("No Google Trends data was retrieved; keeping the previous radar file.", file=sys.stderr)
        return 1

    radar = build_radar(trend_items, news_items)
    radar["regions_checked"] = checked
    radar["warnings"] = warnings
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(radar, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(radar['items'])} trend candidates to {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
