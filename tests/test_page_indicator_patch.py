"""Synthetic patch boundaries only; real viewer behavior is tested on Android."""
from pathlib import Path
import sys, tempfile, unittest, xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import patch_page_indicator as patch
A=patch.A

class PagePatchTests(unittest.TestCase):
    def fixture(self,root):
        p=root/'smali/com/alensw/ui/view/j.smali';p.parent.mkdir(parents=True)
        p.write_text('.class public Lcom/alensw/ui/view/j;\n.super Landroid/view/View;\n'
            '.method protected onDetachedFromWindow()V\n    .locals 1\n    return-void\n.end method\n'
            '.method public onDraw(Landroid/graphics/Canvas;)V\n    .locals 0\n    return-void\n.end method\n')
        for name in ['as','dp']:
            p=root/('smali/com/alensw/ui/c/'+name+'.smali');p.parent.mkdir(parents=True,exist_ok=True)
            p.write_text('.class public Lcom/alensw/ui/c/'+name+';\n'
                '.method protected a(Lcom/alensw/b/h/j;Z)V\n    .locals 1\n'
                '    invoke-static {p0, p1}, Lcom/alensw/jni/ReaderMode;->beforeDisplay(Lcom/alensw/jni/ReaderMode$Host;Ljava/lang/Object;)Z\n'
                '    invoke-virtual {v0, p1, p2}, Lcom/alensw/ui/view/PictureView;->a(Lcom/alensw/b/h/j;Z)V\n    return-void\n.end method\n'
                '.method private d(Z)V\n    .locals 1\n    return-void\n.end method\n')
        for variant in ['xml','xml-v31']:
            p=root/'res'/variant/'main_pref.xml';p.parent.mkdir(parents=True)
            p.write_text('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">'
                '<PreferenceScreen android:title="@string/interface"><PreferenceCategory android:title="@string/behavior_category"/></PreferenceScreen>'
                '<PreferenceScreen android:title="@string/view"><PreferenceCategory android:title="@string/behavior_category"/></PreferenceScreen></PreferenceScreen>')
        p=root/'res/layout/viewer.xml';p.parent.mkdir(parents=True)
        p.write_text('<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"><View android:id="@id/showing"/></RelativeLayout>')
    def snapshot(self,root):return {str(p.relative_to(root)):p.read_bytes() for p in root.rglob('*') if p.is_file()}
    def test_setting_scope_and_overlay_order(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);before=(root/'smali/com/alensw/ui/view/j.smali').read_text()
            patch.patch(root)
            for path in (root/'res').glob('xml*/main_pref.xml'):
                tree=ET.parse(path).getroot();screens=list(tree)
                self.assertIsNone(screens[0].find('.//SwitchPreference'))
                switch=screens[1].find('.//SwitchPreference')
                self.assertEqual(switch.get(A+'key'),'show_page_number')
                self.assertEqual(switch.get(A+'defaultValue'),'false')
            label=list(ET.parse(root/'res/layout/viewer.xml').getroot())[-1]
            self.assertEqual(label.get(A+'id'),'@+id/quickpic_page_number')
            self.assertEqual(label.get(A+'clickable'),'false')
            self.assertEqual(label.get(A+'textSize'),'@dimen/detail_text_size')
            current=(root/'smali/com/alensw/ui/view/j.smali').read_text()
            self.assertEqual(patch.method(current,'.method public onDraw(Landroid/graphics/Canvas;)V')[2],
                             patch.method(before,'.method public onDraw(Landroid/graphics/Canvas;)V')[2])
    def test_late_missing_settings_does_not_write(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root)
            (root/'res/xml-v31/main_pref.xml').write_text('<PreferenceScreen/>')
            before=self.snapshot(root)
            with self.assertRaises(ValueError):patch.patch(root)
            self.assertEqual(before,self.snapshot(root))
    def test_bad_transition_does_not_write(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);p=root/'smali/com/alensw/ui/c/dp.smali'
            p.write_text(p.read_text().replace('.method private d(Z)V','.method private unrelated(Z)V'))
            before=self.snapshot(root)
            with self.assertRaises(ValueError):patch.patch(root)
            self.assertEqual(before,self.snapshot(root))
    def test_duplicate_rejected_before_write(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root);patch.patch(root);before=self.snapshot(root)
            with self.assertRaises(ValueError):patch.patch(root)
            self.assertEqual(before,self.snapshot(root))
