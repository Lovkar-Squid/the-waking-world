package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.cataclysm.Cataclysms;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Stormcaller's Rod: what the mage was holding, and the only thing you get for killing him.
 *
 * <p>It is a piece of what he could do rather than all of it - one clap of the pressure a cataclysm
 * carries in front of it, which throws back everything around you and puts out what is burning. It
 * is deliberately not a cataclysm: a player who kills the man rather than paying him gets the
 * weapon, not the trade, and that is the whole of the bargain.</p>
 */
public class StormRodItem extends Item {
    private static final int COOLDOWN = 8 * 20;

    public StormRodItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC).durability(240));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 at = player.position();
            server.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.5F);
            Cataclysms.puff(server, ParticleTypes.SONIC_BOOM, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
            for (int i = 0; i < 60; i++) {
                double a = i / 60.0 * Math.PI * 2;
                Cataclysms.puff(server, ParticleTypes.CLOUD, at.x + Math.cos(a) * 5.0, at.y + 0.4, at.z + Math.sin(a) * 5.0,
                        2, 0.2, 0.2, 0.2, 0.12);
            }
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6.5), t -> t != player && t.isAlive())) {
                e.hurt(server.damageSources().magic(), 6.0F);
                e.clearFire();
                Vec3 push = e.position().subtract(at).normalize().scale(1.9).add(0, 0.8, 0);
                e.push(push.x, push.y, push.z);
                e.hurtMarked = true;
            }
            player.clearFire();
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.storm_rod.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.storm_rod.tooltip2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }
}
