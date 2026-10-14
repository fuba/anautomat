package dev.androiduse;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Noninteractive marquee; appending a summary does not reset the scroll position. */
final class ProgressTicker extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private String text="";private float offset;private long last;
 ProgressTicker(Context c){super(c);paint.setTypeface(Typeface.create("sans-serif",0));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void text(String value){String next=value.replace('\n',' ').trim();if(!text.equals(next)){if(!next.startsWith(text))offset=0;text=next;invalidate();}}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;paint.setTextSize(13*density);float padding=14*density;paint.setColor(0xed161616);canvas.drawRoundRect(0,0,getWidth(),getHeight(),12*density,12*density,paint);paint.setColor(Color.WHITE);float width=paint.measureText(text);long now=SystemClock.uptimeMillis();if(last!=0&&width>getWidth()-padding*2)offset+=Math.min(100,now-last)*0.035f*density;last=now;float cycle=width+60*density;if(offset>cycle)offset=0;float y=getHeight()/2f-(paint.ascent()+paint.descent())/2;canvas.save();canvas.clipRect(padding,0,getWidth()-padding,getHeight());canvas.drawText(text,padding-offset,y,paint);if(width>getWidth()-padding*2)canvas.drawText(text,padding-offset+cycle,y,paint);canvas.restore();if(width>getWidth()-padding*2&&getVisibility()==VISIBLE)postInvalidateOnAnimation();}
 protected void onVisibilityChanged(View v,int visible){super.onVisibilityChanged(v,visible);last=0;if(visible==VISIBLE)invalidate();}
}
