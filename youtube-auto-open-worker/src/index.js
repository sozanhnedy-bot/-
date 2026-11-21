export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    // الصفحة الرئيسية (اللي هيظهر للمستخدم)
    if (url.pathname === "/") {
      return new Response(
        `<!DOCTYPE html>
        <html lang="ar">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Youtube Auto Open</title>
          <style>
            body { font-family: Arial; text-align: center; margin: 50px; }
            input, button { padding: 12px; font-size: 18px; margin: 10px; }
            #status { margin-top: 20px; font-size: 20px; }
          </style>
        </head>
        <body>
          <h1>يوتيوب تبويبات أوتو</h1>
          <input type="text" id="urlInput" placeholder="رابط الفيديو" value="https://www.youtube.com/watch?v=dQw4w9WgXcQ">
          <input type="number" id="countInput" placeholder="عدد التبويبات" value="10" min="1" max="100">
          <button onclick="startAutoOpen()">تشغيل أوتوماتيك</button>
          <button onclick="stopAutoOpen()">إيقاف</button>
          <div id="status">جاهز</div>
          <script>
            let interval;
            async function startAutoOpen() {
              const url = document.getElementById('urlInput').value;
              const count = parseInt(document.getElementById('countInput').value);
              document.getElementById('status').innerText = "بدأت...";
              interval = setInterval(async () => {
                if (count <= 0) { clearInterval(interval); return; }
                try {
                  const res = await fetch(window.location.origin + "/open?url=" + encodeURIComponent(url));
                  document.getElementById('status').innerText = await res.text();
                } catch(e) {}
                count--;
              }, 600);
            }
            function stopAutoOpen() {
              clearInterval(interval);
              document.getElementById('status').innerText = "موقف";
            }
          </script>
        </body>
        </html>`,
        { headers: { "Content-Type": "text/html; charset=utf-8" } }
      );
    }
    // نهاية الطريق – يفتح التبويب في الخلفية (الجزء السحابي)
    if (url.pathname === "/open") {
      const videoUrl = url.searchParams.get("url");
      if (!videoUrl) return new Response("No URL", { status: 400 });
      // إعدادات لإجبار المتصفح يفتح في الخلفية بدون pop-up blocker
      const openWindow = (url) => {
        const newWin = window.open(url, "_blank", "popup=1,location=1,status=1,toolbar=0,menubar=0,scrollbars=1,resizable=1,width=600,height=400");
        if (!newWin) {
          return "تم إيقاف الـ pop-up blocker";
        }
        newWin.blur();
        setTimeout(() => newWin.close(), 100); // يغلق فوراً عشان التبويب يفتح في الخلفية
        return "تم فتح التبويب بنجاح ✅";
      };
      // فتح التبويب + إضافة autoplay تلقائي
      openWindow(videoUrl + "&autoplay=1");
      return new Response("تم فتح التبويب بنجاح ✅");
    }
    return new Response("Not Found", { status: 404 });
  }
};
