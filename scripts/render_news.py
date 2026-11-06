"""Render RSS items as safe internal news briefs with clear source attribution."""
from __future__ import annotations

import argparse
import html
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
NEWS_PATH = ROOT / "data" / "news.json"
REPORTS_PATH = ROOT / "data" / "reports.json"
SITE_URL = "https://sozanhnedy-bot.github.io/-"
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,80}$")
REGIONS = {"world": "العالم", "arab": "العالم العربي", "egypt": "مصر"}
CATEGORIES = {"politics": "سياسة", "economy": "اقتصاد", "technology": "تكنولوجيا", "sports": "رياضة"}
ILLUSTRATION_FILES = {"egypt": "egypt", "arab": "arab", "world": "world", "politics": "politics", "economy": "economy", "technology": "technology", "sports": "sports"}
BRAND = "World News"
ARABIC_BRAND = "أخبار العالم"
IMAGE_EXT_RE = re.compile(r"\.(?:jpe?g|png|webp|avif)$", re.IGNORECASE)


def text(value: object, limit: int = 4_000) -> str:
    return str(value or "").strip()[:limit]


def title_without_source(title_value: object, source_value: object) -> str:
    title = re.sub(r"\s*(?:🔴\s*)?(?:تابعوا|تابع|اقرأ المزيد|قراءة المزيد|عرض المزيد من العناوين|View more headlines on Google News).*?$", "", text(title_value, 500), flags=re.IGNORECASE).rstrip(" ….")
    source = text(source_value, 160).casefold()
    if not source:
        return title
    for separator in (" — ", " – ", " - ", " | ", " :: "):
        index = title.rfind(separator)
        if index > 0 and title[index + len(separator):].strip().casefold() == source:
            return title[:index].strip()
    return title


def concise_summary(value: object, title: str, source: str) -> str:
    summary = re.sub(r"\s*(?:🔴\s*)?(?:تابعوا|تابع|اقرأ المزيد|قراءة المزيد|عرض المزيد من العناوين|View more headlines on Google News).*?$", "", text(value, 1_200), flags=re.IGNORECASE).rstrip(" ….").strip()
    normalize = lambda item: re.sub(r"[^\w\s]", " ", item.casefold(), flags=re.UNICODE)
    summary_key = re.sub(r"\s+", " ", normalize(title_without_source(summary, source))).strip()
    title_key = re.sub(r"\s+", " ", normalize(title)).strip()
    if not summary_key or summary_key == title_key:
        return ""
    if summary_key.startswith(title_key) and len(summary_key.split()) <= len(title_key.split()) + 2 and not re.search(r"\d", summary):
        return ""
    return summary


def safe_external_url(value: object, *, image: bool = False) -> str:
    candidate = text(value, 1_000)
    try:
        parsed = urlsplit(candidate)
    except ValueError:
        return ""
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        return ""
    if image and not IMAGE_EXT_RE.search(parsed.path):
        return ""
    return candidate


def date_label(value: str) -> str:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
        return parsed.strftime("%Y-%m-%d %H:%M UTC")
    except (ValueError, TypeError):
        return ""


def render(item: dict, all_items: list[dict] | None = None) -> str:
    raw_id = text(item.get("id"), 80)
    if not ID_RE.fullmatch(raw_id):
        raise ValueError("unsafe news ID")
    source = text(item.get("source_name"), 160) or "المصدر غير محدد"
    title = title_without_source(item.get("title"), source) or "خبر قيد التحرير"
    summary = concise_summary(item.get("summary"), title, source)
    excerpt = concise_summary(item.get("excerpt"), title, source)
    if text(item.get("summary_type"), 40) == "source_notice" or "افتح رابط المصدر" in summary or "اقرأ الخبر" in summary:
        summary = ""
    if summary in {"لا تتوفر تفاصيل كافية بعد لإعداد تقرير موثق داخل الموقع؛ سيظل الخبر ضمن قائمة الرصد فقط.", "لم يتضمن موجز الناشر وصفًا إضافيًا يتجاوز العنوان المنشور."}:
        summary = ""
    if excerpt in {"لا تتوفر تفاصيل كافية بعد لإعداد تقرير موثق داخل الموقع؛ سيظل الخبر ضمن قائمة الرصد فقط.", "لم يتضمن موجز الناشر وصفًا إضافيًا يتجاوز العنوان المنشور."}:
        excerpt = ""
    details = excerpt or summary
    dek = summary if summary and excerpt and summary.casefold() != excerpt.casefold() else ""
    region = REGIONS.get(text(item.get("region")), "العالم")
    category_key = text(item.get("category"), 30)
    category = CATEGORIES.get(category_key, REGIONS.get(category_key, region))
    published = text(item.get("published_at") or item.get("created_at"), 80)
    published_display = date_label(published)
    image_url = safe_external_url(item.get("image_url"), image=True)
    illustration_file = ILLUSTRATION_FILES.get(category_key, "world") + ".jpg"
    image_src = image_url or f"../assets/news-illustrations/{illustration_file}"
    e = html.escape
    description = e((dek or details or title)[:220], quote=True)
    preview_image = image_url or f"{SITE_URL}/assets/news-illustrations/{illustration_file}"
    image_alt = title if image_url else f"رسم توضيحي لقسم {category}؛ ليس صورة للحدث"
    image_caption = f"الصورة: {source} · من موجز الناشر" if image_url else f"رسم توضيحي لقسم {category}؛ لا يصوّر الحدث نفسه."
    image_markup = (
        f'<figure class="article-figure"><img src="{e(image_src, quote=True)}" alt="{e(image_alt, quote=True)}" loading="eager" referrerpolicy="no-referrer"><figcaption>{e(image_caption)}</figcaption></figure>'
    )
    source_link = f'<span class="article-source-name">الناشر: {e(source)}</span>'
    related_items = []
    for candidate in all_items or []:
        if not isinstance(candidate, dict) or candidate.get("id") == raw_id or not ID_RE.fullmatch(text(candidate.get("id"), 80)):
            continue
        same_category = text(candidate.get("category"), 30) == category_key and category_key
        same_region = text(candidate.get("region"), 30) == text(item.get("region"), 30)
        if same_category or same_region:
            related_items.append(candidate)
        if len(related_items) >= 3:
            break
    related_markup = ""
    if related_items:
        links = "".join(
            f'<li><a href="./{text(candidate.get("id"), 80)}.html">{e(title_without_source(candidate.get("title"), candidate.get("source_name")))}</a></li>'
            for candidate in related_items
        )
        related_markup = f'<aside class="related-news"><h2>أخبار ذات صلة</h2><ul>{links}</ul></aside>'
    return f'''<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#14283f"><meta name="description" content="{description}">
  <meta property="og:site_name" content="{BRAND}"><meta property="og:type" content="article"><meta property="og:title" content="{e(title, quote=True)} | {BRAND}">
  <meta property="og:description" content="{description}"><meta property="og:image" content="{e(preview_image, quote=True)}">
  <meta name="twitter:card" content="summary_large_image"><title>{e(title)} | {BRAND}</title>
  <link rel="icon" href="../assets/brand-mark.png" type="image/png"><link rel="stylesheet" href="../styles.css?v=20261107-images">
</head>
<body class="article-body">
  <header class="article-masthead"><a class="article-brand" href="../"><img src="../assets/brand-mark.png" alt=""><span><small>{ARABIC_BRAND}</small><b>{BRAND}</b></span></a><a class="article-home" href="../">العودة إلى الأخبار</a></header>
  <main class="article-shell"><nav class="breadcrumb" aria-label="مسار الصفحة"><a href="../">الرئيسية</a><span aria-hidden="true">/</span><a href="../archive.html">الأرشيف</a><span aria-hidden="true">/</span><span>{e(category)}</span></nav>
    <article class="full-report brief-report"><header class="article-heading"><p class="eyebrow">{e(category)} · {e(region)}</p><h1>{e(title)}</h1>
      {f'<p class="article-dek">{e(dek)}</p>' if dek else ''}<div class="article-meta">{source_link}{f'<time datetime="{e(published, quote=True)}">{e(published_display)}</time>' if published_display else ''}</div>
    </header>
    {image_markup}
    {f'<section class="news-detail-copy"><h2>تفاصيل الخبر</h2><p>{e(details)}</p></section>' if details else ''}
    {related_markup}
  </article></main><footer class="site-footer"><div class="footer-brand">{BRAND}</div><p>الناشر مذكور بالاسم · تاريخ النشر محفوظ · القراءة داخل الموقع</p></footer>
</body></html>'''


def build(output: Path) -> int:
    news = json.loads(NEWS_PATH.read_text(encoding="utf-8"))
    reports = json.loads(REPORTS_PATH.read_text(encoding="utf-8"))
    report_ids = {str(item.get("id", "")) for item in reports.get("items", []) if isinstance(item, dict)}
    items = news.get("items", []) if isinstance(news, dict) else []
    target = output / "news"
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)
    rendered = 0
    for item in items:
        if not isinstance(item, dict) or str(item.get("id", "")) in report_ids:
            continue
        raw_id = text(item.get("id"), 80)
        if not ID_RE.fullmatch(raw_id):
            continue
        (target / f"{raw_id}.html").write_text(render(item, items), encoding="utf-8")
        rendered += 1
    print(f"Rendered {rendered} internal news pages.")
    return rendered


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="_site")
    args = parser.parse_args()
    output = Path(args.output)
    if not output.is_absolute():
        output = ROOT / output
    build(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
