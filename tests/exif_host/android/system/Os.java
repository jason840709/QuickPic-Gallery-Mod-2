package android.system;
import java.io.*;
import android.os.ParcelFileDescriptor;
/** Tests positional IO and file growth/shrink with real isolated host files. */
public final class Os {
    public static boolean FAIL_READ_AFTER_WRITE=false, FAIL_TRUNCATE=false, FAIL_FSYNC=false;
    public static int FLAGS=2, FAIL_WRITE_AT=-1, WRITES=0;
    public static int fcntlInt(FileDescriptor fd, int cmd, int arg) throws IOException { file(fd); return FLAGS; }
    private static RandomAccessFile file(FileDescriptor fd) throws IOException {
        RandomAccessFile f = ParcelFileDescriptor.OPEN.get(fd); if (f == null) throw new IOException("closed"); return f;
    }
    public static int pread(FileDescriptor fd, byte[] b, int off, int count, long pos) throws IOException {
        if (FAIL_READ_AFTER_WRITE && WRITES > 0) { FAIL_READ_AFTER_WRITE=false; throw new IOException("Injected read failure"); }
        RandomAccessFile f = file(fd); f.seek(pos); return f.read(b, off, count);
    }
    public static int pwrite(FileDescriptor fd, byte[] b, int off, int count, long pos) throws IOException {
        if (WRITES++ == FAIL_WRITE_AT) { FAIL_WRITE_AT=-1; throw new IOException("Injected write failure"); }
        RandomAccessFile f = file(fd); f.seek(pos); f.write(b, off, count); return count;
    }
    public static StructStat fstat(FileDescriptor fd) throws IOException { return new StructStat(file(fd).length()); }
    public static void ftruncate(FileDescriptor fd, long size) throws IOException { if(FAIL_TRUNCATE){FAIL_TRUNCATE=false;throw new IOException("Injected truncate failure");}file(fd).setLength(size); }
    public static void fsync(FileDescriptor fd) throws IOException { if(FAIL_FSYNC){FAIL_FSYNC=false;throw new IOException("Injected fsync failure");}fd.sync(); }
}
