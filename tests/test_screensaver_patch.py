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
        # Small synthetic methods describe the existing two-item contract; full
        # historical App smali stays outside the public source repository.
        menu = root / patch_screensaver.MENU_PATH
        menu.write_text('# menu before\n'+patch_screensaver.MENU_SIGNATURE+'''
    .locals 3

    const/4 v0, 0x2

    new-array v0, v0, [Ljava/lang/CharSequence;

    const-string v1, "original wallpaper"
    const/4 v2, 0x0
    aput-object v1, v0, v2

    const-string v1, "original screensaver"
    const/4 v2, 0x1
    aput-object v1, v0, v2

    new-instance v1, Landroid/app/AlertDialog$Builder;
    invoke-direct {v1, p0}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V
    return-void
.end method
# menu after
''')
        listener = root / patch_screensaver.LISTENER_PATH
        listener.write_text('# listener before\n'+patch_screensaver.CLICK_SIGNATURE+'''
    .locals 1

    if-nez p2, :old_screensaver
    iget-object p1, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->callback:Lcom/alensw/ui/c/ScreenSaverHelper$Callback;
    invoke-interface {p1}, Lcom/alensw/ui/c/ScreenSaverHelper$Callback;->onWallpaperSelected()V
    return-void

    :old_screensaver
    const/4 p1, 0x1
    if-ne p2, p1, :old_done
    iget-object p1, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->context:Landroid/content/Context;
    iget-object p2, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->filePath:Ljava/lang/String;
    invoke-static {p1, p2}, Lcom/alensw/ui/c/ScreenSaverHelper;->setAsScreenSaver(Landroid/content/Context;Ljava/lang/String;)Z
    move-result p1
    :old_done
    return-void
.end method
# listener after
''')
        for variant in ['xml', 'xml-v28', 'xml-v30', 'xml-v31']:
            pref=root/'res'/variant/'main_pref.xml';pref.parent.mkdir(parents=True)
            pref.write_text('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"><PreferenceScreen android:title="@string/view"><PreferenceCategory android:title="@string/image_category"><SwitchPreference android:key="reader_mode"/></PreferenceCategory></PreferenceScreen></PreferenceScreen>')
        return path

    def test_all_api_preferences_and_entry_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);path=self.fixture(root)
            original_menu=(root/patch_screensaver.MENU_PATH).read_text()
            original_listener=(root/patch_screensaver.LISTENER_PATH).read_text()
            result=patch_screensaver.patch(root)
            self.assertEqual(result['preference_variants'],4)
            self.assertEqual(result['set_as_items'],3)
            text=path.read_text()
            self.assertTrue(text.startswith('# before\n'))
            self.assertTrue(text.endswith('\n# after\n'))
            self.assertIn('ScreensaverExport;->prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z',text)
            menu=(root/patch_screensaver.MENU_PATH).read_text()
            listener=(root/patch_screensaver.LISTENER_PATH).read_text()
            self.assertEqual(menu.count('設置為 B1051C 屏保'),1)
            self.assertEqual(menu.count(patch_screensaver.NEW_ARRAY_HEADER),1)
            self.assertEqual(menu.replace(patch_screensaver.OPTION_BLOCK,'').replace(
                patch_screensaver.NEW_ARRAY_HEADER,patch_screensaver.ARRAY_HEADER),original_menu)
            self.assertEqual(listener.replace(patch_screensaver.DISPATCH_BLOCK,''),original_listener)
            self.assertEqual(listener.count('BigmeScreensaver;->confirm(Landroid/content/Context;Ljava/lang/String;)V'),1)
            self.assertLess(listener.index(':quickpic_b1051c_existing\n'),listener.index('if-nez p2, :old_screensaver'))
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

    def test_unknown_menu_and_listener_shapes_do_not_write_any_file(self):
        cases = [
            (patch_screensaver.MENU_PATH,patch_screensaver.MENU_SIGNATURE,'.method public unrelated()V'),
            (patch_screensaver.MENU_PATH,'const/4 v0, 0x2','const/4 v0, 0x4'),
            (patch_screensaver.MENU_PATH,patch_screensaver.BUILDER,'    nop'),
            (patch_screensaver.LISTENER_PATH,patch_screensaver.CLICK_SIGNATURE,'.method public unrelated()V'),
            (patch_screensaver.LISTENER_PATH,'.locals 1','.locals 2'),
        ]
        for relative,old,new in cases:
            with self.subTest(relative=relative,pattern=old),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);self.fixture(root);p=root/relative
                self.assertIn(old,p.read_text());p.write_text(p.read_text().replace(old,new,1))
                before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
                with self.assertRaises(ValueError):patch_screensaver.patch(root)
                self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_missing_menu_files_do_not_partially_write(self):
        for relative in [patch_screensaver.MENU_PATH,patch_screensaver.LISTENER_PATH]:
            with self.subTest(relative=relative),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);self.fixture(root);(root/relative).unlink()
                before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
                with self.assertRaisesRegex(ValueError,'Missing'):patch_screensaver.patch(root)
                self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_damaged_existing_patch_is_not_silently_accepted(self):
        for relative,old,new in [
            (patch_screensaver.MENU_PATH,'設置為 B1051C 屏保','broken label'),
            (patch_screensaver.LISTENER_PATH,'->confirm(','->broken('),
        ]:
            with self.subTest(relative=relative),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);self.fixture(root);patch_screensaver.patch(root);p=root/relative
                p.write_text(p.read_text().replace(old,new,1))
                before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
                with self.assertRaisesRegex(ValueError,'Invalid existing'):patch_screensaver.patch(root)
                self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_duplicate_target_method_is_rejected_before_any_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.fixture(root);p=root/patch_screensaver.MENU_PATH
            p.write_text(p.read_text()+p.read_text())
            before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
            with self.assertRaisesRegex(ValueError,'exactly one'):patch_screensaver.patch(root)
            self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

if __name__=='__main__':unittest.main()
