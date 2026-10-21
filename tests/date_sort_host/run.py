#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Run behavioral date-sort regressions with small Android/model host doubles.

This exercises the real maintenance helper. Android execution and final DEX
member verification remain separate release checks.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
'android/content/Context.java': '''package android.content; public class Context { public Context getApplicationContext(){return this;} public ContentResolver getContentResolver(){return new ContentResolver();} public android.content.res.Resources getResources(){return new android.content.res.Resources();} }''',
'android/content/ContentResolver.java': '''package android.content; public class ContentResolver { public static int queries; public android.database.Cursor query(android.net.Uri u,String[]p,String s,String[]a,String o){queries++;return new android.database.Cursor(){public boolean moveToFirst(){return true;}public long getLong(int i){String path=u.getPath();long id=Long.parseLong(path.substring(path.lastIndexOf('/')+1));return id>=10000?id:42;}public void close(){}};} }''',
'android/content/res/Resources.java': '''package android.content.res; public class Resources {public Configuration getConfiguration(){return new Configuration();}}''',
'android/content/res/Configuration.java': '''package android.content.res; public class Configuration {public java.util.Locale locale=java.util.Locale.TAIWAN;}''',
'android/database/Cursor.java': '''package android.database; public interface Cursor extends AutoCloseable {boolean moveToFirst();long getLong(int i);void close();}''',
'android/net/Uri.java': '''package android.net; public class Uri {private final java.net.URI u;private Uri(String s){u=java.net.URI.create(s);}public static Uri parse(String s){return new Uri(s);}public String getScheme(){return u.getScheme();}public String getAuthority(){return u.getAuthority();}public String getPath(){return u.getPath();}public String toString(){return u.toString();}}''',
'android/os/Looper.java': '''package android.os;public class Looper {public static boolean background;private static final Looper MAIN=new Looper();public static Looper myLooper(){return background?null:MAIN;}public static Looper getMainLooper(){return MAIN;}}''',
'android/view/Menu.java': '''package android.view;public interface Menu {MenuItem findItem(int id);MenuItem add(int group,int id,int order,CharSequence title);}''',
'android/view/MenuItem.java': '''package android.view;public interface MenuItem {int getItemId();int getOrder();MenuItem setTitle(CharSequence title);}''',
'com/alensw/jni/MediaDates.java': '''package com.alensw.jni;import java.io.File;import java.util.*;public class MediaDates {public static final Map<String,Long>C=new HashMap<>(),M=new HashMap<>();public static final List<String>calls=new ArrayList<>();public static long created(File f){calls.add(f.getPath());return C.getOrDefault(f.getPath(),0L);}public static long modified(File f){calls.add(f.getPath());return M.getOrDefault(f.getPath(),0L);}}''',
'com/alensw/b/b/b.java': '''package com.alensw.b.b;import java.util.*;public class b {public static Comparator<Object>a=(x,y)->{String s=x.toString(),t=y.toString();java.util.regex.Matcher m=java.util.regex.Pattern.compile("(.*?)([0-9]+)$").matcher(s),n=java.util.regex.Pattern.compile("(.*?)([0-9]+)$").matcher(t);if(m.matches()&&n.matches()&&m.group(1).equals(n.group(1)))return Integer.compare(Integer.parseInt(m.group(2)),Integer.parseInt(n.group(2)));return s.compareToIgnoreCase(t);};}''',
'com/alensw/b/g/b.java': '''package com.alensw.b.g; public abstract class b implements android.view.Menu {public final java.util.ArrayList<android.view.MenuItem>e=new java.util.ArrayList<>();}''',
'DateSortHarness.java': r'''
import com.alensw.jni.DateSort;
import com.alensw.jni.MediaDates;
import java.io.File;
import java.util.*;
public class DateSortHarness {
  static int assertions;
  static final Comparator<Object> OLD=(a,b)->0;
  static void check(boolean b,String m){assertions++;if(!b)throw new AssertionError(m);}
  public static class Picture {public String a,b; public int d,e;Picture(String n){a="/local";b=n;d=9;e=999;}}
  public static class Folder extends File {public int d,g;Folder(String n,int pin,int kind){super("/folders/"+n);d=pin<<8;g=kind;}}
  public static class Common {final String name,path,uri;final long modified,taken;Common(String n,String p,String u,long m,long t){name=n;path=p;uri=u;modified=m;taken=t;}public String a(){return name;}public String j(){return path;}public long e(){return modified;}public long f(){return taken;}public android.net.Uri i(){return android.net.Uri.parse(uri);}}
  static class Item implements android.view.MenuItem {int id,order;String title;Item(int i,int o,String t){id=i;order=o;title=t;}public int getItemId(){return id;}public int getOrder(){return order;}public android.view.MenuItem setTitle(CharSequence t){title=t.toString();return this;}}
  static class Menu extends com.alensw.b.g.b {Map<Integer,Item>items=new LinkedHashMap<>();public android.view.MenuItem findItem(int i){return items.get(i);}public android.view.MenuItem add(int g,int i,int o,CharSequence t){Item a=new Item(i,o,t.toString());items.put(i,a);e.add(a);return a;}}
  static ArrayList<Object> list(Object... a){return new ArrayList<>(Arrays.asList(a));}
  static void time(String p,long c,long m){MediaDates.C.put(p,c);MediaDates.M.put(p,m);}
  public static void main(String[] args){
    check(DateSort.nextCreationMode(0)==9&&DateSort.nextCreationMode(9)==10&&DateSort.nextCreationMode(10)==9,"newest first and reverse toggle");
    Item created=new Item(DateSort.CREATED_ID,0,"");
    check(DateSort.creationModeForItem(created,9)==10,"new menu dispatch");
    check(DateSort.creationModeForItem(new Item(17,0,""),9)==-1,"existing menu dispatch untouched");
    check(DateSort.selected(9,0)==DateSort.CREATED_ID&&!DateSort.reverse(9,true)&&DateSort.reverse(10,false),"new-mode menu selection/reverse");
    check(DateSort.selected(3,17)==17&&DateSort.reverse(3,true),"old menu state untouched");
    check(DateSort.isCommonDate(1)&&DateSort.isCommonDate(3)&&DateSort.isCommonDate(9)&&DateSort.isCommonDate(10)&&!DateSort.isCommonDate(2),"provider date modes force comparator after cursor loading");
    for(int mode:new int[]{9,10}){
      int packed=(2<<8)|(3<<4)|mode;check((packed&15)==mode&&((packed>>4)&15)==3&&((packed>>8)&15)==2,"per-folder mode bit preservation");
      packed=65536|(mode<<8)|3;check(((packed>>8)&255)==mode&&(packed&255)==3&&(packed>>16)==1,"gallery mode bit preservation");
      check(DateSort.isFolderDate(mode),"new folder date mode refresh predicate");
    }
    Picture a=new Picture("page2"),b=new Picture("page10");time("/local/page2",10,80);time("/local/page10",90,20);
    ArrayList<Object>pictures=list(a,b);
    check(DateSort.localComparator(pictures,9,OLD).compare(a,b)>0,"created newest-first differs from mtime");
    check(DateSort.localComparator(pictures,10,OLD).compare(a,b)<0,"created oldest-first");
    check(DateSort.localComparator(pictures,1,OLD).compare(a,b)<0,"modified newest-first");
    check(DateSort.localComparator(pictures,3,OLD).compare(a,b)>0,"modified oldest-first");
    for(int mode:new int[]{2,4,5,6,7,8})check(DateSort.localComparator(pictures,mode,OLD)==OLD,"old local comparator retained "+mode);
    check(DateSort.localComparator(pictures,9,OLD)==DateSort.localComparator(pictures,9,OLD),"stable comparator identity");
    time("/local/page2",90,80);
    check(DateSort.localComparator(pictures,10,OLD).compare(a,b)<0,"natural tie ascending");
    check(DateSort.localComparator(pictures,9,OLD).compare(a,b)>0,"natural tie descending");
    Comparator<Object> saved=com.alensw.b.b.b.a;com.alensw.b.b.b.a=(x,y)->-saved.compare(x,y);
    check(DateSort.localComparator(pictures,10,OLD).compare(a,b)>0,"logical-name preference comparator changes observed");com.alensw.b.b.b.a=saved;
    Folder pin=new Folder("pin",1,0),old=new Folder("old",2,0),fresh=new Folder("fresh",2,0),camera=new Folder("camera",0,2);
    time(pin.getPath(),1,1);time(old.getPath(),2,100);time(fresh.getPath(),100,2);time(camera.getPath(),1,1);
    ArrayList<Object>folders=list(pin,old,fresh,camera);
    Comparator<Object>fc=DateSort.folderComparator(folders,9,OLD);
    check(fc.compare(pin,fresh)<0&&fc.compare(camera,fresh)<0,"explicit and automatic camera pins stay first");
    check(fc.compare(fresh,old)<0,"folder creation newest-first");
    check(DateSort.folderComparator(folders,10,OLD).compare(fresh,old)>0,"folder creation oldest-first");
    check(DateSort.folderComparator(folders,4,OLD).compare(fresh,old)>0,"old folder modified descending");
    check(DateSort.folderComparator(folders,3,OLD).compare(fresh,old)<0,"old folder modified ascending");
    for(int mode:new int[]{1,2,5,6})check(DateSort.folderComparator(folders,mode,OLD)==OLD,"old folder comparator retained "+mode);
    Common x=new Common("x",null,"content://cloud/item/x",5,1000),y=new Common("y",null,"content://cloud/item/y",20,1);
    ArrayList<Object>common=list(x,y);MediaDates.calls.clear();
    check(DateSort.commonComparator(common,1,OLD).compare(x,y)>0,"common modified uses e(), not datetaken f()");
    check(DateSort.commonComparator(common,3,OLD).compare(x,y)<0,"common modified ascending");
    check(DateSort.commonComparator(common,9,OLD).compare(x,y)>0,"provider creation unsupported falls back to modified");
    check(MediaDates.calls.isEmpty(),"content URI is never treated as a filesystem path");
    DateSort.configure(null);DateSort.configure(new android.content.Context(){public android.content.Context getApplicationContext(){return null;}});DateSort.configure(new android.content.Context(){public android.content.ContentResolver getContentResolver(){throw new SecurityException("unavailable");}});check(true,"optional configure tolerates null application context/provider failure");
    DateSort.configure(new android.content.Context());Common media=new Common("m",null,"content://media/external/images/media/7",2,1000);
    android.os.Looper.background=false;DateSort.commonComparator(list(media,y),9,OLD);check(android.content.ContentResolver.queries==0,"no main-thread provider query");
    android.os.Looper.background=true;Comparator<Object>mc=DateSort.commonComparator(list(media,y),9,OLD);check(android.content.ContentResolver.queries==1&&mc.compare(media,y)<0,"background date_added used");
    mc.compare(media,y);DateSort.commonComparator(list(media,y),9,OLD);check(android.content.ContentResolver.queries==1,"provider result cached and compare performs no queries");
    ArrayList<Object> bulk=new ArrayList<>();
    for(int n=0;n<8193;n++)bulk.add(new Common("bulk"+n,null,"content://media/external/images/media/"+(10000+n),50000-n,0));
    Object first=bulk.get(0),last=bulk.get(8192);int queryStart=android.content.ContentResolver.queries;
    Comparator<Object> bulkDates=DateSort.commonComparator(bulk,9,OLD);
    check(android.content.ContentResolver.queries-queryStart==8193,"8193 distinct provider dates prewarmed");
    check(bulkDates.compare(first,last)>0,"first provider creation date survives the former 8192-item cache limit");
    Collections.sort(bulk,bulkDates);
    boolean complete=true;for(int n=0;n<bulk.size();n++)complete&=((Common)bulk.get(n)).uri.endsWith("/"+(18192-n));
    check(complete,"all 8193 items use creation date despite opposite mtime order");
    check(bulk.get(0)==last&&bulk.get(8192)==first,"8193-item newest-first endpoints");
    check(mc.compare(media,y)<0,"earlier active comparator remains correct after an 8193-item view");
    int afterPrewarm=android.content.ContentResolver.queries;bulkDates.compare(first,last);Collections.sort(bulk,bulkDates);
    check(android.content.ContentResolver.queries==afterPrewarm,"large-view compare and resort perform no provider queries");
    Comparator<Object> bulkAscending=DateSort.commonComparator(bulk,10,OLD);Collections.sort(bulk,bulkAscending);
    check(bulk.get(0)==first&&bulk.get(8192)==last,"8193-item reverse also preserves complete creation keys");
    for(int mode:new int[]{2,4,7,8})check(DateSort.commonComparator(common,mode,OLD)==OLD,"old common comparator retained "+mode);
    Menu menu=new Menu();menu.add(0,0x7f090075,0,"按修改日期");int[]allowed={0x7f090074,0x7f090075};int[]expanded=DateSort.prepareMenu(new android.content.Context(),menu,allowed);
    check(expanded.length==3&&menu.findItem(DateSort.CREATED_ID)!=null,"creation item survives menu whitelist");
    Menu grouped=new Menu();for(int id:new int[]{0x7f090074,0x7f090075,0x7f090076,0x7f090077,0x7f090078})grouped.add(0,id,0,"item");
    DateSort.prepareMenu(new android.content.Context(),grouped,new int[]{0x7f090074,0x7f090075,0x7f090076,0x7f090077,0x7f090078});
    check(grouped.e.get(1).getItemId()==0x7f090075&&grouped.e.get(2).getItemId()==DateSort.CREATED_ID&&grouped.e.get(3).getItemId()==0x7f090076,"all three date choices consecutive in actual legacy backing list");
    check(menu.items.get(0x7f090075).title.equals("按修改日期"),"patched modified translation is retained");
    check(DateSort.prepareMenu(new android.content.Context(),menu,expanded).length==3&&menu.items.size()==2,"menu augmentation is idempotent");
    System.out.println("DateSort host behavioral assertions passed: "+assertions);
  }
}
''',
}


def run(root=ROOT):
    with tempfile.TemporaryDirectory(prefix='quickpic-date-sort-host-') as temporary:
        work = Path(temporary)
        sources = dict(SOURCES)
        sources['com/alensw/jni/DateSort.java'] = (root / 'src/com/alensw/jni/DateSort.java').read_text()
        sources['com/alensw/jni/MenuLayout.java'] = (root / 'src/com/alensw/jni/MenuLayout.java').read_text()
        paths = []
        for relative, source in sources.items():
            path = work / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            paths.append(path)
        classes = work / 'classes'
        subprocess.run(['javac', '--release', '8', '-d', str(classes), *map(str, paths)], check=True)
        subprocess.run(['java', '-cp', str(classes), 'DateSortHarness'], check=True)


def check_patch(decoded, apktool=None, root=ROOT):
    spec = importlib.util.spec_from_file_location('patch_dates_under_test', root / 'scripts/patch_dates.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    def hashes(folder):
        return {str(p.relative_to(folder)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in folder.rglob('*') if p.is_file()}
    with tempfile.TemporaryDirectory(prefix='quickpic-date-sort-patch-') as temporary:
        work = Path(temporary)
        target = work / 'decoded'
        shutil.copytree(decoded, target)
        report = module.patch(target)
        first = hashes(target)
        module.patch(target)
        assert hashes(target) == first, 'Patch must be idempotent on the actual decoded APK'
        e = (target / 'smali/com/alensw/a/e.smali').read_text()
        x = (target / 'smali/com/alensw/a/x.smali').read_text()
        assert 'and-int/lit8 v0, v0, 0xf' in e, 'Unexpected picture mode storage'
        assert 'and-int/lit16 v0, v0, 0xff' in x, 'Unexpected gallery mode storage'
        for filename, signature in (
            ('com/alensw/a/e', 'localComparator'),
            ('com/alensw/a/x', 'folderComparator'),
            ('com/alensw/bean/CommonFolder', 'commonComparator')):
            text = (target / ('smali/' + filename + '.smali')).read_text()
            descriptor = 'Lcom/alensw/jni/DateSort;->' + signature + '(Ljava/util/ArrayList;ILjava/util/Comparator;)Ljava/util/Comparator;'
            assert text.count(descriptor) == 1, descriptor
        query = (target / 'smali/com/alensw/a/av.smali').read_text()
        assert query.count('->isCommonDate(I)Z') == 1
        assert ':dates_sort_common\n    iget-object v2, p0, Lcom/alensw/a/av;->h:Lcom/alensw/bean/CommonFolder;' in query
        for name in ('bz', 'bf', 'z'):
            text = (target / ('smali/com/alensw/ui/c/' + name + '.smali')).read_text()
            assert text.count('->creationModeForItem(Landroid/view/MenuItem;I)I') == 1
            assert text.count('->selected(II)I') == 1
            assert text.count('->reverse(IZ)Z') == 1
        if apktool:
            subprocess.run([str(apktool), 'b', '-o', str(work / 'unsigned.apk'), str(target)], check=True)
        # A changed target signature must fail before it writes any file.
        path = target / 'smali/com/alensw/ui/c/bz.smali'
        path.write_text(path.read_text().replace('.method public a(Landroid/view/MenuItem;)Z', '.method public a(Landroid/view/MenuItem;)I'))
        before = hashes(target)
        try:
            module.patch(target)
        except ValueError:
            pass
        else:
            raise AssertionError('Unexpected source signature was accepted')
        assert hashes(target) == before, 'Rejected patch changed a file'
        print('Date-sort patch checks passed: actual source seams, descriptors, idempotence, fail-before-write; paths:', len(report['paths']))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path, help='Optional supported decoded APK for exact patch-seam checks')
    parser.add_argument('--apktool', type=Path, help='Also assemble the patched decoded APK (requires --decoded)')
    args = parser.parse_args()
    run()
    if args.decoded:
        check_patch(args.decoded, args.apktool)

