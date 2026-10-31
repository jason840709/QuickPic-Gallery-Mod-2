// SPDX-License-Identifier: MIT
package com.alensw.jni;
import android.content.Context;import android.graphics.Bitmap;import android.graphics.BitmapFactory;import android.graphics.BitmapRegionDecoder;import android.graphics.Rect;import android.net.Uri;import java.io.FileInputStream;import java.io.InputStream;
/** Avoid point-sampled decoder reduction before the selected resampling filter. */
public final class EinkDecode{
 private EinkDecode(){}
 public static Bitmap decode(Uri uri,BitmapFactory.Options options){
  int limit=EinkRender.decodeLimit();Context context=EinkRender.applicationContext();
  if(limit==0||uri==null||context==null||options==null||options.inJustDecodeBounds||options.mCancel)return null;
  // Ordinary-sized images keep the original full-resolution decoder.
  if((long)options.outWidth*options.outHeight<=limit)return null;
  BitmapRegionDecoder decoder=null;BandRows rows=null;Bitmap output=null;
  try(InputStream input="file".equals(uri.getScheme())||uri.getScheme()==null?new FileInputStream(uri.getPath()):context.getContentResolver().openInputStream(uri)){
   if(input==null)return null;decoder=BitmapRegionDecoder.newInstance(input,false);if(decoder==null)return null;
   int sw=decoder.getWidth(),sh=decoder.getHeight();if(sw>65536||sh>65536)return null;double scale=Math.min(1,Math.sqrt((double)limit/((long)sw*sh)));
   int dw=Math.max(1,(int)Math.floor(sw*scale)),dh=Math.max(1,(int)Math.floor(sh*scale));
   output=Bitmap.createBitmap(dw,dh,Bitmap.Config.ARGB_8888);final Bitmap target=output;
   rows=new BandRows(decoder,options);EinkResample.scale(rows,new EinkResample.Rows(){public int width(){return target.getWidth();}public int height(){return target.getHeight();}public void read(int y,int[] p){throw new UnsupportedOperationException();}public void write(int y,int[] p){if(options.mCancel)throw new IllegalStateException("Decode cancelled");target.setPixels(p,0,target.getWidth(),0,y,target.getWidth(),1);}},EinkRender.selectedMode()==2);
   return output;
  }catch(Exception e){if(output!=null)output.recycle();return null;}catch(OutOfMemoryError e){if(output!=null)output.recycle();return null;}
  finally{if(rows!=null)rows.close();if(decoder!=null)decoder.recycle();}
 }
 private static final class BandRows implements EinkResample.Rows{
  final BitmapRegionDecoder decoder;final BitmapFactory.Options cancellation;final int width,height,bandHeight;Bitmap band;int start=-1,end;
  BandRows(BitmapRegionDecoder d,BitmapFactory.Options o){decoder=d;cancellation=o;width=d.getWidth();height=d.getHeight();bandHeight=Math.max(1,Math.min(64,524288/Math.max(1,width)));}
  public int width(){return width;}public int height(){return height;}
  public void read(int y,int[] pixels){
   if(cancellation.mCancel)throw new IllegalStateException("Decode cancelled");
   if(band==null||y<start||y>=end){close();start=(y/bandHeight)*bandHeight;end=Math.min(height,start+bandHeight);BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;o.inPreferredConfig=Bitmap.Config.ARGB_8888;band=decoder.decodeRegion(new Rect(0,start,width,end),o);if(band==null||band.getWidth()!=width)throw new IllegalStateException("No full-resolution image band");}
   band.getPixels(pixels,0,width,0,y-start,width,1);
  }
  public void write(int y,int[] pixels){throw new UnsupportedOperationException();}
  void close(){if(band!=null){band.recycle();band=null;}}
 }
}
