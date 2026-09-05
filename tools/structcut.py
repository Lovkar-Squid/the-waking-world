"""Renders only the built blocks of a dump (natural terrain stripped) - a cutaway of a structure.
python3 tools/structcut.py <server.out> "<header>" out.png [scale] [turns] [ymax]"""
import sys
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from isodump import load, render
NATURAL = {"stone", "dirt", "grass_block", "sand", "sandstone", "gravel", "andesite", "diorite", "granite", "tuff", "deepslate", "coal_ore", "iron_ore",
           "copper_ore", "lapis_ore", "gold_ore", "redstone_ore", "emerald_ore", "diamond_ore", "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_copper_ore",
           "deepslate_lapis_ore", "deepslate_gold_ore", "deepslate_redstone_ore", "deepslate_diamond_ore", "deepslate_emerald_ore", "water", "seagrass", "tall_seagrass", "kelp", "kelp_plant",
           "sea_pickle", "lily_pad", "short_grass", "tall_grass", "fern", "large_fern", "oak_leaves", "oak_log", "birch_leaves", "birch_log", "spruce_leaves", "spruce_log",
           "acacia_leaves", "acacia_log", "dark_oak_leaves", "dark_oak_log", "mangrove_leaves", "mangrove_log", "mangrove_roots", "mud", "clay", "red_sand", "red_sandstone",
           "terracotta", "orange_terracotta", "red_terracotta", "yellow_terracotta", "brown_terracotta", "white_terracotta", "light_gray_terracotta", "coarse_dirt",
           "dead_bush", "cactus", "sugar_cane", "bubble_column", "glow_lichen", "moss_carpet", "moss_block", "dripstone_block", "calcite", "bedrock", "poppy", "dandelion",
           "cornflower", "oxeye_daisy", "azure_bluet", "sunflower", "brown_mushroom", "red_mushroom", "vine", "snow", "ice", "packed_ice", "smooth_basalt", "amethyst_block", "budding_amethyst",
           "rooted_dirt", "hanging_roots", "cave_vines", "cave_vines_plant", "spore_blossom", "azalea", "flowering_azalea", "big_dripleaf", "small_dripleaf", "sculk", "sculk_vein"}
path, header, out = sys.argv[1], sys.argv[2], sys.argv[3]
scale = int(sys.argv[4]) if len(sys.argv) > 4 else 4
turns = int(sys.argv[5]) if len(sys.argv) > 5 else 0
ymax = int(sys.argv[6]) if len(sys.argv) > 6 else 10 ** 9
b, layers, legend = load(path, header)
nat = {c for c, n in legend.items() if n in NATURAL}
cut = {}
for y, rows in layers.items():
    if y > ymax: continue
    cut[y] = ["".join("." if ch in nat else ch for ch in row) for row in rows]
render(b, cut, legend, out, scale, turns)
