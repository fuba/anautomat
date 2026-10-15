package dev.androiduse;

import android.app.Activity;
import android.os.Build;
import android.widget.*;

final class UpdateControls extends LinearLayout {
 final Updates updates;final TextView status;final Button check,action;
 UpdateControls(Activity activity){super(activity);setOrientation(VERTICAL);updates=Updates.get(activity);
  Ui.label(this,I18n.text(activity,"updates"));
  String version="";try{version=updates.installed().versionName;}catch(Exception ignored){}
  addView(Ui.text(activity,I18n.text(activity,"update_installed",version),12,Ui.MUTED,false));Ui.gap(this,8);
  Switch automatic=new Switch(activity);automatic.setText(I18n.text(activity,"update_automatic"));automatic.setTextColor(Ui.TEXT);automatic.setChecked(updates.automatic());automatic.setOnCheckedChangeListener((button,value)->{updates.automatic(value);if(value)permission(activity);});addView(automatic);Ui.gap(this,10);
  status=Ui.text(activity,"",13,Ui.TEXT,false);addView(status);Ui.gap(this,12);
  check=Ui.button(activity,I18n.text(activity,"update_check"),false,()->{permission(activity);updates.check(true,null);});
  action=Ui.button(activity,"",true,()->{if(updates.ready)updates.install(activity);else updates.download();});
  Ui.row(this,check,action);refresh();
 }
 static void permission(Activity activity){if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},10);}
 void refresh(){status.setText(updates.status.isEmpty()?(updates.release==null?"":I18n.text(getContext(),"update_available",updates.release.version)):updates.status);check.setEnabled(!updates.busy.get());action.setVisibility(updates.release==null?GONE:VISIBLE);action.setEnabled(!updates.busy.get());action.setText(I18n.text(getContext(),updates.ready?"update_install":"update_download"));}
}
