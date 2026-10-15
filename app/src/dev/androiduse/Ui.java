package dev.androiduse;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared visual language for the app and its overlay. All dimensions are density independent. */
final class Ui {
 static final int BG=0xfff7f7f5,SURFACE=0xffffffff,FIELD=0xfff5f5f3,TEXT=0xff161616,MUTED=0xff666666,ACCENT=0xff161616,LINE=0xffe3e3e0;
 static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(Context c,int color,int radius,boolean border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(border)d.setStroke(dp(c,1),LINE);return d;}
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
 static TextView text(Context c,String value,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(c,3),1);return t;}
 static void gap(LinearLayout l,int height){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),height)));}
 static LinearLayout card(Context c){LinearLayout l=column(c);int p=dp(c,20);l.setPadding(p,p,p,p);l.setBackground(shape(c,SURFACE,24,true));return l;}
 static void block(LinearLayout parent,View child){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(parent.getContext(),16);parent.addView(child,p);}
 static void label(LinearLayout l,String value){TextView t=text(l.getContext(),value,11,MUTED,true);t.setLetterSpacing(0.1f);l.addView(t);gap(l,10);}
 static EditText input(Context c,String value){EditText e=new EditText(c);e.setText(value);e.setHint(I18n.text(c,"goal_hint"));e.setTextSize(18);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTypeface(Typeface.create("sans-serif",0));e.setGravity(Gravity.TOP|Gravity.LEFT);e.setMinLines(3);e.setMaxLines(5);e.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));e.setBackground(shape(c,FIELD,16,true));return e;}
 static Button button(Context c,String title,boolean primary,Runnable action){Button b=new Button(c);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,12),0,dp(c,12),0);b.setElevation(0);b.setStateListAnimator(null);StateListDrawable background=new StateListDrawable();background.addState(new int[]{-android.R.attr.state_enabled},shape(c,0xffeeeeeb,14,false));background.addState(new int[]{},shape(c,primary?ACCENT:SURFACE,14,!primary));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),background,null));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff939393,primary?0xffffffff:TEXT}));b.setOnClickListener(v->action.run());return b;}
 static void row(LinearLayout parent,Button a,Button b){LinearLayout row=new LinearLayout(parent.getContext());LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(parent.getContext(),48),1);left.rightMargin=dp(parent.getContext(),8);row.addView(a,left);row.addView(b,new LinearLayout.LayoutParams(0,dp(parent.getContext(),48),1));parent.addView(row);}
 static ImageButton icon(Context c,String resource,String label,Runnable action){ImageButton b=new ImageButton(c);b.setImageResource(c.getResources().getIdentifier(resource,"drawable",c.getPackageName()));b.setImageTintList(ColorStateList.valueOf(TEXT));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),shape(c,SURFACE,14,true),null));b.setContentDescription(label);b.setTooltipText(label);b.setOnClickListener(v->action.run());return b;}
 static ImageView mark(Context c,int size){ImageView image=new ImageView(c);image.setImageResource(c.getResources().getIdentifier("ic_agent_mark","drawable",c.getPackageName()));image.setImageTintList(ColorStateList.valueOf(TEXT));image.setBackground(shape(c,FIELD,16,true));int p=dp(c,4);image.setPadding(p,p,p,p);image.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return image;}
}
