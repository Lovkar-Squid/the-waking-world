package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A mass of blocks in flight: a boulder torn off a colossus, or a whole tree ripped out of the
 * ground. Flies on a ballistic arc, tumbles, and on landing blows a crater, hurts and throws
 * whatever is near, and then either settles into the crater (a boulder stays where it fell) or
 * bursts apart into its own blocks flying everywhere (a tree comes down as logs and leaves).
 * The blocks are synced as one compound tag so the client draws exactly what was thrown.
 */
public class ThrownMassEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<CompoundTag> DATA_BLOCKS =
            SynchedEntityData.defineId(ThrownMassEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Boolean> DATA_SCATTER =
            SynchedEntityData.defineId(ThrownMassEntity.class, EntityDataSerializers.BOOLEAN);

    /** One block of the mass: offset from the centre (blocks) and what it is. */
    public record Piece(int dx, int dy, int dz, BlockState state) {
    }

    private List<Piece> pieces = List.of();
    private float damage = 14.0F;
    private double radius = 3.5;
    private double craterRadius = 2.8;

    public ThrownMassEntity(EntityType<? extends ThrownMassEntity> type, Level level) {
        super(type, level);
    }

    // ---- what it is made of ----------------------------------------------------------------

    public void setPieces(List<Piece> list, boolean scatter) {
        this.pieces = List.copyOf(list);
        this.entityData.set(DATA_BLOCKS, write(list));
        this.entityData.set(DATA_SCATTER, scatter);
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public boolean scatters() {
        return this.entityData.get(DATA_SCATTER);
    }

    public void setPower(float damage, double radius, double craterRadius) {
        this.damage = damage;
        this.radius = radius;
        this.craterRadius = craterRadius;
    }

    /** A rough ball of one block type, radius ~1.4: the classic boulder. */
    public static List<Piece> boulder(BlockState state, int size) {
        List<Piece> out = new ArrayList<>();
        int r = Math.max(1, size);
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z <= r * r + r * 0.5) out.add(new Piece(x, y, z, state));
                }
        return out;
    }

    private static CompoundTag write(List<Piece> list) {
        CompoundTag tag = new CompoundTag();
        ListTag l = new ListTag();
        for (Piece p : list) {
            CompoundTag t = NbtUtils.writeBlockState(p.state());
            t.putByte("x", (byte) p.dx());
            t.putByte("y", (byte) p.dy());
            t.putByte("z", (byte) p.dz());
            l.add(t);
        }
        tag.put("p", l);
        return tag;
    }

    private List<Piece> read(CompoundTag tag) {
        List<Piece> out = new ArrayList<>();
        ListTag l = tag.getList("p", 10);
        var lookup = this.level().holderLookup(Registries.BLOCK);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            BlockState s = NbtUtils.readBlockState(lookup, t);
            if (s.isAir()) s = Blocks.STONE.defaultBlockState();
            out.add(new Piece(t.getByte("x"), t.getByte("y"), t.getByte("z"), s));
        }
        return out;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCKS, new CompoundTag());
        builder.define(DATA_SCATTER, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BLOCKS.equals(key)) this.pieces = read(this.entityData.get(DATA_BLOCKS));
    }

    // ---- flight ----------------------------------------------------------------------------

    @Override
    protected double getDefaultGravity() {
        return 0.035;
    }

    /** The fight this was thrown in - kept even after the thrower is gone, so the landing is still written down. */
    private java.util.UUID fight;

    @Override
    public void tick() {
        // whatever it does to the ground on landing is written into its thrower's fight record
        if (this.level() instanceof ServerLevel server) {
            if (fight == null && this.getOwner() instanceof ColossusEntity owner) fight = owner.getUUID();
            me.lovkar.wakingworld.ruin.RuinLedger ledger = me.lovkar.wakingworld.ruin.RuinLedger.get(server);
            me.lovkar.wakingworld.ruin.FightRecord record = fight == null ? null : ledger.record(fight);
            if (record == null && this.getOwner() instanceof ColossusEntity owner) record = owner.fightRecord();
            me.lovkar.wakingworld.ruin.Ruin.begin(ledger, record);
            try {
                super.tick();
            } finally {
                me.lovkar.wakingworld.ruin.Ruin.end();
            }
        } else {
            super.tick();
        }
        if (this.level().isClientSide) {
            if (this.tickCount % 2 == 0 && !pieces.isEmpty()) {
                Piece p = pieces.get(this.random.nextInt(pieces.size()));
                this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, p.state()),
                        this.getX() + p.dx(), this.getY() + 1.0 + p.dy(), this.getZ() + p.dz(), 0, -0.05, 0);
            }
        } else if (this.tickCount > 240 || this.getY() < this.level().getMinBuildHeight()) {
            this.discard();
        }
    }

    /** 70 = it landed: the ground shakes for whoever is near. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 70) {
            WakingWorld.hooks.shakeAt(this.position(), scatters() ? 1.6F : 2.2F, 22 + craterRadius * 6);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target instanceof ColossusPart || target instanceof ColossusEntity || target instanceof ThrownMassEntity) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // the burst below handles the damage; nothing extra for the thing we touched first
    }

    // ---- landing ---------------------------------------------------------------------------

    @Override
    protected void onHit(HitResult result) {
        if (this.isNoGravity()) return; // still in the giant's hand
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel server)) return;
        Vec3 at = result.getLocation();
        Entity owner = this.getOwner();
        AABB area = new AABB(at, at).inflate(radius);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && !(e instanceof ColossusEntity) && e.isAlive())) {
            double d = target.position().distanceTo(at);
            float dmg = (float) (damage * Math.max(0.35, 1.0 - d / (radius + 0.5)));
            target.hurt(owner instanceof LivingEntity living ? this.damageSources().mobProjectile(this, living) : this.damageSources().generic(), dmg);
            Vec3 push = target.position().subtract(at).normalize().scale(1.3).add(0, 0.6, 0);
            target.push(push.x, push.y, push.z);
            target.hurtMarked = true;
        }
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.6F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 20, craterRadius, 0.5, craterRadius, 0.03);
        this.level().broadcastEntityEvent(this, (byte) 70);
        Crater.blast(server, at, craterRadius, 30, 0.6, this.random);

        BlockPos center = BlockPos.containing(at.x, at.y + 0.5, at.z);
        if (scatters()) {
            // a tree comes apart: logs fly, leaves burst into dust
            int flung = 0;
            for (Piece p : pieces) {
                BlockPos pos = center.offset(p.dx(), Math.max(0, p.dy()), p.dz());
                if (p.state().is(BlockTags.LEAVES)) {
                    if (this.random.nextInt(3) == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state()),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.6, 0.6, 0.6, 0.2);
                    continue;
                }
                if (flung < 40 && server.isEmptyBlock(pos)) {
                    Crater.fling(server, pos, p.state(), at, 0.9, this.random); // spawns a falling block of that state at pos
                    flung++;
                } else {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.6, 0.6, 0.6, 0.2);
                }
            }
        } else {
            // a boulder stays: settle its blocks into the crater, lowest first
            List<Piece> sorted = new ArrayList<>(pieces);
            sorted.sort((a, b) -> Integer.compare(a.dy(), b.dy()));
            BlockPos base = center.below((int) Math.round(craterRadius * 0.5));
            for (Piece p : sorted) {
                Crater.settle(server, base.offset(p.dx(), p.dy(), p.dz()), p.state());
            }
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Pieces", write(pieces));
        tag.putBoolean("Scatter", scatters());
        tag.putFloat("Damage", damage);
        tag.putDouble("Radius", radius);
        tag.putDouble("Crater", craterRadius);
        if (fight != null) tag.putUUID("Fight", fight);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Pieces")) setPieces(read(tag.getCompound("Pieces")), tag.getBoolean("Scatter"));
        if (tag.contains("Damage")) damage = tag.getFloat("Damage");
        if (tag.contains("Radius")) radius = tag.getDouble("Radius");
        if (tag.contains("Crater")) craterRadius = tag.getDouble("Crater");
        if (tag.hasUUID("Fight")) fight = tag.getUUID("Fight");
    }
}
