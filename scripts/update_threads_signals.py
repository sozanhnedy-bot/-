"""Read-only Threads public keyword search for top current trend topics.

Requires THREADS_USER_ACCESS_TOKEN with threads_basic and threads_keyword_search.
Before Meta approves threads_keyword_search, public-post searches may be limited to the authorized user's own posts.
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRENDS = ROOT / "data" / "trending.json"
OUTPUT = ROOT / "data" / "threads_signals.json"
API = "https://graph.threads.com/v1.0/keyword_search"
UA = "ThreadsDocumentaryNews/1.0 (read-only trend signals)"
MAX_QUERIES_PER_RUN = 3
MAX_RESULTS = 25


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def search(token: str, query: str) -> list[dict]:
    params = urllib.parse.urlencode({
        "q": query,
        "search_type": "RECENT",
        "fields": "id,timestamp",
        "limit": str(MAX_RESULTS),
        "access_token": token,
    })
    request = urllib.request.Request(f"{API}?{params}", headers={"User-Agent": UA, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            payload = response.read(1_000_000)
    except urllib.error.HTTPError as exc:
        # Do not print the URL or API body: either can contain token-sensitive information.
        raise RuntimeError(f"Threads API HTTP {exc.code}") from None
    data = json.loads(payload.decode("utf-8"))
    posts = data.get("data", [])
    if not isinstance(posts, list):
        raise ValueError("unexpected Threads API response")
    return posts


def main() -> int:
    token = os.environ.get("THREADS_USER_ACCESS_TOKEN", "").strip()
    previous = json.loads(OUTPUT.read_text(encoding="utf-8")) if OUTPUT.exists() else {"schema_version": 1, "updated_at": None, "items": []}
    if not token:
        print("Threads keyword search skipped: app authorization is not configured.")
        return 0
    previous_by_trend = {
        item.get("trend_id"): item
        for item in previous.get("items", [])
        if isinstance(item, dict) and item.get("trend_id")
    }
    trends = json.loads(TRENDS.read_text(encoding="utf-8")).get("items", []) if TRENDS.exists() else []
    topics = []
    seen = set()
    for trend in trends:
        title = " ".join(str(trend.get("title", "")).split())
        if title and title.casefold() not in seen:
            seen.add(title.casefold())
            topics.append(trend)
        if len(topics) >= MAX_QUERIES_PER_RUN:
            break
    if not topics:
        print("No current trend topics to search on Threads.")
        return 0
    items = []
    for trend in topics:
        try:
            posts = search(token, trend["title"])
            # The endpoint provides RECENT matches; keep only coarse aggregate counts.
            trend_id = trend.get("id", "")
            recent_count = len(posts)
            old = previous_by_trend.get(trend_id, {})
            measured_at = old.get("measured_at") if old.get("recent_public_posts") == recent_count else iso_now()
            items.append({
                "trend_id": trend_id,
                "title": trend["title"],
                "region": trend.get("region", "world"),
                "region_label": trend.get("region_label", ""),
                "recent_public_posts": recent_count,
                "measured_at": measured_at or iso_now(),
                "source": "Threads keyword search",
            })
        except (RuntimeError, ValueError, urllib.error.URLError) as exc:
            print(f"Threads search unavailable for a trend: {type(exc).__name__}", file=sys.stderr)
            # Do not replace a valid previous snapshot with a partial/empty one.
            return 0
    data = {"schema_version": 1, "updated_at": iso_now(), "items": items}
    if data["items"] != previous.get("items", []):
        OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Updated aggregate Threads signals for {len(items)} topics.")
    else:
        print("Threads signals unchanged.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
