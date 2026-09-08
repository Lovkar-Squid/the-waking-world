# Polishing 0.2 before it goes out

Written 8 September 2026, after alpha.9 was built. Eight commits sit local and unpushed, waiting
for Lovkar to test them. This is the list of what would make 0.2 feel finished rather than merely
complete — ordered by how much each is worth against how long it takes, so the top of the list is
where to stop if there is no time for the rest.

Nothing here is a bug report. 0.2 works. This is the difference between a mod somebody installs
and a mod somebody keeps.

## Done since this was written  ·  8 September, 0.2.0-alpha.10

**§5 is done.** Nine advancements (Starstone → Star Iron → the whole suit → taking a real blow in
it; live through one cataclysm, live through all five, stand in a tornado for five seconds; the
Chart, and twelve named lands). The Almanac has a tenth chapter on the five and on Star Iron. The
suit says on its own tooltip what it does, because a number that never happens is invisible. The
Wayfarer's Chart introduces itself the first time somebody walks into a named land.

**§6 was mostly already there** and the plan was wrong to imply otherwise: every cataclysm already
has its own on/off, chance and cooldown in the config, with a comment per value, and `omens` /
`omenSeconds` are settings too. What is genuinely missing from §6 is only the balance pass, which
needs §1 — playing it — before any number should be moved.

**§3 was half wrong too.** The sounds exist and are registered: `cataclysm.volcano.rumble`,
`cataclysm.tornado.roar`, `cataclysm.quake.rumble`, `cataclysm.meteor.scream`, `cataclysm.omen`.
Whether they are *enough* is a question for headphones, not for a source tree — check it in §1.

**§2 exists in outline**: `Omen.java` already gives every cataclysm forty seconds of warning, a low
note, the light going wrong, the animals fleeing and a line in the chat. What is untested is
whether forty seconds is enough and whether the five warnings are distinguishable from each other.
Both are §1 questions.

**§4 is partly there**: the tornado already lays a swathe behind it (`Aftermath.swathe`) and the
volcano already leaves its ash on the downwind side. The earthquake's fault stays open. What is
still missing is the volcano cooling to stone and anything at all happening to crops, animals and
villagers.

So the list below is now shorter than it looks: **§1 is the whole job**, and §4 and §9 are what
follows from it.

---

## 1. The testing pass that has to happen anyway  · half an evening

Everything below is guesswork until this is done, because a cataclysm behaves differently in a
world that has been lived in than in a fresh one.

- One survival world, no cheats, from a new spawn: play until a cataclysm finds you rather than
  summoning one. That is the only way to find out whether the warnings are enough.
- Each of the five, once, at full strength, with a village in range: meteor, volcano, tornado,
  earthquake, blood moon. Watch what they do to a build, not just to terrain.
- A dedicated server with a second account, because everything in `cataclysm/` is server-driven
  and the particle rule (`Cataclysms.puff`, force = true) has never been checked with two players
  standing in different places.
- `spark profiler` during a tornado and during an earthquake. Both move a lot of blocks.
- Read `latest.log` afterwards, as always.

**Ship-stopper if:** anything throws, TPS drops below 15, or a cataclysm can destroy a build with
no warning the player could have acted on.

## 2. Warnings the player can act on  · 1–2 h

The single biggest difference between "epic" and "unfair". A cataclysm that arrives unannounced
reads as the mod cheating, however good it looks.

- Each event needs a warning that is unmistakable **and** has a name: the sky, a sound, and one
  line of text that says what is coming and roughly when. `Omen.java` already does some of this —
  the question is whether a player who has never read the wiki understands it.
- The warning has to arrive far enough ahead to *do* something: run, get underground, get the
  animals in. Thirty seconds is a jump scare; two to three minutes is a story.
- Different events must not warn the same way. If the blood moon and the meteor shower share a
  sky, both feel like weather.
- A player who is not looking at the sky needs to hear it. Distance-attenuated rumble for the
  earthquake, a low roar that grows for the tornado.

## 3. Sound  · 2–3 h

The cataclysms are visually strong and audibly thin. This is the cheapest "wow" left on the table.

- Tornado: a looping roar whose volume follows distance, plus debris impacts near the funnel.
- Earthquake: sub-bass rumble under the shake, and a settling groan when it stops.
- Volcano: a rising note during the plug phase, so the eruption is heard before it is seen.
- Meteor: the whistle should arrive before the impact — the delay between the two *is* the drama.
- Blood moon: a single held tone when it rises, then the mob sounds carry it.

The synths in `tools/sfx/` already produce this class of sound. Nothing needs a library.

## 4. Consequences that outlive the event  · 2–4 h

Right now a cataclysm happens and then the world is much as it was. What players remember is the
scar, not the spectacle.

- The tornado should leave a visible path: felled trees, scattered blocks, a strip of stripped
  grass. `Aftermath.java` is the place for it.
- The earthquake should leave the fault open rather than closing it.
- The volcano should cool: obsidian and basalt where the lava stopped, not lava forever.
- Crops, animals and villagers should notice. A village that lost its roofs is a story a player
  tells; a village that is untouched makes the whole thing scenery.
- Something to salvage from each: an item, a block, a reason to walk into the crater. Starstone
  already does this for the meteor — the other four have no equivalent.

## 5. Star Iron has to be findable without the wiki  · 1–2 h

The kit is the reward of 0.2 and nothing in the game points at it.

- An advancement chain: survive a meteor → hold Starstone → forge the first Star Iron ingot →
  wear the set. Twenty-four advancements exist for 0.1; the new content has none.
- A chapter in the Almanac for the cataclysms and for Star Iron: what each event does, what it
  leaves behind, what the metal is for. The book already exists and is the natural place.
- Check the recipes are visible in JEI, that the tags for `enchantable/*` are all present (they
  are what make the tools enchantable at all), and that the tooltip explains the sky-damage
  reduction, which is otherwise invisible.
- The Wayfarer's Chart has the same problem: a player who does not know it exists never crafts
  it. It should be mentioned the first time a Named Land is entered.

## 6. Balance and the config  · 1–2 h

- Every cataclysm needs its own on/off and its own frequency in the config, and the config needs
  a comment per value saying what a server owner is choosing. Somebody will want the blood moon
  and not the volcano.
- Damage numbers: a cataclysm should be survivable in iron with warning, dangerous in leather,
  and a nuisance in Star Iron. That is what makes the kit worth making.
- Frequency: the first one should land in the first few in-game days, so a player knows the mod is
  there — but two in one week is exhausting. A cooldown between events, not just a roll per dusk.
- A per-world "how many have I survived" counter is one line of SavedData and makes a good
  advancement, a good statistic, and a good line in the death screen.

## 7. Named Lands  · 1–2 h

- Read fifty generated names in a row and count how many are embarrassing. The clash re-roll
  works; the question is whether the *style* holds up at volume.
- The land card is beautiful. It should appear once per land and never again — check that it does
  not re-fire on relog.
- A Named Land should be worth entering: a small piece of loot, a landmark, a letter. Right now
  the name is the whole reward.
- What happens at a border between two lands, and what happens in the ocean?

## 8. Performance, properly measured  · 1–2 h

- The tornado's chunk ticket radius is 4 and it walks; check it does not hold a corridor of chunks
  loaded behind it.
- Falling-block entities during the earthquake: cap them per tick rather than per event.
- The volcano's lava was cut from 1402 blocks to 122 — confirm that is still the case after the
  0.2 rework, on a slope rather than on flat ground.
- Test at render distance 8 and at 24. Particle-heavy events look very different at each.

## 9. First impressions  · 2–3 h

- A guide book, or at least a first-join message, that says what this mod does. A player who
  installs it and sees nothing for three days assumes it is broken.
- The mod's own logo and the CurseForge gallery need 0.2 shots: the tornado with its new body, the
  earthquake fault, the volcano's lava river, the Star Iron kit on a stand. Six of these were made
  for the trailer and are already in `promo/bts/`.
- The CurseForge and Modrinth descriptions still describe 0.1. Both need the cataclysms section
  and new screenshots before the file goes up, not after.

## 10. The release itself  · 1 h

In this order, because each step depends on the one before:

1. Bump to `0.2.0` in `resources/META-INF/neoforge.mods.toml` **and** the LOGGER line in
   `WakingWorld.java`. Never two different jars under one version.
2. `NOTEST=1 ./build.sh`, then `./test.sh` until it prints `OK`.
3. Deliver the jar the usual way: `Get-Process javaw` empty, old jars to `old/`, SHA256 both sides.
4. His own test, in a real world, before anything is pushed.
5. Push the eight local commits, tag, GitHub release with notes.
6. CurseForge file, then Modrinth version, then the modpack manifest — the pack can only name an
   approved file, so it goes last.
7. The trailer is already up: https://youtu.be/CjpReo67QQo — it goes in the release notes, both
   store pages and the Discord announcement.
8. The bot announces the release by itself now. Nothing to do by hand in Discord.

---

## Deliberately not doing before 0.2

- **The 1.20.1 port.** Decided already: only once 1.21.1 is solid.
- **More cataclysms.** Five is enough for one release. A sixth is 0.3's headline.
- **Reworking the colossi.** They are 0.1's content and they work.
- **Patreon perks.** Parked at `SupporterList.ENABLED = false` and staying parked.

## If there is only one evening

Do §1 (test), §2 (warnings) and §5 (advancements + Almanac chapter). Those three are what stand
between "the world does dramatic things at me" and "the world does dramatic things at me and I
know what to do about it" — which is the whole difference.
