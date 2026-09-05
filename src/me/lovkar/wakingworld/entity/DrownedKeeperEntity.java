package me.lovkar.wakingworld.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A Drowned Keeper: the cisterns' warden, a drowned that never let go of its trident or its
 * post. Bigger and harder than the drowned of the sea, quick in the water, and it drags
 * whoever it hits under - a Keeper's blow leaves you slow and heavy for a while.
 */
public class DrownedKeeperEntity extends Drowned {
    public DrownedKeeperEntity(EntityType<? extends Drowned> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Drowned.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ARMOR, 5.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0.0F);
    }

    /**
     * The sea's drowned keep the sea's hours: by day they slip back to the water and leave the
     * shore alone unless you are in it with them, by night they come up onto the beach. The Keeper
     * has no sea and no sky - its cistern is dark at noon - so it keeps none of that: it holds its
     * post and goes for whoever comes down, whatever the hour.
     */
    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        java.util.List<net.minecraft.world.entity.ai.goal.Goal> seaHabits = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.ai.goal.WrappedGoal w : this.goalSelector.getAvailableGoals()) {
            String name = w.getGoal().getClass().getSimpleName();
            if (name.equals("DrownedGoToWaterGoal") || name.equals("DrownedGoToBeachGoal") || name.equals("DrownedSwimUpGoal")) seaHabits.add(w.getGoal());
        }
        seaHabits.forEach(this.goalSelector::removeGoal);
    }

    /** Vanilla: a player is fair game only at night or in the water. The Keeper: anyone it sees. */
    @Override
    public boolean okTarget(net.minecraft.world.entity.LivingEntity target) {
        return target != null;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean did = super.doHurtTarget(target);
        if (did) playSound(me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_ATTACK.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        if (did && target instanceof net.minecraft.world.entity.LivingEntity living) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 60, 0));
        }
        return did;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInWater() ? me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_AMBIENT_WATER.get() : me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_STEP.get();
    }

    @Override
    protected SoundEvent getSwimSound() {
        return me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_SWIM.get();
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return me.lovkar.wakingworld.WakingSounds.DROWNED_KEEPER_SWIM.get();
    }
}
