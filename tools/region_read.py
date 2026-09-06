"""Read blocks straight out of a world's region files - no game, no client.

Just enough of the Anvil format to pull a chunk's palette and block states: used by
tools/region_iso.py to draw what a cataclysm left behind, and handy on its own for counting
what a change actually placed without a fill command destroying the evidence.
"""
import io, os, zlib
import nbtlib.nbt as N

REGION = os.environ.get("MC_REGION", "/root/nfserver/world/region")

def load_chunk(cx, cz):
    rx, rz = cx >> 5, cz >> 5
    with open(f"{REGION}/r.{rx}.{rz}.mca", "rb") as f:
        f.seek(4 * ((cx & 31) + (cz & 31) * 32))
        head = f.read(4)
        off = int.from_bytes(head[:3], "big")
        if off == 0:
            return None
        f.seek(off * 4096)
        length = int.from_bytes(f.read(4), "big")
        comp = f.read(1)[0]
        raw = f.read(length - 1)
        raw = zlib.decompress(raw) if comp == 2 else raw
    return N.File.from_fileobj(io.BytesIO(raw))

def block_grid(x0, z0, x1, z1, y0, y1):
    """dict[(x,y,z)] = block name, for the box."""
    out = {}
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            ch = load_chunk(cx, cz)
            if ch is None:
                continue
            root = ch.root if hasattr(ch, "root") else ch
            for sec in root.get("sections", []):
                sy = int(sec["Y"])
                bs = sec.get("block_states")
                if bs is None:
                    continue
                palette = [str(p["Name"]) for p in bs["palette"]]
                if len(palette) == 1:
                    name = palette[0]
                    if name == "minecraft:air":
                        continue
                    data = None
                else:
                    data = list(bs["data"])
                bits = max(4, (len(palette) - 1).bit_length())
                per = 64 // bits
                mask = (1 << bits) - 1
                for i in range(4096):
                    yy = sy * 16 + (i >> 8)
                    if yy < y0 or yy > y1:
                        continue
                    zz = cz * 16 + ((i >> 4) & 15)
                    xx = cx * 16 + (i & 15)
                    if not (x0 <= xx <= x1 and z0 <= zz <= z1):
                        continue
                    if data is None:
                        idx = 0
                    else:
                        w = data[i // per]
                        idx = (w >> (bits * (i % per))) & mask
                    name = palette[idx]
                    if name == "minecraft:air" or name == "minecraft:cave_air":
                        continue
                    out[(xx, yy, zz)] = name
    return out
