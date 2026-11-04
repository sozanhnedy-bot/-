package com.threadsdocumentarynews.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PortalActivity extends Activity {
    private static final String SITE_URL = "https://sozanhnedy-bot.github.io/-/";
    private static final String NEWS_URL = SITE_URL + "data/news.json";
    private static final String SITE_HOST = "sozanhnedy-bot.github.io";
    private static final String CHANNEL_ID = "news-alerts";
    private static final int NOTIFICATION_ID = 1707;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 721;
    private static final long POLL_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private Button alertsButton;
    private SharedPreferences preferences;
    private boolean alertsEnabled;
    private boolean activityResumed;

    private final Runnable newsPoller = new Runnable() {
        @Override
        public void run() {
            if (!alertsEnabled || !activityResumed) return;
            fetchNewsAndNotify();
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        preferences = getSharedPreferences("owner-dashboard", MODE_PRIVATE);
        alertsEnabled = preferences.getBoolean("news-alerts-enabled", false);
        createNotificationChannel();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);

        TextView heading = new TextView(this);
        heading.setText("World News — أخبار العالم");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        heading.setPadding(dp(16), dp(12), dp(16), dp(12));
        heading.setBackgroundColor(NAVY);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(54)));

        webView = new WebView(this);
        webView.setBackgroundColor(PAPER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && SITE_HOST.equalsIgnoreCase(uri.getHost())) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                }
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if ("https".equalsIgnoreCase(uri.getScheme()) && SITE_HOST.equalsIgnoreCase(uri.getHost())) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                }
                return true;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setBackgroundColor(PAPER);
        alertsButton = new Button(this);
        alertsButton.setAllCaps(false);
        alertsButton.setTextColor(Color.WHITE);
        alertsButton.setBackgroundColor(NAVY);
        alertsButton.setTextSize(13);
        updateAlertsButton();
        alertsButton.setOnClickListener(v -> toggleAlerts());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        actionParams.setMargins(dp(5), dp(3), dp(5), dp(3));
        actions.addView(alertsButton, actionParams);

        Button assistant = new Button(this);
        assistant.setText("مساحة المهام");
        assistant.setTextColor(Color.WHITE);
        assistant.setAllCaps(false);
        assistant.setTextSize(13);
        assistant.setBackgroundColor(NAVY);
        assistant.setOnClickListener(v -> startActivity(new Intent(this, TaskAssistantActivity.class)));
        LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        assistantParams.setMargins(dp(5), dp(3), dp(5), dp(3));
        actions.addView(assistant, assistantParams);
        root.addView(actions);

        setContentView(root);
        webView.loadUrl(SITE_URL);
    }

    private void toggleAlerts() {
        if (alertsEnabled) {
            alertsEnabled = false;
            preferences.edit().putBoolean("news-alerts-enabled", false).apply();
            mainHandler.removeCallbacks(newsPoller);
            updateAlertsButton();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        enableAlerts();
    }

    private void enableAlerts() {
        alertsEnabled = true;
        preferences.edit().putBoolean("news-alerts-enabled", true).apply();
        updateAlertsButton();
        scheduleNewsPolling();
    }

    private void updateAlertsButton() {
        if (alertsButton != null) alertsButton.setText(alertsEnabled ? "تنبيهات الأخبار: مفعّلة" : "تشغيل تنبيهات الأخبار");
    }

    private void scheduleNewsPolling() {
        mainHandler.removeCallbacks(newsPoller);
        if (alertsEnabled && activityResumed) mainHandler.post(newsPoller);
    }

    private void fetchNewsAndNotify() {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(NEWS_URL + "?v=" + System.currentTimeMillis()).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) return;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] chunk = new byte[4096];
                    int count;
                    while ((count = input.read(chunk)) != -1 && buffer.size() < 600_000) buffer.write(chunk, 0, count);
                }
                JSONObject snapshot = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                JSONArray items = snapshot.optJSONArray("items");
                if (items == null) return;
                boolean initialized = preferences.getBoolean("news-alerts-initialized", false);
                Set<String> seen = new HashSet<>(preferences.getStringSet("news-alerts-seen", Collections.emptySet()));
                LinkedHashSet<String> currentAlertIds = new LinkedHashSet<>();
                String newTitle = null;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "");
                    if (id.isEmpty()) continue;
                    currentAlertIds.add(id);
                    if (!seen.contains(id) && newTitle == null) {
                        String title = item.optString("title", "").trim();
                        if (!title.isEmpty()) newTitle = title;
                    }
                }
                if (!initialized) {
                    seen.addAll(currentAlertIds);
                    preferences.edit().putBoolean("news-alerts-initialized", true).putStringSet("news-alerts-seen", seen).apply();
                    return;
                }
                boolean isNew = currentAlertIds.stream().anyMatch(id -> !seen.contains(id));
                seen.addAll(currentAlertIds);
                while (seen.size() > 250) seen.remove(seen.iterator().next());
                preferences.edit().putStringSet("news-alerts-seen", seen).apply();
                if (isNew && newTitle != null && alertsEnabled && activityResumed) showNewsNotification(newTitle);
            } catch (Exception ignored) {
                // A missed polling cycle is retried on the next interval.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showNewsNotification(String title) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        Intent open = new Intent(this, PortalActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 19, open, pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this).setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.news_chime));
        builder.setSmallIcon(R.drawable.ic_news_bell)
                .setContentTitle("خبر جديد على الموقع")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setContentIntent(pending)
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        Uri sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.news_chime);
        AudioAttributes audio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "تنبيهات الأخبار", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("تنبيه مختصر عند وصول خبر جديد إلى الموقع");
        channel.setSound(sound, audio);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        scheduleNewsPolling();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        mainHandler.removeCallbacks(newsPoller);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableAlerts();
        else updateAlertsButton();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(newsPoller);
        executor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
