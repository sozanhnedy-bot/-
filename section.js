const CATEGORY_LABELS = { egypt:"مصر", arab:"العالم العربي", world:"العالم", politics:"سياسة", economy:"اقتصاد", technology:"تكنولوجيا", sports:"رياضة", reports:"تقارير ووثائقيات", videos:"فيديوهات" };
const REFERENCE_IMAGES = {
  world:["assets/reference-lead.jpg","assets/reference-wildfire.jpg","assets/reference-entertainment.jpg","assets/reference-market.jpg"],
  arab:["assets/reference-entertainment.jpg","assets/reference-market.jpg","assets/reference-sports.jpg"],
  egypt:["assets/reference-market.jpg","assets/reference-technology.jpg","assets/reference-sports.jpg"],
  politics:["assets/reference-lead.jpg","assets/reference-entertainment.jpg","assets/reference-technology.jpg"],
  economy:["assets/reference-market.jpg","assets/reference-technology.jpg","assets/reference-wildfire.jpg"],
  technology:["assets/reference-technology.jpg","assets/reference-market.jpg","assets/reference-lead.jpg"],
  sports:["assets/reference-sports.jpg","assets/reference-wildfire.jpg","assets/reference-entertainment.jpg"]
};
const $=(selector)=>document.querySelector(selector);
function el(tag,cls="",text=""){const node=document.createElement(tag);if(cls)node.className=cls;if(text)node.textContent=text;return node;}
function validId(value){return /^[A-Za-z0-9_-]{1,80}$/.test(String(value||""));}
function formatDate(value){const date=new Date(value||"");return Number.isFinite(date.getTime())?new Intl.DateTimeFormat("ar-EG",{dateStyle:"medium",timeStyle:"short"}).format(date):"التاريخ غير متاح";}
function normalized(value){return String(value||"").normalize("NFKC").replace(/[\u064B-\u065F\u0670]/g,"").toLocaleLowerCase("ar");}
function comparable(value){return normalized(value).replace(/[^\p{L}\p{N}\s]/gu," ").replace(/\s+/g," ").trim();}
function removePromotion(value){return String(value||"").trim().replace(/\s*(?:🔴\s*)?(?:تابعوا|تابع|اقرأ المزيد|قراءة المزيد|عرض المزيد من العناوين|View more headlines on Google News).*$/iu,"").replace(/[\s…\.]+$/u,"").trim();}
function stripSource(value,publisher){const title=removePromotion(value),source=normalized(publisher);for(const separator of [" — "," – "," - "," | "," :: "]){const index=title.lastIndexOf(separator);if(index>0&&normalized(title.slice(index+separator.length))===source)return title.slice(0,index).trim();}return title;}
function conciseSummary(value,title,publisher){const summary=removePromotion(value),summaryKey=comparable(stripSource(summary,publisher)),titleKey=comparable(title);if(!summaryKey||summaryKey===titleKey)return"";if(summaryKey.startsWith(titleKey)&&summaryKey.split(" ").length<=titleKey.split(" ").length+2&&!/\d/u.test(summary))return"";return summary;}
function safeImage(value){const candidate=String(value||"");if(/^assets\/[A-Za-z0-9._/-]+$/.test(candidate)&&!candidate.includes(".."))return`./${candidate}`;try{const url=new URL(candidate);if(url.protocol==="https:"&&url.hostname.includes(".")&&!url.username&&!url.password)return url.href;}catch(_){}return"";}
function referenceImage(category,region,id=""){
  const list=REFERENCE_IMAGES[category]||REFERENCE_IMAGES[region]||REFERENCE_IMAGES.world;
  const seed=[...String(id)].reduce((sum,char)=>sum+char.charCodeAt(0),0);
  return `./${list[seed%list.length]}`;
}
function makeCard(item,report){
  const card=el("article","portal-story-card"),region=item.region||"world",category=report?"reports":(item.category||region),image=safeImage(report?.image||item.image_url)||referenceImage(category,region,item.id);
  if(image){const link=document.createElement("a");link.href=report?`./reports/${item.id}.html`:`./news/${item.id}.html`;link.className="portal-card-thumb-link";const figure=el("figure","portal-card-thumb"),img=document.createElement("img");img.src=image;img.alt=report?.image_alt||item.title;img.loading="lazy";img.decoding="async";figure.append(img);link.append(figure);card.append(link);}
  const body=el("div","portal-card-body"),meta=el("div","portal-card-meta"),publisher=report&&Array.isArray(report.sources)?report.sources.join("، "):item.source_name,title=report?.title||stripSource(item.title,item.source_name)||"خبر";
  meta.append(el("span","portal-category-label",CATEGORY_LABELS[category]||CATEGORY_LABELS[region]||"خبر"),el("time","portal-date",formatDate(item.published_at||report?.published_at||item.created_at)));if(publisher)meta.append(el("span","portal-source",`المصدر: ${publisher}`));body.append(meta);
  const heading=el("h2","portal-card-title"),anchor=document.createElement("a");anchor.href=report?`./reports/${item.id}.html`:`./news/${item.id}.html`;anchor.textContent=title;heading.append(anchor);body.append(heading);const summary=report?.dek||conciseSummary(item.summary,title,publisher);if(summary)body.append(el("p","portal-card-summary",summary));body.append(el("span","portal-content-kind",report?"تقرير تحريري":"موجز من الناشر"));card.append(body);return card;
}
async function start(){
  const params=new URLSearchParams(location.search),category=(params.get("category")||"world").toLowerCase(),title=CATEGORY_LABELS[category]||"الأخبار";$("#section-title").textContent=title;document.title=`${title} | World News`;
  try{
    const [newsData,reportsData]=await Promise.all([fetch(`./data/news.json?v=${Date.now()}`,{cache:"no-store"}).then((response)=>response.json()),fetch(`./data/reports.json?v=${Date.now()}`,{cache:"no-store"}).then((response)=>response.json())]),news=Array.isArray(newsData.items)?newsData.items:[],reports=Array.isArray(reportsData.items)?reportsData.items:[],full=new Map(reports.filter((item)=>item&&validId(item.id)).map((item)=>[item.id,item]));let rows=[];
    if(category==="reports")rows=reports.filter((item)=>item&&validId(item.id)).map((item)=>({item,report:item}));
    else if(category==="videos")rows=reports.filter((item)=>item&&validId(item.id)&&/^[A-Za-z0-9_-]{11}$/.test(String(item.youtube_id||""))).map((item)=>({item,report:item}));
    else rows=news.filter((item)=>item&&validId(item.id)&&(String(item.category||item.region||"world").toLowerCase()===category||String(item.region||"").toLowerCase()===category)).map((item)=>({item,report:full.get(item.id)}));
    rows.sort((a,b)=>(Date.parse(b.item.published_at||b.report?.published_at||b.item.created_at||"")||0)-(Date.parse(a.item.published_at||a.report?.published_at||a.item.created_at||"")||0));const host=$("#section-list");host.replaceChildren();rows.forEach(({item,report})=>host.append(makeCard(item,report)));$("#section-empty").hidden=rows.length>0;$("#section-description").textContent=`${rows.length.toLocaleString("ar-EG")} مادة منشورة في قسم ${title}؛ الأحدث أولًا.`;
  }catch(_){$("#section-description").textContent="تعذر تحميل هذا القسم الآن.";$("#section-empty").hidden=false;}
}
start();
