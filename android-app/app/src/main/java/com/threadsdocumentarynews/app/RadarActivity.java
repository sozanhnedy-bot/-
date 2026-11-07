package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** Cloud-based semantic analysis; this screen does not claim to measure platform trends. */
public final class RadarActivity extends Activity {
    private static final String NEWS_URL = "https://sozanhnedy-bot.github.io/-/data/news.json";
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int INK = Color.rgb(31, 39, 48);
    private static final int MUTED = Color.rgb(92, 102, 111);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText focusInput;
    private TextView status;
    private TextView result;
    private ProgressBar progress;
    private Button analyzeButton;

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
                "Gemini يجمع العناوين المتشابهة معنويًا، ويشرح الموضوعات البارزة وزوايا التغطية والأسئلة التي لا تكفي العناوين للإجابة عنها. هذا تحليل للعينة المنشورة، وليس قياسًا لترندات X أو تحققًا من صحة الأخبار.", 15, INK);
        content.addView(explanation, margins(-1, -2, 0, 0, 0, 14));

        TextView focusLabel = bodyText("مجال تريد التركيز عليه — اختياري", 14, INK);
        focusLabel.setTypeface(null, Typeface.BOLD);
        content.addView(focusLabel, margins(-1, -2, 0, 0, 0, 6));
        focusInput = new EditText(this);
        focusInput.setSingleLine(true);
        focusInput.setTextColor(INK);
        focusInput.setHintTextColor(MUTED);
        focusInput.setHint("مثال: الاقتصاد أو أخبار المنطقة");
        focusInput.setTextSize(15);
        focusInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        focusInput.setBackgroundColor(Color.WHITE);
        content.addView(focusInput, margins(-1, dp(52), 0, 0, 0, 12));

        TextView privacy = bodyText(
                "الخصوصية: عند التحليل نرسل إلى Google Gemini حتى 40 عنوانًا عامًا مع المصدر والقسم والتاريخ، إضافةً إلى المجال الذي تكتبه هنا. لا نرسل ملخصات أو روابط الأخبار. في الخطة المجانية قد تستخدم Google الطلبات لتحسين منتجاتها. ستظهر موافقة قبل كل إرسال.", 13, MUTED);
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
