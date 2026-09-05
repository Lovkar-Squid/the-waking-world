package me.lovkar.wakingworld;

import net.minecraft.world.phys.Vec3;

/**
 * What common code may ask of the client without touching client classes: on a dedicated server
 * these stay the no-ops below, on a client WakingWorldClient installs the real thing (the camera
 * shake). Keeps client-only classes out of the server's class loader.
 */
public interface ClientHooks {
    ClientHooks NONE = new ClientHooks() {
    };

    /** A tremor felt at full strength at {@code at} (degrees of camera wobble), fading to nothing at {@code range} blocks. */
    default void shakeAt(Vec3 at, float strength, double range) {
    }

    /** A tremor right here, whatever the distance. */
    default void shake(float strength) {
    }

    /** A ground wave leaving {@code from} that will reach the camera a moment later. */
    default void wave(Vec3 from, double speed, double maxRadius, float strength) {
    }

    /** Opens the Waker's Almanac. */
    default void openAlmanac() {
    }

    /** Unfolds a Dead Letter. */
    default void openLetter(net.minecraft.world.item.ItemStack stack) {
    }

    /** An audience with a king. */
    default void openKing(me.lovkar.wakingworld.kingdom.KingEntity king) {
    }

    /** A trader's stall: their offers, in the kingdom's own screen. */
    default void openTrade(me.lovkar.wakingworld.kingdom.TownsfolkEntity trader, net.minecraft.world.item.trading.MerchantOffers offers) {
    }

    /** A run of the director begins: draw the world at this render distance until the cut. */
    default void cineSetup(int renderDistance) {
    }

    /** The director's camera path: the client becomes the camera. */
    default void cineStart(java.util.List<me.lovkar.wakingworld.story.Cinematics.Key> keys, int fadeIn, int fadeOut) {
    }

    /** Cut: the player is a player again. */
    default void cineStop() {
    }

    /** A piece of a letter's voice, or its status (see WakingNet.VoiceData). */
    default void voiceData(java.util.UUID id, int status, int index, int total, byte[] data) {
    }

    /** What the client knows of a letter's voice: "ready", "pending", or null when there is none (or no word yet). Asks if it has not. */
    default String voiceState(java.util.UUID id) {
        return null;
    }
}
