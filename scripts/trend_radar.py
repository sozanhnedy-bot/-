"""Collect public X trend signals and optional authorized Facebook Page posts.

X trends use Trends24's public country page as a third-party signal, not the
official X API. Facebook collection is opt-in and only works with credentials
and permissions the Page owner/app is authorized to use.
"""
from __future__ import annotations

import json
import os
import re
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

try:
    from .update_news import clean_text, fetch_feed, parse_feed
except ImportError:
    from update_news import clean_text, fetch_feed, parse_feed

ROOT = Path(__file__).resolve().parents[1]
SOURCES_PATH = ROOT / "data" / "news_sources.json"
OUTPUT_PATH = ROOT / "data" / "trends.json"
USER_AGENT = "Mozilla/5.0 (compatible; LokaNewsroomTrendRadar/1.0)"
MAX_BYTES = 2_000_000
GOOGLE_TREND_REGIONS = (("مصر", "EG"), ("السعودية", "SA"), ("الإمارات", "AE"), ("العالم", "US"))
X_TREND_PAGES = (
    ("مصر", "https://trends24.in/egypt/"),
    ("السعودية", "https://trends24.in/saudi-arabia/"),
    ("العالم", "https://trends24.in/worldwide/"),
)


class TrendLinkParser(HTMLParser):
    """Extract topic links from Trends24; ignore unrelated navigation links."""
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.in_topic = False
        self.depth = 0
        self.current: list[str] = []
        self.items: list[str] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        attrs = dict(attrs)
        href = attrs.get("href", "")
        classes = attrs.get("class", "").lower()
        is_topic = "/topic/" in href or "trend-name" in classes or "trend-link" in classes
        if tag == "a" and is_topic and not self.in_topic:
            self.in_topic = True
            self.depth = 1
            self.current = []
        elif self.in_topic:
            self.depth += 1

    def handle_data(self, data: str) -> None:
        if self.in_topic:
            self.current.append(data)

    def handle_endtag(self, tag: str) -> None:
        if not self.in_topic:
            return
        self.depth -= 1
        if self.depth <= 0:
            value = clean_text(" ".join(self.current))
            if value and value not in self.items:
                self.items.append(value)
            self.in_topic = False
            self.current = []


def _http_get(url: str, headers: dict | None = None) -> bytes:
    request_headers = {"User-Agent": USER_AGENT}
    request_headers.update(headers or {})
    request = urllib.request.Request(url, headers=request_headers)
    with urllib.request.urlopen(request, timeout=25) as response:
        payload = response.read(MAX_BYTES + 1)
    if len(payload) > MAX_BYTES:
        raise ValueError("remote response exceeded size limit")
    return payload


def fetch_google_trends(region: str, geo: str) -> list[dict]:
    url = "https://trends.google.com/trending/rss?" + urllib.parse.urlencode({"geo": geo})
    payload = _http_get(url, {"Accept": "application/rss+xml, application/xml"})
    parsed = parse_feed(payload, region, "Google Trends", feed_source_url=url)
    return [{
        **item,
        "trend_rank": rank,
        "signal": "google_trends_rss",
        "source_name": "Google Trends",
    } for rank, item in enumerate(parsed, start=1)]


def fetch_x_trends(region: str, url: str) -> list[dict]:
    payload = _http_get(url, {"Accept": "text/html"})
    parser = TrendLinkParser()
    parser.feed(payload.decode("utf-8", errors="replace"))
    if not parser.items:
        raise ValueError("Trends24 page layout changed or no topic links were found")
    captured = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    return [{
        "title": title,
        "region": region,
        "trend_rank": rank,
        "source_name": "Trends24 (public X trends)",
        "source_url": url,
        "url": url,
        "signal": "x_trends24_public",
        "captured_at": captured,
    } for rank, title in enumerate(parser.items[:50], start=1)]


def fetch_youtube_trending(region: str = "EG") -> list[dict]:
    api_key = os.environ.get("YOUTUBE_DATA_API_KEY", "").strip()
    if not api_key:
        return []
    params = urllib.parse.urlencode({
        "part": "snippet,statistics",
        "chart": "mostPopular",
        "regionCode": region,
        "maxResults": "30",
        "key": api_key,
    })
    url = "https://www.googleapis.com/youtube/v3/videos?" + params
    payload = json.loads(_http_get(url, {"Accept": "application/json"}).decode("utf-8"))
    results = []
    for rank, video in enumerate(payload.get("items", []), start=1):
        snippet = video.get("snippet", {})
        video_id = video.get("id", "")
        if not video_id:
            continue
        results.append({
            "title": clean_text(snippet.get("title", "")),
            "region": region,
            "trend_rank": rank,
            "source_name": "YouTube Data API — mostPopular",
            "source_url": f"https://www.youtube.com/watch?v={video_id}",
            "url": f"https://www.youtube.com/watch?v={video_id}",
            "signal": "youtube_most_popular_api",
            "published_at": snippet.get("publishedAt"),
            "views": int(video.get("statistics", {}).get("viewCount", 0) or 0),
        })
    return results


def fetch_authorized_facebook_page() -> list[dict]:
    """Fetch posts only from a Page the configured token is allowed to access."""
    page_id = os.environ.get("FACEBOOK_PAGE_ID", "").strip()
    token = os.environ.get("FACEBOOK_PAGE_ACCESS_TOKEN", "").strip()
    version = os.environ.get("FACEBOOK_GRAPH_API_VERSION", "").strip()
    if not (page_id and token and version):
        return []
    if not re.fullmatch(r"v[0-9]+\.[0-9]+", version):
        raise ValueError("FACEBOOK_GRAPH_API_VERSION must be an explicit version such as vXX.0")
    query = urllib.parse.urlencode({
        "fields": "message,permalink_url,created_time,attachments{media_type,media,url,target}",
        "limit": "50",
        "access_token": token,
    })
    url = f"https://graph.facebook.com/{version}/{urllib.parse.quote(page_id, safe='')}/posts?{query}"
    payload = json.loads(_http_get(url, {"Accept": "application/json"}).decode("utf-8"))
    posts = []
    for post in payload.get("data", []):
        message = clean_text(post.get("message", ""))
        permalink = post.get("permalink_url", "")
        if not message or not permalink:
            continue
        attachments = post.get("attachments", {}).get("data", [])
        media = []
        for attachment in attachments:
            media.append({
                "media_type": attachment.get("media_type", ""),
                "url": attachment.get("url", ""),
                "target": attachment.get("target", {}).get("url", ""),
            })
        posts.append({
            "title": message[:240],
            "excerpt": message[:1200],
            "region": "facebook_page",
            "source_name": "Authorized Facebook Page",
            "source_url": permalink,
            "url": permalink,
            "published_at": post.get("created_time"),
            "signal": "facebook_graph_page_post",
            "media": media,
            "rights_status": "review_required",
        })
    return posts


def normalize_topic(title: str) -> str:
    value = clean_text(title).casefold()
    value = re.sub(r"https?://\S+", " ", value)
    value = re.sub(r"[^\w\s]", " ", value, flags=re.UNICODE)
    return " ".join(value.split())[:180]


def score_trend(item: dict, rank: int | None = None, corroborations: int = 0, persistence: int = 0) -> int:
    """A transparent priority heuristic, never a search-volume or view estimate."""
    rank = rank or item.get("trend_rank") or 50
    rank_score = max(0, 50 - (int(rank) - 1))
    corroboration_score = min(25, max(0, corroborations) * 8)
    persistence_score = min(15, max(0, persistence) * 3)
    source_score = 10 if item.get("signal") in {"x_trends24_public", "youtube_most_popular_api"} else 5
    return min(100, rank_score + corroboration_score + persistence_score + source_score)


def build_radar(items: list[dict], news_items: list[dict], now: datetime | None = None) -> dict:
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    news_titles = [normalize_topic(item.get("title", "")) for item in news_items]
    seen: set[tuple[str, str]] = set()
    results = []
    for item in items:
        title = clean_text(item.get("title", ""))
        key = normalize_topic(title)
        source = item.get("signal", "unknown")
        identity = (source, key)
        if not key or identity in seen:
            continue
        seen.add(identity)
        matches = sum(1 for news_title in news_titles if key in news_title or news_title in key)
        rank = int(item.get("trend_rank") or 50)
        score = score_trend(item, rank, matches)
        results.append({
            "title": title,
            "region": item.get("region", "unknown"),
            "rank": rank,
            "score": score,
            "priority": "strong" if score >= 65 else "medium" if score >= 40 else "low",
            "signal": source,
            "corroborating_news_items": matches,
            "source_name": item.get("source_name", "Public social signal"),
            "source_url": item.get("source_url", ""),
            "url": item.get("url", ""),
            "published_at": item.get("published_at"),
            "captured_at": item.get("captured_at"),
            "media": item.get("media", []),
            "rights_status": item.get("rights_status", "not_assessed"),
            "assessment_note": "ترتيب الترند ومقارنته بعناوين الأخبار؛ ليس عدد تغريدات أو حجم بحث أو توقع مشاهدات.",
        })
    results.sort(key=lambda x: (x["score"], -x["rank"]), reverse=True)
    return {
        "schema_version": 3,
        "generated_at": now.isoformat().replace("+00:00", "Z"),
        "disclaimer": "ترندات X من مصدر عام خارجي، وGoogle Trends مؤشر اهتمام بحثي، ومنشورات فيسبوك من الصفحات المصرح بها فقط. لا تمثل هذه البيانات وصولًا كاملًا أو رسميًا لكل المنصات.",
        "sources_checked": [],\n        "platform_status": {"x": "public_third_party_signal", "google_trends": "rss_enabled", "facebook": "authorized_page_token_optional", "youtube": "official_api_key_optional", "tiktok": "not_connected_requires_approved_access"},
        "items": results[:100],
    }


def main() -> int:
    social_items: list[dict] = []
    news_items: list[dict] = []
    warnings: list[str] = []
    checked: list[str] = []

    for region, url in X_TREND_PAGES:
        try:
            found = fetch_x_trends(region, url)
            social_items.extend(found)
            checked.append(f"X Trends24: {region}")
            print(f"X trends {region}: {len(found)} topics")
        except Exception as exc:
            warnings.append(f"X {region}: {type(exc).__name__}")
            print(f"X trends {region} unavailable: {type(exc).__name__}", file=sys.stderr)

    try:
        fb_posts = fetch_authorized_facebook_page()
        social_items.extend(fb_posts)
        if fb_posts:
            checked.append("Facebook Page (authorized Graph API)")
            print(f"Facebook authorized Page: {len(fb_posts)} posts")
        else:
            print("Facebook Page connector not configured; no Facebook posts collected.")
    except Exception as exc:
        warnings.append(f"Facebook: {type(exc).__name__}")
        print(f"Facebook collection unavailable: {type(exc).__name__}", file=sys.stderr)

    try:
        config = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))
        for feed in config.get("feeds", []):
            if not feed.get("enabled", True):
                continue
            try:
                news_items.extend(fetch_feed(feed["url"], feed["region"], feed["name"], feed_source_url=feed.get("source_url", ""))[:8])
            except Exception as exc:
                warnings.append(f"RSS {feed.get('name', 'source')}: {type(exc).__name__}")

    except Exception as exc:
        warnings.append(f"news_sources: {type(exc).__name__}")

    if not social_items:
        print("No social trend source returned data; preserving the previous radar.", file=sys.stderr)
        return 1

    radar = build_radar(social_items, news_items)
    radar["sources_checked"] = checked
    radar["warnings"] = warnings
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(radar, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(radar['items'])} social trend candidates to {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
