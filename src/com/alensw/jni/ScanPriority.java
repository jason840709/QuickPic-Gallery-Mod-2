// SPDX-License-Identifier: MIT
package com.alensw.jni;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Stack;

/** Orders an already filtered scan wave; does not discover or publish folders. */
public final class ScanPriority {
    private ScanPriority() {}

    private static final class Ranked {
        final File folder;
        final boolean recent;
        final long time;
        final String path;

        Ranked(File value) {
            folder = value;
            recent = MediaDates.isNew(value);
            long modified;
            try {
                modified = value.lastModified();
            } catch (SecurityException ignored) {
                modified = 0;
            }
            time = Math.max(MediaDates.created(value), modified);
            path = value.getPath();
        }
    }

    private static final Comparator<Ranked> PRIORITY = new Comparator<Ranked>() {
        @Override public int compare(Ranked first, Ranked second) {
            if (first.recent != second.recent) return first.recent ? -1 : 1;
            int time = Long.compare(second.time, first.time);
            return time != 0 ? time : first.path.compareTo(second.path);
        }
    };

    /**
     * The old scanner and its workers exchange only push/pop/size/addAll calls.
     * Keep that descriptor while ranking each newly enqueued folder once. A
     * two-folder batch lets newly found descendants run before older siblings.
     */
    private static final class Pending extends Stack<File> {
        private static final long serialVersionUID = 1L;
        private final PriorityQueue<Ranked> queue = new PriorityQueue<Ranked>(128, PRIORITY);
        @Override public synchronized File push(File value) {
            queue.add(new Ranked(value));
            return value;
        }
        @Override public synchronized File pop() {
            return queue.remove().folder;
        }
        @Override public synchronized int size() { return queue.size(); }
        @Override public synchronized boolean isEmpty() { return queue.isEmpty(); }
        @Override public synchronized boolean addAll(Collection<? extends File> values) {
            for (File value : values) push(value);
            return !values.isEmpty();
        }
        @Override public synchronized void clear() { queue.clear(); }
    }

    public static Stack<File> pending() { return new Pending(); }

    /**
     * Snapshot each directory's metadata once before the two workers receive it.
     * Reordering File.listFiles() alone would be reversed by the legacy Stack.
     * The gallery's independently selected display comparator remains unchanged.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void order(ArrayList folders) {
        if (folders == null || folders.size() < 2) return;
        try {
            ArrayList<Ranked> ranked = new ArrayList<Ranked>(folders.size());
            for (Object value : folders) {
                if (!(value instanceof File)) return;
                ranked.add(new Ranked((File) value));
            }
            Collections.sort(ranked, PRIORITY);
            for (int index = 0; index < ranked.size(); index++) {
                folders.set(index, ranked.get(index).folder);
            }
        } catch (RuntimeException ignored) {
            // An unavailable metadata source must not stop the existing scan.
        }
    }
}
