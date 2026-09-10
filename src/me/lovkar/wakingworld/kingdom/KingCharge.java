package me.lovkar.wakingworld.kingdom;

import java.util.List;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.item.WakingItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public final class KingCharge {
    private static final int LEVY_STANDING = 22;
    private static final int MAGE_STANDING = 52;
    public static final double TOWER_RANGE = 700.0;
    private static final List<KingCharge.Ask> ASKS = List.of(
        new KingCharge.Ask(Items.OAK_LOG, 48),
        new KingCharge.Ask(Items.STONE_BRICKS, 64),
        new KingCharge.Ask(Items.WHEAT, 48),
        new KingCharge.Ask(Items.IRON_INGOT, 18),
        new KingCharge.Ask(Items.COAL, 40),
        new KingCharge.Ask(Items.GLASS, 32),
        new KingCharge.Ask(Items.LEATHER, 20),
        new KingCharge.Ask(Items.COPPER_INGOT, 32),
        new KingCharge.Ask(Items.BREAD, 32),
        new KingCharge.Ask(Items.GOLD_INGOT, 12),
        new KingCharge.Ask(Items.WHITE_WOOL, 32),
        new KingCharge.Ask(Items.BRICK, 40)
    );

    private KingCharge() {
    }

    public static void open(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2) {
        if (var2.chargeKind.isEmpty()) {
            if (var2.knownTower != 0L) {
                var2.chargeKind = "mage";
                var2.chargeAt = var2.knownTower;
                var2.chargeItem = "";
                var2.chargeCount = 0;
                var2.chargeGot = 0;
                var1.setDirty();
            } else {
                long var3 = var2.center.asLong() * -7046029254386353131L + (long)var2.chargesPaid * 1315423911L;
                var3 ^= var3 >>> 29;
                KingCharge.Ask var5 = ASKS.get(Math.floorMod(var3, ASKS.size()));
                var2.chargeKind = "levy";
                var2.chargeItem = BuiltInRegistries.ITEM.getKey(var5.item()).toString();
                var2.chargeCount = (int)Math.round((double)var5.base() * (1.0 + (double)(var2.tier - 1) * 0.55 + (double)var2.chargesPaid * 0.12));
                var2.chargeGot = 0;
                var2.chargeAt = 0L;
                var1.setDirty();
            }
        }
    }

    public static Item item(KingdomData.Kingdom var0) {
        if ("levy".equals(var0.chargeKind) && !var0.chargeItem.isEmpty()) {
            ResourceLocation var1 = ResourceLocation.tryParse(var0.chargeItem);
            return var1 == null ? null : (Item)BuiltInRegistries.ITEM.get(var1);
        } else {
            return null;
        }
    }

    public static String describe(ServerLevel var0, KingdomData.Kingdom var1) {
        return var1.chargeKind.isEmpty() ? "" : var1.chargeKind + ";" + var1.chargeItem + ";" + var1.chargeCount + ";" + var1.chargeGot;
    }

    public static boolean deliver(ServerLevel var0, KingdomData.Kingdom var1, ServerPlayer var2, ItemStack var3) {
        Item var4 = item(var1);
        if (var4 != null && var3.is(var4)) {
            int var5 = var1.chargeCount - var1.chargeGot;
            if (var5 <= 0) {
                return false;
            } else {
                int var6 = Math.min(var5, var3.getCount());
                if (!var2.isCreative()) {
                    var3.shrink(var6);
                }

                var1.chargeGot += var6;
                KingdomData var7 = KingdomData.get(var0);
                var7.setDirty();
                var0.playSound(null, var2.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1.2F, 0.9F);
                if (var1.chargeGot >= var1.chargeCount) {
                    paid(var0, var7, var1, var2, 22, "levy");
                } else {
                    var2.displayClientMessage(
                        Component.translatable("charge.wakingworld.progress", new Object[]{var1.chargeGot, var1.chargeCount, var4.getDescription()})
                            .withStyle(ChatFormatting.GOLD),
                        true
                    );
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public static void mageSlain(ServerLevel var0, BlockPos var1, ServerPlayer var2) {
        KingdomData var3 = KingdomData.get(var0);

        for (KingdomData.Kingdom var5 : var3.all()) {
            if ("mage".equals(var5.chargeKind) && var5.chargeAt == var1.asLong()) {
                paid(var0, var3, var5, var2, 52, "mage");
            }
        }

        for (KingdomData.Kingdom var7 : var3.all()) {
            if (var7.knownTower == var1.asLong()) {
                var7.knownTower = 0L;
                var3.setDirty();
            }
        }
    }

    private static void paid(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, ServerPlayer var3, int var4, String var5) {
        var2.chargeKind = "";
        var2.chargeItem = "";
        var2.chargeCount = 0;
        var2.chargeGot = 0;
        var2.chargeAt = 0L;
        var2.chargesPaid++;
        var1.setDirty();
        var1.moveStanding(var2.center, var4);
        List<ServerPlayer> var6 = var0.getPlayers(var1x -> var1x.distanceToSqr((double)var2.center.getX() + 0.5, var1x.getY(), (double)var2.center.getZ() + 0.5) < 48400.0);

        for (ServerPlayer var8 : var6) {
            var8.sendSystemMessage(
                Component.translatable("charge.wakingworld.paid." + var5, new Object[]{Kingdoms.name(var2.center)}).withStyle(ChatFormatting.GOLD)
            );
        }

        var0.playSound(null, var2.center, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 2.0F, 1.0F);
        if (var3 != null) {
            int var9 = 8 + var2.tier * 5 + ("mage".equals(var5) ? 16 : 0);
            give(var3, new ItemStack(Items.EMERALD, var9));
            var3.giveExperiencePoints(60 + var2.tier * 30);
            if ("mage".equals(var5)) {
                give(var3, new ItemStack((ItemLike)WakingItems.SLEEPERS_EMBER.get(), 1));
            }
        }

        KingdomGrowth.review(var0, var1, var2, var6);
        var2.reviewedAt = var0.getGameTime();
        var1.setDirty();
        WakingWorld.LOGGER
            .info("kingdom {}: a {} charge was paid; standing {} tier {}", new Object[]{Kingdoms.name(var2.center), var5, var2.standing, var2.tier});
    }

    private static void give(ServerPlayer var0, ItemStack var1) {
        if (!var0.addItem(var1)) {
            var0.drop(var1, false);
        }
    }

    public static void towerFound(ServerLevel var0, BlockPos var1) {
        KingdomData var2 = KingdomData.get(var0);
        KingdomData.Kingdom var3 = null;
        double var4 = 490000.0;

        for (KingdomData.Kingdom var7 : var2.all()) {
            double var8 = var7.center.distSqr(var1);
            if (var8 < var4) {
                var4 = var8;
                var3 = var7;
            }
        }

        if (var3 != null && var3.knownTower != var1.asLong()) {
            var3.knownTower = var1.asLong();
            var2.setDirty();
        }
    }

    private static record Ask(Item item, int base) {
    }
}
