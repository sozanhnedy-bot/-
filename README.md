# Threads Documentary News

Arabic editorial news portal with internally rendered reports, scheduled RSS and trend snapshots, and an installable progressive web app. The project has its own copper-and-navy identity and is not an official Meta or Threads service.

## Current features

- Arabic responsive site with original local branding, editorial images, and internal report pages generated from `data/reports.json`; readers are not redirected to source articles.
- Original Arabic/English logo assets now provide the homepage cover, Open Graph previews, PWA icons, and the Android launcher icon. Trend-digest posts use the branded cover; report posts retain their story image and carry the channel name in the caption.
- RSS items without a completed editorial report receive a clearly labeled internal brief page; they are not shown as full reports and do not link readers to the publisher.
- Each report can include a validated YouTube video ID. The privacy-enhanced player loads only after the reader taps the button, and the internal short alias stays on this site.
- GitHub Actions checks RSS and Google Trends twice hourly on a best-effort schedule. The public site's standard GitHub Pages and Actions usage is free; scheduled OpenAI summaries and X Trends API calls are explicitly disabled to avoid billable requests. GitHub may delay scheduled runs, while the last published site remains available.
- The Android companion WebView displays the live site and offers opt-in trend notifications with a short custom chime every ten minutes while the app is open. Background alerts require a separate push service and are not enabled yet.
- A PWA manifest and service worker enable installation from a compatible mobile browser and basic offline access.
- The site and Android task assistant dispatch bounded code tasks to GitHub Actions. Fixed web checks and an Android build run before allowed UI changes are committed; source changes do not replace an installed APK.
- A separate daily X/Facebook publisher is disabled unless the owner sets both `SOCIAL_AUTOPUBLISH=true` and `PAID_SOCIAL_APIS_APPROVED=true` and supplies account credentials. YouTube is not auto-published; owner approval and Google OAuth setup are still required.

## Repository layout

- `index.html`, `styles.css`, `app.js`: Arabic homepage and client behavior.
- `article.js`, `scripts/render_articles.py`, `scripts/render_news.py`: internal full reports and clearly labeled RSS-only brief pages.
- `data/`: public news, reports, trend snapshots, and configured RSS feed list.
- `scripts/update_news.py`, `scripts/update_trends.py`, `scripts/update_threads_signals.py`: scheduled data collection.
- `scripts/publish_social.py`: guarded daily Facebook/X publishing; disabled unless the owner enables it.
- `scripts/build_site.py`: creates the complete static GitHub Pages directory.
- `assets/` and `docs/brand-assets.md`: original brand masters plus optimized web/app derivatives and their intended uses.
- `android-app/`: native Android companion source, including the in-app task workspace.
- `.github/workflows/`: Pages deployment, scheduled refresh, daily social publishing, and guarded owner task execution.

## Local checks

```bash
python -m unittest discover -s tests -v
node --check app.js
node --check article.js
python scripts/build_site.py --output /tmp/threads-news-site
```

## Setup

See `SETUP_AR.md` for publishing and automation settings. The website's scheduled RSS and Google Trends updates do not use an OpenAI API key. The optional code-task assistant can use OpenAI only when the owner submits a task and may incur separate API billing; X/social posting also remains opt-in and may be metered.

The Android application is built from `android-app/`. A test APK is delivered separately; do not commit an APK or signing key into this public repository. Production signing and secure updates require a stable owner-controlled signing key.
