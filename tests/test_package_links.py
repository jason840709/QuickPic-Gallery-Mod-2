# SPDX-License-Identifier: MIT
"""Package-private DEX declarations must resolve with exact descriptors."""
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_local_links import LocalLinks

class PackagePrivateLinks(unittest.TestCase):
    def test_package_private_and_public_inherited_members(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = root / 'smali/test/Parent.smali'; target.parent.mkdir(parents=True)
            target.write_text('.class Ltest/Parent;\n.super Ljava/lang/Object;\n.field open:Z\n.field public static final text:Ljava/lang/String; = \"content://synthetic\"\n.method close(ZZJ)V\n.end method\n.method public size()I\n.end method\n')
            target.with_name('Child.smali').write_text('.class Ltest/Child;\n.super Ltest/Parent;\n')
            links = LocalLinks(root)
            report = links.check('iget-boolean v0, v1, Ltest/Child;->open:Z\ninvoke-virtual {v1, v2, v3, v4, v5}, Ltest/Child;->close(ZZJ)V\ninvoke-virtual {v1}, Ltest/Child;->size()I')
            self.assertEqual(len(report['checked_app_references']), 3)
            self.assertEqual(len(links.check('sget-object v0, Ltest/Child;->text:Ljava/lang/String;')['checked_app_references']), 1)
            with self.assertRaisesRegex(ValueError, 'Unresolved'):
                links.check('iget v0, v1, Ltest/Child;->open:I')
            with self.assertRaisesRegex(ValueError, 'Unresolved'):
                links.check('invoke-virtual {v1}, Ltest/Child;->size()V')

if __name__ == '__main__': unittest.main()
