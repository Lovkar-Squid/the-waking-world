package me.lovkar.wakingworld.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * A Stone Thrall: the vaults' keeper, a body of the old rites' servants turned to stone and kept
 * walking by an ember that never went out. Slow, heavy, hard to hurt, and it hits like a wall.
 * Does not burn in the sun, does not drown, does not break doors, fears nothing. Drops stone
 * and, now and then, the ember that kept it moving.
 */
public class StoneThrallEntity extends Zombie {
    /** Variants: the vaults' stone thrall, and the Hollow Thrall of the End's reliquaries - end stone and obsidian, violet-eyed, unhurt by the void's cold. */
    public static final int STONE = 0, HOLLOW = 1;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_VARIANT = net.minecraft.network.syncher.SynchedEntityData.defineId(StoneThrallEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    public StoneThrallEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, STONE);
    }

    public int variant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) setVariant(tag.getInt("Variant"));
    }

    @Override
    public net.minecraft.network.chat.Component getName() {
        if (hasCustomName()) return super.getName();
        return variant() == HOLLOW ? net.minecraft.network.chat.Component.translatable("entity.wakingworld.stone_thrall.hollow") : super.getName();
    }

    /** The Hollow Thrall does not fall out of the world: over the void it is set back on the island it came from. */
    @Override
    public void tick() {
        super.tick();
        if (variant() == HOLLOW && !level().isClientSide && this.getY() < level().getMinBuildHeight() + 2) {
            net.minecraft.world.phys.Vec3 safe = me.lovkar.wakingworld.entity.VoidGuard.nearestGround((net.minecraft.server.level.ServerLevel) level(), position(), 24, null);
            if (safe != null) {
                teleportTo(safe.x, safe.y + 1, safe.z);
                setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            } else {
                discard();
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean canBreakDoors() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return (variant() == HOLLOW) ? me.lovkar.wakingworld.WakingSounds.HOLLOW_THRALL_AMBIENT.get() : me.lovkar.wakingworld.WakingSounds.STONE_THRALL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return (variant() == HOLLOW) ? me.lovkar.wakingworld.WakingSounds.HOLLOW_THRALL_HURT.get() : me.lovkar.wakingworld.WakingSounds.STONE_THRALL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return (variant() == HOLLOW) ? me.lovkar.wakingworld.WakingSounds.HOLLOW_THRALL_DEATH.get() : me.lovkar.wakingworld.WakingSounds.STONE_THRALL_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return me.lovkar.wakingworld.WakingSounds.STONE_THRALL_STEP.get();
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
