# SPDX-License-Identifier: MIT
"""Allow only local search folders into selection and whole-directory deletion."""
from pathlib import Path
import re


def method(text, signature):
    pattern = re.escape(signature) + r'\n.*?^\.end method'
    matches = list(re.finditer(pattern, text, re.M | re.S))
    if len(matches) != 1:
        raise ValueError('Expected one method: ' + signature)
    return matches[0]


def once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Unexpected search-delete seam: ' + old[:100])
    return text.replace(old, new, 1)


def patch(root):
    root = Path(root)
    paths = [root / ('smali/com/alensw/' + n + '.smali') for n in ('ui/c/ae', 'ui/c/an', 'ui/c/dc', 'ui/c/z', 'bean/LocalFolder')]
    texts = [p.read_text() for p in paths]
    if all('quickpicSearch' in t for t in texts):
        return {'changed': [], 'search_folder_delete': True}
    if any('quickpicSearch' in t for t in texts):
        raise ValueError('Partially patched search deletion')
    ae, worker, dc, z, folder = texts
    folder = once(folder, "# instance fields", "# instance fields\n.field public quickpicSearchDirectory:Ljava/lang/String;")
    # Three independent folder exclusions: range/select-all, toggle, and entering long-press selection.
    seams = [('.method public a(IIZ)V', '    if-eq v1, v2, :cond_0', 'v1', 'v2', ':cond_0'),
             ('.method public b(I)V', '    if-eq v1, v5, :cond_2', 'v1', 'v5', ':cond_2'),
             ('.method public c(I)V', '    if-eq v0, v1, :cond_0', 'v0', 'v1', ':cond_0')]
    for signature, seam, value, other, skip in seams:
        m = method(ae, signature)
        call = ('    if-ne ' + value + ', ' + other + ', :quickpicSearchAllowed\n'
                '    invoke-direct {p0, p1}, Lcom/alensw/ui/c/ae;->quickpicSearchFolder(I)Z\n'
                '    move-result ' + value + '\n'
                '    if-eqz ' + value + ', ' + skip + '\n'
                '    :quickpicSearchAllowed')
        ae = ae[:m.start()] + once(m.group(), seam, call) + ae[m.end():]
    ae += '''
.method private quickpicSearchFolder(I)Z
    .locals 2
    iget-object v0, p0, Lcom/alensw/ui/c/ae;->a:Lcom/alensw/ui/c/z;
    instance-of v1, v0, Lcom/alensw/ui/c/dc;
    if-eqz v1, :quickpicSearchNo
    check-cast v0, Lcom/alensw/ui/c/dc;
    invoke-virtual {v0, p1}, Lcom/alensw/ui/c/dc;->quickpicSearchFolder(I)Z
    move-result v0
    return v0
    :quickpicSearchNo
    const/4 v0, 0x0
    return v0
.end method
'''
    # Keep the original confirmation dialog, executor, progress and image/provider routes.
    worker = once(worker, '# direct methods', '.field private quickpicSearchResult:Lcom/alensw/jni/FolderDelete$Result;\n.field private quickpicSearchFailed:Z\n\n# direct methods')
    m = method(worker, '.method protected a()V')
    work = once(m.group(), '    .locals 5', '''    .locals 5
    iget-object v0, p0, Lcom/alensw/ui/c/an;->a:Ljava/util/HashSet;
    invoke-virtual {v0}, Ljava/util/HashSet;->size()I
    move-result v0
    new-instance v1, Lcom/alensw/jni/FolderDelete$Result;
    invoke-direct {v1, v0}, Lcom/alensw/jni/FolderDelete$Result;-><init>(I)V
    iput-object v1, p0, Lcom/alensw/ui/c/an;->quickpicSearchResult:Lcom/alensw/jni/FolderDelete$Result;''')
    work = once(work, '    iget-boolean v1, v1, Lcom/alensw/ui/c/z;->m:Z', r'''    instance-of v1, v1, Lcom/alensw/ui/c/dc;
    if-eqz v1, :quickpicSearchOriginal
    iget-object v1, p0, Lcom/alensw/ui/c/an;->quickpicSearchResult:Lcom/alensw/jni/FolderDelete$Result;
    invoke-static {v3, v1}, Lcom/alensw/jni/SearchDelete;->alreadyGone(Landroid/net/Uri;Lcom/alensw/jni/FolderDelete$Result;)Z
    move-result v1
    if-nez v1, :goto_0
    instance-of v1, v0, Lcom/alensw/bean/LocalFolder;
    if-eqz v1, :quickpicSearchFile
    move-object v1, v0
    check-cast v1, Lcom/alensw/bean/LocalFolder;
    iget-object v1, v1, Lcom/alensw/bean/LocalFolder;->quickpicSearchDirectory:Ljava/lang/String;
    if-eqz v1, :quickpicSearchOriginal
    new-instance v3, Ljava/io/File;
    invoke-direct {v3, v1}, Ljava/io/File;-><init>(Ljava/lang/String;)V
    invoke-static {v3}, Landroid/net/Uri;->fromFile(Ljava/io/File;)Landroid/net/Uri;
    move-result-object v3
    iget-object v1, p0, Lcom/alensw/ui/c/an;->quickpicSearchResult:Lcom/alensw/jni/FolderDelete$Result;
    invoke-static {p0, v3, v1}, Lcom/alensw/jni/SearchDelete;->deleteFolder(Landroid/app/Dialog;Landroid/net/Uri;Lcom/alensw/jni/FolderDelete$Result;)Z
    move-result v1
    if-nez v1, :goto_0
    invoke-virtual {p0}, Lcom/alensw/ui/c/an;->isShowing()Z
    move-result v1
    if-eqz v1, :quickpicSearchCancelled
    const/4 v1, 0x1
    iput-boolean v1, p0, Lcom/alensw/ui/c/an;->quickpicSearchFailed:Z
    :quickpicSearchCancelled
    const/4 v1, 0x0
    goto :goto_0
    :quickpicSearchFile
    instance-of v1, v0, Lcom/alensw/bean/LocalFile;
    if-eqz v1, :quickpicSearchOriginal
    invoke-direct {p0, v3}, Lcom/alensw/ui/c/an;->quickpicSearchFile(Landroid/net/Uri;)Z
    move-result v1
    goto :goto_0
    :quickpicSearchOriginal
    iget-object v1, p0, Lcom/alensw/ui/c/an;->f:Lcom/alensw/ui/c/z;
    iget-boolean v1, v1, Lcom/alensw/ui/c/z;->m:Z''')
    worker = worker[:m.start()] + work + worker[m.end():]
    m = method(worker, '.method protected b()V')
    work = once(m.group(), '    return-void', '''    iget-boolean v0, p0, Lcom/alensw/ui/c/an;->quickpicSearchFailed:Z
    invoke-static {p0, v0}, Lcom/alensw/jni/SearchDelete;->finish(Landroid/app/Dialog;Z)V
    return-void''')
    worker = worker[:m.start()] + work + worker[m.end():]
    # Preserve the original first-image thumbnail/path contract. A separate search-only
    # field carries the actual selected directory to deletion, without another scan.
    m = method(dc, '.method private b(Lcom/alensw/a/e;)V')
    seam = '    invoke-direct/range {v0 .. v7}, Lcom/alensw/bean/LocalFolder;-><init>(Ljava/lang/String;Ljava/lang/String;IJJ)V'
    work = once(m.group(), seam, seam + '\n    invoke-virtual {p1}, Lcom/alensw/a/e;->getPath()Ljava/lang/String;\n    move-result-object v1\n    iput-object v1, v0, Lcom/alensw/bean/LocalFolder;->quickpicSearchDirectory:Ljava/lang/String;')
    dc = dc[:m.start()] + work + dc[m.end():]
    dc = once(dc, '# direct methods', '.field private quickpicSearchDeleted:Ljava/util/ArrayList;\n.field private quickpicSearchHidden:Ljava/util/ArrayList;\n\n# direct methods')
    # Existing result publisher can race with deletion; reject only physically absent covered rows.
    m = method(dc, '.method public handleMessage(Landroid/os/Message;)Z')
    work = once(m.group(), '    packed-switch v0, :pswitch_data_0', '''    const/16 v1, 0xbc2
    if-ne v0, v1, :quickpicSearchMessage
    iget-object v2, p0, Lcom/alensw/ui/c/dc;->quickpicSearchDeleted:Ljava/util/ArrayList;
    if-eqz v2, :quickpicSearchMessage
    iget-object v1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;
    check-cast v1, Lcom/alensw/bean/CommonFile;
    invoke-virtual {v1}, Lcom/alensw/bean/CommonFile;->i()Landroid/net/Uri;
    move-result-object v1
    iget-object v2, p0, Lcom/alensw/ui/c/dc;->quickpicSearchDeleted:Ljava/util/ArrayList;
    invoke-static {v1, v2}, Lcom/alensw/jni/SearchDelete;->covered(Landroid/net/Uri;Ljava/util/Collection;)Z
    move-result v1
    if-eqz v1, :quickpicSearchMessage
    const/4 v0, 0x1
    return v0
    :quickpicSearchMessage
    packed-switch v0, :pswitch_data_0''')
    dc = dc[:m.start()] + work + dc[m.end():]
    dc += '''
.method public quickpicSearchFolder(I)Z
    .locals 2
    iget v0, p0, Lcom/alensw/ui/c/dc;->i:I
    if-nez v0, :quickpicSearchNo
    const-string v0, "folders"
    iget-object v1, p0, Lcom/alensw/ui/c/dc;->B:Ljava/lang/String;
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :quickpicSearchNo
    iget-object v0, p0, Lcom/alensw/ui/c/dc;->y:Lcom/alensw/bean/CommonFolder;
    invoke-virtual {v0, p1}, Lcom/alensw/bean/CommonFolder;->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v0
    instance-of v1, v0, Lcom/alensw/bean/LocalFolder;
    if-eqz v1, :quickpicSearchNo
    check-cast v0, Lcom/alensw/bean/LocalFolder;
    iget-object v0, v0, Lcom/alensw/bean/LocalFolder;->quickpicSearchDirectory:Ljava/lang/String;
    if-eqz v0, :quickpicSearchNo
    const/4 v0, 0x1
    return v0
    :quickpicSearchNo
    const/4 v0, 0x0
    return v0
.end method

.method public a(Ljava/util/Collection;)V
    .locals 6
    iget-object v0, p0, Lcom/alensw/ui/c/dc;->quickpicSearchDeleted:Ljava/util/ArrayList;
    if-nez v0, :quickpicSearchRoots
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    iput-object v0, p0, Lcom/alensw/ui/c/dc;->quickpicSearchDeleted:Ljava/util/ArrayList;
    :quickpicSearchRoots
    invoke-interface {p1}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :quickpicSearchRootNext
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :quickpicSearchExpand
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    instance-of v3, v2, Lcom/alensw/bean/LocalFolder;
    if-eqz v3, :quickpicSearchRootNext
    check-cast v2, Lcom/alensw/bean/LocalFolder;
    iget-object v2, v2, Lcom/alensw/bean/LocalFolder;->quickpicSearchDirectory:Ljava/lang/String;
    if-eqz v2, :quickpicSearchRootNext
    new-instance v3, Ljava/io/File;
    invoke-direct {v3, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V
    invoke-static {v3}, Landroid/net/Uri;->fromFile(Ljava/io/File;)Landroid/net/Uri;
    move-result-object v2
    invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :quickpicSearchRootNext
    :quickpicSearchExpand
    invoke-virtual {v0}, Ljava/util/ArrayList;->isEmpty()Z
    move-result v1
    if-eqz v1, :quickpicSearchExpandFolders
    invoke-super {p0, p1}, Lcom/alensw/ui/c/z;->a(Ljava/util/Collection;)V
    return-void
    :quickpicSearchExpandFolders
    new-instance v1, Ljava/util/ArrayList;
    invoke-direct {v1, p1}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
    iget-object v2, p0, Lcom/alensw/ui/c/dc;->y:Lcom/alensw/bean/CommonFolder;
    const/4 v2, 0x0
    :quickpicSearchRowNext
    iget-object v3, p0, Lcom/alensw/ui/c/dc;->y:Lcom/alensw/bean/CommonFolder;
    invoke-virtual {v3}, Lcom/alensw/bean/CommonFolder;->o()I
    move-result v4
    if-ge v2, v4, :quickpicSearchModels
    invoke-virtual {v3, v2}, Lcom/alensw/bean/CommonFolder;->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v3
    add-int/lit8 v2, v2, 0x1
    invoke-virtual {v3}, Lcom/alensw/bean/CommonFile;->i()Landroid/net/Uri;
    move-result-object v4
    invoke-static {v4, v0}, Lcom/alensw/jni/SearchDelete;->covered(Landroid/net/Uri;Ljava/util/Collection;)Z
    move-result v4
    if-eqz v4, :quickpicSearchRowNext
    invoke-virtual {v1, v3}, Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :quickpicSearchRowNext
    invoke-virtual {v1, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :quickpicSearchRowNext
    :quickpicSearchModels
    sget-object v2, L__APP__;->o:Lcom/alensw/a/x;
    invoke-virtual {v2}, Lcom/alensw/a/x;->h()Ljava/util/ArrayList;
    move-result-object v3
    new-instance v4, Ljava/util/ArrayList;
    invoke-direct {v4, v3}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
    invoke-virtual {v4}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
    move-result-object v3
    :quickpicSearchModelNext
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z
    move-result v4
    if-eqz v4, :quickpicSearchFinish
    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v4
    check-cast v4, Lcom/alensw/a/e;
    invoke-static {v4}, Landroid/net/Uri;->fromFile(Ljava/io/File;)Landroid/net/Uri;
    move-result-object v5
    invoke-static {v5, v0}, Lcom/alensw/jni/SearchDelete;->covered(Landroid/net/Uri;Ljava/util/Collection;)Z
    move-result v5
    if-eqz v5, :quickpicSearchModelNext
    invoke-virtual {v2, v4}, Lcom/alensw/a/x;->f(Lcom/alensw/a/e;)Lcom/alensw/a/e;
    goto :quickpicSearchModelNext
    :quickpicSearchFinish
    invoke-super {p0, v1}, Lcom/alensw/ui/c/z;->a(Ljava/util/Collection;)V
    return-void
.end method
'''
    dc += r"""
.method public quickpicSearchSelectAll()Z
    .locals 7
    iget v0, p0, Lcom/alensw/ui/c/dc;->i:I
    if-nez v0, :quickpicSearchNotHandled
    const-string v0, "folders"
    iget-object v1, p0, Lcom/alensw/ui/c/dc;->B:Ljava/lang/String;
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :quickpicSearchNotHandled
    iget-object v0, p0, Lcom/alensw/ui/c/dc;->f:Lcom/alensw/ui/view/ImageGridView;
    iget-boolean v1, v0, Lcom/alensw/ui/view/ImageGridView;->d:Z
    if-eqz v1, :quickpicSearchNotHandled
    new-instance v1, Ljava/util/ArrayList;
    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
    const/4 v2, 0x0
    iget-object v3, p0, Lcom/alensw/ui/c/dc;->y:Lcom/alensw/bean/CommonFolder;
    invoke-virtual {v3}, Lcom/alensw/bean/CommonFolder;->o()I
    move-result v4
    :quickpicSearchSelectNext
    if-ge v2, v4, :quickpicSearchToggleAll
    invoke-virtual {v3, v2}, Lcom/alensw/bean/CommonFolder;->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v5
    if-eqz v5, :quickpicSearchSelectSkip
    invoke-virtual {v5}, Lcom/alensw/bean/CommonFile;->h()C
    move-result v6
    const/16 v0, 0x44
    if-ne v6, v0, :quickpicSearchSelectAdd
    invoke-virtual {p0, v2}, Lcom/alensw/ui/c/dc;->quickpicSearchFolder(I)Z
    move-result v6
    if-eqz v6, :quickpicSearchSelectSkip
    :quickpicSearchSelectAdd
    invoke-virtual {v1, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :quickpicSearchSelectSkip
    add-int/lit8 v2, v2, 0x1
    goto :quickpicSearchSelectNext
    :quickpicSearchToggleAll
    iget-object v0, p0, Lcom/alensw/ui/c/dc;->s:Ljava/util/LinkedHashSet;
    invoke-virtual {v0, v1}, Ljava/util/LinkedHashSet;->containsAll(Ljava/util/Collection;)Z
    move-result v2
    if-eqz v2, :quickpicSearchAddAll
    invoke-virtual {v0}, Ljava/util/LinkedHashSet;->clear()V
    goto :quickpicSearchSelected
    :quickpicSearchAddAll
    invoke-virtual {v0, v1}, Ljava/util/LinkedHashSet;->addAll(Ljava/util/Collection;)Z
    :quickpicSearchSelected
    iget-object v1, p0, Lcom/alensw/ui/c/dc;->f:Lcom/alensw/ui/view/ImageGridView;
    invoke-virtual {v1}, Lcom/alensw/ui/view/ImageGridView;->b()V
    invoke-virtual {p0}, Lcom/alensw/ui/c/dc;->l()Z
    const/4 v0, 0x1
    return v0
    :quickpicSearchNotHandled
    const/4 v0, 0x0
    return v0
.end method
"""
    m = method(z, '.method public a(Landroid/view/MenuItem;)Z')
    work = once(m.group(), '    sparse-switch v5, :sswitch_data_0', r"""    const v11, 0x102001f
    if-ne v5, v11, :quickpicSearchMenuOriginal
    instance-of v11, p0, Lcom/alensw/ui/c/dc;
    if-eqz v11, :quickpicSearchMenuOriginal
    move-object v11, p0
    check-cast v11, Lcom/alensw/ui/c/dc;
    invoke-virtual {v11}, Lcom/alensw/ui/c/dc;->quickpicSearchSelectAll()Z
    move-result v11
    if-eqz v11, :quickpicSearchMenuOriginal
    return v1
    :quickpicSearchMenuOriginal
    sparse-switch v5, :sswitch_data_0""")
    z = z[:m.start()] + work + z[m.end():]
    dc += r"""
.method protected d(Landroid/view/Menu;)Z
    .locals 5
    iget-object v0, p0, Lcom/alensw/ui/c/dc;->quickpicSearchHidden:Ljava/util/ArrayList;
    if-nez v0, :quickpicSearchRestoreMenu
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    iput-object v0, p0, Lcom/alensw/ui/c/dc;->quickpicSearchHidden:Ljava/util/ArrayList;
    :quickpicSearchRestoreMenu
    invoke-virtual {v0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :quickpicSearchRestoreNext
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :quickpicSearchRestored
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Landroid/view/MenuItem;
    const/4 v3, 0x1
    invoke-interface {v2, v3}, Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;
    goto :quickpicSearchRestoreNext
    :quickpicSearchRestored
    invoke-virtual {v0}, Ljava/util/ArrayList;->clear()V
    invoke-super {p0, p1}, Lcom/alensw/ui/c/z;->d(Landroid/view/Menu;)Z
    move-result v0
    iget-object v1, p0, Lcom/alensw/ui/c/dc;->s:Ljava/util/LinkedHashSet;
    invoke-virtual {v1}, Ljava/util/LinkedHashSet;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :quickpicSearchMenuNext
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :quickpicSearchMenuDone
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    instance-of v2, v2, Lcom/alensw/bean/LocalFolder;
    if-eqz v2, :quickpicSearchMenuNext
    const/4 v1, 0x0
    :quickpicSearchMenuItem
    invoke-interface {p1}, Landroid/view/Menu;->size()I
    move-result v2
    if-ge v1, v2, :quickpicSearchMenuDone
    invoke-interface {p1, v1}, Landroid/view/Menu;->getItem(I)Landroid/view/MenuItem;
    move-result-object v2
    invoke-interface {v2}, Landroid/view/MenuItem;->getItemId()I
    move-result v3
    const v4, 0x7f09004e
    if-eq v3, v4, :quickpicSearchMenuKeep
    const v4, 0x102001f
    if-eq v3, v4, :quickpicSearchMenuKeep
    invoke-interface {v2}, Landroid/view/MenuItem;->isVisible()Z
    move-result v3
    if-eqz v3, :quickpicSearchMenuKeep
    iget-object v4, p0, Lcom/alensw/ui/c/dc;->quickpicSearchHidden:Ljava/util/ArrayList;
    invoke-virtual {v4, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    const/4 v3, 0x0
    invoke-interface {v2, v3}, Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;
    :quickpicSearchMenuKeep
    add-int/lit8 v1, v1, 0x1
    goto :quickpicSearchMenuItem
    :quickpicSearchMenuDone
    return v0
.end method
"""
    app = re.search(r'sget-object \w+, (L[^;]+;)->r:Lcom/alensw/a/bc;', (root/'smali/com/alensw/ui/c/z.smali').read_text())
    if not app:
        raise ValueError('Missing application cache manager')
    worker += r"""
.method private quickpicSearchFile(Landroid/net/Uri;)Z
    .locals 5
    invoke-static {p1}, Lcom/alensw/jni/SearchDelete;->isFile(Landroid/net/Uri;)Z
    move-result v0
    if-eqz v0, :quickpicSearchFileFail
    invoke-virtual {p1}, Landroid/net/Uri;->getPath()Ljava/lang/String;
    move-result-object v0
    new-instance v1, Ljava/io/File;
    invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
    invoke-virtual {p0}, Lcom/alensw/ui/c/an;->getContext()Landroid/content/Context;
    move-result-object v2
    invoke-static {v2, v1}, Lcom/alensw/b/l/b;->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I
    move-result v1
    if-ltz v1, :quickpicSearchFileFail
    invoke-static {v2, p1}, Lcom/alensw/jni/SearchDelete;->afterFile(Landroid/content/Context;Landroid/net/Uri;)Z
    move-result v1
    if-eqz v1, :quickpicSearchFileFail
    sget-object v2, L__APP__;->s:Lcom/alensw/a/ba;
    invoke-virtual {v2, v0}, Lcom/alensw/a/ba;->quickpicForget(Ljava/lang/String;)V
    const/4 v0, 0x1
    return v0
    :quickpicSearchFileFail
    const/4 v0, 0x0
    return v0
.end method
"""
    worker = worker.replace('L__APP__;', app.group(1))
    dc = dc.replace('L__APP__;', app.group(1))
    for path, text in zip(paths, (ae, worker, dc, z, folder)):
        path.write_text(text)
    return {'changed': [str(p.relative_to(root)) for p in paths], 'search_folder_delete': True}
