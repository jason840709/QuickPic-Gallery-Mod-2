package com.alensw.jni;
import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;
import java.util.ArrayList;

/** Uses the original app's FileProvider for Android 7+ external file URI handoffs. */
public final class CompatIntents {
    public static void prepareResult(Activity activity, Intent intent) {
        String caller=activity.getCallingPackage();
        if(caller==null && activity.getCallingActivity()!=null)
            caller=activity.getCallingActivity().getPackageName();
        // Preserve the original local file URI for internal crop result consumers.
        if(caller==null || activity.getPackageName().equals(caller))return;
        prepare(activity,intent);
    }
    private static Uri content(Context context, Uri uri) {
        if(uri==null || !"file".equals(uri.getScheme())) return uri;
        try {
            Class<?> provider=context.getClassLoader().loadClass(context.getPackageName()+".FileProvider");
            return (Uri)provider.getMethod("a",String.class).invoke(null,uri.getPath());
        } catch(Exception e) { Log.w("QuickPicCompat","Cannot map file URI to original FileProvider",e);return uri; }
    }
    public static void prepare(Context context, Intent intent) {
        if(intent==null)return;
        ComponentName component=intent.getComponent();
        if(component!=null && context.getPackageName().equals(component.getPackageName()))return;
        boolean changed=false;
        Uri data=intent.getData(),mapped=content(context,data);
        if(mapped!=data) { intent.setDataAndType(mapped,intent.getType());changed=true; }
        if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Parcelable> list=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if(list!=null) {
                ArrayList<Parcelable> replacement=new ArrayList<Parcelable>();
                for(Parcelable p:list) {
                    Parcelable q=p instanceof Uri ? content(context,(Uri)p) : p;
                    replacement.add(q);if(q!=p)changed=true;
                }
                if(changed)intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,replacement);
            }
        } else {
            Parcelable p=intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(p instanceof Uri) {
                Uri q=content(context,(Uri)p);
                if(q!=p) { intent.putExtra(Intent.EXTRA_STREAM,q);changed=true; }
            }
        }
        if(changed)intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
