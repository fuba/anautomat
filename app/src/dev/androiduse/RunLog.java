package dev.androiduse;

import android.content.Context;
import org.json.*;
import java.util.concurrent.*;

/** Bounded, encrypted local history. No credentials or raw HTTP responses are recorded. */
final class RunLog {
 static final class Snapshot {final long revision;final String text;Snapshot(long revision,String text){this.revision=revision;this.text=text;}}
 private static RunLog instance;private final SecureStore store;private final LogArchive archive;private JSONArray entries=new JSONArray();private long revision;private boolean pending;
 private final ExecutorService writer=Executors.newSingleThreadExecutor();
 static synchronized RunLog get(Context c){if(instance==null)instance=new RunLog(c.getApplicationContext());return instance;}
 private RunLog(Context c){archive=LogArchive.get(c);store=new SecureStore(c,"run-history.enc");try{JSONObject saved=store.read();if(saved!=null)entries=saved.getJSONArray("entries");}catch(Exception ignored){}}
 synchronized void add(String message){try{String text=LogFormat.compact(message.length()>8000?message.substring(0,8000):message);java.text.SimpleDateFormat clock=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.JAPAN);clock.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Tokyo"));String line=clock.format(new java.util.Date())+"  "+text;entries.put(line);archive.append(line);while(entries.length()>500||entries.toString().length()>150000)entries.remove(0);revision++;save();}catch(Exception ignored){}}
 synchronized long revision(){return revision;}
 synchronized Snapshot snapshot(){return new Snapshot(revision,text());}
 synchronized String text(){StringBuilder out=new StringBuilder();for(int i=0;i<entries.length();i++)out.append(LogFormat.compact(entries.optString(i))).append("\n");return out.toString();}
 synchronized void clear(){entries=new JSONArray();revision++;save();}
 private synchronized void save(){if(pending)return;pending=true;writer.execute(()->{while(true){JSONObject snapshot;long version;synchronized(this){version=revision;try{snapshot=new JSONObject().put("entries",new JSONArray(entries.toString()));}catch(Exception e){pending=false;return;}}try{store.write(snapshot);}catch(Exception ignored){}synchronized(this){if(version==revision){pending=false;return;}}}});}
}
