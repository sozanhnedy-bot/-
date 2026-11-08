package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.Layout;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local Android equivalent of the supplied media-studio workflow. */
public final class VideoStudioActivity extends Activity {
    public static final String EXTRA_TREND_TITLE = "com.threadsdocumentarynews.app.VIDEO_TITLE";
    public static final String EXTRA_SCRIPT_TEXT = "com.threadsdocumentarynews.app.VIDEO_SCRIPT";
    public static final String EXTRA_TREND_SOURCE = "com.threadsdocumentarynews.app.VIDEO_SOURCE";
    public static final String EXTRA_AUDIO_PATH = "com.threadsdocumentarynews.app.VIDEO_AUDIO_PATH";
    private static final int PICK_MEDIA = 5201;
    private static final int PICK_AUDIO = 5202;
    private static final int SAVE_VIDEO = 5203;
    private static final int GOLD = Color.rgb(255, 187, 54);
    private static final int BG = Color.rgb(8, 9, 13);
    private static final int CARD = Color.rgb(20, 21, 27);
    private static final int WHITE = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);
    private static final int RED = Color.rgb(255, 130, 115);

    private final ArrayList<Uri> mediaUris = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText titleInput;
    private EditText captionInput;
    private EditText tickerInput;
    private Spinner imageDurationSpinner;
    private Spinner formatSpinner;
    private TextView mediaList;
    private TextView audioName;
    private TextView status;
    private ProgressBar progress;
    private Button buildButton;
    private Button saveButton;
    private Button testAudioButton;
    private FrameLayout previewFrame;
    private VideoView videoView;
    private ImageView imageView;
    private Uri audioUri;
    private File outputFile;
    private Transformer transformer;
    private boolean exporting;
    private MediaPlayer audioTester;
    private final ProgressHolder progressHolder = new ProgressHolder();
    private final Runnable progressPoller = new Runnable() {
        @Override public void run() {
            if (!exporting || transformer == null) return;
            int state = transformer.getProgress(progressHolder);
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                progress.setProgress(progressHolder.progress);
                status.setText("جارٍ إنشاء MP4 على الجهاز… " + progressHolder.progress + "%");
            }
            if (exporting) handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("World News — استوديو الفيديو");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        TextView header = text("استوديو الفيديو", 21, WHITE, true);
        header.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout form = card();
        addText(form, "مشروع MP4 جديد", 20, WHITE, true, 0, 5);
        addText(form, "اختَر الصور والفيديوهات بالترتيب. سيضيف الاستوديو عنوانًا وشريط أخبار داخل الصورة، ويرفق التعليق الصوتي الذي أنشأته في المساعد.", 13, MUTED, false, 0, 10);
        addLabel(form, "عنوان الفيديو");
        titleInput = input("عنوان الخبر أو الترند");
        form.addView(titleInput, params(-1, dp(48), 0, 4, 0, 8));
        addLabel(form, "نص الخبر أو التعليق — اختياري");
        captionInput = input("عنوان فرعي قصير يظهر داخل الفيديو");
        captionInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        captionInput.setMinLines(3);
        form.addView(captionInput, params(-1, -2, 0, 4, 0, 10));
        addLabel(form, "شريط الأخبار أسفل الفيديو");
        tickerInput = input("آخر التطورات | أخبار العالم");
        tickerInput.setText("آخر التطورات  |  أخبار العالم");
        form.addView(tickerInput, params(-1, dp(48), 0, 4, 0, 8));
        addText(form, "الانتقالات المتحركة بين اللقطات غير متاحة في هذا الإصدار؛ ستبقى المقاطع متتابعة مع الرسوم الإخبارية الثابتة.", 12, MUTED, false, 0, 10);

        Button pickMedia = button("اختيار صور وفيديوهات");
        form.addView(pickMedia, params(-1, dp(50), 0, 0, 0, 6));
        pickMedia.setOnClickListener(v -> openMediaPicker());
        Button clearMedia = secondaryButton("مسح قائمة الوسائط");
        form.addView(clearMedia, params(-1, dp(44), 0, 0, 0, 6));
        clearMedia.setOnClickListener(v -> {
            mediaUris.clear();
            updateMediaList();
            clearPreview();
            setStatus("تم مسح الوسائط المحددة.", MUTED);
        });
        mediaList = text("لم تُختر صور أو مقاطع بعد.", 13, MUTED, false);
        mediaList.setPadding(dp(12), dp(9), dp(12), dp(9));
        mediaList.setBackgroundColor(Color.rgb(27, 28, 35));
        form.addView(mediaList, params(-1, -2, 0, 0, 0, 10));

        addLabel(form, "مدة عرض كل صورة");
        String[] durations = new String[29];
        for (int i = 0; i < durations.length; i++) durations[i] = (i + 2) + " ثانية";
        imageDurationSpinner = spinner(durations);
        imageDurationSpinner.setSelection(3); // 5 seconds, matching the supplied project.
        form.addView(imageDurationSpinner, params(-1, dp(48), 0, 4, 0, 8));
        addLabel(form, "اتجاه الفيديو");
        formatSpinner = spinner(new String[]{"رأسي 9:16", "أفقي 16:9"});
        form.addView(formatSpinner, params(-1, dp(48), 0, 4, 0, 10));

        Button pickAudio = button("اختيار ملف صوت اختياري");
        form.addView(pickAudio, params(-1, dp(50), 0, 0, 0, 6));
        pickAudio.setOnClickListener(v -> openAudioPicker());
        audioName = text("لم يُضف صوت بعد.", 13, MUTED, false);
        form.addView(audioName, params(-1, -2, 0, 0, 0, 6));
        testAudioButton = secondaryButton("اختبار الصوت المحدد");
        testAudioButton.setEnabled(false);
        form.addView(testAudioButton, params(-1, dp(46), 0, 0, 0, 0));
        testAudioButton.setOnClickListener(v -> testSelectedAudio());
        content.addView(form, params(-1, -2, 0, 0, 0, 12));

        LinearLayout previewCard = card();
        addText(previewCard, "اختبار ومعاينة الفيديو", 18, WHITE, true, 0, 5);
        addText(previewCard, "اختبر أول ملف محدد، ثم راجع الفيديو النهائي هنا بعد التصدير.", 13, MUTED, false, 0, 8);
        Button testVideo = secondaryButton("معاينة أول صورة أو فيديو محدد");
        previewCard.addView(testVideo, params(-1, dp(48), 0, 0, 0, 8));
        testVideo.setOnClickListener(v -> previewFirstMedia());
        previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(Color.BLACK);
        videoView = new VideoView(this);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(View.GONE);
        previewFrame.addView(videoView, new FrameLayout.LayoutParams(-1, dp(230)));
        previewFrame.addView(imageView, new FrameLayout.LayoutParams(-1, dp(230)));
        previewCard.addView(previewFrame, params(-1, dp(230), 0, 0, 0, 8));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        previewCard.addView(progress, params(-1, dp(8), 0, 0, 0, 6));
        status = text("جاهز. اختر الوسائط لاختبارها أو إنشاء الفيديو.", 13, MUTED, false);
        previewCard.addView(status, params(-1, -2, 0, 0, 0, 8));
        buildButton = button("إنشاء الفيديو MP4");
        previewCard.addView(buildButton, params(-1, dp(54), 0, 0, 0, 6));
        buildButton.setOnClickListener(v -> buildVideo());
        saveButton = secondaryButton("حفظ MP4 على الجهاز");
        saveButton.setEnabled(false);
        previewCard.addView(saveButton, params(-1, dp(50), 0, 0, 0, 0));
        saveButton.setOnClickListener(v -> saveOutput());
        content.addView(previewCard, params(-1, -2, 0, 0, 0, 0));
        setContentView(root);
        String incomingTitle = getIntent().getStringExtra(EXTRA_TREND_TITLE);
        if (incomingTitle != null && !incomingTitle.trim().isEmpty()) titleInput.setText(incomingTitle.trim());
        String incomingScript = getIntent().getStringExtra(EXTRA_SCRIPT_TEXT);
        if (incomingScript != null && !incomingScript.trim().isEmpty()) captionInput.setText(defaultLowerThird(incomingScript));
        String incomingSource = getIntent().getStringExtra(EXTRA_TREND_SOURCE);
        String incomingAudioPath = getIntent().getStringExtra(EXTRA_AUDIO_PATH);
        if (incomingAudioPath != null && !incomingAudioPath.trim().isEmpty()) {
            File preparedAudio = new File(incomingAudioPath);
            if (preparedAudio.isFile() && preparedAudio.length() > 44) {
                audioUri = Uri.fromFile(preparedAudio);
                audioName.setText("تعليق صوتي من الكاتب" + (incomingSource == null ? "" : " · " + incomingSource));
                audioName.setTextColor(GOLD);
                testAudioButton.setEnabled(true);
            }
        }
        if (incomingTitle != null && !incomingTitle.trim().isEmpty()) {
            setStatus("وصل موضوع من الرادار" + (incomingSource == null ? "" : " — " + incomingSource) + ". اختر الوسائط لاختبارها ثم أنشئ MP4.", GOLD);
        }
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_MEDIA);
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_MEDIA) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) addPickedUri(data.getClipData().getItemAt(i).getUri(), flags);
            } else if (data.getData() != null) {
                addPickedUri(data.getData(), flags);
            }
            updateMediaList();
        } else if (requestCode == PICK_AUDIO) {
            audioUri = data.getData();
            if (audioUri != null) {
                persistReadPermission(audioUri, data.getFlags());
                audioName.setText("الصوت المحدد: " + displayName(audioUri));
                audioName.setTextColor(GOLD);
                testAudioButton.setEnabled(true);
                setStatus("تم اختيار ملف الصوت. اضغط اختبار الصوت قبل إنشاء الفيديو.", GOLD);
            }
        } else if (requestCode == SAVE_VIDEO && data.getData() != null) {
            copyOutputTo(data.getData());
        }
    }

    private void addPickedUri(Uri uri, int flags) {
        if (uri == null || mediaUris.contains(uri)) return;
        String type = getContentResolver().getType(uri);
        if (type == null || (!type.startsWith("image/") && !type.startsWith("video/"))) return;
        persistReadPermission(uri, flags);
        mediaUris.add(uri);
    }

    private void persistReadPermission(Uri uri, int flags) {
        try {
            if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0)
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
    }

    private void updateMediaList() {
        if (mediaUris.isEmpty()) {
            mediaList.setText("لم تُختر صور أو مقاطع بعد.");
            mediaList.setTextColor(MUTED);
            return;
        }
        StringBuilder names = new StringBuilder("الملفات المختارة بالترتيب:\n");
        for (int i = 0; i < mediaUris.size(); i++) {
            names.append(i + 1).append(". ").append(displayName(mediaUris.get(i))).append('\n');
        }
        mediaList.setText(names.toString().trim());
        mediaList.setTextColor(WHITE);
    }

    private void previewFirstMedia() {
        if (mediaUris.isEmpty()) {
            setStatus("اختر صورة أو فيديو أولًا لاختباره.", RED);
            return;
        }
        Uri uri = mediaUris.get(0);
        String type = getContentResolver().getType(uri);
        stopVideo();
        if (type != null && type.startsWith("image/")) {
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(uri);
            setStatus("معاينة الصورة الأولى من الملفات المحددة.", GOLD);
        } else {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(uri);
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoView.start();
                setStatus("معاينة الفيديو الأول من الملفات المحددة.", GOLD);
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                setStatus("تعذر تشغيل هذا المقطع على الجهاز. جرّب MP4 بترميز H.264.", RED);
                return true;
            });
        }
    }

    private void testSelectedAudio() {
        if (audioUri == null) {
            setStatus("اختر ملف صوت أولًا.", RED);
            return;
        }
        try {
            if (audioTester != null) { audioTester.release(); audioTester = null; }
            audioTester = new MediaPlayer();
            audioTester.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            audioTester.setDataSource(this, audioUri);
            audioTester.setOnPreparedListener(mp -> {
                mp.setVolume(1f, 1f);
                mp.start();
                setStatus("نجح اختبار الصوت وبدأ التشغيل. ارفع صوت الوسائط إذا لم تسمعه.", GOLD);
            });
            audioTester.setOnCompletionListener(mp -> setStatus("انتهى اختبار الصوت.", GOLD));
            audioTester.setOnErrorListener((mp, what, extra) -> {
                setStatus("فشل تشغيل الصوت. رمز المشغل: " + what + "/" + extra, RED);
                return true;
            });
            audioTester.prepareAsync();
            setStatus("جارٍ تجهيز اختبار الصوت…", GOLD);
        } catch (Exception error) {
            setStatus("تعذر اختبار الصوت: " + safeMessage(error), RED);
        }
    }

    private void buildVideo() {
        if (exporting) return;
        if (mediaUris.isEmpty()) {
            setStatus("اختر صورة أو فيديو واحدًا على الأقل.", RED);
            return;
        }
        stopVideo();
        if (audioTester != null) { audioTester.release(); audioTester = null; }
        buildButton.setEnabled(false);
        saveButton.setEnabled(false);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        exporting = true;
        try {
            int imageSeconds = imageDurationSpinner.getSelectedItemPosition() + 2;
            boolean vertical = formatSpinner.getSelectedItemPosition() == 0;
            int width = vertical ? 1080 : 1920;
            int height = vertical ? 1920 : 1080;
            ArrayList<EditedMediaItem> editedItems = new ArrayList<>();
            ArrayList<androidx.media3.common.Effect> videoEffects = new ArrayList<>();
            videoEffects.add(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
            List<TextureOverlay> newsOverlays = createNewsOverlays();
            if (!newsOverlays.isEmpty()) videoEffects.add(new OverlayEffect(newsOverlays));
            for (Uri uri : mediaUris) {
                String type = getContentResolver().getType(uri);
                MediaItem mediaItem;
                EditedMediaItem.Builder edited;
                if (type != null && type.startsWith("image/")) {
                    mediaItem = new MediaItem.Builder().setUri(uri).setImageDurationMs(imageSeconds * 1000L).build();
                    edited = new EditedMediaItem.Builder(mediaItem).setFrameRate(30);
                } else {
                    mediaItem = MediaItem.fromUri(uri);
                    edited = new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true);
                }
                edited.setEffects(new Effects(Collections.emptyList(), videoEffects));
                editedItems.add(edited.build());
            }
            EditedMediaItemSequence videoSequence = EditedMediaItemSequence.withVideoFrom(editedItems);
            Composition composition;
            if (audioUri != null) {
                EditedMediaItem audioItem = new EditedMediaItem.Builder(MediaItem.fromUri(audioUri)).build();
                EditedMediaItemSequence audioSequence = EditedMediaItemSequence.withAudioFrom(Collections.singletonList(audioItem)).buildUpon().setIsLooping(false).build();
                composition = new Composition.Builder(videoSequence, audioSequence).build();
            } else {
                composition = new Composition.Builder(videoSequence).build();
            }
            File directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (directory == null) directory = getFilesDir();
            File outputDir = new File(directory, "WorldNews");
            if (!outputDir.exists() && !outputDir.mkdirs()) throw new IllegalStateException("تعذر تجهيز مجلد الفيديو على الهاتف.");
            String safeName = "world-news-" + System.currentTimeMillis() + ".mp4";
            outputFile = new File(outputDir, safeName);
            transformer = new Transformer.Builder(this)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition finishedComposition, ExportResult result) {
                            exporting = false;
                            handler.removeCallbacks(progressPoller);
                            progress.setProgress(100);
                            progress.setVisibility(View.GONE);
                            buildButton.setEnabled(true);
                            saveButton.setEnabled(outputFile != null && outputFile.isFile() && outputFile.length() > 0);
                            if (saveButton.isEnabled()) {
                                setStatus("اكتمل إنشاء MP4 محليًا (" + humanSize(outputFile.length()) + "). شغّل المعاينة أو احفظه على الجهاز.", GOLD);
                                playOutput();
                            } else {
                                setStatus("انتهى التصدير لكن ملف MP4 غير موجود.", RED);
                            }
                        }
                        @Override public void onError(Composition failedComposition, ExportResult result, ExportException exception) {
                            exporting = false;
                            handler.removeCallbacks(progressPoller);
                            progress.setVisibility(View.GONE);
                            buildButton.setEnabled(true);
                            saveButton.setEnabled(false);
                            setStatus("فشل إنشاء MP4: " + safeMessage(exception) + " — جرّب تقليل الملفات أو استخدام فيديو MP4/H.264.", RED);
                        }
                    }).build();
            transformer.start(composition, outputFile.getAbsolutePath());
            status.setText("بدأ إنشاء الفيديو على الجهاز…");
            status.setTextColor(GOLD);
            handler.postDelayed(progressPoller, 500);
        } catch (Exception error) {
            exporting = false;
            handler.removeCallbacks(progressPoller);
            progress.setVisibility(View.GONE);
            buildButton.setEnabled(true);
            saveButton.setEnabled(false);
            setStatus("تعذر بدء إنشاء الفيديو: " + safeMessage(error), RED);
        }
    }

    private void playOutput() {
        if (outputFile == null || !outputFile.isFile()) return;
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoPath(outputFile.getAbsolutePath());
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoView.start();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            setStatus("تم إنشاء الملف لكن تعذرت معاينته داخل التطبيق. احفظه وجرب مشغل الهاتف. الرمز: " + what + "/" + extra, RED);
            return true;
        });
    }

    private List<TextureOverlay> createNewsOverlays() {
        ArrayList<TextureOverlay> overlays = new ArrayList<>();
        String headline = titleInput == null ? "" : compactText(titleInput.getText().toString(), 72);
        String lowerThird = captionInput == null ? "" : compactText(captionInput.getText().toString(), 100);
        String ticker = tickerInput == null ? "" : compactText(tickerInput.getText().toString(), 100);
        if (!headline.isEmpty()) overlays.add(newsTextOverlay("عاجل  |  " + headline, 42,
                Color.rgb(255, 255, 255), Color.rgb(171, 32, 37), 0.82f, true));
        if (!lowerThird.isEmpty()) overlays.add(newsTextOverlay(lowerThird, 34,
                Color.rgb(255, 255, 255), Color.rgb(16, 35, 54), -0.62f, false));
        if (!ticker.isEmpty()) overlays.add(newsTextOverlay("◀  " + ticker + "   |   WORLD NEWS", 30,
                Color.rgb(255, 255, 255), Color.rgb(171, 32, 37), -0.88f, true));
        return overlays;
    }

    private TextureOverlay newsTextOverlay(String value, int textSizeSp, int foreground, int background,
                                           float verticalAnchor, boolean bold) {
        String padded = "  " + value + "  ";
        SpannableString styled = new SpannableString(padded);
        styled.setSpan(new ForegroundColorSpan(foreground), 0, padded.length(), 0);
        styled.setSpan(new BackgroundColorSpan(background), 0, padded.length(), 0);
        styled.setSpan(new AbsoluteSizeSpan(textSizeSp, true), 0, padded.length(), 0);
        styled.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, padded.length(), 0);
        if (bold) styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, padded.length(), 0);
        boolean top = verticalAnchor > 0;
        StaticOverlaySettings settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0.0f, verticalAnchor)
                .setOverlayFrameAnchor(0.0f, top ? 1.0f : -1.0f)
                .build();
        return TextOverlay.createStaticTextOverlay(styled, settings);
    }

    private String compactText(String raw, int maxLength) {
        if (raw == null) return "";
        String clean = raw.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1).trim() + "…";
    }

    private String defaultLowerThird(String script) {
        String narration = script == null ? "" : script;
        String marker = "التعليق الصوتي الكامل:";
        int markerIndex = narration.indexOf(marker);
        if (markerIndex >= 0) narration = narration.substring(markerIndex + marker.length()).trim();
        String[] sentences = narration.split("[\\r\\n]+|(?<=[.!؟])\\s+", 2);
        return compactText(sentences.length == 0 ? narration : sentences[0], 100);
    }

    private void saveOutput() {
        if (outputFile == null || !outputFile.isFile()) {
            setStatus("لا يوجد MP4 جاهز للحفظ.", RED);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/mp4");
        intent.putExtra(Intent.EXTRA_TITLE, outputFile.getName());
        startActivityForResult(intent, SAVE_VIDEO);
    }

    private void copyOutputTo(Uri destination) {
        try (InputStream input = new java.io.FileInputStream(outputFile);
             OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IllegalStateException("لم يفتح الهاتف موقع حفظ الملف.");
            byte[] buffer = new byte[256 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            setStatus("تم حفظ الفيديو على الجهاز بنجاح: " + outputFile.getName(), GOLD);
        } catch (Exception error) {
            setStatus("فشل حفظ الفيديو: " + safeMessage(error), RED);
        }
    }

    private void clearPreview() {
        stopVideo();
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
    }

    private void stopVideo() {
        if (videoView != null) {
            try { videoView.stopPlayback(); } catch (Exception ignored) { }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "ملف وسائط" : last;
    }

    private String humanSize(long bytes) {
        if (bytes < 1024L * 1024L) return (bytes / 1024L) + " كيلوبايت";
        return String.format(java.util.Locale.US, "%.1f ميغابايت", bytes / (1024.0 * 1024.0));
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._-]+", "$1[محذوف]").substring(0, Math.min(350, message.length()));
    }

    private void setStatus(String message, int color) {
        if (status != null) {
            status.setText(message);
            status.setTextColor(color);
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(15), dp(15), dp(15), dp(15));
        layout.setBackgroundColor(CARD);
        return layout;
    }

    private void addLabel(LinearLayout parent, String label) {
        TextView view = text(label, 14, WHITE, true);
        parent.addView(view, params(-1, -2, 0, 4, 0, 2));
    }

    private void addText(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom) {
        parent.addView(text(value, size, color, bold), params(-1, -2, 0, top, 0, bottom));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private EditText input(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextColor(WHITE);
        field.setHintTextColor(MUTED);
        field.setHint(hint);
        field.setTextSize(15);
        field.setPadding(dp(12), dp(9), dp(12), dp(9));
        field.setBackgroundColor(Color.rgb(27, 28, 35));
        field.setTextDirection(View.TEXT_DIRECTION_RTL);
        return field;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private Button button(String label) {
        Button button = secondaryButton(label);
        button.setBackgroundColor(GOLD);
        button.setTextColor(Color.rgb(20, 16, 8));
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setBackgroundColor(Color.rgb(39, 40, 49));
        return button;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        handler.removeCallbacks(progressPoller);
        if (exporting && transformer != null) {
            try { transformer.cancel(); } catch (Exception ignored) { }
        }
        stopVideo();
        if (audioTester != null) { audioTester.release(); audioTester = null; }
        super.onDestroy();
    }
}
