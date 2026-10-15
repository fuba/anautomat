package dev.androiduse;

import android.app.Service;
import android.content.Intent;
import android.os.*;

/** One disposable, permissionless process per invocation. Never pass Context to the engine. */
public final class SandboxService extends Service {
 static final int RUN=1,CANCEL=2,RESULT=3;
 static final String DESCRIPTOR="dev.androiduse.sandbox";
 final Handler handler=new Handler(Looper.getMainLooper());boolean started;volatile long memoryBaseline,deadline,memoryBudget;
 final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper()){
  public void handleMessage(Message message){
   if(!android.os.Process.isIsolated()){stopSelf();return;}
   if(message.what==CANCEL){die();return;}
   if(message.what!=RUN||started||message.replyTo==null)return;started=true;
   Bundle input=message.getData();Messenger reply=message.replyTo;
   memoryBaseline=used();deadline=SystemClock.elapsedRealtime()+SandboxEngine.MAX_BUILD_MILLIS;memoryBudget=160L*1024*1024;
   Runnable watchdog=new Runnable(){public void run(){if(SystemClock.elapsedRealtime()>deadline||used()-memoryBaseline>memoryBudget){die();return;}handler.postDelayed(this,50);}};
   handler.postDelayed(watchdog,50);
   new Thread(()->{Bundle result=new Bundle();try{
    String output=SandboxEngine.run(input.getString("code",""),input.getString("input","{}"),input.getString("observation","{}"),input.getString("state","{}"),new AndroidJavaLoader(),()->{System.gc();memoryBaseline=used();memoryBudget=48L*1024*1024;deadline=SystemClock.elapsedRealtime()+SandboxEngine.MAX_MILLIS;});
    result.putString("output",output);
   }catch(Exception e){String error=e.getMessage();result.putString("error",error==null?"Sandbox execution failed":error.substring(0,Math.min(512,error.length())));}catch(Throwable e){die();return;}
   result.putBoolean("isolated",android.os.Process.isIsolated());handler.post(()->{handler.removeCallbacks(watchdog);try{Message response=Message.obtain(null,RESULT);response.setData(result);reply.send(response);}catch(RemoteException e){die();}});
   },"isolated-code").start();
  }
 });
 public IBinder onBind(Intent intent){return android.os.Process.isIsolated()?messenger.getBinder():null;}
 public boolean onUnbind(Intent intent){die();return false;}
 public void onDestroy(){die();super.onDestroy();}
 static long used(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
 static void die(){if(android.os.Process.isIsolated())android.os.Process.killProcess(android.os.Process.myPid());}
}
