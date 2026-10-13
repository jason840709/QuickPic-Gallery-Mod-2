package com.alensw.jni;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;

/** Run with exif_host Android doubles; fixtures and writes are temp files only. */
public final class QuickPicExifHostTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void put16(byte[] b, int p, int v, boolean le) { b[p]=(byte)(le?v:v>>8); b[p+1]=(byte)(le?v>>8:v); }
    private static void put32(byte[] b, int p, long v, boolean le) { for(int i=0;i<4;i++) b[p+i]=(byte)(v>>((le?i:3-i)*8)); }
    private static void entry(byte[] b, int p, int tag, int type, int count, int value, boolean le) {
        put16(b,p,tag,le); put16(b,p+2,type,le); put32(b,p+4,count,le);
        if(type==3 && count==1) put16(b,p+8,value,le); else put32(b,p+8,value,le);
    }
    private static byte[] exif(boolean le, boolean orientation) {
        byte[] b=new byte[272]; byte[] magic={69,120,105,102,0,0}; System.arraycopy(magic,0,b,0,6);
        b[6]=b[7]=(byte)(le?73:77); put16(b,8,42,le); put32(b,10,8,le);
        int n=orientation?4:3; put16(b,14,n,le); int p=16;
        entry(b,p,270,2,6,186,le); p+=12;
        if(orientation) { entry(b,p,274,3,1,6,le); p+=12; }
        entry(b,p,34665,4,1,80,le);p+=12;
        entry(b,p,34853,4,1,120,le);p+=12; put32(b,p,216,le);
        put16(b,86,2,le);entry(b,88,33434,5,1,160,le);entry(b,100,37500,7,8,174,le);
        put16(b,126,2,le); entry(b,128,1,2,2,0,le);b[136]=83;
        entry(b,140,2,5,3,192,le);
        put32(b,166,1,le); put32(b,170,125,le);
        for(int i=0;i<8;i++) b[180+i]=(byte)(200+i);
        byte[] desc={104,101,108,108,111,0};System.arraycopy(desc,0,b,192,6);
        for(int i=0;i<3;i++){put32(b,198+i*8,25+i,le);put32(b,202+i*8,1,le);}
        put16(b,222,2,le);entry(b,224,513,4,1,250,le);entry(b,236,514,4,1,6,le);
        byte[] thumb={(byte)255,(byte)216,1,2,(byte)255,(byte)217};System.arraycopy(thumb,0,b,256,6);
        return b;
    }
    private static void segment(ByteArrayOutputStream b,int code,byte[] payload) {
        b.write(255);b.write(code);b.write((payload.length+2)>>8);b.write((payload.length+2)&255);b.write(payload,0,payload.length);
    }
    private static byte[] jpeg(byte[] exif,int width,int height, boolean panorama) {
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(255);b.write(216);
        segment(b,224,new byte[]{74,70,73,70,0});
        if(exif!=null)segment(b,225,exif);
        if(panorama)segment(b,225,("http://ns.adobe.com/xap/1.0/\u0000<x http://ns.google.com/photos/1.0/panorama/>").getBytes());
        segment(b,226,new byte[]{73,67,67,0,10,20});
        byte[] sof={8,(byte)(height>>8),(byte)height,(byte)(width>>8),(byte)width,3,1,34,0,2,17,1,3,17,1};segment(b,192,sof);
        segment(b,218,new byte[]{3,1,0,2,17,3,17,0,63,0});return b.toByteArray();
    }
    private static byte[] tail(){byte[] b=new byte[180001];new Random(9182).nextBytes(b);b[b.length-2]=(byte)255;b[b.length-1]=(byte)217;return b;}
    private static Path file(byte[] head, byte[] pixels) throws IOException {
        Path p=Files.createTempFile("quickpic-exif-test-", ".jpg");ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(head);b.write(pixels);Files.write(p,b.toByteArray());return p;
    }
    private static void sameTail(Path file, byte[] expected) throws IOException {
        byte[] b=Files.readAllBytes(file);check(Arrays.equals(expected,Arrays.copyOfRange(b,b.length-expected.length,b.length)),"compressed pixels unchanged");
    }
    public static void main(String[] args) throws Exception {
        QuickPicExif.setCacheDirectory(new File(System.getProperty("java.io.tmpdir")));
        for(boolean le:new boolean[]{true,false}) {
            byte[] original=exif(le,true);QuickPicExif.Jpeg j=QuickPicExif.parseJpeg(jpeg(original,640,480,true));
            check(j.info(1)==640 && j.info(2)==480 && j.info(3)==3,"SOF dimensions and components");
            check(j.info(4)==90 && j.info(10)==1 && j.info(11)==1,"info semantics");
            check(Arrays.equals((byte[])j.tiff.value(270,false),"hello".getBytes()),"ASCII bytes");
            check(Math.abs(((Double)j.tiff.value(33434,false))-.008)<1e-9,"rational Double");
            check(Arrays.equals(j.tiff.triple(2,true),new double[]{25,26,27}),"GPS triple");
            check(j.tiff.thumbnail().length==6,"embedded JPEG thumbnail");
            check(j.tiff.value(274,false) instanceof Integer,"SHORT returns Integer");
            check(j.tiff.value(37500,false)==null,"UNDEFINED not exposed by native contract");
            byte[] updated=j.tiff.withOrientation(8,false);check(new QuickPicExif.Tiff(updated).integer(274,false,0)==8,"orientation patch");
            check(Arrays.equals(Arrays.copyOfRange(original,180,188),Arrays.copyOfRange(updated,180,188)),"opaque MakerNote preserved");
            byte[] updatedHeader=j.withExif(updated);check((updatedHeader[3]&255)==224,"JFIF stays first APP0");
            check(QuickPicExif.parseJpeg(updatedHeader).panorama,"XMP preserved");
            check(new QuickPicExif.Tiff(j.tiff.withOrientation(1,true)).thumbnail()==null,"stale transferred thumbnail removed");
            byte[] missing=exif(le,false);QuickPicExif.Tiff added=new QuickPicExif.Tiff(new QuickPicExif.Tiff(missing).withOrientation(3,false));
            check(added.integer(274,false,0)==3 && added.thumbnail()!=null,"append IFD preserves thumbnail offsets");
            check(Arrays.equals((byte[])added.value(270,false),"hello".getBytes()),"append IFD preserves data offsets");
            for(int o=1;o<=8;o++){QuickPicExif.Jpeg oriented=QuickPicExif.parseJpeg(j.withExif(j.tiff.withOrientation(o,false)));check(oriented.info(4)==(o==3?180:o==6?90:o==8?270:0),"native degree table "+o);}
        }
        byte[] pixels=tail();
        Path failing=file(jpeg(exif(true,false),640,480,false),pixels);
        try {
            ParcelFileDescriptor.register(90,failing.toFile());int handle=QuickPicExif.exifOpenFD(90,false);
            byte[] before=Files.readAllBytes(failing);
            for(int fail=0;fail<4;fail++) {
                Os.WRITES=0;Os.FAIL_WRITE_AT=fail;
                check(!QuickPicExif.exifSetDegrees(handle,90),"injected write failure reported "+fail);
                check(Arrays.equals(before,Files.readAllBytes(failing)),"rollback restores full original "+fail);
            }
            Os.FLAGS=OsConstants.O_RDWR|OsConstants.O_APPEND;
            check(!QuickPicExif.exifSetDegrees(handle,90),"append descriptor rejected");
            check(Arrays.equals(before,Files.readAllBytes(failing)),"append rejection leaves file untouched");Os.FLAGS=OsConstants.O_RDWR;
            Os.FAIL_READ_AFTER_WRITE=true;Os.WRITES=0;
            check(!QuickPicExif.exifSetDegrees(handle,90),"read failure during mutation reported");
            check(Arrays.equals(before,Files.readAllBytes(failing)),"read failure rollback exact");
            Os.FAIL_TRUNCATE=true;
            check(!QuickPicExif.exifSetDegrees(handle,90),"truncate failure reported");
            check(Arrays.equals(before,Files.readAllBytes(failing)),"truncate failure rollback exact");
            Os.FAIL_FSYNC=true;
            check(!QuickPicExif.exifSetDegrees(handle,90),"fsync failure reported");
            check(Arrays.equals(before,Files.readAllBytes(failing)),"fsync failure rollback exact");
            QuickPicExif.setCacheDirectory(null);
            check(!QuickPicExif.exifSetDegrees(handle,90),"unconfigured cache rejected");
            check(Arrays.equals(before,Files.readAllBytes(failing)),"no cache leaves file untouched");
            QuickPicExif.setCacheDirectory(new File(System.getProperty("java.io.tmpdir")));
            QuickPicExif.exifClose(handle);
        } finally {Files.delete(failing);}
        Path src=file(jpeg(exif(true,false),640,480,true),pixels),dst=file(jpeg(null,320,240,false),pixels);
        try {
            ParcelFileDescriptor.register(100,src.toFile());int sh=QuickPicExif.exifOpenFD(100,false);ParcelFileDescriptor.unregister(100);
            check(sh!=0,"open keeps duplicate independent of original fd");
            check(QuickPicExif.exifSetDegrees(sh,270),"file orientation insert");check(QuickPicExif.exifGetInfo(sh,4)==270,"updated handle");sameTail(src,pixels);
            ParcelFileDescriptor.register(101,dst.toFile());int dh=QuickPicExif.exifOpenFD(101,false);
            check(QuickPicExif.exifSaveTo(sh,dh),"metadata transfer");check(QuickPicExif.exifGetInfo(dh,4)==0,"transfer normal orientation");
            check(QuickPicExif.exifGetThumbnail(dh)==null && QuickPicExif.exifGetThumbnail(sh)!=null,"transfer preserves source thumbnail");
            check(Arrays.equals((byte[])QuickPicExif.exifGetValue(dh,270,false),"hello".getBytes()),"transfer metadata");sameTail(dst,pixels);sameTail(src,pixels);
            check(QuickPicExif.exifSetDegrees(dh,-90) && QuickPicExif.exifGetInfo(dh,4)==270,"negative rotation normalization");
            Path plain=file(jpeg(null,100,100,false),pixels);
            try {ParcelFileDescriptor.register(103,plain.toFile());int empty=QuickPicExif.exifOpenFD(103,true);
                long prior=Files.size(dst);check(QuickPicExif.exifSaveTo(empty,dh),"transfer smaller metadata");
                check(Files.size(dst)<prior,"header shrink");sameTail(dst,pixels);QuickPicExif.exifClose(empty);
            }finally{Files.delete(plain);}
            QuickPicExif.exifClose(sh);QuickPicExif.exifClose(dh);check(QuickPicExif.exifGetValue(sh,270,false)==null,"closed handle safe");
            int ro=QuickPicExif.exifOpenFD(101,true);check(ro!=0 && !QuickPicExif.exifSetDegrees(ro,90),"read only respected");QuickPicExif.exifClose(ro);
            byte[] malformed=exif(true,true);malformed[6]=0;Path bad=file(jpeg(malformed,10,20,false),pixels);
            try{ParcelFileDescriptor.register(102,bad.toFile());int bh=QuickPicExif.exifOpenFD(102,false);byte[] before=Files.readAllBytes(bad);check(bh!=0 && !QuickPicExif.exifSetDegrees(bh,90),"malformed metadata fails save");check(Arrays.equals(before,Files.readAllBytes(bad)),"malformed EXIF not destroyed");QuickPicExif.exifClose(bh);}finally{Files.delete(bad);}
        } finally {Files.delete(src);Files.delete(dst);}
        check(QuickPicExif.exifOpenFD(-1,false)==0 && !QuickPicExif.exifSaveTo(0,0),"invalid handles safe");
        System.out.println("QuickPicExifHostTest: "+checks+" checks passed");
    }
}
