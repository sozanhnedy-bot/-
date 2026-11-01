"""Render RSS-only items as safe internal briefs without source redirects."""
from __future__ import annotations

import argparse
import html
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEWS_PATH = ROOT / "data" / "news.json"
REPORTS_PATH = ROOT / "data" / "reports.json"
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,80}$")
REGIONS = {"world": "العالم", "arab": "العالم العربي", "egypt": "مصر"}
SAFE_NOTICE = "هذا موجز أولي من خلاصة RSS، وليس تقريرًا تحليليًا كاملًا. لا تتوفر في الخلاصة تفاصيل كافية لإضافة معلومات أخرى دون التحقق من مصادر إضافية."


def text(value: object, limit: int = 4_000) -> str:
    return str(value or "").strip()[:limit]


def render(item: dict) -> str:
    raw_id = text(item.get("id"), 80)
    if not ID_RE.fullmatch(raw_id):
        raise ValueError("unsafe news ID")
    title = text(item.get("title"), 500) or "خبر قيد التحرير"
    summary = text(item.get("summary"), 600)
    if not summary or "افتح رابط المصدر" in summary or "اقرأ الخبر" in summary:
        summary = SAFE_NOTICE
    source = text(item.get("source_name"), 160) or "خلاصة RSS"
    region = REGIONS.get(text(item.get("region")), "خبر")
    published = text(item.get("published_at") or item.get("created_at"), 80)
    e = html.escape
    description = e(summary[:200], quote=True)
    return f'''<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#14283f"><meta name="description" content="{description}">
  <meta property="og:site_name" content="Threads Documentary News"><meta property="og:type" content="article"><meta property="og:title" content="{e(title, quote=True)}">
  <meta property="og:description" content="{description}"><meta property="og:image" content="https://sozanhnedy-bot.github.io/-/assets/brand-cover.jpg">
  <meta name="twitter:card" content="summary_large_image"><title>{e(title)} | Threads Documentary News</title>
  <link rel="icon" href="../assets/brand-mark.png" type="image/png"><link rel="stylesheet" href="../styles.css">
</head>
<body class="article-body">
  <header class="article-masthead"><a class="article-brand" href="../"><img src="../assets/brand-mark.png" alt=""><span><small>ثريدز وثائقي</small><b>Threads Documentary News</b></span></a><a class="article-home" href="../">العودة إلى الموقع</a></header>
  <main class="article-shell"><nav class="breadcrumb"><a href="../">الرئيسية</a><span aria-hidden="true">/</span><span>موجز أولي</span></nav>
    <article class="full-report brief-report"><header class="article-heading"><p class="eyebrow">موجز أولي · {e(region)}</p><h1>{e(title)}</h1><p class="article-dek">{e(summary)}</p>
      <div class="article-meta"><span>المصدر: {e(source)}</span>{f'<time datetime="{e(published, quote=True)}">{e(published)}</time>' if published else ''}</div>
    </header>
    <section class="brief-disclosure"><h2>حالة المادة</h2><p>هذا موجز مستخلص من بيانات RSS المتاحة، وليس تقريرًا كاملًا. نعرضه داخل الموقع كي لا يتحول الزائر إلى صفحة خارجية. قد تضاف تفاصيل موثقة وصورة تحريرية عند اكتمال المراجعة.</p></section>
    <p class="article-meta">لا يحتوي هذا الموجز على رابط خارجي أو فيديو غير معتمد.</p></article>
  </main><footer class="site-footer"><div class="footer-brand">Threads Documentary News</div><p>مصدر معروف · قراءة داخل الموقع</p></footer>
</body></html>'''


def build(output: Path) -> int:
    news = json.loads(NEWS_PATH.read_text(encoding="utf-8"))
    reports = json.loads(REPORTS_PATH.read_text(encoding="utf-8"))
    report_ids = {str(item.get("id", "")) for item in reports.get("items", []) if isinstance(item, dict)}
    target = output / "news"
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)
    rendered = 0
    for item in news.get("items", []):
        if not isinstance(item, dict) or str(item.get("id", "")) in report_ids:
            continue
        raw_id = text(item.get("id"), 80)
        if not ID_RE.fullmatch(raw_id):
            continue
        (target / f"{raw_id}.html").write_text(render(item), encoding="utf-8")
        rendered += 1
    print(f"Rendered {rendered} internal RSS brief pages.")
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
