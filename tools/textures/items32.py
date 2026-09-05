"""The mod's item textures at 32x32 - pixel art from code, so there is no art pipeline to keep.
python3 tools/textures/items32.py -> resources/assets/wakingworld/textures/item/*.png (+ hourglass .mcmeta)
Also the three 16x16 textures the 3D hammer model wraps (hammer_head, hammer_haft, hammer_rune)."""
from PIL import Image
import os, math, random, json

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "item")
os.makedirs(OUT, exist_ok=True)
S = 32

def img(size=S):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))

def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((int(x), int(y)), tuple(int(v) for v in c))

def get(im, x, y):
    if 0 <= x < im.width and 0 <= y < im.height:
        return im.getpixel((int(x), int(y)))
    return (0, 0, 0, 0)

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)

def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)

def outline(im, color, body=None):
    """Draws `color` on every transparent pixel 4-adjacent to the body (all opaque pixels by default)."""
    if body is None:
        body = {(x, y) for y in range(im.height) for x in range(im.width) if get(im, x, y)[3] > 0}
    for (x, y) in list(body):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in body and get(im, x + dx, y + dy)[3] == 0:
                px(im, x + dx, y + dy, color)

def lit(c, x, y, cx, cy, r, light=(-0.6, -0.7), strength=0.45):
    """Simple sphere-ish lighting: brighter towards the light direction, darker away from it."""
    nx, ny = (x - cx) / r, (y - cy) / r
    d = nx * light[0] + ny * light[1]
    return shade(c, 1.0 + strength * d)

def disc(im, cx, cy, r, c, strength=0.45, light=(-0.6, -0.7)):
    for y in range(im.height):
        for x in range(im.width):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                px(im, x, y, lit(c, x + 0.5, y + 0.5, cx, cy, r, light, strength))

def line(im, x0, y0, x1, y1, c, w=1):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(n + 1):
        t = i / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for dx in range(w):
            for dy in range(w):
                px(im, int(round(x)) + dx - w // 2, int(round(y)) + dy - w // 2, c)

def glow(im, points, color, radius=1.6, strength=0.55):
    """Tints the pixels around `points` towards `color` - a soft halo."""
    for (gx, gy) in points:
        for y in range(int(gy - radius - 1), int(gy + radius + 2)):
            for x in range(int(gx - radius - 1), int(gx + radius + 2)):
                d = math.hypot(x - gx, y - gy)
                if d <= radius:
                    c = get(im, x, y)
                    if c[3] > 0 and (x, y) not in points:
                        t = strength * (1 - d / radius)
                        px(im, x, y, mix(c, color, t))

def save(im, name):
    im.save(os.path.join(OUT, name + ".png"))

# ------------------------------------------------------------------ the heart

def heart_body(cx, cy, w, h):
    def inside(x, y):
        X = (x - cx) / w; Y = (cy - y) / h + 0.12
        return (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0
    return {(x, y) for y in range(S) for x in range(S) if inside(x + 0.5, y + 0.5)}

def colossus_heart():
    """A heart of dark basalt with a lava core: two lobes, a point, glowing fissures, vessel stubs
    at the top still dripping, the hottest white at the centre."""
    im = img()
    body = heart_body(16, 17.5, 11.2, 10.2)
    stone = (60, 56, 60, 255); grain = (84, 78, 84, 255); rim = (22, 18, 24, 255)
    glow_c = (255, 120, 25, 255); hot = (255, 210, 100, 255); white = (255, 250, 225, 255)
    rnd = random.Random(7)
    for (x, y) in body:
        f = 1.0 + 0.32 * ((16 - x) / 12.0) + 0.28 * ((15 - y) / 12.0)
        c = grain if rnd.random() < 0.18 else stone
        px(im, x, y, shade(c, f))
    outline(im, rim, body)
    # vessel stubs
    for (x, y) in [(15, 5), (16, 5), (15, 4), (16, 4), (9, 7), (10, 6), (22, 7), (21, 6)]: px(im, x, y, shade(stone, 0.85))
    for (x, y) in [(15, 3), (16, 3), (10, 5), (21, 5)]: px(im, x, y, glow_c)
    for (x, y) in [(15, 2), (10, 4), (21, 4)]: px(im, x, y, (255, 160, 60, 255))
    # fissures: a main crack top to point, branches into each lobe
    main = [(15, 6), (16, 7), (16, 8), (15, 9), (15, 10), (16, 11), (16, 12), (15, 13), (15, 14), (16, 15), (16, 16), (15, 17), (15, 18), (16, 19), (16, 20), (16, 21), (16, 22), (16, 23), (16, 24), (16, 25), (16, 26)]
    left = [(14, 12), (13, 11), (12, 10), (11, 9), (10, 9), (9, 10), (13, 16), (12, 17), (11, 17), (10, 18), (12, 20), (11, 21)]
    right = [(17, 13), (18, 12), (19, 11), (20, 10), (21, 10), (22, 11), (18, 17), (19, 18), (20, 18), (21, 19), (19, 21), (20, 22)]
    for p in main + left + right:
        if p in body: px(im, p[0], p[1], glow_c)
    for (x, y) in [(15, 14), (16, 14), (15, 15), (16, 15), (15, 16), (16, 16), (16, 13), (15, 17)]: px(im, x, y, hot)
    for (x, y) in [(15, 15), (16, 15), (16, 14)]: px(im, x, y, white)
    glow(im, set(main + left + right), glow_c, 1.8, 0.35)
    save(im, "colossus_heart")

def heart_of_the_end():
    """The Titan's heart: obsidian, veined with crying-obsidian violet, a cold white-violet core,
    end-rod sparks around it. Colder and heavier than the others."""
    im = img()
    body = heart_body(16, 17.5, 11.2, 10.2)
    obs = (26, 16, 40, 255); grain = (44, 28, 66, 255); rim = (8, 4, 14, 255)
    vein = (150, 70, 230, 255); tear = (90, 200, 255, 255); core = (230, 210, 255, 255)
    rnd = random.Random(11)
    for (x, y) in body:
        f = 1.0 + 0.4 * ((16 - x) / 12.0) + 0.3 * ((15 - y) / 12.0)
        c = grain if rnd.random() < 0.22 else obs
        px(im, x, y, shade(c, f))
    outline(im, rim, body)
    veins = [(15, 6), (16, 7), (16, 8), (15, 9), (15, 10), (16, 11), (16, 12), (15, 13), (16, 17), (16, 18), (16, 19), (16, 20), (16, 21), (16, 22), (16, 23), (16, 24), (16, 25),
             (14, 11), (13, 10), (12, 9), (11, 9), (10, 10), (13, 15), (12, 16), (11, 17), (18, 12), (19, 11), (20, 10), (21, 10), (22, 11), (19, 16), (20, 17), (21, 18)]
    for p in veins:
        if p in body: px(im, p[0], p[1], vein)
    for (x, y) in [(15, 14), (16, 14), (15, 15), (16, 15), (15, 16), (16, 16), (14, 15), (17, 15)]: px(im, x, y, core)
    for (x, y) in [(12, 13), (13, 14), (20, 14), (19, 15), (14, 20), (18, 21)]: px(im, x, y, tear)
    for (x, y) in [(12, 14), (20, 15), (14, 21), (18, 22), (12, 15), (20, 16)]: px(im, x, y, mix(tear, obs, 0.5))
    glow(im, set(veins), vein, 1.6, 0.3)
    # sparks
    for (x, y) in [(4, 8), (27, 6), (6, 24), (26, 22), (16, 1), (3, 17)]:
        px(im, x, y, (240, 230, 255, 255)); px(im, x + 1, y, (180, 150, 255, 160)); px(im, x, y + 1, (180, 150, 255, 160))
    save(im, "heart_of_the_end")

# ------------------------------------------------------------------ the horn

def horn_of_waking():
    """An aurochs horn, held with the mouthpiece at the bottom left and the wide bell at the top
    right: a crescent that thickens along its length, bronze bands, a bronze mouthpiece, the
    bell's dark mouth ringed with a lighter lip, the old rite's glyph cut into the wide band."""
    im = img()
    horn = (96, 74, 52, 255); horn_l = (150, 120, 84, 255); horn_d = (54, 40, 26, 255); rim = (24, 16, 10, 255)
    bronze = (190, 140, 60, 255); bronze_l = (240, 200, 110, 255); bronze_d = (110, 76, 30, 255); glyph = (255, 170, 60, 255)
    # centre line: a quadratic bezier from the mouthpiece (4, 27) through (6, 8) to the bell (25, 7)
    P0, P1, P2 = (4.5, 27.0), (5.0, 6.0), (25.5, 7.5)
    pts = []
    for i in range(0, 101):
        t = i / 100
        x = (1 - t) ** 2 * P0[0] + 2 * (1 - t) * t * P1[0] + t * t * P2[0]
        y = (1 - t) ** 2 * P0[1] + 2 * (1 - t) * t * P1[1] + t * t * P2[1]
        r = 1.1 + 4.3 * t ** 1.6
        pts.append((x, y, r, t))
    body = set()
    for (cx, cy, r, t) in pts:
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    body.add((x, y))
    for (x, y) in body:
        best = 99; brt = 0; tt = 0
        for (cx, cy, r, t) in pts[::3]:
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < best:
                best = d; tt = t
                brt = (cy - (y + 0.5)) / max(r, 0.8) * 0.8 + ((cx - (x + 0.5)) / max(r, 0.8)) * 0.5
        c = horn_l if brt > 0.45 else horn_d if brt < -0.5 else horn
        if int(tt * 100) % 9 < 2: c = shade(c, 0.86)  # growth ridges
        px(im, x, y, c)
    outline(im, rim, body)
    # the bell: an elliptical mouth on the end, seen a little from the side
    bx, by = 27.0, 7.5
    for y in range(S):
        for x in range(S):
            d = ((x + 0.5 - bx) / 3.0) ** 2 + ((y + 0.5 - by) / 5.6) ** 2
            if d <= 1.0:
                px(im, x, y, (28, 18, 12, 255) if d < 0.62 else (mix(horn_l, (255, 240, 210, 255), 0.35) if x + 0.5 < bx else horn))
            elif d <= 1.35 and get(im, x, y)[3] == 0:
                px(im, x, y, rim)
    px(im, 27, 5, (60, 40, 28, 255)); px(im, 27, 10, (60, 40, 28, 255))
    # bands: a wide one before the bell, a narrow one half-way, the mouthpiece at the tip
    def band(t0, t1, extra):
        for (cx, cy, r, t) in pts:
            if t0 <= t <= t1:
                rr = r + extra
                for y in range(int(cy - rr - 1), int(cy + rr + 2)):
                    for x in range(int(cx - rr - 1), int(cx + rr + 2)):
                        d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                        if d <= rr * rr:
                            e = d > (rr - 1.1) ** 2
                            c = bronze_d if e else (bronze_l if (y + 0.5 - cy) < -0.25 * rr else bronze)
                            px(im, x, y, c)
    band(0.72, 0.80, 0.8)
    band(0.40, 0.44, 0.6)
    band(0.0, 0.07, 0.5)
    px(im, 4, 28, bronze_d); px(im, 3, 28, bronze_d)
    # the glyph on the wide band
    for (x, y) in [(18, 6), (18, 7), (18, 8), (18, 9), (18, 10), (17, 7), (19, 8), (17, 9), (19, 10)]:
        if get(im, x, y)[3] > 0: px(im, x, y, glyph)
    outline(im, rim)
    save(im, "horn_of_waking")

# ------------------------------------------------------------------ the key

def titan_key():
    """The Key of the Titan: a long obsidian key held diagonally, a ring bow set with the six
    sigils' colours, a heart of the end in its centre, three teeth at the bit, violet sparks."""
    im = img()
    obs = (36, 22, 56, 255); obs_l = (78, 54, 110, 255); rim = (10, 4, 16, 255)
    gold = (220, 180, 90, 255); gold_d = (140, 100, 40, 255)
    # shaft from the bow centre (9, 9) down to the bit (25, 25)
    line(im, 11, 11, 24, 24, obs, 3)
    line(im, 10, 12, 23, 25, obs_l, 1)
    # bit: three teeth off the shaft's end, pointing down-left
    for i, (dx, dy) in enumerate([(0, 0), (2, -2), (-2, 2)]):
        bx, by = 25 + dx, 25 + dy
        line(im, bx, by, bx - 3, by + 3, obs, 2)
    line(im, 26, 26, 28, 28, obs, 3)
    line(im, 25, 27, 26, 28, obs_l, 1)
    # the bow: a ring
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 9, y + 0.5 - 9)
            if 5.2 <= d <= 8.2:
                px(im, x, y, lit(obs, x + 0.5, y + 0.5, 9, 9, 8.2, strength=0.5))
    # gold inlay ring inside the bow
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 9, y + 0.5 - 9)
            if 5.2 <= d <= 6.0:
                px(im, x, y, gold_d if (x + y) % 3 else gold)
    # the six sigil gems around the bow
    gems = [(120, 120, 130), (170, 120, 70), (240, 210, 110), (170, 225, 255), (110, 220, 200), (140, 230, 100)]
    for i, g in enumerate(gems):
        a = math.radians(-90 + i * 60)
        gx, gy = 9 + math.cos(a) * 6.7, 9 + math.sin(a) * 6.7
        px(im, gx, gy, (g[0], g[1], g[2], 255)); px(im, gx - 0.5, gy - 0.5, (255, 255, 255, 255))
        px(im, gx + 1, gy, shade((g[0], g[1], g[2], 255), 0.6)); px(im, gx, gy + 1, shade((g[0], g[1], g[2], 255), 0.6))
    # the heart in the middle of the bow
    for (x, y) in [(7, 7), (8, 7), (10, 7), (11, 7), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8), (8, 9), (9, 9), (10, 9), (9, 10)]:
        px(im, x, y, (170, 90, 240, 255))
    px(im, 8, 8, (235, 215, 255, 255)); px(im, 9, 9, (200, 160, 255, 255))
    outline(im, rim)
    # sparks
    for (x, y) in [(20, 6), (4, 22), (29, 17)]:
        px(im, x, y, (230, 210, 255, 255)); px(im, x + 1, y, (170, 120, 255, 150)); px(im, x, y + 1, (170, 120, 255, 150))
    save(im, "titan_key")

# ------------------------------------------------------------------ the hourglass

def hourglass_frame(t):
    """One frame: t in [0,1) - the sand runs out of the top bulb into the bottom one, the glow
    pulses, and the last quarter of the cycle turns the glass over (the frame tips) and refills."""
    im = img()
    wood = (86, 58, 34, 255); wood_l = (130, 92, 56, 255); gold = (222, 184, 92, 255); gold_d = (150, 112, 44, 255); rim = (26, 16, 8, 255)
    glass = (150, 200, 220, 120); glass_l = (230, 245, 255, 190); sand = (90, 220, 210, 255); sand_l = (200, 255, 250, 255); sand_d = (40, 150, 150, 255)
    run = min(1.0, t / 0.75)  # fraction of the sand that has run through
    tipping = t > 0.75
    # the frame: top and bottom plates, four posts
    for x in range(6, 26):
        for y in (3, 4, 27, 28):
            px(im, x, y, gold if (y in (3, 28) and x % 3) else gold_d)
    for x in range(7, 25):
        px(im, x, 2, gold_d if x % 4 else gold); px(im, x, 29, gold_d if x % 4 else gold)
    for x in (6, 25):
        for y in range(5, 27):
            px(im, x, y, wood_l if x == 6 else wood)
    for x in (7, 24):
        for y in range(5, 27):
            px(im, x, y, wood if x == 7 else shade(wood, 0.75))
    # the glass: two bulbs meeting at a neck at y = 15.5
    def half_w(y):
        d = abs(y + 0.5 - 15.5)  # distance from the neck
        return 1.2 + 6.8 * min(1.0, (d / 8.5) ** 0.75)
    body = set()
    for y in range(5, 27):
        w = half_w(y)
        for x in range(8, 24):
            if abs(x + 0.5 - 16) <= w:
                body.add((x, y))
    for (x, y) in body:
        edge = (x - 1, y) not in body or (x + 1, y) not in body
        px(im, x, y, glass_l if (edge and x < 16) else glass)
    # sand in the top bulb: fills from the neck up; its level drops as it runs
    top_rows = [y for y in range(5, 16)]
    top_fill = 1.0 - run
    n_top = int(round(len(top_rows) * top_fill))
    for y in top_rows[len(top_rows) - n_top:]:
        w = half_w(y)
        for x in range(8, 24):
            if abs(x + 0.5 - 16) <= w - (0.6 if y > 5 else 0):
                px(im, x, y, sand_l if abs(x + 0.5 - 16) > w - 1.6 and x < 16 else sand)
    # sand in the bottom bulb: a heap that grows
    bot_rows = [y for y in range(16, 27)]
    n_bot = int(round(len(bot_rows) * run))
    for i, y in enumerate(bot_rows[len(bot_rows) - n_bot:]):
        w = half_w(y)
        heap = w * (0.35 + 0.65 * (i + 1) / max(1, n_bot))
        for x in range(8, 24):
            if abs(x + 0.5 - 16) <= min(w - 0.6, heap):
                px(im, x, y, sand_d if y > 24 else sand)
    # the falling stream
    if 0 < run < 1.0 and not tipping:
        for y in range(15, 26 - n_bot):
            px(im, 15 if (y + int(t * 40)) % 2 else 16, y, sand_l)
    # the glow: brighter as the sand runs, pulsing
    pulse = 0.5 + 0.5 * math.sin(t * math.pi * 6)
    glow(im, {(15, 15), (16, 15), (15, 16), (16, 16)}, sand_l, 3.5 + 2 * pulse, 0.25 + 0.2 * pulse)
    outline(im, rim)
    if tipping:
        # the turn-over: the glass swings towards the viewer - squashed to a line and back, sand swapped over
        phase = (t - 0.75) / 0.25  # 0..1
        if phase >= 0.5:
            im = hourglass_frame(0.0)  # refilled: the top full again
        factor = abs(math.cos(phase * math.pi))
        h = max(2, int(round(S * factor)))
        squashed = im.resize((S, h), Image.NEAREST)
        out = img()
        out.paste(squashed, (0, (S - h) // 2))
        return out
    return im

def hourglass():
    frames = 28
    sheet = Image.new("RGBA", (S, S * frames), (0, 0, 0, 0))
    for i in range(frames):
        sheet.paste(hourglass_frame(i / frames), (0, i * S))
    save(sheet, "hourglass_of_restoration")
    with open(os.path.join(OUT, "hourglass_of_restoration.png.mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": 3, "interpolate": False}}, f)

# ------------------------------------------------------------------ the ember

def sleepers_ember():
    """A coal that never went out: a jagged black lump, hot orange in every crack, sparks off the top."""
    im = img()
    coal = (30, 26, 30, 255); coal_l = (62, 56, 62, 255); rim = (8, 6, 8, 255)
    hot = (255, 110, 20, 255); hotter = (255, 190, 80, 255); white = (255, 240, 200, 255)
    rnd = random.Random(3)
    # an irregular lump: union of a few ellipses
    parts = [(15, 18, 10, 8), (12, 14, 7, 6), (20, 14, 7, 6), (16, 11, 5, 4), (10, 21, 5, 4), (22, 21, 5, 4)]
    body = set()
    for (cx, cy, rx, ry) in parts:
        for y in range(S):
            for x in range(S):
                if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                    body.add((x, y))
    # facets: shade by a few random planes
    for (x, y) in body:
        k = int((x * 0.45 + y * 0.8 + rnd.random() * 0.4) // 3)
        c = coal_l if k % 3 == 0 else coal
        px(im, x, y, shade(c, 1.0 + 0.35 * ((14 - x) / 12.0) + 0.3 * ((12 - y) / 12.0)))
    outline(im, rim, body)
    cracks = [(15, 12), (15, 13), (16, 14), (16, 15), (15, 16), (15, 17), (16, 18), (16, 19), (17, 20), (17, 21),
              (14, 15), (13, 16), (12, 17), (11, 18), (17, 15), (18, 14), (19, 13), (20, 13), (18, 18), (19, 19), (20, 20), (21, 21),
              (13, 20), (12, 21), (11, 22), (9, 19), (10, 19)]
    for p in cracks:
        if p in body: px(im, p[0], p[1], hot)
    for (x, y) in [(16, 15), (15, 16), (16, 16), (15, 15), (16, 17)]: px(im, x, y, hotter)
    px(im, 16, 16, white)
    glow(im, set(cracks), hot, 1.5, 0.35)
    # sparks rising
    for (x, y) in [(18, 6), (12, 5), (22, 4), (14, 2), (25, 9)]:
        px(im, x, y, hotter); px(im, x, y + 1, (255, 120, 30, 140))
    save(im, "sleepers_ember")

# ------------------------------------------------------------------ runes and sigils

KINDS = {
    #          tablet base           glow                   dark rim            symbol
    "stone":      ((118, 116, 122), (255, 150, 60),  (40, 38, 44)),
    "earth":      ((116, 86, 56),   (255, 175, 70),  (46, 32, 18)),
    "sandstone":  ((216, 190, 128), (255, 225, 110), (110, 90, 44)),
    "ice":        ((160, 205, 236), (200, 250, 255), (60, 100, 140)),
    "prismarine": ((78, 142, 132),  (150, 255, 230), (24, 62, 58)),
    "moss":       ((90, 128, 66),   (190, 255, 120), (34, 58, 24)),
    "void":       ((34, 24, 52),    (210, 130, 255), (10, 6, 18)),
}

GLYPHS = {
    # (x, y) strokes on a 10x14 grid centred on the tablet
    "stone": [(5, 1), (5, 2), (5, 3), (5, 4), (5, 5), (5, 6), (5, 7), (5, 8), (5, 9), (5, 10), (5, 11), (2, 4), (3, 3), (4, 2), (6, 2), (7, 3), (8, 4), (3, 9), (4, 8), (6, 8), (7, 9)],
    "earth": [(5, 1), (5, 2), (5, 3), (5, 4), (5, 5), (5, 6), (4, 7), (3, 8), (2, 9), (6, 7), (7, 8), (8, 9), (5, 7), (5, 8), (5, 9), (5, 10), (2, 3), (3, 4), (4, 5), (8, 3), (7, 4), (6, 5)],
    "sandstone": [(5, 2), (5, 3), (5, 9), (5, 10), (1, 6), (2, 6), (8, 6), (9, 6), (3, 4), (7, 4), (3, 8), (7, 8), (4, 5), (5, 5), (6, 5), (4, 6), (6, 6), (4, 7), (5, 7), (6, 7), (5, 6)],
    "ice": [(5, 1), (5, 2), (5, 3), (5, 4), (5, 5), (5, 6), (5, 7), (5, 8), (5, 9), (5, 10), (5, 11), (1, 6), (2, 6), (3, 6), (4, 6), (6, 6), (7, 6), (8, 6), (9, 6), (2, 3), (3, 4), (4, 5), (6, 7), (7, 8), (8, 9), (8, 3), (7, 4), (6, 5), (4, 7), (3, 8), (2, 9)],
    "prismarine": [(1, 5), (2, 4), (3, 3), (4, 4), (5, 5), (6, 6), (7, 7), (8, 6), (9, 5), (1, 9), (2, 8), (3, 7), (4, 8), (5, 9), (6, 10), (7, 11), (8, 10), (9, 9), (5, 1), (5, 2)],
    "moss": [(5, 11), (5, 10), (5, 9), (5, 8), (5, 7), (5, 6), (5, 5), (5, 4), (5, 3), (4, 5), (3, 6), (2, 7), (6, 5), (7, 6), (8, 7), (4, 8), (3, 9), (6, 8), (7, 9), (4, 2), (6, 2), (5, 1)],
}

def rune_item(name):
    """A stone tablet with a rounded top, a chip out of one corner, and the kind's glyph cut deep and lit."""
    base, gl, dark = [tuple(list(c) + [255]) for c in KINDS[name]]
    im = img()
    body = set()
    for y in range(3, 30):
        for x in range(9, 24):
            # rounded top
            if y < 8 and (x + 0.5 - 16.5) ** 2 / 7.5 ** 2 + (y + 0.5 - 8) ** 2 / 5 ** 2 > 1: continue
            if (x, y) in {(9, 29), (10, 29), (9, 28), (23, 29)}: continue  # chipped
            body.add((x, y))
    rnd = random.Random(sum(map(ord, name)))
    for (x, y) in body:
        f = 1.0 + 0.22 * ((16 - x) / 8.0) + 0.18 * ((14 - y) / 12.0)
        c = shade(base, f * (1.06 if rnd.random() < 0.2 else 1.0))
        if name == "ice" and (x + y) % 7 == 0: c = mix(c, (255, 255, 255, 255), 0.35)
        if name == "prismarine" and (x * 3 + y) % 9 == 0: c = mix(c, (170, 240, 220, 255), 0.35)
        if name == "moss" and rnd.random() < 0.08: c = mix(c, (60, 90, 40, 255), 0.6)
        px(im, x, y, c)
    # bevel: light on the left/top edge, dark on the right/bottom
    for (x, y) in body:
        if (x - 1, y) not in body or (x, y - 1) not in body: px(im, x, y, shade(get(im, x, y), 1.25))
        elif (x + 1, y) not in body or (x, y + 1) not in body: px(im, x, y, shade(get(im, x, y), 0.7))
    outline(im, dark, body)
    # the glyph: dark groove with the lit line inside
    ox, oy = 11, 9
    pts = {(ox + gx, oy + gy) for (gx, gy) in GLYPHS[name]}
    for (x, y) in pts:
        for dx, dy in ((1, 0), (0, 1), (1, 1)):
            if (x + dx, y + dy) in body and (x + dx, y + dy) not in pts:
                px(im, x + dx, y + dy, shade(get(im, x + dx, y + dy), 0.62))
    for (x, y) in pts: px(im, x, y, gl)
    top = min(pts, key=lambda p: p[1])
    px(im, top[0], top[1], (255, 255, 255, 255))
    glow(im, pts, gl, 1.6, 0.3)
    save(im, "rune_" + name)

def sigil(name):
    """A medallion of the kind's stuff with a bronze rim, the kind's emblem raised and lit in the middle."""
    base, gl, dark = [tuple(list(c) + [255]) for c in KINDS[name]]
    im = img()
    bronze = (196, 148, 64, 255); bronze_l = (244, 208, 120, 255); bronze_d = (110, 78, 28, 255)
    disc(im, 16, 16, 13.5, base, 0.4)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 11.6 <= d <= 13.5:
                px(im, x, y, bronze_l if (x + y) < 26 else bronze_d if (x + y) > 38 else bronze)
            elif 11.0 <= d < 11.6:
                px(im, x, y, shade(get(im, x, y), 0.7))
    rnd = random.Random(len(name) * 13)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if d < 11.0 and rnd.random() < 0.12:
                px(im, x, y, shade(get(im, x, y), 1.08))
    # emblems
    e = set()
    if name == "stone":       # a mountain
        for i in range(9): e |= {(12 + i, 21), (16, 12 + i)} if False else set()
        for y in range(12, 22):
            w = (y - 12) * 0.7
            e |= {(int(16 - w), y), (int(16 + w), y)}
        e |= {(x, 21) for x in range(10, 23)}
        e |= {(16, 12), (15, 13), (17, 13)}
    elif name == "earth":     # a tree with roots
        e |= {(16, y) for y in range(11, 23)}
        e |= {(13, 12), (14, 11), (18, 11), (19, 12), (12, 14), (20, 14), (14, 13), (18, 13)}
        e |= {(13, 21), (12, 22), (19, 21), (20, 22), (14, 20), (18, 20), (11, 23), (21, 23)}
    elif name == "sandstone": # a sun
        e |= {(x, y) for y in range(S) for x in range(S) if 2.2 <= math.hypot(x + 0.5 - 16, y + 0.5 - 16) <= 3.6}
        for i in range(8):
            a = math.radians(i * 45)
            for r in (5.5, 6.5, 7.5):
                e.add((int(16 + math.cos(a) * r), int(16 + math.sin(a) * r)))
    elif name == "ice":       # a snowflake
        for i in range(6):
            a = math.radians(i * 60 + 90)
            for r in range(1, 9):
                e.add((int(16 + math.cos(a) * r), int(16 + math.sin(a) * r)))
            for r in (5,):
                for s in (-1, 1):
                    b = a + s * math.radians(35)
                    for rr in (1, 2, 3):
                        e.add((int(16 + math.cos(a) * r + math.cos(b) * rr), int(16 + math.sin(a) * r + math.sin(b) * rr)))
    elif name == "void":      # an eye: a ring round a slit pupil, four rays
        e |= {(x, y) for y in range(S) for x in range(S) if 5.2 <= math.hypot(x + 0.5 - 16, y + 0.5 - 16) <= 6.4}
        e |= {(16, y) for y in range(13, 20)} | {(15, 15), (15, 16), (15, 17), (17, 15), (17, 16), (17, 17)}
        for i in range(4):
            a = math.radians(45 + i * 90)
            for r in (8.5, 9.5):
                e.add((int(16 + math.cos(a) * r), int(16 + math.sin(a) * r)))
    elif name == "prismarine":  # three waves
        for row, y0 in enumerate((12, 16, 20)):
            for x in range(9, 24):
                e.add((x, y0 + int(round(1.5 * math.sin((x + row * 2) * 0.9)))))
    else:                     # a leaf
        for y in range(9, 24):
            t = (y - 9) / 14
            w = 5.5 * math.sin(math.pi * t) * (1.1 - 0.4 * t)
            e |= {(int(16 - w + 0.5), y), (int(16 + w + 0.5), y)}
        e |= {(16, y) for y in range(10, 25)}
        e |= {(14, 15), (13, 16), (18, 17), (19, 18), (14, 19), (13, 20), (18, 13), (19, 14)}
    e = {p for p in e if math.hypot(p[0] + 0.5 - 16, p[1] + 0.5 - 16) < 10.5}
    for (x, y) in e:
        if (x + 1, y + 1) not in e: px(im, x + 1, y + 1, shade(get(im, x + 1, y + 1), 0.6))
    for (x, y) in e: px(im, x, y, gl)
    glow(im, e, gl, 1.5, 0.3)
    outline(im, dark)
    save(im, "sigil_" + name)

# ------------------------------------------------------------------ the letters

def dead_letter():
    """A folded letter: yellowed paper, a tea-coloured water stain, faint lines of a hand that
    pressed hard, a red wax seal with a heart pressed into it, a torn corner."""
    im = img()
    paper = (228, 216, 178, 255); paper_d = (196, 180, 136, 255); stain = (200, 176, 124, 255); rim = (96, 80, 52, 255)
    ink = (84, 66, 54, 255); ink_l = (130, 110, 92, 255); wax = (156, 32, 34, 255); wax_l = (214, 80, 70, 255); wax_d = (96, 16, 20, 255)
    body = set()
    for y in range(6, 27):
        for x in range(3, 29):
            if (x, y) in {(28, 6), (28, 7), (27, 6), (3, 26), (4, 26), (3, 25)}: continue  # torn
            body.add((x, y))
    rnd = random.Random(5)
    for (x, y) in body:
        c = paper
        if math.hypot(x - 22, y - 20) < 5.5: c = mix(paper, stain, 0.6 - 0.09 * math.hypot(x - 22, y - 20))
        if rnd.random() < 0.06: c = shade(c, 0.94)
        px(im, x, y, c)
    outline(im, rim, body)
    # the fold lines: the flap from both top corners down to the middle
    for i in range(13):
        px(im, 3 + i, 6 + i, paper_d); px(im, 28 - i, 6 + i, paper_d)
    for x in range(3, 29): px(im, x, 18, paper_d)
    # writing: rows of short dashes below the flap
    for y in (20, 22, 24):
        x = 6
        while x < 20:
            w = rnd.randint(1, 3)
            for i in range(w): px(im, x + i, y, ink if rnd.random() < 0.75 else ink_l)
            x += w + 1
    for (x, y) in [(7, 9), (8, 9), (7, 10), (8, 11), (9, 11)]: px(im, x, y, ink_l)  # an address, faded
    # the seal on the point of the flap
    for y in range(15, 23):
        for x in range(12, 20):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 19)
            if d <= 3.6:
                px(im, x, y, wax_d if d > 2.8 else wax_l if (x + y) < 33 else wax)
    for (x, y) in [(15, 17), (17, 17), (14, 18), (15, 18), (16, 18), (17, 18), (18, 18), (15, 19), (16, 19), (17, 19), (16, 20)]:
        px(im, x, y, wax_d)
    px(im, 15, 17, (240, 150, 140, 255))
    save(im, "dead_letter")

def almanac():
    """The Waker's Almanac: a thick book bound in dark green leather, brass corners and clasp, a
    heart in pale gold on the cover, a red ribbon marking a page, the block of pages showing."""
    im = img()
    leather = (40, 82, 56, 255); leather_l = (64, 116, 82, 255); leather_d = (20, 46, 30, 255); rim = (10, 22, 14, 255)
    brass = (200, 164, 84, 255); brass_l = (246, 220, 140, 255); brass_d = (120, 92, 36, 255)
    page = (236, 226, 196, 255); page_d = (200, 188, 150, 255); ribbon = (170, 40, 40, 255)
    rnd = random.Random(9)
    # the cover: from (4,3) to (26,28); the page block to the right of it and below
    for y in range(3, 29):
        for x in range(4, 27):
            c = leather_l if rnd.random() < 0.12 else leather
            c = shade(c, 1.0 + 0.2 * ((10 - x) / 20.0) + 0.15 * ((8 - y) / 24.0))
            if x == 4 or x == 5: c = leather_d  # the spine
            px(im, x, y, c)
    for y in range(4, 30):
        for x in range(27, 30):
            px(im, x, y, page_d if x == 29 or y == 29 else page)
    for x in range(6, 30):
        px(im, x, 29, page_d if x % 2 else page)
    # brass corners and a clasp
    for (cx, cy) in [(6, 4), (25, 4), (6, 27), (25, 27)]:
        for (dx, dy) in [(0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)]:
            px(im, cx + dx, cy + dy, brass)
        px(im, cx, cy, brass_l)
    for y in range(13, 19):
        px(im, 26, y, brass_d); px(im, 27, y, brass); px(im, 28, y, brass_l if y in (14, 15) else brass)
    # the ribbon
    for y in range(20, 30):
        px(im, 28, y, ribbon); px(im, 29, y, shade(ribbon, 0.7))
    # the emblem: a heart in pale gold, an outline around it
    body = set()
    for y in range(S):
        for x in range(S):
            X = (x + 0.5 - 15) / 4.6; Y = (14.5 - (y + 0.5)) / 4.2 + 0.12
            if (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0: body.add((x, y))
    for (x, y) in body: px(im, x, y, brass_l if (x + y) < 27 else brass)
    outline(im, brass_d, body)
    # a title rule under the emblem
    for x in range(10, 21): px(im, x, 22, brass_d if x % 3 else brass)
    for x in range(12, 19): px(im, x, 24, brass_d)
    outline(im, rim)
    save(im, "almanac")

# ------------------------------------------------------------------ the 3D hammer's textures (16x16)

def hammer_textures():
    # the head: dark deepslate with a lighter chamfer band and a few bright flecks
    im = img(16)
    head = (58, 60, 70, 255); head_l = (92, 94, 108, 255); head_d = (34, 35, 42, 255)
    rnd = random.Random(2)
    for y in range(16):
        for x in range(16):
            c = head_l if y in (0, 15) or x in (0, 15) else head_d if y in (1, 14) or x in (1, 14) else head
            if 2 <= x <= 13 and 2 <= y <= 13 and rnd.random() < 0.1: c = shade(head, 1.25)
            if 2 <= x <= 13 and 2 <= y <= 13 and rnd.random() < 0.08: c = shade(head, 0.8)
            px(im, x, y, c)
    save(im, "hammer_head")
    # the haft: dark oak with two bronze bands and a leather wrap
    im = img(16)
    wood = (78, 54, 32, 255); wood_l = (112, 80, 48, 255); bronze = (196, 150, 66, 255); leather = (96, 62, 38, 255)
    for y in range(16):
        for x in range(16):
            c = wood_l if (x + y * 3) % 7 == 0 else wood
            if y in (2, 3, 12, 13): c = bronze if (x + y) % 5 else shade(bronze, 0.75)
            if 6 <= y <= 9: c = leather if (x + y) % 2 else shade(leather, 0.8)
            px(im, x, y, c)
    save(im, "hammer_haft")
    # the striking face: near-black with the rune of waking glowing in it
    im = img(16)
    face = (30, 30, 36, 255); face_l = (52, 52, 62, 255); gl = (255, 140, 40, 255); hot = (255, 220, 140, 255)
    for y in range(16):
        for x in range(16):
            px(im, x, y, face_l if x in (0, 15) or y in (0, 15) else face)
    for (x, y) in [(7, 3), (8, 3), (7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9), (7, 10), (8, 10), (7, 11), (8, 11), (7, 12), (8, 12),
                   (4, 6), (5, 5), (6, 4), (9, 4), (10, 5), (11, 6), (4, 10), (5, 11), (6, 12), (9, 12), (10, 11), (11, 10), (3, 8), (4, 8), (11, 8), (12, 8)]:
        px(im, x, y, gl)
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]: px(im, x, y, hot)
    glow(im, {(7, 7), (8, 8), (7, 8), (8, 7)}, gl, 3, 0.3)
    save(im, "hammer_rune")

if __name__ == "__main__":
    colossus_heart(); heart_of_the_end(); horn_of_waking(); titan_key(); hourglass(); sleepers_ember(); dead_letter(); almanac(); hammer_textures()
    for k in KINDS:
        if k != "void": rune_item(k)
        sigil(k)
    import shutil
    shutil.move(os.path.join(OUT, "sigil_void.png"), os.path.join(OUT, "void_sigil.png"))
    print("32x32 textures written to", OUT)
