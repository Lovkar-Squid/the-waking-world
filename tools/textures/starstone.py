"""Starstone and Star Iron: the block a fallen star leaves, and the metal prised out of it.

   python3 tools/textures/starstone.py
   Block 16x16 (the mod's other blocks are 16), item 32x32 like the rest of items32.py.
"""
from PIL import Image, ImageFilter
import os, random, math

HERE = os.path.dirname(__file__)
BLOCK = os.path.join(HERE, "..", "..", "resources", "assets", "wakingworld", "textures", "block")
ITEM = os.path.join(HERE, "..", "..", "resources", "assets", "wakingworld", "textures", "item")
os.makedirs(BLOCK, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)
rnd = random.Random(1409)

def clamp(v):
    return max(0, min(255, int(v)))

def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))

# --- the block -------------------------------------------------------------------------------
# black, close-grained rock, shot through with veins that still hold the heat of the fall:
# white-hot in the middle of a vein, falling off to orange and then to a dull ember red.
ROCK = (26, 24, 30)
ROCK_D = (16, 15, 20)
ROCK_L = (44, 41, 50)
HOT = (255, 246, 214)
MID = (255, 158, 42)
LOW = (150, 44, 12)

def starstone():
    im = Image.new("RGBA", (16, 16))
    # rock with a little grain
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            c = ROCK_L if v > 0.90 else (ROCK_D if v < 0.22 else ROCK)
            im.putpixel((x, y), c + (255,))
    # veins: a few random walks across the face, glowing hottest at their heart
    heat = [[0.0] * 16 for _ in range(16)]
    for _ in range(3):
        x, y = rnd.randrange(16), rnd.randrange(16)
        ang = rnd.random() * math.tau
        for _step in range(rnd.randint(14, 22)):
            ang += rnd.uniform(-0.9, 0.9)
            x = (x + math.cos(ang)) % 16
            y = (y + math.sin(ang)) % 16
            ix, iy = int(x), int(y)
            heat[iy][ix] = max(heat[iy][ix], 1.0)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                jx, jy = (ix + dx) % 16, (iy + dy) % 16
                heat[jy][jx] = max(heat[jy][jx], 0.45 + rnd.random() * 0.2)
    for y in range(16):
        for x in range(16):
            h = heat[y][x]
            if h <= 0:
                continue
            base = im.getpixel((x, y))[:3]
            if h > 0.85:
                c = mix(MID, HOT, (h - 0.85) / 0.15)
            else:
                c = mix(LOW, MID, h / 0.85)
            im.putpixel((x, y), mix(base, c, min(1.0, 0.35 + h * 0.75)) + (255,))
    im.save(os.path.join(BLOCK, "starstone.png"))
    return im

# --- the item --------------------------------------------------------------------------------
# a rough nugget of star iron: dark, angular, with the same heat still caught in its seams
def star_iron():
    S = 32
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    # an irregular lump: a blob of points, then filled
    cx, cy = 16, 17
    pts = []
    n = 9
    for i in range(n):
        a = i / n * math.tau
        r = 8.5 + rnd.uniform(-2.2, 2.2)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r * 0.92))
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.polygon(pts, fill=(30, 28, 34, 255))
    # facets: lighter on the upper left, darker below right
    for y in range(S):
        for x in range(S):
            if im.getpixel((x, y))[3] == 0:
                continue
            nx, ny = (x - cx) / 10.0, (y - cy) / 10.0
            lam = -(nx * 0.7 + ny * 0.7)
            base = mix((22, 20, 26), (96, 92, 104), max(0.0, min(1.0, 0.45 + lam * 0.8)))
            if rnd.random() < 0.10:
                base = mix(base, (130, 126, 140), 0.5)
            im.putpixel((x, y), base + (255,))
    # the seams of trapped heat
    for _ in range(3):
        x, y = cx + rnd.uniform(-5, 5), cy + rnd.uniform(-5, 5)
        ang = rnd.random() * math.tau
        for _step in range(rnd.randint(7, 12)):
            ang += rnd.uniform(-0.8, 0.8)
            x += math.cos(ang) * 1.1
            y += math.sin(ang) * 1.1
            ix, iy = int(x), int(y)
            if 0 <= ix < S and 0 <= iy < S and im.getpixel((ix, iy))[3] > 0:
                im.putpixel((ix, iy), MID + (255,))
                for dx, dy in ((1, 0), (0, 1)):
                    jx, jy = ix + dx, iy + dy
                    if 0 <= jx < S and 0 <= jy < S and im.getpixel((jx, jy))[3] > 0:
                        im.putpixel((jx, jy), mix(im.getpixel((jx, jy))[:3], LOW, 0.55) + (255,))
    # a hot core showing through
    for y in range(S):
        for x in range(S):
            if im.getpixel((x, y))[3] == 0:
                continue
            d2 = (x - cx + 1) ** 2 + (y - cy + 1) ** 2
            if d2 < 5:
                im.putpixel((x, y), mix(im.getpixel((x, y))[:3], HOT, 0.65) + (255,))
    # a dark outline so it reads at 16 px in the hotbar
    edge = im.filter(ImageFilter.FIND_EDGES)
    for y in range(S):
        for x in range(S):
            if im.getpixel((x, y))[3] > 0 and edge.getpixel((x, y))[3] > 40:
                nb = [(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                if any(not (0 <= a < S and 0 <= b < S) or im.getpixel((a, b))[3] == 0 for a, b in nb):
                    im.putpixel((x, y), (12, 11, 15, 255))
    im.save(os.path.join(ITEM, "star_iron.png"))
    return im

b = starstone()
i = star_iron()
# a preview sheet for the devlog
sheet = Image.new("RGBA", (16 * 8 + 32 * 4, 16 * 8), (20, 20, 24, 255))
sheet.paste(b.resize((16 * 8, 16 * 8), Image.NEAREST), (0, 0))
sheet.paste(i.resize((32 * 4, 32 * 4), Image.NEAREST), (16 * 8, 0), i.resize((32 * 4, 32 * 4), Image.NEAREST))
sheet.save("/tmp/starstone-sheet.png")
print("starstone.png, star_iron.png written; preview /tmp/starstone-sheet.png")
