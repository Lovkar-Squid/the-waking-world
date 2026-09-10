#!/usr/bin/env python3
"""Split a Java source file into its top-level type's members, brace-aware.

    parse(text) -> Unit(header, imports, type_open, members, footer)

header  : everything before the first import / type declaration (package line, comments)
imports : list of import lines (text)
type_open: the text from the type's first modifier/annotation up to and including the opening '{'
members : list of Member(kind, name, params, text, is_static) in order; text includes the comments
          that precede the member (from the end of the previous member) and the member itself
footer  : text after the type's closing '}' (usually '\n')

Member kinds: 'method' (constructors too, name = simple class name), 'field', 'type' (nested
class/interface/enum/record), 'init' (static or instance initializer), 'enumconsts'.
"""
import re
from dataclasses import dataclass, field


@dataclass
class Member:
    kind: str
    name: str
    params: list          # simple type names for methods
    text: str
    is_static: bool = False
    modifiers: str = ''


@dataclass
class Unit:
    header: str
    imports: list
    type_open: str
    members: list
    footer: str
    type_name: str = ''
    type_kind: str = ''


def _skip_ws_comments(s, i):
    """Return index of the first char that is not whitespace or comment, starting at i."""
    n = len(s)
    while i < n:
        c = s[i]
        if c in ' \t\r\n':
            i += 1
        elif s.startswith('//', i):
            j = s.find('\n', i)
            i = n if j < 0 else j + 1
        elif s.startswith('/*', i):
            j = s.find('*/', i + 2)
            i = n if j < 0 else j + 2
        else:
            break
    return i


def _scan(s, i, stop_depth_zero_semicolon=False):
    """Walk from i (which must be code) to the end of the current member: returns index just after
    the terminating ';' or '}' at depth 0. Handles strings, chars, text blocks, comments."""
    n = len(s)
    depth = 0
    seen_brace = False
    while i < n:
        c = s[i]
        if s.startswith('//', i):
            j = s.find('\n', i)
            i = n if j < 0 else j + 1
            continue
        if s.startswith('/*', i):
            j = s.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if s.startswith('"""', i):
            j = s.find('"""', i + 3)
            while j >= 0 and s[j - 1] == '\\':
                j = s.find('"""', j + 1)
            i = n if j < 0 else j + 3
            continue
        if c == '"':
            i += 1
            while i < n and s[i] != '"':
                if s[i] == '\\':
                    i += 1
                i += 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and s[i] != "'":
                if s[i] == '\\':
                    i += 1
                i += 1
            i += 1
            continue
        if c == '{':
            depth += 1
            seen_brace = True
        elif c == '}':
            depth -= 1
            if depth == 0:
                i += 1
                # a field with an array initialiser or an anonymous class: "= new X() { ... };"
                j = _skip_ws_comments(s, i)
                if j < n and s[j] == ';':
                    return j + 1
                return i
        elif c == ';' and depth == 0:
            return i + 1
        i += 1
    return n


TYPE_RE = re.compile(r'\b(class|interface|enum|record|@interface)\s+(\w+)')
MODS = {'public', 'private', 'protected', 'static', 'final', 'abstract', 'synchronized', 'native',
        'transient', 'volatile', 'strictfp', 'default', 'sealed', 'non-sealed'}


def _strip_comments(t):
    out = []
    i = 0
    n = len(t)
    while i < n:
        if t.startswith('//', i):
            j = t.find('\n', i)
            i = n if j < 0 else j
        elif t.startswith('/*', i):
            j = t.find('*/', i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(t[i])
            i += 1
    return ''.join(out)


def _strip_generics(t):
    out = []
    depth = 0
    for c in t:
        if c == '<':
            depth += 1
        elif c == '>':
            depth -= 1
        elif depth == 0:
            out.append(c)
    return ''.join(out)


def _strip_annotations(t):
    # remove @Foo and @Foo(...) (one level of parens)
    return re.sub(r'@\w+(\([^)]*\))?\s*', '', t)


def _split_top(t, sep=','):
    parts, depth, cur = [], 0, []
    for c in t:
        if c in '<([{':
            depth += 1
        elif c in '>)]}':
            depth -= 1
        if c == sep and depth == 0:
            parts.append(''.join(cur))
            cur = []
        else:
            cur.append(c)
    parts.append(''.join(cur))
    return [p for p in parts if p.strip()]


def simple_type(t):
    """'final Map<String, List<Foo>>[] x' -> 'Map[]'; 'String... names' -> 'String[]'."""
    t = _strip_annotations(t.strip())
    t = t.replace('final ', '')
    t = _strip_generics(t)
    t = t.strip()
    varargs = '...' in t
    t = t.replace('...', ' ')
    # drop the parameter name (last identifier)
    toks = t.split()
    if len(toks) >= 2:
        # the name may carry [] (int x[]) - rare, ignore
        t = ' '.join(toks[:-1])
    dims = t.count('[]') + (1 if varargs else 0)
    base = t.replace('[]', '').strip()
    base = base.split('.')[-1]
    return base + '[]' * dims


def classify(text):
    """Return (kind, name, params, is_static, modifiers) of one member text (comments allowed)."""
    code = _strip_comments(text).strip()
    head = code
    # cut the body / initialiser off for classification purposes
    m = TYPE_RE.search(_strip_generics(head.split('{', 1)[0]) if '{' in head else _strip_generics(head))
    first_paren = head.find('(')
    first_brace = head.find('{')
    first_eq = head.find('=')
    first_semi = head.find(';')
    if m and (first_paren < 0 or m.start() < first_paren) and (first_eq < 0 or m.start() < first_eq):
        return 'type', m.group(2), [], ' static ' in ' ' + head[:m.start()] + ' ', head[:m.start()].strip()
    if re.match(r'^(static\s*)?\{', head):
        return 'init', 'static' if head.startswith('static') else 'instance', [], head.startswith('static'), ''
    if first_paren >= 0 and (first_eq < 0 or first_paren < first_eq) and (first_brace < 0 or first_paren < first_brace) and (first_semi < 0 or first_paren < first_semi):
        sig = head[:first_paren]
        sig_ng = _strip_annotations(_strip_generics(sig))
        toks = sig_ng.split()
        name = toks[-1]
        mods = [x for x in toks if x in MODS]
        # params
        depth, j = 0, first_paren
        while j < len(head):
            if head[j] == '(':
                depth += 1
            elif head[j] == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        params = [simple_type(p) for p in _split_top(head[first_paren + 1:j])]
        return 'method', name, params, 'static' in mods, ' '.join(mods)
    # field (or enum constants): "mods Type a = 1, b, c = {1, 2};" - every declarator name in order
    decl_full = code[:first_semi] if first_semi >= 0 else code
    parts = _split_top(decl_full)
    first = _strip_annotations(_strip_generics(parts[0].split('=', 1)[0])).strip()
    toks = first.replace('[]', ' ').split()
    if not toks:
        return 'unknown', '', [], False, ''
    name = toks[-1]
    mods = [x for x in toks if x in MODS]
    names = [name]
    for part in parts[1:]:
        part = part.split('=', 1)[0].strip()
        if re.match(r'^\w+$', part):
            names.append(part)
    return 'field', name, names[1:], 'static' in mods, ' '.join(mods)


def parse(text):
    n = len(text)
    # header: up to first import or type declaration (outside comments)
    i = 0
    imports = []
    header_end = None
    body_start = None
    pos = 0
    while True:
        j = _skip_ws_comments(text, pos)
        if j >= n:
            raise ValueError('no type declaration found')
        if text.startswith('package ', j):
            pos = text.find(';', j) + 1
            continue
        if text.startswith('import ', j):
            if header_end is None:
                header_end = j
            k = text.find(';', j) + 1
            imports.append(text[j:k].strip())
            pos = k
            continue
        # type declaration starts here (annotations/modifiers)
        if header_end is None:
            header_end = j
        body_start = j
        break
    header = text[:header_end]
    # find the opening brace of the type
    k = body_start
    depth_paren = 0
    while k < n:
        c = text[k]
        if c == '(':
            depth_paren += 1
        elif c == ')':
            depth_paren -= 1
        elif c == '{' and depth_paren == 0:
            break
        elif text.startswith('//', k):
            k = text.find('\n', k)
        elif text.startswith('/*', k):
            k = text.find('*/', k) + 1
        k += 1
    type_open = text[body_start:k + 1]
    tm = TYPE_RE.search(_strip_generics(type_open))
    type_kind, type_name = (tm.group(1), tm.group(2)) if tm else ('', '')
    members = []
    pos = k + 1
    while True:
        j = _skip_ws_comments(text, pos)
        if j >= n:
            raise ValueError('unterminated type body')
        if text[j] == '}':
            # end of type
            trailing = text[pos:j]
            footer = text[j:]
            return Unit(header, imports, type_open, members, footer, type_name, type_kind), trailing
        end = _scan(text, j)
        mtext = text[pos:end]
        kind, name, params, is_static, mods = classify(mtext)
        members.append(Member(kind, name, params, mtext, is_static, mods))
        pos = end


if __name__ == '__main__':
    import sys
    unit, trailing = parse(open(sys.argv[1]).read())
    print('type', unit.type_kind, unit.type_name, 'imports', len(unit.imports))
    for m in unit.members:
        print(f'{m.kind:8} {"static " if m.is_static else "":7} {m.name}({", ".join(m.params)})  [{len(m.text)} chars]')
