"""Isometric preview of a GridDump JSON (labels + specials), no Blender needed:
python3 tools/iso_render.py in.json out.png [title]
Draws every voxel as three shaded rhombi (top, left, right), back to front. Front of the body (-Z) faces the viewer's left."""
import json, sys, math
from PIL import Image, ImageDraw

PART = {1: (150, 150, 150), 2: (175, 170, 165), 3: (135, 140, 150), 4: (125, 130, 140), 5: (110, 115, 120), 6: (100, 105, 110)}
SPECIAL = {7: (255, 120, 40), 8: (255, 240, 120)}

def render(path, out, title=None, scale=None):
    g = json.load(open(path))
    cells = g["cells"]
    if not cells:
        return
    xs = [c[0] for c in cells]; ys = [c[1] for c in cells]; zs = [c[2] for c in cells]
    x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    occ = set((c[0], c[1], c[2]) for c in cells)
    # isometric: screen u = (x - z) * cos30, v = (x + z) * sin30 - y
    s = scale or max(4, int(900 / max(1, (x1 - x0 + z1 - z0 + 4))))
    cos30, sin30 = math.sqrt(3) / 2, 0.5
    def proj(x, y, z):
        return ((x - z) * cos30 * s, (x + z) * sin30 * s - y * s)
    pts = [proj(x, y, z) for x in (x0, x1 + 1) for y in (y0, y1 + 1) for z in (z0, z1 + 1)]
    minu = min(p[0] for p in pts); maxu = max(p[0] for p in pts); minv = min(p[1] for p in pts); maxv = max(p[1] for p in pts)
    W, H = int(maxu - minu) + 40, int(maxv - minv) + 60
    img = Image.new("RGB", (W, H), (28, 30, 36))
    d = ImageDraw.Draw(img)
    ox, oy = 20 - minu, 40 - minv
    # painter's order: far to near = increasing x + z, then increasing y (top drawn last within a column) - sort by (x + z, y)
    order = sorted(cells, key=lambda c: (c[0] + c[2], c[1]))
    for x, y, z, label, special in order:
        base = SPECIAL.get(special) or PART.get(label, (200, 60, 200))
        # skip fully hidden voxels (all three visible faces covered)
        if (x + 1, y, z) in occ and (x, y, z + 1) in occ and (x, y + 1, z) in occ:
            continue
        top = [proj(x, y + 1, z), proj(x + 1, y + 1, z), proj(x + 1, y + 1, z + 1), proj(x, y + 1, z + 1)]
        left = [proj(x, y, z + 1), proj(x + 1, y, z + 1), proj(x + 1, y + 1, z + 1), proj(x, y + 1, z + 1)]   # +z face
        right = [proj(x + 1, y, z), proj(x + 1, y, z + 1), proj(x + 1, y + 1, z + 1), proj(x + 1, y + 1, z)]  # +x face
        def shade(c, f):
            return tuple(max(0, min(255, int(v * f))) for v in c)
        if (x, y + 1, z) not in occ:
            d.polygon([(px + ox, py + oy) for px, py in top], fill=shade(base, 1.15))
        if (x, y, z + 1) not in occ:
            d.polygon([(px + ox, py + oy) for px, py in left], fill=shade(base, 0.8))
        if (x + 1, y, z) not in occ:
            d.polygon([(px + ox, py + oy) for px, py in right], fill=shade(base, 0.6))
    if title:
        d.text((10, 10), title, fill=(230, 230, 230))
    img.save(out)
    return img

if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
