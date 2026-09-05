#!/usr/bin/env bash
# Headless checks: boots the vanilla registries (no game) and builds every preset body with real
# BlockStates - catches static-initialisation, palette and shape bugs in ~7 s. Needs tlibs/ (vanilla's
# runtime libraries: netty, log4j, authlib, ...) next to libs/ and clibs/.
set -euo pipefail
cd "$(dirname "$0")"
CP="build:libs/mc-client.jar:libs/mc-extra.jar:libs/gson.jar:libs/slf4j-api.jar:libs/annotations.jar:$(ls clibs/*.jar tlibs/*.jar | tr '\n' ':')"
mkdir -p tools/out
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d tools/out tools/java/*.java
java -cp "tools/out:$CP" HeadlessCheck 2>&1 | grep -v JAVA_TOOL_OPTIONS | sed 's/^\[[^]]*\] \[main\/INFO\]: \[STDOUT\]: //'
