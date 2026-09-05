package me.lovkar.wakingworld.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A plain item with a line of lore under its name, and optionally the enchanted shimmer. */
public class LoreItem extends Item {
    private final String loreKey;
    private final boolean foil;

    public LoreItem(Item.Properties properties, String loreKey, boolean foil) {
        super(properties);
        this.loreKey = loreKey;
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
