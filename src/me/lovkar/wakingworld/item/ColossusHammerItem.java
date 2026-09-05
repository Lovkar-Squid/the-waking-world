package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.entity.Crater;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A slab of colossus forged around a Heart. Slow and very heavy: twelve damage a swing, and it
 * hits a colossus half again as hard - it is the one thing they really feel.
 * <p>
 * Right-click on the ground: a slam - everything within six blocks is thrown back and hurt.
 * Right-click in the air: a dive - you drop like a stone, and where you land the ground breaks:
 * no fall damage, a crater, a ring of dust, and a slam that grows with the height you came from.
 * Just holding it halves any fall damage.
 */
public class ColossusHammerItem extends Item {
    public static final double RADIUS = 6.0;
    public static final float SLAM_DAMAGE = 9.0F;
    public static final int COOLDOWN = 160;
    /** Damage against a colossus is multiplied by this. */
    public static final float VS_COLOSSUS = 1.5F;

    /** Server side: who is diving - {height it began at, game time it began}. A dive older than 10 s is forgotten. */
    private static final Map<UUID, double[]> DIVES = new HashMap<>();
    /** Client side: the local player's dive. */
    private static boolean clientDiving;
    private static double clientDiveY;

    public ColossusHammerItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 11.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.3, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static boolean holding(LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof ColossusHammerItem || e.getOffhandItem().getItem() instanceof ColossusHammerItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.onGround() && !player.isInWaterOrBubble() && !player.isInLava()) {
            // in the air: dive
            if (level.isClientSide) {
                if (clientDiving) return InteractionResultHolder.pass(stack);
                clientDiving = true;
                clientDiveY = player.getY();
            } else {
                if (DIVES.containsKey(player.getUUID())) return InteractionResultHolder.pass(stack);
                DIVES.put(player.getUUID(), new double[]{player.getY(), level.getGameTime()});
                player.hurtMarked = true;
            }
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x * 0.3, Math.min(dm.y, -1.4), dm.z * 0.3);
            player.stopFallFlying();
            level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.2F, 0.5F);
            player.swing(hand, true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!player.onGround()) return InteractionResultHolder.pass(stack);
        // on the ground: the slam
        boolean heavy = player.fallDistance >= 3.0F;
        if (level.isClientSide) {
            WakingWorld.hooks.shake(heavy ? 0.9F : 0.55F);
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        stack.hurtAndBreak(heavy ? 6 : 3, player, LivingEntity.getSlotForHand(hand));
        player.resetFallDistance();
        player.swing(hand, true);
        slam((ServerLevel) level, player, heavy ? 0.5 : 0.0, false);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * Every tick in a hand: keeps a dive falling hard, and lands it. The client accelerates its own
     * fall (the player's movement is the client's); the server decides the landing.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof Player player)) return;
        // one hammer decides: the one in the main hand, or the offhand one if the main hand holds none
        if (player.getMainHandItem() != stack && (player.getOffhandItem() != stack || player.getMainHandItem().getItem() instanceof ColossusHammerItem)) return;
        if (level.isClientSide) {
            if (!clientDiving || !player.isLocalPlayer()) return;
            if (!player.isAlive()) {
                clientDiving = false;
                return;
            }
            if (player.onGround() || player.isInWaterOrBubble() || player.isInLava()) {
                clientDiving = false;
                double fall = clientDiveY - player.getY();
                WakingWorld.hooks.shake((float) Math.min(1.4, 0.5 + fall * 0.05));
                return;
            }
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x * 0.7, Math.max(-3.2, dm.y - 0.32), dm.z * 0.7);
            if (level.random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.CLOUD, player.getX() + (level.random.nextDouble() - 0.5) * 0.8, player.getY() + 1.0, player.getZ() + (level.random.nextDouble() - 0.5) * 0.8, 0, 0.15, 0);
            }
            return;
        }
        double[] dive = DIVES.get(player.getUUID());
        if (dive == null) return;
        if (level.getGameTime() - dive[1] > 200 || !player.isAlive()) {
            DIVES.remove(player.getUUID());
            return;
        }
        if (player.onGround() || player.isInWaterOrBubble() || player.isInLava()) {
            DIVES.remove(player.getUUID());
            double fall = dive[0] - player.getY();
            if (player.isInWaterOrBubble() || player.isInLava() || fall < 1.5) return; // a splash, not a landing
            double strength = Math.min(1.0, (fall - 1.5) / 14.0);
            player.getCooldowns().addCooldown(this, COOLDOWN);
            stack.hurtAndBreak(4 + (int) (strength * 6), player, player.getMainHandItem() == stack ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.resetFallDistance();
            slam((ServerLevel) level, player, strength, true);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) me.lovkar.wakingworld.advancement.WakingTriggers.DIVE_SLAM.get().trigger(sp, fall);
        }
    }

    /** Diving with the hammer, you land without fall damage; just holding it, you take half. */
    public static void onFall(LivingFallEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof Player player) || !holding(player)) return;
        if (DIVES.containsKey(player.getUUID()) || (player.level().isClientSide && clientDiving)) {
            event.setCanceled(true);
        } else {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.5F);
        }
    }

    /**
     * The slam itself. strength 0..1 scales everything: reach, damage, the crater. A dive landing
     * (fromDive) is the big one - the ground breaks under you even at strength 0.
     */
    private void slam(ServerLevel server, Player player, double strength, boolean fromDive) {
        Vec3 at = player.position();
        double radius = RADIUS * (1.0 + 0.6 * strength);
        boolean heavy = strength > 0.3;

        server.playSound(null, at.x, at.y, at.z, heavy ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 2.5F + (float) strength, 0.6F - (float) strength * 0.15F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F + (float) strength * 2.0F, 0.45F - (float) strength * 0.1F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);

        // the ground breaks: a shallow crater under a dive, a puff of the ground's own dust under a plain slam
        BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
        BlockState ground = server.getBlockState(under);
        if (fromDive && WakingConfig.terrainDamage()) {
            Crater.blast(server, at.add(0, -0.4, 0), 1.6 + 2.2 * strength, 4 + (int) (strength * 10), 0.35 + 0.3 * strength, strength > 0.7 ? 2 : 1, server.random);
        }
        if (!ground.isAir()) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.3, at.z, 40 + (int) (strength * 60), 1.0 + strength, 0.4, 1.0 + strength, 0.25);
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        if (heavy) server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        int points = (int) (radius * 5);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double r = radius * (0.55 + 0.45 * server.random.nextDouble());
            double px = at.x + Math.cos(a) * r, pz = at.z + Math.sin(a) * r;
            BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(px, at.y, pz));
            if (Math.abs(top.getY() - at.y) > 4) continue;
            BlockState below = server.getBlockState(top.below());
            if (below.isAir()) continue;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), px, top.getY() + 0.3, pz, 5, 0.25, 0.4 + strength, 0.25, 0.2);
            if (i % 3 == 0) server.sendParticles(ParticleTypes.POOF, px, top.getY() + 0.4, pz, 2, 0.2, 0.2, 0.2, 0.04);
            // under a dive the odd block hops with the ring
            if (fromDive && WakingConfig.terrainDamage() && server.random.nextInt(6) == 0 && Crater.trampleable(server, top.below(), below) && below.canOcclude()) {
                Crater.fling(server, top.below(), below, at, 0.2 + 0.2 * strength, server.random);
            }
        }

        // everything around is thrown back and hurt - most in close, less at the edge
        AABB area = new AABB(at.x - radius, at.y - 2, at.z - radius, at.x + radius, at.y + 3, at.z + radius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 d = target.position().subtract(at);
            double dist = d.horizontalDistance();
            if (dist > radius + target.getBbWidth()) continue;
            double near = 1.0 - Math.min(1.0, dist / (radius + 1.0)) * 0.7;
            float dmg = (float) ((SLAM_DAMAGE + (fromDive ? 4 : 0) + 12 * strength) * near);
            if (target instanceof ColossusEntity) dmg *= VS_COLOSSUS;
            target.hurt(server.damageSources().playerAttack(player), dmg);
            if (!(target instanceof ColossusEntity)) {
                double nx = dist < 0.01 ? 0 : d.x / dist, nz = dist < 0.01 ? 0 : d.z / dist;
                double kick = 1.1 + 0.8 * strength;
                target.setDeltaMovement(target.getDeltaMovement().add(nx * kick * near, 0.45 + 0.5 * near * (1 + strength), nz * kick * near));
                target.hurtMarked = true;
            }
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.4, 0.4, 0.4, 0.25);
            server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.colossus_hammer.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.colossus_hammer.tooltip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.colossus_hammer.tooltip3").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
