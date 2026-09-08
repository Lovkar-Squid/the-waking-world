package me.lovkar.wakingworld.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A piece of the star iron suit, and the only place the suit says what it is for.
 *
 * <p>Its armour points and toughness are on the tooltip already, because the game puts them there.
 * What the game cannot show is the part that matters: {@link StarIron#onHurt} quietly takes half
 * of everything the sky and the ground throw at somebody wearing all four - a meteor, lava, a
 * falling block, the burning after. A number that never happens is invisible, so a player who
 * makes the suit and never reads the wiki has no way of knowing why they should.</p>
 *
 * <p>Hence two lines: what the metal is, and what the suit does. They are the same on every piece
 * on purpose - a player holding one boot should learn the same thing as a player holding all four,
 * because the boot is where they will start.</p>
 */
public class StarIronArmourItem extends ArmorItem {
    public StarIronArmourItem(Holder<ArmorMaterial> material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.star_iron_armour.tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.wakingworld.star_iron_armour.set",
                        (int) (StarIron.SHIELDED * 100))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
