package dev.androiduse;
import org.json.JSONObject;
final class UsageMeter {
 private long used;private String remaining;
 synchronized void usage(JSONObject u){if(u!=null)used+=u.optLong("total_tokens",u.optLong("input_tokens",0)+u.optLong("output_tokens",0));}
 synchronized void headers(String value){remaining=value!=null&&value.matches("[0-9]+")?value:null;}
 synchronized String summary(){return summary("Goal usage: %1$d tokens","Rate-limit tokens remaining: %1$s");}
 synchronized String summary(String usedTemplate,String remainingTemplate){return String.format(java.util.Locale.ROOT,usedTemplate,used)+(remaining==null?"":"\n"+String.format(java.util.Locale.ROOT,remainingTemplate,remaining));}
}
