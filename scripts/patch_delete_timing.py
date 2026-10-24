# SPDX-License-Identifier: MIT
"""Measure the 11.0.7 selected-image path without changing deletion decisions.

Only the bl worker starts a ThreadLocal session. Shared file/provider wrappers
are transparent delegates outside that session. No new storage probes or batches.
"""
from pathlib import Path
import re
from patch_file_delete import method, once

T = 'Lcom/alensw/jni/DeleteTiming;'
REPORT = T[:-1] + '$Report;'
BL = 'Lcom/alensw/ui/c/bl;'
FIELD = '.field private quickpicDeleteTiming:' + REPORT
FILES = ('smali/com/alensw/b/l/b.smali', 'smali/com/alensw/a/c.smali',
         'smali/com/alensw/a/ba.smali', 'smali/com/alensw/a/bc.smali',
         'smali/com/alensw/jni/FileDelete.smali', 'smali/com/alensw/ui/c/bl.smali')


def edit_method(text, signature, transform):
    match = method(text, signature)
    return text[:match.start()] + transform(match.group()) + text[match.end():]


def bridge(signature, call, result, stage, static=False):
    """Preserve the original return and throwable while closing a timed stage."""
    move = '' if result == 'V' else ('    move-result-object v0\n' if result.startswith('L') else '    move-result v0\n')
    ret = '    return-void' if result == 'V' else ('    return-object v0' if result.startswith('L') else '    return v0')
    return ('\n\n.method public ' + ('static ' if static else '') + signature + '\n'
        '    .locals 2\n'
        '    invoke-static {}, ' + T + '->begin' + stage + '()V\n'
        '    :quickpic_timing_start\n    ' + call + '\n' + move +
        '    :quickpic_timing_end\n'
        '    .catchall {:quickpic_timing_start .. :quickpic_timing_end} :quickpic_timing_throw\n'
        '    invoke-static {}, ' + T + '->end' + stage + '()V\n' + ret + '\n'
        '    :quickpic_timing_throw\n    move-exception v1\n'
        '    invoke-static {}, ' + T + '->end' + stage + '()V\n'
        '    throw v1\n.end method\n')


def file_wrappers(work, directory=False):
    ops = ('delete', 'exists', 'isDirectory') if directory else ('delete', 'exists')
    for operation in ops:
        target = {'delete': 'rawDelete', 'exists': 'exists', 'isDirectory': 'isDirectory'}[operation]
        pattern = r'invoke-virtual (\{[^}]+\}), Ljava/io/File;->' + operation + r'\(\)Z'
        if not re.search(pattern, work):
            raise ValueError('Missing original File.' + operation + ' operation')
        work = re.sub(pattern, lambda m: 'invoke-static ' + m[1] + ', ' + T + '->' + target + '(Ljava/io/File;)Z', work)
    return work


def patch(root):
    root = Path(root)
    texts = {name: (root / name).read_text() for name in FILES}
    if any(T in text for text in texts.values()):
        raise ValueError('Delete timing already applied or partial timing input')
    if 'SelectedDelete;' in texts[FILES[-1]] or 'quickpicSelectedCancelled' in texts[FILES[-1]]:
        raise ValueError('Discarded 11.0.8 batch worker must be reverted before timing')
    texts[FILES[0]] = edit_method(texts[FILES[0]], '.method public static c(Ljava/io/File;)Z', file_wrappers)

    def saf(work):
        work = once(work, 'invoke-virtual {p0}, Ljava/io/File;->isDirectory()Z',
                    'invoke-static {p0}, ' + T + '->isDirectory(Ljava/io/File;)Z')
        work = once(work, 'Lcom/alensw/a/c;->b(Ljava/lang/String;)Landroid/net/Uri;',
                    'Lcom/alensw/a/c;->quickpicTimedDeleteUri(Ljava/lang/String;)Landroid/net/Uri;')
        return once(work, 'Landroid/provider/DocumentsContract;->deleteDocument(Landroid/content/ContentResolver;Landroid/net/Uri;)Z',
                    T + '->deleteDocument(Landroid/content/ContentResolver;Landroid/net/Uri;)Z')
    texts[FILES[1]] = edit_method(texts[FILES[1]], '.method protected static f(Ljava/io/File;)Z', saf)
    texts[FILES[1]] += bridge('quickpicTimedDeleteUri(Ljava/lang/String;)Landroid/net/Uri;',
        'invoke-static {p0}, Lcom/alensw/a/c;->b(Ljava/lang/String;)Landroid/net/Uri;', 'Landroid/net/Uri;', 'Uri', True)

    texts[FILES[2]] = edit_method(texts[FILES[2]], '.method protected a(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I',
        lambda w: once(w, 'invoke-virtual {v0, p1, p2, p3}, Landroid/content/ContentResolver;->delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I',
                      'invoke-static {v0, p1, p2, p3}, ' + T + '->mediaDelete(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I'))
    texts[FILES[2]] += bridge('quickpicTimedMetadata(Ljava/lang/String;CZ)Z',
        'invoke-virtual {p0, p1, p2, p3}, Lcom/alensw/a/ba;->a(Ljava/lang/String;CZ)Z', 'Z', 'Metadata')
    texts[FILES[2]] += bridge('quickpicTimedForget(Ljava/lang/String;)V',
        'invoke-virtual {p0, p1}, Lcom/alensw/a/ba;->quickpicForget(Ljava/lang/String;)V', 'V', 'Cache')
    texts[FILES[3]] += bridge('quickpicTimedForget(Ljava/lang/String;)V',
        'invoke-virtual {p0, p1}, Lcom/alensw/a/bc;->a(Ljava/lang/String;)V', 'V', 'Cache')

    # This Java helper has an SDK gate; leave it and all branch decisions intact.
    fd = file_wrappers(texts[FILES[4]], directory=True)
    fd = once(fd, 'Landroid/provider/DocumentsContract;->deleteDocument(Landroid/content/ContentResolver;Landroid/net/Uri;)Z',
              T + '->deleteDocument(Landroid/content/ContentResolver;Landroid/net/Uri;)Z')
    fd = once(fd, 'Lcom/alensw/jni/FolderDelete;->documentFor(Landroid/content/Context;Ljava/io/File;)Landroid/net/Uri;',
              'Lcom/alensw/jni/FileDelete;->quickpicTimedDocumentFor(Landroid/content/Context;Ljava/io/File;)Landroid/net/Uri;')
    texts[FILES[4]] = fd + bridge('quickpicTimedDocumentFor(Landroid/content/Context;Ljava/io/File;)Landroid/net/Uri;',
        'invoke-static {p0, p1}, Lcom/alensw/jni/FolderDelete;->documentFor(Landroid/content/Context;Ljava/io/File;)Landroid/net/Uri;', 'Landroid/net/Uri;', 'Uri', True)

    bl = once(texts[FILES[5]], '# instance fields', '# instance fields\n' + FIELD)
    def worker(work):
        work = once(work, '    .locals 6', '    .locals 7')
        seam = '    iget-object v0, p0, ' + BL + '->a:Ljava/util/HashSet;'
        work = once(work, seam, seam + '\n    invoke-virtual {v0}, Ljava/util/HashSet;->size()I\n    move-result v0\n'
            '    invoke-static {p0, v0}, ' + T + '->begin(Landroid/app/Dialog;I)V\n'
            '    :quickpic_worker_start\n' + seam)
        work = once(work, '    check-cast v0, Lcom/alensw/a/d;',
                    '    check-cast v0, Lcom/alensw/a/d;\n    const/4 v6, 0x0\n    invoke-static {}, ' + T + '->beginFile()V')
        pattern = r'(    if-ltz v2, :\w+\n)(\s*if-nez v2, :\w+)'
        work, count = re.subn(pattern, r'\1\n    const/4 v6, 0x1\n\2', work)
        if count != 1: raise ValueError('Missing successful per-file branch')
        for original, timed in (
            ('Lcom/alensw/a/ba;->a(Ljava/lang/String;CZ)Z', 'Lcom/alensw/a/ba;->quickpicTimedMetadata(Ljava/lang/String;CZ)Z'),
            ('Lcom/alensw/a/ba;->quickpicForget(Ljava/lang/String;)V', 'Lcom/alensw/a/ba;->quickpicTimedForget(Ljava/lang/String;)V'),
            ('Lcom/alensw/a/bc;->a(Ljava/lang/String;)V', 'Lcom/alensw/a/bc;->quickpicTimedForget(Ljava/lang/String;)V')):
            work = once(work, original, timed)
        seam = '    invoke-virtual {p0}, ' + BL + '->d()Z\n\n    move-result v0'
        work = once(work, seam, '    invoke-static {}, ' + T + '->beginProgress()V\n' + seam + '\n'
            '    invoke-static {}, ' + T + '->endProgress()V\n'
            '    invoke-static {v6}, ' + T + '->endFile(Z)V')
        complete = ('    invoke-static {}, ' + T + '->complete()' + REPORT + '\n'
            '    move-result-object v0\n    iput-object v0, p0, ' + BL + '->quickpicDeleteTiming:' + REPORT + '\n')
        work = once(work, '    return-void', '    :quickpic_worker_end\n'
            '    .catchall {:quickpic_worker_start .. :quickpic_worker_end} :quickpic_worker_throw\n' + complete + '    return-void\n'
            '    :quickpic_worker_throw\n    move-exception v6\n' + complete + '    throw v6')
        return work
    bl = edit_method(bl, '.method protected a()V', worker)
    def finish(work):
        return once(work, '    return-void',
            '    iget-object v0, p0, ' + BL + '->quickpicDeleteTiming:' + REPORT + '\n'
            '    invoke-static {p0, v0}, ' + T + '->finish(Landroid/app/Dialog;' + REPORT + ')V\n    return-void')
    texts[FILES[5]] = edit_method(bl, '.method protected b()V', finish)
    # Validate every source seam before writing; partial failures leave no edits.
    for name, text in texts.items():
        (root / name).write_text(text)
    return {'changed': list(FILES), 'baseline': '11.0.7', 'per_file_progress': True,
            'measurement_only': True, 'new_storage_probes': 0}
