"""Isometric preview of a StyleDump JSON with real block colours:
python3 tools/iso_style.py in.json out.png [title] [glow=#rrggbb]
Every voxel is three shaded rhombi (top, left, right), back to front; lit cells are drawn in the glow colour with a halo."""
import json, sys, math
from PIL import Image, ImageDraw, ImageFilter

BLOCK = {
    'stone': (126, 126, 126), 'cobblestone': (127, 127, 127), 'mossy_cobblestone': (107, 122, 90), 'andesite': (136, 138, 139),
    'tuff': (108, 109, 103), 'deepslate': (80, 80, 82), 'gravel': (131, 127, 127), 'magma_block': (142, 63, 31),
    'polished_blackstone': (53, 49, 58), 'polished_blackstone_bricks': (48, 44, 51), 'blackstone': (42, 38, 43), 'deepslate_tiles': (54, 54, 55),
    'iron_block': (216, 216, 216), 'polished_deepslate': (72, 72, 73), 'chiseled_polished_blackstone': (55, 51, 60), 'gilded_blackstone': (87, 74, 43),
    'deepslate_bricks': (71, 71, 72), 'cobbled_deepslate': (77, 77, 79), 'cracked_deepslate_bricks': (65, 65, 66), 'chiseled_deepslate': (55, 56, 58),
    'moss_block': (89, 112, 58), 'gold_block': (245, 214, 74),
    'quartz_block': (236, 230, 223), 'smooth_quartz': (236, 231, 223), 'quartz_bricks': (235, 229, 222), 'calcite': (223, 224, 220),
    'white_concrete': (207, 213, 214), 'chiseled_quartz_block': (232, 226, 218), 'quartz_pillar': (235, 230, 223), 'amethyst_block': (133, 98, 199),
    'sea_lantern': (168, 211, 205), 'glowstone': (240, 196, 106), 'pearlescent_froglight': (244, 226, 245), 'shroomlight': (240, 146, 70), 'crying_obsidian': (60, 20, 90),
}

def render(path, out, title=None, glow=(255, 150, 60), scale=None, front=False):
    g = json.load(open(path))
    cells = g["cells"]
    if not cells:
        return
    if front:  # turn the body round so its face (-Z) and its right side look at the camera
        cells = [[-c[0] - 1, c[1], -c[2] - 1] + list(c[3:]) for c in cells]
    xs = [c[0] for c in cells]; ys = [c[1] for c in cells]; zs = [c[2] for c in cells]
    x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    occ = set((c[0], c[1], c[2]) for c in cells)
    s = scale or max(4, int(900 / max(1, (x1 - x0 + z1 - z0 + 4))))
    cos30, sin30 = math.sqrt(3) / 2, 0.5
    def proj(x, y, z):
        return ((x - z) * cos30 * s, (x + z) * sin30 * s - y * s)
    pts = [proj(x, y, z) for x in (x0, x1 + 1) for y in (y0, y1 + 1) for z in (z0, z1 + 1)]
    minu = min(p[0] for p in pts); maxu = max(p[0] for p in pts); minv = min(p[1] for p in pts); maxv = max(p[1] for p in pts)
    W, H = int(maxu - minu) + 40, int(maxv - minv) + 80
    img = Image.new("RGB", (W, H), (16, 18, 24))
    halo = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(img); hd = ImageDraw.Draw(halo)
    ox, oy = 20 - minu, 60 - minv
    order = sorted(cells, key=lambda c: (c[0] + c[2], c[1]))
    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c)
    for x, y, z, label, special, block in order:
        if (x + 1, y, z) in occ and (x, y, z + 1) in occ and (x, y + 1, z) in occ:
            continue
        lit = special in (7, 8)
        base = glow if lit else BLOCK.get(block, (200, 60, 200))
        top = [proj(x, y + 1, z), proj(x + 1, y + 1, z), proj(x + 1, y + 1, z + 1), proj(x, y + 1, z + 1)]
        left = [proj(x, y, z + 1), proj(x + 1, y, z + 1), proj(x + 1, y + 1, z + 1), proj(x, y + 1, z + 1)]
        right = [proj(x + 1, y, z), proj(x + 1, y, z + 1), proj(x + 1, y + 1, z + 1), proj(x + 1, y + 1, z)]
        for poly, f in ((top, 1.0 if not lit else 1.15), (left, 0.78 if not lit else 1.0), (right, 0.6 if not lit else 0.9)):
            pp = [(u + ox, v + oy) for u, v in poly]
            d.polygon(pp, fill=shade(base, f), outline=shade(base, 0.85 if not lit else 1.0))
            if lit:
                hd.polygon(pp, fill=glow)
    halo = halo.filter(ImageFilter.GaussianBlur(radius=max(3, s)))
    img = Image.blend(img, Image.eval(halo, lambda v: v), 0.0)
    # additive-ish halo
    import numpy as np
    a = np.asarray(img).astype(int); h = np.asarray(halo).astype(int)
    a = np.clip(a + h * 0.55, 0, 255).astype('uint8')
    img = Image.fromarray(a)
    if title:
        d = ImageDraw.Draw(img)
        d.text((16, 12), title, fill=(235, 235, 235))
        d.text((16, 28), g.get('palette', '')[:110], fill=(140, 150, 165))
    img.save(out)

if __name__ == "__main__":
    glow = (255, 150, 60)
    if len(sys.argv) > 4 and sys.argv[4].startswith('#'):
        hx = sys.argv[4][1:]
        glow = tuple(int(hx[i:i + 2], 16) for i in (0, 2, 4))
    render(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None, glow, front='front' in sys.argv[5:])
