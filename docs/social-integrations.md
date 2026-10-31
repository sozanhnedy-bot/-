# سياسة الربط والنشر الاجتماعي

## Threads

تكامل Threads الحالي للقراءة العامة فقط عند إضافة رمز رسمي وصلاحيات Meta المناسبة. يحتفظ الموقع بإشارات مجمعة؛ لا ينشر إلى Threads ولا يحفظ نصوص المنشورات أو أسماء المستخدمين. البحث العام بالكلمات المفتاحية يحتاج اعتماد الصلاحية عبر Meta App Review، وقد يقتصر قبل الاعتماد على محتوى الحساب المفوّض.

## Facebook وX

طلب المالك هو نشر منشور واحد يوميًا تلقائيًا على صفحة Facebook وحساب X بعد الربط الرسمي. سير العمل `publish-social.yml` لا يعمل إلا عندما تكون قيمة المستودع `SOCIAL_AUTOPUBLISH` مساوية لـ `true`، ويتوقف عند `false` أو عند غياب بيانات الاعتماد. لا تُرسل مدخلات دردشة ChatGPT إلى الشبكات.

يستخدم X رمز مستخدم بصلاحيات `tweet.write` و`media.write`: يرفع النظام صورة مضغوطة ثم ينشئ منشورًا يضم الصورة والرابط الداخلي. تستخدم صفحة Facebook رمز Page access token و`FB_PAGE_ID`، وتنشر عبر Pages API رابط التقرير الداخلي كي تظهر معاينة الصورة. إذا لم يوجد تقرير داخلي لم يُنشر سابقًا، يستخدم النظام موجز ترندات محدودًا بدل تكرار خبر قديم أو اختراع تفاصيل.

لا تضع الرموز في ملفات عامة أو في APK؛ احفظها كأسرار GitHub Actions. يتطلب Facebook صفحة تديرها وتطبيق Meta مع صلاحيات مثل `pages_manage_posts` و`pages_show_list`. X يتطلب حساب مطوّر وصلاحيات OAuth للمستخدم. X API قد يكون مدفوعًا حسب الاستخدام؛ وثيقة رفع الوسائط تعرض تقديرًا قدره 0.010 دولار لكل طلب رفع في وقت التوثيق، وقد تختلف الأسعار. كما أن قراءة X Trends كل عشر دقائق معطّلة افتراضيًا حتى يضبط المالك `X_TRENDS_ENABLED=true` بعد مراجعة التكلفة.

## YouTube

لا ينشر النظام فيديو تلقائيًا على YouTube. يرفع المالك الفيديو عبر YouTube Studio، ويراجعه، ثم يضيف معرّف الفيديو إلى التقرير. يتطلب الرفع عبر YouTube Data API مشروع Google Cloud وتفويض OAuth بصلاحية `youtube.upload`، وهذا الربط غير متاح في الإعداد الحالي. يجب أن يضغط المالك موافقة منفصلة قبل أي نشر مستقبلي إلى YouTube.

## المراجع الرسمية

- [Meta Pages API: بدء الاستخدام والصلاحيات](https://developers.facebook.com/documentation/pages-api/getting-started)
- [Meta Pages API: نشر المنشورات والروابط](https://developers.facebook.com/documentation/pages-api/posts)
- [X API: إنشاء منشور وإرفاق الوسائط](https://docs.x.com/x-api/posts/create-post)
- [X API: رفع صورة](https://docs.x.com/x-api/media/upload-media)
- [X API: الترندات حسب WOEID](https://docs.x.com/x-api/trends/trends-by-woeid/introduction)
- [YouTube Data API: رفع فيديو](https://developers.google.com/youtube/v3/guides/uploading_a_video)
