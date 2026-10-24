// SPDX-License-Identifier: MIT
package android.os;
public final class Handler {
    public interface Callback { boolean handleMessage(Message message); }
    private final Callback callback;
    public Handler() { this(Looper.getMainLooper(),null); }
    public Handler(Looper looper) { this(looper,null); }
    public Handler(Looper looper, Callback callback) { this.callback=callback; }
    public boolean post(Runnable runnable) { return postDelayed(runnable,0); }
    public boolean postDelayed(Runnable runnable, long delay) { Looper.enqueue(this,runnable,null,delay); return true; }
    public void removeCallbacks(Runnable runnable) { Looper.remove(this,runnable,false,0); }
    public void removeMessages(int what) { Looper.remove(this,null,true,what); }
    public Message obtainMessage(int what,Object obj) { return Message.obtain(this,what,obj); }
    public boolean sendMessage(Message message) { return sendMessageDelayed(message,0); }
    public boolean sendMessageDelayed(Message message,long delay) { message.target=this; Looper.enqueue(this,null,message,delay); return true; }
    public boolean sendEmptyMessage(int what) { return sendMessage(obtainMessage(what,null)); }
    public boolean hasMessages(int what) { return Looper.hasMessage(this,what); }
    void dispatch(Message message) { if(callback!=null) callback.handleMessage(message); }
}
