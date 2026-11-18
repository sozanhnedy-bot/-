package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class VisitDetectionActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int GREEN = Color.rgb(35, 125, 82);
    private static final int RED = Color.rgb(175, 55, 55);
    private final List<Session> sessions = new ArrayList<>();
    private LinearLayout results;
    private TextView summary;
    private EditText youtubeUrl;
    private final Random random = new Random();

    private static final class Session {
        boolean expectedBot;
        boolean predictedBot;
        int watchSeconds;
        int repeatCount;
        int gapSeconds;
        String source;
        String reason;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        root.setBackgroundColor(PAPER);
        scroll.addView(root);

        TextView title = text("لوحة اختبار كشف الزيارات", 22, NAVY, true);
        root.addView(title);
        TextView note = text("وضع TEST محلي — كل السجلات التالية اصطناعية ولا تمثل مشاهدات YouTube حقيقية.", 14, RED, true);
        note.setPadding(0, dp(8), 0, dp(14));
        root.addView(note);

        youtubeUrl = new EditText(this);
        youtubeUrl.setSingleLine(true);
        youtubeUrl.setTextSize(14);
        youtubeUrl.setHint("الصق رابط فيديو YouTube للاحتفاظ به وفتحه يدويًا");
        youtubeUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(youtubeUrl, new LinearLayout.LayoutParams(-1, dp(54)));

        Button open = button("فتح الفيديو على YouTube");
        open.setOnClickListener(v -> openYoutube());
        root.addView(open);

        Button generate = button("توليد 20 جلسة اختبار اصطناعية");
        generate.setOnClickListener(v -> generateSessions());
        root.addView(generate);

        Button export = button("تصدير بيانات الاختبار CSV");
        export.setOnClickListener(v -> exportCsv());
        root.addView(export);

        Button clear = button("مسح نتائج الاختبار");
        clear.setOnClickListener(v -> {
            sessions.clear();
            renderResults();
        });
        root.addView(clear);

        summary = text("لم يتم توليد بيانات بعد.", 15, NAVY, true);
        summary.setPadding(0, dp(16), 0, dp(10));
        root.addView(summary);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        setContentView(scroll);
    }

    private void generateSessions() {
        sessions.clear();
        for (int i = 0; i < 20; i++) {
            Session s = new Session();
            s.expectedBot = i >= 12;
            if (!s.expectedBot) {
                s.watchSeconds = 25 + random.nextInt(240);
                s.repeatCount = random.nextInt(3);
                s.gapSeconds = 45 + random.nextInt(900);
                s.source = new String[]{"بحث YouTube", "رابط مباشر", "اقتراحات"}[random.nextInt(3)];
            } else {
                int pattern = i % 4;
                s.watchSeconds = pattern == 0 ? 2 + random.nextInt(5) : 3 + random.nextInt(18);
                s.repeatCount = pattern == 1 ? 10 + random.nextInt(20) : 4 + random.nextInt(10);
                s.gapSeconds = pattern == 2 ? 1 + random.nextInt(4) : 3 + random.nextInt(20);
                s.source = pattern == 3 ? "مصدر متكرر" : "نمط آلي تجريبي";
            }
            s.predictedBot = s.watchSeconds < 12 || s.repeatCount >= 8 || s.gapSeconds < 5;
            if (s.watchSeconds < 12) s.reason = "مدة مشاهدة قصيرة جدًا";
            else if (s.repeatCount >= 8) s.reason = "تكرار مرتفع";
            else if (s.gapSeconds < 5) s.reason = "فواصل زمنية غير طبيعية";
            else s.reason = "لا توجد إشارة كافية للاشتباه";
            sessions.add(s);
        }
        renderResults();
    }

    private void renderResults() {
        results.removeAllViews();
        if (sessions.isEmpty()) {
            summary.setText("لم يتم توليد بيانات بعد. اضغط زر توليد الجلسات لبدء الاختبار.");
            return;
        }
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (Session s : sessions) {
            if (s.expectedBot && s.predictedBot) tp++;
            else if (!s.expectedBot && !s.predictedBot) tn++;
            else if (!s.expectedBot) fp++;
            else fn++;
        }
        int correct = tp + tn;
        double accuracy = 100.0 * correct / sessions.size();
        summary.setText(String.format(Locale.US,
                "النتائج: %d جلسة اختبار\nصحيح: %d | إنذارات كاذبة: %d | حالات فائتة: %d\nالدقة التجريبية: %.1f%%\nمصفوفة الالتباس — TP: %d، TN: %d، FP: %d، FN: %d",
                sessions.size(), correct, fp, fn, accuracy, tp, tn, fp, fn));

        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, dp(4), 0, dp(4));
            results.addView(card, cp);
            boolean correctLabel = s.expectedBot == s.predictedBot;
            TextView head = text("جلسة #" + (i + 1) + " — " + (s.predictedBot ? "مشتبه بها" : "طبيعية حسب الكاشف"), 15, s.predictedBot ? RED : GREEN, true);
            card.addView(head);
            card.addView(text("الحقيقة التجريبية: " + (s.expectedBot ? "BOT" : "HUMAN") + " | تقييم الكاشف: " + (correctLabel ? "صحيح" : "خطأ"), 13, NAVY, false));
            card.addView(text("المصدر: " + s.source + " | مدة المشاهدة: " + s.watchSeconds + " ثانية", 13, NAVY, false));
            card.addView(text("التكرار: " + s.repeatCount + " | الفاصل: " + s.gapSeconds + " ثانية", 13, NAVY, false));
            card.addView(text("سبب التقييم: " + s.reason, 13, NAVY, false));
        }
    }


    private void exportCsv() {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "ولّد جلسات الاختبار أولًا.", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("dataset,session_id,expected_label,predicted_label,watch_seconds,repeat_count,gap_seconds,source,reason\\n");
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            csv.append("SYNTHETIC_LOCAL,")
                    .append(i + 1).append(',')
                    .append(s.expectedBot ? "BOT" : "HUMAN").append(',')
                    .append(s.predictedBot ? "SUSPICIOUS" : "NORMAL").append(',')
                    .append(s.watchSeconds).append(',')
                    .append(s.repeatCount).append(',')
                    .append(s.gapSeconds).append(',')
                    .append(csvValue(s.source)).append(',')
                    .append(csvValue(s.reason)).append('\\n');
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_SUBJECT, "Synthetic local visit test dataset");
        share.putExtra(Intent.EXTRA_TEXT, csv.toString());
        try {
            startActivity(Intent.createChooser(share, "تصدير بيانات الاختبار"));
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح خيارات التصدير.", Toast.LENGTH_LONG).show();
        }
    }

    private String csvValue(String value) {
        if (value == null) return "\\"\\"";
        return "\\"" + value.replace("\\"", "\\"\\"") + "\\"";
    }

    private void openYoutube() {
        String value = youtubeUrl.getText().toString().trim();
        Uri uri;
        try {
            uri = Uri.parse(value);
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException();
            if (host == null || !(host.equalsIgnoreCase("youtube.com") || host.endsWith(".youtube.com") || host.equalsIgnoreCase("youtu.be"))) throw new IllegalArgumentException();
        } catch (Exception e) {
            Toast.makeText(this, "أدخل رابط YouTube صحيحًا يبدأ بـ https://", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح الرابط على الجهاز.", Toast.LENGTH_LONG).show();
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(NAVY);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50));
        p.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(p);
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
