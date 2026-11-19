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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class VisitDetectionActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int GREEN = Color.rgb(35, 125, 82);
    private static final int RED = Color.rgb(175, 55, 55);
    private static final int CREATE_CSV_REQUEST = 4102;
    private String pendingCsv;
    private final List<Session> sessions = new ArrayList<>();
    private LinearLayout results;
    private TextView summary;
    private EditText youtubeUrl;
    private EditText sessionCountInput;
    private EditText durationHoursInput;
    private final Random random = new Random();

    private static final class Session {
        boolean expectedBot;
        boolean predictedBot;
        int watchSeconds;
        int repeatCount;
        int gapSeconds;
        String source;
        String reason;
        int elapsedMinutes;
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

        TextView title = text("محرك إدارة واختبار المهام", 22, NAVY, true);
        root.addView(title);
        TextView note = text("محرك محلي لإنشاء واختبار حتى 20,000 مهمة. لا يرسل مشاهدات ولا يتصل بيوتيوب.", 14, RED, true);
        note.setPadding(0, dp(8), 0, dp(14));
        root.addView(note);

        youtubeUrl = new EditText(this);
        youtubeUrl.setSingleLine(true);
        youtubeUrl.setTextSize(14);
        youtubeUrl.setHint("قسم YouTube غير مفعّل");
        youtubeUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        youtubeUrl.setEnabled(false);
        youtubeUrl.setAlpha(0.55f);
        root.addView(youtubeUrl, new LinearLayout.LayoutParams(-1, dp(54)));

        Button open = button("YouTube — غير مفعّل");
        open.setEnabled(false);
        open.setAlpha(0.55f);
        root.addView(open);

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        sessionCountInput = new EditText(this);
        sessionCountInput.setSingleLine(true);
        sessionCountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        sessionCountInput.setHint("عدد المهام (حتى 20000)");
        sessionCountInput.setText("20000");
        settings.addView(sessionCountInput, new LinearLayout.LayoutParams(0, dp(54), 2));
        durationHoursInput = new EditText(this);
        durationHoursInput.setSingleLine(true);
        durationHoursInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        durationHoursInput.setHint("مدة الاختبار: 24 أو 48 ساعة");
        durationHoursInput.setText("24");
        settings.addView(durationHoursInput, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(settings);

        Button generate = button("إنشاء مهام اختبار محلية");
        generate.setOnClickListener(v -> generateSessions());
        root.addView(generate);

        Button export = button("تصدير سجل المهام CSV");
        export.setOnClickListener(v -> exportCsv());
        root.addView(export);

        Button clear = button("مسح مهام الاختبار");
        clear.setOnClickListener(v -> {
            sessions.clear();
            renderResults();
        });
        root.addView(clear);

        summary = text("لم يتم إنشاء مهام بعد.", 15, NAVY, true);
        summary.setPadding(0, dp(16), 0, dp(10));
        root.addView(summary);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        setContentView(scroll);
    }

    private void generateSessions() {
        sessions.clear();
        int requestedCount = parseBounded(sessionCountInput.getText().toString(), 20000, 1, 20000);
        int durationHours = parseBounded(durationHoursInput.getText().toString(), 24, 24, 48);
        if (durationHours != 24 && durationHours != 48) durationHours = 24;
        for (int i = 0; i < requestedCount; i++) {
            Session s = new Session();
            s.elapsedMinutes = (int) (((long) i * durationHours * 60L) / requestedCount);
            s.expectedBot = (i % 5) == 0;
            if (!s.expectedBot) {
                s.watchSeconds = 25 + random.nextInt(240);
                s.repeatCount = random.nextInt(3);
                s.gapSeconds = 45 + random.nextInt(900);
                s.source = new String[]{"نمط بشري اصطناعي: بحث", "نمط بشري اصطناعي: رابط", "نمط بشري اصطناعي: اقتراحات"}[random.nextInt(3)];
            } else {
                int pattern = i % 4;
                s.watchSeconds = pattern == 0 ? 2 + random.nextInt(5) : 3 + random.nextInt(18);
                s.repeatCount = pattern == 1 ? 10 + random.nextInt(20) : 4 + random.nextInt(10);
                s.gapSeconds = pattern == 2 ? 1 + random.nextInt(4) : 3 + random.nextInt(20);
                s.source = pattern == 3 ? "نمط آلي اصطناعي متكرر" : "نمط آلي اصطناعي تجريبي";
            }
            s.predictedBot = s.watchSeconds < 12 || s.repeatCount >= 8 || s.gapSeconds < 5;
            if (s.watchSeconds < 12) s.reason = "مدة اختبار قصيرة جدًا";
            else if (s.repeatCount >= 8) s.reason = "تكرار مرتفع";
            else if (s.gapSeconds < 5) s.reason = "فواصل زمنية غير طبيعية";
            else s.reason = "لا توجد إشارة كافية للاشتباه";
            sessions.add(s);
        }
        renderResults();
    }

    private int parseBounded(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (Exception e) {
            return fallback;
        }
    }

    private void renderResults() {
        results.removeAllViews();
        if (sessions.isEmpty()) {
            summary.setText("لم يتم إنشاء مهام بعد. اضغط زر إنشاء المهام لبدء الاختبار.");
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
                "النتائج: %d مهمة اختبار\nصحيح: %d | إنذارات كاذبة: %d | حالات فائتة: %d\nالدقة التجريبية: %.1f%%\nمصفوفة الالتباس — TP: %d، TN: %d، FP: %d، FN: %d",
                sessions.size(), correct, fp, fn, accuracy, tp, tn, fp, fn));

        int displayCount = Math.min(sessions.size(), 100);
        for (int i = 0; i < displayCount; i++) {
            Session s = sessions.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, dp(4), 0, dp(4));
            results.addView(card, cp);
            boolean correctLabel = s.expectedBot == s.predictedBot;
            TextView head = text("مهمة #" + (i + 1) + " — " + (s.predictedBot ? "مشتبه بها" : "طبيعية حسب الكاشف"), 15, s.predictedBot ? RED : GREEN, true);
            card.addView(head);
            card.addView(text("الحقيقة التجريبية: " + (s.expectedBot ? "BOT" : "HUMAN") + " | تقييم الكاشف: " + (correctLabel ? "صحيح" : "خطأ"), 13, NAVY, false));
            card.addView(text("المصدر: " + s.source + " | مدة المشاهدة: " + s.watchSeconds + " ثانية", 13, NAVY, false));
            card.addView(text("التكرار: " + s.repeatCount + " | الفاصل: " + s.gapSeconds + " ثانية | وقت تجريبي: الدقيقة " + s.elapsedMinutes, 13, NAVY, false));
            card.addView(text("سبب التقييم: " + s.reason, 13, NAVY, false));
        }
        if (sessions.size() > displayCount) {
            results.addView(text("يتم عرض أول " + displayCount + " مهمة فقط؛ ملف CSV يحتوي على جميع الجلسات.", 13, NAVY, true));
        }
    }


    private void exportCsv() {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "أنشئ مهام الاختبار أولًا.", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("dataset,session_id,elapsed_minute,expected_label,predicted_label,watch_seconds,repeat_count,gap_seconds,source,reason\n");
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            csv.append("LOCAL_TEST_ONLY,")
                    .append(i + 1).append(',')
                    .append(s.elapsedMinutes).append(',')
                    .append(s.expectedBot ? "BOT" : "HUMAN").append(',')
                    .append(s.predictedBot ? "SUSPICIOUS" : "NORMAL").append(',')
                    .append(s.watchSeconds).append(',')
                    .append(s.repeatCount).append(',')
                    .append(s.gapSeconds).append(',')
                    .append(csvValue(s.source)).append(',')
                    .append(csvValue(s.reason)).append('\n');
        }
        pendingCsv = csv.toString();
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("text/csv");
        create.putExtra(Intent.EXTRA_TITLE, "local_task_test.csv");
        try {
            startActivityForResult(create, CREATE_CSV_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح اختيار مكان حفظ الملف.", Toast.LENGTH_LONG).show();
        }
    }

    private String csvValue(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_CSV_REQUEST) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingCsv = null;
            Toast.makeText(this, "تم إلغاء حفظ ملف CSV.", Toast.LENGTH_SHORT).show();
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("Output stream unavailable");
            output.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "تم حفظ ملف بيانات الاختبار.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "فشل حفظ ملف CSV.", Toast.LENGTH_LONG).show();
        } finally {
            pendingCsv = null;
        }
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
