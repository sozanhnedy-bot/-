"""Render internal HTML pages from curated Arabic reports."""
from __future__ import annotations

import argparse
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "reports.json"
NEWS_DATA = ROOT / "data" / "news.json"
SITE_URL = "https://sozanhnedy-bot.github.io/-"
BRAND = "Threads Documentary News"
REPORT_ID_RE = re.compile(r"^[a-f0-9]{20,40}$")
IMAGE_RE = re.compile(r"^assets/story-[a-z0-9-]+\.webp$")
YOUTUBE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")


def esc(value: object) -> str:
    return html.escape(str(value or ""), quote=True)


def safe_image(value: object) -> str:
    candidate = str(value or "")
    return candidate if IMAGE_RE.fullmatch(candidate) else "assets/brand-mark.png"


def safe_social_image(_value: object) -> str:
    return "assets/brand-cover.jpg"


def short_code(report_id: str, report_ids: list[str]) -> str:
    length = 6
    while length < len(report_id) and any(
        other != report_id and other.startswith(report_id[:length]) for other in report_ids
    ):
        length += 2
    return report_id[:length]


def render(report: dict, code: str, news_item: dict | None = None, related_items: list[dict] | None = None) -> str:
    title = esc(report.get("title"))
    dek = esc(report.get("dek"))
    report_id = esc(report.get("id"))
    image_path = safe_image(report.get("image"))
    image = esc(image_path)
    social_image = esc(safe_social_image(report.get("image")))
    alt = esc(report.get("image_alt") or "صورة توضيحية")
    caption = esc(report.get("image_caption") or "صورة توضيحية، ولا تمثل بالضرورة موقع الحدث.")
    candidate = str(report.get("youtube_id") or "")
    youtube_id = candidate if YOUTUBE_ID_RE.fullmatch(candidate) else ""
    video_player = ""
    if youtube_id:
        video_player = f'''<section class="youtube-player-card" aria-label="فيديو التقرير">
<h2>مشاهدة الفيديو داخل التقرير</h2><p>اضغط لتشغيل مشغل YouTube. لن يتصل المشغل بخوادم الفيديو قبل اختيارك.</p>
<button type="button" class="article-action" data-load-youtube>تشغيل الفيديو داخل الموقع</button>
<div class="youtube-frame-host" data-youtube-host data-youtube-id="{esc(youtube_id)}"></div>
</section>'''

    sections = []
    for section in report.get("sections", []):
        if not isinstance(section, dict):
            continue
        paragraphs = "".join(
            f"<p>{esc(paragraph)}</p>"
            for paragraph in section.get("paragraphs", [])
            if isinstance(paragraph, str)
        )
        sections.append(f"<section class=\"article-section\"><h2>{esc(section.get('heading'))}</h2>{paragraphs}</section>")

    sources = "، ".join(esc(name) for name in report.get("sources", []) if isinstance(name, str))
    published = str(report.get("published_at") or (news_item or {}).get("published_at") or "")
    related_rows = []
    for item in related_items or []:
        if not isinstance(item, dict) or item.get("id") == report.get("id"):
            continue
        raw_related_id = str(item.get("id") or "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", raw_related_id):
            continue
        related_title = str(item.get("title") or "").strip()
        if related_title:
            related_rows.append(f'<li><a href="../news/{esc(raw_related_id)}.html">{esc(related_title)}</a></li>')
        if len(related_rows) >= 3:
            break
    related_news = f'<aside class="related-news"><h2>أخبار ذات صلة</h2><ul>{"".join(related_rows)}</ul></aside>' if related_rows else ""
    script_rows = "".join(
        f"<li><span class=\"scene-duration\">{esc(scene.get('duration'))}</span><p><b>الصورة:</b> {esc(scene.get('visual'))}</p><p><b>التعليق الصوتي:</b> {esc(scene.get('voiceover'))}</p></li>"
        for scene in report.get("video_script", []) if isinstance(scene, dict)
    )
    share_payload = json.dumps(
        {
            "title": str(report.get("title") or ""),
            "text": str(report.get("social_caption") or ""),
            "short_url": f"../r/{code}.html",
        },
        ensure_ascii=False,
    ).replace("</", "<\\/")
    share_payload = share_payload.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    canonical = f"{SITE_URL}/reports/{report_id}.html"
    return f'''<!doctype html>
<html lang="ar" dir="rtl"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="theme-color" content="#14283f"><meta name="description" content="{dek}">
<meta property="og:site_name" content="{BRAND}"><meta property="og:type" content="article"><meta property="og:title" content="{title} | {BRAND}">
<meta property="og:description" content="{dek}"><meta property="og:image" content="{SITE_URL}/{social_image}">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="{title} | {BRAND}"><meta name="twitter:description" content="{dek}"><meta name="twitter:image" content="{SITE_URL}/{social_image}">
<meta property="og:url" content="{canonical}"><link rel="canonical" href="{canonical}">
<link rel="icon" href="../assets/brand-mark.png" type="image/png"><link rel="stylesheet" href="../styles.css">
<title>{title} | {BRAND}</title><script defer src="../article.js"></script>
</head><body class="article-body">
<a class="skip-link" href="#article-main">انتقل إلى التقرير</a>
<header class="article-masthead"><a href="../" class="article-brand"><img src="../assets/brand-mark.png" alt=""><span><small>ثريدز وثائقي</small><b>{BRAND}</b></span></a><a class="article-home" href="../">العودة إلى الصفحة الرئيسية</a></header>
<main id="article-main" class="article-shell">
<nav class="breadcrumb" aria-label="مسار الصفحة"><a href="../">الرئيسية</a><span aria-hidden="true">/</span><span>تقرير مفصل</span></nav>
<article class="full-report" data-report-id="{report_id}" data-short-url="../r/{code}.html">
<header class="article-heading"><p class="eyebrow">تقرير إخباري</p><h1>{title}</h1><p class="article-dek">{dek}</p><div class="article-meta"><span>{BRAND}</span><span>المصادر: {sources}</span>{f'<time datetime="{esc(published)}">{esc(published)}</time>' if published else ''}</div></header>
<figure class="article-figure"><img src="../{image}" alt="{alt}" fetchpriority="high"><figcaption>{caption}</figcaption></figure>
{video_player}
<div class="article-copy">{''.join(sections)}</div>
<aside class="sources-box"><b>المصادر المعتمدة</b><p>{sources}</p><small>المصادر مذكورة بالاسم، وتبقى القراءة داخل الموقع.</small></aside>
{related_news}
<section class="social-approval" aria-labelledby="social-title"><div><p class="eyebrow">محتوى اجتماعي</p><h2 id="social-title">نص مشاركة مقترح</h2><p class="social-copy">{esc(report.get('social_caption'))}</p><p class="approval-note">هذا الزر يفتح مشاركة يدوية؛ لا يرسل شيئًا إلى منصة اجتماعية من تلقاء نفسه.</p><button type="button" class="article-action" data-share>مراجعة ثم مشاركة</button> <button type="button" class="article-action secondary" data-copy-short-link>نسخ الرابط المختصر</button></div></section>
<section class="video-script" aria-labelledby="video-title"><div class="video-heading"><div><p class="eyebrow">مسودة إنتاج</p><h2 id="video-title">سكريبت فيديو مقترح</h2></div><button type="button" class="article-action secondary" data-copy-script>نسخ السكريبت</button></div><ol>{script_rows}</ol><p class="approval-note">هذا سكريبت إنتاجي للمراجعة، وليس ملف فيديو منشورًا. استخدم صورًا موثقة أو لقطات مرخصة قبل الإنتاج.</p></section>
</article></main>
<footer class="site-footer"><div class="footer-brand">{BRAND}</div><p>مشروع مستقل · تقرير داخلي · المصادر مذكورة بالاسم</p></footer>
<script type="application/json" id="share-payload">{share_payload}</script>
</body></html>'''


def render_short_link(report: dict) -> str:
    report_id = esc(report["id"])
    title = esc(report.get("title"))
    dek = esc(report.get("dek"))
    image = esc(safe_social_image(report.get("image")))
    target = f"../reports/{report_id}.html"
    canonical = f"{SITE_URL}/reports/{report_id}.html"
    target_json = json.dumps(target)
    return f'''<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="0;url={target}"><link rel="canonical" href="{canonical}">
<meta property="og:site_name" content="{BRAND}"><meta property="og:type" content="article"><meta property="og:title" content="{title} | {BRAND}"><meta property="og:description" content="{dek}"><meta property="og:image" content="{SITE_URL}/{image}">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="{title} | {BRAND}"><meta name="twitter:description" content="{dek}"><meta name="twitter:image" content="{SITE_URL}/{image}">
<title>{title} | {BRAND}</title><script>location.replace({target_json});</script></head>
<body><main><h1>{title}</h1><p>يجري فتح التقرير على الموقع.</p><a href="{target}">افتح التقرير الكامل</a></main></body></html>'''


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, help="reports output directory")
    args = parser.parse_args()
    data = json.loads(DATA.read_text(encoding="utf-8"))
    news_data = json.loads(NEWS_DATA.read_text(encoding="utf-8")) if NEWS_DATA.is_file() else {"items": []}
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    reports = [
        report for report in data.get("items", [])
        if isinstance(report, dict) and isinstance(report.get("id"), str)
        and REPORT_ID_RE.fullmatch(report["id"]) and isinstance(report.get("title"), str)
        and isinstance(report.get("sections"), list) and report["sections"]
    ]
    ids = [report["id"] for report in reports]
    news_by_id = {str(item.get("id")): item for item in news_data.get("items", []) if isinstance(item, dict)}
    report_ids = set(ids)
    short_dir = out.parent / "r"
    short_dir.mkdir(parents=True, exist_ok=True)
    for report in reports:
        code = short_code(report["id"], ids)
        matched_news = news_by_id.get(report["id"], {})
        region = str(matched_news.get("region") or report.get("region") or "")
        category = str(matched_news.get("category") or report.get("category") or "")
        related = [
            item for item in news_data.get("items", [])
            if isinstance(item, dict) and str(item.get("id")) not in report_ids
            and ((category and item.get("category") == category) or (region and item.get("region") == region))
        ]
        related.sort(key=lambda item: str(item.get("published_at") or ""), reverse=True)
        (out / f"{report['id']}.html").write_text(render(report, code, matched_news, related), encoding="utf-8")
        (short_dir / f"{code}.html").write_text(render_short_link(report), encoding="utf-8")
    print(f"Rendered {len(reports)} internal report pages and short aliases.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
