// SPDX-License-Identifier: MIT
package android.content;
import android.content.res.Resources;
public final class Context {
    public boolean readerEnabled;
    private final Resources resources=new Resources();
    public Resources getResources() { return resources; }
}
