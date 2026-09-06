package me.lovkar.wakingworld.supporter;

import me.lovkar.wakingworld.body.ColossusStyle;
import me.lovkar.wakingworld.body.Palette;

import java.util.UUID;

/** Server side: what a supporter's rite changes about the giant it wakes - its clothes, nothing else. */
public final class SupporterCosmetics {
    private SupporterCosmetics() {
    }

    /** The colossus style a player's giants wear (NONE for everyone who is not a supporter, or chose none). */
    public static ColossusStyle colossusStyle(UUID player) {
        SupporterList.Entry e = SupporterList.entry(player);
        return e == null ? ColossusStyle.NONE : ColossusStyle.resolve(e.tier(), e.colossus());
    }

    /** The colour of a player's aura (0xRRGGBB), or -1 when they wear none - the Horn of Waking sounds in it. */
    public static int auraColor(UUID player) {
        SupporterList.Entry e = SupporterList.entry(player);
        if (e == null) return -1;
        AuraStyle a = AuraStyle.resolve(e.tier(), e.aura());
        return a == AuraStyle.NONE ? -1 : a.rgb;
    }

    /** waker / colossus / titan for a supporter, or null - the kingdom greets its wakers by it. */
    public static String tier(UUID player) {
        SupporterList.Entry e = SupporterList.entry(player);
        return e == null || SupporterTiers.rank(e.tier()) <= 0 ? null : e.tier().toLowerCase(java.util.Locale.ROOT);
    }

    private static final java.util.Map<UUID, Long> lastChange = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A player says they changed their look on the service: fetch the list again and tell every client to do
     * the same, so the change shows in seconds rather than at the next five-minute refresh. Once per player per
     * twenty seconds - the packet is cheap to send and the service is small.
     */
    public static void onChanged(net.minecraft.server.level.ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = lastChange.get(player.getUUID());
        if (last != null && now - last < 20_000L) return;
        lastChange.put(player.getUUID(), now);
        SupporterList.refreshAsync();
        me.lovkar.wakingworld.network.WakingNet.refreshCosmetics();
    }

    /** The palette a giant woken by this player is built from: the land's own, dressed in the player's style if they have one. */
    public static Palette dress(Palette palette, UUID waker) {
        if (waker == null || palette == null) return palette;
        ColossusStyle style = colossusStyle(waker);
        return style == ColossusStyle.NONE ? palette : Palette.styled(palette, style);
    }
}
