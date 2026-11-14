package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** Local-only square social-post artwork composer; it never publishes content. */
public final class SocialPostDesignerActivity extends Activity {
    private static final int OPEN_IMAGE = 5101;
    private static final int CREATE_IMAGE = 5102;
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int[] BACKGROUNDS = {NAVY, Color.rgb(117, 35, 41), Color.rgb(35, 92, 99), Color.rgb(31, 36, 44)};
    private static final String[] STYLE_NAMES = {"كحلي تحريري", "خمري عاجل", "تركوازي عالمي", "داكن بسيط"};
    private Spinner platformSpinner, styleSpinner, fontSpinner;
    private EditText titleInput, bodyInput, hashtagsInput;
    private ImageView preview;
    private Bitmap sourceBitmap;
    private SharedPreferences draftPrefs;
    private int styleIndex;
    private boolean exportAsPng;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        draftPrefs = getSharedPreferences("social-post-local-draft", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(20));
        root.setBackgroundColor(PAPER);
        scroll.addView(root);

        TextView heading = text("مصمم منشورات World News", 22, NAVY, true);
        root.addView(heading);
        TextView notice = text("تنبيه: لم يتم ربط واجهة API للنشر الاجتماعي. هذه أداة تصميم وتصدير محلية فقط؛ لا إرسال ولا جدولة ولا OAuth.", 14, Color.rgb(100, 45, 35), false);
        notice.setPadding(dp(12), dp(10), dp(12), dp(10)); notice.setBackgroundColor(Color.rgb(255, 232, 220));
        root.addView(notice, params(-1, -2, 0, 0, 0, 12));

        platformSpinner = spinner(new String[]{"Facebook", "X"});
        platformSpinner.setSelection(draftPrefs.getString("platform", "Facebook").equals("X") ? 1 : 0);
        root.addView(label("المنصة")); root.addView(platformSpinner);
        root.addView(label("القالب اللوني"));
        styleSpinner = spinner(STYLE_NAMES); root.addView(styleSpinner);
        styleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { styleIndex = position; refreshPreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(label("الخط"));
        fontSpinner = spinner(new String[]{"Sans Serif — واضح", "Serif — تحريري", "Monospace — تقني"});
        root.addView(fontSpinner);
        fontSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refreshPreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        preview = new ImageView(this);
        preview.setBackgroundColor(Color.rgb(220, 218, 211));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setContentDescription("معاينة مربعة للمنشور");
        LinearLayout.LayoutParams pv = params(-1, -1, 0, 10, 0, 10); pv.height = dp(320);
        root.addView(preview, pv);
        Button choose = button("اختيار صورة من الجهاز"); choose.setOnClickListener(v -> openImage()); root.addView(choose);

        titleInput = edit("العنوان", draftPrefs.getString("title", ""), 1);
        bodyInput = edit("نص المنشور", draftPrefs.getString("body", ""), 5);
        hashtagsInput = edit("الهاشتاغات (افصل بينها بمسافة)", draftPrefs.getString("hashtags", ""), 2);
        root.addView(label("العنوان")); root.addView(titleInput);
        root.addView(label("النص")); root.addView(bodyInput);
        root.addView(label("الهاشتاغات")); root.addView(hashtagsInput);
        titleInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        bodyInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        hashtagsInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        android.text.TextWatcher previewWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshPreview(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        };
        titleInput.addTextChangedListener(previewWatcher);
        bodyInput.addTextChangedListener(previewWatcher);
        hashtagsInput.addTextChangedListener(previewWatcher);
        platformSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refreshPreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        Button saveDraft = button("حفظ المسودة محليًا"); saveDraft.setOnClickListener(v -> { saveDraft(); Toast.makeText(this, "حُفظت النصوص والمنصة على هذا الجهاز.", Toast.LENGTH_SHORT).show(); }); root.addView(saveDraft);
        Button export = button("تصدير الصورة إلى ملف…"); export.setOnClickListener(v -> beginExport()); root.addView(export);
        setContentView(scroll);
        refreshPreview();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setPadding(0, dp(6), 0, dp(6)); view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); return view;
    }
    private TextView label(String value) { return text(value, 15, NAVY, true); }
    private LinearLayout.LayoutParams params(int width, int height, float weight, int left, int top, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height, weight); p.setMargins(dp(left), dp(top), dp(left), dp(bottom)); return p; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return s; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundColor(NAVY); b.setTextSize(14); LinearLayout.LayoutParams p = params(-1, -2, 0, 0, 5, 5); b.setLayoutParams(p); return b; }
    private EditText edit(String hint, String value, int lines) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextSize(16); e.setGravity(Gravity.RIGHT | Gravity.TOP); e.setMinLines(lines); e.setPadding(dp(12), dp(8), dp(12), dp(8)); e.setBackgroundColor(Color.WHITE); e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); return e; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void openImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, OPEN_IMAGE); } catch (Exception e) { showError("تعذر فتح مستعرض الملفات."); }
    }
    @Override @SuppressWarnings("deprecation") protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == OPEN_IMAGE) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { }
            new Thread(() -> {
                try {
                    Bitmap decoded = ImageAssetNormalizer.decode(getContentResolver(), uri);
                    runOnUiThread(() -> { if (sourceBitmap != null) sourceBitmap.recycle(); sourceBitmap = decoded; refreshPreview(); });
                } catch (IOException | RuntimeException | OutOfMemoryError e) {
                    DiagnosticStore.record(this, "استيراد صورة للمنشور", e);
                    runOnUiThread(() -> {
                        if (!isFinishing()) startActivity(new Intent(this, AppDiagnosticsActivity.class));
                    });
                }
            }).start();
        } else if (requestCode == CREATE_IMAGE) writeExport(data.getData());
    }
    private void refreshPreview() { if (preview == null) return; Bitmap composed = renderArtwork(640); preview.setImageBitmap(composed); }
    private Bitmap renderArtwork(int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out); canvas.drawColor(BACKGROUNDS[Math.max(0, Math.min(styleIndex, BACKGROUNDS.length - 1))]);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (sourceBitmap != null) {
            int side = Math.min(sourceBitmap.getWidth(), sourceBitmap.getHeight());
            int left = (sourceBitmap.getWidth() - side) / 2, top = (sourceBitmap.getHeight() - side) / 2;
            canvas.drawBitmap(sourceBitmap, new Rect(left, top, left + side, top + side), new Rect(0, 0, size, size), p);
            p.setColor(Color.BLACK); p.setAlpha(125); canvas.drawRect(0, size * .56f, size, size, p);
        }
        p.setColor(Color.WHITE); p.setTypeface(selectedTypeface(true)); p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(size * .065f);
        canvas.drawText("WORLD NEWS  •  " + (platformSpinner != null && platformSpinner.getSelectedItemPosition() == 1 ? "X" : "FACEBOOK"), size - size * .055f, size * .64f, p);
        String title = titleInput == null ? "" : titleInput.getText().toString().trim();
        String body = bodyInput == null ? "" : bodyInput.getText().toString().trim();
        String tags = hashtagsInput == null ? "" : hashtagsInput.getText().toString().trim();
        float y = size * .74f; p.setTextSize(size * .075f); if (!title.isEmpty()) { canvas.drawText(ellipsize(title, p, size * .88f), size - size * .055f, y, p); y += size * .09f; }
        p.setTypeface(selectedTypeface(false)); p.setTextSize(size * .046f);
        if (!body.isEmpty()) { String line = body.replace('\n', ' '); canvas.drawText(ellipsize(line, p, size * .88f), size - size * .055f, y, p); y += size * .065f; }
        if (!tags.isEmpty()) { p.setColor(Color.rgb(255, 220, 125)); canvas.drawText(ellipsize(tags, p, size * .88f), size - size * .055f, y, p); }
        return out;
    }
    private String ellipsize(String value, Paint paint, float width) { return TextUtils.ellipsize(value, new android.text.TextPaint(paint), width, TextUtils.TruncateAt.END).toString(); }
    private Typeface selectedTypeface(boolean bold) {
        int selected = fontSpinner == null ? 0 : fontSpinner.getSelectedItemPosition();
        String family = selected == 1 ? "serif" : selected == 2 ? "monospace" : "sans-serif";
        return Typeface.create(family, bold ? Typeface.BOLD : Typeface.NORMAL);
    }
    private void beginExport() {
        if (sourceBitmap == null) { showError("اختر صورة أولًا قبل التصدير."); return; }
        saveDraft();
        String[] formats = {"JPEG", "PNG"};
        new android.app.AlertDialog.Builder(this).setTitle("صيغة الصورة").setItems(formats, (dialog, which) -> {
            exportAsPng = which == 1;
            String mime = which == 0 ? "image/jpeg" : "image/png";
            String ext = which == 0 ? "jpg" : "png";
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT); create.addCategory(Intent.CATEGORY_OPENABLE); create.setType(mime);
            create.putExtra(Intent.EXTRA_TITLE, "world-news-post." + ext); startActivityForResult(create, CREATE_IMAGE);
        }).show();
    }
    private void writeExport(Uri uri) {
        if (uri == null) return;
        boolean png = exportAsPng;
        Bitmap art = null;
        try {
            art = renderArtwork(1080);
            try (OutputStream stream = getContentResolver().openOutputStream(uri, "w")) {
            if (stream == null || !art.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 94, stream)) throw new IOException("تعذر حفظ الصورة إلى الملف المحدد.");
            }
            Toast.makeText(this, "تم تصدير الصورة محليًا.", Toast.LENGTH_LONG).show();
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            DiagnosticStore.record(this, "تصدير صورة المنشور", e);
            if (!isFinishing()) startActivity(new Intent(this, AppDiagnosticsActivity.class));
        } finally { if (art != null && !art.isRecycled()) art.recycle(); }
    }
    private void saveDraft() {
        if (draftPrefs == null) return;
        draftPrefs.edit().putString("platform", platformSpinner.getSelectedItemPosition() == 1 ? "X" : "Facebook")
                .putString("title", titleInput.getText().toString()).putString("body", bodyInput.getText().toString())
                .putString("hashtags", hashtagsInput.getText().toString()).apply();
    }
    private void showError(String message) { Toast.makeText(this, message == null ? "حدث خطأ غير متوقع." : message, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { if (sourceBitmap != null) { sourceBitmap.recycle(); sourceBitmap = null; } super.onDestroy(); }
}
