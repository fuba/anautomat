package dev.androiduse;

import org.json.*;

/** The generated program receives JSON copies and a bounded operation queue, never Android objects. */
public final class SandboxApi {
 public final JSONObject input,observation,state;
 private final JSONArray actions=new JSONArray();private final StringBuilder logs=new StringBuilder();
 SandboxApi(String input,String observation,String state)throws Exception{this.input=AutomationJson.object(input,SandboxEngine.MAX_INPUT);this.observation=AutomationJson.object(observation,SandboxEngine.MAX_OBSERVATION);this.state=AutomationJson.object(state,SandboxEngine.MAX_STATE);}
 public void macro(JSONArray batch)throws Exception{
  String encoded=batch.toString();if(batch.length()+actions.length()>MacroPlan.MAX_ACTIONS||encoded.length()>32768)throw new IllegalArgumentException("Macro limit exceeded");
  JSONArray copy=new JSONArray(encoded);for(int i=0;i<copy.length();i++)copy.getJSONObject(i);
  JSONArray combined=new JSONArray(actions.toString());for(int i=0;i<copy.length();i++)combined.put(copy.getJSONObject(i));if(combined.toString().length()>32768)throw new IllegalArgumentException("Macro output limit exceeded");
  for(int i=0;i<copy.length();i++)actions.put(copy.getJSONObject(i));
 }
 public void log(Object value){String text=String.valueOf(value);if(text.length()+logs.length()+1>SandboxEngine.MAX_LOG)throw new IllegalArgumentException("Log limit exceeded");logs.append(text).append('\n');}
 String finish(Object value)throws Exception{
  String stateText=state.toString();if(stateText==null||stateText.length()>SandboxEngine.MAX_STATE)throw new IllegalArgumentException("State limit exceeded");
  Object json=value==null?JSONObject.NULL:JSONObject.wrap(value);if(json==null)throw new IllegalArgumentException("Return a JSON value");JSONObject result=new JSONObject().put("result",json).put("state",state).put("actions",actions).put("logs",logs.toString());String output=result.toString();
  AutomationJson.check(output,SandboxEngine.MAX_OUTPUT);return output;
 }
}
