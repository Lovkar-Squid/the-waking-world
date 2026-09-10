#!/usr/bin/env python3
"""For every replaced method in merge-report.txt (except the classes given as --skip), print the
unified diff between the OLD decompiled and the NEW decompiled method text, plus the original
source of the method - the guide for porting the change by hand into the original names.

    mdiff.py [class-substring] ...
"""
import re, sys, os, difflib
sys.path.insert(0, '/root/wwrec')
import jsplit

SKIP = {'me/lovkar/wakingworld/mage/MageEntity', 'me/lovkar/wakingworld/mage/RiteStoneEntity'}


def method_text(path, name, params):
    if not os.path.exists(path):
        return None
    u, _ = jsplit.parse(open(path).read())
    for m in u.members:
        if m.kind == 'method' and m.name == name and tuple(m.params) == tuple(params):
            return m.text
    return None


def main():
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    cls = None
    stats = []
    for ln in open('/root/wwrec/merge-report.txt'):
        if ln.startswith('me/'):
            cls = ln.split(':')[0]
            continue
        m = re.match(r"^   ~ \('method', '(\w+)', \((.*)\)\)", ln)
        if not m or cls in SKIP:
            continue
        if only and not any(o in cls for o in only):
            continue
        name = m.group(1)
        params = [p.strip().strip("'") for p in m.group(2).split(',') if p.strip()]
        old = method_text(f'/root/wwrec/decomp-old/{cls}.java', name, params)
        new = method_text(f'/root/wwrec/decomp/{cls}.java', name, params)
        orig = method_text(f'/root/wwrec/src-orig/{cls}.java', name, params)
        if old is None or new is None:
            print(f'#### {cls} {name}({", ".join(params)}): old={old is not None} new={new is not None}')
            continue
        d = list(difflib.unified_diff(old.strip('\n').split('\n'), new.strip('\n').split('\n'), 'old', 'new', n=2, lineterm=''))
        changed = sum(1 for x in d if (x.startswith('+') or x.startswith('-')) and not x.startswith('+++') and not x.startswith('---'))
        stats.append((changed, cls, name))
        print(f'#### {cls} {name}({", ".join(params)}): {changed} changed lines, orig {len(orig.splitlines()) if orig else 0} lines, new {len(new.splitlines())} lines')
        if '--diff' in sys.argv:
            print('\n'.join(d))
            print()
    if '--stats' in sys.argv:
        for s in sorted(stats):
            print(s)


if __name__ == '__main__':
    main()
