// SPDX-License-Identifier: MIT
package com.alensw.jni;

import java.io.IOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import java.util.ArrayList;

/** RIFF animation metadata and compressed ANMF ranges. No native dependencies.
 * Limits match the existing GIF backend. Tiny delays use its <=10ms/100ms policy.
 * This class owns its input bytes; callers must not mutate them after parsing.
 */
final class WebPAnimation {
    static final int MAX_BYTES = 64 * 1024 * 1024;
    static final int MAX_PIXELS = 16 * 1024 * 1024;
    static final int MAX_FRAMES = 10000;
    final int width, height, background, loopCount, duration;
    final int[] delays, starts;
    final Frame[] frames;
    private byte[] data;
    private Bitmap composition;
    private int composedFrame = -1;
    private final int profileOffset, profileLength;

    static final class Frame {
        final int x, y, width, height, delay, offset, length;
        final boolean dispose, noBlend, alphaChunk, alpha;
        Frame(int x, int y, int w, int h, int delay, int flags, int offset, int length,
                boolean alphaChunk, boolean alpha) {
            this.x=x; this.y=y; width=w; height=h; this.delay=delay;
            dispose=(flags & 1)!=0; noBlend=(flags & 2)!=0;
            this.offset=offset; this.length=length; this.alphaChunk=alphaChunk; this.alpha=alpha;
        }
    }
    private WebPAnimation(byte[] data, int w, int h, int background, int loops,
            ArrayList<Frame> list, int profileOffset, int profileLength) throws IOException {
        this.data=data; width=w; height=h; this.background=background; loopCount=loops;
        this.profileOffset=profileOffset; this.profileLength=profileLength;
        frames=list.toArray(new Frame[list.size()]);
        delays=new int[frames.length]; starts=new int[frames.length];
        long total=0;
        for(int i=0;i<frames.length;i++) {
            starts[i]=(int)total; delays[i]=frames[i].delay; total+=delays[i];
            if(total>Integer.MAX_VALUE) throw invalid("duration overflow");
        }
        duration=(int)total;
    }
    static boolean isWebP(byte[] data) {
        return data!=null && data.length>=12 && tag(data,0,"RIFF") && tag(data,8,"WEBP");
    }
    /** Header prefilter only; full parse still validates every animated container. */
    static boolean hasAnimationFlag(byte[] prefix) {
        return isWebP(prefix) && prefix.length >= 30 && tag(prefix,12,"VP8X") && (prefix[20]&2)!=0;
    }
    static WebPAnimation parse(byte[] data) throws IOException {
        if(!isWebP(data) || data.length>MAX_BYTES || data.length<30)
            throw invalid("not a supported animation container");
        long declared=u32(data,4)+8;
        if(declared!=data.length) throw invalid("RIFF length mismatch");
        int w=0,h=0,background=0,loops=0,profileOffset=0,profileLength=0;
        boolean vp8x=false,anim=false,profileFlag=false,profile=false;
        ArrayList<Frame> frames=new ArrayList<Frame>();
        for(int p=12;p<data.length;) {
            int end=chunkEnd(data,p,data.length),size=(int)u32(data,p+4),start=p+8;
            if(tag(data,p,"VP8X")) {
                if(vp8x || p!=12 || size!=10) throw invalid("invalid VP8X");
                vp8x=true; int flags=data[start]&255;
                if((flags&2)==0) throw invalid("not animated");
                profileFlag=(flags&32)!=0;
                w=u24(data,start+4)+1;h=u24(data,start+7)+1;
                if((long)w*h>MAX_PIXELS) throw invalid("canvas too large");
            } else if(tag(data,p,"ICCP")) {
                if(!vp8x || anim || profile || size==0) throw invalid("invalid ICC profile position");
                profile=true;profileOffset=p;profileLength=end-p;
            } else if(tag(data,p,"ANIM")) {
                if(!vp8x || anim || !frames.isEmpty() || size!=6) throw invalid("invalid ANIM");
                anim=true; background=(int)u32(data,start);loops=u16(data,start+4);
            } else if(tag(data,p,"ANMF")) {
                if(!anim || size<24 || frames.size()>=MAX_FRAMES) throw invalid("invalid ANMF");
                int x=u24(data,start)*2,y=u24(data,start+3)*2;
                int fw=u24(data,start+6)+1,fh=u24(data,start+9)+1;
                if((long)x+fw>w || (long)y+fh>h) throw invalid("frame outside canvas");
                int rawDelay=u24(data,start+12),delay=rawDelay<=10?100:rawDelay;
                int frameEnd=start+size,flags=data[start+15]&255;
                boolean bitstream=false,alphaChunk=false,alpha=false;
                for(int fp=start+16;fp<frameEnd;) {
                    int fe=chunkEnd(data,fp,frameEnd),fs=(int)u32(data,fp+4),payload=fp+8;
                    if(tag(data,fp,"ALPH")) {
                        if(alphaChunk || bitstream || fs<1) throw invalid("invalid ALPH");
                        alphaChunk=true;alpha=true;
                    } else if(tag(data,fp,"VP8 ")) {
                        if(bitstream || fs<10 || (data[payload]&1)!=0 ||
                                (data[payload+3]&255)!=0x9d || (data[payload+4]&255)!=1 ||
                                (data[payload+5]&255)!=0x2a ||
                                (u16(data,payload+6)&0x3fff)!=fw ||
                                (u16(data,payload+8)&0x3fff)!=fh) throw invalid("invalid VP8 dimensions");
                        bitstream=true;
                    } else if(tag(data,fp,"VP8L")) {
                        if(bitstream || alphaChunk || fs<5 || (data[payload]&255)!=0x2f)
                            throw invalid("invalid VP8L");
                        long bits=u32(data,payload+1);
                        if((bits>>>29)!=0 || (bits&0x3fff)+1!=fw || ((bits>>>14)&0x3fff)+1!=fh)
                            throw invalid("invalid VP8L dimensions");
                        bitstream=true;alpha=(bits&0x10000000L)!=0;
                    } else if(tag(data,fp,"VP8X") || tag(data,fp,"ANIM") || tag(data,fp,"ANMF")) {
                        throw invalid("nested animation chunk");
                    }
                    fp=fe;
                }
                if(!bitstream) throw invalid("frame without bitstream");
                frames.add(new Frame(x,y,fw,fh,delay,flags,start+16,size-16,alphaChunk,alpha));
            } else if(tag(data,p,"VP8 ") || tag(data,p,"VP8L") || tag(data,p,"ALPH")) {
                throw invalid("unexpected top-level image chunk");
            }
            p=end;
        }
        if(!vp8x || !anim || frames.isEmpty() || profile!=profileFlag)
            throw invalid("missing animation headers");
        return new WebPAnimation(data,w,h,background,loops,frames,profileOffset,profileLength);
    }
    private static int chunkEnd(byte[] data,int p,int limit) throws IOException {
        if(p<0 || limit-p<8) throw invalid("truncated chunk header");
        long size=u32(data,p+4),end=(long)p+8+size,padded=end+(size&1);
        if(padded>limit) throw invalid("truncated chunk payload or padding");
        if((size&1)!=0 && data[(int)end]!=0) throw invalid("nonzero RIFF padding");
        return (int)padded;
    }
    private static boolean tag(byte[] data,int p,String text) {
        if(p<0 || data.length-p<4) return false;
        for(int i=0;i<4;i++) if((data[p+i]&255)!=text.charAt(i)) return false;
        return true;
    }
    private static int u16(byte[] data,int p) {return (data[p]&255)|((data[p+1]&255)<<8);}
    private static int u24(byte[] data,int p) {return u16(data,p)|((data[p+2]&255)<<16);}
    private static long u32(byte[] data,int p) {return (long)u24(data,p)|((long)(data[p+3]&255)<<24);}
    private static IOException invalid(String text) {return new IOException("WebP "+text);}
    synchronized byte[] frameData(int index) throws IOException {
        if(data==null || index<0 || index>=frames.length) throw invalid("closed or invalid frame");
        Frame frame=frames[index];
        boolean extended=frame.alphaChunk || profileLength>0;
        int length=12+frame.length+(extended?18:0)+profileLength;
        if(length>MAX_BYTES) throw invalid("standalone frame too large");
        byte[] result=new byte[length];
        writeTag(result,0,"RIFF");write32(result,4,length-8);writeTag(result,8,"WEBP");
        int p=12;
        if(extended) {
            writeTag(result,p,"VP8X");write32(result,p+4,10);
            result[p+8]=(byte)((frame.alpha?0x10:0)|(profileLength>0?0x20:0));
            write24(result,p+12,frame.width-1);write24(result,p+15,frame.height-1);
            p+=18;
        }
        if(profileLength>0) {
            System.arraycopy(data,profileOffset,result,p,profileLength);p+=profileLength;
        }
        System.arraycopy(data,frame.offset,result,p,frame.length);
        return result;
    }
    private static void writeTag(byte[] data,int p,String text) {
        for(int i=0;i<4;i++) data[p+i]=(byte)text.charAt(i);
    }
    private static void write24(byte[] data,int p,int value) {
        data[p]=(byte)value;data[p+1]=(byte)(value>>>8);data[p+2]=(byte)(value>>>16);
    }
    private static void write32(byte[] data,int p,int value) {
        write24(data,p,value);data[p+3]=(byte)(value>>>24);
    }
    /** One dependency canvas per animation, independent of the UI's two output buffers.
     * Repeated requests reuse pixels. Backward requests (including a new loop's frame 0)
     * rebuild from ANIM background. Forward seeks composite every intervening frame.
     * The returned bitmap is owned here and is only copied while the handle lock is held.
     */
    synchronized Bitmap render(int target) throws IOException {
        if(data==null || target<0 || target>=frames.length) throw invalid("closed or invalid frame");
        if(composition==null) {
            composition=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            new Canvas(composition).drawColor(background,PorterDuff.Mode.SRC);
            composedFrame=-1;
        } else if(target<composedFrame) {
            new Canvas(composition).drawColor(background,PorterDuff.Mode.SRC);
            composedFrame=-1;
        }
        Canvas canvas=new Canvas(composition);
        Paint replace=new Paint();replace.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        for(int index=composedFrame+1;index<=target;index++) {
            if(index>0 && frames[index-1].dispose) {
                Frame previous=frames[index-1];replace.setColor(background);
                canvas.drawRect(previous.x,previous.y,previous.x+previous.width,
                        previous.y+previous.height,replace);
            }
            Frame frame=frames[index];byte[] encoded=frameData(index);
            Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length);
            if(decoded==null) throw invalid("frame decode failed");
            try {
                if(decoded.getWidth()!=frame.width || decoded.getHeight()!=frame.height)
                    throw invalid("decoded frame dimensions mismatch");
                // SRC overwrites transparent pixels as required by noBlend; source-over
                // keeps dependency pixels under transparent pixels of a blended frame.
                canvas.drawBitmap(decoded,frame.x,frame.y,frame.noBlend?replace:null);
                composedFrame=index;
            } finally {decoded.recycle();}
        }
        return composition;
    }
    synchronized void close() {
        data=null;composedFrame=-1;
        if(composition!=null) {composition.recycle();composition=null;}
    }
}
