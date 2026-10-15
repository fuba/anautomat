package dev.androiduse;

/** Seen state outlives overlay recreation; a streaming append is the same message. */
final class TickerState {
 private final java.util.Set<String> seen=new java.util.HashSet<>();
 private String text="";private boolean dismissed=true;private long until;
 String text(){return text;}
 void update(String value,long now,boolean eligible){
  String next=value.replace('\n',' ').trim();
  if(next.equals(text)){if(!eligible)dismiss();return;}
  boolean append=!text.isEmpty()&&next.startsWith(text);
  boolean known=!seen.add(key(next));
  text=next;
  if(!append){dismissed=known||!eligible;until=now+8000;}
  if(!eligible)dismiss();
 }
 void dismiss(){dismissed=true;}
 void dismiss(String expected){if(text.equals(expected)||!expected.isEmpty()&&text.startsWith(expected))dismiss();}
 boolean visible(long now){return !dismissed&&!text.isEmpty()&&now<until;}
 private static String key(String text){try{return java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
