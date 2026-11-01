# ملاحظات الاستضافة

تُنشر الواجهة الثابتة على GitHub Pages من الفرع `main` باستخدام GitHub Actions. الموقع قابل للتثبيت كتطبيق ويب تقدمي (PWA) ويستخدم ملفات ثابتة فقط للواجهة والموجز.

- [GitHub Pages](https://docs.github.com/en/pages)
- [GitHub Pages مع GitHub Actions](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
- [GitHub Actions: الجدولة](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [GitHub Actions: الفوترة](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [OpenAI Responses API](https://developers.openai.com/api/reference/python/)

التحديث الدوري يستخدم خلاصات RSS وGoogle Trends، ولا يستلم `OPENAI_API_KEY` أو رمز X؛ لذلك لا يرسل طلبات إلى هاتين الواجهتين المدفوعتين ولا يحتاج رصيد API كي يبقى الموقع يعمل. المستودع عام ويستخدم GitHub Actions القياسي وGitHub Pages؛ راجع سياسة الفوترة الرسمية لأنها قد تتغير. مساعد التعديلات اليدوي منفصل وقد يستخدم OpenAI عند طلب تغيير برمجي فقط.

تعمل مهمة تحديث الأخبار والترندات مرتين في الساعة، عند الدقيقتين 5 و35، مع نشر البيانات عند تغيرها. يذكر GitHub أن أحداث الجدولة قد تتأخر أو تُسقط أثناء ضغط الخدمة؛ لذلك لا يمكن ضمان توقيت دقيق. [تفاصيل الجدولة الرسمية](https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#schedule). الموقع المنشور يظل متاحًا بآخر نسخة حتى لو تأخر تشغيل مهمة التحديث.
