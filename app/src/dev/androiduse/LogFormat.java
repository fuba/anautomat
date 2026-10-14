package dev.androiduse;

import java.text.SimpleDateFormat;
import java.util.*;

final class LogFormat {
 static String compact(String text){
  String normalized=text.replace("\r\n","\n").replace('\r','\n');StringBuilder out=new StringBuilder();int start=0;
  for(int i=0;i<=normalized.length();i++){if(i==normalized.length()||normalized.charAt(i)=='\n'){int end=i;while(end>start&&(normalized.charAt(end-1)==' '||normalized.charAt(end-1)=='\t'))end--;if(end>start){if(out.length()>0)out.append('\n');out.append(normalized,start,end);}start=i+1;}}
  return out.toString().trim();
 }
 static String filename(String goal,Date started){
  String title=compact(goal).split("[\\n\u3002\uFF01\uFF1F!?]",2)[0].replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]"," ").replaceAll("\\s+"," ").trim();
  if(title.isEmpty())title="Goal";
  int length=title.codePointCount(0,title.length());if(length>36)title=title.substring(0,title.offsetByCodePoints(0,36));
  SimpleDateFormat clock=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS",Locale.JAPAN);clock.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
  return clock.format(started)+"_"+title+".txt";
 }
}
