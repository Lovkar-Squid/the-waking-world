"""The colossi's music discs at 32x32: a dark vinyl with fine grooves, a label in the giant's own colour,
and the sigil's glyph on the label. python3 tools/textures/discs.py -> textures/item/music_disc_<kind>.png"""
from PIL import Image
import os, math

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "item")
S = 32
KINDS = {
    'stone': (216, 207, 192), 'earth': (184, 132, 60), 'sandstone': (242, 210, 122), 'ice': (166, 230, 255),
    'prismarine': (98, 216, 200), 'moss': (140, 230, 100), 'titan': (178, 102, 255),
}

def clamp(v): return max(0, min(255, int(v)))
def shade(c, f): return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)

def paint(kind, label):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx, cy, R = 16.0, 16.0, 14.5
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > R:
                continue
            # vinyl: near-black, grooves as faint rings, a soft highlight top-left
            groove = 0.85 + 0.15 * (1 if int(d * 1.6) % 2 == 0 else 0)
            hl = 1.0 + 0.55 * max(0.0, (-dx * 0.6 - dy * 0.7) / R)
            base = (34, 34, 40)
            if d > R - 1.0:
                base = (18, 18, 22)  # rim
            c = shade(base, groove * hl)
            if d <= 5.6:
                # the label
                f = 1.0 + 0.35 * (-dx * 0.6 - dy * 0.7) / 5.6
                c = shade(label, f)
                if d <= 1.6:
                    c = (12, 12, 14, 255)  # the hole
            im.putpixel((x, y), c)
    # a glyph on the label: a small rune-like mark (three strokes), in a darker label tone
    dark = shade(label, 0.45)
    for (x, y) in [(13, 12), (14, 12), (15, 12), (13, 13), (13, 14), (14, 15), (15, 16), (16, 17), (17, 18), (18, 12), (18, 13), (18, 14), (19, 12)]:
        if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= 5.6 and math.hypot(x + 0.5 - cx, y + 0.5 - cy) > 1.8:
            im.putpixel((x, y), dark)
    # one bright arc on the vinyl, the reflection
    for a in range(200, 250):
        r = math.radians(a)
        x, y = int(cx + math.cos(r) * 10.5), int(cy + math.sin(r) * 10.5)
        if 0 <= x < S and 0 <= y < S:
            im.putpixel((x, y), (90, 92, 104, 255))
    return im

os.makedirs(OUT, exist_ok=True)
for kind, col in KINDS.items():
    paint(kind, col).save(os.path.join(OUT, f"music_disc_{kind}.png"))
print("discs:", ", ".join(KINDS))
