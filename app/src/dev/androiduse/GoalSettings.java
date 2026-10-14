package dev.androiduse;

import android.content.*;
import android.text.*;
import android.view.View;
import android.widget.EditText;

/** One persistent draft shared by every composer. Running goals keep their snapshot. */
final class GoalSettings {
 static SharedPreferences prefs(Context c){
  SharedPreferences p=c.getSharedPreferences("goal-settings",0);
  if(!p.contains("goal"))p.edit().putString("goal",c.getSharedPreferences("MainActivity",0).getString("goal",c.getSharedPreferences("floating",0).getString("draft",I18n.text(c,"default_goal")))).apply();
  return p;
 }
 static String goal(Context c){return prefs(c).getString("goal","");}
 static void goal(Context c,String text){prefs(c).edit().putString("goal",text).apply();}
 static int limit(Context c){return prefs(c).getInt("step-limit",100);}
 static void bind(EditText input){
  SharedPreferences p=prefs(input.getContext());
  SharedPreferences.OnSharedPreferenceChangeListener listener=(settings,key)->{if("goal".equals(key)){String text=settings.getString("goal","");if(!input.getText().toString().equals(text)){int cursor=input.getSelectionStart();input.setText(text);input.setSelection(Math.max(0,Math.min(cursor,text.length())));}}};
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!s.toString().equals(p.getString("goal","")))p.edit().putString("goal",s.toString()).apply();}public void afterTextChanged(Editable e){}});
  input.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){p.registerOnSharedPreferenceChangeListener(listener);listener.onSharedPreferenceChanged(p,"goal");}public void onViewDetachedFromWindow(View v){p.unregisterOnSharedPreferenceChangeListener(listener);}});
 }
}
