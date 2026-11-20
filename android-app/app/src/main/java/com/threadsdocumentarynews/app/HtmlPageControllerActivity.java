package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Local, display-only HTML page viewer. It does not load YouTube or generate visits. */
public final class HtmlPageControllerActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private EditText pageNumber;
    private WebView pageView;
    private int currentPage = 1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(PAPER);
        TextView title = new TextView(this);
        title.setText("عارض صفحات HTML المحلية");
        title.setTextColor(NAVY);
        title.setTextSize(22);
        title.setGravity(Gravity.START);
        root.addView(title);
        TextView note = new TextView(this);
        note.setText("اختر رقم صفحة من 001 إلى 500. المحتوى محلي للعرض فقط.");
        note.setTextColor(NAVY);
        note.setTextSize(14);
        root.addView(note);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        pageNumber = new EditText(this);
        pageNumber.setSingleLine(true);
        pageNumber.setInputType(InputType.TYPE_CLASS_NUMBER);
        pageNumber.setHint("001–500");
        pageNumber.setText("1");
        controls.addView(pageNumber, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button open = new Button(this);
        open.setText("فتح الصفحة");
        open.setAllCaps(false);
        controls.addView(open, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(controls);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        Button previous = new Button(this);
        previous.setText("السابق");
        previous.setAllCaps(false);
        Button next = new Button(this);
        next.setText("التالي");
        next.setAllCaps(false);
        navigation.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        navigation.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(navigation);
        pageView = new WebView(this);
        pageView.setBackgroundColor(Color.WHITE);
        WebSettings settings = pageView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        root.addView(pageView, new LinearLayout.LayoutParams(-1, 0, 1));
        open.setOnClickListener(v -> openSelectedPage());
        previous.setOnClickListener(v -> showPage(Math.max(1, currentPage - 1)));
        next.setOnClickListener(v -> showPage(Math.min(500, currentPage + 1)));
        setContentView(root);
        showPage(1);
    }

    private void openSelectedPage() {
        String value = pageNumber.getText().toString().trim();
        int requested;
        try { requested = Integer.parseInt(value); }
        catch (NumberFormatException e) {
            pageNumber.setError("أدخل رقمًا من 1 إلى 500");
            return;
        }
        if (requested < 1 || requested > 500) {
            pageNumber.setError("النطاق المسموح من 1 إلى 500");
            return;
        }
        showPage(requested);
    }

    private void showPage(int number) {
        currentPage = number;
        pageNumber.setText(String.valueOf(number));
        String id = String.format(java.util.Locale.US, "%03d", number);
        String html = "<!doctype html><html lang='ar' dir='rtl'><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#f5f1e9;color:#14283f;padding:24px}"
                + "main{max-width:680px;margin:auto;background:white;border-radius:16px;padding:24px}"
                + "h1{font-size:24px}p{line-height:1.8}</style>"
                + "<main><h1>الصفحة رقم " + id + "</h1>"
                + "<p>هذه صفحة HTML محلية للعرض فقط. لا تحتوي على مشغل فيديو "
                + "ولا تنفذ زيارات أو مشاهدات أو طلبات خارجية.</p></main></html>";
        pageView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        if (pageView != null) {
            pageView.stopLoading();
            pageView.destroy();
            pageView = null;
        }
        super.onDestroy();
    }
}