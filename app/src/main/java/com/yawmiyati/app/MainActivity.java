package com.yawmiyati.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.*;
import java.time.Instant;
import java.util.*;

public class MainActivity extends Activity {
    static final int REQ_NOTIFICATIONS = 7001;
    WebView webView;
    static final String PREFS="yawmiyati_native";
    static final String ALARMS="alarms";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(238,246,242));
        getWindow().setNavigationBarColor(Color.rgb(238,246,242));
        if(Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(Color.rgb(238,246,242));
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){ v.postDelayed(() -> syncNotificationStateToJs(),350); }
        });
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        webView.addJavascriptInterface(new NativeBridge(this),"Android");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
        ViewCompat.setOnApplyWindowInsetsListener(webView,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(webView);
        createChannels();
    }

    @Override public void onResume(){super.onResume(); if(webView!=null) webView.postDelayed(this::syncNotificationStateToJs,250);}
    void syncNotificationStateToJs(){ if(webView==null)return; boolean enabled=areNotificationsEnabled(); webView.evaluateJavascript("try{if(window.onNativeNotificationState)window.onNativeNotificationState("+enabled+")}catch(e){}",null); }
    boolean areNotificationsEnabled(){
        if(!NotificationManagerCompat.from(this).areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=getSystemService(NotificationManager.class);
            NotificationChannel a=nm.getNotificationChannel("prayer_adhan");
            NotificationChannel r=nm.getNotificationChannel("prayer_reminder");
            return (a==null||a.getImportance()!=NotificationManager.IMPORTANCE_NONE) && (r==null||r.getImportance()!=NotificationManager.IMPORTANCE_NONE);
        }
        return true;
    }
    boolean exactAlarmAllowed(){ if(Build.VERSION.SDK_INT<31)return true; AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE); return am.canScheduleExactAlarms(); }
    void createChannels(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=getSystemService(NotificationManager.class);
            NotificationChannel reminder=new NotificationChannel("prayer_reminder","تذكيرات الصلاة",NotificationManager.IMPORTANCE_HIGH);
            reminder.enableVibration(true); nm.createNotificationChannel(reminder);
            NotificationChannel prayer=new NotificationChannel("prayer_adhan","أذان الصلاة",NotificationManager.IMPORTANCE_HIGH);
            prayer.enableVibration(true); nm.createNotificationChannel(prayer);
        }
    }
    void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);} else syncNotificationStateToJs();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_NOTIFICATIONS){createChannels();syncNotificationStateToJs();if(areNotificationsEnabled())rescheduleAll(this);}}

    static int alarmId(String key){return Math.abs(key.hashCode());}
    public static void scheduleOne(Context ctx,String name,String iso,int reminderMinutes,boolean enabled){
        if(!enabled)return;
        try{
            long prayerAt=Instant.parse(iso).toEpochMilli();
            SharedPreferences sp=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            JSONObject rec=new JSONObject().put("name",name).put("iso",iso).put("reminder",reminderMinutes);
            JSONArray arr;
            try{arr=new JSONArray(sp.getString(ALARMS,"[]"));}catch(Exception e){arr=new JSONArray();}
            JSONArray out=new JSONArray();
            for(int j=0;j<arr.length();j++){JSONObject x=arr.getJSONObject(j);if(!name.equals(x.optString("name")))out.put(x);} out.put(rec);
            sp.edit().putString(ALARMS,out.toString()).apply();
            scheduleAt(ctx,name,prayerAt,"adhan");
            if(reminderMinutes>0)scheduleAt(ctx,name,prayerAt-reminderMinutes*60000L,"reminder");
        }catch(Exception ignored){}
    }
    static void scheduleAt(Context ctx,String name,long at,String kind){
        if(at<=System.currentTimeMillis())return;
        AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(ctx,PrayerAlarmReceiver.class).putExtra("name",name).putExtra("kind",kind);
        PendingIntent pi=PendingIntent.getBroadcast(ctx,alarmId(name+kind),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            if(Build.VERSION.SDK_INT>=23){
                if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}
                else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            } else am.setExact(AlarmManager.RTC_WAKEUP,at,pi);
        }catch(SecurityException e){try{am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}catch(Exception ignored){}}
    }
    static void cancelAllAlarms(Context ctx){
        AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
        for(String n:names)for(String kind:new String[]{"adhan","reminder"}){Intent i=new Intent(ctx,PrayerAlarmReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(ctx,alarmId(n+kind),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);am.cancel(pi);pi.cancel();}
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(ALARMS).apply();
    }
    static void rescheduleAll(Context ctx){
        if(Build.VERSION.SDK_INT>=33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            SharedPreferences sp=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray arr=new JSONArray(sp.getString(ALARMS,"[]"));
            for(int i=0;i<arr.length();i++){JSONObject x=arr.getJSONObject(i);long t=Instant.parse(x.getString("iso")).toEpochMilli(); if(t>System.currentTimeMillis()){scheduleAt(ctx,x.getString("name"),t,"adhan");int rm=x.optInt("reminder",0);if(rm>0)scheduleAt(ctx,x.getString("name"),t-rm*60000L,"reminder");}}
        }catch(Exception ignored){}
    }

    public static class NativeBridge{
        final MainActivity a; NativeBridge(MainActivity x){a=x;}
        @JavascriptInterface public void requestNotificationPermission(){a.runOnUiThread(a::requestNotificationPermission);}
        @JavascriptInterface public boolean areNotificationsEnabled(){return a.areNotificationsEnabled();}
        @JavascriptInterface public boolean isExactAlarmAllowed(){return a.exactAlarmAllowed();}
        @JavascriptInterface public void requestExactAlarmAccess(){if(Build.VERSION.SDK_INT>=31){try{a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+a.getPackageName())));}catch(Exception e){Toast.makeText(a,"افتح إعدادات المنبّهات والتذكيرات للتطبيق",Toast.LENGTH_LONG).show();}}}
        @JavascriptInterface public void schedulePrayerAlarm(String name,String iso,String json){try{JSONObject o=new JSONObject(json);scheduleOne(a,name,iso,o.optInt("reminderMinutes",0),o.optBoolean("enabled",true));}catch(Exception ignored){}}
        @JavascriptInterface public void cancelPrayerAlarms(){cancelAllAlarms(a);}
        @JavascriptInterface public void sendTestNotification(){a.runOnUiThread(()->PrayerAlarmReceiver.showTest(a));}
        @JavascriptInterface public void setCity(String city){}
    }
}
