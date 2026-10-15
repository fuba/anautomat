package dev.androiduse;

import java.util.*;
import java.util.function.*;
import org.json.*;

/** Tests execute real compiled Java, hostile programs and deterministic callback races. */
public final class AutomationTest {
 static int passed;
 interface Work{void run()throws Exception;}
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static void reject(Work work)throws Exception{try{work.run();}catch(Exception e){passed++;return;}throw new AssertionError("Unsafe input accepted");}
 static SandboxEngine.Loader loader=bytes->new ClassLoader(AutomationTest.class.getClassLoader()){
  protected Class<?> findClass(String name)throws ClassNotFoundException{byte[] code=bytes.get(name);if(code==null)throw new ClassNotFoundException(name);return defineClass(name,code,0,code.length);}
 }.loadClass(JavaBytecodePolicy.PROGRAM);
 static JSONObject run(String code)throws Exception{return new JSONObject(SandboxEngine.run(code,"{}","{}","{}",loader,()->{}));}
 public static void main(String[] args)throws Exception{
  JSONObject computed=new JSONObject(SandboxEngine.run("JSONArray values=ctx.input.getJSONArray(\"values\"); int total=0; for(int i=0;i<values.length();i++)total+=values.getInt(i); ctx.state.put(\"total\",total);ctx.log(\"sum \"+total);ctx.macro(new JSONArray().put(new JSONObject().put(\"type\",\"text\").put(\"text\",String.valueOf(total)).put(\"expect_package\",\"com.example.app\")));return new JSONObject().put(\"total\",total);","{\"values\":[2,3,5]}","{}","{}",loader,()->{}));
  check(computed.getJSONObject("result").getInt("total")==10);check(computed.getJSONObject("state").getInt("total")==10);check(computed.getString("logs").equals("sum 10\n"));check(new MacroPlan(computed.getJSONArray("actions"),100,100).count==1);
  check(run("int[] a={1,2,3};return a[2]*a[2];").getInt("result")==9);
  check(run("return new JSONObject().put(\"bool\",true);").getJSONObject("result").getBoolean("bool"));check(run("return null;").isNull("result"));
  check(run("List<String> a=new ArrayList<String>();a.add(\"ok\");return a.get(0);").getString("result").equals("ok"));
  check(run("int[] a={3,1,2};Arrays.sort(a);return a[0];").getInt("result")==1);
  check(run("return new java.math.BigInteger(\"123456789\").multiply(new java.math.BigInteger(\"987654321\")).toString();").getString("result").equals("121932631112635269"));
  check(run("return java.time.LocalDate.parse(\"2026-10-15\").plusDays(1).toString();").getString("result").equals("2026-10-16"));
  check(run("return java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance(\"SHA-256\").digest(\"abc\".getBytes(java.nio.charset.StandardCharsets.UTF_8)));").getString("result").equals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0="));
  for(String source:new String[]{
   "return System.getProperty(\"user.home\");","return Runtime.getRuntime();","return new java.io.File(\"/data/data/dev.androiduse\");",
   "return new java.net.Socket();","return new Thread();","return Object.class.getName();","return ctx.getClass();","return new Object().getClass();",
   "return Class.forName(\"dev.androiduse.MainActivity\");","return android.os.Process.myUid();","return dev.androiduse.SandboxService.class;",
   "return java.lang.invoke.MethodHandles.lookup();","return java.util.concurrent.ForkJoinPool.commonPool();","int[] a={3,2,1};Arrays.parallelSort(a);return a;"
  })reject(()->SandboxEngine.compile(source));
  reject(()->run("ctx.log(new String(new char[8193]));return 0;"));
  reject(()->run("ctx.state.put(\"large\",new String(new char[8193]));return 0;"));
  reject(()->run("return new String(new char[65537]);"));
  reject(()->run("for(int i=0;i<33;i++)ctx.macro(new JSONArray().put(new JSONObject().put(\"type\",\"home\")));return 0;"));
  reject(()->run("ctx.macro(new JSONArray().put(3));return 0;"));
  reject(()->run("ctx.macro(new JSONArray().put(new JSONObject().put(\"type\",\"home\")));throw new Exception(\"no effects\");"));
  reject(()->run("return new JSONObject();"+" ".repeat(32769)));
  reject(()->AutomationJson.object("{\"x\":"+"[".repeat(33)+"0"+"]".repeat(33)+"}",65536));
  reject(()->AutomationJson.check("{\"x\":1",65536));
  reject(()->AutomationJson.check("{'x':'\"','deep':"+"[".repeat(100)+"0"+"]".repeat(100)+",'close':'\"'}",65536));
  check(AutomationJson.object("{\"x\":\"[{}]\"}",100).getString("x").equals("[{}]"));
  macroTests();System.out.println("PASS: "+passed+" Java compilation / API allowlist / JSON / macro ordering / cancellation checks");
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
  check(Protocol.validateAction(new JSONObject().put("type","run_code").put("reason","").put("code","return 1;"),100,100).getString("type").equals("run_code"));
  reject(()->Protocol.validateAction(new JSONObject().put("type","run_code").put("reason","").put("code","x".repeat(32769)),100,100));
 }
}
