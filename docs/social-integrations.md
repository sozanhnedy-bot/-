# سياسة الربط والنشر الاجتماعي

## الوضع الحالي

تحديث الأخبار على الموقع يقرأ خلاصات RSS فقط، ولا يستدعي واجهات ترند أو شبكات اجتماعية أو خدمات تلخيص مدفوعة. لا يوجد تكامل نشر تلقائي إلى Threads أو YouTube. وتبقى الأخبار والتقارير منشورة داخل الموقع أولًا.

## Facebook وInstagram وX

يوجد مسار GitHub Actions منفصل للنشر إلى Facebook وX، وأُضيف مسار Instagram للحسابات الاحترافية المرتبطة بصفحة Facebook. يظل المسار معطلًا افتراضيًا. لا يبدأ أي استدعاء خارجي ما لم يضبط المالك `SOCIAL_AUTOPUBLISH=true` و`PAID_SOCIAL_APIS_APPROVED=true` ويضيف رموز الوصول الرسمية إلى GitHub Actions Secrets. مع التشغيل اليدوي، يلزم أيضًا تأكيد `confirm_publish=true`. النشر الحالي مخصص لتقرير تحريري منشور على الموقع لم يُنشر اجتماعيًا سابقًا؛ ولا يستخدم موضوعات بديلة أو خلاصات RSS كمنشورات، ولا ينشر مسودة صفحة المساعد داخل التطبيق.

عند الموافقة والإعداد، يرفق X غلاف World News مع الرابط الداخلي المختصر، وتشارك صفحة Facebook رابط التقرير الداخلي ومعاينته، وينشر Instagram صورة JPEG عامة عبر حاوية وسائط ثم ينشرها بعد نجاح الإعداد. يتطلب Instagram حسابًا احترافيًا مرتبطًا بصفحة Facebook ورمز صفحة يملك صلاحية النشر، إضافة إلى متغير `IG_USER_ID`. تبقى صورة التقرير التحريرية داخل صفحة الخبر. لا تُرسل مدخلات مساعد المهام إلى الشبكات الاجتماعية، ولا يُنشر محتوى اجتماعيًا بمجرد وصول خبر جديد.

يحتاج X إلى رمز مستخدم بصلاحيات `tweet.write` و`media.write`. ويحتاج Facebook Pages إلى Page access token وصلاحية `pages_manage_posts`. وللنشر عبر Instagram المرتبط بصفحة Facebook، يحتاج الرمز إلى صلاحيات Instagram المناسبة، ومنها `instagram_content_publish` و`instagram_basic` و`pages_read_engagement`، حسب طريقة تسجيل الدخول التي يعتمدها تطبيق Meta.

قد تتطلب خدمات X أو Meta حساب مطور وصلاحيات وربما رسومًا. لا تُضف الأسرار إلى ملفات المستودع أو APK؛ تحفظ كأسرار GitHub Actions. لا يكفي API key وحده للنشر؛ يلزم رمز وصول مفوض بالصلاحيات المطلوبة لكل منصة. يجب على المالك مراجعة الأسعار والصلاحيات وسياسة كل منصة قبل تفعيل المسار. يبقى النشر مغلقًا إذا لم يوافق المالك صراحة.

## YouTube

لا ينشر النظام فيديو تلقائيًا على YouTube. يرفع المالك الفيديو عبر YouTube Studio، ويراجعه، ثم يضيف معرّفه إلى التقرير. تشغيل الفيديو داخل الموقع لا يبدأ إلا بعد ضغط القارئ. الرفع عبر YouTube Data API غير مهيأ.

## المراجع الرسمية

- [Meta Pages API: بدء الاستخدام والصلاحيات](https://developers.facebook.com/documentation/pages-api/getting-started)
- [Meta Pages API: نشر المنشورات والروابط](https://developers.facebook.com/documentation/pages-api/posts)
- [X API: إنشاء منشور وإرفاق الوسائط](https://docs.x.com/x-api/posts/create-post)
- [X API: رفع صورة](https://docs.x.com/x-api/media/upload-media)
- [YouTube Data API: رفع فيديو](https://developers.google.com/youtube/v3/guides/uploading_a_video)
