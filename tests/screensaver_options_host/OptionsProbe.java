// SPDX-License-Identifier: MIT
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import com.alensw.jni.ScreensaverOptions;
import com.alensw.jni.ScreensaverMenu;
import com.alensw.jni.ScreensaverDevice;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class OptionsProbe {
    static int checks,last=-1;
    static final String GENERIC="screensaver_option_generic",BIGME="screensaver_option_bigme",BOOX="screensaver_option_boox",INITIALIZED="screensaver_options_initialized";
    static final CharSequence[] ALL={"桌布","設置為屏保","設置為 B1051C 屏保","設置為文石屏保"};
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final DialogInterface.OnClickListener ORIGINAL=new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){last=w;}};
    public static void main(String[] args)throws Exception{
        Context c=new Context();ScreensaverOptions pref=new ScreensaverOptions(c,null);
        check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0,1,2}),"Both options visible by default");
        check(c.prefs.writes==0,"Reading defaults must not save preferences");
        for(int mask=0;mask<8;mask++){
            c.prefs.values.put(GENERIC,(mask&1)!=0);c.prefs.values.put(BIGME,(mask&2)!=0);c.prefs.values.put(BOOX,(mask&4)!=0);
            int[] expected=ScreensaverOptions.visibleActions(c);
            ScreensaverMenu.show(c,123,ALL,ORIGINAL);
            check(AlertDialog.items.length==expected.length,"Visible row count");
            check("桌布".contentEquals(AlertDialog.items[0]),"Wallpaper must remain");
            // Mutate persistent settings after showing; the visible dialog retains its mapping.
            c.prefs.values.put(GENERIC,(mask&1)==0);c.prefs.values.put(BIGME,(mask&2)==0);c.prefs.values.put(BOOX,(mask&4)==0);
            for(int row=0;row<expected.length;row++){
                check(ALL[expected[row]].equals(AlertDialog.items[row]),"Displayed item matches captured action");
                last=-1;AlertDialog.click.onClick(null,row);check(last==expected[row],"Wrong action after visibility change");
            }
            last=-1;AlertDialog.click.onClick(null,-1);AlertDialog.click.onClick(null,99);check(last==-1,"Invalid index dispatched");
        }
        Method click=ScreensaverOptions.class.getDeclaredMethod("onClick");click.setAccessible(true);
        c.prefs.values.clear();c.prefs.values.put("future_option",true);
        Map<String,Object> before=new HashMap<String,Object>(c.prefs.values);int writes=c.prefs.writes;
        click.invoke(pref);
        check(Arrays.equals(AlertDialog.checks,new boolean[]{true,true,false}),"Checkbox defaults");
        AlertDialog.multi.onClick(null,0,false);AlertDialog.multi.onClick(null,1,false);
        check(AlertDialog.negative==null,"Cancel must have no saving callback");
        check(c.prefs.values.equals(before)&&c.prefs.writes==writes,"Cancel must leave preferences untouched");
        // A reopened dialog uses saved values, not unsaved changes from the cancelled one.
        click.invoke(pref);check(Arrays.equals(AlertDialog.checks,new boolean[]{true,true,false}),"Cancelled changes leaked");
        AlertDialog.multi.onClick(null,0,false);AlertDialog.multi.onClick(null,1,false);AlertDialog.positive.onClick(null,1);
        check(c.prefs.writes==writes+1,"Save must write once");
        check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0}),"All screensaver actions may be hidden");
        check(Boolean.TRUE.equals(c.prefs.values.get("screensaver_options_configured")),"Explicit configuration marker");
        check(Integer.valueOf(2).equals(c.prefs.values.get("screensaver_options_schema")),"Extensible schema");
        check(Boolean.TRUE.equals(c.prefs.values.get("future_option")),"Unknown future keys preserved");
        check("已隱藏屏保選項".equals(pref.summary),"Summary not refreshed");
        Context reopened=new Context();reopened.prefs=c.prefs;new ScreensaverOptions(reopened,null);
        check(Arrays.equals(ScreensaverOptions.visibleActions(reopened),new int[]{0}),"Recreated preference lost saved state");
        c.prefs.values.put(GENERIC,"bad-type");c.prefs.values.put(BIGME,4);
        check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0,1,2}),"Malformed option value fallback");
        initializeChecks();
        System.out.println("PASS "+checks+" production preference/menu host assertions; Android UI/storage are doubles");
    }

    static Context fresh(){ScreensaverDevice.calls=0;ScreensaverDevice.fail=false;return new Context();}
    static void initializeChecks(){
        for(int action=1;action<=3;action++){
            Context c=fresh();ScreensaverDevice.result=action;ScreensaverOptions.initialize(c);
            check(ScreensaverDevice.calls==1,"Clean install detection once");
            check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0,action}),"Detected default action");
            check(c.prefs.writes==1&&Boolean.TRUE.equals(c.prefs.values.get(INITIALIZED)),"Initialization saved once");
            c.prefs.values.put(GENERIC,false);c.prefs.values.put(BIGME,false);c.prefs.values.put(BOOX,false);
            c.pm.info.lastUpdateTime=2;ScreensaverOptions.initialize(c);
            check(ScreensaverDevice.calls==1&&c.prefs.writes==1,"Update redetected initialized install");
            check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0}),"Update lost all-hidden manual state");
        }
        Context c=fresh();c.pm.info.lastUpdateTime=2;ScreensaverOptions.initialize(c);
        check(ScreensaverDevice.calls==0,"18/19 update must not detect");
        check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0,1,2}),"Update must retain legacy defaults");
        check(Boolean.FALSE.equals(c.prefs.values.get(BOOX)),"New BOOX option must default hidden on update");
        c=fresh();c.prefs.values.put(GENERIC,false);c.prefs.values.put(BIGME,false);c.prefs.values.put("screensaver_options_configured",true);ScreensaverOptions.initialize(c);
        check(ScreensaverDevice.calls==0&&Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0}),"Explicit all-hidden preferences lost");
        c=fresh();c.prefs.values.put(BOOX,true);ScreensaverOptions.initialize(c);
        check(ScreensaverDevice.calls==0&&Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0,1,2,3}),"Any prior option blocks detection");
        c=fresh();c.prefs.values.put("screensaver_options_configured",false);ScreensaverOptions.initialize(c);
        check(ScreensaverDevice.calls==0,"Existing configured key blocks detection");
        c=fresh();c.pm.info.firstInstallTime=0;c.pm.info.lastUpdateTime=0;ScreensaverOptions.initialize(c);
        check(ScreensaverDevice.calls==0,"Unknown install time must not count as fresh");
        c=fresh();c.prefs.failRead=true;ScreensaverOptions.initialize(c);check(ScreensaverDevice.calls==0,"Read failure must not detect or crash");
        c=fresh();c.pm.info.lastUpdateTime=2;c.prefs.failWrite=true;ScreensaverOptions.initialize(c);check(c.prefs.writes==0,"Write failure must not crash");
        c=fresh();ScreensaverDevice.fail=true;ScreensaverOptions.initialize(c);
        check(Arrays.equals(ScreensaverOptions.visibleActions(c),new int[]{0,1}),"Unavailable detector uses generic default");
        check(Boolean.TRUE.equals(c.prefs.values.get(INITIALIZED)),"Unavailable detector still initializes once");
    }
}
