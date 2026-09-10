#!/usr/bin/env python3
"""Semantic fingerprint comparison of two compiles: per method, the multiset of instructions that
carry meaning (calls, field access, constants, arithmetic, new, instanceof, athrow ...), ignoring
control flow shape, local slot numbers, casts, dup/pop. A decompile-recompile round trip keeps the
fingerprint; a decompiler mistake (dropped branch, wrong constant, wrong call) changes it.

    fingerprint.py <cp A> <cp B> <class> ...      (classes with $ inner classes are found on disk)
"""
import re, sys, os
from collections import Counter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import members as M

IGNORE = re.compile(r'^(goto|goto_w|if\w*|return|[ilfda]return|[ilfda]load(_\d)?|[ilfda]store(_\d)?|aload|astore|'
                    r'dup\w*|pop\w?|nop|checkcast|swap|tableswitch|lookupswitch|iinc|athrow|jsr|ret|'
                    r'i2l|l2i|i2d|d2i|f2d|d2f|i2f|f2i|i2b|i2c|i2s|l2d|d2l|f2l|l2f|monitorenter|monitorexit)$')
CONSTS = {'iconst_m1': -1, 'iconst_0': 0, 'iconst_1': 1, 'iconst_2': 2, 'iconst_3': 3, 'iconst_4': 4, 'iconst_5': 5,
          'lconst_0': 0, 'lconst_1': 1, 'fconst_0': 0.0, 'fconst_1': 1.0, 'fconst_2': 2.0, 'dconst_0': 0.0, 'dconst_1': 1.0}


def fp(body):
    c = Counter()
    for ln in body.split('\n'):
        m = re.match(r'^\s*\d+: (\w+)\s*(.*)$', ln)
        if not m:
            continue
        op, rest = m.group(1), m.group(2).strip()
        if IGNORE.match(op):
            continue
        if op in CONSTS:
            c[('const', str(CONSTS[op]))] += 1
            continue
        if op in ('bipush', 'sipush'):
            c[('const', rest)] += 1
            continue
        if op in ('ldc', 'ldc_w', 'ldc2_w'):
            v = rest.split('//', 1)[1].strip() if '//' in rest else rest
            v = re.sub(r'^(int|float|long|double|String|class) ', '', v)
            v = v.rstrip('fdFD') if re.match(r'^-?[\d.]+[fdFD]$', v) else v
            c[('const', v)] += 1
            continue
        if op in ('invokevirtual', 'invokestatic', 'invokespecial', 'invokeinterface', 'invokedynamic',
                  'getfield', 'putfield', 'getstatic', 'putstatic', 'new', 'anewarray', 'newarray', 'multianewarray', 'instanceof'):
            v = rest.split('//', 1)[1].strip() if '//' in rest else rest
            v = re.sub(r'lambda\$\w+\$\d+', 'lambda', v)
            c[(op, v)] += 1
            continue
        c[(op, '')] += 1
    return c


def compare(cpa, cpb, cls):
    a = M.members(M.javap(cpa, cls))
    b = M.members(M.javap(cpb, cls))
    # lambdas are matched by fingerprint, not name
    la = [fp(v) for k, v in a.items() if 'lambda$' in k]
    lb = [fp(v) for k, v in b.items() if 'lambda$' in k]
    out = []
    for k in b:
        if 'lambda$' in k:
            continue
        if k not in a:
            out.append(('missing in A', k))
            continue
        fa, fb = fp(a[k]), fp(b[k])
        if fa != fb:
            out.append(('differs', k, fa - fb, fb - fa))
    for k in a:
        if k not in b and 'lambda$' not in k:
            out.append(('missing in B', k))
    # lambda multiset
    ua = list(la)
    for f in lb:
        if f in ua:
            ua.remove(f)
        else:
            out.append(('lambda differs (B has a body A lacks)', str(dict(f))[:300]))
    for f in ua:
        out.append(('lambda differs (A has a body B lacks)', str(dict(f))[:300]))
    return out


if __name__ == '__main__':
    cpa, cpb = sys.argv[1], sys.argv[2]
    total = 0
    for top in sys.argv[3:]:
        for cls in [top] + M.inner_classes(cpa, top):
            if not os.path.exists(os.path.join(cpb, cls + '.class')):
                print(f'== {cls}: MISSING in B')
                total += 1
                continue
            out = compare(cpa, cpb, cls)
            if out:
                total += 1
                print(f'== {cls}')
                for o in out:
                    if o[0] == 'differs':
                        print(f'   ~ {o[1]}')
                        for k, v in o[2].items():
                            print(f'       A only: {v}x {k}')
                        for k, v in o[3].items():
                            print(f'       B only: {v}x {k}')
                    else:
                        print(f'   {o[0]}: {o[1]}')
    print(f'classes with fingerprint differences: {total}')
