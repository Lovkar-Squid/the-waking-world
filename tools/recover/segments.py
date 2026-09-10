#!/usr/bin/env python3
"""Per-field initialiser segments of a class, read from javap -v.

    field_segments(cp, cls) -> {field name: normalised text of the code that initialises it}

The static initialiser is a run of "<compute>; putstatic FIELD" sequences, and every constructor
that calls super() starts with the same run for the instance fields ("putfield"). The text of a
segment has offsets and constant-pool indices stripped, and every invokedynamic is replaced by the
(normalised, recursive) body of the lambda it calls, so a lambda edit counts as a field edit.
"""
import re, subprocess, os
from functools import lru_cache

SPACES = re.compile(r' +')


@lru_cache(maxsize=None)
def javap_v(cp, cls):
    return subprocess.run(['javap', '-v', '-p', '-c', '-constants', '-cp', cp, cls], capture_output=True, text=True).stdout


def parse(cp, cls):
    """Return (methods: {sig: [instruction lines]}, bootstraps: {index: impl string})."""
    out = javap_v(cp, cls)
    methods = {}
    bootstraps = {}
    cur = None
    in_code = False
    in_bsm = False
    bsm_idx = None
    for ln in out.splitlines():
        if ln.startswith('BootstrapMethods:'):
            in_bsm = True
            cur = None
            continue
        if in_bsm:
            m = re.match(r'^\s*(\d+): #\d+ (REF_\w+ \S+)', ln)
            if m:
                bsm_idx = int(m.group(1))
                bootstraps[bsm_idx] = ''
                continue
            m = re.match(r'^\s*#\d+ (REF_\w+ \S+)', ln)
            if m and bsm_idx is not None:
                # the implementation handle is the REF_ argument
                bootstraps[bsm_idx] = m.group(1)
                continue
            if ln.startswith('InnerClasses:') or (ln and not ln.startswith(' ')):
                in_bsm = False
            continue
        if re.match(r'^  \S', ln) and ln.rstrip().endswith(';'):
            cur = SPACES.sub(' ', re.sub(r'#\d+', '', ln)).strip()
            methods[cur] = []
            in_code = False
            continue
        if cur is not None and ln.strip() == 'Code:':
            in_code = True
            continue
        if cur is not None and in_code:
            m = re.match(r'^\s+(\d+): (\w+)\s*(.*)$', ln)
            if m:
                op, rest = m.group(2), m.group(3)
                if op == 'invokedynamic':
                    b = re.search(r'InvokeDynamic #(\d+):(\S+)', rest)
                    methods[cur].append(('indy', int(b.group(1)), b.group(2)))
                else:
                    methods[cur].append(('op', op + ' ' + SPACES.sub(' ', re.sub(r'#\d+,?\s*', '', rest)).strip()))
            elif re.match(r'^\s+(LineNumberTable|LocalVariableTable|StackMapTable|Exception table|RuntimeVisible)', ln):
                in_code = False
    return methods, bootstraps


def lambda_sig(methods, name):
    for sig in methods:
        if re.search(r'\b' + re.escape(name) + r'\(', sig):
            return sig
    return None


def render(methods, bootstraps, instrs, depth=0):
    out = []
    for ins in instrs:
        if ins[0] == 'op':
            out.append(ins[1])
        else:
            impl = bootstraps.get(ins[1], '?')
            m = re.search(r'\.(lambda\$[\w$]+):', impl)
            if m and depth < 4:
                sig = lambda_sig(methods, m.group(1))
                body = render(methods, bootstraps, methods.get(sig, []), depth + 1) if sig else '?'
                out.append('indy ' + ins[2] + ' {' + body + '}')
            else:
                out.append('indy ' + ins[2] + ' ' + re.sub(r'lambda\$\w+\$\d+', 'lambda', impl))
    return '\n'.join(out)


def field_segments(cp, cls):
    if not os.path.exists(os.path.join(cp, cls + '.class')):
        return {}
    methods, bootstraps = parse(cp, cls)
    segs = {}
    simple = cls.split('/')[-1]
    for sig, instrs in methods.items():
        is_clinit = sig.startswith('static {}')
        is_ctor = re.search(r'\b' + re.escape(simple.replace('$', '.')) + r'\(', sig) is not None and not is_clinit and '(' in sig and ' ' + simple.split('$')[-1] + '(' in sig.replace(cls.replace('/', '.').replace('$', '.'), simple.split('$')[-1])
        if not (is_clinit or is_ctor):
            continue
        run = []
        for ins in instrs:
            run.append(ins)
            if ins[0] == 'op' and (ins[1].startswith('putstatic') if is_clinit else ins[1].startswith('putfield')):
                m = re.search(r'// Field (?:[\w/$]+\.)?(\w+):', ins[1])
                if m:
                    name = m.group(1)
                    if name not in segs:
                        segs[name] = render(methods, bootstraps, run)
                run = []
    return segs


if __name__ == '__main__':
    import sys
    for cls in sys.argv[3:]:
        a = field_segments(sys.argv[1], cls)
        b = field_segments(sys.argv[2], cls)
        for k in b:
            print(k, 'same' if a.get(k) == b[k] else ('changed' if k in a else 'new'))
