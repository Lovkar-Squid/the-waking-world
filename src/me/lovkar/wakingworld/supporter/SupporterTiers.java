package me.lovkar.wakingworld.supporter;

import java.util.Locale;

/** The Patreon tiers as ranks: waker = 1, colossus = 2, titan = 3; anything else 0 (no perks). A higher tier unlocks everything below it. */
public final class SupporterTiers {
    private SupporterTiers() {
    }

    public static int rank(String tier) {
        if (tier == null) return 0;
        return switch (tier.trim().toLowerCase(Locale.ROOT)) {
            case "waker" -> 1;
            case "colossus" -> 2;
            case "titan" -> 3;
            default -> 0;
        };
    }
}
