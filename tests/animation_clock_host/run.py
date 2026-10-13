#!/usr/bin/env python3
"""Run the production clock helper with monotonic-time and JNI metadata doubles."""
from pathlib import Path
import argparse,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
def write(root,path,text):
 p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline',action='store_true');p.add_argument('--work-dir',type=Path);args=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='animation-clock-',dir=args.work_dir) as td:
  w=Path(td)
  source=ROOT/'src/com/alensw/jni/AnimationPlayback.java'
  # This compatibility seam represents the legacy relative-wait/infinite-loop
  # policy. It is deliberately not described as an Android runtime test.
  body='package com.alensw.jni;public class AnimationPlayback{public static boolean beforeFrame(Object w){return true;}public static int delayAfterFrame(Object w,int d,int legacy){return legacy;}}' if args.baseline else source.read_text()
  write(w,'com/alensw/jni/AnimationPlayback.java',body)
  write(w,'android/os/SystemClock.java','package android.os;public class SystemClock{public static long now;public static long uptimeMillis(){return now;}}')
  write(w,'com/alensw/jni/JniUtils.java','''package com.alensw.jni;public class JniUtils{
  public static boolean closed;public static boolean gifIsAnimatedWebP(int h){return h>=100&&!closed;}
  public static int gifGetLoopCount(int h){return h==101?1:h==102?2:0;}
  public static int gifGetFrameCount(int h){return closed?0:3;}}
''')
  write(w,'ClockTest.java','''import com.alensw.jni.AnimationPlayback;import com.alensw.jni.JniUtils;import android.os.SystemClock;
public class ClockTest{
 static class Movie{private int j,k,m=3;Movie(int h){j=h;}}
 static class Worker{private final Movie a;Worker(Movie m){a=m;}}
 static int checks;static void yes(boolean x,String s){if(!x)throw new AssertionError(s);checks++;}
 static void eq(int a,int b,String s){yes(a==b,s+" expected="+b+" actual="+a);}
 public static void main(String[] a){
  Movie m=new Movie(100);Worker w=new Worker(m);SystemClock.now=0;
  yes(AnimationPlayback.beforeFrame(w),"start WebP");SystemClock.now=5;eq(AnimationPlayback.delayAfterFrame(w,31,26),26,"first deadline");
  m.k=1;SystemClock.now=40;yes(AnimationPlayback.beforeFrame(w),"late next frame");SystemClock.now=43;
  eq(AnimationPlayback.delayAfterFrame(w,31,28),19,"late callback must not accumulate timing drift");
  m.k=2;SystemClock.now=100;yes(AnimationPlayback.beforeFrame(w),"late third frame");SystemClock.now=107;
  eq(AnimationPlayback.delayAfterFrame(w,31,24),1,"late frames catch up without wait(0)");
  for(int loops:new int[]{1,2}){Movie f=new Movie(100+loops);Worker fw=new Worker(f);SystemClock.now=0;
   for(int i=0;i<3*loops;i++){f.k=i%3;yes(AnimationPlayback.beforeFrame(fw),"finite frame "+i);SystemClock.now+=31;AnimationPlayback.delayAfterFrame(fw,31,31);}
   f.k=0;yes(!AnimationPlayback.beforeFrame(fw),"stop after authored total loops "+loops);}
  Movie infinity=new Movie(100);Worker iw=new Worker(infinity);SystemClock.now=0;
  for(int i=0;i<12;i++){infinity.k=i%3;yes(AnimationPlayback.beforeFrame(iw),"infinite frame "+i);SystemClock.now+=31;AnimationPlayback.delayAfterFrame(iw,31,31);}
  Worker fresh=new Worker(m);m.k=0;SystemClock.now=200;yes(AnimationPlayback.beforeFrame(fresh),"new playback resets clock");SystemClock.now=205;eq(AnimationPlayback.delayAfterFrame(fresh,31,26),26,"fresh deadline");
  Movie gif=new Movie(1);Worker gw=new Worker(gif);yes(AnimationPlayback.beforeFrame(gw),"GIF remains playable");eq(AnimationPlayback.delayAfterFrame(gw,0,10),10,"legacy GIF wait unchanged");
  eq(AnimationPlayback.delayAfterFrame(iw,0,10),-1,"WebP failed decode stops");
  JniUtils.closed=true;yes(!AnimationPlayback.beforeFrame(fresh),"closed WebP token stops");JniUtils.closed=false;
  System.out.println("PASS clock checks="+checks);
 }
}''')
  c=subprocess.run(['javac','--release','8','-d',str(w/'classes'),*map(str,w.rglob('*.java'))],text=True,capture_output=True)
  if c.returncode:raise SystemExit('Compile error, not behavior failure:\n'+c.stderr)
  r=subprocess.run(['java','-cp',str(w/'classes'),'ClockTest'],text=True,capture_output=True);print(r.stdout+r.stderr,end='');raise SystemExit(r.returncode)
if __name__=='__main__':main()
