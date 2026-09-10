#!/bin/bash
# compare every class in a11 against the fresh build, normalised
diffcount=0
for f in $(cd a11 && find me -name "*.class" | sort); do
  c=${f%.class}
  if [ ! -f /root/wakingworld/build/$f ]; then echo "MISSING $c"; diffcount=$((diffcount+1)); continue; fi
  if ! diff <(./norm.sh a11 $c) <(./norm.sh /root/wakingworld/build $c) > /tmp/d.txt; then
    echo "DIFF $c ($(wc -l < /tmp/d.txt) lines)"; diffcount=$((diffcount+1))
  fi
done
for f in $(cd /root/wakingworld/build && find me -name "*.class" | sort); do
  [ -f a11/$f ] || echo "EXTRA ${f%.class}"
done
echo "classes differing: $diffcount"
