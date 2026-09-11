"""Shape-aware isometric render of a HousePreview JSON (or any {name: {blocks: [[x,y,z,block,{props}], ...]}}).

    python3 tools/plan_iso.py houses.json out_dir [scale]

Stairs, slabs, fences, walls, panes, doors, trapdoors, lanterns, beds, pots and the rest are drawn as the
boxes they really are (a stair is a slab and a quarter, a shutter is a thin plate against the wall), so
what comes out is close to what the game shows - close enough to judge a roof line or a jetty without a
client. Each design gets its own PNG on a patch of grass, plus a contact sheet of all of them.
"""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFont

# ---- colours ----------------------------------------------------------------------------------
C = {
    "grass_block": (98, 150, 60), "dirt": (120, 86, 58), "dirt_path": (150, 122, 70), "gravel": (128, 126, 124), "farmland": (110, 70, 40),
    "cobblestone": (122, 122, 122), "mossy_cobblestone": (104, 122, 92), "cobblestone_wall": (122, 122, 122), "andesite": (136, 136, 138),
    "stone_bricks": (128, 126, 124), "cracked_stone_bricks": (118, 116, 114), "mossy_stone_bricks": (108, 126, 96), "chiseled_stone_bricks": (132, 130, 128),
    "stone_brick_slab": (128, 126, 124), "polished_andesite": (150, 150, 152), "bricks": (150, 90, 70), "brick_stairs": (150, 90, 70), "brick_slab": (150, 90, 70),
    "mud_bricks": (140, 106, 80), "white_terracotta": (210, 180, 160), "light_gray_terracotta": (145, 115, 105), "terracotta": (150, 90, 70),
    "oak_log": (108, 86, 52), "oak_planks": (162, 130, 78), "oak_stairs": (162, 130, 78), "oak_slab": (162, 130, 78), "oak_fence": (150, 120, 70), "oak_door": (150, 118, 70),
    "oak_trapdoor": (150, 118, 70), "oak_leaves": (60, 120, 40), "stripped_oak_log": (180, 150, 100), "oak_pressure_plate": (162, 130, 78), "oak_wall_hanging_sign": (140, 100, 60),
    "spruce_log": (62, 42, 24), "spruce_planks": (114, 84, 48), "spruce_stairs": (114, 84, 48), "spruce_slab": (114, 84, 48), "spruce_fence": (114, 84, 48), "spruce_door": (114, 84, 48),
    "spruce_trapdoor": (114, 84, 48), "spruce_leaves": (50, 90, 50),
    "dark_oak_log": (60, 40, 20), "dark_oak_planks": (66, 43, 20), "dark_oak_stairs": (66, 43, 20), "dark_oak_slab": (66, 43, 20), "dark_oak_fence": (66, 43, 20), "dark_oak_door": (66, 43, 20),
    "dark_oak_trapdoor": (66, 43, 20), "dark_oak_leaves": (40, 80, 30),
    "birch_planks": (200, 186, 130), "birch_fence": (200, 186, 130), "birch_door": (200, 186, 130), "birch_trapdoor": (200, 186, 130), "birch_leaves": (90, 140, 60), "birch_log": (200, 196, 180),
    "deepslate_tiles": (64, 64, 70), "deepslate_tile_stairs": (64, 64, 70), "deepslate_tile_slab": (64, 64, 70), "deepslate_tile_wall": (64, 64, 70),
    "glass_pane": (200, 230, 240), "cyan_stained_glass_pane": (60, 170, 180), "lantern": (255, 200, 80), "chain": (80, 80, 90), "campfire": (255, 140, 40),
    "red_bed": (200, 40, 40), "white_bed": (235, 235, 235), "blue_bed": (50, 60, 170), "light_gray_carpet": (160, 160, 160), "cyan_carpet": (40, 150, 160),
    "stripped_dark_oak_log": (80, 60, 36), "stripped_spruce_log": (150, 112, 70), "stripped_birch_log": (210, 200, 160), "magma_block": (120, 50, 20), "deepslate_bricks": (70, 70, 76),
    "cherry_leaves": (230, 160, 190), "snow": (240, 244, 250), "snow_block": (240, 244, 250), "coal_ore": (90, 90, 90), "short_grass": (80, 140, 50),
    "chest": (170, 120, 50), "barrel": (140, 100, 60), "crafting_table": (140, 100, 60), "bookshelf": (150, 110, 70), "composter": (120, 90, 50), "hay_block": (220, 190, 60),
    "smoker": (100, 100, 100), "furnace": (110, 110, 110), "blast_furnace": (90, 90, 96), "anvil": (70, 70, 70), "grindstone": (110, 110, 110), "smithing_table": (60, 60, 70),
    "cauldron": (60, 60, 60), "water_cauldron": (60, 90, 200), "water": (60, 90, 200), "cake": (240, 230, 220), "lectern": (150, 110, 60), "bell": (240, 200, 60), "candle": (240, 230, 200),
    "ladder": (150, 120, 70), "wheat": (200, 180, 80), "carrots": (200, 120, 40), "potatoes": (120, 150, 60), "beetroots": (170, 60, 60),
    "potted_poppy": (220, 40, 40), "potted_dandelion": (240, 220, 40), "potted_cornflower": (80, 100, 220), "potted_azure_bluet": (220, 220, 240), "potted_red_tulip": (220, 50, 40), "potted_oxeye_daisy": (240, 240, 220),
}
POT = (150, 90, 60)
S = 1 / 16

# the wider world palettes of the other renderers fill in whatever this one does not name
try:
    import isodump as _iso
    for _k, _v in _iso.COLORS.items():
        C.setdefault(_k, _v)
except Exception:
    pass
try:
    import region_iso as _riso
    for _k, _v in _riso.P.items():
        C.setdefault(_k.split(":", 1)[1] if ":" in _k else _k, _v[:3])
except Exception:
    pass
C.setdefault("short_grass", (90, 150, 60)); C.setdefault("tall_grass", (90, 150, 60)); C.setdefault("snow", (240, 240, 250))


def boxes(name, props, nb):
    """The boxes of one block in cell fractions: list of (x0,y0,z0,x1,y1,z1,colour)."""
    c = C.get(name, (200, 80, 200))
    facing = props.get("facing", "north")
    half = props.get("half", "bottom")
    typ = props.get("type", "bottom")
    if name.endswith("_stairs"):
        out = []
        if half == "bottom":
            out.append((0, 0, 0, 1, .5, 1, c))
            q = {"east": (.5, .5, 0, 1, 1, 1), "west": (0, .5, 0, .5, 1, 1), "south": (0, .5, .5, 1, 1, 1), "north": (0, .5, 0, 1, 1, .5)}[facing]
        else:
            out.append((0, .5, 0, 1, 1, 1, c))
            q = {"east": (.5, 0, 0, 1, .5, 1), "west": (0, 0, 0, .5, .5, 1), "south": (0, 0, .5, 1, .5, 1), "north": (0, 0, 0, 1, .5, .5)}[facing]
        out.append(q + (c,))
        return out
    if name.endswith("_slab"):
        if typ == "double": return [(0, 0, 0, 1, 1, 1, c)]
        return [(0, .5, 0, 1, 1, 1, c)] if typ == "top" else [(0, 0, 0, 1, .5, 1, c)]
    if name.endswith("_fence") or name.endswith("_wall") or name == "chain":
        w = .25 if name.endswith("_fence") or name == "chain" else .375
        if name == "chain": w = .1875
        out = [(.5 - w / 2 * (2 if name.endswith("_wall") else 1), 0, .5 - w / 2 * (2 if name.endswith("_wall") else 1),
                .5 + w / 2 * (2 if name.endswith("_wall") else 1), 1, .5 + w / 2 * (2 if name.endswith("_wall") else 1), c)]
        if name != "chain":
            h = .9375 if name.endswith("_fence") else .875
            aw = .125 if name.endswith("_fence") else .1875
            for dx, dz, key in ((1, 0, "e"), (-1, 0, "w"), (0, 1, "s"), (0, -1, "n")):
                if nb.get(key):
                    if dx: out.append((.5 if dx > 0 else 0, .375 if name.endswith("_fence") else 0, .5 - aw, 1 if dx > 0 else .5, h, .5 + aw, c))
                    else: out.append((.5 - aw, .375 if name.endswith("_fence") else 0, .5 if dz > 0 else 0, .5 + aw, h, 1 if dz > 0 else .5, c))
        return out
    if name.endswith("glass_pane"):
        ew, ns = nb.get("e") or nb.get("w"), nb.get("n") or nb.get("s")
        if ew and not ns: return [(0, 0, .4375, 1, 1, .5625, c)]
        if ns and not ew: return [(.4375, 0, 0, .5625, 1, 1, c)]
        return [(.4375, 0, .4375, .5625, 1, .5625, c)] + ([(0, 0, .4375, 1, 1, .5625, c)] if ew else []) + ([(.4375, 0, 0, .5625, 1, 1, c)] if ns else [])
    if name.endswith("_trapdoor"):
        if props.get("open") == "true":
            return [{"south": (0, 0, 0, 1, 1, .1875), "north": (0, 0, .8125, 1, 1, 1), "east": (0, 0, 0, .1875, 1, 1), "west": (.8125, 0, 0, 1, 1, 1)}[facing] + (c,)]
        return [(0, .8125, 0, 1, 1, 1, c)] if half == "top" else [(0, 0, 0, 1, .1875, 1, c)]
    if name.endswith("_door"):
        return [{"south": (0, 0, 0, 1, 1, .1875), "north": (0, 0, .8125, 1, 1, 1), "east": (0, 0, 0, .1875, 1, 1), "west": (.8125, 0, 0, 1, 1, 1)}[facing] + (c,)]
    if name == "ladder":
        return [{"south": (0, 0, 0, 1, 1, .125), "north": (0, 0, .875, 1, 1, 1), "east": (0, 0, 0, .125, 1, 1), "west": (.875, 0, 0, 1, 1, 1)}[facing] + (c,)]
    if name == "lantern":
        if props.get("hanging") == "true":
            return [(.3125, .0625, .3125, .6875, .5, .6875, c), (.4375, .5, .4375, .5625, 1, .5625, (80, 80, 90))]
        return [(.3125, 0, .3125, .6875, .4375, .6875, c), (.375, .4375, .375, .625, .5625, .625, (80, 80, 90))]
    if name == "campfire":
        return [(0, 0, 0, 1, .25, 1, (90, 70, 40)), (.25, .25, .25, .75, .6, .75, c)]
    if name.startswith("potted_"):
        return [(.3125, 0, .3125, .6875, .375, .6875, POT), (.4375, .375, .4375, .5625, .75, .5625, c)]
    if name.endswith("_bed"):
        return [(0, .1875, 0, 1, .5625, 1, c), (0, 0, 0, 1, .1875, 1, (80, 60, 40))]
    if name.endswith("_carpet"):
        return [(0, 0, 0, 1, .0625, 1, c)]
    if name.endswith("_pressure_plate"):
        return [(.0625, 0, .0625, .9375, .0625, .9375, c)]
    if name == "oak_wall_hanging_sign":
        return [(.0625, 0, .4375, .9375, .625, .5625, c), (0, .875, .4375, 1, 1, .5625, (80, 80, 90))]
    if name == "bell":
        return [(.25, .25, .25, .75, .8125, .75, c)]
    if name == "anvil":
        return [(.125, 0, .125, .875, .25, .875, c), (.1875, .625, 0, .8125, 1, 1, c), (.25, .25, .25, .75, .625, .75, c)]
    if name == "grindstone":
        return [(.125, .125, .375, .875, .875, .625, c)]
    if name == "candle":
        return [(.4375, 0, .4375, .5625, .375, .5625, c)]
    if name in ("cauldron", "water_cauldron"):
        return [(.0625, 0, .0625, .9375, 1, .9375, (60, 60, 60))] + ([(.125, .75, .125, .875, .9375, .875, c)] if name == "water_cauldron" else [])
    if name in ("wheat", "carrots", "potatoes", "beetroots"):
        return [(.125, 0, .125, .875, .5, .875, c)]
    if name == "farmland":
        return [(0, 0, 0, 1, .9375, 1, c)]
    if name == "lectern":
        return [(0, 0, 0, 1, .875, 1, c)]
    if name == "cake":
        return [(.0625, 0, .0625, .9375, .5, .9375, c)]
    return [(0, 0, 0, 1, 1, 1, c)]


def render(blocks, out, scale=14, title=None, ground=None, pad=2):
    """blocks: list of (x,y,z,name,props). ground: y of a grass patch drawn under the whole footprint."""
    cells = {(x, y, z): (n, p) for x, y, z, n, p in blocks if n != "air"}
    xs = [b[0] for b in blocks]; ys = [b[1] for b in blocks]; zs = [b[2] for b in blocks]
    x0, x1, y0, y1, z0, z1 = min(xs) - pad, max(xs) + pad, min(ys), max(ys), min(zs) - pad, max(zs) + pad
    if ground is not None:
        y0 = min(y0, ground)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, ground, z) not in cells:
                    cells[(x, ground, z)] = ("grass_block", {})
                for y in range(ground - 1, ground - 3, -1):
                    if (x, y, z) not in cells:
                        cells[(x, y, z)] = ("dirt", {})
        y0 = min(y0, ground - 2)
    s = scale
    cos30, sin30 = math.sqrt(3) / 2, 0.5

    def proj(x, y, z):
        return ((x - z) * cos30 * s, (x + z) * sin30 * s - y * s)

    pts = [proj(x, y, z) for x in (x0, x1 + 1) for y in (y0, y1 + 2) for z in (z0, z1 + 1)]
    minu = min(p[0] for p in pts); maxu = max(p[0] for p in pts); minv = min(p[1] for p in pts); maxv = max(p[1] for p in pts)
    W, H = int(maxu - minu) + 40, int(maxv - minv) + 70
    img = Image.new("RGB", (W, H), (28, 30, 36))
    d = ImageDraw.Draw(img)
    ox, oy = 20 - minu, 50 - minv

    def P(x, y, z):
        u, v = proj(x, y, z)
        return (u + ox, v + oy)

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c)

    items = []
    for (x, y, z), (name, props) in cells.items():
        nb = {}
        for key, dx, dz in (("e", 1, 0), ("w", -1, 0), ("s", 0, 1), ("n", 0, -1)):
            o = cells.get((x + dx, y, z + dz))
            if o:
                on = o[0]
                nb[key] = on.endswith("_fence") or on.endswith("_wall") or on.endswith("glass_pane") or not any(on.endswith(k) for k in ("_slab", "_stairs", "_door", "_trapdoor", "lantern", "chain", "_carpet", "campfire")) and on not in ("air",)
        for bx in boxes(name, props, nb):
            items.append((x, y, z, bx))
    # painter's order: far to near by cell, then by height, then by the box's own position inside the cell
    items.sort(key=lambda t: (t[0] + t[2], t[1], t[3][0] + t[3][2], t[3][1]))
    for x, y, z, (bx0, by0, bz0, bx1, by1, bz1, c) in items:
        X0, X1, Y0, Y1, Z0, Z1 = x + bx0, x + bx1, y + by0, y + by1, z + bz0, z + bz1
        top = [P(X0, Y1, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X0, Y1, Z1)]
        left = [P(X0, Y0, Z1), P(X1, Y0, Z1), P(X1, Y1, Z1), P(X0, Y1, Z1)]    # +z face (south)
        right = [P(X1, Y0, Z0), P(X1, Y0, Z1), P(X1, Y1, Z1), P(X1, Y1, Z0)]   # +x face (east)
        d.polygon(top, fill=shade(c, 1.0))
        d.polygon(left, fill=shade(c, 0.78))
        d.polygon(right, fill=shade(c, 0.6))
    if title:
        d.text((10, 8), title, fill=(230, 230, 230))
    img.save(out)
    return img


def main():
    path, outdir = sys.argv[1], sys.argv[2]
    scale = int(sys.argv[3]) if len(sys.argv) > 3 else 14
    os.makedirs(outdir, exist_ok=True)
    data = json.load(open(path))
    sheets = []
    for name, house in data.items():
        blocks = [(b[0], b[1], b[2], b[3], b[4]) for b in house["blocks"]]
        ground = house["doorstep"][1] - 1
        img = render(blocks, os.path.join(outdir, name + ".png"), scale, f"{name} - {house.get('palette', '')}", ground)
        sheets.append(img)
        print(name, img.size)
    # a contact sheet
    if sheets:
        w = max(i.size[0] for i in sheets)
        h = sum(i.size[1] for i in sheets)
        cols = 3
        rows = math.ceil(len(sheets) / cols)
        cw = max(i.size[0] for i in sheets); ch = max(i.size[1] for i in sheets)
        sheet = Image.new("RGB", (cw * cols, ch * rows), (28, 30, 36))
        for i, im in enumerate(sheets):
            sheet.paste(im, ((i % cols) * cw, (i // cols) * ch))
        sheet.save(os.path.join(outdir, "sheet.png"))
        print("sheet", sheet.size)


if __name__ == "__main__":
    main()
