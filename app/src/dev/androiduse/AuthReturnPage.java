package dev.androiduse;

/** No authorization codes, tokens or account data enter the browser return link. */
final class AuthReturnPage {
 static final String INTENT="intent://auth/complete#Intent;scheme=anautomat;package=dev.androiduse;launchFlags=0x14000000;end";
 static String html(boolean success,String message,String button){
  return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>anautomat</title>"
   +(success?"<meta http-equiv=refresh content=\"0;url="+INTENT+"\">":"")
   +"<style>body{margin:0;background:#f7f7f5;color:#161616;font:16px system-ui}main{max-width:420px;margin:15vh auto;padding:32px}h1{font-size:28px}p{line-height:1.6}a{display:inline-block;margin-top:16px;padding:14px 22px;background:#161616;color:white;text-decoration:none;border-radius:14px}</style></head><body><main><h1>anautomat</h1><p>"
   +escape(message)+"</p>"+(success?"<a href=\""+INTENT+"\">"+escape(button)+"</a>":"")+"</main></body></html>";
 }
 private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
 private AuthReturnPage(){}
}
