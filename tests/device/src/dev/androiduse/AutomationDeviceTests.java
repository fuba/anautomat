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
  android.view.accessibility.AccessibilityNodeInfo guard=android.view.accessibility.AccessibilityNodeInfo.obtain();guard.setVisibleToUser(true);if(AgentService.visibleText(guard,"null",0,new int[1]))throw new AssertionError("Missing text satisfied a literal guard");guard.setText("null");if(!AgentService.visibleText(guard,"null",0,new int[1]))throw new AssertionError("Visible literal text guard failed");guard.setVisibleToUser(false);if(AgentService.visibleText(guard,"null",0,new int[1]))throw new AssertionError("Invisible text satisfied a guard");
  android.content.pm.ServiceInfo info=getTargetContext().getPackageManager().getServiceInfo(new ComponentName(getTargetContext(),SandboxService.class),0);
  if(info.exported||(info.flags&android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS)==0)throw new AssertionError("Service is not private and isolated");
  JSONObject arithmetic=execute("def main():\n    state[\"n\"] += 1\n    log(\"sum\")\n    return {\"sum\": input[\"values\"][0] + 8}\n","{\"n\":4}",true);
  if(arithmetic.getJSONObject("result").getInt("sum")!=10||arithmetic.getJSONObject("state").getInt("n")!=5)throw new AssertionError("On-device Starlark/state failed");
  for(String hostile:new String[]{"def main():\n    return open(\"/data/data/dev.androiduse\")\n","def main():\n    return socket(\"example.com\")\n","def main():\n    return Java.type(\"java.lang.Runtime\")\n","def main():\n    return main()\n","def main():\n    for i in range(10000000):\n        pass\n","def main():\n    return \"x\" * (160*1024*1024)\n"})execute(hostile,"{}",false);
  if(execute("def main():\n    return 7\n","{}",true).getInt("result")!=7)throw new AssertionError("Parent failed after child termination");
  String unicode=execute("def main():\n    return \"日本語 😀\\x00\"\n","{}",true).getString("result");if(!unicode.equals("日本語 😀\u0000"))throw new AssertionError("JNI UTF-8 round trip failed");
  CountDownLatch cancelledReply=new CountDownLatch(1);runOnMainSync(()->{SandboxClient cancelled=new SandboxClient(getTargetContext());cancelled.run("def main():\n    for i in range(10000000):\n        pass\n","{}","{}","{}",(value,error)->cancelledReply.countDown());cancelled.cancel();});
  if(cancelledReply.await(300,TimeUnit.MILLISECONDS))throw new AssertionError("Cancelled invocation delivered a stale result");
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  CountDownLatch observed=new CountDownLatch(1);Harness[] service={null};
  runOnMainSync(()->{try{Harness h=new Harness();h.attach(getTargetContext());h.running=true;h.generation=1;h.screenWidth=900;h.screenHeight=1600;h.done=observed;service[0]=h;h.runCode(new JSONObject().put("input",JSONObject.NULL).put("code","def main():\n    state[\"computed\"] = 6*7\n    ui.text(\"A\", app=\"dev.androiduse.tests\")\n    ui.text(\"B\", app=\"dev.androiduse.tests\")\n    ui.enter(app=\"dev.androiduse.tests\")\n    return state[\"computed\"]\n"),1);}catch(Exception e){throw new RuntimeException(e);}});
  if(!observed.await(20,TimeUnit.SECONDS))throw new AssertionError("Sandbox-to-macro integration timed out");
  runOnMainSync(()->{Harness h=service[0];if(!h.actions.equals("text:text:press_enter:")||h.sandboxState.optInt("computed")!=42||h.observations!=1||h.macro!=null)throw new AssertionError("Sandbox macro/state integration failed");h.running=false;h.generation++;});
  runOnMainSync(()->{try{Harness h=new Harness();h.attach(getTargetContext());h.running=true;h.generation=1;h.screenWidth=900;h.screenHeight=1600;h.hold=true;h.runMacro(new MacroPlan(new JSONArray("[{type:'home'},{type:'back'}]"),900,1600),1);h.pauseGoal("test pause");h.pending.run();if(!h.actions.equals("home:")||!h.paused||h.macro!=null||h.history.optJSONObject(h.history.length()-1).optInt("completed")!=0)throw new AssertionError("Paused macro continued");h.running=false;h.generation++;}catch(Exception e){throw new RuntimeException(e);}});
  targetTests(activity);
  runOnMainSync(activity::finish);
  result.putString("stream","PASS: isolated UID process, on-device Starlark/UTF-8/JSON/state, unavailable API rejection, execution and memory limits, cancellation, code-to-macro execution, unique UI targets and pause without replay\n");
 }catch(Throwable e){code=1;result.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}finish(code,result);}
 void targetTests(Activity activity)throws Exception{
  android.widget.EditText[] field={null};android.widget.TextView[] outcome={null};
  runOnMainSync(()->{android.widget.LinearLayout layout=new android.widget.LinearLayout(activity);layout.setOrientation(1);layout.setPadding(24,64,24,24);
   field[0]=new android.widget.EditText(activity);field[0].setHint("Result");field[0].setId(android.R.id.edit);layout.addView(field[0]);
   android.widget.Button button=new android.widget.Button(activity);button.setText("Verify");button.setAllCaps(false);button.setOnClickListener(v->outcome[0].setText(field[0].getText().toString().equals("338350")?"PASS":"FAIL"));layout.addView(button);
   outcome[0]=new android.widget.TextView(activity);outcome[0].setText("WAITING");layout.addView(outcome[0]);
   for(int i=0;i<2;i++){android.widget.Button duplicate=new android.widget.Button(activity);duplicate.setText("Duplicate");layout.addView(duplicate);}
   android.widget.TextView hidden=new android.widget.TextView(activity);hidden.setText("Hidden");hidden.setVisibility(android.view.View.GONE);layout.addView(hidden);activity.setContentView(layout);
  });
  android.app.UiAutomation automation=getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.setServiceInfo(info);android.os.SystemClock.sleep(600);
  android.view.accessibility.AccessibilityNodeInfo root=automation.getRootInActiveWindow();
  if(root==null)throw new AssertionError("No native test UI");
  for(String label:new String[]{"Duplicate","Hidden","Missing"}){boolean rejected=false;try{UiTarget.find(root,label,false);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Unsafe target lookup accepted: "+label);}
  UiTarget.find(root,"id:android:id/edit",true);UiTarget.find(root,"Result",true);
  CountDownLatch done=new CountDownLatch(1);RealHarness[] service={null};
  runOnMainSync(()->{try{RealHarness h=new RealHarness();h.attach(activity);h.automation=automation;h.running=true;h.generation=1;h.screenWidth=900;h.screenHeight=1600;h.done=done;h.lastObservation=new JSONObject().put("package","dev.androiduse");service[0]=h;h.runCode(new JSONObject().put("input",JSONObject.NULL).put("code","def main():\n    total = 0\n    for i in range(1, 101):\n        total += i*i\n    ui.fill(\"Result\", str(total))\n    ui.tap(\"Verify\")\n    return total\n"),1);}catch(Exception e){throw new RuntimeException(e);}});
  if(!done.await(20,TimeUnit.SECONDS))throw new AssertionError("Real Starlark target actions timed out");
  runOnMainSync(()->{if(!outcome[0].getText().toString().equals("PASS"))throw new AssertionError("Targeted fill/tap did not execute");service[0].running=false;service[0].generation++;});
 }
 static final class RealHarness extends AgentService {
  android.app.UiAutomation automation;CountDownLatch done;
  void attach(Context context){attachBaseContext(context);}
  @Override void log(String text){}
  @Override void logSource(String code){}
  @Override public android.view.accessibility.AccessibilityNodeInfo getRootInActiveWindow(){return automation.getRootInActiveWindow();}
  @Override void observe(int id){if(running&&generation==id)done.countDown();}
 }
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
