"""Skins for the dungeon keepers: the Ember Wraith (zombie layout, 64x64), the Rune Sentinel (skeleton
layout, 64x32), the Drowned Keeper (zombie layout, 64x64; the vanilla drowned rags are drawn over it).
python3 tools/textures/mobs.py -> resources/assets/wakingworld/textures/entity/*.png"""
from PIL import Image
import os, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "entity")
os.makedirs(OUT, exist_ok=True)

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)

def faces(box):
    x, y, w, h, d = box
    return {"top": (x + d, y, w, d), "bottom": (x + d + w, y, w, d), "right": (x, y + d, d, h), "front": (x + d, y + d, w, h), "left": (x + d + w, y + d, d, h), "back": (x + d + w + d, y + d, w, h)}

class Skin:
    def __init__(self, seed, size):
        self.im = Image.new("RGBA", size, (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < self.im.width and 0 <= y < self.im.height: self.im.putpixel((x, y), c)

    def rect(self, x, y, w, h, c, noise=0.0):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                cc = c
                if noise and self.rnd.random() < noise: cc = shade(c, 0.85 if self.rnd.random() < 0.5 else 1.12)
                self.px(xx, yy, cc)

    def box(self, b, c, noise=0.1):
        for name, (x, y, w, h) in faces(b).items():
            f = {"top": 1.05, "bottom": 0.7, "right": 0.88, "left": 0.88, "back": 0.93, "front": 1.0}[name]
            self.rect(x, y, w, h, shade(c, f), noise)

    def speckle(self, b, c, chance, faces_=None):
        for name, (x, y, w, h) in faces(b).items():
            if faces_ and name not in faces_: continue
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    if self.rnd.random() < chance: self.px(xx, yy, c)

    def cracks(self, b, c, n, length=(3, 7)):
        x0, y0, w, h, d = b
        fs = faces(b)
        for _ in range(n):
            name = self.rnd.choice(["front", "back", "left", "right"])
            x, y, fw, fh = fs[name]
            cx, cy = self.rnd.randint(x, x + fw - 1), self.rnd.randint(y, y + fh - 1)
            for i in range(self.rnd.randint(*length)):
                if x <= cx < x + fw and y <= cy < y + fh: self.px(cx, cy, c)
                cx += self.rnd.choice([-1, 0, 1]); cy += 1

    def save(self, name):
        self.im.save(os.path.join(OUT, name + ".png"))

# the humanoid (zombie) layout, 64x64
Z_HEAD = (0, 0, 8, 8, 8); Z_HAT = (32, 0, 8, 8, 8); Z_BODY = (16, 16, 8, 12, 4); Z_RARM = (40, 16, 4, 12, 4); Z_LARM = (32, 48, 4, 12, 4); Z_RLEG = (0, 16, 4, 12, 4); Z_LLEG = (16, 48, 4, 12, 4)
# the skeleton layout, 64x32: thin arms and legs
S_HEAD = (0, 0, 8, 8, 8); S_BODY = (16, 16, 8, 12, 4); S_ARM = (40, 16, 2, 12, 2); S_LEG = (0, 16, 2, 12, 2)

def ember_wraith():
    s = Skin(41, (64, 64))
    CHAR = (34, 26, 24); EMBER = (255, 110, 20, 255); HOT = (255, 220, 120, 255); ASH = (70, 62, 58, 255)
    for b in (Z_HEAD, Z_BODY, Z_RARM, Z_LARM, Z_RLEG, Z_LLEG):
        s.box(b, CHAR, 0.18)
        s.speckle(b, ASH, 0.08)
        s.cracks(b, EMBER, 5, (2, 6))
        s.cracks(b, (140, 40, 10, 255), 4, (2, 5))
    # the face: hollow orange eyes, a mouth full of coals
    fx, fy = faces(Z_HEAD)["front"][:2]
    for (x, y) in [(1, 3), (2, 3), (5, 3), (6, 3), (1, 4), (2, 4), (5, 4), (6, 4)]: s.px(fx + x, fy + y, EMBER)
    for (x, y) in [(2, 4), (5, 4)]: s.px(fx + x, fy + y, HOT)
    for x in range(2, 6): s.px(fx + x, fy + 6, EMBER)
    s.px(fx + 3, fy + 6, HOT)
    # the chest: the furnace door - a glowing square with a dark grate
    bx, by = faces(Z_BODY)["front"][:2]
    for y in range(3, 9):
        for x in range(2, 6):
            s.px(bx + x, by + y, HOT if (x + y) % 2 == 0 else EMBER)
    for y in range(2, 10):
        s.px(bx + 1, by + y, ASH); s.px(bx + 6, by + y, ASH)
    # embers down the arms' outer faces and the legs
    for b in (Z_RARM, Z_LARM, Z_RLEG, Z_LLEG):
        x, y, w, h = faces(b)["front"]
        for yy in range(y, y + h, 3): s.px(x + s.rnd.randint(0, w - 1), yy, EMBER)
    s.save("ember_wraith")

def rune_sentinel():
    s = Skin(43, (64, 32))
    BONE = (150, 146, 140); DARK = (72, 70, 74, 255); RUNE = (110, 220, 255, 255); RUNE_L = (200, 245, 255, 255)
    for b in (S_HEAD, S_BODY):
        s.box(b, BONE, 0.14)
        s.speckle(b, DARK, 0.1)
    for b in (S_ARM, S_LEG):
        s.box(b, BONE, 0.14)
        s.speckle(b, DARK, 0.08)
    # a stone helm over the skull: the top and a band round it
    hf = faces(S_HEAD)
    s.rect(hf["top"][0], hf["top"][1], 8, 8, (88, 86, 92, 255), 0.1)
    for name in ("front", "back", "left", "right"):
        x, y, w, h = hf[name]
        s.rect(x, y, w, 2, (88, 86, 92, 255), 0.1)
        s.rect(x, y + 2, w, 1, (60, 58, 64, 255))
    # the face: rune-blue eyes in dark sockets, the ribs' shadow
    fx, fy = hf["front"][:2]
    for (x, y) in [(1, 3), (2, 3), (5, 3), (6, 3), (1, 4), (2, 4), (5, 4), (6, 4)]: s.px(fx + x, fy + y, (30, 30, 36, 255))
    for (x, y) in [(2, 4), (5, 4)]: s.px(fx + x, fy + y, RUNE)
    for (x, y) in [(2, 3), (5, 3)]: s.px(fx + x, fy + y, RUNE_L)
    for x in range(2, 6): s.px(fx + x, fy + 6, (60, 58, 62, 255))
    bx, by = faces(S_BODY)["front"][:2]
    for y in range(1, 11, 2):
        for x in range(1, 7): s.px(bx + x, by + y, shade(BONE + (255,), 0.72))
    # the rune cut into the breastbone and the brow, lit
    for (x, y) in [(3, 4), (4, 4), (3, 5), (4, 5), (3, 6), (4, 6), (3, 7), (4, 7), (2, 5), (5, 5), (2, 7), (5, 7)]: s.px(bx + x, by + y, RUNE)
    s.px(bx + 3, by + 5, RUNE_L); s.px(bx + 4, by + 6, RUNE_L)
    s.px(fx + 3, fy + 1, RUNE); s.px(fx + 4, fy + 1, RUNE)
    # rune marks down the arm bones
    ax, ay, aw, ah = faces(S_ARM)["front"]
    for yy in (ay + 3, ay + 6, ay + 9): s.px(ax, yy, RUNE)
    lx, ly, lw, lh = faces(S_LEG)["front"]
    for yy in (ly + 4, ly + 8): s.px(lx + 1, yy, RUNE)
    s.save("rune_sentinel")

def drowned_keeper():
    s = Skin(47, (64, 64))
    SKIN = (48, 92, 94); DARK = (26, 56, 60, 255); GLOW = (150, 240, 220, 255); BARN = (150, 120, 90, 255)
    for b in (Z_HEAD, Z_BODY, Z_RARM, Z_LARM, Z_RLEG, Z_LLEG):
        s.box(b, SKIN, 0.16)
        s.speckle(b, DARK, 0.1)
        s.speckle(b, (70, 130, 120, 255), 0.05)
    # barnacles and weed on the shoulders and back
    s.speckle(Z_BODY, BARN, 0.06, ("back", "top"))
    s.speckle(Z_HEAD, (60, 110, 70, 255), 0.06, ("top", "back"))
    # the face: pale glowing eyes, a slack mouth
    fx, fy = faces(Z_HEAD)["front"][:2]
    for (x, y) in [(1, 4), (2, 4), (5, 4), (6, 4)]: s.px(fx + x, fy + y, GLOW)
    for (x, y) in [(2, 4), (5, 4)]: s.px(fx + x, fy + y, (230, 255, 250, 255))
    for x in range(2, 6): s.px(fx + x, fy + 6, DARK)
    s.px(fx + 3, fy + 7, DARK)
    # a keeper's collar of prismarine and a belt of chain
    for name in ("front", "back", "left", "right"):
        x, y, w, h = faces(Z_BODY)[name]
        s.rect(x, y, w, 1, (86, 160, 150, 255), 0.1)
        s.rect(x, y + 8, w, 1, (90, 90, 100, 255), 0.2)
    bx, by = faces(Z_BODY)["front"][:2]
    for (x, y) in [(3, 2), (4, 2), (3, 3), (4, 3)]: s.px(bx + x, by + y, GLOW)
    # webbed dark hands and feet
    for b in (Z_RARM, Z_LARM):
        for name in ("front", "back", "left", "right"):
            x, y, w, h = faces(b)[name]
            s.rect(x, y + 10, w, 2, DARK, 0.1)
    for b in (Z_RLEG, Z_LLEG):
        for name in ("front", "back", "left", "right"):
            x, y, w, h = faces(b)[name]
            s.rect(x, y + 10, w, 2, DARK, 0.1)
    s.save("drowned_keeper")

ember_wraith(); rune_sentinel(); drowned_keeper()
print("mob skins written to", OUT)
