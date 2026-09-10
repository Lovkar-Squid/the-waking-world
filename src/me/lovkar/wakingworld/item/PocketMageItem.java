package me.lovkar.wakingworld.item;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.mage.MageEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.AABB;

public class PocketMageItem extends Item {
    private static final String NAME = "MageName";
    private static final String TOWER = "MageTower";

    public PocketMageItem(Properties var1) {
        super(var1);
    }

    public static ItemStack of(String var0, BlockPos var1) {
        ItemStack var2 = new ItemStack((ItemLike)WakingItems.POCKET_MAGE.get());
        CompoundTag var3 = new CompoundTag();
        var3.putString("MageName", var0 == null ? "" : var0);
        var3.putLong("MageTower", var1 == null ? 0L : var1.asLong());
        var2.set(DataComponents.CUSTOM_DATA, CustomData.of(var3));
        return var2;
    }

    public static String nameOf(ItemStack var0) {
        CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
        String var2 = var1 == null ? "" : var1.copyTag().getString("MageName");
        return var2.isEmpty() ? "The mage" : var2;
    }

    public static BlockPos towerOf(ItemStack var0) {
        CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
        return var1 == null ? BlockPos.ZERO : BlockPos.of(var1.copyTag().getLong("MageTower"));
    }

    public InteractionResult useOn(UseOnContext var1) {
        if (var1.getLevel() instanceof ServerLevel var2) {
            if (var1.getPlayer() instanceof ServerPlayer var8) {
                ItemStack var9 = var1.getItemInHand();
                MageEntity var5 = servantOf(var2, var8.getUUID());
                if (var5 != null) {
                    var8.displayClientMessage(
                        Component.translatable("item.wakingworld.pocket_mage.one_only", new Object[]{var5.mageName()}).withStyle(ChatFormatting.GRAY), true
                    );
                    return InteractionResult.FAIL;
                } else {
                    BlockPos var6 = var1.getClickedPos().relative(var1.getClickedFace());
                    MageEntity var7 = (MageEntity)((EntityType)WakingWorld.DARK_MAGE.get()).create(var2);
                    if (var7 == null) {
                        return InteractionResult.FAIL;
                    } else {
                        var7.moveTo((double)var6.getX() + 0.5, (double)var6.getY(), (double)var6.getZ() + 0.5, var8.getYRot() + 180.0F, 0.0F);
                        var7.assign(towerOf(var9));
                        var7.keep(var8.getUUID(), var6);
                        var2.addFreshEntity(var7);
                        unfold(var2, var6);
                        if (!var8.isCreative()) {
                            var9.shrink(1);
                        }

                        var8.displayClientMessage(
                            Component.translatable("item.wakingworld.pocket_mage.out", new Object[]{var7.mageName()}).withStyle(ChatFormatting.LIGHT_PURPLE),
                            true
                        );
                        return InteractionResult.CONSUME;
                    }
                }
            } else {
                return InteractionResult.PASS;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    public static MageEntity servantOf(ServerLevel var0, UUID var1) {
        Iterator var2 = var0.getEntitiesOfClass(
                MageEntity.class,
                new AABB(-3.0E7, (double)var0.getMinBuildHeight(), -3.0E7, 3.0E7, (double)var0.getMaxBuildHeight(), 3.0E7),
                var1x -> var1x.isAlive() && var1x.kept() == 1 && var1.equals(var1x.owner())
            )
            .iterator();
        return var2.hasNext() ? (MageEntity)var2.next() : null;
    }

    public static void unfold(ServerLevel var0, BlockPos var1) {
        double var2 = (double)var1.getX() + 0.5;
        double var4 = (double)var1.getY();
        double var6 = (double)var1.getZ() + 0.5;
        var0.playSound(null, var1, SoundEvents.BOTTLE_EMPTY, SoundSource.NEUTRAL, 2.0F, 0.8F);
        var0.playSound(null, var1, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 2.0F, 0.7F);
        Cataclysms.ring(var0, MageEntity.stageColour(1), 4.5F, var2, var4 + 0.12, var6, 0.7);
        Cataclysms.runes(var0, MageEntity.stageColour(1), 1.4F, var2, var4 + 1.0, var6, 34, 0.5, 0.9, 0.5);
        Cataclysms.puff(var0, ParticleTypes.SOUL_FIRE_FLAME, var2, var4 + 0.8, var6, 40, 0.4, 0.8, 0.4, 0.1);
    }

    public Component getName(ItemStack var1) {
        String var2 = nameOf(var1);
        return Component.translatable("item.wakingworld.pocket_mage.named", new Object[]{var2});
    }

    public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
        var3.add(Component.translatable("item.wakingworld.pocket_mage.tooltip").withStyle(ChatFormatting.GRAY));
        var3.add(Component.translatable("item.wakingworld.pocket_mage.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
        var3.add(Component.translatable("item.wakingworld.pocket_mage.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
    }

    public boolean isFoil(ItemStack var1) {
        return true;
    }
}
