package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Wakes a colossus out of the ground ahead of whoever called it - the same rite the summon command performs. */
public final class Waker {
    private Waker() {
    }

    /**
     * @param palette null = built from the land where it rises
     * @return the colossus, or null when nothing could be woken (already one awake nearby)
     */
    public static ColossusEntity wake(ServerLevel level, LivingEntity caller, Palette palette, int height) {
        Vec3 from = caller.position();
        Vec3 dir = Vec3.directionFromRotation(0.0F, caller.getYRot());
        double distance = height * 0.6 + 10.0;
        return wakeAt(level, caller, palette, height, from.add(dir.scale(distance)));
    }

    /** Wakes one at a given spot (the ground is found under it), facing the caller. */
    public static ColossusEntity wakeAt(ServerLevel level, LivingEntity caller, Palette palette, int height, Vec3 at) {
        return wakeAt(level, caller == null ? at.add(0, 0, -20) : caller.position(), palette, height, at);
    }

    /** Wakes one at a given spot, facing a point (no caller needed - the rites use this). */
    public static ColossusEntity wakeAt(ServerLevel level, Vec3 from, Palette palette, int height, Vec3 at) {
        // one at a time: a second horn blown at a living colossus only makes it angrier
        for (ColossusEntity other : level.getEntitiesOfClass(ColossusEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(160))) {
            if (other.isAlive()) return null;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        if (palette == null) palette = Palette.fromTerrain(level, ground, Math.max(10, height / 2));
        ColossusEntity colossus = WakingWorld.COLOSSUS.get().create(level);
        if (colossus == null) return null;
        colossus.setBodyParams(palette, level.random.nextInt(), height);
        float yaw = (float) (Math.toDegrees(Math.atan2(-(from.x - ground.getX()), from.z - ground.getZ())));
        colossus.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, yaw, 0.0F);
        colossus.yBodyRot = yaw;
        colossus.yHeadRot = yaw;
        colossus.setPersistenceRequired();
        colossus.setWake(colossus.wakeTotal());
        level.addFreshEntity(colossus);
        return colossus;
    }
}
