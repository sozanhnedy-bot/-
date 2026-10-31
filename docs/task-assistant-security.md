# مساحة المهام: الأمان وحدود الخصوصية

- تستدعي الواجهة نقطة GitHub الرسمية `POST /repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches` لتشغيل سير عمل محدد. صلاحية Fine-grained Personal Access Token المطلوبة هي **Actions: write** على المستودع المحدد فقط؛ ولا تحتاج الواجهة إلى صلاحية كتابة الملفات.
- يقرأ سير العمل الطلب، ويرسله إلى OpenAI باستخدام `OPENAI_API_KEY` المحفوظ سرًا في إعدادات GitHub Actions، ثم يطبق التعديل المسموح به، ويجري فحوصًا ثابتة، وينشر التغيير إلى الموقع إذا نجحت.
- الطلبات تُرسل إلى مستودع عام؛ لذلك قد تكون مدخلات تشغيل Actions ظاهرة لمن يستطيع قراءة المستودع. لا تكتب في مساحة المهام كلمات مرور أو مفاتيح API أو بيانات خاصة أو معلومات شخصية.
- لا تحفظ الواجهة رمز GitHub في التخزين المحلي. يُمسح حقل الرمز بعد كل محاولة، ويرسل المتصفح الرمز إلى `api.github.com` فقط. لا يُرسل مفتاح OpenAI إلى المتصفح.
- مساعد المهام يغيّر الشيفرة فقط ولا ينشر اجتماعيًا. النشر اليومي على X وFacebook مسار منفصل ومغلق حتى تُربط الحسابات ويُفعّل `SOCIAL_AUTOPUBLISH=true`. YouTube غير مربوط بعد؛ ويجب أن يبقى نشره موقوفًا حتى يراجع المالك الفيديو ويوافق عليه.

## المراجع الرسمية

- GitHub، [Create a workflow dispatch event](https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event): المدخلات وطريقة تشغيل سير العمل.
- GitHub، [Fine-grained personal access token permissions](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens): يحدد `Actions: write` لطلب تشغيل workflow dispatch.
