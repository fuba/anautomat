package dev.androiduse;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
public final class VoiceActivity extends Activity {
 protected void onCreate(Bundle b){super.onCreate(b);if(b!=null)return;try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,getResources().getConfiguration().getLocales().get(0).toLanguageTag());i.putExtra(RecognizerIntent.EXTRA_PROMPT,I18n.text(VoiceActivity.this,"voice_prompt"));startActivityForResult(i,1);}catch(Exception e){deliver(null,I18n.text(VoiceActivity.this,"voice_missing"));}}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);ArrayList<String> words=data==null?null:data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);deliver(result==RESULT_OK&&words!=null&&!words.isEmpty()?words.get(0):null,I18n.text(VoiceActivity.this,"voice_cancelled"));}
 void deliver(String text,String error){if(AgentService.instance!=null&&AgentService.instance.floating!=null)AgentService.instance.floating.voiceResult(text,error);finish();}
}
