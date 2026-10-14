package dev.androiduse;

import java.io.*;
import java.net.*;
import org.json.*;

final class OpenAiHttp {
 static final class Failure extends IOException {final int code;Failure(int code){super("OpenAI request failed ("+code+")");this.code=code;}}
 static HttpURLConnection open(String url,String method,String bearer)throws Exception{if(!url.startsWith("https://auth.openai.com/")&&!url.startsWith("https://api.openai.com/"))throw new IOException("Unexpected OpenAI endpoint");HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(150000);if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);return c;}
 static JSONObject json(String url,String method,String bearer,String body,String contentType)throws Exception{HttpURLConnection c=open(url,method,bearer);try{if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}int code=c.getResponseCode();if(code!=200)throw new Failure(code);try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2*1024*1024)throw new IOException("OpenAI response too large");}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}}
 static void emptyPost(String url,String body)throws Exception{HttpURLConnection c=open(url,"POST",null);try{c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}int code=c.getResponseCode();if(code!=200)throw new Failure(code);}finally{c.disconnect();}}
}
