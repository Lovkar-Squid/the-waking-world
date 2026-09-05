package me.lovkar.wakingworld.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The land's memory of its fights, one per level, saved with the world: a {@link FightRecord} per
 * colossus. An Hourglass of Restoration turned over near a finished fight puts everything back -
 * over a few seconds, far to near, block by block, the way it was before the giant came.
 */
public final class RuinLedger extends SavedData {
    public static final String NAME = "wakingworld_ruins";
    /** Finished fights kept per level; the oldest go first. */
    public static final int KEEP = 10;
    /** Blocks put back per tick while restoring. */
    public static final int PER_TICK = 140;

    private static final Factory<RuinLedger> FACTORY = new Factory<>(RuinLedger::new, RuinLedger::load, null);

    private final Map<UUID, FightRecord> records = new LinkedHashMap<>();

    public static RuinLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The record of a colossus, opened on first use. */
    public FightRecord open(UUID colossus, BlockPos origin, long now) {
        FightRecord r = records.get(colossus);
        if (r == null) {
            r = new FightRecord(colossus, now, origin);
            records.put(colossus, r);
            setDirty();
        }
        return r;
    }

    public FightRecord record(UUID colossus) {
        return records.get(colossus);
    }

    /** The fight is over (the colossus is gone): the record can be restored from now on. */
    public void finish(UUID colossus, long now) {
        FightRecord r = records.get(colossus);
        if (r == null || r.finished) return;
        r.finished = true;
        r.finishedAt = now;
        if (r.before.isEmpty()) records.remove(colossus);
        // only so many old fights are kept
        List<FightRecord> done = new ArrayList<>();
        for (FightRecord x : records.values()) if (x.finished && !x.restoring()) done.add(x);
        done.sort(Comparator.comparingLong(x -> x.finishedAt));
        while (done.size() > KEEP) records.remove(done.remove(0).id);
        setDirty();
    }

    /** The nearest finished fight whose touched area is within range of a point, or null. */
    public FightRecord nearestFinished(BlockPos at, double range) {
        FightRecord best = null;
        double bestD = range;
        for (FightRecord r : records.values()) {
            if (!r.finished || r.restoring() || r.before.isEmpty()) continue;
            double d = r.distanceTo(at);
            if (d <= bestD) { bestD = d; best = r; }
        }
        return best;
    }

    /**
     * Some things that stand on a fight's ground are not the fight's to undo - the Titan's Gate the
     * arena raises when the Titan falls. Every record forgets these places, so the hourglass leaves
     * whatever stands there alone (a place still on a running restoration's list is simply skipped).
     */
    public void forget(Iterable<BlockPos> positions) {
        boolean any = false;
        for (BlockPos p : positions) {
            long key = p.asLong();
            for (FightRecord r : records.values()) if (r.before.remove(key) != null) any = true;
        }
        if (any) setDirty();
    }

    /**
     * Starts putting a fight's land back: from the bottom up (so sand and gravel find their floor
     * and plants their soil), and within a layer far to near, so the ruin closes in on where it fell.
     * Rubble still in the air is waited for first (see {@link #step}).
     */
    public void restore(FightRecord r) {
        if (r.restoring()) return;
        BlockPos c = r.center();
        List<long[]> order = new ArrayList<>(r.before.size());
        for (Long key : r.before.keySet()) order.add(new long[]{key});
        // the list is consumed from its end: last = lowest y, and among equals the farthest out
        order.sort((a, b) -> {
            BlockPos pa = BlockPos.of(a[0]), pb = BlockPos.of(b[0]);
            if (pa.getY() != pb.getY()) return Integer.compare(pb.getY(), pa.getY());
            return Double.compare(pa.distSqr(c), pb.distSqr(c));
        });
        r.restoring = order;
        r.restoreTotal = order.size();
        r.restoreWait = 0;
        setDirty();
    }

    /**
     * A vanilla falling block (gravel or sand the fight undermined) starting to fall inside an open
     * fight's area becomes that fight's rubble instead, so where it lands is remembered too.
     */
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel server)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity fb) || fb instanceof me.lovkar.wakingworld.entity.RubbleEntity) return;
        RuinLedger ledger = server.getDataStorage().get(FACTORY, NAME);
        if (ledger == null) return;
        BlockPos at = fb.blockPosition();
        for (FightRecord r : ledger.records.values()) {
            if (r.finished || r.before.isEmpty()) continue;
            if (at.getX() < r.minX - 4 || at.getX() > r.maxX + 4 || at.getZ() < r.minZ - 4 || at.getZ() > r.maxZ + 4 || at.getY() < r.minY - 8 || at.getY() > r.maxY + 24) continue;
            event.setCanceled(true);
            me.lovkar.wakingworld.entity.RubbleEntity rubble = new me.lovkar.wakingworld.entity.RubbleEntity(me.lovkar.wakingworld.WakingWorld.RUBBLE.get(), server);
            rubble.setUp(at, fb.getBlockState(), r.id);
            rubble.setPos(fb.getX(), fb.getY(), fb.getZ());
            rubble.setDeltaMovement(fb.getDeltaMovement());
            rubble.time = fb.time;
            rubble.dropItem = fb.dropItem;
            r.mark(server, at);
            r.rubbleUp();
            server.addFreshEntity(rubble);
            ledger.setDirty();
            return;
        }
    }

    /** Every server level tick: advances restorations. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        RuinLedger ledger = server.getDataStorage().get(FACTORY, NAME);
        if (ledger == null) return;
        ledger.tick(server);
    }

    private void tick(ServerLevel server) {
        Iterator<FightRecord> it = records.values().iterator();
        while (it.hasNext()) {
            FightRecord r = it.next();
            if (!r.restoring()) continue;
            step(server, r);
            if (r.restoring == null) it.remove();
        }
    }

    private void step(ServerLevel server, FightRecord r) {
        List<long[]> left = r.restoring;
        // the last stones are still in the air: let them land (ten seconds at the most)
        if (r.rubbleInFlight() > 0 && r.restoreWait++ < 200) return;
        if (left.isEmpty()) {
            settle(server, r);
            return;
        }
        int n = Math.min(PER_TICK, left.size());
        int done = r.restoreTotal - left.size();
        float progress = r.restoreTotal == 0 ? 1f : done / (float) r.restoreTotal;
        Vec3 soundAt = null;
        for (int i = 0; i < n; i++) {
            long key = left.remove(left.size() - 1)[0];
            BlockPos pos = BlockPos.of(key);
            BlockState was = r.before.remove(key);
            if (was == null) continue;
            BlockState now = server.getBlockState(pos);
            if (now == was) continue;
            server.setBlock(pos, was, 2); // clients only: no neighbour reactions until everything is back
            r.settle.add(key);
            if (i % 2 == 0) {
                BlockState shown = was.isAir() ? now : was;
                if (!shown.isAir()) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.05);
            }
            if (i % 9 == 0) server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.02);
            soundAt = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            // nobody gets walled in: whoever stands where a block came back is lifted onto it
            if (!was.isAir() && was.canOcclude()) {
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
                    BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, e.blockPosition());
                    e.teleportTo(e.getX(), Math.max(top.getY(), pos.getY() + 1), e.getZ());
                }
            }
        }
        if (soundAt != null && server.getGameTime() % 6 == 0) {
            server.playSound(null, soundAt.x, soundAt.y, soundAt.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, 0.5F + progress * 1.2F);
            if (server.getGameTime() % 18 == 0) server.playSound(null, soundAt.x, soundAt.y, soundAt.z, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
        setDirty();
    }

    /**
     * Everything is back: now the world gets to react, a few hundred blocks a tick - leaves learn
     * their logs are back (or they would decay), water finds its level, fences and walls join up.
     */
    private void settle(ServerLevel server, FightRecord r) {
        int n = 0;
        while (!r.settle.isEmpty() && n++ < 500) {
            BlockPos pos = BlockPos.of(r.settle.poll());
            BlockState s = server.getBlockState(pos);
            s.updateNeighbourShapes(server, pos, 3);
            server.blockUpdated(pos, s.getBlock());
        }
        if (r.settle.isEmpty()) {
            BlockPos c = r.center();
            server.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 6.0F, 0.8F);
            server.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 6.0F, 0.6F);
            server.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 120, 6, 3, 6, 0.1);
            r.restoring = null;
            r.before.clear();
        }
    }

    // ---- saved data ---------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (FightRecord r : records.values()) list.add(r.save(registries));
        tag.put("Fights", list);
        return tag;
    }

    private static RuinLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        RuinLedger ledger = new RuinLedger();
        ListTag list = tag.getList("Fights", 10);
        for (int i = 0; i < list.size(); i++) {
            FightRecord r = FightRecord.load(list.getCompound(i), registries);
            ledger.records.put(r.id, r);
        }
        return ledger;
    }
}
