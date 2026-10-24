"""Synthetic export and preference wiring; rendering is verified separately on Android."""
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import patch_screensaver
import patch_normalize
A = '{http://schemas.android.com/apk/res/android}'

class ScreensaverPatchTests(unittest.TestCase):
    def fixture(self, root):
        path = root/'smali/com/alensw/ui/c/XPhotoScreenSaverBridge.smali'
        path.parent.mkdir(parents=True)
        path.write_text('# before\n'+patch_screensaver.SIGNATURE+'\n    .locals 1\n    const/4 v0, 0x0\n    return v0\n.end method\n# after\n')
        for variant in ['xml', 'xml-v28', 'xml-v30', 'xml-v31']:
            pref=root/'res'/variant/'main_pref.xml';pref.parent.mkdir(parents=True)
            pref.write_text('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"><PreferenceScreen android:title="@string/view"><PreferenceCategory android:title="@string/image_category"><SwitchPreference android:key="reader_mode"/></PreferenceCategory></PreferenceScreen></PreferenceScreen>')
        return path

    def test_all_api_preferences_and_entry_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);path=self.fixture(root)
            result=patch_screensaver.patch(root)
            self.assertEqual(result['preference_variants'],4)
            text=path.read_text()
            self.assertTrue(text.startswith('# before\n'))
            self.assertTrue(text.endswith('\n# after\n'))
            self.assertIn('ScreensaverExport;->prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z',text)
            for pref in root.glob('res/xml*/main_pref.xml'):
                tree=ET.parse(pref).getroot()
                node=tree.find('.//com.alensw.jni.ScreensaverPreference')
                self.assertEqual(node.get(A+'title'),'設定屏保圖尺寸')
                self.assertEqual(node.get(A+'key'),'screensaver_size')
                self.assertEqual(tree.find('.//SwitchPreference').get(A+'key'),'reader_mode')
            before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
            patch_screensaver.patch(root)
            self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_maintained_base_progress_is_upgraded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);path=root/patch_normalize.PROGRESS_PATH
            path.parent.mkdir(parents=True)
            path.write_text('const-string v0, "正在產生 1860×2480 PNG，完成後將開啟圖片檢視器…"')
            patch_normalize.normalize_progress(root)
            self.assertIn(patch_normalize.NEW_PROGRESS,path.read_text())
            self.assertFalse(patch_normalize.normalize_progress(root)['changed'])

    def test_missing_category_does_not_partially_patch_export(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);path=self.fixture(root)
            pref=root/'res/xml-v31/main_pref.xml';pref.write_text('<PreferenceScreen/>')
            before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
            with self.assertRaisesRegex(ValueError,'image category'):patch_screensaver.patch(root)
            self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

if __name__=='__main__':unittest.main()
