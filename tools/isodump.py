"""Renders a `/wakingworld dump` from the server log as an isometric picture.
python3 tools/isodump.py <server.out> "<dump header prefix e.g. 'dump 2160, 60, 2128'>" out.png [scale]"""
import sys, re
from PIL import Image, ImageDraw

COLORS = {
    "grass_block": (98, 150, 60), "dirt": (120, 86, 58), "coarse_dirt": (110, 80, 54), "dirt_path": (150, 122, 70), "gravel": (128, 126, 124),
    "stone": (125, 125, 125), "andesite": (136, 136, 138), "granite": (150, 104, 88), "diorite": (188, 188, 190), "cobblestone": (122, 122, 122),
    "mossy_cobblestone": (104, 122, 92), "stone_bricks": (128, 126, 124), "cracked_stone_bricks": (118, 116, 114), "mossy_stone_bricks": (108, 126, 96),
    "stone_brick_stairs": (128, 126, 124), "stone_brick_wall": (128, 126, 124), "stone_brick_slab": (128, 126, 124), "cobblestone_slab": (122, 122, 122),
    "cobblestone_wall": (122, 122, 122), "mossy_cobblestone_wall": (104, 122, 92), "chiseled_stone_bricks": (130, 128, 126), "polished_andesite": (140, 140, 142),
    "oak_planks": (162, 130, 78), "oak_log": (108, 86, 52), "oak_stairs": (162, 130, 78), "oak_slab": (162, 130, 78), "oak_fence": (150, 120, 70), "oak_fence_gate": (150, 120, 70),
    "oak_door": (150, 118, 70), "oak_trapdoor": (150, 118, 70), "oak_leaves": (60, 120, 40), "birch_log": (200, 196, 180), "birch_leaves": (90, 140, 60),
    "spruce_planks": (114, 84, 48), "spruce_log": (60, 40, 20), "spruce_stairs": (114, 84, 48), "spruce_slab": (114, 84, 48), "spruce_fence": (114, 84, 48), "spruce_leaves": (50, 90, 50),
    "acacia_planks": (170, 92, 52), "acacia_log": (100, 96, 86), "sandstone": (218, 206, 160), "cut_sandstone": (222, 210, 164), "smooth_sandstone": (226, 214, 170),
    "chiseled_sandstone": (216, 204, 156), "sandstone_stairs": (218, 206, 160), "sandstone_slab": (218, 206, 160), "sandstone_wall": (218, 206, 160), "sand": (219, 207, 163), "red_sand": (190, 102, 34),
    "water": (60, 90, 200), "glass_pane": (200, 230, 240), "chest": (170, 120, 50), "trapped_chest": (170, 120, 50), "barrel": (140, 100, 60), "crafting_table": (140, 100, 60), "bookshelf": (150, 110, 70),
    "campfire": (90, 70, 40), "soul_campfire": (60, 140, 160), "lantern": (255, 200, 80), "soul_lantern": (120, 220, 240), "candle": (240, 230, 200), "chain": (70, 70, 80),
    "red_bed": (200, 40, 40), "flower_pot": (150, 90, 60), "composter": (120, 90, 50), "hay_block": (220, 190, 60), "cauldron": (60, 60, 60), "anvil": (70, 70, 70), "grindstone": (110, 110, 110), "loom": (160, 130, 90), "smoker": (100, 100, 100),
    "white_wool": (235, 235, 235), "light_gray_wool": (160, 160, 160), "brown_wool": (120, 80, 50), "red_wool": (170, 40, 40), "green_wool": (80, 110, 40),
    "farmland": (110, 70, 40), "wheat": (200, 180, 80), "pumpkin": (220, 130, 30), "carved_pumpkin": (220, 130, 30), "dead_bush": (140, 100, 50), "short_grass": (90, 150, 60), "tall_grass": (90, 150, 60),
    "vine": (60, 110, 40), "moss_carpet": (90, 130, 60), "cobweb": (230, 230, 230), "poppy": (220, 40, 40), "oxeye_daisy": (240, 240, 220), "dandelion": (240, 220, 40), "azure_bluet": (220, 220, 240), "cornflower": (80, 100, 220),
    "iron_bars": (150, 150, 150), "spawner": (30, 40, 60), "skeleton_skull": (220, 220, 210), "bone_block": (220, 215, 190), "bell": (240, 200, 60), "lectern": (150, 110, 60),
    "podzol": (96, 70, 40), "snow_block": (240, 240, 250), "packed_ice": (160, 200, 240), "coal_ore": (100, 100, 100), "copper_ore": (150, 120, 100), "iron_ore": (150, 130, 120), "gold_ore": (170, 150, 90),
    "clay": (160, 166, 176), "glow_lichen": (120, 160, 150), "moss_block": (80, 120, 50), "cave_vines": (80, 110, 50), "cave_vines_plant": (80, 110, 50), "deepslate": (80, 80, 85), "tuff": (110, 110, 100),
    "deepslate_tiles": (64, 64, 70), "deepslate_tile_stairs": (64, 64, 70), "polished_deepslate": (72, 72, 78), "deepslate_bricks": (68, 68, 74), "gold_block": (250, 210, 60), "quartz_stairs": (236, 232, 226),
    "red_carpet": (190, 40, 40), "blue_carpet": (50, 60, 170), "cyan_wall_banner": (40, 150, 160), "cyan_banner": (40, 150, 160), "sea_lantern": (180, 230, 220), "lava_cauldron": (220, 100, 20), "blast_furnace": (90, 90, 96),
    "amethyst_cluster": (170, 120, 220), "decorated_pot": (160, 100, 70), "iron_door": (200, 200, 206), "spruce_trapdoor": (114, 84, 48), "spruce_door": (114, 84, 48), "spruce_pressure_plate": (114, 84, 48), "spruce_fence_gate": (114, 84, 48),
    "chiseled_bookshelf": (150, 110, 70), "polished_blackstone_wall": (40, 36, 42), "ladder": (150, 120, 70), "carrots": (200, 120, 40), "potatoes": (120, 150, 60), "spruce_wall_sign": (114, 84, 48),
    "fern": (80, 130, 60), "large_fern": (80, 130, 60), "sunflower": (240, 200, 40), "lilac": (200, 150, 210), "rose_bush": (200, 40, 50), "peony": (230, 180, 200), "allium": (200, 120, 220), "blue_orchid": (60, 160, 220), "lily_of_the_valley": (240, 240, 240), "red_tulip": (220, 50, 40), "orange_tulip": (230, 130, 40), "white_tulip": (240, 240, 230), "pink_tulip": (230, 150, 190),
    "acacia_fence": (170, 92, 52), "acacia_door": (170, 92, 52), "acacia_stairs": (170, 92, 52), "acacia_slab": (170, 92, 52), "acacia_trapdoor": (170, 92, 52), "acacia_pressure_plate": (170, 92, 52), "oak_pressure_plate": (162, 130, 78),
    "cake": (240, 230, 220), "smooth_stone": (160, 160, 160), "smooth_stone_slab": (160, 160, 160), "andesite_slab": (136, 136, 138), "stone_slab": (125, 125, 125), "stone_stairs": (125, 125, 125),
    "snow": (240, 240, 250), "powder_snow": (240, 240, 250), "ice": (160, 200, 240), "spruce_sapling": (50, 90, 50), "oak_sapling": (60, 120, 40), "azalea": (90, 140, 60), "flowering_azalea": (170, 120, 160),
    "cobblestone_stairs": (122, 122, 122), "mossy_cobblestone_slab": (104, 122, 92), "brown_mushroom": (150, 110, 80), "red_mushroom": (200, 40, 40), "pumpkin_stem": (90, 140, 60), "melon": (100, 170, 60), "torch": (255, 220, 100), "wall_torch": (255, 220, 100),
    "white_carpet": (235, 235, 235), "spruce_button": (114, 84, 48), "cauldron_water": (60, 60, 60), "water_cauldron": (60, 90, 200),
    "chiseled_stone_bricks": (130, 128, 126), "deepslate_tile_slab": (64, 64, 70), "rooted_dirt": (110, 80, 56), "packed_mud": (140, 106, 80), "hanging_roots": (150, 110, 80), "magma_block": (140, 60, 20), "altar": (90, 70, 120), "lapis_ore": (60, 80, 150),
    "red_wool": (170, 40, 40), "polished_blackstone_wall": (40, 36, 42), "soul_lantern": (120, 220, 240), "quartz_stairs": (236, 232, 226), "mossy_stone_brick_stairs": (108, 126, 96), "spruce_wall_sign": (114, 84, 48), "oak_wall_sign": (162, 130, 78),
    "cracked_deepslate_bricks": (60, 60, 66), "polished_blackstone_bricks": (48, 44, 52), "chiseled_deepslate": (70, 70, 76), "polished_basalt": (90, 90, 96), "blackstone": (40, 38, 44),
    "netherrack": (110, 50, 50), "fire": (255, 150, 30), "smithing_table": (60, 60, 70), "chiseled_polished_blackstone": (48, 44, 52), "polished_blackstone": (50, 46, 54), "polished_blackstone_brick_stairs": (48, 44, 52),
    "polished_blackstone_brick_slab": (48, 44, 52), "deepslate_brick_stairs": (68, 68, 74), "deepslate_brick_slab": (68, 68, 74), "deepslate_brick_wall": (68, 68, 74), "gilded_blackstone": (120, 100, 50), "chain": (70, 70, 80),
    "polished_deepslate_stairs": (72, 72, 78), "polished_deepslate_slab": (72, 72, 78), "polished_deepslate_wall": (72, 72, 78), "cut_sandstone": (222, 210, 164), "sandstone_wall": (218, 206, 160), "dead_bush": (140, 100, 60), "cactus": (60, 120, 50),
    "red_sandstone": (190, 102, 34), "terracotta": (150, 90, 70), "orange_terracotta": (160, 84, 40), "red_terracotta": (140, 60, 50), "yellow_terracotta": (180, 130, 40), "brown_terracotta": (80, 50, 40), "white_terracotta": (200, 180, 160), "light_gray_terracotta": (130, 100, 90),
    "iron_bars": (150, 150, 160), "spruce_door": (114, 84, 48), "spruce_trapdoor": (114, 84, 48), "water_cauldron": (60, 60, 60), "coal_ore": (110, 110, 110), "copper_ore": (130, 120, 110), "iron_ore": (140, 130, 120), "gold_ore": (150, 140, 100), "lapis_ore": (100, 110, 150), "deepslate_coal_ore": (70, 70, 74), "dark_prismarine": (50, 90, 80), "sea_pickle": (90, 110, 60), "moss_carpet": (80, 120, 50), "glow_lichen": (120, 160, 150), "tall_seagrass": (60, 140, 80),
    "lava": (230, 110, 20), "fire": (255, 150, 30), "soul_fire": (80, 200, 220), "polished_blackstone_brick_wall": (48, 44, 52), "smoker": (100, 100, 100), "chipped_anvil": (70, 70, 70), "damaged_anvil": (70, 70, 70),
    "end_stone": (222, 226, 166), "end_stone_bricks": (218, 224, 158), "end_stone_brick_stairs": (218, 224, 158), "end_stone_brick_slab": (218, 224, 158), "end_stone_brick_wall": (218, 224, 158),
    "purpur_block": (170, 126, 170), "purpur_pillar": (176, 132, 176), "purpur_stairs": (170, 126, 170), "purpur_slab": (170, 126, 170), "obsidian": (20, 16, 32), "crying_obsidian": (60, 20, 90),
    "end_rod": (240, 230, 250), "purple_stained_glass_pane": (130, 60, 170), "purple_candle": (120, 50, 160), "chorus_plant": (100, 70, 110), "chorus_flower": (150, 110, 160), "spawner": (30, 40, 60),
    "amethyst_cluster": (170, 120, 220), "magenta_stained_glass_pane": (200, 90, 200), "cobblestone_wall": (122, 122, 122), "grass": (90, 150, 60), "seagrass": (60, 140, 80), "kelp": (60, 120, 70), "sugar_cane": (140, 190, 90), "lily_pad": (40, 120, 40), "mud": (70, 60, 60),
}

def load(path, header):
    lines = open(path, errors="replace").read().split("\n")
    start = None
    for i, l in enumerate(lines):
        if header in l and "dump" in l:
            start = i
    if start is None:
        raise SystemExit("dump not found")
    m = re.search(r"dump (-?\d+), (-?\d+), (-?\d+)\.\.(-?\d+), (-?\d+), (-?\d+)", lines[start])
    x0, y0, z0, x1, y1, z1 = map(int, m.groups())
    layers = {}
    legend = {}
    y = None
    rows = []
    for l in lines[start + 1:]:
        if l.startswith("y="):
            if y is not None:
                layers[y] = rows
            y = int(l[2:])
            rows = []
        elif l.startswith("legend:"):
            if y is not None:
                layers[y] = rows
            for part in l[len("legend: "):].split():
                if "=" in part:
                    c, name = part.split("=", 1)
                    legend[c] = name
            break
        elif y is not None and l and not l.startswith("["):
            rows.append(l)
    return (x0, y0, z0, x1, y1, z1), layers, legend

def rotate(layers, turns):
    """Turns the map by 90 degrees clockwise (seen from above) `turns` times, so the picture shows another side."""
    out = {}
    for y, rows in layers.items():
        grid = [list(r) for r in rows]
        for _ in range(turns % 4):
            grid = [list(r) for r in zip(*grid[::-1])]
        out[y] = ["".join(r) for r in grid]
    return out

def render(bounds, layers, legend, out, scale=3, turns=0):
    x0, y0, z0, x1, y1, z1 = bounds
    if turns % 2 == 1:
        x0, z0, x1, z1 = z0, x0, z1, x1
    layers = rotate(layers, turns)
    W, D, H = x1 - x0 + 1, z1 - z0 + 1, y1 - y0 + 1
    # isometric: screen x = (x - z) , screen y = (x + z)/2 - y
    img_w = (W + D) * scale + 20
    img_h = int((W + D) * scale / 2 + H * scale) + 20
    im = Image.new("RGBA", (img_w, img_h), (30, 30, 36, 255))
    dr = ImageDraw.Draw(im)
    ox = D * scale + 10
    oy = H * scale + 10
    # draw back to front: increasing x+z, then increasing y
    order = []
    for y in sorted(layers):
        rows = layers[y]
        for zi, row in enumerate(rows):
            for xi, ch in enumerate(row):
                if ch == ".":
                    continue
                order.append((xi + zi, y, xi, zi, ch))
    order.sort(key=lambda t: (t[0], t[1]))
    for _, y, xi, zi, ch in order:
        name = legend.get(ch, "")
        c = COLORS.get(name, (200, 80, 200))
        sx = ox + (xi - zi) * scale
        sy = oy + (xi + zi) * scale // 2 - (y - y0) * scale
        top = c
        left = tuple(int(v * 0.75) for v in c)
        right = tuple(int(v * 0.55) for v in c)
        s = scale
        # top rhombus
        dr.polygon([(sx, sy - s // 2), (sx + s, sy), (sx, sy + s // 2), (sx - s, sy)], fill=top)
        # left face
        dr.polygon([(sx - s, sy), (sx, sy + s // 2), (sx, sy + s // 2 + s), (sx - s, sy + s)], fill=left)
        # right face
        dr.polygon([(sx, sy + s // 2), (sx + s, sy), (sx + s, sy + s), (sx, sy + s // 2 + s)], fill=right)
    im.save(out)
    print("saved", out, im.size)

if __name__ == "__main__":
    path, header, out = sys.argv[1], sys.argv[2], sys.argv[3]
    scale = int(sys.argv[4]) if len(sys.argv) > 4 else 3
    turns = int(sys.argv[5]) if len(sys.argv) > 5 else 0
    b, layers, legend = load(path, header)
    render(b, layers, legend, out, scale, turns)
