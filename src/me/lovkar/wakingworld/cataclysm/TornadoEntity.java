package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tornado: a column of wind that walks the country.
 *
 * <p>It is an entity because it moves - it wanders across the map on a slowly turning heading,
 * pulling in whatever is loose. Anything alive inside its reach is dragged toward the middle and
 * lifted; blocks it can take from the top of the ground go up as falling blocks and come down
 * somewhere else. It does not eat bedrock, containers, or anything with an inventory in it, and it
 * will not take a block a player has put down inside a claim of light - what it lifts is the loose
 * skin of the world, not somebody's house.</p>
 *
 * <p>It dies of old age, and it never crosses the same ground twice for long: a tornado is meant to
 * be survived and then talked about, not to sit on a base until it is gone.</p>
 */
public final class TornadoEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_AGE_FRACTION =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    /** Blocks the wind leaves alone whatever happens. */
    private static final java.util.Set<net.minecraft.world.level.block.Block> ROOTED = java.util.Set.of(
            Blocks.BEDROCK, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.REINFORCED_DEEPSLATE,
            Blocks.END_PORTAL_FRAME, Blocks.SPAWNER, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
            Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.BEACON, Blocks.CONDUIT, Blocks.LODESTONE);

    private double headingX = 1, headingZ = 0;
    private int life = 60 * 20;
    private int maxLife = 60 * 20;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE_FRACTION, 0.0F);
    }

    /** 0 while it is forming, 1 at its strongest, back toward 0 as it dies. */
    public float strength() {
        return this.entityData.get(DATA_AGE_FRACTION);
    }

    public void aimFrom(Vec3 at, int seconds) {
        this.setPos(at.x, at.y, at.z);
        this.maxLife = this.life = seconds * 20;
        double a = this.random.nextDouble() * Math.PI * 2;
        this.headingX = Math.cos(a);
        this.headingZ = Math.sin(a);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            clientTick();
            return;
        }

        life--;
        if (life <= 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.WEATHER, 2.0F, 1.6F);
            discard();
            return;
        }
        // a long ramp up, a long plateau, a ramp down
        float t = 1.0F - (float) life / maxLife;
        float s = Mth.clamp(Math.min(t / 0.15F, (1.0F - t) / 0.20F), 0.0F, 1.0F);
        this.entityData.set(DATA_AGE_FRACTION, s);

        walk(level);
        if (s > 0.15F) {
            pull(level, s);
            if (this.tickCount % 4 == 0) lift(level, s);
        }
        if (this.tickCount % 10 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.WEATHER, 4.0F * s, 0.5F);
            WakingWorld.hooks.shakeAt(position(), 1.4F * s, 60);
        }
        spray(level, s);
    }

    /** It wanders: the heading turns a little every second and the column keeps its feet on the ground. */
    private void walk(ServerLevel level) {
        if (this.tickCount % 20 == 0) {
            double turn = (this.random.nextDouble() - 0.5) * 0.5;
            double nx = headingX * Math.cos(turn) - headingZ * Math.sin(turn);
            double nz = headingX * Math.sin(turn) + headingZ * Math.cos(turn);
            headingX = nx;
            headingZ = nz;
        }
        double speed = 0.22;
        double x = getX() + headingX * speed;
        double z = getZ() + headingZ * speed;
        // follow the ground, but only where the ground is already there. Asking for the surface every
        // tick would generate chunks in front of the column as it walks, which is a lot of world to
        // make for weather nobody may ever see.
        double y = getY();
        BlockPos ahead = BlockPos.containing(x, y, z);
        if (this.tickCount % 5 == 0 && level.isLoaded(ahead)) {
            y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead).getY();
        }
        this.setPos(x, y, z);
    }

    /** Everything alive inside the reach is dragged in and up. */
    private void pull(ServerLevel level, float s) {
        double reach = radius() * 2.6;
        AABB box = new AABB(getX() - reach, getY() - 4, getZ() - reach, getX() + reach, getY() + 34, getZ() + reach);
        for (Entity e : level.getEntities(this, box, e -> !(e instanceof TornadoEntity))) {
            Vec3 d = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
            double dist = d.length();
            if (dist > reach || dist < 0.01) continue;
            double grip = (1.0 - dist / reach) * s;
            Vec3 in = d.scale(1.0 / dist).scale(0.16 * grip);
            // a little sideways, so it circles rather than falling straight in
            Vec3 round = new Vec3(-d.z, 0, d.x).scale(1.0 / dist).scale(0.24 * grip);
            e.setDeltaMovement(e.getDeltaMovement().add(in).add(round).add(0, 0.28 * grip, 0));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity living && this.tickCount % 20 == 0 && grip > 0.4) {
                living.hurt(level.damageSources().flyIntoWall(), 1.5F);
            }
        }
    }

    /** A few blocks off the skin of the ground, thrown up as falling blocks. */
    private void lift(ServerLevel level, float s) {
        int r = (int) Math.ceil(radius());
        for (int i = 0; i < 3; i++) {
            int dx = this.random.nextInt(r * 2 + 1) - r;
            int dz = this.random.nextInt(r * 2 + 1) - r;
            if (dx * dx + dz * dz > r * r) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(getX() + dx, 0, getZ() + dz)).below();
            if (!loose(level, top)) continue;
            BlockState state = level.getBlockState(top);
            level.removeBlock(top, false);
            FallingBlockEntity fb = FallingBlockEntity.fall(level, top, state);
            fb.setHurtsEntities(1.0F, 8);
            fb.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.6, 0.9 + this.random.nextDouble() * 0.5,
                    (this.random.nextDouble() - 0.5) * 0.6);
        }
    }

    /**
     * What the wind is allowed to take: the loose skin of the world. Nothing rooted, nothing that
     * holds anything, and nothing under a roof or in a lit room - that is somebody's building.
     */
    private boolean loose(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (state.isAir()) return false;
        if (ROOTED.contains(state.getBlock())) return false;
        if (state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, at) < 0) return false;                 // unbreakable
        if (state.getDestroySpeed(level, at) > 6.0F) return false;              // it is not taking a stone keep apart
        if (!level.getFluidState(at).isEmpty()) return false;
        if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at.above()) > 4) return false;
        if (!level.canSeeSky(at.above())) return false;                        // under a roof: leave it
        return true;
    }

    private void spray(ServerLevel level, float s) {
        double r = radius();
        for (int i = 0; i < 14; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double h = this.random.nextDouble() * 26 * s;
            double rr = r * (0.35 + h / 34.0);
            double px = getX() + Math.cos(a) * rr;
            double pz = getZ() + Math.sin(a) * rr;
            level.sendParticles(ParticleTypes.CLOUD, px, getY() + h, pz, 1, 0, 0, 0, 0.02);
        }
        BlockPos under = BlockPos.containing(getX(), getY() - 1, getZ());
        BlockState ground = level.getBlockState(under);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                    getX(), getY() + 0.5, getZ(), 24, r * 0.6, 1.5, r * 0.6, 0.4);
        }
    }

    private void clientTick() {
        // the client draws it; nothing to do but exist
    }

    public double radius() {
        return 3.0 + 5.0 * strength();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("Life");
        maxLife = Math.max(1, tag.getInt("MaxLife"));
        headingX = tag.getDouble("HX");
        headingZ = tag.getDouble("HZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
        tag.putInt("MaxLife", maxLife);
        tag.putDouble("HX", headingX);
        tag.putDouble("HZ", headingZ);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;                                     // you do not fight the weather
    }

    /** Everything a tornado needs: on the ground, wandering, for a while. */
    public static TornadoEntity spawn(ServerLevel level, Vec3 at, int seconds) {
        TornadoEntity t = new TornadoEntity(WakingWorld.TORNADO.get(), level);
        t.aimFrom(at, seconds > 0 ? seconds : WakingConfig.tornadoSeconds());
        level.addFreshEntity(t);
        return t;
    }
}
