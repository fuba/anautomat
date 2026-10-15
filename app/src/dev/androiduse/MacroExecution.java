package dev.androiduse;

import java.util.function.*;
import org.json.*;

/** Sequential callback execution, never parallel taps. Cancellation discards the remaining batch. */
final class MacroExecution {
 static final long TIMEOUT=20000;
 interface Driver {String packageName();boolean containsText(String text);void perform(JSONObject action,Runnable success,Consumer<String> failure);}
 interface Scheduler {void post(Runnable task,long delay);}
 final MacroPlan plan;final Driver driver;final Scheduler scheduler;final LongSupplier clock;final Consumer<JSONObject> result;
 int completed;boolean active,inFlight;long deadline;
 MacroExecution(MacroPlan plan,Driver driver,Scheduler scheduler,LongSupplier clock,Consumer<JSONObject> result){this.plan=plan;this.driver=driver;this.scheduler=scheduler;this.clock=clock;this.result=result;}
 void start(){if(active||completed!=0)throw new IllegalStateException();active=true;deadline=clock.getAsLong()+TIMEOUT;scheduler.post(()->{if(active)finish("failed","Macro time limit exceeded");},TIMEOUT);next();}
 void next(){
  if(!active)return;if(clock.getAsLong()>=deadline){finish("failed","Macro time limit exceeded");return;}
  if(completed==plan.count){finish("completed","");return;}
  try{JSONObject action=plan.actions.getJSONObject(completed);String expected=MacroPlan.guard(action,"expect_package");if(!expected.isEmpty()&&!expected.equals(driver.packageName())){finish("failed","Macro package guard failed");return;}
   String text=MacroPlan.guard(action,"expect_text");if(!text.isEmpty()&&!driver.containsText(text)){finish("failed","Macro text guard failed");return;}
   int index=completed;inFlight=true;driver.perform(action,()->{if(!active||!inFlight||completed!=index)return;inFlight=false;completed++;try{scheduler.post(this::next,MacroPlan.delay(action));}catch(Exception e){finish("failed","Invalid macro delay");}},error->{if(active&&inFlight&&completed==index)finish("failed",error);});
  }catch(Exception e){finish("failed","Macro action failed");}
 }
 void finish(String status,String error){if(!active)return;active=false;result.accept(snapshot(status,error));}
 JSONObject cancel(String reason){active=false;return snapshot("cancelled",reason);}
 JSONObject snapshot(String status,String error){try{return new JSONObject().put("type","tool_result").put("tool","macro").put("status",status).put("completed",completed).put("total",plan.count).put("in_flight",inFlight).put("error",error==null?"":error.substring(0,Math.min(512,error.length())));}catch(JSONException e){throw new IllegalStateException(e);}}
}
