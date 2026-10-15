package dev.androiduse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Untrusted release metadata never supplies arbitrary download hosts or filenames. */
final class UpdatePolicy {
 static final String API="https://api.github.com/repos/fuba/anautomat/releases/latest";
 static final int MAX_APK=50*1024*1024;
 final String tag,version,url,checksumUrl,digest;
 final long code,size;
 UpdatePolicy(JSONObject json)throws Exception{
  if(json.getBoolean("draft")||json.getBoolean("prerelease"))throw new IllegalArgumentException("Not a stable release");
  tag=json.getString("tag_name");code=versionCode(tag);version=tag.substring(1);
  String base="https://github.com/fuba/anautomat/releases/download/"+tag+"/";
  JSONObject apk=asset(json,"anautomat.apk"),sums=asset(json,"SHA256SUMS");
  url=base+"anautomat.apk";checksumUrl=base+"SHA256SUMS";
  if(!url.equals(apk.getString("browser_download_url"))||!checksumUrl.equals(sums.getString("browser_download_url")))throw new IllegalArgumentException("Unexpected asset URL");
  size=apk.getLong("size");if(size<1||size>MAX_APK||sums.getLong("size")<1||sums.getLong("size")>65536)throw new IllegalArgumentException("Unexpected asset size");
  digest=apk.optString("digest","");if(!digest.isEmpty()&&!digest.matches("sha256:[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid digest");
 }
 static long versionCode(String tag){
  if(!tag.matches("v(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})"))throw new IllegalArgumentException("Invalid release version");
  String[] p=tag.substring(1).split("\\.");return Long.parseLong(p[0])*1000000+Long.parseLong(p[1])*1000+Long.parseLong(p[2]);
 }
 static JSONObject asset(JSONObject json,String name)throws Exception{
  JSONObject found=null;JSONArray assets=json.getJSONArray("assets");
  for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(name.equals(a.optString("name"))){if(found!=null||!"uploaded".equals(a.optString("state")))throw new IllegalArgumentException("Invalid release asset");found=a;}}
  if(found==null)throw new IllegalArgumentException("Missing release asset");return found;
 }
 static String checksum(byte[] bytes){
  String found=null;for(String line:new String(bytes,StandardCharsets.US_ASCII).split("\\r?\\n")){
   if(line.matches("[a-fA-F0-9]{64} [ *]anautomat\\.apk")){if(found!=null)throw new IllegalArgumentException("Duplicate checksum");found=line.substring(0,64).toLowerCase(java.util.Locale.ROOT);}
  }if(found==null)throw new IllegalArgumentException("Missing APK checksum");return found;
 }
 static boolean trusted(String address){try{URI u=new URI(address);String host=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&("api.github.com".equals(host)||"github.com".equals(host)||"release-assets.githubusercontent.com".equals(host)||"objects.githubusercontent.com".equals(host));}catch(Exception e){return false;}}
}
