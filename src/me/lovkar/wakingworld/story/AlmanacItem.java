package me.lovkar.wakingworld.story;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * The Waker's Almanac: the guide to the mod, given to every player on their first day and
 * craftable after. Opens its own book screen (client: AlmanacScreen) - chapters on tabs, item
 * icons, the kinds' colours - what sleeps, where, what the rite asks, what the vaults and the
 * letters are for, how the fight goes and what it leaves, and a word about the key. It explains
 * everything and spoils nothing: the letters carry the particulars.
 */
public class AlmanacItem extends Item {
    public AlmanacItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            WakingWorld.hooks.openAlmanac();
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.almanac.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
