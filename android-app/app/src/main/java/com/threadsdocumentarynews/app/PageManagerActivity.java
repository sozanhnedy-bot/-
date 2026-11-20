package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class PageManagerActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final String RAW_BASE = "https://raw.githubusercontent.com/sozanhnedy-bot/-/97a1861d9bf8b1b50e93e26ab717277a1af5ffe1/tools/pages/";
    private EditText pageNumber, fromNumber, toNumber;
    private GridLayout pageGrid;
    private WebView pageView;
    private TextView status;
    private volatile boolean batchPaused;
    private volatile boolean batchStop;
    private volatile boolean batchRunning;
    private volatile int batchStart;
    private volatile int batchEnd;
    private Thread batchThread;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = label("إدارة صفحات HTML — 100 صفحة", 21, NAVY, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView hint = label("اختار رقم صفحة لعرضها داخل التطبيق، أو حدّد نطاقًا لعرض أزرار الصفحات بالترتيب. كل صفحة تُفتح عند الضغط على رقمها.", 13, Color.DKGRAY, false);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout direct = new LinearLayout(this);
        direct.setGravity(Gravity.CENTER_VERTICAL);
        pageNumber = numberInput("رقم الصفحة", "1");
        direct.addView(pageNumber, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button open = button("افتح الصفحة");
        open.setOnClickListener(v -> openNumber(pageNumber));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        openParams.setMargins(dp(6), 0, 0, 0);
        direct.addView(open, openParams);
        root.addView(direct);

        TextView rangeLabel = label("اختيار نطاق الصفحات", 15, NAVY, true);
        rangeLabel.setPadding(0, dp(10), 0, dp(3));
        root.addView(rangeLabel);

        LinearLayout range = new LinearLayout(this);
        fromNumber = numberInput("من", "1");
        toNumber = numberInput("إلى", "10");
        range.addView(fromNumber, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams toParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        toParams.setMargins(dp(6), 0, 0, 0);
        range.addView(toNumber, toParams);
        Button showRange = button("اعرض الأرقام");
        LinearLayout.LayoutParams rangeButtonParams = new LinearLayout.LayoutParams(-1, dp(46));
        rangeButtonParams.topMargin = dp(6);
        root.addView(range);
        root.addView(showRange, rangeButtonParams);

        ScrollView gridScroll = new ScrollView(this);
        pageGrid = new GridLayout(this);
        pageGrid.setColumnCount(5);
        gridScroll.addView(pageGrid);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, dp(150));
        gridParams.topMargin = dp(5);
        root.addView(gridScroll, gridParams);
        showRange.setOnClickListener(v -> renderRange());

        LinearLayout batchControls = new LinearLayout(this);
        batchControls.setOrientation(LinearLayout.HORIZONTAL);
        Button batchStart = button("تشغيل جماعي للفحص");
        Button batchPause = button("إيقاف مؤقت");
        Button batchStopButton = button("إيقاف");
        LinearLayout.LayoutParams batchButtonParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        batchButtonParams.setMargins(dp(2), dp(3), dp(2), dp(3));
        batchControls.addView(batchStart, batchButtonParams);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        pauseParams.setMargins(dp(2), dp(3), dp(2), dp(3));
        batchControls.addView(batchPause, pauseParams);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        stopParams.setMargins(dp(2), dp(3), dp(2), dp(3));
        batchControls.addView(batchStopButton, stopParams);
        root.addView(batchControls);
        batchStart.setOnClickListener(v -> startBatchCheck());
        batchPause.setOnClickListener(v -> {
            if (!batchRunning) return;
            batchPaused = !batchPaused;
            batchPause.setText(batchPaused ? "استكمال" : "إيقاف مؤقت");
            status.setText(batchPaused ? "الفحص متوقف مؤقتًا." : "تم استكمال الفحص.");
        });
        batchStopButton.setOnClickListener(v -> {
            batchStop = true;
            batchPaused = false;
            status.setText("جارٍ إيقاف الفحص الجماعي...");
        });

        status = label("لم يتم فتح صفحة بعد.", 12, NAVY, false);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status);

        pageView = new WebView(this);
        pageView.setBackgroundColor(Color.WHITE);
        WebSettings settings = pageView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        pageView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if ("raw.githubusercontent.com".equalsIgnoreCase(host)) {
                    loadHtml(request.getUrl().toString());
                    return true;
                }
                return true;
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                android.net.Uri uri = android.net.Uri.parse(url);
                if ("raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())) {
                    loadHtml(url);
                    return true;
                }
                return true;
            }
        });
        root.addView(pageView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        renderRange();
        openPage(1);
    }

    private void renderRange() {
        int start = readNumber(fromNumber), end = readNumber(toNumber);
        if (start < 1 || end > 100 || start > end) {
            Toast.makeText(this, "اختار نطاقًا صحيحًا من 1 إلى 100", Toast.LENGTH_SHORT).show();
            return;
        }
        pageGrid.removeAllViews();
        for (int i = start; i <= end; i++) {
            final int page = i;
            Button item = button(String.valueOf(i));
            item.setTextSize(13);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(42);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            pageGrid.addView(item, params);
            item.setOnClickListener(v -> openPage(page));
        }
        status.setText("الصفحات المعروضة للاختيار: " + start + " إلى " + end + ". اضغط رقم الصفحة لفتحها.");
    }

    private void openNumber(EditText input) {
        int page = readNumber(input);
        if (page < 1 || page > 100) {
            input.setError("اكتب رقمًا من 1 إلى 100");
            return;
        }
        openPage(page);
    }

    private void openPage(int page) {
        pageNumber.setText(String.valueOf(page));
        String filename = String.format(java.util.Locale.US, "page-%03d.html", page);
        status.setText("جاري فتح الصفحة رقم " + page + " من 100");
        loadHtml(RAW_BASE + filename);
    }

    private void loadHtml(String address) {
        status.setText("جاري تحميل الصفحة...");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "WorldNews-Android");
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    String html = output.toString("UTF-8");
                    runOnUiThread(() -> {
                        pageView.loadDataWithBaseURL(address, html, "text/html", "UTF-8", null);
                        status.setText("تم فتح الصفحة داخل التطبيق: " + pageNumber.getText().toString());
                    });
                }
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("تعذر تحميل الصفحة. راجع اتصال الإنترنت وحاول مرة أخرى."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void startBatchCheck() {
        if (batchRunning) {
            Toast.makeText(this, "الفحص الجماعي يعمل بالفعل", Toast.LENGTH_SHORT).show();
            return;
        }
        int start = readNumber(fromNumber);
        int end = readNumber(toNumber);
        if (start < 1 || end > 100 || start > end) {
            Toast.makeText(this, "اختار نطاقًا صحيحًا من 1 إلى 100", Toast.LENGTH_SHORT).show();
            return;
        }
        batchStart = start;
        batchEnd = end;
        batchPaused = false;
        batchStop = false;
        batchRunning = true;
        batchThread = new Thread(() -> {
            int passed = 0;
            int failed = 0;
            for (int page = batchStart; page <= batchEnd; page++) {
                while (batchPaused && !batchStop) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); batchStop = true; }
                }
                if (batchStop) break;
                final int current = page;
                runOnUiThread(() -> status.setText("فحص الصفحة " + current + " من " + batchEnd + "..."));
                boolean ok = checkPage(current);
                if (ok) passed++; else failed++;
                final int done = page - batchStart + 1;
                final int good = passed;
                final int bad = failed;
                runOnUiThread(() -> status.setText("اكتمل فحص " + done + " من " + (batchEnd - batchStart + 1) + " — ناجح: " + good + " — فاشل: " + bad));
            }
            batchRunning = false;
            batchPaused = false;
            final boolean stopped = batchStop;
            final int good = passed;
            final int bad = failed;
            runOnUiThread(() -> status.setText((stopped ? "تم إيقاف الفحص. " : "انتهى الفحص الجماعي. ") + "ناجح: " + good + " — فاشل: " + bad));
        }, "html-page-batch-check");
        batchThread.start();
    }

    private boolean checkPage(int page) {
        HttpURLConnection connection = null;
        try {
            String filename = String.format(java.util.Locale.US, "page-%03d.html", page);
            connection = (HttpURLConnection) new URL(RAW_BASE + filename).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "WorldNews-Android-PageCheck");
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private int readNumber(EditText input) {
        try { return Integer.parseInt(input.getText().toString().trim()); }
        catch (Exception ignored) { return -1; }
    }

    private EditText numberInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setHint(hint);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundColor(Color.WHITE);
        return input;
    }

    private Button button(String caption) {
        Button b = new Button(this);
        b.setText(caption);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(NAVY));
        return b;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (pageView != null && pageView.canGoBack()) pageView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (pageView != null) pageView.destroy();
        super.onDestroy();
    }
}
