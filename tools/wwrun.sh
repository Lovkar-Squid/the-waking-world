#!/bin/sh
# Fresh NORMAL world with only The Waking World: the kingdom rig. Usage: wwrun.sh [keep]
cd /root/nfserver || exit 1
if [ -f server.pid ]; then kill "$(cat server.pid)" 2>/dev/null; sleep 6; fi
pkill -x java 2>/dev/null; sleep 1
mkdir -p mods-ww-aside
for j in mods/*.jar; do case "$j" in mods/wakingworld-*) ;; *) mv "$j" mods-ww-aside/ 2>/dev/null ;; esac; done
rm -f mods/wakingworld-*.jar
newest=$(ls -1t /root/wakingworld/wakingworld-*.jar | head -1)
cp "$newest" mods/
[ "$1" = "keep" ] || rm -rf wwworld
rm -f logs/latest.log
sed -i 's/^level-name=.*/level-name=wwworld/; s/^level-type=.*/level-type=minecraft:normal/; s/^level-seed=.*/level-seed=8127364/; s/^difficulty=.*/difficulty=peaceful/; s/^view-distance=.*/view-distance=6/; s/^simulation-distance=.*/simulation-distance=6/' server.properties
rm -f cmd.fifo; mkfifo cmd.fifo
(setsid sh -c 'tail -f cmd.fifo | java -Xmx4G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui > server.out 2>&1' < /dev/null > /dev/null 2>&1 &)
sleep 3; pgrep -x java > server.pid
for i in $(seq 1 80); do sleep 5; grep -q "Done (" logs/latest.log 2>/dev/null && break; grep -q "crash\|Exception in thread \"main\"" server.out 2>/dev/null && break; done
printf 'gamerule doDaylightCycle false\ngamerule doWeatherCycle false\nweather clear\ngamerule doMobSpawning false\ntime set 6000\n' > cmd.fifo
echo "server up: $(grep -c 'Done (' logs/latest.log) mods: $(ls mods)"
