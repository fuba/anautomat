package dev.androiduse;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** Keeps the phone-local OAuth listener alive while the system browser is foreground. */
public final class SignInService extends Service {
 public void onCreate(){super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("sign-in","ChatGPT sign-in",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE);Notification n=new Notification.Builder(this,"sign-in").setContentTitle(I18n.text(SignInService.this,"app_name")).setContentText(I18n.text(SignInService.this,"waiting_sign_in")).setSmallIcon(android.R.drawable.ic_lock_lock).setContentIntent(open).build();startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);}
 public int onStartCommand(Intent i,int flags,int id){new Handler().postDelayed(()->{if(!ChatGptAuth.get(this).signingIn)stopSelf();},2000);return START_NOT_STICKY;}
 public void onDestroy(){ChatGptAuth.get(this).cancel();super.onDestroy();}
 public IBinder onBind(Intent intent){return null;}
}
