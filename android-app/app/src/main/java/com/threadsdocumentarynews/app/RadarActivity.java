package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

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

/**
 * Free-first trend radar. It derives topics from the public World News archive;
 * no X/Facebook credentials are used and no post is published from this screen.
 */
public final class RadarActivity extends Activity {
    private static final String NEWS_URL = "https://sozanhnedy-bot.github.io/-/data/news.json";
    private static final int BACKGROUND = Color.rgb(8, 9, 13);
    private static final int SURFACE = Color.rgb(20, 21, 27);
    private static final int SURFACE_ALT = Color.rgb(27, 28, 35);
    private static final int GOLD = Color.rgb(255, 187, 54);
    private static final int TEXT = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);
    private static final int LINE = Color.rgb(54, 55, 64);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private ProgressBar progress;
    private TextView status;
    private EditText manualInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("World News — رادار الترند");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(10), dp(18), dp(10));
        TextView title = text("رادار الترند  ·  World News", 18, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button assistant = secondaryButton("المساعد");
        header.addView(assistant, new LinearLayout.LayoutParams(dp(100), dp(46)));
        assistant.setOnClickListener(v -> openAssistant(""));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout hero = card();
        addText(hero, "اصطد الموضوع، وحوّله إلى مشروع", 23, TEXT, true, 0, 8);
        addText(hero, "الرادار يستخرج أكثر الكلمات والموضوعات تكرارًا من الأخبار المنشورة. لا يدّعي أن هذا قياس رسمي لترندات X، ولا يرسل أي شيء إلى الشبكات الاجتماعية.", 14, MUTED, false, 0, 12);
        Button refresh = primaryButton("تحديث الترندات");
        hero.addView(refresh, params(-1, dp(52), 0, 0, 0, 5));
        refresh.setOnClickListener(v -> loadTrends());
        status = text("اضغط تحديث الترندات لقراءة آخر أرشيف الأخبار.", 13, GOLD, false);
        hero.addView(status, params(-1, -2, 0, dp(6), 0, 0));
        content.addView(hero, params(-1, -2, 0, 0, 0, 14));

        LinearLayout manual = card();
        addText(manual, "رصدت موضوعًا بنفسك؟", 17, TEXT, true, 0, 6);
        manualInput = new EditText(this);
        manualInput.setHint("اكتب موضوعًا أو ترندًا يدويًا…");
        manualInput.setHintTextColor(Color.rgb(128, 129, 137));
        manualInput.setTextColor(TEXT);
        manualInput.setTextSize(15);
        manualInput.setGravity(Gravity.RIGHT | Gravity.TOP);
        manualInput.setMinLines(2);
        manualInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        manualInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        manual.addView(manualInput, params(-1, -2, 0, 0, 0, 8));
        Button useManual = secondaryButton("استخدامه في المساعد");
        manual.addView(useManual, params(-1, dp(48), 0, 0, 0, 0));
        useManual.setOnClickListener(v -> openAssistant(manualInput.getText().toString().trim()));
        content.addView(manual, params(-1, -2, 0, 0, 0, 14));

        LinearLayout trendsCard = card();
        addText(trendsCard, "ما يشتعل الآن", 19, TEXT, true, 0, 4);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        trendsCard.addView(progress, params(-1, dp(38), 0, 4, 0, 4));
        list = trendsCard;
        content.addView(trendsCard);
        setContentView(root);
        loadTrends();
    }

    private void loadTrends() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (status != null) status.setText("جارٍ قراءة أرشيف الأخبار العام…");
        executor.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(NEWS_URL + "?v=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IllegalStateException("تعذر الوصول إلى أرشيف الأخبار.");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = input.read(chunk)) != -1 && buffer.size() < 2_000_000) buffer.write(chunk, 0, count);
                } finally {
                    connection.disconnect();
                }
                List<TrendScorer.Trend> trends = score(new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8)).optJSONArray("items"));
                runOnUiThread(() -> render(trends));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (status != null) status.setText("تعذر تحديث الترندات. جرّب لاحقًا أو أضف موضوعًا يدويًا.");
                });
            }
        });
    }

    private List<TrendScorer.Trend> score(JSONArray items) {
        List<String> titles = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) titles.add(item.optString("title", ""));
            }
        }
        return TrendScorer.score(titles, 12);
    }

    private void render(List<TrendScorer.Trend> trends) {
        if (progress != null) progress.setVisibility(View.GONE);
        while (list.getChildCount() > 2) list.removeViewAt(2);
        if (status != null) status.setText("تم استخراج " + trends.size() + " موضوعات من الأخبار العامة. راجعها قبل استخدامها.");
        if (trends.isEmpty()) {
            addText(list, "لا توجد بيانات كافية حاليًا.", 14, MUTED, false, 10, 0);
            return;
        }
        for (TrendScorer.Trend trend : trends) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(rounded(SURFACE_ALT, LINE, 16));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            addText(copy, "#" + trend.getLabel() + "  ·  " + trend.getCount() + " إشارات", 15, TEXT, true, 0, 4);
            addText(copy, trend.getExample(), 12, MUTED, false, 0, 0);
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button use = secondaryButton("استخدم");
            row.addView(use, new LinearLayout.LayoutParams(dp(88), dp(44)));
            use.setOnClickListener(v -> openAssistant(trend.getLabel()));
            list.addView(row, params(-1, -2, 0, dp(7), 0, 0));
        }
    }

    private void openAssistant(String topic) {
        Intent intent = new Intent(this, TaskAssistantActivity.class);
        if (topic != null && !topic.trim().isEmpty()) intent.putExtra("trend_topic", topic.trim());
        startActivity(intent);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackground(rounded(SURFACE, LINE, 22));
        return layout;
    }

    private Button primaryButton(String caption) {
        Button button = secondaryButton(caption);
        button.setTextColor(Color.rgb(25, 20, 10));
        button.setTextSize(15);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(rounded(GOLD, GOLD, 18));
        return button;
    }

    private Button secondaryButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(TEXT);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(rounded(SURFACE_ALT, LINE, 16));
        return button;
    }

    private void addText(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(value, size, color, bold);
        parent.addView(view, params(-1, -2, 0, top, 0, bottom));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

}
