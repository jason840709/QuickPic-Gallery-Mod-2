// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import java.io.File;

/** Narrow single-file deletion result for the two existing local-image callers. */
public final class FileDelete {
    private static final String AUTHORITY = "com.android.externalstorage.documents";
    private FileDelete() {}

    /** -1: use the original helper; 0: keep original metadata cleanup; 1: provider deleted. */
    public static int tryDelete(Context context, File file) {
        if (Build.VERSION.SDK_INT < 30 || context == null || file == null) return -1;
        try {
            // Other storage and folders retain the original deletion path without extra work.
            if (!FolderDelete.isSecondaryPath(file) || file.isDirectory()) return -1;
            if (file.delete() || !file.exists()) return 0;
            // Never turn a failed unlink of a symlink/alias into deletion of its target.
            if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) return -1;
            // Keep direct unlink first on newer Android; only try indexed media after failure.
            int fast = FastMediaDelete.tryDelete(context, file);
            if (fast >= 0) return fast;
            Uri document = FolderDelete.documentFor(context, file);
            if (document == null || !"content".equals(document.getScheme())
                    || !AUTHORITY.equals(document.getAuthority())) return -1;
            return DocumentsContract.deleteDocument(context.getContentResolver(), document) ? 1 : -1;
        } catch (Exception failure) {
            return -1;
        }
    }
}
