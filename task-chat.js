(() => {
  "use strict";

  const OWNER = "sozanhnedy-bot";
  const REPO = "-";
  const WORKFLOW = "ai-task.yml";
  const API_VERSION = "2026-03-10";
  const form = document.getElementById("task-form");
  const tokenField = document.getElementById("github-token");
  const taskField = document.getElementById("task-prompt");
  const consent = document.getElementById("public-consent");
  const submit = document.getElementById("task-submit");
  const status = document.getElementById("task-status");
  const counter = document.getElementById("task-length");

  if (!form || !tokenField || !taskField || !submit || !status) return;

  taskField.addEventListener("input", () => {
    counter.textContent = String(taskField.value.length);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const token = tokenField.value.trim();
    const task = taskField.value.trim();
    if (!token || !task || !consent.checked) {
      status.textContent = "أدخل الرمز والطلب، ثم وافق على تنبيه الخصوصية.";
      status.dataset.state = "error";
      return;
    }
    if (task.length > 2400) {
      status.textContent = "الطلب أطول من الحد المسموح؛ اختصره إلى 2400 حرف.";
      status.dataset.state = "error";
      return;
    }

    submit.disabled = true;
    status.textContent = "جارٍ إرسال طلب التشغيل مباشرة إلى GitHub…";
    status.dataset.state = "pending";

    try {
      const endpoint = `https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW}/dispatches`;
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Accept": "application/vnd.github+json",
          "Authorization": `Bearer ${token}`,
          "Content-Type": "application/json",
          "X-GitHub-Api-Version": API_VERSION
        },
        body: JSON.stringify({
          ref: "main",
          inputs: { task }
        })
      });

      if (!response.ok) {
        const messages = {
          401: "رفض GitHub الرمز. تأكد من أنه صالح ولم تنتهِ مدته.",
          403: "ليس للرمز صلاحية Actions: Read and write على المستودع الصحيح.",
          404: "لم يعثر GitHub على سير العمل. قد لا يكون قد نُشر بعد على الفرع main.",
          422: "رفض GitHub الطلب. تحقق من طول النص ومن إعداد سير العمل."
        };
        throw new Error(messages[response.status] || `تعذر تشغيل المهمة (HTTP ${response.status}).`);
      }

      let runUrl = `https://github.com/${OWNER}/${REPO}/actions/workflows/${WORKFLOW}`;
      if (response.status !== 204) {
        try {
          const result = await response.json();
          if (result.html_url) runUrl = result.html_url;
        } catch (_) {
          // The stable workflow run list is still useful if GitHub omits a response body.
        }
      }
      status.replaceChildren();
      status.dataset.state = "success";
      status.append(document.createTextNode("تم تشغيل المهمة. تابع نتيجتها وحالة النشر من "));
      const link = document.createElement("a");
      link.href = runUrl;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = "GitHub Actions";
      status.append(link);
      taskField.value = "";
      counter.textContent = "0";
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : "تعذر إرسال المهمة.";
      status.dataset.state = "error";
    } finally {
      tokenField.value = "";
      submit.disabled = false;
    }
  });
})();
