// SPDX-License-Identifier: MIT
package android.content;
import android.database.Cursor;import android.net.Uri;import android.os.Build;import android.system.Os;import android.system.OsConstants;import java.util.*;import java.nio.file.*;
public class ContentResolver {
 public final List<Row> rows=new ArrayList<Row>();public final List<Call> calls=new ArrayList<Call>();public int queries;
 public String mode="";public boolean queryAllowed=true,deleteAllowed=true;public ProbeCursor cursor;public Uri queryUri;
 public String querySelection;public String[] queryProjection,queryArgs;public int passthroughResult=17;
 public static final class Row {public long id;public String path;public int type,pending,trashed;public String volume;
  public Row(long i,String p,int t,String v){id=i;path=p;type=t;volume=v;}}
 public static final class Call {public final Uri uri;public final String selection;public final String[] args;Call(Uri u,String s,String[] a){uri=u;selection=s;args=a;}}
 private static int kind(Uri uri){if(uri==null)return -1;List<String>s=uri.getPathSegments();if(s.size()>1&&s.get(1).equals("file"))return 0;if(s.size()>2&&s.get(1).equals("images")&&s.get(2).equals("media"))return 1;if(s.size()>2&&s.get(1).equals("video")&&s.get(2).equals("media"))return 3;return -1;}
 private static boolean item(Uri uri){int k=kind(uri);int n=uri.getPathSegments().size();return k==0?n==3:k>0&&n==4;}
 private static boolean visible(Row r,Uri uri,boolean id){int k=kind(uri);if(k<0)return false;String volume=uri.getPathSegments().get(0);return (volume.equals("external")||volume.equals(r.volume))&&(k==0||k==r.type)&&(id||Build.VERSION.SDK_INT<29||(r.pending==0&&r.trashed==0));}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
  queries++;queryUri=uri;queryProjection=projection;querySelection=selection;queryArgs=args;
  if(mode.equals("recreate-and-query-error")){recreate(args[0]);throw new SecurityException("query failure after recreation");}
  if(!queryAllowed||mode.equals("query-error"))throw new SecurityException("query permission");if(mode.equals("null-cursor"))return null;
  if(item(uri)||!selection.equals("_data = ?")||args.length!=1||order!=null)throw new AssertionError("lookup contract");
  String[] want=kind(uri)==0?new String[]{"_id","_data","media_type"}:new String[]{"_id","_data"};
  if(!Arrays.equals(want,projection))throw new AssertionError("unsupported typed projection");
  List<Row> found=new ArrayList<Row>();for(Row r:rows)if(visible(r,uri,false)&&r.path.equalsIgnoreCase(args[0]))found.add(r);
  cursor=new ProbeCursor(found,mode);
  if(mode.equals("recreate-in-query"))recreate(args[0]);
  if(mode.equals("stat-denied-in-query"))Os.error=OsConstants.EACCES;
  return cursor;
 }
 public int delete(Uri uri,String selection,String[] args){
  calls.add(new Call(uri,selection,args));if(!deleteAllowed)throw new SecurityException("delete permission");
  int k=kind(uri);if(mode.equals("delegate-only")||k<0||args==null||selection==null)return passthroughResult;
  boolean id=item(uri);String path;long selectedId=-1;int selectedType=-1;
  if(id){
   if(cursor==null||!cursor.closed)throw new AssertionError("cursor must close before item delete");
   String expected="_id = ? AND _data = ? COLLATE BINARY AND media_type = ?"+(Build.VERSION.SDK_INT>=29?" AND is_pending = 0 AND is_trashed = 0":"");
   if(!selection.equals(expected)||args.length!=3)throw new AssertionError("unsafe item selection");
   selectedId=Long.parseLong(args[0]);path=args[1];selectedType=Integer.parseInt(args[2]);
   long uriId=Long.parseLong(uri.getPathSegments().get(uri.getPathSegments().size()-1));if(uriId!=selectedId)throw new AssertionError("different URI ID");
   if(mode.equals("id-error")||mode.equals("both-errors"))throw new SecurityException("item failure");
   if(mode.equals("replace-id")){rows.get(0).id++;return 0;}
   if(mode.equals("changed-path"))rows.get(0).path=path+".moved";
   if(mode.equals("changed-type"))rows.get(0).type=selectedType==0?1:0;
   if(mode.equals("changed-pending"))rows.get(0).pending=1;
   if(mode.equals("changed-trashed"))rows.get(0).trashed=1;
   if(mode.equals("id-zero"))return 0;if(mode.equals("id-negative"))return -1;
  }else{
   if(mode.equals("both-errors"))throw new SecurityException("fallback failure");
   if(args.length!=1)return passthroughResult;path=args[0];
   if(!selection.equals("_data = ? COLLATE BINARY")&&!selection.matches("\\s*_data\\s*=\\s*\\?\\s*"))return passthroughResult;
  }
  boolean binary=selection.contains("COLLATE BINARY");int count=0;
  Iterator<Row> it=rows.iterator();while(it.hasNext()){Row r=it.next();
   if(!visible(r,uri,id)||(binary?!r.path.equals(path):!r.path.equalsIgnoreCase(path)))continue;
   if(id&&(r.id!=selectedId||r.type!=selectedType||(Build.VERSION.SDK_INT>=29&&(r.pending!=0||r.trashed!=0))))continue;
   it.remove();count++;
   try{Files.deleteIfExists(Paths.get(r.path));}catch(java.io.IOException e){throw new RuntimeException(e);}
   if(id&&mode.equals("partial-recreate")){
    recreate(r.path);rows.add(new Row(r.id+1,r.path,r.type,r.volume));
    throw new SecurityException("failure after replacement file and row");
   }
   if(id&&mode.equals("partial-error"))throw new SecurityException("failure after row deletion");
  }return count;
 }
 private static void recreate(String path){try{Files.write(Paths.get(path),new byte[]{9,8,7});}catch(java.io.IOException e){throw new RuntimeException(e);}}
 public static final class ProbeCursor implements Cursor {final List<Row> rows;final String mode;int index=-1;public boolean closed;
  ProbeCursor(List<Row> r,String m){rows=r;mode=m;}
  public boolean moveToNext(){if(mode.equals("move-error"))throw new SecurityException("cursor move");return ++index<rows.size();}
  public long getLong(int c){if(c!=0)throw new AssertionError("id column");if(mode.equals("read-error"))throw new SecurityException("cursor read");return rows.get(index).id;}
  public String getString(int c){if(c!=1)throw new AssertionError("path column");return rows.get(index).path;}
  public int getInt(int c){if(c!=2)throw new AssertionError("type column");return rows.get(index).type;}
  public void close(){closed=true;if(mode.equals("close-error"))throw new SecurityException("cursor close");}
 }
}
