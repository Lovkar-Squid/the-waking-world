# Changelog

All notable changes to The Waking World. The format follows [Keep a Changelog](https://keepachangelog.com/);
versions follow [Semantic Versioning](https://semver.org/) with `-beta.N` pre-releases.

## [0.3.0-beta.1] - 2026-09-12 "Crown and Tower"

The first public build of the 0.3 line: the dark mage and the kingdoms that grow. It is the
alpha.15 build under a release name - everything in the alpha entries below is in it, and nothing
else. Play-tested in the author's worlds (the suburb, the doors, the terraces, the engines and their
volleys) and on a headless server; a beta because the growing kingdoms are a big new system meeting
other people's worlds for the first time.

### Known
- The works (farm, mill, tower, market, catapult) still want fairly level ground - a plot with more
  than a few blocks of rise is passed over; the houses take up to twelve. Cliff-side plots are
  refused. A town in the mountains grows more slowly than one on a plain.
- A suburb raised by an earlier alpha gets its doors with `/wakingworld kingdom houses redo`.

## [0.3.0-alpha.15] - 2026-09-11 (unreleased)

The suburb takes the ground as it finds it: trees felled, hillsides cut and terraced.

### Added
- **Trees round a plot are felled.** Every log within four blocks of the plot comes down with the
  house, the road and the back lanes take down what stands over them, and when the masons finish a
  sweep takes the leaves that have no tree left (`Tidy`, round the house and along a newly laid
  road). The kingdom's own trees and hedges are persistent and are never touched.
- **A house stands on any hillside up to twelve blocks of rise.** The floor is set at the level of
  the road's (or the back lane's) edge in front of the door, so the door always meets the street;
  uphill the plot is cut to that level and the cut is faced with the plinth stone - a low wall on top
  where the face is tall; downhill the house stands on its plinth. The apron round the house is grass
  again after the cut, the garden and the trees stand on the cut ground, the path to the road is
  level with the floor. Plots used to be refused past four blocks of rise.
- Ores at the surface (a mountainside is full of them) no longer count as "something built on the
  plot" - they are natural ground for every plot and site test, and for the masons.

### Fixed
- A drawing (a house plan) is now cut first, from the top down, and built after, from the ground up.
  Before, the farmland of a garden dug into a hillside would not stand under the stone that was
  still to be cleared above it, and the crop above it had no farmland: hillside gardens came out as
  bare stone. The masons also take the ground off a block that will not stand under it.

## [0.3.0-alpha.14] - 2026-09-11 (unreleased)

The suburb gets its doors.

### Fixed
- **The houses outside the walls had no doors** - and no window panes, no plinth course, a stone
  floor where planks were drawn, and a flue that stopped at the roof. A house plan is a drawing:
  the walls are drawn first and the door is drawn into them. But the masons lay every course in
  turn and only into air or natural ground, so the wall went up and the door, arriving at a wall,
  was refused. The previews never showed it because they draw last-course-wins. A house plan is
  now exactly that (`KingdomBuild.Plan(true)`): a later course at a position replaces the earlier
  one. The works and the march wall keep the old plan, where an air course before a block course
  clears the ground for it.
- `/wakingworld kingdom [at] houses redo` raises every standing house again over itself with the
  design as it is drawn today - the way a suburb built by alpha.12 or alpha.13 gets its doors.
  The masons lay every course this time (anything a player changed inside a house is lost); the
  people already living there stay. `kingdom build` lays it all at once.

## [0.3.0-alpha.13] - 2026-09-11 (unreleased)

The engines: a city gets its catapult, and the stones it throws arrive.

### Fixed
- **A city could grow to its full tier and never raise a catapult.** The works were sited from
  fourteen fixed spots round the town; on rough ground, once the roads and houses and the other
  works had taken their share, every one of them was refused - and the same fourteen were tried at
  every review, so the town went on building march wall for ever with three works and no engine
  (Greyhaven on 11 Sep stopped at 3/6; Sarnmark on 9 Sep at 0/6). The plot search now walks five
  rings from a random angle (up to 180 spots, the ground survey on the first sixty that pass the
  cheap tests) and takes a rougher plot when no flat one is left; works keep 40 blocks between
  their centres rather than 48.
- A city (tier 4) with no engine raises its catapult before any other work, whatever the cycle
  says. A city that grew the ordinary way gets the same order as before: farm, mill, tower, market,
  catapult, farm.
- Siege stones are thrown from 58 blocks back towards the town and 74 up - past the edge of what a
  player 160 blocks off (the Signal Horn's reach) keeps ticking, so a volley could hang in the air
  and never land. The chunks the stone starts in and lands on are now held open for the throw, the
  way a falling star's are.

### Changed
- The kingdom report says `engines N`; a review that finds no ground for a work says so in the log.

## [0.3.0-alpha.12] - 2026-09-11 (unreleased)

The suburb: a kingdom that grows now grows outward.

### Added
- **Houses outside the walls.** A town (tier 2) keeps six houses along the roads out of its gates, a
  walled town thirteen, a city twenty-two - raised a few at a time as the town is reviewed, so the
  growth is watched rather than found. Six designs, each drawn once and turned to face its lane:
  the cottage, the longhouse, the jettied townhouse, the smithy, the tavern with its sign, and the
  chapel with a bell in an open belfry (a city's only). Five timber-and-roof palettes, chosen by the
  plot. Every house is footed to the ground, floored, furnished and lit; every one has a garden, a
  tree, a hedge or a yard wall; the lanes get their lamps, a well at the head of each back lane, and
  the road is paved out to the march wall. The people move in when the masons finish: townsfolk of
  the trade the house suggests, kept to the lane they live on.
- `/wakingworld kingdom [at] houses [n]` raises n houses at once; `houses forget` gives refused plots
  another look. The report now says `houses 6/13`.
- `/wakingworld kingdomscan` prints the exact `/place structure` line for every cell that would take
  a kingdom - the way to put one down on a headless server without a four-minute `/locate`.
- Tools: `tools/java/HousePreview.java` + `tools/plan_iso.py` draw every design on flat ground with
  stairs, panes, fences and shutters as the shapes they are; `tools/world_iso.py` does the same for a
  box of a real world out of its region files; `tools/region_read.py` has `state_grid` back.

### Changed
- The works (farm, mill, tower, market, catapult) keep out of the four road corridors now; they are
  the suburb's.
- `KingdomBuild` jobs can carry something to do when the last course is laid.
- Built with `javac -g` from now on (see `docs/RECOVERY-0.3.md`).

### Fixed
- The 0.3 source, recovered from the alpha.11 jar after the workspace that held it was lost.

## [0.3.0-alpha.1 - alpha.11] - 2026-09-08 to 2026-09-09 (unreleased)

The dark mage and the kingdoms that grow. Written up from the jar and `STATUS.md` after the source was
recovered (the alphas' own notes went with the workspace); the detail is in `STATUS.md`.

### Added
- **The dark mage**: his tower on a crag, the Asking Stone and the five rites (aimed down the
  player's bearing), a four-stage boss with seven attacks, a ward per stage, four forms that come
  apart rather than grow, his own boss bar and window, four battle tracks that turn over with his
  stage. The last blow folds him into a jar: the **Pocket Mage**, a companion with orders and
  stances (MEEK / DEFEND / GUARD), Mend, Light and Gather - or a prisoner for a king, who digs a cell
  under the bailey. Ward stones, rune sentinels in the fight, the Mage's Mirror.
- **Kingdoms that grow**: standing (-100..100) moved by trade, a Colossus Heart given to the king, a
  giant put down in the country, or an offence; tiers 1-4 with hysteresis, reviewed once a game day
  with a player near; the Named Lands as the border, one land per tier. Dressing per tier (lanterns
  on the wall walk, the town's colours, flower boxes), works outside the moat two per tier (farm,
  mill, watchtower, market, catapult), a march wall at radius 118 in 24 arcs from tier 3, the king's
  charge (a levy of goods or a contract against the mage), `KingdomRepair` (the masons rebuild
  damage from the hourglass's own record, and fight fires and lava), `KingdomBuild` raising
  everything course by course, catapults that answer a horn blown at a giant, the **Signal Horn**.
- `/wakingworld kingdom [at] [standing n | repair | build | engine]`, `/wakingworld bombard`.

### Fixed
- `Ruin.mark` feeds whichever record is open (a lone meteor's crater was written down nowhere).
- Every heightmap counts leaves: `KingdomExpansion.groundY` walks down to real ground.
- Guards no longer freeze at their post (`PostAttackGoal` leashes from the post, not the restriction).
- The mage folds on any death, is immune to drowning and falling, walks as well as blinks, and his
  sentinels no longer shoot him.
- The 10-tick mercy window no longer eats a siege volley (`siegeStruck`).

## [0.2.0] - 2026-09-08 "Falling Sky"

The Cataclysms: five things that can happen to a world that is waking up. Also the Named Lands, and
the supporter perks switched off.

### Added
- **The Falling Sky** - a meteor shower. Starstone in the crater, star iron out of it under a pickaxe.
- **The Rising Mountain** - a volcano grows out of the ground in stages, basalt and tuff over a plugged
  throat so the crater holds its lava instead of pouring it down the slope.
- **The Blood Moon** - a red sky the client fades into over four seconds, and a siege that places
  monsters on ground that is genuinely dark, 20 to 46 blocks out. Dawn clears them, including the ones
  that were sitting in a chunk nobody had loaded.
- **The Wandering Column** - a tornado that walks the country and lifts what is loose and under open
  sky. There is no funnel model: what you see is the debris, ninety blocks on a helix.
- **The Turning Ground** - an earthquake: the camera shakes, faults open along the heightmap, and
  nothing with a block entity or a light in it is touched.
- **The Named Lands** - the world is divided into 384-block squares and each one is named the first
  time somebody walks into it, from the ground itself. Written by Gemini when the server has a key and
  from the templates when it does not; a name is written once and then kept.
- `/wakingworld volcano | bloodmoon | tornado | earthquake | lands`, and a config section for each
  cataclysm's odds.
- **Camera scenes for the cataclysms**: `/wakingworld cine lands | tornado | earthquake | volcano |
  meteor | bloodmoon`, and `cataclysms` for all six in trailer order - about three minutes, the light
  running from first light to midnight across the cut. Each one finds its own open ground, so they
  play in any world without a structure to stand on. `/wakingworld site` reports the spot the camera
  would pick, and `/wakingworld volcano <x y z> <height> <foot> <seconds>` forces the pace of a rise.
- Nothing the world drives can start while the camera is rolling. A meteor shower or a blood moon
  landing on top of a scene is a ruined take that gives no sign in the footage of what went wrong.
- **The Named Lands get a card of their own** instead of a vanilla title: the name over a rule that
  opens out of the middle, the lore wrapped to a readable measure under it, and the land's kind above.
  A vanilla subtitle is drawn as one line and never wrapped, so the lore used to run off both edges of
  the screen and only its middle was ever legible.
- **A waypoint on your map** when you walk into a named land, for JourneyMap and Xaero's. Neither is a
  dependency - without a map mod nothing happens - and the client config can turn it off.
- **The volcano runs.** Lava comes over a notch in the rim and down one flank in a walled channel from
  the crater to the ground, where it spreads into a burnt fan; the rim is ridged rather than round, so
  the thing is a mountain and not a stack of discs; the plume stands eight stations up the sky with
  embers at the throat and ash falling on anyone under it; and the default rise is halved to two
  minutes.
- **The tornado has a body**: the column wound as a helix instead of a haze, a skirt of the ground it
  is standing on, debris thrown clear, and lightning that starts no fires.
- **The earthquake can be watched, not only felt**: dust off the faults for the whole of it, and a deep
  note under it every second.
- **A star landing is an event**: the flash, a ring of embers going out from the crater, and a column
  of smoke standing over it afterwards so the strike is still findable.
- **The blood moon's monsters are outlined**, so a night siege reads as more than a few pairs of eyes.
- The camera lifts the brightness for night scenes and puts it back at the cut, keeps the boss bar for
  fights only, and clears what the player is looking at on the tick rather than on the frame - too late
  for tooltip mods, which drew over the take.
- **The cataclysms have their own voices** instead of borrowing the explosion sound: a volcanic
  rumble, a tornado that roars like a freight train, the ground's own note under an earthquake, and
  the falling whistle of a star. All synthesized (`tools/sfx/cataclysms.py`); the four that run under
  an event are seamless loops.
- **They leave a mark.** Ash comes down downwind of a vent and dresses the ground it lands on, the
  country round a strike is scorched and its sand fused to glass, and a tornado leaves a swathe of
  snapped trees and scoured ground you can still find days later.
- **Omens.** Every cataclysm now opens with a warning: a low note out of the ground, the light going
  wrong at the edges of vision, the animals bolting, and a line that says what is wrong without
  saying what is coming. `omens` and `omenSeconds` in the config.
- **The Atlas**, a chapter at the back of the Almanac: every named land you have walked, drawn where
  it actually lies rather than as a list, with the square you are standing in edged in gold.
- **Starstone and Star Iron are worth crossing a crater for.** Three star iron and two amethyst hold a
  Sleeper's Ember, which is the rite's own fuel - so a meteor is now a way to wake another colossus.
  A blast furnace gets two star iron out of a Starstone block, so a silk-touched one is worth carrying
  home.
- **The volcano throws its own rock.** Bombs are fired out of the throat and arc over the rim to land
  forty to ninety blocks out, instead of being spawned in the sky beside the mountain like meteors -
  which is what they were, and it showed.
- **The earthquake opens while you watch it.** The fault used to be cut in a single tick, so a camera
  on the ground saw a field that had already finished cracking and then twenty-six seconds of nothing.
  It walks now, a few blocks a second, throwing the surface up as it goes.
- The volcano's plume is much heavier, its warning is 22 s instead of 40, and a world-driven one takes
  a minute rather than two.
- The blood moon breathes: the wash is stronger and has a slow pulse in it, and there are embers in the
  air all night, so it is a thing happening in the world and not a colour on the glass.

- **`[features]`: the parts of the mod, one switch each.** `colossi`, `titan`, `cataclysms`, `kingdoms`,
  `ruins`, `namedLands`, at the top of `wakingworld-server.toml`. A part switched off is not there:
  its structures never find a spot, its rolls never come up, an altar of a switched-off part answers
  but does not open, and its wanderers do not spawn on their own. Only the giants, only the cataclysms,
  or everything but the kingdoms - and the rest of the mod carries on without it. The `/wakingworld`
  commands still work either way, so an operator can still show somebody a volcano on a world that
  never rolls one.

### Changed
- **`namedLands` moved** from `[lands]` to `[features]`, and `greeting` from `[cataclysms]` (where it
  never belonged) to a new `[story]`. A config from an earlier alpha loses those two settings and
  takes their defaults; nothing else moved.
- **The supporter perks are switched off.** Every class is still here and nothing about them changed,
  but `SupporterList.ENABLED` is false: `/wwpatreon` is not registered, no list is fetched, no account
  is looked up, and the Hall of Wakers page does not render. The mod now talks to no outside service.
- `/wwpatreon status` shows the date a membership runs to (while the perks are on).
- Shrines also read the conventional biome tags (`#c:is_*`, marked optional), so they generate in other
  mods' biomes as well.

### Fixed
- The greeting sat in the `[cataclysms]` section of the config, which is not what it is.
- A land could be given a name a neighbour already had; clashes are re-rolled now.
- A meteor could fall into a chunk that was not ticking and never land.
- The volcano switch turned off a cone that was already going up, not just the opening of new ones -
  so a volcano forced from the command stood half-built for ever, though the config promises the
  command still works.
- A forced volcano's rise took longer than it was told to: the pulse clock only wakes every twenty
  ticks and any pace that was not a whole number of seconds quietly rounded up.
- **The camera hopped over every ridge it crossed**, and the server-side smoothing could not help,
  because the cause was on the client: each frame it checked the one column under the camera and, if
  the camera was below it, snapped it on top - sixty times a second, across broken country. It rides
  an envelope now, up at once and down slowly, taken over a few points rather than one.
- The Almanac's Atlas chapter showed its own translation keys: they were written without the modid
  segment the screen looks them up under.
- **Nothing a cataclysm drew was visible from more than 32 blocks away.** `sendParticles` without a
  force flag only reaches players inside that radius, and everything here - the volcano's plume, the
  tornado's column, the dust off an earthquake, the ring a star throws out - is meant to be seen from
  much further. That is why two takes came back with no smoke and a dull tornado. It all goes through
  one forced send now.
- The blood moon never turned the sky red for anybody running shaders. A shader pack draws its own
  sky and its own fog and never asks the game what colour they should be, so the fog tint did
  nothing; there is a red wash over the finished frame as well now, which nothing can override.
- The volcano was framed from a hundred and twenty blocks out at a height of fifty-eight, looking
  down: the cone came out as a bump at the bottom of the frame and then left it. A mountain has to be
  looked up at.
- The camera hopped over ridges instead of flying: every key was lifted clear of the ground under it
  on its own, so the curve through them was a flight of steps. The climb between keys is bounded now.
- The camera picked its sites with a fallback that had no ground under it at all, which put the
  tornado, the meteor and the blood moon over open water; and it shot the volcano into the setting
  sun, which came back as a silhouette in a white frame.
- Laying the volcano's foot was thousands of blocks inside one tick - a two-second freeze in the
  middle of the shot. It goes down in strips under the warning smoke now.
- A battle theme the sound engine declined to start left the whole fight silent. The director
  measured the track's age in ticks of the track itself, which stand still in exactly that case, so
  it went on believing the music was still starting and never retried. It reads the clock now, and a
  fight that loses its theme picks it back up within a second.

## [0.1.0-beta.5] - 2026-09-06

The supporter update: cosmetic perks for the mod's Patreon supporters, a music disc from every giant, and
the small fixes found since the first beta. Nothing here changes the gameplay - the fights, the drops and
the rites are the same for everyone.

### Added
- **Supporter perks** (Patreon, cosmetic only). Link a Minecraft account in the game with `/wwpatreon`;
  the page that opens lets you pick your look, and `/wwpatreon aura <name>`, `/wwpatreon colossus <name>`,
  `/wwpatreon credits on|off`, `/wwpatreon status` and `/wwpatreon refresh` do the same without leaving
  the game. Perks come from a small supporter service; the mod only draws what it is told. The service
  publishes no names - a salted hash per account and the chosen look - and asks Mojang to confirm the
  account before it links or changes anything.
- **Auras**, drawn with the mod's own rune, ring and ember particles: the Waker's Runes; the Colossus
  Sigil (glyphs orbiting your feet and a ring of light every few seconds) in ember orange or the colours
  of the six lands; the Titan's Void (twin pulse and embers) and the Waking Crown (a halo of gold glyphs).
  Client config `supporters.showAuras` hides them on your own screen.
- **Colossus styles**: the giants a supporter's rite wakes rise dressed as the Sentinel (blackstone and
  iron, glowing seams, a visor), the Eldest (deepslate with gold-lit carvings and moss) or the Seraph
  (white plating, violet light along the edges, a visor, lit horns). Same silhouette, same hit boxes -
  every style is checked against the plain giant of every land - other stone. The Titan is never dressed.
- **Music discs**: every colossus leaves its own theme behind when it falls, the Titan too - seven discs
  for the jukebox (Jukebox/Note Blocks volume slider).
- **The Hall of Wakers** at the back of the Almanac's first chapter: the supporters who chose to be named.
- The kingdom greets its wakers: the king's audience, a word at the traders' stalls (same prices for all),
  a salute from the guards.
- A supporter's Horn of Waking sounds in the colour of their aura.
- A colossus remembers who woke it (`Waker` in its data).

### Changed
- The glowing veins are drawn from their own random stream, so a styled giant weathers exactly like the
  plain one. Giants saved by beta.1 may show a different pattern of veins after the update; nothing else.
- The music themes are also registered as `record.colossus.<kind>` sound events for the discs.

## [0.1.0-beta.1] - 2026-09-05

The first public build, for NeoForge 1.21.1. Everything below is new.

### Colossi
- Six kinds of giant - Stone, Earth, Sand, Ice, the Sea, the Grove - each with its own body shape, glowing
  rune veins, two or three signature moves, a battle theme and a victory theme. A colossus is built from
  the blocks round the shrine that wakes it.
- Twenty attacks - the shared ten (stomp, swipe, slam, boulder, roar, uproot-and-throw, charge, leap,
  rubble rain, grab and hurl) and the signature moves (frost breath, ice spikes, sandstorm, sand geyser,
  tidal wave, water jet, grasping roots, spore cloud, rockfall, quake) - three phases, terrain-tearing
  craters, trampling, wading through water, climbing out of holes and off the edge of the End islands.
- Cores as weak points: loud and bright when hit, more damage taken there, the giant cannot die until
  enough of them are broken. Health floor while cores stand.
- Awakening: the giant rises out of the ground shedding earth. Death: half a minute of collapse, limb by
  limb, into a mound of its own blocks, with the crack echoing across the land.
- Custom boss bar carved in stone, filled with runes in the kind's colour, one glowing socket per core;
  the Titan has its own void-river bar with a crown.
- Camera shake, footsteps, dust and rune particles (all in-mod, no particle library).

### Rites, shrines and items
- Six shrines (Standing Stones, Barrow, Sand Tomb, Frost Cairn, Sunken Shrine, Overgrown Sanctum), each
  with an Altar of the Sleeper on a dais.
- Rite: Sleeper's Ember + Rune of the land + the land's gift, then the Horn of Waking. A 220-tick
  ceremony unique to every kind, then the giant rises 36 blocks past the altar.
- Items: Colossus Heart, six Sigils, Colossus Hammer (3D model, dive slam, half fall damage), Horn of
  Waking (found in vaults), six Runes, Sleeper's Ember, Key of the Titan, Void Sigil, Hourglass of
  Restoration, Heart of the End, Dead Letter, Waker's Almanac, four spawn eggs.
- Hourglass of Restoration: the whole fight is recorded block by block and put back exactly.
- Heart of the End: eat one for two more hearts, up to five; kept across deaths.

### Dungeons and mobs
- Sleeper's Vault (embers, runes, the Horn, arrow traps, thrall spawners), Drowned Cistern, Ember Forge
  (a Rune Sentinel guards the master's vault) and the Void Reliquary in the End (the Void Sigil).
- Custom mobs with their own skins, garb and synthesized sounds: Stone Thrall (and the Hollow Thrall of
  the End), Ember Wraith, Rune Sentinel, Drowned Keeper. Rare natural spawns by night: thralls in
  ruins, wraiths in deserts and badlands, keepers in swamp, mangrove and river water.

### Story and world
- Dead Letters: found in ruin and vault chests, written for the place they were found - the nearest
  shrine, vault, dungeon or kingdom, with paces, winds, the exact offerings and a compass on the page.
  Five writers. Optionally written by Gemini about your world's real places and events (`[letters]`
  config; key stays on the server; template fallback).
- Letters read aloud: with the key, every new letter gets a voice in its writer's own manner (Gemini
  TTS - a weary miner, a quiet walker, an old monk, an excited child, a gruff sergeant), stored in the
  world so it is only made once. The letter reads itself when opened (client `readLettersAloud`), the
  words light up as they are spoken, a horn on the page plays, pauses and stops; a letter stays shut
  for the minute its voice is still being made. `voicedLetters`, `voiceModel`.
- The Waker's Almanac: a tabbed guide book given on first join (or crafted: book + amethyst shard).
- Ruins (cottage, watchtower, well, graveyard with crypt, camp, chapel, market, farm, palisade) with
  procedural weathering, and whole abandoned hamlets.
- Kingdoms: walled towns with moat, eight towers, barbicans, market, chapel, farms and a keep (great hall,
  throne, donjon, treasury). Guards (archer, knight, spearman), townsfolk with a custom trading screen
  (surveyor maps to shrines, vaults and the next kingdom; relics, tools, food, candles, books), a king
  who grants an audience with news of your world. Anger for theft and violence; succession when the
  king dies.
- The Titan: an End arena with the great altar and six lesser altars (two runes of each land), the
  Titan (800 HP, harder phases) that leaps from island to island after you, the Dragon Egg as the third
  offering (given back with the loot), the Titan's Gate that opens on the rim when it falls and carries
  you to your bed; a new rite closes it. Dragon Egg safety (indestructible, a new one per dragon;
  toggles).
- 24 advancements in their own tab; creative tab.
- Config: `[colossi]`, `[letters]`, `[rites]` (cost per offering type, multipliers, Titan toggles),
  `[titan]`; client: camera shake, boss music.
- `/wakingworld` operator commands, including `cine` (the trailer camera: loads the stage first, then
  flies you through a scene as a spectator).
- Recommended shader settings for Complementary Reimagined r5.9 + Euphoria Patches 1.10.0 in
  `docs/shaders/`.
