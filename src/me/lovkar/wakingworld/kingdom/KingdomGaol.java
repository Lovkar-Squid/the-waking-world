package me.lovkar.wakingworld.kingdom;

import java.util.Iterator;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.item.PocketMageItem;
import me.lovkar.wakingworld.mage.MageEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;

public final class KingdomGaol {
    private static final int OFF_X = 6;
    private static final int OFF_Z = 18;
    private static final int DEPTH = 8;
    private static final int HALF = 2;
    private static final int TALL = 3;

    private KingdomGaol() {
    }

    public static BlockPos cell(KingdomData.Kingdom var0) {
        return new BlockPos(var0.center.getX() + 6, var0.center.getY() - 8, var0.center.getZ() + 18);
    }

    public static boolean take(ServerLevel var0, KingdomData.Kingdom var1, ServerPlayer var2, ItemStack var3) {
        BlockPos var4 = cell(var1);
        Iterator var5 = var0.getEntitiesOfClass(MageEntity.class, new AABB(var4).inflate(24.0), var0x -> var0x.isAlive() && var0x.kept() == 2).iterator();
        if (var5.hasNext()) {
            MageEntity var10 = (MageEntity)var5.next();
            var2.displayClientMessage(
                Component.translatable("kingdom.wakingworld.gaol.full", new Object[]{var10.mageName()}).withStyle(ChatFormatting.GRAY), true
            );
            return false;
        } else {
            KingdomBuild.Plan var9 = new KingdomBuild.Plan();
            dig(var0, var9, var4);
            KingdomBuild.begin(var0, var4, var9, "gaol");
            KingdomBuild.drain(var0, var9.size());
            MageEntity var6 = (MageEntity)((EntityType)WakingWorld.DARK_MAGE.get()).create(var0);
            if (var6 == null) {
                return false;
            } else {
                var6.moveTo((double)var4.getX() + 0.5, (double)(var4.getY() + 1), (double)var4.getZ() + 0.5, 180.0F, 0.0F);
                var6.assign(PocketMageItem.towerOf(var3));
                var6.cage(var4.above());
                var0.addFreshEntity(var6);
                PocketMageItem.unfold(var0, var4.above());
                var0.playSound(null, var4, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 3.0F, 0.6F);
                if (!var2.isCreative()) {
                    var3.shrink(1);
                }

                for (ServerPlayer var8 : var0.getPlayers(
                    var1x -> var1x.distanceToSqr((double)var1.center.getX() + 0.5, var1x.getY(), (double)var1.center.getZ() + 0.5) < 40000.0
                )) {
                    var8.sendSystemMessage(
                        Component.translatable("kingdom.wakingworld.gaol.taken", new Object[]{var6.mageName(), Kingdoms.name(var1.center)})
                            .withStyle(ChatFormatting.GOLD)
                    );
                }

                WakingWorld.LOGGER.info("kingdom {}: {} is in the cell at {}", new Object[]{Kingdoms.name(var1.center), var6.mageName(), var4.toShortString()});
                return true;
            }
        }
    }

    private static void dig(ServerLevel var0, KingdomBuild.Plan var1, BlockPos var2) {
        BlockState var3 = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState var4 = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        int var5 = var2.getY() + 3;

        for (int var6 = -3; var6 <= 3; var6++) {
            for (int var7 = -3; var7 <= 3; var7++) {
                int var8 = var2.getX() + var6;
                int var9 = var2.getZ() + var7;
                boolean var10 = Math.abs(var6) > 2 || Math.abs(var7) > 2;
                var1.set(var8, var2.getY(), var9, (var6 + var7) % 3 == 0 ? var4 : var3);

                for (int var11 = var2.getY() + 1; var11 <= var5; var11++) {
                    if (var10) {
                        boolean var12 = var7 == -3 && Math.abs(var6) <= 1 && var11 < var5;
                        var1.set(var8, var11, var9, var12 ? Blocks.IRON_BARS.defaultBlockState() : ((var8 + var11 + var9) % 4 == 0 ? var4 : var3));
                    } else {
                        var1.set(var8, var11, var9, Blocks.AIR.defaultBlockState());
                    }
                }

                var1.set(var8, var5 + 1, var9, var6 * var7 % 2 == 0 ? var3 : var4);
            }
        }

        int var13 = var2.getX();
        int var14 = var2.getZ() - 2 - 2;
        int var15 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var13, var14);

        for (int var16 = var2.getY(); var16 <= var15; var16++) {
            var1.set(var13, var16, var14, Blocks.AIR.defaultBlockState());
            var1.set(var13, var16, var14, (BlockState)Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));

            for (int var18 = -1; var18 <= 1; var18++) {
                for (int var20 = -1; var20 <= 1; var20++) {
                    if (var18 != 0 || var20 != 0) {
                        var1.set(var13 + var18, var16, var14 + var20, (var16 + var18 + var20) % 5 == 0 ? var4 : var3);
                    }
                }
            }
        }

        var1.set(var13, var15, var14, Blocks.AIR.defaultBlockState());
        var1.set(var13, var15 + 1, var14, Blocks.AIR.defaultBlockState());

        for (int var17 = -1; var17 <= 1; var17++) {
            for (int var19 = -1; var19 <= 1; var19++) {
                if (var17 != 0 || var19 != 0) {
                    var1.set(var13 + var17, var15, var14 + var19, Blocks.STONE_BRICK_SLAB.defaultBlockState());
                }
            }
        }

        var1.set(var2.getX() + 2, var2.getY() + 1, var2.getZ(), Blocks.STONE_BRICK_SLAB.defaultBlockState());
        var1.set(var2.getX() + 2, var2.getY() + 1, var2.getZ() + 1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        var1.set(var2.getX() - 2, var2.getY() + 1, var2.getZ() + 2, Blocks.HAY_BLOCK.defaultBlockState());
        var1.set(var2.getX(), var5, var2.getZ() + 2, (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }
}
