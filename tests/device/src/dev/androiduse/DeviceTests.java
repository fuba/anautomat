package dev.androiduse;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import org.json.JSONObject;

/** Uses a separate test record, never the user's ChatGPT credentials. */
public final class DeviceTests extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();SecureStore store=new SecureStore(getTargetContext(),"test-credentials.enc");try{
  JSONObject original=new JSONObject().put("access_token","TEST_SECRET_DO_NOT_USE").put("refresh_token","TEST_REFRESH").put("expires_at",1234);
  store.write(original);JSONObject restored=new SecureStore(getTargetContext(),"test-credentials.enc").read();if(!original.toString().equals(restored.toString()))throw new AssertionError("Secure record round trip failed");
  File file=new File(getTargetContext().getNoBackupFilesDir(),"test-credentials.enc");byte[] bytes;try(FileInputStream in=new FileInputStream(file)){bytes=in.readAllBytes();}if(new String(bytes,"ISO-8859-1").contains("TEST_SECRET"))throw new AssertionError("Credentials stored in plaintext");
  bytes[bytes.length-1]^=1;try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}boolean rejected=false;try{store.read();}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Tampered encrypted record accepted");
  store.clear();if(store.read()!=null)throw new AssertionError("Credentials were not deleted");
  for(String language:new String[]{"en","ja"}){android.content.res.Configuration config=new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());config.setLocales(new android.os.LocaleList(java.util.Locale.forLanguageTag(language)));android.content.Context localized=getTargetContext().createConfigurationContext(config);String expected=language.equals("en")?"Run goal":"Goal \u3092\u5b9f\u884c";if(!I18n.text(localized,"run_goal").equals(expected))throw new AssertionError("Locale selection failed: "+language);AgentBubble bubble=new AgentBubble(localized);bubble.state(true,false);if(!bubble.getContentDescription().toString().equals(I18n.text(localized,"menu")+" • "+I18n.text(localized,"running")))throw new AssertionError("Floating accessibility label locale failed");if(!I18n.text(localized,"limit_message",100).contains("100"))throw new AssertionError("Localized limit placeholder failed");}
  android.content.Intent authReturn=android.content.Intent.parseUri(AuthReturnPage.INTENT,android.content.Intent.URI_INTENT_SCHEME).addCategory(android.content.Intent.CATEGORY_BROWSABLE);
  android.content.pm.ResolveInfo destination=getTargetContext().getPackageManager().resolveActivity(authReturn,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
  if(destination==null||!destination.activityInfo.name.equals("dev.androiduse.MainActivity")||!"dev.androiduse".equals(authReturn.getPackage()))throw new AssertionError("Browser return does not resolve to the app");
  android.content.Intent wrongReturn=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("anautomat://auth/other")).setPackage("dev.androiduse").addCategory(android.content.Intent.CATEGORY_BROWSABLE);
  if(getTargetContext().getPackageManager().resolveActivity(wrongReturn,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)!=null)throw new AssertionError("Unexpected browser return path accepted");
  android.app.Activity activity=startActivitySync(new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  runOnMainSync(()->{
   String saved=GoalSettings.goal(getTargetContext());android.widget.LinearLayout holder=new android.widget.LinearLayout(activity);
   try{
    android.widget.EditText first=new android.widget.EditText(activity),second=new android.widget.EditText(activity);GoalSettings.bind(first);GoalSettings.bind(second);holder.addView(first);holder.addView(second);activity.addContentView(holder,new android.view.ViewGroup.LayoutParams(1,1));
    first.setText("SYNC_FIRST");if(!second.getText().toString().equals("SYNC_FIRST"))throw new AssertionError("First composer did not sync");
    second.setText("SYNC_SECOND");if(!first.getText().toString().equals("SYNC_SECOND")||!((MainActivity)activity).goal.getText().toString().equals("SYNC_SECOND"))throw new AssertionError("Reverse or app composer sync failed");
    if(GoalSettings.limit(getTargetContext())!=100)throw new AssertionError("Default step limit is not 100");
   }finally{GoalSettings.goal(getTargetContext(),saved);if(holder.getParent()!=null)((android.view.ViewGroup)holder.getParent()).removeView(holder);}
  });
  runOnMainSync(()->{
   AgentService previous=AgentService.instance;String draft=GoalSettings.goal(getTargetContext());ControlService service=new ControlService();service.attach(getTargetContext());AgentService.instance=service;
   try{service.onConfigurationChanged(new android.content.res.Configuration(getTargetContext().getResources().getConfiguration()));if(service.floating!=null)throw new AssertionError("Unconnected service created an overlay");
    GoalControls appControls=((MainActivity)activity).goalControls;GoalControls overlayControls=new GoalControls(activity,true,()->{});
    appControls.refresh();overlayControls.refresh();if(!appControls.primary.getContentDescription().equals(overlayControls.primary.getContentDescription()))throw new AssertionError("Idle controls differ");
    appControls.activate();if(service.starts!=1)throw new AssertionError("Play did not start idle goal");appControls.refresh();overlayControls.refresh();if(appControls.goal.isEnabled()||overlayControls.goal.isEnabled()||appControls.voice.isEnabled())throw new AssertionError("Active goal is editable");
    overlayControls.activate();if(service.pauses!=1)throw new AssertionError("Same primary button did not pause");appControls.refresh();overlayControls.refresh();if(!appControls.primary.getContentDescription().equals(I18n.text(activity,"resume"))||!appControls.primary.getContentDescription().equals(overlayControls.primary.getContentDescription()))throw new AssertionError("Paused controls differ");
    appControls.activate();if(service.resumes!=1||service.starts!=1)throw new AssertionError("Play restarted instead of resuming");service.running=false;service.limitReached=true;appControls.refresh();overlayControls.refresh();if(!appControls.primary.getContentDescription().equals(I18n.text(activity,"continue_goal")))throw new AssertionError("Play does not describe continuation");overlayControls.activate();if(service.continues!=1)throw new AssertionError("Play did not continue budget");
    appControls.stop.performClick();appControls.refresh();overlayControls.refresh();if(service.stops!=1||!appControls.goal.isEnabled()||!overlayControls.goal.isEnabled())throw new AssertionError("Stop did not unlock both goals");
   }finally{AgentService.instance=previous;GoalSettings.goal(getTargetContext(),draft);((MainActivity)activity).goalControls.refresh();}
  });
  runOnMainSync(()->{final int[] dismissals={0};ProgressTicker ticker=new ProgressTicker(activity,message->dismissals[0]++);float distance=100*activity.getResources().getDisplayMetrics().density;long now=android.os.SystemClock.uptimeMillis();
   for(float end:new float[]{distance,-distance}){android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,0,0,0),up=android.view.MotionEvent.obtain(now,now+100,1,end,0,0);ticker.onTouchEvent(down);ticker.onTouchEvent(up);down.recycle();up.recycle();}
   if(dismissals[0]!=2)throw new AssertionError("Ticker swipe did not dismiss in both directions");
   android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,0,0,0),up=android.view.MotionEvent.obtain(now,now+100,1,0,distance,0);ticker.onTouchEvent(down);ticker.onTouchEvent(up);down.recycle();up.recycle();if(dismissals[0]!=2)throw new AssertionError("Vertical ticker gesture dismissed");
   ticker.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS,null);if(dismissals[0]!=3)throw new AssertionError("Accessible dismiss failed");
  });
  runOnMainSync(()->{boolean visible=AgentService.appVisible;String message=AgentService.liveText;try{ControlService service=new ControlService();service.attach(getTargetContext());AgentService.appVisible=true;service.progress("Seen-before-switch-test");AgentService.appVisible=false;AgentService.tickerState.update(AgentService.liveText,android.os.SystemClock.uptimeMillis(),true);if(AgentService.tickerState.visible(android.os.SystemClock.uptimeMillis()))throw new AssertionError("Message from app replayed before refresh");service.progress("Fresh-outside-app-test");if(!AgentService.tickerState.visible(android.os.SystemClock.uptimeMillis()))throw new AssertionError("Fresh external message hidden");AgentService.markTickerSeen();if(AgentService.tickerState.visible(android.os.SystemClock.uptimeMillis()))throw new AssertionError("Immediate seen marker failed");}finally{AgentService.appVisible=visible;AgentService.liveText=message;AgentService.tickerState.update(message,android.os.SystemClock.uptimeMillis(),false);}});
  final RetryService[] retryService=new RetryService[2];final JSONObject retryUsage=new JSONObject().put("total_tokens",57);
  runOnMainSync(()->{RetryService service=new RetryService();service.attach(getTargetContext());retryService[0]=service;service.running=true;service.generation=20;service.goal="Retry test";service.step=7;service.budget.consume();service.history.put("retained action");service.meter.usage(retryUsage);Exception failure=new Protocol.ResponseFailure("ChatGPT response failed",true);
   service.inferenceFailed(failure,20);service.inferenceFailed(failure,20);service.inferenceFailed(failure,20);
   if(service.running||!service.paused||service.retries.attempts()!=2)throw new AssertionError("Inference failure did not pause after bounded retries");
   if(!service.goal.equals("Retry test")||service.step!=7||service.history.length()!=1||!service.meter.summary().contains("57")||!service.budget.exhausted(1))throw new AssertionError("Paused inference lost session state");
   RetryService manuallyPaused=new RetryService();manuallyPaused.attach(getTargetContext());retryService[1]=manuallyPaused;manuallyPaused.running=true;manuallyPaused.generation=50;manuallyPaused.inferenceFailed(failure,50);manuallyPaused.pauseGoal("Manual pause");
   service.resumeGoal();int resumed=service.generation;service.inferenceFailed(failure,20);if(!service.running||service.paused||service.generation!=resumed||service.retries.attempts()!=0)throw new AssertionError("Resume or stale failure guard failed");
  });
  android.os.SystemClock.sleep(5000);
  runOnMainSync(()->{RetryService service=retryService[0];try{if(service.observations!=1||retryService[1].observations!=0)throw new AssertionError("Cancelled retry replayed after pause or resume");}finally{service.running=false;service.handler.removeCallbacksAndMessages(null);if(service.planner!=null)service.planner.cancel();retryService[1].handler.removeCallbacksAndMessages(null);}});
  if(!LogArchive.get(getTargetContext()).folder().isEmpty()){
   LogArchive archive=new LogArchive(getTargetContext(),"test-archive.enc");android.net.Uri created=null;
   try{
    archive.begin("Log export test");archive.append("First line");archive.append("Continued line");archive.writer.submit(()->{}).get(15,java.util.concurrent.TimeUnit.SECONDS);
    java.lang.reflect.Field field=LogArchive.class.getDeclaredField("document");field.setAccessible(true);created=android.net.Uri.parse((String)field.get(archive));
    try(InputStream input=getTargetContext().getContentResolver().openInputStream(created)){String text=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(!text.equals("First line\nContinued line\n"))throw new AssertionError("SAF snapshot lost or duplicated lines");}
    archive.append("Finished");archive.writer.submit(()->{}).get(15,java.util.concurrent.TimeUnit.SECONDS);if(!created.toString().equals((String)field.get(archive)))throw new AssertionError("Continuation created a different file");
    try(InputStream input=getTargetContext().getContentResolver().openInputStream(created)){if(!new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).endsWith("Finished\n"))throw new AssertionError("Final log missing");}
   }finally{archive.writer.shutdown();if(created!=null)android.provider.DocumentsContract.deleteDocument(getTargetContext().getContentResolver(),created);new SecureStore(getTargetContext(),"test-archive.enc").clear();}
  }
  result.putString("stream","PASS: Android Keystore encryption, restart read, tamper rejection, deletion; live bidirectional GOAL sync and default 100 steps; English and Japanese resources; browser return routing; bounded retries and manual resume without stale callbacks; unified play/pause/continue/stop controls; bidirectional ticker swipe dismissal; SAF log export updates one UTF-8 file\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");finish(1,result);}finally{store.clear();}}
 static final class RetryService extends AgentService {
  int observations;
  void attach(android.content.Context context){attachBaseContext(context);}
  @Override void observe(int id){if(running&&generation==id)observations++;}
  @Override void log(String message){}
 }

 static final class ControlService extends AgentService {
  int starts,pauses,resumes,continues,stops;
  void attach(android.content.Context context){attachBaseContext(context);}
  @Override public void startGoal(String value){starts++;goal=value;running=true;paused=false;limitReached=false;}
  @Override public void pauseGoal(String reason){pauses++;running=false;paused=true;}
  @Override public void resumeGoal(){resumes++;running=true;paused=false;}
  @Override public void continueGoal(){continues++;running=true;paused=false;limitReached=false;}
  @Override public void stopGoal(String reason){stops++;running=false;paused=false;limitReached=false;}
 }

}
