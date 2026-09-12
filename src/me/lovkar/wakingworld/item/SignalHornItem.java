package me.lovkar.wakingworld.item;

import java.util.List;
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.kingdom.KingdomSiege;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class SignalHornItem extends Item {
    private static final double AIM = 160.0;

    public SignalHornItem(Properties var1) {
        super(var1);
    }

    public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
        ItemStack var4 = var2.getItemInHand(var3);
        var1.playSound(null, var2.getX(), var2.getY(), var2.getZ(), (SoundEvent)WakingSounds.HORN_BLOW.get(), SoundSource.PLAYERS, 14.0F, 1.25F);
        if (!var1.isClientSide && var2 instanceof ServerPlayer var5 && var1 instanceof ServerLevel var6) {
            Vec3 var7 = aimPoint(var6, var5);
            if (me.lovkar.wakingworld.compat.Colonies.keepOff(var6, net.minecraft.core.BlockPos.containing(var7), 24)) {
                var5.displayClientMessage(Component.translatable("kingdom.wakingworld.siege_colony").withStyle(ChatFormatting.GRAY), true);
                var5.getCooldowns().addCooldown(this, 40);
                return InteractionResultHolder.fail(var4);
            }
            if (!KingdomSiege.callAt(var6, var5, var7)) {
                var5.displayClientMessage(Component.translatable("item.wakingworld.signal_horn.nobody").withStyle(ChatFormatting.GRAY), true);
                var5.getCooldowns().addCooldown(this, 40);
                return InteractionResultHolder.fail(var4);
            }

            var5.getCooldowns().addCooldown(this, 6000);
            return InteractionResultHolder.consume(var4);
        }

        return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
    }

    private static Vec3 aimPoint(ServerLevel var0, ServerPlayer var1) {
        Vec3 var2 = var1.getEyePosition();
        Vec3 var3 = var2.add(var1.getLookAngle().scale(160.0));
        BlockHitResult var4 = var0.clip(new ClipContext(var2, var3, Block.COLLIDER, Fluid.NONE, var1));
        if (var4 instanceof BlockHitResult var5 && var4.getType() != Type.MISS) {
            return Vec3.atCenterOf(var5.getBlockPos());
        }

        int var6 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(var3.x), (int)Math.floor(var3.z));
        return new Vec3(var3.x, (double)var6, var3.z);
    }

    public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
        var3.add(Component.translatable("item.wakingworld.signal_horn.tooltip").withStyle(ChatFormatting.GRAY));
        var3.add(Component.translatable("item.wakingworld.signal_horn.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
