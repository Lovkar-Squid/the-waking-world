"""Particle textures: eight rune glyphs (16x16, white on transparent - coloured by the option),
a soft ring (32x32) and an ember spark (8x8). python3 tools/textures/particles.py"""
from PIL import Image, ImageDraw
import os, math, random
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "particle")
os.makedirs(OUT, exist_ok=True)

def glyph(i):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rnd = random.Random(1000 + i)
    w = (255, 255, 255, 255)
    # a spine, a few strokes off it, one dot - angular runes
    x = 7 + rnd.randint(0, 1)
    top, bot = rnd.randint(1, 3), rnd.randint(12, 14)
    d.line([(x, top), (x, bot)], fill=w, width=2)
    for _ in range(rnd.randint(2, 4)):
        y = rnd.randint(top + 1, bot - 1)
        dx = rnd.choice([-5, -4, 4, 5]); dy = rnd.choice([-4, -3, 0, 3, 4])
        d.line([(x, y), (x + dx, y + dy)], fill=w, width=2)
    if rnd.random() < 0.6:
        cx, cy = x + rnd.choice([-4, 4]), rnd.randint(3, 12)
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=w)
    # soft halo: a faint copy around
    halo = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for yy in range(16):
        for xx in range(16):
            if im.getpixel((xx, yy))[3]:
                for ddx in (-1, 0, 1):
                    for ddy in (-1, 0, 1):
                        px, py = xx + ddx, yy + ddy
                        if 0 <= px < 16 and 0 <= py < 16 and halo.getpixel((px, py))[3] < 90 and im.getpixel((px, py))[3] == 0:
                            halo.putpixel((px, py), (255, 255, 255, 90))
    return Image.alpha_composite(halo, im)

for i in range(8):
    glyph(i).save(os.path.join(OUT, f"rune_{i}.png"))

# the ring: a bright band with a soft outer glow and a faint inner fill
ring = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
for y in range(32):
    for x in range(32):
        r = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16.0
        if r > 1.0: continue
        band = math.exp(-((r - 0.82) / 0.09) ** 2)
        inner = 0.12 * max(0.0, 1 - r / 0.8)
        a = min(1.0, band + inner)
        ring.putpixel((x, y), (255, 255, 255, int(255 * a)))
ring.save(os.path.join(OUT, "ring.png"))

# the ember: a hot dot with a glow
ember = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
for y in range(8):
    for x in range(8):
        r = math.hypot(x + 0.5 - 4, y + 0.5 - 4) / 4.0
        if r > 1.0: continue
        a = max(0.0, 1 - r) ** 1.5
        c = 255 if r < 0.35 else 235
        ember.putpixel((x, y), (255, c, c, int(255 * a)))
ember.save(os.path.join(OUT, "ember.png"))
print("particle textures written")
