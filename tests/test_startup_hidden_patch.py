from pathlib import Path
import tempfile,unittest,sys,xml.etree.ElementTree as E
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"scripts"))
import patch_startup_hidden as patch
from patch_defaults import hidden_method
class StartupPatch(unittest.TestCase):
 def fixture(self,root):
  app=root/"smali/example/QuickApp.smali";app.parent.mkdir(parents=True);app.write_text(".class public Lexample/QuickApp;\n"+hidden_method("Lexample/QuickApp;"))
  callback=root/"smali/com/alensw/ui/a/ac.smali";callback.parent.mkdir(parents=True);callback.write_text("    invoke-static {}, Lexample/QuickApp;->applyHiddenDefault()V\n")
  for variant in ["xml","xml-v31"]:
   p=root/"res"/variant/"main_pref.xml";p.parent.mkdir(parents=True);p.write_text('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"><PreferenceScreen android:title="@string/security"/></PreferenceScreen>')
 def snapshot(self,r):return {str(p.relative_to(r)):p.read_bytes() for p in r.rglob("*") if p.is_file()}
 def test_switch_security_default_and_guarded_auth_callback(self):
  with tempfile.TemporaryDirectory() as d:
   r=Path(d);self.fixture(r);patch.patch(r)
   for p in r.glob("res/xml*/main_pref.xml"):
    row=E.parse(p).getroot().find(".//SwitchPreference");self.assertEqual(row.get(patch.A+"key"),"startup_show_hidden");self.assertEqual(row.get(patch.A+"defaultValue"),"true")
   self.assertIn("applyHiddenAfterUnlock()V",(r/"smali/com/alensw/ui/a/ac.smali").read_text())
   self.assertIn("if-eqz v0, :unlock_done",(r/"smali/example/QuickApp.smali").read_text())
 def test_late_missing_security_keeps_input_unchanged(self):
  with tempfile.TemporaryDirectory() as d:
   r=Path(d);self.fixture(r);p=r/"res/xml-v31/main_pref.xml";p.write_text(p.read_text().replace("@string/security","@string/view"));before=self.snapshot(r)
   with self.assertRaises(ValueError):patch.patch(r)
   self.assertEqual(before,self.snapshot(r))
 def test_repeat_patch_rejected_without_changes(self):
  with tempfile.TemporaryDirectory() as d:
   r=Path(d);self.fixture(r);patch.patch(r);before=self.snapshot(r)
   with self.assertRaises(ValueError):patch.patch(r)
   self.assertEqual(before,self.snapshot(r))
