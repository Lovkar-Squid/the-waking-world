"""Block textures: the throne (gold, cushion, dark iron, the crest). python3 tools/textures/blocks.py"""
from PIL import Image
import os, random, math
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "block")
os.makedirs(OUT, exist_ok=True)
rnd = random.Random(9)
def clamp(v): return max(0, min(255, int(v)))
def shade(c, f): return (clamp(c[0]*f), clamp(c[1]*f), clamp(c[2]*f), 255)
def tex(fn, name):
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            im.putpixel((x, y), tuple(c[:3]) + (255,))
    im.save(os.path.join(OUT, name + ".png"))
GOLD = (232, 190, 70); GOLD_D = (170, 124, 30); GOLD_L = (250, 230, 150)
def gold(x, y):
    # hammered gold with a bevelled rim and a beaded line
    c = GOLD
    if x in (0, 15) or y in (0, 15): c = GOLD_D
    elif x == 1 or y == 1: c = GOLD_L
    elif (x + y) % 5 == 0 and rnd.random() < 0.5: c = shade(GOLD, 0.9)
    elif rnd.random() < 0.08: c = shade(GOLD, 1.08)
    return c + (255,)
tex(gold, "throne_gold")
RED = (150, 24, 34); RED_D = (100, 12, 22); RED_L = (190, 60, 70)
def cushion(x, y):
    # velvet with a diamond tuft pattern and gold buttons
    c = RED
    if (x + y) % 8 == 0 or (x - y) % 8 == 0: c = RED_D
    if (x % 8 == 4 and y % 8 == 4): c = GOLD
    if rnd.random() < 0.1: c = RED_L if rnd.random() < 0.5 else RED_D
    return c + (255,)
tex(cushion, "throne_cushion")
def dark(x, y):
    v = rnd.randint(-8, 8)
    c = (44 + v, 40 + v, 48 + v)
    if x in (0, 15) or y in (0, 15): c = (30, 28, 34)
    return c + (255,)
tex(dark, "throne_dark")
def ornament(x, y):
    # gold with a red gem in the middle and small points
    d = math.hypot(x - 7.5, y - 7.5)
    if d < 2.2: return (200, 40, 60, 255) if d > 1.2 else (240, 120, 130, 255)
    if d < 3.2: return GOLD_D + (255,)
    if y < 3 and x % 3 == 1: return GOLD_L + (255,)
    return gold(x, y)
tex(ornament, "throne_ornament")
print("block textures written")
