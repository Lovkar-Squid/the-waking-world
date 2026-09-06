# Changelog

All notable changes to The Waking World. The format follows [Keep a Changelog](https://keepachangelog.com/);
versions follow [Semantic Versioning](https://semver.org/) with `-beta.N` pre-releases.

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
