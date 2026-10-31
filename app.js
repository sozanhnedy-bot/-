const REGION_LABELS = {
  world: "العالم",
  arab: "العالم العربي",
  egypt: "مصر",
};

const state = { items: [], region: "all", query: "" };
const list = document.querySelector("#news-list");
const emptyState = document.querySelector("#empty-state");
const updateStatus = document.querySelector("#update-status");
const lastUpdated = document.querySelector("#last-updated");
const feedCount = document.querySelector("#feed-count");

function safeHttpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

function formatDate(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("ar-EG", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function makeElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function makeCard(item, index) {
  const card = makeElement("article", "news-card");
  card.style.animationDelay = `${Math.min(index * 35, 210)}ms`;
  const body = makeElement("div", "card-body");
  const meta = makeElement("div", "card-meta");
  meta.append(makeElement("span", "region-tag", REGION_LABELS[item.region] || "خبر"));
  const dateText = formatDate(item.published_at || item.created_at);
  if (dateText) meta.append(makeElement("time", "card-date", dateText));
  body.append(meta);
  body.append(makeElement("h3", "news-title", item.title || "خبر"));
  if (item.summary) body.append(makeElement("p", "news-summary", item.summary));
  if (item.summary_type === "source_excerpt") {
    body.append(makeElement("p", "summary-note", "مقتطف RSS كما ورد من المصدر، غير مترجم"));
  } else if (item.summary_type === "source_notice") {
    body.append(makeElement("p", "summary-note", "الخلاصة لا توفّر تفاصيل إضافية؛ افتح المصدر للقراءة"));
  }

  const footer = makeElement("div", "card-footer");
  footer.append(makeElement("span", "source-name", item.source_name || "المصدر الأصلي"));
  const articleUrl = safeHttpUrl(item.url);
  if (articleUrl) {
    const link = makeElement("a", "source-link", "المصدر والتفاصيل");
    link.href = articleUrl;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    footer.append(link);
  }
  body.append(footer);
  card.append(body);

  const videoId = typeof item.youtube_video_id === "string" ? item.youtube_video_id : "";
  if (/^[A-Za-z0-9_-]{11}$/.test(videoId)) {
    const frame = makeElement("div", "video-frame");
    const iframe = document.createElement("iframe");
    iframe.src = `https://www.youtube-nocookie.com/embed/${videoId}`;
    iframe.title = `فيديو مضمّن: ${item.title || "ثريدز وثائقي"}`;
    iframe.loading = "lazy";
    iframe.referrerPolicy = "strict-origin-when-cross-origin";
    iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
    iframe.allowFullscreen = true;
    frame.append(iframe);
    card.append(frame);
  }
  return card;
}

function getVisibleItems() {
  const query = state.query.trim().toLocaleLowerCase("ar");
  return state.items.filter((item) => {
    const regionMatches = state.region === "all" || item.region === state.region;
    const searchable = `${item.title || ""} ${item.summary || ""} ${item.source_name || ""}`.toLocaleLowerCase("ar");
    return regionMatches && (!query || searchable.includes(query));
  });
}

function render() {
  const items = getVisibleItems();
  list.replaceChildren();
  emptyState.hidden = items.length !== 0;
  feedCount.textContent = `${items.length} ${items.length === 1 ? "خبر" : "أخبار"}`;
  for (const [index, item] of items.entries()) list.append(makeCard(item, index));
}

async function loadNews() {
  try {
    const response = await fetch("./data/news.json", { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    state.items = Array.isArray(data.items) ? data.items : [];
    state.items.sort((a, b) => new Date(b.published_at || b.created_at || 0) - new Date(a.published_at || a.created_at || 0));
    const updated = formatDate(data.updated_at);
    updateStatus.textContent = updated ? `آخر تحديث: ${updated}` : "بانتظار أول تحديث للأخبار";
    lastUpdated.textContent = updated ? `تحديث البيانات: ${updated}` : "لا توجد بيانات منشورة بعد";
    render();
  } catch (error) {
    updateStatus.textContent = "تعذر تحميل الموجز حاليًا";
    feedCount.textContent = "حاول تحديث الصفحة بعد قليل";
    list.replaceChildren(makeElement("div", "loading-card", "تعذر جلب ملف الأخبار. سيُعاد المحاولة عند تحديث الصفحة."));
    emptyState.hidden = true;
    console.error("Could not load news feed", error);
  }
}

document.querySelectorAll(".filter-button").forEach((button) => {
  button.addEventListener("click", () => {
    state.region = button.dataset.region || "all";
    document.querySelectorAll(".filter-button").forEach((candidate) => {
      const active = candidate === button;
      candidate.classList.toggle("is-active", active);
      candidate.setAttribute("aria-pressed", String(active));
    });
    render();
  });
});

document.querySelector("#search-input").addEventListener("input", (event) => {
  state.query = event.target.value;
  render();
});

loadNews();

if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./service-worker.js").catch((error) => {
      console.warn("Offline support could not be enabled", error);
    });
  });
}
