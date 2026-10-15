package dev.androiduse;

import android.content.*;
import android.os.*;
import java.util.function.BiConsumer;
import org.json.*;

/** Main-thread lifecycle; the only cross-process values are bounded JSON strings. */
final class SandboxClient {
 final Context context;final Handler handler=new Handler(Looper.getMainLooper());
 boolean bound,finished;Messenger remote;ServiceConnection connection;Runnable timeout;
 SandboxClient(Context context){this.context=context;}
 void run(String code,String input,String observation,String state,BiConsumer<JSONObject,String> callback){
  if(connection!=null)throw new IllegalStateException("One invocation per client");
  if(code.length()>SandboxEngine.MAX_CODE||input.length()>SandboxEngine.MAX_INPUT||observation.length()>SandboxEngine.MAX_OBSERVATION||state.length()>SandboxEngine.MAX_STATE){finished=true;callback.accept(null,"Sandbox input limit exceeded");return;}
  Messenger replies=new Messenger(new Handler(Looper.getMainLooper()){public void handleMessage(Message message){if(finished||message.what!=SandboxService.RESULT)return;try{Bundle data=message.getData();String output=data.getString("output","");if(!data.getBoolean("isolated")||output.length()>SandboxEngine.MAX_OUTPUT)throw new IllegalArgumentException();JSONObject result=output.isEmpty()?null:AutomationJson.object(output,SandboxEngine.MAX_OUTPUT);String error=data.getString("error","");if(result==null&&error.isEmpty())error="Sandbox returned no result";if(error.length()>512)error="Sandbox execution failed";finish();callback.accept(result,error);}catch(Exception e){finish();callback.accept(null,"Invalid sandbox result");}}});
  connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName name,IBinder binder){if(finished)return;remote=new Messenger(binder);try{Message message=Message.obtain(null,SandboxService.RUN);Bundle data=new Bundle();data.putString("code",code);data.putString("input",input);data.putString("observation",observation);data.putString("state",state);message.setData(data);message.replyTo=replies;remote.send(message);}catch(Exception e){fail("Sandbox could not start");}}
   public void onServiceDisconnected(ComponentName name){if(!finished)fail("Sandbox process stopped or exceeded its limits");}
   public void onBindingDied(ComponentName name){if(!finished)fail("Sandbox process stopped");}
   public void onNullBinding(ComponentName name){if(!finished)fail("Sandbox isolation unavailable");}
   void fail(String error){finish();callback.accept(null,error);}
  };
  timeout=()->{if(!finished){finish();callback.accept(null,"Sandbox time limit exceeded");}};
  try{bound=context.bindIsolatedService(new Intent(context,SandboxService.class),Context.BIND_AUTO_CREATE,"run"+java.util.UUID.randomUUID().toString().replace("-",""),context.getMainExecutor(),connection);if(!bound){finish();callback.accept(null,"Sandbox binding failed");return;}handler.postDelayed(timeout,15000);}catch(Exception e){finish();callback.accept(null,"Sandbox binding failed");}
 }
 void cancel(){finish();}
 void finish(){if(finished)return;finished=true;if(timeout!=null)handler.removeCallbacks(timeout);if(remote!=null)try{remote.send(Message.obtain(null,SandboxService.CANCEL));}catch(RemoteException ignored){}if(bound){bound=false;try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}}remote=null;}
}
