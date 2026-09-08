# Ideas not yet built

Things Lovkar has asked for or thought of that are not in the mod. Kept apart from `PLAN.md`,
which is a record of what happened, so that neither has to be read to find the other.

Nothing here is a commitment. An idea that turns out to be worse than it sounded gets a line
saying so rather than being quietly deleted.

---

## Kingdoms that grow  ·  0.3  ·  his idea, 8 September 2026

> "Kraljestvo se lahko nadgrajuje, veča, širi, in tako dalje (tudi glede na to kako interactaš
> z njim)" — a kingdom can be upgraded, grow and spread, partly according to how you deal with it.

Today a kingdom is a fixed thing: walls, a keep, a king who holds audience and is succeeded, guards
and townsfolk who trade (`kingdom/`, `KingdomData` holds anger, permits, the throne and a
treasury). It is the same size on the day you find it as on the day you leave.

What the idea is really about: the mod already tracks how a player has treated a kingdom — anger,
permits, the treasury, whether the king was helped or robbed — and none of that has a visible
consequence in the world. A kingdom that visibly grows because of you is the same feedback loop
[[Unrest]] gave the cataclysms: the world answering what you did to it, in a language you can see
from a hilltop.

Worth thinking about before anyone writes code:

- **Tiers rather than continuous growth.** A hamlet → a walled town → a keep with outbuildings →
  a city. Each tier is a structure variant, so the growth is a placement rather than a simulation,
  and a kingdom that grew looks built rather than accreted.
- **What makes it grow.** Trade volume through its traders, favours done for the king, a colossus
  killed in its country, tribute paid. What makes it shrink or refuse you: anger, robbery, a
  colossus you woke on its doorstep.
- **The obvious risk is that it becomes a chore.** Nobody should have to grind a village. The
  growth should follow from what a player was doing anyway, and be noticed rather than pursued.
- **The second risk is block edits.** Growing a town means placing structures into terrain a
  player may have built on. Whatever is placed must never overwrite player blocks; the ruin and
  hamlet code already has to solve this and should be read first.
- **The cheap version, if the full one is too much**: no new buildings at all, but the kingdom's
  population, guard count, trade stock and the king's own words change with its standing. That is
  most of the feeling for a tenth of the work, and it is a good first tier to ship.

Fits naturally with the 0.3 headline being a sixth cataclysm, since one is world-scale and the
other is human-scale and neither competes for the same code.
