"""Synthetic DEX linkage fixtures; no historical App code."""
from pathlib import Path
import sys, tempfile, unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/"scripts"))
from check_local_links import LocalLinks

class LocalLinkTests(unittest.TestCase):
    def test_return_type_is_part_of_method_identity(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"smali/example/Cache.smali";p.parent.mkdir(parents=True)
            p.write_text(".class public Lexample/Cache;\n.super Ljava/lang/Object;\n.method public refresh(Z)Z\n.end method\n")
            links=LocalLinks(d)
            with self.assertRaisesRegex(ValueError,"refresh\\(Z\\)V"):
                links.check("invoke-virtual {v0, v1}, Lexample/Cache;->refresh(Z)V")
            self.assertEqual(len(links.check("invoke-virtual {v0, v1}, Lexample/Cache;->refresh(Z)Z")["checked_app_references"]),1)
    def test_field_and_inherited_method_resolution(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"smali/example";p.mkdir(parents=True)
            (p/"Base.smali").write_text(".class public Lexample/Base;\n.super Ljava/lang/Object;\n.field public visible:Z\n.method public refresh(Z)Z\n.end method\n")
            (p/"Child.smali").write_text(".class public Lexample/Child;\n.super Lexample/Base;\n")
            links=LocalLinks(d)
            report=links.check("invoke-virtual {v0, v1}, Lexample/Child;->refresh(Z)Z\niget-boolean v1, v0, Lexample/Child;->visible:Z")
            self.assertEqual(len(report["checked_app_references"]),2)
            with self.assertRaisesRegex(ValueError,"visible:I"):
                links.check("iget v1, v0, Lexample/Child;->visible:I")
    def test_missing_app_class_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ValueError,"DefinitelyMissing"):
                LocalLinks(d).check("invoke-virtual {v0, v1}, Lcom/alensw/a/DefinitelyMissing;->a(Z)Z")
    def test_platform_references_reported_as_external(self):
        with tempfile.TemporaryDirectory() as d:
            result=LocalLinks(d).check("invoke-virtual {v0}, Ljava/lang/String;->length()I")
            self.assertEqual(len(result["external_references_not_checked"]),1)
if __name__=="__main__":unittest.main()
