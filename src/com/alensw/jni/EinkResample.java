// SPDX-License-Identifier: MIT
package com.alensw.jni;

/** Scale-aware prefiltering, not a fixed-radius blur. Coordinates are pixel centers. */
public final class EinkResample {
    private EinkResample() { }
    public interface Rows {
        int width(); int height();
        void read(int y, int[] pixels);
        void write(int y, int[] pixels);
    }
    /** Exact area coverage or widened Lanczos3; bounded horizontal row cache. */
    public static void scale(Rows source, Rows target, boolean lanczos) {
        int sw=source.width(),sh=source.height(),dw=target.width(),dh=target.height();
        if(sw<1||sh<1||dw<1||dh<1||dw>sw||dh>sh||sw>65536||sh>65536||dw>16384||dh>16384)throw new IllegalArgumentException("Invalid downsample dimensions");
        Axis xs=new Axis(sw,dw,lanczos),ys=new Axis(sh,dh,lanczos);
        long rowBudget=Math.min(4L*1024*1024,Math.max(262144,Runtime.getRuntime().maxMemory()/64));
        int memorySlots=(int)Math.max(1,rowBudget/((long)dw*4*4));
        int slots=Math.max(1,Math.min(memorySlots,Math.min(64,ys.largest)));
        int[] rowIds=new int[slots];java.util.Arrays.fill(rowIds,-1);
        float[][] cache=new float[slots][];
        int[] input=new int[sw],output=new int[dw];float[] sum=new float[dw*4];
        for(int y=0;y<dh;y++){
            java.util.Arrays.fill(sum,0);float[] yw=ys.weights[y];
            for(int k=0;k<yw.length;k++){
                int sy=ys.starts[y]+k,slot=sy%slots;
                float[] row=cache[slot];if(row==null){row=new float[dw*4];cache[slot]=row;}
                if(rowIds[slot]!=sy){source.read(sy,input);horizontal(input,row,xs);rowIds[slot]=sy;}
                float weight=yw[k];for(int i=0;i<sum.length;i++)sum[i]+=row[i]*weight;
            }
            for(int x=0;x<dw;x++){
                int at=x*4,a=clamp(sum[at],255);if(a==0){output[x]=0;continue;}
                int r=clamp(sum[at+1]*255/a,255),g=clamp(sum[at+2]*255/a,255),b=clamp(sum[at+3]*255/a,255);
                output[x]=(a<<24)|(r<<16)|(g<<8)|b;
            }
            target.write(y,output);
        }
    }
    private static int clamp(float v,int max){return Math.max(0,Math.min(max,Math.round(v)));}
    private static void horizontal(int[] source,float[] out,Axis axis){
        for(int x=0;x<axis.starts.length;x++){
            float a=0,r=0,g=0,b=0;float[] weights=axis.weights[x];
            for(int k=0;k<weights.length;k++){
                int c=source[axis.starts[x]+k];float alpha=c>>>24,weight=weights[k],premul=alpha/255;
                a+=alpha*weight;r+=((c>>>16)&255)*premul*weight;g+=((c>>>8)&255)*premul*weight;b+=(c&255)*premul*weight;
            }
            int at=x*4;out[at]=a;out[at+1]=r;out[at+2]=g;out[at+3]=b;
        }
    }
    private static final class Axis {
        final int[] starts;final float[][] weights;int largest;
        Axis(int source,int target,boolean lanczos){
            starts=new int[target];weights=new float[target][];double ratio=(double)source/target,scale=1/ratio;
            for(int i=0;i<target;i++){
                int first,last;double center=(i+.5)*ratio-.5;
                if(lanczos){first=Math.max(0,(int)Math.ceil(center-3*ratio));last=Math.min(source-1,(int)Math.floor(center+3*ratio));}
                else{first=Math.max(0,(int)Math.floor(i*ratio));last=Math.min(source-1,(int)Math.ceil((i+1)*ratio)-1);}
                starts[i]=first;float[] w=new float[last-first+1];double total=0;
                for(int k=0;k<w.length;k++){
                    int sample=first+k;double value;
                    if(lanczos){double distance=(sample-center)*scale;value=sinc(distance)*sinc(distance/3);}
                    else value=Math.max(0,Math.min((i+1)*ratio,sample+1)-Math.max(i*ratio,sample))/ratio;
                    w[k]=(float)value;total+=value;
                }
                for(int k=0;k<w.length;k++)w[k]/=(float)total;
                weights[i]=w;largest=Math.max(largest,w.length);
            }
        }
        private static double sinc(double x){if(Math.abs(x)<1e-9)return 1;double p=Math.PI*x;return Math.sin(p)/p;}
    }
}
