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
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Multi-video dashboard for offline synthetic traffic-detection tests.
 * Video URLs are labels only: this activity never opens YouTube or sends views.
 */
public final class VisitBotLabActivity extends Activity {
    private static final int NAVY=Color.rgb(20,40,63), PAPER=Color.rgb(245,241,233);
    private static final int INK=Color.rgb(30,38,48), GREEN=Color.rgb(35,115,82);
    private static final int MAX_SESSIONS=20000, BATCH_SIZE=100;
    private static final String PREFS="visit-lab-dashboard", URLS_KEY="video_urls";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private final ArrayList<String> videoUrls=new ArrayList<>();
    private final Map<String,String> reports=new HashMap<>();
    private EditText urlInput,countInput;
    private LinearLayout videoList;
    private TextView status,overall;
    private SharedPreferences preferences;
    private String activeUrl;
    private int targetCount,completedCount;
    private boolean running;
    private final SyntheticTrafficEngine.BatchResult metrics=new SyntheticTrafficEngine.BatchResult();

    private final Runnable simulationTick=new Runnable(){
        @Override public void run(){
            if(!running)return;
            int batch=Math.min(BATCH_SIZE,targetCount-completedCount);
            SyntheticTrafficEngine.BatchResult result=SyntheticTrafficEngine.generate(batch,random);
            metrics.add(result);
            completedCount+=batch;
            status.setText("اختبار محلي: "+completedCount+" / "+targetCount+" جلسة — لا يوجد اتصال بيوتيوب");
            if(completedCount>=targetCount){
                running=false;
                String report="جلسات: "+metrics.total+" | طبيعية: "+metrics.normal+" | آلية: "+metrics.automated
                    +" | اكتشاف صحيح: "+metrics.truePositive+" | إنذار كاذب: "+metrics.falsePositive
                    +" | حالات فائتة: "+metrics.falseNegative
                    +" | Accuracy: "+pct(metrics.accuracy())+" | Precision: "+pct(metrics.precision())
                    +" | Recall: "+pct(metrics.recall());
                reports.put(activeUrl,report);
                status.setText("اكتمل الاختبار المحلي للفيديو المحدد.");
                setControlsEnabled(true);
                renderVideoList();
                updateOverall();
            }else handler.postDelayed(this,20L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY); getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        preferences=getSharedPreferences(PREFS,MODE_PRIVATE);
        loadUrls();

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(PAPER);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(20)); scroll.addView(root);

        TextView title=text("Visit Detection Dashboard",23,NAVY,true); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView subtitle=text("لوحة متابعة فيديوهات القناة التجريبية",15,INK,false); subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView safety=text("وضع اختبار محلي فقط: نحتفظ برابط الفيديو كمرجع، ونولّد بيانات اصطناعية داخل التطبيق. لا يتم فتح يوتيوب أو إرسال مشاهدات أو زيارات إليه.",13,GREEN,true);
        safety.setPadding(dp(12),dp(10),dp(12),dp(10)); safety.setBackgroundColor(Color.rgb(229,239,231));
        root.addView(safety,new LinearLayout.LayoutParams(-1,-2));

        addLabel(root,"أضف رابط فيديو من القناة التجريبية");
        urlInput=new EditText(this); urlInput.setSingleLine(true); urlInput.setTextSize(15);
        urlInput.setHint("https://www.youtube.com/watch?v=...");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(urlInput,new LinearLayout.LayoutParams(-1,dp(52)));
        Button add=button("إضافة الفيديو إلى اللوحة",GREEN); add.setOnClickListener(v->addVideo());
        root.addView(add,buttonParams());

        addLabel(root,"عدد جلسات الاختبار لكل فيديو (1 إلى 20000)");
        countInput=new EditText(this); countInput.setSingleLine(true); countInput.setText("1000");
        countInput.setTextSize(18); countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(countInput,new LinearLayout.LayoutParams(-1,dp(52)));

        status=text("أضف روابط الفيديوهات، ثم ابدأ اختبار أي فيديو من القائمة.",14,INK,false);
        status.setPadding(0,dp(12),0,dp(8)); root.addView(status);
        overall=text("",13,NAVY,true); overall.setPadding(dp(10),dp(10),dp(10),dp(10));
        overall.setBackgroundColor(Color.WHITE); root.addView(overall,new LinearLayout.LayoutParams(-1,-2));

        TextView listTitle=text("فيديوهات اللوحة ونتائجها",18,NAVY,true);
        listTitle.setPadding(0,dp(18),0,dp(8)); root.addView(listTitle);
        videoList=new LinearLayout(this); videoList.setOrientation(LinearLayout.VERTICAL); root.addView(videoList);
        Button clear=button("مسح قائمة الفيديوهات المحفوظة",Color.rgb(95,103,112));
        clear.setOnClickListener(v->{if(!running){videoUrls.clear();reports.clear();saveUrls();renderVideoList();updateOverall();status.setText("تم مسح القائمة المحلية.");}});
        LinearLayout.LayoutParams cp=buttonParams();cp.topMargin=dp(12);root.addView(clear,cp);
        setContentView(scroll);
        renderVideoList();updateOverall();
    }

    private void addVideo(){
        String raw=urlInput.getText().toString().trim();
        if(!isYoutubeUrl(raw)){urlInput.setError("أدخل رابط HTTPS صحيحًا من youtube.com أو youtu.be");return;}
        if(videoUrls.contains(raw)){urlInput.setError("الرابط موجود بالفعل في اللوحة");return;}
        videoUrls.add(raw);reports.remove(raw);saveUrls();urlInput.setText("");
        status.setText("تمت إضافة الفيديو إلى القائمة. لم يتم فتح الرابط.");
        renderVideoList();updateOverall();
    }

    private void startTest(String url){
        if(running)return;
        int requested;
        try{requested=Integer.parseInt(countInput.getText().toString().trim());}
        catch(Exception e){countInput.setError("اكتب عددًا من 1 إلى "+MAX_SESSIONS);return;}
        if(requested<1||requested>MAX_SESSIONS){countInput.setError("الحد المسموح من 1 إلى "+MAX_SESSIONS);return;}
        activeUrl=url;targetCount=requested;completedCount=0;resetMetrics();running=true;
        setControlsEnabled(false);
        status.setText("بدأ توليد بيانات اصطناعية محليًا لهذا الرابط — لا يوجد اتصال بيوتيوب.");
        handler.post(simulationTick);
    }

    private void removeVideo(String url){
        if(running)return;
        videoUrls.remove(url);reports.remove(url);saveUrls();renderVideoList();updateOverall();
    }

    private void renderVideoList(){
        if(videoList==null)return;
        videoList.removeAllViews();
        if(videoUrls.isEmpty()){
            TextView empty=text("لسه مفيش فيديوهات. الصق رابط فيديو القناة التجريبية واضغط إضافة الفيديو.",14,INK,false);
            empty.setPadding(dp(12),dp(14),dp(12),dp(14));empty.setBackgroundColor(Color.WHITE);videoList.addView(empty);return;
        }
        for(String url:videoUrls){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=dp(9);videoList.addView(card,cardParams);
            TextView link=text(url,12,NAVY,true);link.setTextIsSelectable(true);card.addView(link);
            String report=reports.get(url);
            TextView result=text(report==null?"لم يتم اختبار هذا الفيديو بعد.":report,12,INK,false);
            result.setPadding(0,dp(8),0,dp(8));card.addView(result);
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);card.addView(actions);
            Button run=button("اختبر هذا الفيديو",GREEN);run.setOnClickListener(v->startTest(url));
            Button remove=button("حذف",Color.rgb(110,70,70));remove.setOnClickListener(v->removeVideo(url));
            LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(46),1);half.setMargins(dp(3),0,dp(3),0);
            actions.addView(run,half);actions.addView(remove,new LinearLayout.LayoutParams(0,dp(46),1));
            run.setEnabled(!running);remove.setEnabled(!running);
        }
    }

    private void updateOverall(){
        if(overall==null)return;
        int tested=0;for(String url:videoUrls)if(reports.containsKey(url))tested++;
        overall.setText("إجمالي الفيديوهات: "+videoUrls.size()+" | فيديوهات تم اختبارها: "+tested
            +"\nكل فيديو له تقرير مستقل. النتائج الحالية ناتجة عن بيانات اصطناعية، وليست تحليلاً لمشاهدات يوتيوب الفعلية.");
    }

    private void setControlsEnabled(boolean enabled){
        urlInput.setEnabled(enabled);countInput.setEnabled(enabled);
        renderVideoList();
    }
    private void loadUrls(){
        JSONArray array;
        try{array=new JSONArray(preferences.getString(URLS_KEY,"[]"));for(int i=0;i<array.length();i++){
            String value=array.optString(i,"");if(isYoutubeUrl(value)&&!videoUrls.contains(value))videoUrls.add(value);
        }}catch(Exception ignored){}
    }
    private void saveUrls(){JSONArray array=new JSONArray();for(String url:videoUrls)array.put(url);
        preferences.edit().putString(URLS_KEY,array.toString()).apply();}
    private boolean isYoutubeUrl(String raw){
        try{Uri uri=Uri.parse(raw);String host=uri.getHost();return "https".equalsIgnoreCase(uri.getScheme())&&host!=null&&
            (host.equalsIgnoreCase("youtube.com")||host.equalsIgnoreCase("www.youtube.com")||
             host.equalsIgnoreCase("m.youtube.com")||host.equalsIgnoreCase("youtu.be")||
             host.equalsIgnoreCase("www.youtube-nocookie.com"));
        }catch(Exception e){return false;}
    }
    private void resetMetrics(){
        metrics.total=0;metrics.normal=0;metrics.automated=0;metrics.truePositive=0;
        metrics.falsePositive=0;metrics.trueNegative=0;metrics.falseNegative=0;
        metrics.averageDurationSeconds=0;metrics.averageGapSeconds=0;
    }
    private String pct(double value){return String.format(Locale.US,"%.1f%%",value*100);}
    private void addLabel(LinearLayout parent,String value){TextView label=text(value,14,INK,true);label.setPadding(0,dp(14),0,dp(4));parent.addView(label);}
    private Button button(String label,int color){Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setTextSize(13);b.setBackgroundColor(color);return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);
        t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams buttonParams(){return new LinearLayout.LayoutParams(-1,dp(48));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){running=false;handler.removeCallbacks(simulationTick);super.onDestroy();}
}
