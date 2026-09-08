# Ideas not yet built

Things Lovkar has asked for or thought of that are not in the mod. Kept apart from `PLAN.md`,
which is a record of what happened, so that neither has to be read to find the other.

Nothing here is a commitment. An idea that turns out to be worse than it sounded gets a line
saying so rather than being quietly deleted.

---

## Kingdoms that grow  ·  0.3  ·  his idea, 8 September 2026

> "Kraljestvo se lahko nadgrajuje, veča, širi, in tako dalje (tudi glede na to kako interactaš
> z njim)" — a kingdom can be upgraded, grow and spread, partly according to how you deal with it.
>
> Clarified the same day: *a mixture of MineColonies and The Village Fights Back*, applied to the
> kingdoms this mod already has.

### What each of those two actually is

**MineColonies** is a colony the *player* runs: you place the buildings, you assign the jobs, you
supply the materials, and the settlement levels up because you built it up. The pleasure is
management.

**[The Village Fights Back](https://modrinth.com/mod/the-village-fights-back)** is the opposite:
you do nothing. A "Village Brain Node" runs an emerald economy, the village evolves through four
stages, every villager fights, walls and gates go up on their own, golem factories and barracks
appear as they are needed, settlers are sent out to expand, and the whole thing tracks the player's
reputation. The pleasure is watching something live without you.

Note it is on **NeoForge 1.21.1** as well — the same loader and version as this mod. That is worth
knowing twice over: people will run the two together, and building the same feature would be doing
work somebody has already done better.

### What that means for us

The instinct is to take the growth from one and the autonomy from the other. The thing to be
careful of is that "a village that builds itself up in tiers" is exactly what The Village Fights
Back already is, on this exact version. Copying it is the one design in this file that is certain
to be judged against a finished mod, and lost.

What this mod has that neither of those does is a **relationship**. A kingdom here already has a
king who grants an audience and is succeeded when he dies, guards, townsfolk who trade, permits,
a treasury, and an anger that remembers being robbed — `kingdom/KingdomData` is holding all of it
right now and none of it changes anything a player can see from a hilltop.

So the growth should be **political rather than economic**. Not "it accumulated enough emeralds" but
"it did well out of knowing you", and the reverse. That is a thing neither of the other two does,
it uses state the mod already keeps, and it is the same loop [[Unrest]] gave the cataclysms: the
world answering what you have been doing to it, in a language you can see.

### Two directions, and they are not the same feature

He named both, and they want different code:

**Up** — the same town, better. Higher walls, a real gatehouse, paved streets instead of dirt,
banners, lanterns, gardens, a bigger keep, better-armed guards, traders with better stock. This is
the one people will see first, and most of it is decoration: a tier is a swap of the structure
variant plus a pass that dresses what is already standing. Cheap, and it reads from a hundred
blocks away, which is the whole point.

**Out** — the kingdom takes country. A border that moves: outposts, watchtowers, farms outside the
walls, a road running to the next thing it owns. Not more town — a town that keeps growing is a
city and eventually a performance problem. What spreads is *claim*, marked by a few small
structures and a line on a map.

### The border should be measured in Named Lands

This is the part worth getting right, and the mod has already built it without meaning to.

The world is **already** divided into large square cells with names, each one named the first time
somebody walks into it, each one drawn on the Wayfarer's Chart the player carries. A kingdom's
border does not need a new spatial system, a chunk-claim map, or a polygon: it is **a set of named
lands**. "The Old Weald belongs to Hearthhold now" is a sentence a player understands immediately,
it is already on their chart, and the land card that names a country can say who holds it.

Everything falls out of that:

- Growing means **taking the next land**, which is one cell, not a radius. A kingdom at tier one
  holds the land it stands in; at tier four it holds four or five, with an outpost in each.
- The Chart already draws them, so the border is visible without drawing a single new UI.
- Two kingdoms whose borders meet is a story that writes itself, and needs no new mechanic to
  notice - they simply want the same cell.
- **[[Unrest]] is the natural brake.** A kingdom will not spread into country that will not settle,
  and a land that goes unquiet after they took it is one they pull back from. That is the cataclysms
  paying for a 0.3 feature rather than being finished with.
- A land the player has never walked has no name yet - so a kingdom expanding into unnamed country
  names it, which is a nice reason for a name to arrive without you.

### Kingdoms have relationships with each other, not only with you  ·  his addition, 8 September 2026

Every kingdom holds a standing toward every other kingdom it knows about: **neutral**, **friendly**,
**hostile**. It is a small thing to store - one number per pair, in the same SavedData that already
keeps a kingdom's anger at a player - and it is what turns four separate villages into a map with
politics on it.

Two kingdoms only know about each other once their borders are close: the named-land grid gives
that for free. Kingdoms whose lands do not touch and are not within a land or two of touching have
no opinion, which keeps the pair table tiny however many kingdoms a world ends up with.

**What moves it.** Neighbours who both want the same unclaimed land drift hostile. Neighbours who
have been quiet next to each other for a long time drift friendly. A kingdom that lost country to
a cataclysm and had to pull back is easier to befriend and easier to bully. And the player is in
this: carrying a message, paying tribute on someone's behalf, or robbing one kingdom in sight of
another should move the pair, not just your own standing. That is the good version of the feature -
the player as a cause of the politics rather than a spectator of it.

**What it looks like from the ground**, which matters more than the number:
- **Friendly** - a road (below). Traders from one town selling the other's goods. Guards who let
  you through the gate if you are welcome next door. Both kingdoms' banners over a shared border
  land.
- **Neutral** - nothing. A border with no marker, and neither town facing the other.
- **Hostile** - walls and a watchtower on the side facing the other one, patrols along the border
  land, no road, or a road that has been broken. A player welcome in one is watched in the other,
  which is the cheapest possible way to make a choice cost something.

### Roads between them

**A road is the visible form of the relationship, and it is the best-looking part of the whole
idea.** Friendly kingdoms build one to each other; hostile ones never do; a road that already
exists and turns hostile falls into disrepair rather than vanishing - and a broken bridge or a
grown-over stretch says more than any number in a GUI.

How to build it without it becoming a pathfinding project:

- **Route along the named-land grid.** A road runs from town centre to town centre through the
  cells between them, one leg per cell boundary, which reduces routing to a handful of waypoints
  instead of a path over the whole world.
- **Build it in short segments, over time, as chunks load.** Never generate a whole road at once.
  A segment is a few dozen blocks of paving following the terrain; the road appearing over several
  days of play is *better* than it arriving finished, because the player watches it happen.
- **Follow the ground, do not flatten it.** Paving that steps with the slope, a small bridge over
  water, a cut where it must, and it simply stops at anything it cannot cross rather than tunnelling
  through a mountain. A road with a gap in it is fine; a mod that carves a trench across somebody's
  view is not.
- **Never through a player's blocks.** Same rule as expansion, and roads will hit it more often
  because they cross country rather than sitting in it. A segment that would touch player-placed
  blocks is skipped, and the road routes around or ends there.
- **Dress it.** Lanterns at intervals, a milestone or a shrine at a land boundary, a bench, a
  waystone at the halfway point. This is where the road stops being paving and starts being a thing
  people screenshot.
- **It is worth using.** A road is faster to walk than country, it is safe-ish - patrolled by
  guards from both ends - and it leads somewhere the player wants to go. That is the reason a
  player cares which kingdoms are friendly, and without it the relationship is decoration.

The relationship and the road together also solve the problem the border section left open: two
kingdoms whose borders meet is a story that writes itself, and now there is somewhere for that
story to go other than a stalemate over one cell.

### The shape, if somebody builds it

- **Tiers, not continuous growth.** A hamlet → a walled town → a keep with outbuildings → a city.
  Each tier is a structure variant, so growing is a placement and not a simulation, and a kingdom
  that grew looks built rather than accreted.
- **What moves it up:** favours done for the king, tribute paid, trade volume through its traders,
  a colossus killed in its country. **What moves it down or shuts you out:** anger, robbery, a
  giant woken on its doorstep, letting a cataclysm find it.
- **The cataclysm hook is the good one.** A kingdom in unquiet country has a *reason* to build
  walls, and a kingdom that has just been through a tornado has a reason to be smaller. That makes
  the two halves of 0.2 pay for a 0.3 feature instead of being finished with.
- **Never overwrite a player's blocks.** Growing a town means placing structures into terrain
  somebody may have built on. The ruin and hamlet code already has to solve this; read it first.
- **Nobody should have to grind a village.** The growth must follow from what a player was doing
  anyway, and be noticed rather than pursued.
- **Never swallow a player's base.** Expansion takes cells; a cell a player has built in must be
  refused, or bought, or fought over - but never simply annexed with a farm placed through a wall.
  This is the single most likely way the feature makes somebody uninstall the mod.
- **The cheap version, and a good first tier to ship:** no new buildings at all. The population,
  the guard count, the traders' stock and the king's own words change with your standing. That is
  most of the feeling for a tenth of the work, and it is the half that cannot collide with anything
  another mod is doing.
- **The second-cheapest thing, and probably the best value in the whole idea:** the dressing pass.
  Banners, lanterns, flower boxes, paving, a market awning, a repaired roof - placed onto the town
  that is already there, with no new structure and no new tier. A kingdom that visibly got nicer
  because of you is most of the feeling, and it is an afternoon's work rather than a system.

Fits naturally with the 0.3 headline being a sixth cataclysm: one is world-scale and the other is
human-scale, and neither competes for the same code.
