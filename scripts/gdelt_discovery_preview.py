"""Collect a local, review-only preview of news discovered through GDELT DOC 2.0.

This tool deliberately does not publish articles, copy article text, or reuse
GDELT's social-image URLs. It stores only headlines, source links and discovery
metadata so a separate editorial and image-rights step can review candidates.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

API_URL = "https://api.gdeltproject.org/api/v2/doc/doc"
USER_AGENT = "WorldNewsGDELTPreview/1.0 (public editorial research)"
CATEGORIES = {
    "world": '("world" OR "international" OR "Middle East" OR "Egypt")',
    "economy": '("economy" OR "markets" OR "inflation" OR "trade")',
    "technology": '("technology" OR "artificial intelligence" OR "cybersecurity")',
    "sports": '("football" OR "sports" OR "champions league")',
}


def clean_text(value: object, limit: int = 500) -> str:
    value = html.unescape(re.sub(r"<[^>]*>", " ", str(value or "")))
    return re.sub(r"\s+", " ", value).strip()[:limit]


def safe_article_url(value: object) -> str:
    candidate = clean_text(value, 2000)
    try:
        parsed = urllib.parse.urlsplit(candidate)
    except ValueError:
        return ""
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        return ""
    host = parsed.hostname.lower()
    if host in {"localhost", "127.0.0.1"} or host.endswith(".localhost"):
        return ""
    query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    query = [(key, val) for key, val in query if not key.lower().startswith("utm_")
             and key.lower() not in {"fbclid", "gclid"}]
    return urllib.parse.urlunsplit((
        "https", parsed.netloc, parsed.path or "/", urllib.parse.urlencode(query), ""
    ))


def parse_seen_date(value: object) -> str:
    raw = clean_text(value, 40)
    if not raw:
        return ""
    try:
        parsed = datetime.strptime(raw, "%Y%m%dT%H%M%SZ").replace(tzinfo=timezone.utc)
        return parsed.isoformat().replace("+00:00", "Z")
    except ValueError:
        return ""


def normalize_article(article: dict, category: str) -> dict | None:
    if not isinstance(article, dict):
        return None
    title = clean_text(article.get("title"), 500)
    url = safe_article_url(article.get("url"))
    domain = clean_text(article.get("domain"), 200).lower()
    if len(title) < 12 or not url:
        return None
    if not domain:
        domain = urllib.parse.urlsplit(url).hostname or ""
    return {
        "title": title,
        "url": url,
        "source_domain": domain,
        "seen_at": parse_seen_date(article.get("seendate")),
        "language": clean_text(article.get("language"), 20),
        "source_country": clean_text(article.get("sourcecountry"), 40),
        "categories": [category],
        "discovery_source": "GDELT DOC 2.0",
    }


def merge_candidates(candidates: list[dict]) -> list[dict]:
    merged: dict[str, dict] = {}
    for item in candidates:
        url = item["url"]
        if url not in merged:
            merged[url] = item
            continue
        existing = merged[url]
        existing["categories"] = sorted(set(existing["categories"]) | set(item["categories"]))
        if item.get("seen_at") and item["seen_at"] > existing.get("seen_at", ""):
            existing["seen_at"] = item["seen_at"]
        for field in ("language", "source_country"):
            if not existing.get(field) and item.get(field):
                existing[field] = item[field]
    return sorted(merged.values(), key=lambda item: item.get("seen_at", ""), reverse=True)


def fetch_category(category: str, query: str, timespan: str, maxrecords: int) -> list[dict]:
    params = {
        "query": query,
        "mode": "ArtList",
        "format": "json",
        "maxrecords": str(maxrecords),
        "timespan": timespan,
        "sort": "DateDesc",
    }
    url = API_URL + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept": "application/json",
    })
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                if response.status != 200:
                    raise RuntimeError(f"GDELT returned HTTP {response.status}")
                content_type = response.headers.get("Content-Type", "").lower()
                payload = response.read(4_000_001)
            break
        except urllib.error.HTTPError as exc:
            if exc.code == 429 and attempt < 2:
                delay = 30 * (attempt + 1)
                print(f"GDELT rate limited the request; retrying in {delay}s", file=sys.stderr)
                time.sleep(delay)
                continue
            if exc.code == 429:
                raise RuntimeError("GDELT rate limit persisted after two retries") from exc
            raise RuntimeError(f"GDELT returned HTTP {exc.code}") from exc
    if len(payload) > 4_000_000:
        raise RuntimeError("GDELT response exceeded the 4 MB safety limit")
    if content_type and "json" not in content_type:
        raise RuntimeError("GDELT returned a non-JSON response")
    try:
        data = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("GDELT response was not valid JSON") from exc
    articles = data.get("articles") if isinstance(data, dict) else None
    if not isinstance(articles, list):
        keys = sorted(data.keys()) if isinstance(data, dict) else []
        detail = clean_text(data.get("error") or data.get("message") or "", 240) if isinstance(data, dict) else ""
        raise RuntimeError(
            "GDELT response did not contain an articles list "
            f"(keys={keys}; detail={detail or 'no provider error message'})"
        )
    result = []
    for article in articles:
        normalized = normalize_article(article, category)
        if normalized:
            result.append(normalized)
    return result


def collect(timespan: str = "24h", maxrecords: int = 50, pause_seconds: float = 6.0) -> list[dict]:
    candidates: list[dict] = []
    for index, (category, query) in enumerate(CATEGORIES.items()):
        if index:
            time.sleep(max(0.0, pause_seconds))
        candidates.extend(fetch_category(category, query, timespan, maxrecords))
    return merge_candidates(candidates)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timespan", default="24h", help="GDELT time window, e.g. 6h, 24h, 3d")
    parser.add_argument("--maxrecords", type=int, default=50, help="Maximum records per category (1-250)")
    parser.add_argument("--output", default="data/gdelt_candidates.json", help="Local JSON output path")
    parser.add_argument("--pause", type=float, default=6.0, help="Seconds between API calls")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+(?:min|h|d|w|m)", args.timespan):
        parser.error("--timespan must look like 30min, 6h, 1d, or 1w")
    if not 1 <= args.maxrecords <= 250:
        parser.error("--maxrecords must be between 1 and 250")
    try:
        items = collect(args.timespan, args.maxrecords, args.pause)
    except (OSError, RuntimeError, TimeoutError) as exc:
        print(f"GDELT preview stopped safely: {exc}", file=sys.stderr)
        return 2
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    document = {
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "discovery_source": "GDELT DOC 2.0",
        "timespan": args.timespan,
        "review_only": True,
        "published": False,
        "image_urls_collected": False,
        "ai_rewriting_enabled": False,
        "items": items,
    }
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(items)} review-only candidates to {output}. Nothing was published.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
