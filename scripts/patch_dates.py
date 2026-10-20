# SPDX-License-Identifier: MIT
"""Add independent created/modified sort choices to the supported original DEX.

All preference writes, per-folder modes, reverse UI, and selection restoration
remain in their original code. Only guarded seams and new mode 9/10 are added.
"""
from pathlib import Path
import re

HELPER = 'Lcom/alensw/jni/DateSort;'
MENU = 'Lcom/alensw/ui/activity/a;->a(Landroid/content/Context;Landroid/view/View;[IIZ)V'


def replace_method(text, signature, change):
    if text.count(signature) != 1:
        raise ValueError('Expected one date-sort method: ' + signature)
    start = text.index(signature)
    end = text.index('.end method', start) + len('.end method')
    part = text[start:end]
    return text[:start] + change(part) + text[end:]


def once(part, old, new):
    if part.count(old) != 1:
        raise ValueError('Unexpected date-sort seam: ' + old[:100])
    return part.replace(old, new, 1)


def extra_local(part):
    found = re.search(r'(?m)^    \.locals (\d+)$', part)
    if not found:
        raise ValueError('Date-sort hook needs .locals')
    count = int(found.group(1))
    if count > 12:
        raise ValueError('Date-sort hook would need a wider register encoding')
    return part[:found.start(1)] + str(count + 1) + part[found.end(1):], 'v' + str(count)


def controller(part, owner, field, selected, reverse, common):
    if HELPER in part:
        return part
    part, scratch = extra_local(part)
    switch = re.search(r'(?m)^    sparse-switch (v\d+), :sswitch_data_0$', part)
    if not switch:
        raise ValueError('Missing controller menu dispatch: ' + owner)
    handler = f'''    iget {scratch}, p0, {owner}->{field}:I
    invoke-static {{p1, {scratch}}}, {HELPER}->creationModeForItem(Landroid/view/MenuItem;I)I
    move-result {scratch}
    if-ltz {scratch}, :dates_existing_dispatch
    iput {scratch}, p0, {owner}->{field}:I
    goto/16 {common}
    :dates_existing_dispatch
'''
    part = part[:switch.start()] + handler + part[switch.start():]
    menu_call = re.search(r'(?m)^    invoke-static \{([^}]+)\}, ' + re.escape(MENU) + '$', part)
    if not menu_call:
        raise ValueError('Missing controller sort menu call: ' + owner)
    regs = [r.strip() for r in menu_call.group(1).split(',')]
    if len(regs) != 5 or regs[-2:] != [selected, reverse]:
        raise ValueError('Unexpected controller sort menu registers: ' + owner)
    state = f'''    iget {scratch}, p0, {owner}->{field}:I
    invoke-static {{{scratch}, {selected}}}, {HELPER}->selected(II)I
    move-result {selected}
    invoke-static {{{scratch}, {reverse}}}, {HELPER}->reverse(IZ)Z
    move-result {reverse}
'''
    return part[:menu_call.start()] + state + part[menu_call.start():]


def comparator(part, owner, items, mode_method, comparator_field, helper_method):
    if HELPER in part:
        return part
    part, scratch = extra_local(part)
    seam = '\n    :goto_0\n'
    hook = f'''
    :goto_0
    iget-object v0, p0, {owner}->{items}:Ljava/util/ArrayList;
    invoke-virtual {{p0}}, {owner}->{mode_method}()I
    move-result v2
    iget-object {scratch}, p0, {owner}->{comparator_field}:Ljava/util/Comparator;
    invoke-static {{v0, v2, {scratch}}}, {HELPER}->{helper_method}(Ljava/util/ArrayList;ILjava/util/Comparator;)Ljava/util/Comparator;
    move-result-object v0
    iput-object v0, p0, {owner}->{comparator_field}:Ljava/util/Comparator;
'''
    return once(part, seam, hook)


def patch(decoded_root):
    root = Path(decoded_root)
    changes = {}

    def edit(relative, signature, change):
        path = root / relative
        text = changes.get(path, path.read_text(encoding='utf-8'))
        changes[path] = replace_method(text, signature, change)

    for name, field, selected, common in (
            ('bz', 't', 'v1', ':goto_2'),
            ('bf', 'j', 'v2', ':goto_1'),
            ('z', 'k', 'v3', ':goto_1')):
        owner = 'Lcom/alensw/ui/c/' + name + ';'
        edit('smali/com/alensw/ui/c/' + name + '.smali',
             '.method public a(Landroid/view/MenuItem;)Z',
             lambda part, owner=owner, field=field, selected=selected, common=common:
             controller(part, owner, field, selected, 'v0', common))

    def preference(part):
        if HELPER in part:
            return part
        part, scratch = extra_local(part)
        seam = '    sparse-switch v7, :sswitch_data_0\n'
        hook = f'''    invoke-static {{p1, v4}}, {HELPER}->creationModeForItem(Landroid/view/MenuItem;I)I
    move-result {scratch}
    if-ltz {scratch}, :dates_existing_dispatch
    move v0, {scratch}
    goto/16 :goto_2
    :dates_existing_dispatch
'''
        return once(part, seam, hook + seam)
    edit('smali/com/alensw/ui/activity/MainPreference.smali',
         '.method public onOptionsItemSelected(Landroid/view/MenuItem;)Z', preference)

    def preference_menu(part):
        if HELPER in part:
            return part
        part, scratch = extra_local(part)
        seam = '    move-result v2\n'
        part = once(part, seam, seam + '\n    move ' + scratch + ', v2\n')
        seam = '    invoke-static {v3, v4, v5, v2, v0}, ' + MENU + '\n'
        hook = f'''    invoke-static {{{scratch}, v2}}, {HELPER}->selected(II)I
    move-result v2
    invoke-static {{{scratch}, v0}}, {HELPER}->reverse(IZ)Z
    move-result v0
'''
        return once(part, seam, hook + seam)
    edit('smali/com/alensw/ui/activity/ab.smali',
         '.method public onPreferenceClick(Landroid/preference/Preference;)Z', preference_menu)

    def shared_menu(part):
        if HELPER in part:
            return part
        seam = '    invoke-static {p2}, Ljava/util/Arrays;->sort([I)V\n'
        hook = f'''    invoke-static {{p0, v1, p2}}, {HELPER}->prepareMenu(Landroid/content/Context;Landroid/view/Menu;[I)[I
    move-result-object p2

'''
        return once(part, seam, hook + seam)
    edit('smali/com/alensw/ui/activity/a.smali',
         '.method public static a(Landroid/content/Context;Landroid/view/View;[IIZ)V', shared_menu)

    edit('smali/com/alensw/a/e.smali', '.method public l()V',
         lambda part: comparator(part, 'Lcom/alensw/a/e;', 'i', 'd', 'r', 'localComparator'))
    edit('smali/com/alensw/a/x.smali', '.method public a(I)V',
         lambda part: comparator(part, 'Lcom/alensw/a/x;', 'l', 'c', 'a', 'folderComparator'))
    edit('smali/com/alensw/bean/CommonFolder.smali', '.method public s()V',
         lambda part: comparator(part, 'Lcom/alensw/bean/CommonFolder;', 'n', 'l', 'm', 'commonComparator'))

    def common_query_sort(part):
        if HELPER in part:
            return part
        # Original date modes trust a datetaken-ordered provider cursor, and some
        # MediaStore folders skip all client sorting. Our four date modes must
        # reach the common comparator after metadata loading, on this worker.
        seam = '    iget-boolean v2, p0, Lcom/alensw/a/av;->d:Z\n'
        hook = f'''    iget-object v2, p0, Lcom/alensw/a/av;->h:Lcom/alensw/bean/CommonFolder;
    invoke-virtual {{v2}}, Lcom/alensw/bean/CommonFolder;->l()I
    move-result v2
    invoke-static {{v2}}, {HELPER}->isCommonDate(I)Z
    move-result v2
    if-nez v2, :dates_sort_common

'''
        part = once(part, seam, hook + seam)
        seam = '    invoke-virtual {v2}, Lcom/alensw/bean/CommonFolder;->s()V\n'
        # Branch to the object load immediately preceding s(), not the invoke:
        # v2 contains the mode predicate rather than the folder at this point.
        load = '    iget-object v2, p0, Lcom/alensw/a/av;->h:Lcom/alensw/bean/CommonFolder;\n\n' + seam
        return once(part, load, '    :dates_sort_common\n' + load)
    edit('smali/com/alensw/a/av.smali', '.method public c()Ljava/lang/Void;', common_query_sort)

    def date_mode(part):
        if HELPER in part:
            return part
        if 'and-int/lit16 v0, v0, 0xff' not in part:
            raise ValueError('Unexpected gallery mode layout')
        return '''.method public final d()Z
    .locals 1
    invoke-virtual {p0}, Lcom/alensw/a/x;->c()I
    move-result v0
    invoke-static {v0}, ''' + HELPER + '''->isFolderDate(I)Z
    move-result v0
    return v0
.end method'''
    edit('smali/com/alensw/a/x.smali', '.method public final d()Z', date_mode)

    for path in sorted((root / 'res').glob('values*/strings.xml')):
        text = path.read_text(encoding='utf-8')
        language = path.parent.name
        label = '按修改日期' if language.startswith('values-zh') else 'By modified date'
        replacement = '<string name="sort_by_date">' + label + '</string>'
        updated, count = re.subn(r'<string name="sort_by_date">[^<]*</string>', replacement, text)
        if count > 1:
            raise ValueError('Repeated date label: ' + str(path))
        if count:
            changes[path] = updated

    # Validate every source seam before writing any patch target.
    for path, text in changes.items():
        path.write_text(text, encoding='utf-8')
    return {
        'paths': [str(path.relative_to(root)) for path in changes],
        'created_modes': {'newest_first': 9, 'oldest_first': 10},
        'existing_mode_storage': 'retained: gallery byte and per-folder low nibble',
        'creation_fallback': 'filesystem birth when available; otherwise local first-added journal; content date_added or modified',
        'modified': 'local file mtime; provider date_modified (not datetaken)',
    }
