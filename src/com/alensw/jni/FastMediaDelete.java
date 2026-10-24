// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;

/** One selected, authorized secondary-storage file; success retains original metadata cleanup. */
public final class FastMediaDelete {
    private static final String AUTHORITY = "com.android.externalstorage.documents";
    private static final String[] PROJECTION = {"_id", "_data", "media_type"};
    private static final String DELETE_SELECTION = "_data = ? COLLATE BINARY AND media_type IN (1,3)";

    private FastMediaDelete() { }

    private static int skip(int reason) {
        DeleteTiming.fastGuard(reason);
        DeleteTiming.fastOutcome(0);
        return -1;
    }

    /**
     * 0 only when lstat confirms ENOENT after this file's MediaStore delete attempt;
     * the caller must retain original metadata cleanup. -1 keeps the original fallback.
     */
    public static int tryDelete(Context context, File file) {
        int storage = DeleteTiming.classifyStorage(file);
        ContentResolver resolver;
        String path;
        try {
            if (Build.VERSION.SDK_INT < 21 || context == null || file == null) return skip(0);
            if (!FolderDelete.isSecondaryPath(file)) return skip(storage == 1 ? 1 : 2);
            if (file.isDirectory()) return skip(3);
            try {
                if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) return skip(4);
            } catch (Exception ignored) { return skip(4); }
            // A MediaStore row is not authorization: require an existing persisted write tree first.
            Uri document = FolderDelete.documentFor(context, file);
            if (document == null || !"content".equals(document.getScheme())
                    || !AUTHORITY.equals(document.getAuthority())) {
                return skip(5);
            }
            path = file.getAbsolutePath();
            resolver = context.getContentResolver();
        } catch (Exception ignored) {
            return skip(6);
        }

        Uri collection = MediaStore.Files.getContentUri("external");
        long id = -1;
        int mediaType = 0;
        int exactRows = 0;
        DeleteTiming.beginFastLookup();
        try {
            Cursor cursor = resolver.query(collection, PROJECTION, "_data = ?", new String[] {path}, null);
            if (cursor == null) throw new IllegalStateException();
            try {
                while (cursor.moveToNext()) {
                    // The index can be NOCASE; only the exact Java path can select a row.
                    if (!path.equals(cursor.getString(1))) continue;
                    exactRows++;
                    id = cursor.getLong(0);
                    mediaType = cursor.getInt(2);
                }
            } finally {
                cursor.close();
            }
        } catch (Exception ignored) {
            DeleteTiming.fastOutcome(1);
            return -1;
        } finally {
            DeleteTiming.endFastLookup();
        }
        if (exactRows == 0) {
            DeleteTiming.fastOutcome(2);
            return -1;
        }
        if (exactRows != 1) {
            DeleteTiming.fastOutcome(3);
            return -1;
        }
        // In particular, hidden/unindexed type-zero files retain the existing SAF fallback.
        if (id <= 0 || (mediaType != 1 && mediaType != 3)) {
            DeleteTiming.fastOutcome(4);
            return -1;
        }
        DeleteTiming.fastOutcome(5);

        DeleteTiming.beginFastDelete();
        try {
            // The ID, BINARY path and current media type all constrain this single request.
            resolver.delete(ContentUris.withAppendedId(collection, id), DELETE_SELECTION, new String[] {path});
        } catch (Exception ignored) {
            // A provider can throw after unlinking. Verify the physical result even on error.
            DeleteTiming.fastOutcome(6);
        } finally {
            DeleteTiming.endFastDelete();
        }

        boolean gone = false;
        DeleteTiming.beginFastVerify();
        try {
            Os.lstat(path);
        } catch (ErrnoException result) {
            gone = result.errno == OsConstants.ENOENT;
        } catch (Exception ignored) {
            // Permission denial, ENOTDIR and all other uncertainty preserve the original fallback.
        } finally {
            DeleteTiming.endFastVerify();
        }
        DeleteTiming.fastOutcome(gone ? 7 : 8);
        // Even after unlink, a provider exception may leave a stale row. Keep caller cleanup.
        return gone ? 0 : -1;
    }
}
