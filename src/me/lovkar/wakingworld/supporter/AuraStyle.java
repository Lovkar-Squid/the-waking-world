package me.lovkar.wakingworld.supporter;

import java.util.Locale;

/**
 * The supporter auras a patron can wear, and which Patreon tier unlocks each. The catalogue is
 * mirrored on the supporter service, which is the only place a choice is made and checked: the
 * mod never lets a client pick for itself, it only draws what the service says. Rank 1 is the
 * Waker tier, 2 the Colossus tier, 3 the Titan tier; a higher tier may wear anything below it.
 */
public enum AuraStyle {
    NONE("none", 0, 0x000000, Pattern.NONE),
    /** Waker: soft green runes rising round the feet. */
    WAKER("waker", 1, 0x5CFF3C, Pattern.RUNES),
    /** Colossus: the orbiting sigil in the tier's ember-orange, plus the six lands' sigils. */
    COLOSSUS("colossus", 2, 0xFF9628, Pattern.SIGIL),
    STONE("stone", 2, 0xD8CFC0, Pattern.SIGIL),
    EARTH("earth", 2, 0xB8843C, Pattern.SIGIL),
    SANDSTONE("sandstone", 2, 0xF2D27A, Pattern.SIGIL),
    ICE("ice", 2, 0xA6E6FF, Pattern.SIGIL),
    PRISMARINE("prismarine", 2, 0x62D8C8, Pattern.SIGIL),
    MOSS("moss", 2, 0x8CE664, Pattern.SIGIL),
    /** Titan: the void sigil with embers and a double pulse, or the crown - a halo of gold glyphs. */
    TITAN("titan", 3, 0xB266FF, Pattern.VOID),
    CROWN("crown", 3, 0xFFD86A, Pattern.CROWN);

    public enum Pattern { NONE, RUNES, SIGIL, VOID, CROWN }

    public final String id;
    public final int rank;
    public final int rgb;
    public final Pattern pattern;

    AuraStyle(String id, int rank, int rgb, Pattern pattern) {
        this.id = id;
        this.rank = rank;
        this.rgb = rgb;
        this.pattern = pattern;
    }

    /** waker = 1, colossus = 2, titan = 3; anything else 0 (no perks). */
    public static int tierRank(String tier) {
        return SupporterTiers.rank(tier);
    }

    public static AuraStyle byId(String id) {
        if (id == null) return null;
        String k = id.trim().toLowerCase(Locale.ROOT);
        for (AuraStyle s : values()) if (s.id.equals(k)) return s;
        return null;
    }

    /** Each tier's own aura - what a supporter wears until they choose. */
    public static AuraStyle defaultFor(int rank) {
        return rank >= 3 ? TITAN : rank == 2 ? COLOSSUS : rank == 1 ? WAKER : NONE;
    }

    /**
     * The aura a supporter actually shows: the one the service recorded for them if their tier
     * unlocks it (the service checks the same thing), otherwise their tier's own. No tier, no aura.
     */
    public static AuraStyle resolve(String tier, String chosen) {
        int rank = tierRank(tier);
        if (rank <= 0) return NONE;
        AuraStyle s = byId(chosen);
        if (s != null && s.rank <= rank) return s;
        return defaultFor(rank);
    }
}
