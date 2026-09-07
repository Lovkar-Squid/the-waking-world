"""The tornado's skin: a tiling sheet of dust streaks with a hole in the middle of it.

python3 tools/textures/funnel.py -> resources/assets/wakingworld/textures/entity/tornado_funnel.png

Wrapped round the funnel in client/TornadoRenderer. It has to tile in both directions (the sheet
goes round the column twice and up it three times) and it has to be mostly holes: a solid cone
reads as a grey traffic cone, and what makes a tornado look like a tornado is that you can half
see through it to the sky on the other side.
"""
from PIL import Image
import os, math, random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld",
                   "textures", "entity")
os.makedirs(OUT, exist_ok=True)
S = 64
rnd = random.Random(31337)


def clamp(v):
    return max(0, min(255, int(v)))


def build():
    # A field of vertical streaks: each is a random walk up the sheet, wrapping at the sides, laid
    # down as alpha rather than colour so the renderer's own tint decides how dark the dust is.
    alpha = [[0.0] * S for _ in range(S)]
    for _ in range(46):
        x = rnd.random() * S
        drift = (rnd.random() - 0.5) * 0.55
        width = 0.9 + rnd.random() * 2.4
        strength = 0.35 + rnd.random() * 0.65
        for y in range(S):
            x = (x + drift + (rnd.random() - 0.5) * 0.35) % S
            # the streak fades in and out along its length, so nothing is a solid bar
            f = strength * (0.45 + 0.55 * math.sin(y / S * math.pi * (1 + rnd.random())))
            for dx in range(-int(width) - 1, int(width) + 2):
                d = abs(dx) / (width + 0.001)
                if d > 1:
                    continue
                px = int(x + dx) % S
                alpha[y][px] = min(1.0, alpha[y][px] + f * (1 - d * d) * 0.55)

    # break it up with holes, so the far wall of the funnel shows through the near one
    for _ in range(180):
        cx, cy = rnd.randrange(S), rnd.randrange(S)
        r = 1.5 + rnd.random() * 4.0
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                d = math.hypot(x - cx, y - cy) / r
                if d < 1:
                    alpha[y % S][x % S] *= 0.15 + 0.85 * d

    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            a = alpha[y][x]
            if a <= 0.01:
                continue
            # a little grain in the value as well, so the dust is not one flat sheet
            v = 0.72 + rnd.random() * 0.28
            g = clamp(224 * v)
            im.putpixel((x, y), (g, clamp(g * 0.97), clamp(g * 0.92), clamp(a * 235)))
    im.save(os.path.join(OUT, "tornado_funnel.png"))
    return im


if __name__ == "__main__":
    build()
    print("entity/tornado_funnel.png")
