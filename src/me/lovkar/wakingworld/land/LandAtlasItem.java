package me.lovkar.wakingworld.land;

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
 * The Wayfarer's Chart: the lands you have walked, drawn where they lie.
 *
 * <p>It used to be the last chapter of the Almanac, which was the wrong place for it - a chapter is
 * something you read once and a map is something you keep in your off hand. So it is its own thing
 * now, and the book only mentions that it exists.</p>
 *
 * <p>It shows only where its owner has been. A chart that filled itself in with country nobody had
 * walked would be a satellite photograph, which is a much less interesting object.</p>
 */
public class LandAtlasItem extends Item {
    public LandAtlasItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            WakingWorld.hooks.openAtlas();
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.15F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.land_atlas.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
