const CACHE_NAME = "world-news-v5-rss-image-fix";
const ROOT = new URL("./", self.location.href);
const DATA_URLS = ["data/news.json", "data/reports.json"].map((path) => new URL(path, ROOT).href);
const SHELL_URLS = [
  ROOT.href,
  new URL("index.html", ROOT).href,
  new URL("archive.html", ROOT).href,
  new URL("section.html", ROOT).href,
  new URL("videos.html", ROOT).href,
  new URL("styles.css", ROOT).href,
  new URL("app.js", ROOT).href,
  new URL("archive.js", ROOT).href,
  new URL("section.js", ROOT).href,
  new URL("videos.js", ROOT).href,
  new URL("article.js", ROOT).href,
  new URL("manifest.webmanifest", ROOT).href,
  new URL("assets/brand-mark.png", ROOT).href,
  new URL("assets/brand-cover.jpg", ROOT).href,
  new URL("assets/app-icon-192.png", ROOT).href,
  new URL("assets/app-icon-512.png", ROOT).href,
  new URL("assets/story-finswimming.webp", ROOT).href,
  new URL("assets/story-morocco-stadium.webp", ROOT).href,
  new URL("assets/story-yemen-diplomacy.webp", ROOT).href,
  ...DATA_URLS,
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_URLS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;
  const key = new URL(request.url).href.split("?")[0];
  if (DATA_URLS.includes(key)) {
    event.respondWith(fetch(request).then((response) => {
      if (response.ok) caches.open(CACHE_NAME).then((cache) => cache.put(request, response.clone()));
      return response;
    }).catch(async () => (await caches.match(request)) || new Response("{}", {headers:{"Content-Type":"application/json; charset=utf-8"}})));
    return;
  }
  event.respondWith(caches.match(request).then((cached) => cached || fetch(request).then((response) => {
    if (response.ok) caches.open(CACHE_NAME).then((cache) => cache.put(request, response.clone()));
    return response;
  })));
});
