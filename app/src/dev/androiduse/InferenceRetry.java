package dev.androiduse;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Retries inference only; device actions are never replayed by this policy. */
final class InferenceRetry {
 static final int MAX_RETRIES=2;
 private int attempts;
 int attempts(){return attempts;}
 void reset(){attempts=0;}
 long nextDelay(Exception error){
  if(attempts>=MAX_RETRIES||!retryable(error))return -1;
  long serverDelay=error instanceof OpenAiHttp.Failure?((OpenAiHttp.Failure)error).retryAfterMillis:0;
  if(serverDelay>86400000)return -1;
  long delay=(2000L<<attempts++)+java.util.concurrent.ThreadLocalRandom.current().nextLong(501);
  return Math.max(delay,serverDelay);
 }
 static boolean retryable(Exception error){
  if(error instanceof OpenAiHttp.Failure){OpenAiHttp.Failure e=(OpenAiHttp.Failure)error;return !permanent(e.errorCode)&&(e.code==408||e.code==429||e.code==500||e.code==502||e.code==503||e.code==504);}
  if(error instanceof Protocol.ResponseFailure)return ((Protocol.ResponseFailure)error).retryable;
  return error instanceof SocketTimeoutException||error instanceof SocketException||error instanceof UnknownHostException||error instanceof EOFException;
 }
 static boolean permanent(String code){return java.util.Arrays.asList("insufficient_quota","organization_usage_limit_exceeded","usage_limit_reached","invalid_api_key","authentication_error","permission_denied","invalid_request_error","invalid_request","unsupported_value","content_policy_violation","context_length_exceeded","model_not_found").contains(code);}
 static long retryAfter(String value,long now){
  if(value==null)return 0;
  try{double seconds=Double.parseDouble(value.trim());if(Double.isFinite(seconds)&&seconds>=0)return (long)Math.min(Long.MAX_VALUE/4d,Math.ceil(seconds*1000));}catch(NumberFormatException ignored){}
  try{return Math.max(0,ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now);}catch(Exception ignored){return 0;}
 }
}
