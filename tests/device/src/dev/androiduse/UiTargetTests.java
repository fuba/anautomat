package dev.androiduse;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Real accessibility selectors and actions, independent of interpreter startup. */
public final class UiTargetTests extends Instrumentation {
 public void onCreate(Bundle state){super.onCreate(state);start();}
 public void onStart(){Bundle result=new Bundle();int status=-1;Activity activity=null;try{
  activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity host=activity;
  EditText[] field={null};TextView[] outcome={null};
  runOnMainSync(()->{LinearLayout layout=new LinearLayout(host);layout.setOrientation(1);layout.setPadding(24,64,24,24);
   field[0]=new EditText(host);field[0].setHint("Result");field[0].setId(android.R.id.edit);layout.addView(field[0]);
   Button button=new Button(host);button.setText("Verify");button.setAllCaps(false);button.setContentDescription("Run verification");button.setOnClickListener(v->outcome[0].setText(field[0].getText().toString().equals("338350")?"PASS":"FAIL"));layout.addView(button);
   outcome[0]=new TextView(host);outcome[0].setText("WAITING");layout.addView(outcome[0]);
   for(int i=0;i<2;i++){Button duplicate=new Button(host);duplicate.setText("Duplicate");layout.addView(duplicate);}
   Button disabled=new Button(host);disabled.setText("Disabled");disabled.setAllCaps(false);disabled.setEnabled(false);layout.addView(disabled);
   TextView hidden=new TextView(host);hidden.setText("Hidden");hidden.setVisibility(android.view.View.GONE);layout.addView(hidden);host.setContentView(layout);
  });
  UiAutomation automation=getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.setServiceInfo(info);waitForIdleSync();android.os.SystemClock.sleep(600);
  AccessibilityNodeInfo root=automation.getRootInActiveWindow();if(root==null)throw new AssertionError("No native test UI");
  for(String label:new String[]{"Duplicate","Hidden","Missing"}){boolean rejected=false;try{UiTarget.find(root,label,false);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Unsafe target accepted: "+label);}
  UiTarget.find(root,"id:android:id/edit",true);UiTarget.find(root,"Verify",false);
  boolean disabledRejected=false;try{UiTarget.click(UiTarget.find(root,"Disabled",false));}catch(Exception expected){disabledRejected=true;}if(!disabledRejected)throw new AssertionError("Disabled target accepted");
  AccessibilityNodeInfo target=UiTarget.find(root,"Result",true);Bundle replacement=new Bundle();replacement.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"338350");
  if(!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,replacement))throw new AssertionError("Hint-targeted fill failed");
  UiTarget.click(UiTarget.find(automation.getRootInActiveWindow(),"Run verification",false));waitForIdleSync();android.os.SystemClock.sleep(100);
  runOnMainSync(()->{if(!outcome[0].getText().toString().equals("PASS"))throw new AssertionError("Description-targeted click failed");});
  result.putString("stream","PASS: exact labels, hints, descriptions, resource IDs, duplicate/hidden/missing rejection, real fill and click\n");
 }catch(Throwable error){status=1;result.putString("stream","FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");}finally{if(activity!=null){Activity host=activity;runOnMainSync(host::finish);}}finish(status,result);}
}
