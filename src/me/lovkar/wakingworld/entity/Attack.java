package me.lovkar.wakingworld.entity;

/**
 * The colossus' moves. Every attack is a fixed number of ticks: a wind-up the player can read,
 * an impact tick where the damage happens, and a recovery. The client animates from the same
 * numbers (see ColossusPose), so what you see is when it hits.
 */
public enum Attack {
    NONE(0, 0, 0),
    /** One foot comes down: a crater under it, a shockwave ring racing far out along the ground. Jump over it. */
    STOMP(1, 34, 18),
    /** The right arm sweeps a wide arc in front; anything in it flies. */
    SWIPE(2, 28, 15),
    /** Both fists overhead, then into the ground: a big crater, a bigger wave. Get behind it. */
    SLAM(3, 46, 28),
    /** It tears a boulder off itself (tick 8), holds it, throws it (impact). The boulder stays where it lands. */
    BOULDER(4, 36, 22),
    /** Head back, roar: knockback, darkness. Announces a new phase. */
    ROAR(5, 44, 12),
    /** It rips a whole tree out of the ground (tick 12) and throws it; the tree comes apart where it lands. */
    UPROOT(6, 50, 26),
    /** Head down, it runs straight through everything for two and a half seconds, then stomps. */
    CHARGE(7, 72, 16),
    /** It leaps at you; where it lands the ground caves in. */
    LEAP(8, 80, 14),
    /** It shakes itself and rocks rain down from its body. */
    RUBBLE(9, 40, 16),
    /** The hand comes down (impact): whoever is under it is picked up, lifted to its face, and thrown a long way (tick 64). */
    GRAB(10, 96, 22),

    // ---- signature moves: each kind has its own ----
    /** Ice: a cone of frost from the mouth - slows, freezes, turns water to ice, leaves snow. */
    FROST_BREATH(11, 70, 20),
    /** Ice (and the Titan, in obsidian): a fist into the ground; a line of spikes erupts towards you. */
    ICE_SPIKES(12, 60, 24),
    /** Sandstone: arms wide, a wall of sand sweeps out - blinds and shoves. */
    SANDSTORM(13, 80, 20),
    /** Sandstone: a stamp; geysers of sand burst up under you. */
    SAND_GEYSER(14, 56, 22),
    /** Prismarine: both arms sweep forward - a wave of water races out in front. */
    TIDAL_WAVE(15, 70, 26),
    /** Prismarine (Titan: a beam of the void): a jet from the mouth that hoses you off your feet. */
    WATER_JET(16, 64, 18),
    /** Moss: a hand into the earth; roots burst up around you and hold you. */
    GRASPING_ROOTS(17, 76, 24),
    /** Moss (Titan: levitation and wither): it shakes and spore clouds spread. */
    SPORE_CLOUD(18, 56, 16),
    /** Stone, earth, Titan: it beats its chest at the sky and rocks rain down around you. */
    ROCKFALL(19, 70, 20),
    /** Stone, earth, Titan: both fists into the ground; a fissure tears towards you. */
    QUAKE(20, 60, 22);

    /** GRAB: the tick the held one is thrown. */
    public static final int GRAB_THROW = 64;

    public final int id;
    public final int duration;
    public final int impact;

    Attack(int id, int duration, int impact) {
        this.id = id;
        this.duration = duration;
        this.impact = impact;
    }

    public static int impactOf(Attack a) {
        return a.impact;
    }

    public static Attack byId(int id) {
        for (Attack a : values()) if (a.id == id) return a;
        return NONE;
    }
}
