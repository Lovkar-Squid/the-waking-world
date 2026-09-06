package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.Crater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A falling star: a burning mass of stone that comes down out of the sky on a long slant, and
 * ends as a crater with a Starstone still glowing at the bottom of it.
 *
 * <p>It is not a vanilla projectile - it flies straight at a fixed speed so it can be aimed at a
 * place from hundreds of blocks up and still land there. Every tick it sweeps the line it just
 * crossed for ground; whatever it meets first is where it lands. The trail is drawn from the
 * server so everyone within sight sees the same streak, and the roar grows as it comes down.</p>
 */
public class MeteorEntity extends Entity {
    private static final EntityDataAccessor<Byte> DATA_SIZE =
            SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.BYTE);

    /** Where it is going (used for the aim line and to give up if it somehow misses the world). */
    private Vec3 target = Vec3.ZERO;
    private boolean carriesStar = true;
    private int life;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ---- what it is ------------------------------------------------------------------------

    /** 1 = a stone the size of a house, 3 = the one that leaves a lake. */
    public int size() {
        return Mth.clamp(this.entityData.get(DATA_SIZE), 1, 3);
    }

    public void setSize(int size) {
        this.entityData.set(DATA_SIZE, (byte) Mth.clamp(size, 1, 3));
    }

    public void setCarriesStar(boolean carries) {
        this.carriesStar = carries;
    }

    /** Aim it at a point on the ground and let go: it comes in at a slant from the given height. */
    public void aimAt(Vec3 at, double fromHeight, double slant, double speed) {
        this.target = at;
        double angle = this.random.nextDouble() * Math.PI * 2;
        Vec3 start = at.add(Math.cos(angle) * slant, fromHeight, Math.sin(angle) * slant);
        this.setPos(start.x, start.y, start.z);
        this.setDeltaMovement(at.subtract(start).normalize().scale(speed));
        this.setYRot((float) (Math.toDegrees(Math.atan2(-this.getDeltaMovement().x, this.getDeltaMovement().z))));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, (byte) 1);
    }

    // ---- flight ----------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        Vec3 from = this.position();
        Vec3 to = from.add(this.getDeltaMovement());

        if (this.level() instanceof ServerLevel server) {
            // the ground it crosses this tick: the first solid thing wins
            BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                impact(server, hit.getLocation());
                return;
            }
            trail(server);
            if (++life > 400 || this.getY() < this.level().getMinBuildHeight()) this.discard();
        }
        this.setPos(to.x, to.y, to.z);
    }

    /** Smoke, fire and rock behind it, and a roar that grows the closer it gets to the ground. */
    private void trail(ServerLevel server) {
        int s = size();
        Vec3 back = this.getDeltaMovement().normalize().scale(-1);
        for (int i = 0; i < 3 + s * 2; i++) {
            double d = i * 0.8;
            double x = this.getX() + back.x * d, y = this.getY() + back.y * d, z = this.getZ() + back.z * d;
            server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.4 * s, 0.4 * s, 0.4 * s, 0.01);
            server.sendParticles(ParticleTypes.FLAME, x, y, z, 2, 0.3 * s, 0.3 * s, 0.3 * s, 0.02);
        }
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState()),
                this.getX(), this.getY(), this.getZ(), 3, 0.5, 0.5, 0.5, 0.15);
        if (this.tickCount % 6 == 0) {
            float volume = 3.0F + s;
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.WEATHER, volume, 0.4F);
        }
    }

    // ---- landing ---------------------------------------------------------------------------

    private void impact(ServerLevel server, Vec3 at) {
        int s = size();
        double craterRadius = 4.0 + s * 2.5;
        float damage = 12.0F + s * 8;

        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 8.0F, 0.35F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.WEATHER, 6.0F, 0.4F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2 + s, craterRadius * 0.3, 0.5, craterRadius * 0.3, 0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 60 + 40 * s, craterRadius, 2.0, craterRadius, 0.08);

        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(craterRadius + 3), LivingEntity::isAlive)) {
            double d = target.position().distanceTo(at);
            float dmg = (float) (damage * Math.max(0.2, 1.0 - d / (craterRadius + 4)));
            target.hurt(this.damageSources().explosion(this, null), dmg);
            Vec3 push = target.position().subtract(at).normalize().scale(1.6).add(0, 0.8, 0);
            target.push(push.x, push.y, push.z);
            target.hurtMarked = true;
        }

        Crater.blast(server, at, craterRadius, 40 + 20 * s, 0.75, this.random);
        Starfall.dress(server, BlockPos.containing(at), craterRadius, s, carriesStar, this.random);
        this.level().broadcastEntityEvent(this, (byte) 70);
        this.discard();
    }

    /** 70 = it landed: the ground shakes for whoever is near. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 70) WakingWorld.hooks.shakeAt(this.position(), 3.0F + size(), 40 + size() * 20);
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Size", (byte) size());
        tag.putBoolean("Star", carriesStar);
        tag.putInt("Life", life);
        tag.putDouble("Tx", target.x);
        tag.putDouble("Ty", target.y);
        tag.putDouble("Tz", target.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSize(tag.getByte("Size"));
        carriesStar = !tag.contains("Star") || tag.getBoolean("Star");
        life = tag.getInt("Life");
        target = new Vec3(tag.getDouble("Tx"), tag.getDouble("Ty"), tag.getDouble("Tz"));
    }
}
