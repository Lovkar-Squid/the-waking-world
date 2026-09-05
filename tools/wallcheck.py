"""Checks a kingdom dump for see-through gaps in the ring wall: for every angle and height, walks
from outside the moat inward through the wall band and reports angles where a whole column of
heights is open (a hole), ignoring the two gates.
python3 tools/wallcheck.py <server.out> "<dump header>" cx cy cz"""
import sys, math
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from isodump import load

path, header, cx, cy, cz = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
(x0, y0, z0, x1, y1, z1), layers, legend = load(path, header)
SEE_THROUGH = {"air", "cave_air", "void_air", "grass", "short_grass", "tall_grass", "fern", "large_fern", "torch", "wall_torch", "lantern",
               "poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "red_tulip", "white_tulip", "pink_tulip", "orange_tulip", "allium",
               "lily_of_the_valley", "blue_orchid", "sunflower", "lilac", "rose_bush", "peony", "snow", "water", "iron_bars", "cyan_banner", "cyan_wall_banner"}

def block(x, y, z):
    if y not in layers: return "air"
    rows = layers[y]
    zi, xi = z - z0, x - x0
    if zi < 0 or zi >= len(rows) or xi < 0 or xi >= len(rows[zi]): return "air"
    c = rows[zi][xi]
    return legend.get(c, "air") if c != "." else "air"

RADIUS = 56
holes = []
for a10 in range(0, 3600, 5):
    a = math.radians(a10 / 10)
    ux, uz = math.cos(a), math.sin(a)
    open_heights = []
    for dy in range(1, 8):
        hit = False
        r = 64.0
        px = pz = None
        while r > 48.0:
            x, z = int(math.floor(cx + ux * r)), int(math.floor(cz + uz * r))
            solid = block(x, cy + dy, z) not in SEE_THROUGH
            if not solid and px is not None and x != px and z != pz:
                # a diagonal step: sealed if both blocks the ray squeezes between are solid
                solid = block(x, cy + dy, pz) not in SEE_THROUGH and block(px, cy + dy, z) not in SEE_THROUGH
            if solid:
                hit = True
                break
            px, pz = x, z
            r -= 0.1
        if not hit: open_heights.append(dy)
    if len(open_heights) >= 3:
        holes.append((a10 / 10, open_heights))

# the gates sit on the +z/-z axis (angle 90 and 270) - anything within 12 blocks of them is a passage
gate_deg = math.degrees(math.atan2(12, RADIUS))
real = [h for h in holes if not (min(abs(h[0] - g) for g in (90, 270)) < gate_deg)]
print("open angle samples:", len(holes), "outside gates:", len(real))
for ang, hs in real[:60]:
    a = math.radians(ang)
    print(f"  angle {ang:6.1f}  heights {hs}  near x={cx + math.cos(a) * RADIUS:.0f} z={cz + math.sin(a) * RADIUS:.0f}")
