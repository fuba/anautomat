package dev.androiduse;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.*;

final class AutomationSettings {
 static boolean codeEnabled(Context c){return c.getSharedPreferences("automation",0).getBoolean("code",true);}
 static void add(LinearLayout card){Context c=card.getContext();Ui.gap(card,16);Switch enabled=new Switch(c);enabled.setText(I18n.text(c,"code_execution"));enabled.setTextColor(Ui.TEXT);enabled.setThumbTintList(ColorStateList.valueOf(Ui.SURFACE));enabled.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.TEXT,Ui.LINE}));enabled.setChecked(codeEnabled(c));enabled.setOnCheckedChangeListener((button,value)->{c.getSharedPreferences("automation",0).edit().putBoolean("code",value).apply();AgentService agent=AgentService.instance;if(!value&&agent!=null&&agent.sandbox!=null)agent.pauseGoal(I18n.text(c,"sandbox_disabled"));});card.addView(enabled);Ui.gap(card,6);card.addView(Ui.text(c,I18n.text(c,"code_limits"),11,Ui.MUTED,false));}
}
