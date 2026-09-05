"""The Titan's Gate sheet: a tileable, animated square of the void - deep purple-black cloud with
violet flow, magenta crests and pale motes drifting through, breathing slowly. 32 frames of 32x32,
stacked (interpolated in game), seamless across blocks and across the loop.

python3 tools/textures/gate.py -> resources/assets/wakingworld/textures/block/titan_gate.png (+ .mcmeta)
"""
import json, math, os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "block")
os.makedirs(OUT, exist_ok=True)

SIZE, FRAMES = 32, 32

def lerp(a, b, t):
    return a + (b - a) * t

# the ramp: black-violet -> deep purple -> the Titan's violet -> magenta -> pale pink at the very crests
RAMP = [(0.00, (8, 3, 16)), (0.30, (34, 10, 68)), (0.55, (92, 34, 160)), (0.75, (178, 102, 255)), (0.90, (232, 150, 255)), (1.00, (255, 226, 255))]

def colour(v):
    v = min(1.0, max(0.0, v))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        if v <= t1:
            f = (v - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(int(round(lerp(c0[i], c1[i], f))) for i in range(3))
    return RAMP[-1][1]

rng = np.random.default_rng(42)
# integer frequencies keep every layer seamless across the tile; integer drifts keep the loop seamless
def layers(n, seed, fmax):
    r = np.random.default_rng(seed)
    out = []
    for i in range(n):
        out.append(dict(fx=int(r.integers(1, fmax + 1)) * (1 if r.random() < 0.5 else -1), fy=int(r.integers(1, fmax + 1)),
                        dx=int(r.integers(-1, 2)), dy=int(r.integers(-1, 2)), spin=int(r.integers(1, 3)) * (1 if r.random() < 0.5 else -1),
                        amp=float(r.uniform(0.5, 1.0)), phase=float(r.uniform(0, 6.28))))
    return out

WARP = layers(3, 5, 2)     # slow, large: bends the space the flow moves through
FLOW = layers(4, 42, 2)    # the flow itself: a few broad waves whose crests become threads of light
CLOUD = layers(3, 77, 2)   # soft glow drifting behind the threads

def field(L, u, w, t):
    return L["amp"] * np.sin(2 * math.pi * (L["fx"] * (u + L["dx"] * t) + L["fy"] * (w + L["dy"] * t)) + L["spin"] * 2 * math.pi * t + L["phase"])

ys, xs = np.mgrid[0:SIZE, 0:SIZE].astype(float)
u0, w0 = (xs + 0.5) / SIZE, (ys + 0.5) / SIZE
strip = Image.new("RGBA", (SIZE, SIZE * FRAMES))
for k in range(FRAMES):
    t = k / FRAMES
    # domain warp: the flow's coordinates are pushed about by slow waves (still periodic in u, w and t)
    wu = sum(field(L, u0, w0, t) for L in WARP) / sum(L["amp"] for L in WARP)
    ww = sum(field(L, w0, u0, t * 1.0) for L in WARP) / sum(L["amp"] for L in WARP)
    u = u0 + 0.16 * wu
    w = w0 + 0.16 * ww
    flow = sum(field(L, u, w, t) for L in FLOW) / sum(L["amp"] for L in FLOW)          # -1..1
    cloud = 0.5 + 0.5 * sum(field(L, u, w, t * 1.0) for L in CLOUD) / sum(L["amp"] for L in CLOUD)  # 0..1
    # threads: the flow's crest lines, thin and bright; the cloud a soft glow behind them; the rest dark
    # threads of constant width: the flow's zero lines, measured against the local slope so a slow
    # crossing does not flood a whole region with light (gradient by periodic differences)
    gx = (np.roll(flow, -1, axis=1) - np.roll(flow, 1, axis=1)) * 0.5
    gy = (np.roll(flow, -1, axis=0) - np.roll(flow, 1, axis=0)) * 0.5
    g = np.maximum(np.sqrt(gx * gx + gy * gy), 0.03)
    ridge = np.clip(1.0 - np.abs(flow) / (0.9 * g), 0, 1) ** 1.5
    halo = np.clip(1.0 - np.abs(flow) / (3.2 * g), 0, 1) ** 2
    v = np.clip(0.05 + 0.24 * cloud ** 2 + 0.22 * halo + 0.85 * ridge, 0, 1)
    # the breath: the whole sheet swells and dims a little over the loop
    v = v * (0.92 + 0.08 * math.sin(2 * math.pi * t))
    frame = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            c = colour(float(v[y, x]))
            a = int(round(lerp(222, 252, float(v[y, x]))))  # near-solid, a touch more open in the dark
            frame.putpixel((x, y), c + (a,))
    strip.paste(frame, (0, k * SIZE))

strip.save(os.path.join(OUT, "titan_gate.png"))
with open(os.path.join(OUT, "titan_gate.png.mcmeta"), "w") as fh:
    json.dump({"animation": {"frametime": 2, "interpolate": True}}, fh)
print("titan_gate.png:", strip.size)
