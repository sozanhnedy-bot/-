const CATEGORY_LABELS = { egypt:"مصر", arab:"العالم العربي", world:"العالم", politics:"سياسة", economy:"اقتصاد", technology:"تكنولوجيا", sports:"رياضة", reports:"تقارير ووثائقيات", videos:"فيديوهات" };
const $ = (selector) => document.querySelector(selector);
const state = { items: [], query: "", from: "", to: "", region: "all", category: "all" };
function el(tag, cls = "", text = "") { const node = document.createElement(tag); if (cls) node.className = cls; if (text) node.textContent = text; return node; }
function validId(value) { return /^[A-Za-z0-9_-]{1,80}$/.test(String(value || "")); }
function normalized(value) { return String(value || "").normalize("NFKC").replace(/[\u064B-\u065F\u0670]/g, "").toLocaleLowerCase("ar"); }
function comparable(value) { return normalized(value).replace(/[^\p{L}\p{N}\s]/gu, " ").replace(/\s+/g, " ").trim(); }
function dateValue(value) { const date = new Date(value || ""); return Number.isFinite(date.getTime()) ? date : null; }
function dateLabel(value) { const date = dateValue(value); return date ? new Intl.DateTimeFormat("ar-EG", { dateStyle: "medium", timeStyle: "short" }).format(date) : "التاريخ غير متاح"; }
function removePromotion(value) { return String(value || "").trim().replace(/\s*(?:🔴\s*)?(?:تابعوا|تابع|اقرأ المزيد|قراءة المزيد|عرض المزيد من العناوين|View more headlines on Google News).*$/iu, "").replace(/[\s…\.]+$/u, "").trim(); }
function stripSource(title, source) { let clean = removePromotion(title), publisher = normalized(source); for (const separator of [" — "," – "," - "," | "," :: "]) { const index = clean.lastIndexOf(separator); if (index > 0 && normalized(clean.slice(index + separator.length)) === publisher) return clean.slice(0, index).trim(); } return clean; }
function conciseSummary(value, title, source) { const summary = removePromotion(value), summaryKey = comparable(stripSource(summary, source)), titleKey = comparable(title); if (!summaryKey || summaryKey === titleKey) return ""; if (summaryKey.startsWith(titleKey) && summaryKey.split(" ").length <= titleKey.split(" ").length + 2 && !/\d/u.test(summary)) return ""; return summary; }
function safeImage(value) { const candidate = String(value || ""); if (/^assets\/[A-Za-z0-9._/-]+$/.test(candidate) && !candidate.includes("..")) return `./${candidate}`; try { const url = new URL(candidate); if (url.protocol === "https:" && url.hostname.includes(".") && !url.username && !url.password && /\.(jpe?g|png|webp|avif)$/i.test(url.pathname)) return url.href; } catch (_) {} return ""; }
function toRecords(news, reports, updatedAt) {
  const reportMap = new Map(reports.filter((report) => report && validId(report.id)).map((report) => [report.id, report]));
  const rows = (Array.isArray(news) ? news : []).filter((item) => item && validId(item.id)).map((item) => {
    const report = reportMap.get(item.id);
    const source = report && Array.isArray(report.sources) ? report.sources.join("، ") : (item.source_name || "");
    const title = report?.title || stripSource(item.title, item.source_name) || "خبر";
    return { id:item.id, title, summary:report?.dek || conciseSummary(item.summary, title, source), source, region:item.region || report?.region || "world", category:report ? "reports" : (item.category || item.region || "world"), date:item.published_at || report?.published_at || item.created_at || "", image:safeImage(report?.image || item.image_url), href:report ? `./reports/${item.id}.html` : `./news/${item.id}.html`, full:Boolean(report) };
  });
  const seen = new Set(rows.map((row) => row.id));
  reports.filter((report) => report && validId(report.id) && !seen.has(report.id)).forEach((report) => { const title = report.title || "تقرير"; rows.push({ id:report.id, title, summary:report.dek || "", source:Array.isArray(report.sources)?report.sources.join("، "):"", region:report.region||"world", category:"reports", date:report.published_at||updatedAt||"", image:safeImage(report.image), href:`./reports/${report.id}.html`, full:true }); });
  return rows.sort((a,b) => (dateValue(b.date)?.getTime()||0) - (dateValue(a.date)?.getTime()||0));
}
function card(item) {
  const article = el("article", "portal-story-card archive-card");
  if (item.image) { const link = document.createElement("a"); link.href = item.href; link.className = "portal-card-thumb-link"; const figure = el("figure", "portal-card-thumb"), image = document.createElement("img"); image.src=item.image; image.alt=item.title; image.loading="lazy"; image.decoding="async"; figure.append(image); link.append(figure); article.append(link); }
  const body=el("div","portal-card-body"), meta=el("div","portal-card-meta"); meta.append(el("span","portal-category-label",CATEGORY_LABELS[item.category]||CATEGORY_LABELS[item.region]||"خبر"),el("time","portal-date",dateLabel(item.date))); if(item.source) meta.append(el("span","portal-source",`المصدر: ${item.source}`));
  body.append(meta); const heading=el("h2","portal-card-title"), link=document.createElement("a"); link.href=item.href; link.textContent=item.title; heading.append(link); body.append(heading); if(item.summary) body.append(el("p","portal-card-summary",item.summary)); body.append(el("span","portal-content-kind",item.full?"تقرير تحريري":"موجز من الناشر")); article.append(body); return article;
}
function applyFilters() {
  const rows = state.items.filter((item) => {
    const date = dateValue(item.date), day = date ? `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}` : "";
    if (state.region !== "all" && item.region !== state.region) return false;
    if (state.category !== "all") { if (state.category === "videos") { if (!item.video) return false; } else if (item.category !== state.category) return false; }
    if (state.from && (!day || day < state.from)) return false;
    if (state.to && (!day || day > state.to)) return false;
    if (state.query && !normalized([item.title,item.summary,item.source,item.category,item.region].join(" ")).includes(state.query)) return false;
    return true;
  });
  const host=$("#archive-list"); host.replaceChildren(); rows.forEach((item) => host.append(card(item)));
  $("#archive-count").textContent=`${rows.length.toLocaleString("ar-EG")} مادة في الأرشيف`;
  $("#archive-empty").hidden=rows.length>0;
}
async function start() {
  try {
    const [news,reports]=await Promise.all([fetch(`./data/news.json?v=${Date.now()}`,{cache:"no-store"}).then((response)=>{if(!response.ok)throw Error();return response.json();}),fetch(`./data/reports.json?v=${Date.now()}`,{cache:"no-store"}).then((response)=>{if(!response.ok)throw Error();return response.json();})]);
    const reportItems=Array.isArray(reports.items)?reports.items:[]; state.items=toRecords(news.items,reportItems,news.updated_at).map((item)=>({...item,video:reportItems.some((report)=>report.id===item.id&&/^[A-Za-z0-9_-]{11}$/.test(String(report.youtube_id||"")))}));
    $("#archive-status").textContent=`${state.items.length.toLocaleString("ar-EG")} مادة محفوظة`;
    $("#archive-filters").addEventListener("submit",(event)=>event.preventDefault());
    ["archive-search","archive-from","archive-to","archive-region","archive-category"].forEach((id)=>$("#"+id).addEventListener("input",()=>{state.query=normalized($("#archive-search").value.trim());state.from=$("#archive-from").value;state.to=$("#archive-to").value;state.region=$("#archive-region").value;state.category=$("#archive-category").value;applyFilters();}));
    $("#archive-reset").addEventListener("click",()=>{$("#archive-filters").reset();state.query="";state.from="";state.to="";state.region="all";state.category="all";applyFilters();});
    applyFilters();
  } catch (_) { $("#archive-status").textContent="تعذر تحميل الأرشيف"; $("#archive-empty").hidden=false; }
}
start();
