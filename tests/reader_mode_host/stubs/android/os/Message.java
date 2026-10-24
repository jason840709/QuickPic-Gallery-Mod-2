// SPDX-License-Identifier: MIT
package android.os;
public final class Message {
    public int what;
    public Object obj;
    Handler target;
    public static Message obtain(Handler target, int what, Object obj) {
        Message message=new Message(); message.target=target; message.what=what; message.obj=obj; return message;
    }
    public void sendToTarget() { target.sendMessage(this); }
}
