"""GUI textures: the Waker's Almanac (an open leather book with tabs and page arrows) and the Dead
Letter parchment (torn edges, water stain, wax seal, compass rose).
python3 tools/textures/gui.py -> resources/assets/wakingworld/textures/gui/{almanac,letter}.png"""
from PIL import Image, ImageDraw
import os, math, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "gui")
os.makedirs(OUT, exist_ok=True)

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)

def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)

LEATHER = (44, 86, 60, 255); LEATHER_L = (66, 116, 82, 255); LEATHER_D = (22, 48, 32, 255); RIM = (10, 22, 14, 255)
BRASS = (200, 164, 84, 255); BRASS_L = (246, 220, 140, 255); BRASS_D = (120, 92, 36, 255)
PAPER = (236, 226, 198, 255); PAPER_D = (206, 192, 156, 255); PAPER_L = (246, 240, 220, 255); PAPER_EDGE = (176, 160, 122, 255)
INK = (60, 42, 28, 255)

def rounded_rect(dr, box, r, fill):
    dr.rounded_rectangle(box, radius=r, fill=fill)

def almanac():
    W, H = 512, 256
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    rnd = random.Random(1)
    # ---- the open book: 292 x 192 at (0, 0) ----
    BW, BH = 292, 192
    rounded_rect(dr, (0, 0, BW - 1, BH - 1), 7, RIM)
    rounded_rect(dr, (1, 1, BW - 2, BH - 2), 6, LEATHER_D)
    rounded_rect(dr, (2, 2, BW - 3, BH - 3), 6, LEATHER)
    # leather grain
    for y in range(3, BH - 3):
        for x in range(3, BW - 3):
            if rnd.random() < 0.10:
                im.putpixel((x, y), LEATHER_L if rnd.random() < 0.5 else shade(LEATHER, 0.9))
    # the spine: a darker band down the middle with a lighter ridge
    for x in range(141, 151):
        for y in range(2, BH - 2):
            im.putpixel((x, y), LEATHER_D if x in (141, 150) else shade(LEATHER, 0.78 + 0.04 * abs(x - 145.5)))
    # pages: left 10..140, right 152..282, y 8..184; a stack of page edges on the outside
    def page(x0, x1, outer_left):
        for y in range(8, 185):
            for x in range(x0, x1 + 1):
                c = PAPER
                # subtle mottling
                if rnd.random() < 0.05: c = shade(PAPER, 0.96)
                # the inner edge towards the spine darkens (the curve of the page)
                dist_spine = (x - x0) if not outer_left else (x1 - x)
                if dist_spine < 6: c = mix(c, PAPER_D, (6 - dist_spine) / 6 * 0.6)
                im.putpixel((x, y), c)
        # page-stack lines on the outer edge and bottom
        for i in range(4):
            xx = (x0 - 1 - i) if outer_left else (x1 + 1 + i)
            for y in range(9 + i, 185 - i):
                im.putpixel((xx, y), PAPER_EDGE if i % 2 == 0 else PAPER_D)
            for x in range(x0 - i if outer_left else x0, (x1 + 1) if outer_left else x1 + 1 + i):
                im.putpixel((x, 185 + i), PAPER_EDGE if i % 2 == 0 else PAPER_D)
        # outline
        dr.rectangle((x0 - 1 if not outer_left else x0 - 5, 7, x1 + 5 if not outer_left else x1 + 1, 189), outline=RIM)
    page(14, 138, True)
    page(154, 278, False)
    # brass corners on the cover
    for (cx, cy, sx, sy) in [(4, 4, 1, 1), (BW - 5, 4, -1, 1), (4, BH - 5, 1, -1), (BW - 5, BH - 5, -1, -1)]:
        for i in range(7):
            im.putpixel((cx + sx * i, cy), BRASS); im.putpixel((cx, cy + sy * i), BRASS)
            im.putpixel((cx + sx * i, cy + sy), BRASS_D); im.putpixel((cx + sx, cy + sy * i), BRASS_D)
        im.putpixel((cx, cy), BRASS_L)
    # a faint header rule on each page
    for x in range(30, 123): im.putpixel((x, 24), PAPER_D if x % 2 else PAPER_EDGE)
    for x in range(170, 263): im.putpixel((x, 24), PAPER_D if x % 2 else PAPER_EDGE)
    # ---- tabs at (300, 0): unselected 28x24, selected 32x24 ----
    def tab(x0, y0, w, selected):
        fill = PAPER if selected else LEATHER_L
        edge = RIM
        dr.rounded_rectangle((x0, y0, x0 + w - 1, y0 + 23), radius=6, fill=edge)
        dr.rounded_rectangle((x0 + 1, y0 + 1, x0 + w - 1, y0 + 22), radius=5, fill=fill)
        # square off the right side (it meets the book)
        dr.rectangle((x0 + w - 6, y0 + 1, x0 + w - 1, y0 + 22), fill=fill)
        for y in range(y0 + 2, y0 + 22):
            for x in range(x0 + 2, x0 + w - 1):
                if rnd.random() < 0.08: im.putpixel((x, y), shade(fill, 0.93))
        if not selected:
            for y in range(y0 + 1, y0 + 23): im.putpixel((x0 + w - 1, y), LEATHER_D)
    tab(300, 0, 28, False)
    tab(300, 26, 32, True)
    # ---- page arrows at (300, 60): left normal, left hover, right normal, right hover; 18x10 each ----
    def arrow(x0, y0, right, hover):
        c = (196, 120, 40, 255) if hover else INK
        c2 = (255, 190, 90, 255) if hover else (120, 92, 60, 255)
        # the head: a triangle whose tip is on the side the arrow points to, then the shaft behind it
        for i in range(9):
            x = x0 + (17 - i if right else i)
            half = min(i, 4)
            for y in range(y0 + 5 - half, y0 + 5 + half + 1):
                im.putpixel((x, y), c if not (y == y0 + 5 and i > 0) else c2)
        for x in range(x0 + (0 if right else 9), x0 + (9 if right else 18)):
            im.putpixel((x, y0 + 4), c); im.putpixel((x, y0 + 5), c2); im.putpixel((x, y0 + 6), c)
    arrow(300, 60, False, False); arrow(320, 60, False, True)
    arrow(300, 72, True, False); arrow(320, 72, True, True)
    # ---- a bookmark ribbon at (300, 90): 10 x 34 ----
    for y in range(90, 124):
        for x in range(300, 310):
            if y > 118 and abs(x - 304.5) < (y - 118) * 1.1 - 0.5: continue  # the notch at the end
            im.putpixel((x, y), (150, 36, 40, 255) if x not in (300, 309) else (96, 20, 24, 255))
    # ---- a header ornament at (300, 130): 96 x 7 ----
    for x in range(300, 396):
        t = abs(x - 348) / 48
        if t < 0.98:
            im.putpixel((x, 133), mix(BRASS_D, PAPER_D, t))
        if x % 12 == 0 and 306 <= x <= 390:
            for (dx, dy) in [(0, 132), (0, 134), (-1, 133), (1, 133)]: im.putpixel((x + dx, dy), BRASS_D)
    im.putpixel((348, 131), BRASS); im.putpixel((348, 135), BRASS); im.putpixel((347, 133), BRASS); im.putpixel((349, 133), BRASS)
    # ---- small icons for the kinds: 6 colour dots 8x8 at (300, 150) ----
    for i, col in enumerate([(120, 118, 122), (116, 86, 56), (216, 190, 128), (160, 205, 236), (78, 142, 132), (90, 128, 66)]):
        x0 = 300 + i * 10
        dr.ellipse((x0, 150, x0 + 7, 157), fill=(col[0], col[1], col[2], 255), outline=RIM)
        im.putpixel((x0 + 2, 152), (255, 255, 255, 180))
    im.save(os.path.join(OUT, "almanac.png"))

def letter():
    W, H = 256, 256
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    rnd = random.Random(2)
    PW, PH = 176, 232
    paper = (228, 216, 178, 255); dark = (198, 182, 140, 255); stain = (202, 178, 126, 255); edge = (120, 100, 66, 255)
    # torn edge profile: jagged offsets on every side
    def jag(n, amp):
        out = []; v = 0
        for i in range(n):
            v += rnd.uniform(-1, 1) * amp
            v = max(-2.5, min(2.5, v * 0.7))
            out.append(v)
        return out
    top, bot, lef, rig = jag(PW, 1.2), jag(PW, 1.2), jag(PH, 1.2), jag(PH, 1.2)
    for y in range(PH):
        for x in range(PW):
            if x < 3 + lef[y] or x > PW - 4 + rig[y] or y < 3 + top[x] or y > PH - 4 + bot[x]:
                continue
            c = paper
            if rnd.random() < 0.06: c = shade(paper, 0.95)
            # water stains
            for (sx, sy, sr) in [(130, 190, 26), (40, 60, 18), (150, 40, 14)]:
                d = math.hypot(x - sx, y - sy)
                if d < sr: c = mix(c, stain, 0.45 * (1 - d / sr) + 0.15)
                elif d < sr + 2: c = mix(c, shade(stain, 0.85), 0.5)
            # aged darker border
            e = min(x - lef[y], PW - x + rig[y], y - top[x], PH - y + bot[x])
            if e < 10: c = mix(c, dark, (10 - e) / 10 * 0.55)
            im.putpixel((x, y), c)
    # the torn rim: darker outline where paper meets nothing
    for y in range(PH):
        for x in range(PW):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < PW and 0 <= y + dy < PH and im.getpixel((x + dx, y + dy))[3] > 0:
                        im.putpixel((x, y), edge); break
    # fold creases: one horizontal, one vertical, faint
    for x in range(6, PW - 6):
        if im.getpixel((x, 78))[3]: im.putpixel((x, 78), mix(im.getpixel((x, 78)), dark, 0.5))
        if im.getpixel((x, 156))[3]: im.putpixel((x, 156), mix(im.getpixel((x, 156)), dark, 0.5))
    for y in range(6, PH - 6):
        if im.getpixel((88, y))[3]: im.putpixel((88, y), mix(im.getpixel((88, y)), dark, 0.35))
    # ---- the wax seal at (200, 0): 26 x 26 ----
    wax = (156, 32, 34, 255); wax_l = (216, 84, 72, 255); wax_d = (92, 14, 18, 255)
    for y in range(26):
        for x in range(26):
            d = math.hypot(x + 0.5 - 13, y + 0.5 - 13)
            if d <= 12.2:
                lumpy = 11.3 + 0.9 * math.sin(math.atan2(y - 13, x - 13) * 5)
                if d > lumpy: c = wax_d
                else: c = wax_l if (x + y) < 20 else wax
                im.putpixel((200 + x, y), c)
    for (x, y) in [(11, 9), (15, 9), (10, 10), (11, 10), (12, 10), (13, 10), (14, 10), (15, 10), (16, 10), (10, 11), (16, 11), (11, 12), (15, 12), (12, 13), (14, 13), (13, 14), (13, 15)]:
        im.putpixel((200 + x, y), wax_d)
    im.putpixel((211, 9), (250, 170, 160, 255))
    # ---- the compass rose at (200, 30): 34 x 34 ----
    cx, cy = 217, 47
    for y in range(30, 64):
        for x in range(200, 234):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 16.5:
                c = (214, 200, 160, 255) if d < 14.5 else (110, 88, 56, 255)
                if d < 14.5 and int(d) % 4 == 0 and d > 6: c = (200, 186, 146, 255)
                im.putpixel((x, y), c)
    for i in range(8):
        a = math.radians(i * 45)
        r = 13 if i % 2 == 0 else 9
        for rr in range(3, r):
            im.putpixel((int(cx + math.cos(a) * rr), int(cy + math.sin(a) * rr)), (150, 128, 90, 255) if i % 2 else (110, 88, 56, 255))
    # N marker
    for (x, y) in [(216, 32), (216, 33), (216, 34), (216, 35), (217, 33), (218, 34), (219, 32), (219, 33), (219, 34), (219, 35)]:
        im.putpixel((x, y), (120, 30, 30, 255))
    im.putpixel((217, 47), (60, 42, 28, 255))
    # ---- page arrows at (200, 70): left, right; 12 x 8; and hover variants below ----
    def small_arrow(x0, y0, right, hover):
        c = (196, 120, 40, 255) if hover else (70, 50, 30, 255)
        for i in range(6):
            x = x0 + (11 - i if right else i)
            half = min(i, 3)
            for y in range(y0 + 4 - half, y0 + 4 + half + 1): im.putpixel((x, y), c)
        for x in range(x0 + (0 if right else 6), x0 + (6 if right else 12)):
            im.putpixel((x, y0 + 3), c); im.putpixel((x, y0 + 4), c); im.putpixel((x, y0 + 5), c)
    small_arrow(200, 70, False, False); small_arrow(214, 70, True, False)
    small_arrow(200, 80, False, True); small_arrow(214, 80, True, True)
    # ---- a needle for the compass at (240, 30): 3 x 14, red north half, dark south half (drawn rotated in code) ----
    for y in range(30, 44):
        for x in range(240, 243):
            im.putpixel((x, y), (170, 40, 40, 255) if y < 37 else (60, 44, 30, 255))
    im.save(os.path.join(OUT, "letter.png"))

def king():
    """The audience with the king: a stone-and-gold frame, a curtained alcove for the king himself, a
    parchment for his words, and a strip of scroll buttons for the things one may ask about."""
    W, H = 256, 256
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    rnd = random.Random(3)
    PW, PH = 248, 190
    STONE = (58, 60, 70, 255); STONE_L = (78, 80, 92, 255); STONE_D = (36, 38, 46, 255)
    GOLD = (208, 170, 70, 255); GOLD_D = (140, 108, 34, 255); GOLD_L = (244, 216, 130, 255)
    # the frame
    rounded_rect(dr, (0, 0, PW - 1, PH - 1), 4, STONE_D)
    rounded_rect(dr, (1, 1, PW - 2, PH - 2), 4, STONE)
    for y in range(2, PH - 2):
        for x in range(2, PW - 2):
            if rnd.random() < 0.12: im.putpixel((x, y), STONE_L if rnd.random() < 0.5 else shade(STONE, 0.9))
    # mortar lines in the frame
    for y in range(4, PH - 4, 6):
        for x in range(2, PW - 2):
            if (x + (y // 6) * 7) % 14 != 0: im.putpixel((x, y), shade(STONE, 0.8))
    for y in range(2, PH - 2):
        for x in range(2, PW - 2):
            if (x + (y // 6) * 7) % 14 == 0 and (y % 6) != 4: im.putpixel((x, y), shade(STONE, 0.8))
    # gold trim
    dr.rectangle((4, 4, PW - 5, PH - 5), outline=GOLD_D)
    dr.rectangle((5, 5, PW - 6, PH - 6), outline=GOLD)
    for x in range(5, PW - 6, 8):
        im.putpixel((x, 5), GOLD_L); im.putpixel((x, PH - 6), GOLD_L)
    # the alcove for the king: (8, 8) .. (77, 149): a red curtain with folds, a stone floor
    for y in range(8, 150):
        for x in range(8, 78):
            if y >= 140:
                c = shade(STONE, 0.9 + 0.1 * ((x + y) % 2)); c = mix(c, STONE_D, (y - 140) / 12)
            else:
                fold = 0.72 + 0.28 * abs(math.sin((x - 8) * math.pi / 10))
                c = shade((118, 26, 34, 255), fold * (0.75 + 0.25 * (1 - (y - 8) / 132)))
                if rnd.random() < 0.05: c = shade(c, 0.9)
            im.putpixel((x, y), c)
    dr.rectangle((8, 8, 77, 149), outline=(24, 20, 24, 255))
    # a gold rail over the curtain
    dr.rectangle((8, 10, 77, 11), fill=GOLD_D); dr.line((8, 10, 77, 10), fill=GOLD)
    # the throne the king sits on (he is drawn over it, seated: his hips come to about y 80 in the alcove)
    RED = (150, 24, 34, 255); GEM = (200, 40, 60, 255)
    dr.rectangle((15, 88, 71, 97), fill=STONE)                            # the step
    dr.rectangle((15, 88, 71, 89), fill=STONE_L)
    dr.rectangle((25, 24, 61, 82), fill=GOLD_D)                           # the back
    dr.rectangle((27, 26, 59, 80), fill=RED)
    for y in range(30, 78, 8):
        for x in range(30, 58, 8): im.putpixel((x, y), GOLD)             # tufts
    for x in range(27, 60, 6): dr.rectangle((x, 19, x + 2, 24), fill=GOLD)  # the crest's points
    dr.rectangle((25, 21, 61, 24), fill=GOLD)
    dr.rectangle((41, 20, 45, 23), fill=GEM)
    dr.rectangle((21, 81, 65, 88), fill=GOLD)                             # the seat
    dr.rectangle((23, 81, 63, 84), fill=RED)
    dr.rectangle((19, 68, 25, 84), fill=GOLD); dr.rectangle((61, 68, 67, 84), fill=GOLD)  # armrests
    dr.rectangle((19, 68, 25, 69), fill=GOLD_L); dr.rectangle((61, 68, 67, 69), fill=GOLD_L)
    dr.rectangle((24, 24, 25, 82), fill=GOLD); dr.rectangle((61, 24, 62, 82), fill=GOLD)
    # the parchment for his words: (86, 8) .. (239, 149)
    for y in range(8, 150):
        for x in range(86, 240):
            c = PAPER
            if rnd.random() < 0.05: c = shade(PAPER, 0.96)
            e = min(x - 86, 239 - x, y - 8, 149 - y)
            if e < 6: c = mix(c, PAPER_D, (6 - e) / 6 * 0.5)
            im.putpixel((x, y), c)
    dr.rectangle((86, 8, 239, 149), outline=PAPER_EDGE)
    # a rule under the title area, ink
    dr.line((96, 32, 229, 32), fill=(120, 96, 64, 255))
    # the button strip: (8, 156) .. (239, 187) darker stone
    for y in range(156, 188):
        for x in range(8, 240):
            c = shade(STONE, 0.72)
            if rnd.random() < 0.1: c = shade(c, 0.9)
            im.putpixel((x, y), c)
    dr.rectangle((8, 156, 239, 187), outline=STONE_D)
    # ---- buttons at (0, 200): normal, hover (214), selected (228); 56 x 14 ----
    def button(y0, paper, edge, gilt):
        for y in range(14):
            for x in range(56):
                c = paper
                if rnd.random() < 0.06: c = shade(paper, 0.95)
                if y in (0, 13) or x in (0, 55): c = edge
                im.putpixel((x, y0 + y), c)
        # rolled ends
        for y in range(1, 13):
            im.putpixel((1, y0 + y), shade(paper, 0.8)); im.putpixel((54, y0 + y), shade(paper, 0.8))
            im.putpixel((2, y0 + y), shade(paper, 1.04)); im.putpixel((53, y0 + y), shade(paper, 1.04))
        if gilt is not None:
            dr.rectangle((0, y0, 55, y0 + 13), outline=gilt)
    button(200, PAPER, PAPER_EDGE, None)
    button(214, PAPER_L, GOLD_D, GOLD)
    button(228, PAPER_D, INK, None)
    # ---- page arrows at (60, 200): left, right, and hover below at (60, 210) ----
    def small_arrow(x0, y0, right, hover):
        c = (196, 120, 40, 255) if hover else (70, 50, 30, 255)
        for i in range(6):
            x = x0 + (11 - i if right else i)
            half = min(i, 3)
            for y in range(y0 + 4 - half, y0 + 4 + half + 1): im.putpixel((x, y), c)
        for x in range(x0 + (0 if right else 6), x0 + (6 if right else 12)):
            im.putpixel((x, y0 + 3), c); im.putpixel((x, y0 + 4), c); im.putpixel((x, y0 + 5), c)
    small_arrow(60, 200, False, False); small_arrow(74, 200, True, False)
    small_arrow(60, 210, False, True); small_arrow(74, 210, True, True)
    # ---- a small crown at (90, 200): 12 x 9 ----
    for (x, y) in [(0, 2), (0, 3), (0, 4), (0, 5), (0, 6), (0, 7), (0, 8), (11, 2), (11, 3), (11, 4), (11, 5), (11, 6), (11, 7), (11, 8),
                   (3, 1), (3, 2), (3, 3), (8, 1), (8, 2), (8, 3), (5, 0), (6, 0), (5, 1), (6, 1), (5, 2), (6, 2)]:
        im.putpixel((90 + x, 200 + y), GOLD)
    for y in range(4, 9):
        for x in range(1, 11): im.putpixel((90 + x, 200 + y), GOLD if y != 8 else GOLD_D)
    for (x, y) in [(2, 5), (5, 6), (6, 6), (9, 5)]: im.putpixel((90 + x, 200 + y), (190, 40, 60, 255))
    im.save(os.path.join(OUT, "king.png"))


def trade():
    """The townsfolk's stall: a dark-oak frame with brass nails, the trader's nook under a striped
    awning at the left, a parchment ledger for the offers at the right, a shelf strip below for the
    purse; then the row and button sprites the screen composes the list from."""
    W, H = 256, 256
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    rnd = random.Random(5)
    PW, PH = 248, 200
    OAK = (70, 44, 26, 255); OAK_L = (92, 60, 36, 255); OAK_D = (44, 26, 14, 255)
    # the frame: planks with grain and brass nails
    rounded_rect(dr, (0, 0, PW - 1, PH - 1), 4, OAK_D)
    rounded_rect(dr, (1, 1, PW - 2, PH - 2), 4, OAK)
    for y in range(2, PH - 2):
        for x in range(2, PW - 2):
            g = 0.9 + 0.12 * math.sin((y * 0.9 + x * 0.05) + math.sin(x * 0.11) * 1.5)
            c = shade(OAK, g)
            if rnd.random() < 0.04: c = shade(c, 0.85)
            im.putpixel((x, y), c)
    for x in range(3, PW - 3, 26):
        for y in (3, PH - 4):
            im.putpixel((x, y), BRASS); im.putpixel((x + 1, y), BRASS_D)
    for y in range(3, PH - 3, 26):
        for x in (3, PW - 4):
            im.putpixel((x, y), BRASS); im.putpixel((x, y + 1), BRASS_D)
    # the trader's nook (8, 8) .. (77, 149): plank back wall, a striped awning, a counter
    for y in range(8, 150):
        for x in range(8, 78):
            if y < 22:
                stripe = ((x - 8) // 7) % 2 == 0
                c = (150, 40, 44, 255) if stripe else (226, 214, 190, 255)
                c = shade(c, 0.9 + 0.1 * ((y - 8) % 2))
                if y in (20, 21) and (x - 8) % 7 in (3, 4): c = (0, 0, 0, 0) if y == 21 else c  # scalloped edge
            elif y >= 136:
                c = shade(OAK_L, 0.95 + 0.05 * ((x + y) % 2)); c = mix(c, OAK_D, (y - 136) / 14)
            else:
                c = shade((116, 84, 54, 255), 0.9 + 0.12 * math.sin(x * 0.7 + (y // 9)))
                if (y - 22) % 9 == 0: c = shade(c, 0.7)
            if c[3]: im.putpixel((x, y), c)
    dr.rectangle((8, 8, 77, 149), outline=OAK_D)
    dr.line((8, 135, 77, 135), fill=BRASS_D)
    # the ledger (86, 8) .. (239, 149)
    for y in range(8, 150):
        for x in range(86, 240):
            c = PAPER
            if rnd.random() < 0.05: c = shade(PAPER, 0.96)
            e = min(x - 86, 239 - x, y - 8, 149 - y)
            if e < 6: c = mix(c, PAPER_D, (6 - e) / 6 * 0.5)
            im.putpixel((x, y), c)
    dr.rectangle((86, 8, 239, 149), outline=PAPER_EDGE)
    dr.line((96, 30, 229, 30), fill=(120, 96, 64, 255))
    # the purse shelf (8, 156) .. (239, 191)
    for y in range(156, 192):
        for x in range(8, 240):
            c = shade(OAK, 0.72 + 0.06 * math.sin(x * 0.3))
            if rnd.random() < 0.06: c = shade(c, 0.9)
            im.putpixel((x, y), c)
    dr.rectangle((8, 156, 239, 191), outline=OAK_D)
    dr.line((8, 157, 239, 157), fill=OAK_L)
    # ---- sprites ----
    # an offer row 150 x 20 at (0, 208) normal and (0, 230) hovered
    for i, (y0, tone) in enumerate(((208, 1.0), (230, 1.06))):
        for y in range(20):
            for x in range(150):
                c = shade(PAPER_L if tone > 1 else PAPER, tone if tone > 1 else 0.98)
                if rnd.random() < 0.04: c = shade(c, 0.96)
                if y in (0, 19): c = PAPER_EDGE
                im.putpixel((x, y0 + y), c)
        # the slots: cost at x 3..20, second cost 24..41, result 66..83 - sunken squares; the buy button sits at 106..146
        for sx in (3, 24, 66):
            dr.rectangle((sx, y0 + 1, sx + 17, y0 + 18), fill=shade(PAPER_D, 0.92), outline=PAPER_EDGE)
            dr.line((sx + 1, y0 + 2, sx + 16, y0 + 2), fill=shade(PAPER_D, 0.8)); dr.line((sx + 1, y0 + 2, sx + 1, y0 + 17), fill=shade(PAPER_D, 0.8))
        # the arrow between
        ax, ay = 46, y0 + 10
        dr.line((ax, ay, ax + 12, ay), fill=INK, width=2)
        dr.polygon([(ax + 12, ay - 4), (ax + 17, ay), (ax + 12, ay + 4)], fill=INK)
    # the buy button 40 x 14 at (160, 208) normal, (160, 224) hover, (160, 240) disabled
    def button(y0, face, edge, text_shadow):
        for y in range(14):
            for x in range(40):
                c = face
                if rnd.random() < 0.06: c = shade(face, 0.94)
                if y == 0 or x == 0: c = shade(edge, 1.25)
                if y == 13 or x == 39: c = edge
                im.putpixel((160 + x, y0 + y), c)
    button(208, BRASS, BRASS_D, True)
    button(224, BRASS_L, BRASS_D, True)
    button(240, (120, 116, 104, 255), (80, 76, 66, 255), False)
    # a little emerald and a stock-out cross for the list
    ex, ey = 210, 208
    dr.polygon([(ex + 4, ey), (ex + 8, ey + 4), (ex + 4, ey + 8), (ex, ey + 4)], fill=(70, 200, 110, 255))
    dr.polygon([(ex + 4, ey + 1), (ex + 6, ey + 4), (ex + 4, ey + 6), (ex + 2, ey + 4)], fill=(150, 240, 180, 255))
    dr.line((222, 208, 230, 216), fill=(170, 40, 40, 255), width=2); dr.line((230, 208, 222, 216), fill=(170, 40, 40, 255), width=2)
    im.save(os.path.join(OUT, "trade.png"))

def bossbar():
    """The colossus boss bar (client/ColossusBossBar.java): a carved stone frame 212x26 with a 200x12 window at
    (6, 7); the fill strip (greyscale runes, tinted by the kind's colour in game) at (0, 32); the empty
    track at (0, 48); core sockets lit / dark / empty seat 11x11 at (0, 64) / (12, 64) / (36, 64); the fill's bright tip 4x12 at
    (24, 64); the Titan's void-eaten ends 24x26 at (0, 80) and (24, 80); a small rune cartouche for the phase
    numeral 22x12 at (48, 80)."""
    W, H = 256, 256
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    rnd = random.Random(31)
    STONE = (112, 108, 100, 255); STONE_L = (150, 146, 136, 255); STONE_D = (74, 70, 64, 255); STONE_X = (44, 42, 38, 255)
    # frame: rounded slab with a bevel, a chiselled groove and rune nicks along the bands
    FW, FH = 212, 26
    rounded_rect(dr, (0, 0, FW - 1, FH - 1), 4, STONE_D)
    rounded_rect(dr, (1, 1, FW - 2, FH - 2), 3, STONE)
    dr.line((3, 1, FW - 4, 1), fill=STONE_L); dr.line((1, 3, 1, FH - 4), fill=STONE_L)
    dr.line((3, FH - 2, FW - 4, FH - 2), fill=STONE_X); dr.line((FW - 2, 3, FW - 2, FH - 4), fill=STONE_X)
    for x in range(2, FW - 2):
        for y in range(2, FH - 2):
            if rnd.random() < 0.10: im.putpixel((x, y), shade(STONE, 0.92 if rnd.random() < 0.5 else 1.06))
    # the window (recess) at (6,7) 200x12 with a dark rim
    dr.rectangle((5, 6, 206, 19), fill=STONE_X)
    dr.rectangle((6, 7, 205, 18), fill=(20, 18, 16, 255))
    # rune nicks on the top and bottom bands
    def nick(x, y):
        k = rnd.randint(0, 3)
        if k == 0: dr.line((x, y, x + 2, y + 2), fill=STONE_X); dr.line((x + 2, y, x, y + 2), fill=STONE_X)
        elif k == 1: dr.line((x, y + 2, x + 1, y), fill=STONE_X); dr.line((x + 1, y, x + 2, y + 2), fill=STONE_X)
        elif k == 2: dr.line((x, y, x, y + 2), fill=STONE_X); dr.line((x, y + 1, x + 2, y + 1), fill=STONE_X)
        else: dr.line((x, y, x + 2, y), fill=STONE_X); dr.line((x + 1, y, x + 1, y + 2), fill=STONE_X)
    for x in range(10, FW - 12, 9):
        nick(x, 2); nick(x + 4, FH - 5)
    # a chiselled boss at each end (a round stud)
    for cx in (3, FW - 4):
        pass
    # fill strip (0,32) 200x12: greyscale glow with rune threads, brighter in the middle rows
    for x in range(200):
        for y in range(12):
            base = 150 + 60 * math.sin(math.pi * (y + 0.5) / 12)
            base += 20 * math.sin(x * 0.35) * math.sin(y * 0.9 + x * 0.1)
            v = clamp(base + rnd.uniform(-8, 8))
            im.putpixel((x, 32 + y), (v, v, v, 255))
    for x in range(3, 200, 7):
        # a rune: a small angular glyph in bright white
        y0 = 32 + rnd.randint(2, 6)
        pts = [(x, y0), (x + 2, y0 - 2), (x + 3, y0 + 1), (x + 1, y0 + 3)]
        pts = pts[:rnd.randint(2, 4)]
        for (a, b) in zip(pts, pts[1:]): dr.line((a[0], a[1], b[0], b[1]), fill=(245, 245, 245, 255))
    # the empty track (0,48) 200x12: dark recess with a faint groove
    for x in range(200):
        for y in range(12):
            v = 26 + (6 if y in (0, 11) else 0) + rnd.randint(-3, 3)
            im.putpixel((x, 48 + y), (clamp(v), clamp(v - 2), clamp(v - 4), 255))
    dr.line((0, 48 + 6, 199, 48 + 6), fill=(40, 36, 34, 255))
    # core sockets: lit (a bright gem in a stone ring) and dark (the ring, cracked)
    def socket(x0, gem):
        dr.ellipse((x0, 64, x0 + 10, 74), fill=STONE_D)
        dr.ellipse((x0 + 1, 65, x0 + 9, 73), fill=STONE)
        if gem == "lit":
            # a white gem (tinted in game), lighter at the top left, a dark seat below it
            dr.ellipse((x0 + 3, 67, x0 + 7, 71), fill=(40, 36, 34, 255))
            dr.ellipse((x0 + 3, 67, x0 + 7, 71), fill=(255, 255, 255, 255))
            im.putpixel((x0 + 6, 70), (200, 200, 200, 255)); im.putpixel((x0 + 4, 68), (255, 255, 255, 255))
        elif gem == "dark":
            dr.ellipse((x0 + 3, 67, x0 + 7, 71), fill=(30, 28, 26, 255))
            dr.line((x0 + 4, 66, x0 + 6, 72), fill=(60, 56, 52, 255))
        else:
            dr.ellipse((x0 + 3, 67, x0 + 7, 71), fill=(40, 36, 34, 255))  # the empty seat
    socket(0, "lit"); socket(12, "dark"); socket(36, "seat")
    # the fill's bright tip 4x12 at (24,64): white fading left to right
    for x in range(4):
        for y in range(12):
            a = clamp(230 - x * 60 - abs(y - 5.5) * 12)
            im.putpixel((24 + x, 64 + y), (255, 255, 255, a))
    # the Titan's void-eaten ends 24x26 at (0,80) and (24,80): black-purple eating into the stone, motes
    for side in range(2):
        ox = side * 24
        for x in range(24):
            for y in range(26):
                d = x if side == 0 else 23 - x
                edge = 10 + 5 * math.sin(y * 0.7) + rnd.uniform(-1.5, 1.5)
                if d < edge:
                    t = d / max(1, edge)
                    a = clamp(255 * (1 - t) ** 1.2)
                    im.putpixel((ox + x, 80 + y), (18 + int(30 * t), 6 + int(10 * t), 30 + int(60 * t), a))
        for i in range(6):
            mx, my = ox + rnd.randint(2, 12) if side == 0 else ox + rnd.randint(11, 21), 80 + rnd.randint(3, 22)
            im.putpixel((mx, my), (200, 150, 255, 220))
    # the phase cartouche 22x12 at (48,80): a darker inset plate
    rounded_rect(dr, (48, 80, 69, 91), 2, STONE_X)
    rounded_rect(dr, (49, 81, 68, 90), 2, (60, 56, 52, 255))
    # a soft radial glow 15x15 at (72,64): white, fading out - tinted by the kind's colour behind a lit core
    for x in range(15):
        for y in range(15):
            d = math.hypot(x - 7, y - 7) / 7.5
            a = clamp(255 * max(0.0, 1 - d) ** 2.2)
            im.putpixel((72 + x, 64 + y), (255, 255, 255, a))

    # ---- the Titan's bar (lower half): the void's own stone ----
    # frame 232x34 at (0,128): a slab of obsidian eaten at the ends, crying-obsidian veins, purpur bevel, end-rod lights; window 220x14 at (6,10)
    TW, TH = 232, 34
    OBS = (18, 12, 30, 255); OBS_L = (44, 30, 68, 255); OBS_D = (8, 4, 14, 255); PURP = (150, 98, 170, 255); PURP_L = (200, 160, 220, 255); VEIN = (196, 90, 255, 255); VEIN_D = (120, 40, 190, 255)
    ty = 128
    rounded_rect(dr, (0, ty, TW - 1, ty + TH - 1), 6, OBS_D)
    rounded_rect(dr, (1, ty + 1, TW - 2, ty + TH - 2), 5, OBS)
    for x in range(2, TW - 2):
        for y in range(2, TH - 2):
            if rnd.random() < 0.12: im.putpixel((x, ty + y), shade(OBS, 0.85 if rnd.random() < 0.5 else 1.25))
    # veins: wandering cracks of light through the stone
    for k in range(26):
        vx, vy = rnd.randint(4, TW - 6), rnd.randint(2, TH - 3)
        for step in range(rnd.randint(6, 18)):
            im.putpixel((max(0, min(TW - 1, vx)), ty + max(0, min(TH - 1, vy))), VEIN if rnd.random() < 0.6 else VEIN_D)
            vx += rnd.choice((-1, 0, 1, 1)); vy += rnd.choice((-1, 0, 1))
    # the purpur bevel along the top and bottom
    dr.line((6, ty + 1, TW - 7, ty + 1), fill=PURP); dr.line((7, ty + 2, TW - 8, ty + 2), fill=PURP_L)
    dr.line((6, ty + TH - 2, TW - 7, ty + TH - 2), fill=PURP_L); dr.line((7, ty + TH - 3, TW - 8, ty + TH - 3), fill=PURP)
    # the window: a dark recess with a violet rim
    dr.rectangle((5, ty + 9, TW - 6, ty + 24), fill=VEIN_D)
    dr.rectangle((6, ty + 10, TW - 7, ty + 23), fill=(6, 2, 12, 255))
    # the void eats the ends: bites of transparency with glowing edges, and motes
    for side in range(2):
        for y in range(TH):
            bite = 3 + 4 * math.sin(y * 0.55 + side) + rnd.uniform(-1.2, 1.2)
            for d in range(int(bite)):
                x = d if side == 0 else TW - 1 - d
                im.putpixel((x, ty + y), (0, 0, 0, 0))
            x = int(bite) if side == 0 else TW - 1 - int(bite)
            if 0 <= x < TW and rnd.random() < 0.7: im.putpixel((x, ty + y), VEIN if rnd.random() < 0.5 else PURP_L)
    for k in range(18):
        mx, my = rnd.randint(2, TW - 3), rnd.randint(1, TH - 2)
        if im.getpixel((mx, ty + my))[3] and not (6 <= mx <= TW - 7 and 10 <= my <= 23): im.putpixel((mx, ty + my), (230, 200, 255, 255))
    # end-rod lights at the four corners of the window's band
    for (lx, ly) in ((9, ty + 5), (TW - 10, ty + 5), (9, ty + TH - 6), (TW - 10, ty + TH - 6)):
        dr.rectangle((lx - 1, ly - 1, lx + 1, ly + 1), fill=(200, 170, 240, 255)); im.putpixel((lx, ly), (255, 255, 255, 255))
    # the Titan's fill 128x14 at (0,164): a seamless strip of void flow - greyscale threads on a dim ground (tinted in game, scrolled)
    for x in range(128):
        for y in range(14):
            u = x / 128 * 2 * math.pi
            v = 95 + 55 * math.sin(3 * u + y * 0.5) * math.sin(u * 2 - y * 0.7) + 35 * math.sin(5 * u + y) + rnd.uniform(-10, 10)
            v += 70 * math.exp(-((y - 6.5) ** 2) / 10)
            vv = clamp(v)
            im.putpixel((x, 164 + y), (vv, vv, vv, 255))
    for k in range(22):
        x, y = rnd.randint(0, 127), rnd.randint(1, 12)
        im.putpixel((x, 164 + y), (250, 250, 250, 255))
        if rnd.random() < 0.5: im.putpixel(((x + 1) % 128, 164 + y), (230, 230, 230, 255))
    # the Titan's empty track 128x14 at (0,180): black with a faint violet thread
    for x in range(128):
        for y in range(14):
            v = 10 + rnd.randint(-2, 2)
            im.putpixel((x, 180 + y), (clamp(v + 2), clamp(v - 4), clamp(v + 8), 255))
        if rnd.random() < 0.15: im.putpixel((x, 180 + 7), (60, 30, 90, 255))
    # the Titan's phase cartouche 30x16 at (128,164): a purpur plate with a violet rim
    rounded_rect(dr, (128, 164, 157, 179), 3, VEIN_D)
    rounded_rect(dr, (129, 165, 156, 178), 3, (30, 16, 48, 255))
    dr.line((131, 166, 154, 166), fill=PURP)
    # the Titan's core socket 13x13 at (160,164): a black ring with a violet rim and an empty seat; the gem 7x7 at (176,164) white (tinted); the cracked gem at (192,164)
    dr.ellipse((160, 164, 172, 176), fill=VEIN_D)
    dr.ellipse((161, 165, 171, 175), fill=OBS)
    dr.ellipse((163, 167, 169, 173), fill=(6, 2, 12, 255))
    dr.ellipse((176, 164, 182, 170), fill=(255, 255, 255, 255)); im.putpixel((178, 166), (255, 255, 255, 255)); im.putpixel((181, 169), (190, 190, 190, 255))
    dr.ellipse((192, 164, 198, 170), fill=(20, 10, 30, 255)); dr.line((193, 163, 197, 171), fill=(80, 50, 110, 255)); dr.line((197, 165, 193, 169), fill=(60, 30, 90, 255))
    # the crown 44x16 at (0,196): a dragon's egg, black with violet speckles, on a purpur bracket, wings of void either side
    rounded_rect(dr, (12, 208, 31, 211), 1, PURP); dr.line((13, 209, 30, 209), fill=PURP_L)
    dr.ellipse((16, 196, 27, 209), fill=(10, 6, 16, 255))
    dr.ellipse((17, 197, 26, 208), fill=(20, 12, 30, 255))
    for k in range(12):
        ex, ey = rnd.randint(18, 25), rnd.randint(198, 207)
        if math.hypot((ex - 21.5) / 4.5, (ey - 202.5) / 5.5) < 1: im.putpixel((ex, ey), (150, 70, 220, 255) if rnd.random() < 0.7 else (220, 170, 255, 255))
    for side in range(2):
        for i in range(10):
            x = 11 - i if side == 0 else 32 + i
            y = 205 - i // 2
            a = clamp(230 - i * 22)
            im.putpixel((x, y), (120, 50, 190, a)); im.putpixel((x, y + 1), (60, 20, 100, a))
    im.save(os.path.join(OUT, "colossus_bar.png"))

almanac(); letter(); king(); trade(); bossbar()
print("gui textures written to", OUT)
