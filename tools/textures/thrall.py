"""The Stone Thrall's skin: the 64x64 humanoid layout painted as cracked deepslate with moss,
orange ember eyes and a glowing crack over the heart. python3 tools/textures/thrall.py"""
from PIL import Image
import random, os
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "textures", "entity")
os.makedirs(OUT, exist_ok=True)
import sys
HOLLOW = len(sys.argv) > 1 and sys.argv[1] == "hollow"
rnd = random.Random(77 if HOLLOW else 31)
im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
# first-layer boxes of the 64x64 layout: (x, y, w, h) regions that are actually mapped
regions = [(0, 0, 32, 16),   # head
           (16, 16, 24, 16), # body
           (40, 16, 16, 16), # right arm
           (0, 16, 16, 16),  # right leg
           (32, 48, 16, 16), # left arm
           (16, 48, 16, 16)] # left leg
def stone():
    v = rnd.randint(-14, 14)
    if HOLLOW:
        # end stone gone grey-violet, veined with obsidian
        base = (196 + v // 2, 190 + v // 2, 150 + v // 2)
        r = rnd.random()
        if r < 0.12: return (28, 18, 40, 255)          # obsidian vein
        if r < 0.20: return (120 + v, 80 + v, 150 + v, 255)  # a violet bruise
        return base + (255,)
    base = (92 + v, 90 + v, 96 + v)
    r = rnd.random()
    if r < 0.06: return (60 + v, 88 + v, 58 + v, 255)  # a mossy fleck
    if r < 0.14: return (48 + v, 46 + v, 52 + v, 255)  # a dark crack pixel
    return base + (255,)
for (x0, y0, w, h) in regions:
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            im.putpixel((x, y), stone())
# cracks: a few dark lines across the body and limbs
for _ in range(26):
    x0, y0, w, h = rnd.choice(regions)
    x, y = rnd.randint(x0, x0 + w - 1), rnd.randint(y0, y0 + h - 1)
    for i in range(rnd.randint(3, 7)):
        if x0 <= x < x0 + w and y0 <= y < y0 + h: im.putpixel((x, y), (20, 12, 30, 255) if HOLLOW else (40, 38, 44, 255))
        x += rnd.choice([-1, 0, 1]); y += 1
# the face (front of the head is at 8..16, 8..16): ember eyes and a grim mouth crack
EYE = (200, 120, 255, 255) if HOLLOW else (255, 140, 40, 255)
EYE_L = (240, 210, 255, 255) if HOLLOW else (255, 220, 130, 255)
for (x, y) in [(9, 11), (10, 11), (13, 11), (14, 11)]: im.putpixel((x, y), EYE)
for (x, y) in [(10, 11), (13, 11)]: im.putpixel((x, y), EYE_L)
for x in range(10, 14): im.putpixel((x, 14), (36, 34, 40, 255))
# the ember in the chest (body front is 20..28, 20..32)
for (x, y) in [(23, 24), (24, 24), (23, 25), (24, 25), (22, 26), (25, 23), (24, 27)]: im.putpixel((x, y), EYE)
im.putpixel((24, 25), EYE_L)
# glowing veins from the chest ember down the arms' outer faces (right arm outer face x 40..44 is the top? keep it simple: a few orange pixels)
for (x, y) in [(41, 22), (41, 25), (45, 27), (33, 55), (36, 58)]: im.putpixel((x, y), EYE)
im.save(os.path.join(OUT, "hollow_thrall.png" if HOLLOW else "stone_thrall.png"))
print("thrall skin written")
