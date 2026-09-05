package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.ruin.FightRecord;
import me.lovkar.wakingworld.ruin.RuinLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Hourglass of Restoration. Turn it over near the ruin of a fight that is over and the land
 * remembers: every block the colossus broke, flung or buried comes back where it was, far to
 * near, its rubble and its burial mound go, and what stood there before stands again. One use.
 */
public class HourglassItem extends Item {
    /** How far from the edge of a ruin it still works. */
    public static final double RANGE = 96.0;

    public HourglassItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.sidedSuccess(stack, true);
        RuinLedger ledger = RuinLedger.get(server);
        FightRecord ruin = ledger.nearestFinished(player.blockPosition(), RANGE);
        if (ruin == null) {
            player.displayClientMessage(Component.translatable("item.wakingworld.hourglass.nothing").withStyle(ChatFormatting.GRAY), true);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.fail(stack);
        }
        int blocks = ruin.size();
        ledger.restore(ruin);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        BlockPos c = ruin.center();
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 3.0F, 0.5F);
        server.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6.0F, 0.6F);
        server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.5, 0.5, 0.5, 0.15);
        server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.4);
        player.displayClientMessage(Component.translatable("item.wakingworld.hourglass.turned", blocks).withStyle(ChatFormatting.AQUA), true);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) me.lovkar.wakingworld.advancement.WakingTriggers.LAND_RESTORED.get().trigger(sp, blocks);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.hourglass.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.hourglass.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
