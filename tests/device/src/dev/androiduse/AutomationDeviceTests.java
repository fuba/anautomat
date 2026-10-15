package dev.androiduse;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** Real isolated-process tests. No network, user actions, credentials, or external apps. */
public final class AutomationDeviceTests extends Instrumentation {
 public void onCreate(Bundle state){super.onCreate(state);start();}
 JSONObject execute(String code,String state,boolean success)throws Exception{
  CountDownLatch done=new CountDownLatch(1);JSONObject[] result={null};String[] error={""};SandboxClient[] client={null};
  runOnMainSync(()->{client[0]=new SandboxClient(getTargetContext());client[0].run(code,"{\"values\":[2,3,5]}","{\"width\":900}",state,(value,message)->{result[0]=value;error[0]=message;done.countDown();});});
  if(!done.await(20,TimeUnit.SECONDS)){runOnMainSync(()->client[0].cancel());throw new AssertionError("Sandbox callback timed out");}
  if(success&&(result[0]==null||!error[0].isEmpty()))throw new AssertionError("Expected sandbox result: "+error[0]);
  if(!success&&(result[0]!=null||error[0].isEmpty()))throw new AssertionError("Unsafe program succeeded");return result[0];
 }
 public void onStart(){Bundle result=new Bundle();int code=-1;try{
  android.content.pm.ServiceInfo info=getTargetContext().getPackageManager().getServiceInfo(new ComponentName(getTargetContext(),SandboxService.class),0);
  if(info.exported||(info.flags&android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS)==0)throw new AssertionError("Service is not private and isolated");
  JSONObject arithmetic=execute("ctx.state.put(\"n\",ctx.state.getInt(\"n\")+1);ctx.log(\"sum\");return new JSONObject().put(\"sum\",ctx.input.getJSONArray(\"values\").getInt(0)+8);","{\"n\":4}",true);
  if(arithmetic.getJSONObject("result").getInt("sum")!=10||arithmetic.getJSONObject("state").getInt("n")!=5)throw new AssertionError("On-device Java compile/state failed");
  execute("return System.getProperty(\"user.home\");","{}",false);
  execute("return new java.io.File(\"/data/data/dev.androiduse\");","{}",false);
  execute("return new java.net.Socket();","{}",false);
  execute("return ctx.getClass();","{}",false);
  execute("while(true) {}","{}",false);
  execute("return new byte[160*1024*1024];","{}",false);
  if(execute("return 7;","{}",true).getInt("result")!=7)throw new AssertionError("Parent failed after child termination");
  CountDownLatch cancelledReply=new CountDownLatch(1);runOnMainSync(()->{SandboxClient cancelled=new SandboxClient(getTargetContext());cancelled.run("while(true){}","{}","{}","{}",(value,error)->cancelledReply.countDown());cancelled.cancel();});
  if(cancelledReply.await(300,TimeUnit.MILLISECONDS))throw new AssertionError("Cancelled invocation delivered a stale result");
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  CountDownLatch observed=new CountDownLatch(1);Harness[] service={null};
  runOnMainSync(()->{try{Harness h=new Harness();h.attach(getTargetContext());h.running=true;h.generation=1;h.screenWidth=900;h.screenHeight=1600;h.done=observed;service[0]=h;h.runCode(new JSONObject().put("input",JSONObject.NULL).put("code","ctx.state.put(\"computed\",6*7);ctx.macro(new JSONArray().put(new JSONObject().put(\"type\",\"text\").put(\"text\",\"A\").put(\"expect_package\",\"dev.androiduse.tests\")).put(new JSONObject().put(\"type\",\"text\").put(\"text\",\"B\").put(\"expect_package\",\"dev.androiduse.tests\")).put(new JSONObject().put(\"type\",\"press_enter\").put(\"expect_package\",\"dev.androiduse.tests\")));return ctx.state.getInt(\"computed\");"),1);}catch(Exception e){throw new RuntimeException(e);}});
  if(!observed.await(20,TimeUnit.SECONDS))throw new AssertionError("Sandbox-to-macro integration timed out");
  runOnMainSync(()->{Harness h=service[0];if(!h.actions.equals("text:text:press_enter:")||h.sandboxState.optInt("computed")!=42||h.observations!=1||h.macro!=null)throw new AssertionError("Sandbox macro/state integration failed");h.running=false;h.generation++;});
  runOnMainSync(()->{try{Harness h=new Harness();h.attach(getTargetContext());h.running=true;h.generation=1;h.screenWidth=900;h.screenHeight=1600;h.hold=true;h.runMacro(new MacroPlan(new JSONArray("[{type:'home'},{type:'back'}]"),900,1600),1);h.pauseGoal("test pause");h.pending.run();if(!h.actions.equals("home:")||!h.paused||h.macro!=null||h.history.optJSONObject(h.history.length()-1).optInt("completed")!=0)throw new AssertionError("Paused macro continued");h.running=false;h.generation++;}catch(Exception e){throw new RuntimeException(e);}});
  runOnMainSync(activity::finish);
  result.putString("stream","PASS: isolated UID process, on-device Java compilation/DEX/JSON/state, API allowlist rejection, loops and memory pressure terminate safely, cancellation, code-to-macro execution and pause without replay\n");
 }catch(Throwable e){code=1;result.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}finish(code,result);}
 static final class Harness extends AgentService {
  String actions="";boolean hold;Runnable pending;int observations;CountDownLatch done;
  void attach(Context context){attachBaseContext(context);}
  @Override void log(String text){}
  @Override void logSource(String code){}
  @Override public android.view.accessibility.AccessibilityNodeInfo getRootInActiveWindow(){android.view.accessibility.AccessibilityNodeInfo node=android.view.accessibility.AccessibilityNodeInfo.obtain();node.setPackageName("dev.androiduse.tests");return node;}
  @Override void performPrimitive(JSONObject action,int id,Runnable success,java.util.function.Consumer<String> failure){actions+=action.optString("type")+":";if(hold)pending=success;else success.run();}
  @Override void observe(int id){if(running&&generation==id){observations++;if(done!=null)done.countDown();}}
 }
}
