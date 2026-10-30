#!/usr/bin/env python3
"""Execute production visibility/preferences helpers with narrow Android doubles."""
from pathlib import Path
import subprocess
import os, shutil
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac')).parent
STUBS = {
'android/util/AttributeSet.java':'package android.util;public interface AttributeSet{}',
'android/content/DialogInterface.java':'package android.content;public interface DialogInterface{interface OnClickListener{void onClick(DialogInterface d,int w);}interface OnMultiChoiceClickListener{void onClick(DialogInterface d,int w,boolean b);}}',
'android/content/SharedPreferences.java':'''package android.content;import java.util.*;public class SharedPreferences{public final Map<String,Object> values=new HashMap<String,Object>();public int writes;public boolean failRead,failWrite;public Map<String,?> getAll(){if(failRead)throw new IllegalStateException("read");return new HashMap<String,Object>(values);}public Editor edit(){if(failWrite)throw new IllegalStateException("write");return new Editor();}public class Editor{private Map<String,Object> pending=new HashMap<String,Object>();public Editor putBoolean(String k,boolean b){pending.put(k,b);return this;}public Editor putInt(String k,int v){pending.put(k,v);return this;}public void apply(){values.putAll(pending);writes++;}}}''',
'android/content/Context.java':'package android.content;public class Context{public SharedPreferences prefs=new SharedPreferences();public android.content.pm.PackageManager pm=new android.content.pm.PackageManager();public android.content.pm.PackageManager getPackageManager(){return pm;}public String getPackageName(){return "host.quickpic";}}',
'android/content/pm/PackageInfo.java':'package android.content.pm;public class PackageInfo{public long firstInstallTime=1,lastUpdateTime=1;}',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager{public PackageInfo info=new PackageInfo();public int calls;public PackageInfo getPackageInfo(String p,int f){calls++;return info;}}',
'com/alensw/jni/ScreensaverDevice.java':'package com.alensw.jni;import android.content.Context;public class ScreensaverDevice{public static int calls,result=1;public static boolean fail;public static int defaultAction(Context c){calls++;if(fail)throw new IllegalStateException("detector");return result;}}',
'android/preference/PreferenceManager.java':'package android.preference;import android.content.*;public class PreferenceManager{public static SharedPreferences getDefaultSharedPreferences(Context c){return c.prefs;}}',
'android/preference/Preference.java':'package android.preference;import android.content.*;import android.util.*;public class Preference{private Context context;public String summary;public Preference(Context c,AttributeSet a){context=c;}public void setPersistent(boolean b){}public Context getContext(){return context;}public void setSummary(CharSequence s){summary=s.toString();}protected void onClick(){}}',
'android/app/AlertDialog.java':'''package android.app;import android.content.*;public class AlertDialog{public static CharSequence[] items;public static boolean[] checks;public static DialogInterface.OnClickListener click,positive,negative;public static DialogInterface.OnMultiChoiceClickListener multi;public static class Builder{public Builder(Context c){}public Builder setTitle(int r){return this;}public Builder setTitle(String s){return this;}public Builder setItems(CharSequence[] a,DialogInterface.OnClickListener l){items=a.clone();click=l;return this;}public Builder setMultiChoiceItems(CharSequence[] a,boolean[] b,DialogInterface.OnMultiChoiceClickListener l){items=a.clone();checks=b.clone();multi=l;return this;}public Builder setNegativeButton(String s,DialogInterface.OnClickListener l){negative=l;return this;}public Builder setPositiveButton(String s,DialogInterface.OnClickListener l){positive=l;return this;}public void show(){}}}''',
}

def main():
    with tempfile.TemporaryDirectory(prefix='quickpic-screensaver-options-') as tmp:
        temp=Path(tmp)
        sources=[]
        for name,source in STUBS.items():
            p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);sources.append(p)
        sources += [ROOT/'src/com/alensw/jni/ScreensaverOptions.java',ROOT/'src/com/alensw/jni/ScreensaverMenu.java',Path(__file__).with_name('OptionsProbe.java')]
        subprocess.run([str(JAVA/'javac'),'--release','8','-Xlint:-options','-encoding','UTF-8','-d',str(temp/'classes')]+[str(p) for p in sources],check=True)
        subprocess.run([str(JAVA/'java'),'-cp',str(temp/'classes'),'OptionsProbe'],check=True)

if __name__=='__main__':main()
