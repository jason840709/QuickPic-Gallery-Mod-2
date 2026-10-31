"""Synthetic patch fixtures; no original decompiled app bodies are bundled."""
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import patch_tap_pages as patch


class TapPatchTests(unittest.TestCase):
    def fixture(self, root):
        picture = root / 'smali/com/alensw/ui/view/PictureView.smali'
        callback = root / 'smali/com/alensw/ui/view/bb.smali'
        picture.parent.mkdir(parents=True)
        picture.write_text('.class public ' + patch.PICTURE + '\n.super Lcom/alensw/ui/view/j;\n\n' + patch.TOUCH_DELEGATE + '\n')
        callback.write_text('''.class Lcom/alensw/ui/view/bb;
.super Ljava/lang/Object;
.field final synthetic a:Lcom/alensw/ui/view/PictureView;
.method public b(FF)V
    .locals 1
    const/4 v0, 0x0
    invoke-interface {v0, p1, p2}, Lcom/alensw/ui/view/q;->a(FF)V
    return-void
.end method
.method public c(FF)V
    .locals 0
    return-void
.end method
''')
        return picture, callback

    def test_restored_stream_has_no_release_interception(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); picture, callback = self.fixture(root)
            patch.patch(root)
            self.assertIn(patch.TOUCH_DELEGATE, picture.read_text())
            self.assertNotIn('getAction()', picture.read_text())
            self.assertIn('onConfirmedTap', callback.read_text())
            self.assertIn('.method public c(FF)V\n    .locals 0\n    return-void', callback.read_text())

    def test_reentry_has_no_duplicate_hook_or_bridges(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); paths = self.fixture(root)
            patch.patch(root); before = [p.read_bytes() for p in paths]
            patch.patch(root)
            self.assertEqual(before, [p.read_bytes() for p in paths])

    def test_unknown_touch_logic_is_preserved_by_rejection(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); picture, callback = self.fixture(root)
            picture.write_text(picture.read_text().replace('    return v0', '    const/4 v0, 0x1\n    return v0'))
            before = [p.read_bytes() for p in (picture, callback)]
            with self.assertRaisesRegex(ValueError, 'Unrecognized PictureView'):
                patch.patch(root)
            self.assertEqual(before, [p.read_bytes() for p in (picture, callback)])

    def test_wrong_confirmed_callback_does_not_partially_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); picture, callback = self.fixture(root)
            callback.write_text(callback.read_text().replace('->a(FF)V', '->b(FF)V'))
            before = [p.read_bytes() for p in (picture, callback)]
            with self.assertRaisesRegex(ValueError, 'expected listener'):
                patch.patch(root)
            self.assertEqual(before, [p.read_bytes() for p in (picture, callback)])

    def test_damaged_existing_bridge_is_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); picture, callback = self.fixture(root)
            patch.patch(root)
            picture.write_text(picture.read_text().replace('->getLocationInWindow([I)V', '->getLocationOnScreen([I)V'))
            with self.assertRaisesRegex(ValueError, 'Damaged tap Host'):
                patch.patch(root)

    def test_hit_test_converts_local_to_window_coordinates(self):
        self.assertIn('getLocationInWindow([I)V', patch.HOST_METHODS)
        self.assertIn('add-float v2, v2, p1', patch.HOST_METHODS)
        self.assertIn('add-float v1, v1, p2', patch.HOST_METHODS)
        self.assertIn('Lcom/alensw/ui/c/cx;->a(FF)Z', patch.HOST_METHODS)

    def test_previous_delegate_is_upgraded_and_event_still_reaches_detector(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); picture, callback = self.fixture(root)
            picture.write_text(picture.read_text().replace(patch.TOUCH_DELEGATE, patch.PREVIOUS_DELEGATE))
            patch.patch(root)
            body = picture.read_text()
            self.assertIn(patch.TOUCH_DELEGATE, body)
            self.assertEqual(body.count('->track(Landroid/view/View;Landroid/view/MotionEvent;)V'), 1)
            self.assertLess(body.index('->track('), body.index('Lcom/alensw/ui/view/w;->a(Landroid/view/MotionEvent;)Z'))
            before = picture.read_bytes(); patch.patch(root)
            self.assertEqual(before, picture.read_bytes())


if __name__ == '__main__':
    unittest.main()
