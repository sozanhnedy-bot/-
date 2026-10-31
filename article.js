function buildScriptText() {
  return [...document.querySelectorAll(".video-script li")].map((item) => item.innerText.trim()).join("\n\n");
}
async function copyScript(button) {
  const text = buildScriptText();
  try {
    await navigator.clipboard.writeText(text);
    button.textContent = "تم نسخ السكريبت";
    window.setTimeout(() => { button.textContent = "نسخ السكريبت"; }, 1800);
  } catch (_) {
    window.prompt("انسخ السكريبت من هنا:", text);
  }
}
function internalShareUrl(payload) {
  try {
    const candidate = payload.short_url ? new URL(payload.short_url, location.href) : new URL(location.href);
    return candidate.origin === location.origin ? candidate.href : location.href;
  } catch (_) {
    return location.href;
  }
}
async function shareApproved(payload, button) {
  if (!window.confirm("تأكيدك مطلوب قبل المشاركة اليدوية. هل تريد فتح قائمة المشاركة وإرسال النص الذي راجعته؟")) return;
  const data = { title: payload.title, text: payload.text, url: internalShareUrl(payload) };
  try {
    if (navigator.share) await navigator.share(data);
    else {
      await navigator.clipboard.writeText(`${data.title}\n${data.text}\n${data.url}`);
      button.textContent = "تم النسخ؛ انشر يدويًا";
      window.setTimeout(() => { button.textContent = "مراجعة ثم مشاركة"; }, 2200);
    }
  } catch (error) {
    if (error.name !== "AbortError") console.warn("Share was not completed", error);
  }
}
async function copyShortLink(payload, button) {
  const url = internalShareUrl(payload);
  try {
    await navigator.clipboard.writeText(url);
    button.textContent = "تم نسخ الرابط المختصر";
    window.setTimeout(() => { button.textContent = "نسخ الرابط المختصر"; }, 1800);
  } catch (_) {
    window.prompt("انسخ الرابط المختصر:", url);
  }
}
function loadYoutubePlayer(button) {
  const host = button.parentElement?.querySelector("[data-youtube-host]");
  const videoId = host?.dataset.youtubeId || "";
  if (!host || !/^[A-Za-z0-9_-]{11}$/.test(videoId)) return;
  const frame = document.createElement("iframe");
  frame.src = `https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoId)}?playsinline=1&rel=0`;
  frame.title = "مشغل فيديو التقرير على YouTube";
  frame.loading = "lazy";
  frame.referrerPolicy = "strict-origin-when-cross-origin";
  frame.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
  frame.allowFullscreen = true;
  host.replaceChildren(frame);
  button.remove();
}
const payloadNode = document.getElementById("share-payload");
let payload = { title: document.title, text: "" };
try { if (payloadNode) payload = JSON.parse(payloadNode.textContent); } catch (_) { /* keep safe fallback */ }
document.querySelector("[data-share]")?.addEventListener("click", (event) => shareApproved(payload, event.currentTarget));
document.querySelector("[data-copy-short-link]")?.addEventListener("click", (event) => copyShortLink(payload, event.currentTarget));
document.querySelector("[data-copy-script]")?.addEventListener("click", (event) => copyScript(event.currentTarget));
document.querySelector("[data-load-youtube]")?.addEventListener("click", (event) => loadYoutubePlayer(event.currentTarget));
