package me.lovkar.wakingworld.item;

import java.util.List;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class MirrorItem extends Item {
    private static final double RANGE = 96.0;
    private static final int COOLDOWN = 140;

    public MirrorItem(Properties var1) {
        super(var1);
    }

    public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
        ItemStack var4 = var2.getItemInHand(var3);
        if (var2.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(var4);
        } else {
            Vec3 var5 = var2.getEyePosition();
            Vec3 var6 = var2.getLookAngle();
            Vec3 var7 = var5.add(var6.scale(96.0));
            BlockHitResult var8 = var1.clip(new ClipContext(var5, var7, Block.COLLIDER, Fluid.NONE, var2));
            Vec3 var9 = var8.getType() == Type.MISS ? var7 : var8.getLocation().subtract(var6.scale(0.6));
            BlockPos var10 = footing(var1, var9, var2);
            if (var10 == null) {
                if (!var1.isClientSide) {
                    var2.displayClientMessage(Component.translatable("item.wakingworld.mage_mirror.nowhere"), true);
                }

                return InteractionResultHolder.fail(var4);
            } else {
                if (var1 instanceof ServerLevel var11) {
                    Vec3 var12 = var2.position();
                    show(var11, var12);
                    var2.teleportTo((double)var10.getX() + 0.5, (double)var10.getY(), (double)var10.getZ() + 0.5);
                    var2.resetFallDistance();
                    show(var11, var2.position());
                    var11.playSound(null, var12.x, var12.y, var12.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.4F, 0.8F);
                    var11.playSound(null, var2.getX(), var2.getY(), var2.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.4F, 1.2F);
                }

                var2.getCooldowns().addCooldown(this, 140);
                var2.awardStat(Stats.ITEM_USED.get(this));
                return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
            }
        }
    }

    private static void show(ServerLevel var0, Vec3 var1) {
        Cataclysms.runes(var0, 9067208, 1.1F, var1.x, var1.y + 1.0, var1.z, 18, 0.35, 0.8, 0.35);
        Cataclysms.ring(var0, 9067208, 1.6F, var1.x, var1.y + 0.05, var1.z);
        Cataclysms.puff(var0, ParticleTypes.PORTAL, var1.x, var1.y + 1.0, var1.z, 30, 0.3, 0.7, 0.3, 0.25);
    }

    private static BlockPos footing(Level var0, Vec3 var1, Player var2) {
        BlockPos var3 = BlockPos.containing(var1);

        for (int var7 : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos var8 = var3.offset(0, var7, 0);
            if (var0.isLoaded(var8)
                && var0.noCollision(
                    var2,
                    var2.getBoundingBox()
                        .move((double)var8.getX() + 0.5 - var2.getX(), (double)var8.getY() - var2.getY(), (double)var8.getZ() + 0.5 - var2.getZ())
                )
                && (!var0.getBlockState(var8.below()).isAir() || var7 <= -3)) {
                return var8;
            }
        }

        return null;
    }

    public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
        var3.add(Component.translatable("item.wakingworld.mage_mirror.tooltip").withStyle(ChatFormatting.GRAY));
        var3.add(Component.translatable("item.wakingworld.mage_mirror.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
