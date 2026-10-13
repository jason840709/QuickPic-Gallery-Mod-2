# SPDX-License-Identifier: MIT
"""Resolve app-owned DEX member references, including exact return descriptors.

Android/framework members are outside this check. This complements host doubles;
it does not run ART or check register types and permissions.
"""
from pathlib import Path
import re

class LocalLinks:
    def __init__(self, root):
        self.root = Path(root)
        self.cache = {}
    def read(self, owner):
        if owner not in self.cache:
            files = list(self.root.glob("smali*/" + owner[1:-1] + ".smali"))
            if len(files) > 1:
                raise ValueError("Duplicate class " + owner)
            self.cache[owner] = files[0].read_text() if files else None
        return self.cache[owner]
    def declares(self, owner, member, kind, visited=None):
        visited = set() if visited is None else visited
        if owner in visited:
            return False
        visited.add(owner)
        text = self.read(owner)
        if text is None:
            return False
        if kind == "method":
            declared = re.findall(r"^\.method [^\n]*? (\S+\([^\n]+)$", text, re.M)
        else:
            declared = re.findall(r"^\.field [^\n]*? (\S+:[^ =\n]+)", text, re.M)
        if member in declared:
            return True
        parents = re.findall(r"^\.(?:super|implements) (L[^;]+;)", text, re.M)
        return any(self.declares(parent, member, kind, visited) for parent in parents)
    def check(self, body):
        checked, external, missing = [], [], []
        for line in body.splitlines():
            call = re.search(r"invoke-[\w/-]+\s+\{[^}]*\},\s*(L[^;]+;)->([^\s]+)", line)
            field = re.search(r"(?:[is]get|[is]put)[\w/-]*\s+.*?,\s*(L[^;]+;)->([^\s]+)", line)
            match = call or field
            if not match:
                continue
            owner, member = match.groups()
            key = owner + "->" + member
            if self.read(owner) is None:
                # Missing app/library classes are unresolved, not framework APIs.
                framework = ("Landroid/", "Ljava/", "Ljavax/", "Ldalvik/",
                             "Lorg/xml/", "Lorg/w3c/", "Lorg/json/")
                if owner.startswith(framework):
                    external.append(key)
                else:
                    missing.append(key)
            elif self.declares(owner, member, "method" if call else "field"):
                checked.append(key)
            else:
                missing.append(key)
        if missing:
            raise ValueError("Unresolved app member references: " + ", ".join(missing))
        return {"checked_app_references": checked, "external_references_not_checked": external}

def check_hidden_default(root):
    root = Path(root)
    paths = list(root.glob("smali*/**/QuickApp.smali"))
    if len(paths) != 1:
        raise ValueError("Expected one QuickApp")
    text = paths[0].read_text()
    start = text.index(".method public static applyHiddenDefault()V")
    end = text.index(".end method", start)
    return LocalLinks(root).check(text[start:end])

if __name__ == "__main__":
    import argparse, json
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--decoded", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    try:
        report = dict(passed=True, **check_hidden_default(args.decoded))
    except (ValueError, OSError) as error:
        report = {"passed": False, "error": str(error)}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    raise SystemExit(0 if report["passed"] else 1)
