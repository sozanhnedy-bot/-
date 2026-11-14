package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-app diagnostics for recent image/video failures and debug-only App Check setup. */
public final class AppDiagnosticsActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int MUTED = Color.rgb(95, 101, 110);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView categoryView;
    private TextView timeView;
    private TextView reportView;
    private TextView tokenStatus;
    private TextView tokenView;
    private Button copyTokenButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("تشخيص الأخطاء");
        buildUi();
        refreshReport();
        if (BuildConfig.DEBUG) loadDebugToken();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.setBackgroundColor(PAPER);
        scroll.addView(root);

        root.addView(text("تشخيص الصور والفيديو", 22, NAVY, true));
        root.addView(text("يُحفظ هنا آخر خطأ من استيراد/تصدير الصور أو إنشاء MP4. راجع التقرير وانسخه لإرساله للدعم.", 14, MUTED, false), params(-1, -2, 0, 0, 8, 12));
        categoryView = text("نوع العملية: —", 14, NAVY, true);
        timeView = text("وقت الخطأ: —", 13, MUTED, false);
        root.addView(categoryView);
        root.addView(timeView, params(-1, -2, 0, 0, 2, 8));

        reportView = text("", 12, Color.rgb(32, 35, 41), false);
        reportView.setTextIsSelectable(true);
        reportView.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        reportView.setGravity(Gravity.TOP | Gravity.RIGHT);
        reportView.setPadding(dp(12), dp(12), dp(12), dp(12));
        reportView.setBackgroundColor(Color.WHITE);
        root.addView(reportView, params(-1, dp(280), 0, 0, 0, 8));

        LinearLayout reportActions = new LinearLayout(this);
        reportActions.setOrientation(LinearLayout.HORIZONTAL);
        Button copyReport = button("نسخ التقرير");
        Button clearReport = button("مسح السجل");
        reportActions.addView(copyReport, new LinearLayout.LayoutParams(0, dp(48), 1f));
        reportActions.addView(clearReport, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(reportActions, params(-1, dp(48), 0, 0, 0, 18));
        copyReport.setOnClickListener(v -> copyText("تقرير تشخيص World News", DiagnosticStore.getReport(this), "تم نسخ تقرير التشخيص."));
        clearReport.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("مسح سجل التشخيص؟")
                .setMessage("سيُحذف آخر تقرير خطأ محفوظ على هذا الجهاز فقط.")
                .setPositiveButton("مسح", (dialog, which) -> { DiagnosticStore.clear(this); refreshReport(); })
                .setNegativeButton("إلغاء", null).show());

        root.addView(text("Firebase App Check Debug Token", 18, NAVY, true), params(-1, -2, 0, 0, 5, 3));
        if (BuildConfig.DEBUG) {
            root.addView(text("رمز خاص بنسخة Debug لهذا الهاتف. يظهر تلقائيًا عند فتح هذه الشاشة، ويمكن نسخه إلى Firebase Console → App Check → Manage debug tokens. لا تشارك الرمز ولا تضعه في Release.", 13, MUTED, false), params(-1, -2, 0, 0, 0, 7));
            tokenStatus = text("جارٍ قراءة الرمز…", 13, MUTED, false);
            root.addView(tokenStatus, params(-1, -2, 0, 0, 0, 5));
            tokenView = text("", 13, Color.rgb(30, 33, 40), false);
            tokenView.setTextIsSelectable(true);
            tokenView.setTextDirection(View.TEXT_DIRECTION_LTR);
            tokenView.setGravity(Gravity.LEFT);
            tokenView.setPadding(dp(10), dp(10), dp(10), dp(10));
            tokenView.setBackgroundColor(Color.WHITE);
            root.addView(tokenView, params(-1, -2, 0, 0, 0, 6));
            Button retryTokenButton = button("تحديث الرمز / إعادة المحاولة");
            root.addView(retryTokenButton, params(-1, dp(46), 0, 0, 0, 4));
            retryTokenButton.setOnClickListener(v -> loadDebugToken());
            copyTokenButton = button("نسخ Debug Token");
            copyTokenButton.setEnabled(false);
            root.addView(copyTokenButton);
            copyTokenButton.setOnClickListener(v -> {
                String token = tokenView.getText().toString().trim();
                if (!token.isEmpty()) copyText("Firebase App Check Debug Token", token, "تم نسخ Debug Token.");
                else Toast.makeText(this, "لا يوجد رمز لنسخه بعد.", Toast.LENGTH_LONG).show();
            });
        } else {
            root.addView(text("هذه المعلومة مخفية في نسخة الإنتاج. افتح نسخة Debug لعرض رمز هذا الهاتف.", 14, MUTED, false));
        }
        setContentView(scroll);
    }

    private void loadDebugToken() {
        if (!BuildConfig.DEBUG || tokenStatus == null) return;
        String configured = BuildConfig.FIREBASE_DEBUG_TOKEN == null ? "" : BuildConfig.FIREBASE_DEBUG_TOKEN.trim();
        if (!configured.isEmpty()) {
            tokenView.setText(configured);
            tokenStatus.setText("تم العثور على رمز Debug المُعدّ لهذا البناء.");
            copyTokenButton.setEnabled(true);
            return;
        }
        tokenStatus.setText("جارٍ إنشاء/قراءة الرمز المحلي لهذا الهاتف…");
        executor.execute(() -> {
            try {
                String token = FirebaseAiSetup.debugTokenBlocking(getApplicationContext());
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tokenView.setText(token);
                    tokenStatus.setText("الرمز جاهز للنسخ إلى قائمة Debug tokens في Firebase Console.");
                    copyTokenButton.setEnabled(!token.trim().isEmpty());
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tokenStatus.setText("تعذر تجهيز الرمز: " + safeMessage(error));
                    copyTokenButton.setEnabled(false);
                });
            }
        });
    }

    private void refreshReport() {
        if (reportView == null) return;
        categoryView.setText("نوع العملية: " + DiagnosticStore.getCategory(this));
        String time = DiagnosticStore.getTime(this);
        timeView.setText(time.isEmpty() ? "وقت الخطأ: لا يوجد" : "وقت الخطأ: " + time);
        reportView.setText(DiagnosticStore.getReport(this));
    }

    private void copyText(String label, String value, String successMessage) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "تعذر الوصول إلى الحافظة.", Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(NAVY);
        b.setTextSize(14);
        return b;
    }

    private LinearLayout.LayoutParams params(int width, int height, float weight, int left, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height, weight);
        p.setMargins(dp(left), dp(top), dp(left), dp(bottom));
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String safeMessage(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
