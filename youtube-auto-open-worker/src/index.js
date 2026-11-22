const PAGE = `<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>YouTube Video Opener</title>
<style>
:root{color-scheme:dark;--bg:#0b1220;--panel:#14243a;--line:#2b405b;--text:#edf4ff;--muted:#9aabc0;--accent:#2dd4c2}
*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:24px;background:linear-gradient(180deg,#0b1220,#0b0f1a);color:var(--text);font-family:system-ui,sans-serif;line-height:1.65}.wrap{max-width:720px;margin:32px auto;padding:28px;background:var(--panel);border:1px solid var(--line);border-radius:22px}h1{text-align:center;color:var(--accent)}.sub{text-align:center;color:var(--muted)}.panel{background:#0b1728;border:1px solid var(--line);border-radius:16px;padding:22px}label{display:block;color:var(--muted);margin:12px 0 6px}input{width:100%;padding:13px;border:2px solid var(--line);border-radius:11px;background:#0b1728;color:var(--text);font-size:16px}button{width:100%;margin-top:16px;padding:14px;border:0;border-radius:12px;background:var(--accent);color:#081521;font-weight:700;font-size:16px;cursor:pointer}.status{text-align:center;min-height:28px}.note{font-size:13px;color:var(--muted)}
</style>
</head>
<body>
<main class="wrap">
<h1>فتح فيديو يوتيوب</h1>
<p class="sub">أدخل رابط الفيديو وافتحه في تبويب واحد</p>
<section class="panel">
<label for="videoUrl">رابط فيديو يوتيوب</label>
<input id="videoUrl" type="url" placeholder="https://www.youtube.com/watch?v=..." autocomplete="url">
<button id="open">فتح الفيديو</button>
<p class="status" id="status" aria-live="polite">جاهز. أدخل رابط الفيديو.</p>
<p class="note">تُفتح صفحة الفيديو مرة واحدة فقط. قد يمنع المتصفح النوافذ المنبثقة؛ استخدم الزر مباشرة للسماح بالفتح.</p>
</section>
</main>
<script>
(() => {
 const input=document.getElementById('videoUrl');
 const button=document.getElementById('open');
 const status=document.getElementById('status');
 function validYouTube(value){
  try{
   const u=new URL(value);
   return ['youtube.com','www.youtube.com','m.youtube.com','youtu.be','www.youtube-nocookie.com'].includes(u.hostname)&&['http:','https:'].includes(u.protocol);
  }catch{return false;}
 }
 button.addEventListener('click',()=>{
  const video=input.value.trim();
  if(!validYouTube(video)){status.textContent='أدخل رابط يوتيوب صحيحًا.';return;}
  const tab=window.open(video,'_blank','noopener,noreferrer');
  status.textContent=tab?'تم فتح الفيديو في تبويب واحد.':'المتصفح منع فتح التبويب؛ اسمح بالنوافذ المنبثقة لهذا الموقع.';
 });
})();
</script>
</body>
</html>`;

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname !== "/") return new Response("Not Found", { status: 404 });
    return new Response(PAGE, {
      headers: {
        "content-type": "text/html; charset=utf-8",
        "cache-control": "no-store"
      }
    });
  }
};