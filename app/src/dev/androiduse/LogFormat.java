package dev.androiduse;

import java.text.SimpleDateFormat;
import java.util.*;

final class LogFormat {
 static String compact(String text){return text.replace("\r\n","\n").replace('\r','\n').replaceAll("[\\t ]+\\n","\n").replaceAll("\\n{2,}","\n").trim();}
 static String filename(String goal,Date started){
  String title=compact(goal).split("[\\n。！？!?]",2)[0].replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]"," ").replaceAll("\\s+"," ").trim();
  if(title.isEmpty())title="Goal";
  int length=title.codePointCount(0,title.length());if(length>36)title=title.substring(0,title.offsetByCodePoints(0,36));
  SimpleDateFormat clock=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS",Locale.JAPAN);clock.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
  return clock.format(started)+"_"+title+".txt";
 }
}
