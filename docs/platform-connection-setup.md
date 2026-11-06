# Loka AI Newsroom — platform connection setup

Add credentials only in the repository's GitHub Actions secrets. Never commit tokens, API keys, cookies, or passwords to source files.

Repository: `sozanhnedy-bot/-`

Open **Settings → Secrets and variables → Actions → New repository secret**.

## Currently supported connectors

### YouTube Data API
Create or select a Google Cloud project, enable **YouTube Data API v3**, and create an API key.

Add this repository secret:

- `YOUTUBE_DATA_API_KEY` — the API key.

The radar requests the official `videos.list` mostPopular chart for Egypt (`regionCode=EG`). This is a country-level popular-video chart, not a complete TikTok-style sound or topic trend feed. Protect and restrict the key in Google Cloud where possible.

### Facebook Page
Use a Page that you administer and an app/token authorized for that Page. The connector only requests Page posts available to that token; it does not read private groups, personal profiles, or arbitrary Facebook content.

Add these repository secrets:

- `FACEBOOK_PAGE_ID` — numeric Page ID.
- `FACEBOOK_PAGE_ACCESS_TOKEN` — Page access token with the required Page read permissions approved for the app.
- `FACEBOOK_GRAPH_API_VERSION` — explicit Graph API version, for example the currently supported version shown in your Meta app dashboard (format `vNN.N`).

Do not send the token in chat. Review Meta's current permissions and app-mode requirements in the app dashboard.

## Sources that currently need no credentials

- Google Trends RSS: Egypt, Saudi Arabia, UAE, and worldwide (US feed as the current worldwide proxy).
- X: public country trend pages via Trends24. This is a third-party public signal, not official X API access.

## Not connected yet

- TikTok: no TikTok collection is enabled yet. TikTok's approved API products and research access have eligibility and approval limits; a normal account or developer app alone does not guarantee access to global trending topics.
- Official X API: not wired to this collector. Public Trends24 pages are the current fallback. Official API products and pricing/permissions must be checked in the X developer portal before implementation.

## Validation and safety

After adding secrets, run **Actions → Refresh trend radar → Run workflow** on the development branch. Inspect the generated `data/trends.json` and the run logs for the `sources_checked` list and warnings. Secrets are never printed by the workflow.

Do not enable live social publishing as part of data-source setup. Collection permissions do not grant permission to republish media. Every media item remains subject to rights and editorial review.
