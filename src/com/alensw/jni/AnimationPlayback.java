package com.alensw.jni;

import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Timing adapter for the existing two-buffer worker; GIF policy is unchanged. */
public final class AnimationPlayback {
    private AnimationPlayback() { }
    // Values contain no worker/controller references, so inactive viewers can be
    // collected. Each start/resume creates a new legacy worker and a fresh clock.
    private static final WeakHashMap<Object, State> STATES = new WeakHashMap<Object, State>();
    private static final class State {
        final int handle, frames, loops;
        long deadline;
        int last = -1, completed;
        State(int h, int f, int n, long now) { handle=h; frames=f; loops=n; deadline=now; }
    }
    private static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
            catch (NoSuchFieldException e) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    public static synchronized boolean beforeFrame(Object worker) {
        try {
            Object controller=field(worker,"a");
            int handle=((Integer)field(controller,"j")).intValue();
            if (handle == 0) return false;
            State state=STATES.get(worker);
            if (state != null && state.handle == handle && JniUtils.gifGetFrameCount(handle) == 0)
                return false;
            if (!JniUtils.gifIsAnimatedWebP(handle)) return true;
            int frame=((Integer)field(controller,"k")).intValue();
            int count=JniUtils.gifGetFrameCount(handle);
            if (count <= 1 || frame < 0 || frame >= count) return false;
            if (state == null || state.handle != handle) {
                state=new State(handle,count,JniUtils.gifGetLoopCount(handle),SystemClock.uptimeMillis());
                STATES.put(worker,state);
            }
            if (frame == 0 && state.last == state.frames-1) {
                state.completed++;
                if (state.loops > 0 && state.completed >= state.loops) return false;
            }
            state.last=frame;
            return true;
        } catch (Exception e) {
            // The build patch checks the exact legacy fields. An unexpected
            // future viewer falls back to its previous worker behavior.
            return true;
        }
    }
    public static synchronized int delayAfterFrame(Object worker, int duration, int legacyWait) {
        State state=STATES.get(worker);
        if (state == null) return legacyWait;
        if (duration <= 0 || JniUtils.gifGetFrameCount(state.handle) == 0) return -1;
        state.deadline += duration;
        long remaining=state.deadline-SystemClock.uptimeMillis();
        // Object.wait(0) means wait forever. A late frame gets a bounded 1 ms
        // wakeup; intervening compositing frames still run in authored order.
        return remaining <= 0 ? 1 : (int)Math.min(remaining,Integer.MAX_VALUE);
    }
}
