# How 0.3.0-alpha.11 got its source back (10 Sep 2026)

The whole 0.3 line - alpha.1 to alpha.11, the dark mage, the kingdom works, the siege, the companion -
was written in a cloud workspace and never committed to this repo ("nothing is pushed until he has
tested"). The workspace was reclaimed. What survived: the PC repo at `01c6f76` (0.2.0 plus the very first
mage commit, ≈ 0.3.0-alpha.1), the installed jar `wakingworld-0.3.0-alpha.11.jar`, `STATUS.md`, and the
memory notes. This branch (`0.3-recovered`) is the source rebuilt from those, and this file is the record
of how - because the method is reusable and the lesson is not to need it.

## The method

1. **Classify every class** by comparing normalised `javap -c -p` output of the repo build against the
   jar (`tools/recover/norm.sh`, `cmpall.sh`): 244 class files identical, 40 changed, 26 new.
   Identical classes keep their original source untouched - most of the mod.
2. **Decompile the jar** with Vineflower 1.10.1 (`--decompile-generics=1`). Without `-g` at compile time
   every local is `var7`; field, method and class names survive, comments and generics on locals do not.
3. **Merge member by member** (`tools/recover/merge.py`): for each changed class, split both the original
   source and the decompiled file into members (`jsplit.py`, brace-aware), compare each member's bytecode
   (`members.py`; lambdas attributed to their enclosing method by name, matched by body; field
   initialisers compared as their own slice of `<clinit>` / the constructors with the lambda bodies
   substituted in, `segments.py`) and keep the ORIGINAL text of every member whose bytecode did not
   change. Only changed or new members take the decompiled text; new members are inserted where the
   class file's own order puts them. Result: 79 replaced methods, 123 added, 2 removed, 16 new classes.
4. **Port the small changes by hand** back into the original text (`mdiff.py` prints the decompiled
   old→new diff of a method, `restore.py` puts the original back): ~45 of the 79 were one to ten lines
   (a `Ruin.mark` here, a listener there) and now read like they were never lost. The big rewrites
   (`MageEntity`, `RiteStoneEntity`, `WakingCommands.register`, the 16 new classes) stay decompiled
   with `varN` names until they are next worked on.
5. **Prove it** (`fingerprint.py`): per method, the multiset of instructions that carry meaning (calls,
   field access, constants, arithmetic, `new`, `instanceof`) must match the jar's, ignoring control-flow
   shape, local slots, casts and `dup/pop`. Every remaining difference is one of: `List.add` vs
   `ArrayList.add` (declared type of a local), `HitResult` vs `BlockHitResult` receiver, an inlined
   `+ 2` the original had in a local, and the one deliberate refactor (`KingdomData.longs`). Strict
   bytecode equality is not reachable from decompiled code and was not the goal. `./test.sh` prints
   `OK` and `config: OK`.

Two things the automation could not see and had to be found by hand: an anonymous class that gained a
method (`MageRenderer$1.render`, `WakingWorldClient$1.openMage`) leaves its enclosing method's bytecode
unchanged - check every `Outer$N` class separately and read its `EnclosingMethod` attribute; and a dead
`if (SupporterList.ENABLED)` block (a compile-time `false`) that javac compiles away but still emits the
lambda for - the decompiler drops the block, the original text keeps it.

## What is still lost

- `docs/PLAN.md` stops at 0.2 (8 Sep). The 0.3 plan lived only in the cloud; `STATUS.md` (9 Sep) and
  the memory notes are what remains of it.
- `tools/stairs_check.py` (the mage-tower stair walker) and whatever else was added under `tools/` in 0.3.
- Comments and variable names inside the wholly rewritten classes.

## The lesson

A cloud tree is not a copy. Commit to the PC repo the same evening, every evening, on a branch nobody
has to look at. And compile with `-g` (done in `build.sh`), so the next recovery keeps its names.
