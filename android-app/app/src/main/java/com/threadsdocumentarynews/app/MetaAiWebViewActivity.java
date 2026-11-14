package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Opens Meta AI as a user-controlled website and keeps explicitly saved items locally. */
public final class MetaAiWebViewActivity extends Activity {
    private static final String META_AI_URL = "https://www.meta.ai/";
    private static final String PREFS = "meta-ai-local-library";
    private static final String PREF_TEXTS = "saved-texts";
    private static final String PREF_CONSENT = "meta-ai-privacy-consent";
    private static final int REQUEST_WEB_FILE = 8801;
    private static final int REQUEST_IMPORT_IMAGE = 8802;
    private static final long MAX_IMPORT_BYTES = 50L * 1024L * 1024L;
    private static final int BG = Color.rgb(8, 9, 13);
    private static final int CARD = Color.rgb(20, 21, 27);
    private static final int TEXT = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);

    private WebView webView;
    private LinearLayout root;
    private LinearLayout libraryContent;
    private ScrollView libraryScroll;
    private TextView status;
    private ValueCallback<Uri[]> pendingWebFileCallback;
    private boolean showingLibrary;
    private boolean receiverRegistered;
    private SharedPreferences preferences;
    private final Set<Long> pendingDownloadIds = new HashSet<>();

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (!pendingDownloadIds.remove(id)) return;
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) return;
            try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (cursor == null || !cursor.moveToFirst()) {
                    showMessage("انتهى التنزيل لكن تعذر فحص نتيجته.");
                    return;
                }
                int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int downloadStatus = statusColumn < 0 ? -1 : cursor.getInt(statusColumn);
                if (downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    refreshLibraryIfVisible();
                    showMessage("اكتمل تنزيل الصورة؛ راجعها في المحفوظات.");
                } else {
                    int reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    int reason = reasonColumn < 0 ? -1 : cursor.getInt(reasonColumn);
                    showMessage("فشل تنزيل الصورة من Meta AI (رمز " + reason + "). جرّب تنزيلها من الموقع ثم استيرادها.");
                }
            } catch (Exception error) {
                showMessage("تعذر فحص نتيجة تنزيل Meta AI.");
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("Meta AI — World News");
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(5), dp(14), dp(5));
        header.setBackgroundColor(BG);
        TextView title = text("Meta AI داخل التطبيق", 18, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button external = smallButton("المتصفح");
        external.setOnClickListener(v -> openInBrowser());
        header.addView(external, new LinearLayout.LayoutParams(dp(92), dp(42)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        status = text("يتم تحميل موقع Meta AI الرسمي…", 12, MUTED, false);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        status.setPadding(dp(14), 0, dp(14), 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));

        webView = new WebView(this);
        configureWebView();
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        libraryScroll = new ScrollView(this);
        libraryScroll.setFillViewport(true);
        libraryContent = new LinearLayout(this);
        libraryContent.setOrientation(LinearLayout.VERTICAL);
        libraryContent.setPadding(dp(14), dp(10), dp(14), dp(18));
        libraryScroll.addView(libraryContent);
        libraryScroll.setVisibility(View.GONE);
        root.addView(libraryScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(5), dp(5), dp(5), dp(5));
        actions.setBackgroundColor(Color.rgb(5, 6, 9));
        actions.addView(actionButton("المحفوظات", this::toggleLibrary), weightParams());
        actions.addView(actionButton("حفظ نص", this::saveCopiedText), weightParams());
        actions.addView(actionButton("استيراد صورة", this::chooseImageToImport), weightParams());
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(58)));
        setContentView(root);

        registerDownloadReceiver();
        if (preferences.getBoolean(PREF_CONSENT, false)) webView.loadUrl(META_AI_URL);
        else showPrivacyNotice();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        // Isolated to this WebView; Meta sign-in may require cookies across Meta-owned domains.
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                status.setText("جارٍ فتح Meta AI… سجّل الدخول بنفسك إذا طلب الموقع ذلك.");
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("أنت تتصفح Meta AI. استخدم «حفظ نص» أو «استيراد صورة» لحفظ المحتوى يدويًا.");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("تعذر تحميل Meta AI داخل WebView. جرّب زر «المتصفح» أو تحقق من اتصال الإنترنت.");
                }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                      android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) {
                    status.setText("رفض موقع Meta AI الطلب داخل التطبيق (HTTP " + response.getStatusCode()
                            + "). جرّب زر «المتصفح»؛ لا يستطيع التطبيق تجاوز قيود الموقع.");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                                       FileChooserParams fileChooserParams) {
                if (pendingWebFileCallback != null) pendingWebFileCallback.onReceiveValue(null);
                pendingWebFileCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(intent, REQUEST_WEB_FILE);
                    return true;
                } catch (Exception error) {
                    pendingWebFileCallback = null;
                    filePathCallback.onReceiveValue(null);
                    showMessage("تعذر فتح منتقي الصور في هذا الجهاز.");
                    return true;
                }
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                startDownload(url, userAgent, mimeType, contentLength));
    }

    private boolean routeUrl(Uri uri) {
        String scheme = uri == null ? "" : value(uri.getScheme()).toLowerCase(Locale.ROOT);
        String host = uri == null ? "" : value(uri.getHost()).toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            showMessage("لأمانك، يُسمح داخل هذه الشاشة بروابط HTTPS فقط.");
            return true;
        }
        if (isMetaHost(host)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception ignored) { showMessage("تعذر فتح الرابط الخارجي."); }
        return true;
    }

    private boolean isMetaHost(String host) {
        return host.equals("meta.ai") || host.endsWith(".meta.ai")
                || host.equals("meta.com") || host.endsWith(".meta.com")
                || host.equals("facebook.com") || host.endsWith(".facebook.com")
                || host.equals("instagram.com") || host.endsWith(".instagram.com")
                || host.equals("fbcdn.net") || host.endsWith(".fbcdn.net")
                || host.equals("fbsbx.com") || host.endsWith(".fbsbx.com")
                || host.equals("cdninstagram.com") || host.endsWith(".cdninstagram.com");
    }

    private void showPrivacyNotice() {
        new AlertDialog.Builder(this)
                .setTitle("قبل فتح Meta AI")
                .setMessage("سيُفتح موقع Meta AI الرسمي داخل التطبيق. أي نص أو صورة ترسلها هناك تُرسل إلى Meta وتخضع لشروطها وسياسة الخصوصية. لا ترسل كلمات مرور أو معلومات سرية. سجّل الدخول بنفسك؛ التطبيق لا يقرأ محادثات Meta تلقائيًا. الصور والنصوص التي تحفظها يدويًا تبقى في هذا الجهاز.")
                .setNegativeButton("إلغاء", (dialog, which) -> finish())
                .setPositiveButton("متابعة إلى Meta AI", (dialog, which) -> {
                    preferences.edit().putBoolean(PREF_CONSENT, true).apply();
                    webView.loadUrl(META_AI_URL);
                })
                .setCancelable(false)
                .show();
    }

    private void startDownload(String url, String userAgent, String mimeType, long contentLength) {
        Uri uri = Uri.parse(value(url));
        String scheme = value(uri.getScheme()).toLowerCase(Locale.ROOT);
        String host = value(uri.getHost()).toLowerCase(Locale.ROOT);
        String mime = value(mimeType).toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) || !isMetaHost(host)) {
            showMessage("لم يُحفظ الملف تلقائيًا. نزّله من Meta AI ثم استورده من زر «استيراد صورة».");
            return;
        }
        if (contentLength > MAX_IMPORT_BYTES) {
            showMessage("حجم الملف أكبر من الحد المحلي البالغ 50 ميغابايت.");
            return;
        }
        if (!mime.startsWith("image/")) {
            showMessage("الحفظ المباشر متاح للصور فقط. لحفظ النص استخدم «حفظ نص»، وللصور جرّب «استيراد صورة» إذا لم يبدأ التنزيل.");
            return;
        }
        try {
            File base = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (base == null) throw new IllegalStateException("App-specific storage unavailable");
            String extension = extensionForMime(mime);
            String name = "meta_ai_" + System.currentTimeMillis() + "." + extension;
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("World News — Meta AI");
            request.setDescription("حفظ الملف داخل مساحة التطبيق");
            request.setMimeType(mime.isEmpty() ? "application/octet-stream" : mime);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_PICTURES, "WorldNewsMetaAI/" + name);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (userAgent != null && !userAgent.trim().isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");
            long downloadId = manager.enqueue(request);
            pendingDownloadIds.add(downloadId);
            showMessage("بدأ التنزيل. سيظهر الملف في «المحفوظات» بعد اكتماله.");
        } catch (Exception error) {
            showMessage("تعذر بدء تنزيل الملف. نزّله من Meta AI ثم اختر «استيراد صورة».");
        }
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, filter);
        receiverRegistered = true;
    }

    private void toggleLibrary() {
        showingLibrary = !showingLibrary;
        webView.setVisibility(showingLibrary ? View.GONE : View.VISIBLE);
        libraryScroll.setVisibility(showingLibrary ? View.VISIBLE : View.GONE);
        status.setText(showingLibrary ? "المحفوظات المحلية — لا تُرفع إلى خادم من التطبيق." : "أنت تتصفح Meta AI. استخدم «حفظ نص» أو «استيراد صورة» لحفظ المحتوى يدويًا.");
        if (showingLibrary) renderLibrary();
    }

    private void refreshLibraryIfVisible() {
        if (showingLibrary) renderLibrary();
    }

    private void renderLibrary() {
        libraryContent.removeAllViews();
        addText(libraryContent, "المحفوظات المحلية", 19, TEXT, true, 0, 6);
        addText(libraryContent, "الصور والنصوص هنا محفوظة على هذا الجهاز فقط.", 12, MUTED, false, 0, 12);

        ArrayList<File> images = listSavedImages();
        if (images.isEmpty()) addText(libraryContent, "لا توجد صور محفوظة بعد.", 14, MUTED, false, 0, 8);
        for (File image : images) {
            LinearLayout item = card();
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setImageBitmap(loadThumbnail(image));
            item.addView(preview, new LinearLayout.LayoutParams(-1, dp(180)));
            addText(item, image.getName(), 12, MUTED, false, 0, 5);
            Button delete = secondaryButton("حذف الصورة من المحفوظات");
            item.addView(delete, new LinearLayout.LayoutParams(-1, dp(42)));
            delete.setOnClickListener(v -> {
                if (image.delete()) renderLibrary();
                else showMessage("تعذر حذف الملف.");
            });
            libraryContent.addView(item, params(-1, -2, 0, 0, 0, 9));
        }

        addText(libraryContent, "النصوص المحفوظة", 17, TEXT, true, 0, 7);
        JSONArray savedTexts = readSavedTexts();
        if (savedTexts.length() == 0) addText(libraryContent, "لا توجد نصوص محفوظة بعد.", 14, MUTED, false, 0, 8);
        for (int i = savedTexts.length() - 1; i >= 0; i--) {
            JSONObject item = savedTexts.optJSONObject(i);
            if (item == null) continue;
            final String id = item.optString("id", "");
            LinearLayout textCard = card();
            TextView body = text(item.optString("text", ""), 14, TEXT, false);
            body.setTextIsSelectable(true);
            textCard.addView(body, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout row = new LinearLayout(this);
            Button copy = secondaryButton("نسخ");
            Button delete = secondaryButton("حذف");
            row.addView(copy, new LinearLayout.LayoutParams(0, dp(42), 1));
            row.addView(delete, new LinearLayout.LayoutParams(0, dp(42), 1));
            textCard.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
            copy.setOnClickListener(v -> copyText(body.getText().toString()));
            delete.setOnClickListener(v -> {
                removeSavedText(id);
                renderLibrary();
            });
            libraryContent.addView(textCard, params(-1, -2, 0, 0, 0, 8));
        }
    }

    private void saveCopiedText() {
        String initial = "";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence copied = clip.getItemAt(0).coerceToText(this);
                if (copied != null) initial = copied.toString();
            }
        }
        EditText input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.rgb(27, 28, 35));
        input.setHint("انسخ إجابة Meta AI أو الصقها هنا لمراجعتها وحفظها.");
        input.setText(initial);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(18), dp(5), dp(18), dp(5));
        holder.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("حفظ نص داخل التطبيق")
                .setView(holder)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ النص", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        showMessage("لا يوجد نص لحفظه.");
                        return;
                    }
                    addSavedText(value);
                    showMessage("تم حفظ النص محليًا.");
                    if (showingLibrary) renderLibrary();
                })
                .show();
    }

    private void chooseImageToImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try { startActivityForResult(intent, REQUEST_IMPORT_IMAGE); }
        catch (Exception error) { showMessage("تعذر فتح منتقي الصور."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WEB_FILE) {
            Uri selected = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (pendingWebFileCallback != null) {
                pendingWebFileCallback.onReceiveValue(selected == null ? null : new Uri[]{selected});
                pendingWebFileCallback = null;
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importImage(data.getData());
        }
    }

    private void importImage(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("No input stream");
            File directory = imageLibraryDirectory();
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create library");
            String extension = extensionForMime(getContentResolver().getType(uri));
            File output = new File(directory, "meta_ai_import_" + System.currentTimeMillis() + "." + extension);
            try (FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_IMPORT_BYTES) throw new IllegalStateException("Image exceeds 50 MB");
                    out.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(output.getAbsolutePath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                output.delete();
                throw new IllegalStateException("Unsupported or damaged image");
            }
            showMessage("تم حفظ الصورة داخل مكتبة التطبيق.");
            if (showingLibrary) renderLibrary();
        } catch (Exception error) {
            showMessage("تعذر استيراد الصورة أو أن صيغتها لا يدعمها هذا الجهاز.");
        }
    }

    private File imageLibraryDirectory() {
        return new File(getFilesDir(), "meta_ai_library");
    }

    private ArrayList<File> listSavedImages() {
        ArrayList<File> result = new ArrayList<>();
        File internal = imageLibraryDirectory();
        if (internal.isDirectory()) addImagesFrom(internal, result);
        File external = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (external != null) addImagesFrom(new File(external, "WorldNewsMetaAI"), result);
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        if (result.size() > 40) return new ArrayList<>(result.subList(0, 40));
        return result;
    }

    private void addImagesFrom(File directory, ArrayList<File> result) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || file.length() <= 0) continue;
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp")) result.add(file);
        }
    }

    private Bitmap loadThumbnail(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        options.inSampleSize = Math.max(1, Math.max(options.outWidth / 600, options.outHeight / 400));
        options.inJustDecodeBounds = false;
        try { return BitmapFactory.decodeFile(file.getAbsolutePath(), options); }
        catch (OutOfMemoryError error) { return null; }
    }

    private JSONArray readSavedTexts() {
        try { return new JSONArray(preferences.getString(PREF_TEXTS, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private void addSavedText(String value) {
        JSONArray texts = readSavedTexts();
        JSONObject item = new JSONObject();
        try {
            item.put("id", String.valueOf(System.currentTimeMillis()));
            item.put("text", value.length() > 20000 ? value.substring(0, 20000) : value);
            item.put("savedAt", System.currentTimeMillis());
            texts.put(item);
            while (texts.length() > 100) texts.remove(0);
            preferences.edit().putString(PREF_TEXTS, texts.toString()).apply();
        } catch (Exception ignored) { showMessage("تعذر حفظ النص."); }
    }

    private void removeSavedText(String id) {
        JSONArray old = readSavedTexts();
        JSONArray kept = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id", ""))) kept.put(item);
        }
        preferences.edit().putString(PREF_TEXTS, kept.toString()).apply();
    }

    private void copyText(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("World News", value));
        showMessage("تم نسخ النص.");
    }

    private void openInBrowser() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(META_AI_URL))); }
        catch (Exception error) { showMessage("تعذر فتح المتصفح."); }
    }

    private String extensionForMime(String mimeType) {
        String mime = value(mimeType).toLowerCase(Locale.ROOT);
        if (mime.contains("png")) return "png";
        if (mime.contains("webp")) return "webp";
        if (mime.contains("gif")) return "gif";
        if (mime.contains("bmp")) return "bmp";
        if (mime.contains("text")) return "txt";
        return "jpg";
    }

    private String value(String text) { return text == null ? "" : text.trim(); }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private Button actionButton(String caption, Runnable action) {
        Button button = smallButton(caption);
        button.setTextSize(11);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private Button smallButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setBackgroundColor(Color.rgb(20, 40, 63));
        return button;
    }

    private Button secondaryButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setBackgroundColor(Color.rgb(36, 37, 45));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private void addText(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(value, size, color, bold);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(0, dp(top), 0, dp(bottom));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(CARD);
        return card;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (showingLibrary) {
            showingLibrary = false;
            libraryScroll.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            return;
        }
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (pendingWebFileCallback != null) {
            pendingWebFileCallback.onReceiveValue(null);
            pendingWebFileCallback = null;
        }
        if (receiverRegistered) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
