package me.lovkar.wakingworld.kingdom;

import java.util.ArrayList;
import java.util.List;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.land.Lands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class KingdomGrowth {
    private static final int REVIEW = 24000;
    private static final int TRADE_GAIN = 1;
    private static final int TRADE_CEILING = 60;
    private static final int REACH_CELLS = 3;

    private KingdomGrowth() {
    }

    public static void favour(ServerLevel var0, BlockPos var1, int var2, String var3) {
        if (var2 != 0) {
            KingdomData var4 = KingdomData.get(var0);
            KingdomData.Kingdom var5 = var4.kingdom(var1);
            if (var2 <= 0 || !var3.equals("trade") || var5.standing < 60) {
                var4.moveStanding(var1, var2);
            }
        }
    }

    public static void traded(ServerLevel var0, BlockPos var1) {
        favour(var0, var1, 1, "trade");
    }

    public static void countryside(ServerLevel var0, BlockPos var1, int var2, String var3) {
        KingdomData var4 = KingdomData.get(var0);
        KingdomData.Kingdom var5 = holderOf(var0, var4, var1);
        if (var5 != null) {
            favour(var0, var5.center, var2, var3);
        } else {
            KingdomData.Kingdom var6 = null;
            double var7 = Double.MAX_VALUE;

            for (KingdomData.Kingdom var10 : var4.all()) {
                double var11 = var10.center.distSqr(var1);
                if (var11 < var7) {
                    var7 = var11;
                    var6 = var10;
                }
            }

            if (var6 != null && var7 < 160000.0) {
                favour(var0, var6.center, var2, var3);
            }
        }
    }

    public static KingdomData.Kingdom holderOf(ServerLevel var0, KingdomData var1, BlockPos var2) {
        if (!WakingConfig.namedLands()) {
            return null;
        } else {
            Lands var3 = Lands.get(var0);
            Lands.Land var4 = var3.landAtCell(Lands.cellOf(var2.getX()), Lands.cellOf(var2.getZ()));
            if (var4 == null) {
                return null;
            } else {
                for (KingdomData.Kingdom var6 : var1.all()) {
                    if (var6.claims.contains(var4.cell())) {
                        return var6;
                    }
                }

                return null;
            }
        }
    }

    public static void tick(ServerLevel var0) {
        KingdomData var1 = KingdomData.get(var0);
        long var2 = var0.getGameTime();

        for (KingdomData.Kingdom var5 : var1.all()) {
            if (var5.reviewedAt < 0L || var2 - var5.reviewedAt >= 24000L) {
                List var6 = var0.getPlayers(
                    var1x -> var1x.distanceToSqr((double)var5.center.getX() + 0.5, var1x.getY(), (double)var5.center.getZ() + 0.5) < 48400.0
                );
                if (!var6.isEmpty()) {
                    var5.reviewedAt = var2;
                    var1.setDirty();
                    review(var0, var1, var5, var6);
                    if (var5.dressed < var5.tier) {
                        KingdomDressing.begin(var0, var5.center, var5.tier);
                    }
                }
            }
        }
    }

    public static void review(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, List<ServerPlayer> var3) {
        int var4 = var2.tier;
        int var5 = KingdomData.tierFor(var2.standing, var4);
        if (var5 != var4) {
            var2.tier = var5;
            var1.setDirty();
            String var6 = var5 > var4 ? "kingdom.wakingworld.grew" : "kingdom.wakingworld.shrank";

            for (ServerPlayer var8 : var3) {
                var8.displayClientMessage(
                    Component.translatable(var6, new Object[]{Kingdoms.name(var2.center), Component.translatable("kingdom.wakingworld.tier." + var5)})
                        .withStyle(var5 > var4 ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                    false
                );
            }

            var0.playSound(
                null, var2.center, var5 > var4 ? SoundEvents.BELL_BLOCK : SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 3.0F, var5 > var4 ? 1.0F : 0.7F
            );
            if (var5 > var4) {
                int var10 = KingdomSpawns.reinforce(var0, var2.center, var5);
                if (var10 > 0) {
                    WakingWorld.LOGGER.info("kingdom {}: took on {} more of the watch", Kingdoms.name(var2.center), var10);
                }
            }
        }

        if (var2.dressed < var2.tier) {
            KingdomDressing.begin(var0, var2.center, var2.tier);
        }

        claims(var0, var1, var2, var3);
        boolean var9 = KingdomExpansion.works(var0, var1, var2, var3);
        if (!var9) {
            KingdomExpansion.march(var0, var1, var2, var3);
        }
    }

    public static void claims(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, List<ServerPlayer> var3) {
        if (WakingConfig.namedLands()) {
            Lands var4 = Lands.get(var0);
            int var5 = var2.tier;
            Lands.Land var6 = var4.landAtCell(Lands.cellOf(var2.center.getX()), Lands.cellOf(var2.center.getZ()));
            if (var6 != null && !var2.claims.contains(var6.cell()) && !heldByAnother(var1, var2, var6.cell())) {
                take(var0, var1, var2, var6, var3);
            }

            while (var2.claims.size() < var5) {
                Lands.Land var7 = null;
                double var8 = Double.MAX_VALUE;

                for (Lands.Land var11 : var4.named()) {
                    if (!var2.claims.contains(var11.cell()) && !heldByAnother(var1, var2, var11.cell())) {
                        double var12 = distance(var2.center, var11);
                        if (!(var12 > 3.0 * (double)WakingConfig.landSize()) && var12 < var8) {
                            var8 = var12;
                            var7 = var11;
                        }
                    }
                }

                if (var7 == null) {
                    break;
                }

                take(var0, var1, var2, var7, var3);
            }

            while (var2.claims.size() > var5) {
                long var17 = -1L;
                double var9 = -1.0;

                for (long var20 : var2.claims) {
                    Lands.Land var14 = var4.byCell(var20);
                    double var15 = var14 == null ? Double.MAX_VALUE : distance(var2.center, var14);
                    if (var15 > var9) {
                        var9 = var15;
                        var17 = var20;
                    }
                }

                if (var17 == -1L) {
                    break;
                }

                Lands.Land var19 = var4.byCell(var17);
                var2.claims.remove(var17);
                var1.setDirty();
                if (var19 != null) {
                    for (ServerPlayer var13 : var3) {
                        var13.displayClientMessage(
                            Component.translatable("kingdom.wakingworld.released", new Object[]{var19.name(), Kingdoms.name(var2.center)})
                                .withStyle(ChatFormatting.GRAY),
                            false
                        );
                    }
                }
            }
        }
    }

    private static void take(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, Lands.Land var3, List<ServerPlayer> var4) {
        var2.claims.add(var3.cell());
        var1.setDirty();

        for (ServerPlayer var6 : var4) {
            var6.displayClientMessage(
                Component.translatable("kingdom.wakingworld.claimed", new Object[]{var3.name(), Kingdoms.name(var2.center)}).withStyle(ChatFormatting.GOLD),
                false
            );
        }
    }

    private static boolean heldByAnother(KingdomData var0, KingdomData.Kingdom var1, long var2) {
        for (KingdomData.Kingdom var5 : var0.all()) {
            if (var5 != var1 && var5.claims.contains(var2)) {
                return true;
            }
        }

        return false;
    }

    private static double distance(BlockPos var0, Lands.Land var1) {
        int var2 = WakingConfig.landSize();
        double var3 = (double)var1.cellX() * (double)var2 + (double)var2 / 2.0;
        double var5 = (double)var1.cellZ() * (double)var2 + (double)var2 / 2.0;
        return Math.sqrt((var3 - (double)var0.getX()) * (var3 - (double)var0.getX()) + (var5 - (double)var0.getZ()) * (var5 - (double)var0.getZ()));
    }

    public static List<String> heldNames(ServerLevel var0, KingdomData.Kingdom var1) {
        ArrayList var2 = new ArrayList();
        if (!WakingConfig.namedLands()) {
            return var2;
        } else {
            Lands var3 = Lands.get(var0);

            for (long var5 : var1.claims) {
                Lands.Land var7 = var3.byCell(var5);
                if (var7 != null) {
                    var2.add(var7.name());
                }
            }

            return var2;
        }
    }
}
