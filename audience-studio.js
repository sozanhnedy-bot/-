(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const stop = new Set(("في من على إلى عن مع هذا هذه ذلك تلك هو هي هم هن كان تكون يكون التي الذي وال أو ثم كما بعد قبل عند بين خلال حيث إذا إن أن ما لا لم لن قد كل بعض أكثر جدًا جدا حول ضد أمام تحت فوق لدى لدى عبر نحو مثل فقط أيضًا ايضا").split(/\s+/));
  const clean = (value) => String(value || "").replace(/[\u0000-\u001f<>]/g, " ").replace(/\s+/g, " ").trim();
  const words = (value) => clean(value).split(/[\s،,؛;:.!?؟()[\]{}"“”]+/).map((w) => w.trim()).filter((w) => w.length > 2 && !stop.has(w));
  const unique = (items) => [...new Set(items)];
  let reports = [];
  function make(tag, text) { const node = document.createElement(tag); node.textContent = text; return node; }
  async function loadReports() {
    const select = $("report-select");
    try {
      const response = await fetch("./data/reports.json", { cache: "no-store" });
      if (!response.ok) throw new Error("reports unavailable");
      const data = await response.json();
      reports = Array.isArray(data.items) ? data.items.filter((r) => r && typeof r.title === "string") : [];
      select.replaceChildren(new Option("اختر تقريرًا منشورًا...", ""));
      reports.forEach((report, index) => select.add(new Option(report.title, String(index))));
      if (!reports.length) select.add(new Option("لا توجد تقارير متاحة", ""));
    } catch (_) {
      select.replaceChildren(new Option("تعذر تحميل التقارير — أدخل البيانات يدويًا", ""));
      $("status").textContent = "تعذر تحميل مكتبة التقارير؛ يمكنك استخدام الإدخال اليدوي.";
    }
  }
  function chooseReport() {
    const report = reports[Number($("report-select").value)];
    if (!report) return;
    $("video-title").value = clean(report.title);
    $("video-summary").value = clean(report.dek || report.social_caption || "");
  }
  function build() {
    const title = clean($("video-title").value);
    const summary = clean($("video-summary").value);
    if (!title) { $("status").textContent = "اكتب عنوان الفيديو أولًا."; $("video-title").focus(); return; }
    const terms = unique([...words(title), ...words(summary)]).slice(0, 12);
    const hashtags = terms.slice(0, 6).map((term) => "#" + term.replace(/[^\p{L}\p{N}_]/gu, ""));
    const tags = $("keywords"); tags.replaceChildren();
    (terms.length ? terms : ["محتوى عربي", "فيديو جديد"]).forEach((term) => tags.append(make("span", term))).forEach((node) => node.className = "audience-chip");
    const neutral = "هل تهتم بموضوع: " + title + "؟\n" + (summary || "شاهد الفيديو للتعرّف على التفاصيل والسياق.") + "\n\nما رأيك؟ شاركنا وجهة نظرك باحترام.\n" + hashtags.join(" ");
    const egypt = "لو الموضوع ده يهمك: " + title + "\n" + (summary || "في الفيديو بنعرض التفاصيل والمعلومات الأساسية.") + "\n\nاتفرج وشاركنا رأيك لو حابب.\n" + hashtags.join(" ");
    const gulf = "إذا كان هذا الموضوع يهمك: " + title + "\n" + (summary || "في هذا الفيديو نستعرض أبرز التفاصيل والمعلومات.") + "\n\nشاهد وشاركنا رأيك إذا رغبت.\n" + hashtags.join(" ");
    $("caption-neutral").textContent = neutral;
    $("caption-egypt").textContent = egypt;
    $("caption-gulf").textContent = gulf;
    const distribution = [
      "• مصر: صفحات ومجتمعات مهتمة بموضوع الفيديو، إذا كانت تسمح بالمشاركة.",
      "• الخليج: صياغة عربية واضحة تناسب الجمهور المحلي دون افتراض لهجة واحدة.",
      "• بلاد الشام: مجتمعات متخصصة في المجال نفسه مع احترام قواعدها.",
      "• المغرب العربي: استخدم وصفًا عربيًا واضحًا، وأضف ترجمة أو صياغة محلية عند الحاجة.",
      "• الجمهور العربي العام: مكتبة الموقع، صفحات القناة الرسمية، ومشاركة الرابط طوعًا."
    ].join("\n");
    $("distribution").textContent = distribution;
    $("results").hidden = false;
    $("status").textContent = "تم تجهيز المسودات على جهازك فقط. راجع دقة كل صياغة قبل استخدامها.";
  }
  async function copy(id) {
    const value = $(id).textContent;
    try {
      await navigator.clipboard.writeText(value);
      $("status").textContent = "تم نسخ النص.";
    } catch (_) {
      const area = document.createElement("textarea");
      area.value = value; document.body.append(area); area.select();
      const ok = document.execCommand("copy"); area.remove();
      $("status").textContent = ok ? "تم نسخ النص." : "تعذر النسخ؛ حدد النص وانسخه يدويًا.";
    }
  }
  function clear() {
    $("report-select").value = ""; $("video-title").value = ""; $("video-summary").value = "";
    $("results").hidden = true; $("status").textContent = "";
  }
  document.addEventListener("DOMContentLoaded", () => {
    $("report-select").addEventListener("change", chooseReport);
    $("generate-btn").addEventListener("click", build);
    $("clear-btn").addEventListener("click", clear);
    document.querySelectorAll(".copy-btn").forEach((button) => button.addEventListener("click", () => copy(button.dataset.copy)));
    loadReports();
  });
})();
