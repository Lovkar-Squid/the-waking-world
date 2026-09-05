package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;

/** The kingdom's people, put into the world by its structure pieces - in the column being generated, never beyond it. */
public final class KingdomSpawns {
    private KingdomSpawns() {
    }

    public static void guard(WorldGenLevel level, int cx, int cy, int cz, int x, int y, int z, int kind, int radius) {
        GuardEntity g = WakingWorld.GUARD.get().create(level.getLevel());
        if (g == null) return;
        g.moveTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360f, 0);
        g.assign(new BlockPos(cx, cy, cz), new BlockPos(x, y, z), kind, level.getRandom());
        g.setPostRadius(radius);
        level.addFreshEntityWithPassengers(g);
    }

    public static void trader(WorldGenLevel level, int cx, int cy, int cz, int x, int y, int z, int profession) {
        TownsfolkEntity t = WakingWorld.TOWNSFOLK.get().create(level.getLevel());
        if (t == null) return;
        t.moveTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360f, 0);
        t.assign(new BlockPos(cx, cy, cz), new BlockPos(x, y, z), profession);
        level.addFreshEntityWithPassengers(t);
    }

    public static void king(WorldGenLevel level, int cx, int cy, int cz, double x, double y, double z) {
        KingEntity k = WakingWorld.KING.get().create(level.getLevel());
        if (k == null) return;
        k.moveTo(x, y, z, 180f, 0); // the throne faces south, down the hall
        k.setYBodyRot(180f);
        k.setYHeadRot(180f);
        k.assign(new BlockPos(cx, cy, cz));
        level.addFreshEntityWithPassengers(k);
        if (level instanceof net.minecraft.server.level.WorldGenRegion region) KingdomData.get(region.getLevel()).setThrone(new BlockPos(cx, cy, cz), new net.minecraft.world.phys.Vec3(x, y, z));
    }
}
