package dev.androiduse;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Fail-closed API allowlist, additional to Android's permissionless process boundary. */
final class JavaBytecodePolicy {
 static final String PROGRAM="dev.androiduse.generated.GoalProgram";
 static final Set<String> ALLOWED=new HashSet<>(Arrays.asList(
  "java/lang/Object","java/lang/String","java/lang/StringBuilder","java/lang/Math","java/lang/StrictMath",
  "java/lang/Number","java/lang/Boolean","java/lang/Byte","java/lang/Short","java/lang/Integer","java/lang/Long","java/lang/Float","java/lang/Double","java/lang/Character",
  "java/lang/Exception","java/lang/RuntimeException","java/lang/IllegalArgumentException","java/lang/ArithmeticException","java/lang/IndexOutOfBoundsException","java/lang/NullPointerException","java/lang/NegativeArraySizeException","java/lang/Comparable","java/lang/CharSequence","java/lang/Iterable",
  "java/util/List","java/util/ArrayList","java/util/Map","java/util/HashMap","java/util/Map$Entry","java/util/Set","java/util/HashSet","java/util/Collection","java/util/Iterator","java/util/Collections","java/util/Arrays","java/util/Comparator",
  "java/math/BigInteger","java/math/BigDecimal","java/math/MathContext","java/math/RoundingMode",
  "java/time/LocalDate","java/time/LocalTime","java/time/LocalDateTime","java/time/Instant","java/time/Duration","java/time/Period","java/time/ZoneId","java/time/ZoneOffset","java/time/ZonedDateTime","java/time/format/DateTimeFormatter","java/time/temporal/TemporalAccessor","java/time/temporal/TemporalAmount","java/time/temporal/TemporalUnit","java/time/temporal/ChronoUnit",
  "java/security/MessageDigest","java/security/NoSuchAlgorithmException","java/nio/charset/Charset","java/nio/charset/StandardCharsets","java/util/Base64","java/util/Base64$Encoder","java/util/Base64$Decoder",
  "org/json/JSONObject","org/json/JSONArray","org/json/JSONException","org/json/JSONTokener","org/json/JSONString","dev/androiduse/SandboxApi"));
 static void check(Map<String,byte[]> classes)throws Exception{
  if(classes.isEmpty()||classes.size()>64||!classes.containsKey(PROGRAM))throw new SecurityException("Invalid generated program");
  int size=0;for(Map.Entry<String,byte[]> entry:classes.entrySet()){String name=entry.getKey();if(!name.equals(PROGRAM)&&!name.startsWith(PROGRAM+"$"))throw new SecurityException("Extra program class rejected");size+=entry.getValue().length;if(size>1024*1024)throw new SecurityException("Compiled program too large");checkClass(entry.getValue());}
 }
 static void type(String name){
  while(name.startsWith("["))name=name.substring(1);if(name.length()==1&&"BCDFIJSZ".contains(name))return;
  if(name.startsWith("L")&&name.endsWith(";"))name=name.substring(1,name.length()-1);
  String program=PROGRAM.replace('.','/');if(!ALLOWED.contains(name)&&!name.equals(program)&&!name.startsWith(program+"$"))throw new SecurityException("API not allowed: "+name.replace('/','.'));
 }
 static void descriptor(String text){Matcher matcher=Pattern.compile("L([^;]+);").matcher(text);while(matcher.find())type(matcher.group(1));}
 static void checkClass(byte[] bytes)throws Exception{
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
   if(in.readInt()!=0xcafebabe)throw new SecurityException("Invalid class file");in.readUnsignedShort();if(in.readUnsignedShort()>52)throw new SecurityException("Unsupported class version");
   int count=in.readUnsignedShort();String[] text=new String[count];int[] tags=new int[count],one=new int[count],two=new int[count];
   for(int i=1;i<count;i++){int tag=tags[i]=in.readUnsignedByte();switch(tag){case 1:text[i]=in.readUTF();break;case 3:case 4:in.readInt();break;case 5:case 6:in.readLong();i++;break;case 7:case 8:one[i]=in.readUnsignedShort();break;case 9:case 10:case 11:case 12:one[i]=in.readUnsignedShort();two[i]=in.readUnsignedShort();break;default:throw new SecurityException("Dynamic bytecode is not allowed");}}
   for(int i=1;i<count;i++){
    if(tags[i]==7)type(text[one[i]]);
    if(tags[i]==12){String name=text[one[i]];if(Arrays.asList("getClass","wait","notify","notifyAll","finalize").contains(name)||name.startsWith("parallel"))throw new SecurityException("Method not allowed: "+name);descriptor(text[two[i]]);}
   }
   in.readUnsignedShort();in.readUnsignedShort();in.readUnsignedShort();int interfaces=in.readUnsignedShort();for(int i=0;i<interfaces;i++)in.readUnsignedShort();
   for(int group=0;group<2;group++){int members=in.readUnsignedShort();for(int i=0;i<members;i++){int access=in.readUnsignedShort();String name=text[in.readUnsignedShort()];if((access&0x0100)!=0||name.equals("finalize"))throw new SecurityException("Native methods and finalizers are not allowed");descriptor(text[in.readUnsignedShort()]);skipAttributes(in);}}
   skipAttributes(in);if(in.available()!=0)throw new SecurityException("Trailing class data");
  }
 }
 static void skipAttributes(DataInputStream in)throws IOException{int attributes=in.readUnsignedShort();for(int i=0;i<attributes;i++){in.readUnsignedShort();int length=in.readInt();if(length<0||length>in.available())throw new IOException("Invalid class attribute");int skipped=in.skipBytes(length);if(skipped!=length)throw new IOException("Incomplete class attribute");}}
 private JavaBytecodePolicy(){}
}
