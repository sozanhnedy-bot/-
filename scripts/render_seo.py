"""Render crawler-facing SEO documents for the static World News site."""
from __future__ import annotations

import html
import json
import re
from datetime import datetime, timedelta, timezone
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
        ("terms.html", None),
    ]
    report_items = reports.get("items", []) if isinstance(reports, dict) else []
    report_ids = {
        str(item.get("id"))
        for item in report_items
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    news_items = news.get("items", []) if isinstance(news, dict) else []
    news_by_id = {
        str(item.get("id")): item
        for item in news_items
        if isinstance(item, dict) and item.get("id") is not None
    }
    for report in report_items:
        if not isinstance(report, dict) or str(report.get("id")) not in report_ids:
            continue
        report_id = str(report.get("id") or "")
        matched_news = news_by_id.get(report_id, {})
        published = report.get("published_at") or matched_news.get("published_at") or matched_news.get("created_at")
        if report.get("sections") and re.fullmatch(r"[a-f0-9]{20,40}", report_id):
            paths.append((f"reports/{report_id}.html", _iso_date(published)))

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



def _parse_datetime(value: object) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def render_news_sitemap(reports: dict, now: datetime | None = None, news: dict | None = None) -> str:
    """List only original editorial reports published in the last 48 hours."""
    current = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    cutoff = current - timedelta(days=2)
    items = reports.get("items", []) if isinstance(reports, dict) else []
    news_items = news.get("items", []) if isinstance(news, dict) else []
    news_by_id = {
        str(item.get("id")): item
        for item in news_items
        if isinstance(item, dict) and item.get("id") is not None
    }
    eligible = []
    for report in items:
        if not isinstance(report, dict) or not report.get("sections"):
            continue
        report_id = str(report.get("id") or "")
        if not re.fullmatch(r"[a-f0-9]{20,40}", report_id):
            continue
        matched_news = news_by_id.get(report_id, {})
        published = _parse_datetime(
            report.get("published_at") or matched_news.get("published_at") or matched_news.get("created_at")
        )
        title = str(report.get("title") or "").strip()
        if not published or not title or not cutoff <= published <= current:
            continue
        eligible.append((published, report_id, title))
    entries = []
    for published, report_id, title in sorted(eligible, reverse=True):
        published_value = published.isoformat(timespec="seconds").replace("+00:00", "Z")
        entries.append(
            "  <url><loc>{}</loc><news:news><news:publication>"
            "<news:name>World News</news:name><news:language>ar</news:language>"
            "</news:publication><news:publication_date>{}</news:publication_date>"
            "<news:title>{}</news:title></news:news></url>".format(
                html.escape(_url(f"reports/{report_id}.html")),
                html.escape(published_value),
                html.escape(title),
            )
        )
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
        'xmlns:news="http://www.google.com/schemas/sitemap-news/0.9">\n'
        + "\n".join(entries)
        + "\n</urlset>\n"
    )


def render_robots() -> str:
    return f"User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: {_url('sitemap.xml')}\nSitemap: {_url('news-sitemap.xml')}\n"


def build(output: Path) -> None:
    news = json.loads(NEWS_PATH.read_text(encoding="utf-8"))
    reports = json.loads(REPORTS_PATH.read_text(encoding="utf-8"))
    output.mkdir(parents=True, exist_ok=True)
    (output / "sitemap.xml").write_text(render_sitemap(news, reports), encoding="utf-8")
    (output / "news-sitemap.xml").write_text(render_news_sitemap(reports, news=news), encoding="utf-8")
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
