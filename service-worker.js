const CACHE_NAME = "threads-documentary-v1";
const ROOT = new URL("./", self.location.href);
const NEWS_URL = new URL("data/news.json", ROOT).href;
const SHELL_URLS = [
  ROOT.href,
  new URL("index.html", ROOT).href,
  new URL("styles.css", ROOT).href,
  new URL("app.js", ROOT).href,
  new URL("manifest.webmanifest", ROOT).href,
  new URL("assets/channel-logo.png", ROOT).href,
  new URL("assets/app-icon-192.png", ROOT).href,
  new URL("assets/app-icon-512.png", ROOT).href,
  NEWS_URL,
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;

  if (request.url === NEWS_URL) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
          }
          return response;
        })
        .catch(async () => (await caches.match(request)) || new Response("{\"schema_version\":1,\"updated_at\":null,\"items\":[]}", {
          headers: { "Content-Type": "application/json; charset=utf-8" },
        }))
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
        }
        return response;
      });
    })
  );
});
