package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.lovkar.wakingworld.advancement.KindTrigger;
import me.lovkar.wakingworld.advancement.WakingTriggers;
import me.lovkar.wakingworld.cataclysm.MeteorEntity;
import me.lovkar.wakingworld.cataclysm.Scars;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.entity.RuneSentinelEntity;
import me.lovkar.wakingworld.item.PocketMageItem;
import me.lovkar.wakingworld.item.WakingItems;
import me.lovkar.wakingworld.kingdom.GuardEntity;
import me.lovkar.wakingworld.kingdom.KingCharge;
import me.lovkar.wakingworld.kingdom.KingEntity;
import me.lovkar.wakingworld.kingdom.KingdomExpansion;
import me.lovkar.wakingworld.kingdom.TownsfolkEntity;
import me.lovkar.wakingworld.worldgen.Tidy;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public class MageEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_ROUSED =
            SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.BOOLEAN);
    /** How far into a cast he is, 0-1, for the robe's light and the ring at his feet. */
    private static final EntityDataAccessor<Float> DATA_CAST =
            SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CHANNEL = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_KEPT = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORDER = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STANCE = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SHRINK = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CHANGING = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WARD = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_BAR_ID = SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final ServerBossEvent bar = new ServerBossEvent(
            Component.translatable("entity.wakingworld.dark_mage"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private BlockPos tower = BlockPos.ZERO;
    /** The tower is raked once, the first time anybody comes to it (see {@link me.lovkar.wakingworld.worldgen.Tidy}). */
    private boolean tidied;
    private int castCooldown = 60;
    private int spell;
    private int castTicks;
    /** Where the spell was aimed when he began it - so the ring lands where you WERE, and can be left. */
    private Vec3 castAt = Vec3.ZERO;
    private int blinkCooldown = 120;
    private boolean second;
    private int stage = 1;
    private int channel;
    private int channelTotal;
    private float channelHurt;
    private Vec3 stormAt = Vec3.ZERO;
    private int changing;
    private int changeTotal;
    private float ward;
    private float wardMax;
    private UUID scar;
    private int mendCooldown;
    private int lampCooldown;
    private int haulCooldown;
    private int shrinking;
    private UUID owner;
    private BlockPos post = BlockPos.ZERO;
    public static final int WILD = 0;
    public static final int KEPT = 1;
    public static final int CAGED = 2;
    public static final int FOLLOW = 0;
    public static final int HOLD = 1;
    public static final int RANGE = 2;
    public static final int MEEK = 0;
    public static final int DEFEND = 1;
    public static final int GUARD = 2;
    private static final double GUARD_REACH = 17.0;
    private static final int MEND_EVERY = 900;
    private static final int LAMP_EVERY = 400;
    private static final int HAUL_EVERY = 120;

    public MageEntity(EntityType<? extends MageEntity> var1, Level var2) {
        super(var1, var2);
        this.setPersistenceRequired();
        this.xpReward = 260;
        this.bar.setVisible(false);
    }

    public static Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 320.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder var1) {
        super.defineSynchedData(var1);
        var1.define(DATA_ROUSED, false);
        var1.define(DATA_CAST, 0.0F);
        var1.define(DATA_NAME, "");
        var1.define(DATA_STAGE, 1);
        var1.define(DATA_CHANNEL, 0.0F);
        var1.define(DATA_BAR_ID, Optional.empty());
        var1.define(DATA_CHANGING, 0.0F);
        var1.define(DATA_WARD, 0.0F);
        var1.define(DATA_KEPT, 0);
        var1.define(DATA_ORDER, 0);
        var1.define(DATA_STANCE, 1);
        var1.define(DATA_SHRINK, 0.0F);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 24.0F, 1.0F));
        WaterAvoidingRandomStrollGoal var1 = new WaterAvoidingRandomStrollGoal(this, 0.42);
        var1.setInterval(90);
        this.goalSelector.addGoal(6, var1);
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
    }

    private boolean stride(Vec3 var1, double var2) {
        return this.getNavigation().isInProgress()
                && this.getNavigation().getTargetPos() != null
                && this.getNavigation().getTargetPos().distToCenterSqr(var1.x, var1.y, var1.z) < 4.0
            ? true
            : this.getNavigation().moveTo(var1.x, var1.y, var1.z, var2);
    }

    private Vec3 standOff(LivingEntity var1, double var2) {
        Vec3 var4 = this.position().subtract(var1.position());
        if (var4.horizontalDistanceSqr() < 0.01) {
            var4 = new Vec3(1.0, 0.0, 0.0);
        }

        var4 = new Vec3(var4.x, 0.0, var4.z).normalize();
        double var5 = (double)(this.random.nextBoolean() ? 1 : -1) * (0.35 + this.random.nextDouble() * 0.5);
        double var7 = Math.cos(var5);
        double var9 = Math.sin(var5);
        Vec3 var11 = new Vec3(var4.x * var7 - var4.z * var9, 0.0, var4.x * var9 + var4.z * var7);
        return var1.position().add(var11.scale(var2));
    }

    public void assign(BlockPos var1) {
        this.tower = var1;
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_NAME, MageNames.of(var1));
        }
    }

    public boolean roused() {
        return entityData.get(DATA_ROUSED);
    }

    public float castLight() {
        return entityData.get(DATA_CAST);
    }

    public String mageName() {
        String var1 = (String)this.entityData.get(DATA_NAME);
        return !var1.isEmpty() ? var1 : MageNames.of(this.tower.equals(BlockPos.ZERO) ? this.blockPosition() : this.tower);
    }

    public InteractionResult mobInteract(Player var1, InteractionHand var2) {
        if (this.roused() && this.kept() == 0) {
            return InteractionResult.PASS;
        } else if (!this.level().isClientSide) {
            if (this.kept() == 0 && var1 instanceof ServerPlayer var3) {
                this.greet(var3);
            }

            return InteractionResult.SUCCESS;
        } else {
            WakingWorld.hooks.openMage(this);
            return InteractionResult.SUCCESS;
        }
    }

    private void greet(ServerPlayer var1) {
        if (this.level() instanceof ServerLevel var2) {
            var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_AMBIENT, SoundSource.NEUTRAL, 1.2F, 0.7F);
            Cataclysms.puff(var2, ParticleTypes.WITCH, this.getX(), this.getY() + 1.8, this.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
        }

        this.met.add(var1.getUUID());
        if (!has(var1)) {
            ItemStack var4 = new ItemStack((ItemLike)MageBlocks.RITE_STONE_ITEM.get());
            if (!var1.addItem(var4)) {
                var1.drop(var4, false);
            }

            var1.sendSystemMessage(Component.translatable("entity.wakingworld.dark_mage.stone").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static boolean has(ServerPlayer player) {
        for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
            if (s.is(MageBlocks.RITE_STONE_ITEM.get())) return true;
        }
        return false;
    }

    /** Who he has already introduced himself to; forgotten on a restart, which costs nothing. */
    private final java.util.Set<java.util.UUID> met = new java.util.HashSet<>();
    private static final int FOLD = 70;
    private final List<Integer> shell = new ArrayList<>();
    private int keystones;
    private static final int CHANGE = 50;
    private static final int SHELL_STONES = 14;
    private static final int SPELL_TURN = 0;
    private static final int SPELL_RING = 1;
    private static final int SPELL_STAR = 2;
    private static final int SPELL_CHAIN = 3;
    private static final int SPELL_WELL = 4;
    private static final int SPELL_STORM = 5;
    private static final int SPELL_LAST = 6;
    private static final int SPELL_CALL = 7;
    private static final int SENTINELS = 3;
    private int callCooldown = 300;
    private final List<Integer> called = new ArrayList<>();
    private int lastWordCooldown = 200;
    private static final int STORM_STARS = 7;
    private static final double STORM_R = 6.5;
    private int alone;
    private static final int CHANNEL = 160;
    private static final float CHANNEL_BREAK = 55.0F;

    public boolean hurt(DamageSource var1, float var2) {
        if (var1.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(var1, var2);
        } else if (this.kept() != 0) {
            return false;
        } else {
            if (!this.level().isClientSide && !this.roused()) {
                if (!(var1.getEntity() instanceof Player)) {
                    return false;
                }

                this.rouse();
            }

            if (this.changing > 0 && !var1.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else if (this.keystones > 0 && !var1.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                if (this.level() instanceof ServerLevel var7 && this.tickCount % 4 == 0) {
                    var7.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.4F, 1.6F);
                    Cataclysms.runes(var7, stageColour(this.stage), 0.9F, this.getX(), this.getY() + 1.1, this.getZ(), 6, 0.7, 0.8, 0.7);
                    if (var1.getEntity() instanceof ServerPlayer var10) {
                        var10.displayClientMessage(
                            Component.translatable("entity.wakingworld.dark_mage.ward.held", new Object[]{this.keystones}).withStyle(ChatFormatting.GRAY), true
                        );
                    }
                }

                return false;
            } else {
                if (this.channel > 0) {
                    this.channelHurt += var2;
                }

                if (this.level() instanceof ServerLevel var3) {
                    this.ensureScar(var3);
                }

                boolean var6 = var1.is(DamageTypes.GENERIC_KILL) || var1.is(DamageTypes.FELL_OUT_OF_WORLD);
                if (this.kept() == 0 && this.shrinking == 0 && !var6 && !this.level().isClientSide && this.getHealth() - var2 <= 0.0F) {
                    this.fold((ServerLevel)this.level(), var1.getEntity() instanceof ServerPlayer var8 ? var8 : this.nearestFoe());
                    return false;
                } else {
                    return super.hurt(var1, var2);
                }
            }
        }
    }

    public boolean isInvulnerableTo(DamageSource var1) {
        return !var1.is(DamageTypes.DROWN) && !var1.is(DamageTypes.IN_WALL) && !var1.is(DamageTypes.CRAMMING) && !var1.is(DamageTypes.FALL)
            ? super.isInvulnerableTo(var1)
            : true;
    }

    private ServerPlayer nearestFoe() {
        return this.level() instanceof ServerLevel var1
            ? var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 9216.0)
                .stream()
                .min(Comparator.comparingDouble(var1x -> var1x.distanceToSqr(this)))
                .orElse(null)
            : null;
    }

    private void fold(ServerLevel var1, ServerPlayer var2) {
        this.shrinking = 70;
        this.entityData.set(DATA_SHRINK, 0.001F);
        this.setHealth(1.0F);
        this.setInvulnerable(true);
        this.setNoAi(true);
        this.setTarget(null);
        this.channel = 0;
        this.castTicks = 0;
        this.changing = 0;
        this.entityData.set(DATA_CAST, 0.0F);
        this.entityData.set(DATA_CHANGING, 0.0F);
        this.entityData.set(DATA_WARD, 0.0F);
        this.ward = 0.0F;
        this.owner = var2 == null ? null : var2.getUUID();
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_DEATH, SoundSource.HOSTILE, 7.0F, 0.7F);
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 6.0F, 1.4F);
        WakingWorld.hooks.shakeAt(this.position(), 4.5F, 60.0);

        for (ServerPlayer var4 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 4096.0)) {
            var4.sendSystemMessage(
                Component.translatable("entity.wakingworld.dark_mage.folded", new Object[]{this.mageName()}).withStyle(ChatFormatting.LIGHT_PURPLE)
            );
        }
    }

    private void foldTick(ServerLevel var1) {
        this.shrinking--;
        float var2 = 1.0F - (float)this.shrinking / 70.0F;
        this.entityData.set(DATA_SHRINK, Math.max(0.001F, var2));
        this.setDeltaMovement(0.0, 0.0, 0.0);
        int var3 = stageColour(4);

        for (int var4 = 0; var4 < 4; var4++) {
            double var5 = (double)this.shrinking * 0.4 + (double)var4 * 1.6;
            double var7 = 7.0 * (double)(1.0F - var2) + 0.2;
            Cataclysms.runes(
                var1,
                var4 % 2 == 0 ? var3 : stageColour(1),
                1.2F * (1.0F - var2) + 0.3F,
                this.getX() + Math.cos(var5) * var7,
                this.getY() + 0.4 + (double)(1.0F - var2) * 2.2,
                this.getZ() + Math.sin(var5) * var7,
                1,
                0.0,
                0.0,
                0.0
            );
        }

        if (this.shrinking % 8 == 0) {
            Cataclysms.ring(var1, var3, 6.0F * (1.0F - var2) + 0.5F, this.getX(), this.getY() + 0.12, this.getZ(), 0.6);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3.0F, 0.6F + var2 * 1.2F);
        }

        if (this.shrinking <= 0) {
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.HOSTILE, 4.0F, 0.7F);
            Cataclysms.puff(var1, ParticleTypes.FLASH, this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
            Cataclysms.runes(var1, var3, 1.6F, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.5, 0.6, 0.5);
            this.dropWard(var1, true);
            this.dismiss(var1);
            this.closeScar(var1);
            this.hoard(var1);
            ItemStack var9 = PocketMageItem.of(this.mageName(), this.tower);
            ItemEntity var10 = this.spawnAtLocation(var9);
            if (var10 != null) {
                var10.setGlowingTag(true);
                var10.setUnlimitedLifetime();
                var10.setInvulnerable(true);
                this.markJar(var1, var10.position());
            }

            if (this.owner != null) {
                ServerPlayer var6 = (ServerPlayer)var1.getPlayerByUUID(this.owner);
                if (var6 != null) {
                    ((KindTrigger)WakingTriggers.COLOSSUS_SLAIN.get()).trigger(var6, "dark_mage", 0);
                    if (WakingConfig.kingdoms() && !this.tower.equals(BlockPos.ZERO)) {
                        KingCharge.mageSlain(var1, this.tower, var6);
                    }
                }
            }

            this.bar.removeAllPlayers();
            this.discard();
        }
    }

    private void rouse() {
        this.entityData.set(DATA_ROUSED, true);
        this.bar.setVisible(true);
        this.clearRestriction();
        if (this.level() instanceof ServerLevel var1) {
            this.scar = Scars.begin(var1, this.blockPosition(), "a mage's fight");
            this.changing = 60;
            this.changeTotal = 60;
            this.entityData.set(DATA_CHANGING, 0.001F);
            this.castCooldown = 40;
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 8.0F, 0.45F);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 5.0F, 1.5F);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 7.0F, 0.5F);
            WakingWorld.hooks.shakeAt(this.position(), 5.5F, 70.0);
            Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.2, this.getZ(), 120, 0.8, 1.2, 0.8, 0.3);
            Cataclysms.puff(var1, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 90, 1.0, 1.0, 1.0, 0.06);
            Cataclysms.ring(var1, stageColour(1), 18.0F, this.getX(), this.getY() + 0.15, this.getZ(), 1.4);
            if (!this.tower.equals(BlockPos.ZERO)) {
                double var9 = (double)this.tower.getX() + 0.5;
                double var4 = (double)this.tower.getY();
                double var6 = (double)this.tower.getZ() + 0.5;

                for (int var8 = 0; var8 < 5; var8++) {
                    Cataclysms.ring(var1, 2761267, 13.0F - (float)var8 * 1.5F, var9, var4 + 0.6 + (double)var8 * 9.0, var6, 0.9);
                }

                Cataclysms.puff(var1, ParticleTypes.LARGE_SMOKE, var9, var4 + 42.0, var6, 80, 2.0, 2.0, 2.0, 0.08);
                var1.playSound(null, this.tower, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 8.0F, 0.4F);
            }

            for (ServerPlayer var3 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 8100.0)) {
                var3.sendSystemMessage(
                    Component.translatable("entity.wakingworld.dark_mage.roused", new Object[]{this.mageName()}).withStyle(ChatFormatting.DARK_PURPLE)
                );
            }
        }

        WakingWorld.LOGGER.info("mage: {} has been struck and will not be talked to again", this.mageName());
    }

    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel var1) {
            if (!this.tidied && !this.tower.equals(BlockPos.ZERO) && this.tickCount > 100 && this.tickCount % 20 == 5) {
                this.tidied = true;
                Tidy.begin(var1, this.tower, 15, 8, -10, 46);
                if (WakingConfig.kingdoms()) {
                    KingCharge.towerFound(var1, this.tower);
                }
            }

            this.setAirSupply(this.getMaxAirSupply());
            if (!this.roused() && this.kept() == 0 && !this.hasRestriction()) {
                this.restrictTo(this.blockPosition(), 7);
            }

            if (this.shrinking > 0) {
                this.foldTick(var1);
            } else if (this.kept() != 0) {
                this.service(var1);
            } else {
                if (this.roused()) {
                    if (((Optional)this.entityData.get(DATA_BAR_ID)).isEmpty()) {
                        this.entityData.set(DATA_BAR_ID, Optional.of(this.bar.getId()));
                    }

                    if (this.lastWordCooldown > 0) {
                        this.lastWordCooldown--;
                    }

                    if (this.callCooldown > 0) {
                        this.callCooldown--;
                    }

                    this.bar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
                    this.bar.setName(Component.translatable("entity.wakingworld.dark_mage.named", new Object[]{this.mageName()}));

                    for (ServerPlayer var3 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 4096.0)) {
                        this.bar.addPlayer(var3);
                    }

                    for (ServerPlayer var5 : this.bar.getPlayers().toArray(new ServerPlayer[0])) {
                        if (var5.distanceToSqr(this) > 6400.0 || var5.isRemoved()) {
                            this.bar.removePlayer(var5);
                        }
                    }

                    if (this.ward > 0.0F && this.tickCount % 2 == 0) {
                        this.wardShell(var1);
                    }

                    this.watchAlone(var1);
                    this.fight(var1);
                } else if (this.tickCount % 3 == 0) {
                    Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.15, this.getZ(), 1, 0.35, 0.05, 0.35, 0.006);
                    if (this.tickCount % 24 == 0) {
                        Cataclysms.puff(var1, ParticleTypes.WITCH, this.getX(), this.getY() + 1.7, this.getZ(), 2, 0.25, 0.2, 0.25, 0.0);
                    }
                }

                this.towerAura(var1);
            }
        }
    }

    private void service(ServerLevel var1) {
        this.entityData.set(DATA_CAST, 0.0F);
        if (this.kept() == 2) {
            this.setTarget(null);
            if (this.tickCount % 4 == 0) {
                Cataclysms.embers(var1, stageColour(1), 0.5F, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0.25, 0.05, 0.25, 0.004);
            }

            if (this.post.distSqr(this.blockPosition()) > 81.0) {
                this.teleportTo((double)this.post.getX() + 0.5, (double)this.post.getY(), (double)this.post.getZ() + 0.5);
            }
        } else {
            if (this.mendCooldown > 0) {
                this.mendCooldown--;
            }

            if (this.lampCooldown > 0) {
                this.lampCooldown--;
            }

            if (this.haulCooldown > 0) {
                this.haulCooldown--;
            }

            if (this.castCooldown > 0) {
                this.castCooldown--;
            }

            if (this.tickCount % 5 == 0) {
                Cataclysms.embers(var1, stageColour(1), 0.6F, this.getX(), this.getY() + 0.15, this.getZ(), 1, 0.3, 0.05, 0.3, 0.005);
            }

            Player var2 = this.owner == null ? null : var1.getPlayerByUUID(this.owner);
            int var3 = this.order();
            Vec3 var4 = var3 == 0 && var2 != null
                ? var2.position()
                : new Vec3((double)this.post.getX() + 0.5, (double)this.post.getY(), (double)this.post.getZ() + 0.5);

            double var5 = switch (var3) {
                case 1 -> 4.0;
                case 2 -> 22.0;
                default -> 10.0;
            };
            double var7 = this.position().distanceTo(var4);
            if (var7 > var5 * 3.5 && var3 == 0) {
                if (--this.blinkCooldown <= 0) {
                    this.step(var1, var4, 3.0);
                }
            } else if (var7 > var5) {
                double var9 = var3 == 2 ? var5 * 0.6 : Math.max(2.0, var5 * 0.5);
                Vec3 var11 = var4.subtract(this.position()).horizontalDistanceSqr() < 0.01
                    ? var4
                    : var4.subtract(var4.subtract(this.position()).normalize().scale(var9));
                if (!this.stride(var11, 1.05) && --this.blinkCooldown <= 0) {
                    this.step(var1, var4, var9);
                }
            } else if (this.getNavigation().isInProgress() && var7 < var5 * 0.6) {
                this.getNavigation().stop();
            }

            if (var2 != null && this.tickCount % 10 == 0) {
                LivingEntity var12 = this.pickFoe(var1, var2);
                this.setTarget(var12);
                if (var12 != null && this.castCooldown <= 0) {
                    this.castCooldown = 45 + this.random.nextInt(25);
                    this.helpAgainst(var1, var12);
                }
            }
        }
    }

    private void step(ServerLevel var1, Vec3 var2, double var3) {
        this.blinkCooldown = 25 + this.random.nextInt(20);

        for (int var5 = 0; var5 < 8; var5++) {
            double var6 = this.random.nextDouble() * Math.PI * 2.0;
            double var8 = var3 * (0.4 + this.random.nextDouble() * 0.6);
            double var10 = var2.x + Math.cos(var6) * var8;
            double var12 = var2.z + Math.sin(var6) * var8;
            BlockPos var14 = var1.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(var10, var2.y, var12));
            if (!(Math.abs((double)var14.getY() - var2.y) > 10.0)
                && var1.noCollision(this, this.getBoundingBox().move(var10 - this.getX(), (double)var14.getY() - this.getY(), var12 - this.getZ()))) {
                Cataclysms.runes(var1, stageColour(1), 0.9F, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.3, 0.7, 0.3);
                this.teleportTo(var10, (double)var14.getY(), var12);
                Cataclysms.runes(var1, stageColour(1), 0.9F, var10, (double)var14.getY() + 1.0, var12, 10, 0.3, 0.7, 0.3);
                var1.playSound(null, var10, (double)var14.getY(), var12, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.4F, 1.2F);
                return;
            }
        }
    }

    private LivingEntity pickFoe(ServerLevel var1, Player var2) {
        int var3 = this.stance();
        if (var3 == 0) {
            return null;
        } else {
            LivingEntity var4 = var2.getLastHurtByMob();
            if (this.alive(var4) && var4.distanceToSqr(this) < 1296.0) {
                return var4;
            } else {
                LivingEntity var5 = var2.getLastHurtMob();
                if (this.alive(var5) && var5.distanceToSqr(this) < 1296.0) {
                    return var5;
                } else {
                    LivingEntity var6 = this.getLastHurtByMob();
                    if (this.alive(var6) && var6.distanceToSqr(this) < 1296.0) {
                        return var6;
                    } else {
                        return var3 != 2 ? null : this.hunt(var1, var2);
                    }
                }
            }
        }
    }

    private LivingEntity hunt(ServerLevel var1, Player var2) {
        AABB var3 = new AABB(this.position(), this.position()).inflate(17.0);
        if (var2.distanceToSqr(this) < 1024.0) {
            var3 = var3.minmax(new AABB(var2.position(), var2.position()).inflate(17.0));
        }

        LivingEntity var4 = null;
        double var5 = Double.MAX_VALUE;

        for (LivingEntity var8 : var1.getEntitiesOfClass(LivingEntity.class, var3, this::hostile)) {
            double var9 = var8.distanceToSqr(var2);
            double var11 = var8.distanceToSqr(this);
            if ((!(var9 > 289.0) || !(var11 > 289.0)) && this.hasLineOfSight(var8)) {
                double var13 = Math.min(var9, var11 * 1.6);
                if (var13 < var5) {
                    var5 = var13;
                    var4 = var8;
                }
            }
        }

        return var4;
    }

    private boolean hostile(LivingEntity var1) {
        if (!(var1 instanceof Monster) && !(var1 instanceof Slime) && !(var1 instanceof Hoglin)) {
            return false;
        } else if (var1 instanceof ColossusEntity) {
            return false;
        } else {
            return !(var1 instanceof MageEntity) && !(var1 instanceof WitherBoss) ? this.helpable(var1) : false;
        }
    }

    private boolean alive(LivingEntity var1) {
        return var1 != null && var1.isAlive() && var1 != this && !(var1 instanceof Player) && !(var1 instanceof MageEntity);
    }

    private void helpAgainst(ServerLevel var1, LivingEntity var2) {
        this.getLookControl().setLookAt(var2, 30.0F, 30.0F);
        Vec3 var3 = var2.position();
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 2.4F, 1.1F);
        Cataclysms.ring(var1, stageColour(1), 3.0F, var3.x, var3.y + 0.15, var3.z, 0.6);

        for (int var4 = 0; var4 < 26; var4++) {
            double var5 = (double)var4 / 26.0 * Math.PI * 2.0;
            Cataclysms.puff(
                var1, ParticleTypes.SOUL_FIRE_FLAME, var3.x + Math.cos(var5) * 2.6, var3.y + 0.2, var3.z + Math.sin(var5) * 2.6, 2, 0.08, 0.35, 0.08, 0.03
            );
        }

        for (LivingEntity var8 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(var3, var3).inflate(3.2), this::helpable)) {
            var8.hurt(this.damageSources().indirectMagic(this, this), 8.0F);
            var8.igniteForSeconds(3.0F);
        }
    }

    private boolean helpable(LivingEntity var1) {
        if (var1 == this || !var1.isAlive() || var1 instanceof MageEntity) {
            return false;
        } else if (var1 instanceof Player var2) {
            return this.owner == null || !var2.getUUID().equals(this.owner);
        } else if (var1 instanceof Animal) {
            return false;
        } else if (var1 instanceof AbstractVillager) {
            return false;
        } else if (var1 instanceof AbstractGolem) {
            return false;
        } else {
            return var1 instanceof TamableAnimal
                ? false
                : !(var1 instanceof TownsfolkEntity) && !(var1 instanceof GuardEntity) && !(var1 instanceof KingEntity);
        }
    }

    private void raiseWard(ServerLevel var1, int var2) {
        this.dropWard(var1, false);
        if (var2 < 2) {
            this.entityData.set(DATA_WARD, 0.0F);
        } else {
            int var3 = var2 >= 4 ? 4 : 3;
            List var4 = this.tearUp(var1, 14);
            if (var4.isEmpty()) {
                this.entityData.set(DATA_WARD, 0.0F);
            } else {
                int var5 = var4.size();
                HashSet var6 = new HashSet();

                for (int var7 = 0; var7 < var3 && var7 < var5; var7++) {
                    var6.add((int)Math.round((double)var7 * ((double)var5 / (double)Math.min(var3, var5))) % var5);
                }

                this.keystones = 0;

                for (int var10 = 0; var10 < var5; var10++) {
                    WardStoneEntity var8 = (WardStoneEntity)((EntityType)WakingWorld.WARD_STONE.get()).create(var1);
                    if (var8 != null) {
                        boolean var9 = var6.contains(var10);
                        var8.moveTo(this.getX(), this.getY() + 1.2, this.getZ(), 0.0F, 0.0F);
                        var8.set(this, var9 ? keyBlock((BlockState)var4.get(var10)) : (BlockState)var4.get(var10), var9, var10, var5);
                        var1.addFreshEntity(var8);
                        this.shell.add(var8.getId());
                        if (var9) {
                            this.keystones++;
                        }
                    }
                }

                this.entityData.set(DATA_WARD, this.keystones > 0 ? 1.0F : 0.0F);
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 5.0F, 0.55F);
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 6.0F, 0.6F);
                WakingWorld.LOGGER.info("mage: {} raises a ward of {} stones, {} of them keystones", new Object[]{this.mageName(), var5, this.keystones});
            }
        }
    }

    private static BlockState keyBlock(BlockState var0) {
        return Blocks.AMETHYST_BLOCK.defaultBlockState();
    }

    private List<BlockState> tearUp(ServerLevel var1, int var2) {
        ArrayList var3 = new ArrayList();

        for (int var4 = 0; var4 < var2 * 6 && var3.size() < var2; var4++) {
            double var5 = this.random.nextDouble() * Math.PI * 2.0;
            double var7 = 3.0 + this.random.nextDouble() * 5.0;
            int var9 = (int)Math.round(this.getX() + Math.cos(var5) * var7);
            int var10 = (int)Math.round(this.getZ() + Math.sin(var5) * var7);
            BlockPos var11 = var1.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(var9, (int)this.getY(), var10)).below();
            if (!(Math.abs((double)var11.getY() - this.getY()) > 8.0)) {
                BlockState var12 = var1.getBlockState(var11);
                if (!var12.isAir() && !var12.hasBlockEntity() && KingdomExpansion.natural(var12) && !var12.getFluidState().isSource()) {
                    var3.add(var12);
                    Scars.writing(var1, this.scar);

                    try {
                        Scars.set(var1, var11, Blocks.AIR.defaultBlockState());
                    } finally {
                        Scars.close();
                    }

                    Cataclysms.puff(
                        var1,
                        new BlockParticleOption(ParticleTypes.BLOCK, var12),
                        (double)var11.getX() + 0.5,
                        (double)var11.getY() + 0.6,
                        (double)var11.getZ() + 0.5,
                        10,
                        0.3,
                        0.2,
                        0.3,
                        0.1
                    );
                    Cataclysms.runes(
                        var1,
                        stageColour(this.stage),
                        0.9F,
                        (double)var11.getX() + 0.5,
                        (double)var11.getY() + 1.0,
                        (double)var11.getZ() + 0.5,
                        4,
                        0.2,
                        0.3,
                        0.2
                    );
                }
            }
        }

        return var3;
    }

    void keystoneBroken(ServerLevel var1, WardStoneEntity var2) {
        this.keystones = Math.max(0, this.keystones - 1);
        this.entityData.set(DATA_WARD, this.keystones > 0 ? (float)this.keystones / 4.0F : 0.0F);
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 5.0F, 0.7F);
        Cataclysms.ring(var1, 16765562, 4.0F, var2.getX(), var2.getY(), var2.getZ(), 0.8);
        if (this.keystones > 0) {
            for (ServerPlayer var6 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 2304.0)) {
                var6.displayClientMessage(
                    Component.translatable("entity.wakingworld.dark_mage.ward.keystone", new Object[]{this.keystones}).withStyle(ChatFormatting.YELLOW), true
                );
            }
        } else {
            this.burst(var1);
            this.dropWard(var1, true);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 6.0F, 0.6F);
            Cataclysms.ring(var1, stageColour(this.stage), 6.0F, this.getX(), this.getY() + 1.0, this.getZ(), 0.9);

            for (ServerPlayer var4 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 2304.0)) {
                var4.sendSystemMessage(
                    Component.translatable("entity.wakingworld.dark_mage.ward.broken", new Object[]{this.mageName()}).withStyle(ChatFormatting.GREEN)
                );
            }
        }
    }

    private void burst(ServerLevel var1) {
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 1.3F);
        WakingWorld.hooks.shakeAt(this.position(), 3.0F, 35.0);

        for (int var3 : this.shell) {
            Entity var5 = var1.getEntity(var3);
            if (var5 instanceof WardStoneEntity) {
                WardStoneEntity var4 = (WardStoneEntity)var5;
                Vec3 var11 = var4.position().subtract(this.position()).normalize();

                for (int var6 = 1; var6 <= 8; var6++) {
                    Vec3 var7 = var4.position().add(var11.scale((double)var6 * 0.9));
                    Cataclysms.puff(var1, new BlockParticleOption(ParticleTypes.BLOCK, var4.state()), var7.x, var7.y, var7.z, 3, 0.15, 0.15, 0.15, 0.12);
                }
            }
        }

        for (LivingEntity var9 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(6.5), this::hostileTo)) {
            var9.hurt(this.damageSources().indirectMagic(this, this), 7.0F);
            Vec3 var10 = var9.position().subtract(this.position()).normalize().scale(1.3).add(0.0, 0.6, 0.0);
            var9.push(var10.x, var10.y, var10.z);
            var9.hurtMarked = true;
        }
    }

    private void dropWard(ServerLevel var1, boolean var2) {
        for (int var4 : this.shell) {
            Entity var6 = var1.getEntity(var4);
            if (var6 instanceof WardStoneEntity) {
                WardStoneEntity var5 = (WardStoneEntity)var6;
                if (var2) {
                    var5.shatter();
                } else {
                    var5.discard();
                }
            }
        }

        this.shell.clear();
        this.keystones = 0;
        this.entityData.set(DATA_WARD, 0.0F);
    }

    private void wardShell(ServerLevel var1) {
        float var2 = this.wardMax <= 0.0F ? 0.0F : this.ward / this.wardMax;
        if (!(var2 <= 0.0F)) {
            int var3 = stageColour(this.stage);
            int var4 = 2 + Math.round(var2 * 3.0F);
            double var5 = (double)this.tickCount * 0.09;

            for (int var7 = 0; var7 < var4; var7++) {
                double var8 = ((double)this.tickCount * 0.11 + (double)var7 * 2.399963) % (Math.PI * 2);
                double var10 = Math.cos(var8 * 0.5 + (double)var7);
                double var12 = Math.sqrt(Math.max(0.0, 1.0 - var10 * var10)) * 1.65;
                double var14 = var8 + var5;
                Cataclysms.runes(
                    var1,
                    var3,
                    0.8F,
                    this.getX() + Math.cos(var14) * var12,
                    this.getY() + 1.0 + var10 * 1.5,
                    this.getZ() + Math.sin(var14) * var12,
                    1,
                    0.0,
                    0.0,
                    0.0
                );
            }

            if (this.tickCount % 20 == 0) {
                Cataclysms.ring(var1, var3, 1.9F, this.getX(), this.getY() + 0.1, this.getZ(), 0.7);
            }
        }
    }

    private void towerAura(ServerLevel var1) {
        if (!this.tower.equals(BlockPos.ZERO) && !this.roused()) {
            double var2 = (double)this.tower.getX() + 0.5;
            double var4 = (double)this.tower.getY();
            double var6 = (double)this.tower.getZ() + 0.5;
            if (var1.getNearestPlayer(var2, var4 + 16.0, var6, 130.0, false) != null) {
                long var8 = (long)this.tickCount;
                int var10 = 9067208;
                int var11 = 6979288;
                int var12 = 13215487;
                if (var8 % 22L == 0L) {
                    Cataclysms.ring(var1, var10, 13.0F, var2, var4 + 0.6, var6, 0.3);
                }

                if (var8 % 22L == 11L) {
                    Cataclysms.ring(var1, var11, 10.0F, var2, var4 + 9.4, var6, 0.26);
                }

                if (var8 % 34L == 0L) {
                    Cataclysms.ring(var1, var12, 8.0F, var2, var4 + 27.4, var6, 0.22);
                }

                if (var8 % 6L == 0L) {
                    double var13 = (double)var8 * 0.9 % 460.0 / 460.0;
                    Cataclysms.ring(var1, var10, 5.5F, var2, var4 - 6.0 + var13 * 48.0, var6, 0.5);
                }

                if (var8 % 2L == 0L) {
                    for (int var24 = 0; var24 < 3; var24++) {
                        double var14 = ((double)var8 * 0.8 + (double)var24 * 120.0) % 360.0;
                        double var16 = var4 - 5.0 + var14 / 360.0 * 46.0;
                        double var18 = Math.toRadians(var14 * 2.6 + (double)var24 * 120.0);
                        double var20 = var16 - (var4 + 27.0);
                        double var22 = var20 <= 0.0 ? 7.3 : Math.max(0.7, 7.3 * (1.0 - var20 / 14.0));
                        Cataclysms.embers(
                            var1,
                            var24 == 1 ? var11 : var10,
                            0.7F,
                            var2 + Math.cos(var18) * var22,
                            var16,
                            var6 + Math.sin(var18) * var22,
                            1,
                            0.05,
                            0.05,
                            0.05,
                            0.0
                        );
                    }
                }

                if (var8 % 3L == 0L) {
                    double var25 = Math.toRadians((double)(-var8) * 1.7 % 360.0);
                    double var15 = var4 + 30.0 + Math.sin((double)var8 * 0.03) * 4.0;
                    Cataclysms.runes(var1, var12, 1.0F, var2 + Math.cos(var25) * 3.2, var15, var6 + Math.sin(var25) * 3.2, 1, 0.02, 0.02, 0.02);
                }

                if (var8 % 4L == 0L) {
                    Cataclysms.embers(var1, var12, 1.3F, var2, var4 + 42.2, var6, 2, 0.22, 0.25, 0.22, 0.01);
                    if (var8 % 40L == 0L) {
                        Cataclysms.puff(var1, ParticleTypes.ASH, var2, var4 + 38.0, var6, 8, 1.8, 1.6, 1.8, 0.0);
                    }
                }

                if (var8 % 5L == 0L) {
                    double var26 = Math.toRadians((double)var8 * 2.2 % 360.0);
                    Cataclysms.embers(var1, var10, 0.8F, var2 + Math.cos(var26) * 7.7, var4 + 0.35, var6 + Math.sin(var26) * 7.7, 1, 0.08, 0.04, 0.08, 0.006);
                    if (var8 % 25L == 0L) {
                        Cataclysms.runes(
                            var1, var10, 0.9F, var2 + Math.cos(var26 + 2.1) * 8.6, var4 + 1.1, var6 + Math.sin(var26 + 2.1) * 8.6, 1, 0.3, 0.25, 0.3
                        );
                    }
                }
            }
        }
    }

    private int stageFor(float var1) {
        float var2 = var1 / this.getMaxHealth();
        int var3 = var2 > 0.7F ? 1 : (var2 > 0.4F ? 2 : (var2 > 0.15F ? 3 : 4));
        return Math.max(this.stage, var3);
    }

    public int stage() {
        return (Integer)this.entityData.get(DATA_STAGE);
    }

    public Optional<UUID> barId() {
        return (Optional<UUID>)this.entityData.get(DATA_BAR_ID);
    }

    public int kept() {
        return (Integer)this.entityData.get(DATA_KEPT);
    }

    public int order() {
        return (Integer)this.entityData.get(DATA_ORDER);
    }

    public float shrinking() {
        return (Float)this.entityData.get(DATA_SHRINK);
    }

    public UUID owner() {
        return this.owner;
    }

    public void keep(UUID var1, BlockPos var2) {
        this.owner = var1;
        this.post = var2;
        this.clearRestriction();
        this.entityData.set(DATA_KEPT, 1);
        this.entityData.set(DATA_ROUSED, false);
        this.entityData.set(DATA_ORDER, 0);
        this.entityData.set(DATA_STANCE, 2);
        this.mendCooldown = 0;
        this.lampCooldown = 0;
        this.haulCooldown = 0;
        this.setTarget(null);
        this.bar.setVisible(false);
        this.bar.removeAllPlayers();
        this.setHealth(this.getMaxHealth());
        this.stage = 1;
        this.entityData.set(DATA_STAGE, 1);
        this.ward = 0.0F;
        this.wardMax = 0.0F;
        this.entityData.set(DATA_WARD, 0.0F);
    }

    public void cage(BlockPos var1) {
        this.post = var1;
        this.owner = null;
        this.entityData.set(DATA_KEPT, 2);
        this.entityData.set(DATA_ROUSED, false);
        this.entityData.set(DATA_ORDER, 1);
        this.setTarget(null);
        this.bar.setVisible(false);
        this.bar.removeAllPlayers();
        this.setHealth(this.getMaxHealth());
        this.setNoAi(false);
    }

    public void foldBack(ServerPlayer var1) {
        if (this.level() instanceof ServerLevel var2) {
            PocketMageItem.unfold(var2, this.blockPosition());
            ItemStack var4 = PocketMageItem.of(this.mageName(), this.tower);
            if (!var1.addItem(var4)) {
                var1.drop(var4, false);
            }

            var1.displayClientMessage(
                Component.translatable("item.wakingworld.pocket_mage.back", new Object[]{this.mageName()}).withStyle(ChatFormatting.GRAY), true
            );
            this.discard();
        }
    }

    public void order(int var1) {
        this.entityData.set(DATA_ORDER, var1);
        if (var1 != 0) {
            this.post = this.blockPosition();
        }
    }

    public int stance() {
        return (Integer)this.entityData.get(DATA_STANCE);
    }

    public void stance(int var1) {
        this.entityData.set(DATA_STANCE, Mth.clamp(var1, 0, 2));
        if (var1 == 0) {
            this.setTarget(null);
        }
    }

    public void commanded(ServerPlayer var1, int var2) {
        if (this.level() instanceof ServerLevel var3) {
            switch (var2) {
                case 0:
                    this.order(0);
                    if (this.distanceToSqr(var1) > 49.0) {
                        this.step(var3, var1.position(), 2.5);
                    }
                    break;
                case 1:
                case 2:
                    this.order(var2);
                    break;
                case 3:
                    this.stance(0);
                    break;
                case 4:
                    this.stance(1);
                    break;
                case 5:
                    this.stance(2);
                    break;
                case 6:
                    if (!this.mend(var1)) {
                        this.say(var1, "item.wakingworld.pocket_mage.soon", left(this.mendCooldown));
                    }
                    break;
                case 7:
                    if (!this.lamp(var1)) {
                        this.say(var1, "item.wakingworld.pocket_mage.soon", left(this.lampCooldown));
                    }
                    break;
                case 8:
                    int var5 = this.haul(var1);
                    if (var5 < 0) {
                        this.say(var1, "item.wakingworld.pocket_mage.soon", left(this.haulCooldown));
                    } else if (var5 == 0) {
                        this.say(var1, "item.wakingworld.pocket_mage.nothing");
                    } else {
                        this.say(var1, "item.wakingworld.pocket_mage.hauled", Component.literal(String.valueOf(var5)));
                    }
                    break;
                case 9:
                    this.foldBack(var1);
            }
        }
    }

    private void say(ServerPlayer var1, String var2, Object... var3) {
        var1.displayClientMessage(Component.translatable(var2, var3).withStyle(ChatFormatting.GRAY), true);
    }

    private static Component left(int var0) {
        return Component.literal(String.valueOf(Math.max(1, (var0 + 19) / 20)));
    }

    public boolean mend(ServerPlayer var1) {
        if (!(this.level() instanceof ServerLevel var2)) {
            return false;
        } else if (this.mendCooldown > 0) {
            return false;
        } else {
            this.mendCooldown = 900;
            this.getLookControl().setLookAt(var1, 30.0F, 30.0F);
            this.entityData.set(DATA_CAST, 0.35F);
            var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 2.0F, 1.5F);
            var2.playSound(null, var1.getX(), var1.getY(), var1.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.6F, 1.2F);
            Vec3 var7 = this.position().add(0.0, 1.4, 0.0);
            Vec3 var4 = var1.position().add(0.0, 1.0, 0.0);

            for (int var5 = 0; var5 <= 20; var5++) {
                Vec3 var6 = var7.lerp(var4, (double)var5 / 20.0);
                Cataclysms.embers(var2, 13082879, 0.8F, var6.x, var6.y, var6.z, 1, 0.08, 0.08, 0.08, 0.0);
            }

            Cataclysms.ring(var2, 13082879, 2.4F, var1.getX(), var1.getY() + 0.1, var1.getZ(), 0.7);
            Cataclysms.runes(var2, 13082879, 1.0F, var1.getX(), var1.getY() + 1.0, var1.getZ(), 22, 0.4, 0.8, 0.4);
            var1.heal(8.0F);
            var1.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 240, 1, false, true, true));
            var1.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 0, false, true, true));
            var1.removeEffect(MobEffects.POISON);
            var1.removeEffect(MobEffects.WITHER);
            var1.clearFire();
            return true;
        }
    }

    public boolean lamp(ServerPlayer var1) {
        if (!(this.level() instanceof ServerLevel var2)) {
            return false;
        } else if (this.lampCooldown > 0) {
            return false;
        } else {
            this.lampCooldown = 400;
            this.entityData.set(DATA_CAST, 0.3F);
            var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.NEUTRAL, 2.0F, 1.4F);
            Cataclysms.ring(var2, stageColour(1), 6.0F, this.getX(), this.getY() + 0.1, this.getZ(), 0.9);

            for (int var8 = 0; var8 < 40; var8++) {
                double var4 = (double)var8 / 40.0 * Math.PI * 2.0;
                double var6 = 3.0 + this.random.nextDouble() * 9.0;
                Cataclysms.embers(
                    var2,
                    16765562,
                    0.9F,
                    this.getX() + Math.cos(var4) * var6,
                    this.getY() + 0.5 + this.random.nextDouble() * 3.0,
                    this.getZ() + Math.sin(var4) * var6,
                    1,
                    0.1,
                    0.2,
                    0.1,
                    0.01
                );
            }

            for (ServerPlayer var10 : var2.getPlayers(var1x -> var1x.distanceToSqr(this) < 400.0)) {
                var10.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0, false, false, true));
            }

            var1.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0, false, false, true));
            return true;
        }
    }

    public int haul(ServerPlayer var1) {
        if (!(this.level() instanceof ServerLevel var2)) {
            return -1;
        } else if (this.haulCooldown > 0) {
            return -1;
        } else {
            this.haulCooldown = 120;
            this.entityData.set(DATA_CAST, 0.3F);
            Vec3 var11 = var1.position().add(0.0, 0.6, 0.0);
            int var4 = 0;

            for (ItemEntity var6 : var2.getEntitiesOfClass(
                ItemEntity.class, new AABB(this.position(), this.position()).inflate(14.0), var0 -> var0.isAlive() && !var0.hasPickUpDelay()
            )) {
                Vec3 var7 = var6.position();
                Cataclysms.embers(var2, stageColour(1), 0.7F, var7.x, var7.y + 0.2, var7.z, 3, 0.1, 0.1, 0.1, 0.01);
                Vec3 var8 = var11.subtract(var7);
                double var9 = Math.max(1.0, var8.length());
                var6.setDeltaMovement(var8.scale(0.34 / var9).add(0.0, Math.min(0.42, 0.12 + var9 * 0.02), 0.0));
                var6.hasImpulse = true;
                var6.setNoGravity(false);
                if (++var4 >= 96) {
                    break;
                }
            }

            if (var4 > 0) {
                var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.NEUTRAL, 1.8F, 1.3F);
                Cataclysms.ring(var2, stageColour(1), 4.6F, this.getX(), this.getY() + 0.1, this.getZ(), 0.7);
            } else {
                var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.2F, 0.8F);
            }

            return var4;
        }
    }

    public int mendIn() {
        return this.mendCooldown;
    }

    public int lampIn() {
        return this.lampCooldown;
    }

    public float changing() {
        return (Float)this.entityData.get(DATA_CHANGING);
    }

    public float ward() {
        return (Float)this.entityData.get(DATA_WARD);
    }

    public float channelProgress() {
        return (Float)this.entityData.get(DATA_CHANNEL);
    }

    private void enter(ServerLevel var1, int var2) {
        this.stage = var2;
        this.entityData.set(DATA_STAGE, var2);
        this.second = var2 >= 2;
        this.castCooldown = Math.min(this.castCooldown, 30);
        this.castTicks = 0;
        this.entityData.set(DATA_CAST, 0.0F);
        this.changing = 50;
        this.changeTotal = 50;
        this.entityData.set(DATA_CHANGING, 0.001F);
        this.raiseWard(var1, var2);
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 8.0F, 0.5F - (float)var2 * 0.04F);
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 6.0F, 0.6F);
        WakingWorld.hooks.shakeAt(this.position(), 2.0F + (float)var2, 60.0);

        for (ServerPlayer var4 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 4096.0)) {
            var4.sendSystemMessage(
                Component.translatable("entity.wakingworld.dark_mage.stage." + var2, new Object[]{this.mageName()}).withStyle(ChatFormatting.DARK_PURPLE)
            );
        }

        WakingWorld.LOGGER.info("mage: {} enters stage {}", this.mageName(), var2);
    }

    private void changeTick(ServerLevel var1) {
        this.changing--;
        float var2 = 1.0F - (float)this.changing / (float)this.changeTotal;
        this.entityData.set(DATA_CHANGING, Math.max(0.001F, var2));
        this.setDeltaMovement(0.0, 0.02, 0.0);
        int var3 = stageColour(Math.max(1, this.stage - 1));
        int var4 = stageColour(this.stage);

        for (int var5 = 0; var5 < 3; var5++) {
            double var6 = (double)this.changing * 0.5 + (double)var5 * 2.1;
            double var8 = 0.6 + (double)var2 * 4.5;
            Cataclysms.embers(
                var1,
                var3,
                1.1F,
                this.getX() + Math.cos(var6) * var8,
                this.getY() + 0.4 + (double)var2 * 2.4,
                this.getZ() + Math.sin(var6) * var8,
                1,
                0.05,
                0.05,
                0.05,
                0.03
            );
        }

        for (int var10 = 0; var10 < 3; var10++) {
            double var13 = (double)(-this.changing) * 0.62 + (double)var10 * 2.1;
            double var16 = 6.5 * (double)(1.0F - var2) + 0.5;
            Cataclysms.runes(
                var1,
                var4,
                1.3F,
                this.getX() + Math.cos(var13) * var16,
                this.getY() + 0.3 + (double)var2 * 2.0,
                this.getZ() + Math.sin(var13) * var16,
                1,
                0.03,
                0.03,
                0.03
            );
        }

        if (this.changing % 6 == 0) {
            Cataclysms.ring(var1, var4, 2.5F + 6.0F * var2, this.getX(), this.getY() + 0.15, this.getZ(), 0.5);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3.0F, 0.5F + var2);
        }

        if (this.changing <= 0) {
            this.entityData.set(DATA_CHANGING, 0.0F);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 7.0F, 0.7F);
            WakingWorld.hooks.shakeAt(this.position(), 4.0F, 40.0);
            Cataclysms.ring(var1, var4, 14.0F, this.getX(), this.getY() + 0.2, this.getZ(), 1.1);
            Cataclysms.runes(var1, var4, 2.4F, this.getX(), this.getY() + 1.4, this.getZ(), 60, 1.6, 1.6, 1.6);

            for (int var11 = 0; var11 < 70; var11++) {
                double var14 = (double)var11 / 70.0 * Math.PI * 2.0;
                Cataclysms.puff(
                    var1,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + Math.cos(var14) * 5.0,
                    this.getY() + 0.4,
                    this.getZ() + Math.sin(var14) * 5.0,
                    2,
                    0.1,
                    1.1,
                    0.1,
                    0.2
                );
            }

            for (LivingEntity var15 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(6.0), this::hostileTo)) {
                Vec3 var7 = var15.position().subtract(this.position()).normalize().scale(1.1).add(0.0, 0.5, 0.0);
                var15.push(var7.x, var7.y, var7.z);
                var15.hurtMarked = true;
            }
        }
    }

    public static int stageColour(int var0) {
        return switch (var0) {
            case 1 -> 9067208;
            case 2 -> 5941448;
            case 3 -> 13130400;
            default -> 14704698;
        };
    }

    private void fight(ServerLevel var1) {
        int var2 = this.stageFor(this.getHealth());
        if (var2 != this.stage) {
            this.enter(var1, var2);
        }

        if (this.changing > 0) {
            this.changeTick(var1);
        } else if (this.channel > 0) {
            this.channelTick(var1);
        } else {
            LivingEntity var3 = this.getTarget();
            if (var3 != null && var3.isAlive() && !(var3.distanceToSqr(this) > 4096.0)) {
                this.getLookControl().setLookAt(var3, 30.0F, 30.0F);
                if (this.castTicks > 0) {
                    this.castTicks--;
                    this.entityData.set(DATA_CAST, 1.0F - (float)this.castTicks / (float)this.windUp());
                    this.telegraph(var1);
                    if (this.castTicks == 0) {
                        this.release(var1, var3);
                    }
                } else {
                    double var4 = (double)var3.distanceTo(this);
                    if (--this.blinkCooldown > 0 || !(var4 < 5.0) && !(var4 > 26.0)) {
                        if (this.tickCount % 12 == 0 && this.castTicks == 0) {
                            if (var4 > 17.0) {
                                this.stride(var3.position(), 0.9);
                            } else if (var4 < 8.0) {
                                this.stride(this.standOff(var3, 12.0), 1.0);
                            } else if (this.random.nextInt(3) == 0) {
                                this.stride(this.standOff(var3, 11.0 + this.random.nextDouble() * 3.0), 0.75);
                            }
                        }
                    } else {
                        this.blink(var1, var3);
                    }

                    if (--this.castCooldown <= 0) {
                        this.spell = this.choose(var3);
                        this.castAt = var3.position();
                        this.stormAt = this.castAt;
                        this.castTicks = this.windUp();
                        this.castCooldown = Math.max(22, (this.stage >= 3 ? 34 : (this.stage == 2 ? 44 : 70)) + this.random.nextInt(this.stage >= 3 ? 18 : 34));
                        this.entityData.set(DATA_CAST, 0.02F);
                        var1.playSound(
                            null,
                            this.getX(),
                            this.getY(),
                            this.getZ(),
                            SoundEvents.EVOKER_CAST_SPELL,
                            SoundSource.HOSTILE,
                            4.0F,
                            0.7F + this.random.nextFloat() * 0.2F
                        );
                        Cataclysms.ring(var1, stageColour(this.stage), 1.4F, this.getX(), this.getY() + 0.1, this.getZ());
                    }
                }
            } else {
                this.entityData.set(DATA_CAST, 0.0F);
            }
        }
    }

    private int choose(LivingEntity var1) {
        double var2 = (double)var1.distanceTo(this);
        int var4 = var2 < 6.0 ? 0 : (var2 < 18.0 ? 1 : 2);
        if (this.stage >= 4 && this.channelReady()) {
            return 6;
        } else if (this.stage >= 2 && this.callCooldown <= 0 && this.living() < 3) {
            return 7;
        } else if (this.stage >= 3 && this.random.nextInt(100) < 34) {
            return 5;
        } else if (this.stage >= 2 && this.random.nextInt(100) < 32) {
            return var2 > 12.0 ? 3 : 4;
        } else {
            return var4;
        }
    }

    private boolean channelReady() {
        return this.lastWordCooldown <= 0;
    }

    private int windUp() {
        return switch (this.spell) {
            case 3 -> 20;
            case 4 -> 24;
            case 5 -> this.stage >= 4 ? 26 : 32;
            case 6 -> 30;
            case 7 -> 26;
            default -> this.stage >= 3 ? 18 : (this.stage >= 2 ? 22 : 30);
        };
    }

    private void telegraph(ServerLevel var1) {
        float var2 = 1.0F - (float)this.castTicks / (float)this.windUp();
        Vec3 var3 = this.position().add(0.0, 1.5, 0.0);
        int var4 = stageColour(this.stage);
        Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var3.x, var3.y, var3.z, 4, 0.3, 0.3, 0.3, 0.02);
        switch (this.spell) {
            case 0:
                double var18 = 7.2 * (1.0 - (double)var2 * 0.55);

                for (int var27 = 0; var27 < 18; var27++) {
                    double var30 = (double)var27 / 18.0 * Math.PI * 2.0 + (double)var2 * 2.0;
                    Cataclysms.puff(
                        var1,
                        ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + Math.cos(var30) * var18,
                        this.getY() + 0.25,
                        this.getZ() + Math.sin(var30) * var18,
                        1,
                        0.02,
                        0.02,
                        0.02,
                        0.01
                    );
                }
                break;
            case 1:
                for (int var17 = 0; var17 < 20; var17++) {
                    double var24 = (double)var17 / 20.0 * Math.PI * 2.0 - (double)var2 * 1.6;
                    Cataclysms.puff(
                        var1,
                        ParticleTypes.SOUL_FIRE_FLAME,
                        this.castAt.x + Math.cos(var24) * 3.4,
                        this.castAt.y + 0.2,
                        this.castAt.z + Math.sin(var24) * 3.4,
                        1,
                        0.02,
                        0.15,
                        0.02,
                        0.02
                    );
                }

                if (this.castTicks % 5 == 0) {
                    Cataclysms.ring(var1, var4, 3.4F, this.castAt.x, this.castAt.y + 0.15, this.castAt.z);
                }
                break;
            case 2:
            default:
                Vec3 var16 = this.castAt.add(0.0, 0.3, 0.0).subtract(var3).scale(0.08333333333333333);

                for (int var23 = 1; var23 <= 12; var23++) {
                    Vec3 var26 = var3.add(var16.scale((double)var23));
                    if (var1.random.nextFloat() < 0.5F + var2 * 0.5F) {
                        Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var26.x, var26.y, var26.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
                break;
            case 3:
                Vec3 var15 = this.castAt.add(0.0, 0.8, 0.0).subtract(var3).scale(0.0625);

                for (int var22 = 1; var22 <= 16; var22++) {
                    Vec3 var25 = var3.add(var15.scale((double)var22));
                    if (var1.random.nextFloat() < 0.35F + var2 * 0.65F) {
                        Cataclysms.runes(var1, var4, 0.7F, var25.x, var25.y, var25.z, 1, 0.05, 0.05, 0.05);
                    }
                }
                break;
            case 4:
                Vec3 var14 = this.castAt.subtract(this.position()).scale(0.125);

                for (int var21 = 1; var21 <= 8; var21++) {
                    Vec3 var7 = this.position().add(var14.scale((double)var21));
                    Cataclysms.ring(var1, var4, 1.1F + 0.5F * var2, var7.x, var7.y + 0.12, var7.z);
                }
                break;
            case 5:
                for (int var13 = 0; var13 < 7; var13++) {
                    double var20 = (double)var13 / 7.0 * Math.PI * 2.0 + (double)var2 * 0.8;
                    double var29 = this.stormAt.x + Math.cos(var20) * 6.5;
                    double var31 = this.stormAt.z + Math.sin(var20) * 6.5;
                    Cataclysms.ring(var1, var4, 2.0F, var29, this.stormAt.y + 0.15, var31);
                    Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var29, this.stormAt.y + 0.3, var31, 1, 0.15, 0.4, 0.15, 0.03);
                }
                break;
            case 6:
                for (int var12 = 0; var12 < 6; var12++) {
                    double var19 = var1.random.nextDouble() * Math.PI * 2.0;
                    double var28 = 6.0 * (1.0 - (double)var2);
                    Cataclysms.runes(
                        var1,
                        var4,
                        1.4F,
                        this.getX() + Math.cos(var19) * var28,
                        this.getY() + 0.5 + var1.random.nextDouble() * 2.0,
                        this.getZ() + Math.sin(var19) * var28,
                        1,
                        0.0,
                        0.0,
                        0.0
                    );
                }
                break;
            case 7:
                for (int var5 = 0; var5 < 3; var5++) {
                    double var6 = Math.toRadians((double)var5 * 120.0 + (double)var2 * 90.0);
                    double var8 = this.getX() + Math.cos(var6) * 4.5;
                    double var10 = this.getZ() + Math.sin(var6) * 4.5;
                    Cataclysms.ring(var1, var4, 1.6F, var8, this.getY() + 0.12, var10, 0.5);
                    Cataclysms.runes(var1, var4, 1.0F, var8, this.getY() + 0.4 + (double)var2 * 1.2, var10, 1, 0.15, 0.2, 0.15);
                }
        }
    }

    private void blink(ServerLevel var1, LivingEntity var2) {
        this.blinkCooldown = (this.stage >= 3 ? 70 : 140) + this.random.nextInt(this.stage >= 3 ? 40 : 80);
        Vec3 var3 = this.position();

        for (int var4 = 0; var4 < 8; var4++) {
            double var5 = this.random.nextDouble() * Math.PI * 2.0;
            double var7 = 9.0 + this.random.nextDouble() * 6.0;
            double var9 = var2.getX() + Math.cos(var5) * var7;
            double var11 = var2.getZ() + Math.sin(var5) * var7;
            BlockPos var13 = var1.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(var9, var2.getY(), var11));
            if (!(Math.abs((double)var13.getY() - var2.getY()) > 12.0)
                && var1.noCollision(this, this.getBoundingBox().move(var9 - this.getX(), (double)var13.getY() - this.getY(), var11 - this.getZ()))) {
                var1.playSound(null, var3.x, var3.y, var3.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3.0F, 0.7F);
                Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var3.x, var3.y + 1.0, var3.z, 60, 0.4, 0.9, 0.4, 0.2);
                Cataclysms.runes(var1, stageColour(this.stage), 1.2F, var3.x, var3.y + 1.0, var3.z, 14, 0.4, 0.9, 0.4);
                this.teleportTo(var9, (double)var13.getY(), var11);
                Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var9, (double)var13.getY() + 1.0, var11, 60, 0.4, 0.9, 0.4, 0.2);
                Cataclysms.runes(var1, stageColour(this.stage), 1.2F, var9, (double)var13.getY() + 1.0, var11, 14, 0.4, 0.9, 0.4);
                var1.playSound(null, var9, (double)var13.getY(), var11, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3.0F, 1.1F);
                return;
            }
        }
    }

    private void release(ServerLevel var1, LivingEntity var2) {
        this.entityData.set(DATA_CAST, 0.0F);
        Vec3 var3 = this.castAt.equals(Vec3.ZERO) ? var2.position() : this.castAt;
        int var4 = stageColour(this.stage);
        switch (this.spell) {
            case 0:
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0F, 1.4F);
                Cataclysms.puff(var1, ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                Cataclysms.ring(var1, var4, 7.0F, this.getX(), this.getY() + 0.2, this.getZ());
                WakingWorld.hooks.shakeAt(this.position(), 2.6F, 40.0);

                for (LivingEntity var25 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(7.0), this::hostileTo)) {
                    var25.hurt(this.damageSources().indirectMagic(this, this), 9.0F);
                    Vec3 var28 = var25.position().subtract(this.position()).normalize().scale(1.5).add(0.0, 0.9, 0.0);
                    var25.push(var28.x, var28.y, var28.z);
                    var25.hurtMarked = true;
                }
                break;
            case 1:
                var1.playSound(null, var3.x, var3.y, var3.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 4.0F, 0.6F);

                for (int var17 = 0; var17 < 40; var17++) {
                    double var23 = (double)var17 / 40.0 * Math.PI * 2.0;
                    Cataclysms.puff(
                        var1,
                        ParticleTypes.SOUL_FIRE_FLAME,
                        var3.x + Math.cos(var23) * 3.2,
                        var3.y + 0.2,
                        var3.z + Math.sin(var23) * 3.2,
                        3,
                        0.1,
                        0.4,
                        0.1,
                        0.03
                    );
                }

                Cataclysms.ring(var1, var4, 3.4F, var3.x, var3.y + 0.15, var3.z);

                for (LivingEntity var24 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(var3, var3).inflate(4.0), this::hostileTo)) {
                    var24.hurt(this.damageSources().indirectMagic(this, this), 11.0F);
                    var24.igniteForSeconds(4.0F);
                }

                WakingWorld.hooks.shakeAt(var3, 1.8F, 30.0);
                break;
            case 2:
            default:
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 5.0F, 0.5F);

                for (int var16 = 0; var16 < (this.stage >= 3 ? 3 : (this.stage >= 2 ? 2 : 1)); var16++) {
                    MeteorEntity var22 = (MeteorEntity)((EntityType)WakingWorld.METEOR.get()).create(var1);
                    if (var22 != null) {
                        var22.setSize(1);
                        var22.setCarriesStar(false);
                        var22.spare(this);
                        if (this.scar != null) {
                            var22.useScar(this.scar);
                        }

                        Vec3 var27 = var16 == 0 ? var3 : var3.add(this.random.nextGaussian() * 3.5, 0.0, this.random.nextGaussian() * 3.5);
                        var22.aimAt(var27, 46.0, 18.0, 1.5);
                        var1.addFreshEntity(var22);
                    }
                }
                break;
            case 3:
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 5.0F, 0.5F);

                for (LivingEntity var21 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(var3, var3).inflate(3.0), this::hostileTo)) {
                    Vec3 var26 = this.position().subtract(var21.position());
                    double var30 = Math.max(1.0, var26.length());
                    Vec3 var10 = var26.scale(1.0 / var30).scale(Math.min(2.2, var30 * 0.22)).add(0.0, 0.35, 0.0);
                    var21.push(var10.x, var10.y, var10.z);
                    var21.hurtMarked = true;
                    var21.hurt(this.damageSources().indirectMagic(this, this), 6.0F);
                    var21.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    Vec3 var11 = var21.position().add(0.0, 1.0, 0.0).subtract(this.position().add(0.0, 1.5, 0.0)).scale(0.07142857142857142);

                    for (int var12 = 1; var12 <= 14; var12++) {
                        Vec3 var13 = this.position().add(0.0, 1.5, 0.0).add(var11.scale((double)var12));
                        Cataclysms.runes(var1, var4, 1.0F, var13.x, var13.y, var13.z, 2, 0.06, 0.06, 0.06);
                    }
                }
                break;
            case 4:
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 5.0F, 0.6F);
                Vec3 var14 = var3.subtract(this.position()).scale(0.125);

                for (int var20 = 1; var20 <= 8; var20++) {
                    Vec3 var7 = this.position().add(var14.scale((double)var20));
                    Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var7.x, var7.y + 0.4, var7.z, 24, 0.35, 1.6, 0.35, 0.16);
                    Cataclysms.ring(var1, var4, 2.0F, var7.x, var7.y + 0.1, var7.z);

                    for (LivingEntity var31 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(var7, var7).inflate(2.0, 3.0, 2.0), this::hostileTo)) {
                        var31.hurt(this.damageSources().indirectMagic(this, this), 7.0F);
                        var31.push(0.0, 0.45, 0.0);
                        var31.hurtMarked = true;
                    }
                }

                WakingWorld.hooks.shakeAt(var3, 2.0F, 30.0);
                break;
            case 5:
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 6.0F, 0.5F);

                for (int var5 = 0; var5 < 7; var5++) {
                    double var6 = (double)var5 / 7.0 * Math.PI * 2.0;
                    Vec3 var8 = new Vec3(var3.x + Math.cos(var6) * 6.5, var3.y, var3.z + Math.sin(var6) * 6.5);
                    MeteorEntity var9 = (MeteorEntity)((EntityType)WakingWorld.METEOR.get()).create(var1);
                    if (var9 != null) {
                        var9.setSize(1);
                        var9.setCarriesStar(false);
                        var9.spare(this);
                        if (this.scar != null) {
                            var9.useScar(this.scar);
                        }

                        var9.aimAt(var8, (double)(40 + var5), 14.0, 1.7);
                        var1.addFreshEntity(var9);
                    }
                }
                break;
            case 6:
                this.beginChannel(var1);
                break;
            case 7:
                this.call(var1, var2);
        }
    }

    private void markJar(ServerLevel var1, Vec3 var2) {
        Cataclysms.ring(var1, stageColour(1), 5.0F, var2.x, var2.y + 0.06, var2.z, 0.35);
        Cataclysms.ring(var1, stageColour(4), 2.4F, var2.x, var2.y + 0.08, var2.z, 0.25);

        for (int var3 = 0; var3 < 40; var3++) {
            double var4 = var2.y + 0.2 + (double)var3 * 0.22;
            Cataclysms.runes(var1, var3 % 3 == 0 ? stageColour(4) : stageColour(1), 1.0F, var2.x, var4, var2.z, 1, 0.12, 0.02, 0.12);
        }

        Cataclysms.embers(var1, stageColour(1), 1.2F, var2.x, var2.y + 0.5, var2.z, 30, 0.3, 0.5, 0.3, 0.04);
        var1.playSound(null, BlockPos.containing(var2), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3.0F, 1.4F);
    }

    private void closeScar(ServerLevel var1) {
        if (this.scar != null) {
            Scars.done(var1, this.scar);
            this.scar = null;
        }
    }

    public void remove(RemovalReason var1) {
        if (this.level() instanceof ServerLevel var2) {
            this.closeScar(var2);
        }

        this.bar.setVisible(false);
        this.bar.removeAllPlayers();
        super.remove(var1);
    }

    private void watchAlone(ServerLevel var1) {
        if (var1.getNearestPlayer(this, 100.0) != null) {
            this.alone = 0;
        } else {
            if (this.scar != null && ++this.alone > 600) {
                this.closeScar(var1);
                this.alone = 0;
            }
        }
    }

    private void ensureScar(ServerLevel var1) {
        if (this.scar == null && this.roused() && this.shrinking == 0) {
            this.scar = Scars.begin(var1, this.blockPosition(), "a mage's fight");
        }
    }

    private int living() {
        if (!(this.level() instanceof ServerLevel var1)) {
            return 0;
        } else {
            int var5 = 0;
            Iterator var3 = this.called.iterator();

            while (var3.hasNext()) {
                Entity var4 = var1.getEntity((Integer)var3.next());
                if (var4 != null && var4.isAlive()) {
                    var5++;
                } else {
                    var3.remove();
                }
            }

            return var5;
        }
    }

    private void call(ServerLevel var1, LivingEntity var2) {
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 6.0F, 0.8F);
        int var3 = Math.min(3 - this.living(), this.stage >= 3 ? 3 : 2);
        int var4 = 0;

        for (int var5 = 0; var5 < 3 && var4 < var3; var5++) {
            double var6 = Math.toRadians((double)var5 * 120.0 + (double)this.random.nextInt(40));
            double var8 = this.getX() + Math.cos(var6) * 4.5;
            double var10 = this.getZ() + Math.sin(var6) * 4.5;
            BlockPos var12 = var1.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(var8, this.getY(), var10));
            if (!(Math.abs((double)var12.getY() - this.getY()) > 6.0)) {
                RuneSentinelEntity var13 = (RuneSentinelEntity)((EntityType)WakingWorld.RUNE_SENTINEL.get()).create(var1);
                if (var13 != null) {
                    var13.moveTo(var8, (double)var12.getY(), var10, this.random.nextFloat() * 360.0F, 0.0F);
                    if (!var1.noCollision(var13)) {
                        var13.discard();
                    } else {
                        var13.finalizeSpawn(var1, var1.getCurrentDifficultyAt(var12), MobSpawnType.MOB_SUMMONED, null);
                        var13.setPersistenceRequired();
                        if (var2 != null) {
                            var13.setTarget(var2);
                        }

                        var1.addFreshEntity(var13);
                        this.called.add(var13.getId());
                        var4++;
                        Cataclysms.ring(var1, stageColour(this.stage), 3.0F, var8, (double)var12.getY() + 0.12, var10, 0.8);
                        Cataclysms.runes(var1, stageColour(this.stage), 1.3F, var8, (double)var12.getY() + 1.0, var10, 26, 0.4, 0.9, 0.4);
                        Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, var8, (double)var12.getY() + 0.6, var10, 40, 0.35, 0.9, 0.35, 0.14);
                        var1.playSound(null, var12, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 3.0F, 0.5F);
                    }
                }
            }
        }

        this.callCooldown = var4 > 0 ? 20 * (this.stage >= 3 ? 26 : 38) : 160;
        if (var4 > 0) {
            for (ServerPlayer var15 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 2304.0)) {
                var15.sendSystemMessage(
                    Component.translatable("entity.wakingworld.dark_mage.called", new Object[]{this.mageName()}).withStyle(ChatFormatting.DARK_PURPLE)
                );
            }
        }
    }

    private void dismiss(ServerLevel var1) {
        for (int var3 : this.called) {
            Entity var5 = var1.getEntity(var3);
            if (var5 instanceof LivingEntity) {
                LivingEntity var4 = (LivingEntity)var5;
                if (var4.isAlive()) {
                    Cataclysms.runes(var1, stageColour(1), 1.1F, var4.getX(), var4.getY() + 1.0, var4.getZ(), 20, 0.3, 0.7, 0.3);
                    var1.playSound(null, var4.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.7F);
                    var4.discard();
                }
            }
        }

        this.called.clear();
    }

    private void beginChannel(ServerLevel var1) {
        this.channel = 160;
        this.channelTotal = 160;
        this.channelHurt = 0.0F;
        this.lastWordCooldown = 900;
        this.entityData.set(DATA_CHANNEL, 0.001F);
        var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6.0F, 1.3F);

        for (ServerPlayer var3 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 4096.0)) {
            var3.sendSystemMessage(Component.translatable("entity.wakingworld.dark_mage.lastword", new Object[]{this.mageName()}).withStyle(ChatFormatting.RED));
        }
    }

    private void channelTick(ServerLevel var1) {
        this.channel--;
        float var2 = 1.0F - (float)this.channel / (float)this.channelTotal;
        this.entityData.set(DATA_CHANNEL, Math.max(0.001F, var2));
        this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
        int var3 = stageColour(4);
        if (this.channel % 2 == 0) {
            double var4 = (double)this.channel * 0.35;
            double var6 = 5.5 * (1.0 - (double)var2) + 0.6;
            Cataclysms.runes(
                var1,
                var3,
                1.6F,
                this.getX() + Math.cos(var4) * var6,
                this.getY() + 1.0 + (double)var2 * 2.5,
                this.getZ() + Math.sin(var4) * var6,
                1,
                0.0,
                0.0,
                0.0
            );
            Cataclysms.puff(var1, ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 2.2 + (double)var2 * 2.0, this.getZ(), 3, 0.5, 0.3, 0.5, 0.02);
        }

        if (this.channel % 10 == 0) {
            Cataclysms.ring(var1, var3, 3.0F + 9.0F * var2, this.getX(), this.getY() + 0.15, this.getZ());
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 4.0F, 0.4F + var2);
        }

        if (this.channelHurt >= 55.0F) {
            this.endChannel(var1, false);
        } else {
            if (this.channel <= 0) {
                this.endChannel(var1, true);
            }
        }
    }

    private void endChannel(ServerLevel var1, boolean var2) {
        this.channel = 0;
        this.entityData.set(DATA_CHANNEL, 0.0F);
        this.castCooldown = var2 ? 50 : 80;
        if (!var2) {
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 6.0F, 0.5F);
            Cataclysms.puff(var1, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.6, this.getZ(), 70, 0.8, 1.0, 0.8, 0.05);
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));

            for (ServerPlayer var11 : var1.getPlayers(var1x -> var1x.distanceToSqr(this) < 4096.0)) {
                var11.sendSystemMessage(
                    Component.translatable("entity.wakingworld.dark_mage.lastword.broken", new Object[]{this.mageName()}).withStyle(ChatFormatting.GREEN)
                );
            }
        } else {
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0F, 0.5F);
            WakingWorld.hooks.shakeAt(this.position(), 6.0F, 70.0);
            Cataclysms.ring(var1, stageColour(4), 16.0F, this.getX(), this.getY() + 0.2, this.getZ());

            for (int var3 = 0; var3 < 90; var3++) {
                double var4 = (double)var3 / 90.0 * Math.PI * 2.0;
                Cataclysms.puff(
                    var1,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + Math.cos(var4) * 13.0,
                    this.getY() + 0.4,
                    this.getZ() + Math.sin(var4) * 13.0,
                    3,
                    0.2,
                    1.4,
                    0.2,
                    0.2
                );
            }

            for (LivingEntity var10 : var1.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(14.0), this::hostileTo)) {
                double var5 = var10.position().distanceTo(this.position());
                var10.hurt(this.damageSources().indirectMagic(this, this), (float)(26.0 * Math.max(0.35, 1.0 - var5 / 16.0)));
                var10.igniteForSeconds(6.0F);
                Vec3 var7 = var10.position().subtract(this.position()).normalize().scale(1.1).add(0.0, 0.6, 0.0);
                var10.push(var7.x, var7.y, var7.z);
                var10.hurtMarked = true;
            }
        }
    }

    private boolean hostileTo(LivingEntity var1) {
        return var1 != this && var1.isAlive() && !(var1 instanceof MageEntity) ? !(var1 instanceof RuneSentinelEntity) : false;
    }

    public void die(DamageSource var1) {
        if (this.level() instanceof ServerLevel var2) {
            this.dropWard(var2, true);
            this.dismiss(var2);
            this.closeScar(var2);
            this.bar.removeAllPlayers();
            Cataclysms.puff(var2, ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.0, this.getZ(), 160, 0.7, 1.2, 0.7, 0.35);
            Cataclysms.puff(var2, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 80, 1.0, 1.0, 1.0, 0.06);
            var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_DEATH, SoundSource.HOSTILE, 6.0F, 0.6F);
            WakingWorld.hooks.shakeAt(this.position(), 5.0F, 70.0);

            for (int var6 = 0; var6 < 44; var6++) {
                double var4 = (double)var6 / 44.0 * Math.PI * 2.0;
                Cataclysms.puff(
                    var2,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + Math.cos(var4) * 4.5,
                    this.getY() + 0.3,
                    this.getZ() + Math.sin(var4) * 4.5,
                    2,
                    0.1,
                    0.6,
                    0.1,
                    0.08
                );
            }

            this.hoard(var2);
            if (var1.getEntity() instanceof ServerPlayer var7) {
                ((KindTrigger)WakingTriggers.COLOSSUS_SLAIN.get()).trigger(var7, "dark_mage", 0);
            }

            if (WakingConfig.kingdoms() && !this.tower.equals(BlockPos.ZERO)) {
                KingCharge.mageSlain(var2, this.tower, var1.getEntity() instanceof ServerPlayer var8 ? var8 : null);
            }
        }

        super.die(var1);
    }

    private void hoard(ServerLevel var1) {
        this.drop((Item)WakingItems.STORM_ROD.get(), 1);
        this.drop((Item)WakingItems.MAGE_MIRROR.get(), 1);
        this.drop((Item)WakingItems.SLEEPERS_EMBER.get(), 3 + this.random.nextInt(2));
        this.drop((Item)WakingItems.STAR_IRON.get(), 4 + this.random.nextInt(4));
        this.drop(Items.AMETHYST_SHARD, 6 + this.random.nextInt(5));
        this.drop(Items.ECHO_SHARD, 2 + this.random.nextInt(2));
        this.drop(Items.GLOWSTONE_DUST, 10 + this.random.nextInt(8));
        this.drop(Items.EXPERIENCE_BOTTLE, 8 + this.random.nextInt(8));
        this.drop(Items.SOUL_LANTERN, 2 + this.random.nextInt(3));
    }

    private void drop(Item var1, int var2) {
        if (var2 > 0) {
            this.spawnAtLocation(new ItemStack(var1, var2));
        }
    }

    public void addAdditionalSaveData(CompoundTag var1) {
        super.addAdditionalSaveData(var1);
        var1.putLong("Tower", this.tower.asLong());
        var1.putBoolean("Roused", this.roused());
        var1.putBoolean("Tidied", this.tidied);
        var1.putBoolean("Second", this.second);
        var1.putInt("Stage", this.stage);
        var1.putFloat("Ward", this.ward);
        var1.putFloat("WardMax", this.wardMax);
        var1.putInt("Kept", this.kept());
        var1.putInt("Stance", this.stance());
        var1.putInt("Order", this.order());
        var1.putLong("Post", this.post.asLong());
        if (this.owner != null) {
            var1.putUUID("Owner", this.owner);
        }
    }

    public void readAdditionalSaveData(CompoundTag var1) {
        super.readAdditionalSaveData(var1);
        this.tower = BlockPos.of(var1.getLong("Tower"));
        this.entityData.set(DATA_ROUSED, var1.getBoolean("Roused"));
        this.tidied = var1.getBoolean("Tidied");
        this.second = var1.getBoolean("Second");
        this.stage = Math.max(1, var1.getInt("Stage"));
        this.entityData.set(DATA_STAGE, this.stage);
        this.entityData.set(DATA_KEPT, var1.getInt("Kept"));
        this.entityData.set(DATA_ORDER, var1.getInt("Order"));
        this.entityData.set(DATA_STANCE, var1.contains("Stance") ? var1.getInt("Stance") : 1);
        this.post = BlockPos.of(var1.getLong("Post"));
        this.owner = var1.hasUUID("Owner") ? var1.getUUID("Owner") : null;
        this.ward = var1.getFloat("Ward");
        this.wardMax = var1.getFloat("WardMax");
        this.entityData.set(DATA_WARD, this.wardMax <= 0.0F ? 0.0F : Math.max(0.0F, this.ward / this.wardMax));
        this.bar.setVisible(this.roused());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (roused()) bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** He is not a source of light for the mob cap, and he is never afraid of the sun. */
    @Override
    public boolean isSunBurnTick() {
        return false;
    }

    public static boolean present() {
        return WakingConfig.mage();
    }
}
