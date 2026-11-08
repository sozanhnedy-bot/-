package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** News analysis and attributed third-party trend feeds; provider results are not official X rankings. */
public final class RadarActivity extends Activity {
    private static final String NEWS_URL = "https://sozanhnedy-bot.github.io/-/data/news.json";
    private static final String GOOGLE_TRENDS_RSS_URL = "https://trends.google.com/trending/rss?geo=EG";
    private static final String APIDIRECT_BASE_URL = "https://apidirect.io/v1/twitter";
    private static final String API_DIRECT_SIGNUP_URL = "https://apidirect.io/signup";
    private static final long EGYPT_WOEID = 23424802L;
    private static final int MAX_FEED_BYTES = 4_000_000;
    private static final int MAX_APIDIRECT_RESPONSE_BYTES = 1_500_000;
    private static final int LOCAL_MONTHLY_REQUEST_LIMIT = 45;
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int INK = Color.rgb(31, 39, 48);
    private static final int MUTED = Color.rgb(92, 102, 111);
    private static final int RED = Color.rgb(170, 55, 55);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText focusInput;
    private TextView status;
    private TextView result;
    private ProgressBar progress;
    private Button analyzeButton;
    private Button apiDirectKeyButton;
    private Button xTrendsButton;
    private Button xHashtagSearchButton;
    private Button xAnalyzeButton;
    private Button googleTrendsButton;
    private Button localNewsButton;
    private TextView feedStatus;
    private LinearLayout feedList;
    private List<XTrendData> latestXData = new ArrayList<>();

    private static final class TrendItem {
        final String title;
        final String source;
        final String detail;
        final String sourceUrl;
        TrendItem(String title, String source, String detail) {
            this(title, source, detail, "");
        }
        TrendItem(String title, String source, String detail, String sourceUrl) {
            this.title = title;
            this.source = source;
            this.detail = detail;
            this.sourceUrl = sourceUrl;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("World News — تحليل الأخبار بالذكاء الاصطناعي");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);

        TextView heading = new TextView(this);
        heading.setText("تحليل الأخبار بالذكاء الاصطناعي");
        heading.setTextSize(19);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        heading.setPadding(dp(18), dp(12), dp(18), dp(12));
        heading.setBackgroundColor(NAVY);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView explanation = bodyText(
                "رادار الأخبار يجمع Google Trends لمصر وأخبار موقعك. ويمكنك تشغيل رادار X يدويًا عبر ApiDirect ثم إرسال أسماء الترندات والعدادات المجمعة فقط إلى Gemini لتحليلها.", 15, INK);
        content.addView(explanation, margins(-1, -2, 0, 0, 0, 14));

        LinearLayout sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.VERTICAL);
        sources.setPadding(dp(14), dp(14), dp(14), dp(14));
        sources.setBackgroundColor(Color.WHITE);
        TextView sourcesTitle = bodyText("رادار المصادر والموضوعات", 18, INK);
        sourcesTitle.setTypeface(null, Typeface.BOLD);
        sources.addView(sourcesTitle, margins(-1, -2, 0, 0, 0, 4));
        TextView sourcesNote = bodyText("ApiDirect مزود مستقل غير تابع لـX؛ شروطه تضع مسؤولية الالتزام بشروط المنصة على المستخدم، لذلك لا نعرضه كمصدر رسمي من X. لا يوجد تحديث تلقائي بالخلفية. الخطة المجانية المنشورة: 50 طلبًا لكل endpoint شهريًا بلا بطاقة؛ التطبيق يوقف طلباته المحلية عند 45 احتياطًا.", 13, MUTED);
        sources.addView(sourcesNote, margins(-1, -2, 0, 0, 0, 8));
        apiDirectKeyButton = new Button(this);
        apiDirectKeyButton.setText(ApiDirectCredentialStore.hasKey(this) ? "تغيير / مسح مفتاح ApiDirect" : "إعداد مفتاح ApiDirect بأمان");
        apiDirectKeyButton.setAllCaps(false);
        apiDirectKeyButton.setTextColor(NAVY);
        sources.addView(apiDirectKeyButton, margins(-1, dp(46), 0, 0, 0, 6));
        apiDirectKeyButton.setOnClickListener(v -> showApiDirectKeyDialog());
        xTrendsButton = new Button(this);
        xTrendsButton.setText("جلب ترندات X لمصر الآن");
        xTrendsButton.setAllCaps(false);
        xTrendsButton.setTextColor(Color.WHITE);
        xTrendsButton.setBackgroundColor(NAVY);
        sources.addView(xTrendsButton, margins(-1, dp(48), 0, 0, 0, 5));
        xTrendsButton.setOnClickListener(v -> loadApiDirectTrends());
        xHashtagSearchButton = new Button(this);
        xHashtagSearchButton.setText("ابحث عن هاشتاج X واحد");
        xHashtagSearchButton.setAllCaps(false);
        xHashtagSearchButton.setTextColor(Color.WHITE);
        xHashtagSearchButton.setBackgroundColor(NAVY);
        sources.addView(xHashtagSearchButton, margins(-1, dp(48), 0, 0, 0, 5));
        xHashtagSearchButton.setOnClickListener(v -> searchApiDirectHashtags());
        xAnalyzeButton = new Button(this);
        xAnalyzeButton.setText("حلّل نتائج X المجمعة بواسطة Gemini");
        xAnalyzeButton.setAllCaps(false);
        xAnalyzeButton.setTextColor(Color.WHITE);
        xAnalyzeButton.setBackgroundColor(Color.rgb(72, 55, 130));
        xAnalyzeButton.setEnabled(false);
        sources.addView(xAnalyzeButton, margins(-1, dp(48), 0, 0, 0, 7));
        xAnalyzeButton.setOnClickListener(v -> confirmXAggregateAnalysisConsent());
        googleTrendsButton = new Button(this);
        googleTrendsButton.setText("تحديث ترندات Google — مصر");
        googleTrendsButton.setAllCaps(false);
        googleTrendsButton.setTextColor(Color.WHITE);
        googleTrendsButton.setBackgroundColor(NAVY);
        sources.addView(googleTrendsButton, margins(-1, dp(48), 0, 0, 0, 5));
        googleTrendsButton.setOnClickListener(v -> loadGoogleTrends());
        localNewsButton = new Button(this);
        localNewsButton.setText("عرض أحدث أخبار موقعي");
        localNewsButton.setAllCaps(false);
        localNewsButton.setTextColor(Color.WHITE);
        localNewsButton.setBackgroundColor(NAVY);
        sources.addView(localNewsButton, margins(-1, dp(48), 0, 0, 0, 7));
        localNewsButton.setOnClickListener(v -> loadLocalNews());
        feedStatus = bodyText("لم يتم تحديث مصادر الموضوعات بعد.", 13, MUTED);
        sources.addView(feedStatus, margins(-1, -2, 0, 0, 0, 6));
        feedList = new LinearLayout(this);
        feedList.setOrientation(LinearLayout.VERTICAL);
        sources.addView(feedList, margins(-1, -2, 0, 0, 0, 0));
        content.addView(sources, margins(-1, -2, 0, 0, 0, 16));

        TextView focusLabel = bodyText("هاشتاج X للبحث — واحد في كل طلب", 14, INK);
        focusLabel.setTypeface(null, Typeface.BOLD);
        content.addView(focusLabel, margins(-1, -2, 0, 0, 0, 6));
        focusInput = new EditText(this);
        focusInput.setSingleLine(true);
        focusInput.setTextColor(INK);
        focusInput.setHintTextColor(MUTED);
        focusInput.setHint("مثال: #AI أو #مصر أو #الذكاء_الاصطناعي");
        focusInput.setTextSize(15);
        focusInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        focusInput.setBackgroundColor(Color.WHITE);
        content.addView(focusInput, margins(-1, dp(52), 0, 0, 0, 12));

        TextView privacy = bodyText(
                "الخصوصية والمفتاح: مفتاح ApiDirect الذي تدخله يُشفّر ويُحفظ على هذا الجهاز عبر Android Keystore، ولا يُضمّن في الشيفرة أو إعدادات APK. طلبات X تُرسل يدويًا من الجهاز إلى ApiDirect. عند تحليل نتائج X، يرسل Gemini أسماء الترندات والهاشتاجات والمقاييس المجمعة فقط؛ لا نرسل إليه نصوص المنشورات أو أسماء الحسابات أو روابطها. لا تضف وسيلة دفع إذا كنت تريد البقاء ضمن المجاني. بيانات X من مزود مستقل ليست اعتمادًا رسميًا من X.", 13, MUTED);
        content.addView(privacy, margins(-1, -2, 0, 0, 0, 14));

        analyzeButton = new Button(this);
        analyzeButton.setText("حلّل الأخبار الآن بواسطة Gemini");
        analyzeButton.setAllCaps(false);
        analyzeButton.setTextColor(Color.WHITE);
        analyzeButton.setTextSize(15);
        analyzeButton.setBackgroundColor(NAVY);
        content.addView(analyzeButton, margins(-1, dp(54), 0, 0, 0, 8));
        analyzeButton.setOnClickListener(v -> confirmAndAnalyze());

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        content.addView(progress, margins(-1, dp(34), 0, 0, 0, 4));
        status = bodyText("التحليل يستند إلى عينة من أحدث الأخبار المنشورة.", 13, MUTED);
        content.addView(status, margins(-1, -2, 0, 0, 0, 12));

        TextView resultHeading = bodyText("نتيجة التحليل", 19, INK);
        resultHeading.setTypeface(null, Typeface.BOLD);
        content.addView(resultHeading, margins(-1, -2, 0, dp(8), 0, 6));
        result = bodyText("لم يُجرَ تحليل بعد.", 15, INK);
        result.setTextIsSelectable(true);
        result.setPadding(dp(16), dp(16), dp(16), dp(16));
        result.setBackgroundColor(Color.WHITE);
        content.addView(result, margins(-1, -2, 0, 0, 0, 0));

        setContentView(root);
    }

    private void showApiDirectKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(15);
        keyInput.setHint("الصق مفتاح ApiDirect هنا");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(20), dp(4), dp(20), dp(4));
        dialogContent.addView(keyInput, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView note = bodyText("المفتاح لا يظهر بعد الحفظ، ويُخزّن مشفرًا على هذا الجهاز فقط. لا ترسله في المحادثة ولا تضفه إلى GitHub أو ملف APK.", 12, MUTED);
        dialogContent.addView(note, margins(-1, -2, 0, 8, 0, 0));
        boolean hasKey = ApiDirectCredentialStore.hasKey(this);
        if (hasKey) {
            Button clearKey = new Button(this);
            clearKey.setText("مسح المفتاح من هذا الجهاز");
            clearKey.setAllCaps(false);
            dialogContent.addView(clearKey, margins(-1, dp(44), 0, 6, 0, 0));
            clearKey.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("مسح المفتاح المحفوظ؟")
                    .setMessage("سيُحذف المفتاح المشفر من هذا الجهاز فقط.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("مسح", (d, w) -> {
                        ApiDirectCredentialStore.clear(this);
                        apiDirectKeyButton.setText("إعداد مفتاح ApiDirect بأمان");
                        feedStatus.setText("تم مسح المفتاح المحفوظ من هذا الجهاز.");
                        feedStatus.setTextColor(MUTED);
                    })
                    .show());
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مفتاح ApiDirect")
                .setView(dialogContent)
                .setNegativeButton("إلغاء", null)
                .setNeutralButton("إنشاء حساب / المفتاح", (d, w) -> openApiDirectSignup())
                .setPositiveButton("حفظ مشفرًا", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    ApiDirectCredentialStore.save(this, keyInput.getText().toString());
                    apiDirectKeyButton.setText("تغيير / مسح مفتاح ApiDirect");
                    feedStatus.setText("تم حفظ المفتاح مشفرًا على هذا الجهاز. لم يُرسل إلى المحادثة أو GitHub.");
                    feedStatus.setTextColor(MUTED);
                    dialog.dismiss();
                } catch (Exception error) {
                    keyInput.setError(safeError(error, "تعذر حفظ المفتاح."));
                }
            });
        });
        dialog.show();
        if (hasKey) {
            // Do not prefill or reveal a previously saved secret.
            keyInput.setHint("أدخل مفتاحًا جديدًا لاستبداله؛ اتركه فارغًا للإلغاء");
        }
    }

    private void openApiDirectSignup() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(API_DIRECT_SIGNUP_URL)));
        } catch (Exception error) {
            feedStatus.setText("تعذر فتح صفحة التسجيل. افتح apidirect.io/signup يدويًا.");
        }
    }

    private void withApiDirectConsent(Runnable action) {
        android.content.SharedPreferences preferences = getSharedPreferences("apidirect_terms", MODE_PRIVATE);
        if (preferences.getBoolean("accepted_18_plus_and_terms_v1", false)) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("مراجعة مهمة قبل ربط X")
                .setMessage("يتطلب ApiDirect أن يكون المستخدم 18 عامًا أو أكثر. المزود مستقل وغير تابع لـX، وشروطه تحمّلك مسؤولية الالتزام بشروط المنصة. لا توضح صفحة المزود أنه يستخدم واجهة X الرسمية؛ لذلك لا نقدر نضمن توافق مصدره مع سياسات X. لا تستخدمه إن كنت تحتاج اعتمادًا رسميًا. الاستخدام يدوي، والخطة المجانية المعلنة 50 طلبًا لكل endpoint شهريًا بلا بطاقة؛ التطبيق يوقف الطلبات المحلية عند 45.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("عمري 18+ وأوافق على المتابعة", (dialog, which) -> {
                    preferences.edit().putBoolean("accepted_18_plus_and_terms_v1", true).apply();
                    action.run();
                })
                .show();
    }

    private String getSavedApiDirectKey() {
        try {
            String key = ApiDirectCredentialStore.read(this);
            if (key != null && !key.trim().isEmpty()) return key;
        } catch (Exception error) {
            setFeedBusy(false, safeError(error, "تعذر قراءة مفتاح ApiDirect."));
            return null;
        }
        setFeedBusy(false, "أدخل مفتاح ApiDirect أولًا؛ تقدر تنشئه من الزر المخصص أعلى الرادار.");
        return null;
    }

    private void loadApiDirectTrends() {
        final String apiKey = getSavedApiDirectKey();
        if (apiKey == null) {
            showApiDirectKeyDialog();
            return;
        }
        withApiDirectConsent(() -> startXLoad("trends", "جارٍ جلب ترندات مصر من ApiDirect…",
                () -> ApiDirectXClient.loadEgyptTrends(apiKey)));
    }

    private void searchApiDirectHashtags() {
        List<String> hashtags = parseSearchHashtags(focusInput.getText().toString());
        if (hashtags.isEmpty()) {
            setFeedBusy(false, "اكتب هاشتاجًا واحدًا في خانة البحث أسفل الرادار.");
            return;
        }
        final String apiKey = getSavedApiDirectKey();
        if (apiKey == null) {
            showApiDirectKeyDialog();
            return;
        }
        withApiDirectConsent(() -> startXLoad("posts", "جارٍ البحث عن " + android.text.TextUtils.join(" · ", hashtags) + " عبر ApiDirect…",
                () -> ApiDirectXClient.searchHashtags(apiKey, hashtags)));
    }

    private List<String> parseSearchHashtags(String raw) {
        List<String> result = new ArrayList<>();
        Pattern valid = Pattern.compile("^[\\p{L}\\p{N}_]{2,48}$");
        for (String part : raw.split("[,،\\s]+")) {
            String tag = part.trim().replaceFirst("^#", "");
            if (!tag.isEmpty() && valid.matcher(tag).matches() && !result.contains(tag)) result.add(tag);
            if (result.size() >= 1) break;
        }
        return result;
    }

    private interface XDataLoader {
        List<XFeedItem> load() throws Exception;
    }

    private void startXLoad(String endpointKey, String startMessage, XDataLoader loader) {
        if (!reserveApiRequest(endpointKey)) {
            setFeedBusy(false, "وصل هذا الجهاز للحد الوقائي المحلي (45 طلبًا لهذا endpoint خلال الشهر). لا تُضف وسيلة دفع؛ انتظر بداية الشهر القادم.");
            return;
        }
        setFeedBusy(true, startMessage);
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<XFeedItem> loaded = null;
            String error = null;
            try {
                loaded = loader.load();
                if (loaded.isEmpty()) error = "لم يرجع ApiDirect أي موضوع صالح. جرّب لاحقًا أو راجع الهاشتاجات.";
            } catch (Exception exception) {
                error = safeError(exception, "تعذر الاتصال بـApiDirect. افحص الإنترنت والمفتاح والحصة المجانية.");
            }
            final List<XFeedItem> results = loaded;
            final String failure = error;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (failure != null) {
                    setFeedBusy(false, "لم يكتمل رادار X: " + failure);
                    latestXData = new ArrayList<>();
                    xAnalyzeButton.setEnabled(false);
                    feedList.removeAllViews();
                    feedList.addView(bodyText(failure, 14, RED), margins(-1, -2, 0, 0, 0, 8));
                    return;
                }
                latestXData = new ArrayList<>();
                List<TrendItem> displayItems = new ArrayList<>();
                for (XFeedItem item : results) {
                    latestXData.add(item.getAnalysisData());
                    displayItems.add(new TrendItem(item.getName(), "X · عبر ApiDirect", item.getDetail(), item.getSourceUrl()));
                }
                xAnalyzeButton.setEnabled(!latestXData.isEmpty());
                setFeedBusy(false, "تم جلب " + displayItems.size() + " نتيجة من ApiDirect. راجعها وتحقق من الأخبار قبل اعتمادها.");
                renderFeed(displayItems);
            });
        });
    }

    private boolean reserveApiRequest(String endpointKey) {
        android.content.SharedPreferences preferences = getSharedPreferences("apidirect_monthly_usage", MODE_PRIVATE);
        String month = new java.text.SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        if (!month.equals(preferences.getString("month", ""))) {
            preferences.edit().clear().putString("month", month).apply();
        }
        String countKey = "used_" + endpointKey;
        int used = preferences.getInt(countKey, 0);
        if (used >= LOCAL_MONTHLY_REQUEST_LIMIT) return false;
        preferences.edit().putInt(countKey, used + 1).apply();
        return true;
    }

    private void confirmXAggregateAnalysisConsent() {
        if (latestXData.isEmpty()) {
            setFeedBusy(false, "جلب ترندات أو ابحث عن هاشتاج قبل طلب التحليل.");
            return;
        }
        android.content.SharedPreferences preferences = getSharedPreferences("gemini_x_aggregate_consent", MODE_PRIVATE);
        if (preferences.getBoolean("accepted_v1", false)) {
            analyzeLatestXData();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إرسال الملخص المجمع إلى Gemini؟")
                .setMessage("سيُرسل إلى Gemini أسماء الترندات والهاشتاجات والاستعلامات والمقاييس والتوقيت فقط. لن نرسل نصوص المنشورات أو أسماء الحسابات أو روابطها. لا يُعد ذلك تحققًا من الخبر أو إثباتًا لترند رسمي. تابع فقط إذا كنت موافقًا على معالجة هذه البيانات بواسطة Google Gemini.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("أوافق على التحليل", (dialog, which) -> {
                    preferences.edit().putBoolean("accepted_v1", true).apply();
                    analyzeLatestXData();
                })
                .show();
    }

    private void analyzeLatestXData() {
        if (latestXData.isEmpty()) {
            setFeedBusy(false, "جلب ترندات أو ابحث عن هاشتاج قبل طلب التحليل.");
            return;
        }
        final String focus = focusInput.getText().toString().trim();
        final List<XTrendData> analysisData = new ArrayList<>(latestXData);
        xAnalyzeButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("جارٍ إرسال أسماء الترندات والعدادات المجمعة فقط إلى Gemini…");
        executor.execute(() -> {
            try {
                String analysis = GeminiXTrendAnalyzer.analyzeBlocking(this, analysisData, focus);
                runOnUiThread(() -> {
                    result.setText(analysis);
                    status.setText("حلّل Gemini " + analysisData.size() + " عنصرًا مجمعًا. لم تُرسل نصوص المنشورات أو أسماء الحسابات.");
                });
            } catch (Exception error) {
                String message = safeError(error, "تعذر تحليل نتائج X بواسطة Gemini.");
                runOnUiThread(() -> status.setText("لم يكتمل تحليل X: " + message));
            } finally {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    xAnalyzeButton.setEnabled(!latestXData.isEmpty());
                });
            }
        });
    }

    private void openXSearchUrl(String address) {
        try {
            android.net.Uri uri = android.net.Uri.parse(address);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean trustedHost = "x.com".equals(host) || "www.x.com".equals(host)
                    || "twitter.com".equals(host) || "www.twitter.com".equals(host);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost
                    || uri.getPath() == null || !uri.getPath().startsWith("/search")) {
                throw new IllegalArgumentException("رابط بحث X غير آمن.");
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception error) {
            setFeedBusy(false, "تعذر فتح بحث X: " + safeError(error, "رابط غير صالح."));
        }
    }

    private void loadGoogleTrends() {
        setFeedBusy(true, "جارٍ تحميل Google Trends لمصر عبر RSS الرسمي…");
        executor.execute(() -> {
            List<TrendItem> items = new ArrayList<>();
            String error = null;
            try {
                byte[] bytes = readUrlBytes(GOOGLE_TRENDS_RSS_URL, "application/rss+xml, application/xml, text/xml", MAX_FEED_BYTES);
                items = parseGoogleTrendsRss(bytes);
                if (items.isEmpty()) error = "وصل RSS لكن لم أجد موضوعات صالحة فيه.";
            } catch (Exception e) {
                error = safeError(e, "تعذر قراءة تغذية Google Trends.");
            }
            final List<TrendItem> loaded = items;
            final String failure = error;
            runOnUiThread(() -> {
                setFeedBusy(false, failure == null ? "تم تحديث Google Trends لمصر — " + loaded.size() + " موضوعًا." : failure);
                if (failure == null) renderFeed(loaded);
            });
        });
    }

    private void loadLocalNews() {
        setFeedBusy(true, "جارٍ تحميل أحدث الأخبار المنشورة على موقعك…");
        executor.execute(() -> {
            List<TrendItem> items = new ArrayList<>();
            String error = null;
            try {
                byte[] bytes = readUrlBytes(NEWS_URL + "?v=" + System.currentTimeMillis(), "application/json", MAX_FEED_BYTES);
                List<NewsHeadline> headlines = parseHeadlines(new String(bytes, StandardCharsets.UTF_8));
                for (NewsHeadline headline : headlines) {
                    String detail = headline.getSource() + " · " + headline.getCategory() + " · " + headline.getPublishedAt();
                    items.add(new TrendItem(headline.getTitle(), "أخبار الموقع", detail));
                }
                if (items.isEmpty()) error = "لم توجد عناوين أخبار صالحة في الملف المنشور.";
            } catch (Exception e) {
                error = safeError(e, "تعذر تحميل ملف أخبار الموقع.");
            }
            final List<TrendItem> loaded = items;
            final String failure = error;
            runOnUiThread(() -> {
                setFeedBusy(false, failure == null ? "تم تحميل " + loaded.size() + " عنوانًا من أخبار موقعك." : failure);
                if (failure == null) renderFeed(loaded);
            });
        });
    }

    private byte[] readUrlBytes(String address, String accept, int maxBytes) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent", "WorldNews-Android/1.6");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("المصدر أعاد HTTP " + code + ".");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > maxBytes) throw new IllegalStateException("حجم التغذية أكبر من الحد المتوقع.");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<TrendItem> parseGoogleTrendsRss(byte[] xml) throws Exception {
        org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        List<TrendItem> items = new ArrayList<>();
        boolean inItem = false;
        String activeTag = "";
        String title = "";
        String date = "";
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && items.size() < 60) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equalsIgnoreCase(name)) {
                    inItem = true;
                    title = "";
                    date = "";
                } else if (inItem && ("title".equalsIgnoreCase(name) || "pubDate".equalsIgnoreCase(name))) {
                    activeTag = name;
                }
            } else if (event == XmlPullParser.TEXT && inItem) {
                if ("title".equalsIgnoreCase(activeTag)) title += parser.getText();
                else if ("pubDate".equalsIgnoreCase(activeTag)) date += parser.getText();
            } else if (event == XmlPullParser.END_TAG) {
                String name = parser.getName();
                if (inItem && "item".equalsIgnoreCase(name)) {
                    if (!title.trim().isEmpty()) items.add(new TrendItem(title.trim(), "Google Trends — مصر", date.trim()));
                    inItem = false;
                    activeTag = "";
                } else if (name != null && name.equalsIgnoreCase(activeTag)) {
                    activeTag = "";
                }
            }
            event = parser.next();
        }
        return items;
    }

    private void renderFeed(List<TrendItem> items) {
        feedList.removeAllViews();
        for (TrendItem item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackgroundColor(Color.rgb(249, 248, 245));
            TextView title = bodyText(item.title, 16, INK);
            title.setTypeface(null, Typeface.BOLD);
            row.addView(title, margins(-1, -2, 0, 0, 0, 4));
            if (!item.detail.isEmpty()) {
                TextView detail = bodyText(item.source + " · " + item.detail, 11, MUTED);
                row.addView(detail, margins(-1, -2, 0, 0, 0, 5));
            } else {
                row.addView(bodyText(item.source, 11, MUTED), margins(-1, -2, 0, 0, 0, 5));
            }
            if (!item.sourceUrl.isEmpty()) {
                Button openX = new Button(this);
                openX.setText("افتح نتائج البحث على X");
                openX.setAllCaps(false);
                openX.setTextColor(NAVY);
                row.addView(openX, margins(-1, dp(40), 0, 0, 0, 4));
                openX.setOnClickListener(v -> openXSearchUrl(item.sourceUrl));
            }
            Button makeVideo = new Button(this);
            makeVideo.setText("ابدأ سكريبت وفيديو عن هذا الموضوع");
            makeVideo.setAllCaps(false);
            makeVideo.setTextColor(Color.WHITE);
            makeVideo.setBackgroundColor(NAVY);
            row.addView(makeVideo, margins(-1, dp(44), 0, 0, 0, 0));
            makeVideo.setOnClickListener(v -> startAssistantWithTopic(item));
            feedList.addView(row, margins(-1, -2, 0, 0, 0, 7));
        }
    }

    private void startAssistantWithTopic(TrendItem item) {
        Intent intent = new Intent(this, TaskAssistantActivity.class);
        intent.putExtra(TaskAssistantActivity.EXTRA_RADAR_TOPIC, item.title);
        intent.putExtra(TaskAssistantActivity.EXTRA_RADAR_SOURCE, item.source);
        intent.putExtra(TaskAssistantActivity.EXTRA_RADAR_CONTEXT, item.detail);
        startActivity(intent);
    }

    private void setFeedBusy(boolean busy, String message) {
        apiDirectKeyButton.setEnabled(!busy);
        xTrendsButton.setEnabled(!busy);
        xHashtagSearchButton.setEnabled(!busy);
        xAnalyzeButton.setEnabled(!busy && !latestXData.isEmpty());
        googleTrendsButton.setEnabled(!busy);
        localNewsButton.setEnabled(!busy);
        feedStatus.setText(message);
        feedStatus.setTextColor(busy ? NAVY : MUTED);
    }

    private String safeError(Exception error, String fallback) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.substring(0, Math.min(240, message.length()));
    }

    private void confirmAndAnalyze() {
        new AlertDialog.Builder(this)
                .setTitle("إرسال عناوين إلى Google Gemini؟")
                .setMessage("سيُرسل التطبيق حتى 40 عنوانًا عامًا مع مصدرها وقسمها وتاريخها، إضافة إلى المجال الاختياري المكتوب. يستخدم Gemini هذه البيانات لإعداد التحليل. في الخطة المجانية قد تستخدم Google الطلبات لتحسين منتجاتها. تابع فقط إذا كنت موافقًا.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("أوافق وأحلل", (dialog, which) -> startAnalysis())
                .show();
    }

    private void startAnalysis() {
        analyzeButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("جارٍ تحميل عناوين الأخبار وإرسالها إلى Gemini للتحليل…");
        String focus = focusInput.getText().toString().trim();
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(NEWS_URL + "?v=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("تعذر تحميل أرشيف الأخبار (HTTP " + code + ").");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = input.read(chunk)) != -1) {
                        if (bytes.size() + count > 4_000_000) throw new IllegalStateException("ملف الأخبار أكبر من الحد المتوقع.");
                        bytes.write(chunk, 0, count);
                    }
                }
                List<NewsHeadline> headlines = parseHeadlines(bytes.toString(StandardCharsets.UTF_8.name()));
                String analysis = analyzeWithGemini(headlines, focus);
                runOnUiThread(() -> {
                    result.setText(analysis);
                    status.setText("اكتمل التحليل باستخدام Gemini " + GeminiNewsAnalyzer.MODEL_NAME + " لعينة من " + headlines.size() + " عنوانًا.");
                });
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = "تعذر إكمال التحليل. تحقق من الإنترنت وإعداد Firebase/App Check ثم أعد المحاولة.";
                final String shown = message;
                runOnUiThread(() -> status.setText("لم يكتمل التحليل: " + shown));
            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    analyzeButton.setEnabled(true);
                });
            }
        });
    }

    private String analyzeWithGemini(List<NewsHeadline> headlines, String focus) throws Exception {
        return GeminiNewsAnalyzer.analyzeBlocking(this, headlines, focus);
    }

    static List<NewsHeadline> parseHeadlines(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("items");
        List<NewsHeadline> headlines = new ArrayList<>();
        if (items == null) return headlines;
        for (int i = 0; i < items.length() && headlines.size() < GeminiNewsAnalyzer.MAX_HEADLINES; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("title", "").trim();
            if (title.isEmpty()) continue;
            headlines.add(new NewsHeadline(
                    title,
                    item.optString("source_name", "مصدر غير محدد"),
                    item.optString("category", "غير مصنف"),
                    item.optString("published_at", item.optString("summary_date", "تاريخ غير محدد"))
            ));
        }
        return headlines;
    }

    private TextView bodyText(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT | Gravity.TOP);
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
