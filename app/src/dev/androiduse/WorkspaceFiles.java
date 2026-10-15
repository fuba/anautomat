package dev.androiduse;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** All shared storage stays in the app process, under the user's selected SAF tree. */
final class WorkspaceFiles {
 static final String DIR=DocumentsContract.Document.MIME_TYPE_DIR;
 static final int MAX_CHILDREN=4096,MAX_MACROS=256,MAX_SOURCE=WorkspacePolicy.MAX_SOURCE;
 final ContentResolver resolver;final Uri tree,root;
 WorkspaceFiles(ContentResolver resolver,String selected)throws IOException{
  this(resolver,selected,null);
 }
 WorkspaceFiles(ContentResolver resolver,String selected,Uri parent)throws IOException{
  if(selected.isEmpty())throw new IOException("Choose a workspace folder first");
  this.resolver=resolver;tree=Uri.parse(selected);
  root=parent==null?DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)):parent;
 }
 static final class Entry{final Uri uri;final String name,mime;final int flags;Entry(Uri u,String n,String m,int f){uri=u;name=n;mime=m;flags=f;}}
 List<Entry> children(Uri parent)throws IOException{
  ArrayList<Entry> entries=new ArrayList<>();
  Uri query=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));
  try(Cursor cursor=resolver.query(query,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS},null,null,null)){
   if(cursor==null)throw new IOException("Cannot list workspace folder");
   while(cursor.moveToNext()){
    if(entries.size()>=MAX_CHILDREN)throw new IOException("Workspace folder has too many entries");
    entries.add(new Entry(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)),cursor.getString(1),cursor.getString(2),cursor.getInt(3)));
   }
  }return entries;
 }
 Uri child(Uri parent,String name,String mime,boolean create)throws Exception{
  Entry match=null;for(Entry item:children(parent))if(name.equals(item.name)){if(match!=null)throw new IOException("Ambiguous workspace name");match=item;}
  if(match!=null){if((match.flags&DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT)!=0)throw new IOException("Virtual workspace documents are unsupported");if(DIR.equals(mime)!=DIR.equals(match.mime))throw new IOException("Workspace name has the wrong document type");return match.uri;}
  if(!create)return null;
  // Android appends .txt to unknown extensions requested as text/plain.
  String createMime=!DIR.equals(mime)&&name.endsWith(".star")?"application/octet-stream":mime;
  Uri created=DocumentsContract.createDocument(resolver,parent,createMime,name);if(created==null)throw new IOException("Cannot create workspace document");
  // Providers may rename collisions. Never silently use a different logical name.
  Uri exact=child(parent,name,mime,false);if(!created.equals(exact))throw new IOException("Workspace provider changed the document name: "+name);return created;
 }
 Uri directory(Uri parent,String name)throws Exception{return child(parent,name,DIR,true);}
 Uri runs()throws Exception{return directory(root,"runs");}
 Uri macros()throws Exception{return directory(root,"macros");}
 void initialize()throws Exception{runs();macros();}
 void write(Uri document,byte[] bytes)throws Exception{
  try(OutputStream out=resolver.openOutputStream(document,"wt")){if(out==null)throw new IOException("Cannot open workspace document");out.write(bytes);}
 }
 Uri write(Uri parent,String name,String mime,String text)throws Exception{Uri document=child(parent,name,mime,true);write(document,text.getBytes(StandardCharsets.UTF_8));return document;}
 String readMacro(String name)throws Exception{
  Uri document=child(macros(),WorkspacePolicy.macroName(name),"text/plain",false);if(document==null)throw new IOException("Macro does not exist");
  try(InputStream input=resolver.openInputStream(document)){
   if(input==null)throw new IOException("Cannot read macro");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
   while((n=input.read(buffer))!=-1){if(bytes.size()+n>MAX_SOURCE)throw new IOException("Macro source exceeds 32768 UTF-8 bytes");bytes.write(buffer,0,n);}
   String source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();WorkspacePolicy.sourceBytes(source);return source;
  }
 }
 List<String> listMacros()throws Exception{
  ArrayList<String> names=new ArrayList<>();HashSet<String> seen=new HashSet<>();
  for(Entry entry:children(macros())){
   if(!seen.add(entry.name))throw new IOException("Ambiguous workspace name");
   if(!DIR.equals(entry.mime)&&entry.name.matches("[a-z0-9][a-z0-9_-]{0,63}\\.star")){if(names.size()>=MAX_MACROS)throw new IOException("Too many macros (maximum 256)");names.add(entry.name);}
  }Collections.sort(names);return names;
 }
 void saveMacro(String name,String source)throws Exception{
  WorkspacePolicy.macroName(name);byte[] bytes=WorkspacePolicy.sourceBytes(source);Uri parent=macros();
  if(child(parent,name,"text/plain",false)!=null)throw new IOException("Macro already exists; save a new name");
  if(listMacros().size()>=MAX_MACROS)throw new IOException("Too many macros (maximum 256)");
  write(child(parent,name,"text/plain",true),bytes);
 }
}
