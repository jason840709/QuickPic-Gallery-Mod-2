// SPDX-License-Identifier: MIT
package android.os;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
/** Deterministic Android-like ordering. Dispatch time never moves back to a message's due time. */
public final class Looper {
    private static final Looper MAIN=new Looper();
    private static final List<Entry> queue=new ArrayList<Entry>();
    private static long now,sequence;
    public static int removedPictureMessages;
    private static final class Entry {
        final Handler handler; final Runnable runnable; final Message message; final long due,sequence;
        Entry(Handler h,Runnable r,Message m,long d,long s) { handler=h;runnable=r;message=m;due=d;sequence=s; }
    }
    public static Looper getMainLooper() { return MAIN; }
    public static long now() { return now; }
    public static void reset() { queue.clear(); now=0; sequence=0; removedPictureMessages=0; }
    static void enqueue(Handler h,Runnable r,Message m,long delay) { queue.add(new Entry(h,r,m,now+Math.max(0,delay),sequence++)); }
    static void remove(Handler h,Runnable r,boolean messages,int what) {
        for(Iterator<Entry> iterator=queue.iterator();iterator.hasNext();) {
            Entry entry=iterator.next();
            if(entry.handler==h && (messages?entry.message!=null&&entry.message.what==what:entry.runnable==r)) {
                if(entry.message!=null && entry.message.what==30 && entry.message.obj!=null) removedPictureMessages++;
                iterator.remove();
            }
        }
    }
    static boolean hasMessage(Handler h,int what) { for(Entry entry:queue) if(entry.handler==h&&entry.message!=null&&entry.message.what==what)return true;return false; }
    public static int queuedMessages(int what) { int count=0;for(Entry entry:queue)if(entry.message!=null&&entry.message.what==what)count++;return count; }
    public static int queuedReferences(Object object) { int count=0;for(Entry entry:queue)if(entry.message!=null&&entry.message.obj==object)count++;return count; }
    public static int queuedCallbacks() { int count=0;for(Entry entry:queue)if(entry.runnable!=null)count++;return count; }
    public static void blockUntil(long time) { if(time<now)throw new IllegalArgumentException("clock reversal");now=time; }
    private static Entry next() {
        Entry best=null;for(Entry entry:queue) if(best==null||entry.due<best.due||entry.due==best.due&&entry.sequence<best.sequence)best=entry;return best;
    }
    public static void idleUntil(long time) {
        if(time<now)throw new IllegalArgumentException("clock reversal");
        int budget=10000;
        while(true) {
            Entry entry=next();if(entry==null||entry.due>time)break;
            if(--budget==0)throw new AssertionError("main queue failed to quiesce");
            queue.remove(entry);now=Math.max(now,entry.due);
            if(entry.runnable!=null) entry.runnable.run();
            else try { entry.handler.dispatch(entry.message); } finally { entry.message.obj=null;entry.message.target=null; }
        }
        now=Math.max(now,time);
    }
    public static void drainReady() { idleUntil(now); }
}
