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
import java.util.UUID;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult.Type;

public class MeteorEntity extends Entity {
    /** A star screams once, not every tick of its fall. */
    private boolean screamed;

    private static final EntityDataAccessor<Byte> DATA_SIZE =
            SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.BYTE);

    /** Where it is going (used for the aim line and to give up if it somehow misses the world). */
    private Vec3 target = Vec3.ZERO;
    private boolean carriesStar = true;
    private int life;
    /**
     * A lava bomb rather than a star: thrown out of a volcano's throat instead of falling out of the
     * sky, so it arcs under gravity, and where it lands it leaves lava rather than a crater.
     */
    private boolean bomb;
    private UUID ownScar;
    private boolean ownsScar;
    private UUID spared;
    private int siegeTarget = -1;
    private float siegeBlow;

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

    /**
     * Throw it, rather than aim it: a lava bomb leaves the vent with a velocity and then belongs to
     * gravity, so it draws the arc out of the mountain that a player's eye can follow all the way to
     * where it lands.
     */
    public void hurl(Vec3 from, Vec3 velocity) {
        this.bomb = true;
        this.carriesStar = false;
        this.setSize(1);
        this.setPos(from.x, from.y, from.z);
        this.setDeltaMovement(velocity);
        this.target = from;
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

    public void aimFrom(Vec3 var1, Vec3 var2, double var3, double var5, double var7) {
        this.target = var1;
        Vec3 var9 = new Vec3(var2.x, 0.0, var2.z);
        if (var9.lengthSqr() < 1.0E-4) {
            var9 = new Vec3(1.0, 0.0, 0.0);
        }

        var9 = var9.normalize();
        Vec3 var10 = var1.add(var9.x * var5, var3, var9.z * var5);
        this.setPos(var10.x, var10.y, var10.z);
        this.setDeltaMovement(var1.subtract(var10).normalize().scale(var7));
        this.setYRot((float)Math.toDegrees(Math.atan2(-this.getDeltaMovement().x, this.getDeltaMovement().z)));
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
            if (++life > 400 || this.getY() < this.level().getMinBuildHeight()) {
                // a star that never lands closes the record it opened, or the Hourglass waits on it for ever
                if (ownScar != null && ownsScar) { Scars.done(server, ownScar); ownScar = null; }
                this.discard();
            }
        }
        this.setPos(to.x, to.y, to.z);
        if (bomb) {
            // thrown, not aimed: it slows and it falls, so it comes down where an arc says it should
            this.setDeltaMovement(this.getDeltaMovement().scale(0.99).add(0.0, -0.055, 0.0));
        }
    }

    /** Smoke, fire and rock behind it, and a roar that grows the closer it gets to the ground. */
    private void trail(ServerLevel server) {
        if (bomb) {
            Cataclysms.puff(server, ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 4, 0.25, 0.25, 0.25, 0.02);
            Cataclysms.puff(server, ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            return;
        }
        int s = size();
        Vec3 back = this.getDeltaMovement().normalize().scale(-1);
        for (int i = 0; i < 3 + s * 2; i++) {
            double d = i * 0.8;
            double x = this.getX() + back.x * d, y = this.getY() + back.y * d, z = this.getZ() + back.z * d;
            Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.4 * s, 0.4 * s, 0.4 * s, 0.01);
            Cataclysms.puff(server, ParticleTypes.FLAME, x, y, z, 2, 0.3 * s, 0.3 * s, 0.3 * s, 0.02);
        }
        Cataclysms.puff(server, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState()),
                this.getX(), this.getY(), this.getZ(), 3, 0.5, 0.5, 0.5, 0.15);
        if (this.tickCount % 6 == 0) {
            float volume = 3.0F + s;
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.WEATHER, volume, 0.4F);
            // and the falling whistle, once, as it comes into the last of its dive
            if (!screamed) {
                screamed = true;
                server.playSound(null, this.getX(), this.getY(), this.getZ(),
                        me.lovkar.wakingworld.WakingSounds.METEOR_SCREAM.get(), SoundSource.WEATHER,
                        5.0F + size() * 2.0F, 0.85F + this.random.nextFloat() * 0.2F);
            }
        }
    }

    void ownScar(UUID var1) {
        this.ownScar = var1;
        this.ownsScar = true;
    }

    public void useScar(UUID var1) {
        this.ownScar = var1;
        this.ownsScar = false;
    }

    public void aimedAt(int var1, float var2) {
        this.siegeTarget = var1;
        this.siegeBlow = var2;
    }

    public void spare(Entity var1) {
        this.spared = var1 == null ? null : var1.getUUID();
    }

    // ---- landing ---------------------------------------------------------------------------

    private void impact(ServerLevel server, Vec3 at) {
        // the cataclysm's open scar if there is one, else the record this lone star opened itself
        UUID scar = Cataclysms.scarOf(server);
        Scars.writing(server, scar != null ? scar : ownScar);
        try {
            land(server, at);
        } finally {
            Scars.close();
            if (ownScar != null && ownsScar) { Scars.done(server, ownScar); ownScar = null; }
        }
    }

    private void land(ServerLevel server, Vec3 at) {
        if (bomb) {
            splash(server, at);
            this.discard();
            return;
        }
        int s = size();
        double craterRadius = 4.0 + s * 2.5;
        float damage = 12.0F + s * 8;

        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 8.0F, 0.35F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.WEATHER, 6.0F, 0.4F);
        Cataclysms.puff(server, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2 + s, craterRadius * 0.3, 0.5, craterRadius * 0.3, 0);
        Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 60 + 40 * s, craterRadius, 2.0, craterRadius, 0.08);
        // the flash, and the ring going out from it. A star landing at night was three grey puffs on
        // camera: what a strike needs is something bright at the moment of it and something moving
        // outwards afterwards, or there is nothing to cut to.
        Cataclysms.puff(server, ParticleTypes.FLASH, at.x, at.y + 1.5, at.z, 3 + s, 0.4, 0.4, 0.4, 0);
        Cataclysms.puff(server, ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 40 + 30 * s, 0.6, 0.4, 0.6, 0.55);
        double ring = craterRadius * 1.25;
        for (int i = 0; i < 60 + 20 * s; i++) {
            double a = i / (double) (60 + 20 * s) * Math.PI * 2;
            double px = at.x + Math.cos(a) * ring, pz = at.z + Math.sin(a) * ring;
            Cataclysms.puff(server, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, at.y + 0.6, pz, 2, 0.3, 0.2, 0.3, 0.06);
            Cataclysms.puff(server, ParticleTypes.LAVA, px, at.y + 0.4, pz, 1, 0.2, 0.1, 0.2, 0.0);
        }
        // a column of smoke standing over the crater, so the strike is still findable a minute later
        for (int i = 0; i < 6; i++) {
            Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, at.x, at.y + 3 + i * 5.0, at.z,
                    14, craterRadius * (0.4 + i * 0.22), 1.5, craterRadius * (0.4 + i * 0.22), 0.04);
        }

        // the one it was told to spare (the horn-blower who called the volley) walks away from it
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(craterRadius + 3),
                e -> e.isAlive() && !e.getUUID().equals(spared))) {
            double d = target.position().distanceTo(at);
            float dmg = (float) (damage * Math.max(0.2, 1.0 - d / (craterRadius + 4)));
            target.hurt(this.damageSources().explosion(this, null), dmg);
            Vec3 push = target.position().subtract(at).normalize().scale(1.6).add(0, 0.8, 0);
            target.push(push.x, push.y, push.z);
            target.hurtMarked = true;
        }
        // a siege stone lands its whole worth on the giant it was thrown at, mercy window or not
        if (siegeTarget >= 0 && server.getEntity(siegeTarget) instanceof ColossusEntity giant) giant.siegeStruck(server, at, siegeBlow);

        Crater.blast(server, at, craterRadius, 40 + 20 * s, 0.75, this.random);
        Starfall.dress(server, BlockPos.containing(at), craterRadius, s, carriesStar, this.random);
        Aftermath.scorch(server, BlockPos.containing(at), craterRadius, craterRadius * 3.2 + 8, this.random);
        if (me.lovkar.wakingworld.WakingConfig.blight()) {
            Aftermath.blight(server, BlockPos.containing(at), craterRadius * 2.6 + 6, 0.8, this.random);
        }
        // a star that lands on a shrine may open it. Only the ones carrying a star: a pebble that
        // scorched a field is not the sky making a point about anything.
        if (carriesStar) Answer.maybe(server, at, Omen.Kind.METEOR);
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

    /**
     * Where a lava bomb lands: lava, and a little of it thrown about.
     *
     * <p>Three or four source blocks rather than one, on whatever they land on and never inside
     * anything - a bomb that punched a hole in somebody's roof and filled it with lava would be the
     * single most hated thing in this mod. The lava then does what lava does, which is the point:
     * the mountain is throwing pieces of itself into the country and setting fire to them.</p>
     */
    private void splash(ServerLevel server, Vec3 at) {
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 3.0F, 0.55F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.WEATHER, 3.0F, 0.6F);
        Cataclysms.puff(server, ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.4, 0.3, 0.4, 0.0);
        Cataclysms.puff(server, ParticleTypes.LAVA, at.x, at.y + 0.4, at.z, 40, 1.2, 0.6, 1.2, 0.0);
        Cataclysms.puff(server, ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 30, 1.0, 0.5, 1.0, 0.08);
        Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 20, 1.0, 0.8, 1.0, 0.03);

        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0), LivingEntity::isAlive)) {
            target.hurt(this.damageSources().explosion(this, null), 6.0F);
            target.igniteForSeconds(6);
        }

        BlockPos centre = BlockPos.containing(at);
        pour(server, centre);
        for (int i = 0; i < 3; i++) {
            BlockPos near = centre.offset(this.random.nextInt(3) - 1, 0, this.random.nextInt(3) - 1);
            if (this.random.nextFloat() < 0.75F) pour(server, near);
        }
    }

    /** One source block of lava, put down on the surface and never into anything solid. */
    private void pour(ServerLevel server, BlockPos at) {
        BlockPos on = at;
        for (int i = 0; i < 4 && !server.getBlockState(on).canBeReplaced(); i++) on = on.above();
        if (!server.getBlockState(on).canBeReplaced()) return;
        if (server.getBlockState(on.below()).isAir()) return;               // not hanging in the air
        Scars.set(server, on, Blocks.LAVA.defaultBlockState());
    }
}
