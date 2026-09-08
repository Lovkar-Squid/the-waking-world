package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.advancement.WakingTriggers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Who was still standing when it stopped.
 *
 * <p>A cataclysm that leaves no mark on the player's own record is a weather effect. This is the
 * one line at the end of each of them that says: you were there, and you are alive. Everything the
 * advancements know about the five comes through here.</p>
 *
 * <p>It deliberately keeps no register of who was present when the thing <em>began</em>. That
 * would be more exact and much worse: a cataclysm outlives a restart, a player logs out in the
 * middle of one, a server hiccups - and every one of those cases would quietly take the credit
 * away from somebody who did in fact live through it. Being near it at the end and alive is a
 * rule a player can understand, and it cannot be lost.</p>
 */
public final class Survived {
    /** Near enough to have been in it. The omen itself carries 380 blocks, so this is well inside. */
    public static final double NEAR = 220.0;

    private Survived() {
    }

    /** For the two that take the whole world: the blood moon, and a shower aimed at the players. */
    public static void everyone(ServerLevel level, Omen.Kind kind) {
        for (ServerPlayer p : level.players()) give(p, kind);
    }

    /** For the three that happen somewhere: the volcano, the tornado, the earthquake. */
    public static void near(ServerLevel level, Omen.Kind kind, Vec3 at) {
        near(level, kind, at, NEAR);
    }

    public static void near(ServerLevel level, Omen.Kind kind, Vec3 at, double radius) {
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) <= r2) give(p, kind);
        }
    }

    private static void give(ServerPlayer p, Omen.Kind kind) {
        if (p.isSpectator() || !p.isAlive()) return;
        WakingTriggers.SURVIVED.get().trigger(p, kind.key, 0);
    }
}
