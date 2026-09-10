#!/usr/bin/env python3
"""Make a private nested class public at compile time, the way NeoForge's access transformer does
at runtime.

javac decides whether `Outer.Inner` may be used from the InnerClasses attribute of the class files it
reads - the entry in the OUTER class and the one the inner class carries about itself. A stub with only
the inner class does not help: the outer's attribute still says "private". So this rewrites those two
attributes in copies of the real class files, which then go first on the compile classpath (stubs/).

    open_nested.py <jar> <stubs dir> net/minecraft/client/particle/ParticleEngine$SpriteParticleRegistration ...
"""
import io, struct, sys, zipfile

ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED = 0x0001, 0x0002, 0x0004


def read_cp(buf):
    """Return (constant pool as list, offset after it). Index 0 unused."""
    n = struct.unpack_from('>H', buf, 8)[0]
    off = 10
    cp = [None]
    i = 1
    while i < n:
        tag = buf[off]
        if tag == 1:
            ln = struct.unpack_from('>H', buf, off + 1)[0]
            cp.append(('utf8', buf[off + 3:off + 3 + ln].decode('utf-8', 'replace')))
            off += 3 + ln
        elif tag in (3, 4):
            cp.append(('int', None)); off += 5
        elif tag in (5, 6):
            cp.append(('long', None)); cp.append(None); off += 9; i += 1
        elif tag == 7:
            cp.append(('class', struct.unpack_from('>H', buf, off + 1)[0])); off += 3
        elif tag == 8:
            cp.append(('string', None)); off += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            cp.append(('ref', None)); off += 5
        elif tag in (15,):
            cp.append(('mh', None)); off += 4
        elif tag in (16, 19, 20):
            cp.append(('one', None)); off += 3
        else:
            raise SystemExit(f'unknown constant tag {tag}')
        i += 1
    return cp, off


def skip_members(buf, off):
    count = struct.unpack_from('>H', buf, off)[0]; off += 2
    for _ in range(count):
        off += 6
        ac = struct.unpack_from('>H', buf, off)[0]; off += 2
        for _ in range(ac):
            ln = struct.unpack_from('>I', buf, off + 2)[0]
            off += 6 + ln
    return off


def open_inner(data: bytes, wanted: set) -> bytes:
    buf = bytearray(data)
    cp, off = read_cp(buf)
    off += 6  # access, this, super
    ifn = struct.unpack_from('>H', buf, off)[0]; off += 2 + 2 * ifn
    off = skip_members(buf, off)   # fields
    off = skip_members(buf, off)   # methods
    ac = struct.unpack_from('>H', buf, off)[0]; off += 2
    changed = 0
    for _ in range(ac):
        name_idx, ln = struct.unpack_from('>HI', buf, off)
        body = off + 6
        if cp[name_idx][1] == 'InnerClasses':
            n = struct.unpack_from('>H', buf, body)[0]
            p = body + 2
            for _ in range(n):
                inner_idx, outer_idx, name_i, flags = struct.unpack_from('>HHHH', buf, p)
                inner_name = cp[cp[inner_idx][1]][1] if inner_idx else ''
                if inner_name in wanted:
                    new = (flags & ~(ACC_PRIVATE | ACC_PROTECTED)) | ACC_PUBLIC
                    struct.pack_into('>H', buf, p + 6, new)
                    changed += 1
                p += 8
        off = body + ln
    return bytes(buf), changed


def main():
    jar, out = sys.argv[1], sys.argv[2]
    wanted = set(sys.argv[3:])
    outers = {w.rsplit('$', 1)[0] for w in wanted}
    with zipfile.ZipFile(jar) as z:
        for name in sorted(wanted | outers):
            data = z.read(name + '.class')
            patched, changed = open_inner(data, wanted)
            path = f'{out}/{name}.class'
            import os
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'wb') as f:
                f.write(patched)
            print(f'{name}: {changed} InnerClasses entr{"y" if changed == 1 else "ies"} opened')


if __name__ == '__main__':
    main()
