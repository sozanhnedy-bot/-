package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskAssistantActivity extends Activity {
    private static final String DISPATCH_URL = "https://api.github.com/repos/sozanhnedy-bot/-/actions/workflows/ai-task.yml/dispatches";
    private static final String RUNS_URL = "https://github.com/sozanhnedy-bot/-/actions/workflows/ai-task.yml";
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int COPPER = Color.rgb(158, 77, 57);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText tokenInput;
    private EditText taskInput;
    private CheckBox consent;
    private Button submit;
    private TextView status;
    private Button runsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("مساحة المهام");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        page.setBackgroundColor(PAPER);
        scroll.addView(page);

        TextView brand = text("Threads Documentary News", 17, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        brand.setPadding(dp(15), dp(11), dp(15), dp(11));
        brand.setBackgroundColor(NAVY);
        page.addView(brand, matchHeight(dp(48)));

        TextView title = text("مساحة العمل داخل التطبيق", 23, NAVY, true);
        title.setPadding(0, dp(18), 0, dp(5));
        page.addView(title);
        TextView intro = text("اكتب طلبك بالعربية لتعديل الموقع أو واجهات تطبيق Android. ChatGPT ينفذ التعديل المحدد في ملفات المشروع المسموح بها؛ وبعد الاختبارات الناجحة يُنشر الموقع، وتُبنى شيفرة التطبيق.", 15, Color.rgb(73, 83, 94), false);
        page.addView(intro);

        TextView warning = text("تنبيه: المستودع عام، وقد يظهر نص المهمة في سجل GitHub Actions. لا ترسل كلمات مرور أو مفاتيح API أو بيانات شخصية. تشغيل المهمة يتطلب رمز GitHub خاصًا بالمستودع.", 14, Color.rgb(88, 52, 39), true);
        warning.setPadding(dp(12), dp(11), dp(12), dp(11));
        GradientDrawable warningBg = new GradientDrawable();
        warningBg.setColor(Color.rgb(246, 227, 215));
        warningBg.setStroke(dp(1), Color.rgb(222, 188, 168));
        warningBg.setCornerRadius(dp(4));
        warning.setBackground(warningBg);
        LinearLayout.LayoutParams warningParams = matchHeight(-2);
        warningParams.topMargin = dp(15);
        warningParams.bottomMargin = dp(15);
        page.addView(warning, warningParams);

        page.addView(label("رمز GitHub المؤقت — Actions: Read and write فقط"));
        tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setHint("github_pat_…");
        tokenInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        tokenInput.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tokenInput.setAutofillHints((String[]) null);
        page.addView(tokenInput, matchHeight(dp(52)));

        page.addView(label("طلب التعديل (حتى 2400 حرفًا)"));
        taskInput = new EditText(this);
        taskInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        taskInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        taskInput.setHint("مثال: أضف في شاشة الأخبار زرًا أكبر لفتح التقرير الكامل، وحافظ على ألوان الهوية.");
        taskInput.setMinLines(5);
        taskInput.setMaxLines(8);
        taskInput.setMaxEms(100);
        taskInput.setSaveEnabled(false);
        page.addView(taskInput, matchHeight(-2));

        consent = new CheckBox(this);
        consent.setText("أوافق على إرسال الطلب إلى GitHub وأفهم أن نصه قد يظهر علنًا، وأن تحديث APK المثبت يحتاج إصدارًا جديدًا وتثبيتًا يدويًا.");
        consent.setTextColor(Color.rgb(73, 83, 94));
        consent.setTextSize(14);
        LinearLayout.LayoutParams consentParams = matchHeight(-2);
        consentParams.topMargin = dp(12);
        page.addView(consent, consentParams);

        submit = new Button(this);
        submit.setText("إرسال المهمة إلى ChatGPT");
        submit.setTextColor(Color.WHITE);
        submit.setAllCaps(false);
        submit.setBackgroundColor(NAVY);
        LinearLayout.LayoutParams submitParams = matchHeight(dp(52));
        submitParams.topMargin = dp(10);
        page.addView(submit, submitParams);
        submit.setOnClickListener(v -> dispatchTask());

        status = text("", 14, COPPER, false);
        status.setPadding(0, dp(10), 0, 0);
        page.addView(status);

        runsButton = new Button(this);
        runsButton.setText("متابعة تنفيذ المهمة في GitHub");
        runsButton.setAllCaps(false);
        runsButton.setVisibility(View.GONE);
        runsButton.setOnClickListener(v -> openRuns());
        page.addView(runsButton, matchHeight(dp(48)));

        TextView note = text("مهام ChatGPT لا تنشر على الشبكات بنفسها. سياسة النشر المطلوبة: منشور يومي على X وفيسبوك بعد ربط الحسابين، أما YouTube فيحتاج تنبيهًا وموافقتك قبل النشر. لم تُربط حسابات النشر بعد. قد تكون لـ OpenAI API تكلفة منفصلة عن اشتراك ChatGPT.", 13, Color.DKGRAY, false);
        note.setPadding(0, dp(20), 0, 0);
        page.addView(note);
        setContentView(scroll);
    }

    private void dispatchTask() {
        String token = tokenInput.getText().toString().trim();
        String task = taskInput.getText().toString().trim();
        if (token.isEmpty() || task.isEmpty() || !consent.isChecked()) {
            status.setText("أدخل الرمز والطلب، ثم وافق على التنبيه.");
            return;
        }
        if (task.length() > 2400) {
            status.setText("اختصر الطلب إلى 2400 حرف.");
            return;
        }
        submit.setEnabled(false);
        runsButton.setVisibility(View.GONE);
        status.setText("جارٍ إرسال المهمة مباشرة إلى GitHub…");
        executor.execute(() -> {
            int code = -1;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(DISPATCH_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
                JSONObject payload = new JSONObject();
                payload.put("ref", "main");
                JSONObject inputs = new JSONObject();
                inputs.put("task", task);
                payload.put("inputs", inputs);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                code = connection.getResponseCode();
            } catch (Exception ignored) {
                code = -1;
            } finally {
                if (connection != null) connection.disconnect();
            }
            final int resultCode = code;
            mainHandler.post(() -> {
                tokenInput.setText("");
                submit.setEnabled(true);
                if (resultCode >= 200 && resultCode < 300) {
                    status.setText("تم إرسال المهمة. افتح GitHub لمتابعة الفحوصات والنشر.");
                    runsButton.setVisibility(View.VISIBLE);
                    taskInput.setText("");
                } else if (resultCode == 401) {
                    status.setText("رفض GitHub الرمز؛ تأكد من صلاحيته.");
                } else if (resultCode == 403) {
                    status.setText("يحتاج الرمز صلاحية Actions: Read and write للمستودع الصحيح.");
                } else if (resultCode == 404) {
                    status.setText("لم يعثر GitHub على سير العمل. قد لا يكون قد نُشر بعد على main.");
                } else if (resultCode == 422) {
                    status.setText("رفض GitHub الطلب؛ تحقق من إعداد سير العمل.");
                } else {
                    status.setText("تعذر الاتصال بـ GitHub. أعد المحاولة بعد التحقق من الإنترنت والرمز.");
                }
            });
        });
        tokenInput.setText("");
    }

    private void openRuns() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RUNS_URL))); }
        catch (Exception ignored) { status.setText("تعذر فتح صفحة GitHub Actions."); }
    }

    private TextView label(String value) {
        TextView view = text(value, 14, NAVY, true);
        LinearLayout.LayoutParams params = matchHeight(-2);
        params.topMargin = dp(12);
        params.bottomMargin = dp(5);
        view.setLayoutParams(params);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
