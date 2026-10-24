// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

/** A page transaction: decode off screen, then change pixels and page identity together. */
public final class ReaderMode {
    private ReaderMode() { }
    public static final String KEY = "reader_mode";
    public static final int FULL_REQUEST = 5;

    /** Implemented by the two pinned legacy viewer controllers through checked DEX bridges. */
    public interface Host {
        Context readerContext();
        int readerIndex();
        int readerCount();
        Uri readerUri(int index);
        boolean readerImage(int index);
        Future<?> readerLoad(int index, Handler handler);
        void readerCommit(Object frame, int index);
        void readerCancelLegacy();
        boolean readerActive();
        boolean readerSlideshow();
        boolean readerSlideReady();
        void readerStopSlideshow();
    }
    /** A complete bitmap, or an animation whose first frame has already been decoded and drawn. */
    public interface Frame {
        Uri readerUri();
        boolean readerReady();
        void readerRelease();
    }
    private static final WeakHashMap<Host, State> STATES = new WeakHashMap<Host, State>();
    private static final class State {
        final WeakReference<Host> host;
        Uri visible;
        Pending pending;
        boolean committing;
        long slideGeneration;
        State(Host value) { host = new WeakReference<Host>(value); }
    }
    private static State state(Host host) {
        State value = STATES.get(host);
        if (value == null) { value = new State(host); STATES.put(host, value); }
        return value;
    }
    public static boolean enabled(Context context) {
        return context != null && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false);
    }
    public static int slideshowRequest(Context context) { return enabled(context) ? FULL_REQUEST : 2; }
    private static boolean ready(Host host) {
        return host.readerActive() && enabled(host.readerContext());
    }
    private static boolean same(Uri left, Uri right) { return left != null && left.equals(right); }

    /** Called before the legacy display seam. Never allow a preview to replace the old page. */
    public static boolean beforeDisplay(Host host, Object picture) {
        if (!ready(host)) {
            cancel(host);
            return false;
        }
        State value = state(host);
        if (value.committing) return false;
        if (value.pending != null) return true;
        if (!host.readerImage(-1)) { cancel(host); return false; }
        Uri current = host.readerUri(-1);
        if (same(value.visible, current)) return true;
        if (picture instanceof Frame) {
            Frame frame = (Frame)picture;
            if (frame.readerReady() && same(frame.readerUri(), current)) {
                value.visible = current;
                return false;
            }
        }
        start(host, value, host.readerIndex());
        return true;
    }
    /** Preserve repeated taps without changing the selected/deletable page until commit. */
    public static boolean turn(Host host, int direction) {
        if (!ready(host)) return false;
        int count = host.readerCount();
        if (count < 1 || direction == 0) return false;
        State value = state(host);
        int base = value.pending == null ? host.readerIndex() : value.pending.index;
        if (base < 0 || base >= count) return false;
        int target = (base + (direction > 0 ? 1 : count - 1)) % count;
        if (!host.readerImage(target)) { cancel(host); return false; }
        if (target == host.readerIndex() && same(value.visible, host.readerUri(-1))) {
            cancelPending(value);
            return true;
        }
        start(host, value, target);
        return true;
    }
    public static void resume(Host host) {
        if (!ready(host)) { cancel(host); return; }
        if (host.readerSlideshow() || !host.readerImage(-1)) return;
        State value = state(host);
        if (value.pending == null && !same(value.visible, host.readerUri(-1)))
            start(host, value, host.readerIndex());
    }
    /** Reload a settled post-removal target while the deleted page is still visible. */
    public static void reloadTarget(Host host, int index) {
        if (!ready(host) || !host.readerImage(index)) { cancel(host); return; }
        start(host, state(host), index);
    }
    private static void start(Host host, State value, int index) {
        cancelPending(value);
        host.readerCancelLegacy();
        Pending pending = new Pending(value, index, host.readerUri(index), host.readerUri(-1), host.readerIndex());
        value.pending = pending;
        try {
            pending.future = host.readerLoad(index, pending.handler);
            if (pending.future == null) { pending.fail(); return; }
            pending.handler.postDelayed(pending, 40);
        } catch (RuntimeException failure) { pending.fail(); }
    }
    private static void cancelPending(State value) {
        Pending pending = value.pending;
        value.pending = null;
        if (pending != null) {
            if (pending.future != null) pending.future.cancel(false);
            pending.handler.removeCallbacks(pending);
            // Do not remove message30: its retained picture must still be released.
        }
    }
    public static void cancel(Host host) {
        State value = STATES.remove(host);
        if (value != null) { cancelPending(value); value.slideGeneration++; }
    }
    private static final class Pending implements Runnable, Handler.Callback {
        final State state;
        final int index, baseIndex;
        final Uri target, baseUri;
        final Handler handler;
        Future<?> future;
        boolean completionSeen;
        Pending(State s, int i, Uri uri, Uri old, int oldIndex) {
            state=s; index=i; target=uri; baseUri=old; baseIndex=oldIndex;
            handler=new Handler(Looper.getMainLooper(), this);
        }
        boolean live(Host host) {
            return state.pending == this && host != null && ready(host)
                && !host.readerSlideshow() && host.readerIndex() == baseIndex
                && same(host.readerUri(-1), baseUri) && same(host.readerUri(index), target);
        }
        @Override public boolean handleMessage(Message message) {
            if (message.what != 30 || !(message.obj instanceof Frame)) return true;
            Frame frame = (Frame)message.obj;
            try {
                Host host = state.host.get();
                if (!live(host)) return true;
                if (!frame.readerReady() || !same(frame.readerUri(), target)) { fail(); return true; }
                state.pending = null;
                handler.removeCallbacks(this);
                state.committing = true;
                try { host.readerCommit(frame, index); state.visible=target; }
                finally { state.committing=false; }
            } finally { frame.readerRelease(); }
            return true;
        }
        @Override public void run() {
            Host host = state.host.get();
            if (!live(host)) {
                if (state.pending == this) cancelPending(state);
                return;
            }
            if (!future.isDone()) { handler.postDelayed(this, 40); return; }
            if (!completionSeen) { completionSeen=true; handler.post(this); return; }
            // The loader enqueues message30 before completing its Future. The UI
            // queue consumes that picture first; absence here is a terminal failure.
            try { future.get(); } catch (Exception failure) { fail(); return; }
            fail();
        }
        void fail() {
            Host host = state.host.get();
            boolean notify = live(host);
            if (state.pending == this) cancelPending(state);
            if (notify) showFailure(host.readerContext());
        }
    }
    public static void commitSlide(Host host, Object picture, int index) {
        if (!(picture instanceof Frame)) return;
        Frame frame=(Frame)picture;
        if (!ready(host) || !host.readerSlideshow() || !frame.readerReady()
                || !same(frame.readerUri(), host.readerUri(index))) return;
        State value=state(host);
        cancelPending(value);
        value.committing=true;
        try { host.readerCommit(frame,index); value.visible=frame.readerUri(); }
        finally { value.committing=false; }
    }
    public static void slideReceived(Host host) { state(host).slideGeneration++; }
    public static void watchSlide(final Host host, final Future<?> future) {
        final State value=state(host);
        cancelPending(value);
        final long generation=++value.slideGeneration;
        final Handler handler=new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            boolean completionSeen;
            @Override public void run() {
                Host current=value.host.get();
                if (current == null || generation != value.slideGeneration || !ready(current)
                        || !current.readerSlideshow()) return;
                if (!future.isDone()) { handler.postDelayed(this,40); return; }
                if (!completionSeen) { completionSeen=true; handler.post(this); return; }
                if (current.readerSlideReady()) return;
                current.readerStopSlideshow();
                showFailure(current.readerContext());
            }
        },40);
    }
    private static void showFailure(Context context) {
        String language=context.getResources().getConfiguration().locale.getLanguage();
        Toast.makeText(context, "zh".equals(language) ? "圖片載入失敗" : "Could not load image", Toast.LENGTH_SHORT).show();
    }
}
