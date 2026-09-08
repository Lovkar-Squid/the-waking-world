"""The dark mage's skin and its glow map, for the built model in client/MageModel.java.

The boxes and their texture offsets here MUST agree with the model, cube for cube - a box is laid
out the way Minecraft lays one out (top, bottom, right, front, left, back), which faces() does.

python3 tools/textures/mage.py
  -> resources/assets/wakingworld/textures/entity/mage/dark_mage.png       (128x128)
  -> resources/assets/wakingworld/textures/entity/mage/dark_mage_glow.png  (128x128, the lit parts only)
"""
from PIL import Image
import os, math, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "entity", "mage")
os.makedirs(OUT, exist_ok=True)
S = 128


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


def faces(box):
    """A cube's six panels in the vanilla layout. box = (u, v, w, h, d) as in the model's addBox."""
    x, y, w, h, d = box
    return {
        "top": (x + d, y, w, d),
        "bottom": (x + d + w, y, w, d),
        "right": (x, y + d, d, h),
        "front": (x + d, y + d, w, h),
        "left": (x + d + w, y + d, d, h),
        "back": (x + d + w + d, y + d, w, h),
    }


LIGHT = {"top": 1.16, "bottom": 0.62, "right": 0.86, "left": 0.92, "front": 1.0, "back": 0.80}


class Sheet:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def px(self, x, y, c, glow=None):
        if 0 <= x < S and 0 <= y < S:
            self.im.putpixel((int(x), int(y)), tuple(int(v) for v in c))
            if glow is not None:
                self.glow.putpixel((int(x), int(y)), tuple(int(v) for v in glow))

    def get(self, x, y):
        if 0 <= x < S and 0 <= y < S:
            return self.im.getpixel((int(x), int(y)))
        return (0, 0, 0, 0)

    def rect(self, x, y, w, h, c, noise=0.0):
        for yy in range(int(y), int(y + h)):
            for xx in range(int(x), int(x + w)):
                cc = c
                if noise and self.rnd.random() < noise:
                    cc = shade(c, 0.86 if self.rnd.random() < 0.5 else 1.14)
                self.px(xx, yy, cc)

    def box(self, b, c, noise=0.12, skip=()):
        for name, (x, y, w, h) in faces(b).items():
            if name in skip:
                continue
            self.rect(x, y, w, h, shade(c, LIGHT[name]), noise)

    def panel(self, b, name):
        return faces(b)[name]

    def lit(self, x, y, c):
        """A pixel that burns: it goes on the skin AND on the glow map."""
        self.px(x, y, c, c)

    def litrect(self, x, y, w, h, c):
        for yy in range(int(y), int(y + h)):
            for xx in range(int(x), int(x + w)):
                self.lit(xx, yy, c)


# ---- the palette ---------------------------------------------------------------------------------
CLOTH = (31, 27, 40)          # the robe: not black, a very dark cold violet
CLOTH_HI = (49, 43, 66)
CLOTH_LO = (18, 15, 24)
MANTLE = (44, 24, 56)         # the shoulder cape, a shade warmer
TRIM = (122, 88, 42)          # old brass thread
TRIM_HI = (176, 138, 78)
VOID = (7, 6, 11)             # what is inside the hood
EYE = (196, 118, 255)
EYE_HOT = (238, 208, 255)
RUNE = (150, 84, 232)
BONE = (176, 168, 150)        # the hands
STAFF_W = (48, 38, 34)
CRYSTAL = (168, 96, 246)
CRYSTAL_HOT = (232, 196, 255)

# ---- the boxes, exactly as the model declares them ------------------------------------------------
HEAD = (0, 0, 9, 9, 9)
PEAK = (36, 0, 6, 7, 9)
MANTLE_B = (66, 0, 12, 5, 8)
BODY = (0, 20, 8, 13, 5)
SKIRT = (28, 20, 10, 7, 7)
HEM = (64, 20, 13, 5, 9)
CLOAK = (0, 40, 10, 22, 1)
R_ARM = (24, 40, 4, 12, 4)
R_CUFF = (42, 40, 5, 4, 5)
L_ARM = (64, 40, 4, 12, 4)
L_CUFF = (82, 40, 5, 4, 5)
STAFF = (104, 40, 1, 26, 1)
CRYSTAL_B = (0, 66, 4, 4, 4)
ORB = (18, 66, 3, 3, 3)

s = Sheet(20260908)

# ---- the hood ------------------------------------------------------------------------------------
s.box(HEAD, CLOTH, noise=0.16)
# the fabric falls in folds: a couple of darker creases down each side of the hood
for name in ("left", "right", "back"):
    x, y, w, h = s.panel(HEAD, name)
    for k in (2, 5, 7):
        for yy in range(y + 1, y + h):
            s.px(x + k, yy, shade(CLOTH_LO, LIGHT[name] * (1.0 + 0.06 * math.sin(yy * 0.9))))
# the front is the opening: a black hole with two lights a long way inside it
fx, fy, fw, fh = s.panel(HEAD, "front")
for yy in range(fh):
    for xx in range(fw):
        # an arch: wide at the bottom, closing over the top
        dx = abs(xx - (fw - 1) / 2.0)
        top = 1.8 + 1.5 * (dx / (fw / 2.0)) ** 2
        if yy >= top and dx <= 3.1:
            d = 1.0 - min(1.0, (dx / 3.1) ** 2 * 0.5 + (fh - yy) / (fh * 2.2))
            s.px(fx + xx, fy + yy, mix(VOID, (16, 12, 22), d * 0.5))
# the rim of the hood catches the light
for xx in range(fw):
    dx = abs(xx - (fw - 1) / 2.0)
    top = int(1.8 + 1.5 * (dx / (fw / 2.0)) ** 2)
    if dx <= 3.6:
        s.px(fx + xx, fy + top - 1, shade(CLOTH_HI, 1.05))
# the eyes
for (ex, ey) in ((3, 5), (5, 5)):
    s.lit(fx + ex, fy + ey, EYE)
    s.lit(fx + ex, fy + ey - 1, shade(EYE, 0.55))
s.lit(fx + 3, fy + 5, EYE_HOT)
s.lit(fx + 5, fy + 5, EYE_HOT)
# a brass line round the hood's edge
x, y, w, h = s.panel(HEAD, "top")
s.rect(x, y, w, 1, shade(TRIM, 1.1))

# ---- the hood's point ----------------------------------------------------------------------------
s.box(PEAK, CLOTH_LO, noise=0.18)
x, y, w, h = s.panel(PEAK, "top")
s.rect(x, y + h - 2, w, 1, shade(TRIM, 0.9))

# ---- the mantle ----------------------------------------------------------------------------------
s.box(MANTLE_B, MANTLE, noise=0.14)
for name in ("front", "back"):
    x, y, w, h = s.panel(MANTLE_B, name)
    s.rect(x, y + h - 1, w, 1, shade(TRIM, LIGHT[name]))
    s.rect(x, y, w, 1, shade(TRIM_HI, LIGHT[name] * 0.8))
    # a row of small studs along the shoulder
    for k in range(1, w, 3):
        s.px(x + k, y + 1, shade(TRIM_HI, LIGHT[name]))

# ---- the robe ------------------------------------------------------------------------------------
s.box(BODY, CLOTH, noise=0.15)
fx, fy, fw, fh = s.panel(BODY, "front")
# a seam of brass down the middle, and three runes burning quietly on it
for yy in range(fh):
    s.px(fx + fw // 2 - 1, fy + yy, shade(TRIM, 0.85))
    s.px(fx + fw // 2, fy + yy, shade(TRIM_HI, 0.9))
for k, yy in enumerate((3, 6, 9)):
    s.lit(fx + fw // 2, fy + yy, RUNE)
    s.lit(fx + fw // 2 - 1, fy + yy, shade(RUNE, 0.7))
bx, by, bw, bh = s.panel(BODY, "back")
for yy in range(1, bh, 3):
    s.rect(bx + 1, by + yy, bw - 2, 1, shade(CLOTH_LO, LIGHT["back"]))

# ---- the skirt and the hem -----------------------------------------------------------------------
s.box(SKIRT, CLOTH, noise=0.15)
s.box(HEM, CLOTH_LO, noise=0.18)
for b, c in ((SKIRT, CLOTH_LO), (HEM, CLOTH_LO)):
    for name in ("front", "back", "left", "right"):
        x, y, w, h = s.panel(b, name)
        for k in range(1, w, 3):
            for yy in range(y, y + h):
                s.px(x + k, yy, shade(c, LIGHT[name] * 0.9))
# the hem's edge is worn and trailing
for name in ("front", "back", "left", "right"):
    x, y, w, h = s.panel(HEM, name)
    for xx in range(w):
        if (xx * 7) % 5 < 2:
            s.px(x + xx, y + h - 1, (0, 0, 0, 0))
        else:
            s.px(x + xx, y + h - 1, shade(CLOTH_LO, 0.6))
# a burning line round the hem: the only bright thing at floor level
x, y, w, h = s.panel(HEM, "front")
for xx in range(1, w - 1, 2):
    s.lit(x + xx, y + 1, shade(RUNE, 0.85))
x, y, w, h = s.panel(HEM, "back")
for xx in range(1, w - 1, 2):
    s.lit(x + xx, y + 1, shade(RUNE, 0.6))

# ---- the cloak -----------------------------------------------------------------------------------
s.box(CLOAK, CLOTH_LO, noise=0.2)
x, y, w, h = s.panel(CLOAK, "back")
for yy in range(h):
    for xx in range(w):
        if (xx + yy // 3) % 4 == 0:
            s.px(x + xx, y + yy, shade(CLOTH, LIGHT["back"] * 1.1))
# a sigil between the shoulder blades
cx0, cy0 = x + w // 2, y + 5
for (dx, dy) in ((0, -2), (0, -1), (0, 0), (0, 1), (0, 2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 2), (1, 2)):
    s.lit(cx0 + dx, cy0 + dy, RUNE)
for xx in range(w):
    if (xx * 5) % 3 == 0:
        s.px(x + xx, y + h - 1, (0, 0, 0, 0))

# ---- the sleeves and the hands -------------------------------------------------------------------
for arm, cuff, sign in ((R_ARM, R_CUFF, -1), (L_ARM, L_CUFF, 1)):
    s.box(arm, CLOTH, noise=0.15)
    s.box(cuff, MANTLE, noise=0.14)
    for name in ("front", "back", "left", "right"):
        x, y, w, h = s.panel(cuff, name)
        s.rect(x, y, w, 1, shade(TRIM, LIGHT[name]))
    # the hand looking out of the cuff: the underside of the cuff box is the palm
    x, y, w, h = s.panel(cuff, "bottom")
    s.rect(x + 1, y + 1, max(1, w - 2), max(1, h - 2), shade(BONE, 0.9), noise=0.2)

# ---- the staff -----------------------------------------------------------------------------------
s.box(STAFF, STAFF_W, noise=0.25)
for name in ("front", "back", "left", "right"):
    x, y, w, h = s.panel(STAFF, name)
    for yy in range(y, y + h, 4):
        s.px(x, yy, shade(TRIM, LIGHT[name]))
    for yy in range(y, y + 4):
        s.lit(x, yy, shade(RUNE, 0.8))          # the head of it is bound in something live

# ---- the crystal and the orbiting stone ----------------------------------------------------------
for name, (x, y, w, h) in faces(CRYSTAL_B).items():
    for yy in range(h):
        for xx in range(w):
            edge = xx in (0, w - 1) or yy in (0, h - 1)
            c = shade(CRYSTAL, LIGHT[name] * (0.8 if edge else 1.0))
            if not edge:
                c = CRYSTAL_HOT if (xx + yy) % 2 == 0 else c
            s.lit(x + xx, y + yy, c)
for name, (x, y, w, h) in faces(ORB).items():
    for yy in range(h):
        for xx in range(w):
            mid = (xx == w // 2 and yy == h // 2)
            c = EYE_HOT if mid else shade(RUNE, LIGHT[name])
            s.lit(x + xx, y + yy, c)

# the glow map must be black where nothing burns: RenderType.eyes adds, it does not blend
glow = Image.new("RGBA", (S, S), (0, 0, 0, 255))
glow.paste(s.glow, (0, 0), s.glow)

s.im.save(os.path.join(OUT, "dark_mage.png"))
glow.save(os.path.join(OUT, "dark_mage_glow.png"))
print("wrote", os.path.join(OUT, "dark_mage.png"), "and its glow map")
