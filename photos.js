(() => {
  "use strict";

  const $ = (selector) => document.querySelector(selector);
  const gallery = $("#photos-gallery");
  const search = $("#photo-search");
  const count = $("#photos-count");
  const status = $("#photos-status");
  const empty = $("#photos-empty");
  const allowedRights = new Set(["licensed", "permission_granted", "public_domain", "cc0", "cc-by", "cc-by-sa"]);

  function text(value, limit = 500) {
    return typeof value === "string" ? value.trim().slice(0, limit) : "";
  }

  function validId(value) {
    return /^[A-Za-z0-9_-]{1,80}$/.test(String(value || ""));
  }

  function imageMatchesStory(item) {
    const words = (value) => new Set(text(value, 1000).toLocaleLowerCase("und").replace(/[^\p{L}\p{N}]+/gu, " ").split(/\s+/).filter((word) => word.length > 2));
    const titleWords = words(item.title);
    if (!titleWords.size) return false;
    const alt = text(item.image_alt, 300);
    const altWords = words(alt);
    const titleKey = [...titleWords].sort().join(" ");
    const altKey = [...altWords].sort().join(" ");
    const source = text(item.image_source_url, 1000);
    if (!source.includes("commons.wikimedia.org/wiki/File:")) return true;
    let filename = "";
    try { filename = decodeURIComponent(new URL(source).pathname.split("/").pop() || ""); } catch (_) {}
    const imageWords = words((altKey && altKey !== titleKey ? alt : "") + " " + filename);
    let overlap = 0;
    for (const word of titleWords) if (imageWords.has(word)) overlap += 1;
    return overlap >= 2;
  }

  function localImage(value) {
    const candidate = text(value, 500);
    return /^assets\/[A-Za-z0-9._/-]+$/.test(candidate) && !candidate.includes("..")
      ? `./${candidate}` : "";
  }

  function licensedImage(item) {
    const status = text(item.image_rights_status, 40).toLowerCase();
    const credit = text(item.image_credit, 240);
    const license = text(item.image_license_url, 1000);
    const image = text(item.image_url, 1000);
    try {
      const imageUrl = new URL(image);
      const licenseUrl = new URL(license);
      const host = imageUrl.hostname.toLowerCase().replace(/^www\./, "");
      const path = decodeURIComponent(imageUrl.pathname).toLowerCase();
      const blocked = ["logo", "social-preview", "primary-color", "newsgroup-logos", "news-slate-stream", "/seo/", "default.jpg"];
      return allowedRights.has(status) && imageMatchesStory(item) && Boolean(credit)
        && imageUrl.protocol === "https:" && licenseUrl.protocol === "https:"
        && Boolean(imageUrl.hostname && licenseUrl.hostname)
        && !imageUrl.username && !imageUrl.password
        && host !== "google.com" && !host.endsWith(".google.com")
        && !host.endsWith(".googleusercontent.com") && !host.endsWith(".gstatic.com")
        && !blocked.some((marker) => path.includes(marker))
        ? imageUrl.href : "";
    } catch (_) {
      return "";
    }
  }

  function hasPublicDetails(item) {
    const title = text(item.title, 500);
    const source = text(item.source_name, 160);
    if (!source || !/^https:\/\//i.test(text(item.source_url, 1000))) return false;
    const values = item.summary_type === "source_notice" ? [item.excerpt] : [item.summary, item.excerpt];
    return values.some((value) => {
      const detail = text(value, 1200).replace(/[^\p{L}\p{N}\s]/gu, " ").replace(/\s+/g, " ").trim();
      const titleKey = title.replace(/[^\p{L}\p{N}\s]/gu, " ").replace(/\s+/g, " ").trim();
      const words = detail.split(" ").filter(Boolean).length;
      return detail.length >= 45 && words >= 7 && detail !== titleKey;
    });
  }

  function dateValue(value) {
    const parsed = Date.parse(value || "");
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function makeCard(item) {
    const article = document.createElement("article");
    article.className = "portal-story-card";
    const link = document.createElement("a");
    link.className = "portal-card-thumb-link";
    link.href = item.href;
    const figure = document.createElement("figure");
    figure.className = "portal-card-thumb";
    const image = document.createElement("img");
    image.src = item.image;
    image.alt = item.alt || item.title;
    image.loading = "lazy";
    image.decoding = "async";
    image.referrerPolicy = "no-referrer";
    image.addEventListener("error", () => article.remove(), { once: true });
    figure.append(image);
    const caption = document.createElement("figcaption");
    caption.textContent = item.caption;
    figure.append(caption);
    link.append(figure);
    article.append(link);

    const body = document.createElement("div");
    body.className = "portal-card-body";
    const meta = document.createElement("div");
    meta.className = "portal-card-meta";
    const kind = document.createElement("span");
    kind.className = "portal-category-label";
    kind.textContent = item.kind;
    meta.append(kind);
    body.append(meta);
    const heading = document.createElement("h2");
    heading.className = "portal-card-title";
    const titleLink = document.createElement("a");
    titleLink.href = item.href;
    titleLink.textContent = item.title;
    heading.append(titleLink);
    body.append(heading);
    if (item.credit) {
      const credit = document.createElement("p");
      credit.className = "portal-card-foot-note";
      credit.textContent = item.credit;
      body.append(credit);
    }
    article.append(body);
    return article;
  }

  function draw(items) {
    const query = text(search?.value, 200).toLocaleLowerCase("ar");
    const filtered = items.filter((item) => !query || `${item.title} ${item.credit} ${item.kind}`.toLocaleLowerCase("ar").includes(query));
    gallery.replaceChildren(...filtered.map(makeCard));
    count.textContent = `عدد الصور المعروضة: ${filtered.length}`;
    empty.hidden = filtered.length > 0;
    status.textContent = filtered.length ? `تم تحميل ${filtered.length} صورة` : "لا توجد صور مطابقة";
  }

  async function loadJson(path) {
    const response = await fetch(`./data/${path}`, { cache: "no-store" });
    if (!response.ok) throw new Error(`Failed to load ${path}`);
    return response.json();
  }

  async function start() {
    if (!gallery) return;
    status.textContent = "جارٍ تحميل الصور الموثقة...";
    const [reportsResult, newsResult] = await Promise.allSettled([
      loadJson("reports.json"),
      loadJson("news.json"),
    ]);
    const items = [];
    if (reportsResult.status === "fulfilled" && Array.isArray(reportsResult.value.items)) {
      for (const report of reportsResult.value.items) {
        if (!report || !validId(report.id) || !Array.isArray(report.sections) || !report.sections.length) continue;
        const image = localImage(report.image);
        if (!image) continue;
        items.push({
          id: report.id,
          title: text(report.title, 300) || "تقرير تحريري",
          image,
          alt: text(report.image_alt, 300),
          caption: text(report.image_caption, 300) || "صورة توضيحية مرتبطة بالتقرير.",
          kind: "تقرير تحريري",
          credit: "صورة محلية مرتبطة بالتقرير",
          href: `./reports/${report.id}.html`,
          date: dateValue(report.published_at),
        });
      }
    }
    if (newsResult.status === "fulfilled" && Array.isArray(newsResult.value.items)) {
      for (const item of newsResult.value.items) {
        if (!item || !validId(item.id) || !hasPublicDetails(item)) continue;
        const image = licensedImage(item);
        if (!image) continue;
        items.push({
          id: item.id,
          title: text(item.title, 300) || "خبر منشور",
          image,
          alt: text(item.image_alt, 300),
          caption: `صورة مرخصة أو مأذون باستخدامها · ${text(item.image_credit, 200)}`,
          kind: "صورة مرخصة",
          credit: `الترخيص: ${text(item.image_license_url, 300)}`,
          href: `./news/${item.id}.html`,
          date: dateValue(item.published_at),
        });
      }
    }
    items.sort((a, b) => b.date - a.date);
    draw(items);
  }

  search?.addEventListener("input", () => {
    const cards = [...gallery.querySelectorAll(".portal-story-card")];
    const query = text(search.value, 200).toLocaleLowerCase("ar");
    let visible = 0;
    for (const card of cards) {
      const match = card.textContent.toLocaleLowerCase("ar").includes(query);
      card.hidden = !match;
      if (match) visible += 1;
    }
    count.textContent = `عدد الصور المعروضة: ${visible}`;
    empty.hidden = visible > 0;
    status.textContent = visible ? `تم تحميل ${visible} صورة` : "لا توجد صور مطابقة";
  });

  start().catch(() => {
    gallery.replaceChildren();
    count.textContent = "تعذر تحميل معرض الصور";
    status.textContent = "تعذر تحميل البيانات";
    empty.hidden = false;
  });
})();
