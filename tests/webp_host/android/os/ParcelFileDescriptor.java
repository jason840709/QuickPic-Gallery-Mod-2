package android.os;
import java.io.*; import java.util.*;
/** dup shares seek position with the caller; closing duplicate keeps registered caller open. */
public final class ParcelFileDescriptor implements Closeable {
 public static final Map<FileDescriptor,RandomAccessFile> OPEN=new IdentityHashMap<FileDescriptor,RandomAccessFile>();
 private static final Map<Integer,RandomAccessFile> FILES=new HashMap<Integer,RandomAccessFile>();
 public static int DUPLICATES=0; public static long BYTES_READ=0; private final RandomAccessFile file;private final FileDescriptor fd=new FileDescriptor();private boolean closed;
 private ParcelFileDescriptor(RandomAccessFile f){file=f;OPEN.put(fd,f);DUPLICATES++;}
 public static void register(int raw,File f)throws IOException{FILES.put(raw,new RandomAccessFile(f,"r"));}
 public static void unregister(int raw)throws IOException{RandomAccessFile f=FILES.remove(raw);if(f!=null)f.close();}
 public static long position(int raw)throws IOException{return FILES.get(raw).getFilePointer();}
 public static void position(int raw,long p)throws IOException{FILES.get(raw).seek(p);}
 public static ParcelFileDescriptor fromFd(int raw)throws IOException{RandomAccessFile f=FILES.get(raw);if(f==null)throw new IOException("bad fd");return new ParcelFileDescriptor(f);}
 public FileDescriptor getFileDescriptor(){return fd;}
 public void close()throws IOException{if(!closed){closed=true;OPEN.remove(fd);DUPLICATES--;}}
 public static final class AutoCloseInputStream extends InputStream {private final ParcelFileDescriptor p;public AutoCloseInputStream(ParcelFileDescriptor p){this.p=p;}public int read()throws IOException{return p.file.read();}public int read(byte[] b,int off,int n)throws IOException{int read=p.file.read(b,off,n);if(read>0)BYTES_READ+=read;return read;}public void close()throws IOException{p.close();}}
}
