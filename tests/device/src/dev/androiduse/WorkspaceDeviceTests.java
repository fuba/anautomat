package dev.androiduse;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Real SAF hierarchy, migration and reusable source; only test-owned documents are removed. */
final class WorkspaceDeviceTests {
 static void run(Context context)throws Exception{
  String selected=LogArchive.get(context).folder();if(selected.isEmpty())throw new AssertionError("Select a test workspace folder before running device tests");
  WorkspaceFiles files=new WorkspaceFiles(context.getContentResolver(),selected);
  String unique="workspace_test_"+java.util.UUID.randomUUID().toString().replace("-","");
  Uri testRoot=files.directory(files.root,unique),legacy=null,run=null;LogArchive archive=null;
  String record="test-workspace.enc";
  try{
   WorkspaceFiles isolated=new WorkspaceFiles(context.getContentResolver(),selected,testRoot);isolated.initialize();
   String source="def main():\n    # Preserve indentation and empty lines.\n\n    return \"日本語 😀\"\n";
   isolated.saveMacro("sum_squares.star",source);
   if(!isolated.listMacros().equals(java.util.List.of("sum_squares.star"))||!source.equals(isolated.readMacro("sum_squares.star")))throw new AssertionError("Reusable UTF-8 source changed");
   try{isolated.saveMacro("sum_squares.star","different");throw new AssertionError("Existing macro overwritten");}catch(java.io.IOException expected){}
   if(!source.equals(isolated.readMacro("sum_squares.star")))throw new AssertionError("Rejected overwrite changed source");
   for(String name:new String[]{"../escape.star","a/b.star","content://secret"}){try{isolated.readMacro(name);throw new AssertionError("Path escaped workspace");}catch(java.io.IOException expected){}}
   isolated.write(isolated.macros(),"invalid.star","text/plain","a".repeat(32769));
   try{isolated.readMacro("invalid.star");throw new AssertionError("Oversize external macro accepted");}catch(java.io.IOException expected){}
   Uri malformed=isolated.child(isolated.macros(),"invalid_unicode.star","text/plain",true);isolated.write(malformed,new byte[]{(byte)0xc0,(byte)0xaf});
   try{isolated.readMacro("invalid_unicode.star");throw new AssertionError("Invalid UTF-8 accepted");}catch(java.io.IOException expected){}
   isolated.directory(isolated.runs(),"collision.txt");try{isolated.child(isolated.runs(),"collision.txt","text/plain",true);throw new AssertionError("Directory used as file");}catch(java.io.IOException expected){}
   String filename=unique+".txt";legacy=files.write(files.root,filename,"text/plain","Old root log\n");
   new SecureStore(context,record).write(new JSONObject().put("name",filename).put("document",legacy.toString()).put("tree",selected).put("content","First line\n"));
   archive=new LogArchive(context,record);archive.append("Continued line");drain(archive);
   JSONObject saved=new SecureStore(context,record).read();if(saved.getInt("layout")!=2)throw new AssertionError("Legacy archive layout not upgraded");
   run=files.child(files.runs(),saved.getString("run"),WorkspaceFiles.DIR,false);if(run==null)throw new AssertionError("Run directory missing");
   Uri log=files.child(run,"log.txt","text/plain",false);if(log==null||!read(context,log).equals("First line\nContinued line\n")||!read(context,legacy).equals("Old root log\n"))throw new AssertionError("Migration changed legacy file or lost log");
   String tag=archive.recordCode(3,new JSONObject().put("code",source).put("input","{\"x\":42}"));archive.artifact(tag+".result.json","application/json","{\"result\":42}");drain(archive);
   Uri steps=files.child(run,"steps",WorkspaceFiles.DIR,false);if(!source.equals(read(context,files.child(steps,tag+".star","text/plain",false))))throw new AssertionError("Archived source indentation changed");
   if(!read(context,files.child(steps,tag+".input.json","application/json",false)).equals("{\"x\":42}")||!read(context,files.child(steps,tag+".result.json","application/json",false)).contains("42"))throw new AssertionError("Input/result sidecars missing");
   archive.writer.shutdown();archive=new LogArchive(context,record);archive.append("After restart");drain(archive);
   if(!new SecureStore(context,record).read().getString("document").equals(log.toString())||!read(context,log).endsWith("After restart\n"))throw new AssertionError("Restart created another run or lost log");
   // A cancelled request must not even start its queued storage operation.
   CountDownLatch cancelled=new CountDownLatch(1);archive.workspace(new JSONObject().put("type","save_macro").put("name",unique+".star").put("code",source),()->false,(value,error)->cancelled.countDown());drain(archive);
   if(cancelled.getCount()!=1||files.child(files.macros(),unique+".star","text/plain",false)!=null)throw new AssertionError("Cancelled workspace operation ran");
   android.content.SharedPreferences settings=context.getSharedPreferences("automation",0);boolean had=settings.contains("code"),enabled=settings.getBoolean("code",true);
   try{
    settings.edit().putBoolean("code",false).commit();CountDownLatch disabled=new CountDownLatch(1);String[] error={null};
    archive.workspace(new JSONObject().put("type","save_macro").put("name",unique+".star").put("code",source),()->true,(value,failure)->{error[0]=failure;disabled.countDown();});
    if(!disabled.await(20,TimeUnit.SECONDS)||error[0]==null||files.child(files.macros(),unique+".star","text/plain",false)!=null)throw new AssertionError("Disabled queued tool wrote a file or failed to report completion");
   }finally{android.content.SharedPreferences.Editor edit=settings.edit();if(had)edit.putBoolean("code",enabled);else edit.remove("code");edit.commit();}
  }finally{
   if(archive!=null){archive.writer.shutdown();archive.writer.awaitTermination(15,TimeUnit.SECONDS);}new SecureStore(context,record).clear();
   if(run!=null)DocumentsContract.deleteDocument(context.getContentResolver(),run);
   if(legacy!=null)DocumentsContract.deleteDocument(context.getContentResolver(),legacy);
   DocumentsContract.deleteDocument(context.getContentResolver(),testRoot);
  }
 }
 static void drain(LogArchive archive)throws Exception{archive.writer.submit(()->{}).get(20,TimeUnit.SECONDS);}
 static String read(Context context,Uri uri)throws Exception{if(uri==null)throw new AssertionError("Missing document");try(java.io.InputStream in=context.getContentResolver().openInputStream(uri)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 private WorkspaceDeviceTests(){}
}
