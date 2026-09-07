package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.network.WakingNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The minutes before it happens.
 *
 * <p>A cataclysm that simply begins is a surprise, and a surprise is over as soon as it lands. What
 * makes one frightening is knowing it is coming and not being able to do anything about it - so
 * every one of them now opens with an omen: a low note out of the ground, the light going wrong, the
 * animals leaving, and a line in the chat that says what is wrong without saying what is coming.</p>
 *
 * <p>Everything here is a signal rather than an effect. Nothing is destroyed, nobody is hurt, and a
 * player who takes the hint has time to be somewhere else.</p>
 */
public final class Omen {
    /** What is about to happen. The wording and the colour of the sky differ; the shape does not. */
    public enum Kind {
        VOLCANO("volcano", 0xB4432A),
        TORNADO("tornado", 0x5A6472),
        EARTHQUAKE("earthquake", 0x7A6A4E),
        METEOR("meteor", 0x6E4E86),
        BLOOD_MOON("bloodmoon", 0x8E0E14);

        public final String key;
        public final int tint;

        Kind(String key, int tint) {
            this.key = key;
            this.tint = tint;
        }
    }

    private Omen() {
    }

    /**
     * Sound the warning. {@code seconds} is how long the players have; it is passed to the client so
     * the light can come on and go off again in step with it rather than guessing.
     */
    public static void begin(ServerLevel level, Vec3 at, Kind kind, int seconds) {
        if (!WakingConfig.omens()) return;
        WakingWorld.LOGGER.info("cataclysm: an omen of {} at {} {} {}, {} s",
                kind.key, (int) at.x, (int) at.y, (int) at.z, seconds);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 380 * 380) continue;
            WakingNet.omen(p, kind.tint, seconds * 20);
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.omen." + kind.key)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        note(level, at);
        flee(level, at, kind);
    }

    /** The note itself: one low bell out of the ground, heard a long way off. */
    public static void note(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, WakingSounds.OMEN.get(), SoundSource.WEATHER, 9.0F, 0.9F);
    }

    /**
     * Every second of the warning, so the omen is a state and not a single moment: the ground smokes
     * where it is about to open, and the birds are not singing.
     */
    public static void tick(ServerLevel level, Vec3 at, Kind kind, int left) {
        if (!WakingConfig.omens()) return;
        if (left % 60 == 0) note(level, at);
        double r = 26;
        for (int i = 0; i < 6; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = level.random.nextDouble() * r;
            double x = at.x + Math.cos(a) * d, z = at.z + Math.sin(a) * d;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) x, (int) z);
            if (top <= level.getMinBuildHeight() + 1) continue;
            Cataclysms.puff(level, kind == Kind.VOLCANO ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.SMOKE,
                    x, top + 0.4, z, 2, 0.4, 0.2, 0.4, 0.015);
        }
    }

    /**
     * The animals go. They know before anybody else does - which is the oldest warning there is, and
     * the one a player is most likely to notice without being told.
     *
     * <p>They are pushed away from the site and given somewhere to be, not teleported: a field of
     * cows all facing the same way and moving is a sight, and a field of cows that vanished is a
     * bug report.</p>
     */
    private static void flee(ServerLevel level, Vec3 at, Kind kind) {
        AABB box = new AABB(at, at).inflate(90);
        int moved = 0;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, box, Entity::isAlive)) {
            Vec3 away = animal.position().subtract(at);
            if (away.lengthSqr() < 1) away = new Vec3(1, 0, 0);
            away = away.normalize();
            animal.setDeltaMovement(animal.getDeltaMovement().add(away.x * 0.42, 0.22, away.z * 0.42));
            animal.hurtMarked = true;
            animal.getNavigation().moveTo(animal.getX() + away.x * 40, animal.getY(), animal.getZ() + away.z * 40, 1.6);
            moved++;
        }
        if (moved > 0) WakingWorld.LOGGER.info("cataclysm: {} animals left before the {}", moved, kind.key);
    }

    /** Where the omen should be centred for a cataclysm that has not picked its spot yet. */
    public static Vec3 near(ServerPlayer p, double distance) {
        double a = p.level().random.nextDouble() * Math.PI * 2;
        BlockPos g = Cataclysms.surface(p.serverLevel(), p.getX() + Math.cos(a) * distance, p.getZ() + Math.sin(a) * distance);
        return Vec3.atBottomCenterOf(g);
    }
}
