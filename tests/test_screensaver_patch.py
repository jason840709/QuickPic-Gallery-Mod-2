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
    def fixture(self, root, app_owner='Lcom/alensw/PicFolderXPhotoPNG1/QuickApp;'):
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
    const v2, 0x7f0a0141
    invoke-virtual {v1, v2}, Landroid/app/AlertDialog$Builder;->setTitle(I)Landroid/app/AlertDialog$Builder;
    move-result-object v1
    new-instance v2, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;
    invoke-direct {v2, p0, p1, p2}, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;-><init>(Landroid/content/Context;Ljava/lang/String;Lcom/alensw/ui/c/ScreenSaverHelper$Callback;)V
    invoke-virtual {v1, v0, v2}, Landroid/app/AlertDialog$Builder;->setItems([Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;
    move-result-object p0
    invoke-virtual {p0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;
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
        app=root/'smali'/(app_owner[1:-1]+'.smali');app.parent.mkdir(parents=True)
        app.write_text('.class public '+app_owner+'\n.super Landroid/app/Application;\n'+patch_screensaver.STARTUP_SIGNATURE+'''\n    .locals 1
    invoke-static {}, '''+app_owner+'''->applyHiddenDefault()V
    const/4 v0, 0x1
    return v0
.end method
''')
        return path

    def test_all_api_preferences_and_entry_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);path=self.fixture(root)
            original_menu=(root/patch_screensaver.MENU_PATH).read_text()
            original_listener=(root/patch_screensaver.LISTENER_PATH).read_text()
            result=patch_screensaver.patch(root)
            self.assertEqual(result['preference_variants'],4)
            self.assertEqual(result['set_as_items'],4)
            text=path.read_text()
            self.assertTrue(text.startswith('# before\n'))
            self.assertTrue(text.endswith('\n# after\n'))
            self.assertIn('ScreensaverExport;->prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z',text)
            menu=(root/patch_screensaver.MENU_PATH).read_text()
            listener=(root/patch_screensaver.LISTENER_PATH).read_text()
            self.assertEqual(menu.count('設置為 B1051C 屏保'),1)
            self.assertEqual(menu.count(patch_screensaver.NEW_ARRAY_HEADER),1)
            original_prefix=original_menu[:original_menu.index(patch_screensaver.BUILDER)]
            restored_prefix=menu[:menu.index(patch_screensaver.BOOX_MARKER)-4]
            self.assertEqual(restored_prefix.replace(patch_screensaver.OPTION_BLOCK,'').replace(
                patch_screensaver.NEW_ARRAY_HEADER,patch_screensaver.ARRAY_HEADER),original_prefix)
            self.assertIn('ScreensaverMenu;->show(Landroid/content/Context;I[Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)V',menu)
            self.assertEqual(listener.replace(patch_screensaver.BOOX_DISPATCH_BLOCK,'').replace(patch_screensaver.DISPATCH_BLOCK,''),original_listener)
            self.assertEqual(listener.count('BooxScreensaver;->confirm(Landroid/content/Context;Ljava/lang/String;)V'),1)
            self.assertEqual(menu.count('設置為文石屏保'),1)
            app=next(root.glob('smali*/**/QuickApp.smali')).read_text()
            self.assertEqual(app.count(patch_screensaver.INITIALIZE_CALL),1)
            self.assertEqual(listener.count('BigmeScreensaver;->confirm(Landroid/content/Context;Ljava/lang/String;)V'),1)
            self.assertLess(listener.index(':quickpic_b1051c_existing\n'),listener.index('if-nez p2, :old_screensaver'))
            for pref in root.glob('res/xml*/main_pref.xml'):
                tree=ET.parse(pref).getroot()
                node=tree.find('.//com.alensw.jni.ScreensaverPreference')
                self.assertEqual(node.get(A+'title'),'設定屏保圖尺寸')
                self.assertEqual(node.get(A+'key'),'screensaver_size')
                options=tree.find('.//com.alensw.jni.ScreensaverOptions')
                self.assertEqual(options.get(A+'title'),'顯示屏保選項')
                category=tree.find('.//PreferenceCategory')
                self.assertEqual(list(category).index(options),list(category).index(node)+1)
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
            (patch_screensaver.MENU_PATH,'const v2, 0x7f0a0141','const v2, 0x7f0a0142'),
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

    def test_existing_17_menu_upgrades_without_duplicate_action(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.fixture(root);p=root/patch_screensaver.MENU_PATH
            p.write_text(p.read_text().replace(patch_screensaver.ARRAY_HEADER,patch_screensaver.THREE_ARRAY_HEADER)
                         .replace(patch_screensaver.BUILDER,patch_screensaver.OPTION_BLOCK+patch_screensaver.BUILDER))
            patch_screensaver.patch(root)
            text=p.read_text()
            self.assertEqual(text.count('設置為 B1051C 屏保'),1)
            self.assertEqual(text.count(patch_screensaver.VISIBILITY_MARKER),1)

    def test_missing_size_anchor_does_not_partially_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.fixture(root);p=root/'res/xml/main_pref.xml'
            p.write_text('<PreferenceScreen><Preference android:key="screensaver_size"/></PreferenceScreen>')
            before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
            with self.assertRaisesRegex(ValueError,'size preference anchor'):patch_screensaver.patch(root)
            self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_version19_dynamic_menu_upgrades_to_four_items(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.fixture(root);menu=root/patch_screensaver.MENU_PATH;listener=root/patch_screensaver.LISTENER_PATH
            original_listener=listener.read_text()
            menu.write_text(patch_screensaver._method(menu.read_text(),patch_screensaver.MENU_SIGNATURE,patch_screensaver._menu19,'19 menu'))
            listener.write_text(patch_screensaver._method(listener.read_text(),patch_screensaver.CLICK_SIGNATURE,patch_screensaver._listener19,'19 listener'))
            patch_screensaver.patch(root)
            self.assertEqual(menu.read_text().count('設置為文石屏保'),1)
            self.assertEqual(listener.read_text().replace(patch_screensaver.BOOX_DISPATCH_BLOCK,'').replace(patch_screensaver.DISPATCH_BLOCK,''),original_listener)

    def test_missing_or_damaged_startup_anchor_does_not_write_any_file(self):
        for replacement in ['', '    invoke-static {}, Lother/QuickApp;->applyHiddenDefault()V\n']:
            with self.subTest(replacement=replacement),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);self.fixture(root);app=next(root.glob('smali*/**/QuickApp.smali'))
                app.write_text(app.read_text().replace('    invoke-static {}, Lcom/alensw/PicFolderXPhotoPNG1/QuickApp;->applyHiddenDefault()V\n',replacement))
                before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
                with self.assertRaisesRegex(ValueError,'cold-start hidden-default anchor'):patch_screensaver.patch(root)
                self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_damaged_initialization_hook_does_not_write_any_file(self):
        for kind in ['descriptor','duplicate','position']:
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);self.fixture(root);patch_screensaver.patch(root);app=next(root.glob('smali*/**/QuickApp.smali'))
                text=app.read_text()
                if kind=='descriptor':text=text.replace(patch_screensaver.INITIALIZE_CALL,patch_screensaver.INITIALIZE_CALL.replace('Landroid/content/Context;','Ljava/lang/Object;'))
                elif kind=='duplicate':text=text.replace(patch_screensaver.INITIALIZE_CALL,patch_screensaver.INITIALIZE_CALL*2)
                else:text=text.replace(patch_screensaver.INITIALIZE_CALL,'').replace('    const/4 v0, 0x1',patch_screensaver.INITIALIZE_CALL+'    const/4 v0, 0x1')
                # Position case must actually differ from the immediately-after-anchor form.
                if kind=='position':text=text.replace(patch_screensaver.INITIALIZE_CALL+'    const/4 v0, 0x1','    const/4 v0, 0x1\n'+patch_screensaver.INITIALIZE_CALL)
                app.write_text(text);before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
                with self.assertRaises(ValueError):patch_screensaver.patch(root)
                self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_formal_package_owner_initializes_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);owner='Lio/github/jason840709/quickpic/QuickApp;';self.fixture(root,owner)
            patch_screensaver.patch(root)
            app=next(root.glob('smali*/**/QuickApp.smali')).read_text()
            anchor='    invoke-static {}, '+owner+'->applyHiddenDefault()V\n'
            self.assertIn(anchor+patch_screensaver.INITIALIZE_CALL,app)
            before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
            patch_screensaver.patch(root)
            self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_formal_package_rejects_other_call_owner_without_partial_write(self):
        owner='Lio/github/jason840709/quickpic/QuickApp;'
        for wrong in ['Lcom/alensw/PicFolderXPhotoPNG1/QuickApp;','Lother/QuickApp;']:
            with self.subTest(wrong=wrong),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);self.fixture(root,owner);app=next(root.glob('smali*/**/QuickApp.smali'))
                app.write_text(app.read_text().replace('{}, '+owner+'->applyHiddenDefault()V','{}, '+wrong+'->applyHiddenDefault()V'))
                before={p:p.read_bytes() for p in root.rglob('*') if p.is_file()}
                with self.assertRaisesRegex(ValueError,'cold-start hidden-default anchor'):patch_screensaver.patch(root)
                self.assertEqual(before,{p:p.read_bytes() for p in root.rglob('*') if p.is_file()})

if __name__=='__main__':unittest.main()
