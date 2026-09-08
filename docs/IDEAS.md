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
- **The cheap version, and a good first tier to ship:** no new buildings at all. The population,
  the guard count, the traders' stock and the king's own words change with your standing. That is
  most of the feeling for a tenth of the work, and it is the half that cannot collide with anything
  another mod is doing.

Fits naturally with the 0.3 headline being a sixth cataclysm: one is world-scale and the other is
human-scale, and neither competes for the same code.
