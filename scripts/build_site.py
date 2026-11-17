"""Build the static Pages directory from the checked-in public news data."""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path
from datetime import datetime, timedelta, timezone

ROOT = Path(__file__).resolve().parents[1]
ROOT_FILES = (
    "index.html", "archive.html", "section.html", "videos.html", "photos.html",
    "about.html", "privacy.html", "contact.html", "editorial-policy.html",
    "app.js", "archive.js", "section.js", "videos.js", "photos.js", "article.js",
    "styles.css", "styles-newspaper-overrides.css", "manifest.webmanifest", "service-worker.js",
)
DATA_FILES = ("news.json", "reports.json")
ASSET_FILES = (
    "brand-mark.png", "app-icon-192.png", "app-icon-512.png",
    "brand-cover.jpg",
    "portal-interface-banner-optimized.jpg",
    "reference-lead.jpg", "reference-technology.jpg", "reference-sports.jpg",
    "reference-market.jpg", "reference-wildfire.jpg", "reference-entertainment.jpg",
    "story-finswimming.webp", "story-morocco-stadium.webp", "story-yemen-diplomacy.webp",
)


def build(output: Path) -> int:
    output = output.resolve()
    if output in {Path("/"), ROOT, ROOT.parent}:
        raise ValueError("refusing to replace a critical output directory")
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True, exist_ok=True)
    for name in ROOT_FILES:
        source = ROOT / name
        if not source.is_file():
            raise FileNotFoundError(f"Required site file is missing: {name}")
        shutil.copy2(source, output / name)

    for folder in ("data", "assets"):
        (output / folder).mkdir(exist_ok=True)
    for name in DATA_FILES:
        source = ROOT / "data" / name
        if name == "news.json":
            news = json.loads(source.read_text(encoding="utf-8"))
            for item in news.get("items", []):
                if isinstance(item, dict):
                    item.pop("article_content", None)
                    if isinstance(item.get("excerpt"), str):
                        item["excerpt"] = item["excerpt"][:300]
                    if item.get("summary_type") == "source_excerpt":
                        excerpt = str(item.get("excerpt") or "")
                        title = str(item.get("title") or "")
                        source = str(item.get("source_name") or "")
                        normalize = lambda value: " ".join("".join(ch if ch.isalnum() or ch.isspace() else " " for ch in value.casefold()).split())
                        detail = normalize(excerpt)
                        title_key = normalize(title)
                        words = detail.split()
                        if len(excerpt) >= 45 and len(words) >= 7 and detail != title_key:
                            item["summary"] = excerpt
                        else:
                            item["summary"] = "لم يتضمن موجز الناشر وصفًا إضافيًا يتجاوز العنوان المنشور."
                            item["summary_type"] = "source_notice"
            (output / "data" / name).write_text(json.dumps(news, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
            now = datetime.now(timezone.utc)
            cutoff = now - timedelta(hours=72)
            latest_items = []
            for item in news.get("items", []):
                if not isinstance(item, dict):
                    continue
                raw_date = item.get("published_at") or item.get("created_at")
                try:
                    published = datetime.fromisoformat(str(raw_date).replace("Z", "+00:00"))
                    if published.tzinfo is None:
                        published = published.replace(tzinfo=timezone.utc)
                    published = published.astimezone(timezone.utc)
                except (TypeError, ValueError):
                    continue
                if cutoff <= published <= now:
                    latest_items.append(item)
            latest = {
                "schema_version": news.get("schema_version", 1),
                "updated_at": news.get("updated_at", ""),
                "items": latest_items,
            }
            (output / "data" / "news-latest.json").write_text(
                json.dumps(latest, ensure_ascii=False, separators=(",", ":")) + "\n",
                encoding="utf-8",
            )
        else:
            shutil.copy2(source, output / "data" / name)
    for name in ASSET_FILES:
        shutil.copy2(ROOT / "assets" / name, output / "assets" / name)
    shutil.copytree(ROOT / "assets" / "news-illustrations", output / "assets" / "news-illustrations", dirs_exist_ok=True)
    news_images = ROOT / "assets" / "news-images"
    if news_images.is_dir():
        shutil.copytree(news_images, output / "assets" / "news-images", dirs_exist_ok=True)

    (output / ".nojekyll").touch()
    subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "render_articles.py"), "--output", str(output / "reports")],
        cwd=ROOT,
        check=True,
    )
    subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "render_news.py"), "--output", str(output)],
        cwd=ROOT,
        check=True,
    )
    subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "render_seo.py"), "--output", str(output)],
        cwd=ROOT,
        check=True,
    )
    print(f"Built read-only news site in {output}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="_site", help="destination directory (default: _site)")
    args = parser.parse_args()
    output = Path(args.output)
    if not output.is_absolute():
        output = ROOT / output
    return build(output)


if __name__ == "__main__":
    raise SystemExit(main())
