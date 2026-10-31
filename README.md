# Threads Documentary News

Arabic editorial news portal with internally rendered reports, scheduled RSS and trend snapshots, and an installable progressive web app. The project has its own copper-and-navy identity and is not an official Meta or Threads service.

## Current features

- Arabic responsive site with original local branding, editorial images, and internal report pages generated from `data/reports.json`; readers are not redirected to source articles.
- RSS items without a completed editorial report receive a clearly labeled internal brief page; they are not shown as full reports and do not link readers to the publisher.
- Each report can include a validated YouTube video ID. The privacy-enhanced player loads only after the reader taps the button, and the internal short alias stays on this site.
- GitHub Actions refreshes RSS and Google Trends every ten minutes. Optional X Trends require a read token and the explicit `X_TRENDS_ENABLED=true` repository variable. GitHub may delay scheduled runs.
- The Android companion WebView displays the live site and offers opt-in trend notifications with a short custom chime every ten minutes while the app is open. Background alerts require a separate push service and are not enabled yet.
- A PWA manifest and service worker enable installation from a compatible mobile browser and basic offline access.
- The site and Android task assistant dispatch bounded code tasks to GitHub Actions. Fixed web checks and an Android build run before allowed UI changes are committed; source changes do not replace an installed APK.
- A separate daily X/Facebook publisher is disabled until the owner sets `SOCIAL_AUTOPUBLISH=true` and supplies account credentials. It uses the report text, image, and internal link, or a factual trend digest. YouTube is not auto-published; owner approval and Google OAuth setup are still required.

## Repository layout

- `index.html`, `styles.css`, `app.js`: Arabic homepage and client behavior.
- `article.js`, `scripts/render_articles.py`, `scripts/render_news.py`: internal full reports and clearly labeled RSS-only brief pages.
- `data/`: public news, reports, trend snapshots, and configured RSS feed list.
- `scripts/update_news.py`, `scripts/update_trends.py`, `scripts/update_threads_signals.py`: scheduled data collection.
- `scripts/publish_social.py`: guarded daily Facebook/X publishing; disabled unless the owner enables it.
- `scripts/build_site.py`: creates the complete static GitHub Pages directory.
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

See `SETUP_AR.md` for publishing and automation settings. The task assistant requires a GitHub Actions secret named `OPENAI_API_KEY`, a repository variable named `OPENAI_MODEL`, and a narrowly scoped GitHub fine-grained token entered only when dispatching a task. OpenAI API usage may be billed separately from a ChatGPT subscription. X API calls may also be metered; do not enable X trends or social publishing until you have reviewed the current price and created the required app credentials.

The Android application is built from `android-app/`. A test APK is delivered separately; do not commit an APK or signing key into this public repository. Production signing and secure updates require a stable owner-controlled signing key.
