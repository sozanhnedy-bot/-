"""Render crawler-facing SEO documents for the static World News site."""
from __future__ import annotations

import html
import json
from datetime import datetime, timezone
from pathlib import Path

try:
    from scripts.render_news import has_public_details
except ModuleNotFoundError:
    from render_news import has_public_details

ROOT = Path(__file__).resolve().parents[1]
SITE_URL = "https://sozanhnedy-bot.github.io/-"
NEWS_PATH = ROOT / "data" / "news.json"
REPORTS_PATH = ROOT / "data" / "reports.json"


def _iso_date(value: object) -> str | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).date().isoformat()


def _url(path: str) -> str:
    return f"{SITE_URL}/{path.lstrip('/')}"


def _public_paths(news: dict, reports: dict) -> list[tuple[str, str | None]]:
    paths: list[tuple[str, str | None]] = [
        ("", None),
        ("about.html", None),
        ("privacy.html", None),
        ("contact.html", None),
        ("editorial-policy.html", None),
    ]
    report_items = reports.get("items", []) if isinstance(reports, dict) else []
    report_ids = {
        str(item.get("id"))
        for item in report_items
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    for report in report_items:
        if not isinstance(report, dict) or str(report.get("id")) not in report_ids:
            continue
        if report.get("sections"):
            paths.append((f"reports/{report['id']}.html", _iso_date(report.get("published_at"))))

    items = news.get("items", []) if isinstance(news, dict) else []
    for item in items:
        if not isinstance(item, dict) or str(item.get("id")) in report_ids:
            continue
        raw_id = str(item.get("id") or "")
        if raw_id and has_public_details(item):
            paths.append((f"news/{raw_id}.html", _iso_date(item.get("published_at"))))
    return paths


def render_sitemap(news: dict, reports: dict) -> str:
    entries = []
    for path, lastmod in _public_paths(news, reports):
        lastmod_markup = f"<lastmod>{html.escape(lastmod)}</lastmod>" if lastmod else ""
        entries.append(f"  <url><loc>{html.escape(_url(path))}</loc>{lastmod_markup}</url>")
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        + "\n".join(entries)
        + "\n</urlset>\n"
    )


def render_robots() -> str:
    return f"User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: {_url('sitemap.xml')}\n"


def build(output: Path) -> None:
    news = json.loads(NEWS_PATH.read_text(encoding="utf-8"))
    reports = json.loads(REPORTS_PATH.read_text(encoding="utf-8"))
    output.mkdir(parents=True, exist_ok=True)
    (output / "sitemap.xml").write_text(render_sitemap(news, reports), encoding="utf-8")
    (output / "robots.txt").write_text(render_robots(), encoding="utf-8")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="_site")
    args = parser.parse_args()
    destination = Path(args.output)
    if not destination.is_absolute():
        destination = ROOT / destination
    build(destination)
