package dev.androiduse;

import org.json.JSONObject;

/** Bound nesting before recursive JSON parsing in the privileged app process. */
final class AutomationJson {
 static JSONObject object(String text,int limit)throws Exception{
  check(text,limit);return new JSONObject(text);
 }
 static void check(String text,int limit){
  if(text==null||text.length()>limit)throw new IllegalArgumentException("JSON size limit exceeded");
  int depth=0;boolean quoted=false,escaped=false;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(c<32)throw new IllegalArgumentException("Unescaped JSON control character");if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
   else if(c=='"')quoted=true;else if(c=='['||c=='{'){if(++depth>32)throw new IllegalArgumentException("JSON nesting limit exceeded");}else if(c==']'||c=='}'){if(--depth<0)throw new IllegalArgumentException("Invalid JSON nesting");}
   else if(!Character.isWhitespace(c)&&!Character.isLetterOrDigit(c)&&":,+-.".indexOf(c)<0)throw new IllegalArgumentException("Non-JSON token");
  }if(quoted||depth!=0)throw new IllegalArgumentException("Incomplete JSON");
 }
 private AutomationJson(){}
}
