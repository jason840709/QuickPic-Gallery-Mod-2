// SPDX-License-Identifier: MIT
package com.alensw.jni;
import android.view.Menu;
import android.view.MenuItem;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
/** Preserve item identity, listeners and state while ordering the legacy Menu. */
public final class MenuLayout {
    private MenuLayout() {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void order(Menu menu, int[] ids) {
        if (menu == null) return;
        for (Class<?> type = menu.getClass(); type != null; type = type.getSuperclass()) {
            if (!"com.alensw.b.g.b".equals(type.getName())) continue;
            try {
                Field field = type.getDeclaredField("e"); field.setAccessible(true);
                Object value = field.get(menu);
                if (!(value instanceof List)) return;
                List items = (List) value;
                synchronized (items) {
                    ArrayList sorted = new ArrayList(items.size());
                    for (int id : ids) for (Object item : items)
                        if (item instanceof MenuItem && ((MenuItem)item).getItemId() == id && !sorted.contains(item)) sorted.add(item);
                    for (Object item : items) if (!sorted.contains(item)) sorted.add(item);
                    items.clear(); items.addAll(sorted);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            return;
        }
        // Native Android menus use XML orderInCategory and Menu.add order.
    }
}
