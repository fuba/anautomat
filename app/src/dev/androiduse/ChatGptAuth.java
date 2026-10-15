package dev.androiduse;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class ChatGptAuth {
 private static ChatGptAuth singleton;
 static synchronized ChatGptAuth get(Context c){if(singleton==null)singleton=new ChatGptAuth(c.getApplicationContext());return singleton;}
 final Context context;final SecureStore store;
 volatile String status="";volatile boolean signingIn=false;
 private JSONObject account;private ServerSocket listener;private volatile int epoch=0;private final Object refreshLock=new Object();
 ChatGptAuth(Context c){context=c;status=I18n.text(c,"not_signed_in");store=new SecureStore(c);try{account=store.read();if(account!=null)status=Protocol.hasPlanScope(account)?"ChatGPT: "+account.optString("email",I18n.text(context,"connected")):I18n.text(context,"signed_out_ready");}catch(Exception e){status=I18n.text(context,"auth_read_failed");}}
 synchronized boolean connected(){return account!=null&&Protocol.hasPlanScope(account);}
 synchronized String model(){return account==null?"":account.optString("model");}
 synchronized JSONArray models(){return account==null?new JSONArray():account.optJSONArray("models")==null?new JSONArray():account.optJSONArray("models");}
 synchronized void selectModel(String slug)throws Exception{if(account==null)throw new IOException(I18n.text(context,"sign_in_first"));account.put("model",slug);store.write(account);}
 synchronized void cancel(){epoch++;signingIn=false;if(listener!=null)try{listener.close();}catch(IOException ignored){}listener=null;}
 void logout(){JSONObject old;int current;synchronized(this){cancel();current=epoch;old=account;try{if(old!=null){account=new JSONObject().put("client_id",old.getString("client_id")).put("subject",old.getString("subject")).put("email",old.optString("email"));store.write(account);}else store.clear();status=I18n.text(context,"signed_out");}catch(Exception e){account=null;store.clear();status=I18n.text(context,"signed_out");}}
  if(old!=null&&!old.optString("refresh_token").isEmpty())try{JSONObject discovery=OpenAiHttp.json(Protocol.ISSUER+"/.well-known/openid-configuration","GET",null,null,null);Map<String,String> form=new LinkedHashMap<>();form.put("token",old.getString("refresh_token"));form.put("token_type_hint","refresh_token");form.put("client_id",old.getString("client_id"));OpenAiHttp.emptyPost(discovery.getString("revocation_endpoint"),Protocol.form(form));}catch(Exception e){synchronized(this){if(current==epoch)status=I18n.text(context,"revoke_failed");}}
 }
 synchronized String begin()throws Exception{
  if(signingIn)throw new IOException(I18n.text(context,"signing_in"));
  int attempt=++epoch;String state=Protocol.random(),nonce=Protocol.random(),verifier=Protocol.random();JSONObject previous=account==null?null:new JSONObject(account.toString());
  SharedPreferences prefs=context.getSharedPreferences("installation",0);String host=prefs.getString("host",null);if(host==null){host="urn:uuid:"+UUID.randomUUID();if(!prefs.edit().putString("host",host).commit())throw new IOException(I18n.text(context,"device_id_failed"));}
  ServerSocket socket=new ServerSocket();socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));socket.setSoTimeout(1000);listener=socket;
  String redirect="http://127.0.0.1:"+socket.getLocalPort()+"/auth/callback";
  Map<String,String> params=new LinkedHashMap<>();params.put("client_id",previous==null?"dynamic_agent_client":previous.getString("client_id"));if(previous==null)params.put("agent_name_hint",I18n.text(context,"app_name"));else if(!previous.optString("id_token").isEmpty())params.put("id_token_hint",previous.getString("id_token"));params.put("ext_agent_host_id",host);params.put("response_type","code");params.put("redirect_uri",redirect);params.put("scope",Protocol.SCOPES);params.put("resource",Protocol.RESOURCE);params.put("state",state);params.put("nonce",nonce);params.put("code_challenge_method","S256");params.put("code_challenge",Protocol.challenge(verifier));
  String url=Protocol.ISSUER+"/api/accounts/authorize?"+Protocol.form(params);signingIn=true;status=I18n.text(context,"browser_sign_in");
  new Thread(()->awaitCallback(socket,attempt,state,nonce,verifier,redirect,previous),"ChatGPT-sign-in").start();return url;
 }
 private void awaitCallback(ServerSocket server,int attempt,String state,String nonce,String verifier,String redirect,JSONObject previous){
  long deadline=System.currentTimeMillis()+10*60*1000;Socket accepted=null;
  try{
   Map<String,String> callback=null;
   while(System.currentTimeMillis()<deadline&&attempt==epoch){Socket client=null;try{client=server.accept();
    client.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));String line=in.readLine();if(line==null||line.length()>16000){respond(client,400,"Invalid request");continue;}String[] request=line.split(" ");
    if(request.length!=3||!request[0].equals("GET")){respond(client,400,"Invalid request");continue;}
    try{callback=Protocol.callback(request[1],state,previous==null?null:previous.getString("client_id"));}catch(Exception e){respond(client,400,"Sign-in could not be verified. Return to anautomat.");if(e.getMessage().contains("denied"))throw e;continue;}
    accepted=client;client=null;break;
   }catch(SocketTimeoutException ignored){}finally{if(client!=null)try{client.close();}catch(IOException ignored){}}}
   if(callback==null)throw new IOException(I18n.text(context,"sign_in_timeout"));
   server.close();synchronized(this){if(attempt!=epoch)return;status=I18n.text(context,"verifying_auth");}
   Map<String,String> form=new LinkedHashMap<>();form.put("grant_type","authorization_code");form.put("client_id",callback.get("client_id"));form.put("code",callback.get("code"));form.put("code_verifier",verifier);form.put("redirect_uri",redirect);form.put("resource",Protocol.RESOURCE);
   JSONObject tokens=OpenAiHttp.json(Protocol.ISSUER+"/api/accounts/oauth/token","POST",null,Protocol.form(form),"application/x-www-form-urlencoded");
   JSONObject jwks=OpenAiHttp.json(Protocol.ISSUER+"/.well-known/jwks.json","GET",null,null,null);
   JSONObject identity=Protocol.verifyIdToken(tokens.getString("id_token"),jwks,callback.get("client_id"),nonce,System.currentTimeMillis()/1000);
   if(previous!=null&&!previous.getString("subject").equals(identity.getString("sub")))throw new IOException(I18n.text(context,"wrong_account"));
   if(!Protocol.hasPlanScope(tokens))throw new IOException(I18n.text(context,"plan_scope_missing"));
   if(!"Bearer".equalsIgnoreCase(tokens.getString("token_type"))||tokens.getString("access_token").isEmpty())throw new IOException("Invalid token response");
   tokens.put("client_id",callback.get("client_id"));tokens.put("subject",identity.getString("sub"));tokens.put("email",identity.optString("email",I18n.text(context,"connected")));tokens.put("expires_at",System.currentTimeMillis()/1000+tokens.getLong("expires_in"));
   synchronized(this){if(attempt!=epoch)return;store.write(tokens);account=tokens;status="ChatGPT: "+tokens.getString("email");}
   // Only verified and persisted credentials permit the browser-to-app handoff.
   if(accepted!=null){try{respond(accepted,200,I18n.text(context,"sign_in_complete"));}catch(IOException ignored){}try{accepted.close();}catch(IOException ignored){}accepted=null;}
   new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{synchronized(ChatGptAuth.this){if(attempt!=epoch)return;}try{context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(RuntimeException ignored){/* The browser return link remains available. */}});
   try{loadModels();}catch(Exception e){synchronized(this){if(attempt==epoch)status=I18n.text(context,"models_failed");}}
  }catch(Exception e){synchronized(this){if(attempt==epoch)status=I18n.text(context,"sign_in_failed")+safeError(e);}}
  finally{if(accepted!=null)try{respond(accepted,400,I18n.text(context,"sign_in_return_failed"));accepted.close();}catch(IOException ignored){try{accepted.close();}catch(IOException ignoredAgain){}}try{server.close();}catch(IOException ignored){}synchronized(this){if(attempt==epoch){signingIn=false;listener=null;context.stopService(new Intent(context,SignInService.class));}}}
 }
 private void respond(Socket socket,int code,String message)throws IOException{byte[] body=AuthReturnPage.html(code==200,message,I18n.text(context,"return_to_app")).getBytes("UTF-8");OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+"\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(body);out.flush();}
 String accessToken()throws Exception{synchronized(refreshLock){JSONObject saved;int current;synchronized(this){if(!connected())throw new IOException(I18n.text(context,"please_sign_in"));saved=new JSONObject(account.toString());current=epoch;}long now=System.currentTimeMillis()/1000;
  if(saved.getLong("expires_at")<=now+60){if(saved.optString("refresh_token").isEmpty())throw new IOException(I18n.text(context,"sign_in_again"));Map<String,String> form=new LinkedHashMap<>();form.put("grant_type","refresh_token");form.put("client_id",saved.getString("client_id"));form.put("refresh_token",saved.getString("refresh_token"));form.put("resource",Protocol.RESOURCE);JSONObject refreshed=OpenAiHttp.json(Protocol.ISSUER+"/api/accounts/oauth/token","POST",null,Protocol.form(form),"application/x-www-form-urlencoded");if(!"Bearer".equalsIgnoreCase(refreshed.getString("token_type"))||!Protocol.hasPlanScope(refreshed)||refreshed.getString("refresh_token").isEmpty())throw new IOException("Refresh did not return required credentials");synchronized(this){if(current!=epoch||!connected())throw new IOException(I18n.text(context,"auth_changed"));JSONObject next=new JSONObject(account.toString());for(String key:new String[]{"access_token","refresh_token","scope","expires_in","earliest_refresh_at"})if(refreshed.has(key))next.put(key,refreshed.get(key));next.put("expires_at",System.currentTimeMillis()/1000+refreshed.getLong("expires_in"));store.write(next);account=next;saved=next;}}
  synchronized(this){if(current!=epoch||!connected())throw new IOException(I18n.text(context,"auth_changed"));}return saved.getString("access_token");}
 }
 void loadModels()throws Exception{int current=epoch;String access=accessToken();JSONObject result=OpenAiHttp.json(Protocol.RESOURCE+"/models","GET",access,null,null);JSONArray visible=new JSONArray(),all=result.getJSONArray("models");for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);if("list".equals(m.optString("visibility")))visible.put(new JSONObject().put("slug",m.getString("slug")).put("display_name",m.getString("display_name")));}if(visible.length()==0)throw new IOException(I18n.text(context,"no_models"));synchronized(this){if(current!=epoch||!connected())return;JSONObject next=new JSONObject(account.toString());next.put("models",visible);boolean exists=false;for(int i=0;i<visible.length();i++)exists|=visible.getJSONObject(i).getString("slug").equals(next.optString("model"));if(!exists)next.put("model",visible.getJSONObject(0).getString("slug"));store.write(next);account=next;status="ChatGPT: "+next.getString("email");}}
 static String safeError(Exception e){if(e instanceof OpenAiHttp.Failure){int code=((OpenAiHttp.Failure)e).code;return I18n.text(singleton.context,code==429?"http_rate_limit":code==401?"http_unauthorized":code==403?"http_forbidden":"http_failure",code);}return e instanceof IOException?e.getMessage():I18n.text(singleton.context,"data_invalid");}
}
