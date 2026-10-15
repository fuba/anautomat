package dev.androiduse;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.*;

/** OAuth validation and wire parsing, shared by Android and the JVM security tests. */
public final class Protocol {
 public static final String ISSUER="https://auth.openai.com", RESOURCE="https://api.openai.com/v1";
 public static final String SCOPES="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
 public static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b64(b);}
 public static String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static String challenge(String verifier)throws Exception{return b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
 public static String form(Map<String,String> values)throws Exception{StringJoiner out=new StringJoiner("&");for(Map.Entry<String,String> e:values.entrySet())out.add(URLEncoder.encode(e.getKey(),"UTF-8")+"="+URLEncoder.encode(e.getValue(),"UTF-8"));return out.toString();}
 public static Map<String,String> callback(String target,String state,String previousClient)throws Exception{
  URI uri=new URI(target);if(uri.isAbsolute()||!"/auth/callback".equals(uri.getPath()))throw new IOException("Invalid callback path");
  Map<String,String> query=new HashMap<>();if(uri.getRawQuery()==null)throw new IOException("Missing callback parameters");
  for(String item:uri.getRawQuery().split("&")){String[] pair=item.split("=",2);String k=URLDecoder.decode(pair[0],"UTF-8"),v=pair.length==2?URLDecoder.decode(pair[1],"UTF-8"):"";if(query.put(k,v)!=null)throw new IOException("Duplicate callback parameter");}
  if(!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),query.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)))throw new IOException("OAuth state mismatch");
  if(query.containsKey("error"))throw new IOException("Sign-in was denied or cancelled");
  if(query.getOrDefault("code","").isEmpty())throw new IOException("Missing authorization code");
  String client=query.getOrDefault("client_id",previousClient==null?"":previousClient);
  if(client.isEmpty()||client.equals("dynamic_agent_client")||previousClient!=null&&!client.equals(previousClient))throw new IOException("Invalid issued client ID");
  query.put("client_id",client);return query;
 }
 public static JSONObject verifyIdToken(String token,JSONObject jwks,String client,String nonce,long now)throws Exception{
  String[] parts=token.split("\\.",-1);if(parts.length!=3)throw new IOException("Invalid ID token");
  JSONObject header=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8));
  if(!"RS256".equals(header.optString("alg"))||header.has("crit"))throw new IOException("Unsupported ID-token signature algorithm");
  PublicKey key=null;JSONArray keys=jwks.getJSONArray("keys");for(int i=0;i<keys.length();i++){JSONObject k=keys.getJSONObject(i);if(header.getString("kid").equals(k.optString("kid"))&&"RSA".equals(k.optString("kty"))&&(!k.has("use")||"sig".equals(k.optString("use")))&&(!k.has("alg")||"RS256".equals(k.optString("alg"))))key=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,Base64.getUrlDecoder().decode(k.getString("n"))),new BigInteger(1,Base64.getUrlDecoder().decode(k.getString("e")))));}
  if(key==null)throw new IOException("Unknown ID-token signing key");Signature signature=Signature.getInstance("SHA256withRSA");signature.initVerify(key);signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));if(!signature.verify(Base64.getUrlDecoder().decode(parts[2])))throw new IOException("Invalid ID-token signature");
  JSONObject claims=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8));
  Object aud=claims.get("aud");boolean matched=client.equals(aud);if(aud instanceof JSONArray){JSONArray a=(JSONArray)aud;for(int i=0;i<a.length();i++)matched|=client.equals(a.getString(i));if(a.length()>1&&!client.equals(claims.optString("azp")))matched=false;}
  if(!ISSUER.equals(claims.getString("iss"))||!matched||claims.getLong("exp")<=now||claims.optLong("nbf",0)>now+60||claims.optLong("iat",now)>now+60||!nonce.equals(claims.getString("nonce"))||claims.getString("sub").isEmpty())throw new IOException("ID-token identity validation failed");return claims;
 }
 public static boolean hasPlanScope(JSONObject token){List<String> scopes=Arrays.asList(token.optString("scope").split("\\s+"));return scopes.contains("chatgpt.tokens.use.direct")&&scopes.contains("resource.invoke");}
 static final class ResponseFailure extends IOException {final boolean retryable;ResponseFailure(String message,boolean retryable){super(message);this.retryable=retryable;}}
 static String streamLine(BufferedReader reader)throws IOException{try{return reader.readLine();}catch(IOException e){if(e instanceof javax.net.ssl.SSLException)throw e;throw new ResponseFailure("ChatGPT response interrupted",true);}}
 static ResponseFailure responseFailure(JSONObject event){JSONObject response=event.optJSONObject("response"),error=response==null?event.optJSONObject("error"):response.optJSONObject("error");String code=error==null?event.optString("code"):error.optString("code");return new ResponseFailure("ChatGPT response failed",!event.optString("type").equals("response.incomplete")&&!InferenceRetry.permanent(code));}
 public static String readResponse(Reader input)throws Exception{return readResponse(input,u->{});}
 public static String readResponse(Reader input,java.util.function.Consumer<JSONObject> usage)throws Exception{return readResponse(input,usage,(kind,text)->{});}
 public static String readResponse(Reader input,java.util.function.Consumer<JSONObject> usage,java.util.function.BiConsumer<String,String> progress)throws Exception{
  BufferedReader reader=new BufferedReader(input);StringBuilder event=new StringBuilder(),text=new StringBuilder();String line;boolean completed=false;StringBuilder summary=new StringBuilder();boolean preparing=false;
  while((line=streamLine(reader))!=null){if(line.isEmpty()){if(event.length()>0){JSONObject e=new JSONObject(event.toString());event.setLength(0);String type=e.optString("type");if(type.equals("response.reasoning_summary_text.delta")){summary.append(e.getString("delta"));if(summary.length()>8000)summary.delete(0,summary.length()-8000);progress.accept("summary",summary.toString());}else if(type.equals("response.reasoning_summary_text.done")){progress.accept("summary_done",e.optString("text",summary.toString()));summary.setLength(0);}else if(type.equals("response.created")||type.equals("response.in_progress")){progress.accept("phase","thinking");}else if(type.equals("response.output_text.delta")){if(!preparing){progress.accept("phase","preparing_action");preparing=true;}text.append(e.getString("delta"));if(text.length()>50000)throw new IOException("Response too large");}else if(type.equals("response.completed")){usage.accept(e.getJSONObject("response").optJSONObject("usage"));if(!"completed".equals(e.getJSONObject("response").optString("status")))throw new ResponseFailure("Response incomplete",false);completed=true;break;}else if(type.equals("response.failed")||type.equals("response.incomplete")||type.equals("error")){JSONObject response=e.optJSONObject("response");if(response!=null)usage.accept(response.optJSONObject("usage"));throw responseFailure(e);}}}else if(line.startsWith("data:")){if(event.length()>0)event.append('\n');event.append(line.substring(5).trim());if(event.length()>2*1024*1024)throw new IOException("Event too large");}}
  if(!completed||text.length()==0)throw new ResponseFailure("Response ended before completion",true);return text.toString();
 }
 public static JSONObject validateAction(JSONObject action,int width,int height)throws Exception{
  String tool=action.getString("type");
  if(tool.equals("run_code")){if(!(action.get("reason") instanceof String)||!(action.get("code") instanceof String)||action.getString("code").isEmpty()||action.getString("code").length()>SandboxEngine.MAX_CODE)throw new IOException("Invalid sandbox code");if(action.has("input")&&!action.isNull("input")&&(!(action.get("input") instanceof String)||action.getString("input").length()>SandboxEngine.MAX_INPUT))throw new IOException("Invalid sandbox input");return action;}
  if(tool.equals("macro")){if(!(action.get("reason") instanceof String))throw new IOException("Invalid macro reason");action.put("actions",new MacroPlan(action.getJSONArray("actions"),width,height).actions);return action;}
  return validatePrimitive(action,width,height);
 }
 public static JSONObject validatePrimitive(JSONObject action,int width,int height)throws Exception{
  String type=action.getString("type");if(!Arrays.asList("tap","tap_target","fill","long_press","swipe","text","input_text","press_enter","launch","back","home","recents","notifications","quick_settings","wait","done","blocked").contains(type)||!(action.get("reason") instanceof String))throw new IOException("Invalid action");
  if(Arrays.asList("tap","long_press","swipe").contains(type)){coordinate(action,"x",width);coordinate(action,"y",height);if(type.equals("swipe")){coordinate(action,"x2",width);coordinate(action,"y2",height);}}
  if((type.equals("tap_target")||type.equals("fill"))&&(!(action.get("target") instanceof String)||action.getString("target").isEmpty()||action.getString("target").length()>256||action.getString("target").equals("id:")))throw new IOException("Invalid UI target");
  if((type.equals("text")||type.equals("input_text")||type.equals("fill"))&&(!(action.get("text") instanceof String)||action.getString("text").length()>10000))throw new IOException("Invalid text action");if(type.equals("launch")&&!action.getString("package").matches("[a-zA-Z0-9_.]+"))throw new IOException("Invalid package");return action;
 }
 static void coordinate(JSONObject a,String k,int max)throws Exception{Object value=a.get(k);if(!(value instanceof Number))throw new IOException("Invalid coordinate");double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n<0||n>=max)throw new IOException("Coordinate outside screen");}
 private Protocol(){}
}
