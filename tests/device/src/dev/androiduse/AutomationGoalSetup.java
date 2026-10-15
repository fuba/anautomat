package dev.androiduse;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONObject;

/** Explicit opt-in demo setup/restore; preserves the user's saved Goal in encrypted storage. */
public final class AutomationGoalSetup extends Instrumentation {
 boolean restore;
 public void onCreate(Bundle options){super.onCreate(options);restore="true".equals(options.getString("restore"));start();}
 public void onStart(){Bundle result=new Bundle();int code=-1;try{
  SecureStore saved=new SecureStore(getTargetContext(),"test-saved-goal.enc");
  if(restore){JSONObject record=saved.read();if(record!=null){GoalSettings.goal(getTargetContext(),record.getString("goal"));saved.clear();}}
  else{if(saved.read()!=null)throw new IllegalStateException("Restore the previous demo first");saved.write(new JSONObject().put("goal",GoalSettings.goal(getTargetContext())));
   GoalSettings.goal(getTargetContext(),"In the offline Java + macro test screen (dev.androiduse.tests), use run_code to write Java that calculates the sum of squares from 1 through 100. From that Java code, generate a macro that taps the Result field, fills it with the computed answer, and taps Verify. Include expect_package guards for dev.androiduse.tests. Verify the screen shows PASS: 338350 and then finish. Use the screenshot for coordinates. Only interact with this test screen; no other apps, communications, file access or network actions are authorized. Do not control anautomat's own UI or floating button.");}
  if(!GoalSettings.prefs(getTargetContext()).edit().putString("goal",GoalSettings.goal(getTargetContext())).commit())throw new IllegalStateException("Could not persist test Goal");
  result.putString("stream",restore?"PASS: Original Goal restored\n":"PASS: Offline Java/macro test Goal configured\n");
 }catch(Exception e){code=1;result.putString("stream","FAIL: "+e.getClass().getSimpleName()+"\n");}finish(code,result);}
}
