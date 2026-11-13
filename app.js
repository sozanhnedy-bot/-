const REFRESH_MS = 10 * 60 * 1000;
const HOME_WINDOW_MS = 3 * 24 * 60 * 60 * 1000;
const CATEGORY_LABELS = {
  egypt: "مصر", arab: "العالم العربي", world: "العالم",
  politics: "سياسة", economy: "اقتصاد", technology: "تكنولوجيا", sports: "رياضة",
  reports: "تقارير ووثائقيات", videos: "فيديوهات",
};
const REGION_LABELS = { egypt: "مصر", arab: "العالم العربي", world: "العالم" };
const HOME_CATEGORIES = [
  { id: "egypt", title: "مصر" }, { id: "arab", title: "العالم العربي" }, { id: "world", title: "العالم" },
  { id: "politics", title: "سياسة" }, { id: "economy", title: "اقتصاد" },
  { id: "technology", title: "تكنولوجيا" }, { id: "sports", title: "رياضة" },
];
const state = { news: [], reports: [], query: "", region: "all", initialLoad: true, featuredIds: new Set() };
const reportMap = new Map();
const $ = (selector, root = document) => root.querySelector(selector);

function el(tag, className = "", text = "") {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== "") node.textContent = text;
  return node;
}
function text(value, limit = 900) { return typeof value === "string" ? value.trim().slice(0, limit) : ""; }
function validId(value) { return /^[A-Za-z0-9_-]{1,80}$/.test(String(value || "")); }
function normalize(value) { return String(value || "").normalize("NFKC").replace(/[\u064B-\u065F\u0670]/g, "").toLocaleLowerCase("ar"); }
function safeImage(value) {
  const raw = text(value, 800);
  if (/^assets\/[A-Za-z0-9._/-]+$/.test(raw) && !raw.includes("..")) return `./${raw}`;
  try {
    const url = new URL(raw);
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    const googleImage = host === "google.com" || host.endsWith(".google.com")
      || host.endsWith(".googleusercontent.com") || host.endsWith(".gstatic.com");
    const trustedNewsThumbnail = host === "lh3.googleusercontent.com";
    if (url.protocol === "https:" && url.hostname.includes(".") && !url.username && !url.password && (!googleImage || trustedNewsThumbnail)) return url.href;
  } catch (_) { }
  return "";
}
function stripSourceSuffix(titleValue, sourceValue) {
  const title = text(titleValue, 500);
  const source = text(sourceValue, 160);
  if (!source) return title;
  for (const separator of [" — ", " – ", " - ", " | ", " :: "]) {
    const index = title.lastIndexOf(separator);
    if (index > 0 && normalize(title.slice(index + separator.length)) === normalize(source)) return title.slice(0, index).trim();
  }
  return title;
}
function removePromotion(value) {
  return text(value, 1_200)
    .replace(/\s*(?:🔴\s*)?(?:تابعوا|تابع|اقرأ المزيد|قراءة المزيد|عرض المزيد من العناوين|View more headlines on Google News).*$/iu, "")
    .replace(/[\s…\.]+$/u, "")
    .trim();
}
function editorialTitle(value, source) {
  return stripSourceSuffix(removePromotion(value), source) || text(value, 500);
}
function comparableCopy(value) {
  return normalize(value).replace(/[^\p{L}\p{N}\s]/gu, " ").replace(/\s+/g, " ").trim();
}
function distinctSummary(value, title, source) {
  const summary = removePromotion(value);
  const summaryKey = comparableCopy(stripSourceSuffix(summary, source));
  const titleKey = comparableCopy(title);
  if (!summaryKey || summaryKey === titleKey) return "";
  if (summaryKey.startsWith(titleKey)) {
    const titleWords = titleKey.split(" ").filter(Boolean).length;
    const summaryWords = summaryKey.split(" ").filter(Boolean).length;
    if (summaryWords <= titleWords + 2 && !/\d/u.test(summary)) return "";
  }
  return summary;
}
function recordFor(item) {
  if (!item || !validId(item.id)) return null;
  const report = reportMap.get(item.id) || null;
  const region = text(item.region, 30).toLowerCase();
  const category = text(report?.category || item.category, 30).toLowerCase() || region || "world";
  const sourceName = report && Array.isArray(report.sources)
    ? report.sources.map((value) => text(value, 160)).filter(Boolean).join("، ")
    : text(item.source_name, 160);
  const title = editorialTitle(report?.title || item.title, sourceName);
  const primarySummary = distinctSummary(
    report?.dek || (item.summary_type === "source_notice" ? item.excerpt : item.summary),
    title,
    sourceName,
  );
  const articleSummary = !report ? distinctSummary(item.article_content, title, sourceName) : "";
  // Never present a section illustration as if it were a photograph of this story.
  // Show an image only when the source/report supplied a real image URL.
  const suppliedImage = safeImage(report?.image || item.image_url);
  const image = suppliedImage;
  const categoryLabel = CATEGORY_LABELS[category] || REGION_LABELS[region] || "العالم";
  return {
    id: item.id,
    report,
    title,
    summary: primarySummary || articleSummary,
    date: text(item.published_at || report?.published_at || item.created_at, 80),
    sourceName,
    sourceUrl: text(item.source_url || report?.source_url, 800),
    image,
    imageAlt: suppliedImage ? text(report?.image_alt || item.image_alt || item.title, 300) : "",
    imageCaption: suppliedImage ? text(report?.image_caption || item.image_caption, 300) : "",
    region,
    category,
    summaryType: report ? "editorial_report" : text(item.summary_type, 40),
    breaking: item.breaking === true,
    href: report ? `./reports/${item.id}.html` : `./news/${item.id}.html`,
    youtubeId: text(report?.youtube_id, 20),
  };
}
function allRecords() {
  const items = state.news.map(recordFor).filter(Boolean);
  const ids = new Set(items.map((item) => item.id));
  state.reports.forEach((report) => {
    if (!report || !validId(report.id) || ids.has(report.id)) return;
    items.push(recordFor({ ...report, region: report.region || "world", category: "reports", created_at: report.published_at || "" }));
  });
  return items.filter(Boolean).sort((a, b) => (Date.parse(b.date) || 0) - (Date.parse(a.date) || 0));
}
function recentRecords(records) {
  const cutoff = Date.now() - HOME_WINDOW_MS;
  return records.filter((record) => {
    const stamp = Date.parse(record.date);
    return Number.isFinite(stamp) && stamp >= cutoff;
  });
}
function formatDate(value, withTime = false) {
  if (!value) return "التاريخ غير متاح";
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return "التاريخ غير متاح";
  return new Intl.DateTimeFormat("ar-EG", withTime ? { dateStyle: "medium", timeStyle: "short" } : { dateStyle: "medium" }).format(date);
}
function addImage(container, record, className = "portal-card-image") {
  if (!record.image) return;
  const figure = el("figure", className);
  const img = document.createElement("img");
  img.src = record.image;
  img.alt = record.imageAlt || record.title;
  img.loading = "lazy";
  img.decoding = "async";
  img.addEventListener("error", () => figure.remove(), { once: true });
  figure.append(img);
  if (record.imageCaption) figure.append(el("figcaption", "portal-image-caption", record.imageCaption));
  container.append(figure);
}
function sourceMeta(record) {
  const meta = el("div", "portal-card-meta");
  const category = CATEGORY_LABELS[record.category] || REGION_LABELS[record.region] || "خبر";
  meta.append(el("span", "portal-category-label", category));
  meta.append(el("time", "portal-date", formatDate(record.date)));
  if (record.sourceName) meta.append(el("span", "portal-source", `المصدر: ${record.sourceName}`));
  return meta;
}
function articleCard(record, compact = false) {
  const card = el("article", compact ? "portal-news-item" : "portal-story-card");
  if (record.image) {
    const linkImage = document.createElement("a");
    linkImage.className = "portal-card-thumb-link";
    linkImage.href = record.href;
    linkImage.setAttribute("aria-label", `افتح: ${record.title}`);
    addImage(linkImage, record, "portal-card-thumb");
    card.append(linkImage);
  }
  const body = el("div", "portal-card-body");
  body.append(sourceMeta(record));
  const title = el("h3", compact ? "portal-news-title" : "portal-card-title");
  const titleLink = document.createElement("a");
  titleLink.href = record.href;
  titleLink.textContent = record.title;
  title.append(titleLink);
  body.append(title);
  if (record.summary) body.append(el("p", "portal-card-summary", record.summary));
  const kind = record.report ? "تقرير تحريري" : "خبر";
  const foot = el("div", "portal-card-foot");
  foot.append(el("span", "portal-content-kind", kind));
  const open = document.createElement("a");
  open.className = "portal-open-link";
  open.href = record.href;
  open.textContent = record.report ? "اقرأ التقرير" : "اقرأ التفاصيل";
  foot.append(open);
  body.append(foot);
  card.append(body);
  return card;
}
function renderLead(records) {
  const host = $("#top-stories");
  host.replaceChildren();
  const featured = records.slice(0, 3);
  state.featuredIds = new Set(featured.map((record) => record.id));
  const lead = featured[0];
  if (!lead) { host.append(el("p", "loading-card", "لا توجد أخبار حديثة الآن. الأخبار السابقة محفوظة في الأرشيف.")); return; }
  const card = el("article", `lead-story${lead.image ? " has-image" : " text-only"}`);
  if (lead.image) addImage(card, lead, "lead-story-image");
  const body = el("div", "lead-story-copy");
  body.append(sourceMeta(lead));
  body.append(el("h2", "lead-story-title", lead.title));
  if (lead.summary) body.append(el("p", "lead-story-summary", lead.summary));
  if (lead.report) body.append(el("p", "lead-story-editorial-note", "تقرير تحريري داخل World News"));
  const link = document.createElement("a");
  link.className = "lead-story-link";
  link.href = lead.href;
  link.textContent = lead.report ? "اقرأ التقرير داخل الموقع" : "اقرأ التفاصيل داخل الموقع";
  body.append(link);
  card.append(body);
  host.append(card);
  if (featured.length > 1) {
    const rail = el("aside", "lead-story-rail");
    rail.setAttribute("aria-label", "أبرز الأخبار الأخرى");
    rail.append(el("h2", "lead-rail-heading", "أبرز الأخبار"));
    featured.slice(1).forEach((record) => {
      const item = articleCard(record, true);
      item.classList.add("lead-rail-item");
      rail.append(item);
    });
    host.append(rail);
  }
}
function matches(record) {
  if (state.region !== "all" && record.region !== state.region) return false;
  if (!state.query) return true;
  const haystack = normalize([record.title, record.summary, record.sourceName, record.category, record.region].join(" "));
  return haystack.includes(state.query);
}
function renderLatest(records) {
  const filtered = records.filter(matches);
  const host = $("#news-list");
  host.replaceChildren();
  const excludedIds = state.region === "all" && !state.query ? state.featuredIds : new Set();
  const recent = filtered.filter((record) => !excludedIds.has(record.id)).slice(0, 12);
  recent.forEach((record) => host.append(articleCard(record, true)));
  $("#feed-count").textContent = `${filtered.length.toLocaleString("ar-EG")} مادة خلال آخر 72 ساعة؛ تُعرض أحدث 12 مادة في هذه القائمة.`;
  $("#empty-state").hidden = filtered.length > 0;
}
function renderSections(records) {
  const host = $("#category-sections");
  host.replaceChildren();
  HOME_CATEGORIES.forEach(({ id, title }) => {
    const entries = records.filter((record) => (record.category === id || record.region === id) && (!state.query || matches(record))).slice(0, 3);
    if (!entries.length) return;
    const section = el("section", "portal-category-section");
    section.id = `category-${id}`;
    const heading = el("div", "portal-section-title");
    const titleBox = el("div");
    titleBox.append(el("p", "eyebrow", "آخر التغطيات المنشورة"), el("h2", "", title));
    heading.append(titleBox);
    const more = document.createElement("a");
    more.className = "text-link";
    more.href = `./section.html?category=${encodeURIComponent(id)}`;
    more.textContent = "عرض القسم ←";
    heading.append(more);
    section.append(heading);
    const grid = el("div", "portal-card-grid");
    entries.forEach((record) => grid.append(articleCard(record)));
    section.append(grid);
    host.append(section);
  });
}
function renderReportsAndVideos(records) {
  const reports = records.filter((record) => record.report);
  const reportHost = $("#reports-list");
  reportHost.replaceChildren();
  reports.slice(0, 3).forEach((record) => reportHost.append(articleCard(record)));
  $("#reports-section").hidden = reports.length === 0;
  const videos = reports.filter((record) => /^[A-Za-z0-9_-]{11}$/.test(record.youtubeId));
  const videoHost = $("#videos-list");
  videoHost.replaceChildren();
  videos.slice(0, 3).forEach((record) => videoHost.append(articleCard(record)));
  $("#videos-section").hidden = videos.length === 0;
}
function renderBreaking(records) {
  const sixHoursAgo = Date.now() - 6 * 60 * 60 * 1000;
  const item = records.find((record) => record.breaking && Date.parse(record.date) >= sixHoursAgo);
  const strip = $("#breaking-strip");
  if (!item || !strip) { if (strip) strip.hidden = true; return; }
  const link = $("#breaking-link");
  link.textContent = item.title;
  link.href = item.href;
  strip.hidden = false;
}
function render() {
  const records = recentRecords(allRecords());
  renderLead(records);
  renderLatest(records);
  renderSections(records);
  renderReportsAndVideos(records);
  renderBreaking(records);
}
async function fetchJson(path) {
  const response = await fetch(`${path}?v=${Date.now()}`, { cache: "no-store" });
  if (!response.ok) throw new Error(`${path}: ${response.status}`);
  return response.json();
}
async function loadData() {
  try {
    const [news, reports] = await Promise.all([fetchJson("./data/news.json"), fetchJson("./data/reports.json")]);
    state.news = Array.isArray(news.items) ? news.items : [];
    state.reports = Array.isArray(reports.items) ? reports.items : [];
    reportMap.clear();
    state.reports.forEach((report) => { if (report && validId(report.id)) reportMap.set(report.id, report); });
    const updated = news.updated_at ? formatDate(news.updated_at, true) : "غير متاح";
    $("#update-status").textContent = `تحديث بيانات الأخبار: ${updated}`;
    $("#last-updated").textContent = `آخر استيراد للخلاصات: ${updated}`;
    $("#edition-date").textContent = new Intl.DateTimeFormat("ar-EG", { dateStyle: "full" }).format(new Date());
    render();
    state.initialLoad = false;
  } catch (error) {
    $("#update-status").textContent = "تعذر تحديث الأخبار الآن";
    if (state.initialLoad) $("#top-stories").replaceChildren(el("p", "loading-card", "تعذر تحميل الأخبار مؤقتًا. أعد المحاولة بعد قليل."));
    console.error("Could not load news data", error);
  }
}

document.querySelectorAll(".filter-button").forEach((button) => button.addEventListener("click", () => {
  state.region = button.dataset.region || "all";
  document.querySelectorAll(".filter-button").forEach((candidate) => {
    const active = candidate === button;
    candidate.classList.toggle("is-active", active);
    candidate.setAttribute("aria-pressed", String(active));
  });
  render();
}));
$("#search-input").addEventListener("input", (event) => { state.query = normalize(event.target.value.trim()); render(); });
$(".portal-search").addEventListener("submit", (event) => event.preventDefault());
loadData();
window.setInterval(loadData, REFRESH_MS);
if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
  window.addEventListener("load", () => navigator.serviceWorker.register("./service-worker.js").catch((error) => console.warn("Offline support unavailable", error)));
}
