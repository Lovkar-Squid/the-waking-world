#!/usr/bin/env python3
"""Merge the decompiled alpha.11 class with the original repo source, member by member.

    merge.py <old classes dir> <new classes dir> <orig src root> <decomp root> <out root> <class> ...

For each top-level class: members whose bytecode did not change keep the ORIGINAL source text
(names, comments); changed or new members take the DECOMPILED text; removed members go.
Nested named types are replaced whole when anything inside them changed.
Prints a per-class summary; the result must then be compiled and compared with javap.
"""
import os, re, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jsplit
import members as M
import segments as S


def sig_key(sig, cls_simple):
    """javap member signature -> ('method', name, (params...)) or ('field', name) or None (lambda etc.)."""
    s = sig.rstrip(';')
    m = re.match(r'^(.*?)\b([\w$]+)\((.*)\)(?: throws .*)?$', s)
    if m:
        name = m.group(2)
        if name.startswith('lambda$') or name == '<clinit>' or name == 'static {}':
            return None
        params = tuple(jsplit.simple_type(p.replace('$', '.')) for p in jsplit._split_top(jsplit._strip_generics(m.group(3))))
        if name == '<init>':
            name = cls_simple
        if '.' in name:
            name = name.split('.')[-1]
        if name == cls_simple.split('$')[-1]:
            pass
        return ('method', name, params)
    if s == 'static {}':
        return ('init', 'static')
    # field: "public static final int X = 5" / "private java.util.List<Foo> list"
    left = s.split('=')[0].strip()
    name = left.split()[-1]
    return ('field', name)


def lambda_owner(sig):
    m = re.search(r'\blambda\$(\w+)\$\d+\(', sig)
    return m.group(1) if m else None


def analyse(old_cp, new_cp, cls):
    """Return dict: changed_methods (set of names), changed_fields, static_init_changed, ctors_changed,
    plus the member maps."""
    cls_simple = cls.split('/')[-1]
    o = M.members(M.javap(old_cp, cls)) if os.path.exists(os.path.join(old_cp, cls + '.class')) else {}
    n = M.members(M.javap(new_cp, cls))
    same_keys, changed_keys = set(), set()
    changed_names = set()      # method names (overload-insensitive) that changed through lambdas
    header_changed = False
    old_lambda_bodies = {}
    for sig, body in o.items():
        lo = lambda_owner(sig)
        if lo:
            old_lambda_bodies.setdefault(lo, []).append(body)
    for sig, body in n.items():
        lo = lambda_owner(sig)
        if lo:
            if body not in old_lambda_bodies.get(lo, []):
                changed_names.add(lo)
            continue
        k = sig_key(sig, cls_simple)
        if k is None:
            continue
        if sig in o and o[sig] == body:
            same_keys.add(k)
        else:
            changed_keys.add(k)
    for sig in o:
        if sig not in n and not lambda_owner(sig):
            k = sig_key(sig, cls_simple)
            if k and k not in changed_keys and k not in same_keys:
                changed_keys.add(('removed',) + k)
    # a lambda changed in `foo` taints every overload of foo
    for k in list(same_keys):
        if k[0] == 'method' and k[1] in changed_names:
            same_keys.discard(k)
            changed_keys.add(k)
    static_init_changed = ('init', 'static') in changed_keys or 'static' in changed_names
    ctor_keys = [k for k in same_keys | changed_keys if k[0] == 'method' and k[1] == cls_simple.split('$')[-1]]
    ctors_changed = any(k in changed_keys for k in ctor_keys) or 'new' in changed_names
    return dict(same=same_keys, changed=changed_keys, static_init_changed=static_init_changed,
                ctors_changed=ctors_changed, old=o, new=n)


def code_of(text):
    return re.sub(r'\s+', ' ', jsplit._strip_comments(text)).strip()


def member_keys(m):
    """All keys a member covers (a multi-declarator field covers several)."""
    if m.kind == 'field':
        return [('field', m.name)] + [('field', x) for x in m.params]
    return [member_key(m)]


def member_key(m):
    if m.kind == 'method':
        return ('method', m.name, tuple(m.params))
    if m.kind == 'field':
        return ('field', m.name)
    if m.kind == 'type':
        return ('type', m.name)
    if m.kind == 'init':
        return ('init', m.name)
    return ('unknown', m.text[:30])


def nested_changed(old_cp, new_cp, cls, name):
    """Anything changed inside nested type cls$name (or deeper)?"""
    base = cls + '$' + name
    out = False
    for inner in [base] + M.inner_classes(new_cp, base):
        if not os.path.exists(os.path.join(old_cp, inner + '.class')):
            return True
        same, changed, added, removed = M.compare(old_cp, new_cp, inner)
        if changed or added or removed:
            return True
    return out


def merge_class(old_cp, new_cp, src_root, dec_root, out_root, cls):
    rel = cls + '.java'
    orig_path = os.path.join(src_root, rel)
    dec_path = os.path.join(dec_root, rel)
    out_path = os.path.join(out_root, rel)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    dec_text = open(dec_path).read()
    if not os.path.exists(orig_path):
        open(out_path, 'w').write(dec_text)
        print(f'{cls}: NEW -> decompiled as is')
        return
    orig_text = open(orig_path).read()
    cls_simple = cls.split('/')[-1]
    a = analyse(old_cp, new_cp, cls)
    old_segs, new_segs = S.field_segments(old_cp, cls), S.field_segments(new_cp, cls)
    ou, _ = jsplit.parse(orig_text)
    du, _ = jsplit.parse(dec_text)
    omap = {}
    for mm in ou.members:
        for k in member_keys(mm):
            omap[k] = mm
    dmap = {member_key(m): m for m in du.members}
    kept, replaced, added, removed = [], [], [], []

    def unchanged(k):
        if k[0] == 'method':
            return k in a['same']
        if k[0] == 'field':
            if k not in a['same']:
                return False
            m = omap[k]
            init_changed = a['static_init_changed'] if m.is_static else a['ctors_changed']
            if not init_changed:
                return True
            # the initialiser block changed somewhere: is this field's own segment of it the same?
            if old_segs.get(k[1]) == new_segs.get(k[1]):
                return True
            return code_of(m.text) == code_of(dmap[k].text)
        if k[0] == 'type':
            return not nested_changed(old_cp, new_cp, cls, k[1])
        if k[0] == 'init':
            return (('init', 'static') in a['same']) if k[1] == 'static' else not a['ctors_changed']
        return False

    out = []   # list of (set of keys, text)
    for m in ou.members:
        keys = [k for k in member_keys(m) if k in dmap]
        gone = [k for k in member_keys(m) if k not in dmap]
        removed.extend(gone)
        if not keys:
            continue
        if all(unchanged(k) for k in keys) and not gone:
            out.append((set(keys), m.text))
            kept.extend(keys)
        else:
            for k in keys:
                out.append(({k}, dmap[k].text))
                replaced.append(k)
    # new members go after the member that precedes them in alpha.11's own order
    # (the class file keeps fields and methods each in source order)
    order = [sig_key(sig, cls_simple) for sig in a['new']]
    order = [k for k in order if k]

    def insert_after(k, text):
        i = order.index(k) if k in order else -1
        j = i - 1
        while j >= 0:
            pk = order[j]
            if pk[0] == k[0]:
                for idx in range(len(out) - 1, -1, -1):
                    if pk in out[idx][0]:
                        out.insert(idx + 1, ({k}, text))
                        return
            j -= 1
        if k[0] == 'field':
            # no predecessor field kept: before the first field
            for idx, (ks, _) in enumerate(out):
                if any(x[0] == 'field' for x in ks):
                    out.insert(idx, ({k}, text))
                    return
        out.append(({k}, text))

    for m in du.members:
        k = member_key(m)
        if k not in omap:
            insert_after(k, m.text)
            added.append(k)
    out_members = [t for _, t in out]
    # class header: keep the original unless the declaration itself changed
    old_head = next((s for s in a['old']), None)
    type_open = ou.type_open
    o_first = M.javap(old_cp, cls)
    n_first = M.javap(new_cp, cls)
    o_decl = next((l for f, l in o_first if not f and l.strip().endswith('{')), '')
    n_decl = next((l for f, l in n_first if not f and l.strip().endswith('{')), '')
    if o_decl != n_decl:
        type_open = du.type_open
        replaced.append(('decl', o_decl.strip(), n_decl.strip()))
    imports = list(dict.fromkeys(ou.imports + du.imports))
    header = ou.header.rstrip('\n') + '\n\n'
    body = ''.join(out_members)
    text = header + '\n'.join(imports) + '\n\n' + type_open + body + '\n}\n'
    open(out_path, 'w').write(text)
    print(f'{cls}: kept {len(kept)}, replaced {len(replaced)}, added {len(added)}, removed {len(removed)}')
    for k in replaced:
        print('   ~', k)
    for k in added:
        print('   +', k)
    for k in removed:
        print('   -', k)
    # sanity: methods the analysis knows about but neither file has
    return dict(kept=kept, replaced=replaced, added=added, removed=removed)


if __name__ == '__main__':
    old_cp, new_cp, src_root, dec_root, out_root = sys.argv[1:6]
    for cls in sys.argv[6:]:
        merge_class(old_cp, new_cp, src_root, dec_root, out_root, cls)
