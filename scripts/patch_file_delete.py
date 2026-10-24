# SPDX-License-Identifier: MIT
"""Avoid duplicate MediaStore deletion only after authorized single-file SAF success."""
from pathlib import Path
import re

WRAPPER = r"""
.method public static quickpicDelete(Landroid/content/Context;Ljava/io/File;)I
    .locals 2
    invoke-static {p0, p1}, Lcom/alensw/jni/FileDelete;->tryDelete(Landroid/content/Context;Ljava/io/File;)I
    move-result v0
    if-gez v0, :quickpic_delete_return
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
    const/16 v1, 0x1e
    if-ge v0, v1, :quickpic_delete_original
    invoke-static {p0, p1}, Lcom/alensw/jni/FastMediaDelete;->tryDelete(Landroid/content/Context;Ljava/io/File;)I
    move-result v0
    if-gez v0, :quickpic_delete_return
    :quickpic_delete_original
    invoke-static {p1}, Lcom/alensw/b/l/b;->c(Ljava/io/File;)Z
    move-result v0
    if-eqz v0, :quickpic_delete_failed
    const/4 v0, 0x0
    return v0
    :quickpic_delete_failed
    const/4 v0, -0x1
    :quickpic_delete_return
    return v0
.end method
""".strip()

FORGET = r"""
.method public quickpicForget(Ljava/lang/String;)V
    .locals 1
    if-eqz p1, :quickpic_forget_done
    iget-object v0, p0, Lcom/alensw/a/ba;->q:Lcom/alensw/a/an;
    invoke-virtual {v0, p1}, Lcom/alensw/a/an;->a(Ljava/lang/String;)V
    iget-object v0, p0, Lcom/alensw/a/ba;->p:Lcom/alensw/a/am;
    invoke-virtual {v0, p1}, Lcom/alensw/a/am;->c(Ljava/lang/String;)Ljava/lang/Integer;
    :quickpic_forget_done
    return-void
.end method
""".strip()


def method(text, signature):
    matches = list(re.finditer(r'^' + re.escape(signature) + r'\n.*?^\.end method', text, re.M | re.S))
    if len(matches) != 1:
        raise ValueError('Expected one method: ' + signature)
    return matches[0]


def once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Unexpected single-file deletion seam: ' + old.splitlines()[0])
    return text.replace(old, new, 1)


def add_method(text, name, body):
    if '->' + name + '(' in text or '.method public ' + name + '(' in text or '.method public static ' + name + '(' in text:
        if text.count(body) != 1:
            raise ValueError('Unexpected existing helper: ' + name)
        return text
    return text.rstrip() + '\n\n' + body + '\n'


def patch(root):
    root = Path(root)
    relative = ['smali/com/alensw/b/l/b.smali', 'smali/com/alensw/a/ba.smali',
                'smali/com/alensw/ui/c/bl.smali', 'smali/com/alensw/ui/c/as.smali']
    texts = {name: (root / name).read_text() for name in relative}
    if '.method public static c(Ljava/io/File;)Z' not in texts[relative[0]]:
        raise ValueError('Missing original boolean file helper')
    texts[relative[0]] = add_method(texts[relative[0]], 'quickpicDelete', WRAPPER)
    ba = texts[relative[1]]
    for field in ('q:Lcom/alensw/a/an;', 'p:Lcom/alensw/a/am;'):
        if field not in ba:
            raise ValueError('Missing original path-cache field: ' + field)
    texts[relative[1]] = add_method(ba, 'quickpicForget', FORGET)

    text = texts[relative[2]]
    match = method(text, '.method protected a()V')
    work = match.group(0)
    if ':quickpic_bl_deleted' not in work:
        work = once(work,
            '    invoke-static {v2}, Lcom/alensw/b/l/b;->c(Ljava/io/File;)Z\n\n    move-result v2\n\n    if-eqz v2, :cond_1',
            '    invoke-virtual {p0}, Landroid/app/Dialog;->getContext()Landroid/content/Context;\n    move-result-object v5\n    invoke-static {v5, v2}, Lcom/alensw/b/l/b;->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I\n    move-result v2\n    if-ltz v2, :cond_1\n    if-nez v2, :quickpic_bl_forget')
        seam = '    invoke-virtual {v2, v3, v4, v5}, Lcom/alensw/a/ba;->a(Ljava/lang/String;CZ)Z'
        work = once(work, seam, seam + '\n    goto :quickpic_bl_deleted\n    :quickpic_bl_forget\n    iget-object v2, p0, Lcom/alensw/ui/c/bl;->b:Lcom/alensw/a/ba;\n    invoke-virtual {v2, v3}, Lcom/alensw/a/ba;->quickpicForget(Ljava/lang/String;)V\n    :quickpic_bl_deleted')
    if work.count('->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I') != 1 or work.count('->quickpicForget(Ljava/lang/String;)V') != 1:
        raise ValueError('Invalid selected-image hooks')
    texts[relative[2]] = text[:match.start()] + work + text[match.end():]

    text = texts[relative[3]]
    match = method(text, '.method protected m()V')
    work = match.group(0)
    if ':quickpic_as_deleted' not in work:
        work = once(work,
            '    invoke-static {v0}, Lcom/alensw/b/l/b;->i(Ljava/lang/String;)Z\n\n    move-result v1\n\n    if-eqz v1, :cond_0',
            '    new-instance v1, Ljava/io/File;\n    invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V\n    iget-object v2, p0, Lcom/alensw/ui/c/as;->C:Lcom/alensw/ui/c/cx;\n    invoke-static {v2, v1}, Lcom/alensw/b/l/b;->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I\n    move-result v1\n    if-ltz v1, :cond_0\n    if-nez v1, :quickpic_as_forget')
        seam = '    invoke-virtual {v1, v0, v2, v4}, Lcom/alensw/a/ba;->a(Ljava/lang/String;CZ)Z'
        app = re.search(r'sget-object v1, (L[^;]+;)->s:Lcom/alensw/a/ba;', work)
        if not app:
            raise ValueError('Missing original application metadata manager')
        work = once(work, seam, seam + '\n    goto :quickpic_as_deleted\n    :quickpic_as_forget\n    sget-object v1, ' + app.group(1) + '->s:Lcom/alensw/a/ba;\n    invoke-virtual {v1, v0}, Lcom/alensw/a/ba;->quickpicForget(Ljava/lang/String;)V\n    :quickpic_as_deleted')
    if work.count('->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I') != 1 or work.count('->quickpicForget(Ljava/lang/String;)V') != 1:
        raise ValueError('Invalid local-viewer hooks')
    texts[relative[3]] = text[:match.start()] + work + text[match.end():]
    # Validate every seam before writing any supplied source file.
    changed = []
    for name in relative:
        path = root / name
        if path.read_text() != texts[name]:
            path.write_text(texts[name])
            changed.append(name)
    return {'changed': changed, 'single_file_saf_deduplication': True}
