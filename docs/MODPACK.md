# Lovkar's Waking World Ultimate

The recommended modpack for The Waking World, on CurseForge:
https://www.curseforge.com/minecraft/modpacks/lovkars-waking-world-ultimate

NeoForge 21.1.249 for Minecraft 1.21.1. The Waking World plus the mods that make a long hunt for the
giants comfortable and beautiful - nothing that overlaps the mod's own systems, and no biome overhaul (the
shrines, ruins and kingdoms live in the vanilla biomes).

## What is in it

**The Waking World** - the colossi, the rites, the letters, the kingdoms, the Titan.

**Shaders, set up as in the trailer.** Complementary Reimagined r5.9 and Complementary Unbound r5.9 with
Euphoria Patches 1.10.0; Iris and Sodium; the pack ships our shader settings (coloured lighting 512,
high shadows and clouds, generated normals, interactive foliage, the End nebula), Complementary
Reimagined is selected on the first start and Euphoria patches it. Distant Horizons for the far hills,
Sound Physics Remastered for the echo in the vaults, Falling Leaves and Not Enough Animations for the
small things.

**Performance.** Sodium, Lithium, Entity Culling, ImmediatelyFast, ModernFix, FerriteCore.

**Quality of life.** JEI (+ Just Enough Resources), Jade, JourneyMap, AppleSkin, Mouse Tweaks,
Controlling + Searchables, Inventory Sorter, Clumps.

**The road.** Waystones (+ Balm) for a world where a letter can point a thousand paces away, Nature's
Compass to find a giant's biome, Sophisticated Backpacks and Storage (+ Core) for the loot, Farmer's
Delight and Comforts for camp, Corpse so a bad fight does not cost the run, Artifacts (+ Curios) and
Supplementaries (+ Moonlight Lib) for things to find on the way.

**The fight.** Better Combat (+ playerAnimator, Cloth Config) for the melee, Mowzie's Mobs (+ GeckoLib)
for other things worth hunting, When Dungeons Arise, Towns and Towers and YUNG's Better Dungeons,
Strongholds and Mineshafts (+ YUNG's API) so the world between the shrines is worth crossing.

## Gemini

The pack does not carry a Gemini key - it is yours to make, free, in five minutes: see the mod's README
(*Gemini letters and voices*). Without it the letters are the built-in ones and silent; with it they are
written about your world and read aloud.

## Building the pack

`tools/modpack/build_pack.py <version> <wakingworld-file-id>` writes `lovkars-waking-world-ultimate-<version>.zip`
from the project/file list in the script (the mod's own CurseForge file id is the second argument), with
the Iris config and the two shader settings files as overrides. Every referenced file must be approved
on CurseForge before the pack is uploaded, or the pack is rejected.
