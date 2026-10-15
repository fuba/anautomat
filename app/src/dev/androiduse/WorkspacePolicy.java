package dev.androiduse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Logical macro names and byte limits; never accept paths or URIs from the planner. */
final class WorkspacePolicy {
 static final int MAX_SOURCE=32768;
 static String macroName(String name)throws IOException{
  if(!name.matches("[a-z0-9][a-z0-9_-]{0,63}\\.star"))throw new IOException("Use a lowercase macro name such as check_display.star (up to 64 characters before .star)");return name;
 }
 static byte[] sourceBytes(String source)throws IOException{
  try{ByteBuffer bytes=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(source));
   if(bytes.remaining()==0||bytes.remaining()>MAX_SOURCE)throw new IOException("Macro source must be 1-32768 UTF-8 bytes");byte[] result=new byte[bytes.remaining()];bytes.get(result);return result;
  }catch(CharacterCodingException e){throw new IOException("Invalid macro Unicode",e);}
 }
 private WorkspacePolicy(){}
}
