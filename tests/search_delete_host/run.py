#!/usr/bin/env python3
"""Compile the real search and folder helpers; delete only synthetic temporary files."""
from pathlib import Path
import argparse,importlib.util,json,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('folder_fixture',ROOT/'tests/folder_delete_host/run.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
FIX=dict(m.FIXTURES)
FIX['android/net/Uri.java']='''package android.net;
public final class Uri {final java.net.URI u;private Uri(String s){u=java.net.URI.create(s);}public static Uri parse(String s){return new Uri(s);}public static Uri fromFile(java.io.File f){try{return parse(new java.net.URI("file",null,f.getAbsolutePath(),null).toString());}catch(Exception e){throw new RuntimeException(e);}}public String getScheme(){return u.getScheme();}public String getAuthority(){return u.getAuthority();}public String getQuery(){return u.getQuery();}public String getFragment(){return u.getFragment();}public String getPath(){return u.getPath();}public String toString(){return u.toString();}}'''
FIX['android/system/ErrnoException.java']='''package android.system;public class ErrnoException extends Exception{public int errno;public ErrnoException(int n){errno=n;}}'''
FIX['android/system/OsConstants.java']='''package android.system;public class OsConstants{public static final int ENOENT=2;}'''
FIX['android/system/Os.java']='''package android.system;public class Os{public static Object lstat(String p)throws ErrnoException{if(p.contains("unconfirmed"))throw new ErrnoException(13);if(!java.nio.file.Files.exists(java.nio.file.Paths.get(p),java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new ErrnoException(2);return new Object();}}'''
FIX['com/alensw/jni/MediaCleanup.java']='package com.alensw.jni;public class MediaCleanup{public static int calls;public static int delete(android.content.ContentResolver r,android.net.Uri u,String w,String[] a){calls++;return 0;}}'
FIX['com/alensw/jni/SearchDeleteProbe.java']=r'''package com.alensw.jni;
import android.app.Dialog;import android.content.Context;import android.net.Uri;import java.io.*;import java.nio.file.*;import java.util.*;
public class SearchDeleteProbe{
static int checks;static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
static void put(File f)throws Exception{f.getParentFile().mkdirs();Files.write(f.toPath(),new byte[]{3,4,5});}
public static void main(String[] a)throws Exception{
 File w=new File(a[0]),parent=new File(w,"book"),child=new File(parent,"chapter");
 File[] entries={new File(parent,"page.png"),new File(parent,"note.txt"),new File(parent,".nomedia"),new File(child,".hidden"),new File(child,"x.bin")};for(File f:entries)put(f);
 File neighbor=new File(w,"book-extra/keep.txt");put(neighbor);Dialog d=new Dialog(new Context());FolderDelete.Result r=new FolderDelete.Result(3);Uri pu=Uri.fromFile(parent);
 check(SearchDelete.deleteFolder(d,pu,r),"whole folder success");check(!parent.exists(),"parent directory gone");for(File f:entries)check(!f.exists(),"all entries gone "+f.getName());check(neighbor.exists(),"unselected prefix neighbor survives");
 check(SearchDelete.deleteFolder(d,Uri.fromFile(child),r),"parent then child selection");check(SearchDelete.alreadyGone(Uri.fromFile(entries[0]),r),"parent then image selection");
 Collection<Uri> roots=Arrays.asList(pu);check(SearchDelete.covered(Uri.fromFile(child),roots),"prune child search row");check(SearchDelete.covered(Uri.fromFile(entries[0]),roots),"prune image row");check(!SearchDelete.covered(Uri.fromFile(neighbor),roots),"boundary excludes neighbor");
 put(entries[0]);check(!SearchDelete.covered(Uri.fromFile(entries[0]),roots),"recreated file retained");check(!SearchDelete.covered(Uri.fromFile(child),roots),"recreated parent does not cover absent child");check(!SearchDelete.alreadyGone(Uri.fromFile(entries[0]),r),"recreated is not done");
 File file=new File(w,"plain.png");put(file);check(!SearchDelete.deleteFolder(d,Uri.fromFile(file),new FolderDelete.Result(1))&&file.exists(),"file never accepted as folder");check(SearchDelete.isFile(Uri.fromFile(file)),"local file guard");check(!SearchDelete.isFile(Uri.fromFile(parent)),"directory rejected from image route");check(!SearchDelete.afterFile(d.getContext(),Uri.fromFile(file))&&MediaCleanup.calls==0,"present image never cleaned");file.delete();check(SearchDelete.afterFile(d.getContext(),Uri.fromFile(file))&&MediaCleanup.calls==1,"absent image index cleanup");
 File cancel=new File(w,"cancel");put(new File(cancel,"keep.dat"));d.showing=false;check(!SearchDelete.deleteFolder(d,Uri.fromFile(cancel),new FolderDelete.Result(1))&&cancel.exists(),"cancel before mutation");d.showing=true;
 for(String bad:new String[]{"content://media/external/1","file://other/book","file:///tmp/x?q=1","file:///tmp/x#x","file:relative"})check(!SearchDelete.deleteFolder(d,Uri.parse(bad),new FolderDelete.Result(1)),"reject invalid uri");
 check(!SearchDelete.covered(null,roots),"null candidate");check(!SearchDelete.covered(pu,null),"null roots");
 File uncertain=new File(w,"unconfirmed");put(new File(uncertain,"a.txt"));check(!SearchDelete.deleteFolder(d,Uri.fromFile(uncertain),new FolderDelete.Result(1)),"no success without ENOENT proof");
 System.out.println("checks="+checks);
}}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('--output-json',type=Path);p.add_argument('--work-dir',type=Path);a=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='search-delete-',dir=a.work_dir) as tmp:
  w=Path(tmp);src=w/'src';out=w/'classes';out.mkdir()
  for n,t in FIX.items():
   f=src/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(t)
  sources=list(src.rglob('*.java'))+[ROOT/'src/com/alensw/jni/FolderDelete.java',ROOT/'src/com/alensw/jni/SearchDelete.java']
  subprocess.run(['javac','--release','8','-d',str(out),*map(str,sources)],check=True)
  result=subprocess.check_output(['java','-cp',str(out),'com.alensw.jni.SearchDeleteProbe',str(w/'files')],text=True).strip()
  value={'passed':True,'host':result,'scope':'real Java helper with Android API doubles and synthetic filesystem; not Android provider/UI'}
  if a.output_json:a.output_json.write_text(json.dumps(value,indent=2)+'\n')
  print(json.dumps(value))
if __name__=='__main__':main()
