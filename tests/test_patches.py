"""Synthetic fixtures only: no decoded app code or resources are stored here."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build
import patch_core
import patch_viewer
import patch_xphoto
import patch_about
import patch_normalize

A = "{http://schemas.android.com/apk/res/android}"


def write(root, relative, text):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def core_fixture(root, count=5):
    # Deliberately minimal invented smali structures matching hook signatures.
    write(root, patch_core.APP, ".method public onCreate()V\n    .locals 1\n    invoke-super {p0}, Landroid/app/Application;->onCreate()V\n    return-void\n.end method\n")
    write(root, patch_core.URI, "\n".join(signature + "\n    .locals 1\n    :try_start_0\n    return v0\n.end method\n" for signature in (
        ".method public static a(Landroid/content/Context;Landroid/content/Intent;)Z",
        ".method public static a(Landroid/app/Activity;Landroid/content/Intent;I)Z")))
    for index in range(count):
        write(root, "smali/com/alensw/fixture" + str(index) + ".smali", "    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->setResult(ILandroid/content/Intent;)V\n")


class BuildTests(unittest.TestCase):
    def test_supported_release_and_historical_base_hashes(self):
        from unittest.mock import patch
        for expected in (build.BASE_SHA256, build.RELEASE_BASE_SHA256):
            with patch.object(build, "sha", return_value=expected):
                self.assertEqual(build.validate_base(Path("fixture.apk")), expected)

    def test_unsupported_base_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "unsupported.apk"
            path.write_bytes(b"synthetic unsupported input")
            with self.assertRaisesRegex(ValueError, "Unsupported base"):
                build.validate_base(path)

    def test_tool_selection(self):
        self.assertEqual(build.apktool_command(Path("apktool")), [Path("apktool")])
        self.assertEqual(build.apktool_command(Path("apktool.jar")), ["java", "-jar", Path("apktool.jar")])

    def test_formal_release_identity(self):
        self.assertEqual((build.PACKAGE, build.VERSION_NAME, build.VERSION_CODE),
                         ("io.github.jason840709.quickpic", "11.0.23", 11023001))

    def test_native_output_rejected(self):
        import zipfile
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "synthetic.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("classes.dex", b"fixture")
                archive.writestr("resources.arsc", b"fixture")
                archive.writestr("lib/armeabi-v7a/fixture.so", b"fixture")
            with self.assertRaisesRegex(ValueError, "native"):
                build.verify_output(path)


class CoreTests(unittest.TestCase):
    def test_known_structure_hooks(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); core_fixture(root)
            report = patch_core.patch(root)
            self.assertEqual(report["external_result_hooks"], 5)
            self.assertIn("getCacheDir", (root / patch_core.APP).read_text())
            self.assertEqual((root / patch_core.URI).read_text().count("CompatIntents;->prepare("), 2)
            with self.assertRaisesRegex(ValueError, "already applied"):
                patch_core.patch(root)

    def test_bad_structure_has_no_partial_edits(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); core_fixture(root, count=4)
            before = {path: path.read_bytes() for path in root.rglob("*.smali")}
            with self.assertRaisesRegex(ValueError, "five result"):
                patch_core.patch(root)
            self.assertEqual(before, {path: path.read_bytes() for path in root.rglob("*.smali")})


class FeatureTests(unittest.TestCase):
    def test_about_preserves_eink_history_inside_first_public_release(self):
        values = patch_about.replacement_strings(Path("res/values-zh-rTW/strings.xml"))
        body = values["eink_features_content"]
        self.assertEqual(values["eink_features_title"], "版本開發紀錄")
        self.assertIn(patch_about.HISTORICAL_EINK_TW.replace("\n", "\\n"), body)
        self.assertTrue(body.startswith("11.0.0 新增功能"))
        self.assertNotIn("11.0.0 前的", body)
        self.assertEqual(values[patch_about.LATEST_TITLE], "最新版本更新內容")
        self.assertEqual(values[patch_about.ARCHIVE_TITLE], "完整版本開發紀錄")
        self.assertTrue(values[patch_about.LATEST_CONTENT].startswith("11.0.23 更新"))
        self.assertIn("已用 64 位元手機經過測試", body)
        for original_feature in ("無動畫", "左下角", "包含/排除資料夾", "v2 新增"):
            self.assertIn(original_feature, body)
        self.assertNotRegex(body, r"/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/")
        self.assertNotRegex(body, r"/(?:Users|Volumes)/")

    def test_about_empty_strings_survive_apktool_roundtrip(self):
        text = '<resources><string name="paypal"/><string name="unrelated">keep me</string><string name="github_url" /><string name="version_number">old</string></resources>'
        path = Path("res/values/strings.xml")
        result = patch_about.normalize_strings(text, path)
        root = ET.fromstring(result)
        self.assertEqual([n.get("name") for n in root], ["paypal", "unrelated", "github_url", "version_number"])
        self.assertEqual(root[1].text, "keep me")
        self.assertEqual(root[2].text, patch_about.REPO + "/blob/main/README.md")
        self.assertEqual(root[3].text, "11.0.23")
        self.assertEqual(patch_about.normalize_strings(result, path), result)

    def test_xphoto_only_changes_open_method(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            prefix = "# synthetic generation method remains byte-identical\n"
            suffix = "\n# synthetic subsequent method remains byte-identical\n"
            path = write(root, str(patch_xphoto.RELATIVE_PATH), prefix +
                         ".method private static openXPhoto(Landroid/content/Context;Ljava/io/File;)V\n    .locals 0\n    return-void\n.end method" + suffix)
            report = patch_xphoto.patch(root)
            self.assertTrue(report["png_generation_preserved"])
            self.assertTrue(path.read_text().startswith(prefix))
            self.assertTrue(path.read_text().endswith(suffix))
            self.assertFalse(patch_xphoto.patch(root)["changed"])

    def test_xphoto_wrong_method_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = write(root, str(patch_xphoto.RELATIVE_PATH), "# wrong synthetic method\n")
            with self.assertRaises(ValueError):
                patch_xphoto.patch(root)
            self.assertEqual(path.read_text(), "# wrong synthetic method\n")

    def test_viewer_menu_order_and_overflow(self):
        with tempfile.TemporaryDirectory() as temp:
            path = write(Path(temp), "menu.xml", '<menu xmlns:android="http://schemas.android.com/apk/res/android">' +
                         ''.join('<item android:id="@id/' + name + '" />' for name in ("share", "edit", "delete", "rotate", "slideshow", "set_as")) + '</menu>')
            patch_viewer.patch_menu(path)
            root = ET.parse(path).getroot()
            self.assertEqual([node.get(A + "id") for node in list(root)[:2]], ["@id/slideshow", "@id/delete"])
            self.assertEqual([node.get(A + "showAsAction") for node in root], ["always", "never", "never", "never", None, "never"])

    def test_timer_replaces_gallery_icon_in_all_drawable_variants(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for folder in ("drawable", "drawable-v24"):
                write(root, "res/" + folder + "/ic_slide_interval.xml", "<vector />")
            paths = patch_viewer.patch_interval_icon(root)
            self.assertEqual(len(paths), 2)
            for rel in paths:
                icon = ET.parse(root / rel).getroot()
                self.assertEqual(icon.get(A + "viewportWidth"), "24")
                self.assertEqual(icon.get(A + "viewportHeight"), "24")
                self.assertNotIn(A + "tint", icon.attrib)  # Java resolves the App action-icon theme.
                self.assertIn("M9,1h6v2H9z", icon.find("path").get(A + "pathData"))
            before = [(root / rel).read_bytes() for rel in paths]
            patch_viewer.patch_interval_icon(root)
            self.assertEqual(before, [(root / rel).read_bytes() for rel in paths])

    def test_about_preserves_other_preferences(self):
        original = '<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">\n<Preference android:key="storage" android:title="Keep storage" />\n<PreferenceScreen android:title="@string/about"><Preference android:layout="@layout/about_version" /><Preference android:title="@string/caio_name"><intent android:data="https://old.invalid/contact" /></Preference></PreferenceScreen>\n<Preference android:key="viewer" android:title="Keep viewer" />\n</PreferenceScreen>'
        result = patch_about.normalize_preferences(original)
        self.assertIn('<Preference android:key="storage" android:title="Keep storage" />', result)
        self.assertIn('<Preference android:key="viewer" android:title="Keep viewer" />', result)
        self.assertNotIn("old.invalid", result)
        self.assertEqual(patch_about.normalize_preferences(result), result)
        root = ET.fromstring(result)
        self.assertTrue(any(node.get(A + "title") == "@string/caio_name" for node in root.iter()))

    def test_runtime_menu_id_consistent(self):
        java = (ROOT / "src/com/alensw/jni/ViewerControls.java").read_text()
        self.assertIn("INTERVAL = 0x00f0a010", java)
        self.assertIn("0xf0a010", (ROOT / "scripts/patch_viewer.py").read_text())


class NormalizeTests(unittest.TestCase):
    def test_identity_paths_updater_and_native_removal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            old = "com.alensw.PicFolderXPhotoPNG1"
            write(root, "AndroidManifest.xml", '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + old + '"><application android:label="@string/app_name" largeHeap="true" /></manifest>')
            write(root, "apktool.yml", "versionCode: 1\nversionName: old\ntargetSdkVersion: 23\n")
            write(root, "res/values/strings.xml", '<resources><string name="app_name">Old Clone</string><string name="version_number">old</string><string name="app_name_full">old</string><string name="update_desc">old</string></resources>')
            write(root, "res/xml/main_pref.xml", '<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"><Preference android:title="@string/update"><intent android:data="https://fixture.invalid/old" /></Preference></PreferenceScreen>')
            write(root, "smali/com/alensw/PicFolderXPhotoPNG1/Fixture.smali", "Lcom/alensw/PicFolderXPhotoPNG1/Fixture;\nQuickPic885-Screensaver-Bridge-1\nb1051c-screensaver-1860x2480-\n")
            write(root, "lib/armeabi-v7a/fixture.so", "synthetic native fixture")
            write(root, patch_normalize.PROGRESS_PATH,
                  '# synthetic dialog\n    const-string v0, "' + patch_normalize.OLD_PROGRESS + '"\n')
            original = root / "original/AndroidManifest.xml"
            original.parent.mkdir()
            binary_backup = b"\x03\x00\x08\x00\xff\xfe\x80" + old.encode()
            original.write_bytes(binary_backup)
            report = patch_normalize.patch(root)
            self.assertEqual(original.read_bytes(), binary_backup)
            self.assertTrue(report["progress_toast"]["changed"])
            self.assertIn(patch_normalize.NEW_PROGRESS, (root / patch_normalize.PROGRESS_PATH).read_text())
            second = patch_normalize.patch(root)
            self.assertFalse(second["progress_toast"]["changed"])
            self.assertEqual(original.read_bytes(), binary_backup)
            self.assertEqual(report["package"], build.PACKAGE)
            self.assertFalse((root / "lib").exists())
            self.assertFalse((root / "smali/com/alensw/PicFolderXPhotoPNG1").exists())
            normalized = (root / "smali/io/github/jason840709/quickpic/Fixture.smali").read_text()
            self.assertIn("QuickPic-Screensaver", normalized)
            self.assertIn("screensaver-1860x2480-", normalized)
            self.assertNotIn(old.replace(".", "/"), normalized)
            self.assertEqual(ET.parse(root / "AndroidManifest.xml").getroot().get(A + "versionName"), "11.0.23")
            self.assertIn("QuickPic-Gallery-Mod-2/releases", (root / "res/xml/main_pref.xml").read_text())
            self.assertEqual(ET.parse(root / "res/values/strings.xml").getroot()[0].text, "快圖")


    def test_escaped_progress_preserves_other_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / patch_normalize.PROGRESS_PATH
            path.parent.mkdir(parents=True)
            old = json.dumps(patch_normalize.OLD_PROGRESS, ensure_ascii=True)[1:-1].encode("ascii")
            new = json.dumps(patch_normalize.NEW_PROGRESS, ensure_ascii=True)[1:-1].encode("ascii")
            prefix = b'# synthetic unrelated bytes\r\n    const-string v0, "'
            suffix = b'"\r\n# synthetic unrelated ending\r\n'
            path.write_bytes(prefix + old + suffix)
            self.assertTrue(patch_normalize.normalize_progress(root)["changed"])
            self.assertEqual(path.read_bytes(), prefix + new + suffix)
            self.assertFalse(patch_normalize.normalize_progress(root)["changed"])
            self.assertEqual(path.read_bytes(), prefix + new + suffix)

    def test_unknown_progress_rejected_without_edit(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = write(root, patch_normalize.PROGRESS_PATH, "# synthetic unknown progress\n")
            with self.assertRaisesRegex(ValueError, "progress string"):
                patch_normalize.normalize_progress(root)
            self.assertEqual(path.read_text(), "# synthetic unknown progress\n")


if __name__ == "__main__":
    unittest.main()
