"""64x64 humanoid skins for the kingdom's people: three guards, six townsfolk, the king.
python3 tools/textures/skins.py -> resources/assets/wakingworld/textures/entity/kingdom/*.png

Standard skin layout (HumanoidModel: head + hat overlay, body, arms, legs). Detailed: hair in three
tones with a fringe, brows, eyes with a catch-light, cheeks, mouths and beards; tunics with folds,
laced necklines, seams, belts with buckles and pouches, cuffs, gloves, boots with straps; each trade
its own marks (the surveyor's strap, the smith's soot, the chandler's wax, the scribe's inked hand,
the king's chain and pendant). The 3-D cloaks, hats and crowns are in garb.py."""
from PIL import Image
import os, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "entity", "kingdom")
os.makedirs(OUT, exist_ok=True)

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)

def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)

# box layouts: (origin x, origin y, w, h, d) - faces: top (x+d,y) w*d, bottom (x+d+w,y) w*d,
# right (x,y+d) d*h, front (x+d,y+d) w*h, left (x+d+w,y+d) d*h, back (x+d+w+d,y+d) w*h
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
RARM = (40, 16, 4, 12, 4)
LARM = (32, 48, 4, 12, 4)
RLEG = (0, 16, 4, 12, 4)
LLEG = (16, 48, 4, 12, 4)
SIDES = ("front", "back", "left", "right")

def faces(box):
    x, y, w, h, d = box
    return {
        "top": (x + d, y, w, d), "bottom": (x + d + w, y, w, d),
        "right": (x, y + d, d, h), "front": (x + d, y + d, w, h), "left": (x + d + w, y + d, d, h), "back": (x + d + w + d, y + d, w, h),
    }

class Skin:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < 64 and 0 <= y < 64:
            self.im.putpixel((x, y), c)

    def get(self, x, y):
        return self.im.getpixel((x, y))

    def rect(self, x, y, w, h, c, noise=0.0, dark=0.9):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                cc = c
                if noise and self.rnd.random() < noise: cc = shade(c, dark if self.rnd.random() < 0.5 else 1.07)
                self.px(xx, yy, cc)

    def box(self, b, c, noise=0.08, shading=True):
        """Fills all six faces of a box; sides a little darker, bottom darkest."""
        for name, (x, y, w, h) in faces(b).items():
            f = 1.0
            if shading:
                f = {"top": 1.05, "bottom": 0.7, "right": 0.88, "left": 0.88, "back": 0.93, "front": 1.0}[name]
            self.rect(x, y, w, h, shade(c, f), noise)

    def band(self, b, y0, y1, c, faces_=SIDES, noise=0.05):
        """A horizontal band around a box's sides between local rows y0..y1 (from the top of the side faces)."""
        fs = faces(b)
        for name in faces_:
            x, y, w, h = fs[name]
            f = {"right": 0.88, "left": 0.88, "back": 0.93, "front": 1.0}[name]
            self.rect(x, y + y0, w, y1 - y0 + 1, shade(c, f), noise)

    def folds(self, b, c, faces_=("front", "back"), every=3, rows=(1, 11)):
        """Cloth: faint vertical folds and a lighter highlight beside each."""
        fs = faces(b)
        for name in faces_:
            x, y, w, h = fs[name]
            for xx in range(1, w - 1):
                if xx % every == 1:
                    for yy in range(rows[0], min(h, rows[1] + 1)):
                        if (yy + xx) % 5 != 0: self.px(x + xx, y + yy, shade(c, 0.86))
                elif xx % every == 2 and w > 4:
                    for yy in range(rows[0], min(h, rows[1] + 1), 2):
                        self.px(x + xx, y + yy, shade(c, 1.06))

    def seam(self, b, c, row=0):
        for name in SIDES:
            x, y, w, h = faces(b)[name]
            self.rect(x, y + row, w, 1, shade(c, 0.8))

    def save(self, name):
        self.im.save(os.path.join(OUT, name + ".png"))

SKIN_TONES = [(224, 178, 142), (205, 158, 118), (176, 126, 90), (236, 196, 166), (150, 104, 72)]
HAIR = {"brown": (76, 48, 26), "black": (28, 22, 20), "blond": (198, 160, 92), "red": (140, 62, 30), "grey": (150, 148, 140), "white": (226, 222, 210)}

def hair_strands(s, x, y, w, h, hair):
    """Hair with three tones in short vertical strands."""
    s.rect(x, y, w, h, hair, 0.0)
    for xx in range(x, x + w):
        tone = [0.82, 1.0, 1.15, 1.0, 0.9][(xx * 7 + y) % 5]
        for yy in range(y, y + h):
            if (yy + xx) % 3 != 2: s.px(xx, yy, shade(hair, tone))

def head(s, skin, hair, beard=None, hood=None, hat=None, crown=False, eyes=(60, 90, 140), old=False, long_hair=False, fringe="straight"):
    s.box(HEAD, skin, noise=0.04)
    fs = faces(HEAD)
    fx, fy = fs["front"][0], fs["front"][1]
    # cheeks and the jaw a shade darker, the brow a shade lighter
    s.rect(fx, fy + 5, 1, 2, shade(skin, 0.9)); s.rect(fx + 7, fy + 5, 1, 2, shade(skin, 0.9))
    s.rect(fx + 1, fy + 2, 6, 1, shade(skin, 1.04))
    if hair is not None:
        hair_strands(s, fs["top"][0], fs["top"][1], 8, 8, hair)
        for name in ("right", "left", "back"):
            x, y, w, h = fs[name]
            rows = 4 if name == "back" else 3
            if long_hair: rows = 8 if name == "back" else 6
            hair_strands(s, x, y, w, rows, shade(hair, 0.92))
        # the fringe on the front
        if fringe == "straight":
            hair_strands(s, fx, fy, 8, 1, hair)
            s.px(fx, fy + 1, hair); s.px(fx + 7, fy + 1, hair)
        elif fringe == "parted":
            hair_strands(s, fx, fy, 8, 1, hair)
            s.px(fx, fy + 1, hair); s.px(fx + 1, fy + 1, hair); s.px(fx + 7, fy + 1, hair)
        elif fringe == "swept":
            hair_strands(s, fx, fy, 8, 1, hair)
            for xx in range(0, 5): s.px(fx + xx, fy + 1, hair)
            s.px(fx + 7, fy + 1, hair)
        elif fringe == "bald":
            s.rect(fs["top"][0], fs["top"][1], 8, 8, shade(skin, 1.02), 0.03)
            s.rect(fs["top"][0], fs["top"][1], 8, 2, shade(hair, 0.9)); s.rect(fs["top"][0], fs["top"][1] + 6, 8, 2, shade(hair, 0.9))
            s.rect(fs["top"][0], fs["top"][1], 1, 8, shade(hair, 0.9)); s.rect(fs["top"][0] + 7, fs["top"][1], 1, 8, shade(hair, 0.9))
            s.px(fx, fy + 1, hair); s.px(fx + 7, fy + 1, hair)
    # brows in the hair colour, eyes: white, iris, a catch-light on the iris
    brow = shade(hair if hair is not None else skin, 0.75)
    s.rect(fx + 1, fy + 3, 2, 1, brow); s.rect(fx + 5, fy + 3, 2, 1, brow)
    for ex in (2, 5):
        s.px(fx + ex, fy + 4, (250, 250, 250, 255)); s.px(fx + ex + 1, fy + 4, eyes + (255,))
    # the nose, a shadow under it, the mouth
    s.px(fx + 3, fy + 5, shade(skin, 0.9)); s.px(fx + 4, fy + 5, shade(skin, 0.86))
    s.rect(fx + 3, fy + 6, 2, 1, shade(skin, 0.68))
    s.px(fx + 3, fy + 6, mix(shade(skin, 0.68), (170, 80, 80), 0.35))
    if old:
        s.px(fx + 1, fy + 5, shade(skin, 0.86)); s.px(fx + 6, fy + 5, shade(skin, 0.86))
        s.px(fx + 2, fy + 2, shade(skin, 0.9)); s.px(fx + 5, fy + 2, shade(skin, 0.9))
    if beard is not None:
        s.rect(fx + 1, fy + 6, 6, 2, beard, 0.0)
        for xx in range(1, 7):
            s.px(fx + xx, fy + 7, shade(beard, 0.85 if xx % 2 else 1.05))
        s.rect(fx + 2, fy + 5, 1, 1, beard); s.rect(fx + 5, fy + 5, 1, 1, beard)
        s.rect(fx + 3, fy + 6, 2, 1, shade(beard, 0.55))  # the mouth in the beard
        for name in ("right", "left"):
            x, y, w, h = fs[name]
            s.rect(x + (w - 3 if name == "right" else 0), y + 6, 3, 2, shade(beard, 0.9), 0.15)
        # the beard hangs under the chin
        bx, by, bw, bh = fs["bottom"]
        s.rect(bx + 1, by, 6, 3, shade(beard, 0.7), 0.1)
    # hat layer: a hood (all round), a cap (top + upper sides), or a crown (a ring near the top)
    if hood is not None:
        s.box(HAT, hood, noise=0.08)
        hf = faces(HAT)["front"]
        for yy in range(2, 8):
            for xx in range(1, 7):
                s.px(hf[0] + xx, hf[1] + yy, (0, 0, 0, 0))
        # the hood's shadow on the face
        s.rect(fx + 1, fy + 2, 6, 1, shade(skin, 0.8))
    if hat is not None:
        hf = faces(HAT)
        s.rect(hf["top"][0], hf["top"][1], 8, 8, hat, 0.1)
        for name in SIDES:
            x, y, w, h = hf[name]
            s.rect(x, y, w, 3, shade(hat, 0.9 if name != "front" else 1.0), 0.1)
        x, y, w, h = hf["front"]
        s.rect(x, y + 3, w, 1, shade(hat, 0.75))
    if crown:
        gold = (232, 190, 70, 255); gold_d = (160, 120, 30, 255)
        hf = faces(HAT)
        for name in SIDES:
            x, y, w, h = hf[name]
            s.rect(x, y + 1, w, 2, gold, 0.1)
            s.rect(x, y + 3, w, 1, gold_d)

def body(s, tunic, belt=None, trim=None, apron=None, collar=None, lace=None, chain=False, pendant=None, strap=None, sigils=None, soot=False):
    s.box(BODY, tunic, noise=0.05)
    s.folds(BODY, tunic, ("front", "back"), 3, (1, 10))
    s.folds(BODY, tunic, ("left", "right"), 2, (1, 10))
    s.seam(BODY, tunic, 0)
    fs = faces(BODY)
    x, y, w, h = fs["front"]
    if trim is not None:
        s.band(BODY, 11, 11, trim)
        s.rect(x + 3, y, 2, 12, trim, 0.05)
        s.rect(x + 3, y, 2, 1, shade(trim, 0.85))
    if lace is not None:
        # a laced neckline: a V of the undershirt with cross-laces
        s.rect(x + 3, y + 1, 2, 3, lace); s.px(x + 2, y + 1, lace); s.px(x + 5, y + 1, lace)
        s.px(x + 3, y + 2, shade(lace, 0.6)); s.px(x + 4, y + 3, shade(lace, 0.6))
    if belt is not None:
        s.band(BODY, 7, 8, belt)
        s.band(BODY, 8, 8, shade(belt, 0.85))
        s.px(x + 3, y + 7, (222, 188, 90, 255)); s.px(x + 4, y + 7, (222, 188, 90, 255)); s.px(x + 3, y + 8, (170, 130, 40, 255)); s.px(x + 4, y + 8, (170, 130, 40, 255))
        # a pouch on the right hip
        lx, ly, lw, lh = fs["left"]
        s.rect(lx + 1, ly + 8, 2, 2, shade(belt, 0.8)); s.px(lx + 1, ly + 8, shade(belt, 1.1))
    if apron is not None:
        s.rect(x + 1, y + 3, 6, 9, apron, 0.06)
        s.rect(x + 2, y + 2, 4, 1, apron)
        s.rect(x + 1, y + 3, 1, 9, shade(apron, 0.85)); s.rect(x + 6, y + 3, 1, 9, shade(apron, 0.85))
        s.rect(x + 2, y + 8, 4, 1, shade(apron, 0.8)); s.px(x + 2, y + 9, shade(apron, 0.8)); s.px(x + 5, y + 9, shade(apron, 0.8))  # a pocket
        if soot:
            for (dx, dy) in [(2, 5), (5, 6), (3, 10), (6, 4)]: s.px(x + dx, y + dy, (46, 40, 38, 255))
    if collar is not None:
        s.band(BODY, 0, 0, collar)
        s.px(x + 3, y + 1, collar); s.px(x + 4, y + 1, collar)
    if chain:
        for xx in range(0, 8):
            if xx % 2 == 0: s.px(x + xx, y + 1 + (1 if 2 <= xx <= 5 else 0), (232, 190, 70, 255))
    if pendant is not None:
        s.px(x + 3, y + 4, pendant); s.px(x + 4, y + 4, pendant); s.px(x + 3, y + 5, shade(pendant, 0.7)); s.px(x + 4, y + 5, shade(pendant, 0.7))
    if strap is not None:
        # a strap from the left shoulder to the right hip, front and back
        for name in ("front", "back"):
            bx, by, bw, bh = fs[name]
            for k in range(0, 8):
                yy = by + k
                xx = bx + (7 - k) if name == "front" else bx + k
                if 0 <= xx - bx < 8 and k < 8: s.px(xx, yy, strap); s.px(xx, yy + 1, shade(strap, 0.8))
    if sigils is not None:
        for (dx, dy) in [(1, 3), (6, 5), (2, 9), (5, 10)]:
            s.px(x + dx, y + dy, sigils); s.px(x + dx, y + dy + 1, sigils)
        bx, by, bw, bh = fs["back"]
        s.rect(bx + 3, by + 3, 2, 2, sigils); s.px(bx + 2, by + 4, sigils); s.px(bx + 5, by + 4, sigils)

def arms(s, sleeve, skin, cuff=None, bare_from=None, glove=None, ink=False, wax=False):
    for i, b in enumerate((RARM, LARM)):
        s.box(b, sleeve, noise=0.05)
        s.folds(b, sleeve, ("front", "back"), 2, (1, 9))
        s.seam(b, sleeve, 0)
        fs = faces(b)
        for name in SIDES:
            x, y, w, h = fs[name]
            s.rect(x, y + 10, w, 2, shade(skin, 0.95 if name == "front" else 0.85))
            # knuckles
            if name == "front": s.px(x + 1, y + 10, shade(skin, 0.88)); s.px(x + 2, y + 10, shade(skin, 0.88))
        s.rect(fs["bottom"][0], fs["bottom"][1], 4, 4, shade(skin, 0.8))
        if bare_from is not None:
            for name in SIDES:
                x, y, w, h = fs[name]
                s.rect(x, y + bare_from, w, 10 - bare_from, shade(skin, 0.95 if name == "front" else 0.85), 0.04)
            s.band(b, bare_from - 1, bare_from - 1, shade(sleeve, 0.8))  # the rolled edge
        if cuff is not None:
            s.band(b, 8, 9, cuff)
            s.band(b, 9, 9, shade(cuff, 0.85))
        if glove is not None:
            s.band(b, 9, 11, glove)
            s.rect(fs["bottom"][0], fs["bottom"][1], 4, 4, shade(glove, 0.8))
        if ink and i == 0:
            x, y, w, h = fs["front"]
            s.px(x + 2, y + 11, (30, 34, 60, 255)); s.px(x + 3, y + 10, (30, 34, 60, 255))
        if wax:
            for name in ("front", "left"):
                x, y, w, h = fs[name]
                s.px(x + 1, y + 8, (236, 226, 200, 255)); s.px(x + 1, y + 9, (236, 226, 200, 255)); s.px(x + 3, y + 9, (236, 226, 200, 255))

def legs(s, hose, boot, boot_from=8, garter=None):
    for b in (RLEG, LLEG):
        s.box(b, hose, noise=0.05)
        s.folds(b, hose, ("front", "back"), 2, (1, boot_from - 1))
        s.band(b, 5, 5, shade(hose, 0.9))  # the knee
        s.band(b, boot_from, 11, boot)
        s.band(b, boot_from, boot_from, shade(boot, 0.8))
        s.band(b, boot_from + 2, boot_from + 2, shade(boot, 0.85))  # the strap
        fs = faces(b)
        x, y, w, h = fs["front"]
        s.px(x + 1, y + boot_from + 2, (190, 160, 80, 255))  # its buckle
        s.rect(fs["bottom"][0], fs["bottom"][1], 4, 4, shade(boot, 0.7))
        if garter is not None: s.band(b, boot_from - 1, boot_from - 1, garter)

def make(name, seed, skin_i, hair_c, tunic, hose, boot, sleeve=None, belt=None, trim=None, apron=None, hood=None, hat=None,
         beard=None, crown=False, collar=None, cuff=None, old=False, eyes=(60, 90, 140), bare_from=None, boot_from=8,
         lace=None, chain=False, pendant=None, strap=None, sigils=None, soot=False, glove=None, ink=False, wax=False, garter=None,
         long_hair=False, fringe="straight"):
    s = Skin(seed)
    skin = SKIN_TONES[skin_i % len(SKIN_TONES)]
    head(s, skin, hair_c, beard=beard, hood=hood, hat=hat, crown=crown, eyes=eyes, old=old, long_hair=long_hair, fringe=fringe)
    body(s, tunic, belt=belt, trim=trim, apron=apron, collar=collar, lace=lace, chain=chain, pendant=pendant, strap=strap, sigils=sigils, soot=soot)
    arms(s, sleeve if sleeve is not None else tunic, skin, cuff=cuff, bare_from=bare_from, glove=glove, ink=ink, wax=wax)
    legs(s, hose, boot, boot_from=boot_from, garter=garter)
    s.save(name)

CYAN = (52, 128, 140); CYAN_D = (34, 90, 100); GOLD = (214, 176, 70); LEATHER = (110, 74, 40); LEATHER_D = (78, 52, 28)
GREY = (120, 120, 126); GREY_D = (86, 86, 92); BROWN = (96, 64, 36); CREAM = (222, 208, 176); WHITE = (238, 236, 228)

# ---- guards: cyan-and-gold livery of the kingdom over what the armour leaves bare ----
make("guard_archer", 11, 1, HAIR["brown"], tunic=(70, 110, 60), hose=(60, 50, 40), boot=LEATHER_D, sleeve=(70, 110, 60), belt=LEATHER, hood=(52, 84, 46), cuff=LEATHER, eyes=(70, 100, 60), lace=CREAM, glove=LEATHER_D, fringe="swept")
make("guard_knight", 12, 3, HAIR["black"], tunic=CYAN, hose=GREY_D, boot=GREY_D, sleeve=GREY, belt=LEATHER_D, trim=GOLD, collar=GREY, glove=GREY_D, fringe="parted")
make("guard_spearman", 13, 0, HAIR["blond"], tunic=CYAN_D, hose=(70, 62, 54), boot=GREY_D, sleeve=GREY, belt=LEATHER, trim=GOLD, beard=HAIR["blond"], glove=LEATHER_D, lace=CREAM)

# ---- townsfolk: each trade its own coat ----
make("townsfolk_surveyor", 21, 2, HAIR["brown"], tunic=(56, 76, 130), hose=(64, 52, 40), boot=LEATHER_D, sleeve=(56, 76, 130), belt=LEATHER, cuff=(150, 130, 90), eyes=(80, 60, 40), strap=LEATHER_D, lace=CREAM, fringe="swept")
make("townsfolk_relic_monger", 22, 4, HAIR["black"], tunic=(88, 50, 120), hose=(40, 30, 50), boot=(30, 24, 30), sleeve=(88, 50, 120), belt=GOLD, hood=(60, 34, 84), trim=(150, 110, 170), eyes=(150, 110, 60), sigils=(190, 150, 220), pendant=(120, 220, 255), long_hair=True)
make("townsfolk_smith", 23, 1, HAIR["red"], tunic=(120, 60, 40), hose=(60, 50, 44), boot=(40, 32, 26), sleeve=(120, 60, 40), apron=(70, 56, 46), beard=HAIR["red"], bare_from=4, soot=True, fringe="bald")
make("townsfolk_provisioner", 24, 3, HAIR["brown"], tunic=(84, 128, 62), hose=(80, 66, 50), boot=LEATHER_D, sleeve=CREAM, apron=WHITE, belt=LEATHER, eyes=(60, 110, 70), lace=CREAM, fringe="parted")
make("townsfolk_chandler", 25, 0, HAIR["blond"], tunic=(196, 160, 72), hose=(96, 78, 56), boot=LEATHER_D, sleeve=(196, 160, 72), belt=BROWN, trim=(232, 210, 140), eyes=(90, 120, 150), wax=True, long_hair=True, fringe="swept")
make("townsfolk_scribe", 26, 2, HAIR["grey"], tunic=(40, 50, 92), hose=(40, 40, 60), boot=(30, 28, 34), sleeve=(40, 50, 92), belt=(120, 110, 90), collar=WHITE, old=True, eyes=(70, 70, 90), ink=True, cuff=WHITE)

# ---- the king: purple and gold, a white beard, the crown ----
make("king", 31, 3, HAIR["white"], tunic=(96, 40, 120), hose=(60, 30, 80), boot=(40, 28, 22), sleeve=(96, 40, 120), belt=GOLD, trim=GOLD, beard=HAIR["white"], crown=True, collar=(240, 236, 230), cuff=GOLD, old=True, eyes=(90, 100, 130), chain=True, pendant=(200, 40, 60), garter=GOLD, long_hair=True)
print("skins written to", OUT)
