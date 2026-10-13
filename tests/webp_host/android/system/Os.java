package android.system;
import java.io.*;import android.os.ParcelFileDescriptor;
public final class Os {
 private static RandomAccessFile f(FileDescriptor fd)throws ErrnoException{RandomAccessFile f=ParcelFileDescriptor.OPEN.get(fd);if(f==null)throw new ErrnoException("bad fd",9);return f;}
 public static long lseek(FileDescriptor fd,long p,int mode)throws ErrnoException{try{RandomAccessFile f=f(fd);f.seek(mode==OsConstants.SEEK_CUR?f.getFilePointer()+p:p);return f.getFilePointer();}catch(IOException e){throw new ErrnoException("seek",5);}}
 public static StructStat fstat(FileDescriptor fd)throws ErrnoException{f(fd);return new StructStat();}
 public static int fcntlInt(FileDescriptor fd,int cmd,int arg)throws ErrnoException{f(fd);return 0;}
 public static int read(FileDescriptor fd,byte[] b,int o,int n)throws ErrnoException{try{return Math.max(0,f(fd).read(b,o,n));}catch(IOException e){throw new ErrnoException("read",5);}}
 public static int write(FileDescriptor fd,byte[] b,int o,int n)throws ErrnoException{throw new ErrnoException("readonly",5);}
 public static void ftruncate(FileDescriptor fd,long n)throws ErrnoException{throw new ErrnoException("readonly",5);}
 public static void fsync(FileDescriptor fd)throws ErrnoException{f(fd);}
}
