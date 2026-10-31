// SPDX-License-Identifier: MIT
package com.alensw.jni;
import android.view.View;
import android.view.MotionEvent;

public final class NavigationProbe {
    static int checks;
    static class Target implements TapPageNavigation.Host {
        int turns,toggles,direction,hits;boolean ready=true,blocked=false,toggle=true;
        public boolean quickpicTapReady(){return ready;}
        public boolean quickpicTapControlsHit(float x,float y){hits++;return blocked;}
        public void quickpicTapTurn(int d){turns++;direction=d;}
        public boolean quickpicTapToggleControls(){toggles++;return toggle;}
    }
    static void expect(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static boolean tap(View v,Target h,float x,float y){return TapPageNavigation.onConfirmedTap(v,h,x,y);}
    public static void main(String[] args){
        View view=new View();Target host=new Target();
        expect(!tap(null,host,10,10),"null view");expect(!tap(view,null,10,10),"null host");
        TapPagePreference.mode=0;
        expect(!tap(view,host,10,900),"disabled yields original action");
        expect(host.turns==0&&host.toggles==0&&host.hits==0,"disabled has no host effects");
        TapPagePreference.mode=1;TapPageLayout.result=-1;
        expect(tap(view,host,10,900),"previous handled");expect(host.turns==1&&host.direction==-1&&host.toggles==0,"previous once");
        host=new Target();TapPageLayout.result=1;
        expect(tap(view,host,890,900),"next handled");expect(host.turns==1&&host.direction==1&&host.toggles==0,"next once");
        host=new Target();TapPageLayout.result=0;
        expect(tap(view,host,450,600),"center handled");expect(host.toggles==1&&host.turns==0,"center uses native control toggle");
        host=new Target();host.blocked=true;
        expect(!tap(view,host,450,10),"toolbar keeps original dispatch");expect(host.hits==1&&host.turns==0&&host.toggles==0,"toolbar does not navigate");
        host=new Target();view.focus=false;int reads=TapPagePreference.reads;
        expect(!tap(view,host,10,900),"popup focus blocks queued tap");expect(TapPagePreference.reads==reads&&host.hits==0,"unfocused has no effects");
        view.focus=true;view.shown=false;expect(!tap(view,host,10,900),"detached/hidden view");view.shown=true;
        host.ready=false;expect(!tap(view,host,10,900),"missing page listener");host.ready=true;
        float[][] invalid={{-1,1},{1,-1},{900,1},{1,1200},{Float.NaN,1},{1,Float.POSITIVE_INFINITY}};
        for(float[] p:invalid)expect(!tap(view,host,p[0],p[1]),"invalid tap coordinate");
        expect(host.turns==0&&host.toggles==0&&host.hits==0,"invalid coordinates have no host effects");
        view.width=0;expect(!tap(view,host,1,1),"unlaid out view");view.width=900;
        TapPagePreference.mode=8;expect(!tap(view,host,1,1),"unknown layout");
        TapPagePreference.mode=1;TapPagePreference.fail=true;expect(!tap(view,host,1,1),"preference failure keeps original action");TapPagePreference.fail=false;
        host=new Target();host.toggle=false;expect(!tap(view,host,450,600),"unavailable controls keep original action");
        host=new Target();TapPageLayout.result=1;
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_DOWN,1));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_POINTER_DOWN,2));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_POINTER_UP,2));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_UP,1));
        expect(!tap(view,host,890,900),"small-span multi-touch blocked after final UP");
        expect(host.turns==0&&host.toggles==0,"multi-touch has no custom action");
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_DOWN,1));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_UP,1));
        expect(tap(view,host,890,900)&&host.turns==1,"next normal gesture resets blocked flag");
        host=new Target();TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_DOWN,1));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_CANCEL,1));
        expect(!tap(view,host,890,900)&&host.turns==0,"cancel blocks delayed custom action");
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_DOWN,1));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_MOVE,2));
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_UP,1));
        expect(!tap(view,host,890,900),"any multi-pointer event blocks gesture");
        View other=new View();Target otherHost=new Target();
        expect(tap(other,otherHost,890,900)&&otherHost.turns==1,"state is per view");
        host=new Target();TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_DOWN,1));
        MotionEvent release=new MotionEvent(MotionEvent.ACTION_UP,1);TapPageLayout.result=1;
        expect(TapPageNavigation.onTapRelease(view,host,release)&&host.turns==1,"validated release pages immediately");
        host=new Target();TapPageLayout.result=0;
        expect(!TapPageNavigation.onTapRelease(view,host,release)&&host.toggles==0,"release in menu area keeps original confirmation");
        TapPageLayout.result=1;release.duration=600;
        expect(!TapPageNavigation.onTapRelease(view,host,release)&&host.turns==0,"long press does not page");expect(!tap(view,host,890,900)&&host.turns==0,"delayed confirmation cannot revive a rejected long press");release.duration=60;
        expect(!TapPageNavigation.onTapRelease(view,host,new MotionEvent(MotionEvent.ACTION_DOWN,1)),"down cannot page");
        expect(!TapPageNavigation.onTapRelease(view,host,new MotionEvent(MotionEvent.ACTION_UP,2)),"multi-pointer release cannot page");
        TapPageNavigation.track(view,new MotionEvent(MotionEvent.ACTION_CANCEL,1));
        expect(!TapPageNavigation.onTapRelease(view,host,release)&&host.turns==0,"cancelled release cannot page");
        System.out.println("PASS "+checks+" production tap routing checks; no Android gesture simulation");
    }
}
