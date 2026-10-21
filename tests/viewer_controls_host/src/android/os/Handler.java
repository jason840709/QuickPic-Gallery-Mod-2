package android.os;
public class Handler {
 public Runnable queued;
 public long delay;
 public int removes,posts,checks;
 public boolean hasCallbacks(Runnable r){checks++;return queued==r;}
 public void removeCallbacks(Runnable r){removes++;if(queued==r)queued=null;}
 public boolean postDelayed(Runnable r,long value){posts++;queued=r;delay=value;return true;}
}
