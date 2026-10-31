from pathlib import Path
import tempfile,sys,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import patch_tap_settings as patch
class SettingsPatch(unittest.TestCase):
 def fixture(self,root,folder='xml',category='controls_category'):
  p=root/'res'/folder/'main_pref.xml';p.parent.mkdir(parents=True,exist_ok=True);p.write_text('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"><PreferenceCategory android:title="@string/'+category+'"><SwitchPreference android:key="single_tap_zoom"/></PreferenceCategory></PreferenceScreen>');return p
 def test_variants_and_idempotence(self):
  with tempfile.TemporaryDirectory()as tmp:
   r=Path(tmp);a=self.fixture(r);b=self.fixture(r,'xml-v30');self.assertEqual(len(patch.patch(r)),2);self.assertEqual(patch.patch(r),[])
   for p in [a,b]:
    s=p.read_text();self.assertEqual(s.count('com.alensw.jni.TapPagePreference'),1);self.assertIn('tap_page_layout',s);self.assertLess(s.index('tap_page_layout'),s.index('single_tap_zoom'))
 def test_other_category_is_rejected_without_partial_mutation(self):
  with tempfile.TemporaryDirectory()as tmp:
   r=Path(tmp);a=self.fixture(r);before=a.read_text();self.fixture(r,'xml-v30','image_category')
   with self.assertRaises(ValueError):patch.patch(r)
   self.assertEqual(a.read_text(),before)
