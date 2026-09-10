#!/bin/bash
# javap -c -p of one class, with the parts that vary between compiles stripped:
# line-number tables, "Compiled from", and constant-pool indices in the instruction comments.
javap -c -p -cp "$1" "$2" 2>/dev/null | grep -v "Picked up\|Compiled from\|^  *line [0-9]*: [0-9]*\|LineNumberTable" | sed -E 's/#[0-9]+//g; s/ +/ /g'
