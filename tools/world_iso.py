"""Draw a box of a world out of its region files with the shape-aware renderer (stairs, panes, fences...).

    MC_REGION=/path/to/world/region python3 tools/world_iso.py cx cz y0 y1 halfx halfz out.png [scale] [title]

The box is (cx ± halfx, y0..y1, cz ± halfz). Blocks below the lowest air over each column are not drawn,
so a house on a hill is a house on a hill and not a cube of dirt; the ground under it is kept two deep.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_read import state_grid
import plan_iso


def main():
    cx, cz, y0, y1, hx, hz = map(int, sys.argv[1:7])
    out = sys.argv[7]
    scale = int(sys.argv[8]) if len(sys.argv) > 8 else 10
    title = sys.argv[9] if len(sys.argv) > 9 else None
    g = state_grid(cx - hx, cz - hz, cx + hx, cz + hz, y0, y1)
    # keep the surface and two blocks under it: the top solid block of each column and what is above it
    tops = {}
    for (x, y, z), (n, p) in g.items():
        if (x, z) not in tops or y > tops[(x, z)]:
            tops[(x, z)] = y
    blocks = []
    for (x, y, z), (n, p) in g.items():
        n = n.split(":", 1)[1] if ":" in n else n
        # a column's ground is the highest block that is not a plant, a leaf or something that stands on the ground
        blocks.append((x, y, z, n, p))
    # prune: for each column find the highest 'ground' block (grass/dirt/stone-like), keep y >= ground - 2
    ground = {}
    GROUND = {"grass_block", "dirt", "stone", "coarse_dirt", "podzol", "sand", "gravel", "andesite", "diorite", "granite", "dirt_path", "cobblestone",
              "stone_bricks", "polished_andesite", "mossy_cobblestone", "deepslate", "tuff", "clay", "mud", "rooted_dirt", "snow_block", "farmland", "water", "moss_block"}
    for x, y, z, n, p in blocks:
        if n in GROUND and ((x, z) not in ground or y > ground[(x, z)]):
            pass
    cols = {}
    for x, y, z, n, p in blocks:
        cols.setdefault((x, z), []).append((y, n))
    keep = []
    for (x, z), lst in cols.items():
        lst.sort()
        # the ground is the highest block below which everything is 'ground' - approximate: the highest GROUND block
        gy = max((y for y, n in lst if n in GROUND), default=min(y for y, n in lst))
        keep.append(((x, z), gy - 2))
    floor = dict(keep)
    pruned = [(x, y, z, n, p) for x, y, z, n, p in blocks if y >= floor[(x, z)]]
    img = plan_iso.render(pruned, out, scale, title, None, pad=0)
    print(out, img.size, len(pruned), "blocks")


if __name__ == "__main__":
    main()
