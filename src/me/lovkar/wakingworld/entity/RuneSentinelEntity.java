package me.lovkar.wakingworld.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.mage.MageEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;

public class RuneSentinelEntity extends Skeleton {
    public RuneSentinelEntity(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0.0F);
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrowStack, float velocity, ItemStack weapon) {
        AbstractArrow arrow = super.getArrow(arrowStack, velocity, weapon);
        arrow.igniteForSeconds(3); // the rune's spark
        arrow.setBaseDamage(arrow.getBaseDamage() + 1.0);
        return arrow;
    }

    public void setTarget(LivingEntity var1) {
        if (!(var1 instanceof MageEntity)) {
            super.setTarget(var1);
        }
    }

    public boolean isAlliedTo(Entity var1) {
        return var1 instanceof MageEntity || var1 instanceof RuneSentinelEntity || super.isAlliedTo(var1);
    }

    public boolean hurt(DamageSource var1, float var2) {
        return var1.getEntity() instanceof MageEntity ? false : super.hurt(var1, var2);
    }

    /** Daylight does not trouble it. */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    /** The skeleton's step is decided in its own package; the sound is ours all the same. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(me.lovkar.wakingworld.WakingSounds.RUNE_SENTINEL_STEP.get(), 0.6F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** The bow - vanilla's version plays the skeleton's twang, so the shot is done here with the rune-bow's own. */
    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float power) {
        ItemStack weapon = getItemInHand(net.minecraft.world.entity.projectile.ProjectileUtil.getWeaponHoldingHand(this, net.minecraft.world.item.Items.BOW));
        ItemStack arrowStack = getProjectile(weapon);
        AbstractArrow arrow = getArrow(arrowStack, power, weapon);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + dist * 0.2, dz, 1.6F, (float) (14 - level().getDifficulty().getId() * 4));
        playSound(me.lovkar.wakingworld.WakingSounds.RUNE_SENTINEL_SHOOT.get(), 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return me.lovkar.wakingworld.WakingSounds.RUNE_SENTINEL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return me.lovkar.wakingworld.WakingSounds.RUNE_SENTINEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return me.lovkar.wakingworld.WakingSounds.RUNE_SENTINEL_DEATH.get();
    }

    /** No stray for this one: the cold does not touch what the runes hold. */
    @Override
    protected void doFreezeConversion() {
    }
}
