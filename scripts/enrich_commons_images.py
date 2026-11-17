"""Find subject-matched, reusable Wikimedia Commons images for recent news.

Images are downloaded only when Commons exposes an explicit reuse license,
creator credit, and a strong match to distinctive words in the story title.
Ambiguous results are skipped rather than attached to an unrelated story.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEWS_PATH = ROOT / "data" / "news.json"
IMAGE_DIR = ROOT / "assets" / "news-images"
API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "WorldNewsImageResearch/1.1 (https://sozanhnedy-bot.github.io/-/; editorial image licensing contact)"
MAX_IMAGE_BYTES = 8 * 1024 * 1024
ALLOWED_MIMES = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}
STOP_WORDS = {
    "من", "في", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي",
    "بعد", "قبل", "بين", "خلال", "حول", "أمام", "ضمن", "عبر", "أكثر", "أقل",
    "the", "and", "for", "with", "from", "after", "before", "over", "into",
    "news", "report", "update", "breaking", "صور", "صورة", "أخبار", "خبر",
    "says", "said", "live", "takeaways", "first", "leg", "other", "middle",
    "east", "register", "international", "agency", "official", "announces",
}
PUBLISHER_WORDS = {
    "bbc", "news", "register", "telegraph", "times", "post", "today",
    "international", "middle", "east", "reuters", "associated", "press",
}

def clean(value: object, limit: int = 2000) -> str:
    value = html.unescape(re.sub(r"<[^>]+>", " ", str(value or "")))
    return re.sub(r"\s+", " ", value).strip()[:limit]

def tokens(value: str) -> set[str]:
    normalized = re.sub(r"[^\w\s]", " ", value.casefold(), flags=re.UNICODE)
    return {word for word in normalized.split() if len(word) > 2 and word not in STOP_WORDS}

def image_matches_story(item: dict) -> bool:
    """Reject licensed images whose available subject metadata does not match the story."""
    title = clean(item.get("title", ""), 500)
    if not title:
        return True
    story_tokens = tokens(title)
    image_tokens: set[str] = set()
    alt = clean(item.get("image_alt", ""), 300)
    if alt and tokens(alt) != story_tokens:
        image_tokens.update(tokens(alt))
    source_url = clean(item.get("image_source_url", ""), 1000)
    if source_url:
        try:
            filename = urllib.parse.unquote(urllib.parse.urlsplit(source_url).path.rsplit("/", 1)[-1])
            image_tokens.update(tokens(filename))
        except ValueError:
            pass
    if not image_tokens:
        return False
    return len(story_tokens & image_tokens) >= 2


def search_queries(title: str) -> list[str]:
    """Build a small set of subject-focused Commons queries, not publisher queries."""
    clean_title = clean(title, 500)
    story = clean_title.rsplit(" - ", 1)[0]
    queries = [story]
    # Proper-name spans in Latin-script headlines, e.g. "Donald Trump", "New Zealand".
    spans = re.findall(r"\b[A-Z][A-Za-z’'-]*(?:\s+[A-Z][A-Za-z’'-]*){0,2}\b", story)
    for span in spans:
        words = [w.casefold() for w in re.findall(r"[A-Za-z’'-]+", span)]
        if not words or all(w in PUBLISHER_WORDS for w in words):
            continue
        if len(span) >= 4:
            queries.append(span)
    # Arabic/other scripts: search the headline and a compact pair of meaningful terms.
    meaningful = [w for w in re.findall(r"[\w]+", story, flags=re.UNICODE)
                  if len(w) > 3 and w.casefold() not in STOP_WORDS]
    if len(meaningful) >= 2:
        queries.append(" ".join(meaningful[:4]))
    result = []
    seen = set()
    for query in queries:
        key = query.casefold()
        if key not in seen and len(tokens(query)) >= 1:
            seen.add(key)
            result.append(query[:160])
        if len(result) >= 3:
            break
    return result

def license_status(short_name: str, license_url: str) -> str:
    name = clean(short_name, 100).casefold()
    parsed = urllib.parse.urlsplit(license_url)
    if parsed.scheme != "https" or parsed.hostname not in {"creativecommons.org", "www.creativecommons.org"}:
        return ""
    path = parsed.path.casefold()
    if ("public domain" in name or name.startswith("pd-")) and "/publicdomain/mark/" in path:
        return "public_domain"
    if ("cc0" in name or "/publicdomain/zero/" in path) and "/publicdomain/zero/" in path:
        return "cc0"
    if ("cc by-sa" in name or "/licenses/by-sa/" in path) and re.search(r"/licenses/by-sa/\d", path):
        return "cc-by-sa"
    if ("cc by" in name or "/licenses/by/" in path) and re.search(r"/licenses/by/\d", path):
        return "cc-by"
    return ""

def api_json(params: dict) -> dict:
    url = API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=8) as response:
        if response.status != 200:
            raise ValueError("Commons API returned a non-success response")
        return json.loads(response.read(2_000_000).decode("utf-8"))

def search_files(query: str) -> list[dict]:
    params = {
        "action": "query", "generator": "search", "gsrnamespace": "6",
        "gsrsearch": query, "gsrlimit": "8",
        "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata",
        "format": "json", "formatversion": "2",
    }
    data = api_json(params)
    return data.get("query", {}).get("pages", []) or []

def candidate_metadata(page: dict, story_tokens: set[str], query_tokens: set[str] | None = None) -> dict | None:
    info = (page.get("imageinfo") or [{}])[0]
    mime = str(info.get("mime", "")).lower()
    ext = ALLOWED_MIMES.get(mime)
    if not ext or int(info.get("size", 0) or 0) > MAX_IMAGE_BYTES:
        return None
    meta = info.get("extmetadata") or {}
    value = lambda key: clean((meta.get(key) or {}).get("value", ""), 1000)
    short = value("LicenseShortName")
    license_url = value("LicenseUrl")
    status = license_status(short, license_url)
    if not status or not license_url.startswith("https://"):
        return None
    creator = value("Artist") or value("Credit")
    if not creator:
        return None
    file_title = clean(page.get("title", ""), 500)
    description = value("ImageDescription")
    object_name = value("ObjectName")
    searchable = tokens(file_title + " " + description + " " + object_name)
    overlap = story_tokens & searchable
    query_overlap = (query_tokens or set()) & searchable
    # Two independent subject terms are required; a shared surname alone is not enough.
    if len(overlap) < 2:
        return None
    if query_tokens and not query_overlap:
        return None
    original = info.get("url", "")
    parsed = urllib.parse.urlsplit(original)
    if parsed.scheme != "https" or parsed.hostname != "upload.wikimedia.org":
        return None
    source_url = "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(file_title.replace(" ", "_"), safe=":/")
    return {
        "url": original, "extension": ext, "status": status, "license_url": license_url,
        "credit": clean(creator, 300), "description": clean(object_name or description, 300),
        "source_url": source_url, "score": len(overlap) * 2 + len(distinctive),
        "caption": f"صورة توثيقية مرتبطة بموضوع الخبر من Wikimedia Commons ({short}) — تصوير: {clean(creator, 180)}",
    }

def download_image(url: str, destination: Path) -> bool:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            mime = response.headers.get_content_type().lower()
            if mime not in ALLOWED_MIMES or int(response.headers.get("Content-Length", "0") or 0) > MAX_IMAGE_BYTES:
                return False
            payload = response.read(MAX_IMAGE_BYTES + 1)
        if len(payload) > MAX_IMAGE_BYTES:
            return False
        signatures = {
            "image/jpeg": payload.startswith(b"\xff\xd8\xff"),
            "image/png": payload.startswith(b"\x89PNG\r\n\x1a\n"),
            "image/webp": payload.startswith(b"RIFF") and payload[8:12] == b"WEBP",
        }
        if not signatures.get(mime, False):
            return False
        destination.write_bytes(payload)
        return True
    except (OSError, ValueError, urllib.error.URLError, TimeoutError):
        return False

def published_date(item: dict) -> datetime | None:
    value = str(item.get("published_at") or item.get("created_at") or "")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed.astimezone(timezone.utc)
    except ValueError:
        return None

def enrich_item(item: dict) -> bool:
    title = clean(item.get("title", ""), 500)
    story_tokens = tokens(title)
    if len(story_tokens) < 2:
        return False
    candidates_by_url = {}
    for query in search_queries(title):
        try:
            pages = search_files(query)
        except (OSError, ValueError, urllib.error.URLError, TimeoutError) as exc:
            print(f"Commons search skipped for {item.get('id')} ({query}): {type(exc).__name__}", file=sys.stderr, flush=True)
            continue
        query_tokens = tokens(query)
        for page in pages:
            candidate = candidate_metadata(page, story_tokens, query_tokens)
            if candidate:
                old = candidates_by_url.get(candidate["url"])
                if not old or candidate["score"] > old["score"]:
                    candidates_by_url[candidate["url"]] = candidate
    candidates = sorted(candidates_by_url.values(), key=lambda entry: entry["score"], reverse=True)
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    image_id = re.sub(r"[^A-Za-z0-9_-]", "", str(item.get("id", "")))[:80]
    if not image_id:
        return False
    for candidate in candidates[:3]:
        relative = f"assets/news-images/{image_id}{candidate['extension']}"
        destination = ROOT / relative
        if not download_image(candidate["url"], destination):
            continue
        item.update({
            "image_url": relative,
            "image_rights_status": candidate["status"],
            "image_credit": candidate["credit"],
            "image_license_url": candidate["license_url"],
            "image_source_url": candidate["source_url"],
            "image_alt": candidate["description"] or title,
            "image_caption": candidate["caption"] + "؛ لا تعني بالضرورة أنها صورة لحظة الحدث.",
            "image_match_method": "wikimedia-commons-subject-search",
        })
        print(f"Added a licensed Commons image for {item.get('id')}: {candidate['source_url']}", flush=True)
        return True
    return False

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=15, help="Maximum number of images to add")
    parser.add_argument("--days", type=int, default=30, help="Only consider stories published within this many days")
    parser.add_argument("--max-attempts", type=int, default=25, help="Maximum stories searched in one run")
    args = parser.parse_args()
    data = json.loads(NEWS_PATH.read_text(encoding="utf-8"))
    items = data.get("items", [])
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    cutoff = datetime.now(timezone.utc) - timedelta(days=max(1, args.days))
    recent = sorted(
        (item for item in items if isinstance(item, dict) and published_date(item) and published_date(item) >= cutoff),
        key=lambda item: published_date(item) or datetime.min.replace(tzinfo=timezone.utc),
        reverse=True,
    )
    changed = 0
    examined = 0
    for item in recent:
        if changed >= max(0, args.limit) or examined >= max(0, args.max_attempts):
            break
        current = str(item.get("image_url", "") or "")
        has_rights = bool(item.get("image_rights_status") and item.get("image_license_url") and item.get("image_credit"))
        if has_rights and image_matches_story(item):
            continue
        if current and has_rights:
            for key in ("image_url", "image_rights_status", "image_credit", "image_license_url", "image_source_url", "image_alt", "image_caption", "image_match_method"):
                item.pop(key, None)
            print(f"Cleared an image with a weak subject match for story {item.get('id')}.", flush=True)
        examined += 1
        print(f"Searching Commons for story {examined}/{args.max_attempts}: {clean(item.get('title'), 160)}", flush=True)
        if enrich_item(item):
            changed += 1
    if changed:
        NEWS_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Commons image pass complete: {changed} images added from {examined} attempted stories.", flush=True)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
