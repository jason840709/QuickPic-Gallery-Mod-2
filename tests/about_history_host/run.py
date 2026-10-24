#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check offline development history migration on fixtures and an actual decode.

Only About resources are copied. Optional aapt2 compilation validates resource
syntax; it does not build, sign, install or run an APK.
"""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import patch_about

A = patch_about.A


def display_value(value):
    value = value or ""
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    return value.replace("\\n", "\n").replace('\\"', '"').replace("\\'", "'")


def strings(text):
    return {n.get("name"): display_value(n.text) for n in ET.fromstring(text)
            if n.tag == "string"}


def structure(node):
    return (node.tag, dict(node.attrib), (node.text or "").strip(),
            [structure(n) for n in node])


def outside_about(text):
    if patch_about.HISTORY_KEY in text:
        start, end = patch_about.screen_span(text, "key", patch_about.HISTORY_KEY)
        if text[:start].endswith("\n    "):
            start -= len("\n    ")
        text = text[:start] + text[end:]
    start, end = patch_about.screen_span(text, "title", "@string/about")
    return text[:start] + text[end:]


class MigrationTests(unittest.TestCase):
    def test_preserves_supplied_and_unknown_records(self):
        old = 'Original E-Ink text\\n\\nChanges in 11.0.2\\nAn older exact record\\n\\nChanges in 11.9.99\\nA supplied future record'
        canonical = patch_about.replacement_strings(Path("res/values/strings.xml"))["eink_features_content"]
        result = patch_about.merge_history(old, canonical)
        self.assertIn("Original E-Ink text", patch_about.history_sections(result)[1]["11.0.0"][1])
        self.assertEqual(display_value(result).count("Changes in 11.0.2\n"), 1)
        self.assertIn("An older exact record", result)
        self.assertIn("A supplied future record", result)
        self.assertEqual(patch_about.merge_history(result, canonical), result)
        for version in patch_about.HISTORY_VERSIONS:
            self.assertIn("Changes in " + version + "\n", display_value(result))

    def test_consolidates_only_requested_deletion_releases(self):
        for folder in ("values", "values-zh-rTW", "values-zh-rCN"):
            canonical = patch_about.replacement_strings(Path("res") / folder / "strings.xml")["eink_features_content"]
            old = "\n\n".join("Changes in 11.0." + str(v) + "\nPrivate development marker " + str(v) for v in range(7, 14))
            old += "\n\nChanges in 11.9.99\nKeep future wording"
            merged = patch_about.merge_history(old, canonical)
            _, sections = patch_about.history_sections(merged)
            self.assertTrue(all("11.0." + str(v) not in sections for v in range(7, 13)))
            self.assertNotIn("Private development marker", merged)
            self.assertEqual(sections["11.0.13"], patch_about.history_sections(canonical)[1]["11.0.13"])
            self.assertEqual(sections["11.9.99"][1], "Keep future wording")
            self.assertEqual(patch_about.merge_history(merged, canonical), merged)

    def test_actual_newlines_and_android_quotes_remain_readable(self):
        old = '"E-Ink notes\n · Keep the author\\\'s wording and \\"quotes\\""'
        canonical = patch_about.replacement_strings(Path("res/values/strings.xml"))["eink_features_content"]
        result = patch_about.merge_history(old, canonical)
        self.assertIn(display_value(old), patch_about.history_sections(result)[1]["11.0.0"][1])
        self.assertEqual(patch_about.merge_history(result, canonical), result)

    def test_localized_labels_and_existing_resource_names(self):
        labels = {"values": "Version development history",
                  "values-zh-rTW": "版本開發紀錄", "values-zh-rCN": "版本开发记录"}
        for folder, label in labels.items():
            source = '<resources><string name="app_name">Keep</string></resources>'
            if folder == "values":
                source = source.replace("</resources>", '<string name="eink_features_title">Old</string><string name="eink_features_content">Original E-Ink</string></resources>')
            result = patch_about.normalize_strings(source, Path("res") / folder / "strings.xml")
            parsed = strings(result)
            self.assertEqual(parsed["eink_features_title"], label)
            self.assertEqual(parsed["app_name"], "Keep")
            self.assertIn("11.0.13", parsed["eink_features_content"])
            self.assertIn("11.0.15", parsed[patch_about.LATEST_CONTENT])
            self.assertEqual(parsed[patch_about.ARCHIVE_TITLE], {"values": "Complete version history", "values-zh-rTW": "完整版本開發紀錄", "values-zh-rCN": "完整版本开发记录"}[folder])
            self.assertEqual(patch_about.normalize_strings(result, Path("res") / folder / "strings.xml"), result)

    def test_header_removal_requires_successful_copy(self):
        original = '<LinearLayout xmlns:android="' + patch_about.NS + '"><TextView android:text="@string/build_type" android:textAllCaps="true"/><include layout="@layout/pref_divider"/><LinearLayout android:paddingLeft="16dip"><TextView android:text="@string/eink_features_title"/><TextView android:text="@string/eink_features_content"/></LinearLayout><include layout="@layout/pref_divider"/></LinearLayout>'
        with self.assertRaisesRegex(ValueError, "Copy development history"):
            patch_about.normalize_header(original)
        migrated = patch_about.history_layout(original)
        migrated_root = ET.fromstring(migrated)
        self.assertEqual(migrated_root.attrib, ET.fromstring(original)[2].attrib)
        self.assertEqual([n.get(A + "text") for n in migrated_root], ["@string/" + key for key in (
            patch_about.LATEST_TITLE, patch_about.LATEST_CONTENT, patch_about.ARCHIVE_TITLE, "eink_features_content")])
        header = patch_about.normalize_header(original, history_copied=True)
        self.assertNotIn("eink_features_", header)
        self.assertEqual(len(ET.fromstring(header).findall("include")), 1)
        self.assertEqual(patch_about.normalize_header(header), header)
        self.assertEqual(patch_about.history_layout(header, migrated), migrated)
        with self.assertRaisesRegex(ValueError, "Missing original history"):
            patch_about.history_layout(header)

    def test_history_is_offline_and_immediately_below_about(self):
        original = '<PreferenceScreen xmlns:android="' + patch_about.NS + '">\n<Preference android:key="storage" android:title="Preserve this" />\n<PreferenceScreen android:title="@string/about"><Preference android:layout="@layout/about_version"/><Preference android:title="@string/caio_name"><intent android:data="https://old.invalid/contact"/></Preference></PreferenceScreen>\n<Preference android:key="viewer" android:title="Preserve that"/>\n</PreferenceScreen>'
        result = patch_about.normalize_preferences(original)
        self.assertEqual(outside_about(result), outside_about(original))
        root = ET.fromstring(result)
        about = next(n for n in root if n.get(A + "title") == "@string/about")
        history = list(root)[list(root).index(about) + 1]
        self.assertEqual(history.get(A + "key"), patch_about.HISTORY_KEY)
        self.assertEqual(history.get(A + "title"), "@string/eink_features_title")
        self.assertFalse(list(history.iter("intent")))
        self.assertTrue(any(n.get(A + "title") == "@string/caio_name" for n in about.iter()))
        self.assertNotIn("old.invalid", result)
        self.assertEqual(patch_about.normalize_preferences(result), result)

    def test_old_heading_removed_and_eink_grouped_in_first_public_release(self):
        for heading, folder, record in (("E-Ink feature record before 11.0.0", "values", "Changes in 11.0.0"),
                                         ("11.0.0 前的 E-Ink 優化紀錄", "values-zh-rTW", "11.0.0 新增功能")):
            old = heading + "\n\nKeep all E-Ink instructions\n\n" + record + "\nKeep 64-bit instructions"
            canonical = patch_about.replacement_strings(Path("res") / folder / "strings.xml")["eink_features_content"]
            merged = display_value(patch_about.merge_history(old, canonical))
            preamble, sections = patch_about.history_sections(merged)
            self.assertFalse(preamble)
            self.assertNotIn(heading, merged)
            self.assertEqual(sections["11.0.0"][1], "Keep all E-Ink instructions\n\nKeep 64-bit instructions")
            self.assertEqual(list(sections), list(patch_about.HISTORY_VERSIONS))

    def test_latest_summary_and_archive_share_current_release_content(self):
        for folder in ("values", "values-zh-rTW", "values-zh-rCN"):
            values = patch_about.replacement_strings(Path("res") / folder / "strings.xml")
            _, archive = patch_about.history_sections(values["eink_features_content"])
            latest_preamble, latest = patch_about.history_sections(values[patch_about.LATEST_CONTENT])
            self.assertFalse(latest_preamble)
            self.assertEqual(latest["11.0.15"], archive["11.0.15"])
            self.assertEqual(len(latest), 1)
            self.assertEqual(values["version_number"], "11.0.15")

    def test_duplicate_supplied_versions_fail_without_dropping_text(self):
        canonical = patch_about.replacement_strings(Path("res/values/strings.xml"))["eink_features_content"]
        with self.assertRaisesRegex(ValueError, "Duplicate history record"):
            patch_about.merge_history("Changes in 11.0.2\nFirst\n\nChanges in 11.0.2\nSecond", canonical)

    def test_invalid_history_cannot_partially_write_a_decode(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = {
                "res/xml/main_pref.xml": '<PreferenceScreen xmlns:android="' + patch_about.NS + '"><PreferenceScreen android:title="@string/about"><Preference android:layout="@layout/about_version"/></PreferenceScreen></PreferenceScreen>',
                "res/values/strings.xml": '<resources><string name="app_name">Keep</string><string name="eink_features_title">Old</string><string name="eink_features_content">Old record</string></resources>',
                "res/layout/about_version.xml": '<LinearLayout xmlns:android="' + patch_about.NS + '"><TextView android:text="@string/build_type"/><LinearLayout><TextView android:text="@string/eink_features_content"/></LinearLayout></LinearLayout>',
            }
            for relative, text in paths.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            with self.assertRaisesRegex(ValueError, "Incomplete About history block"):
                patch_about.patch(root)
            self.assertEqual(paths, {relative: (root / relative).read_text() for relative in paths})
            self.assertFalse((root / "res/layout/quickpic_version_history.xml").exists())


def actual_checks(decoded, work, aapt2=None):
    original = {}
    source_paths = [*decoded.glob("res/xml*/main_pref.xml"),
                    *decoded.glob("res/values*/strings.xml"),
                    decoded / "res/layout/about_version.xml"]
    for extra in (decoded / "res/values/public.xml", decoded / "res/layout/quickpic_version_history.xml"):
        if extra.is_file():
            source_paths.append(extra)
    for source in source_paths:
        relative = source.relative_to(decoded)
        target = work / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        original[str(relative)] = source.read_text()
    checks = []

    def check(name, good):
        checks.append({"name": name, "passed": bool(good)})

    patch_about.patch(work)
    first = {str(p.relative_to(work)): p.read_text() for p in (work / "res").rglob("*.xml")}
    patch_about.patch(work)
    check("second complete patch is byte-identical", first == {
        str(p.relative_to(work)): p.read_text() for p in (work / "res").rglob("*.xml")})
    for relative, before in original.items():
        after = first[relative]
        if relative.endswith("main_pref.xml"):
            check(relative + " keeps all other settings byte-identical", outside_about(before) == outside_about(after))
            old = ET.fromstring(before)
            new = ET.fromstring(after)
            old_about = next(n for n in old if n.get(A + "title") == "@string/about")
            about = next(n for n in new if n.get(A + "title") == "@string/about")
            history = list(new)[list(new).index(about) + 1]
            check(relative + " history directly follows About", history.get(A + "key") == patch_about.HISTORY_KEY)
            check(relative + " history is fully offline", not list(history.iter("intent")))
            check(relative + " history opens dedicated layout", history[0].get(A + "layout") == "@layout/quickpic_version_history")
            # The maintained 11.0.4 baseline already has normalized credits.
            if any(n.get(A + "key") == "jason_developer" for n in old_about):
                check(relative + " retains complete About entries", structure(old_about) == structure(about))
        elif "/values" in relative and relative.endswith("strings.xml"):
            old = strings(before)
            new = strings(after)
            check(relative + " retains launcher name", old.get("app_name") == new.get("app_name"))
            if "eink_features_content" in old:
                preamble, old_sections = patch_about.history_sections(old["eink_features_content"])
                new_preamble, new_sections = patch_about.history_sections(new["eink_features_content"])
                check(relative + " starts with the first public release", not new_preamble and list(new_sections)[0] == "11.0.0")
                if preamble:
                    check(relative + " retains all E-Ink prose under 11.0.0", preamble in new_sections["11.0.0"][1])
                for version, (_, body) in old_sections.items():
                    if version in patch_about.CONSOLIDATED_VERSIONS:
                        check(relative + " replaces deletion development entry " + version,
                              version == "11.0.13" or version not in new_sections)
                    else:
                        check(relative + " retains all supplied prose for " + version, body in new_sections[version][1])
                check(relative + " removes only obsolete pre-release heading", "E-Ink feature record before 11.0.0" not in new["eink_features_content"] and "11.0.0 前的 E-Ink" not in new["eink_features_content"])
            if "eink_features_content" in new:
                body = new["eink_features_content"]
                for version in patch_about.HISTORY_VERSIONS:
                    pattern = r"(?:^|\n)(?:Changes in )?" + re.escape(version) + r"(?: 更新| 新增功能)?\n"
                    check(relative + " has one record for " + version, len(re.findall(pattern, body)) == 1)
                _, actual_sections = patch_about.history_sections(body)
                check(relative + " removes intermediate deletion entries", not any("11.0." + str(v) in actual_sections for v in range(7, 13)))
            if "version_number" in new:
                check(relative + " displays 11.0.15", new["version_number"] == "11.0.15")
        elif relative == "res/values/public.xml":
            check("all existing public resource IDs unchanged", before == after)
    for folder, label in (("values", "Version development history"),
                          ("values-zh-rTW", "版本開發紀錄"), ("values-zh-rCN", "版本开发记录")):
        values = strings(first["res/" + folder + "/strings.xml"])
        check(folder + " localizes development history", values["eink_features_title"] == label)
        check(folder + " latest summary contains only current version", list(patch_about.history_sections(values[patch_about.LATEST_CONTENT])[1]) == ["11.0.15"])
        check(folder + " latest and full history show identical current changes", patch_about.history_sections(values[patch_about.LATEST_CONTENT])[1]["11.0.15"] == patch_about.history_sections(values["eink_features_content"])[1]["11.0.15"])
    header = ET.fromstring(first["res/layout/about_version.xml"])
    check("About header contains only app identity", {n.get(A + "text") for n in header.iter("TextView")} == {
        "@string/app_name_full", "@string/version_number", "@string/build_type"})
    history = ET.fromstring(first["res/layout/quickpic_version_history.xml"])
    check("latest summary precedes complete chronological archive", [n.get(A + "text") for n in history] == [
        "@string/" + key for key in (patch_about.LATEST_TITLE, patch_about.LATEST_CONTENT, patch_about.ARCHIVE_TITLE, "eink_features_content")])
    check("two history sections have visible spacing", history[2].get(A + "layout_marginTop") == "24dip")
    check("history text adapts to the actual theme", all(n.get(A + "textColor", "").startswith("?android:") for n in history))
    if aapt2:
        # Compile the modified resources only. No final APK is produced.
        compile_res = work / "compile-res"
        for relative, content in first.items():
            if relative == "res/values/public.xml":
                continue
            path = compile_res / Path(relative).relative_to("res")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        result = subprocess.run([str(aapt2), "compile", "--dir", str(compile_res), "-o", str(work / "about-resources.zip")],
                                capture_output=True, text=True)
        check("aapt2 compiles all migrated layouts/preferences/strings", result.returncode == 0)
        if result.returncode:
            print(result.stderr, file=sys.stderr)
    return {"passed": all(c["passed"] for c in checks), "checks": checks,
            "scope": "About/history XML, latest summary and chronological archive, historical prose preservation, locales, stable public IDs and idempotence; no Android execution",
            "aapt2_resource_compilation": bool(aapt2),
            "input_sha256": {k: hashlib.sha256(v.encode()).hexdigest() for k, v in original.items()},
            "output_sha256": {k: hashlib.sha256(v.encode()).hexdigest() for k, v in first.items()}}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--decoded", type=Path)
    ap.add_argument("--work-dir", type=Path)
    ap.add_argument("--output-json", type=Path)
    ap.add_argument("--aapt2", type=Path)
    args = ap.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(MigrationTests))
    report = {"passed": result.wasSuccessful(), "unit_tests": result.testsRun}
    if args.decoded:
        with tempfile.TemporaryDirectory(prefix="quickpic-about-history-") as temp:
            report["actual_decode"] = actual_checks(args.decoded, args.work_dir or Path(temp), args.aapt2)
            report["passed"] &= report["actual_decode"]["passed"]
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    failures = [c["name"] for c in report.get("actual_decode", {}).get("checks", []) if not c["passed"]]
    print(json.dumps({"passed": report["passed"], "unit_tests": result.testsRun,
                      "actual_checks": len(report.get("actual_decode", {}).get("checks", [])),
                      "failures": failures}, ensure_ascii=False))
    raise SystemExit(0 if report["passed"] else 1)


if __name__ == "__main__":
    main()
