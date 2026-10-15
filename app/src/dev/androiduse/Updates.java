package dev.androiduse;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Phone-only, opt-out release checks and explicitly requested, verified updates. */
final class Updates {
 static final int JOB=1909,NOTICE=1910;
 static final long INTERVAL=6*60*60*1000L;
 private static Updates instance;
 static synchronized Updates get(Context context){if(instance==null)instance=new Updates(context.getApplicationContext());return instance;}
 final Context context;final SharedPreferences prefs;final Handler main=new Handler(Looper.getMainLooper());
 final AtomicBoolean busy=new AtomicBoolean();
 volatile UpdatePolicy release;volatile String status="";volatile boolean ready;
 Updates(Context c){context=c;prefs=c.getSharedPreferences("updates",0);try{UpdatePolicy saved=new UpdatePolicy(new JSONObject(prefs.getString("release","")));if(saved.code>installed().getLongVersionCode()){release=saved;ready=new File(c.getCacheDir(),"update.apk").isFile()&&!prefs.getString("hash","").isEmpty();}}catch(Exception ignored){}schedule();}
 PackageInfo installed()throws Exception{return context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);}
 boolean automatic(){return prefs.getBoolean("automatic",true);}
 void automatic(boolean enabled){prefs.edit().putBoolean("automatic",enabled).apply();schedule();if(enabled)check(false,null);}
 void schedule(){JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(!automatic()){scheduler.cancel(JOB);context.getSystemService(NotificationManager.class).cancel(NOTICE);return;}JobInfo old=scheduler.getPendingJob(JOB);if(old==null)scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,UpdateJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL).setPersisted(true).build());}
 void opened(){if(automatic()&&System.currentTimeMillis()-prefs.getLong("checked",0)>INTERVAL)check(false,null);}
 void check(boolean manual,Runnable finished){
  if(!busy.compareAndSet(false,true)){if(finished!=null)main.post(finished);return;}
  status=text("update_checking");new Thread(()->{try{
   JSONObject json=new JSONObject(new String(fetch(UpdatePolicy.API,1024*1024),java.nio.charset.StandardCharsets.UTF_8));UpdatePolicy next=new UpdatePolicy(json);
   prefs.edit().putLong("checked",System.currentTimeMillis()).putString("release",json.toString()).apply();
   if(next.code>installed().getLongVersionCode()){if(release==null||!release.tag.equals(next.tag)){ready=false;new File(context.getCacheDir(),"update.apk").delete();}release=next;status=text("update_available",next.version);if(!manual&&automatic())notifyAvailable(next);}
   else{release=null;ready=false;status=text("update_current");context.getSystemService(NotificationManager.class).cancel(NOTICE);}
  }catch(Exception e){status=text("update_check_failed");}finally{busy.set(false);if(finished!=null)main.post(finished);}},"release-check").start();
 }
 void notifyAvailable(UpdatePolicy next){
  if(next.tag.equals(prefs.getString("notified","")))return;
  if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  NotificationManager manager=context.getSystemService(NotificationManager.class);if(!manager.areNotificationsEnabled())return;
  manager.createNotificationChannel(new NotificationChannel("updates",text("updates"),NotificationManager.IMPORTANCE_DEFAULT));
  PendingIntent open=PendingIntent.getActivity(context,NOTICE,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  manager.notify(NOTICE,new Notification.Builder(context,"updates").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(text("update_available",next.version)).setContentText(text("update_open")).setContentIntent(open).setAutoCancel(true).build());
  prefs.edit().putString("notified",next.tag).apply();
 }
 void download(){
  UpdatePolicy next=release;if(next==null||!busy.compareAndSet(false,true))return;
  ready=false;status=text("update_downloading");new Thread(()->{File staging=new File(context.getCacheDir(),"update.part");try{
   String expected=UpdatePolicy.checksum(fetch(next.checksumUrl,65536));if(!next.digest.isEmpty()&&!next.digest.equals("sha256:"+expected))throw new SecurityException("Digest disagreement");
   transfer(next.url,staging,UpdatePolicy.MAX_APK);if(staging.length()!=next.size||!expected.equals(hash(staging)))throw new SecurityException("Checksum mismatch");
   verifyPackage(staging,next);File target=new File(context.getCacheDir(),"update.apk");if(!staging.renameTo(target))throw new IOException("Cannot save update");
   prefs.edit().putString("hash",expected).apply();ready=true;status=text("update_ready");
  }catch(Exception e){ready=false;status=text("update_download_failed");}finally{staging.delete();busy.set(false);}},"release-download").start();
 }
 void install(Activity activity){
  if(!ready||busy.get()||release==null)return;
  AgentService agent=AgentService.instance;if(agent!=null&&(agent.running||agent.paused||agent.limitReached)){status=text("update_end_goal");return;}
  if(!context.getPackageManager().canRequestPackageInstalls()){status=text("update_allow_install");try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName())));}catch(Exception e){status=text("update_install_failed");}return;}
  // Recheck the private file immediately before granting the installer access.
  if(!busy.compareAndSet(false,true))return;UpdatePolicy next=release;
  new Thread(()->{try{File apk=new File(context.getCacheDir(),"update.apk");if(!hash(apk).equals(prefs.getString("hash","")))throw new SecurityException("Changed APK");verifyPackage(apk,next);
   main.post(()->{try{AgentService current=AgentService.instance;if(current!=null&&(current.running||current.paused||current.limitReached)){status=text("update_end_goal");return;}if(activity.isFinishing()||activity.isDestroyed())return;
    activity.startActivity(installerIntent());status=text("update_ready");
   }catch(Exception e){status=text("update_install_failed");}finally{busy.set(false);}});
  }catch(Exception e){ready=false;status=text("update_download_failed");busy.set(false);}},"release-verify").start();
 }
 Intent installerIntent(){
  Uri uri=Uri.parse("content://"+context.getPackageName()+".updates/anautomat.apk");Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("APK",uri));
  // APK viewers such as terminal apps must not intercept the update flow.
  java.util.List<ResolveInfo> candidates=context.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_SYSTEM_ONLY);
  for(ResolveInfo candidate:candidates){ActivityInfo info=candidate.activityInfo;if(info!=null&&info.exported){intent.setComponent(new ComponentName(info.packageName,info.name));return intent;}}
  throw new ActivityNotFoundException("System package installer unavailable");
 }
 void verifyPackage(File file,UpdatePolicy next)throws Exception{
  PackageInfo own=installed(),apk=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
  if(apk==null||!context.getPackageName().equals(apk.packageName)||apk.getLongVersionCode()!=next.code||apk.getLongVersionCode()<=own.getLongVersionCode()||!next.version.equals(apk.versionName))throw new SecurityException("Wrong package or version");
  if(own.signingInfo==null||apk.signingInfo==null)throw new SecurityException("Missing signer");
  Set<String> original=signers(own),downloaded=signers(apk);if(original.isEmpty()||!original.equals(downloaded))throw new SecurityException("Signer mismatch");
 }
 static Set<String> signers(PackageInfo info)throws Exception{Set<String> result=new HashSet<>();for(android.content.pm.Signature signature:info.signingInfo.getApkContentsSigners())result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return result;}
 static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return hex(digest.digest());}
 static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
 static byte[] fetch(String url,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();transfer(url,out,max);return out.toByteArray();}
 static void transfer(String url,File file,int max)throws Exception{try(OutputStream out=new FileOutputStream(file)){transfer(url,out,max);}}
 static void transfer(String address,OutputStream out,int max)throws Exception{
  long deadline=SystemClock.elapsedRealtime()+120000;String url=address;
  for(int redirects=0;redirects<=5;redirects++){
   if(!UpdatePolicy.trusted(url))throw new SecurityException("Untrusted download host");
   HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("User-Agent","anautomat-updater");connection.setRequestProperty("Accept","application/vnd.github+json");
   try{int code=connection.getResponseCode();if(code==301||code==302||code==303||code==307||code==308){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Missing redirect");url=new URL(new URL(url),location).toString();continue;}
    if(code!=200||connection.getContentLengthLong()>max)throw new IOException("Release download unavailable");
    try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[32768];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>max||SystemClock.elapsedRealtime()>deadline)throw new IOException("Download limit exceeded");out.write(buffer,0,n);}}return;
   }finally{connection.disconnect();}
  }throw new IOException("Too many redirects");
 }
 String text(String key,Object...args){return I18n.text(context,key,args);}
}
