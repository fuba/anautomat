package dev.androiduse;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Swipe-dismissable marquee; appending a summary does not reset the scroll position. */
final class ProgressTicker extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private String text="";private float offset;private long last;private float downX,downY;private final java.util.function.Consumer<String> dismiss;private String downText="";
 ProgressTicker(Context c,java.util.function.Consumer<String> dismiss){super(c);this.dismiss=dismiss;setVisibility(INVISIBLE);setClickable(true);paint.setTypeface(Typeface.create("sans-serif",0));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
 public boolean onTouchEvent(android.view.MotionEvent event){switch(event.getActionMasked()){case android.view.MotionEvent.ACTION_DOWN:downX=event.getX();downY=event.getY();downText=text;return true;case android.view.MotionEvent.ACTION_UP:float dx=event.getX()-downX,dy=event.getY()-downY;if(Math.abs(dx)>=48*getResources().getDisplayMetrics().density&&Math.abs(dx)>Math.abs(dy)){dismiss.accept(downText);}else performClick();return true;case android.view.MotionEvent.ACTION_MOVE:case android.view.MotionEvent.ACTION_CANCEL:return true;default:return super.onTouchEvent(event);}}
 public boolean performClick(){super.performClick();return true;}
 public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);}
 public boolean performAccessibilityAction(int action,android.os.Bundle arguments){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS){dismiss.accept(text);return true;}return super.performAccessibilityAction(action,arguments);}
 void text(String value){String next=value.replace('\n',' ').trim();if(!text.equals(next)){if(!next.startsWith(text))offset=0;text=next;setContentDescription(next);invalidate();}}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;paint.setTextSize(13*density);float padding=14*density;paint.setColor(0xed161616);canvas.drawRoundRect(0,0,getWidth(),getHeight(),12*density,12*density,paint);paint.setColor(Color.WHITE);float width=paint.measureText(text);long now=SystemClock.uptimeMillis();if(last!=0&&width>getWidth()-padding*2)offset+=Math.min(100,now-last)*0.035f*density;last=now;float cycle=width+60*density;if(offset>cycle)offset=0;float y=getHeight()/2f-(paint.ascent()+paint.descent())/2;canvas.save();canvas.clipRect(padding,0,getWidth()-padding,getHeight());canvas.drawText(text,padding-offset,y,paint);if(width>getWidth()-padding*2)canvas.drawText(text,padding-offset+cycle,y,paint);canvas.restore();if(width>getWidth()-padding*2&&getVisibility()==VISIBLE)postInvalidateOnAnimation();}
 protected void onVisibilityChanged(View v,int visible){super.onVisibilityChanged(v,visible);last=0;if(visible==VISIBLE)invalidate();}
}
