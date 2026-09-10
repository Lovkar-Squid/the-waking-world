package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.cataclysm.Cataclysms;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WardStoneEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(WardStoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_KEY = SynchedEntityData.defineId(WardStoneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_LEFT = SynchedEntityData.defineId(WardStoneEntity.class, EntityDataSerializers.FLOAT);
    public static final float KEY_HEALTH = 22.0F;
    private static final double SHELL = 2.9;
    private static final double SPIN = 0.021;
    private int mageId = -1;
    private int slot;
    private int total = 1;
    private float left = 22.0F;
    private float phase;

    public WardStoneEntity(EntityType<? extends WardStoneEntity> var1, Level var2) {
        super(var1, var2);
        this.noPhysics = true;
    }

    protected void defineSynchedData(Builder var1) {
        var1.define(DATA_STATE, Block.getId(Blocks.STONE.defaultBlockState()));
        var1.define(DATA_KEY, false);
        var1.define(DATA_LEFT, 1.0F);
    }

    public void set(MageEntity var1, BlockState var2, boolean var3, int var4, int var5) {
        this.mageId = var1.getId();
        this.slot = var4;
        this.total = Math.max(1, var5);
        this.phase = (float)var4 * 2.399963F % (float) (Math.PI * 2);
        this.entityData.set(DATA_STATE, Block.getId(var2));
        this.entityData.set(DATA_KEY, var3);
        this.entityData.set(DATA_LEFT, 1.0F);
    }

    public BlockState state() {
        return Block.stateById((Integer)this.entityData.get(DATA_STATE));
    }

    public boolean keystone() {
        return (Boolean)this.entityData.get(DATA_KEY);
    }

    public float left() {
        return (Float)this.entityData.get(DATA_LEFT);
    }

    public void bindTo(int var1) {
        this.mageId = var1;
    }

    public void tick() {
        super.tick();
        if (this.level().getEntity(this.mageId) instanceof MageEntity var2 && var2.isAlive()) {
            double var3 = (double)this.tickCount * 0.021;
            double var5 = this.total == 1 ? 0.0 : 1.0 - 2.0 * ((double)this.slot + 0.5) / (double)this.total;
            double var7 = Math.sqrt(Math.max(0.0, 1.0 - var5 * var5));
            double var9 = (double)this.phase + var3;
            double var11 = Math.sin((double)this.tickCount * 0.05 + (double)this.phase) * 0.16;
            double var13 = var2.getX() + Math.cos(var9) * var7 * 2.9;
            double var15 = var2.getY() + 1.15 + var5 * 2.9 * 0.85 + var11;
            double var17 = var2.getZ() + Math.sin(var9) * var7 * 2.9;
            this.setPos(var13, var15, var17);
            this.setDeltaMovement(Vec3.ZERO);
            if (this.level().isClientSide) {
                return;
            }

            if (this.keystone() && this.tickCount % 6 == 0) {
                Cataclysms.runes((ServerLevel)this.level(), 16765562, 0.7F, var13, var15 + 0.4, var17, 1, 0.1, 0.1, 0.1);
            }

            return;
        }

        if (!this.level().isClientSide) {
            this.shatter();
        }
    }

    public boolean hurt(DamageSource var1, float var2) {
        if (this.level().isClientSide) {
            return false;
        } else if (var1.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.shatter();
            return true;
        } else if (!(var1.getEntity() instanceof Player)) {
            return false;
        } else {
            ServerLevel var3 = (ServerLevel)this.level();
            if (!this.keystone()) {
                var3.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.6F, 0.7F);
                Cataclysms.puff(
                    var3, new BlockParticleOption(ParticleTypes.BLOCK, this.state()), this.getX(), this.getY(), this.getZ(), 6, 0.25, 0.25, 0.25, 0.05
                );
                return false;
            } else {
                this.left -= var2;
                this.entityData.set(DATA_LEFT, Math.max(0.0F, this.left / 22.0F));
                var3.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2.0F, 0.8F);
                Cataclysms.puff(var3, new BlockParticleOption(ParticleTypes.BLOCK, this.state()), this.getX(), this.getY(), this.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                if (this.left <= 0.0F) {
                    if (this.level().getEntity(this.mageId) instanceof MageEntity var4) {
                        var4.keystoneBroken(var3, this);
                    }

                    this.shatter();
                }

                return true;
            }
        }
    }

    public void shatter() {
        if (this.level() instanceof ServerLevel var1) {
            Cataclysms.puff(var1, new BlockParticleOption(ParticleTypes.BLOCK, this.state()), this.getX(), this.getY(), this.getZ(), 24, 0.35, 0.35, 0.35, 0.16);
            var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.8F, 0.9F);
        }

        this.discard();
    }

    public boolean isPickable() {
        return !this.isRemoved();
    }

    public boolean isPushable() {
        return false;
    }

    public boolean shouldRenderAtSqrDistance(double var1) {
        return var1 < 9216.0;
    }

    protected void readAdditionalSaveData(CompoundTag var1) {
        this.mageId = var1.getInt("Mage");
        this.slot = var1.getInt("Slot");
        this.total = Math.max(1, var1.getInt("Total"));
        this.left = var1.contains("Left") ? var1.getFloat("Left") : 22.0F;
        this.phase = var1.getFloat("Phase");
        this.entityData.set(DATA_STATE, var1.getInt("State"));
        this.entityData.set(DATA_KEY, var1.getBoolean("Key"));
        this.entityData.set(DATA_LEFT, Math.max(0.0F, this.left / 22.0F));
    }

    protected void addAdditionalSaveData(CompoundTag var1) {
        var1.putInt("Mage", this.mageId);
        var1.putInt("Slot", this.slot);
        var1.putInt("Total", this.total);
        var1.putFloat("Left", this.left);
        var1.putFloat("Phase", this.phase);
        var1.putInt("State", (Integer)this.entityData.get(DATA_STATE));
        var1.putBoolean("Key", this.keystone());
    }
}
