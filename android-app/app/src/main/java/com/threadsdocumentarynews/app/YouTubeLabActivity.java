package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class YouTubeLabActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int INK = Color.rgb(30, 38, 48);
    private EditText urlInput;
    private WebView videoView;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.setBackgroundColor(PAPER);

        TextView title = new TextView(this);
        title.setText("YouTube Lab");
        title.setTextColor(NAVY);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView hint = new TextView(this);
        hint.setText("أدخل رابط فيديو يوتيوب لفتحه داخل التطبيق");
        hint.setTextColor(INK);
        hint.setTextSize(14);
        root.addView(hint);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14);
        urlInput.setHint("https://www.youtube.com/watch?v=...");
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(52)));

        Button open = new Button(this);
        open.setText("فتح الفيديو");
        open.setAllCaps(false);
        open.setTextColor(Color.WHITE);
        open.setBackgroundColor(NAVY);
        open.setOnClickListener(v -> openVideo());
        root.addView(open, new LinearLayout.LayoutParams(-1, dp(48)));

        status = new TextView(this);
        status.setText("المشاهدة هنا للعرض العادي فقط؛ لا توجد زيادة مصطنعة للمشاهدات.");
        status.setTextColor(INK);
        status.setTextSize(12);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(42)));

        videoView = new WebView(this);
        videoView.setBackgroundColor(Color.BLACK);
        WebSettings settings = videoView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        videoView.setWebChromeClient(new WebChromeClient());
        videoView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                return !("https".equalsIgnoreCase(uri.getScheme()) && host != null
                        && (host.equalsIgnoreCase("youtube.com") || host.endsWith(".youtube.com")
                        || host.equalsIgnoreCase("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")
                        || host.equalsIgnoreCase("google.com") || host.endsWith(".google.com")));
            }
        });
        root.addView(videoView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        String initial = getIntent().getStringExtra("youtube_url");
        if (initial != null && !initial.trim().isEmpty()) {
            urlInput.setText(initial);
            openVideo();
        }
    }

    private void openVideo() {
        String raw = urlInput.getText().toString().trim();
        Uri uri = Uri.parse(raw);
        String host = uri.getHost();
        boolean youtube = "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && (host.equalsIgnoreCase("youtube.com") || host.equalsIgnoreCase("www.youtube.com")
                || host.equalsIgnoreCase("m.youtube.com") || host.equalsIgnoreCase("youtu.be"));
        if (!youtube) {
            status.setText("الرابط غير صالح. استخدم رابط HTTPS من youtube.com أو youtu.be.");
            return;
        }
        status.setText("جاري فتح الفيديو داخل التطبيق…");
        videoView.loadUrl(uri.toString());
    }

    @Override public void onBackPressed() {
        if (videoView != null && videoView.canGoBack()) videoView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (videoView != null) videoView.destroy();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
