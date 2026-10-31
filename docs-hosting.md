# ملاحظات الاستضافة

تُنشر الواجهة الثابتة على GitHub Pages من الفرع `main` باستخدام GitHub Actions. الموقع قابل للتثبيت كتطبيق ويب تقدمي (PWA) ويستخدم ملفات ثابتة فقط للواجهة والموجز.

- [GitHub Pages](https://docs.github.com/en/pages)
- [GitHub Pages مع GitHub Actions](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
- [GitHub Actions: الجدولة](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [OpenAI Responses API](https://developers.openai.com/api/reference/python/)

تُحفظ `OPENAI_API_KEY` في GitHub Actions Secrets عند الرغبة في التلخيص الآلي المدفوع. بدون المفتاح يستخدم الموقع مقتطف RSS المتاح. لا تُدرج أي مفتاح في الملفات العامة.
