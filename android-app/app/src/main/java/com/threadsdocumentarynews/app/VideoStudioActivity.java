package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

/** Local Android equivalent of the supplied media-studio workflow. */
public final class VideoStudioActivity extends Activity {
    public static final String EXTRA_TREND_TITLE = "com.threadsdocumentarynews.app.VIDEO_TITLE";
    public static final String EXTRA_SCRIPT_TEXT = "com.threadsdocumentarynews.app.VIDEO_SCRIPT";
    public static final String EXTRA_TREND_SOURCE = "com.threadsdocumentarynews.app.VIDEO_SOURCE";
    public static final String EXTRA_AUDIO_PATH = "com.threadsdocumentarynews.app.VIDEO_AUDIO_PATH";
    private static final int PICK_MEDIA = 5201;
    private static final int PICK_AUDIO = 5202;
    private static final int SAVE_VIDEO = 5203;
    private static final int PICK_MUSIC = 5204;
    private static final int GOLD = Color.rgb(255, 187, 54);
    private static final int BG = Color.rgb(8, 9, 13);
    private static final int CARD = Color.rgb(20, 21, 27);
    private static final int WHITE = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);
    private static final int RED = Color.rgb(255, 130, 115);

    private final ArrayList<Uri> mediaUris = new ArrayList<>();
    private final ArrayList<VideoClipTransform> clipTransforms = new ArrayList<>();
    private EditText titleInput;
    private EditText captionInput;
    private EditText tickerInput;
    private Spinner imageDurationSpinner;
    private Spinner formatSpinner;
    private Spinner templateModeSpinner;
    private Spinner colorPaletteSpinner;
    private Spinner resolutionSpinner;
    private Button mediaPickerButton;
    private TextView mediaList;
    private TextView audioName;
    private TextView musicName;
    private TextView status;
    private ProgressBar progress;
    private Button buildButton;
    private Button saveButton;
    private Button cancelExportButton;
    private Button testAudioButton;
    private Button testMusicButton;
    private Spinner musicVolumeSpinner;
    private Spinner clipSelectionSpinner;
    private Spinner fitModeSpinner;
    private Spinner filterPresetSpinner;
    private Spinner transitionModeSpinner;
    private Spinner transitionDurationSpinner;
    private LinearLayout timelineTrack;
    private SeekBar zoomSeekBar;
    private SeekBar cropXSeekBar;
    private SeekBar cropYSeekBar;
    private SeekBar filterIntensitySeekBar;
    private SeekBar trimStartSeekBar;
    private SeekBar trimEndSeekBar;
    private TextView zoomValueLabel;
    private TextView cropPositionLabel;
    private TextView filterIntensityLabel;
    private TextView trimStartLabel;
    private TextView trimEndLabel;
    private FrameLayout previewHost;
    private FrameLayout previewFrame;
    private VideoView videoView;
    private ImageView imageView;
    private Uri audioUri;
    private Uri musicUri;
    private File outputFile;
    private boolean exporting;
    private boolean updatingClipControls;
    private int selectedMediaIndex;
    private String currentExportStage = "جارٍ إنشاء MP4 على الجهاز…";
    private MediaPlayer audioTester;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("World News — استوديو الفيديو");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextView headerTitle = text("تحرير الفيديو", 18, WHITE, true);
        headerTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, -1, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);

        LinearLayout form = card();
        addText(form, "مشروع MP4 جديد", 20, WHITE, true, 0, 5);
        addText(form, "قالب أخبار احترافي: الصورة في الأعلى والخبر أسفلها. اختر الألوان من مساحة العمل ثم أضف الصور أو الفيديو والصوت الاختياري.", 13, MUTED, false, 0, 10);
        Button licenseButton = secondaryButton("تراخيص محرّك التصدير");
        form.addView(licenseButton, params(-1, dp(42), 0, 0, 0, 8));
        licenseButton.setOnClickListener(v -> showFfmpegLicenseNotice());
        addLabel(form, "نوع النشرة");
        templateModeSpinner = spinner(new String[]{"أخبار عاجلة", "أخبار عادية"});
        form.addView(templateModeSpinner, params(-1, dp(48), 0, 4, 0, 8));
        addLabel(form, "ألوان القالب");
        colorPaletteSpinner = spinner(new String[]{
                "أحمر + أبيض + كحلي — مثل الصورة المرجعية",
                "كحلي + أبيض + أحمر",
                "أسود + أبيض + ذهبي",
                "أخضر + أبيض + كحلي"
        });
        colorPaletteSpinner.setSelection(0);
        form.addView(colorPaletteSpinner, params(-1, dp(48), 0, 4, 0, 8));
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
        addText(form, "تُجمع المقاطع افتراضيًا بقطع مباشر، ويمكن اختيار تلاشي مرئي للأسود عند الحدود. الموسيقى اختيارية ومنخفضة ولا تتكرر تلقائيًا.", 12, MUTED, false, 0, 10);

        mediaPickerButton = button("＋ إضافة صور أو فيديو");
        form.addView(mediaPickerButton, params(-1, dp(50), 0, 0, 0, 6));
        mediaPickerButton.setOnClickListener(v -> openMediaPicker());
        Button clearMedia = secondaryButton("مسح قائمة الوسائط");
        form.addView(clearMedia, params(-1, dp(44), 0, 0, 0, 6));
        clearMedia.setOnClickListener(v -> {
            mediaUris.clear();
            clipTransforms.clear();
            selectedMediaIndex = 0;
            updateMediaList();
            clearPreview();
            setStatus("تم مسح الوسائط المحددة.", MUTED);
        });
        mediaList = text("لم تُختر صور أو مقاطع بعد.", 13, MUTED, false);
        mediaList.setPadding(dp(12), dp(9), dp(12), dp(9));
        mediaList.setBackgroundColor(Color.rgb(27, 28, 35));
        form.addView(mediaList, params(-1, -2, 0, 0, 0, 10));

        LinearLayout timelinePanel = card();
        addText(timelinePanel, "الخط الزمني  ·  اضغط على مقطع لتحديده", 14, WHITE, true, 0, 6);
        HorizontalScrollView timelineScroll = new HorizontalScrollView(this);
        timelineScroll.setHorizontalScrollBarEnabled(false);
        timelineTrack = new LinearLayout(this);
        timelineTrack.setOrientation(LinearLayout.HORIZONTAL);
        timelineScroll.addView(timelineTrack, new HorizontalScrollView.LayoutParams(-2, -1));
        timelinePanel.addView(timelineScroll, params(-1, dp(64), 0, 2, 0, 6));
        LinearLayout timelineActions = new LinearLayout(this);
        timelineActions.setOrientation(LinearLayout.HORIZONTAL);
        Button moveEarlierButton = secondaryButton("قبل");
        Button moveLaterButton = secondaryButton("بعد");
        Button removeClipButton = secondaryButton("حذف المحدد");
        timelineActions.addView(moveEarlierButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        timelineActions.addView(moveLaterButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        timelineActions.addView(removeClipButton, new LinearLayout.LayoutParams(0, dp(42), 1.25f));
        timelinePanel.addView(timelineActions, params(-1, dp(42), 0, 0, 0, 0));
        moveEarlierButton.setOnClickListener(v -> moveSelectedClip(-1));
        moveLaterButton.setOnClickListener(v -> moveSelectedClip(1));
        removeClipButton.setOnClickListener(v -> removeSelectedClip());

        addLabel(form, "تحرير المقطع المحدد");
        clipSelectionSpinner = spinner(new String[]{"لا توجد وسائط بعد"});
        clipSelectionSpinner.setEnabled(false);
        form.addView(clipSelectionSpinner, params(-1, dp(48), 0, 4, 0, 8));
        addLabel(form, "ملاءمة الصورة أو الفيديو داخل الإطار");
        fitModeSpinner = spinner(new String[]{"احتواء — إظهار المصدر كاملًا", "ملء الإطار — قص الزوائد"});
        fitModeSpinner.setSelection(1);
        form.addView(fitModeSpinner, params(-1, dp(48), 0, 4, 0, 8));
        zoomValueLabel = text("التكبير: 100%", 13, MUTED, false);
        form.addView(zoomValueLabel, params(-1, -2, 0, 2, 0, 0));
        zoomSeekBar = new SeekBar(this);
        zoomSeekBar.setMax(150);
        form.addView(zoomSeekBar, params(-1, dp(42), 0, 0, 0, 2));
        cropPositionLabel = text("موضع القص: أفقي 50% · رأسي 50%", 13, MUTED, false);
        form.addView(cropPositionLabel, params(-1, -2, 0, 0, 0, 0));
        cropXSeekBar = new SeekBar(this);
        cropXSeekBar.setMax(100);
        cropXSeekBar.setProgress(50);
        form.addView(cropXSeekBar, params(-1, dp(38), 0, 0, 0, 0));
        cropYSeekBar = new SeekBar(this);
        cropYSeekBar.setMax(100);
        cropYSeekBar.setProgress(50);
        form.addView(cropYSeekBar, params(-1, dp(38), 0, 0, 0, 8));
        addLabel(form, "فلتر المقطع المحدد");
        filterPresetSpinner = spinner(VideoFilterPreset.labels());
        form.addView(filterPresetSpinner, params(-1, dp(48), 0, 4, 0, 2));
        filterIntensityLabel = text("شدة الفلتر: 100%", 13, MUTED, false);
        form.addView(filterIntensityLabel, params(-1, -2, 0, 2, 0, 0));
        filterIntensitySeekBar = new SeekBar(this);
        filterIntensitySeekBar.setMax(100);
        filterIntensitySeekBar.setProgress(100);
        form.addView(filterIntensitySeekBar, params(-1, dp(38), 0, 0, 0, 3));
        addText(form, "تُطبّق الفلاتر على المقطع عند التصدير؛ المعاينة الحالية تعرض القص والتكبير فقط.", 11, MUTED, false, 0, 9);
        addLabel(form, "قص مدة المقطع المحدد");
        trimStartLabel = text("بداية المقطع: 0%", 13, MUTED, false);
        form.addView(trimStartLabel, params(-1, -2, 0, 2, 0, 0));
        trimStartSeekBar = new SeekBar(this);
        trimStartSeekBar.setMax(95);
        form.addView(trimStartSeekBar, params(-1, dp(38), 0, 0, 0, 1));
        trimEndLabel = text("نهاية المقطع: 100%", 13, MUTED, false);
        form.addView(trimEndLabel, params(-1, -2, 0, 0, 0, 0));
        trimEndSeekBar = new SeekBar(this);
        trimEndSeekBar.setMax(100);
        trimEndSeekBar.setProgress(100);
        form.addView(trimEndSeekBar, params(-1, dp(38), 0, 0, 0, 9));

        addLabel(form, "مدة عرض كل صورة");
        String[] durations = new String[29];
        for (int i = 0; i < durations.length; i++) durations[i] = (i + 2) + " ثانية";
        imageDurationSpinner = spinner(durations);
        imageDurationSpinner.setSelection(3); // 5 seconds, matching the supplied project.
        form.addView(imageDurationSpinner, params(-1, dp(48), 0, 4, 0, 8));
        addLabel(form, "الانتقال بين المقاطع");
        transitionModeSpinner = spinner(new String[]{"قطع مباشر", "تلاشي للأسود"});
        transitionModeSpinner.setSelection(0);
        form.addView(transitionModeSpinner, params(-1, dp(48), 0, 4, 0, 4));
        transitionDurationSpinner = spinner(new String[]{"0.25 ثانية", "0.5 ثانية", "0.75 ثانية"});
        transitionDurationSpinner.setSelection(1);
        form.addView(transitionDurationSpinner, params(-1, dp(48), 0, 0, 0, 8));
        addLabel(form, "مقاس الفيديو والمنصة");
        formatSpinner = spinner(VideoCanvasPreset.labels());
        form.addView(formatSpinner, params(-1, dp(48), 0, 4, 0, 10));
        addText(form, "اختر النسبة المناسبة؛ سيتغير إطار المعاينة ومقاس التصدير تلقائيًا.", 12, MUTED, false, 0, 8);
        addLabel(form, "دقة التصدير");
        resolutionSpinner = spinner(new String[]{"720p", "1080p"});
        resolutionSpinner.setSelection(0);
        form.addView(resolutionSpinner, params(-1, dp(48), 0, 4, 0, 10));
        addText(form, "720p أخف وأسرع على الهاتف؛ اختر 1080p لجودة أعلى", 12, MUTED, false, 0, 8);

        Button pickAudio = button("اختيار تعليق الراوي (اختياري)");
        form.addView(pickAudio, params(-1, dp(50), 0, 0, 0, 6));
        pickAudio.setOnClickListener(v -> openAudioPicker());
        audioName = text("لم يُضف صوت بعد.", 13, MUTED, false);
        form.addView(audioName, params(-1, -2, 0, 0, 0, 6));
        testAudioButton = secondaryButton("اختبار الصوت المحدد");
        testAudioButton.setEnabled(false);
        form.addView(testAudioButton, params(-1, dp(46), 0, 0, 0, 0));
        testAudioButton.setOnClickListener(v -> testSelectedAudio());
        addLabel(form, "موسيقى خلفية أو مؤثر صوتي منفصل");
        Button pickMusic = secondaryButton("اختيار موسيقى أو مؤثر صوتي");
        form.addView(pickMusic, params(-1, dp(48), 0, dp(9), 0, 5));
        pickMusic.setOnClickListener(v -> openMusicPicker());
        musicName = text("اختياري — لا توجد موسيقى خلفية.", 13, MUTED, false);
        form.addView(musicName, params(-1, -2, 0, 0, 0, 5));
        musicVolumeSpinner = spinner(new String[]{"مستوى الخلفية 10%", "مستوى الخلفية 18%", "مستوى الخلفية 28%"});
        musicVolumeSpinner.setSelection(1);
        form.addView(musicVolumeSpinner, params(-1, dp(46), 0, 0, 0, 5));
        testMusicButton = secondaryButton("اختبار صوت الخلفية");
        testMusicButton.setEnabled(false);
        form.addView(testMusicButton, params(-1, dp(44), 0, 0, 0, 0));
        testMusicButton.setOnClickListener(v -> testSelectedMusic());
        content.addView(form, params(-1, -2, 0, 0, 0, 12));

        LinearLayout previewCard = card();
        addText(previewCard, "المعاينة", 14, MUTED, true, 0, 5);
        previewHost = new FrameLayout(this);
        previewHost.setBackgroundColor(Color.rgb(12, 13, 17));
        previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(Color.BLACK);
        videoView = new VideoView(this);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(View.GONE);
        previewFrame.addView(videoView, new FrameLayout.LayoutParams(-1, -1));
        previewFrame.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
        previewHost.addView(previewFrame, new FrameLayout.LayoutParams(dp(164), dp(290), Gravity.CENTER));
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density);
        int editorPreviewHeightDp = landscape
                ? Math.max(112, Math.min(210, Math.round(screenHeightDp * 0.38f)))
                : Math.max(190, Math.min(270, Math.round(screenHeightDp * 0.32f)));
        previewCard.addView(previewHost, params(-1, dp(editorPreviewHeightDp), 0, 0, 0, 4));
        previewHost.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updatePreviewAspectRatio());
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        progress.setIndeterminate(false);
        cancelExportButton = secondaryButton("إلغاء التصدير");
        cancelExportButton.setVisibility(View.GONE);
        cancelExportButton.setOnClickListener(v -> {
            if (exporting && exportEngine != null) {
                cancelExportButton.setEnabled(false);
                setStatus("جارٍ إلغاء التصدير وتنظيف الملف المؤقت…", MUTED);
                exportEngine.cancel();
            }
        });
        status = text("جاهز. اختر الوسائط لاختبارها أو إنشاء الفيديو.", 13, MUTED, false);
        buildButton = button("إنشاء الفيديو MP4");
        buildButton.setText("تصدير");
        buildButton.setOnClickListener(v -> buildVideo());
        saveButton = secondaryButton("حفظ MP4 على الجهاز");
        saveButton.setEnabled(false);
        saveButton.setText("حفظ الفيديو");
        saveButton.setOnClickListener(v -> saveOutput());

        Button backButton = secondaryButton("رجوع");
        backButton.setTextSize(12);
        backButton.setOnClickListener(v -> finish());
        header.addView(backButton, params(dp(62), dp(40), 4, 0, 0, 0));
        header.addView(buildButton, params(dp(82), dp(40), 8, 0, 0, 0));

        HorizontalScrollView editorToolsScroll = new HorizontalScrollView(this);
        editorToolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout editorTools = new LinearLayout(this);
        editorTools.setGravity(Gravity.CENTER_VERTICAL);
        editorTools.setOrientation(LinearLayout.HORIZONTAL);
        editorToolsScroll.addView(editorTools, new HorizontalScrollView.LayoutParams(-2, -1));
        addEditorToolTab(editorTools, "وسائط", scroll, mediaPickerButton);
        addEditorToolTab(editorTools, "نص", scroll, titleInput);
        addEditorToolTab(editorTools, "قص", scroll, fitModeSpinner);
        addEditorToolTab(editorTools, "فلاتر", scroll, filterPresetSpinner);
        addEditorToolTab(editorTools, "صوت", scroll, audioName);
        addEditorToolTab(editorTools, "مقاس", scroll, resolutionSpinner);

        LinearLayout exportFooter = new LinearLayout(this);
        exportFooter.setOrientation(LinearLayout.VERTICAL);
        exportFooter.setPadding(dp(12), dp(3), dp(12), dp(6));
        exportFooter.setBackgroundColor(BG);
        exportFooter.addView(progress, params(-1, dp(6), 0, 0, 0, 3));
        exportFooter.addView(status, params(-1, -2, 0, 0, 0, 4));
        LinearLayout footerActions = new LinearLayout(this);
        footerActions.setGravity(Gravity.CENTER_VERTICAL);
        footerActions.setOrientation(LinearLayout.HORIZONTAL);
        footerActions.addView(saveButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        footerActions.addView(cancelExportButton, params(dp(116), dp(46), 8, 0, 0, 0));
        exportFooter.addView(footerActions, new LinearLayout.LayoutParams(-1, dp(46)));

        if (landscape) {
            root.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
            LinearLayout workspace = new LinearLayout(this);
            workspace.setOrientation(LinearLayout.HORIZONTAL);
            workspace.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            LinearLayout previewColumn = new LinearLayout(this);
            previewColumn.setOrientation(LinearLayout.VERTICAL);
            previewColumn.setPadding(dp(6), dp(2), dp(4), dp(2));
            previewColumn.addView(previewCard, new LinearLayout.LayoutParams(-1, 0, 1f));
            previewColumn.addView(timelinePanel, params(-1, dp(112), 0, 2, 0, 2));
            LinearLayout toolsColumn = new LinearLayout(this);
            toolsColumn.setOrientation(LinearLayout.VERTICAL);
            toolsColumn.setPadding(dp(4), dp(2), dp(6), dp(2));
            toolsColumn.addView(editorToolsScroll, new LinearLayout.LayoutParams(-1, dp(46)));
            toolsColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            workspace.addView(previewColumn, new LinearLayout.LayoutParams(0, -1, 1.12f));
            workspace.addView(toolsColumn, new LinearLayout.LayoutParams(0, -1, 0.88f));
            root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1f));
            root.addView(exportFooter, new LinearLayout.LayoutParams(-1, -2));
        } else {
            root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));
            root.addView(previewCard, params(-1, -2, 8, 2, 8, 4));
            root.addView(timelinePanel, params(-1, -2, 8, 0, 8, 4));
            root.addView(editorToolsScroll, new LinearLayout.LayoutParams(-1, dp(46)));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            root.addView(exportFooter, new LinearLayout.LayoutParams(-1, -2));
        }
        setContentView(root);
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreviewAspectRatio();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        clipSelectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < mediaUris.size()) {
                    selectedMediaIndex = position;
                    loadClipTransformControls();
                    previewFirstMedia();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        fitModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSelectedClipTransform();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        filterPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSelectedClipTransform();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        bindTransformSeekBar(zoomSeekBar);
        bindTransformSeekBar(cropXSeekBar);
        bindTransformSeekBar(cropYSeekBar);
        bindTransformSeekBar(filterIntensitySeekBar);
        bindTrimSeekBars();
        previewHost.post(() -> updatePreviewAspectRatio());
        if (savedInstanceState != null) restoreEditorState(savedInstanceState);
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

    private void restoreEditorState(Bundle state) {
        ArrayList<String> savedUris = state.getStringArrayList("editor.mediaUris");
        float[] savedTransforms = state.getFloatArray("editor.transforms");
        if (savedUris != null) {
            mediaUris.clear();
            clipTransforms.clear();
            for (int i = 0; i < savedUris.size(); i++) {
                Uri uri = Uri.parse(savedUris.get(i));
                mediaUris.add(uri);
                int offset = i * 8;
                try {
                    if (savedTransforms == null || savedTransforms.length < offset + 8) throw new IllegalArgumentException();
                    int fit = Math.max(0, Math.min(1, Math.round(savedTransforms[offset])));
                    int filter = Math.max(0, Math.min(VideoFilterPreset.values().length - 1, Math.round(savedTransforms[offset + 4])));
                    clipTransforms.add(new VideoClipTransform(
                            fit == 0 ? VideoClipTransform.FitMode.FIT : VideoClipTransform.FitMode.FILL,
                            savedTransforms[offset + 1], savedTransforms[offset + 2], savedTransforms[offset + 3],
                            VideoFilterPreset.values()[filter], savedTransforms[offset + 5],
                            savedTransforms[offset + 6], savedTransforms[offset + 7]));
                } catch (Exception ignored) {
                    clipTransforms.add(VideoClipTransform.defaultTransform());
                }
            }
            selectedMediaIndex = Math.max(0, Math.min(state.getInt("editor.selected", 0), Math.max(0, mediaUris.size() - 1)));
            updateMediaList();
            if (!mediaUris.isEmpty()) previewFirstMedia();
        }
        if (state.containsKey("editor.title")) titleInput.setText(state.getString("editor.title"));
        if (state.containsKey("editor.caption")) captionInput.setText(state.getString("editor.caption"));
        if (state.containsKey("editor.ticker")) tickerInput.setText(state.getString("editor.ticker"));
        restoreSpinnerSelection(formatSpinner, state, "editor.format");
        restoreSpinnerSelection(templateModeSpinner, state, "editor.template");
        restoreSpinnerSelection(colorPaletteSpinner, state, "editor.palette");
        restoreSpinnerSelection(imageDurationSpinner, state, "editor.imageDuration");
        restoreSpinnerSelection(resolutionSpinner, state, "editor.resolution");
        restoreSpinnerSelection(transitionModeSpinner, state, "editor.transition");
        restoreSpinnerSelection(transitionDurationSpinner, state, "editor.transitionDuration");
        String savedAudio = state.getString("editor.audio");
        if (savedAudio != null) {
            audioUri = Uri.parse(savedAudio);
            audioName.setText("الصوت المحدد: " + displayName(audioUri));
            testAudioButton.setEnabled(true);
        }
        String savedMusic = state.getString("editor.music");
        if (savedMusic != null) {
            musicUri = Uri.parse(savedMusic);
            musicName.setText("الخلفية: " + displayName(musicUri));
            testMusicButton.setEnabled(true);
        }
    }

    private void restoreSpinnerSelection(Spinner spinner, Bundle state, String key) {
        if (state.containsKey(key) && spinner.getAdapter() != null && spinner.getAdapter().getCount() > 0)
            spinner.setSelection(Math.max(0, Math.min(state.getInt(key), spinner.getAdapter().getCount() - 1)));
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

    private void openMusicPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_MUSIC);
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
        } else if (requestCode == PICK_MUSIC) {
            musicUri = data.getData();
            if (musicUri != null) {
                persistReadPermission(musicUri, data.getFlags());
                musicName.setText("الخلفية: " + displayName(musicUri));
                musicName.setTextColor(GOLD);
                testMusicButton.setEnabled(true);
                setStatus("تم اختيار موسيقى/مؤثر خلفي. سيتم خفضه تلقائيًا ومزجه تحت صوت الراوي.", GOLD);
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
        clipTransforms.add(VideoClipTransform.defaultTransform());
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
            refreshClipSelector();
            return;
        }
        StringBuilder names = new StringBuilder("الملفات المختارة بالترتيب:\n");
        for (int i = 0; i < mediaUris.size(); i++) {
            names.append(i + 1).append(". ").append(displayName(mediaUris.get(i))).append('\n');
        }
        mediaList.setText(names.toString().trim());
        mediaList.setTextColor(WHITE);
        refreshClipSelector();
    }

    private void previewFirstMedia() {
        if (mediaUris.isEmpty()) {
            setStatus("اختر صورة أو فيديو أولًا لاختباره.", RED);
            return;
        }
        if (selectedMediaIndex < 0 || selectedMediaIndex >= mediaUris.size()) selectedMediaIndex = 0;
        Uri uri = mediaUris.get(selectedMediaIndex);
        String type = getContentResolver().getType(uri);
        stopVideo();
        if (type != null && type.startsWith("image/")) {
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(uri);
            applySelectedTransformToPreview();
            setStatus("معاينة الصورة " + (selectedMediaIndex + 1) + " من الملفات المحددة.", GOLD);
        } else {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(uri);
            applySelectedTransformToPreview();
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                applySelectedTransformToPreview();
                videoView.start();
                setStatus("معاينة الفيديو " + (selectedMediaIndex + 1) + " من الملفات المحددة.", GOLD);
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
        testAudioSource(audioUri, "تعليق الراوي");
    }

    private void testSelectedMusic() {
        if (musicUri == null) {
            setStatus("اختر موسيقى أو مؤثرًا صوتيًا أولًا.", RED);
            return;
        }
        testAudioSource(musicUri, "موسيقى الخلفية");
    }

    private void testAudioSource(Uri source, String label) {
        try {
            if (audioTester != null) { audioTester.release(); audioTester = null; }
            audioTester = new MediaPlayer();
            audioTester.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            audioTester.setDataSource(this, source);
            audioTester.setOnPreparedListener(mp -> {
                mp.setVolume(1f, 1f);
                mp.start();
                setStatus("نجح اختبار " + label + " وبدأ التشغيل. ارفع صوت الوسائط إذا لم تسمعه.", GOLD);
            });
            audioTester.setOnCompletionListener(mp -> setStatus("انتهى اختبار " + label + ".", GOLD));
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

    private VideoExportEngine exportEngine;

    private void buildVideo() {
        if (exporting) return;
        if (mediaUris.isEmpty()) {
            setStatus("اختر صورة أو فيديو واحدًا على الأقل.", RED);
            return;
        }
        pauseVideoForExport();
        if (audioTester != null) { audioTester.release(); audioTester = null; }
        buildButton.setEnabled(false);
        saveButton.setEnabled(false);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        cancelExportButton.setVisibility(View.VISIBLE);
        cancelExportButton.setEnabled(true);
        exporting = true;
        try {
            int imageSeconds = imageDurationSpinner.getSelectedItemPosition() + 2;
            boolean output720p = resolutionSpinner.getSelectedItemPosition() == 0;
            boolean breakingNews = templateModeSpinner.getSelectedItemPosition() == 0;
            int[] outputSize = selectedCanvasPreset().outputSize(!output720p);
            int width = outputSize[0];
            int height = outputSize[1];
            String headline = titleInput == null ? "" : compactText(titleInput.getText().toString(), 90);
            String lowerThird = captionInput == null ? "" : compactText(captionInput.getText().toString(), 120);
            String ticker = tickerInput == null ? "" : compactText(tickerInput.getText().toString(), 120);
            int colorPalette = colorPaletteSpinner == null ? 0 : colorPaletteSpinner.getSelectedItemPosition();
            VideoTransition.Mode transitionMode = transitionModeSpinner.getSelectedItemPosition() == 1
                    ? VideoTransition.Mode.FADE_TO_BLACK : VideoTransition.Mode.NONE;
            long transitionDurationMs = (transitionDurationSpinner.getSelectedItemPosition() + 1L) * 250L;

            File directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (directory == null) directory = getFilesDir();
            File outputDir = new File(directory, "WorldNews");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IllegalStateException("تعذر تجهيز مجلد الفيديو على الهاتف.");
            }
            outputFile = new File(outputDir, "world-news-" + System.currentTimeMillis() + ".mp4");

            VideoExportRequest request = new VideoExportRequest(
                    mediaUris, audioUri, musicUri, headline, lowerThird, ticker,
                    outputFile, width, height, imageSeconds, selectedMusicGain(), breakingNews, colorPalette,
                    new ArrayList<>(clipTransforms), transitionMode, transitionDurationMs);

            exportEngine = new FfmpegVideoExportEngine(this);
            exportEngine.export(request, new VideoExportEngine.Listener() {
                @Override public void onStage(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    currentExportStage = message;
                    status.setText(message + "  " + progress.getProgress() + "%");
                    status.setTextColor(GOLD);
                }

                @Override public void onProgress(int percent) {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setProgress(percent);
                    status.setText(currentExportStage + "  " + percent + "%");
                    status.setTextColor(GOLD);
                }

                @Override public void onCompleted(File file) {
                    if (isFinishing() || isDestroyed()) return;
                    exporting = false;
                    progress.setProgress(100);
                    progress.setVisibility(View.GONE);
                    cancelExportButton.setVisibility(View.GONE);
                    buildButton.setEnabled(true);
                    saveButton.setEnabled(file != null && file.isFile() && file.length() > 0L);
                    if (saveButton.isEnabled()) {
                        outputFile = file;
                        setStatus("اكتمل إنشاء MP4 محليًا (" + humanSize(file.length()) + "). شغّل المعاينة أو احفظه على الجهاز.", GOLD);
                        playOutput();
                    } else {
                        setStatus("انتهى التصدير لكن ملف MP4 غير موجود.", RED);
                    }
                }

                @Override public void onError(Exception error) {
                    if (isFinishing() || isDestroyed()) return;
                    exporting = false;
                    progress.setVisibility(View.GONE);
                    cancelExportButton.setVisibility(View.GONE);
                    cancelExportButton.setEnabled(true);
                    buildButton.setEnabled(true);
                    saveButton.setEnabled(false);
                    if (error instanceof CancellationException) {
                        setStatus("تم إلغاء التصدير وتنظيف الملف المؤقت.", MUTED);
                    } else {
                        android.util.Log.e("VideoStudioActivity", "MP4 export failed", error);
                        setStatus("فشل إنشاء MP4: " + safeMessage(error) + " — افتح تقرير التشخيص لنسخ التفاصيل الكاملة.", RED);
                        showExportErrorDetails(error);
                    }
                }
            });
            currentExportStage = "بدأ إنشاء الفيديو على الجهاز…";
            status.setText(currentExportStage);
            status.setTextColor(GOLD);
        } catch (Exception error) {
            exporting = false;
            progress.setVisibility(View.GONE);
            cancelExportButton.setVisibility(View.GONE);
            cancelExportButton.setEnabled(true);
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

    private float selectedMusicGain() {
        int position = musicVolumeSpinner == null ? 1 : musicVolumeSpinner.getSelectedItemPosition();
        return position == 0 ? 0.10f : position == 2 ? 0.28f : 0.18f;
    }

    private long getMediaDurationMs(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration == null ? 0L : Math.max(0L, Long.parseLong(duration));
        } catch (Exception ignored) {
            return 0L;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
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

    private void showFfmpegLicenseNotice() {
        String notice = "يستخدم التطبيق FFmpegKitNext 9.0.2 المبني محليًا لمعمارية arm64-v8a، "
                + "مع ترميز H.264 بواسطة libx264 وإعداد GPL مفعّل.\n\n"
                + "مكتبة FFmpeg ذات libx264 هنا مشمولة بمتطلبات GPL، وليس LGPL فقط. قبل توزيع APK "
                + "عام يجب مراجعة الالتزامات القانونية ومصدر المكتبة وإشعارات مكوناتها؛ لا توزّع هذه النسخة "
                + "قبل إتمام المراجعة. راجع android-app/FFMPEG_LICENSES.md. هذا تنبيه تعريفي وليس استشارة قانونية.";
        new AlertDialog.Builder(this)
                .setTitle("تراخيص محرّك الفيديو")
                .setMessage(notice)
                .setPositiveButton("حسنًا", null)
                .show();
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

    private void pauseVideoForExport() {
        if (videoView == null || videoView.getVisibility() != View.VISIBLE) return;
        try {
            if (videoView.isPlaying()) videoView.pause();
        } catch (Exception ignored) { }
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

    private VideoCanvasPreset selectedCanvasPreset() {
        return VideoCanvasPreset.fromPosition(formatSpinner == null ? 0 : formatSpinner.getSelectedItemPosition());
    }

    private void bindTransformSeekBar(SeekBar seekBar) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progressValue, boolean fromUser) {
                updateSelectedClipTransform();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private void bindTrimSeekBars() {
        trimStartSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser || updatingClipControls) return;
                int start = Math.min(value, 95);
                if (start >= trimEndSeekBar.getProgress() - 5) {
                    trimEndSeekBar.setProgress(Math.min(100, start + 5));
                }
                updateSelectedClipTransform();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        trimEndSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser || updatingClipControls) return;
                int end = Math.max(5, value);
                if (value < 5) trimEndSeekBar.setProgress(5);
                if (end <= trimStartSeekBar.getProgress() + 5) {
                    trimStartSeekBar.setProgress(Math.max(0, end - 5));
                }
                updateSelectedClipTransform();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private void updateSelectedClipTransform() {
        if (updatingClipControls || selectedMediaIndex < 0 || selectedMediaIndex >= clipTransforms.size()) return;
        VideoClipTransform.FitMode mode = fitModeSpinner.getSelectedItemPosition() == 0
                ? VideoClipTransform.FitMode.FIT : VideoClipTransform.FitMode.FILL;
        float zoom = 1f + zoomSeekBar.getProgress() / 100f;
        float positionX = cropXSeekBar.getProgress() / 100f;
        float positionY = cropYSeekBar.getProgress() / 100f;
        VideoFilterPreset filter = VideoFilterPreset.fromPosition(filterPresetSpinner.getSelectedItemPosition());
        float filterIntensity = filterIntensitySeekBar.getProgress() / 100f;
        float trimStart = trimStartSeekBar.getProgress() / 100f;
        float trimEnd = trimEndSeekBar.getProgress() / 100f;
        VideoClipTransform transform = new VideoClipTransform(mode, zoom, positionX, positionY,
                filter, filterIntensity, trimStart, trimEnd);
        clipTransforms.set(selectedMediaIndex, transform);
        zoomValueLabel.setText("التكبير: " + Math.round(zoom * 100f) + "%");
        cropPositionLabel.setText("موضع القص: أفقي " + Math.round(positionX * 100f)
                + "% · رأسي " + Math.round(positionY * 100f) + "%");
        filterIntensityLabel.setText("شدة الفلتر: " + Math.round(filterIntensity * 100f) + "%");
        updateTrimLabels(trimStart, trimEnd);
        applySelectedTransformToPreview();
    }

    private void loadClipTransformControls() {
        if (selectedMediaIndex < 0 || selectedMediaIndex >= clipTransforms.size()) return;
        VideoClipTransform transform = clipTransforms.get(selectedMediaIndex);
        updatingClipControls = true;
        fitModeSpinner.setSelection(transform.getFitMode() == VideoClipTransform.FitMode.FIT ? 0 : 1);
        zoomSeekBar.setProgress(Math.round((transform.getZoom() - 1f) * 100f));
        cropXSeekBar.setProgress(Math.round(transform.getPositionX() * 100f));
        cropYSeekBar.setProgress(Math.round(transform.getPositionY() * 100f));
        filterPresetSpinner.setSelection(transform.getFilterPreset().ordinal());
        filterIntensitySeekBar.setProgress(Math.round(transform.getFilterIntensity() * 100f));
        trimStartSeekBar.setProgress(Math.round(transform.getTrimStartFraction() * 100f));
        trimEndSeekBar.setProgress(Math.round(transform.getTrimEndFraction() * 100f));
        updatingClipControls = false;
        zoomValueLabel.setText("التكبير: " + Math.round(transform.getZoom() * 100f) + "%");
        cropPositionLabel.setText("موضع القص: أفقي " + Math.round(transform.getPositionX() * 100f)
                + "% · رأسي " + Math.round(transform.getPositionY() * 100f) + "%");
        filterIntensityLabel.setText("شدة الفلتر: " + Math.round(transform.getFilterIntensity() * 100f) + "%");
        updateTrimLabels(transform.getTrimStartFraction(), transform.getTrimEndFraction());
        applySelectedTransformToPreview();
    }

    private void updateTrimLabels(float start, float end) {
        trimStartLabel.setText("بداية المقطع: " + Math.round(start * 100f) + "%");
        trimEndLabel.setText("نهاية المقطع: " + Math.round(end * 100f) + "%");
    }

    private void applySelectedTransformToPreview() {
        if (selectedMediaIndex < 0 || selectedMediaIndex >= clipTransforms.size()) return;
        VideoClipTransform transform = clipTransforms.get(selectedMediaIndex);
        ImageView.ScaleType scaleType = transform.getFitMode() == VideoClipTransform.FitMode.FIT
                ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP;
        imageView.setScaleType(scaleType);
        float zoom = transform.getZoom();
        float offsetX = (0.5f - transform.getPositionX()) * previewFrame.getWidth() * (zoom - 1f);
        float offsetY = (0.5f - transform.getPositionY()) * previewFrame.getHeight() * (zoom - 1f);
        imageView.setScaleX(zoom);
        imageView.setScaleY(zoom);
        imageView.setTranslationX(offsetX);
        imageView.setTranslationY(offsetY);
        videoView.setScaleX(zoom);
        videoView.setScaleY(zoom);
        videoView.setTranslationX(offsetX);
        videoView.setTranslationY(offsetY);
    }

    private void refreshClipSelector() {
        if (clipSelectionSpinner == null) return;
        @SuppressWarnings("unchecked")
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) clipSelectionSpinner.getAdapter();
        adapter.clear();
        for (Uri uri : mediaUris) adapter.add(displayName(uri));
        if (mediaUris.isEmpty()) {
            adapter.add("لا توجد وسائط بعد");
            selectedMediaIndex = 0;
            clipSelectionSpinner.setEnabled(false);
        } else {
            clipSelectionSpinner.setEnabled(true);
            if (selectedMediaIndex >= mediaUris.size()) selectedMediaIndex = mediaUris.size() - 1;
        }
        adapter.notifyDataSetChanged();
        clipSelectionSpinner.setSelection(selectedMediaIndex);
        refreshTimeline();
        if (!mediaUris.isEmpty()) loadClipTransformControls();
    }

    private void refreshTimeline() {
        if (timelineTrack == null) return;
        timelineTrack.removeAllViews();
        for (int i = 0; i < mediaUris.size(); i++) {
            final int position = i;
            String name = displayName(mediaUris.get(i));
            if (name.length() > 17) name = name.substring(0, 16) + "…";
            TextView clip = text((i + 1) + "\n" + name, 12,
                    i == selectedMediaIndex ? BG : WHITE, true);
            clip.setGravity(Gravity.CENTER);
            clip.setPadding(dp(8), dp(6), dp(8), dp(6));
            clip.setBackgroundColor(i == selectedMediaIndex ? GOLD : Color.rgb(37, 39, 47));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(120), -1);
            itemParams.setMargins(0, 0, dp(8), 0);
            timelineTrack.addView(clip, itemParams);
            clip.setOnClickListener(v -> selectTimelineClip(position));
        }
    }

    private void selectTimelineClip(int position) {
        if (position < 0 || position >= mediaUris.size()) return;
        selectedMediaIndex = position;
        clipSelectionSpinner.setSelection(position);
        refreshTimeline();
        loadClipTransformControls();
        previewFirstMedia();
    }

    private void moveSelectedClip(int offset) {
        int destination = selectedMediaIndex + offset;
        if (selectedMediaIndex < 0 || selectedMediaIndex >= mediaUris.size()
                || destination < 0 || destination >= mediaUris.size()) return;
        Uri movedUri = mediaUris.get(selectedMediaIndex);
        mediaUris.set(selectedMediaIndex, mediaUris.get(destination));
        mediaUris.set(destination, movedUri);
        VideoClipTransform movedTransform = clipTransforms.get(selectedMediaIndex);
        clipTransforms.set(selectedMediaIndex, clipTransforms.get(destination));
        clipTransforms.set(destination, movedTransform);
        selectedMediaIndex = destination;
        updateMediaList();
        loadClipTransformControls();
        previewFirstMedia();
        setStatus("تم تغيير ترتيب المقطع في الخط الزمني.", GOLD);
    }

    private void removeSelectedClip() {
        if (selectedMediaIndex < 0 || selectedMediaIndex >= mediaUris.size()) return;
        stopVideo();
        mediaUris.remove(selectedMediaIndex);
        clipTransforms.remove(selectedMediaIndex);
        if (selectedMediaIndex >= mediaUris.size()) selectedMediaIndex = Math.max(0, mediaUris.size() - 1);
        updateMediaList();
        if (mediaUris.isEmpty()) {
            clearPreview();
            setStatus("حُذف المقطع. أضف صورة أو فيديو للمتابعة.", MUTED);
        } else {
            loadClipTransformControls();
            previewFirstMedia();
            setStatus("تم حذف المقطع المحدد من الخط الزمني.", GOLD);
        }
    }

    private void updatePreviewAspectRatio() {
        if (previewHost == null || previewFrame == null) return;
        int hostWidth = previewHost.getWidth();
        int hostHeight = previewHost.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0) return;

        float aspectRatio = selectedCanvasPreset().getAspectRatio();
        int frameWidth;
        int frameHeight;
        if ((float) hostWidth / hostHeight > aspectRatio) {
            frameHeight = hostHeight;
            frameWidth = Math.round(frameHeight * aspectRatio);
        } else {
            frameWidth = hostWidth;
            frameHeight = Math.round(frameWidth / aspectRatio);
        }
        ViewGroup.LayoutParams current = previewFrame.getLayoutParams();
        if (current.width != frameWidth || current.height != frameHeight) {
            previewFrame.setLayoutParams(new FrameLayout.LayoutParams(frameWidth, frameHeight, Gravity.CENTER));
        }
    }

    private void showExportErrorDetails(Exception error) {
        boolean fullHd = resolutionSpinner != null && resolutionSpinner.getSelectedItemPosition() > 0;
        int[] outputSize = selectedCanvasPreset().outputSize(fullHd);
        StringBuilder report = new StringBuilder("نسخة التطبيق: ").append(BuildConfig.VERSION_NAME)
                .append("\nإصدار Android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(')')
                .append("\nعدد ملفات الوسائط: ").append(mediaUris.size())
                .append("\nتعليق صوتي: ").append(audioUri == null ? "لا" : "نعم")
                .append("\nموسيقى خلفية: ").append(musicUri == null ? "لا" : "نعم");
        report.append("\nلوحة الإخراج: ").append(selectedCanvasPreset().getLabel())
                .append(" — ").append(outputSize[0]).append('×').append(outputSize[1]);
        for (int i = 0; i < clipTransforms.size(); i++) {
            VideoClipTransform transform = clipTransforms.get(i);
            report.append("\nالمقطع ").append(i + 1).append(": ")
                    .append(transform.getFitMode() == VideoClipTransform.FitMode.FIT ? "FIT" : "FILL")
                    .append(", zoom=").append(Math.round(transform.getZoom() * 100f)).append('%')
                    .append(", crop=").append(Math.round(transform.getTrimStartFraction() * 100f))
                    .append('-').append(Math.round(transform.getTrimEndFraction() * 100f)).append('%')
                    .append(", filter=").append(transform.getFilterPreset().getLabel())
                    .append(' ').append(Math.round(transform.getFilterIntensity() * 100f)).append('%');
        }
        String details = report.append("\n\n").append(VideoExportDiagnostics.format(error)).toString();
        DiagnosticStore.recordReport(this, "تصدير الفيديو", details);
        TextView body = text(details, 12, WHITE, false);
        body.setTextIsSelectable(true);
        body.setTextDirection(View.TEXT_DIRECTION_LTR);
        body.setGravity(Gravity.LEFT);
        body.setPadding(dp(10), dp(8), dp(10), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(4), dp(8), dp(4));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(360)));
        new AlertDialog.Builder(this)
                .setTitle("تفاصيل خطأ إنشاء MP4")
                .setView(panel)
                .setPositiveButton("نسخ التقرير", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                "World News video export diagnostics", details));
                        android.widget.Toast.makeText(this, "تم نسخ تقرير الخطأ.",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("فتح شاشة التشخيص", (dialog, which) ->
                        startActivity(new Intent(this, AppDiagnosticsActivity.class)))
                .setNegativeButton("إغلاق", null)
                .show();
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

    private void addEditorToolTab(LinearLayout parent, String label, ScrollView scroll, View target) {
        Button tab = secondaryButton(label);
        tab.setTextSize(12);
        tab.setMinHeight(dp(40));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(dp(72), dp(42));
        tabParams.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(tab, tabParams);
        tab.setOnClickListener(v -> scrollToEditorControl(scroll, target));
    }

    private void scrollToEditorControl(ScrollView scroll, View target) {
        if (scroll == null || target == null) return;
        int[] controlLocation = new int[2];
        int[] scrollLocation = new int[2];
        target.getLocationOnScreen(controlLocation);
        scroll.getLocationOnScreen(scrollLocation);
        scroll.smoothScrollTo(0, Math.max(0, controlLocation[1] - scrollLocation[1] - dp(12)));
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
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, MutableSpinnerItems.copyOf(values)) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(WHITE);
                view.setPadding(dp(12), dp(7), dp(12), dp(7));
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(WHITE);
                view.setBackgroundColor(CARD);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                return view;
            }
        };
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

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        ArrayList<String> savedUris = new ArrayList<>();
        for (Uri uri : mediaUris) savedUris.add(uri.toString());
        state.putStringArrayList("editor.mediaUris", savedUris);
        float[] transforms = new float[clipTransforms.size() * 8];
        for (int i = 0; i < clipTransforms.size(); i++) {
            VideoClipTransform t = clipTransforms.get(i);
            int offset = i * 8;
            transforms[offset] = t.getFitMode() == VideoClipTransform.FitMode.FIT ? 0 : 1;
            transforms[offset + 1] = t.getZoom();
            transforms[offset + 2] = t.getPositionX();
            transforms[offset + 3] = t.getPositionY();
            transforms[offset + 4] = t.getFilterPreset().ordinal();
            transforms[offset + 5] = t.getFilterIntensity();
            transforms[offset + 6] = t.getTrimStartFraction();
            transforms[offset + 7] = t.getTrimEndFraction();
        }
        state.putFloatArray("editor.transforms", transforms);
        state.putInt("editor.selected", selectedMediaIndex);
        state.putString("editor.title", titleInput.getText().toString());
        state.putString("editor.caption", captionInput.getText().toString());
        state.putString("editor.ticker", tickerInput.getText().toString());
        state.putInt("editor.format", formatSpinner.getSelectedItemPosition());
        state.putInt("editor.template", templateModeSpinner.getSelectedItemPosition());
        state.putInt("editor.palette", colorPaletteSpinner.getSelectedItemPosition());
        state.putInt("editor.imageDuration", imageDurationSpinner.getSelectedItemPosition());
        state.putInt("editor.resolution", resolutionSpinner.getSelectedItemPosition());
        state.putInt("editor.transition", transitionModeSpinner.getSelectedItemPosition());
        state.putInt("editor.transitionDuration", transitionDurationSpinner.getSelectedItemPosition());
        if (audioUri != null) state.putString("editor.audio", audioUri.toString());
        if (musicUri != null) state.putString("editor.music", musicUri.toString());
    }

    @Override protected void onDestroy() {
        if (exporting && exportEngine != null) {
            try { exportEngine.cancel(); } catch (Exception ignored) { }
        }
        stopVideo();
        if (audioTester != null) { audioTester.release(); audioTester = null; }
        super.onDestroy();
    }
}
