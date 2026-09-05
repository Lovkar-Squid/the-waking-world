package me.lovkar.wakingworld.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * An Ember Wraith: what is left of a forge-hand who would not leave the fire - a charred body
 * with the coals still going inside it. Fast for a dead thing, never burns, sets what it strikes
 * alight, and rain or water put it out for good. Keeps the Ember Forges.
 */
public class EmberWraithEntity extends Zombie {
    public EmberWraithEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, 0.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 28.0);
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
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean did = super.doHurtTarget(target);
        if (did) {
            target.igniteForSeconds(4);
            playSound(me.lovkar.wakingworld.WakingSounds.EMBER_WRAITH_FLARE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        }
        return did;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.02, 0);
            if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.SMOKE, getRandomX(0.6), getY() + 1.6, getRandomZ(0.6), 0, 0.04, 0);
        } else if (level() instanceof ServerLevel server && tickCount % 20 == 0 && isAlive()) {
            // its footfalls scorch: a little fire where it walks over something that burns, never over stone
            net.minecraft.core.BlockPos at = blockPosition();
            if (server.getBlockState(at).isAir() && server.getBlockState(at.below()).is(net.minecraft.tags.BlockTags.DIRT) && random.nextInt(4) == 0) {
                server.setBlock(at, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return me.lovkar.wakingworld.WakingSounds.EMBER_WRAITH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return me.lovkar.wakingworld.WakingSounds.EMBER_WRAITH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return me.lovkar.wakingworld.WakingSounds.EMBER_WRAITH_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return me.lovkar.wakingworld.WakingSounds.EMBER_WRAITH_STEP.get();
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
    }
}
