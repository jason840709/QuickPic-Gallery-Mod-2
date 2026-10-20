// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Additive date modes; the original controllers still own all saved settings. */
public final class DateSort {
    public static final int CREATED_ID = 0x00f0a030;
    private static final int MODIFIED_ID = 0x7f090075;
    private static final int CREATED_DESC = 9;
    private static final int CREATED_ASC = 10;
    private static final ConcurrentHashMap<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> URI_CREATED = new ConcurrentHashMap<>();
    private static volatile ContentResolver resolver;
    private static volatile Field naturalField;
    private static volatile boolean naturalResolved;
    private static final Comparator<Object> FOLDER_MODIFIED_ASC = new FolderDates(false, false);
    private static final Comparator<Object> FOLDER_MODIFIED_DESC = new FolderDates(false, true);
    private static final Comparator<Object> FOLDER_CREATED_ASC = new FolderDates(true, false);
    private static final Comparator<Object> FOLDER_CREATED_DESC = new FolderDates(true, true);
    private static final Comparator<Object> LOCAL_MODIFIED_ASC = new LocalDates(false, false);
    private static final Comparator<Object> LOCAL_MODIFIED_DESC = new LocalDates(false, true);
    private static final Comparator<Object> LOCAL_CREATED_ASC = new LocalDates(true, false);
    private static final Comparator<Object> LOCAL_CREATED_DESC = new LocalDates(true, true);
    private static final Comparator<Object> COMMON_MODIFIED_ASC = new CommonDates(false, false);
    private static final Comparator<Object> COMMON_MODIFIED_DESC = new CommonDates(false, true);
    private static final Comparator<Object> COMMON_CREATED_ASC = new CommonDates(true, false);
    private static final Comparator<Object> COMMON_CREATED_DESC = new CommonDates(true, true);

    private DateSort() { }

    public static void configure(Context context) {
        if (context == null) return;
        try {
            Context application = context.getApplicationContext();
            resolver = (application == null ? context : application).getContentResolver();
        } catch (RuntimeException ignored) {
            // Optional provider metadata must never prevent app startup.
        }
    }

    /** Called after the original sort menu inflation and before its ID filter. */
    public static int[] prepareMenu(Context context, Menu menu, int[] allowed) {
        boolean dates = false;
        for (int id : allowed) if (id == MODIFIED_ID) dates = true;
        if (!dates) return allowed;
        MenuItem modified = menu.findItem(MODIFIED_ID);
        if (menu.findItem(CREATED_ID) == null) {
            menu.add(0, CREATED_ID, modified == null ? 0 : modified.getOrder(),
                    createdLabel(context));
        }
        for (int id : allowed) if (id == CREATED_ID) return allowed;
        int[] expanded = Arrays.copyOf(allowed, allowed.length + 1);
        expanded[allowed.length] = CREATED_ID;
        return expanded;
    }

    private static String createdLabel(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        if ("zh".equals(language)) {
            return "CN".equals(locale.getCountry()) ? "按创建日期（加入时间）" : "按建立日期（加入時間）";
        }
        return "By creation date (date added)";
    }

    public static int nextCreationMode(int current) {
        return current == CREATED_DESC ? CREATED_ASC : CREATED_DESC;
    }

    public static int creationModeForItem(MenuItem item, int current) {
        return item != null && item.getItemId() == CREATED_ID ? nextCreationMode(current) : -1;
    }

    public static int selected(int mode, int original) {
        return isCreation(mode) ? CREATED_ID : original;
    }

    public static boolean reverse(int mode, boolean original) {
        return isCreation(mode) ? mode == CREATED_ASC : original;
    }

    public static boolean isCommonDate(int mode) {
        return mode == 1 || mode == 3 || isCreation(mode);
    }

    public static boolean isFolderDate(int mode) {
        return mode == 3 || mode == 4 || isCreation(mode);
    }

    public static boolean isCreation(int mode) {
        return mode == CREATED_DESC || mode == CREATED_ASC;
    }

    public static Comparator<Object> folderComparator(ArrayList<?> items, int mode, Comparator<Object> original) {
        if (mode != 3 && mode != 4 && !isCreation(mode)) return original;
        final boolean created = isCreation(mode);
        final boolean descending = mode == 4 || mode == CREATED_DESC;
        for (Object item : items) {
            if (item instanceof File) date((File) item, created);
        }
        return created ? (descending ? FOLDER_CREATED_DESC : FOLDER_CREATED_ASC)
                : (descending ? FOLDER_MODIFIED_DESC : FOLDER_MODIFIED_ASC);
    }

    public static Comparator<Object> localComparator(ArrayList<?> items, int mode, Comparator<Object> original) {
        if (mode != 1 && mode != 3 && !isCreation(mode)) return original;
        final boolean created = isCreation(mode);
        for (Object item : items) {
            File file = localFile(item);
            if (file != null) date(file, created);
        }
        boolean descending = mode == 1 || mode == CREATED_DESC;
        return created ? (descending ? LOCAL_CREATED_DESC : LOCAL_CREATED_ASC)
                : (descending ? LOCAL_MODIFIED_DESC : LOCAL_MODIFIED_ASC);
    }

    public static Comparator<Object> commonComparator(ArrayList<?> items, int mode, Comparator<Object> original) {
        if (mode != 1 && mode != 3 && !isCreation(mode)) return original;
        final boolean created = isCreation(mode);
        for (Object item : items) {
            File file = commonFile(item);
            if (file != null) date(file, created);
            else if (created) prepareUri(item);
        }
        boolean descending = mode == 1 || mode == CREATED_DESC;
        return created ? (descending ? COMMON_CREATED_DESC : COMMON_CREATED_ASC)
                : (descending ? COMMON_MODIFIED_DESC : COMMON_MODIFIED_ASC);
    }

    private static long date(File file, boolean created) {
        return created ? MediaDates.created(file) : MediaDates.modified(file);
    }

    private static final class FolderDates implements Comparator<Object> {
        private final boolean created, descending;
        FolderDates(boolean created, boolean descending) { this.created = created; this.descending = descending; }
        public int compare(Object left, Object right) {
            if (left == right) return 0;
            int group = pinned(left) - pinned(right);
            if (group != 0) return group;
            if (!(left instanceof File) || !(right instanceof File)) return 0;
            File a = (File) left, b = (File) right;
            if (a.getPath().equalsIgnoreCase(b.getPath())) return 0;
            int order = Long.compare(date(a, created), date(b, created));
            if (order == 0) order = compareNames(a.getPath(), b.getPath());
            return descending ? -order : order;
        }
    }

    private static final class LocalDates implements Comparator<Object> {
        private final boolean created, descending;
        LocalDates(boolean created, boolean descending) { this.created = created; this.descending = descending; }
        public int compare(Object left, Object right) {
            if (left == right) return 0;
            File a = localFile(left), b = localFile(right);
            long da = a == null ? localModified(left) : date(a, created);
            long db = b == null ? localModified(right) : date(b, created);
            int order = Long.compare(da, db);
            if (order == 0) order = compareNames(localName(left), localName(right));
            return descending ? -order : order;
        }
    }

    private static final class CommonDates implements Comparator<Object> {
        private final boolean created, descending;
        CommonDates(boolean created, boolean descending) { this.created = created; this.descending = descending; }
        public int compare(Object left, Object right) {
            if (left == right) return 0;
            int order = Long.compare(commonDate(left, created), commonDate(right, created));
            if (order == 0) order = compareNames(stringCall(left, "a"), stringCall(right, "a"));
            return descending ? -order : order;
        }
    }

    private static int pinned(Object item) {
        // Same grouping as x.e(e,e): explicit pin, or automatic camera pin.
        int packed = numberField(item, "d").intValue();
        int pin = (packed >> 8) & 15;
        int kind = numberField(item, "g").intValue();
        return pin == 1 || (pin == 0 && kind == 2) ? 0 : 1;
    }

    private static File localFile(Object item) {
        String parent = stringField(item, "a"), name = stringField(item, "b");
        return parent == null || name == null ? null : new File(parent, name);
    }

    private static String localName(Object item) {
        String name = stringField(item, "b");
        return name == null ? "" : name;
    }

    private static long localModified(Object item) {
        return Math.max(0, numberField(item, "d").longValue()) * 1000L;
    }

    private static File commonFile(Object item) {
        String path = stringCall(item, "j");
        if (path != null && path.startsWith("/")) return new File(path);
        Object value = call(item, "i");
        if (value instanceof Uri) {
            Uri uri = (Uri) value;
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new File(uri.getPath());
        }
        return null;
    }

    private static long commonDate(Object item, boolean created) {
        File file = commonFile(item);
        if (file != null) return date(file, created);
        if (created) {
            Object value = call(item, "i");
            Long added = value instanceof Uri ? URI_CREATED.get(value.toString()) : null;
            if (added != null && added.longValue() > 0) return added.longValue();
        }
        Object value = call(item, "e");
        return value instanceof Number ? Math.max(0, ((Number) value).longValue()) * 1000L : 0L;
    }

    private static void prepareUri(Object item) {
        // Never perform a provider query from compare() or on the main thread.
        if (resolver == null || Looper.myLooper() == Looper.getMainLooper()) return;
        Object value = call(item, "i");
        if (!(value instanceof Uri)) return;
        Uri uri = (Uri) value;
        if (!"content".equals(uri.getScheme()) || !"media".equals(uri.getAuthority())) return;
        String key = uri.toString();
        if (URI_CREATED.containsKey(key)) return;
        long added = 0;
        try (Cursor cursor = resolver.query(uri, new String[]{"date_added"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) added = Math.max(0, cursor.getLong(0)) * 1000L;
        } catch (RuntimeException ignored) {
            // Documents/cloud providers need not expose date_added; use their mtime.
        }
        // Retain provider dates for the process lifetime. Live CommonFolder
        // comparators share these keys across worker-to-view list copies; an
        // arbitrary size-based eviction would change their comparison results.
        // The map stores only a URI string and timestamp for visited media.
        URI_CREATED.put(key, Long.valueOf(added));
    }

    @SuppressWarnings("unchecked")
    private static int compareNames(String left, String right) {
        if (!naturalResolved) {
            synchronized (DateSort.class) {
                if (!naturalResolved) {
                    try {
                        naturalField = Class.forName("com.alensw.b.b.b").getField("a");
                    } catch (ReflectiveOperationException | RuntimeException ignored) { }
                    naturalResolved = true;
                }
            }
        }
        if (left == null) left = "";
        if (right == null) right = "";
        if (naturalField != null) {
            try {
                Object current = naturalField.get(null);
                if (current instanceof Comparator) return ((Comparator<Object>) current).compare(left, right);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return left.compareToIgnoreCase(right);
    }

    private static Access access(Object item) {
        if (item == null) return null;
        Class<?> type = item.getClass();
        Access found = ACCESS.get(type);
        if (found != null) return found;
        Access added = new Access(type);
        Access previous = ACCESS.putIfAbsent(type, added);
        return previous == null ? added : previous;
    }

    private static Object call(Object item, String name) {
        Access found = access(item);
        Method method = found == null ? null : found.methods.get(name);
        if (method == null) return null;
        try { return method.invoke(item); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static String stringCall(Object item, String name) {
        Object value = call(item, name);
        return value instanceof String ? (String) value : null;
    }

    private static Object field(Object item, String name) {
        Access found = access(item);
        Field field = found == null ? null : found.fields.get(name);
        if (field == null) return null;
        try { return field.get(item); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static String stringField(Object item, String name) {
        Object value = field(item, name);
        return value instanceof String ? (String) value : null;
    }

    private static Number numberField(Object item, String name) {
        Object value = field(item, name);
        return value instanceof Number ? (Number) value : Integer.valueOf(0);
    }

    private static final class Access {
        final ConcurrentHashMap<String, Field> fields = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Method> methods = new ConcurrentHashMap<>();
        Access(Class<?> type) {
            for (String name : new String[]{"a", "b", "d", "g"}) {
                try { fields.put(name, type.getField(name)); }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            for (String name : new String[]{"a", "e", "i", "j"}) {
                try { methods.put(name, type.getMethod(name)); }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
    }
}
