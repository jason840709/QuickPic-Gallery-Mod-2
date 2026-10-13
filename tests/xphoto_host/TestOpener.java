import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import com.alensw.jni.ScreensaverOpener;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** Fake API tests exercise the production helper, not a copied routing implementation. */
public final class TestOpener {
    private static File png;
    private static int checks, scenarioStart;
    private static Context context;

    private static void ok(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    private static void reset(boolean activity) {
        scenarioStart = checks;
        context = activity ? new android.app.Activity() : new Context();
        context.pm.viewers.add(new ResolveInfo());
        test.fake.FileProvider.fail = false;
        android.widget.Toast.last = null;
    }

    private static Intent last() {
        return context.attempts.get(context.attempts.size() - 1);
    }

    private static Intent payload() {
        return (Intent) last().extras.get("payload");
    }

    private static void open() { ScreensaverOpener.open(context, png); }

    private static void done(String name) throws Exception {
        ok(png.isFile() && Arrays.equals(Files.readAllBytes(png.toPath()), new byte[]{'P','N','G','!'}),
                "PNG survives unchanged in " + name);
        System.out.println("PASS|" + name + "|" + (checks - scenarioStart));
    }

    private static void chooser() {
        ok("chooser".equals(last().action), "chooser opened");
        Intent view = payload();
        ok("image/png".equals(view.type) && "content".equals(view.data.getScheme()), "PNG content URI");
        ok(view.data.toString().equals("content://test.fake.FileProvider" + png.getAbsolutePath()),
                "FileProvider receives saved PNG absolute path");
        ok((view.flags & 1) != 0 && view.clip.uri == view.data, "payload read grant and clip");
        ok((last().flags & 1) != 0 && last().clip == view.clip, "chooser read grant and clip");
        ok(view.component == null && view.pkg == null, "unrestricted fallback");
        ok(context.pm.queryCalls == 0, "viewer query does not gate chooser");
    }

    public static void main(String[] args) throws Exception {
        png = new File(args[0]);

        reset(true);
        open();
        ok(context.attempts.size() == 1, "original preferred");
        ok(last().component != null && last().data == null, "legacy component / no URI");
        ok(last().component.pkg.equals("com.ebook.imagemanager")
                && last().component.name.equals("com.xrz.photo.ImageFolderSubAt"), "original component values");
        ok(last().extras.get("list") instanceof java.util.ArrayList, "legacy ArrayList");
        ok(((java.util.ArrayList) last().extras.get("list")).get(0).equals(png.getAbsolutePath()),
                "legacy absolute file path");
        ok(last().extras.get("position").equals(0), "position zero");
        ok(last().flags == Intent.FLAG_ACTIVITY_CLEAR_TOP, "legacy flags");
        ok(android.widget.Toast.last.contains("請在 XPhoto"), "XPhoto manual setup instruction");
        done("original_xphoto_protocol_preserved");

        reset(true);
        context.pm.original = null;
        context.pm.samePackage = new ResolveInfo();
        open();
        ok(context.attempts.size() == 1 && last().pkg.equals("com.ebook.imagemanager"), "package VIEW second");
        ok(last().data != null && "image/png".equals(last().type)
                && last().clip.uri == last().data && (last().flags & 1) != 0, "same-package content URI grant");
        done("alternate_xphoto_package_view");

        reset(true);
        context.pm.original = null;
        open();
        chooser();
        ok("已保存 1860×2480 PNG，請選擇 App 開啟".equals(android.widget.Toast.last), "short truthful chooser toast");
        done("missing_xphoto_chooser");

        reset(true);
        context.pm.original.exported = false;
        open();
        chooser();
        ok(context.attempts.size() == 1, "unexported original never launched");
        done("unexported_xphoto_chooser");

        reset(true);
        context.pm.original.enabled = false;
        open();
        chooser();
        ok(context.attempts.size() == 1, "disabled original never launched");
        done("disabled_xphoto_activity_chooser");

        reset(true);
        context.pm.original.applicationInfo.enabled = false;
        open();
        chooser();
        done("disabled_xphoto_application_chooser");

        reset(true);
        context.pm.appState = 2;
        open();
        chooser();
        ok(context.attempts.size() == 1, "disabled XPhoto skipped, chooser still attempted");
        done("application_setting_disabled_chooser");

        reset(true);
        context.failOriginal = true;
        context.pm.samePackage = new ResolveInfo();
        open();
        ok(context.attempts.size() == 2 && last().pkg != null, "failed original tries package VIEW");
        done("original_launch_failure_package_fallback");

        reset(true);
        context.failOriginal = true;
        context.failPackage = true;
        context.pm.samePackage = new ResolveInfo();
        open();
        ok(context.attempts.size() == 3, "both XPhoto failures fallback");
        chooser();
        done("both_xphoto_launches_fail_chooser");

        reset(true);
        context.pm.original = null;
        context.pm.viewers.clear();
        open();
        chooser();
        ok(context.attempts.size() == 1, "zero visible query handlers still attempts chooser");
        ok(android.widget.Toast.last.contains("已保存"), "saved status remains truthful");
        done("zero_visible_handlers_still_launch_chooser");

        reset(true);
        context.pm.original = null;
        context.failChooser = true;
        open();
        ok(android.widget.Toast.last.contains("PNG 已保存")
                && android.widget.Toast.last.contains("無法開啟圖片選擇器")
                && android.widget.Toast.last.contains("尚未設定螢幕保護")
                && android.widget.Toast.last.contains(png.getAbsolutePath()), "chooser failure saved path/status");
        done("chooser_launch_failure_saved_status");

        reset(false);
        context.pm.original = null;
        open();
        chooser();
        ok((last().flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0, "application context NEW_TASK");
        done("application_context_new_task");

        reset(true);
        context.pm.original.enabled = false;
        context.pm.componentState = 1;
        open();
        ok(last().component != null, "explicit enabled override respected");
        done("component_enabled_override");

        reset(true);
        context.pm.original = null;
        test.fake.FileProvider.fail = true;
        open();
        ok(context.attempts.isEmpty() && android.widget.Toast.last.contains("PNG 已保存")
                && android.widget.Toast.last.contains("無法分享圖片"), "provider failure safe");
        done("provider_failure_saved_status");

        reset(true);
        ScreensaverOpener.open(context, new File(png.getParentFile(), "absent.png"));
        ok(context.attempts.isEmpty() && android.widget.Toast.last.contains("無法讀取"), "unreadable input reported");
        done("unreadable_input_no_launch");
    }
}
