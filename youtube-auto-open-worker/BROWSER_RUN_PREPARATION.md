# Cloudflare Browser Run preparation

This branch is an isolated preparation branch. The production `main` branch and
`youtube-auto-open-worker/src/index.js` are intentionally unchanged.

## Verified starting point

- Worker: `youtube-auto-open-worker`
- Entry file: `youtube-auto-open-worker/src/index.js`
- Current UI is embedded in the `PAGE` constant in that file.
- The current browser-opening logic is client-side `window.open(...)`.
- The deployed Worker already has a Browser binding named `BROWSER`.
- The existing UI accepts a YouTube URL and a tab count from 1 to 100.

## Safe implementation target

Prepare a separate Browser Run test path for ordinary web-page QA, not repeated
YouTube playback or view generation. Keep the existing UI and production entry
file untouched until a reviewed integration is explicitly chosen.

The test path should:
1. Accept only explicitly permitted test URLs (initial smoke test: `https://example.com/`).
2. Validate URL scheme and hostname server-side; reject YouTube domains and redirects to
   disallowed hosts.
3. Use Cloudflare's `BROWSER` binding with `@cloudflare/puppeteer`.
4. Create a browser session and pages only within conservative account limits.
5. Return page titles/statuses for QA rather than generating engagement.
6. Close pages and the browser in `finally`, including on errors or cancellation.
7. Require an authenticated request before any browser work.
8. Keep this test route isolated from the existing public homepage.

## Current status

- [x] Created isolated branch `cloudflare-browser-run-prep`.
- [x] Inspected the current entry file and confirmed the local `window.open` loop.
- [x] Confirmed the existing `BROWSER` binding is configured on the deployed Worker.
- [ ] Inspect the project's dependency/configuration files before adding Puppeteer.
- [ ] Add and test an isolated QA endpoint using permitted non-YouTube test URLs.
- [ ] Review costs, concurrency limits, authentication, and cleanup.
- [ ] Only after review, decide whether to integrate any safe QA functionality.

## Important

No production file was edited and nothing was deployed. This branch does not
implement automated multi-tab YouTube playback. Cloudflare Browser Run's current
Puppeteer guide: https://developers.cloudflare.com/browser-run/puppeteer/
