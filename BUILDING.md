# Building The Waking World

Plain `javac`, no Gradle - NeoForge 1.21.1 mods run on official (Mojang) names, so there is no
mappings step. `./build.sh` compiles `src/` against the jars in `libs/`, `clibs/` and `tlibs/` (none of
them in the repo) and packs `wakingworld-<version>.jar` from the classes plus `resources/`.

- `libs/`: the Minecraft 1.21.1 client jar (`mc-client.jar`), `neoforge-21.1.x-universal.jar`,
  `neoforge-21.1.x-client.jar`, `gson.jar`, `slf4j-api.jar`, `annotations.jar` (JetBrains), the NeoForge
  event bus (`bus-*.jar`) and loader (`loader-*.jar`) jars, and NightConfig (`core-*.jar`, `toml-*.jar`,
  from `libraries/com/electronwill/night-config/`) for the config check - all out of a NeoForge 21.1
  installation's `libraries/` folder.
- `clibs/`: guava, fastutil, commons-lang3, datafixerupper, joml, brigadier.
- `tlibs/`: vanilla's own runtime libraries (netty, log4j, commons-io, authlib, ...) from Maven Central and
  libraries.minecraft.net; `netty-buffer` and `netty-common` are on the compile classpath for the network
  payloads, the rest is only for the headless check.
- `stubs/`: tiny stand-ins for `net.neoforged.api.distmarker.Dist`/`OnlyIn` (the real classes ship with
  the loader at runtime, but not in these jars) and, for the private nested types NeoForge opens up with
  an access transformer (`ParticleEngine$SpriteParticleRegistration`, `BlockEntityType$BlockEntitySupplier`),
  **patched copies of the real class files** made by `tools/java/open_nested.py`:

      python3 tools/java/open_nested.py libs/mc-client.jar stubs \
          'net/minecraft/client/particle/ParticleEngine$SpriteParticleRegistration' \
          'net/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier'

  A stub holding only the nested class does not work: javac reads the access flags from the OUTER class's
  InnerClasses attribute, so the outer class file is what has to be patched. `stubs/` goes first on the
  classpath.

JDK 21. The version lives in `resources/META-INF/neoforge.mods.toml` (and the startup log line in
`WakingWorld.java`). `NOTEST=1 ./build.sh` skips the headless check.

`javac -g`: the classes carry their local-variable tables since 0.3.0-alpha.12. That costs a few percent
of jar size and buys a decompile that keeps every variable name - which is what a lost source tree is
recovered from (see `docs/RECOVERY-0.3.md`). Do not take it out.

## Headless checks

`./test.sh` (run by `build.sh` when `tlibs/` exists) boots the vanilla registries without a game and builds
every preset body with real block states - it catches static-initialisation order bugs, palette wire-format
regressions and shape errors in a few seconds and prints `OK`.

It then builds the config spec for real (`tools/java/ConfigCheck.java`) and prints every setting's path in
both files. That is where an unbalanced `push`/`pop`, a duplicate key or a default outside its own range
shows up - none of which say anything until a world is loaded - and it asserts that every part of the mod
is under `[features]` and lives in exactly one place. It wants NightConfig and the loader jar in `libs/`
and says so and skips itself when they are missing.

## Tools

`tools/java/HousePreview.java` draws every suburb design on flat ground and writes the courses as JSON;
`tools/plan_iso.py` renders that (or any such JSON) with stairs, slabs, fences, panes, doors and
shutters as the shapes they are, one PNG per design and a contact sheet; `tools/world_iso.py` renders
a box of a real world the same way out of its region files (`MC_REGION=.../region`). `tools/recover/`
holds the scripts that rebuilt the 0.3 source from a jar (`docs/RECOVERY-0.3.md`).

`tools/textures/*.py` paint every texture in the mod (items, mob skins, garb, blocks, the gate, the GUI
sheets) with Pillow and numpy - edit the script, not the PNG. `tools/sfx/*.py` synthesize the sounds
(ffmpeg encodes the OGGs). `tools/music/` processes the battle themes. `tools/java/GridDump.java` writes a
body's voxel grid as JSON for the Blender preview (`tools/colossus_blender.py`); `isodump.py`,
`structcut.py`, `wallcheck.py` render and check structures dumped with `/wakingworld dump`.

## Getting a jar onto the dev machine when it is over 20 MB

The file bridge Claude uses to write into the connected folders caps a single file at 20 MiB, and
the jar is close to that - almost entirely because of the music (17 MB of the 19 MB compressed, for
19.6 minutes of it at 128 kbps, which is a fair price rather than bloat). The cap belongs to the
transfer, not to the mod: players get the jar from CurseForge, where 20 MB is nothing.

So do not shrink the mod to fit the pipe. Split the jar instead, and put it back together on the
other side:

    split -b 9000000 -d -a 1 wakingworld-x.y.z.jar parts/ww.part

then, on Windows, join them in order and check the hash matches the one from `sha256sum`:

    $fs = [System.IO.File]::Create($out)
    Get-ChildItem $p -Filter "ww.part*" | Sort-Object Name | ForEach-Object {
      $in = [System.IO.File]::OpenRead($_.FullName); $in.CopyTo($fs); $in.Close() }
    $fs.Close()
    (Get-FileHash $out -Algorithm SHA256).Hash

Streaming the parts through `CopyTo` matters: piping them through PowerShell as text mangles the
bytes. Verified byte-identical on 0.2.0-alpha.2 (20,008,912 bytes, SHA256 C4082F52...DDCF9D5).
