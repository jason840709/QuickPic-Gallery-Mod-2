# SPDX-License-Identifier: MIT
"""Prioritize pending folder waves and use both allowed startup path caches.

The two-worker executor, cancellation, exclusion checks and per-folder UI
publication remain in place. Path cache serialization is untouched.
"""
from pathlib import Path
import re


ORDER_CALL = (
    '    invoke-static {p1}, '
    'Lcom/alensw/jni/ScanPriority;->order(Ljava/util/ArrayList;)V\n'
)


def _replace_method(text, signature, change):
    matches = list(re.finditer(r'^' + re.escape(signature) + r'\s*$', text, re.M))
    if len(matches) != 1:
        raise ValueError('Expected one scan method: ' + signature)
    start = matches[0].start()
    end = text.index('.end method', start) + len('.end method')
    return text[:start] + change(text[start:end]) + text[end:]


def patch(root):
    root = Path(root)
    path = root / 'smali/com/alensw/a/v.smali'
    original = path.read_text()
    text = original

    def order_wave(part):
        guarded = ('    if-nez p4, :scan_wave_ordered\n' + ORDER_CALL
                   + '    :scan_wave_ordered\n')
        if re.search(
                r'if-nez p4, :\w+\s+invoke-static \{p1\}, '
                r'Lcom/alensw/jni/ScanPriority;->order\(Ljava/util/ArrayList;\)V', part):
            return part
        if ORDER_CALL in part:
            return part.replace(ORDER_CALL, guarded, 1)
        if ('Lcom/alensw/b/k/a;->invokeAll(Ljava/util/Collection;)Ljava/util/List;' not in part
                or 'Lcom/alensw/a/w;-><init>' not in part):
            raise ValueError('Unexpected folder wave executor')
        seam = re.search(r'^    \.locals \d+\s*$', part, re.M)
        if seam is None:
            raise ValueError('Missing folder wave locals')
        # Traversal batches already came from the ranked heap. Sort the separate
        # cached/revalidation lists only, without ranking queued folders again.
        return part[:seam.end()] + '\n' + guarded + part[seam.end():]

    text = _replace_method(
        text, '.method private a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I', order_wave)

    def prioritize_pending(part):
        if 'Lcom/alensw/jni/ScanPriority;->pending()Ljava/util/Stack;' in part:
            if not re.search(r'const/4 v5, 0x2\s+if-ge v0, v5, :\w+', part):
                raise ValueError('Priority pending queue is missing its bounded batch')
            return part
        stack = re.compile(
            r'    new-instance v4, Ljava/util/Stack;\s+'
            r'    invoke-direct \{v4\}, Ljava/util/Stack;-><init>\(\)V')
        part, count = stack.subn(
            '    invoke-static {}, Lcom/alensw/jni/ScanPriority;->pending()Ljava/util/Stack;\n'
            '    move-result-object v4', part)
        if count != 1:
            raise ValueError('Missing folder pending stack')
        seam = ('    invoke-virtual {v3, v0}, '
                'Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z\n')
        if part.count(seam) != 1 or '    :cond_2\n' not in part:
            raise ValueError('Unexpected folder traversal drain')
        return part.replace(seam, seam + '''
    :scan_two_folder_batch
    invoke-virtual {v3}, Ljava/util/ArrayList;->size()I
    move-result v0
    const/4 v5, 0x2
    if-ge v0, v5, :cond_2
''', 1)

    text = _replace_method(text, '.method private a(Lcom/alensw/a/e;)I', prioritize_pending)

    def merge_normal_cache(part):
        if re.search(
                r'invoke-direct \{p0, (v\d+)\}, Lcom/alensw/a/v;->b\(Ljava/util/ArrayList;\)V\s+'
                r'invoke-direct \{p0, \1\}, Lcom/alensw/a/v;->a\(Ljava/util/ArrayList;\)V', part):
            return part
        # This instruction runs only in the original show-hidden=true branch.
        # Both loaders retain their existing include/exclude checks. The hidden
        # loader below deduplicates paths that changed visibility since last run.
        seam = re.search(
            r'^    invoke-direct \{p0, (v\d+)\}, '
            r'Lcom/alensw/a/v;->a\(Ljava/util/ArrayList;\)V\s*$', part, re.M)
        if seam is None:
            raise ValueError('Missing protected-hidden startup prescan')
        prefix = (
            '    :scan_visible_cache\n'
            '    invoke-direct {p0, ' + seam.group(1) + '}, '
            'Lcom/alensw/a/v;->b(Ljava/util/ArrayList;)V\n\n')
        return part[:seam.start()] + prefix + part[seam.start():]

    text = _replace_method(text, '.method private c()I', merge_normal_cache)

    def defer_cached_scan(part):
        if ('    .locals 13\n' in part and '    move-object v12, v1\n' in part
                and 'invoke-direct {p0, v12, v3, v3, v10}, Lcom/alensw/a/v;->a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I' in part):
            return part
        if '    .locals 12\n' not in part:
            raise ValueError('Unexpected full scan register allocation')
        part = part.replace('    .locals 12\n', '    .locals 13\n', 1)
        start = part.index('    :goto_7\n')
        end = part.index('    :goto_8\n', start)
        old = part[start:end]
        if old.count('->a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I') != 1:
            raise ValueError('Unexpected startup cached-scan block')
        # Retain the cached fallback list while traversing selected roots first.
        # The former prescan blocked discovery of every newly downloaded folder.
        part = part[:start] + '''    :goto_7
    move-object v12, v1
    const/4 v0, 0x0

''' + part[end:]
        seam = '    :cond_b\n'
        if part.count(seam) != 1:
            raise ValueError('Missing full scan completion seam')
        part = part.replace(seam, seam + '''    :scan_deferred_cache
    iget-boolean v0, p0, Lcom/alensw/a/v;->e:Z
    if-nez v0, :scan_deferred_cache_done
    invoke-direct {p0, v12, v3, v3, v10}, Lcom/alensw/a/v;->a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I
    move-result v0
    add-int/2addr v1, v0
    :scan_deferred_cache_done
''', 1)
        # Its only incoming branch was in the removed cached prescan block.
        orphan = re.compile(r'    :cond_13\s+move v0, v3\s+goto/16 :goto_8\s*')
        part, count = orphan.subn('', part)
        if count != 1 or ':cond_13' in part:
            raise ValueError('Unexpected old prescan empty-list branch')
        return part

    text = _replace_method(text, '.method private c()I', defer_cached_scan)

    def deduplicate_hidden_cache(part):
        if 'Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z' in part:
            return part
        seam = re.search(
            r'^    invoke-virtual \{p1, (v\d+)\}, '
            r'Ljava/util/ArrayList;->add\(Ljava/lang/Object;\)Z\s*$', part, re.M)
        if seam is None or ':goto_0' not in part:
            raise ValueError('Missing hidden cache candidate append')
        prefix = (
            '    :scan_hidden_cache_unique\n'
            '    invoke-virtual {p1, ' + seam.group(1) + '}, '
            'Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z\n'
            '    move-result v1\n'
            '    if-nez v1, :goto_0\n\n')
        return part[:seam.start()] + prefix + part[seam.start():]

    text = _replace_method(
        text, '.method private a(Ljava/util/ArrayList;)V', deduplicate_hidden_cache)
    if text != original:
        path.write_text(text)
    return {
        'paths': [str(path.relative_to(root))],
        'changed': text != original,
        'pending_wave_order': 'new subtree, max(created, modified) descending, path',
        'pending_queue': 'priority queue; rank once on enqueue; two folders per batch',
        'startup_discovery': 'selected roots before cached fallback folders',
        'startup_path_cache': 'normal and hidden when unlocked; normal when locked',
        'path_cache_format': 'unchanged; saved after successful full scan',
        'image_cache': 'not added',
    }


if __name__ == '__main__':
    import argparse
    import json
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('decoded_root')
    print(json.dumps(patch(parser.parse_args().decoded_root), ensure_ascii=False, indent=2))
