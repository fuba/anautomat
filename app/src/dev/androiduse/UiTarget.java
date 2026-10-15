package dev.androiduse;

import android.view.accessibility.AccessibilityNodeInfo;

/** Exact, unique, visible targets. Resolve immediately before the operation. */
final class UiTarget {
 static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String target,boolean editable)throws Exception{
  AccessibilityNodeInfo[] found={null};int[] count={0};scan(root,target,editable,0,count,found);
  if(found[0]==null)throw new Exception("UI target not found: "+target);return found[0];
 }
 static void scan(AccessibilityNodeInfo node,String target,boolean editable,int depth,int[] count,AccessibilityNodeInfo[] found)throws Exception{
  if(node==null)return;if(depth>18||count[0]++>=350)throw new Exception("UI tree exceeds target lookup budget");
  if(node.isVisibleToUser()&&(!editable||node.isEditable())&&matches(node,target)){
   if(found[0]!=null)throw new Exception("UI target is ambiguous: "+target);found[0]=node;
  }
  for(int i=0;i<node.getChildCount();i++)scan(node.getChild(i),target,editable,depth+1,count,found);
 }
 static boolean matches(AccessibilityNodeInfo node,String target){
  if(target.startsWith("id:"))return target.substring(3).equals(node.getViewIdResourceName());
  for(CharSequence value:new CharSequence[]{node.getText(),node.getContentDescription(),node.getHintText()})if(value!=null&&target.contentEquals(value))return true;
  return false;
 }
 static void click(AccessibilityNodeInfo node)throws Exception{
  for(int depth=0;node!=null&&depth<8;depth++,node=node.getParent()){
   if(!node.isVisibleToUser()||!node.isEnabled())throw new Exception("UI target is unavailable or disabled");
   if(node.isClickable()){if(!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new Exception("Target click rejected");return;}
  }
  throw new Exception("UI target has no clickable control");
 }
 private UiTarget(){}
}
