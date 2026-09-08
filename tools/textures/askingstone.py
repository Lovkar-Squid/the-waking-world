"""The Asking Stone's block textures - the one thing the mage hands over.

python3 tools/textures/askingstone.py -> resources/assets/wakingworld/textures/block/asking_stone_*.png
Four 16x16s: the base, the sides, and the top in both states (dark, and burning while it asks).
"""
from PIL import Image
import os, math, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "block")
os.makedirs(OUT, exist_ok=True)


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


def tex(fn, name, seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), tuple(fn(x, y, rnd)[:3]) + (255,))
    im.save(os.path.join(OUT, name + ".png"))


STONE = (38, 36, 46)
STONE_D = (24, 22, 30)
STONE_L = (58, 55, 70)
RUNE = (128, 62, 214)
RUNE_HOT = (214, 168, 255)
BRASS = (128, 96, 44)


def rock(x, y, rnd, base=STONE):
    """Cut deepslate: a fine grain with a few chips out of it."""
    v = rnd.randint(-7, 7)
    c = (base[0] + v, base[1] + v, base[2] + v)
    if rnd.random() < 0.06:
        c = shade(base, 0.72)
    elif rnd.random() < 0.06:
        c = shade(base, 1.22)
    return c


def side(x, y, rnd):
    c = rock(x, y, rnd)
    if y <= 1:
        c = shade(STONE_L, 1.0)                       # the chamfered lip at the top
    if y >= 10:
        c = rock(x, y, rnd, STONE_D)                  # it sits in shadow at the foot
    # a band of shallow carving round the middle
    if 4 <= y <= 6 and (x + y) % 4 != 0:
        c = shade(c, 0.78)
    if y == 5 and x % 4 == 2:
        c = BRASS
    return c


def base(x, y, rnd):
    c = rock(x, y, rnd, STONE_D)
    if (x in (0, 15) or y in (0, 15)):
        c = shade(STONE_D, 0.8)
    return c


def ring(x, y, hot):
    """The rune wheel cut into the top: a circle, four spokes, and a socket in the middle."""
    dx, dy = x - 7.5, y - 7.5
    d = math.hypot(dx, dy)
    a = math.degrees(math.atan2(dy, dx)) % 360
    if d < 1.9:
        return RUNE_HOT if hot else shade(RUNE, 0.42)
    if 1.9 <= d < 2.6:
        return shade(STONE_D, 0.9)
    if 4.6 <= d <= 5.6:
        return RUNE if hot else shade(RUNE, 0.30)
    # four spokes out to the ring
    for k in range(4):
        if abs(((a - k * 90) + 180) % 360 - 180) < 7 and 2.6 < d < 4.6:
            return RUNE if hot else shade(RUNE, 0.28)
    # the small marks between the spokes
    for k in range(4):
        if abs(((a - 45 - k * 90) + 180) % 360 - 180) < 10 and 5.9 < d < 6.7:
            return RUNE_HOT if hot else shade(RUNE, 0.34)
    return None


def top_of(hot):
    def fn(x, y, rnd):
        r = ring(x, y, hot)
        if r is not None:
            return r
        c = rock(x, y, rnd)
        if x in (0, 15) or y in (0, 15):
            c = shade(STONE_D, 0.9)
        elif x in (1, 14) or y in (1, 14):
            c = shade(STONE_L, 0.95)
        return c
    return fn


tex(side, "asking_stone_side", 11)
tex(base, "asking_stone_base", 12)
tex(top_of(False), "asking_stone_top", 13)
tex(top_of(True), "asking_stone_top_lit", 13)
print("asking stone textures written")
