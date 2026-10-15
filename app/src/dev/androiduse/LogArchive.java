package dev.androiduse;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Ordered background snapshots of the whole current run, independent of the bounded UI history. */
final class LogArchive {
 private static LogArchive instance;
 final Context context;final SecureStore store;final ExecutorService writer=Executors.newSingleThreadExecutor();
 private String name="",document="",documentTree="",run="";private final StringBuilder content=new StringBuilder();private boolean exportFailed;
 volatile String status="";
 static synchronized LogArchive get(Context c){if(instance==null)instance=new LogArchive(c.getApplicationContext());return instance;}
 private LogArchive(Context c){this(c,"run-archive.enc");}
 LogArchive(Context c,String record){context=c;store=new SecureStore(c,record);writer.execute(()->{try{JSONObject saved=store.read();if(saved!=null){name=saved.optString("name");content.append(saved.optString("content"));if(saved.optInt("layout")==2){run=saved.optString("run");document=saved.optString("document");documentTree=saved.optString("tree");}else if(!name.isEmpty())run=runName(name);} }catch(Exception e){status=I18n.text(context,"archive_read_failed");}});}
 static String runName(String filename){return filename.replaceFirst("\\.txt$","")+"_"+java.util.UUID.randomUUID().toString().substring(0,8);}
 String folder(){return context.getSharedPreferences("log-export",0).getString("tree","");}
 String folderLabel(){return context.getSharedPreferences("log-export",0).getString("label","");}
 void begin(String goal){String filename=LogFormat.filename(goal,new java.util.Date());writer.execute(()->{name=filename;run=runName(filename);exportFailed=false;document="";documentTree="";content.setLength(0);persist();});}
 void append(String line){writer.execute(()->{if(name.isEmpty())return;content.append(line).append('\n');persist();export();});}
 void select(Uri tree,String label){writer.execute(()->{exportFailed=false;status="";});context.getSharedPreferences("log-export",0).edit().putString("tree",tree.toString()).putString("label",label).apply();saveCurrent();}
 void saveCurrent(){String fallback=RunLog.get(context).text();writer.execute(()->{if(name.isEmpty()&&!fallback.isEmpty()){name=LogFormat.filename(I18n.text(context,"log_history"),new java.util.Date());run=runName(name);content.append(fallback);persist();}try{if(!folder().isEmpty())new WorkspaceFiles(context.getContentResolver(),folder()).initialize();}catch(Exception e){exportFailed=true;status=I18n.text(context,"export_failed");}export();});}
 void disable(){context.getSharedPreferences("log-export",0).edit().remove("tree").remove("label").apply();status="";}
 private void persist(){try{store.write(new JSONObject().put("layout",2).put("run",run).put("name",name).put("document",document).put("tree",documentTree).put("content",content.toString()));}catch(Exception e){exportFailed=true;status=I18n.text(context,"log_save_failed");}}
 private Uri runDirectory(WorkspaceFiles files)throws Exception{if(run.isEmpty())throw new IOException("No active run archive");return files.directory(files.runs(),run);}
 String recordCode(int step,JSONObject action){String tag=String.format(java.util.Locale.ROOT,"step-%04d-%s",step,java.util.UUID.randomUUID().toString().substring(0,8));artifact(tag+".star","text/plain",action.optString("code"));artifact(tag+".input.json","application/json",action.isNull("input")?"{}":action.optString("input","{}"));return tag;}
 void artifact(String filename,String mime,String text){writer.execute(()->{String selected=folder();if(selected.isEmpty()||run.isEmpty())return;try{WorkspaceFiles files=new WorkspaceFiles(context.getContentResolver(),selected);Uri steps=files.directory(runDirectory(files),"steps");files.write(steps,filename,mime,text);}catch(Exception e){exportFailed=true;status=I18n.text(context,"export_failed");}});}
 void workspace(JSONObject action,java.util.function.BooleanSupplier active,java.util.function.BiConsumer<JSONObject,String> callback){String selected=folder();writer.execute(()->{try{
  if(!active.getAsBoolean())return;if(!selected.equals(folder()))throw new IOException("Workspace folder changed; retry after checking the selected folder");
  if(!AutomationSettings.codeEnabled(context))throw new IOException(I18n.text(context,"sandbox_disabled"));
  WorkspaceFiles files=new WorkspaceFiles(context.getContentResolver(),selected);JSONObject result=new JSONObject();String type=action.getString("type");
  if(type.equals("list_macros"))result.put("names",new org.json.JSONArray(files.listMacros()));
  else if(type.equals("read_macro"))result.put("name",action.getString("name")).put("code",files.readMacro(action.getString("name")));
  else if(type.equals("save_macro")){files.saveMacro(action.getString("name"),action.getString("code"));result.put("name",action.getString("name")).put("saved",true);}
  else throw new IOException("Unknown workspace operation");
  callback.accept(result,null);
 }catch(Exception e){callback.accept(null,e.getMessage()==null?"Workspace operation failed":e.getMessage());}});}
 private void export(){String tree=folder();if(tree.isEmpty()||name.isEmpty())return;try{
  WorkspaceFiles files=new WorkspaceFiles(context.getContentResolver(),tree);
  if(document.isEmpty()||!tree.equals(documentTree)){files.initialize();document=files.child(runDirectory(files),"log.txt","text/plain",true).toString();documentTree=tree;persist();}
  try(OutputStream out=context.getContentResolver().openOutputStream(Uri.parse(document),"wt")){if(out==null)throw new IOException(I18n.text(context,"file_open_failed"));out.write(content.toString().getBytes(StandardCharsets.UTF_8));}
  if(!exportFailed)status=I18n.text(context,"saved_prefix")+"runs/"+run+"/log.txt";
 }catch(Exception e){exportFailed=true;status=I18n.text(context,"export_failed");}}
}
