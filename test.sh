#!/usr/bin/env bash
# Headless checks: boots the vanilla registries (no game) and builds every preset body with real
# BlockStates - catches static-initialisation, palette and shape bugs in ~7 s. Needs tlibs/ (vanilla's
# runtime libraries: netty, log4j, authlib, ...) next to libs/ and clibs/.
set -euo pipefail
cd "$(dirname "$0")"
CP="build:libs/mc-client.jar:libs/mc-extra.jar:libs/gson.jar:libs/slf4j-api.jar:libs/annotations.jar:$(ls clibs/*.jar tlibs/*.jar | tr '\n' ':')"
mkdir -p tools/out
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d tools/out $(ls tools/java/*.java | grep -v ConfigCheck.java)
java -cp "tools/out:$CP" HeadlessCheck 2>&1 | grep -v JAVA_TOOL_OPTIONS | sed 's/^\[[^]]*\] \[main\/INFO\]: \[STDOUT\]: //'

# The config spec, built for real: catches an unbalanced push/pop, a duplicate key or a default
# outside its range - none of which show up until a world is loaded. Wants NightConfig and the FML
# loader besides the usual libraries; skipped, loudly, when they are not there.
CFGCP="$CP:$(ls libs/neoforge-*-universal.jar libs/loader-*.jar libs/bus-*.jar libs/core-*.jar libs/toml-*.jar 2>/dev/null | tr '\n' ':')"
if ls libs/core-*.jar libs/toml-*.jar libs/loader-*.jar >/dev/null 2>&1; then
  javac -encoding UTF-8 --release 21 -nowarn -cp "$CFGCP" -d tools/out tools/java/ConfigCheck.java
  java -cp "tools/out:$CFGCP" ConfigCheck 2>&1 | grep -v 'JAVA_TOOL_OPTIONS\|StatusConsoleListener' | tail -3
else
  echo "config: skipped (libs/core-*.jar, libs/toml-*.jar and libs/loader-*.jar wanted - see BUILDING.md)"
fi
