"""Build the static Pages directory from the checked-in public news data."""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROOT_FILES = (
    "index.html", "archive.html", "section.html", "videos.html",
    "app.js", "archive.js", "section.js", "videos.js", "article.js",
    "styles.css", "styles-newspaper-overrides.css", "manifest.webmanifest", "service-worker.js",
)
DATA_FILES = ("news.json", "reports.json")
ASSET_FILES = (
    "brand-mark.png", "app-icon-192.png", "app-icon-512.png",
    "brand-cover.jpg",
    "portal-interface-banner.jpg",
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
        shutil.copy2(ROOT / "data" / name, output / "data" / name)
    for name in ASSET_FILES:
        shutil.copy2(ROOT / "assets" / name, output / "assets" / name)
    shutil.copytree(ROOT / "assets" / "news-illustrations", output / "assets" / "news-illustrations", dirs_exist_ok=True)

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
