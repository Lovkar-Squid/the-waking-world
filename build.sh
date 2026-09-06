#!/usr/bin/env bash
# Build The Waking World with plain javac (NeoForge 1.21.1 runs on official names - no mappings step).
# Needs JDK 21 and libs/ (Minecraft client jar, NeoForge universal + client jars, gson, slf4j,
# annotations) plus clibs/ (guava, fastutil, commons-lang3, datafixerupper, joml) - not in the repo.
set -euo pipefail
cd "$(dirname "$0")"
VERSION=$(sed -n 's/^version="\(.*\)"/\1/p' resources/META-INF/neoforge.mods.toml | head -1)
OUT="wakingworld-${VERSION}.jar"
CP="stubs:$(ls libs/mc-client.jar libs/neoforge-*-universal.jar libs/neoforge-*-client.jar libs/gson.jar libs/slf4j-api.jar libs/annotations.jar libs/bus-*.jar libs/loader-*.jar clibs/*.jar tlibs/netty-buffer-*.jar tlibs/netty-common-*.jar tlibs/authlib-*.jar tlibs/lwjgl-3*.jar tlibs/lwjgl-openal-*.jar | tr '\n' ':')"
rm -rf build2 && mkdir -p build2
javac -encoding UTF-8 --release 21 -proc:none -Xlint:-options -nowarn -cp "$CP" -d build2 $(find src -name '*.java')
rm -rf build && mv build2 build
rm -f "$OUT"
jar cf "$OUT" -C build . -C resources .
echo "built $OUT ($(stat -c %s "$OUT") bytes)"
if [ -z "${NOTEST:-}" ] && [ -d tlibs ]; then ./test.sh; fi
