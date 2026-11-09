package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.AudioAttributes;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextWatcher;
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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskAssistantActivity extends Activity {
    public static final String EXTRA_RADAR_TOPIC = "com.threadsdocumentarynews.app.RADAR_TOPIC";
    public static final String EXTRA_RADAR_SOURCE = "com.threadsdocumentarynews.app.RADAR_SOURCE";
    public static final String EXTRA_RADAR_CONTEXT = "com.threadsdocumentarynews.app.RADAR_CONTEXT";
    private static final String RUNS_URL = "https://github.com/sozanhnedy-bot/-/actions/workflows/ai-task.yml";
    private static final String ACTIONS_URL = "https://github.com/sozanhnedy-bot/-/actions";
    private static final int BACKGROUND = Color.rgb(8, 9, 13);
    private static final int SURFACE = Color.rgb(20, 21, 27);
    private static final int SURFACE_ALT = Color.rgb(27, 28, 35);
    private static final int GOLD = Color.rgb(255, 187, 54);
    private static final int GOLD_DARK = Color.rgb(203, 133, 25);
    private static final int TEXT = Color.rgb(248, 247, 244);
    private static final int MUTED = Color.rgb(164, 164, 171);
    private static final int LINE = Color.rgb(54, 55, 64);
    private static final int CREATE_PDF_REQUEST = 4101;
    private static final int CREATE_AUDIO_REQUEST = 4102;
    private static final int PICK_SOCIAL_IMAGE_REQUEST = 4103;
    private static final int SAVE_SOCIAL_IMAGE_REQUEST = 4104;
    private static final String TEST_VOICE_TEXT = "مرحبًا، هذا اختبار للصوت العربي في تطبيق أخبار العالم.";
    private static final String[] TTS_VOICE_IDS = {"Zephyr", "Kore", "Aoede", "Achernar", "Achird", "Charon", "Puck", "Rasalgethi"};
    private static final String[] TTS_VOICE_LABELS = {"Zephyr — نسائي، مشرق", "Kore — نسائي، ثابت", "Aoede — نسائي، هادئ", "Achernar — نسائي، ناعم", "Achird — رجالي، ودود", "Charon — رجالي، إخباري", "Puck — رجالي، حيوي", "Rasalgethi — رجالي، واضح"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout pageContent;
    private ScrollView scroll;
    private EditText topicInput;
    private EditText referenceInput;
    private EditText scriptOutput;
    private EditText socialDraftInput;
    private EditText socialHeadlineInput;
    private Spinner durationSpinner;
    private Spinner platformSpinner;
    private Spinner styleSpinner;
    private Spinner voiceSpinner;
    private Button generateButton;
    private Button voiceModelButton;
    private Button testGeminiButton;
    private Button testNabraButton;
    private Button previewButton;
    private Button continueWithVoiceButton;
    private Button continueWithoutVoiceButton;
    private Button pdfButton;
    private Button exportAudioButton;
    private Button generateSocialPostButton;
    private Button renderSocialImageButton;
    private Button saveSocialImageButton;
    private Button shareSocialPostButton;
    private TextView status;
    private TextView voiceTestStatus;
    private TextView socialStatus;
    private ImageView socialImagePreview;
    private TextView workflowStatus;
    private Button refreshWorkflowButton;
    private MediaPlayer mediaPlayer;
    private LinearLayout scriptOutputCard;
    private String pendingPdfText = "";
    private String pendingPdfTitle = "سكريبت World News";
    private String pendingAudioPath = "";
    private String lastVoicePath = "";
    private String lastVoiceText = "";
    private String lastVoiceSelection = "";
    private String lastVoiceProvider = "";
    private Uri socialPhotoUri;
    private Uri savedSocialImageUri;
    private Uri renderedSocialPhotoUri;
    private String renderedSocialHeadline = "";
    private Bitmap socialPostBitmap;
    private int selectedTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTitle("World News — مساعد الإنتاج");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(9), dp(20), dp(9));
        header.setBackgroundColor(BACKGROUND);
        TextView brand = text("أخبار العالم  ·  World News", 17, TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView spark = text("✦", 24, GOLD, true);
        spark.setGravity(Gravity.CENTER);
        spark.setBackground(rounded(SURFACE_ALT, LINE, 20));
        header.addView(spark, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(dp(18), dp(14), dp(18), dp(24));
        scroll.addView(pageContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildBottomNav());
        setContentView(root);
        showTab(0);
        String radarTopic = getIntent().getStringExtra(EXTRA_RADAR_TOPIC);
        if (radarTopic != null && !radarTopic.trim().isEmpty() && topicInput != null) {
            topicInput.setText(radarTopic.trim());
            if (scriptOutput != null) scriptOutput.setText("");
            lastVoicePath = "";
            lastVoiceText = "";
            lastVoiceSelection = "";
            lastVoiceProvider = "";
            if (exportAudioButton != null) exportAudioButton.setEnabled(false);
            String source = getIntent().getStringExtra(EXTRA_RADAR_SOURCE);
            status.setText("أُدرج موضوع من الرادار" + (source == null ? "" : " — " + source) + ". راجعه ثم اضغط إنشاء مسودة السكريبت.");
            status.setTextColor(GOLD);
            String context = getIntent().getStringExtra(EXTRA_RADAR_CONTEXT);
            if (referenceInput != null && ((source != null && !source.isEmpty()) || (context != null && !context.isEmpty()))) {
                referenceInput.setText("مصدر الموضوع: " + (source == null ? "الرادار" : source)
                        + (context == null || context.isEmpty() ? "" : "\n" + context)
                        + "\nهذه بيانات سياق أولية وليست تحققًا مستقلًا من الوقائع.");
            }
        }
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(5, 6, 9));
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.addView(navItem("✧\nالمساعد", 0), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("▱\nالمشاريع", 1), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("◷\nالحالة", 2), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("◎\nالنشر", 3), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("▷\nالفيديو", 4), new LinearLayout.LayoutParams(0, dp(58), 1));
        return nav;
    }

    private TextView navItem(String caption, int tab) {
        TextView item = text(caption, 12, selectedTab == tab ? GOLD : MUTED, selectedTab == tab);
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(v -> {
            if (tab == 4) startActivity(new Intent(this, VideoStudioActivity.class));
            else showTab(tab);
        });
        return item;
    }

    private void showTab(int tab) {
        if (selectedTab == 0 && topicInput != null && scriptOutput != null && !scriptOutput.getText().toString().trim().isEmpty()) {
            saveProject(false);
        }
        selectedTab = tab;
        pageContent.removeAllViews();
        if (tab == 0) buildAssistantPage();
        else if (tab == 1) buildProjectsPage();
        else if (tab == 2) buildWorkflowStatusPage();
        else buildSocialPage();
        scroll.scrollTo(0, 0);
    }

    private void buildAssistantPage() {
        addHero();
        LinearLayout card = card();
        addText(card, "ابدأ بفكرة الفيديو", 21, TEXT, true, 0, 5);
        addText(card, "اكتب الفكرة واضغط إنشاء. التفاصيل الأخرى اختيارية وتقدر تغيّرها وقت ما تحتاج.", 14, MUTED, false, 0, 12);

        addLabel(card, "عن إيه الفيديو؟");
        topicInput = new EditText(this);
        topicInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        topicInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        topicInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        topicInput.setHintTextColor(Color.rgb(128, 129, 137));
        topicInput.setTextColor(TEXT);
        topicInput.setHint("اكتب فكرتك بكلمات بسيطة…");
        topicInput.setTextSize(16);
        topicInput.setMinLines(3);
        topicInput.setMaxLines(5);
        topicInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        topicInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        card.addView(topicInput, params(-1, -2, 0, 0, 0, 10));

        Button optionsToggle = secondaryButton("إعدادات إضافية  ·  المدة والمنصة والنبرة");
        card.addView(optionsToggle, params(-1, dp(46), 0, 0, 0, 6));
        LinearLayout advancedOptions = new LinearLayout(this);
        advancedOptions.setOrientation(LinearLayout.VERTICAL);
        advancedOptions.setVisibility(View.GONE);
        optionsToggle.setOnClickListener(v -> {
            boolean opening = advancedOptions.getVisibility() != View.VISIBLE;
            advancedOptions.setVisibility(opening ? View.VISIBLE : View.GONE);
            optionsToggle.setText(opening ? "إخفاء الإعدادات الإضافية" : "إعدادات إضافية  ·  المدة والمنصة والنبرة");
        });

        addLabel(advancedOptions, "منصة الفيديو ونسبة العرض");
        platformSpinner = makeSpinner(new String[]{"TikTok / Reels — عمودي 9:16", "YouTube Shorts — عمودي 9:16", "فيديو أفقي — 16:9"});
        advancedOptions.addView(platformSpinner, params(-1, dp(48), 0, 0, 0, 5));

        addLabel(advancedOptions, "مدة الفيديو");
        durationSpinner = makeSpinner(new String[]{"30 ثانية", "60 ثانية", "3 دقائق"});
        advancedOptions.addView(durationSpinner, params(-1, dp(48), 0, 0, 0, 5));

        addLabel(advancedOptions, "نبرة النص");
        styleSpinner = makeSpinner(new String[]{"وثائقي متزن", "حماسي", "خبري مباشر", "قصصي مشوق"});
        advancedOptions.addView(styleSpinner, params(-1, dp(48), 0, 0, 0, 5));

        addLabel(advancedOptions, "مراجع أو معلومات — اختياري");
        referenceInput = new EditText(this);
        referenceInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        referenceInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        referenceInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        referenceInput.setHintTextColor(Color.rgb(128, 129, 137));
        referenceInput.setTextColor(TEXT);
        referenceInput.setHint("أضف حقائق أو مقتطفات تريد أن يلتزم بها Gemini.");
        referenceInput.setTextSize(14);
        referenceInput.setMinLines(2);
        referenceInput.setMaxLines(4);
        referenceInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        referenceInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        advancedOptions.addView(referenceInput, params(-1, -2, 0, 0, 0, 10));
        card.addView(advancedOptions, params(-1, -2, 0, 0, 0, 5));

        generateButton = primaryButton("إنشاء مسودة السكريبت");
        card.addView(generateButton, params(-1, dp(58), 0, dp(8), 0, 4));
        generateButton.setOnClickListener(v -> generateScript());

        status = text("سيطلب التطبيق موافقتك قبل إرسال الفكرة إلى Gemini.", 13, GOLD, false);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(status, params(-1, -2, 0, dp(6), 0, 0));
        pageContent.addView(card);

        LinearLayout voiceTestCard = card();
        addText(voiceTestCard, "اختبار الصوت", 19, TEXT, true, 0, 5);
        addText(voiceTestCard, "اختبر الصوت قبل كتابة أي سكريبت. يستخدم الاختبار عبارة قصيرة ويشغّلها فعليًا.", 13, MUTED, false, 0, 8);
        voiceSpinner = makeSpinner(TTS_VOICE_LABELS);
        voiceTestCard.addView(voiceSpinner, params(-1, dp(48), 0, 0, 0, 7));
        testGeminiButton = secondaryButton("اختبار صوت Gemini الآن");
        voiceTestCard.addView(testGeminiButton, params(-1, dp(48), 0, 0, 0, 6));
        testGeminiButton.setOnClickListener(v -> confirmGeminiVoiceTest());
        if (BuildConfig.DEBUG) {
            Button debugTokenButton = secondaryButton("إظهار رمز App Check Debug لهذا الجهاز");
            voiceTestCard.addView(debugTokenButton, params(-1, dp(48), 0, 0, 0, 6));
            debugTokenButton.setOnClickListener(v -> showAppCheckDebugToken());
        }
        testNabraButton = secondaryButton("اختبار صوت Nabra المحلي");
        voiceTestCard.addView(testNabraButton, params(-1, dp(48), 0, 0, 0, 6));
        testNabraButton.setOnClickListener(v -> testNabraVoice());
        voiceModelButton = secondaryButton(LocalAiMedia.voiceReady(this) ? "Nabra جاهز — محلي ومجاني" : "تنزيل Nabra الاحتياطي المجاني (نحو 83 ميغابايت)");
        voiceTestCard.addView(voiceModelButton, params(-1, dp(48), 0, 0, 0, 7));
        voiceModelButton.setOnClickListener(v -> {
            if (LocalAiMedia.voiceReady(this)) setVoiceTestResult("Nabra جاهز للاختبار المحلي دون إنترنت.", GOLD);
            else confirmVoiceDownload();
        });
        voiceTestStatus = text("لم يبدأ الاختبار بعد. اختر صوتًا ثم اختبر Gemini أو Nabra.", 13, MUTED, false);
        voiceTestStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        voiceTestStatus.setBackground(rounded(SURFACE_ALT, LINE, 12));
        voiceTestCard.addView(voiceTestStatus, params(-1, -2, 0, 0, 0, 0));
        pageContent.addView(voiceTestCard, params(-1, -2, 0, dp(12), 0, 0));

        LinearLayout outputCard = card();
        scriptOutputCard = outputCard;
        addText(outputCard, "مسودة السكريبت وتقسيم المشاهد", 17, TEXT, true, 0, 7);
        scriptOutput = new EditText(this);
        scriptOutput.setGravity(Gravity.TOP | Gravity.RIGHT);
        scriptOutput.setTextDirection(View.TEXT_DIRECTION_RTL);
        scriptOutput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        scriptOutput.setTextColor(TEXT);
        scriptOutput.setHintTextColor(Color.rgb(128, 129, 137));
        scriptOutput.setHint("سيظهر هنا عنوان الفيديو، نص الراوي، وتقسيم المشاهد مع وصف بصري لكل مشهد.");
        scriptOutput.setTextSize(14);
        scriptOutput.setMinLines(9);
        scriptOutput.setMaxLines(26);
        scriptOutput.setPadding(dp(14), dp(12), dp(14), dp(12));
        scriptOutput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        outputCard.addView(scriptOutput, params(-1, -2, 0, 0, 0, 10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button save = secondaryButton("حفظ المشروع");
        row.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button share = secondaryButton("نسخ النص");
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        shareParams.setMargins(dp(8), 0, 0, 0);
        row.addView(share, shareParams);
        outputCard.addView(row, params(-1, -2, 0, 0, 0, 8));
        save.setOnClickListener(v -> saveProject(true));
        share.setOnClickListener(v -> copyScript());

        continueWithVoiceButton = primaryButton("إنشاء صوت الراوي ثم فتح استوديو الفيديو");
        outputCard.addView(continueWithVoiceButton, params(-1, dp(54), 0, 0, 0, 6));
        continueWithVoiceButton.setOnClickListener(v -> continueToVideoStudio(false));
        continueWithoutVoiceButton = secondaryButton("فتح الاستوديو بدون تعليق صوتي");
        outputCard.addView(continueWithoutVoiceButton, params(-1, dp(48), 0, 0, 0, 8));
        continueWithoutVoiceButton.setOnClickListener(v -> continueToVideoStudio(true));

        addText(outputCard, "الصوت الذكي", 16, TEXT, true, 0, 5);
        addText(outputCard, "استخدم الاختبار المستقل أعلاه للتأكد من الصوت، أو أنشئ معاينة لنص الراوي في هذا السكريبت.", 13, MUTED, false, 0, 8);

        previewButton = secondaryButton("▷  إنشاء ومعاينة صوت Gemini");
        outputCard.addView(previewButton, params(-1, dp(50), 0, 0, 0, 6));
        previewButton.setOnClickListener(v -> previewVoice());

        exportAudioButton = secondaryButton("حفظ التعليق الصوتي WAV");
        exportAudioButton.setEnabled(false);
        outputCard.addView(exportAudioButton, params(-1, dp(50), 0, 0, 0, 6));
        exportAudioButton.setOnClickListener(v -> exportAudio());

        pdfButton = secondaryButton("⇩  تصدير المشروع PDF");
        outputCard.addView(pdfButton, params(-1, dp(50), 0, 0, 0, 0));
        pdfButton.setOnClickListener(v -> exportPdf());
        pageContent.addView(outputCard, params(-1, -2, 0, dp(15), 0, 0));

        loadSavedProject();
        refreshModelButtons();
    }

    private void continueToVideoStudio(boolean withoutVoice) {
        String script = scriptOutput == null ? "" : scriptOutput.getText().toString().trim();
        if (script.isEmpty()) {
            setStatus("أنشئ أو اكتب السكريبت أولًا، ثم أرسله إلى استوديو الفيديو.", Color.rgb(255, 130, 115));
            return;
        }
        String topic = topicInput == null ? "" : topicInput.getText().toString().trim();
        if (withoutVoice) {
            launchVideoStudio(topic, script, "");
            return;
        }
        String speech = narrationForSpeech(script);
        if (speech.length() < 20) {
            setStatus("لا يوجد نص راوٍ كافٍ لإنشاء الصوت. أضف التعليق الصوتي الكامل أو اختر المتابعة بدون صوت.", Color.rgb(255, 130, 115));
            return;
        }
        String voice = selectedGeminiVoice();
        if (!lastVoicePath.isEmpty() && new java.io.File(lastVoicePath).isFile()
                && lastVoiceText.equals(speech) && lastVoiceSelection.equals(voice)
                && !lastVoiceProvider.contains("اختبار")) {
            launchVideoStudio(topic, script, lastVoicePath);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إنشاء التعليق الصوتي قبل الفيديو؟")
                .setMessage("سيُرسل نص الراوي إلى Gemini TTS، ثم يفتح التطبيق استوديو الفيديو والصوت مرفقًا تلقائيًا. لو تعذر Gemini، سيستخدم Nabra إذا كان قد تم تنزيله. يمكنك إلغاء أو اختيار «فتح الاستوديو بدون تعليق صوتي».")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("أنشئ الصوت وافتح الفيديو", (dialog, which) -> createVoiceThenOpenStudio(topic, script, speech, voice))
                .show();
    }

    private void createVoiceThenOpenStudio(String topic, String script, String speech, String voice) {
        if (continueWithVoiceButton != null) continueWithVoiceButton.setEnabled(false);
        setStatus("يجري إنشاء التعليق الصوتي؛ سيفتح استوديو الفيديو تلقائيًا بعد نجاحه…", GOLD);
        executor.execute(() -> {
            try {
                String audioPath;
                String provider;
                try {
                    audioPath = GeminiSpeech.generateWavBlocking(this, speech, voice);
                    provider = "Gemini · " + voice;
                } catch (Exception geminiError) {
                    if (!LocalAiMedia.voiceReady(this)) throw geminiError;
                    mainHandler.post(() -> setStatus("Gemini لم يعمل؛ يجري استخدام Nabra المحلي…", GOLD));
                    audioPath = LocalAiMedia.synthesizeArabic(this, speech);
                    provider = "Nabra المحلي";
                }
                final String finalAudioPath = audioPath;
                final String finalProvider = provider;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    lastVoicePath = finalAudioPath;
                    lastVoiceText = speech;
                    lastVoiceSelection = voice;
                    lastVoiceProvider = finalProvider;
                    if (exportAudioButton != null) exportAudioButton.setEnabled(true);
                    if (continueWithVoiceButton != null) continueWithVoiceButton.setEnabled(true);
                    launchVideoStudio(topic, script, finalAudioPath);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (continueWithVoiceButton != null) continueWithVoiceButton.setEnabled(true);
                    setStatus("لم ينجح إنشاء الصوت، لذلك لم أفتح الفيديو بصوت ناقص: " + userFriendlyAiError(error)
                            + (LocalAiMedia.voiceReady(this) ? "" : " — نزّل Nabra من بطاقة اختبار الصوت أو اختر فتح الاستوديو بدون تعليق صوتي."), Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private void launchVideoStudio(String topic, String script, String audioPath) {
        Intent intent = new Intent(this, VideoStudioActivity.class);
        intent.putExtra(VideoStudioActivity.EXTRA_TREND_TITLE, topic);
        intent.putExtra(VideoStudioActivity.EXTRA_SCRIPT_TEXT, script);
        String source = getIntent().getStringExtra(EXTRA_RADAR_SOURCE);
        if (source != null) intent.putExtra(VideoStudioActivity.EXTRA_TREND_SOURCE, source);
        if (audioPath != null && !audioPath.isEmpty()) intent.putExtra(VideoStudioActivity.EXTRA_AUDIO_PATH, audioPath);
        startActivity(intent);
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(20), dp(22), dp(20));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(41, 31, 19), Color.rgb(24, 20, 34), Color.rgb(12, 13, 18)});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), LINE);
        hero.setBackground(bg);
        TextView chips = text("TikTok   ·   Reels   ·   فيديو وثائقي", 12, GOLD, true);
        chips.setPadding(0, 0, 0, dp(12));
        hero.addView(chips);
        addText(hero, "فكرتك تتحول\nلسكريبت ومشاهد", 25, TEXT, true, 0, 8);
        addText(hero, "Gemini للكتابة والصوت، مع Nabra المحلي كبديل صوتي مجاني عند تعذر الخدمة.", 14, MUTED, false, 0, 0);
        pageContent.addView(hero, params(-1, -2, 0, 0, 0, 16));
    }

    private void buildProjectsPage() {
        addText(pageContent, "آخر مشروع محفوظ", 22, TEXT, true, 0, 5);
        addText(pageContent, "يُحفظ السكريبت والمراجع والإعدادات على هذا الهاتف فقط، ويمكنك الرجوع إليها وتعديلها أو تصديرها.", 14, MUTED, false, 0, 15);
        LinearLayout saved = card();
        String topic = getPreferences(MODE_PRIVATE).getString("last_media_topic", "");
        String script = getPreferences(MODE_PRIVATE).getString("last_media_script", "");
        addText(saved, topic.isEmpty() ? "لا توجد مشاريع محفوظة بعد" : topic, 17, TEXT, true, 0, 7);
        addText(saved, script.isEmpty() ? "أنشئ أول سكريبت من تبويب المساعد." : "آخر مشروع محفوظ على هذا الهاتف.", 13, MUTED, false, 0, 10);
        Button open = secondaryButton(topic.isEmpty() ? "إنشاء مشروع جديد" : "فتح آخر مشروع للتعديل");
        saved.addView(open, params(-1, dp(50), 0, dp(5), 0, 0));
        open.setOnClickListener(v -> showTab(0));
        pageContent.addView(saved);

        LinearLayout github = card();
        addText(github, "مهمة تعديل الموقع عبر GitHub", 17, TEXT, true, 0, 5);
        addText(github, "هذا مسار منفصل عن كتابة الفيديو المحلية. افتح نموذج GitHub Actions وسجّل الدخول بحساب يملك صلاحية تشغيل المهام على المستودع، ثم اختر Run workflow. لا تلصق PAT أو مفتاحًا في التطبيق؛ مفتاح خدمة الموقع يبقى محفوظًا في أسرار GitHub Actions.", 13, MUTED, false, 0, 10);
        Button dispatch = primaryButton("فتح نموذج إرسال مهمة الموقع");
        github.addView(dispatch, params(-1, dp(50), 0, dp(5), 0, 6));
        dispatch.setOnClickListener(v -> openUrl(RUNS_URL, "تعذر فتح نموذج GitHub Actions."));
        Button runs = secondaryButton("عرض سجل تشغيلات GitHub Actions");
        github.addView(runs, params(-1, dp(48), 0, 0, 0, 0));
        runs.setOnClickListener(v -> openUrl(ACTIONS_URL, "تعذر فتح سجل GitHub Actions."));
        pageContent.addView(github, params(-1, -2, 0, dp(14), 0, 0));
    }

    private void buildWorkflowStatusPage() {
        addText(pageContent, "حالة العمل", 24, TEXT, true, 0, 5);
        addText(pageContent, "متابعة بناء Android والأخطاء الأخيرة من داخل التطبيق.", 14, MUTED, false, 0, 14);

        LinearLayout health = card();
        addText(health, "جاهزية هذا الهاتف", 17, TEXT, true, 0, 8);
        boolean firebaseConfigured = !BuildConfig.FIREBASE_API_KEY.isBlank() && !BuildConfig.FIREBASE_APP_ID.isBlank() && !BuildConfig.FIREBASE_PROJECT_ID.isBlank();
        addText(health, (firebaseConfigured ? "✓" : "○") + "  كتابة السكريبت: Gemini عبر Firebase" + (firebaseConfigured ? " — الإعداد موجود" : " — إعداد Firebase ناقص"), 14, firebaseConfigured ? GOLD : MUTED, false, 0, 6);
        addText(health, (LocalAiMedia.voiceReady(this) ? "✓" : "○") + "  Nabra الاحتياطي: " + (LocalAiMedia.voiceReady(this) ? "جاهز دون إنترنت" : "غير منزّل؛ Gemini هو المسار الأول"), 14, LocalAiMedia.voiceReady(this) ? GOLD : MUTED, false, 0, 6);
        String savedTopic = getPreferences(MODE_PRIVATE).getString("last_media_topic", "");
        addText(health, (savedTopic.isEmpty() ? "○ لا يوجد مشروع محفوظ" : "✓ آخر مشروع محفوظ: " + savedTopic), 14, savedTopic.isEmpty() ? MUTED : GOLD, false, 0, 0);
        pageContent.addView(health, params(-1, -2, 0, 0, 0, 12));

        LinearLayout runs = card();
        addText(runs, "بناء التطبيق على GitHub", 17, TEXT, true, 0, 6);
        addText(runs, "تُعرض حالة آخر تشغيل وخطوة الفشل إن وجدت. هذه قراءة فقط؛ لا تُرسل بيانات دخول GitHub من الهاتف.", 13, MUTED, false, 0, 10);
        workflowStatus = text("اضغط تحديث لقراءة حالة آخر عمليات بناء Android.", 14, TEXT, false);
        workflowStatus.setGravity(Gravity.RIGHT | Gravity.TOP);
        workflowStatus.setPadding(dp(13), dp(13), dp(13), dp(13));
        workflowStatus.setBackground(rounded(SURFACE_ALT, LINE, 16));
        runs.addView(workflowStatus, params(-1, -2, 0, 0, 0, 10));
        refreshWorkflowButton = primaryButton("تحديث حالة البناء");
        runs.addView(refreshWorkflowButton, params(-1, dp(50), 0, 0, 0, 0));
        refreshWorkflowButton.setOnClickListener(v -> refreshWorkflowStatus());
        pageContent.addView(runs, params(-1, -2, 0, 0, 0, 12));

        LinearLayout repair = card();
        addText(repair, "إصلاح الأخطاء", 17, TEXT, true, 0, 6);
        addText(repair, "كاتب السكريبت ومولد صوت Gemini يحتاجان اتصال إنترنت وحصة مجانية متاحة. عند تعذر صوت Gemini، يحاول التطبيق استخدام Nabra محليًا إذا كان منزّلًا.", 13, MUTED, false, 0, 0);
        pageContent.addView(repair, params(-1, -2, 0, 0, 0, 0));
        refreshWorkflowStatus();
    }

    private void refreshWorkflowStatus() {
        if (workflowStatus == null) return;
        workflowStatus.setText("جارٍ جلب حالة بناء Android…");
        if (refreshWorkflowButton != null) refreshWorkflowButton.setEnabled(false);
        executor.execute(() -> {
            String result;
            try {
                String json = readGitHubJson("https://api.github.com/repos/sozanhnedy-bot/-/actions/runs?per_page=20");
                JSONObject root = new JSONObject(json);
                JSONArray allRuns = root.optJSONArray("workflow_runs");
                StringBuilder out = new StringBuilder();
                int matched = 0;
                if (allRuns != null) {
                    for (int i = 0; i < allRuns.length() && matched < 5; i++) {
                        JSONObject run = allRuns.optJSONObject(i);
                        if (run == null) continue;
                        String path = run.optString("path", "");
                        String name = run.optString("name", "");
                        if (!path.endsWith("android-apk.yml") && !path.endsWith("android-build.yml") && !name.toLowerCase(java.util.Locale.ROOT).contains("android")) continue;
                        matched++;
                        String conclusion = run.optString("conclusion", "");
                        String statusValue = run.optString("status", "unknown");
                        out.append(matched).append(". ").append(name).append("\n");
                        out.append("الحالة: ").append(arabicRunState(statusValue, conclusion)).append("\n");
                        out.append("الفرع: ").append(run.optString("head_branch", "—")).append("  ·  ")
                                .append(run.optString("event", "")).append("\n");
                        out.append("بدأ: ").append(run.optString("created_at", "غير متاح")).append("\n");
                        if ("failure".equals(conclusion)) {
                            String failedSteps = fetchFailedSteps(run.optLong("id", 0));
                            out.append("الخطأ: فشل التشغيل");
                            if (!failedSteps.isEmpty()) out.append(" — ").append(failedSteps);
                            out.append("\n");
                        }
                        out.append("\n");
                    }
                }
                result = matched == 0 ? "لم أجد تشغيلات Android حديثة." : out.toString().trim();
            } catch (Exception error) {
                result = "تعذّر قراءة الحالة: " + (error.getMessage() == null ? "تحقق من اتصال الإنترنت." : error.getMessage());
            }
            final String display = result;
            mainHandler.post(() -> {
                if (workflowStatus != null) workflowStatus.setText(display);
                if (refreshWorkflowButton != null) refreshWorkflowButton.setEnabled(true);
            });
        });
    }

    private String fetchFailedSteps(long runId) {
        if (runId <= 0) return "";
        try {
            JSONObject response = new JSONObject(readGitHubJson("https://api.github.com/repos/sozanhnedy-bot/-/actions/runs/" + runId + "/jobs?per_page=50"));
            JSONArray jobs = response.optJSONArray("jobs");
            StringBuilder failed = new StringBuilder();
            if (jobs == null) return "";
            for (int i = 0; i < jobs.length(); i++) {
                JSONObject job = jobs.optJSONObject(i);
                if (job == null) continue;
                JSONArray steps = job.optJSONArray("steps");
                if (steps == null) continue;
                for (int j = 0; j < steps.length(); j++) {
                    JSONObject step = steps.optJSONObject(j);
                    if (step != null && "failure".equals(step.optString("conclusion"))) {
                        if (failed.length() > 0) failed.append(", ");
                        failed.append(step.optString("name", "خطوة غير معروفة"));
                    }
                }
            }
            return failed.toString();
        } catch (Exception ignored) {
            return "تعذر جلب اسم خطوة الفشل";
        }
    }

    private String readGitHubJson(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "WorldNews-Android");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("GitHub أعاد HTTP " + code);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 2_000_000) throw new IllegalStateException("بيانات الحالة أكبر من المتوقع.");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }

    private String arabicRunState(String statusValue, String conclusion) {
        if ("in_progress".equals(statusValue) || "queued".equals(statusValue)) return "جارٍ التنفيذ";
        if ("success".equals(conclusion)) return "ناجح";
        if ("failure".equals(conclusion)) return "فشل";
        if ("cancelled".equals(conclusion)) return "أُلغي";
        return "غير مكتمل";
    }

    private void buildSocialPage() {
        addText(pageContent, "منشور الخبر", 22, TEXT, true, 0, 6);
        addText(pageContent, "مسودة مناسبة لـ X وفيسبوك — بدون نشر تلقائي", 14, GOLD, true, 0, 12);
        LinearLayout card = card();
        addText(card, "1. اكتب الخبر والهاشتاجات", 17, TEXT, true, 0, 6);
        addText(card, "سيصوغ Gemini من آخر مشروع محفوظ فقط؛ لا يبحث على الإنترنت ولا يتحقق من المصادر. راجع الخبر قبل المشاركة.", 13, MUTED, false, 0, 8);

        String savedTopic = getPreferences(MODE_PRIVATE).getString("last_media_topic", "").trim();
        socialHeadlineInput = new EditText(this);
        socialHeadlineInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        socialHeadlineInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        socialHeadlineInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        socialHeadlineInput.setTextColor(TEXT);
        socialHeadlineInput.setHintTextColor(Color.rgb(128, 129, 137));
        socialHeadlineInput.setHint("عنوان قصير يظهر على صورة البوست");
        socialHeadlineInput.setTextSize(16);
        socialHeadlineInput.setMinLines(2);
        socialHeadlineInput.setMaxLines(3);
        socialHeadlineInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        socialHeadlineInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        socialHeadlineInput.setText(savedTopic);
        socialHeadlineInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().trim().equals(renderedSocialHeadline)) {
                    savedSocialImageUri = null;
                    if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(false);
                    if (saveSocialImageButton != null) saveSocialImageButton.setEnabled(false);
                }
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        card.addView(socialHeadlineInput, params(-1, -2, 0, dp(4), 0, 8));

        socialDraftInput = new EditText(this);
        socialDraftInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        socialDraftInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        socialDraftInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        socialDraftInput.setTextColor(TEXT);
        socialDraftInput.setHintTextColor(Color.rgb(128, 129, 137));
        socialDraftInput.setHint("سيظهر هنا نص المنشور والهاشتاجات للمراجعة والتعديل.");
        socialDraftInput.setTextSize(14);
        socialDraftInput.setMinLines(5);
        socialDraftInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        socialDraftInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        socialDraftInput.setText(getPreferences(MODE_PRIVATE).getString("last_social_draft", ""));
        socialDraftInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                getPreferences(MODE_PRIVATE).edit().putString("last_social_draft", editable.toString()).apply();
            }
        });
        card.addView(socialDraftInput, params(-1, -2, 0, 0, 0, 8));

        generateSocialPostButton = primaryButton("كتابة بوست الخبر والهاشتاجات");
        card.addView(generateSocialPostButton, params(-1, dp(52), 0, 0, 0, 6));
        generateSocialPostButton.setOnClickListener(v -> makeSocialDraft());
        Button copy = secondaryButton("نسخ نص البوست للمراجعة");
        card.addView(copy, params(-1, dp(46), 0, 0, 0, dp(18)));
        copy.setOnClickListener(v -> copySocialDraft());

        addText(card, "2. جهّز صورة الخبر", 17, TEXT, true, 0, 5);
        addText(card, "اختر صورة تملك حق استخدامها. سيضع التطبيق العنوان عليها بقالب World News، مع الحفاظ على الصورة كاملة دون قصّها.", 13, MUTED, false, 0, 8);
        Button choosePhoto = secondaryButton("اختيار صورة الخبر من الهاتف");
        card.addView(choosePhoto, params(-1, dp(48), 0, 0, 0, 6));
        choosePhoto.setOnClickListener(v -> pickSocialPhoto());

        socialImagePreview = new ImageView(this);
        socialImagePreview.setAdjustViewBounds(true);
        socialImagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        socialImagePreview.setBackgroundColor(Color.rgb(9, 13, 21));
        if (socialPostBitmap != null && !socialPostBitmap.isRecycled()) socialImagePreview.setImageBitmap(socialPostBitmap);
        card.addView(socialImagePreview, params(-1, dp(210), 0, 0, 0, 7));

        renderSocialImageButton = secondaryButton("تحديث التصميم من العنوان والصورة");
        card.addView(renderSocialImageButton, params(-1, dp(46), 0, 0, 0, 6));
        renderSocialImageButton.setOnClickListener(v -> renderSocialPostImage());
        saveSocialImageButton = primaryButton("حفظ صورة البوست JPEG");
        saveSocialImageButton.setEnabled(socialPostBitmap != null && !socialPostBitmap.isRecycled());
        card.addView(saveSocialImageButton, params(-1, dp(50), 0, 0, 0, 6));
        saveSocialImageButton.setOnClickListener(v -> saveSocialPostImage());
        shareSocialPostButton = secondaryButton("مشاركة الصورة والنص يدويًا");
        shareSocialPostButton.setEnabled(savedSocialImageUri != null && !socialDraftInput.getText().toString().trim().isEmpty());
        card.addView(shareSocialPostButton, params(-1, dp(48), 0, 0, 0, 0));
        shareSocialPostButton.setOnClickListener(v -> shareSocialPost());

        socialStatus = text("لا يوجد نشر مباشر الآن. أنشئ المسودة، اختر صورة، راجعها، ثم احفظها أو شاركها بنفسك. ربط النشر المباشر لـ X وفيسبوك مرحلة لاحقة.", 13, MUTED, false);
        socialStatus.setPadding(0, dp(10), 0, 0);
        card.addView(socialStatus);
        pageContent.addView(card);
    }

    private Spinner makeSpinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setPadding(dp(12), dp(7), dp(12), dp(7));
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setBackgroundColor(SURFACE_ALT);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(rounded(SURFACE_ALT, LINE, 14));
        return spinner;
    }

    private void addChipRow(LinearLayout parent, String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        for (String label : labels) {
            TextView chip = text(label, 12, TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setBackground(rounded(SURFACE_ALT, LINE, 20));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(34));
            p.setMargins(dp(3), 0, dp(3), 0);
            row.addView(chip, p);
        }
        parent.addView(row, params(-1, dp(38), 0, 0, 0, 2));
    }

    private void addLabel(LinearLayout parent, String caption) {
        addText(parent, caption, 13, TEXT, true, dp(9), 5);
    }

    private void refreshModelButtons() {
        boolean ready = LocalAiMedia.voiceReady(this);
        if (voiceModelButton != null) voiceModelButton.setText(ready ? "Nabra جاهز — محلي ومجاني" : "تنزيل Nabra الاحتياطي المجاني (نحو 83 ميغابايت)");
        if (testNabraButton != null) testNabraButton.setText(ready ? "اختبار صوت Nabra المحلي" : "اختبار Nabra — نزّل النموذج أولًا");
    }

    private void confirmVoiceDownload() {
        new AlertDialog.Builder(this).setTitle("تنزيل الصوت العربي")
                .setMessage("سيُنزل نموذج Nabra المحلي (نحو 83 ميغابايت) مرة واحدة كخيار احتياطي مجاني إذا تعذر صوت Gemini أو انتهت حصته. التنزيل يستهلك مساحة على الهاتف. هل تبدأ؟")
                .setNegativeButton("ليس الآن", null)
                .setPositiveButton("تنزيل", (dialog, which) -> downloadVoiceModel()).show();
    }

    private void downloadVoiceModel() {
        if (voiceModelButton != null) voiceModelButton.setEnabled(false);
        setVoiceTestButtonsEnabled(false);
        setStatus("جارٍ تنزيل وتجهيز الصوت العربي… 0%", GOLD);
        executor.execute(() -> {
            try {
                LocalAiMedia.ProgressListener listener = percent -> mainHandler.post(() -> setStatus("جارٍ تنزيل وتجهيز الصوت العربي… " + percent + "%", GOLD));
                LocalAiMedia.downloadVoiceModel(this, listener);
                mainHandler.post(() -> {
                    if (voiceModelButton != null) voiceModelButton.setEnabled(true);
                    refreshModelButtons();
                    setVoiceTestButtonsEnabled(true);
                    setVoiceTestResult("اكتمل تنزيل وتجهيز Nabra. اضغط «اختبار صوت Nabra المحلي» لتجربته دون إنترنت.", GOLD);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (voiceModelButton != null) voiceModelButton.setEnabled(true);
                    setVoiceTestButtonsEnabled(true);
                    setVoiceTestResult(error.getMessage() == null ? "تعذر تنزيل أو تجهيز Nabra." : "تعذر تنزيل أو تجهيز Nabra: " + error.getMessage(), Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private void generateScript() {
        String topic = topicInput.getText().toString().trim();
        if (topic.length() < 4) {
            setStatus("اكتب موضوعًا واضحًا قبل التوليد.", Color.rgb(255, 130, 115));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إرسال الفكرة إلى Gemini؟")
                .setMessage("سيُرسل موضوع الفيديو والمدة والمنصة والنبرة والمراجع التي كتبتها إلى Google Gemini عبر Firebase لإعداد المسودة. توجد حصة مجانية محدودة؛ إذا كان مشروع Firebase مربوطًا بالفوترة فقد تُحتسب رسوم. في الطبقة المجانية قد تستخدم Google بيانات الطلب لتحسين منتجاتها. لا تضع كلمات مرور أو معلومات سرية. هل توافق على الإرسال لهذه المرة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("موافق، اكتب المسودة", (dialog, which) -> generateScriptWithGemini(topic))
                .show();
    }

    private void generateScriptWithGemini(String topic) {
        generateButton.setEnabled(false);
        setStatus("يرسل الطلب إلى Gemini ويكتب المسودة…", GOLD);
        String duration = String.valueOf(durationSpinner.getSelectedItem());
        String style = String.valueOf(styleSpinner.getSelectedItem());
        String platform = platformSpinner == null ? "TikTok / Reels — عمودي 9:16" : String.valueOf(platformSpinner.getSelectedItem());
        String references = referenceInput == null ? "" : referenceInput.getText().toString().trim();
        executor.execute(() -> {
            try {
                String result = GeminiNewsAnalyzer.generateScriptBlocking(this, topic, duration, style, platform, references);
                mainHandler.post(() -> {
                    scriptOutput.setText(result);
                    lastVoiceText = "";
                    lastVoicePath = "";
                    lastVoiceSelection = "";
                    lastVoiceProvider = "";
                    if (exportAudioButton != null) exportAudioButton.setEnabled(false);
                    generateButton.setEnabled(true);
                    saveProject(false);
                    setStatus("اكتملت مسودة Gemini. اضغط زر الصوت إذا أردت إنشاء تعليق صوتي.", GOLD);
                    if (scriptOutputCard != null) scroll.post(() -> scroll.smoothScrollTo(0, scriptOutputCard.getTop()));
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    generateButton.setEnabled(true);
                    setStatus(userFriendlyAiError(error), Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private void saveProject(boolean showMessage) {
        if (topicInput == null || scriptOutput == null) return;
        String topic = topicInput.getText().toString().trim();
        String script = scriptOutput.getText().toString().trim();
        if (topic.isEmpty() || script.isEmpty()) {
            setStatus("لا يوجد سكريبت لحفظه بعد.", Color.rgb(255, 130, 115));
            return;
        }
        getPreferences(MODE_PRIVATE).edit()
                .putString("last_media_topic", topic)
                .putString("last_media_script", script)
                .putString("last_media_duration", String.valueOf(durationSpinner.getSelectedItem()))
                .putString("last_media_style", String.valueOf(styleSpinner.getSelectedItem()))
                .putString("last_media_platform", String.valueOf(platformSpinner.getSelectedItem()))
                .putString("last_media_references", referenceInput.getText().toString().trim())
                .apply();
        if (showMessage) setStatus("حُفظ المشروع على الهاتف فقط.", GOLD);
    }

    private void loadSavedProject() {
        String topic = getPreferences(MODE_PRIVATE).getString("last_media_topic", "");
        String script = getPreferences(MODE_PRIVATE).getString("last_media_script", "");
        if (!topic.isEmpty()) topicInput.setText(topic);
        if (!script.isEmpty()) scriptOutput.setText(script);
        referenceInput.setText(getPreferences(MODE_PRIVATE).getString("last_media_references", ""));
        String duration = getPreferences(MODE_PRIVATE).getString("last_media_duration", "");
        String style = getPreferences(MODE_PRIVATE).getString("last_media_style", "");
        String platform = getPreferences(MODE_PRIVATE).getString("last_media_platform", "");
        setSpinnerSelection(durationSpinner, duration);
        setSpinnerSelection(styleSpinner, style);
        setSpinnerSelection(platformSpinner, platform);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        if (value == null || value.isEmpty()) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void previewVoice() {
        if (scriptOutput == null || scriptOutput.getText().toString().trim().isEmpty()) {
            setStatus("أنشئ السكريبت أو اكتبه أولًا قبل معاينة الصوت.", Color.rgb(255, 130, 115));
            return;
        }
        String speech = narrationForSpeech(scriptOutput.getText().toString());
        if (speech.length() < 20) {
            setStatus("لم أجد نصًا كافيًا للراوي. أعد توليد السكريبت أو أضف نص التعليق الصوتي الكامل.", Color.rgb(255, 130, 115));
            return;
        }
        final String finalSpeech = speech;
        final String selectedVoice = selectedGeminiVoice();
        if (!lastVoicePath.isEmpty() && lastVoiceText.equals(finalSpeech) && lastVoiceSelection.equals(selectedVoice) && new java.io.File(lastVoicePath).isFile()) {
            previewButton.setEnabled(true);
            playVoice(lastVoicePath);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إنشاء صوت Gemini؟")
                .setMessage("سيُرسل نص الراوي إلى Google Gemini TTS عبر Firebase. توجد حصة مجانية محدودة؛ إذا كان مشروع Firebase مربوطًا بالفوترة فقد تُحتسب رسوم، ولا يستطيع التطبيق معرفة ذلك مسبقًا. في الطبقة المجانية قد تستخدم Google بيانات الطلب لتحسين منتجاتها. إذا رفض Gemini الطلب، يحاول التطبيق استخدام Nabra محليًا إذا كان منزّلًا. هل توافق على إرسال النص لهذه المرة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("موافق، أنشئ الصوت", (dialog, which) -> generateVoiceWithFallback(finalSpeech, selectedVoice))
                .show();
    }

    private String selectedGeminiVoice() {
        int position = voiceSpinner == null ? 0 : voiceSpinner.getSelectedItemPosition();
        return TTS_VOICE_IDS[Math.max(0, Math.min(position, TTS_VOICE_IDS.length - 1))];
    }

    private void generateVoiceWithFallback(String speech, String selectedVoice) {
        previewButton.setEnabled(false);
        setStatus("يجري إنشاء التعليق الصوتي عبر Gemini…", GOLD);
        executor.execute(() -> {
            try {
                String path;
                String provider;
                try {
                    path = GeminiSpeech.generateWavBlocking(this, speech, selectedVoice);
                    provider = "Gemini · " + selectedVoice;
                } catch (Exception geminiError) {
                    if (!LocalAiMedia.voiceReady(this)) {
                        String reason = geminiError.getMessage() == null ? "Gemini غير متاح أو انتهت الحصة." : geminiError.getMessage();
                        throw new IllegalStateException(reason + " نزّل Nabra الاحتياطي من الزر إذا أردت صوتًا محليًا دون اتصال.", geminiError);
                    }
                    mainHandler.post(() -> setStatus("Gemini غير متاح أو انتهت حصته؛ يجري التحويل إلى Nabra المحلي…", GOLD));
                    try {
                        path = LocalAiMedia.synthesizeArabic(this, speech);
                        provider = "Nabra الاحتياطي المحلي";
                    } catch (Exception localError) {
                        throw new IllegalStateException("تعذر Gemini (" + safeMessage(geminiError) + ") وتعذر Nabra (" + safeMessage(localError) + ").", localError);
                    }
                }
                final String finalProvider = provider;
                final String finalPath = path;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    lastVoicePath = finalPath;
                    lastVoiceText = speech;
                    lastVoiceSelection = selectedVoice;
                    lastVoiceProvider = finalProvider;
                    if (exportAudioButton != null) exportAudioButton.setEnabled(true);
                    previewButton.setEnabled(true);
                    playVoice(finalPath);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    previewButton.setEnabled(true);
                    setStatus(userFriendlyAiError(error), Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? "سبب غير معروف" : error.getMessage();
    }

    private void showAppCheckDebugToken() {
        setVoiceTestResult("يجري تجهيز رمز App Check لهذا الجهاز…", GOLD);
        FirebaseAiSetup.requestDebugToken(this, (token, error) -> mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (token == null || token.trim().isEmpty()) {
                String detail = error == null ? "أعد فتح التطبيق وحاول مرة أخرى." : error;
                setVoiceTestResult("تعذر قراءة رمز App Check Debug: " + detail, Color.rgb(255, 130, 115));
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("رمز App Check Debug")
                    .setMessage("انسخ هذا الرمز إلى خانة Debug token في Firebase Console. لا تشاركه أو ترفعه إلى GitHub.\n\n" + token)
                    .setNegativeButton("إغلاق", null)
                    .setPositiveButton("نسخ الرمز", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("App Check Debug token", token));
                            Toast.makeText(this, "تم نسخ رمز Debug. لا تشاركه مع أحد.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
        }));
    }

    private void confirmGeminiVoiceTest() {
        String voice = selectedGeminiVoice();
        new AlertDialog.Builder(this)
                .setTitle("اختبار Gemini TTS")
                .setMessage("سيُرسل التطبيق عبارة اختبار ثابتة فقط إلى Gemini عبر Firebase، وليس نص السكريبت. الحصة المجانية محدودة وقد تُحتسب رسوم إذا كان المشروع مربوطًا بالفوترة. هل توافق على الاختبار؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("اختبر Gemini", (dialog, which) -> runGeminiVoiceTest(voice))
                .show();
    }

    private void runGeminiVoiceTest(String voice) {
        setVoiceTestButtonsEnabled(false);
        if (exportAudioButton != null) exportAudioButton.setEnabled(false);
        setVoiceTestResult("جارٍ اختبار Gemini بصوت " + voice + "…", GOLD);
        executor.execute(() -> {
            try {
                String path = GeminiSpeech.generateWavBlocking(this, TEST_VOICE_TEXT, voice);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    lastVoicePath = path;
                    lastVoiceText = TEST_VOICE_TEXT;
                    lastVoiceSelection = voice;
                    lastVoiceProvider = "Gemini TTS اختبار · " + voice;
                    playVoice(path);
                });
            } catch (Exception error) {
                String details = userFriendlyAiError(error);
                mainHandler.post(() -> {
                    setVoiceTestButtonsEnabled(true);
                    setVoiceTestResult("فشل اختبار Gemini:\n" + details, Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private void testNabraVoice() {
        if (!LocalAiMedia.voiceReady(this)) {
            setVoiceTestResult("Nabra غير منزّل. اضغط زر تنزيل Nabra في هذه البطاقة، ثم أعد الاختبار.", Color.rgb(255, 187, 54));
            return;
        }
        setVoiceTestButtonsEnabled(false);
        if (exportAudioButton != null) exportAudioButton.setEnabled(false);
        setVoiceTestResult("جارٍ إنشاء عبارة الاختبار محليًا باستخدام Nabra…", GOLD);
        executor.execute(() -> {
            try {
                String path = LocalAiMedia.synthesizeArabic(this, TEST_VOICE_TEXT);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    lastVoicePath = path;
                    lastVoiceText = TEST_VOICE_TEXT;
                    lastVoiceSelection = selectedGeminiVoice();
                    lastVoiceProvider = "Nabra اختبار محلي";
                    playVoice(path);
                });
            } catch (Exception error) {
                String details = voiceErrorDetails(error);
                mainHandler.post(() -> {
                    setVoiceTestButtonsEnabled(true);
                    setVoiceTestResult("فشل اختبار Nabra:\n" + details, Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private void setVoiceTestButtonsEnabled(boolean enabled) {
        if (testGeminiButton != null) testGeminiButton.setEnabled(enabled);
        if (testNabraButton != null) testNabraButton.setEnabled(enabled);
    }

    private void setVoiceTestResult(String message, int color) {
        if (voiceTestStatus != null) {
            voiceTestStatus.setText(message);
            voiceTestStatus.setTextColor(color);
        }
        setStatus(message, color);
    }

    private String voiceErrorDetails(Throwable error) {
        StringBuilder details = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 3) {
            if (details.length() > 0) details.append("\nسبب إضافي: ");
            details.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) details.append(": ").append(message.trim());
            current = current.getCause();
            depth++;
        }
        String safe = details.toString()
                .replaceAll("(?i)(api[_ -]?key|key)(\\s*[=:]\\s*)([^\\s&]+)", "$1$2[محذوف]")
                .replaceAll("AIza[0-9A-Za-z_-]{20,}", "[مفتاح مخفي]");
        return safe.substring(0, Math.min(700, safe.length()));
    }

    private String userFriendlyAiError(Throwable error) {
        String details = voiceErrorDetails(error);
        String lower = details.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("app check") || lower.contains("appcheck")) {
            return "Firebase رفض طلب Gemini بسبب App Check. في Firebase Console افتح Security → App Check → APIs → Firebase AI Logic واجعل Baseline protection = Enforced وReplay protection = Disabled. "
                    + "ولأن هذه نسخة Debug، سجّل رمز Debug الخاص بهذا الهاتف من Security → App Check → Apps → تطبيق World News → Manage debug tokens. لا تنشر رمز Debug ولا ترسله في المحادثة.\nالتفصيل: " + details;
        }
        return details.isEmpty() ? "تعذر إكمال طلب Gemini." : details;
    }

    private void playVoice(String path) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(path);
            player.setOnPreparedListener(mp -> {
                mp.setVolume(1f, 1f);
                mp.start();
                setVoiceTestButtonsEnabled(true);
                setVoiceTestResult("نجح التوليد وبدأ تشغيل " + (lastVoiceProvider.isEmpty() ? "الصوت العربي" : lastVoiceProvider) + ". ارفع صوت الوسائط إذا لم تسمعه.", GOLD);
            });
            player.setOnCompletionListener(mp -> setVoiceTestResult("انتهى تشغيل " + (lastVoiceProvider.isEmpty() ? "الصوت العربي" : lastVoiceProvider) + ".", GOLD));
            player.setOnErrorListener((mp, what, extra) -> {
                setVoiceTestButtonsEnabled(true);
                setVoiceTestResult("تم إنشاء ملف WAV لكن تعذر تشغيله على الهاتف. رمز المشغل: " + what + "/" + extra, Color.rgb(255, 130, 115));
                return true;
            });
            if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
            mediaPlayer = player;
            player.prepareAsync();
            setVoiceTestResult("تم إنشاء الصوت؛ جارٍ تجهيز التشغيل…", GOLD);
        } catch (Exception error) {
            setVoiceTestButtonsEnabled(true);
            setVoiceTestResult("تعذر فتح معاينة الصوت: " + voiceErrorDetails(error), Color.rgb(255, 130, 115));
        }
    }

    private void exportAudio() {
        if (lastVoicePath.isEmpty() || !new java.io.File(lastVoicePath).isFile()) {
            setStatus("أنشئ معاينة التعليق الصوتي أولًا، ثم يمكنك حفظ ملف WAV.", Color.rgb(255, 130, 115));
            return;
        }
        String currentSpeech = narrationForSpeech(scriptOutput.getText().toString());
        if (!lastVoiceText.equals(currentSpeech) || !lastVoiceSelection.equals(selectedGeminiVoice())) {
            setStatus("تغير النص أو الصوت المختار منذ إنشاء الملف. أعد إنشاء المعاينة قبل التصدير.", Color.rgb(255, 130, 115));
            return;
        }
        pendingAudioPath = lastVoicePath;
        String topic = topicInput.getText().toString().trim();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, safeFileName(topic) + "-voiceover.wav");
        try { startActivityForResult(intent, CREATE_AUDIO_REQUEST); }
        catch (Exception error) { setStatus("تعذر فتح نافذة حفظ ملف الصوت.", Color.rgb(255, 130, 115)); }
    }

    private String getNarrationText(String script) {
        return NarrationParser.extract(script);
    }

    private String narrationForSpeech(String script) {
        String speech = getNarrationText(script).trim();
        if (speech.length() > 3500) {
            int end = speech.lastIndexOf(' ', 3500);
            speech = speech.substring(0, end > 2000 ? end : 3500).trim();
        }
        return speech;
    }

    private void exportPdf() {
        if (scriptOutput == null || scriptOutput.getText().toString().trim().isEmpty()) {
            setStatus("لا يوجد مشروع لتصديره بعد.", Color.rgb(255, 130, 115));
            return;
        }
        saveProject(false);
        pendingPdfText = scriptOutput.getText().toString().trim();
        String topic = topicInput.getText().toString().trim();
        pendingPdfTitle = topic.isEmpty() ? "سكريبت World News" : topic;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, safeFileName(pendingPdfTitle) + ".pdf");
        try {
            startActivityForResult(intent, CREATE_PDF_REQUEST);
        } catch (Exception error) {
            setStatus("تعذر فتح نافذة حفظ PDF على هذا الهاتف.", Color.rgb(255, 130, 115));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_SOCIAL_IMAGE_REQUEST) {
            persistSocialImagePermission(uri, data.getFlags());
            socialPhotoUri = uri;
            savedSocialImageUri = null;
            if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(false);
            renderSocialPostImage();
            return;
        }
        if (requestCode == SAVE_SOCIAL_IMAGE_REQUEST) {
            if (!isSocialPostImageCurrent()) {
                socialStatus.setText("تغيّر العنوان أو الصورة قبل اكتمال الحفظ. حدّث التصميم ثم احفظه مرة أخرى.");
                return;
            }
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("لم يمكن فتح ملف الصورة للحفظ.");
                if (socialPostBitmap == null || socialPostBitmap.isRecycled()
                        || !socialPostBitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                    throw new IllegalStateException("تعذر تحويل تصميم البوست إلى JPEG.");
                }
                output.flush();
                savedSocialImageUri = uri;
                if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(socialDraftInput != null && !socialDraftInput.getText().toString().trim().isEmpty());
                socialStatus.setText("تم حفظ صورة البوست. يمكنك الآن مشاركة الصورة والنص يدويًا بعد المراجعة.");
            } catch (Exception error) {
                socialStatus.setText("تعذر حفظ صورة البوست: " + (error.getLocalizedMessage() == null ? "خطأ في الملف." : error.getLocalizedMessage()));
            }
            return;
        }
        if (requestCode != CREATE_PDF_REQUEST && requestCode != CREATE_AUDIO_REQUEST) return;
        if (requestCode == CREATE_AUDIO_REQUEST) {
            try (InputStream input = new FileInputStream(pendingAudioPath); OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("لم يمكن فتح الملف للحفظ.");
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                setStatus("تم حفظ التعليق الصوتي العربي بصيغة WAV.", GOLD);
            } catch (Exception error) {
                setStatus("تعذر حفظ ملف الصوت: " + (error.getMessage() == null ? "خطأ في الملف" : error.getMessage()), Color.rgb(255, 130, 115));
            }
            return;
        }
        try (OutputStream stream = getContentResolver().openOutputStream(uri, "w")) {
            if (stream == null) throw new IllegalStateException("لم يمكن فتح الملف للحفظ.");
            writePdf(stream, pendingPdfTitle, pendingPdfText);
            setStatus("تم تصدير المشروع بصيغة PDF.", GOLD);
        } catch (Exception error) {
            setStatus("تعذر تصدير PDF: " + (error.getMessage() == null ? "خطأ في الملف" : error.getMessage()), Color.rgb(255, 130, 115));
        }
    }

    private void writePdf(OutputStream stream, String title, String body) throws Exception {
        PdfDocument document = new PdfDocument();
        final int pageWidth = 595;
        final int pageHeight = 842;
        final int margin = 42;
        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.rgb(35, 39, 46));
        bodyPaint.setTextSize(12.5f);
        bodyPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        StaticLayout full = StaticLayout.Builder.obtain(body, 0, body.length(), bodyPaint, pageWidth - margin * 2)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(true)
                .setLineSpacing(4f, 1f)
                .build();
        int lineHeight = full.getLineCount() > 0 ? Math.max(1, full.getLineBottom(0) - full.getLineTop(0)) : 20;
        int linesPerPage = Math.max(1, (pageHeight - margin * 2 - 64) / lineHeight);
        int pageNumber = 1;
        for (int firstLine = 0; firstLine < full.getLineCount(); firstLine += linesPerPage) {
            int lastLine = Math.min(full.getLineCount(), firstLine + linesPerPage);
            int start = full.getLineStart(firstLine);
            int end = full.getLineEnd(lastLine - 1);
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create());
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            Paint heading = new Paint(Paint.ANTI_ALIAS_FLAG);
            heading.setColor(Color.rgb(28, 45, 63));
            heading.setTextSize(19f);
            heading.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            heading.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(title, pageWidth - margin, 48, heading);
            Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
            brand.setColor(Color.rgb(166, 104, 26));
            brand.setTextSize(9f);
            brand.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("World News — مسودة قابلة للتحرير والمراجعة", pageWidth - margin, 66, brand);
            StaticLayout part = StaticLayout.Builder.obtain(body, start, end, bodyPaint, pageWidth - margin * 2)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(true)
                    .setLineSpacing(4f, 1f)
                    .build();
            canvas.save();
            canvas.translate(margin, 86);
            part.draw(canvas);
            canvas.restore();
            document.finishPage(page);
        }
        document.writeTo(stream);
        document.close();
    }

    private String safeFileName(String value) {
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (cleaned.length() > 45) cleaned = cleaned.substring(0, 45);
        return cleaned.isEmpty() ? "world-news-script" : cleaned;
    }

    private void makeSocialDraft() {
        String topic = getPreferences(MODE_PRIVATE).getString("last_media_topic", "").trim();
        String script = getPreferences(MODE_PRIVATE).getString("last_media_script", "").trim();
        String references = getPreferences(MODE_PRIVATE).getString("last_media_references", "").trim();
        if (topic.isEmpty() || script.isEmpty()) {
            socialStatus.setText("أنشئ واحفظ مشروع الخبر والسكريبت أولًا من تبويب المساعد.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إرسال المشروع إلى Gemini لصياغة بوست؟")
                .setMessage("سيُرسل عنوان المشروع والسكريبت والمراجع التي أضفتها إلى Google Gemini عبر Firebase لصياغة مسودة وهاشتاجات. لن يُرسل شيء إلى X أو Facebook. لا تضع معلومات سرية. هل توافق على الإرسال لهذه المرة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("موافق، اكتب المسودة", (dialog, which) -> generateSocialPostWithGemini(topic, script, references))
                .show();
    }

    private void generateSocialPostWithGemini(String topic, String script, String references) {
        if (generateSocialPostButton != null) generateSocialPostButton.setEnabled(false);
        socialStatus.setText("جارٍ إعداد نص موجز وهاشتاجات من معلومات المشروع فقط…");
        executor.execute(() -> {
            try {
                String result = GeminiNewsAnalyzer.generateSocialPostBlocking(this, topic, script, references);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (generateSocialPostButton != null) generateSocialPostButton.setEnabled(true);
                    String caption = socialPostSection(result, "نص المنشور:");
                    String hashtags = socialPostSection(result, "الهاشتاجات:");
                    String readyDraft = caption.isEmpty() || hashtags.isEmpty()
                            ? result : caption + "\n\n" + hashtags;
                    socialDraftInput.setText(readyDraft);
                    getPreferences(MODE_PRIVATE).edit().putString("last_social_draft", readyDraft).apply();
                    socialHeadlineInput.setText(socialImageHeadline(result, topic));
                    if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(savedSocialImageUri != null);
                    if (socialPhotoUri != null) renderSocialPostImage();
                    else socialStatus.setText("اكتملت المسودة. اختر صورة الخبر ليجهز التطبيق بطاقة الصورة؛ لم يتم النشر.");
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (generateSocialPostButton != null) generateSocialPostButton.setEnabled(true);
                    socialStatus.setText("تعذر إنشاء المسودة: " + (error.getLocalizedMessage() == null ? "تحقق من الاتصال وإعداد Gemini." : error.getLocalizedMessage()));
                });
            }
        });
    }

    private String socialPostSection(String generated, String prefix) {
        for (String line : generated.split("\\r?\\n")) {
            String cleaned = line.trim();
            if (cleaned.startsWith(prefix)) return cleaned.substring(prefix.length()).trim();
        }
        return "";
    }

    private String socialImageHeadline(String generated, String fallback) {
        for (String line : generated.split("\\r?\\n")) {
            String cleaned = line.trim();
            if (cleaned.startsWith("عنوان الصورة:")) {
                String value = cleaned.substring("عنوان الصورة:".length()).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return fallback;
    }

    private void pickSocialPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_SOCIAL_IMAGE_REQUEST);
        } catch (Exception error) {
            socialStatus.setText("تعذر فتح اختيار الصور على هذا الهاتف.");
        }
    }

    private void persistSocialImagePermission(Uri uri, int flags) {
        try {
            int required = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
            if ((flags & required) == required) getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
    }

    private void renderSocialPostImage() {
        if (socialPhotoUri == null) {
            socialStatus.setText("اختر صورة الخبر من الهاتف أولًا.");
            return;
        }
        String headline = socialHeadlineInput == null ? "" : socialHeadlineInput.getText().toString().trim();
        if (headline.isEmpty()) headline = getPreferences(MODE_PRIVATE).getString("last_media_topic", "").trim();
        if (headline.isEmpty()) {
            socialStatus.setText("اكتب عنوانًا قصيرًا قبل تجهيز صورة البوست.");
            return;
        }
        final String finalHeadline = headline;
        final Uri photo = socialPhotoUri;
        savedSocialImageUri = null;
        if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(false);
        if (saveSocialImageButton != null) saveSocialImageButton.setEnabled(false);
        if (renderSocialImageButton != null) renderSocialImageButton.setEnabled(false);
        socialStatus.setText("جارٍ تجهيز صورة 1200×675 مع الحفاظ على الصورة كاملة…");
        executor.execute(() -> {
            Bitmap source = null;
            Bitmap rendered = null;
            String failure = null;
            try {
                source = decodeSocialPhoto(photo);
                rendered = createSocialPostBitmap(source, finalHeadline);
            } catch (Exception error) {
                failure = error.getLocalizedMessage() == null ? "تعذر قراءة الصورة المحددة." : error.getLocalizedMessage();
            } finally {
                if (source != null && !source.isRecycled()) source.recycle();
            }
            final Bitmap result = rendered;
            final String errorText = failure;
            mainHandler.post(() -> {
                if (renderSocialImageButton != null) renderSocialImageButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                if (result == null) {
                    if (saveSocialImageButton != null) saveSocialImageButton.setEnabled(isSocialPostImageCurrent());
                    socialStatus.setText("تعذر تجهيز الصورة: " + errorText);
                    return;
                }
                if (socialPostBitmap != null && !socialPostBitmap.isRecycled()) socialPostBitmap.recycle();
                socialPostBitmap = result;
                renderedSocialHeadline = finalHeadline;
                renderedSocialPhotoUri = photo;
                if (socialImagePreview != null) socialImagePreview.setImageBitmap(result);
                savedSocialImageUri = null;
                if (saveSocialImageButton != null) saveSocialImageButton.setEnabled(true);
                if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(false);
                socialStatus.setText("تم تجهيز معاينة الصورة. احفظها أولًا ثم يمكنك مشاركة الصورة والنص يدويًا؛ لم يتم النشر.");
            });
        });
    }

    private Bitmap decodeSocialPhoto(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("لم يمنح مدير الملفات صلاحية قراءة الصورة.");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException("الملف المحدد ليس صورة قابلة للقراءة.");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2400) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        int rotation = socialPhotoRotation(uri);
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("تعذر إعادة فتح الصورة.");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IllegalStateException("تعذر فك الصورة المحددة.");
            if (rotation == 0) return bitmap;
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (oriented != bitmap) bitmap.recycle();
            return oriented;
        }
    }

    private int socialPhotoRotation(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return 0;
            ExifInterface exif = new ExifInterface(input);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) { }
        return 0;
    }

    private Bitmap createSocialPostBitmap(Bitmap source, String headline) {
        final int width = 1200;
        final int height = 675;
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(9, 14, 24));

        float scale = Math.min((width - 72f) / source.getWidth(), (height - 72f) / source.getHeight());
        float imageWidth = source.getWidth() * scale;
        float imageHeight = source.getHeight() * scale;
        RectF destination = new RectF((width - imageWidth) / 2f, (height - imageHeight) / 2f,
                (width + imageWidth) / 2f, (height + imageHeight) / 2f);
        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, destination, imagePaint);

        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setShader(new LinearGradient(0, height * 0.42f, 0, height,
                new int[]{0x00101828, 0xE90A101A}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height * 0.36f, width, height, shade);

        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(GOLD);
        canvas.drawRoundRect(new RectF(width - 82, 48, width - 70, 112), 6, 6, accent);

        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(TEXT);
        brandPaint.setTextSize(28f);
        brandPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        StaticLayout brand = StaticLayout.Builder.obtain("أخبار العالم  |  WORLD NEWS", 0,
                        "أخبار العالم  |  WORLD NEWS".length(), brandPaint, 490)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .build();
        canvas.save();
        canvas.translate(width - 100 - 490, 54);
        brand.draw(canvas);
        canvas.restore();

        TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(GOLD);
        labelPaint.setTextSize(24f);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        String label = "ملخص الخبر";
        StaticLayout labelLayout = StaticLayout.Builder.obtain(label, 0, label.length(), labelPaint, width - 120)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setMaxLines(1)
                .build();
        canvas.save();
        canvas.translate(60, 425);
        labelLayout.draw(canvas);
        canvas.restore();

        String title = headline.replace('\n', ' ').trim();
        if (title.length() > 80) title = title.substring(0, 79).trim() + "…";
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(54f);
        titlePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titlePaint.setShadowLayer(3f, 0f, 2f, Color.BLACK);
        StaticLayout titleLayout = StaticLayout.Builder.obtain(title, 0, title.length(), titlePaint, width - 120)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setMaxLines(3)
                .build();
        canvas.save();
        canvas.translate(60, 470);
        titleLayout.draw(canvas);
        canvas.restore();

        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(GOLD);
        canvas.drawRect(60, 642, width - 60, 648, rule);
        return output;
    }

    private void saveSocialPostImage() {
        if (!isSocialPostImageCurrent()) {
            socialStatus.setText("حدّث تصميم الصورة بعد تعديل العنوان أو اختيار صورة مختلفة.");
            return;
        }
        String topic = getPreferences(MODE_PRIVATE).getString("last_media_topic", "world-news");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_TITLE, safeFileName(topic) + "-world-news-post.jpg");
        try {
            startActivityForResult(intent, SAVE_SOCIAL_IMAGE_REQUEST);
        } catch (Exception error) {
            socialStatus.setText("تعذر فتح نافذة حفظ صورة البوست.");
        }
    }

    private void shareSocialPost() {
        String draft = socialDraftInput == null ? "" : socialDraftInput.getText().toString().trim();
        if (draft.isEmpty()) {
            socialStatus.setText("اكتب نص البوست أولًا.");
            return;
        }
        if (savedSocialImageUri == null) {
            socialStatus.setText("احفظ صورة البوست أولًا، ثم شارك النص والصورة يدويًا.");
            return;
        }
        if (!isSocialPostImageCurrent()) {
            savedSocialImageUri = null;
            if (shareSocialPostButton != null) shareSocialPostButton.setEnabled(false);
            socialStatus.setText("تغيّر العنوان أو الصورة بعد التصميم. حدّث الصورة واحفظها من جديد قبل المشاركة.");
            return;
        }
        int characterCount = draft.codePointCount(0, draft.length());
        if (characterCount > 280) {
            socialStatus.setText("المسودة أطول من 280 حرفًا تقريبًا (" + characterCount + "). اختصرها قبل المشاركة إلى X.");
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("last_social_draft", draft).apply();
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/jpeg");
        share.putExtra(Intent.EXTRA_TEXT, draft);
        share.putExtra(Intent.EXTRA_STREAM, savedSocialImageUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newUri(getContentResolver(), "World News post image", savedSocialImageUri));
        try {
            startActivity(Intent.createChooser(share, "مشاركة مسودة البوست يدويًا"));
            socialStatus.setText("اختر التطبيق وراجع المنشور قبل إرساله. لم يحدث نشر تلقائي.");
        } catch (Exception error) {
            socialStatus.setText("تعذرت مشاركة المسودة من هذا الهاتف.");
        }
    }

    private boolean isSocialPostImageCurrent() {
        if (socialPostBitmap == null || socialPostBitmap.isRecycled() || socialPhotoUri == null || renderedSocialPhotoUri == null) return false;
        String currentHeadline = socialHeadlineInput == null ? "" : socialHeadlineInput.getText().toString().trim();
        return socialPhotoUri.equals(renderedSocialPhotoUri) && currentHeadline.equals(renderedSocialHeadline);
    }

    private void copyScript() {
        if (scriptOutput == null || scriptOutput.getText().toString().trim().isEmpty()) return;
        copyToClipboard("سكريبت World News", scriptOutput.getText().toString());
        setStatus("نُسخ السكريبت إلى الحافظة.", GOLD);
    }

    private void copySocialDraft() {
        if (socialDraftInput == null || socialDraftInput.getText().toString().trim().isEmpty()) {
            socialStatus.setText("أنشئ مسودة أولًا قبل نسخها.");
            return;
        }
        String draft = socialDraftInput.getText().toString().trim();
        getPreferences(MODE_PRIVATE).edit().putString("last_social_draft", draft).apply();
        copyToClipboard("مسودة منشور World News", draft);
        socialStatus.setText("نُسخت المسودة للمراجعة. لم يتم النشر.");
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
    }

    private void setStatus(String value, int color) {
        if (status != null) {
            status.setText(value);
            status.setTextColor(color);
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackground(rounded(SURFACE, LINE, 22));
        return layout;
    }

    private Button primaryButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(Color.rgb(25, 20, 10));
        button.setTextSize(16);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(GOLD, GOLD_DARK, 18));
        return button;
    }

    private Button secondaryButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(SURFACE_ALT, LINE, 16));
        return button;
    }

    private void addText(LinearLayout parent, String caption, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(caption, size, color, bold);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        parent.addView(view, params(-1, -2, 0, top, 0, bottom));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private void openUrl(String url, String error) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) { if (status != null) setStatus(error, Color.rgb(255, 130, 115)); }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (socialPostBitmap != null && !socialPostBitmap.isRecycled()) {
            socialPostBitmap.recycle();
            socialPostBitmap = null;
        }
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
