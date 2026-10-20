import com.alensw.jni.MediaDates;
import android.content.Context;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class MediaDatesTest {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void reset() throws Exception { for(String n:new String[]{"created","modified","directories","baseline","newRoots"}){Field f=MediaDates.class.getDeclaredField(n);f.setAccessible(true);Object o=f.get(null);if(o instanceof Map)((Map)o).clear();else ((Set)o).clear();} }
 static File file(File d,String n,long t)throws Exception{File f=new File(d,n);try(FileOutputStream o=new FileOutputStream(f)){o.write(1);}if(!f.setLastModified(t))throw new IOException("mtime");return f;}
 public static void main(String[]args)throws Exception{
 File root=new File(args[0]);root.mkdirs();File data=new File(root,"comic");data.mkdir();
 File old=file(data,"old.webp",1000000);File hidden=file(data,".hidden.jpg",900000);
 MediaDates.configure(new Context(root));
 MediaDates.observeDirectory(data,data.listFiles());
 ok(MediaDates.created(old)==1000000,"initial unsupported creation uses original mtime, not now");
 ok(MediaDates.modified(old)==1000000,"modified independent");
 ok(MediaDates.created(hidden)==900000,"hidden entries journaled without exposing UI");
 File added=file(data,"download.png",1000);long before=System.currentTimeMillis();
 MediaDates.observeDirectory(data,data.listFiles());long arrival=MediaDates.created(added);
 ok(arrival>=before,"new file with archived old mtime uses arrival");
 ok(MediaDates.created(old)==1000000,"old creation unchanged when adding sibling");
 ok(MediaDates.modified(added)==1000,"new creation does not overwrite modification");
 added.setLastModified(2000);MediaDates.observeDirectory(data,data.listFiles());
 ok(MediaDates.created(added)==arrival && MediaDates.modified(added)==2000,"editing changes only modification");
 reset();MediaDates.configure(new Context(root));
 ok(MediaDates.created(added)==arrival,"arrival survives process restart");
 MediaDates.observeDirectory(data,data.listFiles());
 File newDir=new File(data,"new comic");newDir.mkdir();newDir.setLastModified(100);
 MediaDates.observeDirectory(data,data.listFiles());
 ok(MediaDates.isNew(newDir),"new folder recognized");
 File page=file(newDir,"001.jpg",50);MediaDates.observeDirectory(newDir,newDir.listFiles());
 ok(MediaDates.created(page)>1000000 && MediaDates.isNew(new File(newDir,"nested")),"new subtree inherits arrival priority");
 MediaDates.observeDirectory(data,null);ok(MediaDates.created(old)==1000000,"failed listing does not replace baseline");
 old.delete();MediaDates.observeDirectory(data,data.listFiles());File replaced=file(data,"old.webp",1);MediaDates.observeDirectory(data,data.listFiles());
 ok(MediaDates.created(replaced)>1000000,"observed delete then recreate is new");
 // Existing album and nested chapter, with archived page timestamps.
 File album=new File(data,"album");album.mkdir();MediaDates.observeDirectory(data,data.listFiles());
 File chapter=new File(album,"chapter");chapter.mkdir();MediaDates.observeDirectory(album,album.listFiles());
 File oldPage=file(chapter,"page.png",10);MediaDates.observeDirectory(chapter,chapter.listFiles());long oldArrival=MediaDates.created(oldPage);
 oldPage.delete();chapter.delete();album.delete();MediaDates.observeDirectory(data,data.listFiles());
 album.mkdir();MediaDates.observeDirectory(data,data.listFiles());
 // Restart before rediscovering children: stale descendant files remain on disk.
 reset();MediaDates.configure(new Context(root));
 chapter.mkdir();MediaDates.observeDirectory(album,album.listFiles());
 File newPage=file(chapter,"page.png",10);long newBefore=System.currentTimeMillis();MediaDates.observeDirectory(chapter,chapter.listFiles());
 ok(MediaDates.created(newPage)>=newBefore,"recreated album invalidates nested old page journal across restart");
 ok(MediaDates.modified(newPage)==10,"recreated album retains archived modification time");
 reset();MediaDates.configure(new Context(root));
 File index=new File(root,"quickpic-date-index-v1");for(File f:index.listFiles())try(FileOutputStream out=new FileOutputStream(f)){out.write(1);}
 ok(MediaDates.created(added)==2000,"corrupt index falls back without crashing");
 File fresh=new File(root,"inaccessible-before-first-list");fresh.mkdir();MediaDates.observeDirectory(fresh,null);File p=file(fresh,"existing.png",77);MediaDates.observeDirectory(fresh,fresh.listFiles());
 ok(MediaDates.created(p)==77,"failed first listing does not make old content new");
 System.out.println("PASS "+checks+" date-journal assertions; actual Java journal with Context/AtomicFile doubles, no Android UI claim");
 }
}
