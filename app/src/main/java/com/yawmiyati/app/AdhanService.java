package com.yawmiyati.app;
import android.app.*;import android.content.*;import android.media.*;import android.os.*;import androidx.core.app.NotificationCompat;
public class AdhanService extends Service{
 MediaPlayer player; static final int ID=4801;
 @Override public void onCreate(){super.onCreate();String ch="prayer_adhan";Notification n=new NotificationCompat.Builder(this,ch).setSmallIcon(com.yawmiyati.app.R.mipmap.ic_launcher).setContentTitle("يومي — الأذان").setContentText("حان وقت الصلاة").setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true).build();startForeground(ID,n);}
 @Override public int onStartCommand(Intent i,int flags,int startId){try{if(player!=null){player.stop();player.release();}int resId=getResources().getIdentifier("adhan","raw",getPackageName()); if(resId==0)resId=R.raw.notification; player=MediaPlayer.create(this,resId);if(player!=null){player.setOnCompletionListener(mp->{try{stopForeground(true);}catch(Exception ignored){}stopSelf();});player.start();}}catch(Exception e){try{stopForeground(true);}catch(Exception ignored){}stopSelf();}return START_NOT_STICKY;}
 @Override public void onDestroy(){if(player!=null){try{if(player.isPlaying())player.stop();}catch(Exception ignored){}player.release();player=null;}super.onDestroy();}
 @Override public android.os.IBinder onBind(Intent i){return null;}
}
