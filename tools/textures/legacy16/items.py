"""Draws the mod's 16x16 item textures - pixel art from code, so there is no art pipeline to keep.
python3 tools/textures/items.py -> resources/assets/wakingworld/textures/item/*.png"""
from PIL import Image, ImageDraw
import os, math, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "item")
os.makedirs(OUT, exist_ok=True)

def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

def px(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c)

def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3] if len(c) > 3 else 255)

def blob(im, cx, cy, rx, ry, c, rim=None):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d <= 1.0:
                f = 1.15 - 0.45 * d - 0.25 * max(0.0, (x - cx) / rx) - 0.2 * max(0.0, (y - cy) / ry)
                px(im, x, y, shade(c, f))
            elif rim and d <= 1.35:
                px(im, x, y, rim)

def heart():
    """The heart of a mountain: a real heart shape - two lobes, a point - of dark basalt, split by
    glowing cracks, a white-hot core, two vessel stubs at the top still dripping magma."""
    im = img()
    stone = (58, 54, 56, 255); light = (92, 86, 90, 255); rim = (24, 20, 24, 255)
    glow = (255, 130, 30, 255); hot = (255, 220, 110, 255); white = (255, 250, 220, 255)
    def inside(x, y):
        # classic heart curve, y up; centred on (7.5, 8.2), ~6.4 px half-width
        X = (x - 7.5) / 5.0; Y = (8.6 - y) / 4.6 + 0.1
        return (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0
    body = {(x, y) for y in range(16) for x in range(16) if inside(x, y)}
    for (x, y) in body:
        f = 1.0 + 0.35 * ((7.5 - x) / 6.0) + 0.25 * ((8.5 - y) / 6.0)
        c = light if (x + y) % 5 == 0 else stone
        px(im, x, y, shade(c, f))
    # outline: the transparent pixels touching the body (4-neighbours only, so the point stays sharp)
    for (x, y) in list(body):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in body: px(im, x + dx, y + dy, rim)
    # vessel stubs between and on the lobes
    for (x, y) in [(7, 2), (8, 2), (7, 1), (4, 3), (11, 3)]: px(im, x, y, shade(stone, 0.8))
    for (x, y) in [(7, 0), (8, 1), (4, 2), (11, 2)]: px(im, x, y, glow)
    # cracks: one main fissure from the top centre down to the point, branches to both lobes
    for (x, y) in [(7, 3), (7, 4), (8, 5), (8, 6), (7, 7), (7, 8), (8, 9), (7, 10), (7, 11), (7, 12), (7, 13),
                   (6, 6), (5, 7), (4, 6), (3, 5), (9, 7), (10, 8), (11, 7), (12, 6), (5, 10), (4, 11), (10, 10), (11, 11), (9, 12)]:
        px(im, x, y, glow)
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 6)]: px(im, x, y, hot)
    px(im, 8, 8, white); px(im, 7, 8, white)
    im.save(os.path.join(OUT, "colossus_heart.png"))

def sigil(name, base, glow):
    im = img()
    blob(im, 7.5, 7.5, 6.5, 6.5, base, rim=shade(base, 0.45))
    rnd = random.Random(sum(map(ord, name)))
    # a rune: a vertical stroke, a cross-stroke and two diagonals
    for y in range(4, 12): px(im, 7, y, glow)
    for x in range(5, 11): px(im, x, 7 if rnd.random() < 0.5 else 6, glow)
    for i in range(3):
        px(im, 7 - i - 1, 11 - i, glow); px(im, 8 + i, 11 - i, glow)
    px(im, 7, 3, (255, 255, 255, 255))
    im.save(os.path.join(OUT, f"sigil_{name}.png"))

def hammer():
    """A two-handed war hammer: a long banded haft from the bottom-left corner to a huge blocky
    deepslate head in the top-right, a glowing rune split across the striking face, a spiked pommel."""
    im = img()
    haft = (74, 52, 34, 255); band = (176, 156, 112, 255); dark = (30, 28, 34, 255)
    head = (70, 72, 80, 255); face = (108, 110, 120, 255); edge = (40, 41, 48, 255)
    glow = (255, 140, 40, 255); hot = (255, 228, 140, 255)
    # haft: 3 px wide diagonal from (0,15) up into the head
    for i in range(9):
        x, y = 0 + i, 15 - i
        px(im, x, y, shade(haft, 1.25)); px(im, x + 1, y, haft); px(im, x, y + 1, shade(haft, 0.65))
    for i in (2, 5):
        px(im, i, 15 - i, band); px(im, i + 1, 15 - i, shade(band, 0.8)); px(im, i, 16 - i, shade(band, 0.6))
    # pommel spike
    px(im, 0, 15, dark); px(im, 1, 15, dark); px(im, 0, 14, dark); px(im, 1, 14, shade(dark, 1.8))
    # head: a big beveled slab, x 6..15, y 0..9, its long axis across the haft (lower-left to upper-right)
    body = set()
    for y in range(0, 10):
        for x in range(6, 16):
            # bevel the four corners
            if (x - 6) + y < 3 or (15 - x) + (9 - y) < 3 or (x - 6) + (9 - y) < 1 or (15 - x) + y < 1: continue
            body.add((x, y))
    for (x, y) in body:
        c = face if (x - y) >= 4 else head
        if (x * 3 + y * 5) % 7 == 0: c = shade(c, 0.88)
        px(im, x, y, c)
    for (x, y) in body:
        if any((x + dx, y + dy) not in body for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px(im, x, y, edge)
    # the rune: a jagged glowing fissure across the face, perpendicular to the haft
    for (x, y) in [(8, 1), (9, 2), (10, 3), (10, 4), (11, 5), (12, 6), (13, 7), (13, 8), (7, 3), (9, 5), (12, 4), (14, 6)]: px(im, x, y, glow)
    for (x, y) in [(10, 4), (11, 5), (10, 3)]: px(im, x, y, hot)
    # striking faces catch the light
    for y in range(1, 9):
        if (15, y) in body: px(im, 15, y, shade(edge, 1.9))
    for x in range(8, 15):
        if (x, 0) in body: px(im, x, 0, shade(edge, 1.7))
    im.save(os.path.join(OUT, "colossus_hammer.png"))

def horn():
    im = img()
    bone = (222, 208, 176, 255); dark = (150, 130, 96, 255); glow = (255, 150, 40, 255)
    pts = []
    for t in range(40):
        a = t / 39.0
        x = 2 + 11 * a
        y = 13 - 9 * a - 3 * math.sin(a * math.pi)
        pts.append((x, y, 2.6 - 2.0 * a))
    for (x, y, r) in pts:
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                if dx * dx + dy * dy <= r * r:
                    px(im, int(x + dx), int(y + dy), bone if (dx + dy) % 2 else shade(bone, 0.9))
    for (x, y, r) in pts[::6]:
        px(im, int(x), int(y + r), dark)
    for (x, y) in [(3, 12), (4, 13), (3, 13)]: px(im, x, y, glow)
    im.save(os.path.join(OUT, "horn_of_waking.png"))

def key():
    im = img()
    obs = (46, 22, 68, 255); purple = (150, 80, 220, 255); glow = (220, 170, 255, 255)
    blob(im, 4.5, 4.5, 3.6, 3.6, obs, rim=shade(obs, 0.5))
    for (x, y) in [(4, 4), (3, 5), (5, 5)]: px(im, x, y, purple)
    px(im, 4, 5, glow)
    for i in range(9):
        px(im, 6 + i, 6 + i, obs); px(im, 7 + i, 6 + i, shade(obs, 1.4))
    for (x, y) in [(13, 13), (14, 12), (12, 14), (14, 14)]: px(im, x, y, purple)
    im.save(os.path.join(OUT, "titan_key.png"))

def heart_of_the_end():
    """The Titan's heart: the same heart, but obsidian-dark, split by ender-purple cracks around a
    cold cyan core, with void sparks at the vessel stubs."""
    im = img()
    stone = (30, 20, 42, 255); light = (58, 40, 78, 255); rim = (12, 6, 18, 255)
    glow = (190, 90, 255, 255); hot = (120, 240, 255, 255); white = (230, 255, 255, 255)
    def inside(x, y):
        X = (x - 7.5) / 5.0; Y = (8.6 - y) / 4.6 + 0.1
        return (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0
    body = {(x, y) for y in range(16) for x in range(16) if inside(x, y)}
    for (x, y) in body:
        f = 1.0 + 0.35 * ((7.5 - x) / 6.0) + 0.25 * ((8.5 - y) / 6.0)
        c = light if (x + y) % 5 == 0 else stone
        px(im, x, y, shade(c, f))
    for (x, y) in list(body):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in body: px(im, x + dx, y + dy, rim)
    for (x, y) in [(7, 2), (8, 2), (7, 1), (4, 3), (11, 3)]: px(im, x, y, shade(stone, 1.4))
    for (x, y) in [(7, 0), (8, 1), (4, 2), (11, 2)]: px(im, x, y, glow)
    for (x, y) in [(7, 3), (7, 4), (8, 5), (8, 6), (7, 7), (7, 8), (8, 9), (7, 10), (7, 11), (7, 12), (7, 13),
                   (6, 6), (5, 7), (4, 6), (3, 5), (9, 7), (10, 8), (11, 7), (12, 6), (5, 10), (4, 11), (10, 10), (11, 11), (9, 12)]:
        px(im, x, y, glow)
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 6)]: px(im, x, y, hot)
    px(im, 8, 8, white); px(im, 7, 8, white)
    im.save(os.path.join(OUT, "heart_of_the_end.png"))

def hourglass_frame(fill, glow_phase):
    """One frame: fill = how much sand is still in the top bulb (1 -> 0), glow_phase 0..1 for the pulse."""
    im = img()
    gold = (222, 178, 70, 255); gold_d = (150, 112, 36, 255); gold_l = (255, 225, 140, 255)
    glass = (150, 200, 235, 120); glass_hi = (220, 245, 255, 150); frame = (56, 40, 20, 255)
    g = 0.5 + 0.5 * math.sin(glow_phase * 2 * math.pi)
    sand = (255, int(130 + 40 * g), 30, 255); hot = (255, int(215 + 40 * g), int(120 + 60 * g), 255); rune = (int(180 + 75 * g), int(140 + 60 * g), 255, 255)
    # caps with a rune notch in the middle
    for x in range(3, 13):
        px(im, x, 1, gold); px(im, x, 2, gold_d); px(im, x, 13, gold_d); px(im, x, 14, gold)
    for (x, y) in [(2, 1), (13, 1), (2, 14), (13, 14)]: px(im, x, y, frame)
    px(im, 7, 1, rune); px(im, 8, 1, rune); px(im, 7, 14, rune); px(im, 8, 14, rune)
    px(im, 3, 1, gold_l); px(im, 4, 1, gold_l); px(im, 3, 14, gold_l)
    for y in range(2, 14):
        px(im, 3, y, frame); px(im, 12, y, frame)
    def half_at(y):
        return 1 + int(abs(y - 7.5) * 0.9)
    for y in range(3, 13):
        h = half_at(y)
        for x in range(8 - h, 8 + h): px(im, x, y, glass)
        px(im, 8 - h, y, glass_hi)
    # top bulb: rows 3..6 hold sand from the bottom up according to fill
    top_rows = [6, 5, 4, 3]
    n_top = int(round(fill * 4))
    for i in range(n_top):
        y = top_rows[i]; h = half_at(y)
        for x in range(8 - h + 1, 8 + h - 1): px(im, x, y, sand)
    # the thread through the waist while anything is left up there
    if fill > 0.02:
        px(im, 7, 7, hot); px(im, 8, 7, hot); px(im, 7, 8, hot); px(im, 8, 8, sand)
        px(im, 7, 9, sand)
    # bottom heap: rows 12..9 fill from the bottom as the top empties
    bot_rows = [12, 11, 10, 9]
    n_bot = int(round((1 - fill) * 4))
    for i in range(n_bot):
        y = bot_rows[i]; h = half_at(y)
        for x in range(8 - h + 1, 8 + h - 1): px(im, x, y, sand)
    if n_bot >= 1: px(im, 7, 12, hot); px(im, 8, 12, hot)
    return im

def squash(im, factor):
    """Vertical squash about the centre (the hourglass turning over); factor < 0 flips it."""
    out = img()
    f = abs(factor)
    if f < 0.06: f = 0.06
    for y in range(16):
        src = (y - 7.5) / f + 7.5
        if factor < 0: src = 15 - src
        sy = int(round(src))
        if 0 <= sy < 16:
            for x in range(16):
                c = im.getpixel((x, sy))
                if c[3]: px(out, x, y, c)
    return out

def hourglass():
    """Animated: 20 frames of sand running down, then 8 frames of the glass turning over so the
    full bulb is on top again - a seamless loop. Frames stacked vertically + .mcmeta."""
    frames = []
    for i in range(20):
        frames.append(hourglass_frame(1 - i / 19.0, i / 20.0))
    empty = hourglass_frame(0.0, 0.0)
    for k in range(8):
        theta = math.pi * (k + 1) / 8.0
        frames.append(squash(empty, math.cos(theta)))
    strip = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames): strip.paste(f, (0, 16 * i))
    strip.save(os.path.join(OUT, "hourglass_of_restoration.png"))
    with open(os.path.join(OUT, "hourglass_of_restoration.png.mcmeta"), "w") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')

def ember():
    """A coal that never went out: a black lump with orange cracks and a white-hot centre."""
    im = img()
    coal = (28, 26, 30, 255); glow = (255, 130, 30, 255); hot = (255, 220, 120, 255)
    blob(im, 7.5, 8.5, 5.2, 4.6, coal, rim=(12, 10, 12, 255))
    for (x, y) in [(5, 7), (6, 8), (7, 9), (8, 8), (9, 7), (10, 8), (7, 6), (8, 11), (6, 10), (4, 9), (11, 10)]:
        px(im, x, y, glow)
    for (x, y) in [(7, 8), (8, 9), (8, 7)]: px(im, x, y, hot)
    for (x, y) in [(6, 3), (9, 2), (11, 4)]: px(im, x, y, (255, 160, 60, 200))  # sparks
    im.save(os.path.join(OUT, "sleepers_ember.png"))

def rune_item(name, base, glow):
    """A small stone tablet with a glyph cut into it, lit in the kind's colour."""
    im = img()
    d_rim = shade(base, 0.45)
    for y in range(2, 14):
        for x in range(4, 12):
            edge = y in (2, 13) or x in (4, 11)
            px(im, x, y, d_rim if edge else shade(base, 1.0 + (0.08 if (x + y) % 3 == 0 else 0)))
    rnd = random.Random(sum(map(ord, name)) * 7)
    cx = 7 + rnd.randint(0, 1)
    for y in range(4, 12): px(im, cx, y, glow)
    yy = rnd.randint(5, 9)
    for x in range(6, 10): px(im, x, yy, glow)
    px(im, cx + rnd.choice([-2, 2]), rnd.randint(4, 11), glow)
    px(im, cx, 3, (255, 255, 255, 255))
    im.save(os.path.join(OUT, f"rune_{name}.png"))


def dead_letter():
    """A folded letter, yellowed and water-stained, a red wax seal, faint lines of writing."""
    im = img()
    paper = (226, 214, 176, 255); dark = (176, 160, 118, 255); stain = (196, 178, 132, 255); ink = (92, 78, 66, 255); wax = (150, 36, 36, 255); waxh = (200, 70, 60, 255)
    for y in range(3, 14):
        for x in range(2, 14):
            c = paper
            if (x - 9) ** 2 + (y - 10) ** 2 <= 6: c = stain
            if y in (3, 13) or x in (2, 13): c = dark
            px(im, x, y, c)
    # the fold: a diagonal crease from the top corners to the middle
    for i in range(6):
        px(im, 2 + i, 3 + i, dark); px(im, 13 - i, 3 + i, dark)
    # writing
    for y, (x0, x1) in ((6, (4, 8)), (8, (4, 11)), (10, (4, 9)), (11, (6, 10))):
        for x in range(x0, x1):
            if (x + y) % 3: px(im, x, y, ink)
    # the seal
    for (x, y) in [(7, 8), (8, 8), (7, 9), (8, 9), (6, 9), (9, 9), (7, 10), (8, 10)]: px(im, x, y, wax)
    px(im, 7, 8, waxh)
    im.save(os.path.join(OUT, "dead_letter.png"))

def almanac():
    """A thick book bound in dark green leather, brass corners, a pale heart-shaped emblem on the cover."""
    im = img()
    leather = (46, 84, 58, 255); light = (66, 112, 78, 255); dark = (26, 52, 36, 255); brass = (196, 160, 80, 255); page = (232, 222, 190, 255); emblem = (220, 200, 150, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            c = leather if (x + y) % 4 else light
            if x == 2 or y in (1, 14): c = dark
            if x == 13: c = page
            px(im, x, y, c)
    for (x, y) in [(3, 2), (12, 2), (3, 13), (12, 13), (3, 3), (4, 2), (11, 2), (12, 3), (3, 12), (4, 13), (11, 13), (12, 12)]: px(im, x, y, brass)
    for y in range(2, 14): px(im, 5, y, dark)
    # the emblem: a small heart
    for (x, y) in [(7, 6), (8, 6), (10, 6), (11, 6), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (8, 8), (9, 8), (10, 8), (9, 9)]: px(im, x, y, emblem)
    px(im, 9, 7, brass)
    im.save(os.path.join(OUT, "almanac.png"))

heart(); hammer(); horn(); key(); hourglass(); heart_of_the_end(); ember(); dead_letter(); almanac()
for name, base, glow in [("stone", (120, 118, 122, 255), (255, 150, 60, 255)), ("earth", (110, 84, 56, 255), (255, 170, 70, 255)),
                         ("sandstone", (214, 190, 130, 255), (255, 220, 100, 255)), ("ice", (160, 205, 235, 255), (190, 250, 255, 255)),
                         ("prismarine", (80, 140, 130, 255), (150, 255, 230, 255)), ("moss", (90, 125, 65, 255), (190, 255, 120, 255))]:
    rune_item(name, base, glow)
for name, base, glow in [("stone", (110, 110, 112, 255), (255, 140, 40, 255)), ("earth", (120, 92, 60, 255), (255, 150, 60, 255)),
                         ("sandstone", (216, 196, 140, 255), (255, 210, 90, 255)), ("ice", (170, 214, 240, 255), (200, 255, 255, 255)),
                         ("prismarine", (80, 150, 140, 255), (160, 255, 230, 255)), ("moss", (86, 130, 60, 255), (200, 255, 120, 255))]:
    sigil(name, base, glow)
print("textures written to", OUT)
