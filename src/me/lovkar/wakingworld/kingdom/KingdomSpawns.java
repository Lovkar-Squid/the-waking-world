package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    public static int reinforce(ServerLevel var0, BlockPos var1, int var2) {
        int var3 = 4 + var2 * 4;
        int var4 = var0.getEntitiesOfClass(GuardEntity.class, new AABB(var1).inflate(68.0)).size();
        int var5 = Math.min(6, var3 - var4);
        if (var5 <= 0) {
            return 0;
        } else {
            int var6 = 0;

            for (int var7 = 0; var7 < var5 * 4 && var6 < var5; var7++) {
                double var8 = var0.random.nextDouble() * Math.PI * 2.0;
                boolean var10 = var6 % 3 == 0;
                double var11 = var10 ? 56.0 : (double)(46 - var0.random.nextInt(22));
                int var13 = var1.getX() + (int)Math.round(Math.cos(var8) * var11);
                int var14 = var1.getZ() + (int)Math.round(Math.sin(var8) * var11);
                BlockPos var15 = var0.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(var13, var1.getY(), var14));
                if (Math.abs(var15.getY() - var1.getY()) <= 14) {
                    GuardEntity var16 = (GuardEntity)((EntityType)WakingWorld.GUARD.get()).create(var0);
                    if (var16 != null
                        && var0.noCollision(
                            var16,
                            var16.getBoundingBox()
                                .move((double)var13 + 0.5 - var16.getX(), (double)var15.getY() - var16.getY(), (double)var14 + 0.5 - var16.getZ())
                        )) {
                        var16.moveTo((double)var13 + 0.5, (double)var15.getY(), (double)var14 + 0.5, var0.random.nextFloat() * 360.0F, 0.0F);
                        int var17 = var10 ? 0 : (var0.random.nextBoolean() ? 1 : 2);
                        var16.assign(var1, var15, var17, var0.random);
                        var16.setPostRadius(var17 == 0 ? 4 : 9);
                        var0.addFreshEntity(var16);
                        var6++;
                    }
                }
            }

            return var6;
        }
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
