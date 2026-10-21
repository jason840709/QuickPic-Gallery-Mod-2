import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;
import com.alensw.jni.ViewerControls;
import com.alensw.jni.MenuLayout;
import java.lang.reflect.*;
import java.util.*;

/** Executes production helpers; every fake below models an Android/legacy boundary. */
public class ViewerControlsTest {
    static int checks;
    static final int DELETE=0x7f09004e, SLIDE=0x7f090052, INTERVAL=0x00f0a010;
    static final int SHARE=0x7f09000a, EDIT=0x7f090069, ROTATE=0x7f09005a;
    static final int OTHER=0x7f090099, ACTION_ICON=0x7f010001;
    static final String ACTIVE="slideshow_interval", CUSTOM="slideshow_custom_interval";

    static void check(boolean value,String name) {
        checks++;
        if(!value)throw new AssertionError(name);
    }
    static void equal(Object actual,Object expected,String name) {
        check(Objects.equals(actual,expected),name+" expected="+expected+" actual="+actual);
    }
    static class Item implements MenuItem {
        final int id;
        int mode=-1,iconResource,iconAssignments,titleAssignments,titleResource;
        Drawable icon;
        CharSequence title;
        final Object clickCallback=new Object();
        Item(int value){id=value;title="item:"+Integer.toHexString(id);}
        public int getItemId(){return id;}
        public void setShowAsAction(int value){mode=value;}
        public MenuItem setIcon(int value){iconResource=value;icon=new Drawable(value);iconAssignments++;return this;}
        public MenuItem setIcon(Drawable value){icon=value;iconResource=value.resourceId;iconAssignments++;return this;}
        public Drawable getIcon(){return icon;}
        public MenuItem setTitle(int value){titleResource=value;title="resource:"+Integer.toHexString(value);titleAssignments++;return this;}
        public MenuItem setTitle(CharSequence value){title=value;titleAssignments++;return this;}
        public CharSequence getTitle(){return title;}
    }
    static abstract class BaseMenu implements Menu {
        int adds,removes;
        abstract List<Item> items();
        public Item findItem(int id){for(Item item:items())if(item.id==id)return item;return null;}
        public void removeItem(int id){Item item=findItem(id);if(item!=null){items().remove(item);removes++;}}
        public Item add(int group,int id,int order,int title){Item item=new Item(id);item.setTitle(title);items().add(item);adds++;return item;}
        public Item add(int group,int id,int order,CharSequence title){Item item=new Item(id);item.setTitle(title);items().add(item);adds++;return item;}
        public int size(){return items().size();}
        public Item getItem(int index){return items().get(index);}
        List<Integer> ids(){List<Integer> ids=new ArrayList<Integer>();for(Item item:items())ids.add(item.id);return ids;}
    }
    // The old App Menu keeps real MenuItem instances in its private ArrayList e.
    // Using a private nested class also catches reflection that forgot setAccessible.
    private static class LegacyMenu extends com.alensw.b.g.b {
        LegacyMenu(int... ids){super(ids);}
        protected Item newItem(int id){return new Item(id);}
        @SuppressWarnings("unchecked") List<Item> items(){return (List<Item>)(List<?>)hostItems();}
        public Item findItem(int id){return (Item)super.findItem(id);}
        public Item getItem(int index){return (Item)super.getItem(index);}
        Object storage(){return hostStorage();}
        List<Integer> ids(){List<Integer> ids=new ArrayList<Integer>();for(Item item:items())ids.add(item.id);return ids;}
    }
    private static class NativeMenu extends BaseMenu {
        private final ArrayList<Item> nativeItems=new ArrayList<Item>();
        NativeMenu(int... ids){for(int id:ids)nativeItems.add(new Item(id));}
        List<Item> items(){return nativeItems;}
    }
    public static class LegacyContext extends Context {
        final List<Integer> buttons=new ArrayList<Integer>();
        final List<String> calls=new ArrayList<String>();
        public void a(MenuItem item,int mode) {
            int id=item.getItemId();buttons.remove(Integer.valueOf(id));
            if(mode!=0)buttons.add(id);
            item.setShowAsAction(mode);
            calls.add(Integer.toHexString(id)+":"+mode);
        }
    }
    static void resetPreferences() {PreferenceManager.prefs=new SharedPreferences();}
    static void saved(String key,String value){PreferenceManager.prefs.map.put(key,value);}
    static String saved(String key){return PreferenceManager.prefs.map.get(key);}
    static void apply(Context context,Object controller,int seconds)throws Exception {
        Method m=ViewerControls.class.getDeclaredMethod("apply",Context.class,Object.class,int.class);
        m.setAccessible(true);m.invoke(null,context,controller,seconds);
    }
    static int seconds(Context context)throws Exception {
        Method m=ViewerControls.class.getDeclaredMethod("seconds",Context.class);
        m.setAccessible(true);return (Integer)m.invoke(null,context);
    }
    static EditText inlineInput(View view) {
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof LinearLayout)for(View child:((LinearLayout)view).children){EditText input=inlineInput(child);if(input!=null)return input;}
        return null;
    }
    static AlertDialog open(Context context,Object controller) {
        int count=AlertDialog.shown.size();
        ViewerControls.showDialog(context,controller);
        check(AlertDialog.shown.size()==count+1,"opening interval creates one dialog");
        AlertDialog dialog=AlertDialog.last;
        check(inlineInput(dialog.view)!=null,"the first interval dialog contains editable custom input");
        return dialog;
    }
    static EditText chooseCustom(AlertDialog dialog) {
        int count=AlertDialog.shown.size();
        EditText input=inlineInput(dialog.view);
        int focusBefore=input.focusRequests;
        dialog.select(5);
        check(!dialog.dismissed && AlertDialog.last==dialog && AlertDialog.shown.size()==count,
              "custom choice stays in same dialog without a second modal");
        check(input.focused && input.focusRequests>focusBefore,"custom choice focuses inline input");
        return input;
    }
    static void applyCustom(Context context,Object controller,String value) {
        AlertDialog dialog=open(context,controller);
        EditText input=chooseCustom(dialog);input.setText(value);dialog.positiveButton.performClick();
    }
    static void testMenuOrder() {
        MenuLayout.order(null,new int[]{SLIDE});
        LegacyMenu menu=new LegacyMenu(SHARE,OTHER,DELETE,EDIT,SLIDE);
        List<Item> originals=new ArrayList<Item>(menu.items());
        Object storage=menu.storage();
        MenuLayout.order(menu,new int[]{SLIDE,INTERVAL,DELETE,EDIT,SHARE});
        equal(menu.ids(),Arrays.asList(SLIDE,DELETE,EDIT,SHARE,OTHER),"legacy ArrayList follows requested IDs and retains remaining items");
        check(menu.storage()==storage,"legacy menu retains its original backing ArrayList");
        for(Item item:originals)check(menu.findItem(item.id)==item,"legacy order retains exact MenuItem and callback "+item.id);
        check(menu.adds==0 && menu.removes==0,"legacy order never rebuilds menu items");
        MenuLayout.order(menu,new int[]{SLIDE,INTERVAL,DELETE,EDIT,SHARE});
        equal(menu.ids(),Arrays.asList(SLIDE,DELETE,EDIT,SHARE,OTHER),"legacy order is idempotent when requested item is missing");
        LegacyMenu duplicates=new LegacyMenu(SHARE,SLIDE,OTHER);
        MenuLayout.order(duplicates,new int[]{SLIDE,SLIDE,SHARE});
        equal(duplicates.ids(),Arrays.asList(SLIDE,SHARE,OTHER),"repeated preferred ID does not duplicate menu items");
        NativeMenu nativeMenu=new NativeMenu(SHARE,OTHER,SLIDE,DELETE);
        List<Integer> before=nativeMenu.ids();
        MenuLayout.order(nativeMenu,new int[]{SLIDE,DELETE,SHARE});
        equal(nativeMenu.ids(),before,"native menu retains its XML-controlled order");
        check(nativeMenu.adds==0 && nativeMenu.removes==0,"native ordering never recreates menu items");
    }
    static void testConfigure() {
        for(int width:new int[]{360,599,600,840}) {
            LegacyContext context=new LegacyContext();context.res.config.smallestScreenWidthDp=width;
            LegacyMenu menu=new LegacyMenu(SHARE,DELETE,SLIDE,EDIT,ROTATE,OTHER);
            Item remaining=menu.findItem(OTHER);remaining.mode=MenuItem.SHOW_AS_ACTION_NEVER;
            ViewerControls.configure(context,menu);
            equal(menu.ids(),Arrays.asList(SLIDE,INTERVAL,DELETE,EDIT,SHARE,ROTATE,OTHER),
                  "legacy configure orders action commands then delete/edit/share and retains the rest at width "+width);
            check(menu.findItem(OTHER)==remaining && remaining.mode==0,"remaining XML overflow command retains identity and mode at width "+width);
            equal(context.buttons,Arrays.asList(SLIDE,INTERVAL),"phone and tablet show slideshow then interval at width "+width);
            for(int id:new int[]{DELETE,EDIT,SHARE,ROTATE})
                check(menu.findItem(id).mode==MenuItem.SHOW_AS_ACTION_NEVER,"existing command stays overflow "+id+" width "+width);
            equal(menu.findItem(INTERVAL).titleResource,0x7f0a007b,"interval action requests the localized interval title resource");
            Item interval=menu.findItem(INTERVAL);int adds=menu.adds;
            ViewerControls.configure(context,menu);
            check(menu.findItem(INTERVAL)==interval && menu.adds==adds,"reconfigure reuses interval MenuItem");
            equal(context.buttons,Arrays.asList(SLIDE,INTERVAL),"reconfigure keeps stable action order");
            menu.removeItem(SLIDE);ViewerControls.configure(context,menu);
            check(menu.findItem(INTERVAL)==null,"no slideshow command removes interval command");
        }
        Context nativeContext=new Context();NativeMenu nativeMenu=new NativeMenu(SHARE,DELETE,SLIDE,EDIT,ROTATE);
        nativeContext.res.config.locale=Locale.US;
        ViewerControls.configure(nativeContext,nativeMenu);
        check(nativeMenu.findItem(SLIDE).mode==2 && nativeMenu.findItem(INTERVAL).mode==2,
              "native action bar fallback exposes both slideshow and interval");
        for(int id:new int[]{DELETE,EDIT,SHARE,ROTATE})check(nativeMenu.findItem(id).mode==0,"native overflow mode retained "+id);
        equal(nativeMenu.findItem(INTERVAL).titleResource,0x7f0a007b,"native interval action requests the same localized title resource");
    }
    static void testThemeTint() {
        for(boolean reference:new boolean[]{false,true})for(int systemMode:new int[]{0x10,0x20}) {
            Context context=new Context();context.res.config.uiMode=systemMode;
            final int expected=reference?0xfff0f1f2:0xff234567;
            context.res.colors.put(77,expected);
            context.theme.values.put(ACTION_ICON,new int[]{reference?77:0,reference?0:expected});
            context.theme.values.put(android.R.attr.textColorPrimary,new int[]{0,0xff111111});
            LegacyMenu menu=new LegacyMenu(DELETE,SLIDE,SHARE,EDIT);
            ViewerControls.configure(context,menu);
            Drawable actual=menu.findItem(INTERVAL).getIcon();
            check(actual!=null && actual.color==expected,"interval icon resolves actual App colorActionIcon "+reference+" mode "+systemMode);
            check(context.theme.resolved.contains(ACTION_ICON),"interval tint resolves App action-icon attribute");
            check(actual.mutated && actual.mode==PorterDuff.Mode.SRC_IN,"interval icon mutates drawable and tints with SRC_IN");
            context.theme.values.put(ACTION_ICON,new int[]{0,0xffabcdef});
            ViewerControls.configure(context,menu);
            check(menu.findItem(INTERVAL).getIcon().color==0xffabcdef,"reconfigure picks up changed App theme");
            check(actual.color==expected,"new tint cannot modify the previous drawable instance");
        }
    }
    static void testDialog() {
        resetPreferences();Context context=new Context();
        com.alensw.ui.c.as controller=new com.alensw.ui.c.as(0,null);
        saved(ACTIVE,"3");
        AlertDialog first=open(context,controller);
        equal(first.title.toString(),"resource:7f0a007b","dialog requests the interval title resource");
        equal(first.positiveLabel.toString(),"套用自訂","Traditional Chinese custom-apply button");
        equal(first.selected,0,"active 3-second preset selected on first open");
        check(first.labels.length==6,"five presets plus one custom choice");
        EditText initial=inlineInput(first.view);
        check(initial.inputType==android.text.InputType.TYPE_CLASS_NUMBER,"inline input accepts numeric values");
        first.cancel();
        check(!PreferenceManager.prefs.map.containsKey(CUSTOM),"cancel first dialog does not create a custom preference");
        saved(ACTIVE,"7");
        AlertDialog migrated=open(context,controller);
        equal(migrated.selected,5,"existing nonpreset active interval selects custom");
        equal(inlineInput(migrated.view).getText().toString(),"7","existing nonpreset interval initializes the custom field");
        migrated.select(0);
        equal(saved(CUSTOM),"7","first preset switch remembers a preexisting custom active interval");
        int[] presets={3,5,10,30,60};
        for(int index=0;index<presets.length;index++) {
            saved(ACTIVE,"7");saved(CUSTOM,"7");
            AlertDialog dialog=open(context,controller);EditText input=inlineInput(dialog.view);input.setText("99");
            dialog.select(index);
            equal(saved(ACTIVE),Integer.toString(presets[index]),"preset commits immediately "+presets[index]);
            equal(controller.millis(),presets[index]*1000,"preset updates active controller "+presets[index]);
            equal(saved(CUSTOM),"7","preset keeps remembered custom value "+presets[index]);
            check(dialog.dismissed,"preset closes interval dialog immediately "+presets[index]);
        }
        applyCustom(context,controller,"7");
        equal(saved(ACTIVE),"7","custom apply changes active seconds");equal(saved(CUSTOM),"7","custom apply remembers custom seconds separately");
        equal(controller.millis(),7000,"custom apply updates active controller");
        check(AlertDialog.last.dismissed,"custom apply closes original dialog");
        AlertDialog preset=open(context,controller);preset.select(0);
        equal(saved(ACTIVE),"3","preset can become active after custom");equal(saved(CUSTOM),"7","custom 7 survives activating preset 3");
        AlertDialog reopened=open(context,controller);
        equal(reopened.selected,0,"active preset stays selected when custom remembered");
        equal(inlineInput(reopened.view).getText().toString(),"7","reopened editable field contains remembered 7 while preset 3 is active");
        check(reopened.labels[5].toString().contains("7"),"custom choice displays its remembered 7-second value");
        chooseCustom(reopened).setText("23");reopened.cancel();
        equal(saved(ACTIVE),"3","cancel does not apply custom draft");equal(saved(CUSTOM),"7","cancel does not overwrite remembered custom draft");
        for(String value:new String[]{"","0","-1","86401","2147483648","1.5","abc"}) {
            saved(ACTIVE,"3");saved(CUSTOM,"7");AlertDialog dialog=open(context,controller);
            EditText input=chooseCustom(dialog);input.setText(value);dialog.positiveButton.performClick();
            check(!dialog.dismissed && input.error!=null,"invalid custom stays editable "+value);
            equal(saved(ACTIVE),"3","invalid custom does not persist active "+value);
            equal(saved(CUSTOM),"7","invalid custom does not replace custom "+value);
        }
        for(String value:new String[]{"1","86400"," 10 "}) {
            applyCustom(context,controller,value);
            equal(saved(ACTIVE),value.trim(),"valid custom persists active "+value);
            equal(saved(CUSTOM),value.trim(),"valid custom persists remembered value "+value);
            check(AlertDialog.last.dismissed,"valid custom dismisses dialog "+value);
        }
        for(String malformed:new String[]{"bad","0","86401","2147483648"}) {
            saved(ACTIVE,"5");saved(CUSTOM,malformed);
            AlertDialog recovered=open(context,controller);
            equal(inlineInput(recovered.view).getText().toString(),"5","invalid remembered custom falls back to active interval "+malformed);
            recovered.cancel();equal(saved(CUSTOM),malformed,"cancelling recovered input does not rewrite saved custom "+malformed);
        }
        context.res.config.locale=Locale.US;
        AlertDialog english=open(context,controller);
        equal(english.positiveLabel.toString(),"Apply custom","English custom-apply button");
        equal(english.title.toString(),"resource:7f0a007b","English dialog requests the interval title resource");english.cancel();
    }
    static void testControllerLifecycle()throws Exception {
        resetPreferences();Context context=new Context();
        for(String value:new String[]{"bad","2147483648"}){saved(ACTIVE,value);check(seconds(context)==3,"malformed saved seconds fall back "+value);}
        saved(ACTIVE,"0");check(seconds(context)==1,"saved seconds lower clamp");
        saved(ACTIVE,"999999");check(seconds(context)==86400,"saved seconds upper clamp");
        Runnable next=new Runnable(){public void run(){}};
        long started=System.currentTimeMillis()-1000;
        com.alensw.ui.c.as modern=new com.alensw.ui.c.as(started,next);
        Handler handler=modern.handler();handler.queued=next;apply(context,modern,10);
        check(modern.millis()==10000 && handler.removes==1 && handler.posts==1 && handler.delay<=9000 && handler.delay>=8500,
              "as already queued slideshow recalculates interval from elapsed time");
        check(handler.queued==next,"as retains the exact queued slideshow runnable");
        com.alensw.ui.c.dp legacy=new com.alensw.ui.c.dp(System.currentTimeMillis()-100000,next);
        legacy.handler().queued=next;apply(context,legacy,1);
        check(legacy.millis()==1000 && legacy.handler().delay==50,"dp overdue queued slideshow uses minimum 50 ms");
        com.alensw.ui.c.as preload=new com.alensw.ui.c.as(0,next);apply(context,preload,60);
        check(preload.millis()==60000 && preload.handler().posts==0 && preload.handler().removes==0,
              "nonnull preloading runnable is not prematurely queued");
        com.alensw.ui.c.dp stopped=new com.alensw.ui.c.dp(0,null);apply(context,stopped,5);
        check(stopped.millis()==5000 && stopped.handler().posts==0,"stopped slideshow updates interval without starting");
        android.os.Build.VERSION.SDK_INT=28;handler.posts=0;handler.removes=0;handler.checks=0;handler.queued=next;
        apply(context,modern,30);
        check(modern.millis()==30000 && handler.checks==0 && handler.posts==0 && handler.removes==0,
              "pre29 interval update avoids unavailable hasCallbacks API and does not restart");
        android.os.Build.VERSION.SDK_INT=36;
        int errors=android.util.Log.errors;apply(context,new Object(),10);
        check(android.util.Log.errors==errors+1 && "10".equals(saved(ACTIVE)),"reflection failure logs an error and keeps persisted setting");
    }
    public static void main(String[] args)throws Exception {
        testMenuOrder();testConfigure();testThemeTint();testDialog();testControllerLifecycle();
        System.out.println("PASS viewer controls host checks="+checks+"; actual helpers, host doubles only, no device equivalence");
    }
}
