package dev.androiduse;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small code-native icon, with a constant footprint in every agent state. */
final class AgentBubble extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final android.graphics.drawable.Drawable mark;
 private int state;
 private float orbitAngle=-90f;
 private android.animation.ValueAnimator orbit;
 private final Shader glow=new RadialGradient(0,0,4.2f,new int[]{0xccffffff,0x55ffffff,0x00ffffff},new float[]{0,0.4f,1},Shader.TileMode.CLAMP);
 AgentBubble(Context context){super(context);mark=context.getDrawable(context.getResources().getIdentifier("ic_agent_mark","drawable",context.getPackageName()));setContentDescription(I18n.text(getContext(),"menu"));setClickable(true);}
 void state(boolean running,boolean paused){int next=running?1:paused?2:0;if(state!=next){state=next;updateOrbit();invalidate();}setContentDescription("Android Use メニュー • "+(running?I18n.text(getContext(),"running"):paused?I18n.text(getContext(),"paused"):I18n.text(getContext(),"idle")));}
 private void updateOrbit(){boolean animate=state==1&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&android.animation.ValueAnimator.areAnimatorsEnabled();if(!animate){if(orbit!=null){orbit.cancel();orbit=null;}return;}if(orbit!=null)return;orbit=android.animation.ValueAnimator.ofFloat(-90f,270f);orbit.setDuration(1800);orbit.setRepeatCount(android.animation.ValueAnimator.INFINITE);orbit.setInterpolator(new android.view.animation.LinearInterpolator());orbit.addUpdateListener(a->{orbitAngle=(float)a.getAnimatedValue();invalidate();});orbit.start();}
 protected void onAttachedToWindow(){super.onAttachedToWindow();updateOrbit();}
 protected void onDetachedFromWindow(){if(orbit!=null){orbit.cancel();orbit=null;}super.onDetachedFromWindow();}
 protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateOrbit();}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);float unit=Math.min(getWidth(),getHeight())/48f;canvas.save();canvas.scale(unit,unit);
  paint.setStyle(Paint.Style.FILL);paint.setColor(0x99161616);canvas.drawCircle(24,24,20,paint);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x55ffffff);canvas.drawCircle(24,24,19.5f,paint);
  mark.setBounds(0,0,48,48);mark.setAlpha(235);mark.draw(canvas);
  if(state==1){paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(1.6f);for(int i=0;i<10;i++){paint.setColor(((18+i*17)<<24)|0x00ffffff);canvas.drawArc(4.5f,4.5f,43.5f,43.5f,orbitAngle-30+i*3,3,false,paint);}float x=24+19.5f*(float)Math.cos(Math.toRadians(orbitAngle)),y=24+19.5f*(float)Math.sin(Math.toRadians(orbitAngle));paint.setStyle(Paint.Style.FILL);canvas.save();canvas.translate(x,y);paint.setShader(glow);canvas.drawCircle(0,0,4.2f,paint);paint.setShader(null);paint.setColor(Color.WHITE);canvas.drawCircle(0,0,1.8f,paint);canvas.restore();}
  if(state==2){paint.setStyle(Paint.Style.FILL);paint.setColor(0xee161616);canvas.drawCircle(37,11,6,paint);paint.setColor(0xfff4f4f4);canvas.drawRoundRect(34,8,36,14,0.5f,0.5f,paint);canvas.drawRoundRect(38,8,40,14,0.5f,0.5f,paint);}
  canvas.restore();
 }
 public boolean performClick(){super.performClick();return true;}
}
