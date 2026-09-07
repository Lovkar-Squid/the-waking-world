"""The Wayfarer's Chart item: a rolled vellum map on two turned battens.
python3 tools/textures/chart.py -> resources/assets/wakingworld/textures/item/land_atlas.png"""
from PIL import Image
import os, math, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "item")
os.makedirs(OUT, exist_ok=True)
rnd = random.Random(4711)
S = 32

VELL = (226, 212, 176); VELL_L = (244, 235, 208); VELL_D = (192, 174, 132); VELL_E = (150, 130, 92)
WOOD = (104, 72, 40); WOOD_L = (150, 110, 64); WOOD_D = (58, 40, 22)
BRASS = (206, 168, 84); BRASS_L = (248, 222, 148)
INK = (78, 58, 38); INK_L = (120, 96, 66)
MARK = (214, 96, 26); MARK_L = (255, 208, 130)
LINE = (26, 18, 12, 255)


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)


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


def chart():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    # the sheet, hanging between the battens - a slight sag at the bottom, a curl at the top
    left, right = 6, 26
    top, bot = 7, 25
    for y in range(top, bot + 1):
        for x in range(left, right + 1):
            fx = (x - left) / (right - left)
            # the sheet rolls away at both sides, so the light sits a third of the way in
            v = 1.0 - abs(fx - 0.34) * 0.62
            c = mix(VELL_D, VELL_L, v)
            if y <= top + 1 or y >= bot - 1:
                c = mix(c, VELL_D, 0.55)
            if rnd.random() < 0.09:
                c = shade(c, 0.96 if rnd.random() < 0.5 else 1.04)
            px(im, x, y, c)
    # what is drawn on it: a coastline, two rivers, and the mark of where you are
    coast = [(8, 20), (10, 18), (12, 19), (14, 16), (17, 15), (19, 17), (22, 16), (24, 18)]
    for i in range(len(coast) - 1):
        (x0, y0), (x1, y1) = coast[i], coast[i + 1]
        steps = max(abs(x1 - x0), abs(y1 - y0)) * 3 + 1
        for k in range(steps + 1):
            x = x0 + (x1 - x0) * k / steps
            y = y0 + (y1 - y0) * k / steps
            if get(im, x, y)[3]:
                px(im, x, y, INK)
    for (x0, y0, x1, y1) in ((11, 21, 13, 24), (20, 19, 21, 23)):
        steps = 8
        for k in range(steps + 1):
            x = x0 + (x1 - x0) * k / steps
            y = y0 + (y1 - y0) * k / steps
            if get(im, x, y)[3]:
                px(im, x, y, INK_L)
    for (x, y) in ((9, 12), (13, 11), (18, 12), (23, 12), (11, 23), (16, 22)):
        if get(im, x, y)[3]:
            px(im, x, y, mix(get(im, x, y)[:3], INK_L, 0.75))
            px(im, x + 1, y, mix(get(im, x + 1, y)[:3], INK_L, 0.45))
    # the mark: a ringed star over the place the reader stands
    for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        px(im, 16 + dx, 18 + dy, MARK if (dx or dy) else MARK_L)
    # the battens, one each side, with brass caps
    for bx in (left - 3, right + 1):
        for y in range(top - 2, bot + 3):
            for x in range(bx, bx + 3):
                t = (x - bx) / 2.0
                c = mix(WOOD_L, WOOD, min(1.0, abs(t - 0.3) * 2.1))
                if t > 0.72:
                    c = mix(c, WOOD_D, (t - 0.72) / 0.28)
                if rnd.random() < 0.16:
                    c = shade(c, 0.93 if rnd.random() < 0.5 else 1.07)
                px(im, x, y, c)
        for y in (top - 2, top - 1, bot + 2, bot + 1):
            for x in range(bx - 1, bx + 4):
                t = (x - bx + 1) / 4.0
                px(im, x, y, mix(BRASS_L, BRASS, min(1.0, abs(t - 0.3) * 2.0)))
    # a cord tied round the middle of the right batten
    for y in (15, 16):
        for x in range(right, right + 4):
            px(im, x, y, (150, 46, 36) if y == 15 else (100, 28, 24))
    px(im, right + 4, 16, (150, 46, 36))
    px(im, right + 4, 17, (100, 28, 24))
    outline(im)
    return im


if __name__ == "__main__":
    chart().save(os.path.join(OUT, "land_atlas.png"))
    print("item/land_atlas.png")
