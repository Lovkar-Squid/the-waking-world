"""Star iron kit: five tools, four pieces of armour, and the two layers the player wears.

   python3 tools/textures/stariron.py

Items are 32x32, like every other item in the mod. The metal is the worked form of the nugget in
starstone.py: a dark, close-grained iron that came down through the atmosphere, so it does not
burn and it has never quite gone out - a hairline of the fall's heat still runs along the forging.

How a piece is drawn, every time:
  1. a silhouette, as polygons and thick segments (`poly`, `thick`);
  2. `forge` shades it - a distance field inside the mask becomes a rounded bevel, the bevel
     becomes a normal, the normal takes a light, and the light lands on a five-tone ramp;
  3. details go on top by hand - a working edge brought to white, a hairline seam of heat, rivets,
     plate divisions - because at 32 pixels, deliberate detail reads and noise does not.
"""
from PIL import Image
import os, math, random

HERE = os.path.dirname(__file__)
ITEM = os.path.join(HERE, "..", "..", "resources", "assets", "wakingworld", "textures", "item")
ARMOR = os.path.join(HERE, "..", "..", "resources", "assets", "wakingworld", "textures", "models", "armor")
os.makedirs(ITEM, exist_ok=True)
os.makedirs(ARMOR, exist_ok=True)
rnd = random.Random(20260907)

S = 32
SUB = 4

# the ramp, darkest to brightest - five tones is what reads at this size
T0 = (12, 13, 20)
T1 = (25, 27, 39)
T2 = (43, 47, 65)
T3 = (72, 79, 103)
T4 = (124, 134, 165)
T5 = (214, 224, 243)
RAMP = [T0, T1, T2, T3, T4, T5]

EMBER_L = (140, 42, 10)
EMBER = (255, 132, 22)
EMBER_H = (255, 238, 186)
STAR = (232, 238, 255)
WOOD = (82, 57, 33)
WOOD_D = (49, 34, 20)
WOOD_L = (121, 89, 53)
WOOD_H = (150, 114, 70)
CORD = (42, 36, 32)
LINE = (8, 7, 12, 255)

LIGHT = (-0.52, -0.66, 0.54)                       # up and to the left, a little towards the viewer
_L = math.sqrt(sum(v * v for v in LIGHT))
LIGHT = tuple(v / _L for v in LIGHT)


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


def ramp(v, table=RAMP):
    """A continuous 0..1 into the tone ramp, with the steps softened just enough to avoid banding."""
    v = max(0.0, min(0.999, v)) * (len(table) - 1)
    i = int(v)
    return mix(table[i], table[min(i + 1, len(table) - 1)], v - i)


def img(w=S, h=S):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((int(x), int(y)), c if len(c) == 4 else c + (255,))


def get(im, x, y):
    if 0 <= x < im.width and 0 <= y < im.height:
        return im.getpixel((int(x), int(y)))
    return (0, 0, 0, 0)


def outline(im, color=LINE):
    body = {(x, y) for y in range(im.height) for x in range(im.width) if get(im, x, y)[3] > 0}
    for (x, y) in body:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in body:
                px(im, x + dx, y + dy, color)


# ---- silhouettes --------------------------------------------------------------------------------
def poly(points, w=S, h=S):
    inside = set()
    for y in range(h):
        for x in range(w):
            hits = 0
            for sy in range(SUB):
                for sx in range(SUB):
                    fx, fy = x + (sx + 0.5) / SUB, y + (sy + 0.5) / SUB
                    c = False
                    n = len(points)
                    for i in range(n):
                        x0, y0 = points[i]
                        x1, y1 = points[(i + 1) % n]
                        if (y0 > fy) != (y1 > fy) and fx < (x1 - x0) * (fy - y0) / (y1 - y0) + x0:
                            c = not c
                    if c:
                        hits += 1
            if hits >= SUB * SUB * 0.5:
                inside.add((x, y))
    return inside


def thick(x0, y0, x1, y1, half, w=S, h=S):
    inside = set()
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    for y in range(h):
        for x in range(w):
            hits = 0
            for sy in range(SUB):
                for sx in range(SUB):
                    fx, fy = x + (sx + 0.5) / SUB - x0, y + (sy + 0.5) / SUB - y0
                    t = max(0.0, min(length, fx * ux + fy * uy))
                    if math.hypot(fx - ux * t, fy - uy * t) <= half:
                        hits += 1
            if hits >= SUB * SUB * 0.5:
                inside.add((x, y))
    return inside


def ellipse(cx, cy, rx, ry, w=S, h=S):
    return {(x, y) for y in range(h) for x in range(w)
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0}


def field(mask, w=S, h=S):
    """Distance from each pixel of the mask to the nearest pixel outside it (chamfer, good enough)."""
    BIG = 1e6
    d = [[0.0 if (x, y) not in mask else BIG for x in range(w)] for y in range(h)]
    for y in range(h):
        for x in range(w):
            if d[y][x] == 0:
                continue
            for dx, dy, c in ((-1, 0, 1.0), (0, -1, 1.0), (-1, -1, 1.414), (1, -1, 1.414)):
                nx, ny = x + dx, y + dy
                v = (d[ny][nx] if 0 <= nx < w and 0 <= ny < h else 0.0) + c
                if v < d[y][x]:
                    d[y][x] = v
    for y in range(h - 1, -1, -1):
        for x in range(w - 1, -1, -1):
            if d[y][x] == 0:
                continue
            for dx, dy, c in ((1, 0, 1.0), (0, 1, 1.0), (1, 1, 1.414), (-1, 1, 1.414)):
                nx, ny = x + dx, y + dy
                v = (d[ny][nx] if 0 <= nx < w and 0 <= ny < h else 0.0) + c
                if v < d[y][x]:
                    d[y][x] = v
    return d


def seg_dist(p, a, b):
    (x, y), (x0, y0), (x1, y1) = p, a, b
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / L2))
    return math.hypot(x - (x0 + dx * t), y - (y0 + dy * t)), t


# ---- shading ------------------------------------------------------------------------------------
def forge(im, mask, bevel=2.6, base=0.10, gain=0.62, w=S, h=S, grain=0.06, palette=RAMP):
    """Metal: a rounded bevel out of the distance field, lit, onto the ramp."""
    d = field(mask, w, h)

    def height(x, y):
        if (x, y) not in mask:
            return 0.0
        return math.sin(min(1.0, d[y][x] / bevel) * math.pi / 2)

    for (x, y) in mask:
        hx = (height(x + 1, y) - height(x - 1, y)) * 0.5
        hy = (height(x, y + 1) - height(x, y - 1)) * 0.5
        nx, ny, nz = -hx * 2.4, -hy * 2.4, 1.0
        n = math.sqrt(nx * nx + ny * ny + nz * nz)
        lam = (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / n
        v = base + gain * lam
        # the crown of a thick piece stays mid-tone rather than blowing out
        v -= 0.10 * min(1.0, d[y][x] / (bevel * 2.2))
        if grain and rnd.random() < grain:
            v += 0.05 if rnd.random() < 0.5 else -0.05
        px(im, x, y, ramp(v, palette))


def timber(im, mask, w=S, h=S):
    d = field(mask, w, h)
    for (x, y) in mask:
        t = min(1.0, d[y][x] / 2.0)
        c = mix(WOOD_H, WOOD, t) if d[y][x] < 1.6 else WOOD
        # the shadow side: down and to the right
        below = (x + 1, y + 1) not in mask or (x + 1, y) not in mask
        if below and d[y][x] < 1.6:
            c = WOOD_D
        if rnd.random() < 0.20:
            c = mix(c, WOOD_L if rnd.random() < 0.5 else WOOD_D, 0.4)
        px(im, x, y, c)


def rimlight(im, mask, strength=0.75, light=(-0.55, -0.72), tone=T5):
    """The one-pixel border facing the light, brought up. Dark metal needs this to have a shape."""
    for (x, y) in mask:
        if all((x + dx, y + dy) in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            continue
        nx = ny = 0.0
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            if (x + dx, y + dy) not in mask:
                nx += dx
                ny += dy
        n = math.hypot(nx, ny)
        if not n:
            continue
        face = nx / n * light[0] + ny / n * light[1]
        if face > 0.30:
            px(im, x, y, mix(get(im, x, y)[:3], tone, (face - 0.30) / 0.70 * strength))
        elif face < -0.45:
            px(im, x, y, mix(get(im, x, y)[:3], T0, (-face - 0.45) / 0.55 * 0.55))


def edge(im, mask, a, b, reach=1.7, strength=1.0):
    """A working edge: the metal along this segment is thin, so it takes the light hardest."""
    for (x, y) in mask:
        dist, t = seg_dist((x + 0.5, y + 0.5), a, b)
        if dist < reach:
            k = (1.0 - dist / reach) ** 1.4 * strength
            px(im, x, y, mix(get(im, x, y)[:3], T5, k * 0.92))


def seam(im, mask, a, b, hot=1.0, width=0.55, bleed=1.5):
    """The hairline of heat still in the forging. Thin on purpose - it is a crack, not a lamp."""
    for (x, y) in mask:
        dist, t = seg_dist((x + 0.5, y + 0.5), a, b)
        if dist > bleed:
            continue
        along = math.sin(max(0.0, min(1.0, t)) * math.pi) ** 0.6
        g = hot * along
        c = get(im, x, y)[:3]
        if dist <= width:
            c = mix(c, mix(EMBER, EMBER_H, 0.55 * g), 0.94 * g)
        elif dist <= 1.05:
            c = mix(c, EMBER, 0.52 * g)
        else:
            c = mix(c, EMBER_L, 0.18 * g)
        px(im, x, y, c)


def sparks(im, mask, n=3, seed=None):
    """A few pale points, because it is sky-metal and somebody should notice."""
    r = random.Random(seed) if seed is not None else rnd
    pool = sorted(mask)
    for _ in range(n):
        x, y = pool[r.randrange(len(pool))]
        px(im, x, y, mix(get(im, x, y)[:3], STAR, 0.75))


def rivet(im, x, y, bright=T4, dark=T1):
    px(im, x, y, bright)
    px(im, x + 1, y, mix(bright, dark, 0.55))
    px(im, x, y + 1, mix(bright, dark, 0.55))
    px(im, x + 1, y + 1, dark)


def groove(im, mask, a, b, reach=0.75, tone=0.42):
    """A plate division: one dark pixel line with a lit lip above it."""
    for (x, y) in mask:
        dist, _ = seg_dist((x + 0.5, y + 0.5), a, b)
        if dist < reach:
            px(im, x, y, mix(get(im, x, y)[:3], T0, tone))
        elif dist < reach + 1.0 and (x, y - 1) in mask:
            d2, _ = seg_dist((x + 0.5, y - 0.5), a, b)
            if d2 < reach:
                px(im, x, y, mix(get(im, x, y)[:3], T4, 0.35))


# ---- the five tools ------------------------------------------------------------------------------
HX0, HY0, HX1, HY1 = 8.0, 28.2, 20.6, 12.6


def with_haft(im, trim=None, half=1.7):
    m = thick(HX0, HY0, HX1, HY1, half)
    if trim:
        m = m - trim
    timber(im, m)
    for at, run in ((0.30, 1.3), (0.60, 1.1)):
        for (x, y) in m:
            dist, t = seg_dist((x + 0.5, y + 0.5), (HX0, HY0), (HX1, HY1))
            if abs(t - at) * math.hypot(HX1 - HX0, HY1 - HY0) < run:
                px(im, x, y, mix(CORD, T2, 0.25 if (x + y) % 2 else 0.0))
    return m


def sword():
    im = img()
    grip = thick(5.8, 28.2, 11.6, 22.4, 1.35)
    timber(im, grip)
    for (x, y) in grip:
        if (x + y) % 3 == 0:
            px(im, x, y, mix(get(im, x, y)[:3], CORD, 0.55))
    pommel = poly([(4.2, 29.0), (6.0, 26.8), (8.0, 28.6), (6.2, 30.8)])
    forge(im, pommel, bevel=1.6)
    guard = poly([(7.4, 18.4), (10.2, 15.4), (18.0, 22.6), (15.2, 25.6)])
    forge(im, guard, bevel=1.9)
    seam(im, guard, (9.0, 17.0), (16.4, 24.0), hot=0.8, bleed=1.1)
    blade = poly([(10.8, 21.6), (24.6, 5.0), (27.6, 6.0), (28.4, 8.8), (14.2, 23.6)])
    forge(im, blade, bevel=2.3, base=0.04)
    for (x, y) in blade:                                            # the spine, on the lower-left
        dist, _ = seg_dist((x + 0.5, y + 0.5), (10.8, 21.6), (24.6, 5.0))
        if dist < 1.5:
            px(im, x, y, mix(get(im, x, y)[:3], T0, 0.55 * (1.5 - dist) / 1.5))
    edge(im, blade, (26.4, 6.8), (13.2, 22.8), reach=2.2, strength=1.15)
    seam(im, blade, (13.8, 20.0), (19.6, 13.0), hot=0.5, width=0.35, bleed=0.7)
    sparks(im, blade, 3)
    rimlight(im, pommel, 0.7)
    rimlight(im, guard, 0.7)
    rimlight(im, blade, 0.7)
    outline(im)
    return im


def pickaxe():
    im = img()
    head = poly([(5.8, 15.6), (9.6, 10.0), (15.6, 8.2), (21.8, 8.8), (26.8, 11.6), (29.0, 15.8),
                 (25.8, 16.4), (21.4, 12.8), (16.2, 11.6), (11.2, 12.8), (8.0, 17.0)])
    eye = poly([(14.6, 9.0), (20.8, 9.4), (20.4, 15.2), (14.8, 14.2)])
    with_haft(im, trim=head | eye)
    forge(im, head, bevel=2.2)
    edge(im, head, (6.4, 15.6), (9.8, 11.0), reach=1.8)
    edge(im, head, (26.2, 12.4), (28.6, 15.6), reach=1.8)
    seam(im, head, (11.4, 12.2), (22.2, 11.4), hot=0.42, width=0.4, bleed=0.9)
    forge(im, eye, bevel=1.9)
    seam(im, eye, (17.6, 10.0), (17.6, 14.2), hot=0.95, width=0.5, bleed=1.2)
    rivet(im, 15, 10)
    rivet(im, 18, 12)
    sparks(im, head, 3)
    rimlight(im, head, 0.7)
    rimlight(im, eye, 0.7)
    outline(im)
    return im


def axe():
    im = img()
    bit = poly([(15.0, 8.2), (21.4, 7.0), (26.4, 10.2), (27.8, 15.0), (25.6, 19.8), (21.2, 22.0),
                (18.2, 18.8), (20.4, 15.0), (19.8, 11.4), (16.4, 12.4)])
    eye = poly([(13.6, 9.0), (18.4, 7.8), (19.6, 13.4), (14.8, 14.8)])
    with_haft(im, trim=bit | eye)
    forge(im, bit, bevel=2.6)
    edge(im, bit, (26.8, 11.4), (23.8, 20.6), reach=2.1)
    seam(im, bit, (19.0, 10.8), (22.6, 17.0), hot=0.42, width=0.4, bleed=0.9)
    forge(im, eye, bevel=1.8)
    seam(im, eye, (16.4, 9.4), (17.0, 13.6), hot=0.95, width=0.5, bleed=1.1)
    rivet(im, 15, 10)
    sparks(im, bit, 3)
    rimlight(im, bit, 0.7)
    rimlight(im, eye, 0.7)
    outline(im)
    return im


def shovel():
    im = img()
    blade = poly([(14.8, 13.4), (20.4, 7.6), (25.4, 5.4), (28.6, 7.0), (28.0, 12.4), (22.4, 19.4),
                  (17.8, 17.2)])
    collar = poly([(13.2, 14.8), (17.4, 10.4), (20.2, 12.8), (16.0, 17.2)])
    with_haft(im, trim=blade | collar)
    forge(im, blade, bevel=3.0, base=0.08)
    edge(im, blade, (26.0, 6.2), (28.2, 11.4), reach=2.2)
    groove(im, blade, (17.6, 15.4), (26.6, 7.6), reach=0.6, tone=0.30)
    seam(im, blade, (19.6, 13.4), (24.8, 8.8), hot=0.38, width=0.35, bleed=0.85)
    forge(im, collar, bevel=1.7)
    seam(im, collar, (14.8, 14.6), (18.6, 11.4), hot=0.9, width=0.5, bleed=1.1)
    sparks(im, blade, 3)
    rimlight(im, blade, 0.7)
    rimlight(im, collar, 0.7)
    outline(im)
    return im


def hoe():
    im = img()
    head = poly([(14.6, 13.8), (18.6, 9.2), (28.6, 7.6), (29.2, 10.6), (21.6, 12.4), (20.6, 16.8),
                 (17.4, 16.8)])
    neck = poly([(17.6, 13.0), (21.2, 8.6), (23.2, 10.0), (19.6, 14.4)])
    with_haft(im, trim=head | neck)
    forge(im, head, bevel=2.1)
    edge(im, head, (21.4, 11.8), (28.8, 9.8), reach=1.6)
    seam(im, head, (18.4, 13.0), (25.2, 9.8), hot=0.40, width=0.35, bleed=0.85)
    rivet(im, 18, 12)
    sparks(im, head, 2)
    rimlight(im, head, 0.7)
    outline(im)
    return im


# ---- the four pieces, in the hand ------------------------------------------------------------------
def helmet():
    """A closed helm: crown, brow band, a visor slit with the dark behind it, and a breath grille."""
    im = img()
    crown = ellipse(16, 14.2, 10.6, 9.6)
    jaw = poly([(6.4, 13.8), (25.6, 13.8), (24.6, 23.0), (21.0, 26.2), (11.0, 26.2), (7.4, 23.0)])
    shell = crown | jaw
    forge(im, shell, bevel=3.6, base=0.08)
    # the crown catches the light along the top left, the jaw falls away into the dark
    edge(im, shell, (8.4, 8.4), (18.0, 5.4), reach=2.0, strength=0.75)
    groove(im, shell, (6.6, 13.4), (25.4, 13.4), reach=0.6, tone=0.46)
    groove(im, shell, (7.6, 21.4), (24.4, 21.4), reach=0.55, tone=0.40)
    comb = {(x, y) for (x, y) in shell if abs(x - 15.5) < 1.5 and y < 13}
    forge(im, comb, bevel=1.4, base=0.16, grain=0.0)
    seam(im, comb, (15.5, 4.8), (15.5, 12.6), hot=0.85, width=0.55, bleed=1.2)
    # the sight: two slits, cut dark, with the smallest ember in them
    for y in (16, 17):
        for x in range(8, 24):
            if (x, y) not in shell:
                continue
            if 14 <= x <= 17:                                   # the nasal between the eyes
                continue
            c = mix(T0, EMBER_L, 0.34 if y == 16 else 0.14)
            px(im, x, y, c)
    for x in (9, 12, 19, 22):
        px(im, x, 16, mix(T2, T4, 0.45))
    # the grille over the mouth
    for x in range(11, 21, 2):
        for y in (20, 21, 22):
            if (x, y) in shell:
                px(im, x, y, mix(get(im, x, y)[:3], T0, 0.62))
    for xy in ((7, 12), (24, 12), (8, 23), (23, 23)):
        if xy in shell:
            rivet(im, xy[0], xy[1])
    sparks(im, shell, 3)
    rimlight(im, shell, 0.7)
    outline(im)
    return im


def chestplate():
    im = img()
    torso = poly([(9.6, 9.4), (13.2, 9.4), (16.0, 11.6), (18.8, 9.4), (22.4, 9.4), (24.8, 11.8),
                  (24.0, 15.4), (23.4, 21.0), (8.6, 21.0), (8.0, 15.4), (7.2, 11.8)])
    fauld = poly([(8.8, 21.0), (23.2, 21.0), (22.4, 26.0), (9.6, 26.0)])
    pauld = (ellipse(7.4, 12.4, 4.4, 3.6) | ellipse(24.6, 12.4, 4.4, 3.6)) - \
            {(x, y) for y in range(32) for x in range(32) if y > 16}
    forge(im, torso, bevel=3.4, base=0.10)
    forge(im, fauld, bevel=2.0, base=0.06)
    forge(im, pauld, bevel=2.2, base=0.12)
    groove(im, fauld, (9.4, 23.4), (22.6, 23.4), reach=0.6, tone=0.40)
    # the centre ridge, and the heat only where the plates were closed
    ridge = {(x, y) for (x, y) in torso if abs(x - 15.5) < 1.3 and y > 11}
    forge(im, ridge, bevel=1.3, base=0.16, grain=0.0)
    seam(im, ridge, (15.5, 12.8), (15.5, 20.0), hot=0.6, width=0.4, bleed=0.95)
    edge(im, torso, (8.2, 12.0), (9.4, 20.6), reach=1.4, strength=0.6)
    for xy in ((10, 12), (20, 12), (10, 18), (20, 18)):
        rivet(im, xy[0], xy[1])
    sparks(im, torso | pauld, 4)
    rimlight(im, torso, 0.7)
    rimlight(im, fauld, 0.7)
    rimlight(im, pauld, 0.7)
    outline(im)
    return im


def leggings():
    """Belt, cuisses, knee cops, greaves - the joints are what make it read as legs and not a table."""
    im = img()
    belt = poly([(7.0, 7.0), (24.4, 7.0), (24.4, 9.4), (7.0, 9.4)])
    cuiss = poly([(7.2, 9.2), (24.2, 9.2), (23.4, 14.6), (18.0, 14.6), (16.0, 12.6),
                  (14.0, 14.6), (8.4, 14.6)])
    thigh = poly([(8.4, 14.2), (14.6, 14.2), (13.9, 20.4), (9.1, 20.4)]) | \
            poly([(17.4, 14.2), (23.6, 14.2), (22.9, 20.4), (18.1, 20.4)])
    knee = ellipse(11.5, 21.8, 3.0, 2.4) | ellipse(20.5, 21.8, 3.0, 2.4)
    shin = poly([(9.4, 22.4), (13.6, 22.4), (13.2, 27.2), (9.8, 27.2)]) | \
           poly([(18.4, 22.4), (22.6, 22.4), (22.2, 27.2), (18.8, 27.2)])
    forge(im, belt, bevel=1.6, base=0.04)
    forge(im, cuiss, bevel=2.8, base=0.10)
    forge(im, thigh, bevel=2.0, base=0.06)
    forge(im, knee, bevel=1.8, base=0.18)
    forge(im, shin, bevel=1.9, base=0.06)
    groove(im, cuiss, (7.4, 12.2), (24.0, 12.2), reach=0.55, tone=0.40)
    seam(im, belt, (7.0, 8.4), (24.6, 8.4), hot=0.34, width=0.35, bleed=0.75)
    for (x, y) in ellipse(15.5, 8.3, 2.1, 1.7):
        px(im, x, y, mix(get(im, x, y)[:3], T4, 0.55))
    for (x, y) in ellipse(15.5, 8.3, 0.9, 0.8):
        px(im, x, y, T0)
    for xy in ((8, 10), (23, 10), (9, 18), (22, 18)):
        rivet(im, xy[0], xy[1])
    for cx in (11.5, 20.5):
        seam(im, knee, (cx, 20.2), (cx, 23.4), hot=0.5, width=0.4, bleed=0.95)
        edge(im, knee, (cx - 1.8, 20.6), (cx + 1.2, 20.2), reach=1.2, strength=0.6)
    sparks(im, cuiss, 3)
    rimlight(im, belt, 0.7)
    rimlight(im, cuiss, 0.7)
    rimlight(im, thigh, 0.7)
    rimlight(im, knee, 0.7)
    rimlight(im, shin, 0.7)
    outline(im)
    return im


def boots():
    """Sabatons: a cuff, a greave shaft, an ankle band, and a foot that points somewhere."""
    im = img()
    for sgn, cx in ((-1, 10.0), (1, 22.0)):
        cuff = poly([(cx - 4.4, 5.8), (cx + 4.4, 5.8), (cx + 3.9, 9.2), (cx - 3.9, 9.2)])
        shaft = poly([(cx - 3.7, 9.0), (cx + 3.7, 9.0), (cx + 3.3, 18.2), (cx - 3.3, 18.2)])
        ankle = poly([(cx - 3.8, 17.8), (cx + 3.8, 17.8), (cx + 3.6, 20.8), (cx - 3.6, 20.8)])
        heel, toe = (cx - 3.6, cx + 6.4) if sgn > 0 else (cx + 3.6, cx - 6.4)
        foot = poly([(heel, 20.4), (cx + 0.4 * sgn, 20.4), (toe, 22.6), (toe, 25.4),
                     (toe - 0.9 * sgn, 26.6), (heel + 0.6 * sgn, 26.6), (heel, 24.0)])
        forge(im, cuff, bevel=1.5, base=0.16)
        forge(im, shaft, bevel=2.5, base=0.06)
        forge(im, ankle, bevel=1.5, base=0.16)
        forge(im, foot, bevel=2.4, base=0.10)
        seam(im, shaft, (cx, 10.4), (cx, 17.2), hot=0.42, width=0.35, bleed=0.8)
        groove(im, foot, (heel, 24.2), (toe, 23.4), reach=0.55, tone=0.38)
        edge(im, foot, (heel + 0.4 * sgn, 26.2), (toe - 0.6 * sgn, 25.6), reach=1.2, strength=0.7)
        edge(im, cuff, (cx - 3.8, 6.4), (cx + 3.8, 6.4), reach=1.1, strength=0.6)
        rivet(im, int(cx) - 3, 11)
        rivet(im, int(cx) + 1, 15)
        for m in (cuff, shaft, ankle, foot):
            rimlight(im, m, 0.7)
        sparks(im, shaft, 2)
    outline(im)
    return im


# ---- the worn layers -----------------------------------------------------------------------------
def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


FACE_LIGHT = {"top": 0.26, "bottom": -0.26, "right": -0.12, "left": 0.06, "back": -0.16, "front": 0.0}


def worn_box(im, u, v, w, h, d, heat=(), bands=(), studs=(), visor=None):
    """One box of the humanoid sheet, plated.

    Across a face the light rolls the way it would around a limb - bright on the left, falling to
    dark on the right, with one specular line where the plate turns. Bands are plate divisions: a
    lit lip with a dark groove under it. Heat is deliberately rationed to a few short vents: a
    stripe down the whole player reads as a strap, not as a metal that has not gone out.
    """
    for name, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        for yy in range(y, y + fh):
            for xx in range(x, x + fw):
                across = (xx - x + 0.5) / fw * 2 - 1
                down = (yy - y) / max(1, fh - 1)
                v0 = 0.30 - 0.34 * across + 0.20 * math.exp(-((across + 0.45) / 0.30) ** 2)
                v0 += FACE_LIGHT[name]
                if down < 0.06:
                    v0 += 0.26                                   # the top lip of the piece
                elif down > 0.96:
                    v0 -= 0.16                                   # and its shadowed under-edge
                for b in bands:
                    if abs(down - b) < 0.045:
                        v0 += 0.24
                    elif 0.0 < down - b < 0.10:
                        v0 -= 0.20
                if rnd.random() < 0.07:
                    v0 += 0.045 if rnd.random() < 0.5 else -0.045
                c = ramp(v0)
                px(im, xx, yy, c)
        if name in ("front", "back"):
            for (sx, sy, sh) in heat:
                for yy in range(y + sy, y + sy + sh):
                    g = 1.0 - abs((yy - y - sy) / max(1, sh - 1) - 0.5) * 1.1
                    px(im, x + sx, yy, mix(mix(EMBER, EMBER_H, 0.45 * g), get(im, x + sx, yy)[:3], 0.10))
        if name == "front":
            for (sx, sy) in studs:
                rivet(im, x + sx, y + sy)
            if visor is not None:
                lo, hi = visor
                for yy in range(y + lo, y + hi + 1):
                    for xx in range(x, x + fw):
                        centre = abs(xx - (x + fw / 2.0)) < 0.6
                        c = T0 if not centre else mix(T2, T4, 0.4)
                        if not centre and yy == y + lo:
                            c = mix(T0, EMBER_L, 0.42)
                        px(im, xx, yy, c)


def layer1():
    """Helmet, chestplate, arms, and the shaft of the boots."""
    im = img(64, 32)
    worn_box(im, 0, 0, 8, 8, 8, bands=(0.72,), visor=(3, 4), heat=((3, 0, 3),))
    worn_box(im, 16, 16, 8, 12, 4, bands=(0.18, 0.58),
             studs=((1, 3), (6, 3), (1, 9), (6, 9)), heat=((3, 4, 5),))
    worn_box(im, 40, 16, 4, 12, 4, bands=(0.14, 0.44), studs=((1, 2),))
    worn_box(im, 0, 16, 4, 12, 4, bands=(0.60, 0.86), heat=((1, 8, 2),))
    return im


def layer2():
    """Leggings: a thinner plate over hips and legs. No heat here - the seam is under the belt."""
    im = img(64, 32)
    worn_box(im, 16, 16, 8, 12, 4, bands=(0.06, 0.22), studs=((1, 1), (6, 1)))
    worn_box(im, 0, 16, 4, 12, 4, bands=(0.10, 0.50))
    return im


PIECES = {
    "star_iron_sword": sword, "star_iron_pickaxe": pickaxe, "star_iron_axe": axe,
    "star_iron_shovel": shovel, "star_iron_hoe": hoe,
    "star_iron_helmet": helmet, "star_iron_chestplate": chestplate,
    "star_iron_leggings": leggings, "star_iron_boots": boots,
}

if __name__ == "__main__":
    for name, fn in PIECES.items():
        fn().save(os.path.join(ITEM, name + ".png"))
        print("item/" + name + ".png")
    layer1().save(os.path.join(ARMOR, "star_iron_layer_1.png"))
    layer2().save(os.path.join(ARMOR, "star_iron_layer_2.png"))
    print("models/armor/star_iron_layer_{1,2}.png")
