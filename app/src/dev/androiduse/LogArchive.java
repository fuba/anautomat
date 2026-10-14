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
 private String name="",document="",documentTree="";private final StringBuilder content=new StringBuilder();
 volatile String status="";
 static synchronized LogArchive get(Context c){if(instance==null)instance=new LogArchive(c.getApplicationContext());return instance;}
 private LogArchive(Context c){this(c,"run-archive.enc");}
 LogArchive(Context c,String record){context=c;store=new SecureStore(c,record);writer.execute(()->{try{JSONObject saved=store.read();if(saved!=null){name=saved.optString("name");document=saved.optString("document");documentTree=saved.optString("tree");content.append(saved.optString("content"));} }catch(Exception e){status=I18n.text(context,"archive_read_failed");}});}
 String folder(){return context.getSharedPreferences("log-export",0).getString("tree","");}
 String folderLabel(){return context.getSharedPreferences("log-export",0).getString("label","");}
 void begin(String goal){String filename=LogFormat.filename(goal,new java.util.Date());writer.execute(()->{name=filename;document="";documentTree="";content.setLength(0);persist();});}
 void append(String line){writer.execute(()->{if(name.isEmpty())return;content.append(line).append('\n');persist();export();});}
 void select(Uri tree,String label){context.getSharedPreferences("log-export",0).edit().putString("tree",tree.toString()).putString("label",label).apply();saveCurrent();}
 void saveCurrent(){String fallback=RunLog.get(context).text();writer.execute(()->{if(name.isEmpty()&&!fallback.isEmpty()){name=LogFormat.filename(I18n.text(context,"log_history"),new java.util.Date());content.append(fallback);persist();}export();});}
 void disable(){context.getSharedPreferences("log-export",0).edit().remove("tree").remove("label").apply();status="";}
 private void persist(){try{store.write(new JSONObject().put("name",name).put("document",document).put("tree",documentTree).put("content",content.toString()));}catch(Exception e){status=I18n.text(context,"log_save_failed");}}
 private void export(){String tree=folder();if(tree.isEmpty()||name.isEmpty())return;try{
  Uri root=Uri.parse(tree);
  if(document.isEmpty()||!tree.equals(documentTree)){Uri parent=DocumentsContract.buildDocumentUriUsingTree(root,DocumentsContract.getTreeDocumentId(root));Uri created=DocumentsContract.createDocument(context.getContentResolver(),parent,"text/plain",name);if(created==null)throw new IOException(I18n.text(context,"file_create_failed"));document=created.toString();documentTree=tree;persist();}
  try(OutputStream out=context.getContentResolver().openOutputStream(Uri.parse(document),"wt")){if(out==null)throw new IOException(I18n.text(context,"file_open_failed"));out.write(content.toString().getBytes(StandardCharsets.UTF_8));}
  status=I18n.text(context,"saved_prefix")+name;
 }catch(Exception e){status=I18n.text(context,"export_failed");}}
}
