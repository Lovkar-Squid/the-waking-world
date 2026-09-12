package me.lovkar.wakingworld.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.ruin.FightRecord;
import me.lovkar.wakingworld.ruin.RuinLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class KingdomRepair {
    private static final int PER_SECOND = 45;
    private static final double INSIDE = 64.0;
    private static final int SLAB = 8;
    private static final int UP = 26;
    private static final int DOWN = 12;
    private static final int PATROL = 20;
    private static final int WORK_REACH = 8;
    private static final int WATCH_AFTER = 800;

    private KingdomRepair() {
    }

    private static boolean damaged(BlockState var0) {
        return var0.isAir()
            || var0.is(Blocks.LAVA)
            || var0.is(Blocks.FIRE)
            || var0.is(Blocks.SOUL_FIRE)
            || var0.is(Blocks.MAGMA_BLOCK)
            || var0.is(Blocks.OBSIDIAN);
    }

    public static void tick(ServerLevel var0) {
        KingdomData var1 = KingdomData.get(var0);
        if (!var1.all().isEmpty()) {
            for (KingdomData.Kingdom var3 : var1.all()) {
                List var4 = var0.getPlayers(
                    var1x -> var1x.distanceToSqr((double)var3.center.getX() + 0.5, var1x.getY(), (double)var3.center.getZ() + 0.5) < 25600.0
                );
                if (var4.isEmpty()) {
                    var3.rebuilding = false;
                } else {
                    work(var0, var1, var3, var4, 45);
                }
            }
        }
    }

    private static void sweepWork(ServerLevel var0, KingdomData.Kingdom var1, long var2) {
        if (!var1.works.isEmpty() || !var1.houses.isEmpty()) {
            ArrayList<Long> var4 = new ArrayList<>(var1.works);
            var4.addAll(var1.houses);
            BlockPos var5 = BlockPos.of((Long)var4.get((int)(var2 / 20L % (long)var4.size())));
            if (var0.isLoaded(var5)) {
                MutableBlockPos var6 = new MutableBlockPos();

                for (int var7 = -8; var7 <= 8; var7++) {
                    for (int var8 = -8; var8 <= 8; var8++) {
                        for (int var9 = -4; var9 <= 14; var9++) {
                            var6.set(var5.getX() + var7, var5.getY() + var9, var5.getZ() + var8);
                            BlockState var10 = var0.getBlockState(var6);
                            if (var10.is(Blocks.FIRE) || var10.is(Blocks.SOUL_FIRE) || var10.is(Blocks.LAVA)) {
                                var0.setBlock(var6, Blocks.AIR.defaultBlockState(), 3);
                                var1.watchUntil = var2 + 800L;
                            }
                        }
                    }
                }
            }
        }
    }

    public static void watch(ServerLevel var0) {
        KingdomData var1 = KingdomData.get(var0);
        if (!var1.all().isEmpty()) {
            long var2 = var0.getGameTime();
            MutableBlockPos var4 = new MutableBlockPos();

            for (KingdomData.Kingdom var6 : var1.all()) {
                boolean var7 = var6.rebuilding || var2 <= var6.watchUntil;
                if (var0.getNearestPlayer((double)var6.center.getX() + 0.5, (double)var6.center.getY(), (double)var6.center.getZ() + 0.5, 160.0, false) != null
                    && (var7 || var2 % 20L == 0L)) {
                    if (!var7) {
                        sweepWork(var0, var6, var2);
                    }

                    int var8 = var6.center.getX() - 64;
                    int var9 = var6.center.getX() + 64;
                    if (var6.watchX < var8 || var6.watchX > var9) {
                        var6.watchX = var8;
                    }

                    int var10 = Math.min(var6.watchX + 8, var9 + 1);
                    int var11 = 0;
                    int var12 = var6.center.getY() - 12;
                    int var13 = var6.center.getY() + 26;

                    for (int var14 = var6.watchX; var14 < var10; var14++) {
                        int var15 = (int)Math.sqrt(Math.max(0.0, 4096.0 - (double)(var14 - var6.center.getX()) * (double)(var14 - var6.center.getX())));

                        for (int var16 = var6.center.getZ() - var15; var16 <= var6.center.getZ() + var15; var16++) {
                            var4.set(var14, var6.center.getY(), var16);
                            if (var0.isLoaded(var4)) {
                                for (int var17 = var12; var17 <= var13; var17++) {
                                    var4.set(var14, var17, var16);
                                    BlockState var18 = var0.getBlockState(var4);
                                    if (var18.is(Blocks.FIRE) || var18.is(Blocks.SOUL_FIRE)) {
                                        var0.setBlock(var4, Blocks.AIR.defaultBlockState(), 3);
                                        var11++;
                                    } else if (var18.is(Blocks.LAVA)) {
                                        var0.setBlock(var4, Blocks.AIR.defaultBlockState(), 3);
                                        var11++;
                                    }
                                }
                            }
                        }
                    }

                    var6.watchX = var10 > var9 ? var8 : var10;
                    if (var11 > 0) {
                        if (var2 > var6.watchUntil) {
                            for (ServerPlayer var20 : var0.getPlayers(
                                var1x -> var1x.distanceToSqr((double)var6.center.getX() + 0.5, var1x.getY(), (double)var6.center.getZ() + 0.5) < 25600.0
                            )) {
                                var20.displayClientMessage(
                                    Component.translatable("kingdom.wakingworld.firewatch", new Object[]{Kingdoms.name(var6.center)})
                                        .withStyle(ChatFormatting.GOLD),
                                    false
                                );
                            }
                        }

                        var6.watchUntil = var2 + 800L;
                        if (var2 % 20L == 0L) {
                            var0.playSound(null, var6.center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.4F, 1.0F);
                        }
                    }
                }
            }
        }
    }

    public static int sweep(ServerLevel var0, KingdomData.Kingdom var1) {
        MutableBlockPos var2 = new MutableBlockPos();
        int var3 = 0;
        int var4 = var1.center.getY() - 12;
        int var5 = var1.center.getY() + 26;
        byte var6 = 64;

        for (int var7 = var1.center.getX() - var6; var7 <= var1.center.getX() + var6; var7++) {
            int var8 = (int)Math.sqrt(Math.max(0.0, 4096.0 - (double)(var7 - var1.center.getX()) * (double)(var7 - var1.center.getX())));

            for (int var9 = var1.center.getZ() - var8; var9 <= var1.center.getZ() + var8; var9++) {
                var2.set(var7, var1.center.getY(), var9);
                if (var0.isLoaded(var2)) {
                    for (int var10 = var4; var10 <= var5; var10++) {
                        var2.set(var7, var10, var9);
                        BlockState var11 = var0.getBlockState(var2);
                        if (var11.is(Blocks.FIRE) || var11.is(Blocks.SOUL_FIRE) || var11.is(Blocks.LAVA)) {
                            var0.setBlock(var2, Blocks.AIR.defaultBlockState(), 3);
                            var3++;
                        }
                    }
                }
            }
        }

        return var3;
    }

    public static void alarm(ServerLevel var0, KingdomData.Kingdom var1) {
        var1.watchUntil = Math.max(var1.watchUntil, var0.getGameTime() + 800L);
    }

    public static int work(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, List<ServerPlayer> var3, int var4) {
        RuinLedger var5 = RuinLedger.get(var0);
        int var6 = var4;
        boolean var7 = false;

        for (FightRecord var9 : var5.finishedNear(var2.center, 104.0)) {
            if (var6 <= 0) {
                break;
            }

            Map<BlockPos, BlockState> var10 = var9.takeWithin(var2.center, 64.0, 20, 38, var6, var0::isLoaded);
            if (!var10.isEmpty()) {
                for (Entry<BlockPos, BlockState> var12 : var10.entrySet()) {
                    BlockPos var13 = (BlockPos)var12.getKey();
                    if (damaged(var0.getBlockState(var13)) && !me.lovkar.wakingworld.compat.Colonies.keepOff(var0, var13)) {
                        var0.setBlock(var13, (BlockState)var12.getValue(), 3);
                        if ((var6 & 7) == 0) {
                            dust(var0, var13);
                        }

                        var6--;
                        var7 = true;
                    }
                }

                var5.setDirty();
            }
        }

        if (!var7) {
            var2.rebuilding = false;
            return 0;
        } else {
            if (!var2.rebuilding) {
                var2.rebuilding = true;
                alarm(var0, var2);

                for (ServerPlayer var15 : var3) {
                    var15.displayClientMessage(
                        Component.translatable("kingdom.wakingworld.rebuilding", new Object[]{Kingdoms.name(var2.center)}).withStyle(ChatFormatting.GRAY),
                        false
                    );
                }
            }

            if (var0.getGameTime() % 40L == 0L) {
                var0.playSound(null, var2.center, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.6F, 0.9F);
            }

            return var4 - var6;
        }
    }

    static void dust(ServerLevel var0, BlockPos var1) {
        Cataclysms.puff(var0, ParticleTypes.CLOUD, (double)var1.getX() + 0.5, (double)var1.getY() + 1.0, (double)var1.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.01);
    }
}
