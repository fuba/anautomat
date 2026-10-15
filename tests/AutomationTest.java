package dev.androiduse;

import java.util.*;
import java.util.function.*;
import org.json.*;

/** Host-side validation and deterministic macro callback/cancellation races. */
public final class AutomationTest {
 static int passed;
 interface Work{void run()throws Exception;}
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static void reject(Work work)throws Exception{try{work.run();}catch(Exception e){passed++;return;}throw new AssertionError("Unsafe input accepted");}
 public static void main(String[] args)throws Exception{
  reject(()->AutomationJson.object("{\"x\":"+"[".repeat(33)+"0"+"]".repeat(33)+"}",65536));
  reject(()->AutomationJson.check("{\"x\":1",65536));
  reject(()->AutomationJson.check("{'x':'\"','deep':"+"[".repeat(100)+"0"+"]".repeat(100)+",'close':'\"'}",65536));
  check(AutomationJson.object("{\"x\":\"[{}]\"}",100).getString("x").equals("[{}]"));
  JSONArray targets=new JSONArray("[{type:'fill',target:'Result',text:'338350',expect_package:'com.example.app'},{type:'tap_target',target:'Verify',expect_package:'com.example.app'}]");
  check(new MacroPlan(targets,100,100).count==2);
  for(String json:new String[]{"[{type:'fill',target:'Result',text:'x'}]","[{type:'tap_target',target:'',expect_package:'com.example.app'}]","[{type:'tap_target',target:'id:',expect_package:'com.example.app'}]","[{type:'tap_target',target:null,expect_package:'com.example.app'}]","[{type:'fill',target:'Result',text:4,expect_package:'com.example.app'}]"})reject(()->new MacroPlan(new JSONArray(json),100,100));
  macroTests();System.out.println("PASS: "+passed+" JSON / guarded targets / macro ordering / cancellation checks");
 }
 static final class Clock implements MacroExecution.Scheduler,LongSupplier{
  static final class Task{final long due;final Runnable work;Task(long due,Runnable work){this.due=due;this.work=work;}}
  long now;List<Task> tasks=new ArrayList<>();public long getAsLong(){return now;}public void post(Runnable task,long delay){tasks.add(new Task(now+delay,task));}
  void advance(long ms){long end=now+ms;while(true){Task next=null;for(Task t:tasks)if(t.due<=end&&(next==null||t.due<next.due))next=t;if(next==null)break;tasks.remove(next);now=next.due;next.work.run();}now=end;}
 }
 static final class Driver implements MacroExecution.Driver{
  int calls;Runnable completion;Consumer<String> failure;String pkg="com.example.app";boolean text=true;
  public String packageName(){return pkg;}public boolean containsText(String value){return text;}
  public void perform(JSONObject action,Runnable success,Consumer<String> error){calls++;completion=success;failure=error;}
 }
 static MacroExecution execution(MacroPlan plan,Driver driver,Clock clock,List<JSONObject> results){return new MacroExecution(plan,driver,clock,clock,results::add);}
 static void macroTests()throws Exception{
  JSONArray actions=new JSONArray("[{type:'tap',x:2,y:3,expect_package:'com.example.app',expect_text:'Ready'},{type:'text',text:'done',delay_ms:0,expect_package:'com.example.app'}]");
  MacroPlan plan=new MacroPlan(actions,100,100);Driver driver=new Driver();Clock clock=new Clock();List<JSONObject> result=new ArrayList<>();MacroExecution run=execution(plan,driver,clock,result);run.start();check(driver.calls==1&&run.completed==0);Runnable first=driver.completion;first.run();first.run();check(run.completed==1);clock.advance(99);check(driver.calls==1);clock.advance(1);check(driver.calls==2);first.run();check(run.completed==1);driver.completion.run();clock.advance(0);check(result.size()==1&&result.get(0).getInt("completed")==2&&result.get(0).getString("status").equals("completed"));clock.advance(30000);check(result.size()==1);
  driver=new Driver();clock=new Clock();result=new ArrayList<>();run=execution(plan,driver,clock,result);run.start();JSONObject cancelled=run.cancel("pause");driver.completion.run();clock.advance(30000);check(driver.calls==1&&result.isEmpty()&&cancelled.getBoolean("in_flight")&&cancelled.getInt("completed")==0);
  driver=new Driver();clock=new Clock();result=new ArrayList<>();run=execution(plan,driver,clock,result);run.start();driver.completion.run();run.cancel("pause between actions");clock.advance(30000);check(driver.calls==1&&result.isEmpty());
  driver=new Driver();driver.pkg="other.app";clock=new Clock();result=new ArrayList<>();run=execution(plan,driver,clock,result);run.start();check(driver.calls==0&&result.get(0).getString("status").equals("failed"));
  driver=new Driver();driver.text=false;clock=new Clock();result=new ArrayList<>();run=execution(plan,driver,clock,result);run.start();check(driver.calls==0&&result.size()==1);
  driver=new Driver();clock=new Clock();result=new ArrayList<>();run=execution(plan,driver,clock,result);run.start();driver.failure.accept("rejected");driver.completion.run();clock.advance(30000);check(driver.calls==1&&result.size()==1&&result.get(0).getInt("completed")==0);
  driver=new Driver();clock=new Clock();result=new ArrayList<>();run=execution(plan,driver,clock,result);run.start();clock.advance(MacroExecution.TIMEOUT);driver.completion.run();check(result.size()==1&&result.get(0).getBoolean("in_flight")&&driver.calls==1);
  reject(()->new MacroPlan(new JSONArray(),100,100));
  for(String json:new String[]{"[{type:'macro',actions:[]}]","[{type:'run_code',code:'return 1;'}]","[{type:'tap',x:100,y:1}]","[{type:'wait',delay_ms:-1}]","[{type:'wait',delay_ms:0.5}]","[{type:'wait',delay_ms:'0'}]","[{type:'wait',delay_ms:2001}]","[{type:'home',expect_package:''}]","[{type:'home',expect_text:4}]","[{type:'home',unknown:true}]","[{type:'home'},{type:'shell'}]"})reject(()->new MacroPlan(new JSONArray(json),100,100));
  check(MacroPlan.guard(new JSONObject().put("expect_text",JSONObject.NULL),"expect_text").isEmpty());
  reject(()->new MacroPlan(new JSONArray("[{type:'tap',x:1,y:1,expect_package:null}]"),100,100));
  JSONArray longBatch=new JSONArray();for(int i=0;i<33;i++)longBatch.put(new JSONObject().put("type","home"));reject(()->new MacroPlan(longBatch,100,100));
  JSONArray slowBatch=new JSONArray();for(int i=0;i<8;i++)slowBatch.put(new JSONObject().put("type","wait").put("delay_ms",2000));reject(()->new MacroPlan(slowBatch,100,100));
  check(Protocol.validateAction(new JSONObject().put("type","macro").put("reason","").put("actions",actions),100,100).getJSONArray("actions").length()==2);
  check(Protocol.validateAction(new JSONObject().put("type","run_code").put("reason","").put("code","def main():\n    return 1\n"),100,100).getString("type").equals("run_code"));
  reject(()->Protocol.validateAction(new JSONObject().put("type","run_code").put("reason","").put("code","x".repeat(32769)),100,100));
 }
}
