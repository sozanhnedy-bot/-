# Loka Cloud Browser QA Worker

An isolated Cloudflare Worker for a single-page browser QA smoke test. It does not modify or import the existing YouTube Auto Open Worker.

## Safety defaults

- Only `https://example.com/` is allowed.
- HTTPS is required; credentials and non-default ports are rejected.
- The server validates the URL before browser execution.
- The endpoint requires an `Authorization: Bearer ...` token stored as the Worker secret `QA_TOKEN`.
- One page is tested per request. There is no bulk tab or playback feature.
- Browser sessions are closed in the script's `finally` block.
- The UI does not persist the token.

## Deploy as a separate Worker

From this directory, use Wrangler:

```sh
npx wrangler secret put QA_TOKEN
npx wrangler deploy
```

Use a long random secret and do not commit it to Git. The separate Worker name is `loka-cloud-browser-qa`; its route and deployment do not replace `youtube-auto-open-worker`.

## Test

Open the new Worker URL, enter the same secret configured as `QA_TOKEN`, and run the default URL. A successful result should report:

- `ok: true`
- `url: https://example.com/`
- `title: Example Domain`
- `httpStatus: 200`
- `host: example.com`

Requests to YouTube or any other host are rejected by server-side validation.

## Repository scope

This directory is a standalone QA project. The original `youtube-auto-open-worker/src/index.js` and production `main` branch are not changed by this addition.
