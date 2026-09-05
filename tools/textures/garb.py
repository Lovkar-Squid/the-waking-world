"""Textures for the 3-D garb of the kingdom's people (client/GarbModel.java): one 64x64 sheet per kind.
The boxes here mirror the cubes in GarbModel.createLayer - texOffs (u, v) and size (w, h, d) must agree.
python3 tools/textures/garb.py -> resources/assets/wakingworld/textures/entity/kingdom/garb/<kind>.png"""
from PIL import Image
import os, random, math

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "entity", "kingdom", "garb")
os.makedirs(OUT, exist_ok=True)

def clamp(v): return max(0, min(255, int(v)))
def shade(c, f): return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)

CYAN = (52, 128, 140); CYAN_D = (34, 90, 100); GOLD = (222, 184, 74); GOLD_D = (160, 122, 36)
LEATHER = (110, 74, 40); LEATHER_D = (78, 52, 28); PURPLE = (96, 40, 120); PURPLE_D = (64, 26, 84)
CREAM = (222, 208, 176); WHITE = (240, 238, 232); BLACK = (24, 22, 24); GREEN = (70, 110, 60); GREEN_D = (48, 78, 42)

class Sheet:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < 64 and 0 <= y < 64: self.im.putpixel((int(x), int(y)), c)

    def rect(self, x, y, w, h, c, noise=0.0):
        for yy in range(int(y), int(math.ceil(y + h))):
            for xx in range(int(x), int(math.ceil(x + w))):
                cc = c
                if noise and self.rnd.random() < noise: cc = shade(c, 0.88 if self.rnd.random() < 0.5 else 1.08)
                self.px(xx, yy, cc)

    def faces(self, u, v, w, h, d):
        """(x, y, w, h) of each face in the box's UV layout."""
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}

    def box(self, u, v, w, h, d, c, noise=0.08, faces=None):
        for name, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            if faces and name not in faces: continue
            f = {"top": 1.06, "bottom": 0.72, "right": 0.9, "left": 0.9, "back": 0.95, "front": 1.0}[name]
            self.rect(x, y, max(1, fw), max(1, fh), shade(c, f), noise)

    def border(self, u, v, w, h, d, c, faces=("front", "back", "left", "right"), thick=1):
        for name, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            if name not in faces: continue
            for k in range(thick):
                self.rect(x, y + fh - 1 - k, fw, 1, c)      # hem
            if fw >= 3:
                self.rect(x, y, 1, fh, c); self.rect(x + fw - 1, y, 1, fh, c)  # edges

    def hem_folds(self, u, v, w, h, d, c, faces=("front", "back")):
        """Vertical fold shading down a hanging cloth."""
        for name, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            if name not in faces: continue
            for xx in range(int(x) + 1, int(x + fw) - 1):
                if (xx - int(x)) % 3 == 0:
                    for yy in range(int(y) + 1, int(y + fh) - 1):
                        self.px(xx, yy, shade(c, 0.86 if (yy - int(y)) % 4 else 0.8))

    def ermine(self, u, v, w, h, d, faces=("front", "back", "left", "right", "top")):
        for name, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            if name not in faces: continue
            self.rect(x, y, fw, fh, WHITE, 0.05)
            for yy in range(int(y), int(y + fh)):
                for xx in range(int(x), int(x + fw)):
                    if (xx * 7 + yy * 3) % 5 == 0 and (xx + yy) % 2 == 0: self.px(xx, yy, BLACK)

    def rhombus(self, x, y, c, size=2):
        for dy in range(-size, size + 1):
            span = size - abs(dy)
            for dx in range(-span, span + 1): self.px(x + dx, y + dy, c)

    def save(self, name):
        self.im.save(os.path.join(OUT, name + ".png"))

def cloak(s, length, c, border, emblem=None, folds=True):
    s.box(0, 0, 10, length, 1, c, 0.06)
    if folds: s.hem_folds(0, 0, 10, length, 1, c)
    s.border(0, 0, 10, length, 1, border)
    if emblem:
        for name in ("front", "back"):
            x, y, w, h = s.faces(0, 0, 10, length, 1)[name]
            s.rhombus(x + 5, y + min(h - 4, 6), emblem)
            s.px(x + 5, y + min(h - 4, 6), shade(emblem, 0.75))

def plume(s, c1, c2):
    # (30, 0) 1 x 5 x 5: feathers in two tones
    s.box(30, 0, 1, 5, 5, c1, 0.1)
    for name, (x, y, w, h) in s.faces(30, 0, 1, 5, 5).items():
        for yy in range(int(y), int(y + h)):
            if (yy - int(y)) % 2 == 1: s.rect(x, yy, w, 1, shade(c2, 0.95))

# ---- guards ----
s = Sheet(1); cloak(s, 14, GREEN, GOLD_D, GOLD)
s.box(30, 0, 3, 8, 2, LEATHER, 0.1); s.border(30, 0, 3, 8, 2, LEATHER_D, ("front", "back", "left", "right"))
tx, ty, tw, td = s.faces(30, 0, 3, 8, 2)["top"]; s.rect(tx, ty, tw, td, (200, 200, 200)); s.px(tx + 1, ty, (120, 30, 30, 255)); s.px(tx, ty + 1, (120, 30, 30, 255))  # arrow tips and fletching
s.save("archer")

s = Sheet(2); cloak(s, 14, CYAN, GOLD, GOLD); plume(s, CYAN, GOLD); s.save("knight")
s = Sheet(3); cloak(s, 12, CYAN_D, GOLD_D, GOLD); plume(s, GOLD, CYAN); s.save("spearman")

# ---- townsfolk ----
s = Sheet(4)
s.box(0, 32, 12, 1, 12, LEATHER, 0.1)                            # brim
s.box(0, 46, 7, 4, 7, LEATHER, 0.08); s.border(0, 46, 7, 4, 7, LEATHER_D)   # hat crown with a dark band at the base
for name, (x, y, w, h) in s.faces(0, 46, 7, 4, 7).items():
    if name in ("front", "back", "left", "right"): s.rect(x, y + 2, w, 1, (60, 40, 22, 255))
s.box(30, 0, 4, 4, 2, LEATHER_D, 0.1); fx, fy, fw, fh = s.faces(30, 0, 4, 4, 2)["back"]; s.rect(fx + 1, fy + 1, 2, 1, GOLD_D)  # satchel with a buckle
s.box(44, 0, 1, 8, 1, LEATHER_D, 0.1)                            # strap
s.save("surveyor")

s = Sheet(5); cloak(s, 16, PURPLE, GOLD_D, folds=True)
for name in ("front", "back"):  # rune marks down the cloak
    x, y, w, h = s.faces(0, 0, 10, 16, 1)[name]
    for k, (dx, dy) in enumerate([(2, 3), (7, 5), (4, 9), (6, 12)]):
        s.px(x + dx, y + dy, (170, 130, 200, 255)); s.px(x + dx, y + dy + 1, (170, 130, 200, 255)); s.px(x + dx + (1 if k % 2 else -1), y + dy + 1, (170, 130, 200, 255))
s.box(0, 32, 8, 8, 8, PURPLE_D, 0.08)                            # hood
hx, hy, hw, hh = s.faces(0, 32, 8, 8, 8)["front"]
for yy in range(2, 8):
    for xx in range(1, 7): s.px(hx + xx, hy + yy, (0, 0, 0, 0))   # the face shows
s.border(0, 32, 8, 8, 8, (130, 90, 160), ("front",))
s.save("relic_monger")

s = Sheet(6)
s.box(30, 0, 7, 6, 1, (70, 56, 46), 0.12)                         # apron bib
bx, by, bw, bh = s.faces(30, 0, 7, 6, 1)["front"]; s.rect(bx, by, bw, 1, LEATHER_D); s.px(bx + 1, by + 3, (40, 34, 30, 255)); s.px(bx + 5, by + 2, (40, 34, 30, 255))  # soot
s.box(46, 0, 2, 4, 1, (150, 150, 156), 0.1)                       # tools at the belt
tx, ty, tw, th = s.faces(46, 0, 2, 4, 1)["front"]; s.rect(tx, ty + 2, tw, 2, LEATHER_D)
s.save("smith")

s = Sheet(7)
s.box(0, 32, 8, 3, 8, (196, 178, 140), 0.08); s.border(0, 32, 8, 3, 8, GREEN_D)   # cap with a green band
s.box(30, 0, 5, 6, 3, (150, 118, 76), 0.15)                                       # burlap sack
sx, sy, sw, sh = s.faces(30, 0, 5, 6, 3)["back"]; s.rect(sx, sy + 1, sw, 1, LEATHER_D)  # its tie
s.save("provisioner")

s = Sheet(8)
s.box(0, 32, 8, 2, 8, (196, 160, 72), 0.1); s.border(0, 32, 8, 2, 8, (150, 118, 50))   # mustard cap
s.box(30, 0, 9, 2, 1, LEATHER_D, 0.1)                                                  # bandolier
cx, cy, cw, ch = s.faces(30, 0, 9, 2, 1)["front"]
for k in range(0, 9, 2): s.px(cx + k, cy, CREAM); s.px(cx + k, cy + 1, CREAM); s.px(cx + k, cy - 0, (40, 30, 20, 255)) if False else None
s.save("chandler")

s = Sheet(9)
s.box(0, 32, 10, 2, 10, (30, 36, 70), 0.08); s.border(0, 32, 10, 2, 10, (60, 70, 120))  # flat cap
s.box(30, 0, 3, 9, 3, CREAM, 0.1)                                                        # a rolled scroll
for name, (x, y, w, h) in s.faces(30, 0, 3, 9, 3).items():
    if name in ("front", "back", "left", "right"): s.rect(x, y + 4, w, 1, (170, 60, 50, 255))  # its ribbon
s.save("scribe")

# ---- the king ----
s = Sheet(10); cloak(s, 20, PURPLE, GOLD, None)
for name in ("front", "back"):
    x, y, w, h = s.faces(0, 0, 10, 20, 1)[name]
    s.rect(x, y + h - 3, w, 2, WHITE, 0.05)
    for xx in range(int(x), int(x + w), 2): s.px(xx, y + h - 2, BLACK)     # ermine hem
    s.rhombus(x + 5, y + 7, GOLD, 2); s.px(x + 5, y + 7, (200, 40, 60, 255))
s.ermine(0, 22, 12, 3, 6)                                                    # mantle
s.box(0, 32, 9, 2, 9, GOLD, 0.06)                                            # crown ring
cx, cy, cw, ch = s.faces(0, 32, 9, 2, 9)["front"]
for k, gem in enumerate([(200, 40, 60), (40, 80, 200), (200, 40, 60)]): s.px(cx + 2 + k * 2, cy, gem + (255,))
for name in ("back", "left", "right"):
    x, y, w, h = s.faces(0, 32, 9, 2, 9)[name]; s.px(x + w // 2, y, (60, 160, 90, 255))
for i in range(5): s.box(40 + i * 4, 32, 1, 2, 1, GOLD, 0.0); s.px(40 + i * 4 + 1, 32, (250, 230, 150, 255))
s.save("king")
print("garb textures written to", OUT)
