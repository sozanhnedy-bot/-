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
- [x] Add an isolated QA Worker project in `browser-qa-worker/`, with token authentication and an `example.com`-only URL allowlist.
- [x] Verify Browser Run Quick Action against `https://example.com/` (HTTP 200, title `Example Domain`).
- [ ] Run the new Worker-bound Playwright script end-to-end; the first direct custom-script API attempt timed out after 30 seconds, so this remains unverified.
- [x] Add server-side bearer-token authentication, single-page execution, redirect blocking, and browser cleanup in `finally`.
- [ ] Review account-specific costs and limits before deployment.
- [ ] Configure the `QA_TOKEN` secret and deploy the separate QA Worker only after the owner is ready; production Worker remains untouched.

## Important

No production file was edited and nothing was deployed. The isolated QA Worker code is committed on this branch but is not yet deployed or end-to-end tested. This branch does not implement automated multi-tab YouTube playback. Cloudflare Browser Run's current
Puppeteer guide: https://developers.cloudflare.com/browser-run/puppeteer/
