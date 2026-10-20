// SPDX-License-Identifier: MIT
package com.alensw.jni;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Controlled date inputs; production MediaDates is tested separately. */
public final class MediaDates {
    private static final Map<String, Long> created = new HashMap<String, Long>();
    private static final Set<String> recent = new HashSet<String>();
    public static void register(File file, long time, boolean isNew) {
        created.put(file.getPath(), Long.valueOf(time));
        if (isNew) recent.add(file.getPath()); else recent.remove(file.getPath());
    }
    public static long created(File file) {
        Long value = created.get(file.getPath());
        return value == null ? 0 : value.longValue();
    }
    public static boolean isNew(File file) { return recent.contains(file.getPath()); }
}
