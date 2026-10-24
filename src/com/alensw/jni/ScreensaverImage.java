// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.CRC32;

/** Creates the centered, opaque white-background PNG used by the screensaver bridge. */
public final class ScreensaverImage {
    public static final int MAX_AXIS = 8192;
    public static final long MAX_PIXELS = 32000000L;
    private static final long MEMORY_RESERVE = 16L * 1024 * 1024;
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private ScreensaverImage() {}

    /** Absolute allocation guard; available memory is checked separately before decoding. */
    public static boolean validSize(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_AXIS && height <= MAX_AXIS
                && (long) width * height <= MAX_PIXELS;
    }

    /** An exact, possibly fractional crop in the upright source's pixel coordinates. */
    public static final class Crop {
        public final double left;
        public final double top;
        public final double width;
        public final double height;

        private Crop(double left, double top, double width, double height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    /** Largest centered rectangle with the requested aspect ratio; no integer-ratio distortion. */
    public static Crop centeredCrop(int sourceWidth, int sourceHeight, int width, int height) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid crop dimensions");
        }
        double cropWidth = sourceWidth;
        double cropHeight = sourceHeight;
        if ((long) sourceWidth * height > (long) sourceHeight * width) {
            cropWidth = (double) sourceHeight * width / height;
        } else {
            cropHeight = (double) sourceWidth * height / width;
        }
        return new Crop((sourceWidth - cropWidth) / 2.0,
                (sourceHeight - cropHeight) / 2.0, cropWidth, cropHeight);
    }

    /**
     * Keeps the source and all previous exports. A final PNG becomes visible only after
     * compression, fsync and validation of a temporary file in the same directory.
     * Run on the existing export worker. OutOfMemoryError is left to that caller.
     */
    public static File prepare(String path, int width, int height, File outputDirectory)
            throws IOException {
        if (!validSize(width, height)) {
            throw new IOException("尺寸須為 1～8192 像素，總像素不可超過 3200 萬");
        }
        if (path == null || path.length() == 0) throw new IOException("來源圖片路徑是空的");
        File source = new File(path);
        if (!source.isFile() || !source.canRead()) throw new IOException("來源圖片無法讀取");
        if (outputDirectory == null) throw new IOException("未指定屏保圖片資料夾");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        bounds.inScaled = false;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("圖片格式不受支援或檔案已損壞");
        }
        int rawWidth = bounds.outWidth;
        int rawHeight = bounds.outHeight;
        int orientation = readOrientation(source.getAbsolutePath());
        boolean swap = orientation >= 5 && orientation <= 8;
        Crop uprightCrop = centeredCrop(swap ? rawHeight : rawWidth,
                swap ? rawWidth : rawHeight, width, height);
        Crop rawCrop = centeredCrop(rawWidth, rawHeight,
                swap ? height : width, swap ? width : height);
        Rect region = enclosing(rawCrop, rawWidth, rawHeight);
        int sample = decodeSample(rawCrop, swap ? height : width, swap ? width : height);

        Bitmap decoded = null;
        Bitmap output = null;
        File temporary = null;
        try {
            Decoded part = decode(source, rawWidth, rawHeight, region, sample, width, height);
            decoded = part.bitmap;
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            // Verified legacy behavior: alpha is composited onto white for the reader.
            canvas.drawColor(0xffffffff);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
                    | Paint.DITHER_FLAG);
            canvas.drawBitmap(decoded, transform(part.region, decoded.getWidth(),
                    decoded.getHeight(), rawWidth, rawHeight, orientation, uprightCrop,
                    width, height), paint);
            output.setHasAlpha(false);
            decoded.recycle();
            decoded = null;

            if (!outputDirectory.isDirectory()
                    && !outputDirectory.mkdirs() && !outputDirectory.isDirectory()) {
                throw new IOException("無法建立屏保圖片資料夾");
            }
            File noMedia = new File(outputDirectory, ".nomedia");
            if (!noMedia.isFile() && !noMedia.createNewFile() && !noMedia.isFile()) {
                throw new IOException("無法保留屏保資料夾的 .nomedia");
            }
            String filename = "screensaver-" + width + "x" + height + "-"
                    + UUID.randomUUID().toString() + ".png";
            File result = new File(outputDirectory, filename);
            temporary = File.createTempFile("." + filename + "-", ".tmp", outputDirectory);
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw new IOException("PNG 壓縮失敗");
                }
                stream.flush();
                stream.getFD().sync();
            }
            verifyPng(temporary, width, height);
            // UUID names separate concurrent exports. Never remove or replace an old output.
            if (result.exists() || !temporary.renameTo(result)) {
                throw new IOException("無法保存屏保 PNG");
            }
            temporary = null;
            return result;
        } finally {
            if (decoded != null) decoded.recycle();
            if (output != null) output.recycle();
            if (temporary != null) temporary.delete();
        }
    }

    private static int readOrientation(String path) {
        try {
            int orientation = new ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return orientation >= 1 && orientation <= 8 ? orientation : 1;
        } catch (IOException e) {
            // Android's platform EXIF reader does not support every BitmapFactory format.
            return 1;
        }
    }

    private static Rect enclosing(Crop crop, int width, int height) {
        return new Rect(Math.max(0, (int) Math.floor(crop.left)),
                Math.max(0, (int) Math.floor(crop.top)),
                Math.min(width, (int) Math.ceil(crop.left + crop.width)),
                Math.min(height, (int) Math.ceil(crop.top + crop.height)));
    }

    private static int decodeSample(Crop crop, int width, int height) {
        int sample = 1;
        while ((long) sample * 2 <= Integer.MAX_VALUE
                && crop.width / (sample * 2L) >= width
                && crop.height / (sample * 2L) >= height) {
            sample *= 2;
        }
        return sample;
    }

    private static final class Decoded {
        final Bitmap bitmap;
        final Rect region;

        Decoded(Bitmap bitmap, Rect region) {
            this.bitmap = bitmap;
            this.region = region;
        }
    }

    private static Decoded decode(File source, int rawWidth, int rawHeight, Rect region,
            int sample, int width, int height) throws IOException {
        checkMemory(region.width(), region.height(), sample, width, height);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        BitmapRegionDecoder decoder = null;
        IOException regionFailure = null;
        try {
            decoder = BitmapRegionDecoder.newInstance(source.getAbsolutePath(), false);
        } catch (IOException e) {
            regionFailure = e;
        } catch (RuntimeException e) {
            regionFailure = new IOException("無法開啟圖片裁切區", e);
        }
        if (decoder != null) {
            try {
                if (decoder.getWidth() != rawWidth || decoder.getHeight() != rawHeight) {
                    throw new IOException("來源圖片已變更，請重新產生屏保圖片");
                }
                Bitmap bitmap = decoder.decodeRegion(region, options);
                if (bitmap != null) return new Decoded(bitmap, region);
            } catch (RuntimeException e) {
                regionFailure = new IOException("無法解碼圖片裁切區", e);
            } finally {
                decoder.recycle();
            }
        }
        // Some legacy formats cannot decode a region. Keep the same quality-preserving
        // sample, and reject an unsafe full-image allocation instead of silently blurring it.
        checkMemory(rawWidth, rawHeight, sample, width, height);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        } catch (RuntimeException e) {
            throw new IOException("圖片解碼失敗", e);
        }
        if (bitmap == null) throw new IOException("圖片解碼失敗", regionFailure);
        return new Decoded(bitmap, new Rect(0, 0, rawWidth, rawHeight));
    }

    private static void checkMemory(int decodeWidth, int decodeHeight, int sample,
            int width, int height) throws IOException {
        long sampledWidth = (decodeWidth + sample - 1L) / sample;
        long sampledHeight = (decodeHeight + sample - 1L) / sample;
        long pixels = sampledWidth * sampledHeight;
        long outputPixels = (long) width * height;
        // A power-of-two sample retains at least the target detail. Region decoding
        // normally needs fewer than four target-sized pixel buffers, plus the output.
        if (pixels > MAX_PIXELS * 4 + MAX_AXIS * 4L + 4) {
            throw new IOException("圖片過大，無法安全解碼；請調低輸出尺寸或改用其他圖片格式");
        }
        long required = (pixels + outputPixels) * 4 + MEMORY_RESERVE;
        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        if (required > available) {
            throw new IOException("可用記憶體不足，請調低屏保圖片尺寸");
        }
    }

    private static Matrix transform(Rect region, int decodedWidth, int decodedHeight,
            int rawWidth, int rawHeight, int orientation, Crop crop, int width, int height) {
        double a = 1, b = 0, c = 0, d = 0, e = 1, f = 0;
        switch (orientation) {
            case 2: a = -1; c = rawWidth; break;
            case 3: a = -1; c = rawWidth; e = -1; f = rawHeight; break;
            case 4: e = -1; f = rawHeight; break;
            case 5: a = 0; b = 1; d = 1; e = 0; break;
            case 6: a = 0; b = -1; c = rawHeight; d = 1; e = 0; break;
            case 7: a = 0; b = -1; c = rawHeight;
                    d = -1; e = 0; f = rawWidth; break;
            case 8: a = 0; b = 1; d = -1; e = 0; f = rawWidth; break;
            default: break;
        }
        double sx = (double) region.width() / decodedWidth;
        double sy = (double) region.height() / decodedHeight;
        double ox = width / crop.width;
        double oy = height / crop.height;
        Matrix matrix = new Matrix();
        matrix.setValues(new float[] {
            (float) (ox * a * sx), (float) (ox * b * sy),
            (float) (ox * (a * region.left + b * region.top + c - crop.left)),
            (float) (oy * d * sx), (float) (oy * e * sy),
            (float) (oy * (d * region.left + e * region.top + f - crop.top)),
            0, 0, 1
        });
        return matrix;
    }

    private static void verifyPng(File file, int width, int height) throws IOException {
        byte[] header = new byte[33];
        try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
            stream.readFully(header);
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (header[i] != PNG_MAGIC[i]) throw new IOException("輸出檔不是有效 PNG");
        }
        if (readInt(header, 8) != 13 || readInt(header, 12) != 0x49484452
                || readInt(header, 16) != width || readInt(header, 20) != height) {
            throw new IOException("PNG 尺寸或標頭錯誤");
        }
        CRC32 crc = new CRC32();
        crc.update(header, 12, 17);
        if (crc.getValue() != (readInt(header, 29) & 0xffffffffL)) {
            throw new IOException("PNG 標頭校驗失敗");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        bounds.inScaled = false;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth != width || bounds.outHeight != height) {
            throw new IOException("PNG 實際尺寸錯誤");
        }
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 255) << 24 | (data[offset + 1] & 255) << 16
                | (data[offset + 2] & 255) << 8 | data[offset + 3] & 255;
    }
}
