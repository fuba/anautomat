package dev.androiduse;

import android.content.Context;

/** Resource-backed copy, including application and accessibility overlay contexts. */
final class I18n {
 static String text(Context c,String key,Object...args){int id=c.getResources().getIdentifier(key,"string",c.getPackageName());if(id==0)throw new IllegalArgumentException("Missing string: "+key);return args.length==0?c.getString(id):c.getString(id,args);}
 static String language(Context c){return "ja".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage())?"Japanese":"English";}
}
