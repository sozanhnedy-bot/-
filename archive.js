const CATEGORY_LABELS = { egypt:"مصر", arab:"العالم العربي", world:"العالم", politics:"سياسة", economy:"اقتصاد", technology:"تكنولوجيا", sports:"رياضة", reports:"تقارير ووثائقيات", videos:"فيديوهات" };
const $ = (s) => document.querySelector(s);
const state = { items: [], query: "", from: "", to: "", region: "all", category: "all" };
function el(tag, cls = "", text = "") { const n = document.createElement(tag); if (cls) n.className = cls; if (text) n.textContent = text; return n; }
function validId(v) { return /^[A-Za-z0-9_-]{1,80}$/.test(String(v || "")); }
function normalized(v) { return String(v || "").normalize("NFKC").replace(/[\u064B-\u065F\u0670]/g, "").toLocaleLowerCase("ar"); }
function dateValue(v) { const d = new Date(v || ""); return Number.isFinite(d.getTime()) ? d : null; }
function dateLabel(v) { const d = dateValue(v); return d ? new Intl.DateTimeFormat("ar-EG", { dateStyle: "medium", timeStyle: "short" }).format(d) : "التاريخ غير متاح"; }
function stripSource(title, source) { let t = String(title || "").trim(), s = normalized(source); for (const sep of [" — "," – "," - "," | "]) { const i = t.lastIndexOf(sep); if (i > 0 && normalized(t.slice(i + sep.length)) === s) return t.slice(0, i).trim(); } return t; }
function safeImage(v) { const x = String(v || ""); if (/^assets\/[A-Za-z0-9._/-]+$/.test(x) && !x.includes("..")) return `./${x}`; try { const u = new URL(x); if (u.protocol === "https:" && u.hostname.includes(".") && !u.username && !u.password && /\.(jpe?g|png|webp|avif)$/i.test(u.pathname)) return u.href; } catch (_) {} return ""; }
function toRecords(news, reports, updatedAt) {
  const reportMap = new Map(reports.filter((r) => r && validId(r.id)).map((r) => [r.id, r]));
  const rows = (Array.isArray(news) ? news : []).filter((n) => n && validId(n.id)).map((n) => {
    const r = reportMap.get(n.id); const source = r && Array.isArray(r.sources) ? r.sources.join("، ") : (n.source_name || "");
    return { id:n.id, title:r?.title || stripSource(n.title, n.source_name), summary:r?.dek || n.summary || "", source, region:n.region || r?.region || "world", category:r ? "reports" : (n.category || n.region || "world"), date:n.published_at || r?.published_at || n.created_at || "", image:safeImage(r?.image || n.image_url), href:r ? `./reports/${n.id}.html` : `./news/${n.id}.html`, full:Boolean(r) };
  });
  const seen = new Set(rows.map((r) => r.id));
  reports.filter((r) => r && validId(r.id) && !seen.has(r.id)).forEach((r) => rows.push({ id:r.id,title:r.title || "تقرير",summary:r.dek || "",source:Array.isArray(r.sources)?r.sources.join("، "):"",region:r.region||"world",category:"reports",date:r.published_at||updatedAt||"",image:safeImage(r.image),href:`./reports/${r.id}.html`,full:true }));
  return rows.sort((a,b) => (dateValue(b.date)?.getTime()||0)-(dateValue(a.date)?.getTime()||0));
}
function card(item) {
  const article = el("article", "portal-story-card archive-card");
  if (item.image) { const a = document.createElement("a"); a.href=item.href; a.className="portal-card-thumb-link"; const f=el("figure","portal-card-thumb"); const img=document.createElement("img"); img.src=item.image; img.alt=item.title; img.loading="lazy"; f.append(img); a.append(f); article.append(a); }
  const body=el("div","portal-card-body"), meta=el("div","portal-card-meta"); meta.append(el("span","portal-category-label",CATEGORY_LABELS[item.category]||CATEGORY_LABELS[item.region]||"خبر"),el("time","portal-date",dateLabel(item.date))); if(item.source) meta.append(el("span","portal-source",`المصدر: ${item.source}`));
  body.append(meta); const h=el("h2","portal-card-title"), a=document.createElement("a"); a.href=item.href; a.textContent=item.title; h.append(a); body.append(h); if(item.summary) body.append(el("p","portal-card-summary",item.summary)); body.append(el("span","portal-content-kind",item.full?"تقرير تحريري":"موجز إخباري")); article.append(body); return article;
}
function applyFilters() {
  const rows = state.items.filter((item) => {
    const d = dateValue(item.date); const day = d ? `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}` : "";
    if (state.region !== "all" && item.region !== state.region) return false;
    if (state.category !== "all") { if (state.category === "videos") { if (!item.video) return false; } else if (item.category !== state.category) return false; }
    if (state.from && (!day || day < state.from)) return false;
    if (state.to && (!day || day > state.to)) return false;
    if (state.query && !normalized([item.title,item.summary,item.source,item.category,item.region].join(" ")).includes(state.query)) return false;
    return true;
  });
  const host=$("#archive-list"); host.replaceChildren(); rows.forEach((item)=>host.append(card(item)));
  $("#archive-count").textContent=`${rows.length.toLocaleString("ar-EG")} مادة في الأرشيف`;
  $("#archive-empty").hidden=rows.length>0;
}
async function start() {
  try {
    const [news,reports]=await Promise.all([fetch(`./data/news.json?v=${Date.now()}`,{cache:"no-store"}).then(r=>{if(!r.ok)throw Error();return r.json();}),fetch(`./data/reports.json?v=${Date.now()}`,{cache:"no-store"}).then(r=>{if(!r.ok)throw Error();return r.json();})]);
    const reportItems=Array.isArray(reports.items)?reports.items:[]; state.items=toRecords(news.items,reportItems,news.updated_at).map((item)=>({...item,video:reportItems.some(r=>r.id===item.id&&/^[A-Za-z0-9_-]{11}$/.test(String(r.youtube_id||"")))}));
    $("#archive-status").textContent=`${state.items.length.toLocaleString("ar-EG")} مادة محفوظة`;
    $("#archive-filters").addEventListener("submit",(event)=>event.preventDefault());
    ["archive-search","archive-from","archive-to","archive-region","archive-category"].forEach((id)=>$("#"+id).addEventListener("input",()=>{ state.query=normalized($("#archive-search").value.trim()); state.from=$("#archive-from").value; state.to=$("#archive-to").value; state.region=$("#archive-region").value; state.category=$("#archive-category").value; applyFilters(); }));
    $("#archive-reset").addEventListener("click",()=>{ $("#archive-filters").reset(); state.query="";state.from="";state.to="";state.region="all";state.category="all";applyFilters(); });
    applyFilters();
  } catch (_) { $("#archive-status").textContent="تعذر تحميل الأرشيف"; $("#archive-empty").hidden=false; }
}
start();
