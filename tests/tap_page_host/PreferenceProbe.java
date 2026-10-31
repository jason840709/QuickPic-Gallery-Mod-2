import com.alensw.jni.TapPagePreference;import android.content.*;import android.app.AlertDialog;import android.widget.RadioGroup;import java.lang.reflect.Method;import java.util.Locale;
public final class PreferenceProbe {
 private static int checks;private static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 private static void open(TapPagePreference p)throws Exception{Method m=TapPagePreference.class.getDeclaredMethod("onClick");m.setAccessible(true);m.invoke(p);}
 public static void main(String[]args)throws Exception{
  Context c=new Context();check(TapPagePreference.mode(null)==0,"null disabled");check(TapPagePreference.mode(c)==0,"missing disabled");check(c.prefs.writes==0,"read doesn't write");
  for(int n:new int[]{-1,7,Integer.MAX_VALUE}){c.prefs.values.put("tap_page_layout",n);check(TapPagePreference.mode(c)==0,"invalid disabled");}
  c.prefs.values.put("tap_page_layout","wrong");check(TapPagePreference.mode(c)==0,"type corrupt disabled");c.prefs.fail=true;check(TapPagePreference.mode(c)==0,"read failure disabled");c.prefs.fail=false;
  for(int i=0;i<7;i++){c.prefs.values.put("tap_page_layout",i);check(TapPagePreference.mode(c)==i,"valid persisted "+i);}
  check(c.prefs.writes==0,"lookup never writes");c.prefs.values.clear();c.prefs.values.put("untouched",42);
  c.resources.configuration.locale=Locale.TAIWAN;TapPagePreference p=new TapPagePreference(c,null);check("點擊翻頁".equals(p.title),"traditional title");check("停用".equals(p.summary),"traditional disabled");check(!p.persistent,"custom persistent false");
  open(p);check(RadioGroup.last.checked==0x00f0b100,"initial disabled radio");RadioGroup.last.check(0x00f0b103);check(TapPagePreference.mode(c)==0&&c.prefs.writes==0,"selection not committed");check(AlertDialog.negative==null,"cancel has no write callback");
  open(p);check(RadioGroup.last.checked==0x00f0b100,"reopen discards unsaved choice");RadioGroup.last.check(0x00f0b105);AlertDialog.positive.onClick(null,-1);check(TapPagePreference.mode(c)==5,"save writes requested choice");check(c.prefs.writes==1,"single save");check("右側上下".equals(p.summary),"saved summary");check(c.prefs.values.get("untouched").equals(42),"unrelated preference preserved");
  TapPagePreference again=new TapPagePreference(c,null);check("右側上下".equals(again.summary),"new preference retains selection");
  c.resources.configuration.locale=Locale.ENGLISH;TapPagePreference en=new TapPagePreference(c,null);check("Tap to turn pages".equals(en.title),"English title");check("Right edge: top / bottom".equals(en.summary),"English summary");
  c.resources.configuration.locale=Locale.SIMPLIFIED_CHINESE;TapPagePreference cn=new TapPagePreference(c,null);check("点击翻页".equals(cn.title),"simplified title");
  c.resources.configuration.locale=Locale.forLanguageTag("zh-Hant");TapPagePreference hant=new TapPagePreference(c,null);check("點擊翻頁".equals(hant.title),"script traditional");
  System.out.println("PASS "+checks+" production preference assertions; Android UI is doubled");
 }
}
