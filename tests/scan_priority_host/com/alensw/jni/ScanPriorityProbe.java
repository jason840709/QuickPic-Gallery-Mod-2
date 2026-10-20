// SPDX-License-Identifier: MIT
package com.alensw.jni;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public final class ScanPriorityProbe {
    private static int checks;
    private static void check(boolean good, String name) {
        checks++;
        if (!good) throw new AssertionError(name);
    }
    private static final class Folder extends File {
        final long modified;
        int reads;
        Folder(String path, long modified, long created, boolean recent) {
            super(path); this.modified = modified;
            MediaDates.register(this, created, recent);
        }
        @Override public long lastModified() { reads++; return modified; }
    }
    private static int siblingVisitsBeforeComic(boolean priority) {
        Folder root = new Folder("/library", 0, 0, false);
        Folder fresh = new Folder("/library/new-download", 1, 10000, true);
        Folder comic = new Folder("/library/new-download/chapter", 1, 10000, true);
        List<File> siblings = new ArrayList<File>();
        for (int i = 0; i < 100; i++)
            siblings.add(new Folder(String.format("/library/old-%03d", i), 9000, 1, false));
        siblings.add(fresh);
        Map<File, List<File>> children = new HashMap<File, List<File>>();
        children.put(root, siblings);
        children.put(fresh, Arrays.<File>asList(comic));
        Stack<File> pending = priority ? ScanPriority.pending() : new Stack<File>();
        pending.push(root);
        int old = 0;
        while (!pending.isEmpty()) {
            List<File> wave = new ArrayList<File>();
            // The reference drains the whole depth, as the original v.a(e).
            // Production pending traversal dispatches at most two eligible items.
            while (!pending.isEmpty() && (!priority || wave.size() < 2)) wave.add(pending.pop());
            for (File folder : wave) {
                if (folder.equals(comic)) return old;
                if (folder.getName().startsWith("old-")) old++;
                List<File> next = children.get(folder);
                if (next != null) pending.addAll(next);
            }
        }
        throw new AssertionError("Comic was never discovered");
    }
    public static void main(String[] args) throws Exception {
        Folder old = new Folder("/old", 9000, 0, false);
        Folder arrival = new Folder("/arrival", 1, 10000, false);
        Folder freshA = new Folder("/new-a", 1, 0, true);
        Folder freshB = new Folder("/new-b", 2, 0, true);
        Folder tieA = new Folder("/tie-a", 3, 0, false);
        Folder tieB = new Folder("/tie-b", 3, 0, false);
        ArrayList<File> wave = new ArrayList<File>(Arrays.<File>asList(old, tieB, freshA, tieA, arrival, freshB));
        ScanPriority.order(wave);
        check(wave.equals(Arrays.<File>asList(freshB, freshA, arrival, old, tieA, tieB)), "new/time/path priority");
        for (File value : wave) check(((Folder) value).reads == 1, "metadata read once in ordering");
        ArrayList<Object> invalid = new ArrayList<Object>(Arrays.<Object>asList(old, "unsupported", arrival));
        ArrayList<Object> original = new ArrayList<Object>(invalid);
        ScanPriority.order(invalid);
        check(invalid.equals(original), "invalid list remains unchanged");
        ScanPriority.order(null); ScanPriority.order(new ArrayList<File>());

        Stack<File> pending = ScanPriority.pending();
        Folder olderSibling = new Folder("/pending-old", 999, 0, false);
        Folder newRoot = new Folder("/pending-new", 10, 0, true);
        Folder newChild = new Folder("/pending-new/child", 20, 0, true);
        pending.push(olderSibling); pending.addAll(Arrays.<File>asList(newRoot));
        check(pending.size() == 2 && pending.pop() == newRoot, "Stack bridges dispatch new root first");
        pending.addAll(Arrays.<File>asList(newChild));
        check(pending.pop() == newChild, "new descendant bypasses older pending sibling");
        check(pending.pop() == olderSibling && pending.isEmpty(), "older sibling is retained");
        check(newRoot.reads == 1 && newChild.reads == 1 && olderSibling.reads == 1, "heap metadata read once on enqueue");

        final Stack<File> concurrent = ScanPriority.pending();
        final List<File> first = new ArrayList<File>(), second = new ArrayList<File>();
        for (int i = 0; i < 50; i++) {
            first.add(new Folder("/worker-a/" + i, i, 0, false));
            second.add(new Folder("/worker-b/" + i, i, 0, false));
        }
        Thread a = new Thread(new Runnable() { public void run() { concurrent.addAll(first); } });
        Thread b = new Thread(new Runnable() { public void run() { concurrent.addAll(second); } });
        a.start(); b.start(); a.join(); b.join();
        check(concurrent.size() == 100, "both worker child batches retained");
        long previous = Long.MAX_VALUE;
        while (!concurrent.isEmpty()) {
            long time = concurrent.pop().lastModified();
            check(time <= previous, "concurrent heap priority"); previous = time;
        }
        concurrent.clear();

        int bfs = siblingVisitsBeforeComic(false), prioritized = siblingVisitsBeforeComic(true);
        check(bfs == 100, "reference breadth wave blocks nested comic behind 100 old siblings");
        check(prioritized == 1, "bounded priority queue publishes nested comic after one old sibling in fixture");
        System.out.println("{\"checks\":" + checks
            + ",\"old_bfs_siblings_before_comic\":" + bfs
            + ",\"priority_siblings_before_comic\":" + prioritized
            + ",\"two_worker_same_batch_conservative_bound\":2}");
    }
}
