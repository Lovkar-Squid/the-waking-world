## 0.3.0-beta.2 — a colony is somebody's land

**If you play with MineColonies, this mod now keeps off your colonies.** Everything The Waking World does to the world on its own stops at a colony's border:

- **Cataclysms** — a falling star, a volcano, a tornado, an earthquake and the blight all look somewhere else, and if one happens beside a colony it changes nothing inside it. A blood moon spawns nothing within your walls.
- **Kingdoms** — a growing town sites no farm, mill, watchtower, market or catapult on a colony, raises no house, lays no road or lane and builds no march wall across it. Its masons stop at the line; so do its repairs and its lanterns.
- **The engines** — a catapult will not fire on a colony's land, whoever blows the horn and whatever is standing there.
- **The Asking Stone** — the mage will not send a rite onto a colony. Turn, and ask elsewhere.
- **Colossi** — a giant breaks nothing, tramples nothing, throws no tree and heaps no mound on a colony's land, and rubble that would land there comes down as dust.
- **The wanderers** — Stone Thralls, Ember Wraiths and Drowned Keepers do not spawn inside a colony.

The border is the colony's claimed chunks plus a buffer of open country round them — 32 blocks by default. Both are yours to set: `[compat]` in the server config has `protectColonies` and `colonyBuffer`, and `/wakingworld colony [x y z]` tells you what the mod thinks of a spot.

**Nothing changes without MineColonies.** The mod does not depend on it; the one class that names a MineColonies type is loaded only if the mod is there.

**What is already built stands.** A kingdom that generated before you founded a colony keeps every block it had — it simply grows no further into you.

Proved against a real colony on a test server: a star that fell inside it wrote down nothing and left all 841 columns exactly as they were, while the same star outside dug 591; every house plot on the colony's side was refused; the paved road stopped exactly at the buffer's edge; the catapults refused the shot. And without MineColonies installed the same build still dug its crater, with nothing in the log.

Everything in 0.3.0-beta.1 is unchanged. Full changelog: https://github.com/Lovkar-Squid/the-waking-world/blob/main/CHANGELOG.md
