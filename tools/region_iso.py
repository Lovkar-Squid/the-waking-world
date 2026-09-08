"""Draw a piece of a world out of its own region files, isometrically.

    python3 tools/region_iso.py            # edit the call at the bottom, or import render()

Reads .mca directly with nbtlib - no game, no client, no screenshot. Used for the behind-the-scenes
pictures of what a cataclysm actually left behind: craters, the volcano, an earthquake fault.
Emissive blocks (magma, lava, fire, starstone) are drawn twice through a blur so they glow.
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_read import load_chunk
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---- palette: base colour, and whether it lights itself -----------------------------
P = {
 "minecraft:stone": (0x7a,0x7a,0x7a), "minecraft:deepslate": (0x51,0x51,0x56),
 "minecraft:andesite": (0x88,0x88,0x88), "minecraft:diorite": (0xbc,0xbc,0xbc),
 "minecraft:granite": (0x96,0x6a,0x53), "minecraft:tuff": (0x6c,0x6e,0x63),
 "minecraft:dirt": (0x86,0x60,0x43), "minecraft:coarse_dirt": (0x77,0x55,0x3a),
 "minecraft:rooted_dirt": (0x91,0x6e,0x52), "minecraft:grass_block": (0x71,0xa7,0x4d),
 "minecraft:podzol": (0x5b,0x3c,0x16), "minecraft:mycelium": (0x6f,0x60,0x63),
 "minecraft:gravel": (0x83,0x7e,0x7c), "minecraft:sand": (0xdb,0xd3,0xa0),
 "minecraft:sandstone": (0xd8,0xcc,0x9a), "minecraft:clay": (0xa0,0xa7,0xb4),
 "minecraft:water": (0x2f,0x5e,0xc4), "minecraft:ice": (0x91,0xb7,0xed),
 "minecraft:snow_block": (0xf0,0xf5,0xf5), "minecraft:snow": (0xf0,0xf5,0xf5),
 "minecraft:coal_ore": (0x60,0x60,0x60), "minecraft:iron_ore": (0x99,0x86,0x76),
 "minecraft:copper_ore": (0x91,0x7a,0x62), "minecraft:redstone_ore": (0x8a,0x5c,0x5c),
 "minecraft:lapis_ore": (0x5b,0x6b,0x8c), "minecraft:gold_ore": (0x9a,0x8b,0x59),
 "minecraft:diamond_ore": (0x6e,0x8f,0x8f), "minecraft:emerald_ore": (0x5e,0x8f,0x6b),
 "minecraft:oak_log": (0x6b,0x53,0x2f), "minecraft:birch_log": (0xcf,0xc9,0xb0),
 "minecraft:spruce_log": (0x4a,0x33,0x1d), "minecraft:dark_oak_log": (0x3d,0x2a,0x16),
 "minecraft:oak_leaves": (0x3f,0x7a,0x33), "minecraft:birch_leaves": (0x4d,0x7f,0x3a),
 "minecraft:spruce_leaves": (0x28,0x53,0x30), "minecraft:dark_oak_leaves": (0x2e,0x63,0x28),
 "minecraft:jungle_leaves": (0x33,0x7a,0x2c), "minecraft:azalea_leaves": (0x4a,0x82,0x38),
 "minecraft:grass": (0x60,0x9a,0x42), "minecraft:short_grass": (0x60,0x9a,0x42),
 "minecraft:tall_grass": (0x60,0x9a,0x42), "minecraft:fern": (0x5a,0x92,0x40),
 "minecraft:moss_block": (0x59,0x77,0x30), "minecraft:mud": (0x3c,0x32,0x35),
 # what the star leaves
 "minecraft:basalt": (0x4c,0x4a,0x50), "minecraft:blackstone": (0x2b,0x25,0x2b),
 "minecraft:magma_block": (0x8e,0x3f,0x22), "minecraft:soul_sand": (0x51,0x3e,0x32),
 "minecraft:fire": (0xe8,0x86,0x2f), "wakingworld:starstone": (0x2a,0x21,0x2c),
 # the mage's tower: deepslate and blackstone, and the few things that burn in it
 "minecraft:deepslate_bricks": (0x3a,0x39,0x3f), "minecraft:cracked_deepslate_bricks": (0x33,0x32,0x37),
 "minecraft:polished_deepslate": (0x36,0x35,0x3b), "minecraft:cobbled_deepslate": (0x45,0x44,0x4a),
 "minecraft:deepslate_tiles": (0x2e,0x2d,0x32), "minecraft:cracked_deepslate_tiles": (0x2a,0x29,0x2e),
 "minecraft:chiseled_deepslate": (0x32,0x31,0x36),
 "minecraft:polished_blackstone": (0x2f,0x2a,0x31), "minecraft:polished_blackstone_bricks": (0x2b,0x26,0x2d),
 "minecraft:cracked_polished_blackstone_bricks": (0x26,0x22,0x28), "minecraft:gilded_blackstone": (0x3c,0x2c,0x22),
 "minecraft:crying_obsidian": (0x2a,0x14,0x44), "minecraft:soul_soil": (0x4a,0x39,0x2e),
 "minecraft:soul_fire": (0x3a,0xc4,0xd8), "minecraft:soul_lantern": (0x5a,0xc8,0xd0),
 "minecraft:black_candle": (0x1c,0x1a,0x1e), "minecraft:respawn_anchor": (0x24,0x14,0x3c),
 "minecraft:bookshelf": (0x74,0x5b,0x38), "minecraft:chiseled_bookshelf": (0x7c,0x62,0x3c),
 "minecraft:dark_oak_planks": (0x42,0x2c,0x16), "minecraft:lectern": (0x6a,0x51,0x30),
 "minecraft:enchanting_table": (0x3a,0x28,0x3e), "minecraft:cartography_table": (0x5e,0x4a,0x33),
 "minecraft:chest": (0x8a,0x6a,0x33), "minecraft:brewing_stand": (0x6a,0x5c,0x4e),
 "minecraft:cauldron": (0x38,0x38,0x3c), "minecraft:decorated_pot": (0x9a,0x6a,0x52),
 "minecraft:chain": (0x33,0x36,0x3d), "minecraft:cobweb": (0xc8,0xc8,0xcc),
 "minecraft:mushroom_stem": (0xcb,0xc5,0xb4), "minecraft:red_mushroom_block": (0xa0,0x2c,0x28),
}
GLOW = {"minecraft:magma_block": (255,140,40, 0.9), "minecraft:fire": (255,190,90, 1.0),
        "wakingworld:starstone": (255,150,60, 1.0), "minecraft:lava": (255,150,40, 1.0),
        "minecraft:soul_fire": (90,220,240, 1.0), "minecraft:soul_lantern": (110,230,240, 0.9),
        "minecraft:crying_obsidian": (150,80,255, 0.7), "minecraft:respawn_anchor": (150,80,255, 0.6)}
SKIP = {"minecraft:air","minecraft:cave_air","minecraft:void_air"}
# thin things the renderer would otherwise draw as full cubes, burying the terrain under them
SKIP |= {"minecraft:snow", "minecraft:short_grass", "minecraft:grass", "minecraft:tall_grass",
         "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush", "minecraft:seagrass",
         "minecraft:tall_seagrass", "minecraft:vine", "minecraft:torch", "minecraft:wall_torch",
         "minecraft:sugar_cane", "minecraft:sweet_berry_bush", "minecraft:lily_pad",
         "minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower", "minecraft:azure_bluet",
         "minecraft:oxeye_daisy", "minecraft:allium", "minecraft:blue_orchid", "minecraft:pink_petals",
         "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:glow_lichen"}

def load_box(x0,z0,x1,z1,y0,y1):
    out = {}
    for cx in range(x0>>4, (x1>>4)+1):
        for cz in range(z0>>4, (z1>>4)+1):
            ch = load_chunk(cx,cz)
            if ch is None: continue
            root = ch[''] if '' in ch else ch
            for sec in root.get("sections", []):
                sy = int(sec["Y"])
                if sy*16 > y1 or sy*16+15 < y0: continue
                bs = sec.get("block_states")
                if bs is None: continue
                pal = [str(p["Name"]) for p in bs["palette"]]
                data = list(bs["data"]) if "data" in bs else None
                bits = max(4,(len(pal)-1).bit_length()); per = 64//bits; mask=(1<<bits)-1
                for i in range(4096):
                    yy = sy*16 + (i>>8)
                    if yy<y0 or yy>y1: continue
                    zz = cz*16 + ((i>>4)&15); xx = cx*16 + (i&15)
                    if not (x0<=xx<=x1 and z0<=zz<=z1): continue
                    if data is None: idx=0
                    else:
                        w = data[i//per] & 0xFFFFFFFFFFFFFFFF
                        idx = (w >> (bits*(i%per))) & mask
                    n = pal[idx]
                    if n in SKIP: continue
                    out[(xx,yy,zz)] = n
    return out

def shade(c, f):
    return tuple(max(0,min(255,int(v*f))) for v in c)

def render(cx, cz, cy, half=17, ydown=16, yup=10, out="crater.png", title=None, sub=None, disc=True):
    x0,x1 = cx-half, cx+half; z0,z1 = cz-half, cz+half
    y0,y1 = cy-ydown, cy+yup
    g = load_box(x0,z0,x1,z1,y0,y1)
    if disc:
        g = {p:n for p,n in g.items() if (p[0]-cx)**2 + (p[2]-cz)**2 <= half*half}
    TW, TH, VH = 16, 8, 9        # tile width, tile height, voxel height
    W = (x1-x0+z1-z0+2)*TW//2 + 120
    H = (x1-x0+z1-z0+2)*TH//2 + (y1-y0+1)*VH + 160
    ox = (z1-z0+1)*TW//2 + 60
    oy = 70 + (y1-cy)*VH
    base = Image.new("RGB",(W,H),(14,14,18))
    glow = Image.new("RGB",(W,H),(0,0,0))
    d = ImageDraw.Draw(base); dg = ImageDraw.Draw(glow)
    def vis(x,y,z):
        return not ((x+1,y,z) in g and (x,y,z+1) in g and (x,y+1,z) in g)
    order = sorted(g.keys(), key=lambda p:(p[0]-x0)+(p[2]-z0)+(p[1]-y0))
    for (x,y,z) in order:
        if not vis(x,y,z): continue
        n = g[(x,y,z)]
        col = P.get(n)
        if col is None:
            col = (0x6f,0x6f,0x74) if "stone" in n or "slate" in n else (0x5a,0x5a,0x60)
        sx = ox + (x-x0-(z-z0))*TW//2
        sy = oy + (x-x0+(z-z0))*TH//2 - (y-y0)*VH
        top  = [(sx,sy),(sx+TW//2,sy+TH//2),(sx,sy+TH),(sx-TW//2,sy+TH//2)]
        left = [(sx-TW//2,sy+TH//2),(sx,sy+TH),(sx,sy+TH+VH),(sx-TW//2,sy+TH//2+VH)]
        right= [(sx+TW//2,sy+TH//2),(sx,sy+TH),(sx,sy+TH+VH),(sx+TW//2,sy+TH//2+VH)]
        d.polygon(top,   fill=shade(col,1.00))
        d.polygon(left,  fill=shade(col,0.66))
        d.polygon(right, fill=shade(col,0.46))
        if n in GLOW:
            gc, a = GLOW[n][:3], GLOW[n][3]
            dg.polygon(top,   fill=shade(gc,a))
            dg.polygon(left,  fill=shade(gc,a*0.7))
            dg.polygon(right, fill=shade(gc,a*0.5))
    from PIL import ImageChops
    base = ImageChops.screen(base, glow.filter(ImageFilter.GaussianBlur(5)))
    base = ImageChops.screen(base, glow.filter(ImageFilter.GaussianBlur(16)).point(lambda v: int(v*0.55)))
    # crop to what was actually drawn, then lay it on a clean card
    bbox = base.point(lambda v: 255 if v > 20 else 0).convert("L").getbbox()
    art = base.crop(bbox)
    pad, bar = 34, (74 if title else 24)
    cw = max(art.width + pad*2, 560)
    card = Image.new("RGB", (cw, art.height + pad + bar), (14,14,18))
    card.paste(art, ((cw - art.width)//2, pad))
    if title:
        d = ImageDraw.Draw(card)
        try:
            f1 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 21)
            f2 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
        except Exception:
            f1 = f2 = ImageFont.load_default()
        d.line([(pad, card.height-bar+6), (card.width-pad, card.height-bar+6)], fill=(40,40,48))
        d.text((pad, card.height-bar+22), title, font=f1, fill=(236,230,222))
        if sub: d.text((pad, card.height-bar+50), sub, font=f2, fill=(146,142,138))
    card.save(out)
    print(out, card.size, "blocks:", len(g))

if __name__ == "__main__":
    render(441, 421, 67, half=17, ydown=14, yup=8,
           out="/tmp/claude-0/-home-claude/f973fb44-8199-5bc3-ae5b-ee262cc95fe4/scratchpad/crater.png",
           title="The first crater",
           sub="Read straight out of the test server's save - nothing placed by hand. The Waking World 0.2")
