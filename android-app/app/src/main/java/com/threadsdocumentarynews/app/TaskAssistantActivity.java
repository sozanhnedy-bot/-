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
    private static final String SOCIAL_URL = "https://github.com/sozanhnedy-bot/-/actions/workflows/publish-social.yml";
    private static final int BACKGROUND = Color.rgb(8, 9, 13);
    private static final int SURFACE = Color.rgb(20, 21, 27);
    private static final int SURFACE_ALT = Color.rgb(27, 28, 35);
    private static final int GOLD = Color.rgb(255, 187, 54);
    private static final int GOLD_DARK = Color.rgb(203, 133, 25);
    private static final int TEXT = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);
    private static final int LINE = Color.rgb(54, 55, 64);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText tokenInput;
    private EditText taskInput;
    private CheckBox consent;
    private Button submit;
    private TextView status;
    private Button runsButton;
    private LinearLayout pageContent;
    private ScrollView scroll;
    private int selectedTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setTitle("مساعد ثريدز وثائقي");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(9), dp(20), dp(9));
        header.setBackgroundColor(BACKGROUND);
        TextView brand = text("ثريدز وثائقي", 17, TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView spark = text("✦", 24, GOLD, true);
        spark.setGravity(Gravity.CENTER);
        GradientDrawable sparkBg = rounded(SURFACE_ALT, LINE, 20);
        spark.setBackground(sparkBg);
        header.addView(spark, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(dp(18), dp(14), dp(18), dp(24));
        scroll.addView(pageContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(buildBottomNav());
        setContentView(root);
        showTab(0);
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(5, 6, 9));
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.addView(navItem("✧\nالمساعد", 0), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("▱\nالمشاريع", 1), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("◎\nالنشر", 2), new LinearLayout.LayoutParams(0, dp(58), 1));
        return nav;
    }

    private TextView navItem(String caption, int tab) {
        TextView item = text(caption, 12, selectedTab == tab ? GOLD : MUTED, selectedTab == tab);
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(v -> showTab(tab));
        return item;
    }

    private void showTab(int tab) {
        selectedTab = tab;
        pageContent.removeAllViews();
        if (tab == 0) buildAssistantPage();
        else if (tab == 1) buildProjectsPage();
        else buildSocialPage();
        scroll.scrollTo(0, 0);
    }

    private void buildAssistantPage() {
        addHero();
        LinearLayout card = card();
        addText(card, "مساعد المهام", 21, TEXT, true, 0, 4);
        addText(card, "اكتب طلب تعديل واضحًا للموقع أو تطبيق Android. سيراجعه المساعد، ويشغّل الفحوصات قبل نشر تغييرات الموقع.", 14, MUTED, false, 0, 14);

        addLabel(card, "طلب التعديل (حتى 2400 حرفًا)");
        taskInput = new EditText(this);
        taskInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        taskInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        taskInput.setHintTextColor(Color.rgb(128, 129, 137));
        taskInput.setTextColor(TEXT);
        taskInput.setHint("مثال: حسّن شاشة مساعد المهام مع الحفاظ على وظائفها الحالية.");
        taskInput.setTextSize(15);
        taskInput.setMinLines(4);
        taskInput.setMaxLines(7);
        taskInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        taskInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        taskInput.setSaveEnabled(false);
        card.addView(taskInput, params(-1, -2, 0, 0, 0, 12));

        addLabel(card, "رمز GitHub المؤقت — صلاحية Actions فقط");
        tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setHint("github_pat_…");
        tokenInput.setHintTextColor(Color.rgb(128, 129, 137));
        tokenInput.setTextColor(TEXT);
        tokenInput.setTextSize(14);
        tokenInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        tokenInput.setPadding(dp(14), 0, dp(14), 0);
        tokenInput.setBackground(rounded(SURFACE_ALT, LINE, 14));
        tokenInput.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tokenInput.setAutofillHints((String[]) null);
        card.addView(tokenInput, params(-1, dp(50), 0, 0, 0, 4));

        consent = new CheckBox(this);
        consent.setText("أوافق على إرسال الطلب إلى GitHub. قد يظهر نص المهمة في سجل Actions العام.");
        consent.setTextColor(MUTED);
        consent.setButtonTintList(android.content.res.ColorStateList.valueOf(GOLD));
        consent.setTextSize(13);
        card.addView(consent, params(-1, -2, 0, dp(7), 0, 4));

        submit = new Button(this);
        submit.setText("إرسال المهمة إلى المساعد");
        submit.setTextColor(Color.rgb(25, 20, 10));
        submit.setTextSize(16);
        submit.setTypeface(null, Typeface.BOLD);
        submit.setAllCaps(false);
        submit.setBackground(rounded(GOLD, GOLD_DARK, 18));
        card.addView(submit, params(-1, dp(56), 0, dp(10), 0, 0));
        submit.setOnClickListener(v -> dispatchTask());

        status = text("", 13, GOLD, false);
        status.setPadding(0, dp(10), 0, 0);
        card.addView(status);
        runsButton = new Button(this);
        runsButton.setText("متابعة التنفيذ في GitHub");
        runsButton.setTextColor(TEXT);
        runsButton.setAllCaps(false);
        runsButton.setVisibility(View.GONE);
        runsButton.setBackground(rounded(SURFACE_ALT, LINE, 14));
        runsButton.setOnClickListener(v -> openUrl(RUNS_URL, "تعذر فتح GitHub Actions."));
        card.addView(runsButton, params(-1, dp(48), 0, dp(7), 0, 0));
        pageContent.addView(card);

        addText(pageContent, "حماية حسابك", 16, TEXT, true, dp(20), 5);
        addText(pageContent, "لا تضع كلمة مرور أو مفتاح API هنا. يُمسح رمز GitHub بعد الإرسال، ولا يُحفظ داخل التطبيق.", 13, MUTED, false, 0, 0);
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(20), dp(22), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(41, 31, 19), Color.rgb(24, 20, 34), Color.rgb(12, 13, 18)});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), LINE);
        hero.setBackground(bg);
        TextView chips = text("مساعد المالك     ·     مهام آمنة", 12, GOLD, true);
        chips.setPadding(0, 0, 0, dp(14));
        hero.addView(chips);
        addText(hero, "خلّي أفكارك\nتتحول لتطوير حقيقي", 25, TEXT, true, 0, 8);
        addText(hero, "أرسل طلبك، وتابع الفحوصات والنشر من مكان واحد.", 14, MUTED, false, 0, 0);
        LinearLayout.LayoutParams hp = params(-1, -2, 0, 0, 0, 16);
        pageContent.addView(hero, hp);
    }

    private void buildProjectsPage() {
        addText(pageContent, "المشاريع والمهام", 22, TEXT, true, 0, 6);
        addText(pageContent, "تابع آخر طلباتك عبر GitHub Actions. التطبيق لا يحتفظ بنصوص المهام السابقة محليًا.", 14, MUTED, false, 0, 16);
        LinearLayout card = card();
        addText(card, "Threads Documentary News", 18, TEXT, true, 0, 6);
        addText(card, "الموقع الإخباري وتطبيق Android", 13, MUTED, false, 0, 12);
        Button open = secondaryButton("فتح سجل المهام والفحوصات");
        card.addView(open, params(-1, dp(50), 0, dp(8), 0, 0));
        open.setOnClickListener(v -> openUrl(RUNS_URL, "تعذر فتح GitHub Actions."));
        pageContent.addView(card);
        addText(pageContent, "مهم", 16, GOLD, true, dp(18), 5);
        addText(pageContent, "إرسال الطلب لا يعني أن APK المثبت سيتحدث تلقائيًا. عند تعديل التطبيق، نزّل APK الجديد وثبّته على هاتفك.", 13, MUTED, false, 0, 0);
    }

    private void buildSocialPage() {
        addText(pageContent, "النشر الاجتماعي", 22, TEXT, true, 0, 6);
        addText(pageContent, "X وفيسبوك", 14, GOLD, true, 0, 14);
        LinearLayout card = card();
        TextView badge = text("●  النشر التلقائي متوقف افتراضيًا", 14, GOLD, true);
        badge.setPadding(0, 0, 0, dp(12));
        card.addView(badge);
        addText(card, "يوجد في المشروع مسار منفصل للنشر اليومي لتقرير تحريري على X وصفحة Facebook. لا ينشر تلقائيًا إلا بعد إعداد صلاحيات المنصتين وتفعيل موافقة API المدفوعة في GitHub.", 14, MUTED, false, 0, 10);
        addText(card, "عنوان البريد أو كلمة مرور الحساب لا يكفيان. يلزم ربط مطوّر رسمي ورموز وصول للمنصات؛ لا تدخلها في التطبيق ولا ترسلها في المهام.", 14, MUTED, false, 0, 10);
        Button open = secondaryButton("عرض مسار النشر وإعداده في GitHub");
        card.addView(open, params(-1, dp(52), 0, dp(9), 0, 4));
        open.setOnClickListener(v -> openUrl(SOCIAL_URL, "تعذر فتح صفحة إعداد النشر."));
        pageContent.addView(card);

        LinearLayout safety = card();
        addText(safety, "موافقة قبل النشر", 16, TEXT, true, 0, 5);
        addText(safety, "لم يتم إرسال أي منشور. قبل تفعيل الربط، راجع نص المنشور والتقرير والمنصات المستهدفة وأي رسوم تفرضها واجهات X أو Meta.", 13, MUTED, false, 0, 0);
        pageContent.addView(safety, params(-1, -2, 0, dp(14), 0, 0));
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
        status.setText("جارٍ إرسال المهمة إلى GitHub…");
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

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackground(rounded(SURFACE, LINE, 22));
        return layout;
    }

    private Button secondaryButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(SURFACE_ALT, LINE, 16));
        return button;
    }

    private void addLabel(LinearLayout parent, String caption) {
        addText(parent, caption, 13, TEXT, true, dp(11), 6);
    }

    private void addText(LinearLayout parent, String caption, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(caption, size, color, bold);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        parent.addView(view, params(-1, -2, 0, top, 0, bottom));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
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

    private void openUrl(String url, String error) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) {
            if (status != null) status.setText(error);
        }
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
