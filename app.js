const REGION_LABELS = { world: "العالم", arab: "العالم العربي", egypt: "مصر" };
const REFRESH_MS = 10 * 60 * 1000;
const state = { news: [], reports: [], trends: [], region: "all", query: "", initialLoad: true, alertEnabled: localStorage.getItem("wdw-alerts") === "on" };
const $ = (selector) => document.querySelector(selector);
const newsList = $("#news-list");
const emptyState = $("#empty-state");
const reportMap = new Map();

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function formatDate(value, options = { dateStyle: "medium", timeStyle: "short" }) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("ar-EG", options).format(date);
}
function reportUrl(id) { return `./reports/${encodeURIComponent(id)}.html`; }
function shortReportUrl(id) {
  const ids = state.reports.map((report) => String(report.id || ""));
  let length = 6;
  while (length < id.length && ids.some((other) => other !== id && other.startsWith(id.slice(0, length)))) length += 2;
  return `./r/${encodeURIComponent(id.slice(0, length))}.html`;
}
function newsUrl(id) { return `./news/${encodeURIComponent(id)}.html`; }
function imageUrl(report) { return `./${String(report.image || "").replace(/^\.\//, "")}`; }
function sourceLine(report) { return `المصادر: ${(report.sources || []).join("، ") || "مصادر الخبر"}`; }
function readItems() {
  return state.news.filter((item) => reportMap.has(item.id)).sort((a, b) => new Date(b.published_at || b.created_at || 0) - new Date(a.published_at || a.created_at || 0));
}
function readBriefItems() {
  const query = state.query.trim().toLocaleLowerCase("ar");
  return state.news.filter((item) => {
    if (reportMap.has(item.id) || !/^[A-Za-z0-9_-]{1,80}$/.test(String(item.id || ""))) return false;
    const text = `${item.title || ""} ${item.summary || ""}`.toLocaleLowerCase("ar");
    return (state.region === "all" || item.region === state.region) && (!query || text.includes(query));
  }).sort((a, b) => new Date(b.published_at || b.created_at || 0) - new Date(a.published_at || a.created_at || 0));
}
function visibleItems() {
  const query = state.query.trim().toLocaleLowerCase("ar");
  return readItems().filter((item) => {
    const report = reportMap.get(item.id);
    const text = `${report.title} ${report.dek} ${(report.sections || []).flatMap((section) => section.paragraphs || []).join(" ")}`.toLocaleLowerCase("ar");
    return (state.region === "all" || item.region === state.region) && (!query || text.includes(query));
  });
}
function makeStoryLink(item, report, className) {
  const link = el("a", className);
  link.href = reportUrl(report.id);
  link.setAttribute("aria-label", `اقرأ التقرير: ${report.title}`);
  return link;
}
function renderHero() {
  const host = $("#top-stories");
  host.replaceChildren();
  const items = readItems();
  if (!items.length) { host.append(el("p", "loading-card", "لا توجد تقارير مكتملة منشورة حتى الآن.")); return; }
  const first = items[0], report = reportMap.get(first.id);
  const hero = makeStoryLink(first, report, "hero-story");
  const image = document.createElement("img");
  image.src = imageUrl(report); image.alt = report.image_alt || ""; image.loading = "eager"; image.fetchPriority = "high";
  const copy = el("div", "hero-copy");
  copy.append(el("p", "eyebrow", "التقرير الأبرز"));
  copy.append(el("h2", "", report.title));
  copy.append(el("p", "", report.dek));
  const meta = el("div", "story-meta");
  meta.append(el("span", "pill", REGION_LABELS[first.region] || "تقرير"));
  meta.append(el("time", "", formatDate(first.published_at || first.created_at)));
  meta.append(el("span", "", sourceLine(report)));
  copy.append(meta);
  copy.append(el("span", "hero-link", "اقرأ التقرير كاملًا"));
  hero.append(image, copy);
  host.append(hero);

  const side = el("aside", "hero-side");
  side.append(el("h2", "side-heading", "تقارير أخرى"));
  for (const item of items.slice(1, 4)) {
    const story = reportMap.get(item.id), link = makeStoryLink(item, story, "side-story");
    const thumb = document.createElement("img"); thumb.src = imageUrl(story); thumb.alt = ""; thumb.loading = "lazy";
    const text = document.createElement("span");
    text.append(el("time", "", formatDate(item.published_at || item.created_at, { dateStyle: "medium" })));
    text.append(el("h3", "", story.title));
    link.append(thumb, text); side.append(link);
  }
  if (items.length < 2) side.append(el("p", "muted-label", "تُضاف التقارير الجديدة تلقائيًا بعد اكتمال تحريرها."));
  host.append(side);
}
function trendUrl(id) { return `./trends/${encodeURIComponent(id)}.html`; }
function renderTrends() {
  const host = $("#trending-list"); host.replaceChildren();
  const trends = [...state.trends].sort((a, b) => (b.score || 0) - (a.score || 0)).slice(0, 9);
  const stamp = state.trendUpdated ? formatDate(state.trendUpdated) : "";
  $("#trend-updated").textContent = stamp ? `آخر رصد: ${stamp}` : "تُحدّث الترندات دوريًا";
  if (!trends.length) { host.append(el("p", "loading-card", "لا توجد ترندات حديثة متاحة في آخر رصد. ستظهر العناصر الجديدة هنا تلقائيًا.")); return; }
  for (const [index, trend] of trends.entries()) {
    if (!/^[a-f0-9]{18}$/.test(String(trend.id || ""))) continue;
    const item = el("article", "trend-item");
    item.append(el("span", "trend-number", String(index + 1).padStart(2, "0")));
    const body = document.createElement("div");
    const report = (trend.report_id && reportMap.get(trend.report_id)) || null;
    const destination = report ? reportUrl(report.id) : trendUrl(trend.id);
    body.append(el("span", "trend-label", trend.alert ? "تنبيه ترند" : "ترند حديث"));
    const heading = el("h3");
    const title = el("a", "trend-title-link", trend.title || "موضوع رائج");
    title.href = destination;
    title.setAttribute("aria-label", `افتح المحتوى الداخلي: ${trend.title || "موضوع رائج"}`);
    heading.append(title);
    body.append(heading);
    const rawRegion = String(trend.region_label || "").replace(/^(?:X|Google Trends)\s*[·-]\s*/i, "").trim();
    const region = REGION_LABELS[trend.region] || rawRegion || "مصر";
    const detail = [trend.approx_traffic ? `اهتمام تقريبي: ${trend.approx_traffic}` : "رصد جديد", region, trend.published_at ? formatDate(trend.published_at, { dateStyle: "short", timeStyle: "short" }) : ""].filter(Boolean).join(" · ");
    body.append(el("p", "", detail));
    const relatedTitles = [...new Set((trend.news_items || []).map((news) => String(news?.title || "").trim()).filter(Boolean))].slice(0, 2);
    if (relatedTitles.length) body.append(el("p", "trend-related-preview", `عناوين مرتبطة: ${relatedTitles.join(" · ")}`));
    const link = el("a", "read-link", "افتح المحتوى داخل الموقع"); link.href = destination; body.append(link);
    item.append(body); host.append(item);
  }
}
function makeCard(item, index) {
  const report = reportMap.get(item.id);
  const card = el("article", "news-card"); card.style.animationDelay = `${Math.min(index * 35, 210)}ms`;
  const link = makeStoryLink(item, report, "card-report-link");
  const image = document.createElement("img"); image.className = "card-image"; image.src = imageUrl(report); image.alt = report.image_alt || ""; image.loading = "lazy";
  const body = el("div", "card-body");
  const meta = el("div", "card-meta"); meta.append(el("span", "region-tag", REGION_LABELS[item.region] || "خبر")); meta.append(el("time", "", formatDate(item.published_at || item.created_at)));
  body.append(meta, el("h3", "news-title", report.title), el("p", "news-summary", report.dek), el("p", "card-sources", sourceLine(report)));
  const actions = el("div", "card-actions");
  const read = el("a", "read-link", "التفاصيل داخل الموقع"); read.href = reportUrl(report.id);
  const share = el("button", "share-button", "مراجعة المشاركة"); share.type = "button"; share.addEventListener("click", () => shareReport(report));
  actions.append(read, share); body.append(actions); link.append(image); card.append(link, body); return card;
}
function renderNews() {
  const items = visibleItems(); newsList.replaceChildren();
  emptyState.hidden = items.length > 0;
  $("#feed-count").textContent = `${items.length} ${items.length === 1 ? "تقرير" : "تقارير"} مكتملة`;
  items.forEach((item, index) => newsList.append(makeCard(item, index)));
}
function renderBriefs() {
  const section = $("#latest-briefs"), host = $("#briefs-list");
  if (!section || !host) return;
  const items = readBriefItems().slice(0, 12);
  section.hidden = items.length === 0;
  host.replaceChildren();
  for (const item of items) {
    const card = el("article", "brief-card");
    const meta = el("p", "brief-meta", `${REGION_LABELS[item.region] || "خبر"} · ${formatDate(item.published_at || item.created_at)} · ${item.source_name || "خلاصة RSS"}`);
    card.append(meta, el("h3", "", item.title || "خبر قيد التحرير"));
    card.append(el("p", "brief-summary", item.summary || "لا تتوفر تفاصيل كافية بعد لإعداد تقرير موثق داخل الموقع."));
    const read = el("a", "read-link", "اقرأ الموجز داخل الموقع");
    read.href = newsUrl(item.id);
    card.append(read);
    host.append(card);
  }
}
function render() { renderHero(); renderTrends(); renderNews(); renderBriefs(); }
async function shareReport(report) {
  const allowed = window.confirm("هل توافق على فتح مشاركة يدوية لهذا التقرير؟ لن يُنشر شيء من هذه النافذة تلقائيًا.");
  if (!allowed) return;
  const link = shortReportUrl(report.id);
  const shareData = { title: report.title, text: `${report.social_caption}\n\n${link}`, url: new URL(link, location.href).href };
  try {
    if (navigator.share) await navigator.share(shareData);
    else { await navigator.clipboard.writeText(`${shareData.title}\n${shareData.text}`); window.alert("تم نسخ نص المشاركة. اختر المنصة وانشره يدويًا."); }
  } catch (error) { if (error.name !== "AbortError") console.warn("Share not completed", error); }
}
function gentleChime() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    [[659.25, 0], [880, .12]].forEach(([frequency, offset]) => {
      const osc = ctx.createOscillator(), gain = ctx.createGain();
      osc.type = "sine"; osc.frequency.value = frequency;
      gain.gain.setValueAtTime(.0001, ctx.currentTime + offset);
      gain.gain.exponentialRampToValueAtTime(.045, ctx.currentTime + offset + .025);
      gain.gain.exponentialRampToValueAtTime(.0001, ctx.currentTime + offset + .22);
      osc.connect(gain); gain.connect(ctx.destination); osc.start(ctx.currentTime + offset); osc.stop(ctx.currentTime + offset + .23);
    });
    window.setTimeout(() => ctx.close(), 650);
  } catch (error) { console.info("Audio alert is unavailable", error); }
}
function notifyNewTrends(previousIds) {
  if (state.initialLoad || !state.alertEnabled) return;
  const fresh = state.trends.find((item) => item.alert && !previousIds.has(item.id));
  if (!fresh) return;
  gentleChime();
  if ("Notification" in window && Notification.permission === "granted") new Notification("ترند جديد يستحق المتابعة", { body: fresh.title, icon: "./assets/app-icon-192.png", tag: fresh.id });
}
async function fetchJson(path) {
  const response = await fetch(`${path}?v=${Date.now()}`, { cache: "no-store" });
  if (!response.ok) throw new Error(`${path}: ${response.status}`);
  return response.json();
}
async function loadData() {
  const oldTrendIds = new Set(state.trends.map((item) => item.id));
  try {
    const [news, reports, trends] = await Promise.all([fetchJson("./data/news.json"), fetchJson("./data/reports.json"), fetchJson("./data/trending.json")]);
    state.news = Array.isArray(news.items) ? news.items : [];
    reportMap.clear();
    state.reports = Array.isArray(reports.items) ? reports.items : [];
    state.reports.forEach((report) => reportMap.set(report.id, report));
    state.trends = Array.isArray(trends.items) ? trends.items : [];
    state.trendUpdated = trends.updated_at;
    const updated = formatDate(news.updated_at);
    $("#update-status").textContent = updated ? `آخر نشر للأخبار: ${updated}` : "بانتظار أول تحديث للأخبار";
    $("#last-updated").textContent = updated ? `بيانات الأخبار: ${updated}` : "لا توجد بيانات منشورة بعد";
    $("#edition-date").textContent = new Intl.DateTimeFormat("ar-EG", { dateStyle: "full" }).format(new Date());
    const top = state.trends.find((item) => item.alert) || state.trends[0];
    $("#breaking-copy").textContent = top ? `${top.title} — ${top.approx_traffic || "ترند حديث"}` : "نرصد الترندات الحديثة، وتظهر هنا عند وصول بيانات جديدة.";
    render(); notifyNewTrends(oldTrendIds); state.initialLoad = false;
  } catch (error) {
    $("#update-status").textContent = "تعذر تحديث البيانات الآن";
    if (state.initialLoad) { newsList.replaceChildren(el("p", "loading-card", "تعذر تحميل الأخبار مؤقتًا. أعد المحاولة بعد قليل.")); }
    console.error("Could not load portal data", error);
  }
}

document.querySelectorAll(".filter-button").forEach((button) => button.addEventListener("click", () => {
  state.region = button.dataset.region || "all";
  document.querySelectorAll(".filter-button").forEach((candidate) => { const active = candidate === button; candidate.classList.toggle("is-active", active); candidate.setAttribute("aria-pressed", String(active)); });
  renderNews(); renderBriefs();
}));
$("#search-input").addEventListener("input", (event) => { state.query = event.target.value; renderNews(); renderBriefs(); });
$("#alerts-toggle").addEventListener("click", async (event) => {
  if (state.alertEnabled) { state.alertEnabled = false; localStorage.setItem("wdw-alerts", "off"); event.currentTarget.textContent = "فعّل تنبيه الترند"; return; }
  if ("Notification" in window && Notification.permission === "default") await Notification.requestPermission();
  state.alertEnabled = true; localStorage.setItem("wdw-alerts", "on"); event.currentTarget.textContent = "إيقاف التنبيه"; gentleChime();
});
$("#alerts-toggle").textContent = state.alertEnabled ? "إيقاف التنبيه" : "فعّل تنبيه الترند";
loadData(); window.setInterval(loadData, REFRESH_MS);
if ("serviceWorker" in navigator && location.protocol.startsWith("http")) window.addEventListener("load", () => navigator.serviceWorker.register("./service-worker.js").catch((error) => console.warn("Offline support unavailable", error)));

const threadsSignalsScript = document.createElement("script");
threadsSignalsScript.src = "./threads-signals.js";
threadsSignalsScript.defer = true;
document.body.appendChild(threadsSignalsScript);
