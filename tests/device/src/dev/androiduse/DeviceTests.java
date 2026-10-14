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
  for(String language:new String[]{"en","ja"}){android.content.res.Configuration config=new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());config.setLocales(new android.os.LocaleList(java.util.Locale.forLanguageTag(language)));android.content.Context localized=getTargetContext().createConfigurationContext(config);String expected=language.equals("en")?"Run goal →":"Goal \u3092\u5b9f\u884c →";if(!I18n.text(localized,"run_goal").equals(expected))throw new AssertionError("Locale selection failed: "+language);AgentBubble bubble=new AgentBubble(localized);bubble.state(true,false);if(!bubble.getContentDescription().toString().equals(I18n.text(localized,"menu")+" • "+I18n.text(localized,"running")))throw new AssertionError("Floating accessibility label locale failed");if(!I18n.text(localized,"limit_message",100).contains("100"))throw new AssertionError("Localized limit placeholder failed");}
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
  result.putString("stream","PASS: Android Keystore encryption, restart read, tamper rejection, deletion; live bidirectional GOAL sync and default 100 steps; English and Japanese resources; SAF log export updates one UTF-8 file\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");finish(1,result);}finally{store.clear();}}
}
