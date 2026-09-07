"""The Named Lands, behind the scenes: one land at its true size, and its neighbours.

The map is cell 0,0 read straight out of the region files - 384 by 384 blocks, the whole of one
land. The names in the grid are the mod's own output, logged by /wakingworld lands name at each
cell's middle; the kind of country at that middle is what picks the words.
"""
import sys, os, json, pickle
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from PIL import Image, ImageDraw, ImageFont
from region_iso import P, load_box

SP = "/tmp/claude-0/-home-claude/f973fb44-8199-5bc3-ae5b-ee262cc95fe4/scratchpad"
INK, DIM, BG, GOLD = (236, 230, 222), (146, 142, 138), (14, 14, 18), (226, 178, 74)
SIZE = 384

def font(bold, size):
    n = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:    return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{n}", size)
    except Exception: return ImageFont.load_default()

def land_map(px=2):
    grid = load_box(0, 0, SIZE - 1, SIZE - 1, 50, 150)
    top = {}
    for (x, y, z), n in grid.items():
        k = (x, z)
        if k not in top or y > top[k][0]: top[k] = (y, n)
    hs = [v[0] for v in top.values()]; lo, hi = min(hs), max(hs)
    W = H = SIZE * px
    img = Image.new("RGB", (W, H), (10, 10, 13))
    d = ImageDraw.Draw(img)
    for (x, z), (y, n) in top.items():
        col = P.get(n) or ((0x6f, 0x6f, 0x74) if ("stone" in n or "slate" in n) else (0x5a, 0x5a, 0x60))
        f = 0.55 + 0.60 * ((y - lo) / max(1, hi - lo))
        c = tuple(max(0, min(255, int(v * f))) for v in col)
        d.rectangle([x * px, z * px, x * px + px - 1, z * px + px - 1], fill=c)
    # the middle - the one column that decides the name
    m = SIZE // 2 * px
    d.line([(m - 11, m), (m + 11, m)], fill=GOLD, width=2)
    d.line([(m, m - 11), (m, m + 11)], fill=GOLD, width=2)
    d.ellipse([m - 17, m - 17, m + 17, m + 17], outline=GOLD, width=1)
    f = font(False, 12)
    t = "the middle, which decides the name"
    d.rectangle([m + 22, m - 8, m + 30 + d.textlength(t, font=f), m + 10], fill=(12, 12, 15))
    d.text((m + 26, m - 6), t, font=f, fill=GOLD)
    scrim = Image.new("L", (W, H), 0)
    ds = ImageDraw.Draw(scrim)
    for i in range(96):
        ds.rectangle([0, H - 96 + i, W, H - 96 + i], fill=int(200 * (i / 96) ** 1.6))
    img = Image.composite(Image.new("RGB", (W, H), (9, 9, 12)), img, scrim)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], outline=GOLD, width=2)
    return img

def grid_panel(cells, W, H):
    img = Image.new("RGB", (W, H), (10, 10, 13))
    d = ImageDraw.Draw(img)
    by = {(c["cx"], c["cz"]): c for c in cells}
    cw, ch = W // 3, (H - 4) // 3
    fb, fs = font(True, 12), font(False, 10)
    for i, cx in enumerate((-1, 0, 1)):
        for j, cz in enumerate((-1, 0, 1)):
            x, y = i * cw, j * ch
            here = (cx, cz) == (0, 0)
            d.rectangle([x, y, x + cw - 2, y + ch - 2],
                        fill=(26, 24, 20) if here else (17, 17, 21),
                        outline=GOLD if here else (48, 46, 54), width=2 if here else 1)
            c = by.get((cx, cz))
            d.text((x + 8, y + 7), f"{cx},{cz}", font=fs, fill=(110, 106, 102))
            if not c:
                d.text((x + 8, y + ch // 2 - 6), "not walked", font=fs, fill=(80, 78, 84)); continue
            words, line, lines = c["name"].split(), "", []
            for w in words:
                t = (line + " " + w).strip()
                if d.textlength(t, font=fb) > cw - 18 and line: lines.append(line); line = w
                else: line = t
            lines.append(line)
            ty = y + ch // 2 - 8 * len(lines)
            for l in lines:
                d.text((x + 8, ty), l, font=fb, fill=GOLD if here else INK); ty += 15
    return img

def main():
    cells = json.load(open(f"{SP}/lands.json"))
    home = next(c for c in cells if (c["cx"], c["cz"]) == (0, 0))
    m = land_map()
    gw = 430
    g = grid_panel(cells, gw, m.height - 132)

    pad, bar = 30, 116
    W = pad * 2 + m.width + 26 + gw
    card = Image.new("RGB", (W, pad + m.height + bar), BG)
    card.paste(m, (pad, pad)); card.paste(g, (pad + m.width + 26, pad))
    d = ImageDraw.Draw(card)
    gx, gy = pad + m.width + 26, pad + g.height + 16
    d.text((gx, gy), "NINE CELLS, NAMED BY THE MOD", font=font(True, 12), fill=(150, 128, 84)); gy += 22
    for line in ("Every square is 384 blocks on a side.",
                 "The kind of country in the middle picks the",
                 "words; a name is written once and then kept.",
                 "With a Gemini key the server writes them instead."):
        d.text((gx, gy), line, font=font(False, 12), fill=DIM); gy += 17

    d.text((pad + 12, pad + m.height - 62), home["name"], font=font(True, 26), fill=GOLD)
    d.text((pad + 12, pad + m.height - 28), home["lore"], font=font(False, 13), fill=(206, 198, 188))

    yb = pad + m.height + 16
    d.line([(pad, yb), (W - pad, yb)], fill=(40, 40, 48))
    d.text((pad, yb + 14), "The Named Lands", font=font(True, 21), fill=INK)
    d.text((pad, yb + 42),
           "One land, at its true size: 384 by 384 blocks, read out of the region files. Walk over that line and "
           "the country you enter has a name,", font=font(False, 13), fill=DIM)
    d.text((pad, yb + 60),
           "shown once and never again. These nine came from the templates; with a Gemini key on the server the "
           "model writes them instead.", font=font(False, 13), fill=DIM)
    out = f"{SP}/bts-lands.png"
    card.save(out)
    print(out, card.size, "|", home["name"])

main()
