package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The instrument of the rite: sound it (hold right-click for two seconds) over an altar whose
 * offerings are all laid down and the ceremony begins. Away from an altar it only carries - and
 * turns any colossus within earshot towards you. Found in the vaults. Not consumed.
 */
public class HornOfWakingItem extends Item {
    public static final int BLOW_TICKS = 40;

    public HornOfWakingItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        // the horn's own voice: loud enough to carry across a valley (volume scales the range - 16 blocks per unit)
        level.playSound(null, player.getX(), player.getY(), player.getZ(), me.lovkar.wakingworld.WakingSounds.HORN_BLOW.get(), SoundSource.PLAYERS, 14.0F, 0.95F + level.random.nextFloat() * 0.1F);
        if (!level.isClientSide) {
            WakingWorld.hooks.shake(0.0F);
            for (net.minecraft.server.level.ServerPlayer sp : ((ServerLevel) level).getPlayers(pl -> pl.distanceToSqr(player) < 80 * 80)) {
                ((ServerLevel) level).sendParticles(sp, ParticleTypes.SONIC_BOOM, false, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0, 0, 0, 0);
            }
            // a supporter's horn sounds in the colour of their aura: a ring of light at their feet as the note begins
            int rgb = me.lovkar.wakingworld.supporter.SupporterCosmetics.auraColor(player.getUUID());
            if (rgb >= 0) ((ServerLevel) level).sendParticles(me.lovkar.wakingworld.particle.WakingParticles.ring(rgb, 0.8f), player.getX(), player.getY() + 0.05, player.getZ(), 0, 0, 0.12, 0, 1.0);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** While the note holds: for a supporter, rune glyphs in their aura's colour pour out of the horn's mouth. */
    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player) || remaining % 2 != 0) return;
        int rgb = me.lovkar.wakingworld.supporter.SupporterCosmetics.auraColor(player.getUUID());
        if (rgb < 0) return;
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 mouth = player.getEyePosition().add(look.scale(0.7)).add(0, -0.25, 0);
        server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.rune(rgb, 0.55f), mouth.x, mouth.y, mouth.z, 1,
                look.x * 0.25, 0.04 + look.y * 0.1, look.z * 0.25, 0.08);
        if (remaining % 8 == 0) server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.ember(rgb, 0.6f), mouth.x, mouth.y, mouth.z, 3, 0.15, 0.1, 0.15, 0.06);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(user instanceof net.minecraft.server.level.ServerPlayer player) || !(level instanceof ServerLevel server)) return stack;
        // sounded over an altar? the one looked at, or the nearest within a few blocks
        me.lovkar.wakingworld.ritual.AltarBlockEntity altar = null;
        net.minecraft.world.phys.HitResult hit = player.pick(8.0, 0.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bh && server.getBlockEntity(bh.getBlockPos()) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity a) altar = a;
        if (altar == null) {
            double best = 36;
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(player.blockPosition().offset(-6, -3, -6), player.blockPosition().offset(6, 3, 6))) {
                if (server.getBlockEntity(p) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity a) {
                    double d = p.distSqr(player.blockPosition());
                    if (d < best) { best = d; altar = a; }
                }
            }
        }
        if (altar != null) {
            altar.blow(player);
            player.getCooldowns().addCooldown(this, 60);
            return stack;
        }
        // no altar: the horn only carries - and anything awake within earshot turns towards you
        boolean stirred = false;
        for (ColossusEntity c : server.getEntitiesOfClass(ColossusEntity.class, player.getBoundingBox().inflate(120))) {
            if (!c.isAlive() || c.isWaking()) continue;
            c.setTarget(player);
            stirred = true;
        }
        player.getCooldowns().addCooldown(this, 100);
        player.displayClientMessage(Component.translatable(stirred ? "item.wakingworld.horn_of_waking.stirred" : "item.wakingworld.horn_of_waking.nothing").withStyle(ChatFormatting.GRAY), true);
        server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.4, 0.3, 0.4, 0.05);
        int rgb = me.lovkar.wakingworld.supporter.SupporterCosmetics.auraColor(player.getUUID());
        if (rgb >= 0) server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.rune(rgb, 0.8f), player.getX(), player.getY() + 1.4, player.getZ(), 14, 0.6, 0.5, 0.6, 0.06);
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return BLOW_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.horn_of_waking.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.horn_of_waking.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
