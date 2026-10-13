package com.alensw.jni;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** JPEG/EXIF replacement for the original 32-bit JNI parser.
 * Owns a duplicate descriptor, never the caller's descriptor. All metadata edits
 * preserve raw TIFF offsets and unknown fields, including opaque MakerNotes.
 * This intentionally has the original JPEG-only contract, not a media decoder.
 */
public final class QuickPicExif {
    private QuickPicExif() {}
    private static final Map<Integer, Session> SESSIONS = new HashMap<Integer, Session>();
    private static int nextHandle = 1;
    private static volatile File cacheDirectory;

    /** Configure with Application.getCacheDir() before allowing metadata writes. */
    public static void setCacheDirectory(File directory) { cacheDirectory = directory; }
    private static final int MAX_HEADER = 4 * 1024 * 1024;
    private static final int[] TYPE_SIZE = {0,1,1,2,4,8,1,1,2,4,8,4,8};
    private static final byte[] EXIF = {69,120,105,102,0,0};
    private static final String PANORAMA = "http://ns.google.com/photos/1.0/panorama/";

    private static final class Session {
        final ParcelFileDescriptor fd;
        final boolean readOnly;
        Jpeg jpeg;
        Session(ParcelFileDescriptor fd, boolean readOnly, Jpeg jpeg) {
            this.fd = fd; this.readOnly = readOnly; this.jpeg = jpeg;
        }
    }

    public static synchronized int exifOpenFD(int rawFd, boolean readOnly) {
        if (rawFd < 0) return 0;
        ParcelFileDescriptor dup = null;
        try {
            dup = ParcelFileDescriptor.fromFd(rawFd);
            Jpeg jpeg = readJpeg(dup.getFileDescriptor());
            int handle;
            do { handle = nextHandle++; if (nextHandle <= 0) nextHandle = 1; }
            while (handle == 0 || SESSIONS.containsKey(handle));
            SESSIONS.put(handle, new Session(dup, readOnly, jpeg));
            return handle;
        } catch (Exception e) {
            if (dup != null) try { dup.close(); } catch (IOException ignored) {}
            return 0;
        }
    }

    public static synchronized void exifClose(int handle) {
        Session session = SESSIONS.remove(handle);
        if (session != null) try { session.fd.close(); } catch (IOException ignored) {}
    }

    public static synchronized int exifGetInfo(int handle, int key) {
        Session s = SESSIONS.get(handle);
        return s == null ? 0 : s.jpeg.info(key);
    }

    public static synchronized Object exifGetValue(int handle, int tag, boolean gps) {
        Session s = SESSIONS.get(handle);
        return s == null || s.jpeg.tiff == null ? null : s.jpeg.tiff.value(tag, gps);
    }

    public static synchronized double[] exifGet3RealValue(int handle, int tag, boolean gps) {
        Session s = SESSIONS.get(handle);
        return s == null || s.jpeg.tiff == null ? null : s.jpeg.tiff.triple(tag, gps);
    }

    public static synchronized byte[] exifGetThumbnail(int handle) {
        Session s = SESSIONS.get(handle);
        return s == null || s.jpeg.tiff == null ? null : s.jpeg.tiff.thumbnail();
    }

    public static synchronized boolean exifSetDegrees(int handle, int degrees) {
        Session s = SESSIONS.get(handle);
        if (s == null || s.readOnly) return false;
        try {
            Jpeg current = readJpeg(s.fd.getFileDescriptor());
            if (current.invalidExif) return false;
            int normalized = ((degrees % 360) + 360) % 360;
            int orientation = normalized == 90 ? 6 : normalized == 180 ? 3 : normalized == 270 ? 8 : 1;
            byte[] payload = current.tiff == null ? minimalExif(orientation) : current.tiff.withOrientation(orientation, false);
            byte[] header = current.withExif(payload);
            replaceHeaderSafely(s.fd.getFileDescriptor(), current.header, header);
            s.jpeg = finishRead(s.fd.getFileDescriptor(), header);
            return true;
        } catch (Exception e) { return false; }
    }

    /** Copy metadata after pixel editing: destination orientation becomes normal.
     * Original JNI removed the stale source thumbnail. Source itself is unmodified.
     */
    public static synchronized boolean exifSaveTo(int srcHandle, int dstHandle) {
        Session src = SESSIONS.get(srcHandle), dst = SESSIONS.get(dstHandle);
        if (src == null || dst == null || dst.readOnly) return false;
        if (src == dst) return exifSetDegrees(dstHandle, 0);
        try {
            Jpeg source = readJpeg(src.fd.getFileDescriptor());
            Jpeg target = readJpeg(dst.fd.getFileDescriptor());
            if (source.invalidExif || target.invalidExif) return false;
            byte[] payload = source.tiff == null ? minimalExif(1) : source.tiff.withOrientation(1, true);
            // Pixel dimensions describe the newly encoded destination, if present.
            Tiff copied = new Tiff(payload);
            copied.patchDimension(256, target.width);
            copied.patchDimension(257, target.height);
            copied.patchDimension(40962, target.width);
            copied.patchDimension(40963, target.height);
            byte[] header = target.withExif(payload);
            replaceHeaderSafely(dst.fd.getFileDescriptor(), target.header, header);
            src.jpeg = source;
            dst.jpeg = finishRead(dst.fd.getFileDescriptor(), header);
            return true;
        } catch (Exception e) { return false; }
    }

    private static byte[] minimalExif(int orientation) {
        byte[] out = new byte[32];
        System.arraycopy(EXIF, 0, out, 0, 6);
        out[6] = 73; out[7] = 73; out[8] = 42; out[10] = 8;
        out[14] = 1; out[16] = 18; out[17] = 1; out[18] = 3;
        out[20] = 1; out[24] = (byte) orientation;
        return out;
    }

    private static byte[] readAt(FileDescriptor fd, long position, int length) throws Exception {
        byte[] bytes = new byte[length];
        int count = 0;
        while (count < length) {
            int read = Os.pread(fd, bytes, count, length - count, position + count);
            if (read <= 0) throw new IOException("Truncated JPEG");
            count += read;
        }
        return bytes;
    }

    private static void writeAt(FileDescriptor fd, byte[] bytes, int length, long position) throws Exception {
        int count = 0;
        while (count < length) {
            int written = Os.pwrite(fd, bytes, count, length - count, position + count);
            if (written <= 0) throw new IOException("Unable to save EXIF");
            count += written;
        }
    }

    private static Jpeg readJpeg(FileDescriptor fd) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] start = readAt(fd, 0, 2);
        if (u8(start[0]) != 255 || u8(start[1]) != 216) throw new IOException("Not JPEG");
        out.write(start);
        long position = 2;
        for (int segment = 0; segment < 4096; segment++) {
            byte[] marker = readAt(fd, position++, 1);
            if (u8(marker[0]) != 255) throw new IOException("Invalid JPEG marker");
            out.write(marker);
            int code;
            do {
                marker = readAt(fd, position++, 1);
                out.write(marker); code = u8(marker[0]);
                if (position > MAX_HEADER) throw new IOException("JPEG header too large");
            } while (code == 255);
            if (code == 217) return finishRead(fd, out.toByteArray());
            if (code == 0 || code == 216) throw new IOException("Invalid JPEG marker");
            if (code == 1 || (code >= 208 && code <= 215)) continue;
            byte[] size = readAt(fd, position, 2);
            int length = be16(size, 0);
            if (length < 2 || position + length > MAX_HEADER) throw new IOException("Invalid JPEG segment");
            byte[] data = readAt(fd, position, length);
            out.write(data); position += length;
            if (code == 218) return finishRead(fd, out.toByteArray());
        }
        throw new IOException("Too many JPEG segments");
    }

    private static Jpeg finishRead(FileDescriptor fd, byte[] header) throws Exception {
        Jpeg jpeg = parseJpeg(header);
        // Original lib's Samsung S3 Mini quirk: malformed appended mini-photo
        // tails disable the lossless JPEG path (native CheckS3MiniPhotoEOI).
        Object model = jpeg.tiff == null ? null : jpeg.tiff.value(272, false);
        if (model instanceof byte[]) {
            String name = new String((byte[]) model, "ISO-8859-1");
            if (name.startsWith("GT-I8190")) {
                boolean valid = false;
                long size = Os.fstat(fd).st_size;
                for (int relative = -3354; relative > -3364; relative -= 2) {
                    long offset = size + relative;
                    if (offset <= 0) break;
                    byte[] marker = readAt(fd, offset, 2);
                    if (u8(marker[0]) == 255 && u8(marker[1]) == 217) {
                        valid = u8(readAt(fd, offset - 1, 1)[0]) != 255;
                        break;
                    }
                    if (marker[0] != 0 || marker[1] != 0) break;
                }
                if (!valid) jpeg.lossless = false;
            }
        }
        return jpeg;
    }

    /** Full recoverable backup precedes the first byte changed in the destination.
     * Rewriting a content-provider fd cannot use pathname rename transactions.
     * If the provider also fails rollback, leave the private backup for recovery.
     */
    private static void replaceHeaderSafely(FileDescriptor fd, byte[] oldHeader, byte[] newHeader) throws Exception {
        if (Arrays.equals(oldHeader, newHeader)) return;
        int flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0);
        if ((flags & OsConstants.O_ACCMODE) != OsConstants.O_RDWR || (flags & OsConstants.O_APPEND) != 0)
            throw new IOException("Descriptor must be read/write without append");
        long size = Os.fstat(fd).st_size;
        if (size < oldHeader.length) throw new IOException("Truncated destination");
        File directory = cacheDirectory;
        if (directory == null || !directory.isDirectory() || !directory.canWrite())
            throw new IOException("Private EXIF cache directory is not configured or writable");
        File backup = File.createTempFile("quickpic-exif-recovery-", ".jpg", directory);
        boolean keepBackup = false;
        try {
            FileOutputStream copy = new FileOutputStream(backup);
            try {
                for (long p = 0; p < size;) {
                    int count = (int)Math.min(65536, size - p);
                    copy.write(readAt(fd, p, count)); p += count;
                }
                copy.getFD().sync();
            } finally { copy.close(); }
            if (Os.fstat(fd).st_size != size || !Arrays.equals(readAt(fd, 0, oldHeader.length), oldHeader))
                throw new IOException("Destination changed during EXIF save");
            try {
                replaceHeader(fd, oldHeader, newHeader);
            } catch (Throwable failure) {
                try {
                    FileInputStream original = new FileInputStream(backup);
                    try {
                        byte[] block = new byte[65536]; long p = 0; int count;
                        while ((count = original.read(block)) > 0) { writeAt(fd, block, count, p); p += count; }
                        if (p != size) throw new IOException("Incomplete EXIF recovery file");
                        Os.ftruncate(fd, size); Os.fsync(fd);
                    } finally { original.close(); }
                } catch (Throwable rollbackFailure) {
                    keepBackup = true;
                    Log.e("QuickPicExif", "EXIF save and rollback failed; original JPEG backup: " + backup.getAbsolutePath(), rollbackFailure);
                }
                if (failure instanceof Exception) throw (Exception) failure;
                if (failure instanceof Error) throw (Error) failure;
                throw new IOException("EXIF save failed", failure);
            }
        } finally {
            if (!keepBackup && !backup.delete()) backup.deleteOnExit();
        }
    }

    /** Move compressed pixels without decoding them; bounded-memory, no fd seek. */
    private static void replaceHeader(FileDescriptor fd, byte[] oldHeader, byte[] newHeader) throws Exception {
        if (Arrays.equals(oldHeader, newHeader)) return;
        long size = Os.fstat(fd).st_size;
        long delta = (long) newHeader.length - oldHeader.length;
        if (size < oldHeader.length) throw new IOException("Truncated destination");
        if (delta > 0) {
            Os.ftruncate(fd, size + delta);
            for (long end = size; end > oldHeader.length;) {
                int count = (int) Math.min(65536, end - oldHeader.length);
                long offset = end - count;
                byte[] block = readAt(fd, offset, count);
                writeAt(fd, block, count, offset + delta); end = offset;
            }
        } else if (delta < 0) {
            for (long offset = oldHeader.length; offset < size;) {
                int count = (int) Math.min(65536, size - offset);
                byte[] block = readAt(fd, offset, count);
                writeAt(fd, block, count, offset + delta); offset += count;
            }
        }
        writeAt(fd, newHeader, newHeader.length, 0);
        if (delta < 0) Os.ftruncate(fd, size + delta);
        Os.fsync(fd);
    }

    private static int u8(byte b) { return b & 255; }
    private static int be16(byte[] b, int p) { return (u8(b[p]) << 8) | u8(b[p + 1]); }
    private static boolean startsExif(byte[] b, int p, int count) {
        if (count < EXIF.length) return false;
        for (int i = 0; i < EXIF.length; i++) if (b[p + i] != EXIF[i]) return false;
        return true;
    }

    // Package-private byte parser is independently exercised by JVM host tests.
    static Jpeg parseJpeg(byte[] header) throws IOException { return new Jpeg(header); }

    static final class Jpeg {
        final byte[] header;
        Tiff tiff;
        int width, height, components;
        boolean panorama, lossless, invalidExif;
        Jpeg(byte[] bytes) throws IOException {
            header = bytes;
            if (bytes.length < 2 || u8(bytes[0]) != 255 || u8(bytes[1]) != 216) throw new IOException("Not JPEG");
            int p = 2;
            boolean terminal = false;
            int exifSegments = 0;
            while (p < bytes.length) {
                if (u8(bytes[p++]) != 255) throw new IOException("Invalid marker");
                while (p < bytes.length && u8(bytes[p]) == 255) p++;
                if (p >= bytes.length) throw new IOException("Truncated marker");
                int code = u8(bytes[p++]);
                if (code == 217) { terminal = true; break; }
                if (code == 1 || (code >= 208 && code <= 215)) continue;
                if (p + 2 > bytes.length) throw new IOException("Truncated segment");
                int length = be16(bytes, p), data = p + 2, count = length - 2;
                if (length < 2 || (long) p + length > bytes.length) throw new IOException("Invalid segment");
                if (code == 225) {
                    if (startsExif(bytes, data, count) && ++exifSegments > 1) invalidExif = true;
                    if (tiff == null && startsExif(bytes, data, count)) {
                        try { tiff = new Tiff(Arrays.copyOfRange(bytes, data, data + count)); }
                        catch (IOException malformed) { invalidExif = true; }
                    }
                    if (count >= 29 && new String(bytes, data, count, "ISO-8859-1").contains(PANORAMA)) panorama = true;
                }
                if (isSof(code) && count >= 8) {
                    height = be16(bytes, data + 1); width = be16(bytes, data + 3);
                    components = u8(bytes[data + 5]);
                    if (components < 1 || count < 6 + components * 3) throw new IOException("Invalid SOF");
                    lossless = components > 1 || u8(bytes[data + 7]) == 17;
                }
                p += length;
                if (code == 218) { terminal = true; break; }
            }
            if (!terminal || width == 0 || height == 0) throw new IOException("Missing JPEG frame");
        }
        int info(int key) {
            switch (key) {
                case 1: return width;
                case 2: return height;
                case 3: return components;
                case 4:
                    int o = tiff == null ? 0 : tiff.integer(274, false, 0);
                    return o == 3 ? 180 : o == 6 ? 90 : o == 8 ? 270 : 0;
                case 10: return panorama ? 1 : 0;
                case 11: return lossless ? 1 : 0;
                default: return 0;
            }
        }
        byte[] withExif(byte[] payload) throws IOException {
            if (payload.length > 65533) throw new IOException("EXIF APP1 too large");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(header, 0, 2);
            // Retain initial JFIF APP0 placement and all XMP/ICC/other segments.
            boolean inserted = false;
            int p = 2;
            while (p < header.length) {
                int start = p++;
                while (u8(header[p]) == 255) p++;
                int code = u8(header[p++]);
                if (!inserted && code != 224) {
                    out.write(255); out.write(225);
                    out.write((payload.length + 2) >> 8); out.write((payload.length + 2) & 255);
                    out.write(payload, 0, payload.length);
                    inserted = true;
                }
                if (code == 217) { out.write(header, start, p - start); break; }
                if (code == 1 || (code >= 208 && code <= 215)) { out.write(header, start, p - start); continue; }
                int length = be16(header, p);
                boolean exif = code == 225 && startsExif(header, p + 2, length - 2);
                p += length;
                if (!exif) out.write(header, start, p - start);
                if (code == 218) break;
            }
            return out.toByteArray();
        }
        private static boolean isSof(int code) {
            return code >= 192 && code <= 207 && code != 196 && code != 200 && code != 204;
        }
    }

    static final class Entry {
        final int tag, type, entryOffset, valueOffset;
        final long count;
        Entry(int tag, int type, long count, int entryOffset, int valueOffset) {
            this.tag = tag; this.type = type; this.count = count;
            this.entryOffset = entryOffset; this.valueOffset = valueOffset;
        }
    }

    static final class Tiff {
        final byte[] data;
        final boolean little;
        final Map<Integer, Entry> primary = new HashMap<Integer, Entry>();
        final Map<Integer, Entry> gps = new HashMap<Integer, Entry>();
        final Map<Integer, Entry> thumbnailTags = new HashMap<Integer, Entry>();
        int root, nextField;
        Tiff(byte[] payload) throws IOException {
            data = payload;
            if (data.length < 14 || !startsExif(data, 0, data.length)) throw new IOException("Invalid EXIF");
            little = data[6] == 73 && data[7] == 73;
            if (!little && !(data[6] == 77 && data[7] == 77)) throw new IOException("Invalid TIFF byte order");
            if (u16(8) != 42) throw new IOException("Invalid TIFF header");
            root = offset(u32(10));
            Set<Integer> visited = new HashSet<Integer>();
            parseIfd(root, primary, visited, 0, true);
        }
        private int offset(long relative) throws IOException {
            long absolute = relative + 6;
            if (relative < 8 || absolute > data.length - 2) throw new IOException("Invalid TIFF offset");
            return (int) absolute;
        }
        private void parseIfd(int p, Map<Integer, Entry> tags, Set<Integer> visited, int depth, boolean main) throws IOException {
            if (depth > 8 || !visited.add(p)) return;
            int n = u16(p);
            long end = (long) p + 2 + n * 12L;
            if (end + 4 > data.length) throw new IOException("Truncated IFD");
            for (int i = 0; i < n; i++) {
                int q = p + 2 + i * 12, tag = u16(q), type = u16(q + 2);
                long count = u32(q + 4);
                if (type < 1 || type >= TYPE_SIZE.length || count == 0) continue;
                long size = count * TYPE_SIZE[type];
                long val = size <= 4 ? q + 8 : u32(q + 8) + 6;
                if (val < 6 || val + size > data.length || size > data.length) continue;
                Entry entry = new Entry(tag, type, count, q, (int) val);
                if (!tags.containsKey(tag)) tags.put(tag, entry);
            }
            if (main) {
                nextField = (int) end;
                Entry exif = tags.get(34665), location = tags.get(34853);
                if (exif != null) parseOptional(integerEntry(exif), primary, visited, depth + 1);
                if (location != null) parseOptional(integerEntry(location), gps, visited, depth + 1);
                long next = u32(nextField);
                if (next != 0) parseOptional(next, thumbnailTags, visited, depth + 1);
            }
        }
        private void parseOptional(long relative, Map<Integer, Entry> tags, Set<Integer> visited, int depth) {
            try { parseIfd(offset(relative), tags, visited, depth, false); }
            catch (IOException malformed) { /* Isolate malformed optional subdirectories. */ }
        }
        int integer(int tag, boolean isGps, int fallback) {
            Object value = value(tag, isGps);
            return value instanceof Integer ? ((Integer) value).intValue() : fallback;
        }
        Object value(int tag, boolean isGps) {
            Entry e = (isGps ? gps : primary).get(tag);
            if (e == null) return null;
            switch (e.type) {
                case 1: case 3: case 4: case 6: case 8: case 9: return Integer.valueOf((int) integerEntry(e));
                case 5: case 10: case 11: case 12: return Double.valueOf(real(e, 0));
                case 2:
                    int end = e.valueOffset, max = end + (int) e.count;
                    while (end < max && data[end] != 0) end++;
                    return Arrays.copyOfRange(data, e.valueOffset, end);
                default: return null; // Original JNI does not expose UNDEFINED blobs as values.
            }
        }
        double[] triple(int tag, boolean isGps) {
            Entry e = (isGps ? gps : primary).get(tag);
            if (e == null || e.count < 3 || !(e.type == 5 || e.type == 10 || e.type == 12)) return null;
            return new double[]{real(e, 0), real(e, 1), real(e, 2)};
        }
        private long integerEntry(Entry e) {
            int p = e.valueOffset;
            switch (e.type) {
                case 1: return u8(data[p]);
                case 6: return data[p];
                case 3: return u16(p);
                case 8: return (short) u16(p);
                case 4: return u32(p);
                case 9: return (int) u32(p);
                default: return 0;
            }
        }
        private double real(Entry e, int index) {
            int p = e.valueOffset + TYPE_SIZE[e.type] * index;
            if (e.type == 5 || e.type == 10) {
                double num = e.type == 5 ? u32(p) : (int) u32(p);
                double den = e.type == 5 ? u32(p + 4) : (int) u32(p + 4);
                return den == 0 ? 0 : num / den;
            }
            if (e.type == 11) return Float.intBitsToFloat((int) u32(p));
            if (e.type == 12) {
                long lo = u32(little ? p : p + 4), hi = u32(little ? p + 4 : p);
                return Double.longBitsToDouble((hi << 32) | lo);
            }
            return 0;
        }
        byte[] thumbnail() {
            Entry offset = thumbnailTags.get(513), length = thumbnailTags.get(514);
            if (offset == null || length == null) return null;
            long p = integerEntry(offset) + 6, n = integerEntry(length);
            if (p < 14 || n <= 0 || p + n > data.length) return null;
            return Arrays.copyOfRange(data, (int) p, (int) (p + n));
        }
        byte[] withOrientation(int orientation, boolean stripThumbnail) throws IOException {
            byte[] out;
            // Only an IFD0 orientation controls the displayed main image.
            int count = u16(root);
            int orientationEntry = -1;
            for (int i = 0; i < count; i++) {
                int p = root + 2 + i * 12;
                if (u16(p) == 274) { orientationEntry = p; break; }
            }
            if (orientationEntry >= 0) {
                out = data.clone();
                putOrientation(out, orientationEntry, orientation);
                if (stripThumbnail) put32(out, nextField, 0);
            } else {
                // Append a replacement IFD0. Existing out-of-line data and opaque
                // MakerNote offsets remain unchanged, unlike TIFF reserialization.
                int newRoot = (data.length + 1) & ~1;
                long length = (long) newRoot + 2 + (count + 1) * 12L + 4;
                if (length > 65533 || count == 65535) throw new IOException("EXIF has no room for orientation");
                out = Arrays.copyOf(data, (int) length);
                put16(out, newRoot, count + 1);
                int insert = 0;
                while (insert < count && u16(root + 2 + insert * 12) < 274) insert++;
                System.arraycopy(data, root + 2, out, newRoot + 2, insert * 12);
                putOrientation(out, newRoot + 2 + insert * 12, orientation);
                System.arraycopy(data, root + 2 + insert * 12, out, newRoot + 2 + (insert + 1) * 12, (count - insert) * 12);
                put32(out, newRoot + 2 + (count + 1) * 12, stripThumbnail ? 0 : u32(nextField));
                put32(out, 10, newRoot - 6);
            }
            return out;
        }
        void patchDimension(int tag, int value) {
            Entry e = primary.get(tag);
            if (e == null || e.count != 1 || value <= 0) return;
            if (e.type == 3 && value <= 65535) put16(data, e.valueOffset, value);
            if (e.type == 4) put32(data, e.valueOffset, value);
        }
        private void putOrientation(byte[] out, int p, int orientation) {
            Arrays.fill(out, p, p + 12, (byte) 0);
            put16(out, p, 274); put16(out, p + 2, 3);
            put32(out, p + 4, 1); put16(out, p + 8, orientation);
        }
        private int u16(int p) {
            return little ? u8(data[p]) | (u8(data[p + 1]) << 8) : be16(data, p);
        }
        private long u32(int p) {
            return little ? (long)u8(data[p]) | ((long)u8(data[p+1]) << 8) | ((long)u8(data[p+2]) << 16) | ((long)u8(data[p+3]) << 24)
                    : ((long)u8(data[p]) << 24) | ((long)u8(data[p+1]) << 16) | ((long)u8(data[p+2]) << 8) | u8(data[p+3]);
        }
        private void put16(byte[] out, int p, int value) {
            out[p] = (byte)(little ? value : value >> 8);
            out[p + 1] = (byte)(little ? value >> 8 : value);
        }
        private void put32(byte[] out, int p, long value) {
            for (int i = 0; i < 4; i++) out[p + i] = (byte)(value >> ((little ? i : 3 - i) * 8));
        }
    }
}
