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
    "index.html", "archive.html", "section.html", "videos.html", "photos.html", "tasks.html",
    "about.html", "privacy.html", "contact.html", "editorial-policy.html", "terms.html", "youtube-lab.html",
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
    render_youtube_pages(output)
    print(f"Built read-only news site in {output}")
    return 0



def render_youtube_pages(output: Path) -> None:
    """Generate 500 lightweight, independently hosted manual-control pages."""
    pages = output / "youtube-lab-pages"
    pages.mkdir(parents=True, exist_ok=True)
    template = """<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0b1220">
<title>YouTube Lab · السجل __ID__</title>
<style>
*{box-sizing:border-box}body{margin:0;padding:22px;background:#0b1220;color:#edf4ff;font:16px/1.7 system-ui,sans-serif}
main{max-width:760px;margin:auto}.panel{background:#14243a;border:1px solid #2b405b;border-radius:16px;padding:20px}
a,button{display:inline-block;border:1px solid #42617f;border-radius:10px;padding:10px 14px;color:#edf4ff;background:#203752;text-decoration:none;cursor:pointer}
input{display:block;width:100%;margin:12px 0;padding:12px;border-radius:9px;border:1px solid #42617f;background:#091525;color:white;direction:ltr}
iframe{width:100%;aspect-ratio:16/9;border:0;border-radius:12px;background:#050b13;margin-top:14px}
small{color:#a9bbd0}
</style></head><body><main><p><a href="../youtube-lab.html">العودة إلى لوحة الـ500 سجل</a></p>
<section class="panel"><small>WORLD NEWS · صفحة مستقلة مستضافة على الموقع</small>
<h1>السجل رقم __ID__</h1><p>أدخل رابط الفيديو واحفظه. لا يبدأ التشغيل إلا عند الضغط على الزر.</p>
<input id="videoUrl" type="url" placeholder="https://www.youtube.com/watch?v=...">
<button id="save">حفظ الرابط</button> <button id="play">تشغيل يدوي</button> <button id="stop">إيقاف</button>
<p id="status" aria-live="polite">جاهز</p>
<iframe id="player" title="مشغّل YouTube" src="about:blank" allow="accelerometer; encrypted-media; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
</section></main>
<script>
(function(){
const ID=__NUM__, KEY='worldnews.youtubeLab.records.v1';
function records(){try{const x=JSON.parse(localStorage.getItem(KEY)||'null');return Array.isArray(x)&&x.length===500?x:Array.from({length:500},()=>({enabled:false,videoUrl:''}));}catch(e){return Array.from({length:500},()=>({enabled:false,videoUrl:''}));}}
const list=records(), field=document.getElementById('videoUrl'), status=document.getElementById('status'), player=document.getElementById('player');
field.value=(list[ID-1]&&list[ID-1].videoUrl)||'';
function save(){list[ID-1]=Object.assign({enabled:false,videoUrl:''},list[ID-1]||{},{videoUrl:field.value.trim()});try{localStorage.setItem(KEY,JSON.stringify(list));status.textContent='تم حفظ الرابط على هذا المتصفح.';}catch(e){status.textContent='تعذر الحفظ المحلي.';}}
function getId(raw){try{const u=new URL(raw);if(u.hostname==='youtu.be')return u.pathname.split('/').filter(Boolean)[0]||'';if(u.hostname==='youtube.com'||u.hostname.endsWith('.youtube.com')||u.hostname==='www.youtube-nocookie.com'){if(u.pathname==='/watch')return u.searchParams.get('v')||'';const m=u.pathname.match(/^\\/(?:embed|shorts|live)\\/([A-Za-z0-9_-]{6,})/);return m?m[1]:'';}}catch(e){}return '';}
document.getElementById('save').addEventListener('click',save);
document.getElementById('play').addEventListener('click',()=>{save();const id=getId(field.value);if(!id){status.textContent='أدخل رابط YouTube صحيحًا.';return;}player.src='https://www.youtube-nocookie.com/embed/'+encodeURIComponent(id)+'?autoplay=1&rel=0';status.textContent='التشغيل بدأ بطلب يدوي.';});
document.getElementById('stop').addEventListener('click',()=>{player.src='about:blank';status.textContent='تم إيقاف المشغّل.';});
})();
</script></body></html>
"""
    for number in range(1, 501):
        page = template.replace("__ID__", f"{number:03d}").replace("__NUM__", str(number))
        (pages / f"{number:03d}.html").write_text(page, encoding="utf-8")


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
