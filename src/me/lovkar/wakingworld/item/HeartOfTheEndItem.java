package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * What the Titan leaves behind: its heart, cold and heavy with the End. Eat it (right-click, held
 * for a moment) and it becomes part of you - two more hearts, for good; five Hearts (five Titans)
 * make ten hearts more, and no further. The one reward in the mod that stays with the player.
 */
public class HeartOfTheEndItem extends Item {
    public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "heart_of_the_end");
    /** Health per Heart eaten (two hearts) and the most it will ever give (five Hearts: ten hearts, 20 health). */
    public static final double PER_USE = 4.0, MAX = 20.0;

    public HeartOfTheEndItem(Item.Properties properties) {
        super(properties);
    }

    /** Where the bonus is remembered across deaths: NeoForge carries this sub-tag of the player's data over to the respawned player. */
    private static final String PERSISTED = "PlayerPersisted", KEY = "wakingworld:heart_of_the_end";

    /** How much extra health the player already carries from these. */
    public static double bonus(Player player) {
        AttributeInstance a = player.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return 0;
        AttributeModifier m = a.getModifier(MODIFIER_ID);
        return m == null ? remembered(player) : m.amount();
    }

    private static double remembered(Player player) {
        net.minecraft.nbt.CompoundTag persisted = data(player).getCompound(PERSISTED);
        return persisted.contains(KEY) ? persisted.getDouble(KEY) : 0;
    }

    private static void remember(Player player, double amount) {
        net.minecraft.nbt.CompoundTag data = data(player);
        net.minecraft.nbt.CompoundTag persisted = data.getCompound(PERSISTED);
        persisted.putDouble(KEY, amount);
        data.put(PERSISTED, persisted);
    }

    /** Puts the modifier on a player who should have it (a respawn, a new dimension) - the hearts are for good. */
    public static void restore(Player player) {
        double want = remembered(player);
        AttributeInstance a = player.getAttribute(Attributes.MAX_HEALTH);
        if (a == null || want <= 0) return;
        AttributeModifier m = a.getModifier(MODIFIER_ID);
        if (m != null && m.amount() >= want) return;
        a.removeModifier(MODIFIER_ID);
        a.addPermanentModifier(new AttributeModifier(MODIFIER_ID, want, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Death makes a new player: the old one's hearts go over to it. */
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        double had = Math.max(remembered(event.getOriginal()), bonusOf(event.getOriginal()));
        if (had > 0) remember(event.getEntity(), had);
        restore(event.getEntity());
    }

    /** After the respawn the health was set from the plain maximum: fill the new hearts too. */
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        restore(event.getEntity());
        if (!event.isEndConquered()) event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }

    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) restore(player);
    }

    /** NeoForge's per-entity NBT (the vanilla jar on the compile path does not know the patched method). */
    private static net.minecraft.nbt.CompoundTag data(Player player) {
        return ((net.neoforged.neoforge.common.extensions.IEntityExtension) player).getPersistentData();
    }

    private static double bonusOf(Player player) {
        AttributeInstance a = player.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return 0;
        AttributeModifier m = a.getModifier(MODIFIER_ID);
        return m == null ? 0 : m.amount();
    }

    /** It is eaten, not clicked: held to the mouth for a moment and a half, like any other meal. */
    private static final int EAT_TICKS = 32;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (bonus(player) >= MAX) {
            if (level.isClientSide) player.displayClientMessage(Component.translatable("item.wakingworld.heart_of_the_end.full").withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return EAT_TICKS;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Player player) || !(level instanceof ServerLevel server)) return stack;
        double have = bonus(player);
        AttributeInstance a = player.getAttribute(Attributes.MAX_HEALTH);
        if (a == null || have >= MAX) return stack;
        double now = Math.min(MAX, have + PER_USE);
        a.removeModifier(MODIFIER_ID);
        a.addPermanentModifier(new AttributeModifier(MODIFIER_ID, now, AttributeModifier.Operation.ADD_VALUE));
        remember(player, now);
        player.heal((float) PER_USE);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.2F);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.6F);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.7, 0.9, 0.7, 0.5);
        server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(), 12, 0.8, 0.5, 0.8, 0.1);
        server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
        player.displayClientMessage(Component.translatable("item.wakingworld.heart_of_the_end.eaten", (int) (now / 2), (int) (MAX / 2)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        // the advancement counts Hearts eaten (Titans felled), not the player's hearts: the fifth completes it
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) me.lovkar.wakingworld.advancement.WakingTriggers.HEART_EATEN.get().trigger(sp, Math.round(now / PER_USE));
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.heart_of_the_end.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.heart_of_the_end.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
