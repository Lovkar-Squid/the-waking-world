#!/usr/bin/env bash
# start | stop | fresh | send "<cmd>" | wait   - the headless test rig, without the foot-guns.
cd "$(dirname "$0")"
JAR_SRC=/root/wakingworld
ARGS=@libraries/net/neoforged/neoforge/21.1.248/unix_args.txt

pids() { pgrep -x java 2>/dev/null; }

stop() {
  if [ -p in.fifo ] && pids >/dev/null; then echo stop > in.fifo; fi
  for i in $(seq 1 20); do pids >/dev/null || break; sleep 2; done
  for p in $(pids); do kill -9 "$p" 2>/dev/null; done
  for p in $(pgrep -x sleep); do kill "$p" 2>/dev/null; done
  sleep 2
}

start() {
  rm -f in.fifo server.out
  mkfifo in.fifo
  (setsid sleep 100000 > in.fifo 2>/dev/null &)
  sleep 1
  (setsid nohup java -Xmx3G $ARGS nogui < in.fifo > server.out 2>&1 &) < /dev/null
}

ready() {
  for i in $(seq 1 40); do
    grep -q 'Done (' server.out 2>/dev/null && { echo READY; return 0; }
    sleep 5
  done
  echo "NOT READY"; tail -3 server.out; return 1
}

case "$1" in
  stop)  stop ;;
  start) start; ready ;;
  fresh) stop
         rm -f mods/*.jar
         cp "$JAR_SRC"/wakingworld-*.jar mods/ 2>/dev/null
         ls mods/
         [ "$2" = "keep" ] || rm -rf world
         start; ready ;;
  send)  shift; for c in "$@"; do echo "$c" > in.fifo; sleep 0.7; done ;;
  wait)  ready ;;
  *)     echo "usage: nf.sh start|stop|fresh [keep]|send <cmd>...|wait" ;;
esac
