package dev.androiduse;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
public final class VoiceActivity extends Activity {
 protected void onCreate(Bundle b){super.onCreate(b);if(b!=null)return;try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,getResources().getConfiguration().getLocales().get(0).toLanguageTag());i.putExtra(RecognizerIntent.EXTRA_PROMPT,I18n.text(VoiceActivity.this,"voice_prompt"));startActivityForResult(i,1);}catch(Exception e){deliver(null,I18n.text(VoiceActivity.this,"voice_missing"));}}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);ArrayList<String> words=data==null?null:data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);deliver(result==RESULT_OK&&words!=null&&!words.isEmpty()?words.get(0):null,I18n.text(VoiceActivity.this,"voice_cancelled"));}
 void deliver(String text,String error){AgentService service=AgentService.instance;if(service!=null&&(service.running||service.paused||service.limitReached)){text=null;error=I18n.text(this,"goal_locked");}if(getIntent().getBooleanExtra("return_to_overlay",false)&&service!=null&&service.floating!=null)service.floating.voiceResult(text,error);else if(text!=null)GoalSettings.goal(this,text);else android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_SHORT).show();finish();}
}
