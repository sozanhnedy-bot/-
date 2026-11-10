package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Standalone social-account setup and publishing-mode screen. */
public final class SocialAccountsActivity extends Activity {
    private static final int BG = Color.rgb(8, 9, 13);
    private static final int CARD = Color.rgb(20, 21, 27);
    private static final int SURFACE = Color.rgb(29, 30, 37);
    private static final int WHITE = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);
    private static final int GOLD = Color.rgb(255, 187, 54);
    private static final int LINE = Color.rgb(48, 49, 58);
    private SharedPreferences preferences;
    private TextView modeStatus;
    private RadioButton manualMode;
    private RadioButton automaticMode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        preferences = getSharedPreferences("social_publishing_settings", MODE_PRIVATE);
        setTitle("إعداد النشر الاجتماعي");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        TextView header = text("حسابات ومنصات التواصل", 21, WHITE, true);
        header.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(10), dp(18), dp(10));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout modeCard = card();
        addText(modeCard, "طريقة النشر", 18, WHITE, true, 0, 5);
        addText(modeCard, "اختَر الآن الطريقة التي تريد استخدامها بعد إكمال ربط الحسابات. الوضع التلقائي المقصود يعمل من خدمة سحابية حتى لو أغلقت التطبيق.", 13, MUTED, false, 0, 8);
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        manualMode = radio("يدوي — أراجع الخبر وأضغط «نشر» بنفسي", "manual".equals(preferences.getString("mode", "manual")));
        automaticMode = radio("تلقائي — نشر الأخبار الجديدة بعد ربط الحسابات", "automatic".equals(preferences.getString("mode", "manual")));
        modes.addView(manualMode);
        modes.addView(automaticMode);
        modeCard.addView(modes, params(-1, -2, 0, 0, 0, 5));
        modeStatus = text("", 12, GOLD, false);
        modeCard.addView(modeStatus, params(-1, -2, 0, 0, 0, 8));
        modes.setOnCheckedChangeListener((group, checkedId) -> saveMode(checkedId == automaticMode.getId()));
        Button manualPublish = button("نشر يدوي الآن");
        manualPublish.setOnClickListener(v -> Toast.makeText(this,
                "اربط المنصة أولًا؛ النشر الفعلي لا يتاح قبل تفويض OAuth وإعداد خدمة النشر.", Toast.LENGTH_LONG).show());
        modeCard.addView(manualPublish, params(-1, dp(48), 0, 0, 0, 0));
        content.addView(modeCard, params(-1, -2, 0, 0, 0, 12));
        saveMode(automaticMode.isChecked());

        LinearLayout info = card();
        addText(info, "قبل الربط", 17, WHITE, true, 0, 5);
        addText(info, "زر «فتح المنصة» يفتح موقعها، وزر «بوابة المطور» يفتح صفحة إنشاء تطبيق OAuth. هذه الصفحات لا تربط حسابك بالتطبيق تلقائيًا وحدها؛ بعد تسجيل تطبيقات المطوّرين وإعداد عنوان الرجوع الآمن، يظهر تفويض رسمي باسم World News. لا تضع كلمات مرور أو رموز وصول في حقول عامة داخل APK.", 13, MUTED, false, 0, 0);
        content.addView(info, params(-1, -2, 0, 0, 0, 12));

        addPlatform(content, "X", "نص أو صورة أو فيديو", "https://x.com/login", "https://developer.x.com/en/portal/dashboard");
        addPlatform(content, "Facebook Pages", "النشر لصفحة تملك صلاحية إدارتها", "https://www.facebook.com/login", "https://developers.facebook.com/apps/");
        addPlatform(content, "Instagram", "يتطلب حسابًا احترافيًا ووسائط منشورة", "https://www.instagram.com/accounts/login/", "https://developers.facebook.com/apps/");
        addPlatform(content, "YouTube", "النشر الآلي عبر API يتطلب ملف فيديو", "https://studio.youtube.com/", "https://console.cloud.google.com/apis/credentials");
        addPlatform(content, "TikTok", "يتطلب فيديو وتأكيدًا صريحًا لكل منشور عبر API", "https://www.tiktok.com/login", "https://developers.tiktok.com/");

        TextView footer = text("حالة الربط الآن: لا توجد حسابات مفوّضة. لم يتم إرسال أي منشور.", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(10), dp(8), dp(2));
        content.addView(footer, params(-1, -2, 0, 0, 0, 0));
        setContentView(root);
    }

    private void saveMode(boolean automatic) {
        if (preferences == null || modeStatus == null) return;
        String value = automatic ? "automatic" : "manual";
        preferences.edit().putString("mode", value).apply();
        modeStatus.setText(automatic
                ? "تم حفظ اختيار التلقائي على هذا الجهاز. لن يعمل بالخلفية حتى إعداد الخادم وربط الحسابات."
                : "تم حفظ اختيار اليدوي على هذا الجهاز. النشر اليدوي ينتظر ربط الحسابات أولًا.");
    }

    private RadioButton radio(String label, boolean checked) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextColor(WHITE);
        button.setTextSize(14);
        button.setButtonTintList(android.content.res.ColorStateList.valueOf(GOLD));
        button.setChecked(checked);
        return button;
    }

    private void addPlatform(LinearLayout parent, String name, String note, String accountUrl, String developerUrl) {
        LinearLayout platform = card();
        addText(platform, name, 17, WHITE, true, 0, 3);
        addText(platform, note, 12, MUTED, false, 0, 7);
        addText(platform, "الحالة: غير مربوط", 12, GOLD, true, 0, 7);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button account = secondaryButton("فتح المنصة");
        Button setup = secondaryButton("بوابة المطور");
        row.addView(account, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        setupParams.setMargins(dp(6), 0, 0, 0);
        row.addView(setup, setupParams);
        account.setOnClickListener(v -> openUrl(accountUrl));
        setup.setOnClickListener(v -> openUrl(developerUrl));
        platform.addView(row);
        parent.addView(platform, params(-1, -2, 0, 0, 0, 10));
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) { Toast.makeText(this, "تعذر فتح الرابط. تحقق من اتصال الإنترنت.", Toast.LENGTH_SHORT).show(); }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(14));
        layout.setBackground(rounded(CARD, LINE, 18));
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void addText(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom) {
        parent.addView(text(value, size, color, bold), params(-1, -2, 0, top, 0, bottom));
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(BG);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(rounded(GOLD, GOLD, 14));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(WHITE);
        button.setTextSize(12);
        button.setBackground(rounded(SURFACE, LINE, 12));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(width, height);
        layout.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
