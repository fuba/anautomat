package dev.androiduse;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Identical goal, voice, play/pause, stop and status UI in the app and overlay. */
final class GoalControls extends LinearLayout {
 final EditText goal;
 final ImageButton voice,primary,stop;
 final TextView detail;
 final Runnable leaveOverlay;
 GoalControls(Context context,boolean overlay,Runnable leaveOverlay){
  super(context);this.leaveOverlay=leaveOverlay;setOrientation(VERTICAL);
  Ui.label(this,"GOAL");goal=Ui.input(context,GoalSettings.goal(context));GoalSettings.bind(goal);addView(goal);Ui.gap(this,14);
  LinearLayout row=new LinearLayout(context);
  voice=Ui.icon(context,"ic_microphone",I18n.text(context,"voice_goal"),()->{leaveOverlay.run();context.startActivity(new Intent(context,VoiceActivity.class).putExtra("return_to_overlay",overlay).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
  primary=Ui.icon(context,"ic_play",I18n.text(context,"run_goal"),this::activate);
  primary.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),Ui.shape(context,Ui.TEXT,14,false),null));primary.setImageTintList(ColorStateList.valueOf(0xffffffff));
  stop=Ui.icon(context,"ic_stop",I18n.text(context,"stop_goal"),()->{AgentService service=AgentService.instance;if(service!=null)service.stopGoal(I18n.text(context,"user_stopped"));refresh();});
  LinearLayout.LayoutParams side=new LinearLayout.LayoutParams(Ui.dp(context,50),Ui.dp(context,50));side.rightMargin=Ui.dp(context,10);row.addView(voice,side);
  LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(0,Ui.dp(context,50),1);center.rightMargin=Ui.dp(context,10);row.addView(primary,center);row.addView(stop,new LinearLayout.LayoutParams(Ui.dp(context,50),Ui.dp(context,50)));addView(row);Ui.gap(this,16);
  detail=Ui.text(context,"",12,Ui.MUTED,false);detail.setMaxLines(5);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.setPadding(Ui.dp(context,14),Ui.dp(context,12),Ui.dp(context,14),Ui.dp(context,12));detail.setBackground(Ui.shape(context,Ui.FIELD,16,true));addView(detail);refresh();
 }
 void activate(){
  AgentService service=AgentService.instance;if(service==null)return;
  if(mode(service)!=GoalControlState.Mode.RUNNING){goal.clearFocus();((android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(goal.getWindowToken(),0);}
  switch(mode(service)){
   case RUNNING:service.pauseGoal(I18n.text(getContext(),"pause_menu"));break;
   case PAUSED:leaveOverlay.run();service.resumeGoal();break;
   case LIMIT:leaveOverlay.run();service.continueGoal();break;
   default:leaveOverlay.run();service.startGoal(goal.getText().toString());break;
  }
  refresh();
 }
 void refresh(){
  Context context=getContext();AgentService service=AgentService.instance;GoalControlState.Mode mode=mode(service);boolean active=mode!=GoalControlState.Mode.IDLE;
  if(active&&!goal.getText().toString().equals(service.goal))goal.setText(service.goal);
  goal.setEnabled(!active);enable(voice,!active);
  String action=mode==GoalControlState.Mode.RUNNING?"pause":mode==GoalControlState.Mode.PAUSED?"resume":mode==GoalControlState.Mode.LIMIT?"continue_goal":"run_goal";
  primary.setImageResource(context.getResources().getIdentifier(mode==GoalControlState.Mode.RUNNING?"ic_pause":"ic_play","drawable",context.getPackageName()));primary.setContentDescription(I18n.text(context,action));primary.setTooltipText(I18n.text(context,action));
  ChatGptAuth auth=ChatGptAuth.get(context);boolean ready=service!=null&&auth.connected()&&!auth.model().isEmpty()&&!auth.signingIn;
  enable(primary,mode==GoalControlState.Mode.RUNNING||ready&&(active||!goal.getText().toString().trim().isEmpty()));enable(stop,active);
  String state=mode==GoalControlState.Mode.RUNNING?"running":mode==GoalControlState.Mode.PAUSED?"paused":mode==GoalControlState.Mode.LIMIT?"limit_reached":"idle";
  String message=service==null?I18n.text(context,"accessibility_required"):!auth.connected()?I18n.text(context,"please_sign_in"):auth.model().isEmpty()?I18n.text(context,"load_models"):AgentService.status.split("\n")[0];
  detail.setText(I18n.text(context,state)+(service==null?"":I18n.text(context,"step_status",service.step)+"\n"+service.usage())+(message.isEmpty()?"":"\n"+message));
 }
 private static GoalControlState.Mode mode(AgentService service){return service==null?GoalControlState.Mode.IDLE:GoalControlState.mode(service.running,service.paused,service.limitReached);}
 private static void enable(View view,boolean enabled){view.setEnabled(enabled);view.setAlpha(enabled?1f:.3f);}
}
