"""Checks the keep's square bailey wall for see-through holes (axis-aligned rays from outside in).
python3 tools/baileycheck.py <server.out> "<dump header>" cx cy cz"""
import sys
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from isodump import load
from wallcheck_common import see_through
path, header, cx, cy, cz = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
(x0, y0, z0, x1, y1, z1), layers, legend = load(path, header)
def block(x, y, z):
    rows = layers.get(y)
    if rows is None: return "air"
    zi, xi = z - z0, x - x0
    if zi < 0 or zi >= len(rows) or xi < 0 or xi >= len(rows[zi]): return "air"
    c = rows[zi][xi]
    return legend.get(c, "air") if c != "." else "air"
BAILEY = 22
holes = []
for side in ("N", "S", "W", "E"):
    for t in range(-26, 27):
        open_h = []
        for dy in range(1, 7):
            hit = False
            for m in range(27, 16, -1):
                if side == "N": dx, dz = t, -m
                elif side == "S": dx, dz = t, m
                elif side == "W": dx, dz = -m, t
                else: dx, dz = m, t
                if not see_through(block(cx + dx, cy + dy, cz + dz)):
                    hit = True
                    break
            if not hit: open_h.append(dy)
        if len(open_h) >= 3: holes.append((side, t, open_h))
print("open columns:", len(holes))
for h in holes: print("  side", h[0], "offset", h[1], "heights", h[2])
