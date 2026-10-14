package dev.androiduse;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Credentials never leave app-private storage and are encrypted with an Android Keystore key. */
final class SecureStore {
 private final AtomicFile file;
 SecureStore(Context context){this(context,"chatgpt.enc");}
 SecureStore(Context context,String filename){file=new AtomicFile(new File(context.getNoBackupFilesDir(),filename));}
 private javax.crypto.SecretKey key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias("android-use-chatgpt")){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder("android-use-chatgpt",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}return (javax.crypto.SecretKey)store.getKey("android-use-chatgpt",null);}
 synchronized JSONObject read()throws Exception{if(!file.getBaseFile().exists())return null;byte[] bytes=file.readFully();if(bytes.length<29)throw new IOException("Invalid credential record");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(bytes,0,12)));return new JSONObject(new String(cipher.doFinal(java.util.Arrays.copyOfRange(bytes,12,bytes.length)),"UTF-8"));}
 synchronized void write(JSONObject record)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(record.toString().getBytes("UTF-8"));FileOutputStream out=null;try{out=file.startWrite();out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
 synchronized void clear(){file.delete();}
}
