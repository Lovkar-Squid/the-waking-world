"""The Wandering Column, behind the scenes: where the storm walked, and what the client draws.

Left panel  - the world map around the run, the storm's real trajectory (polled a position a
              tick at a time off the test server) and every block it lifted or set back down,
              found by diffing the region files before and after.
Right panel - the debris column itself, straight out of TornadoRenderer's own helix: ninety
              motes seeded from the entity id, dumped by tools/java/TornadoDump.java.
Nothing here is drawn by hand.
"""
import sys, os, json, math, pickle
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont
from region_iso import P

SP = "/tmp/claude-0/-home-claude/f973fb44-8199-5bc3-ae5b-ee262cc95fe4/scratchpad"
INK, DIM, BG = (236, 230, 222), (146, 142, 138), (14, 14, 18)
HOT, COOL = (255, 158, 66), (150, 205, 255)

def font(bold, size):
    n = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:    return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{n}", size)
    except Exception: return ImageFont.load_default()

def surface(grid, x0, x1, z0, z1):
    """top block per column, and its height"""
    top = {}
    for (x, y, z), n in grid.items():
        if not (x0 <= x <= x1 and z0 <= z <= z1): continue
        k = (x, z)
        if k not in top or y > top[k][0]: top[k] = (y, n)
    return top

def map_panel(after, path, removed, added, x0, x1, z0, z1, px=3):
    top = surface(after, x0, x1, z0, z1)
    hs = [v[0] for v in top.values()]
    lo, hi = min(hs), max(hs)
    W, H = (x1 - x0 + 1) * px, (z1 - z0 + 1) * px
    img = Image.new("RGB", (W, H), (10, 10, 13))
    d = ImageDraw.Draw(img)
    for (x, z), (y, n) in top.items():
        col = P.get(n)
        if col is None:
            col = (0x6f, 0x6f, 0x74) if ("stone" in n or "slate" in n) else (0x5a, 0x5a, 0x60)
        f = 0.52 + 0.62 * ((y - lo) / max(1, hi - lo))          # height shading
        c = tuple(max(0, min(255, int(v * f))) for v in col)
        sx, sz = (x - x0) * px, (z - z0) * px
        d.rectangle([sx, sz, sx + px - 1, sz + px - 1], fill=c)

    # what the storm moved
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    dg = ImageDraw.Draw(glow)
    for (x, y, z) in added:
        if x0 <= x <= x1 and z0 <= z <= z1:
            sx, sz = (x - x0) * px, (z - z0) * px
            d.rectangle([sx - 1, sz - 1, sx + px, sz + px], fill=COOL)
    for (x, y, z) in removed:
        if x0 <= x <= x1 and z0 <= z <= z1:
            sx, sz = (x - x0) * px, (z - z0) * px
            d.rectangle([sx - 1, sz - 1, sx + px, sz + px], fill=HOT)
            dg.rectangle([sx - 2, sz - 2, sx + px + 1, sz + px + 1], fill=HOT)

    # the walked track
    pts = [((x - x0) * px + px / 2, (z - z0) * px + px / 2) for x, y, z in path]
    for w, c in ((7, (90, 46, 12)), (3, (255, 214, 150))):
        d.line(pts, fill=c, width=w, joint="curve")
    dg.line(pts, fill=(190, 120, 40), width=6, joint="curve")
    img = ImageChops.screen(img, glow.filter(ImageFilter.GaussianBlur(6)))
    d = ImageDraw.Draw(img)
    sx, sz = pts[0]; ex, ez = pts[-1]
    d.ellipse([sx - 6, sz - 6, sx + 6, sz + 6], outline=(255, 236, 200), width=2)
    d.ellipse([ex - 4, ez - 4, ex + 4, ez + 4], fill=(255, 236, 200))
    f = font(False, 12)
    def label(px, pz, text, dy):
        w = d.textlength(text, font=f)
        x = px + 11 if px + 13 + w < W - 6 else px - 11 - w
        y = min(max(pz + dy, 5), H - 19)
        d.rectangle([x - 4, y - 3, x + w + 4, y + 15], fill=(12, 12, 15))
        d.text((x, y), text, font=f, fill=(255, 236, 200))
    label(sx, sz, "touches down", -7)
    label(ex, ez, "lifts", 6)
    d.rectangle([0, 0, W - 1, H - 1], outline=(44, 44, 52))
    return img

def column_panel(motes, H):
    """the helix, isometric, back to front - real cubes, not sprites"""
    span = max(max(abs(m["x"]), abs(m["z"])) for m in motes) + 1.2
    top = max(m["y"] for m in motes) + 1.5
    VH = (H - 74) / top                      # pixels per block of height
    TW, TH = VH * 1.30, VH * 0.62
    W = int(span * 2 * TW * 0.62) + 56
    img = Image.new("RGB", (W, H), (10, 10, 13))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    d, dg = ImageDraw.Draw(img), ImageDraw.Draw(glow)
    ox, oy = W // 2, H - 42

    def project(x, y, z):
        return ox + (x - z) * TW / 2, oy + (x + z) * TH / 2 - y * VH

    for r, a in ((span * 0.92, 16), (span * 0.6, 26)):       # the dust it stands in
        pts = [project(math.cos(t / 28 * math.pi * 2) * r, 0, math.sin(t / 28 * math.pi * 2) * r) for t in range(28)]
        d.polygon(pts, fill=(16 + a, 15 + a, 20 + a))

    for m in sorted(motes, key=lambda m: (m["x"] + m["z"])):
        col = P.get("minecraft:" + m["block"], (0x80, 0x80, 0x86))
        s2 = m["size"]
        sx, sy = project(m["x"], m["y"], m["z"])
        depth = 0.74 + 0.42 * (m["x"] + m["z"] + span * 2) / (span * 4)
        c = tuple(max(0, min(255, int(v * depth))) for v in col)
        hw, hh, vh = TW * s2 / 2, TH * s2 / 2, VH * s2
        d.polygon([(sx, sy - hh), (sx + hw, sy), (sx, sy + hh), (sx - hw, sy)],
                  fill=tuple(int(v) for v in c))
        d.polygon([(sx - hw, sy), (sx, sy + hh), (sx, sy + hh + vh), (sx - hw, sy + vh)],
                  fill=tuple(int(v * 0.64) for v in c))
        d.polygon([(sx + hw, sy), (sx, sy + hh), (sx, sy + hh + vh), (sx + hw, sy + vh)],
                  fill=tuple(int(v * 0.45) for v in c))
        dg.ellipse([sx - hw, sy - hh, sx + hw, sy + hh + vh], fill=tuple(int(v * 0.22) for v in c))

    img = ImageChops.screen(img, glow.filter(ImageFilter.GaussianBlur(13)))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], outline=(44, 44, 52))
    return img

def main():
    after = pickle.load(open(f"{SP}/t4_after.pkl", "rb"))
    diff = pickle.load(open(f"{SP}/t4_diff.pkl", "rb"))
    col = json.load(open(f"{SP}/tornado_column.json"))
    path = [tuple(map(float, l.split())) for l in open("/tmp/path.txt")]
    seen, trim = set(), []
    for p in path:                                   # drop the frozen tail once it stops moving
        k = (round(p[0], 3), round(p[2], 3))
        if k in seen: continue
        seen.add(k); trim.append(p)
    walked = sum(math.dist((a[0], a[2]), (b[0], b[2])) for a, b in zip(trim, trim[1:]))

    xs = [p[0] for p in trim]; zs = [p[2] for p in trim]
    pad = 26
    gx = [p[0] for p in after]; gz = [p[2] for p in after]
    lx0, lx1, lz0, lz1 = min(gx), max(gx), min(gz), max(gz)
    x0, x1 = max(int(min(xs)) - pad, lx0), min(int(max(xs)) + pad, lx1)
    z0, z1 = max(int(min(zs)) - pad, lz0), min(int(max(zs)) + pad, lz1)
    m = map_panel(after, trim, diff["removed"], diff["added"], x0, x1, z0, z1)
    c = column_panel(col["motes"], m.height)

    gap, pad2, bar = 22, 30, 124
    W = pad2 * 2 + m.width + gap + c.width
    card = Image.new("RGB", (W, pad2 + m.height + bar), BG)
    card.paste(m, (pad2, pad2)); card.paste(c, (pad2 + m.width + gap, pad2))
    d = ImageDraw.Draw(card)
    f = font(False, 12)
    d.text((pad2 + 4, pad2 + m.height + 7), "where it walked  ·  lifted  ·  set back down", font=f, fill=DIM)
    d.text((pad2 + m.width + gap + 4, pad2 + m.height + 7), "what the client draws", font=f, fill=DIM)
    y = pad2 + m.height + 32
    d.line([(pad2, y), (W - pad2, y)], fill=(40, 40, 48))
    d.text((pad2, y + 14), "The Wandering Column", font=font(True, 21), fill=INK)
    d.text((pad2, y + 42),
           f"There is no funnel model - a tornado is only the dirt caught in it, so that is what is drawn: "
           f"ninety blocks on a helix seeded from the entity id.", font=font(False, 13), fill=DIM)
    d.text((pad2, y + 60),
           f"In {len(trim)} sampled seconds it walked {walked:.0f} blocks, lifted {len(diff['removed'])} and set "
           f"{len(diff['added'])} back down. It takes only what is loose and under open sky.",
           font=font(False, 13), fill=DIM)
    out = f"{SP}/bts-tornado.png"
    card.save(out)
    print(out, card.size, "| walked", round(walked), "| lifted", len(diff["removed"]), "| dropped", len(diff["added"]))

main()
