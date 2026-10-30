// SPDX-License-Identifier: MIT
package com.alensw.jni;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/** Whole-file validation before passing a disposable PNG to the OEM importer. */
public final class ScreensaverPng {
    private static final byte[] MAGIC = {(byte)137,80,78,71,13,10,26,10};
    private ScreensaverPng() {}

    public static void verify(File file, int width, int height, long maximumBytes)
            throws IOException {
        if (file == null || !file.isFile() || !file.canRead()
                || file.length() < 57 || file.length() > maximumBytes) {
            throw new IOException("屏保圖片不完整或超過大小限制");
        }
        final long total = file.length();
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            for (byte b : MAGIC) if (in.readUnsignedByte() != (b & 255)) {
                throw new IOException("屏保圖片不是 PNG");
            }
            long consumed = 8;
            boolean first = true, data = false, dataEnded = false;
            byte[] buffer = new byte[16384];
            while (consumed + 12 <= total) {
                long length = in.readInt() & 0xffffffffL;
                int type = in.readInt();
                if (length > total - consumed - 12) throw new IOException("PNG 區段不完整");
                if (first && (type != 0x49484452 || length != 13)) {
                    throw new IOException("PNG 缺少圖片標頭");
                }
                if (!first && type == 0x49484452) throw new IOException("PNG 標頭重複");
                if (type == 0x49444154) {
                    if (dataEnded || length == 0) throw new IOException("PNG 圖片資料錯誤");
                    data = true;
                } else if (data) dataEnded = true;
                CRC32 crc = new CRC32();
                crc.update(type >>> 24); crc.update(type >>> 16);
                crc.update(type >>> 8); crc.update(type);
                long left = length;
                if (first) {
                    byte[] header = new byte[13]; in.readFully(header); crc.update(header);
                    if (integer(header,0) != width || integer(header,4) != height
                            || (header[8] & 255) != 8 || (header[9] & 255) != 2
                            || header[10] != 0 || header[11] != 0 || header[12] != 0) {
                        throw new IOException("PNG 必須是指定尺寸的 8 位元、不透明 RGB 圖片");
                    }
                    left = 0;
                }
                while (left > 0) {
                    int count = (int)Math.min(left, buffer.length);
                    in.readFully(buffer,0,count); crc.update(buffer,0,count); left -= count;
                }
                if (crc.getValue() != (in.readInt() & 0xffffffffL)) {
                    throw new IOException("PNG 區段校驗失敗");
                }
                consumed += length + 12;
                first = false;
                if (type == 0x49454e44) {
                    if (length != 0 || !data || consumed != total || in.read() != -1) {
                        throw new IOException("PNG 結尾錯誤");
                    }
                    return;
                }
                // Reject unknown critical chunks; the producer emits ordinary static RGB.
                if ((type & 0x20000000) == 0 && type != 0x49484452
                        && type != 0x49444154 && type != 0x504c5445) {
                    throw new IOException("PNG 包含不支援的主要區段");
                }
            }
            throw new IOException("PNG 缺少結尾");
        }
    }

    private static int integer(byte[] b, int offset) {
        return (b[offset]&255)<<24 | (b[offset+1]&255)<<16
                | (b[offset+2]&255)<<8 | b[offset+3]&255;
    }
}
