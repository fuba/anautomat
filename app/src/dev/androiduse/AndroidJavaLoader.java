package dev.androiduse;

import com.android.tools.r8.*;
import dalvik.system.InMemoryDexClassLoader;
import java.nio.ByteBuffer;
import java.util.*;

/** D8 conversion and ART loading stay entirely inside the disposable isolated process. */
final class AndroidJavaLoader implements SandboxEngine.Loader {
 public Class<?> load(Map<String,byte[]> classes)throws Exception{
  List<ByteBuffer> output=new ArrayList<>();D8Command.Builder builder=D8Command.builder().setMinApiLevel(30).setMode(CompilationMode.RELEASE).setDisableDesugaring(true).setProgramConsumer(new DexIndexedConsumer(){
   public void accept(int index,ByteDataView data,Set<String> descriptors,DiagnosticsHandler handler){synchronized(output){output.add(ByteBuffer.wrap(data.copyByteData()));}}
   public void finished(DiagnosticsHandler handler){}
  });
  for(byte[] bytes:classes.values())builder.addClassProgramData(bytes,com.android.tools.r8.origin.Origin.unknown());
  D8.run(builder.build());if(output.isEmpty())throw new IllegalArgumentException("No generated DEX");
  return new InMemoryDexClassLoader(output.toArray(new ByteBuffer[0]),SandboxApi.class.getClassLoader()).loadClass(JavaBytecodePolicy.PROGRAM);
 }
}
