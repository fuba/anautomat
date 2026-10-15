package dev.androiduse;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** Fully validate the entire batch before the first device operation. No nested tools. */
final class MacroPlan {
 static final int MAX_ACTIONS=32,MAX_MILLIS=15000,DEFAULT_DELAY=100;
 static final Set<String> TYPES=new HashSet<>(Arrays.asList("tap","long_press","swipe","text","input_text","press_enter","launch","back","home","recents","notifications","quick_settings","wait"));
 static final Set<String> KEYS=new HashSet<>(Arrays.asList("type","reason","x","y","x2","y2","text","package","delay_ms","expect_package","expect_text"));
 final JSONArray actions;final int count;
 MacroPlan(JSONArray input,int width,int height)throws Exception{
  if(input.length()<1||input.length()>MAX_ACTIONS||input.toString().length()>32768)throw new IOException("Macro must contain 1 to 32 bounded actions");
  actions=new JSONArray(input.toString());int duration=0;
  for(int i=0;i<actions.length();i++){
   JSONObject action=actions.getJSONObject(i);for(java.util.Iterator<String> keys=action.keys();keys.hasNext();){String key=keys.next();if(!KEYS.contains(key))throw new IOException("Unknown macro field: "+key);}
   if(!TYPES.contains(action.getString("type")))throw new IOException("Nested or unknown macro action");
   if(!action.has("reason"))action.put("reason","");Protocol.validatePrimitive(action,width,height);
   int delay=delay(action);duration+=delay+gestureDuration(action);
   for(String key:new String[]{"expect_package","expect_text"})if(action.has(key)&&!action.isNull(key)&&(!(action.get(key) instanceof String)||action.getString(key).isEmpty()||action.getString(key).length()>256))throw new IOException("Invalid macro guard");
   if(Arrays.asList("tap","long_press","swipe","text","input_text","press_enter").contains(action.getString("type"))&&guard(action,"expect_package").isEmpty())throw new IOException("Touch and text macros require expect_package");
   if(action.has("expect_package")&&!action.isNull("expect_package")&&!action.getString("expect_package").matches("[a-zA-Z0-9_.]+"))throw new IOException("Invalid package guard");
  }if(duration>MAX_MILLIS)throw new IOException("Macro duration limit exceeded");count=actions.length();
 }
 static int delay(JSONObject action)throws Exception{if(!action.has("delay_ms")||action.isNull("delay_ms"))return DEFAULT_DELAY;Object value=action.get("delay_ms");if(!(value instanceof Number))throw new IOException("Invalid macro delay");double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n!=Math.floor(n)||n<0||n>2000)throw new IOException("Macro delay must be 0 to 2000 ms");return (int)n;}
 static String guard(JSONObject action,String key)throws Exception{return action.isNull(key)?"":action.getString(key);}
 static int gestureDuration(JSONObject a){switch(a.optString("type")){case "tap":return 100;case "long_press":return 900;case "swipe":return 500;default:return 0;}}
}
