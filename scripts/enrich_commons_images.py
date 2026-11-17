"""Find subject-matched, reusable Wikimedia Commons images for recent news.

Only files with an explicit CC0, CC BY, CC BY-SA, or public-domain license,
creator/credit metadata, and a strong title-to-file text match are downloaded.
Uncertain matches are left without a photo.
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
USER_AGENT = "WorldNewsImageResearch/1.0 (https://sozanhnedy-bot.github.io/-/; editorial image licensing contact)"
MAX_IMAGE_BYTES = 8 * 1024 * 1024
ALLOWED_MIMES = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}
STOP_WORDS = {
    "من", "في", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي",
    "بعد", "قبل", "بين", "خلال", "حول", "أمام", "ضمن", "عبر", "أكثر", "أقل",
    "the", "and", "for", "with", "from", "after", "before", "over", "into",
    "news", "report", "update", "breaking", "صور", "صورة", "أخبار", "خبر",
}

def clean(value: object, limit: int = 2000) -> str:
    value = html.unescape(re.sub(r"<[^>]+>", " ", str(value or "")))
    return re.sub(r"\s+", " ", value).strip()[:limit]

def tokens(value: str) -> set[str]:
    normalized = re.sub(r"[^\w\s]", " ", value.casefold(), flags=re.UNICODE)
    return {word for word in normalized.split() if len(word) > 2 and word not in STOP_WORDS}

def license_status(short_name: str, license_url: str) -> str:
    name = clean(short_name, 100).casefold()
    url = license_url.casefold()
    if "public domain" in name or name.startswith("pd-") or "creativecommons.org/publicdomain/mark/" in url:
        return "public_domain"
    if "cc0" in name or "creativecommons.org/publicdomain/zero/" in url:
        return "cc0"
    if "cc by-sa" in name or "/licenses/by-sa/" in url:
        return "cc-by-sa"
    if "cc by" in name or re.search(r"/licenses/by/\d", url):
        return "cc-by"
    return ""

def api_json(params: dict) -> dict:
    url = API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise ValueError("Commons API returned a non-success response")
        return json.loads(response.read(2_000_000).decode("utf-8"))

def search_files(title: str) -> list[dict]:
    params = {
        "action": "query", "generator": "search", "gsrnamespace": "6",
        "gsrsearch": title[:220], "gsrlimit": "10",
        "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata",
        "format": "json", "formatversion": "2",
    }
    data = api_json(params)
    return data.get("query", {}).get("pages", []) or []

def candidate_metadata(page: dict, story_tokens: set[str]) -> dict | None:
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
    artist = value("Artist")
    credit = value("Credit")
    creator = artist or credit
    if not creator:
        return None
    file_title = clean(page.get("title", ""), 500)
    description = value("ImageDescription")
    object_name = value("ObjectName")
    searchable = tokens(file_title + " " + description + " " + object_name)
    overlap = story_tokens & searchable
    distinctive = {word for word in overlap if len(word) >= 6}
    if len(overlap) < 2 and not distinctive:
        return None
    original = info.get("url", "")
    parsed = urllib.parse.urlsplit(original)
    if parsed.scheme != "https" or parsed.hostname != "upload.wikimedia.org":
        return None
    source_url = "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(file_title.replace(" ", "_"), safe=":/")
    return {
        "url": original, "extension": ext, "status": status, "license_url": license_url,
        "credit": clean(creator, 300), "description": clean(object_name or description, 300),
        "source_url": source_url, "score": len(overlap) + len(distinctive),
        "caption": f"صورة توثيقية مرتبطة بموضوع الخبر من Wikimedia Commons ({short})",
    }

def download_image(url: str, destination: Path) -> bool:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
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
    try:
        pages = search_files(title)
    except (OSError, ValueError, urllib.error.URLError, TimeoutError) as exc:
        print(f"Commons search skipped for {item.get('id')}: {type(exc).__name__}", file=sys.stderr)
        return False
    candidates = [candidate for page in pages if (candidate := candidate_metadata(page, story_tokens))]
    candidates.sort(key=lambda entry: entry["score"], reverse=True)
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    for candidate in candidates[:4]:
        image_id = re.sub(r"[^A-Za-z0-9_-]", "", str(item.get("id", "")))[:80]
        if not image_id:
            continue
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
            "image_caption": candidate["caption"],
            "image_match_method": "wikimedia-commons-title-overlap",
        })
        return True
    return False

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--days", type=int, default=7)
    args = parser.parse_args()
    data = json.loads(NEWS_PATH.read_text(encoding="utf-8"))
    items = data.get("items", [])
    cutoff = datetime.now(timezone.utc) - timedelta(days=max(1, args.days))
    recent = sorted(
        (item for item in items if isinstance(item, dict) and published_date(item) and published_date(item) >= cutoff),
        key=lambda item: published_date(item) or datetime.min.replace(tzinfo=timezone.utc),
        reverse=True,
    )
    changed = 0
    examined = 0
    for item in recent:
        if changed >= max(0, args.limit):
            break
        current = str(item.get("image_url", "") or "")
        current_is_local = current.startswith("assets/news-images/") and (ROOT / current).is_file()
        if current_is_local and item.get("image_rights_status") and item.get("image_license_url") and item.get("image_credit"):
            continue
        if item.get("image_rights_status") in {"licensed", "permission_granted", "public_domain", "cc0", "cc-by", "cc-by-sa"} and item.get("image_credit") and str(item.get("image_license_url", "")).startswith("https://"):
            continue
        examined += 1
        if enrich_item(item):
            changed += 1
            print(f"Added a licensed Commons image for {item.get('id')}.")
    if changed:
        NEWS_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Commons image pass complete: {changed} images added from {examined} attempted stories.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
