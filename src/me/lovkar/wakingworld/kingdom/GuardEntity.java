package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The kingdom's guards: archers on the walls, knights at the gates and in the keep, spearmen on the
 * roads. They keep the town clear of monsters and leave players alone - until the kingdom is angry
 * with one (the treasury robbed, the king struck, a guard or a trader attacked); then every guard
 * within reach turns on them for a day. They hold their posts and never wander far.
 */
public class GuardEntity extends PathfinderMob implements RangedAttackMob {
    public static final int ARCHER = 0, KNIGHT = 1, SPEARMAN = 2;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);

    private BlockPos center = BlockPos.ZERO;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, KNIGHT);
    }

    public int kind() {
        return entityData.get(DATA_KIND);
    }

    public void setKind(int kind) {
        entityData.set(DATA_KIND, kind);
    }

    public BlockPos center() {
        return center;
    }

    /** How far from its post a guard will wander. */
    public void setPostRadius(int radius) {
        if (hasRestriction()) restrictTo(getRestrictCenter(), radius);
    }

    /** Where this guard belongs and stands: set once by the structure. */
    public void assign(BlockPos kingdomCenter, BlockPos post, int kind, RandomSource random) {
        this.center = kingdomCenter;
        setKind(kind);
        restrictTo(post, kind == ARCHER ? 4 : 7);
        equip(kind, random);
        registerGoalsFor(kind);
    }

    private void equip(int kind, RandomSource random) {
        switch (kind) {
            case ARCHER -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            }
            case SPEARMAN -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
            default -> {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
        }
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0.0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.9));
        WaterAvoidingRandomStrollGoal stroll = new WaterAvoidingRandomStrollGoal(this, 0.6);
        stroll.setInterval(240); // sentries stand; they stretch their legs now and then
        goalSelector.addGoal(7, stroll);
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(GuardEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> angryAt((Player) p)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, m -> !(m instanceof Creeper) && nearPost(m)));
    }

    private boolean attackGoalsSet;

    private void registerGoalsFor(int kind) {
        if (attackGoalsSet) return;
        attackGoalsSet = true;
        if (kind == ARCHER) goalSelector.addGoal(3, new RangedAttackGoal(this, 1.0, 30, 18.0F));
        else goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Kind")) setKind(tag.getInt("Kind"));
        if (tag.contains("Center")) NbtUtils.readBlockPos(tag, "Center").ifPresent(p -> center = p);
        if (tag.contains("Post")) NbtUtils.readBlockPos(tag, "Post").ifPresent(p -> restrictTo(p, tag.contains("PostRadius") ? tag.getInt("PostRadius") : 6));
        registerGoalsFor(kind());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind());
        tag.put("Center", NbtUtils.writeBlockPos(center));
        if (hasRestriction()) {
            tag.put("Post", NbtUtils.writeBlockPos(getRestrictCenter()));
            tag.putInt("PostRadius", (int) getRestrictRadius());
        }
    }

    /** A guard fights what comes near its post, not what it can see across the town. */
    private boolean nearPost(LivingEntity target) {
        if (!hasRestriction()) return true;
        double reach = kind() == ARCHER ? 22 : 14;
        return target.distanceToSqr(getRestrictCenter().getCenter()) <= reach * reach;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target instanceof Player player && tickCount % 20 == 0 && !angryAt(player) && !player.isCreative()) setTarget(null); // the anger has passed
        if (target != null && !(target instanceof Player) && tickCount % 40 == 0 && !nearPost(target) && distanceToSqr(target) > 12 * 12) setTarget(null); // it has run off; back to the post
        setAggressive(getTarget() != null);
    }

    public boolean angryAt(Player player) {
        return level() instanceof ServerLevel server && KingdomData.get(server).isAngry(server, center, player.getUUID());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean did = super.hurt(source, amount);
        if (did && source.getEntity() instanceof Player player && level() instanceof ServerLevel server && !player.isCreative()) {
            Kingdoms.offend(server, center, player, KingdomData.ANGER_TICKS, "guard");
        }
        return did;
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        if (kind() == SPEARMAN) return distanceToSqr(target) <= 3.6 * 3.6;
        return super.isWithinMeleeAttackRange(target);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack arrowStack = getProjectile(weapon);
        if (arrowStack.isEmpty()) arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, power, weapon.getItem() instanceof BowItem ? weapon : null);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + dist * 0.2, dz, 1.6F, 6.0F);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        playSound(me.lovkar.wakingworld.WakingSounds.GUARD_SHOOT.get(), 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return me.lovkar.wakingworld.WakingSounds.GUARD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return me.lovkar.wakingworld.WakingSounds.GUARD_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(me.lovkar.wakingworld.WakingSounds.GUARD_STEP.get(), 0.5F, 0.9F + getRandom().nextFloat() * 0.2F);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean did = super.doHurtTarget(target);
        if (did) playSound(me.lovkar.wakingworld.WakingSounds.GUARD_ATTACK.get(), 1.0F, 0.9F + getRandom().nextFloat() * 0.2F);
        return did;
    }

    @Override
    public Component getName() {
        if (hasCustomName()) return super.getName();
        return Component.translatable("entity.wakingworld.guard." + switch (kind()) {
            case ARCHER -> "archer";
            case SPEARMAN -> "spearman";
            default -> "knight";
        });
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Guards do not take friendly arrows to heart. */
    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        return other instanceof GuardEntity || other instanceof TownsfolkEntity || other instanceof KingEntity || super.isAlliedTo(other);
    }

    @Override
    public boolean hasRestriction() {
        return getRestrictRadius() > 0 && !getRestrictCenter().equals(BlockPos.ZERO);
    }

    public static void log(String s) {
        WakingWorld.LOGGER.info(s);
    }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            boolean angry = angryAt(player);
            String tier = angry ? null : me.lovkar.wakingworld.supporter.SupporterCosmetics.tier(player.getUUID()); // a waker of the world gets a salute
            player.displayClientMessage(Component.translatable(angry ? "entity.wakingworld.guard.say.angry" : tier != null && getRandom().nextBoolean()
                    ? "entity.wakingworld.guard.say." + tier : "entity.wakingworld.guard.say." + (getRandom().nextInt(4) + 1)), true);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }
}
