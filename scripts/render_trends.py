"""Render each detected trend as a readable internal page with no outbound links."""
from __future__ import annotations

import argparse
import html
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRENDS_PATH = ROOT / "data" / "trending.json"
ID_RE = re.compile(r"^[a-f0-9]{18}$")
REGIONS = {"world": "العالم", "arab": "العالم العربي", "egypt": "مصر"}


def clean(value: object, limit: int = 2_000) -> str:
    return str(value or "").strip()[:limit]


def render(item: dict) -> str:
    trend_id = clean(item.get("id"), 80)
    if not ID_RE.fullmatch(trend_id):
        raise ValueError("unsafe trend ID")

    title = clean(item.get("title"), 240) or "موضوع رائج"
    region = clean(item.get("region_label"), 120)
    if not region or re.match(r"^(?:X|Google Trends)\s*[·-]\s*", region, re.IGNORECASE):
        region = REGIONS.get(clean(item.get("region")), "العالم")
    timestamp = clean(item.get("published_at"), 80)
    traffic = clean(item.get("approx_traffic"), 40)
    alert = bool(item.get("alert"))
    related = []
    seen = set()
    for entry in item.get("news_items", []) if isinstance(item.get("news_items"), list) else []:
        if not isinstance(entry, dict):
            continue
        headline = clean(entry.get("title"), 240)
        snippet = clean(entry.get("snippet"), 700)
        key = headline.casefold()
        if headline and key not in seen:
            seen.add(key)
            related.append((headline, snippet))
        if len(related) >= 8:
            break

    e = html.escape
    description = "موجز متابعة داخلي للموضوع والعناوين المرتبطة به."
    meta = []
    if traffic:
        meta.append(f"حجم الاهتمام التقريبي: {e(traffic)}")
    if region:
        meta.append(e(region))
    if timestamp:
        meta.append(f'<time datetime="{e(timestamp, quote=True)}">{e(timestamp)}</time>')
    meta_html = "".join(f"<span>{part}</span>" for part in meta)

    if related:
        related_html = "".join(
            f'<li><h3>{e(headline)}</h3>{f"<p>{e(snippet)}</p>" if snippet else ""}</li>'
            for headline, snippet in related
        )
        content = f'<section class="trend-related"><h2>عناوين مرتبطة بالموضوع</h2><ul class="trend-related-list">{related_html}</ul></section>'
    else:
        content = '<section class="trend-related"><h2>المتابعة</h2><p>لا تتوفر حاليًا عناوين خبرية مرتبطة تكفي لكتابة موجز موثق. ستظهر التفاصيل هنا عند وصول معلومات مناسبة.</p></section>'

    status = "تنبيه ترند" if alert else "موضوع رائج"
    return f'''<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#14283f"><meta name="description" content="{e(description, quote=True)}">
  <meta property="og:site_name" content="Threads Documentary News"><meta property="og:type" content="article"><meta property="og:title" content="{e(title, quote=True)}">
  <meta property="og:description" content="{e(description, quote=True)}"><meta property="og:image" content="https://sozanhnedy-bot.github.io/-/assets/brand-cover.jpg">
  <meta name="twitter:card" content="summary_large_image"><title>{e(title)} | Threads Documentary News</title>
  <link rel="icon" href="../assets/brand-mark.png" type="image/png"><link rel="stylesheet" href="../styles.css">
</head>
<body class="article-body">
  <header class="article-masthead"><a class="article-brand" href="../"><img src="../assets/brand-mark.png" alt=""><span><small>ثريدز وثائقي</small><b>Threads Documentary News</b></span></a><a class="article-home" href="../">العودة إلى الموقع</a></header>
  <main class="article-shell"><nav class="breadcrumb"><a href="../">الرئيسية</a><span aria-hidden="true">/</span><span>موجز ترند</span></nav>
    <article class="full-report trend-report"><header class="article-heading"><p class="eyebrow">{e(status)} · {e(region)}</p><h1>{e(title)}</h1>
      <p class="article-dek">هذا الموضوع يحظى باهتمام متزايد. الرصد يوضح مستوى الاهتمام والعناوين المتصلة به، ولا يُعد وحده تأكيدًا مستقلًا لكل تفاصيل الحدث.</p>
      <div class="article-meta">{meta_html}</div>
    </header>
    {content}
    <p class="trend-disclosure">موجز متابعة داخلي مبني على العناوين والمقتطفات المتاحة، ويُحدَّث مع وصول بيانات جديدة.</p>
    </article>
  </main><footer class="site-footer"><div class="footer-brand">Threads Documentary News</div><p>موجز داخلي · متابعة الموضوع داخل الموقع</p></footer>
</body></html>'''


def build(output: Path) -> int:
    data = json.loads(TRENDS_PATH.read_text(encoding="utf-8"))
    target = output / "trends"
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)
    rendered = 0
    for item in data.get("items", []):
        if not isinstance(item, dict):
            continue
        trend_id = clean(item.get("id"), 80)
        if not ID_RE.fullmatch(trend_id):
            continue
        (target / f"{trend_id}.html").write_text(render(item), encoding="utf-8")
        rendered += 1
    print(f"Rendered {rendered} internal trend pages.")
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
