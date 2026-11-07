package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskAssistantActivity extends Activity {
    private static final String RUNS_URL = "https://github.com/sozanhnedy-bot/-/actions/workflows/ai-task.yml";
    private static final String ACTIONS_URL = "https://github.com/sozanhnedy-bot/-/actions";
    private static final String SOCIAL_URL = "https://github.com/sozanhnedy-bot/-/actions/workflows/publish-social.yml";
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
    private static final long QWEN_FILE_SIZE = 491_400_032L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout pageContent;
    private ScrollView scroll;
    private EditText topicInput;
    private EditText referenceInput;
    private EditText scriptOutput;
    private EditText socialDraftInput;
    private Spinner durationSpinner;
    private Spinner platformSpinner;
    private Spinner styleSpinner;
    private Button selectedTemplateButton;
    private Button generateButton;
    private Button scriptModelButton;
    private Button voiceModelButton;
    private Button previewButton;
    private Button pdfButton;
    private Button exportAudioButton;
    private TextView status;
    private TextView socialStatus;
    private MediaPlayer mediaPlayer;
    private String pendingPdfText = "";
    private String pendingPdfTitle = "سكريبت World News";
    private String pendingAudioPath = "";
    private String lastVoicePath = "";
    private String lastVoiceText = "";
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
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(5, 6, 9));
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.addView(navItem("✧\nالمساعد", 0), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("▱\nالمشاريع", 1), new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(navItem("◎\nالنشر", 2), new LinearLayout.LayoutParams(0, dp(58), 1));
        return nav;
    }

    private TextView navItem(String caption, int tab) {
        TextView item = text(caption, 12, selectedTab == tab ? GOLD : MUTED, selectedTab == tab);
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(v -> showTab(tab));
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
        else buildSocialPage();
        scroll.scrollTo(0, 0);
    }

    private void buildAssistantPage() {
        addHero();
        LinearLayout card = card();
        addText(card, "أنشئ سكريبت ومشاهد الفيديو", 21, TEXT, true, 0, 5);
        addText(card, "اكتب الفكرة وأضف مراجع إن وجدت، ثم اختر المنصة والمدة والنبرة. ينشئ النموذج سكريبتًا ومشاهد قابلة للتعديل؛ وبعد تنزيل الصوت يمكن توليد التعليق وحفظه WAV أو تصدير المشروع PDF. لا تُنشئ هذه النسخة لقطات تصوير أو ملف MP4 نهائيًا.", 14, MUTED, false, 0, 12);

        addLabel(card, "القالب");
        addTemplateGrid(card);

        addLabel(card, "منصة الفيديو ونسبة العرض");
        platformSpinner = makeSpinner(new String[]{"TikTok / Reels — عمودي 9:16", "YouTube Shorts — عمودي 9:16", "فيديو أفقي — 16:9"});
        card.addView(platformSpinner, params(-1, dp(48), 0, 0, 0, 5));

        addLabel(card, "مدة الفيديو");
        durationSpinner = makeSpinner(new String[]{"15 ثانية", "20 ثانية", "30 ثانية", "60 ثانية", "3 دقائق", "4 دقائق", "6 دقائق", "20 دقيقة"});
        card.addView(durationSpinner, params(-1, dp(48), 0, 0, 0, 5));

        addLabel(card, "نبرة النص");
        styleSpinner = makeSpinner(new String[]{"وثائقي متزن", "حماسي", "خبري مباشر", "قصصي مشوق"});
        card.addView(styleSpinner, params(-1, dp(48), 0, 0, 0, 5));
        applyTemplate(0, selectedTemplateButton);

        addLabel(card, "موضوع الفيديو");
        topicInput = new EditText(this);
        topicInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        topicInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        topicInput.setHintTextColor(Color.rgb(128, 129, 137));
        topicInput.setTextColor(TEXT);
        topicInput.setHint("مثال: كيف أجهّز مشروعي على الهاتف؟");
        topicInput.setTextSize(15);
        topicInput.setMinLines(3);
        topicInput.setMaxLines(5);
        topicInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        topicInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        card.addView(topicInput, params(-1, -2, 0, 0, 0, 10));

        addLabel(card, "مراجع أو معلومات تريد الالتزام بها — اختياري");
        referenceInput = new EditText(this);
        referenceInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        referenceInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        referenceInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        referenceInput.setHintTextColor(Color.rgb(128, 129, 137));
        referenceInput.setTextColor(TEXT);
        referenceInput.setHint("أضف حقائق أو مقتطفات موثوقة. النموذج محلي ولا يبحث في الإنترنت.");
        referenceInput.setTextSize(14);
        referenceInput.setMinLines(2);
        referenceInput.setMaxLines(4);
        referenceInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        referenceInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        card.addView(referenceInput, params(-1, -2, 0, 0, 0, 10));

        scriptModelButton = secondaryButton(scriptModelReady() ? "نموذج الكتابة المحلي جاهز" : "تنزيل نموذج الكتابة المفتوح (491 ميغابايت)");
        card.addView(scriptModelButton, params(-1, dp(48), 0, dp(3), 0, 4));
        scriptModelButton.setOnClickListener(v -> confirmModelDownload(false));

        generateButton = primaryButton("✦  توليد السكريبت والمشاهد");
        card.addView(generateButton, params(-1, dp(58), 0, dp(8), 0, 4));
        generateButton.setOnClickListener(v -> generateScript());

        status = text("لا يرسل التطبيق موضوعك إلى خدمة ذكاء اصطناعي. نزّل النموذج مرة واحدة، وبعدها يعمل دون اتصال.", 13, GOLD, false);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(status, params(-1, -2, 0, dp(6), 0, 0));
        pageContent.addView(card);

        LinearLayout outputCard = card();
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

        voiceModelButton = secondaryButton(LocalAiMedia.voiceReady(this) ? "الصوت العربي Nabra جاهز" : "تنزيل الصوت العربي المفتوح (83 ميغابايت)");
        outputCard.addView(voiceModelButton, params(-1, dp(48), 0, 0, 0, 6));
        voiceModelButton.setOnClickListener(v -> confirmModelDownload(true));

        previewButton = secondaryButton("▷  معاينة التعليق الصوتي العربي");
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
        String incomingTrend = getIntent().getStringExtra("trend_topic");
        if (incomingTrend != null && !incomingTrend.trim().isEmpty()) {
            topicInput.setText(incomingTrend.trim());
            topicInput.setSelection(topicInput.length());
            setStatus("تم إدخال الموضوع من رادار الترند. راجعه ثم ولّد السكريبت.", GOLD);
        }
        refreshModelButtons();
    }

    private void addTemplateGrid(LinearLayout parent) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        String[][] templates = {
                {"ريلز سريع", "5 مشاهد · 30 ثانية · إيقاع حماسي"},
                {"شورت مكثف", "3 مشاهد · 15 ثانية · هوك في أول ثانية"},
                {"يوتيوب طويل", "8 مشاهد · 4 دقائق · شرح متدرج"},
                {"وثائقي", "12 مشهد · 6 دقائق · 3 فصول هادئة"},
                {"وثائقي طويل", "24 مشهد · 20 دقيقة · 5 فصول"},
                {"إعلان منتج", "4 مشاهد · 20 ثانية · مشكلة ثم حل"}
        };
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                Button template = secondaryButton(templates[index][0] + "\n" + templates[index][1]);
                template.setTextSize(13);
                template.setGravity(Gravity.CENTER);
                template.setMinHeight(dp(72));
                template.setOnClickListener(v -> applyTemplate(index, template));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(76), 1);
                p.setMargins(column == 0 ? 0 : dp(6), dp(3), column == 1 ? 0 : dp(6), dp(3));
                line.addView(template, p);
                if (index == 0) selectedTemplateButton = template;
            }
            grid.addView(line, params(-1, dp(82), 0, 0, 0, 0));
        }
        parent.addView(grid, params(-1, -2, 0, 0, 0, 8));
        applyTemplate(0, selectedTemplateButton);
    }

    private void applyTemplate(int index, Button selected) {
        if (selectedTemplateButton != null) selectedTemplateButton.setBackground(rounded(SURFACE_ALT, LINE, 16));
        selectedTemplateButton = selected;
        selectedTemplateButton.setBackground(rounded(Color.rgb(55, 42, 24), GOLD_DARK, 16));
        if (durationSpinner == null || platformSpinner == null || styleSpinner == null) return;
        String duration;
        String platform;
        String style;
        switch (index) {
            case 1:
                duration = "15 ثانية"; platform = "YouTube Shorts — عمودي 9:16"; style = "خبري مباشر"; break;
            case 2:
                duration = "4 دقائق"; platform = "فيديو أفقي — 16:9"; style = "وثائقي متزن"; break;
            case 3:
                duration = "6 دقائق"; platform = "فيديو أفقي — 16:9"; style = "وثائقي متزن"; break;
            case 4:
                duration = "20 دقيقة"; platform = "فيديو أفقي — 16:9"; style = "وثائقي متزن"; break;
            case 5:
                duration = "20 ثانية"; platform = "TikTok / Reels — عمودي 9:16"; style = "خبري مباشر"; break;
            default:
                duration = "30 ثانية"; platform = "TikTok / Reels — عمودي 9:16"; style = "حماسي";
        }
        setSpinnerSelection(durationSpinner, duration);
        setSpinnerSelection(platformSpinner, platform);
        setSpinnerSelection(styleSpinner, style);
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
        addText(hero, "كتابة ومساعدة صوتية على الهاتف — بلا مفتاح API وبلا رسوم لكل استخدام.", 14, MUTED, false, 0, 0);
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

    private void buildSocialPage() {
        addText(pageContent, "النشر الاجتماعي", 22, TEXT, true, 0, 6);
        addText(pageContent, "X وفيسبوك — مسودة للمراجعة", 14, GOLD, true, 0, 12);
        LinearLayout card = card();
        addText(card, "صياغة منشور من المشروع المحفوظ", 17, TEXT, true, 0, 6);
        socialDraftInput = new EditText(this);
        socialDraftInput.setGravity(Gravity.TOP | Gravity.RIGHT);
        socialDraftInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        socialDraftInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        socialDraftInput.setTextColor(TEXT);
        socialDraftInput.setHintTextColor(Color.rgb(128, 129, 137));
        socialDraftInput.setHint("سيظهر هنا نص المنشور للمراجعة قبل أي نشر.");
        socialDraftInput.setTextSize(14);
        socialDraftInput.setMinLines(5);
        socialDraftInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        socialDraftInput.setBackground(rounded(SURFACE_ALT, LINE, 16));
        card.addView(socialDraftInput, params(-1, -2, 0, dp(7), 0, 9));
        Button draft = secondaryButton("إنشاء مسودة من آخر مشروع");
        card.addView(draft, params(-1, dp(48), 0, 0, 0, 6));
        draft.setOnClickListener(v -> makeSocialDraft());
        Button copy = primaryButton("نسخ للمراجعة — لم يتم النشر");
        card.addView(copy, params(-1, dp(52), 0, 0, 0, 6));
        copy.setOnClickListener(v -> copySocialDraft());
        Button open = secondaryButton("عرض إعدادات مسار النشر في GitHub");
        card.addView(open, params(-1, dp(48), 0, 0, 0, 0));
        open.setOnClickListener(v -> openUrl(SOCIAL_URL, "تعذر فتح إعدادات مسار النشر."));
        socialStatus = text("لا يُنشر شيء تلقائيًا. النشر الفعلي يظل متوقفًا حتى ربط الحسابات ومراجعة المنصات والتكلفة والموافقة الصريحة.", 13, MUTED, false);
        socialStatus.setPadding(0, dp(10), 0, 0);
        card.addView(socialStatus);
        pageContent.addView(card);
    }

    private Spinner makeSpinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
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

    private boolean scriptModelReady() {
        java.io.File f = LocalAiMedia.scriptModelFile(this);
        return f.isFile() && f.length() == QWEN_FILE_SIZE;
    }

    private void refreshModelButtons() {
        if (scriptModelButton != null) scriptModelButton.setText(scriptModelReady() ? "✓ نموذج الكتابة المحلي جاهز" : "تنزيل نموذج الكتابة المفتوح (491 ميغابايت)");
        if (voiceModelButton != null) voiceModelButton.setText(LocalAiMedia.voiceReady(this) ? "✓ الصوت العربي Nabra جاهز" : "تنزيل الصوت العربي المفتوح (83 ميغابايت)");
    }

    private void confirmModelDownload(boolean voice) {
        String title = voice ? "تنزيل الصوت العربي" : "تنزيل نموذج الكتابة";
        String message = voice
                ? "سيُنزل نموذج Nabra المفتوح للصوت العربي (نحو 83 ميغابايت) مرة واحدة. سيُحفظ داخل التطبيق ويعمل دون اتصال. هل تبدأ الآن؟"
                : "سيُنزل نموذج Qwen المفتوح للكتابة (نحو 491 ميغابايت) مرة واحدة. يحتاج مساحة وذاكرة كافية، ويعمل بعدها دون اتصال. هل تبدأ الآن؟";
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("ليس الآن", null)
                .setPositiveButton("تنزيل", (dialog, which) -> downloadModel(voice)).show();
    }

    private void downloadModel(boolean voice) {
        Button target = voice ? voiceModelButton : scriptModelButton;
        if (target != null) target.setEnabled(false);
        setStatus(voice ? "جارٍ تنزيل الصوت العربي… 0%" : "جارٍ تنزيل نموذج الكتابة… 0%", GOLD);
        executor.execute(() -> {
            try {
                LocalAiMedia.ProgressListener listener = percent -> mainHandler.post(() -> setStatus(
                        (voice ? "جارٍ تنزيل الصوت العربي… " : "جارٍ تنزيل نموذج الكتابة… ") + percent + "%", GOLD));
                if (voice) LocalAiMedia.downloadVoiceModel(this, listener);
                else LocalAiMedia.downloadScriptModel(this, listener);
                mainHandler.post(() -> {
                    if (target != null) target.setEnabled(true);
                    refreshModelButtons();
                    setStatus(voice ? "اكتمل تنزيل الصوت العربي. صار متاحًا دون اتصال." : "اكتمل تنزيل نموذج الكتابة. صار متاحًا دون اتصال.", GOLD);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (target != null) target.setEnabled(true);
                    setStatus(error.getMessage() == null ? "تعذر تنزيل النموذج." : error.getMessage(), Color.rgb(255, 130, 115));
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
        if (!scriptModelReady()) {
            setStatus("نزّل نموذج الكتابة المحلي أولًا من الزر أعلاه.", Color.rgb(255, 130, 115));
            return;
        }
        generateButton.setEnabled(false);
        setStatus("يكتب النموذج على الهاتف الآن… قد يستغرق دقيقة على الأجهزة المتوسطة.", GOLD);
        String duration = String.valueOf(durationSpinner.getSelectedItem());
        String style = String.valueOf(styleSpinner.getSelectedItem());
        String platform = platformSpinner == null ? "TikTok / Reels — عمودي 9:16" : String.valueOf(platformSpinner.getSelectedItem());
        String references = referenceInput == null ? "" : referenceInput.getText().toString().trim();
        executor.execute(() -> {
            try {
                String result = LocalAiMedia.generateScript(this, topic, duration, style, platform, references);
                String generatedVoicePath = "";
                String voiceIssue = "";
                if (LocalAiMedia.voiceReady(this)) {
                    String narration = narrationForSpeech(result);
                    try {
                        generatedVoicePath = LocalAiMedia.synthesizeArabic(this, narration);
                    } catch (Exception voiceError) {
                        voiceIssue = voiceError.getMessage() == null ? "تعذر تجهيز الصوت تلقائيًا." : voiceError.getMessage();
                    }
                }
                final String finalVoicePath = generatedVoicePath;
                final String finalVoiceIssue = voiceIssue;
                mainHandler.post(() -> {
                    scriptOutput.setText(result);
                    lastVoiceText = finalVoicePath.isEmpty() ? "" : narrationForSpeech(result);
                    lastVoicePath = finalVoicePath;
                    if (exportAudioButton != null) exportAudioButton.setEnabled(!finalVoicePath.isEmpty());
                    generateButton.setEnabled(true);
                    saveProject(false);
                    if (!finalVoicePath.isEmpty()) setStatus("اكتمل السكريبت والمشاهد وتجهيز التعليق الصوتي. راجع المعلومات قبل النشر.", GOLD);
                    else if (!finalVoiceIssue.isEmpty()) setStatus("اكتمل السكريبت، لكن تعذر تجهيز الصوت: " + finalVoiceIssue, Color.rgb(255, 190, 100));
                    else setStatus("اكتمل السكريبت والمشاهد محليًا. نزّل Nabra لمعاينة التعليق الصوتي.", GOLD);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    generateButton.setEnabled(true);
                    setStatus(error.getMessage() == null ? "تعذر تشغيل النموذج على هذا الهاتف." : error.getMessage(), Color.rgb(255, 130, 115));
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
        if (!LocalAiMedia.voiceReady(this)) {
            setStatus("نزّل صوت Nabra العربي من الزر أعلاه أولًا.", Color.rgb(255, 130, 115));
            return;
        }
        String speech = narrationForSpeech(scriptOutput.getText().toString());
        if (speech.length() < 20) {
            setStatus("لم أجد نصًا كافيًا للراوي. أعد توليد السكريبت أو أضف نص التعليق الصوتي الكامل.", Color.rgb(255, 130, 115));
            return;
        }
        final String finalSpeech = speech;
        previewButton.setEnabled(false);
        if (!lastVoicePath.isEmpty() && lastVoiceText.equals(finalSpeech) && new java.io.File(lastVoicePath).isFile()) {
            previewButton.setEnabled(true);
            playVoice(lastVoicePath);
            return;
        }
        setStatus("يجري تحويل نص الراوي فقط إلى صوت عربي محليًا…", GOLD);
        executor.execute(() -> {
            try {
                String path = LocalAiMedia.synthesizeArabic(this, finalSpeech);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    lastVoicePath = path;
                    lastVoiceText = finalSpeech;
                    if (exportAudioButton != null) exportAudioButton.setEnabled(true);
                    previewButton.setEnabled(true);
                    playVoice(path);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    previewButton.setEnabled(true);
                    setStatus(error.getMessage() == null ? "تعذرت معاينة الصوت." : error.getMessage(), Color.rgb(255, 130, 115));
                });
            }
        });
    }

    private void playVoice(String path) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(path);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> setStatus("انتهت معاينة الصوت العربي.", GOLD));
            player.setOnErrorListener((mp, what, extra) -> {
                setStatus("تعذر تشغيل ملف الصوت على هذا الهاتف.", Color.rgb(255, 130, 115));
                return true;
            });
            if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
            mediaPlayer = player;
            player.prepareAsync();
            setStatus("تعمل معاينة Nabra العربية؛ احفظ الملف بصيغة WAV عند الحاجة.", GOLD);
        } catch (Exception error) {
            setStatus("تعذر فتح معاينة الصوت.", Color.rgb(255, 130, 115));
        }
    }

    private void exportAudio() {
        if (lastVoicePath.isEmpty() || !new java.io.File(lastVoicePath).isFile()) {
            setStatus("أنشئ معاينة التعليق الصوتي أولًا، ثم يمكنك حفظ ملف WAV.", Color.rgb(255, 130, 115));
            return;
        }
        String currentSpeech = narrationForSpeech(scriptOutput.getText().toString());
        if (!lastVoiceText.equals(currentSpeech)) {
            setStatus("تغير نص السكريبت منذ إنشاء الصوت. أعد معاينة الصوت قبل التصدير.", Color.rgb(255, 130, 115));
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
        if ((requestCode != CREATE_PDF_REQUEST && requestCode != CREATE_AUDIO_REQUEST) || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
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
        if (topic.isEmpty() || script.isEmpty()) {
            socialStatus.setText("احفظ مشروعًا أولًا من تبويب المساعد.");
            return;
        }
        String intro = getNarrationText(script).replace('\n', ' ').trim();
        if (intro.length() > 220) intro = intro.substring(0, 217) + "…";
        socialDraftInput.setText(topic + "\n\n" + intro + "\n\n#WorldNews #أخبار_العالم");
        socialStatus.setText("هذه مسودة فقط. لم يُرسل أي منشور إلى X أو Facebook.");
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
        copyToClipboard("مسودة منشور World News", socialDraftInput.getText().toString());
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
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
