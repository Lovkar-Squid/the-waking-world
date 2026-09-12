package me.lovkar.wakingworld.kingdom;

import java.util.ArrayList;
import java.util.List;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.cataclysm.MeteorEntity;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

public final class KingdomSiege {
    private static final double RANGE = 2600.0;
    private static final int GAP = 10;
    private static final int PER_ENGINE = 4;
    private static final int MOST = 14;
    private static final float VOLLEY_CORES = 1.5F;
    private static final double FROM_HEIGHT = 74.0;
    private static final double SLANT = 58.0;
    public static final int HORN_COOLDOWN = 6000;
    private static final List<KingdomSiege.Volley> FLYING = new ArrayList<>();

    private KingdomSiege() {
    }

    public static boolean callFor(ServerLevel var0, ServerPlayer var1) {
        if (!WakingConfig.kingdoms()) {
            return false;
        } else if (var0.dimension() != Level.OVERWORLD) {
            return false;
        } else {
            ColossusEntity var2 = null;
            double var3 = 4900.0;

            for (ColossusEntity var6 : var0.getEntitiesOfClass(
                ColossusEntity.class, var1.getBoundingBox().inflate(70.0), var0x -> var0x.isAlive() && !var0x.isTitan() && !var0x.bombarded()
            )) {
                double var7 = var6.distanceToSqr(var1);
                if (var7 < var3) {
                    var3 = var7;
                    var2 = var6;
                }
            }

            if (var2 == null) {
                return false;
            } else {
                KingdomData var13 = KingdomData.get(var0);
                KingdomData.Kingdom var14 = null;
                double var15 = 6760000.0;

                for (KingdomData.Kingdom var10 : var13.all()) {
                    if (!var10.catapults.isEmpty() && !var13.isAngry(var0, var10.center, var1.getUUID())) {
                        double var11 = var10.center.distSqr(var2.blockPosition());
                        if (var11 < var15) {
                            var15 = var11;
                            var14 = var10;
                        }
                    }
                }

                if (var14 == null) {
                    return false;
                } else {
                    var2.setBombarded(true);
                    return throwFrom(var0, var14, var2, var2.position(), var1);
                }
            }
        }
    }

    public static boolean callAt(ServerLevel var0, ServerPlayer var1, Vec3 var2) {
        if (!WakingConfig.kingdoms()) {
            return false;
        } else if (var0.dimension() != Level.OVERWORLD) {
            return false;
        } else {
            KingdomData var3 = KingdomData.get(var0);
            BlockPos var4 = BlockPos.containing(var2);
            KingdomData.Kingdom var5 = null;
            double var6 = 6760000.0;

            for (KingdomData.Kingdom var9 : var3.all()) {
                if (!var9.catapults.isEmpty() && (var1 == null || !var3.isAngry(var0, var9.center, var1.getUUID()))) {
                    double var10 = var9.center.distSqr(var4);
                    if (var10 < var6) {
                        var6 = var10;
                        var5 = var9;
                    }
                }
            }

            if (var5 == null) {
                return false;
            } else {
                ColossusEntity var15 = null;
                double var16 = 676.0;

                for (ColossusEntity var12 : var0.getEntitiesOfClass(
                    ColossusEntity.class, new AABB(var2, var2).inflate(26.0), var0x -> var0x.isAlive() && !var0x.isTitan()
                )) {
                    double var13 = var12.distanceToSqr(var2);
                    if (var13 < var16) {
                        var16 = var13;
                        var15 = var12;
                    }
                }

                return throwFrom(var0, var5, var15, var2, var1);
            }
        }
    }

    private static boolean throwFrom(ServerLevel var0, KingdomData.Kingdom var1, ColossusEntity var2, Vec3 var3, ServerPlayer var4) {
        int var5 = Math.min(14, var1.catapults.size() * 4);
        if (var5 <= 0) {
            return false;
        } else if (me.lovkar.wakingworld.compat.Colonies.keepOff(var0, BlockPos.containing(var3), 24)) {
            // the engines will not fire on a colony's land, whoever asks and whatever stands there
            if (var4 != null) {
                var4.displayClientMessage(Component.translatable("kingdom.wakingworld.siege_colony").withStyle(ChatFormatting.GRAY), true);
            }
            WakingWorld.LOGGER.info("kingdom {}: will not fire on a colony's land at {} {} {}", Kingdoms.name(var1.center), (int)var3.x, (int)var3.y, (int)var3.z);
            return false;
        } else {
            Vec3 var6 = new Vec3((double)var1.center.getX() - var3.x, 0.0, (double)var1.center.getZ() - var3.z);
            BlockPos var7 = BlockPos.of(var1.catapults.iterator().next());
            FLYING.add(
                new KingdomSiege.Volley(
                    var0, var2 == null ? -1 : var2.getId(), var3, var6, var7, var5, var2 == null ? 0.0F : var2.coreHealth() * 1.5F / (float)var5
                )
            );
            double var8 = var4 != null ? var4.getX() : var3.x;
            double var10 = var4 != null ? var4.getZ() : var3.z;

            for (ServerPlayer var13 : var0.getPlayers(var4x -> var4x.distanceToSqr(var8, var4x.getY(), var10) < 25600.0)) {
                var13.sendSystemMessage(
                    Component.translatable("kingdom.wakingworld.bombard", new Object[]{Kingdoms.name(var1.center)}).withStyle(ChatFormatting.GOLD)
                );
            }

            var0.playSound(null, var1.center, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 6.0F, 0.7F);

            for (long var17 : var1.catapults) {
                BlockPos var15 = BlockPos.of(var17);
                Cataclysms.puff(
                    var0,
                    ParticleTypes.LARGE_SMOKE,
                    (double)var15.getX() + 0.5,
                    (double)var15.getY() + 4.0,
                    (double)var15.getZ() + 0.5,
                    30,
                    1.2,
                    0.8,
                    1.2,
                    0.05
                );
                Cataclysms.ring(var0, 14704698, 5.0F, (double)var15.getX() + 0.5, (double)var15.getY() + 1.2, (double)var15.getZ() + 0.5);
            }

            WakingWorld.LOGGER
                .info(
                    "kingdom {}: throws {} shot at {} {} {}{}",
                    new Object[]{
                        Kingdoms.name(var1.center), var5, (int)var3.x, (int)var3.y, (int)var3.z, var2 == null ? "" : " - a colossus is standing there"
                    }
                );
            return true;
        }
    }

    public static void tick(Post var0) {
        if (!FLYING.isEmpty() && var0.getLevel() instanceof ServerLevel var1) {
            FLYING.removeIf(var1x -> {
                if (var1x.level != var1) {
                    return false;
                } else if (--var1x.wait > 0) {
                    return false;
                } else {
                    ColossusEntity var10000;
                    label28: {
                        var1x.wait = 10;
                        if (var1.getEntity(var1x.giant) instanceof ColossusEntity var3 && var3.isAlive()) {
                            var10000 = var3;
                            break label28;
                        }

                        var10000 = null;
                    }

                    ColossusEntity var2 = var10000;
                    if (var1x.giant >= 0 && var2 == null) {
                        return true;
                    } else {
                        throwOne(var1, var2, var1x);
                        return --var1x.left <= 0;
                    }
                }
            });
        }
    }

    private static void throwOne(ServerLevel var0, ColossusEntity var1, KingdomSiege.Volley var2) {
        MeteorEntity var3 = (MeteorEntity)((EntityType)WakingWorld.METEOR.get()).create(var0);
        if (var3 != null) {
            var3.setSize(1);
            var3.setCarriesStar(false);
            Vec3 var4 = var1 != null ? var1.position() : var2.aim;
            Vec3 var5 = var4.add(var0.random.nextGaussian() * 2.5, 0.0, var0.random.nextGaussian() * 2.5);
            var3.aimFrom(var5, var2.bearing, 74.0, 58.0, 1.9);
            if (var1 != null) {
                var3.aimedAt(var1.getId(), var2.blow);
            }

            // the stone starts 58 blocks back towards the town and 74 up - past the edge of what a player
            // 160 blocks off has ticking; an entity in a chunk that is not ticking hangs in the air for ever
            Cataclysms.hold(var0, var3.position());
            Cataclysms.hold(var0, var5);
            var0.addFreshEntity(var3);
            Cataclysms.puff(
                var0,
                ParticleTypes.FLAME,
                (double)var2.engine.getX() + 0.5,
                (double)var2.engine.getY() + 4.5,
                (double)var2.engine.getZ() + 0.5,
                26,
                0.6,
                0.5,
                0.6,
                0.12
            );
            Cataclysms.puff(
                var0,
                ParticleTypes.LARGE_SMOKE,
                (double)var2.engine.getX() + 0.5,
                (double)var2.engine.getY() + 4.0,
                (double)var2.engine.getZ() + 0.5,
                18,
                0.7,
                0.4,
                0.7,
                0.04
            );
            var0.playSound(null, var2.engine, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 1.6F);
            Vec3 var6 = var3.position();
            Vec3 var7 = var3.getDeltaMovement().normalize().scale(3.0);

            for (int var8 = 1; var8 <= 6; var8++) {
                Vec3 var9 = var6.add(var7.scale((double)var8));
                Cataclysms.embers(var0, 14704698, 1.5F, var9.x, var9.y, var9.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private static final class Volley {
        final ServerLevel level;
        final int giant;
        final Vec3 aim;
        final Vec3 bearing;
        final BlockPos engine;
        final float blow;
        int left;
        int wait;

        Volley(ServerLevel var1, int var2, Vec3 var3, Vec3 var4, BlockPos var5, int var6, float var7) {
            this.level = var1;
            this.giant = var2;
            this.aim = var3;
            this.bearing = var4;
            this.engine = var5;
            this.left = var6;
            this.blow = var7;
        }
    }
}
