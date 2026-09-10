package me.lovkar.wakingworld;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;
import me.lovkar.wakingworld.kingdom.KingEntity;
import me.lovkar.wakingworld.kingdom.TownsfolkEntity;
import me.lovkar.wakingworld.mage.MageEntity;
import me.lovkar.wakingworld.story.Cinematics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;

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

    /** The moon has turned red, or it has not any more: the sky and the fog go with it. */
    default void landCard(String name, String lore, String kind, net.minecraft.core.BlockPos at) {
    }

    default void omen(int tint, int ticks) {
    }

    default void atlas(String lands) {
    }

    default void bloodMoon(boolean on) {
    }

    /** Opens the Waker's Almanac. */
    default void openAlmanac() {
    }


    /** Open the Wayfarer's Chart on this client. */
    default void openAtlas() {
    }

    /** Unfolds a Dead Letter. */
    default void openLetter(net.minecraft.world.item.ItemStack stack) {
    }

    /** An audience with a king. */
    default void openKing(me.lovkar.wakingworld.kingdom.KingEntity king) {
    }

    default void openMage(MageEntity var1) {
    }

    /** A trader's stall: their offers, in the kingdom's own screen. */
    default void openTrade(me.lovkar.wakingworld.kingdom.TownsfolkEntity trader, net.minecraft.world.item.trading.MerchantOffers offers) {
    }

    /** A run of the director begins: draw the world at this render distance until the cut. */
    default void cineSetup(int renderDistance) {
    }

    /** The director's camera path: the client becomes the camera. */
    default void cineStart(java.util.List<me.lovkar.wakingworld.story.Cinematics.Key> keys, int fadeIn, int fadeOut, boolean bossBar) {
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
