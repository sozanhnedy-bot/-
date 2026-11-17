# ملاحظات الاستضافة

تُنشر الواجهة الثابتة على GitHub Pages باستخدام GitHub Actions. الموقع قابل للتثبيت كتطبيق ويب تقدمي (PWA)، وتُعرض الأخبار والتقارير وصفحاتها الداخلية من ملفات ثابتة.

- [GitHub Pages](https://docs.github.com/en/pages)
- [GitHub Pages مع GitHub Actions](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
- [GitHub Actions: الجدولة](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [GitHub Actions: الفوترة](https://docs.github.com/en/billing/concepts/product-billing/github-actions)

يقرأ التحديث الدوري خلاصات RSS فقط، ويعمل مرتين في الساعة من دون طلبات OpenAI أو X أو Facebook. لا يحتاج استمرار الموقع إلى رصيد API مدفوع؛ لكن سياسات GitHub Pages وActions قد تتغير، لذا راجع سياسة الفوترة الرسمية. مساعد المهام داخل تطبيق Android مسار منفصل يستخدم أسرار GitHub عند طلب المالك لمهمة برمجية.

تعمل المهمة المجدولة في الدقائق 5 و20 و35 و50 من كل ساعة (UTC)، أي محاولة كل 15 دقيقة. يسجل كل تشغيل عدد الأخبار التي استقبلها من كل مصدر، وأحدث تاريخ نشر وصل منه، وأي مصادر فشلت. يعيد جلب RSS المحاولة حتى ثلاث مرات عند أخطاء الشبكة المؤقتة أو أخطاء HTTP القابلة لإعادة المحاولة. لكن GitHub يوضح أن أحداث الجدولة قد تتأخر أو تُسقط عند ضغط الخدمة؛ لذلك الجدول بأفضل جهد ولا يضمن توقيتًا دقيقًا. تبقى آخر نسخة منشورة متاحة عند تأخر التحديث.


## Parallel news discovery (RSS + GDELT)

The refresh workflow can query the free GDELT DOC 2.0 endpoint alongside configured RSS feeds. GDELT contributes headlines, publisher URLs, and discovery timestamps only; it does not supply article text or images. RSS remains active independently. A failure from one source is logged and does not block successful results from the other. GDELT calls are spaced by the configured pause to reduce rate-limit pressure. No OpenAI key is required and AI summarization remains disabled by default.
