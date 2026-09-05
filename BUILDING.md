# Building The Waking World

Plain `javac`, no Gradle - NeoForge 1.21.1 mods run on official (Mojang) names, so there is no
mappings step. `./build.sh` compiles `src/` against the jars in `libs/`, `clibs/` and `tlibs/` (none of
them in the repo) and packs `wakingworld-<version>.jar` from the classes plus `resources/`.

- `libs/`: the Minecraft 1.21.1 client jar (`mc-client.jar`), `neoforge-21.1.x-universal.jar`,
  `neoforge-21.1.x-client.jar`, `gson.jar`, `slf4j-api.jar`, `annotations.jar` (JetBrains), the NeoForge
  event bus (`bus-*.jar`) and loader (`loader-*.jar`) jars - all out of a NeoForge 21.1 installation's
  `libraries/` folder.
- `clibs/`: guava, fastutil, commons-lang3, datafixerupper, joml, brigadier.
- `tlibs/`: vanilla's own runtime libraries (netty, log4j, commons-io, authlib, ...) from Maven Central and
  libraries.minecraft.net; `netty-buffer` and `netty-common` are on the compile classpath for the network
  payloads, the rest is only for the headless check.
- `stubs/`: tiny stand-ins for `net.neoforged.api.distmarker.Dist`/`OnlyIn` (the real classes ship with
  the loader at runtime, but not in these jars) and a few private nested types NeoForge opens up.

JDK 21. The version lives in `resources/META-INF/neoforge.mods.toml` (and the startup log line in
`WakingWorld.java`). `NOTEST=1 ./build.sh` skips the headless check.

## Headless checks

`./test.sh` (run by `build.sh` when `tlibs/` exists) boots the vanilla registries without a game and builds
every preset body with real block states - it catches static-initialisation order bugs, palette wire-format
regressions and shape errors in a few seconds and prints `OK`.

## Tools

`tools/textures/*.py` paint every texture in the mod (items, mob skins, garb, blocks, the gate, the GUI
sheets) with Pillow and numpy - edit the script, not the PNG. `tools/sfx/*.py` synthesize the sounds
(ffmpeg encodes the OGGs). `tools/music/` processes the battle themes. `tools/java/GridDump.java` writes a
body's voxel grid as JSON for the Blender preview (`tools/colossus_blender.py`); `isodump.py`,
`structcut.py`, `wallcheck.py` render and check structures dumped with `/wakingworld dump`.
