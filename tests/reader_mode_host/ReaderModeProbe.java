// SPDX-License-Identifier: MIT
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;
import com.alensw.jni.ReaderMode;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Exercises the production transaction helper, never a reimplementation of it. */
public final class ReaderModeProbe {
    private static int checks, failedCases;
    private static final List<String> results=new ArrayList<String>();
    private interface Scenario { void run() throws Exception; }
    private static void yes(boolean condition,String detail) {
        checks++;
        if(!condition) throw new AssertionError(detail);
    }
    private static void equal(int actual,int expected,String detail) {
        yes(actual==expected,detail+" expected="+expected+" actual="+actual);
    }
    private static void reset() throws Exception {
        Field field=ReaderMode.class.getDeclaredField("STATES");field.setAccessible(true);
        ((Map<?,?>)field.get(null)).clear();Looper.reset();Toast.shown.clear();
    }
    private static void scenario(String name,Scenario scenario) {
        try {
            reset();scenario.run();
            equal(Looper.removedPictureMessages,0,"retained message30 may not be dropped");
            results.add("{\"name\":\""+name+"\",\"result\":\"PASS\"}");
            System.out.println("PASS "+name);
        } catch(Throwable failure) {
            failedCases++;
            results.add("{\"name\":\""+name+"\",\"result\":\"FAIL\"}");
            System.out.println("FAIL "+name+": "+failure);
            failure.printStackTrace(System.out);
        }
    }
    private static final class ControlledFuture implements Future<Object> {
        boolean done,canceled,throwOnGet;
        int cancellations,gets;
        void finish() { done=true; }
        void fail() { done=true;throwOnGet=true; }
        @Override public boolean cancel(boolean interrupt) { cancellations++;canceled=true;done=true;return true; }
        @Override public boolean isCancelled() { return canceled; }
        @Override public boolean isDone() { return done; }
        @Override public Object get() throws ExecutionException {
            gets++;
            if(!done) throw new AssertionError("test must never block on unfinished Future");
            if(canceled)throw new CancellationException();
            if(throwOnGet)throw new ExecutionException(new IllegalStateException("decoder failure"));
            return null;
        }
        @Override public Object get(long timeout,TimeUnit unit) throws ExecutionException { return get(); }
    }
    private static final class Frame implements ReaderMode.Frame {
        final Uri uri;final boolean ready;final String kind;
        int releases;
        Frame(Uri uri,boolean ready,String kind) { this.uri=uri;this.ready=ready;this.kind=kind; }
        @Override public Uri readerUri() { return uri; }
        @Override public boolean readerReady() { return ready; }
        @Override public void readerRelease() {
            if(++releases>1)throw new AssertionError("frame released more than once: "+kind);
        }
    }
    private static final class Load {
        final int index;final Handler handler;final ControlledFuture future=new ControlledFuture();
        Load(int index,Handler handler) { this.index=index;this.handler=handler; }
        void picture(Frame frame,long delay) { handler.sendMessageDelayed(handler.obtainMessage(30,frame),delay); }
        void finish(Frame frame) { picture(frame,0);future.finish(); }
    }
    private static final class Host implements ReaderMode.Host {
        final Context context=new Context();
        final List<Uri> uris=new ArrayList<Uri>();
        final List<Boolean> images=new ArrayList<Boolean>();
        final List<Load> loads=new ArrayList<Load>();
        int index,commits,cancelLegacy,stops;
        boolean active=true,slideshow,slideReady,returnNull,loadThrows,commitReentry;
        Uri heldCurrentUri;Boolean heldCurrentImage;
        String displayed="old page";
        Host(int count) {
            context.readerEnabled=true;
            for(int i=0;i<count;i++){uris.add(Uri.parse("content://public-test/page/"+i));images.add(Boolean.TRUE);}
        }
        @Override public Context readerContext() { return context; }
        @Override public int readerIndex() { return index; }
        @Override public int readerCount() { return uris.size(); }
        @Override public Uri readerUri(int value) {
            if(value<0&&heldCurrentUri!=null)return heldCurrentUri;
            int actual=value<0?index:value;return actual>=0&&actual<uris.size()?uris.get(actual):null;
        }
        @Override public boolean readerImage(int value) {
            if(value<0&&heldCurrentImage!=null)return heldCurrentImage.booleanValue();
            int actual=value<0?index:value;return actual>=0&&actual<images.size()&&images.get(actual).booleanValue();
        }
        @Override public Future<?> readerLoad(int target,Handler handler) {
            if(loadThrows)throw new IllegalStateException("loader start failure");
            Load load=new Load(target,handler);loads.add(load);return returnNull?null:load.future;
        }
        @Override public void readerCommit(Object picture,int target) {
            Frame frame=(Frame)picture;
            yes(frame.ready,"commit must contain decoded pixels");
            yes(frame.uri.equals(readerUri(target)),"commit URI and page identity must agree");
            if(commitReentry)yes(!ReaderMode.beforeDisplay(this,picture),"commit may enter legacy display seam");
            commits++;index=target;heldCurrentUri=null;heldCurrentImage=null;displayed=frame.kind;
        }
        @Override public void readerCancelLegacy() { cancelLegacy++; }
        @Override public boolean readerActive() { return active; }
        @Override public boolean readerSlideshow() { return slideshow; }
        @Override public boolean readerSlideReady() { return slideReady; }
        @Override public void readerStopSlideshow() { stops++;slideshow=false; }
        Load last() { return loads.get(loads.size()-1); }
        Frame frame(int target,String kind) { return new Frame(readerUri(target),true,kind); }
        void prime() { yes(!ReaderMode.beforeDisplay(this,frame(index,"full initial")),"direct full first frame accepted"); }
    }
    private static void unchanged(Host host,int index,String display,String detail) {
        equal(host.index,index,detail+" selected/deletable index");yes(host.displayed.equals(display),detail+" pixels");equal(host.commits,0,detail+" commits");
    }
    private static void toast(int count,String detail) { equal(Toast.shown.size(),count,detail); }
    private static void pendingStopped(Host host,String detail) {
        int before=host.loads.size();ReaderMode.resume(host);equal(host.loads.size(),before+1,detail+" permits a fresh attempt");ReaderMode.cancel(host);
    }
    private static void modeOff() {
        Host host=new Host(3);host.context.readerEnabled=false;
        yes(!ReaderMode.enabled(null),"null context disables mode");
        yes(!ReaderMode.enabled(host.context),"preference defaults off");
        equal(ReaderMode.slideshowRequest(host.context),2,"legacy slideshow request when off");
        yes(!ReaderMode.beforeDisplay(host,new Object()),"mode off preview passes to legacy");
        yes(!ReaderMode.turn(host,1),"mode off turn passes to legacy");ReaderMode.resume(host);
        equal(host.loads.size(),0,"mode off never starts full request");equal(host.commits,0,"mode off no commit");
        host.context.readerEnabled=true;
        yes(ReaderMode.enabled(host.context),"reader preference enables mode");equal(ReaderMode.slideshowRequest(host.context),5,"reader slideshow requests complete frame");
    }
    private static void previewAndNull() {
        Host host=new Host(4);host.commitReentry=true;
        yes(ReaderMode.beforeDisplay(host,null),"null placeholder cannot replace old page");
        equal(host.loads.size(),1,"initial null starts one full decode");
        yes(ReaderMode.beforeDisplay(host,new Object()),"preview suppressed during pending decode");
        equal(host.loads.size(),1,"preview does not duplicate pending request");
        unchanged(host,0,"old page","while full frame pending");
        Load load=host.last();Looper.idleUntil(120);unchanged(host,0,"old page","slow decode");
        Frame full=host.frame(0,"full static");load.finish(full);Looper.drainReady();
        equal(host.commits,1,"full static commits once");equal(full.releases,1,"received full frame reference released");
        yes(host.displayed.equals("full static"),"complete pixels replace old page");
        equal(Looper.queuedCallbacks(),0,"successful decode stops polling");toast(0,"success has no failure toast");
        yes(ReaderMode.beforeDisplay(host,new Object()),"later thumbnail cannot replace visible full frame");
        equal(host.loads.size(),1,"visible full page does not reload");
    }
    private static void directFrames() {
        for(String kind:new String[]{"full static","primed animation"}) {
            Host host=new Host(2);Frame full=host.frame(0,kind);
            yes(!ReaderMode.beforeDisplay(host,full),kind+" passes directly to legacy display");
            equal(host.loads.size(),0,"complete direct frame avoids redundant decode");equal(full.releases,0,"legacy caller owns direct frame");
            yes(ReaderMode.beforeDisplay(host,new Frame(host.readerUri(0),false,"thumbnail")),"thumbnail after full frame suppressed");ReaderMode.cancel(host);
        }
        Host animation=new Host(2);
        yes(ReaderMode.beforeDisplay(animation,new Frame(animation.readerUri(0),false,"unprimed animation")),"unprimed animation waits for decoded first frame");
        unchanged(animation,0,"old page","unprimed animation");ReaderMode.cancel(animation);
        Host wrong=new Host(2);yes(ReaderMode.beforeDisplay(wrong,wrong.frame(1,"wrong page")),"wrong URI cannot pass direct seam");ReaderMode.cancel(wrong);
        Host video=new Host(2);video.images.set(0,Boolean.FALSE);yes(!ReaderMode.beforeDisplay(video,new Object()),"nonimage keeps legacy display");equal(video.loads.size(),0,"nonimage never starts reader decode");
    }
    private static void rapidTurns() {
        Host host=new Host(5);host.prime();host.commitReentry=true;
        yes(ReaderMode.turn(host,1),"first next intercepted");Load first=host.last();equal(first.index,1,"first target");
        yes(ReaderMode.turn(host,1),"second next intercepted");Load second=host.last();equal(second.index,2,"second next accumulates pending target");
        equal(first.future.cancellations,1,"replaced target canceled once");unchanged(host,0,"old page","two rapid next taps");
        Frame old=host.frame(1,"obsolete first"),wanted=host.frame(2,"latest full");first.picture(old,0);second.finish(wanted);Looper.drainReady();
        equal(old.releases,1,"obsolete frame released once");equal(wanted.releases,1,"winning frame reference released once");
        equal(host.commits,1,"only latest target commits");equal(host.index,2,"latest target selected atomically");equal(host.cancelLegacy,2,"each intercepted turn cancels legacy work");
        equal(Looper.queuedReferences(old),0,"obsolete picture removed by delivery, no retained queue reference");
    }
    private static void reverseAndWrap() {
        Host host=new Host(3);host.prime();ReaderMode.turn(host,1);Load first=host.last();
        yes(ReaderMode.turn(host,-1),"reverse back to visible page intercepted");equal(first.future.cancellations,1,"reverse cancels pending target");
        equal(host.loads.size(),1,"reverse to ready visible page needs no decode");unchanged(host,0,"old page","reverse to original page");
        Frame stale=host.frame(1,"reverse obsolete");first.picture(stale,0);Looper.drainReady();equal(stale.releases,1,"reverse obsolete released");
        yes(ReaderMode.turn(host,-1),"previous wraps from first page");equal(host.last().index,2,"reverse wraps to final page");
        ReaderMode.turn(host,-1);equal(host.last().index,1,"second reverse accumulates wrapped pending index");
        ReaderMode.turn(host,1);equal(host.last().index,2,"forward reverses pending reverse target");
        ReaderMode.turn(host,1);equal(host.loads.size(),4,"wrapping back to visible page cancels without fifth decode");
        unchanged(host,0,"old page","wrapped target returns to original");equal(Looper.queuedCallbacks(),0,"all wrapped pending callbacks canceled");
    }
    private static void mixedVideoToImage() {
        final Host host=new Host(3);host.images.set(0,Boolean.FALSE);host.displayed="old video";
        yes(!host.readerImage(-1)&&host.readerImage(1),"per-index types distinguish video from image");
        yes(!ReaderMode.beforeDisplay(host,new Object()),"video without image transaction keeps legacy seam");
        yes(ReaderMode.turn(host,1),"video to image starts full image transaction");
        final Load load=host.last();equal(load.index,1,"image target next to current video");
        final Frame oldVideo=new Frame(host.readerUri(-1),true,"queued old video frame");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try { yes(ReaderMode.beforeDisplay(host,oldVideo),"queued old-video callback cannot replace pending image"); }
                finally { oldVideo.readerRelease(); }
            }
        },10);
        Looper.idleUntil(20);unchanged(host,0,"old video","old video callback while image pending");
        equal(load.future.cancellations,0,"old video callback does not cancel image target");
        equal(host.loads.size(),1,"old video callback does not restart image load");equal(oldVideo.releases,1,"legacy caller releases its blocked video frame");
        host.commitReentry=true;Frame full=host.frame(1,"full image after video");load.finish(full);Looper.drainReady();
        equal(host.commits,1,"full image replaces video once ready");equal(host.index,1,"video to image page identity changes at commit");
        yes(host.displayed.equals("full image after video"),"video to image shows decoded full frame");equal(full.releases,1,"image result released once");
        equal(host.cancelLegacy,1,"new transaction cancels legacy video decode once");toast(0,"queued video callback cannot report image failure");
    }
    private static void reloadAfterRemoval() {
        for(int deletedIndex:new int[]{0,2}) {
            Host host=new Host(3);host.index=deletedIndex;host.images.set(deletedIndex,Boolean.FALSE);host.displayed="deleted video still visible";
            Uri deletedUri=host.readerUri(-1);host.heldCurrentUri=deletedUri;host.heldCurrentImage=Boolean.FALSE;
            host.uris.remove(deletedIndex);host.images.remove(deletedIndex);
            int target=Math.min(deletedIndex,host.readerCount()-1);Uri wanted=host.readerUri(target);
            yes(!deletedUri.equals(wanted),"removal leaves a distinct neighboring image target");
            yes(!host.readerImage(-1)&&host.readerImage(target),"deleted current video and settled image target have separate types");
            ReaderMode.reloadTarget(host,target);equal(host.loads.size(),1,"post-removal reload starts one full image decode despite old video");Load load=host.last();equal(load.index,target,"reload uses settled post-removal index");
            equal(host.cancelLegacy,1,"post-removal transaction cancels legacy work");
            yes(host.readerUri(-1).equals(deletedUri),"reload preserves deleted page URI until full frame ready");
            unchanged(host,deletedIndex,"deleted video still visible","post-removal image decode pending");
            yes(ReaderMode.beforeDisplay(host,new Object()),"old deleted-video callback cannot cancel image reload");
            equal(load.future.cancellations,0,"reload survives old current video callback");
            ReaderMode.resume(host);equal(host.loads.size(),1,"resume does not duplicate settled target load");
            Looper.idleUntil(Looper.now()+100);yes(host.readerUri(-1).equals(deletedUri),"slow target decode keeps old URI");
            unchanged(host,deletedIndex,"deleted video still visible","post-removal slow image decode");
            host.commitReentry=true;Frame full=host.frame(target,"full image after removal");load.finish(full);Looper.drainReady();
            equal(host.commits,1,"post-removal full frame commits once");equal(host.index,target,"post-removal identity updates with pixels");
            yes(host.readerUri(-1).equals(wanted),"post-removal commit adopts neighboring image URI");
            yes(host.displayed.equals("full image after removal"),"post-removal commit adopts full pixels");
            equal(full.releases,1,"post-removal result released once");toast(0,"video to image reload has no false error");ReaderMode.cancel(host);
        }
    }
    private static void reloadVideoTargetCancels() {
        Host direct=new Host(3);direct.images.set(1,Boolean.FALSE);ReaderMode.reloadTarget(direct,1);
        equal(direct.loads.size(),0,"video reload target keeps legacy loader");equal(direct.cancelLegacy,0,"video reload no-op does not cancel legacy work");
        unchanged(direct,0,"old page","video reload target no-op");
        Host pending=new Host(3);pending.images.set(2,Boolean.FALSE);pending.prime();ReaderMode.turn(pending,1);
        Load load=pending.last();Frame stale=pending.frame(1,"image before video target");load.picture(stale,25);
        ReaderMode.reloadTarget(pending,2);equal(load.future.cancellations,1,"settled video target cancels old image transaction");
        equal(pending.loads.size(),1,"settled video target adds no image load");equal(Looper.queuedMessages(30),1,"video target preserves retained old image result for release");
        Looper.idleUntil(100);equal(stale.releases,1,"image canceled by video target released once");
        unchanged(pending,0,"old page","settled video target canceled image");equal(Looper.queuedCallbacks(),0,"video target removes image poll");toast(0,"video target cancellation has no error");
    }
    private static void reloadDisabledOrPaused() {
        for(boolean paused:new boolean[]{false,true}) {
            Host initial=new Host(2);if(paused)initial.active=false;else initial.context.readerEnabled=false;
            ReaderMode.reloadTarget(initial,1);equal(initial.loads.size(),0,"disabled/paused reload does not start decoder");
            unchanged(initial,0,"old page","disabled/paused initial reload");
            Host pending=new Host(2);ReaderMode.reloadTarget(pending,1);Load load=pending.last();Frame stale=pending.frame(1,"reload invalidated by lifecycle");load.picture(stale,25);
            if(paused)pending.active=false;else pending.context.readerEnabled=false;
            ReaderMode.reloadTarget(pending,1);equal(load.future.cancellations,1,"disabled/paused reload cancels pending transaction");
            equal(pending.loads.size(),1,"disabled/paused reload does not restart work");
            Looper.idleUntil(Looper.now()+100);equal(stale.releases,1,"disabled/paused reload retained result released");
            unchanged(pending,0,"old page","disabled/paused reload completion");equal(Looper.queuedCallbacks(),0,"disabled/paused reload leaves no poll");toast(0,"disabled/paused reload has no false error");
        }
    }
    private static void cancellationOwnership() {
        Host host=new Host(3);host.prime();ReaderMode.turn(host,1);Load load=host.last();Frame full=host.frame(1,"queued canceled");load.picture(full,25);
        equal(Looper.queuedReferences(full),1,"loader retains picture in message30");ReaderMode.cancel(host);
        equal(load.future.cancellations,1,"explicit cancel cancels Future once");equal(Looper.queuedMessages(30),1,"cancel preserves message30 for release");
        Looper.idleUntil(100);equal(full.releases,1,"canceled retained picture released exactly once");
        equal(Looper.queuedReferences(full),0,"delivered canceled picture has no queue reference");equal(Looper.queuedCallbacks(),0,"cancel removes polling callbacks");
        unchanged(host,0,"old page","cancel result");toast(0,"explicit cancel does not report failure");
    }
    private static void invalidatedResults() {
        for(int reason=0;reason<6;reason++) {
            Host host=new Host(4);host.prime();ReaderMode.turn(host,1);Load load=host.last();Frame frame=host.frame(1,"invalidated "+reason);load.picture(frame,50);load.future.finish();
            if(reason==0)host.context.readerEnabled=false;
            if(reason==1)host.active=false;
            if(reason==2)host.slideshow=true;
            if(reason==3)host.index=2;
            if(reason==4)host.uris.set(0,Uri.parse("content://public-test/replaced-base"));
            if(reason==5)host.uris.set(1,Uri.parse("content://public-test/replaced-target"));
            Looper.idleUntil(Looper.now()+100);equal(host.commits,0,"invalidated result cannot commit reason="+reason);equal(frame.releases,1,"invalidated result released reason="+reason);
            equal(load.future.cancellations,1,"invalidated request canceled reason="+reason);equal(Looper.queuedCallbacks(),0,"invalidated poll removed reason="+reason);toast(0,"invalidation has no false error");ReaderMode.cancel(host);
        }
    }
    private static final class GcCase {
        final WeakReference<Host> host;final Load load;final Frame frame;
        GcCase(WeakReference<Host> host,Load load,Frame frame) { this.host=host;this.load=load;this.frame=frame; }
    }
    private static GcCase unreachablePendingHost() {
        Host host=new Host(3);host.prime();ReaderMode.turn(host,1);Load load=host.last();Frame frame=host.frame(1,"orphan full");load.picture(frame,50);
        return new GcCase(new WeakReference<Host>(host),load,frame);
    }
    private static void collect(WeakReference<?> reference) throws InterruptedException {
        for(int attempt=0;attempt<60&&reference.get()!=null;attempt++) { System.gc();Thread.sleep(5); }
        yes(reference.get()==null,"pending queue and state must not keep host alive");
    }
    private static void collectedHost() throws Exception {
        GcCase orphan=unreachablePendingHost();collect(orphan.host);Looper.idleUntil(100);
        equal(orphan.frame.releases,1,"orphan message still releases retained picture");equal(orphan.load.future.cancellations,1,"orphan request canceled");equal(Looper.queuedCallbacks(),0,"orphan polling terminates");toast(0,"orphan host has no failure toast");
    }
    private static void futureWithoutPicture() {
        Host host=new Host(2);ReaderMode.resume(host);Load load=host.last();load.future.finish();Looper.idleUntil(120);
        unchanged(host,0,"old page","Future done without message30");toast(1,"missing result stops pending with one error");
        yes(Toast.shown.get(0).equals("Could not load image"),"English failure text");equal(Looper.queuedCallbacks(),0,"missing result does not poll forever");pendingStopped(host,"missing result");
    }
    private static void errorAndException() {
        Host error=new Host(2);ReaderMode.resume(error);Load load=error.last();load.handler.sendEmptyMessage(31);load.handler.sendEmptyMessage(30);load.future.finish();Looper.idleUntil(120);
        equal(error.commits,0,"error message never commits");toast(1,"error completion produces one failure");pendingStopped(error,"error message");
        Toast.shown.clear();Host exception=new Host(2);exception.context.getResources().getConfiguration().locale=Locale.TRADITIONAL_CHINESE;
        ReaderMode.resume(exception);exception.last().future.fail();Looper.idleUntil(Looper.now()+120);toast(1,"Future exception reports failure");
        yes(Toast.shown.get(0).equals("圖片載入失敗"),"Chinese failure text");equal(exception.commits,0,"Future exception keeps old pixels");pendingStopped(exception,"Future exception");
        Toast.shown.clear();Host nullFuture=new Host(2);nullFuture.returnNull=true;ReaderMode.resume(nullFuture);toast(1,"null Future terminates immediately");equal(Looper.queuedCallbacks(),0,"null Future leaves no polling");
        Toast.shown.clear();Host startException=new Host(2);startException.loadThrows=true;ReaderMode.resume(startException);toast(1,"loader start exception terminates immediately");equal(Looper.queuedCallbacks(),0,"start exception leaves no polling");
    }
    private static void badFullResult() {
        for(boolean wrongUri:new boolean[]{false,true}) {
            Host host=new Host(3);ReaderMode.resume(host);Frame bad=new Frame(host.readerUri(wrongUri?1:0),wrongUri,"invalid full");host.last().finish(bad);Looper.drainReady();
            equal(host.commits,0,"incomplete/mismatched full frame rejected");equal(bad.releases,1,"invalid full reference released");toast(wrongUri?2:1,"invalid frame reports failure once");equal(Looper.queuedCallbacks(),0,"invalid full terminates pending");ReaderMode.cancel(host);
        }
    }
    private static void queuedCompletionRace() {
        Host host=new Host(3);host.prime();ReaderMode.turn(host,1);Load load=host.last();Frame frame=host.frame(1,"race winner");load.picture(frame,50);load.future.finish();
        // The poll was due at 40. Message30 was due at 50. Neither can run until 60.
        // Android dispatches poll at wall time 60: its posted retry is due at 60,
        // behind the retained picture due at 50, even though the poll runs first.
        Looper.blockUntil(60);Looper.drainReady();
        equal(host.commits,1,"already queued full result wins over done-Future failure poll");equal(host.index,1,"race commits correct target");equal(frame.releases,1,"race frame released once");toast(0,"blocked-main ordering cannot create false failure");equal(Looper.queuedCallbacks(),0,"race success removes retry");
    }
    private static void supersededQueueOwnership() {
        Host host=new Host(4);host.prime();ReaderMode.turn(host,1);Load old=host.last();Frame oldFrame=host.frame(1,"old queued");old.picture(oldFrame,50);old.future.finish();
        ReaderMode.turn(host,1);Load latest=host.last();Frame newFrame=host.frame(2,"new queued");latest.picture(newFrame,60);latest.future.finish();
        Looper.blockUntil(80);Looper.drainReady();equal(oldFrame.releases,1,"superseded queued full frame released");equal(newFrame.releases,1,"latest queued full frame released");equal(host.commits,1,"stale and valid queued messages commit only once");equal(host.index,2,"pending accumulation survives blocked queue");toast(0,"superseded result has no false error");
    }
    private static void slideshowWatch() {
        Host host=new Host(3);host.slideshow=true;ControlledFuture future=new ControlledFuture();ReaderMode.watchSlide(host,future);Looper.idleUntil(120);
        equal(host.stops,0,"unfinished slideshow future keeps watching");equal(Looper.queuedCallbacks(),1,"unfinished slideshow retains only next watch");
        future.finish();host.slideReady=true;Looper.idleUntil(200);equal(host.stops,0,"ready slide does not fail");toast(0,"ready slide has no error");equal(Looper.queuedCallbacks(),0,"ready slide stops watching");
    }
    private static void slideshowReceivedAndCancel() {
        for(boolean cancel:new boolean[]{false,true}) {
            Host host=new Host(2);host.slideshow=true;ControlledFuture future=new ControlledFuture();ReaderMode.watchSlide(host,future);future.finish();
            if(cancel)ReaderMode.cancel(host);else ReaderMode.slideReceived(host);
            Looper.idleUntil(Looper.now()+100);equal(host.stops,0,"received/canceled watch cannot stop slideshow");toast(0,"received/canceled watch has no error");equal(Looper.queuedCallbacks(),0,"received/canceled watch drains");ReaderMode.cancel(host);
        }
        Host host=new Host(2);host.slideshow=true;ControlledFuture old=new ControlledFuture(),current=new ControlledFuture();ReaderMode.watchSlide(host,old);ReaderMode.watchSlide(host,current);old.finish();
        Looper.idleUntil(Looper.now()+100);equal(host.stops,0,"older generation cannot fail current slideshow");ReaderMode.slideReceived(host);current.finish();Looper.idleUntil(Looper.now()+100);toast(0,"superseded watch has no failure toast");
    }
    private static void slideshowFailure() {
        for(boolean failed:new boolean[]{false,true}) {
            Host host=new Host(2);host.slideshow=true;ControlledFuture future=new ControlledFuture();ReaderMode.watchSlide(host,future);if(failed)future.fail();else future.finish();
            Looper.idleUntil(Looper.now()+120);equal(host.stops,1,"completed slideshow without full frame stops");toast(failed?2:1,"slideshow missing/error result reports once");equal(Looper.queuedCallbacks(),0,"failed slideshow watch terminates");ReaderMode.cancel(host);
        }
    }
    private static void slideshowQueuedCompletionRace() {
        final Host host=new Host(3);host.slideshow=true;
        ControlledFuture future=new ControlledFuture();ReaderMode.watchSlide(host,future);
        final Frame frame=host.frame(1,"queued full slideshow");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                ReaderMode.slideReceived(host);ReaderMode.commitSlide(host,frame,1);host.slideReady=true;
            }
        },50);
        future.finish();Looper.blockUntil(60);Looper.drainReady();
        equal(host.commits,1,"queued slideshow result precedes done-Future retry");
        equal(host.stops,0,"blocked-main queue cannot stop valid slideshow");
        equal(host.index,1,"queued slide selects its full frame");toast(0,"queued slide has no false error");
        equal(Looper.queuedCallbacks(),0,"received slideshow invalidates posted completion retry");
    }
    private static void slideshowInvalidation() {
        for(int reason=0;reason<3;reason++) {
            Host host=new Host(2);host.slideshow=true;ControlledFuture future=new ControlledFuture();ReaderMode.watchSlide(host,future);future.finish();
            if(reason==0)host.context.readerEnabled=false;if(reason==1)host.active=false;if(reason==2)host.slideshow=false;
            Looper.idleUntil(Looper.now()+100);equal(host.stops,0,"disabled/paused/stopped slideshow cannot be failed again");toast(0,"invalidated slideshow has no error");equal(Looper.queuedCallbacks(),0,"invalidated slideshow watch terminates");ReaderMode.cancel(host);
        }
    }
    private static void slideshowCommitSafeguards() {
        Host host=new Host(3);host.slideshow=true;host.commitReentry=true;
        ReaderMode.commitSlide(host,new Object(),1);equal(host.commits,0,"nonframe cannot commit slideshow");
        Frame incomplete=new Frame(host.readerUri(1),false,"unprimed slide");ReaderMode.commitSlide(host,incomplete,1);equal(host.commits,0,"unprimed slideshow cannot commit");
        Frame wrong=host.frame(2,"wrong slide");ReaderMode.commitSlide(host,wrong,1);equal(host.commits,0,"slideshow mismatched URI rejected");
        Frame full=host.frame(1,"primed animation slide");host.context.readerEnabled=false;ReaderMode.commitSlide(host,full,1);equal(host.commits,0,"disabled mode cannot commit slide");
        host.context.readerEnabled=true;host.active=false;ReaderMode.commitSlide(host,full,1);equal(host.commits,0,"paused host cannot commit slide");
        host.active=true;host.slideshow=false;ReaderMode.commitSlide(host,full,1);equal(host.commits,0,"stopped slideshow cannot commit slide");
        host.slideshow=true;ReaderMode.commitSlide(host,full,1);equal(host.commits,1,"primed valid slideshow commits once");equal(host.index,1,"slideshow pixels and index advance together");equal(full.releases,0,"slideshow caller retains ownership until its seam releases");
        equal(incomplete.releases,0,"rejected slideshow caller retains ownership");equal(wrong.releases,0,"wrong slideshow caller retains ownership");
    }
    private static void slideReplacesManualPending() {
        Host host=new Host(3);host.prime();ReaderMode.turn(host,1);Load manual=host.last();Frame stale=host.frame(1,"manual before slideshow");manual.picture(stale,50);
        host.slideshow=true;ControlledFuture slide=new ControlledFuture();ReaderMode.watchSlide(host,slide);equal(manual.future.cancellations,1,"slideshow start cancels manual pending");
        Frame full=host.frame(2,"full slide");ReaderMode.commitSlide(host,full,2);ReaderMode.slideReceived(host);slide.finish();Looper.idleUntil(100);
        equal(stale.releases,1,"manual message released after slide takes over");equal(host.commits,1,"manual result cannot overwrite slideshow");equal(host.index,2,"slideshow target remains selected");toast(0,"valid slide suppresses missing-frame failure");
    }
    private static WeakReference<Host> unreachableSlideHost() {
        Host host=new Host(2);host.slideshow=true;ReaderMode.watchSlide(host,new ControlledFuture());return new WeakReference<Host>(host);
    }
    private static void collectedSlideHost() throws Exception {
        WeakReference<Host> weak=unreachableSlideHost();collect(weak);Looper.idleUntil(100);equal(Looper.queuedCallbacks(),0,"collected slideshow host terminates watcher");toast(0,"collected slideshow host has no error");
    }
    public static void main(String[] args) {
        scenario("mode_off_passthrough",()->modeOff());
        scenario("null_preview_keep_old_page_until_full",()->previewAndNull());
        scenario("direct_static_and_primed_animation",()->directFrames());
        scenario("rapid_next_accumulates_pending_index",()->rapidTurns());
        scenario("reverse_and_wrap_accumulate_pending_index",()->reverseAndWrap());
        scenario("mixed_video_to_image_survives_old_video_callback",()->mixedVideoToImage());
        scenario("reload_after_removal_preserves_old_uri_until_commit",()->reloadAfterRemoval());
        scenario("reload_video_target_noop_and_cancel",()->reloadVideoTargetCancels());
        scenario("reload_mode_off_and_pause_cancel",()->reloadDisabledOrPaused());
        scenario("cancel_keeps_message30_and_releases_once",()->cancellationOwnership());
        scenario("mode_pause_slide_dataset_and_index_invalidation",()->invalidatedResults());
        scenario("collected_host_releases_result",()->collectedHost());
        scenario("done_future_without_picture_fails",()->futureWithoutPicture());
        scenario("error_exception_null_and_start_failure",()->errorAndException());
        scenario("incomplete_or_mismatched_full_result_fails",()->badFullResult());
        scenario("blocked_main_poll40_message50_unblock60",()->queuedCompletionRace());
        scenario("superseded_queued_results_release_once",()->supersededQueueOwnership());
        scenario("slideshow_watch_and_ready",()->slideshowWatch());
        scenario("slideshow_received_cancel_and_generation",()->slideshowReceivedAndCancel());
        scenario("slideshow_no_result_and_exception_fail",()->slideshowFailure());
        scenario("slideshow_blocked_main_poll40_result50_unblock60",()->slideshowQueuedCompletionRace());
        scenario("slideshow_mode_pause_stop_invalidation",()->slideshowInvalidation());
        scenario("slideshow_commit_safeguards",()->slideshowCommitSafeguards());
        scenario("slideshow_replaces_manual_pending",()->slideReplacesManualPending());
        scenario("collected_slideshow_host_terminates_watch",()->collectedSlideHost());
        System.out.println("COUNTS scenarios="+results.size()+" checks="+checks+" failed_scenarios="+failedCases);
        System.out.println("SCOPE actual ReaderMode.java; controlled Android queue/decoder doubles; not Android UI, DEX or codec playback.");
        StringBuilder joined=new StringBuilder();for(String result:results){if(joined.length()>0)joined.append(',');joined.append(result);}
        System.out.println("RESULT_JSON {\"result\":\""+(failedCases==0?"PASS":"FAIL")+"\",\"scenarios\":"+results.size()+",\"checks\":"+checks+",\"failed_scenarios\":"+failedCases+",\"cases\":["+joined+"]}");
        if(failedCases>0)System.exit(1);
    }
}
