package com.threadsdocumentarynews.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Local-only visit simulation laboratory.
 * It creates synthetic records in memory and never opens network connections
 * or sends traffic to YouTube or any other public service.
 */
public final class VisitBotLabActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 40, 63);
    private static final int PAPER = Color.rgb(245, 241, 233);
    private static final int INK = Color.rgb(30, 38, 48);
    private static final int GREEN = Color.rgb(35, 115, 82);
    private static final int MAX_SESSIONS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> logEntries = new ArrayList<>();
    private EditText countInput;
    private TextView counter;
    private TextView status;
    private TextView logView;
    private Button startButton;
    private Button stopButton;
    private int targetCount;
    private int completedCount;
    private boolean running;

    private final Runnable simulationTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (completedCount >= targetCount) {
                finishSimulation();
                return;
            }
            completedCount++;
            String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            logEntries.add(0, stamp + "  •  جلسة افتراضية #" + completedCount + "  •  محاكاة محلية");
            if (logEntries.size() > 12) logEntries.remove(logEntries.size() - 1);
            updateDisplay();
            if (completedCount >= targetCount) finishSimulation();
            else handler.postDelayed(this, 25L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Visit Bot Lab");
        title.setTextColor(NAVY);
        title.setTextSize(25);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView subtitle = new TextView(this);
        subtitle.setText("معمل اختبار الزيارات — محاكاة محلية فقط");
        subtitle.setTextColor(INK);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView safety = new TextView(this);
        safety.setText("تنبيه: الأرقام هنا جلسات افتراضية للاختبار داخل التطبيق، ولا تُرسل مشاهدات إلى يوتيوب أو أي منصة.");
        safety.setTextColor(GREEN);
        safety.setTextSize(13);
        safety.setPadding(dp(12), dp(10), dp(12), dp(10));
        safety.setBackgroundColor(Color.rgb(229, 239, 231));
        root.addView(safety, new LinearLayout.LayoutParams(-1, -2));

        TextView countLabel = new TextView(this);
        countLabel.setText("عدد الجلسات التجريبية (من 1 إلى 1000)");
        countLabel.setTextColor(INK);
        countLabel.setTextSize(14);
        countLabel.setPadding(0, dp(18), 0, dp(4));
        root.addView(countLabel);

        countInput = new EditText(this);
        countInput.setSingleLine(true);
        countInput.setText("500");
        countInput.setTextSize(18);
        countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countInput.setHint("مثال: 500");
        root.addView(countInput, new LinearLayout.LayoutParams(-1, dp(54)));

        counter = new TextView(this);
        counter.setTextColor(NAVY);
        counter.setTextSize(27);
        counter.setTypeface(null, android.graphics.Typeface.BOLD);
        counter.setGravity(Gravity.CENTER);
        counter.setPadding(0, dp(16), 0, dp(8));
        root.addView(counter, new LinearLayout.LayoutParams(-1, dp(58)));

        status = new TextView(this);
        status.setTextColor(INK);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(34)));

        startButton = makeButton("ابدأ المحاكاة", GREEN);
        startButton.setOnClickListener(v -> startSimulation());
        root.addView(startButton, buttonParams());

        stopButton = makeButton("إيقاف", NAVY);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopSimulation());
        LinearLayout.LayoutParams stopParams = buttonParams();
        stopParams.topMargin = dp(8);
        root.addView(stopButton, stopParams);

        Button reset = makeButton("تصفير النتائج", Color.rgb(95, 103, 112));
        reset.setOnClickListener(v -> resetSimulation());
        LinearLayout.LayoutParams resetParams = buttonParams();
        resetParams.topMargin = dp(8);
        root.addView(reset, resetParams);

        TextView logTitle = new TextView(this);
        logTitle.setText("سجل آخر الجلسات");
        logTitle.setTextColor(NAVY);
        logTitle.setTextSize(17);
        logTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        logTitle.setPadding(0, dp(22), 0, dp(8));
        root.addView(logTitle);

        logView = new TextView(this);
        logView.setTextColor(INK);
        logView.setTextSize(13);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setBackgroundColor(Color.WHITE);
        root.addView(logView, new LinearLayout.LayoutParams(-1, -2));

        Button youtube = makeButton("فتح YouTube", NAVY);
        youtube.setOnClickListener(v -> startActivity(new android.content.Intent(this, YouTubeLabActivity.class)));
        LinearLayout.LayoutParams youtubeParams = buttonParams();
        youtubeParams.topMargin = dp(16);
        root.addView(youtube, youtubeParams);

        updateDisplay();
        setContentView(scroll);
    }

    private Button makeButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackgroundColor(color);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(-1, dp(50));
    }

    private void startSimulation() {
        if (running) return;
        int requested;
        try {
            requested = Integer.parseInt(countInput.getText().toString().trim());
        } catch (Exception error) {
            countInput.setError("اكتب رقمًا من 1 إلى 1000");
            return;
        }
        if (requested < 1 || requested > MAX_SESSIONS) {
            countInput.setError("الحد المسموح من 1 إلى 1000");
            return;
        }
        targetCount = requested;
        completedCount = 0;
        logEntries.clear();
        running = true;
        countInput.setEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        status.setText("المحاكاة تعمل محليًا...");
        updateDisplay();
        handler.post(simulationTick);
    }

    private void stopSimulation() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(simulationTick);
        countInput.setEnabled(true);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        status.setText("تم إيقاف المحاكاة عند " + completedCount + " جلسة افتراضية.");
        updateDisplay();
    }

    private void finishSimulation() {
        running = false;
        handler.removeCallbacks(simulationTick);
        countInput.setEnabled(true);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        status.setText("اكتملت المحاكاة — " + completedCount + " جلسة افتراضية.");
        updateDisplay();
    }

    private void resetSimulation() {
        stopSimulation();
        completedCount = 0;
        targetCount = 0;
        logEntries.clear();
        status.setText("جاهز لاختبار جديد.");
        updateDisplay();
    }

    private void updateDisplay() {
        counter.setText(completedCount + " / " + targetCount + " جلسة");
        if (logEntries.isEmpty()) {
            logView.setText("لا توجد جلسات بعد. اضغط «ابدأ المحاكاة» لتوليد بيانات اختبار محلية.");
        } else {
            StringBuilder text = new StringBuilder();
            for (String entry : logEntries) text.append(entry).append('\n');
            logView.setText(text.toString().trim());
        }
    }

    @Override protected void onDestroy() {
        running = false;
        handler.removeCallbacks(simulationTick);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
