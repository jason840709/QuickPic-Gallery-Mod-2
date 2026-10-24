# SPDX-License-Identifier: MIT
"""Replace only the album-folder deletion worker in a supplied external decode."""
from pathlib import Path
import re

HELPER = 'Lcom/alensw/jni/FolderDelete;'
RESULT = 'Lcom/alensw/jni/FolderDelete$Result;'
FIELD = '.field private folderDeleteResult:' + RESULT


def replace_method(text, signature, replacement):
    start = text.index(signature)
    end = text.index('.end method', start) + len('.end method')
    return text[:start] + replacement.strip() + text[end:]


def patch(root):
    root = Path(root)
    path = root / 'smali/com/alensw/ui/c/cf.smali'
    text = path.read_text()
    if '.super Lcom/alensw/ui/a/ao;' not in text:
        raise ValueError('Unexpected folder-delete worker superclass')
    for member in ('a:Ljava/util/ArrayList;', 'b:Lcom/alensw/a/ba;', 'c:Lcom/alensw/a/bc;', 'e:Lcom/alensw/ui/c/bz;'):
        if member not in text:
            raise ValueError('Unexpected folder-delete worker state: ' + member)
    if FIELD not in text:
        text = text.replace('# direct methods', FIELD + '\n\n# direct methods', 1)
    constructor = re.search(r'\.method constructor <init>\([^\n]+\)V\n.*?\.end method', text, re.S)
    if not constructor:
        raise ValueError('Missing folder-delete worker constructor')
    part = constructor.group(0)
    seam = '    invoke-direct {p0, p2, p3, p4}, Lcom/alensw/ui/a/ao;-><init>(Landroid/app/Activity;Ljava/lang/String;I)V'
    if '    invoke-virtual {p5}, Ljava/util/ArrayList;->size()I' not in part:
        if part.count(seam) != 1:
            raise ValueError('Unexpected folder-delete dialog constructor')
        part = part.replace(seam, '    invoke-virtual {p5}, Ljava/util/ArrayList;->size()I\n    move-result p4\n\n' + seam)
        text = text[:constructor.start()] + part + text[constructor.end():]
    work = r'''
.method protected a()V
    .locals 9
    iget-object v0, p0, Lcom/alensw/ui/c/cf;->a:Ljava/util/ArrayList;
    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I
    move-result v1
    new-instance v2, Lcom/alensw/jni/FolderDelete$Result;
    invoke-direct {v2, v1}, Lcom/alensw/jni/FolderDelete$Result;-><init>(I)V
    iput-object v2, p0, Lcom/alensw/ui/c/cf;->folderDeleteResult:Lcom/alensw/jni/FolderDelete$Result;
    invoke-virtual {v0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
    move-result-object v0
    :folder_delete_next
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
    move-result v1
    if-eqz v1, :folder_delete_done
    invoke-static {p0, v2}, Lcom/alensw/jni/FolderDelete;->mayContinue(Landroid/app/Dialog;Lcom/alensw/jni/FolderDelete$Result;)Z
    move-result v1
    if-eqz v1, :folder_delete_done
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Lcom/alensw/a/e;
    iget-object v4, p0, Lcom/alensw/ui/c/cf;->b:Lcom/alensw/a/ba;
    invoke-virtual {v3}, Lcom/alensw/a/e;->getPath()Ljava/lang/String;
    move-result-object v5
    invoke-virtual {v4, v5}, Lcom/alensw/a/ba;->a(Ljava/lang/String;)V
    invoke-static {p0, v3, v2}, Lcom/alensw/jni/FolderDelete;->deleteOne(Landroid/app/Dialog;Ljava/io/File;Lcom/alensw/jni/FolderDelete$Result;)Z
    move-result v4
    :folder_delete_progress
    invoke-virtual {p0}, Lcom/alensw/ui/c/cf;->d()Z
    move-result v1
    if-nez v1, :folder_delete_next
    :folder_delete_done
    iget-object v3, p0, Lcom/alensw/ui/c/cf;->a:Ljava/util/ArrayList;
    invoke-virtual {v2, v3}, Lcom/alensw/jni/FolderDelete$Result;->settleSelected(Ljava/util/ArrayList;)V
    iget-object v3, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    iget-object v3, v3, Lcom/alensw/ui/c/bz;->l:Lcom/alensw/a/x;
    invoke-virtual {v3}, Lcom/alensw/a/x;->h()Ljava/util/ArrayList;
    move-result-object v3
    iget-object v4, p0, Lcom/alensw/ui/c/cf;->a:Ljava/util/ArrayList;
    invoke-static {v3, v4}, Lcom/alensw/jni/FolderDelete;->snapshot(Ljava/util/ArrayList;Ljava/util/ArrayList;)Ljava/util/ArrayList;
    move-result-object v3
    invoke-virtual {v3}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
    move-result-object v0
    :folder_delete_cache_model
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
    move-result v1
    if-eqz v1, :folder_delete_return
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Lcom/alensw/a/e;
    invoke-virtual {v2, v3}, Lcom/alensw/jni/FolderDelete$Result;->wasDeleted(Ljava/io/File;)Z
    move-result v4
    if-eqz v4, :folder_delete_cache_model
    invoke-virtual {v3}, Lcom/alensw/a/e;->j()I
    move-result v4
    add-int/lit8 v4, v4, -0x1
    :folder_delete_cache
    if-ltz v4, :folder_delete_cache_model
    invoke-virtual {v3, v4}, Lcom/alensw/a/e;->e(I)Lcom/alensw/a/d;
    move-result-object v5
    if-eqz v5, :folder_delete_cache_next
    new-instance v6, Ljava/io/File;
    iget-object v5, v5, Lcom/alensw/a/d;->b:Ljava/lang/String;
    invoke-direct {v6, v3, v5}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
    invoke-virtual {v6}, Ljava/io/File;->getPath()Ljava/lang/String;
    move-result-object v5
    iget-object v6, p0, Lcom/alensw/ui/c/cf;->c:Lcom/alensw/a/bc;
    invoke-virtual {v6, v5}, Lcom/alensw/a/bc;->a(Ljava/lang/String;)V
    :folder_delete_cache_next
    add-int/lit8 v4, v4, -0x1
    goto :folder_delete_cache
    :folder_delete_return
    return-void
.end method
'''
    finish = r'''
.method protected b()V
    .locals 6
    iget-object v0, p0, Lcom/alensw/ui/c/cf;->folderDeleteResult:Lcom/alensw/jni/FolderDelete$Result;
    if-eqz v0, :folder_finish_layout
    iget-object v1, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    iget-object v1, v1, Lcom/alensw/ui/c/bz;->l:Lcom/alensw/a/x;
    invoke-virtual {v1}, Lcom/alensw/a/x;->h()Ljava/util/ArrayList;
    move-result-object v1
    iget-object v2, p0, Lcom/alensw/ui/c/cf;->a:Ljava/util/ArrayList;
    invoke-static {v1, v2}, Lcom/alensw/jni/FolderDelete;->snapshot(Ljava/util/ArrayList;Ljava/util/ArrayList;)Ljava/util/ArrayList;
    move-result-object v1
    invoke-virtual {v1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :folder_finish_next
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :folder_finish_result
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Lcom/alensw/a/e;
    invoke-virtual {v0, v2}, Lcom/alensw/jni/FolderDelete$Result;->wasDeleted(Ljava/io/File;)Z
    move-result v3
    if-eqz v3, :folder_finish_failed
    iget-object v3, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    iget-object v3, v3, Lcom/alensw/ui/c/bz;->l:Lcom/alensw/a/x;
    invoke-virtual {v3, v2}, Lcom/alensw/a/x;->f(Lcom/alensw/a/e;)Lcom/alensw/a/e;
    goto :folder_finish_next
    :folder_finish_failed
    invoke-virtual {v0, v2}, Lcom/alensw/jni/FolderDelete$Result;->wasAttempted(Ljava/io/File;)Z
    move-result v3
    if-eqz v3, :folder_finish_next
    invoke-virtual {v0, v2}, Lcom/alensw/jni/FolderDelete$Result;->shouldRequestPermission(Ljava/io/File;)Z
    move-result v3
    if-eqz v3, :folder_finish_reload
    iget-object v3, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    invoke-virtual {v2}, Lcom/alensw/a/e;->getPath()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {v3, v4}, Lcom/alensw/ui/c/bz;->a(Ljava/lang/String;)Z
    :folder_finish_reload
    iget-object v3, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    const/4 v4, 0x0
    invoke-virtual {v3, v2, v4}, Lcom/alensw/ui/c/bz;->a(Lcom/alensw/a/e;Ljava/lang/Runnable;)V
    goto :folder_finish_next
    :folder_finish_result
    invoke-static {p0, v0}, Lcom/alensw/jni/FolderDelete;->finish(Landroid/app/Dialog;Lcom/alensw/jni/FolderDelete$Result;)V
    :folder_finish_layout
    iget-object v0, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    iget-object v0, v0, Lcom/alensw/ui/c/bz;->C:Lcom/alensw/ui/c/cx;
    invoke-virtual {v0}, Lcom/alensw/ui/c/cx;->q()Z
    move-result v1
    if-eqz v1, :folder_finish_request_layout
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Lcom/alensw/ui/c/cx;->d(Z)V
    :folder_finish_request_layout
    iget-object v0, p0, Lcom/alensw/ui/c/cf;->e:Lcom/alensw/ui/c/bz;
    iget-object v0, v0, Lcom/alensw/ui/c/bz;->j:Lcom/alensw/ui/view/ImageGridView;
    invoke-virtual {v0}, Lcom/alensw/ui/view/ImageGridView;->requestLayout()V
    return-void
.end method
'''
    text = replace_method(text, '.method protected a()V', work)
    text = replace_method(text, '.method protected b()V', finish)
    path.write_text(text)
    return {'changed': [str(path.relative_to(root))], 'whole_folder_delete': True}
