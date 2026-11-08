# مصادر الترندات في World News — تحقق الجدوى

**تاريخ التحقق: 8 نوفمبر 2026**

## ما يمكن للرادار إرجاعه

- **X:** واجهة X API v2 الرسمية `GET /2/trends/by/woeid/{id}` تعرض موضوعات رائجة مرتبطة بموقع WOEID. تتطلب حساب مطوّر ومشروعًا وتطبيقًا وBearer Token. صفحة التسعير الرسمية تسعّر طلب الترند بـ **0.01 دولار لكل طلب**؛ لذلك يجب أن يكون التحديث يدويًا أو محدود التكرار مع تخزين مؤقت وحد إنفاق، لا polling سريعًا. لا يوضع الرمز داخل APK؛ يستدعي التطبيق خدمة وسيطة تحفظه على الخادم. [1] [2]
- **TikTok:** لدى TikTok for Business واجهة Discovery تعرض وسومًا ومقاطع/موسيقى رائجة ضمن نطاقات زمنية. الوصول مقيّد بتسجيل مطوّر أعمال والتفويض والصلاحيات المناسبة. لم يظهر سعر أو حد عام موثوق للواجهة في الصفحات الرسمية المتاحة؛ يجب تأكيد الأهلية والسعر والحدود مع TikTok قبل تشغيلها. Research API ليست بديلًا تجاريًا عامًا، وCreative Center واجهة استكشاف وليست API عامة للاستخراج الآلي. [3] [4] [5]
- **YouTube:** واجهة YouTube Data API `videos.list` مع `chart=mostPopular` ترجع **فيديوهات شائعة** حسب البلد والفئة، وليس قائمة موضوعات ترند. سجل التغييرات يوضح أن المخطط صار مرتبطًا بفئات الموسيقى والأفلام والألعاب بدل قائمة عامة. الطلب يكلف وحدة حصة واحدة، والحصة الافتراضية الموثقة هي 10,000 وحدة يوميًا؛ تُسمّى داخل التطبيق «فيديوهات شائعة»، لا «ترندات مواضيع». [6] [7] [8]
- **Google Trends:** لا نحتاج API Alpha المحدودة لعرض الترندات الحالية. صفحة **Trending Now** العامة تشمل أكثر من 100 بلد/منطقة وتتيح تصدير النتائج كـRSS؛ وتقول مساعدة Google إن البيانات تتحدّث في المتوسط كل عشر دقائق. اختُبر رابط RSS لمصر `geo=EG` واستجاب بموضوعات حديثة. هذا يقيس عمليات بحث Google، وليس ترندات X/TikTok. [11] [12] [9]

## ربطها بتجربة التطبيق

التدفق المقترح: يعرض الرادار Google Trends RSS لمصر وأحدث أخبار الموقع؛ يختار المستخدم موضوعًا؛ يمرره التطبيق إلى Gemini لصياغة السكريبت بعد موافقة المستخدم؛ ثم يفتح استوديو الفيديو لاختيار الصور والمقاطع والصوت، ومعاينة MP4 وحفظه محليًا. لا يلزم مفتاح API أو خادم وسيط لمصدري RSS وJSON الحاليين.

لإضافة X أو TikTok أو YouTube لاحقًا، يلزم التحقق من شروط المصدر، وقد يلزم مفتاح أو خادم وسيط آمن؛ لا توضع الأسرار في APK. استخدام Google Trends RSS مع أخبار الموقع لا يتطلب هذه المفاتيح. التحديث يظل بطلب المستخدم ولا توجد polling تلقائية مفروضة على هذه المصادر.

## خيار X: هاشتاجات مخصصة وترندات حسب الدولة في RSS واحد

لم أجد بين الخدمات التي فُحصت مزوّدًا واحدًا موثقًا يجمع البحث عن الهاشتاجات/الكلمات التي يحددها المستخدم مع الترندات الساخنة حسب البلد في RSS واحد.

- **RSS.app** ينشئ تغذية من بحث X أو هاشتاج مخصص، ويدعم دمج تغذيات موجودة في Bundle. لكنه لا يوثّق مصدرًا لترندات X حسب البلد. الخطة المجانية لا تتضمن دمج التغذيات؛ السعر المنشور لخطة Basic هو 8.32 دولار شهريًا مع تحديث كل 60 دقيقة، وتُعرض الأسعار مع خيار الفوترة السنوية. التجربة المنشورة سبعة أيام. هذه الأسعار والحدود قابلة للتغيير. [13] [14] [15]
- **Apify Twitter/X Trends Scraper** يوثّق ترندات حسب الدولة/الموقع عبر API وDataset، لكنه لا يوثّق بحثًا عن هاشتاجات يحددها المستخدم ولا مخرج RSS جاهزًا. تسعيره المنشور يبدأ بـ0.000230 دولار لكل ترند على الخطة المجانية إضافة إلى 0.003 دولار لبدء التشغيل، ويتطلب رمز Apify. شروط X ومصداقية Actor المجتمعي يلزم مراجعتهما قبل الاعتماد عليه. [16] [17] [18]
- **Trends24 وGetDayTrends** يعرضان صفحات ترندات حسب البلد، لكن لم يُعثر على API أو RSS عام موثق يحقق الطلب. شروط Trends24 تمنع كشط البيانات وإعادة نشرها من مصدر آخر؛ لذلك لا أوصي ببناء التكامل على كشط صفحاتهم. [19] [20]
- **RSSHub** يوثّق تغذية بحث بالكلمة، لكن لم يظهر مسار موثق لترندات X حسب البلد. مسارات X تتطلب إعداد اعتماد، لذلك لا تحقق وحدها RSS الموحد المطلوب. [21]
- **المسار الأقرب للاعتماد على وثائق X** هو استخدام API رسميين منفصلين: بحث المنشورات للهاشتاجات والكلمات، وTrends by WOEID للترندات الجغرافية. ثم نبني خادمًا وسيطًا يجمع النتائج ويصدر RSS. تسعير X المنشور هو 0.005 دولار لكل مورد منشور مقروء و0.010 دولار لطلب الترند، ويتطلب حساب مطور ومشروعًا وتطبيقًا ورمز وصول. هذه ليست RSS جاهزة من X، كما أن سياسة X تضع قيودًا على إعادة توزيع المحتوى وإبقائه محدثًا وحذف ما لم يعد متاحًا؛ يجب مراجعة السياسة قبل نشر تغذية عامة. لا يُحفظ رمز X داخل APK. [1] [2] [22] [23]

**القرار العملي:** يظل X API الرسمي هو المسار الأوضح من ناحية الاعتماد، لكنه مدفوع حسب الطلب. اختير ApiDirect كتجربة تقنية منخفضة التكلفة، وليس كمصدر رسمي أو موثّق الترخيص من X. يجب أن يظهر هذا التحفظ للمستخدم قبل أول طلب، وألا نعرض نتائجه على أنها قائمة رسمية مؤكدة. Google Trends RSS وأخبار الموقع يظلان مصادر مستقلة إضافية.

## ApiDirect: حدود الربط المختار

توثّق صفحة ApiDirect واجهة ترندات `GET /v1/twitter/trends?woeid=...` بسعر 0.006 دولار لكل طلب بعد المجاني، مع 50 طلبًا مجانًا لكل endpoint في الشهر، ودون اشتراط بطاقة للدخول إلى الطبقة المجانية. وتسمح قائمة المواقع العامة باستخدام WOEID مصر `23424802`. لكن شروط ApiDirect تصف البيانات بأنها من منصات طرف ثالث ولا تحدد في الصفحات التي راجعناها هل الوصول إلى X يتم عبر API مرخّص؛ كما تجعل الالتزام بشروط المنصات الأساسية مسؤولية المستخدم. لذلك هذا خيار قابل للتجربة، لا ضمان امتثال لـX. [24] [25] [26]

صفحة البحث تعرض استعلامًا موحدًا وحقولًا تتضمن عنوان النتيجة والمقتطف والناشر؛ ولم توثق صفحة البحث التي راجعناها صيغة OR لعدة هاشتاجات. لذلك يرسل التطبيق هاشتاجًا واحدًا في كل طلب لتجنب افتراض صيغة غير موثقة. [29]

إرشادات مطوري X تمنع الوصول غير الرسمي عبر الكشط/الأتمتة خارج API، وتفرض قيودًا على عرض بيانات X وإعادة توزيعها. لهذا يعرض التطبيق ApiDirect بوضوح كطرف مستقل، ويطلب موافقة أول استخدام ويشترط 18+ حسب شروط ApiDirect. لا نعرض نصوص منشورات أو أسماء مستخدمين في الرادار. في بحث الهاشتاجات، تُحلّل مقتطفات المزود في الذاكرة لاستخراج أسماء الهاشتاجات وأعدادها، ثم تُتلف؛ Gemini يتلقى أسماء الترندات والعدادات المجمعة فقط. إذا كان الاستخدام يتطلب اعتماد X رسميًا، يجب عدم الاعتماد على هذا المزود والانتقال إلى X API الرسمي بعد قبول سعره وشروطه. [27] [28]

التكامل في Android يدوي فقط، بلا polling أو مهام خلفية. يخزن مفتاح ApiDirect مشفرًا باستخدام Android Keystore على الجهاز ولا يضعه في الكود أو BuildConfig أو APK. يوقف التطبيق الطلبات المحلية لكل endpoint عند 45 طلبًا شهريًا لترك هامش من أصل 50 مجانًا؛ هذا حد محلي وليس رصيد حساب موثوقًا، وقد يتأثر بمسح بيانات التطبيق أو استخدام جهاز آخر. لا تُضاف وسيلة دفع إذا كان الهدف البقاء مجانيًا. التحقق الفعلي للاتصال يظل معلقًا على إدخال مفتاح المستخدم؛ لا يمكن تأكيد استجابة API قبل ذلك.

## المصادر

[1]: https://docs.x.com/x-api/trends/trends-by-woeid/introduction "X Trends by WOEID"
[2]: https://docs.x.com/x-api/getting-started/pricing "X API pay-per-usage pricing"
[3]: https://business-api.tiktok.com/portal/docs/discovery-api-overview/v1.3 "TikTok Business Discovery API"
[4]: https://developers.tiktok.com/doc/about-research-api/ "TikTok Research API eligibility"
[5]: https://ads.tiktok.com/creative/creativeCenter/trends "TikTok Creative Center trends"
[6]: https://developers.google.com/youtube/v3/docs/videos/list "YouTube videos.list reference"
[7]: https://developers.google.com/youtube/v3/revision_history "YouTube Data API revision history"
[8]: https://developers.google.com/youtube/v3/determine_quota_cost "YouTube API quota calculator"
[9]: https://developers.google.com/search/apis/trends "Google Trends API alpha"
[10]: https://support.google.com/trends/answer/4365533?hl=en "Google Trends data FAQ"
[11]: https://support.google.com/trends/answer/3076011?hl=en "Google Trends Trending Now and RSS export"
[12]: https://trends.google.com/trending/rss?geo=EG "Google Trends RSS feed for Egypt"
[13]: https://rss.app/rss-feed/create-twitter-rss-feed "RSS.app X feed generator"
[14]: https://rss.app/pricing "RSS.app plans and refresh rates"
[15]: https://help.rss.app/en/articles/10627664-how-to-combine-rss-feeds-into-one-feed "RSS.app feed bundles"
[16]: https://apify.com/automation-lab/twitter-trends-scraper "Apify country trends actor"
[17]: https://apify.com/automation-lab/twitter-trends-scraper/api "Apify actor API"
[18]: https://docs.apify.com/legal/actor-terms-and-conditions "Apify Actor terms"
[19]: https://trends24.in/terms "Trends24 terms"
[20]: https://getdaytrends.com/help/ "GetDayTrends help"
[21]: https://docs.rsshub.app/routes/social-media#x-twitter "RSSHub X/Twitter routes"
[22]: https://docs.x.com/x-api/posts/search/introduction "X recent post search API"
[23]: https://docs.x.com/developer-terms/policy "X Developer Policy"
[24]: https://apidirect.io/endpoints/twitter-trends "API Direct Twitter Trends endpoint and pricing"
[25]: https://apidirect.io/terms "API Direct Terms of Service"
[26]: https://apidirect.io/static/twitter-trends-locations.json "API Direct WOEID locations"
[27]: https://docs.x.com/developer-guidelines "X Developer Guidelines"
[28]: https://docs.x.com/developer-terms/agreement "X Developer Agreement"
[29]: https://apidirect.io/endpoints/twitter-search "API Direct Twitter search overview"
