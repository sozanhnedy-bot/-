# تشغيل بوابة ثريدز وثائقي

## رابط الموقع

بعد نجاح سير النشر على GitHub Pages سيكون رابط الموقع:

`https://sozanhnedy-bot.github.io/-/`

الموقع ثابت وقابل للتثبيت على شاشة هاتف Android من قائمة المتصفح **إضافة إلى الشاشة الرئيسية**. لا يحتاج الزائر إلى حساب أو تسجيل دخول.

## النشر على GitHub Pages

يُنشر الموقع تلقائيًا من الفرع `main` عبر GitHub Actions. من إعدادات المستودع افتح **Settings → Pages**، واختر **GitHub Actions** كمصدر للنشر إذا لم يُفعّل تلقائيًا عند أول تشغيل. تابع النتيجة من تبويب **Actions** حتى يظهر سير **Publish portal to GitHub Pages** ناجحًا.

## تحديث الأخبار

يقرأ سير **Update news summaries** خلاصات RSS المحددة في `data/news_sources.json` ويحدّث `data/news.json`. يبقى التحديث الآلي متوقفًا إلى أن تضيف المتغير `AUTOMATION_ENABLED` بقيمة `true` إلى **Settings → Secrets and variables → Actions → Variables**.

من دون السر `OPENAI_API_KEY` تُستخدم مقتطفات RSS كما هي. عند إضافة المفتاح، تُنشأ ملخصات عربية عبر OpenAI API وقد تُفرض رسوم حسب حساب المزود. لا تضف المفتاح إلى ملفات المستودع أو الشيفرة؛ احفظه في **Actions Secrets** فقط.

جدولة GitHub Actions كل عشر دقائق أفضل جهد وقد تتأخر أو تُسقط بعض التشغيلات. لا يحدث نشر تلقائي على حسابات التواصل الاجتماعي.

## الخصوصية

الموقع لا يتطلب تسجيل دخول من الزائر ولا يتضمن تحليلات تتبع. المحتوى يأتي من خلاصات المصادر وروابطها الأصلية، وتضمين YouTube يستخدم المشغّل الرسمي عند توفر معرّف فيديو.

## الفحص المحلي

```bash
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
node --check app.js
python -m http.server 8000
```

افتح `http://localhost:8000` للمعاينة المحلية.
