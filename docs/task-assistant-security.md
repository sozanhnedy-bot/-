# مساعد المالك داخل تطبيق Android: الأمان والحدود

- لا يحتوي الموقع العام على صفحة مهام أو لوحة إدارة أو نموذج لتشغيل الشيفرة. مساعد المهام متاح داخل تطبيق Android فقط.
- يرسل التطبيق طلب GitHub الرسمي `POST /repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches` لتشغيل سير عمل محدد. يحتاج رمز GitHub محدود النطاق إلى صلاحية `Actions: write` على المستودع المقصود فقط.
- يستخدم سير العمل سر `OPENAI_API_KEY` المحفوظ في إعدادات GitHub Actions عند طلب المهمة؛ لا يرسل التطبيق مفتاح OpenAI إلى المتصفح ولا يضعه في APK.
- المستودع عام. قد تكون مدخلات تشغيل Actions ظاهرة لمن يستطيع قراءة المستودع أو سجلاته؛ لا ترسل كلمات مرور أو مفاتيح أو بيانات شخصية أو معلومات سرية.
- لا يخزن التطبيق رمز GitHub بعد الإرسال، ويُمسح حقل الرمز. يرسل الرمز إلى واجهة GitHub الرسمية فقط، لا إلى الموقع العام.
- مساعد المهام منفصل عن النشر الاجتماعي. لا ينشر إلى Facebook أو X أو YouTube، ومسار النشر الاجتماعي معطل ما لم يفعله المالك بإعداد وموافقة منفصلين.
- أي تغيير يمر بفحوصات المشروع قبل دمجه؛ لكن تشغيل المهمة أو نجاح بناء الموقع لا يجعل صفحة الإدارة عامة ولا يحدّث APK المثبت تلقائيًا.

## المراجع الرسمية

- GitHub، [Create a workflow dispatch event](https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event).
- GitHub، [Fine-grained personal access token permissions](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens).
