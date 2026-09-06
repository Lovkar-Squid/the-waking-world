#!/usr/bin/env python3
"""Builds the CurseForge modpack zip "Lovkar's Waking World Ultimate" (manifest + modlist + overrides)."""
import json, zipfile, sys, os, io
VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.0.0"
TWW_FILE = int(sys.argv[2]) if len(sys.argv) > 2 else 8824171   # The Waking World 0.1.0-beta.5 on CurseForge (beta.1 was 8817339)
# (projectID, fileID, name, url-slug-class)
MODS = [
 (1683580, TWW_FILE, "The Waking World", "mc-mods/the-waking-world"),
 # performance
 (394468, 6382651, "Sodium", "mc-mods/sodium"),
 (455508, 6661598, "Iris Shaders", "mc-mods/irisshaders"),
 (360438, 8330365, "Lithium", "mc-mods/lithium"),
 (448233, 8287097, "Entity Culling", "mc-mods/entityculling"),
 (686911, 8749421, "ImmediatelyFast", "mc-mods/immediatelyfast"),
 (790626, 8774737, "ModernFix", "mc-mods/modernfix"),
 (429235, 7524151, "FerriteCore", "mc-mods/ferritecore"),
 (508933, 8389148, "Distant Horizons", "mc-mods/distant-horizons"),
 # visuals and ambience
 (627557, 8791766, "Complementary Shaders - Reimagined", "shaders/complementary-reimagined"),
 (385587, 8791767, "Complementary Shaders - Unbound", "shaders/complementary-unbound"),
 (915902, 8791859, "Euphoria Patches", "mc-mods/euphoria-patches"),
 (535489, 7032247, "Sound Physics Remastered", "mc-mods/sound-physics-remastered"),
 (463155, 6240977, "Falling Leaves", "mc-mods/falling-leaves-forge"),
 (433760, 8274908, "Not Enough Animations", "mc-mods/not-enough-animations"),
 # quality of life
 (238222, 8815666, "Just Enough Items (JEI)", "mc-mods/jei"),
 (324717, 8591319, "Jade", "mc-mods/jade"),
 (32274, 8764294, "JourneyMap", "mc-mods/journeymap"),
 (248787, 7854442, "AppleSkin", "mc-mods/appleskin"),
 (60089, 5637846, "Mouse Tweaks", "mc-mods/mouse-tweaks"),
 (250398, 6368976, "Controlling", "mc-mods/controlling"),
 (858542, 5831692, "Searchables", "mc-mods/searchables"),
 (240633, 7188660, "Inventory Sorter", "mc-mods/inventory-sorter"),
 (256717, 5623731, "Clumps", "mc-mods/clumps"),
 (240630, 6506298, "Just Enough Resources (JER)", "mc-mods/just-enough-resources-jer"),
 # adventure
 (245755, 8777999, "Waystones", "mc-mods/waystones"),
 (531761, 8645517, "Balm", "mc-mods/balm"),
 (252848, 7892954, "Nature's Compass", "mc-mods/natures-compass"),
 (422301, 8687841, "Sophisticated Backpacks", "mc-mods/sophisticated-backpacks"),
 (619320, 8687896, "Sophisticated Storage", "mc-mods/sophisticated-storage"),
 (618298, 8815718, "Sophisticated Core", "mc-mods/sophisticated-core"),
 (398521, 8765184, "Farmer's Delight", "mc-mods/farmers-delight"),
 (276951, 7515858, "Comforts", "mc-mods/comforts"),
 (309927, 6529130, "Curios API", "mc-mods/curios"),
 (312353, 8791899, "Artifacts", "mc-mods/artifacts"),
 (412082, 8802603, "Supplementaries", "mc-mods/supplementaries"),
 (499980, 8802498, "Moonlight Lib", "mc-mods/selene"),
 (639842, 8430183, "Better Combat", "mc-mods/better-combat-by-daedelus"),
 (658587, 7389814, "playerAnimator", "mc-mods/playeranimator"),
 (348521, 5729127, "Cloth Config API", "mc-mods/cloth-config"),
 (316582, 7018307, "Corpse", "mc-mods/corpse"),
 (250498, 7760267, "Mowzie's Mobs", "mc-mods/mowzies-mobs"),
 (388172, 8350073, "GeckoLib", "mc-mods/geckolib"),
 (442508, 7150870, "When Dungeons Arise", "mc-mods/when-dungeons-arise"),
 (626761, 8657120, "Towns and Towers", "mc-mods/towns-and-towers"),
 (1015112, 5954804, "YUNG's Better Dungeons", "mc-mods/yungs-better-dungeons-neoforge"),
 (1015105, 6272264, "YUNG's Better Strongholds", "mc-mods/yungs-better-strongholds-neoforge"),
 (1015096, 5812193, "YUNG's Better Mineshafts", "mc-mods/yungs-better-mineshafts-neoforge"),
 (1015100, 8702150, "YUNG's API", "mc-mods/yungs-api-neoforge"),
]
assert len({m[0] for m in MODS}) == len(MODS), "duplicate project"
if TWW_FILE == 0:
    print("WARNING: The Waking World file id is 0 - pass it as the second argument once the file is approved", file=sys.stderr)
manifest = {
  "minecraft": {"version": "1.21.1", "modLoaders": [{"id": "neoforge-21.1.249", "primary": True}]},
  "manifestType": "minecraftModpack", "manifestVersion": 1,
  "name": "Lovkar's Waking World Ultimate", "version": VERSION, "author": "Lovkar",
  "files": [{"projectID": p, "fileID": f, "required": True} for p, f, _, _ in MODS],
  "overrides": "overrides",
}
rows = "\n".join(f'<li><a href="https://www.curseforge.com/minecraft/{slug}">{name}</a></li>' for _, _, name, slug in sorted(MODS, key=lambda m: m[2].lower()))
modlist = f"<ul>\n{rows}\n</ul>\n"
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, f"lovkars-waking-world-ultimate-{VERSION}.zip")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("manifest.json", json.dumps(manifest, indent=2))
    z.writestr("modlist.html", modlist)
    z.write(os.path.join(here, "iris.properties"), "overrides/config/iris.properties")
    z.write(os.path.join(here, "ComplementaryReimagined_r5.9.zip.txt"), "overrides/shaderpacks/ComplementaryReimagined_r5.9.zip.txt")
    z.write(os.path.join(here, "ComplementaryReimagined_r5.9 + EuphoriaPatches_1.10.0.txt"), "overrides/shaderpacks/ComplementaryReimagined_r5.9 + EuphoriaPatches_1.10.0.txt")
print(f"built {out} ({os.path.getsize(out)} bytes, {len(MODS)} projects)")
