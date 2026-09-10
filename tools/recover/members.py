#!/usr/bin/env python3
"""Per-member comparison of two compiles of the same classes.

    members.py <old classpath dir> <new classpath dir> <class name> ...

For every class (inner classes included, found on disk) prints which fields/methods are identical,
changed, added or removed, comparing normalised javap -c -p -constants output member by member.
Lambda bodies (lambda$foo$0) are attributed to the enclosing method name `foo`.
"""
import re, subprocess, sys, os, json

STRIP = re.compile(r'#\d+')
SPACES = re.compile(r' +')


def javap(cp, cls):
    out = subprocess.run(['javap', '-c', '-p', '-constants', '-cp', cp, cls], capture_output=True, text=True).stdout
    lines = []
    for ln in out.splitlines():
        if ln.startswith('Picked up') or ln.startswith('Compiled from') or 'LineNumberTable' in ln:
            continue
        if re.match(r'^\s*line \d+: \d+$', ln):
            continue
        member = bool(re.match(r'^  \S', ln)) and ln.rstrip().endswith(';') and not re.match(r'^\s*\d+: ', ln)
        lines.append((member, SPACES.sub(' ', STRIP.sub('', ln))))
    return lines


def members(lines):
    """Return ordered dict signature -> body text."""
    res = {}
    cur = None
    body = []
    for member, ln in lines:
        if member:
            if cur is not None:
                res[cur] = '\n'.join(body)
            cur = ln.strip()
            body = []
        elif cur is not None:
            body.append(ln.rstrip())
    if cur is not None:
        res[cur] = '\n'.join(body)
    return res


def key(sig):
    """Method name (for lambda attribution) or field name."""
    m = re.search(r'(\S+)\(', sig)
    if m:
        name = m.group(1)
        lm = re.match(r'lambda\$(\w+)\$\d+', name)
        return ('method', lm.group(1) if lm else name, sig)
    return ('field', sig.rstrip(';').split(' ')[-1].split('=')[0].strip(), sig)


def compare(old_cp, new_cp, cls):
    o = members(javap(old_cp, cls)) if os.path.exists(os.path.join(old_cp, cls + '.class')) else {}
    n = members(javap(new_cp, cls))
    same, changed, added, removed = [], [], [], []
    for sig, body in n.items():
        if sig in o:
            (same if o[sig] == body else changed).append(sig)
        else:
            added.append(sig)
    for sig in o:
        if sig not in n:
            removed.append(sig)
    return same, changed, added, removed


def inner_classes(cp, top):
    d = os.path.dirname(os.path.join(cp, top))
    base = os.path.basename(top)
    out = []
    if os.path.isdir(d):
        for f in sorted(os.listdir(d)):
            if f.startswith(base + '$') and f.endswith('.class'):
                out.append(os.path.join(os.path.dirname(top), f[:-6]))
    return out


def main():
    old_cp, new_cp = sys.argv[1], sys.argv[2]
    report = {}
    for top in sys.argv[3:]:
        for cls in [top] + inner_classes(new_cp, top):
            same, changed, added, removed = compare(old_cp, new_cp, cls)
            report[cls] = dict(same=same, changed=changed, added=added, removed=removed)
            if changed or added or removed or not os.path.exists(os.path.join(old_cp, cls + '.class')):
                print(f'== {cls}: {len(same)} same, {len(changed)} changed, {len(added)} added, {len(removed)} removed')
                for s in changed: print('   ~', s)
                for s in added: print('   +', s)
                for s in removed: print('   -', s)
    json.dump(report, open('/root/wwrec/members.json', 'w'), indent=1)


if __name__ == '__main__':
    main()
