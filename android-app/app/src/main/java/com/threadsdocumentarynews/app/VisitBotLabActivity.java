package com.threadsdocumentarynews.app;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
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
import java.util.Random;

/** Local synthetic session generator. The YouTube URL is a label only; no network traffic is sent. */
public final class VisitBotLabActivity extends Activity {
    private static final int NAVY=Color.rgb(20,40,63), PAPER=Color.rgb(245,241,233);
    private static final int INK=Color.rgb(30,38,48), GREEN=Color.rgb(35,115,82);
    private static final int MAX_SESSIONS=20000, BATCH_SIZE=100;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<String> logEntries=new ArrayList<>();
    private final Random random=new Random();
    private EditText urlInput,countInput;
    private TextView counter,status,summary,logView;
    private Button startButton,stopButton;
    private String testUrl="";
    private int targetCount,completedCount,naturalLikeCount,suspiciousLikeCount;
    private boolean running;

    private final Runnable simulationTick=new Runnable(){
        @Override public void run(){
            if(!running)return;
            int batch=Math.min(BATCH_SIZE,targetCount-completedCount);
            SyntheticTrafficEngine.BatchResult result = SyntheticTrafficEngine.generate(batch, random);
            naturalLikeCount += result.normal;
            suspiciousLikeCount += result.automated;
            completedCount+=batch;
            String stamp=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date());
            logEntries.add(0,stamp+" • توليد "+batch+" جلسة افتراضية • الإجمالي "+completedCount+"/"+targetCount);
            if(logEntries.size()>10)logEntries.remove(logEntries.size()-1);
            updateDisplay();
            if(completedCount>=targetCount)finishSimulation(); else handler.postDelayed(this,30L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY); getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(PAPER);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(14),dp(18),dp(20)); scroll.addView(root);
        TextView title=text("Visit Bot Lab",25,NAVY,true); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView subtitle=text("معمل توليد جلسات اختبار افتراضية",15,INK,false); subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(38)));
        TextView safety=text("اختبار محلي فقط: الرابط للتعريف بالفيديو محل الاختبار، ولا يتم فتحه أو إرسال مشاهدات أو زيارات إلى يوتيوب.",13,GREEN,true);
        safety.setPadding(dp(12),dp(10),dp(12),dp(10)); safety.setBackgroundColor(Color.rgb(229,239,231));
        root.addView(safety,new LinearLayout.LayoutParams(-1,-2));
        addLabel(root,"رابط فيديو يوتيوب محل الاختبار");
        urlInput=new EditText(this); urlInput.setSingleLine(true); urlInput.setTextSize(15);
        urlInput.setHint("https://www.youtube.com/watch?v=...");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(urlInput,new LinearLayout.LayoutParams(-1,dp(54)));
        addLabel(root,"عدد الجلسات المطلوب توليدها (1 إلى 20000)");
        countInput=new EditText(this); countInput.setSingleLine(true); countInput.setText("1000"); countInput.setTextSize(18);
        countInput.setInputType(InputType.TYPE_CLASS_NUMBER); countInput.setHint("اكتب العدد المطلوب");
        root.addView(countInput,new LinearLayout.LayoutParams(-1,dp(54)));
        counter=text("0 / 0 جلسة",27,NAVY,true); counter.setGravity(Gravity.CENTER); counter.setPadding(0,dp(16),0,dp(8));
        root.addView(counter,new LinearLayout.LayoutParams(-1,dp(58)));
        status=text("جاهز للاختبار.",14,INK,false); status.setGravity(Gravity.CENTER);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
        summary=text("أنماط تجريبية: —",13,INK,false); summary.setPadding(0,dp(8),0,dp(8)); root.addView(summary);
        startButton=makeButton("ابدأ توليد الجلسات",GREEN); startButton.setOnClickListener(v->startSimulation());
        root.addView(startButton,buttonParams());
        stopButton=makeButton("إيقاف",NAVY); stopButton.setEnabled(false); stopButton.setOnClickListener(v->stopSimulation());
        LinearLayout.LayoutParams sp=buttonParams(); sp.topMargin=dp(8); root.addView(stopButton,sp);
        Button reset=makeButton("تصفير النتائج",Color.rgb(95,103,112)); reset.setOnClickListener(v->resetSimulation());
        LinearLayout.LayoutParams rp=buttonParams(); rp.topMargin=dp(8); root.addView(reset,rp);
        TextView logTitle=text("سجل التنفيذ",17,NAVY,true); logTitle.setPadding(0,dp(22),0,dp(8)); root.addView(logTitle);
        logView=text("لا توجد جلسات بعد.",13,INK,false); logView.setPadding(dp(12),dp(12),dp(12),dp(12)); logView.setBackgroundColor(Color.WHITE);
        root.addView(logView,new LinearLayout.LayoutParams(-1,-2));
        setContentView(scroll); updateDisplay();
    }

    private void startSimulation(){
        if(running)return;
        String raw=urlInput.getText().toString().trim(); Uri uri=Uri.parse(raw); String host=uri.getHost();
        boolean youtube="https".equalsIgnoreCase(uri.getScheme())&&host!=null&&
          (host.equalsIgnoreCase("youtube.com")||host.equalsIgnoreCase("www.youtube.com")||
           host.equalsIgnoreCase("m.youtube.com")||host.equalsIgnoreCase("youtu.be"));
        if(!youtube){urlInput.setError("أدخل رابط HTTPS صحيحًا من youtube.com أو youtu.be");return;}
        int requested;
        try{requested=Integer.parseInt(countInput.getText().toString().trim());}
        catch(Exception e){countInput.setError("اكتب عددًا من 1 إلى "+MAX_SESSIONS);return;}
        if(requested<1||requested>MAX_SESSIONS){countInput.setError("الحد المسموح من 1 إلى "+MAX_SESSIONS);return;}
        testUrl=raw; targetCount=requested; completedCount=0; naturalLikeCount=0; suspiciousLikeCount=0; logEntries.clear();
        running=true; setInputsEnabled(false); startButton.setEnabled(false); stopButton.setEnabled(true);
        status.setText("يتم توليد بيانات افتراضية محليًا للرابط المحدد — لا يوجد اتصال بيوتيوب.");
        updateDisplay(); handler.post(simulationTick);
    }
    private void stopSimulation(){
        if(!running)return; running=false; handler.removeCallbacks(simulationTick); setInputsEnabled(true);
        startButton.setEnabled(true); stopButton.setEnabled(false);
        status.setText("توقف الاختبار عند "+completedCount+" جلسة افتراضية."); updateDisplay();
    }
    private void finishSimulation(){
        running=false; handler.removeCallbacks(simulationTick); setInputsEnabled(true);
        startButton.setEnabled(true); stopButton.setEnabled(false);
        status.setText("اكتمل توليد "+completedCount+" جلسة افتراضية محليًا."); updateDisplay();
    }
    private void resetSimulation(){
        if(running)stopSimulation(); completedCount=0; targetCount=0; naturalLikeCount=0; suspiciousLikeCount=0;
        testUrl=""; logEntries.clear(); setInputsEnabled(true); status.setText("جاهز لاختبار جديد."); updateDisplay();
    }
    private void setInputsEnabled(boolean enabled){urlInput.setEnabled(enabled);countInput.setEnabled(enabled);}
    private void updateDisplay(){
        counter.setText(completedCount+" / "+targetCount+" جلسة");
        summary.setText("جلسات بنمط طبيعي: "+naturalLikeCount+" | جلسات بنمط آلي: "+suspiciousLikeCount);
        if(logEntries.isEmpty())logView.setText("لا توجد جلسات بعد. أدخل الرابط والعدد ثم ابدأ الاختبار.");
        else{StringBuilder out=new StringBuilder();out.append("رابط الاختبار: ").append(testUrl).append('\n');
          for(String entry:logEntries)out.append(entry).append('\n'); logView.setText(out.toString().trim());}
    }
    private void addLabel(LinearLayout parent,String value){TextView label=text(value,14,INK,true);label.setPadding(0,dp(16),0,dp(4));parent.addView(label);}
    private Button makeButton(String label,int color){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setBackgroundColor(color);return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams buttonParams(){return new LinearLayout.LayoutParams(-1,dp(50));}
    @Override protected void onDestroy(){running=false;handler.removeCallbacks(simulationTick);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
