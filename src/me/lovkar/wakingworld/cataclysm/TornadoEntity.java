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
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.advancement.ValueTrigger;
import me.lovkar.wakingworld.advancement.WakingTriggers;
import me.lovkar.wakingworld.ruin.Ruin;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class TornadoEntity extends Entity {
    /**
     * A self-expiring ticket the column drags along with it, so the ground it is walking over stays
     * ticking. Six hundred ticks is long enough to outlive any single step and short enough that a
     * dead tornado leaves nothing loaded behind it.
     */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> HOLD =
            net.minecraft.server.level.TicketType.create("wakingworld_tornado",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 600);

    private static final EntityDataAccessor<Float> DATA_AGE_FRACTION =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    /** Blocks the wind leaves alone whatever happens. */
    private static final java.util.Set<net.minecraft.world.level.block.Block> ROOTED = java.util.Set.of(
            Blocks.BEDROCK, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.REINFORCED_DEEPSLATE,
            Blocks.END_PORTAL_FRAME, Blocks.SPAWNER, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
            Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.BEACON, Blocks.CONDUIT, Blocks.LODESTONE);

    private double headingX = 1, headingZ = 0;
    /**
     * How many seconds each player has stood inside the column.
     *
     * <p>Transient on purpose: this is one storm, and a tornado that outlives a restart has taken
     * the reckoning with it. Nothing here is worth a line in the save file.</p>
     */
    private final java.util.Map<java.util.UUID, Integer> inside = new java.util.HashMap<>();
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
        Scars.writing(level, scar);
        try {
            serverTick(level);
        } finally {
            Scars.close();
        }
    }

    /** The scar it is scouring as it walks, so an hourglass can lay the country back down. */
    private java.util.UUID scar;

    private void serverTick(ServerLevel level) {
        life--;
        if (life <= 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.WEATHER, 2.0F, 1.6F);
            Survived.near(level, Omen.Kind.TORNADO, position());
            Scars.done(level, scar);
            scar = null;
            discard();
            return;
        }
        // a long ramp up, a long plateau, a ramp down
        float t = 1.0F - (float) life / maxLife;
        float s = Mth.clamp(Math.min(t / 0.15F, (1.0F - t) / 0.20F), 0.0F, 1.0F);
        this.entityData.set(DATA_AGE_FRACTION, s);

        walk(level);
        if (this.tickCount % 20 == 0 && s > 0.3F) eye(level);
        if (s > 0.15F) {
            pull(level, s);
            if (this.tickCount % 4 == 0) lift(level, s);
            // and the swathe it leaves: trees snapped, grass scoured off, laid on the ground it has
            // just crossed so the damage follows the real path instead of ringing where it stopped
            if (this.tickCount % 6 == 0 && me.lovkar.wakingworld.WakingConfig.terrainDamage()) {
                Aftermath.swathe(level, getX(), getZ(), radius() * 1.25, this.random);
            }
            // and the field it crossed. A flattened crop is worth more than another acre of
            // coarse dirt, because somebody planted it.
            if (this.tickCount % 10 == 0 && me.lovkar.wakingworld.WakingConfig.blight()) {
                Aftermath.blight(level, blockPosition(), radius() * 1.6, 0.5, this.random);
            }
        }
        // the roar is 4.5 s long: started every 4 it runs unbroken, started every half second it
        // would be nine copies of itself playing at once
        if (this.tickCount % 80 == 0) {
            level.playSound(null, getX(), getY(), getZ(), me.lovkar.wakingworld.WakingSounds.TORNADO_ROAR.get(),
                    SoundSource.WEATHER, 7.0F * Math.max(0.4F, s), 0.86F + this.random.nextFloat() * 0.1F);
        }
        if (this.tickCount % 10 == 0) WakingWorld.hooks.shakeAt(position(), 1.4F * s, 60);
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
        // Keep the ground under it ticking. A column walks about four blocks a second, so in half a
        // minute it is a hundred and thirty from where it started - and the moment it steps outside
        // whatever happens to be loaded it stops being ticked at all: it freezes on the spot, stops
        // walking, and stops drawing itself. On camera that is a shot of an empty field, which is
        // exactly what the ride shot came back as. The ticket expires on its own, so nothing is
        // held open behind it.
        if (this.tickCount % 20 == 0) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(
                    net.minecraft.core.BlockPos.containing(x, getY(), z));
            level.getChunkSource().addRegionTicket(HOLD, cp, 4, cp);
        }
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

    /** How high above the column's foot it carries something before it lets go of it. */
    private static final double THROW_AT = 20.0;

    /**
     * Everything inside the reach is dragged in and up - and anything that gets into the column
     * itself is taken off its feet, carried round and up, and then thrown clear.
     *
     * <p>Standing near it used to be a nudge and a scratch, which is a strange thing for a tornado
     * to be. There are two grips now. Out in the skirt you are pulled and pushed about and can walk
     * out of it if you mean to. Inside the column you are not walking anywhere: your own momentum
     * is mostly taken away from you, you go round it and up, and at {@value #THROW_AT} blocks above
     * its foot it has finished with you and flings you out - and from there the ground is your
     * problem, because a fall you did not choose is the whole point of having been picked up.</p>
     */
    private void pull(ServerLevel level, float s) {
        double reach = radius() * 2.6;
        double core = Math.max(2.5, radius() * 0.9);
        AABB box = new AABB(getX() - reach, getY() - 4, getZ() - reach, getX() + reach, getY() + 40, getZ() + reach);
        for (Entity e : level.getEntities(this, box, e -> !(e instanceof TornadoEntity))) {
            if (e instanceof net.minecraft.world.entity.player.Player p && p.isSpectator()) continue;
            Vec3 d = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
            double dist = d.length();
            if (dist > reach) continue;
            double grip = (1.0 - Math.min(1.0, dist / reach)) * s;
            double norm = 1.0 / Math.max(0.6, dist);              // never divide by nothing at the axis
            Vec3 round = new Vec3(-d.z, 0, d.x).scale(norm);
            Vec3 in = d.scale(norm);

            if (dist < core && s > 0.45) {
                double up = e.getY() - getY();
                if (up > THROW_AT) {
                    // it is done with you
                    Vec3 out = in.scale(-1.35);
                    e.setDeltaMovement(out.x, 0.5, out.z);
                    e.hurtMarked = true;
                    continue;                                     // and the fall from here is not reset
                }
                // caught: most of your own movement is taken, and the column has you
                e.setDeltaMovement(e.getDeltaMovement().scale(0.55)
                        .add(round.scale(0.62))
                        .add(in.scale(0.10))
                        .add(0, up < THROW_AT * 0.6 ? 0.62 : 0.22, 0));
                e.hurtMarked = true;
                e.fallDistance = 0;
                if (e instanceof LivingEntity living && this.tickCount % 10 == 0) {
                    living.hurt(level.damageSources().flyIntoWall(), 2.0F);
                }
                continue;
            }

            if (dist < 0.01) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(in.scale(0.16 * grip)).add(round.scale(0.24 * grip)).add(0, 0.28 * grip, 0));
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
            Ruin.mark(level, top);
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

    /**
     * What anybody actually sees. There is no funnel model - a tornado is only the air and the dirt
     * caught in it - so all of it is drawn here, and the first version drew fourteen particles up a
     * column twenty-six blocks tall, which from any distance was nothing at all.
     *
     * <p>Three things now: the column itself, wound as a helix so it reads as turning rather than as
     * a haze; a skirt of the ground it is standing on, which is what sells the size; and debris
     * flung clear of it. The ground it is over is sampled once and used for every block particle -
     * one lookup, not one per particle.</p>
     */
    private void spray(ServerLevel level, float s) {
        double r = radius();
        double turn = this.tickCount * 0.35;
        // the column: two twisted strands from the ground to the top of it
        for (int i = 0; i < 46; i++) {
            double up = this.random.nextDouble();
            double h = up * 30 * s;
            double rr = r * (0.30 + up * 0.95);
            double a = turn + up * 7.0 + (i % 2 == 0 ? 0 : Math.PI) + (this.random.nextDouble() - 0.5) * 0.5;
            double px = getX() + Math.cos(a) * rr;
            double pz = getZ() + Math.sin(a) * rr;
            Cataclysms.puff(level, ParticleTypes.CLOUD, px, getY() + h, pz, 1, 0, 0, 0, 0.02 + up * 0.05);
        }
        BlockPos under = BlockPos.containing(getX(), getY() - 1, getZ());
        BlockState ground = level.getBlockState(under);
        if (!ground.isAir()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            // the skirt where it meets the ground - the widest, dirtiest part of it
            Cataclysms.puff(level, dust, getX(), getY() + 0.4, getZ(), 60, r * 1.15, 0.8, r * 1.15, 0.55);
            // and what it is carrying, up the first third of the column
            for (int i = 0; i < 10; i++) {
                double a = turn * 1.4 + this.random.nextDouble() * Math.PI * 2;
                double rr = r * (0.5 + this.random.nextDouble() * 0.9);
                Cataclysms.puff(level, dust, getX() + Math.cos(a) * rr, getY() + this.random.nextDouble() * 11 * s,
                        getZ() + Math.sin(a) * rr, 3, 0.4, 0.6, 0.4, 0.35);
            }
        }
        // it drags the storm with it: a bolt now and then, close by, that starts no fires
        if (s > 0.45 && this.random.nextInt(90) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double d = 14 + this.random.nextDouble() * 26;
            BlockPos hit = BlockPos.containing(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
            net.minecraft.world.entity.LightningBolt bolt =
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(
                        level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, hit)));
                bolt.setVisualOnly(true);       // a trailer, not a forest fire
                level.addFreshEntity(bolt);
            }
        }
    }

    private void clientTick() {
        // the client draws it; nothing to do but exist
    }

    /**
     * Standing in it.
     *
     * <p>The column pulls hard enough that a player inside it is not there by accident, and it is
     * survivable in the right armour - which makes it exactly the sort of thing worth a line on
     * somebody's record. Counted a second at a time while they are inside, and never taken back:
     * stepping out does not undo having been in.</p>
     */
    private void eye(ServerLevel level) {
        double r = radius() * 0.9;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            if (dx * dx + dz * dz > r * r) continue;
            if (p.getY() < getY() - 4 || p.getY() > getY() + 40) continue;
            int secs = inside.merge(p.getUUID(), 1, Integer::sum);
            me.lovkar.wakingworld.advancement.WakingTriggers.IN_THE_EYE.get().trigger(p, secs);
        }
    }

    public double radius() {
        return 3.5 + 6.5 * strength();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("Life");
        maxLife = Math.max(1, tag.getInt("MaxLife"));
        headingX = tag.getDouble("HX");
        headingZ = tag.getDouble("HZ");
        if (tag.hasUUID("Scar")) scar = tag.getUUID("Scar");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
        tag.putInt("MaxLife", maxLife);
        tag.putDouble("HX", headingX);
        tag.putDouble("HZ", headingZ);
        if (scar != null) tag.putUUID("Scar", scar);
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
        t.scar = Scars.begin(level, BlockPos.containing(at.x, at.y, at.z), "a tornado");
        t.aimFrom(at, seconds > 0 ? seconds : WakingConfig.tornadoSeconds());
        level.addFreshEntity(t);
        return t;
    }
}
