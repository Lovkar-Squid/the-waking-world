# The Waking World — where we are

Written 9 Sep 2026 at **0.3.0-alpha.11**, brought up to date 11 Sep 2026 at **0.3.0-alpha.12**.
This is the handover: what exists, what was fixed and how it was proved, what is untested, and what
is left. **Section 0 is new and comes first because it changes where the source is.**

---

## 0. 10/11 Sep 2026 — the source was lost and recovered; alpha.12 adds the suburb

### The recovery

The cloud workspace that held the whole 0.3 line was reclaimed. **The source is now in the PC repo,
branch `0.3-recovered`** (`C:\Users\marko\curseforge\waking-world\repo`; commit `428d84c` =
alpha.11 recovered, then alpha.12 on top). Not pushed to GitHub. How it was rebuilt from the alpha.11
jar and the last commit is in `docs/RECOVERY-0.3.md`; the scripts are in `tools/recover/`. Short
version: 244 classes were untouched, 40 changed ones were merged member by member (unchanged members
keep the original text, ~45 small changes were ported back by hand), 16 new classes are decompiled
(`MageEntity`, `RiteStoneEntity`, `WakingCommands.register` and the 16 new classes still have `varN`
names). Bytecode fingerprint checked against the jar; `test.sh` OK. **`build.sh` compiles with `-g`
now** so a decompile keeps its names next time. Lost for good: `tools/stairs_check.py`, the 0.3
part of `docs/PLAN.md`, comments in the rewritten classes.

**Rule, restated: commit to the PC repo every evening. A cloud tree is not a copy.**

The cloud tree this session used is `/root/wakingworld` (recreated); the rig is `/root/nfserver`
with `wwrun.sh [keep]` (see below). Both are gone with the container; the repo is what remains.

### alpha.12 — the suburb ("kingdom ko raste res še več hiš okoli")

`kingdom/KingdomHouses.java` (the plots, the order, the lanes, the people) and
`kingdom/HouseBuilder.java` (the designs, drawn once facing south in a local frame and rotated).

- Tier keeps houses: **1 → 0, 2 → 6, 3 → 13, 4 → 22**. A review raises up to **3** of the shortfall
  (`PER_REVIEW`), after the works and the march wall; `/wakingworld kingdom [at] houses [n]` raises
  n at once, `houses forget` clears refused plots.
- **Plots are fixed slots** (48): along the south and north gate roads first, then east and west
  lanes as overflow; a front row with doors 4 blocks off the road, a back lane at 15-16, a back row
  at 19; depths 76 / 87 / 98 from the centre (the last eave stays inside the march wall at 118).
  Slot order is deterministic, so a town seen at tier 2 and tier 4 has grown, not shuffled.
- A plot is refused for the ground (under the sea, water on it, slope over 4 across the house and
  its eaves - written to `badSlots`, never retried), for a work in the way or something built on it
  (retried next review), or for not being loaded. The log says which and why.
- The **n-th house's kind** is fixed: 4 cottages + 2 longhouses (town); tavern, smithy, 2 townhouses,
  longhouse, 2 cottages (walled town); chapel, 4 townhouses, longhouse, 3 cottages (city). A wide
  kind that will not fit beside what stands becomes a cottage.
- **Palettes** (5): oak+lime terracotta+dark oak roof; spruce+mud bricks+deepslate tile; dark
  oak+ash terracotta+spruce roof; stripped oak+birch+brick tile; spruce+lime+dark oak roof.
- Each house: plinth footed to the ground under every column, floor, room cleared, plate of logs,
  gable roof with eaves and gable posts, chimney with a lit campfire, shutters (open trapdoors flat
  against the wall - `FACING` = the wall's outward normal), sills, hanging lanterns under the eaves,
  door awning, pots, doorstep slab, furniture inside. Yard: a levelled garden bed (a channel dug into
  a hillside is a spring - the first one drained down the lane), a tree of persistent leaves, a hedge
  or a cobble yard wall. The road is paved to 116 with lamp posts at 81/92/103; the back lane gets a
  well at its head and a lamp at its end.
- **The works now keep out of the road corridors** (`KingdomExpansion.inLane`: |dx| ≤ 34 along
  either axis). In a world where a work already sits on a lane, the plots behind it are refused
  "a work in the way" and the town grows past it.
- **People**: when the masons finish (`KingdomBuild.begin(..., done)`), 1-2 townsfolk of the trade the
  house suggests spawn at the door, restricted to 10 blocks. Measured on the rig: 20 houses → 24
  townsfolk outside the walls.
- Fire watch sweeps the houses too (`KingdomRepair.sweepWork`); `occupied` keeps works 20 off a door.

*Proved on the rig* (seed 8127364, kingdom **Eldermere** placed at -2486 70 714 - a snowy mountain
site, deliberately unkind): 20 houses raised over four `houses` calls, every kind at least once, every
palette, on slopes up to 4; save/restart kept them; residents spawned; no exception in the log. The
renders are in `promo\bts\` (`suburb_designs.png`, `eldermere_15_houses.png`, `eldermere_north_road.png`,
`eldermere_south_road.png`). **Not proved:** how it looks in the client (roof lines, shutters, the
sign, the bell), the residents' behaviour, a flat-plains town where every lane fills.

### The rig, for kingdoms (new)

- `/root/nfserver/wwrun.sh [keep]` - only the newest `wakingworld-*.jar`, normal world `wwworld`
  seed 8127364, `max-tick-time=-1` (the watchdog killed a 60 s tick), started with `setsid`. Commands
  go in through `cmd.fifo`; output is in `logs/latest.log`.
- **`/locate structure wakingworld:kingdom` hangs the rig for minutes** (the site test is strict and
  the box has two cores). Instead: `/wakingworld kingdomscan 8 64 8 6` now prints
  `/place structure wakingworld:kingdom X Y Z` for every cell that passes - as `/place` will see it,
  chunk middle nudged into its land square. Then **forceload the whole footprint** (`/place` demands
  every chunk of the bounding box loaded, and `forceload add` takes at most 256 chunks per call: four
  150×150 quarters), wait a minute, place. `KingdomSpawns.king` does not set the throne when placed
  by command (no `WorldGenRegion`), but the kingdom is registered.
- Then `kingdom <at> standing 50` reviews, `kingdom build 200000` drains the masons, `save-all flush`,
  and `MC_REGION=/root/nfserver/wwworld/region python3 tools/world_iso.py cx cz y0 y1 hx hz out.png
  scale "title"` draws it.


---

## 1. Where everything lives

| Thing | Path |
|---|---|
| Mod source | **PC repo, branch `0.3-recovered`** (`C:\Users\marko\curseforge\waking-world\repo`); a cloud tree is only ever a working copy |
| Build | `./build.sh` → compiles, runs `test.sh`, prints `OK` + `config: OK`. `NOTEST=1 ./build.sh` skips the tests |
| Version string — **two places** | `resources/META-INF/neoforge.mods.toml` and the LOGGER line in `WakingWorld.java` |
| Headless test server | `/root/nfserver`, driven by `nf.sh start\|stop\|fresh [keep]\|send "<cmd>"\|wait`. Live output in `server.out`. `fresh` wipes the world unless `keep` |
| Save readers | `tools/region_read.py` — `block_grid(...)`, `state_grid(...)` (keeps block Properties). `tools/region_iso.py` — `render(cx, cz, cy, half, ydown, yup, out, title, sub, disc)`. Both need `MC_REGION=/root/nfserver/world/region` |
| Tower walker | `tools/stairs_check.py` — **lost with the workspace**; rewrite from the memory note if the stairs need proving again |
| Item / GUI textures | `tools/textures/items32.py`, `tools/textures/gui.py` |
| His game instance | `C:\Users\marko\curseforge\minecraft\Instances\The Waking World Dev\mods` (note the `minecraft\` level) |
| Behind-the-scenes images | `C:\Users\marko\curseforge\waking-world\promo\bts\` |
| Boss music sources | `C:\Users\marko\curseforge\waking-world\promo\music\` |

### Delivery — the jar is 29 MB and the bridge caps a file at 20 MB

```
sha256sum wakingworld-0.3.0-alpha.N.jar
split -b 14000000 -d wakingworld-0.3.0-alpha.N.jar /tmp/xfer/ww.part
# SendUserFile each part → device_commit_files to C:\Users\marko\curseforge\_transfer\
# then on his PC, PowerShell:
#   [IO.File]::Create(out); foreach part { ReadAllBytes; fs.Write }
#   Get-FileHash -Algorithm SHA256   → must match
#   move the previous jar to mods\_old ; Remove-Item _transfer -Recurse
```

**Never swap a jar while the game is running** — `Get-Process javaw` must return 0.
**Never ship two different jars under one version number** — bump instead.

### Dev commands (console-safe, no player needed)

- `/wakingworld kingdom [at]` — report; `... standing <n>` reviews the tier on the spot
- `/wakingworld kingdom [at] repair` — one sweep, reports blocks and fires
- `/wakingworld kingdom build [blocks]` — drains the mason queue (headless has no watcher)
- `/wakingworld kingdom <at> engine` — **new**, raises one catapult there and registers it
- `/wakingworld bombard <at>` — **new**, calls a volley on a point with no player
- `/wakingworld terrain`, `tidy [r]`, `kingdomscan <at> <cells>`

### Measuring movement headlessly (the trick that caught the guard bug)

A datapack in `world/datapacks/wwtest/` with `data/wwtest/function/trace.mcfunction`:

```
execute as @e[tag=tg,limit=1] store result score #cur x run data get entity @s Pos[0] 100
execute unless score #cur x = #prev x run scoreboard players add #moved x 1
execute if score #cur x > #max x run scoreboard players operation #max x = #cur x
execute if score #cur x < #min x run scoreboard players operation #min x = #cur x
scoreboard players add #ticks x 1
scoreboard players operation #prev x = #cur x
schedule function wwtest:trace 1t
```

`data get` output inside a function is **not** echoed to the console — that is why it goes
into a scoreboard and is read afterwards with `scoreboard players get`.

**Two traps that made a measurement lie, both mine:**
- a target in a glass cage is **unreachable**, so `MeleeAttackGoal.canUse` fails on `createPath`
  and the test measures the cage, not the bug;
- a target summoned `Invulnerable:1b` is invisible to targeting entirely
  (`canBeSeenAsEnemy()` = `!isInvulnerable() && ...`), so the mob never has a target at all.
  Use a normal mob and raise its health with `/attribute ... base set 4000` plus
  `data merge ... {Health:4000f}` — the `Attributes` NBT on `/summon` did not take.

---

## 2. Standing rules

- Chat with him in **Slovenian**; the mod and anything public is **English**.
- Public identity is **Lovkar** only. Never his real first name, never `Markoman44`, never `Markoman444`.
- **Never** use or store his sudo password.
- Secrets live only in `.env` files (`lovkar-bot`, `modpack-build`, `~/lovkar-bot` on the server).
  Never echo a token into chat or memory.
- **Never open `~/lovkar-bot/docker-compose.yml`** — CasaOS inlined the bot token, the client
  secret, `DASH_SECRET` and three Gemini keys into it.
- Never touch Patreon payout / tax / payment settings.
- Do not delete Discord channels, messages or files — rename, hide or move and let him delete.
- **Nothing is pushed to the mod repo until he has tested.**
- Save BTS / sneak-peek images **as the work happens**, not at the end.

---

## 3. Version history of this line

| Build | What it carried |
|---|---|
| alpha.1–alpha.2 | mage tower stair fix, `MageScreen`, rite explained |
| alpha.4 | Asking Stone on an empty hand, mage name box, dais steps, king's bed, `KingdomRepair`, `KingdomExpansion` |
| alpha.5–alpha.6 | **`Ruin.mark` feeds any open record** (the crater bug), `groundY` through leaves, aimed rites, floating offerings, tower rings, king's charge, the four-stage boss, `KingdomBuild`, march wall, five works, catapults, fire watch, Pocket Mage |
| alpha.7 | the four boss-music tracks, looped and normalised to −17 LUFS |
| alpha.8 | mage forms per stage (coming apart, not growing), ward stones, Mage's Mirror, rune sentinels in the fight |
| alpha.9 | whole mage fight as one scar, the jar made findable (glow, no despawn, rune column) — **built but never installed**, superseded |
| alpha.10 | companion orders and stances, guard chase fix, catapult rebuild, siege damage, lava patrol, Signal Horn *(shipped, then superseded within the hour)* |
| alpha.11 | SHA256 `29d5293e5c4641596a40e3bebc66524df6d0dc5f57758d1f2ab31ad15945a4da`, 29 041 476 B. The jar the source was recovered from; in `mods\_old` |
| **alpha.12** | **current** — the suburb (section 0). In `The Waking World Dev\mods` |

---

## 4. What alpha.10 / alpha.11 changed, and how each was proved

### 4.1 Companion — more orders, and he fights other hostile mobs

`MageEntity` gained a synced **stance** (`DATA_STANCE`) alongside the existing order:

- `MEEK` — starts nothing at all;
- `DEFEND` — what hit you, what you hit, what is hitting him (the old behaviour);
- `GUARD` — plus a sweep for anything hostile within **17 blocks of you or of him**, with
  line of sight required, scored by distance **to the owner** rather than to him.
  A newly placed companion starts in GUARD.

Never targeted: bosses (colossus, wither), villagers, golems, tamed animals, townsfolk,
guards, the king. The splash filter (`helpable`) matters more than the target filter —
he throws at a zombie in a village square and everything within 3.2 blocks catches.

New deeds, each on its own timer: **Mend** (8 HP + regeneration + absorption, clears poison,
wither and fire, 45 s), **Light** (night vision 3 min to everyone within 20 blocks, 20 s),
**Gather** (every loose item within 14 blocks flies to you, 6 s — he does not *carry*, he
*reaches*, which keeps his "I will not carry anything" line true).

The window is now **two rows of five** 46 px buttons (the same halved-sprite trick as
`KingScreen`): follow / hold / range / **stance** / gather, then mend / light / talk / jar /
farewell. The stance button is a dial — it shows where it stands and one click turns it one
notch, with a client-side copy of the value so three fast clicks walk three notches.
"Talk" merges his Terms and his account of himself into one paged document.

**A real bug found on the way:** `castCooldown` was decremented only inside the ten-tick beat
*and* only while a foe was already standing there, so a companion's "55" meant **27–45 seconds
between bolts**. Now decremented every tick: 2.2–3.5 s.

Protocol: `WakingNet.MageOrder` — 0–2 movement, 3–5 stance, 6 mend, 7 lamp, 8 haul, 9 jar.
Routing lives in `MageEntity.commanded(ServerPlayer, int)`.

*Proved:* stance round-trips through NBT and survives a server restart (wrote 2, read 2);
a fresh mage defaults to DEFEND (1); a kept mage with no owner ticked for a minute with no
exception. The window itself is client-only and untested.

### 4.2 Guards stuttered — measured, then fixed

Vanilla `MeleeAttackGoal.canContinueToUse()` ends the goal the moment the target steps outside
the mob's **restriction** — and a guard's restriction is the 4–7 blocks of his post. The goal
started, called `navigation.moveTo`, was cancelled on the next tick, and `stop()` called
`navigation.stop()`; `canUse` then has a **20-tick throttle** before it will look again.

*Measured before:* a knight at 740,91,740 with a 7-block post and a zombie 10 blocks away —
**X unchanged for all 655 ticks**. Not a stutter at that range: total paralysis.

Fix: `GuardEntity.PostAttackGoal` overrides `canContinueToUse` to use a leash measured from the
**post** (`CHASE = 12` beyond the post radius) instead of the restriction. The 40-tick
target-dropping rule in `customServerAiStep` now uses the same `canChase`, so the two agree.

*Measured after:* the knight stands at X 749.59 — 9.6 blocks outside its post — and took the
zombie from 4000 to 764 HP in 33 s. Leash still holds: a target 25 blocks from the post gets
no chase at all (X stayed at 737.8).

### 4.3 The catapult looked wrong

The old arm was a **stack of oak fences laid diagonally**. A fence is a 4 px post that only
joins along the axes, so a diagonal run of them is five separate specks with a cauldron
hanging in the sky at the end. Read out of the save, the bed was also floating three blocks
above the hardstanding on two axle-trees, with a clear course of air the whole length of it.

Rebuilt as a **trebuchet**: dark-oak deck on a chassis with side rails, four wheels made as
crosses of end-grain log (the one shape that reads as spokes at this scale), two A-frame
cheeks with a tie, an axle across their heads, a long arm of **full logs doubled at every
step** running down and back with the shot on a chain, the counterweight hanging in the open
over the front, a windlass, a pile of shot, a brazier and the town's colours. And it is built
in local axes then rotated, so **it points out of the country** instead of always due north.

`Tidy` now runs round every engine — the first test one grew invisible under a birch canopy.

*Proved:* the elevation read back out of the save matches the design block for block;
iso renders before/after are in `promo\bts\`. The final look is his call in game.

### 4.4 The bombardment barely hurt the giant — two causes

1. The shot was a plain size-1 meteor, and its blast is scored from the giant's **feet**,
   where the entity sits. A forty-block mountain was measured as a mob standing on a point
   and run through the same distance falloff.
2. `LivingEntity.hurt` throws away any blow landing inside the **10-tick mercy window** that
   is not larger than the last one. Stones leave 11 ticks apart but their flight time varies
   by about a second, so most of a volley was swallowed by the rule that stops a zombie
   hitting you twice.

Now: `MeteorEntity.aimedAt(giantId, blow)` and `ColossusEntity.siegeStruck(...)`, which finds
the nearest part **with an unbroken core**, clears `invulnerableTime`, and hits it through
`hurtPart`. The volley's whole worth is stated once in `KingdomSiege.VOLLEY_CORES = 1.5F` and
divided by however many stones are thrown, so one engine and four engines deliver the same
favour at different speeds. Scatter tightened 4.0 → 2.5, `GAP` 11 → 10, `PER_ENGINE` 3 → 4,
`MOST` 12 → 14.

*Measured on a real colossus:* 600 → **407.85 HP (−32 %)** and **one core broken**, from
14 stones. The arithmetic checks out exactly (14 × 5.14 raw × 1.5 core factor = 108, plus the
broken core's 60, plus ~24 of meteor blast = 192).

### 4.5 The mage did not fold unless a player landed the last blow

The condition required `source.getEntity() instanceof ServerPlayer`, so drowning, fire, a fall
or his own meteor simply killed him: no jar, no decision, the vanilla death animation, and the
boss bar left hanging at the top of the screen with nothing behind it.

Now he folds on **any** death except `/kill` and the void. The jar goes to whoever struck him
if that was a player, otherwise to the nearest player within 96 blocks, otherwise onto the
floor (where it already glows, never despawns and is marked with a rune column). `remove()`
takes the bar down whatever removes him. He is also immune to drowning, suffocation, cramming
and falling, and his air supply is topped up so no bubble bar appears.

*Proved:* `/damage 400 minecraft:drown` left him at 320/320; `/damage 400 minecraft:on_fire`
on a roused mage put him at 1.0 HP (which is what `fold` sets) and 3.5 s later a **Pocket Mage
"Mordraine who Reads the Weather"** was lying on the ground.

### 4.6 The Hourglass refused after a mage fight

The fight's scar opens on the first blow and used to close **only** on fold or death. An
unfinished record is exactly what the Hourglass answers "it has not finished yet" to, for
anything in reach. Now `remove(RemovalReason)` seals it, and `watchAlone` seals it after 30 s
with no player within 100 blocks; `ensureScar` opens a fresh one if somebody comes back and
hits him.

### 4.7 Rune sentinels shot their own summoner

His spells landed on them and `HurtByTargetGoal` did the obvious thing. Three fixes, belt and
braces: `hostileTo` excludes them from every spell, `setTarget` refuses a `MageEntity`,
`isAlliedTo` covers him and each other, and damage from a `MageEntity` is refused outright.

### 4.8 The mage only teleported

He had **no movement goals at all**. Added `FloatGoal` and a slow `WaterAvoidingRandomStrollGoal`
(interval 90). A quiet WILD mage is leashed to 7 blocks around wherever he is standing when he
first ticks — otherwise the stroll goal walks a boss down his own stairs and out across the
moor and the player arrives at an empty tower. The leash is cleared on `rouse()` and on `keep()`.

In the fight he now walks to hold his range: closes above 17 blocks, gives ground below 8,
and drifts round his man otherwise (`stride` / `standOff`); the blink is kept for under 5 or
over 26. As a companion he **paths** after you and only blinks when there is no way to walk it.

*Proved:* a quiet mage moved from −557.83 to −555.90 over 12 s on foot, no teleport sound.

### 4.9 The kingdom clears lava

The fire watch only ran while a scar was being rebuilt or within 40 s of the last flame — a
volcano's flow breaks nothing the ledger knows about, so nobody ever looked. Added a slow
patrol (one slab per second when nothing is wrong, whole town in ~16 s) plus a round-robin
sweep of one outlying work per beat (`WORK_REACH = 8`). `DOWN` raised 6 → 12.

**Not verified** — the patrol deliberately only runs with a player within 160 blocks, and the
rig has no player.

### 4.10 New item — the Signal Horn

`wakingworld:signal_horn`. Blow it and the nearest kingdom with engines throws its shot at
whatever you are looking at, up to 160 blocks, giant or not. Cooldown
`KingdomSiege.HORN_COOLDOWN` = **5 minutes**; a horn nobody answers costs only 2 s.
The town must have built catapults, be within 2600 blocks, and not be angry with you.
Craft: iron, copper, iron across the top, goat horn in the middle, iron below.

The Horn of Waking is unchanged: once per giant, free, colossus only.

`KingdomSiege.callAt(level, caller, target)` is the generic entry point; `callFor` (the
colossus case) delegates to the same `throwFrom`.

---

## 5. What is untested

Everything below needs his client. The mage **cannot be hurt by a non-player by design**, so
the boss fight is not testable headlessly at all.

1. **The whole of alpha.11 in play** — nothing since alpha.8 has had a real play-test.
2. The companion window: the 5-per-row layout, whether any label clips at 42 px, whether the
   stance dial reads right.
3. Mend / Light / Gather in practice, and whether GUARD makes him too strong.
4. The catapult's actual look in game, and whether it points sensibly on a slope.
5. The Signal Horn end to end, and whether crafting is the right way to get it — it might be
   better as something the king hands over when the town first builds an engine.
6. The lava patrol.
7. The mage walking: that he does not get stuck, does not leave the tower, and that the
   in-fight pacing does not look drunk.
8. That the sentinels really stop shooting him.
9. That guards no longer stutter in a real town.
10. The four boss-music tracks in play.

---

## 6. Still to do

**Mod**
- Play-test alpha.12 and report - the suburb first (`/wakingworld kingdom houses 6` on a town to see it at once), then everything in section 5.
- Decide where the Signal Horn comes from (craft vs. a king's gift).
- The source is on the PC repo branch `0.3-recovered`; nothing is pushed until he has tested.
- Suburb ideas not built: houses abandoned when a town shrinks, a cottager profession with its own
  garb, market stalls along the road, the side lanes joined to the gate roads by a ring lane.
- Written down in `docs/IDEAS.md` and not built: relationships between kingdoms, roads,
  structure variants per tier, the cataclysm brake on expansion.

**Modpack**
- **Two instances since 9 Sep, and the folder name is not the display name.**
  Build from the folder `MineColonies Ultimate` (its instance name is now
  *"MineColonies Ultimate Dev"*, projectID 0). The folder `Lovkar's MineColonies Ultimate`
  (projectID 1680254) is the downloaded published pack he actually plays — do not scan,
  build from, or modify it. Check the `name` field in `minecraftinstance.json` to be sure.
- File **8843492** (pack 1.0.4) went up tagged `1.21.1` only — the NeoForge tag was eaten by
  the PowerShell `-File` array bug. Harmless for installing but it will not show under a
  NeoForge filter. Fixable in the authors console without re-uploading. **He has not said
  whether to do it.**
- The MineColonies pack could gain The Waking World as a 1.0.5.

**Housekeeping**
- Rotate the six exposed secrets.
- The local-only `scrub-backup` branch and `.git/refs/original/`.
- YouTube phone verification.

---

## 7. Hard-won facts worth not rediscovering

- **`Ruin.mark` feeds whichever record is open** — fight or cataclysm scar. Before that a
  crater was written down nowhere.
- **Every Minecraft heightmap counts leaves.** Use `KingdomExpansion.groundY`, never a raw
  heightmap, for anything meaning "the ground".
- **ModelPart fields persist between frames.** Anything a model touches must be written every
  frame from its own base, or `-=` becomes an integrator. (Cost one build where the mage's
  head and body ended 200 units below the world.)
- **`BlockBehaviour.useItemOn` fires for every use including an empty hand.**
- **A player auto-climbs 0.6 of a block**; a bed's `FACING` points foot → head.
- **A fence does not make a diagonal beam.** Doubled full blocks do.
- **`LivingEntity.hurt` has a 10-tick mercy window** that silently eats scripted damage.
- **`canBeSeenAsEnemy()` is false for an invulnerable entity** — it can never be targeted.
- Ring particles: `Cataclysms.ring` takes the radius in **blocks** and converts (`radius / 3`).
- Kingdom geometry: wall `RADIUS 56`, `REACH 70`, moat 59.5–62.5, ring road 40, march wall
  `OUTER 118` in 24 arcs. Keep `BAILEY 22`, hall half-width 7, z −6…12.
- Music: seamless loop = `mid(X..L−X) + crossfade(tail→head, X = 4 s)`, then normalise to
  **−17 LUFS** (where the colossus themes sit). OGG Vorbis q5.
- `device_stage_files` wants `C:\Users\marko\...`; `device_bash` wants `$HOME/mnt/...`.
- `scan-instance.py` runs in **device_bash**, not PowerShell.
- A modpack file must **not** be given the "Java 21" game version (`errorCode 1009`).
- **`/place structure` wants every chunk of the structure's bounding box loaded**, and `forceload add`
  refuses more than 256 chunks in one call - four quarters of a 300-block square. Forceloaded chunks
  take a minute to arrive on two cores; `setblock` is the quick test of whether one is there.
- **An open trapdoor's plane lies against the face opposite its `FACING`**: to hang a shutter flush on
  a wall, put the trapdoor in the block outside the wall with `FACING` = the wall's outward normal.
- **Water placed at each column's own ground on a slope is a spring.** A garden bed must be one
  level (the highest ground, filled with dirt) or it drains itself down the lane.
- A design drawn in a local frame turns with `BlockState.rotate(Rotation)` - stairs, doors, beds,
  logs, signs all follow; `Rotation.CLOCKWISE_90` maps (x, z) → (-z, x) and takes SOUTH to WEST.
- **The mason's `canSurvive` retry saves the order problem**: hanging lanterns, bells and wall signs
  fail on the first pass (their support is above or beside, laid later) and land on the retry.
- Recovering source from a jar: an anonymous class that gained a method leaves its enclosing
  method's bytecode unchanged; `javac` compiles `if (CONST_FALSE)` blocks away but still emits their
  lambdas; `List.add` vs `ArrayList.add` in a fingerprint is only the declared type of a local.
