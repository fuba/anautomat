package dev.androiduse;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** One disposable, permissionless process per invocation. Never pass Context to the engine. */
public final class SandboxService extends Service {
 static final int RUN=1,CANCEL=2,RESULT=3;
 static final String DESCRIPTOR="dev.androiduse.sandbox";
 final Handler handler=new Handler(Looper.getMainLooper());boolean started;volatile long memoryBaseline,deadline;
 final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper()){
  public void handleMessage(Message message){
   if(!android.os.Process.isIsolated()){stopSelf();return;}
   if(message.what==CANCEL){die();return;}
   if(message.what!=RUN||started||message.replyTo==null)return;started=true;
   Bundle input=message.getData();Messenger reply=message.replyTo;
   memoryBaseline=Debug.getPss()*1024L;deadline=SystemClock.elapsedRealtime()+SandboxEngine.MAX_START_MILLIS;
   Runnable watchdog=new Runnable(){public void run(){if(SystemClock.elapsedRealtime()>deadline||Debug.getPss()*1024L-memoryBaseline>80L*1024*1024){die();return;}handler.postDelayed(this,50);}};
   handler.postDelayed(watchdog,50);
   new Thread(()->{Bundle result=new Bundle();try{
    JSONObject request=new JSONObject().put("Code",input.getString("code","")).put("Input",input.getString("input","{}")).put("Observation",input.getString("observation","{}")).put("State",input.getString("state","{}"));
    StarlarkNative.ensureLoaded();deadline=SystemClock.elapsedRealtime()+SandboxEngine.MAX_MILLIS+500;
    byte[] bytes=StarlarkNative.execute(request.toString().getBytes(StandardCharsets.UTF_8));if(bytes==null||bytes.length>SandboxEngine.MAX_OUTPUT)throw new Exception("Invalid Starlark response");
    String output=new String(bytes,StandardCharsets.UTF_8);JSONObject parsed=AutomationJson.object(output,SandboxEngine.MAX_OUTPUT);if(parsed.has("error"))throw new Exception(parsed.getString("error"));
    result.putString("output",output);
   }catch(Exception e){String error=e.getMessage();result.putString("error",error==null?"Sandbox execution failed":error.substring(0,Math.min(512,error.length())));}catch(Throwable e){die();return;}
   result.putBoolean("isolated",android.os.Process.isIsolated());handler.post(()->{handler.removeCallbacks(watchdog);try{Message response=Message.obtain(null,RESULT);response.setData(result);reply.send(response);}catch(RemoteException e){die();}});
   },"isolated-code").start();
  }
 });
 public IBinder onBind(Intent intent){return android.os.Process.isIsolated()?messenger.getBinder():null;}
 public boolean onUnbind(Intent intent){die();return false;}
 public void onDestroy(){die();super.onDestroy();}
 static void die(){if(android.os.Process.isIsolated())android.os.Process.killProcess(android.os.Process.myPid());}
}
