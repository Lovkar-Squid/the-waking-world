package me.lovkar.wakingworld.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Nothing a colossus leaves behind is lost to the void: the items and experience it drops in the
 * End are placed on the nearest ground, and watched for a while - whatever still finds its way
 * over the edge is pulled back onto the island before the world can swallow it.
 */
public final class VoidGuard {
    private VoidGuard() {
    }

    private record Guarded(Entity entity, Vec3 safe, long until) {
    }

    private static final List<Guarded> WATCHED = new ArrayList<>();

    /** True when there is ground somewhere under a point (false over the void of the End). */
    public static boolean groundUnder(ServerLevel level, double x, double z) {
        BlockPos g = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
        return g.getY() > level.getMinBuildHeight() + 1;
    }

    /**
     * The nearest column with ground under it, searched in rings out to {@code radius} blocks (step
     * three), as the point one block above its surface - or {@code fallback} when there is none.
     */
    public static Vec3 nearestGround(ServerLevel level, Vec3 from, int radius, Vec3 fallback) {
        if (groundUnder(level, from.x, from.z)) {
            BlockPos g = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(from));
            return new Vec3(from.x, Math.max(g.getY(), Math.min(from.y, g.getY() + 2)), from.z);
        }
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 3; r <= radius && best == null; r += 3) {
            for (int dx = -r; dx <= r; dx += 3) {
                for (int dz = -r; dz <= r; dz += 3) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    double x = from.x + dx, z = from.z + dz;
                    if (!groundUnder(level, x, z)) continue;
                    double d = dx * dx + dz * dz;
                    if (d < bestD) {
                        BlockPos g = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
                        bestD = d;
                        best = new Vec3(Math.floor(x) + 0.5, g.getY(), Math.floor(z) + 0.5);
                    }
                }
            }
        }
        return best != null ? best : fallback;
    }

    /** Watches an entity for ten minutes: over the edge and falling, it is set back down at {@code safe}. */
    public static void watch(Entity entity, Vec3 safe) {
        WATCHED.add(new Guarded(entity, safe, entity.level().getGameTime() + 20 * 60 * 10));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (WATCHED.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        Iterator<Guarded> it = WATCHED.iterator();
        while (it.hasNext()) {
            Guarded g = it.next();
            Entity e = g.entity;
            if (e.isRemoved() || level.getGameTime() > g.until) {
                if (e.level() == level) it.remove();
                continue;
            }
            if (e.level() != level) continue;
            boolean falling = e.getY() < level.getMinBuildHeight() + 2
                    || (e.tickCount % 10 == 0 && e.getY() < g.safe.y - 24 && !groundUnder(level, e.getX(), e.getZ()));
            if (!falling) continue;
            e.teleportTo(g.safe.x, g.safe.y + 0.6, g.safe.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
            e.fallDistance = 0;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, g.safe.x, g.safe.y + 1, g.safe.z, 24, 0.4, 0.5, 0.4, 0.1);
            level.playSound(null, g.safe.x, g.safe.y, g.safe.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.8F, 1.4F);
        }
    }
}
