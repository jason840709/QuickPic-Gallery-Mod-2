#!/usr/bin/env python3
"""Exercise production tap routing with small Android and preference doubles."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac')).parent
STUBS = {
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/view/MotionEvent.java': '''package android.view;
public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;
private final int action,count;public MotionEvent(int a,int c){action=a;count=c;}public int getActionMasked(){return action;}public int getPointerCount(){return count;}}''',
    'android/view/View.java': '''package android.view; import android.content.Context;
public class View { public boolean shown=true,focus=true;public int width=900,height=1200;public Context context=new Context();
public boolean isShown(){return shown;}public boolean hasWindowFocus(){return focus;}public int getWidth(){return width;}public int getHeight(){return height;}public Context getContext(){return context;} }''',
    'com/alensw/jni/TapPagePreference.java': '''package com.alensw.jni; import android.content.Context;
public class TapPagePreference { public static int mode,reads;public static boolean fail;public static boolean reversed(Context c){return false;}public static int mode(Context c){reads++;if(fail)throw new IllegalStateException();return mode;} }''',
    'com/alensw/jni/TapPageLayout.java': '''package com.alensw.jni;
public class TapPageLayout { public static final int COUNT=8;public static int result,calls;public static int action(int mode,boolean reversed,float x,float y,int w,int h){calls++;return result;} }'''
}

def main():
    with tempfile.TemporaryDirectory(prefix='quickpic-tap-navigation-') as tmp:
        work = Path(tmp); sources = []
        for relative, text in STUBS.items():
            p = work / relative; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text); sources.append(p)
        sources += [ROOT / 'src/com/alensw/jni/TapPageNavigation.java', Path(__file__).with_name('NavigationProbe.java')]
        subprocess.run([str(JAVA/'javac'), '--release', '8', '-encoding', 'UTF-8', '-d', str(work/'classes'), *map(str, sources)], check=True)
        subprocess.run([str(JAVA/'java'), '-cp', str(work/'classes'), 'com.alensw.jni.NavigationProbe'], check=True)

if __name__ == '__main__':
    main()
