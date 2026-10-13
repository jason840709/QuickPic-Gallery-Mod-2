package android.graphics;
/** Geometric / source-over mathematical model, not Android raster or color management. */
public final class Canvas {
 private final Bitmap bitmap; public Canvas(Bitmap b){if(b.isRecycled())throw new IllegalStateException();bitmap=b;}
 public void drawColor(int color,PorterDuff.Mode mode){drawRect(0,0,bitmap.getWidth(),bitmap.getHeight(),paint(color,mode));}
 private static Paint paint(int c,PorterDuff.Mode m){Paint p=new Paint();p.color=c;p.setXfermode(new PorterDuffXfermode(m));return p;}
 public void drawRect(float l,float t,float r,float b,Paint p){for(int y=Math.max(0,(int)t);y<Math.min(bitmap.getHeight(),(int)b);y++)for(int x=Math.max(0,(int)l);x<Math.min(bitmap.getWidth(),(int)r);x++)put(x,y,p.color,p);}
 private void put(int x,int y,int color,Paint p){int i=y*bitmap.getWidth()+x;PorterDuff.Mode m=p==null||p.xfermode==null?PorterDuff.Mode.SRC_OVER:p.xfermode.mode;bitmap.pixels[i]=m==PorterDuff.Mode.CLEAR?0:m==PorterDuff.Mode.SRC?color:blend(color,bitmap.pixels[i]);}
 private static int blend(int s,int d){int a=s>>>24,da=d>>>24;if(a==255)return s;if(a==0)return d;double oa=a+da*(255-a)/255.0;if(oa==0)return 0;int v=((int)Math.round(oa))<<24;for(int shift=0;shift<=16;shift+=8){double c=(((s>>>shift)&255)*a+((d>>>shift)&255)*da*(255-a)/255.0)/oa;v|=((int)Math.round(c))<<shift;}return v;}
 public void drawBitmap(Bitmap b,float x,float y,Paint p){drawBitmap(b,null,new Rect((int)x,(int)y,(int)x+b.getWidth(),(int)y+b.getHeight()),p);}
 public void drawBitmap(Bitmap b,Rect source,Rect target,Paint p){if(b.isRecycled())throw new IllegalStateException();int tw=target.right-target.left,th=target.bottom-target.top;for(int y=0;y<th;y++)for(int x=0;x<tw;x++){int dx=x+target.left,dy=y+target.top;if(dx>=0&&dy>=0&&dx<bitmap.getWidth()&&dy<bitmap.getHeight())put(dx,dy,b.pixels[(y*b.getHeight()/th)*b.getWidth()+x*b.getWidth()/tw],p);}}
}
