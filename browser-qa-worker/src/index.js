const ALLOWED_HOSTS = new Set(["example.com"]);

const PAGE = `<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cloud Browser QA</title>
<style>
:root{color-scheme:dark;--bg:#0b1220;--panel:#14243a;--line:#2b405b;--text:#edf4ff;--muted:#9aabc0;--accent:#2dd4c2}
*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:20px;background:linear-gradient(160deg,#172e48,#080e19);color:var(--text);font-family:system-ui,sans-serif;line-height:1.6}
main{max-width:680px;margin:5vh auto;padding:24px;background:var(--panel);border:1px solid var(--line);border-radius:20px}
h1{text-align:center;color:var(--accent);font-size:25px;margin:0 0 6px}.sub{text-align:center;color:var(--muted);margin:0 0 22px}
label{display:block;color:var(--muted);margin:14px 0 6px}input{width:100%;padding:13px;border:1px solid var(--line);border-radius:10px;background:#091321;color:var(--text);font-size:16px}
button{width:100%;margin-top:18px;padding:14px;border:0;border-radius:11px;background:var(--accent);color:#071521;font-weight:800;font-size:16px}
button:disabled{opacity:.5}.status{white-space:pre-wrap;background:#091321;padding:14px;border-radius:10px;min-height:54px;margin-top:18px;direction:ltr;text-align:left}
small{color:var(--muted)} 
</style>
</head>
<body><main>
<h1>Cloud Browser QA</h1>
<p class="sub">اختبار فتح صفحة ويب داخل متصفح Cloudflare السحابي</p>
<label for="token">رمز الدخول</label><input id="token" type="password" autocomplete="off" placeholder="QA token">
<label for="url">رابط صفحة الاختبار</label><input id="url" type="url" value="https://example.com/" autocomplete="url">
<button id="run">تشغيل اختبار الصفحة</button>
<div class="status" id="status" aria-live="polite">جاهز للاختبار.</div>
<small>الاختبار مقيد حاليًا بالمضيف example.com فقط. الرمز لا يُحفظ في المتصفح.</small>
</main>
<script>
const token=document.getElementById("token"),url=document.getElementById("url"),run=document.getElementById("run"),status=document.getElementById("status");
run.addEventListener("click",async()=>{
 run.disabled=true;status.textContent="جارٍ تشغيل المتصفح السحابي...";
 try{
  const response=await fetch("/api/run",{method:"POST",headers:{"content-type":"application/json","authorization":"Bearer "+token.value},body:JSON.stringify({url:url.value})});
  const data=await response.json();status.textContent=JSON.stringify(data,null,2);
 }catch(e){status.textContent="فشل الاتصال: "+e.message;}
 finally{run.disabled=false;}
});
</script></body></html>`;

function json(data,status=200){return new Response(JSON.stringify(data,null,2),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}});}

function validateTarget(value){
  let target;
  try{target=new URL(value);}catch{return null;}
  if(target.protocol!=="https:"||target.username||target.password)return null;
  if(!ALLOWED_HOSTS.has(target.hostname))return null;
  if(target.port && target.port!=="443")return null;
  return target;
}

export default {
 async fetch(request,env){
  const url=new URL(request.url);
  if(request.method==="GET"&&url.pathname==="/"){
   return new Response(PAGE,{headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-store","x-content-type-options":"nosniff"}});
  }
  if(request.method!=="POST"||url.pathname!=="/api/run")return new Response("Not Found",{status:404});
  if(!env.QA_TOKEN)return json({ok:false,error:"QA_TOKEN secret is not configured."},503);
  const auth=request.headers.get("authorization")||"";
  if(auth!=="Bearer "+env.QA_TOKEN)return json({ok:false,error:"Unauthorized."},401);
  let body;
  try{body=await request.json();}catch{return json({ok:false,error:"Invalid JSON body."},400);}
  const target=validateTarget(body?.url);
  if(!target)return json({ok:false,error:"URL rejected. Only HTTPS https://example.com/ is allowed in this QA build."},400);
  if(!env.BROWSER||typeof env.BROWSER.script!=="function")return json({ok:false,error:"Cloudflare Browser binding BROWSER is unavailable."},503);
  try{
   const result=await env.BROWSER.script(
    `async ({page,browser})=>{try{await page.route("**/*",route=>{try{const requestUrl=new URL(route.request().url());if(requestUrl.protocol!=="https:"||requestUrl.hostname!=="example.com")return route.abort();return route.continue();}catch{return route.abort();}});const response=await page.goto(${JSON.stringify(target.href)},{waitUntil:"domcontentloaded",timeout:20000});const finalUrl=new URL(page.url());if(finalUrl.protocol!=="https:"||finalUrl.hostname!=="example.com")throw new Error("Navigation left the allowed host.");return {url:page.url(),title:await page.title(),httpStatus:response?response.status():null,host:finalUrl.hostname};}finally{await browser.close();}}`,
    {library:"playwright",timeout:30000,keepAlive:10000}
   );
   const data=await result.json();
   if(!result.ok||!data.success)return json({ok:false,error:"Browser Run failed.",details:data},502);
   const value=data.result||{};
   if(value.host!=="example.com")return json({ok:false,error:"Final host was not allowed.",details:value},502);
   return json({ok:true,message:"Cloud browser test completed.",...value});
  }catch(error){
   return json({ok:false,error:"Cloud browser execution failed.",details:String(error).slice(0,500)},502);
  }
 }
};
