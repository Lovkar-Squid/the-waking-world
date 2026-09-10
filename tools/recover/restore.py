#!/usr/bin/env python3
"""restore.py <cls> <method> [param,param,...]  - put the ORIGINAL source text of a method back into
the merged file in /root/wakingworld/src (in place of the decompiled text). Prints the old/new decompiled
diff so the change can then be ported by hand."""
import sys, os, difflib
sys.path.insert(0, '/root/wwrec')
import jsplit


def member(path, name, params):
    u, _ = jsplit.parse(open(path).read())
    for m in u.members:
        if m.kind == 'method' and m.name == name and (params is None or tuple(m.params) == tuple(params)):
            return m
    return None


def main():
    cls, name = sys.argv[1], sys.argv[2]
    params = [p for p in sys.argv[3].split(',') if p] if len(sys.argv) > 3 else None
    cur_path = f'/root/wakingworld/src/{cls}.java'
    orig = member(f'/root/wwrec/src-orig/{cls}.java', name, params)
    cur = member(cur_path, name, params)
    assert orig and cur, (orig is not None, cur is not None)
    text = open(cur_path).read()
    assert text.count(cur.text) == 1
    text = text.replace(cur.text, orig.text, 1)
    open(cur_path, 'w').write(text)
    old = member(f'/root/wwrec/decomp-old/{cls}.java', name, params)
    new = member(f'/root/wwrec/decomp/{cls}.java', name, params)
    if old and new:
        d = difflib.unified_diff(old.text.strip('\n').split('\n'), new.text.strip('\n').split('\n'), 'old', 'new', n=3, lineterm='')
        print('\n'.join(d))
    print(f'restored {cls} {name}')


if __name__ == '__main__':
    main()
