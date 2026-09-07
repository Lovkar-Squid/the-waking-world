"""The Blood Moon, behind the scenes: where the host actually stood.

Every marker is a real entity read back out of world/entities/*.mca after a siege on the test
server - its type and its position, not a diagram. The two rings are the band the spawner is
allowed to use; the sky swatch is the colour RedSky fades the fog to on the client.
"""
import sys, os, json, math, pickle
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont
from region_iso import P

SP = "/tmp/claude-0/-home-claude/f973fb44-8199-5bc3-ae5b-ee262cc95fe4/scratchpad"
INK, DIM, BG = (236, 230, 222), (146, 142, 138), (14, 14, 18)
CX, CZ, R = 400, 200, 62
SKY = (107, 11, 14)                                   # RedSky: 0.42, 0.045, 0.055

KIND = {
    "zombie":   ("Zombie",   (0x54, 0x86, 0x44)),
    "husk":     ("Husk",     (0xc0, 0xa8, 0x70)),
    "skeleton": ("Skeleton", (0xe0, 0xdd, 0xd2)),
    "stray":    ("Stray",    (0xa6, 0xcc, 0xdd)),
    "spider":   ("Spider",   (0x8a, 0x40, 0x40)),
    "creeper":  ("Creeper",  (0x54, 0xd2, 0x54)),
    "enderman": ("Enderman", (0xb8, 0x78, 0xdc)),
}

def font(bold, size):
    n = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:    return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{n}", size)
    except Exception: return ImageFont.load_default()

def map_panel(grid, host, px=5):
    x0, x1, z0, z1 = CX - R, CX + R, CZ - R, CZ + R
    top = {}
    for (x, y, z), n in grid.items():
        if not (x0 <= x <= x1 and z0 <= z <= z1): continue
        k = (x, z)
        if k not in top or y > top[k][0]: top[k] = (y, n)
    hs = [v[0] for v in top.values()]; lo, hi = min(hs), max(hs)
    W = H = (x1 - x0 + 1) * px
    img = Image.new("RGB", (W, H), (10, 9, 11))
    d = ImageDraw.Draw(img)
    for (x, z), (y, n) in top.items():
        col = P.get(n) or ((0x6f, 0x6f, 0x74) if ("stone" in n or "slate" in n) else (0x5a, 0x5a, 0x60))
        f = 0.30 + 0.34 * ((y - lo) / max(1, hi - lo))          # night: everything dim
        c = (min(255, int(col[0] * f * 1.30 + 12)), int(col[1] * f * 0.80), int(col[2] * f * 0.84))
        sx, sz = (x - x0) * px, (z - z0) * px
        d.rectangle([sx, sz, sx + px - 1, sz + px - 1], fill=c)

    def at(wx, wz): return (wx - x0) * px, (wz - z0) * px
    ccx, ccz = at(CX, CZ)
    for r, lab in ((20, "20"), (46, "46")):
        rr = r * px
        d.ellipse([ccx - rr, ccz - rr, ccx + rr, ccz + rr], outline=(96, 40, 44), width=1)
    d.text((ccx + 20 * px + 4, ccz - 14), "20", font=font(False, 11), fill=(150, 82, 84))
    d.text((ccx + 46 * px + 4, ccz - 14), "46 blocks", font=font(False, 11), fill=(150, 82, 84))

    glow = Image.new("RGB", (W, H), (0, 0, 0)); dg = ImageDraw.Draw(glow)
    for e in host:
        name = e["id"].replace("minecraft:", "")
        _, c = KIND.get(name, ("?", (200, 200, 200)))
        sx, sz = at(e["pos"][0], e["pos"][2])
        d.ellipse([sx - 4, sz - 4, sx + 4, sz + 4], fill=c, outline=(12, 10, 12))
        dg.ellipse([sx - 5, sz - 5, sx + 5, sz + 5], fill=tuple(int(v * 0.55) for v in c))
    img = ImageChops.screen(img, glow.filter(ImageFilter.GaussianBlur(7)))
    d = ImageDraw.Draw(img)
    d.line([(ccx - 7, ccz), (ccx + 7, ccz)], fill=(255, 226, 210), width=2)
    d.line([(ccx, ccz - 7), (ccx, ccz + 7)], fill=(255, 226, 210), width=2)
    f = font(False, 12)
    t = "the player"
    d.rectangle([ccx + 8, ccz + 6, ccx + 14 + d.textlength(t, font=f), ccz + 24], fill=(12, 10, 12))
    d.text((ccx + 12, ccz + 8), t, font=f, fill=(255, 226, 210))
    d.rectangle([0, 0, W - 1, H - 1], outline=(52, 40, 42))
    return img

def main():
    grid = pickle.load(open(f"{SP}/t4_after.pkl", "rb"))
    host = json.load(open(f"{SP}/bloodmoon_host.json"))
    m = map_panel(grid, host)

    counts = {}
    for e in host:
        k = e["id"].replace("minecraft:", "")
        counts[k] = counts.get(k, 0) + 1
    order = sorted(counts.items(), key=lambda kv: -kv[1])
    ds = [math.dist((e["pos"][0], e["pos"][2]), (CX, CZ)) for e in host]

    legend_w, pad, bar = 250, 30, 108
    W = pad * 2 + m.width + 24 + legend_w
    card = Image.new("RGB", (W, pad + m.height + bar), BG)
    card.paste(m, (pad, pad))
    d = ImageDraw.Draw(card)
    lx = pad + m.width + 24
    d.text((lx, pad + 1), "WHAT ROSE, COUNTED", font=font(True, 12), fill=(150, 82, 84))
    y = pad + 26
    for k, n in order:
        label, c = KIND.get(k, (k, (200, 200, 200)))
        d.ellipse([lx, y + 3, lx + 9, y + 12], fill=c)
        d.text((lx + 18, y), label, font=font(False, 13), fill=INK)
        d.text((lx + 150, y), str(n), font=font(True, 13), fill=DIM)
        y += 22
    y += 10
    d.line([(lx, y), (lx + legend_w - 14, y)], fill=(40, 40, 48)); y += 14
    d.text((lx, y), "THE SKY IT FADES TO", font=font(True, 12), fill=(150, 82, 84)); y += 20
    d.rectangle([lx, y, lx + 34, y + 20], fill=SKY, outline=(60, 46, 48))
    d.text((lx + 44, y + 3), "#%02X%02X%02X   over 4 s" % SKY, font=font(False, 12), fill=DIM)
    y += 34
    d.text((lx, y), "EVERY ONE OF THEM CARRIES", font=font(True, 12), fill=(150, 82, 84)); y += 20
    for line in ("+15 % movement speed", "Fire Resistance - dawn will not burn them",
                 "Strength, if it is a monster"):
        d.text((lx, y), "· " + line, font=font(False, 12), fill=DIM); y += 17

    yb = pad + m.height + 16
    d.line([(pad, yb), (W - pad, yb)], fill=(40, 40, 48))
    d.text((pad, yb + 14), "The Blood Moon", font=font(True, 21), fill=INK)
    d.text((pad, yb + 42),
           f"One siege, read back out of the save: {len(host)} monsters, each one placed on ground that was "
           f"genuinely dark and had room to stand.", font=font(False, 13), fill=DIM)
    d.text((pad, yb + 60),
           f"They land between {min(ds):.0f} and {max(ds):.0f} blocks out - close enough to find you, far "
           f"enough that none of them opens the fight standing on your head.", font=font(False, 13), fill=DIM)
    out = f"{SP}/bts-bloodmoon.png"
    card.save(out)
    print(out, card.size, "| host", len(host), "| ring", round(min(ds)), round(max(ds)))

main()
