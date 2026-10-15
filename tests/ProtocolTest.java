package dev.androiduse;
import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import org.json.*;

public final class ProtocolTest {
 static int passed;
 interface Work{void run()throws Exception;}
 static void reject(Work work)throws Exception{try{work.run();}catch(Exception e){passed++;return;}throw new AssertionError("Invalid input was accepted");}
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static String jwt(JSONObject claims,KeyPair pair)throws Exception{String body=Protocol.b64("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8))+"."+Protocol.b64(claims.toString().getBytes(StandardCharsets.UTF_8));Signature s=Signature.getInstance("SHA256withRSA");s.initSign(pair.getPrivate());s.update(body.getBytes(StandardCharsets.US_ASCII));return body+"."+Protocol.b64(s.sign());}
 public static void main(String[] args)throws Exception{
  check(Protocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"));
  check(Protocol.callback("/auth/callback?state=valid&code=code&client_id=oaiapp_test","valid",null).get("client_id").equals("oaiapp_test"));
  check(Protocol.callback("/auth/callback?state=valid&code=code","valid","oaiapp_test").get("client_id").equals("oaiapp_test"));
  reject(()->Protocol.callback("/auth/callback?state=wrong&code=code&client_id=oaiapp_test","valid",null));
  reject(()->Protocol.callback("/auth/callback?state=valid&state=valid&code=c&client_id=oaiapp_test","valid",null));
  reject(()->Protocol.callback("/auth/callback?state=valid&code=c","valid",null));
  reject(()->Protocol.callback("/auth/callback?state=valid&code=c&client_id=other","valid","oaiapp_test"));
  reject(()->Protocol.callback("/callback?state=valid&code=c&client_id=oaiapp_test","valid",null));
  reject(()->Protocol.callback("/auth/callback?state=valid&error=access_denied","valid",null));
  check(!Protocol.hasPlanScope(new JSONObject().put("scope","openid email")));
  check(Protocol.hasPlanScope(new JSONObject().put("scope",Protocol.SCOPES)));
  KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);KeyPair pair=gen.generateKeyPair();RSAPublicKey key=(RSAPublicKey)pair.getPublic();
  JSONObject jwks=new JSONObject().put("keys",new JSONArray().put(new JSONObject().put("kty","RSA").put("kid","test").put("n",Protocol.b64(key.getModulus().toByteArray())).put("e",Protocol.b64(key.getPublicExponent().toByteArray()))));
  JSONObject claims=new JSONObject().put("iss",Protocol.ISSUER).put("sub","user").put("aud","oaiapp_test").put("nonce","nonce").put("exp",2000);
  check(Protocol.verifyIdToken(jwt(claims,pair),jwks,"oaiapp_test","nonce",1000).getString("sub").equals("user"));
  reject(()->Protocol.verifyIdToken(jwt(claims,pair),jwks,"other","nonce",1000));
  reject(()->Protocol.verifyIdToken(jwt(claims,pair),jwks,"oaiapp_test","bad-nonce",1000));
  reject(()->Protocol.verifyIdToken(jwt(claims,pair),jwks,"oaiapp_test","nonce",2000));
  JSONObject wrongIssuer=new JSONObject(claims.toString()).put("iss","https://attacker.test");reject(()->Protocol.verifyIdToken(jwt(wrongIssuer,pair),jwks,"oaiapp_test","nonce",1000));
  KeyPair attacker=gen.generateKeyPair();reject(()->Protocol.verifyIdToken(jwt(claims,attacker),jwks,"oaiapp_test","nonce",1000));
  reject(()->Protocol.verifyIdToken(Protocol.b64("{\"alg\":\"none\"}".getBytes())+"."+Protocol.b64(claims.toString().getBytes())+".",jwks,"oaiapp_test","nonce",1000));
  String delta="data: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}\n\n";
  check(Protocol.readResponse(new StringReader(delta+"data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n")).equals("hello"));
  reject(()->Protocol.readResponse(new StringReader(delta)));reject(()->Protocol.readResponse(new StringReader(delta+"data: {\"type\":\"response.failed\"}\n\n")));
  reject(()->Protocol.readResponse(new StringReader(delta+"data: {\"type\":\"response.completed\",\"response\":{\"status\":\"incomplete\"}}\n\n")));
  check(Protocol.validateAction(new JSONObject("{\"type\":\"tap\",\"reason\":\"ok\",\"x\":1,\"y\":1}"),100,100).getString("type").equals("tap"));
  reject(()->Protocol.validateAction(new JSONObject("{\"type\":\"tap\",\"reason\":\"\",\"x\":100,\"y\":1}"),100,100));
  reject(()->Protocol.validateAction(new JSONObject("{\"type\":\"tap\",\"reason\":\"\",\"x\":\"1\",\"y\":1}"),100,100));
  reject(()->Protocol.validateAction(new JSONObject("{\"type\":\"shell\",\"reason\":\"\"}"),100,100));
  check(Protocol.validateAction(new JSONObject().put("type","input_text").put("reason","terminal").put("text","echo test\n"),100,100).getString("text").endsWith("\n"));
  check(Protocol.validateAction(new JSONObject().put("type","press_enter").put("reason","submit"),100,100).getString("type").equals("press_enter"));
  reject(()->Protocol.validateAction(new JSONObject().put("type","input_text").put("reason","bad").put("text",JSONObject.NULL),100,100));
  reject(()->Protocol.validateAction(new JSONObject().put("type","input_text").put("reason","too long").put("text","x".repeat(10001)),100,100));
  String returnPage=AuthReturnPage.html(true,"<script>alert('x')</script>","Open & return");
  check(returnPage.contains(AuthReturnPage.INTENT)&&returnPage.contains("http-equiv=refresh"));
  check(!returnPage.contains("<script>")&&returnPage.contains("&lt;script&gt;")&&returnPage.contains("Open &amp; return"));
  check(!AuthReturnPage.html(false,"Failed","Return").contains("intent://"));
  check(!AuthReturnPage.INTENT.contains("token")&&!AuthReturnPage.INTENT.contains("code="));
  check(GoalControlState.mode(false,false,false)==GoalControlState.Mode.IDLE);check(GoalControlState.mode(true,false,false)==GoalControlState.Mode.RUNNING);check(GoalControlState.mode(false,true,false)==GoalControlState.Mode.PAUSED);check(GoalControlState.mode(false,false,true)==GoalControlState.Mode.LIMIT);check(GoalControlState.mode(true,true,true)==GoalControlState.Mode.RUNNING);
  TickerState ticker=new TickerState();ticker.update("First",0,true);check(ticker.visible(0));ticker.dismiss();check(!ticker.visible(1));ticker.update("First appended",2,true);check(!ticker.visible(2));ticker.update("Second",3,true);check(ticker.visible(3));ticker.update("First",4,true);check(!ticker.visible(4));ticker.update("Hidden in app",5,false);ticker.update("Hidden in app",6,true);check(!ticker.visible(6));ticker.update("Fresh",7,true);check(!ticker.visible(8007));ticker.update("Fresh appended",9000,true);check(!ticker.visible(9000));
  TickerState gestureTicker=new TickerState();gestureTicker.update("Old message",0,true);gestureTicker.update("New message",1,true);gestureTicker.dismiss("Old message");check(gestureTicker.visible(1));gestureTicker.update("New message appended",2,true);gestureTicker.dismiss("New message");check(!gestureTicker.visible(2));
  InferenceRetry retry=new InferenceRetry();Exception transientFailure=new Protocol.ResponseFailure("ChatGPT response failed",true);
  long firstDelay=retry.nextDelay(transientFailure),secondDelay=retry.nextDelay(transientFailure);check(firstDelay>=2000&&firstDelay<=2500);check(secondDelay>=4000&&secondDelay<=4500);check(retry.nextDelay(transientFailure)==-1);retry.reset();check(retry.attempts()==0&&retry.nextDelay(transientFailure)>=2000);
  check(!InferenceRetry.retryable(new OpenAiHttp.Failure(401)));check(!InferenceRetry.retryable(new OpenAiHttp.Failure(403)));check(!InferenceRetry.retryable(new OpenAiHttp.Failure(400)));check(!InferenceRetry.retryable(new OpenAiHttp.Failure(429,"insufficient_quota",0)));check(InferenceRetry.retryable(new OpenAiHttp.Failure(503)));check(InferenceRetry.retryable(new java.net.SocketTimeoutException()));check(!InferenceRetry.retryable(new javax.net.ssl.SSLException("certificate")));check(!InferenceRetry.retryable(new IOException("Invalid response")));
  check(new InferenceRetry().nextDelay(new OpenAiHttp.Failure(429,"rate_limit_exceeded",12000))==12000);check(new InferenceRetry().nextDelay(new OpenAiHttp.Failure(429,"rate_limit_exceeded",86400001))==-1);
  check(InferenceRetry.retryAfter("12",0)==12000);check(InferenceRetry.retryAfter("Wed, 01 Jan 2025 00:00:10 GMT",1735689600000L)==10000);check(InferenceRetry.retryAfter("bad",0)==0);
  try{Protocol.readResponse(new StringReader("data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"server_error\"}}}\n\n"));throw new AssertionError("Failed response accepted");}catch(Protocol.ResponseFailure e){check(e.retryable);}
  try{Protocol.readResponse(new StringReader("data: {\"type\":\"error\",\"code\":\"insufficient_quota\"}\n\n"));throw new AssertionError("Quota error accepted");}catch(Protocol.ResponseFailure e){check(!e.retryable);}
  try{Protocol.readResponse(new StringReader("data: {\"type\":\"response.incomplete\"}\n\n"));throw new AssertionError("Incomplete response accepted");}catch(Protocol.ResponseFailure e){check(!e.retryable);}
  try{Protocol.readResponse(new StringReader(delta));throw new AssertionError("Partial action accepted");}catch(Protocol.ResponseFailure e){check(e.retryable);}
  UsageMeter meter=new UsageMeter();String completed="data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"usage\":{\"input_tokens\":100,\"output_tokens\":20,\"total_tokens\":120}}}\n\n";
  Protocol.readResponse(new StringReader(delta+completed),meter::usage);Protocol.readResponse(new StringReader(delta+completed),meter::usage);check(meter.summary().contains("240 tokens"));
  UsageMeter failedMeter=new UsageMeter();try{Protocol.readResponse(new StringReader("data: {\"type\":\"response.failed\",\"response\":{\"usage\":{\"total_tokens\":13}}}\n\n"),failedMeter::usage);}catch(Protocol.ResponseFailure expected){}check(failedMeter.summary().contains("13 tokens"));
  meter.headers("1234");check(meter.summary().contains("Rate-limit tokens remaining: 1234"));meter.headers(null);check(meter.summary().equals("Goal usage: 240 tokens"));
  meter.headers("not-a-number");check(meter.summary().equals("Goal usage: 240 tokens"));
  reject(()->Protocol.readResponse(new StringReader(delta),meter::usage));check(meter.summary().contains("240 tokens"));
  java.util.List<String> progress=new java.util.ArrayList<>();String publicSummary="data: {\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"Checking screen\"}\n\ndata: {\"type\":\"response.reasoning_summary_text.done\",\"text\":\"Screen checked\"}\n\n";
  String hidden="data: {\"type\":\"response.reasoning_text.delta\",\"delta\":\"PRIVATE_REASONING\"}\n\n";
  check(Protocol.readResponse(new StringReader(publicSummary+hidden+delta+completed),u->{},(kind,value)->progress.add(kind+":"+value)).equals("hello"));
  check(progress.contains("summary:Checking screen"));check(progress.contains("summary_done:Screen checked"));check(progress.stream().noneMatch(v->v.contains("PRIVATE_REASONING")));check(progress.stream().noneMatch(v->v.contains("hello")));
  StepBudget budget=new StepBudget();for(int i=0;i<99;i++)budget.consume();check(!budget.exhausted(100));budget.consume();check(budget.exhausted(100));check(!budget.exhausted(101));budget.renew();check(!budget.exhausted(1));budget.consume();check(budget.exhausted(1));
  check(LogFormat.compact("  one\n\n\n two  ").equals("one\n two"));check(LogFormat.filename("Open Settings! Detailed steps",new java.util.Date(0)).equals("1970-01-01_09-00-00-000_Open Settings.txt"));check(!LogFormat.filename("a/b:c*?",new java.util.Date()).contains("/"));check(LogFormat.filename("",new java.util.Date()).endsWith("_Goal.txt"));
  check(LogFormat.compact("A"+"\t".repeat(100000)+"\n\nB").equals("A\nB"));
  System.out.println("PASS: "+passed+" OAuth / JWT / SSE / action / usage / progress checks");
 }
}
