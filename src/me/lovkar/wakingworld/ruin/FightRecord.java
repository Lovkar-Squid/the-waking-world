package me.lovkar.wakingworld.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything one colossus did to the land: for every block it broke, flung, trampled or buried,
 * what stood there before it came - and every spot where a piece of it (or of the ground it
 * threw) came to rest. Enough to put the land back exactly as it was (see {@link RuinLedger}).
 */
public final class FightRecord {
    /** No fight gets to remember more blocks than this. */
    public static final int CAP = 400_000;

    public final UUID id;
    public final long started;
    public final BlockPos origin;
    /** pos (asLong) -> what was there before the colossus touched it. Air where its rubble came to rest on open ground. */
    final Map<Long, BlockState> before = new HashMap<>();
    boolean finished;
    long finishedAt;
    /** A restoration in progress: what is left to put back, far to near. */
    List<long[]> restoring;
    int restoreTotal;
    int restoreWait;
    /** Positions put back so far - they get their neighbour updates once everything is in place. */
    java.util.ArrayDeque<Long> settle = new java.util.ArrayDeque<>();
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    FightRecord(UUID id, long started, BlockPos origin) {
        this.id = id;
        this.started = started;
        this.origin = origin;
    }

    /** Rubble of this fight still in the air (it reports where it lands; restoration waits for it). */
    int rubbleInFlight;

    /**
     * Remembers what is at pos right now, unless this fight already remembers it - and what is
     * around it, because breaking a block breaks its dependants too: the flower and the snow on
     * top of it, the torch on its side, the grass under a stone that lands on it. Taking a log
     * out also remembers the leaves near it, which would otherwise decay unrecorded.
     */
    public void mark(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        markOne(level, pos, state);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            markOne(level, n, level.getBlockState(n));
        }
        if (state.is(BlockTags.LOGS)) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -6; dx <= 6; dx++)
                for (int dy = -6; dy <= 6; dy++)
                    for (int dz = -6; dz <= 6; dz++) {
                        m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        BlockState s = level.getBlockState(m);
                        if (s.is(BlockTags.LEAVES)) markOne(level, m.immutable(), s);
                    }
        }
    }

    private void markOne(ServerLevel level, BlockPos pos, BlockState state) {
        long key = pos.asLong();
        if (before.containsKey(key) || before.size() >= CAP) return;
        before.put(key, state);
        grow(pos);
    }

    /**
     * A block of the fight's own rubble came to rest here: on restoration it goes (what was under
     * it is remembered, if anything). Its neighbours are remembered too - the grass it will turn
     * to dirt above all.
     */
    public void placed(ServerLevel level, BlockPos pos, BlockState was) {
        long key = pos.asLong();
        if (!before.containsKey(key) && before.size() < CAP) {
            before.put(key, was != null ? was : Blocks.AIR.defaultBlockState());
            grow(pos);
        }
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            markOne(level, n, level.getBlockState(n));
        }
    }

    public void rubbleUp() {
        rubbleInFlight++;
    }

    public void rubbleDown() {
        if (rubbleInFlight > 0) rubbleInFlight--;
    }

    public int rubbleInFlight() {
        return rubbleInFlight;
    }

    private void grow(BlockPos pos) {
        minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
        minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
        minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
    }

    public int size() {
        return before.size();
    }

    public boolean finished() {
        return finished;
    }

    public boolean restoring() {
        return restoring != null;
    }

    /** The middle of everything it touched (the origin until it touched anything). */
    public BlockPos center() {
        if (before.isEmpty()) return origin;
        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /** How far a point is from the nearest edge of the touched area, 0 inside it. */
    public double distanceTo(BlockPos p) {
        if (before.isEmpty()) return Math.sqrt(origin.distSqr(p));
        double dx = Math.max(0, Math.max(minX - p.getX(), p.getX() - maxX));
        double dz = Math.max(0, Math.max(minZ - p.getZ(), p.getZ() - maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- nbt ------------------------------------------------------------------------------

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("Started", started);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putBoolean("Finished", finished);
        tag.putLong("FinishedAt", finishedAt);
        // palette + parallel arrays: positions and palette indices
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        long[] positions = new long[before.size()];
        int[] states = new int[before.size()];
        int i = 0;
        for (Map.Entry<Long, BlockState> e : before.entrySet()) {
            Integer idx = index.get(e.getValue());
            if (idx == null) {
                idx = palette.size();
                palette.add(e.getValue());
                index.put(e.getValue(), idx);
            }
            positions[i] = e.getKey();
            states[i] = idx;
            i++;
        }
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        tag.put("Palette", pal);
        tag.putLongArray("Positions", positions);
        tag.putIntArray("States", states);
        if (restoring != null) {
            long[] left = new long[restoring.size()];
            for (int k = 0; k < left.length; k++) left[k] = restoring.get(k)[0];
            tag.putLongArray("Restoring", left);
            tag.putInt("RestoreTotal", restoreTotal);
        }
        return tag;
    }

    static FightRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        FightRecord r = new FightRecord(tag.getUUID("Id"), tag.getLong("Started"), NbtUtils.readBlockPos(tag, "Origin").orElse(BlockPos.ZERO));
        r.finished = tag.getBoolean("Finished");
        r.finishedAt = tag.getLong("FinishedAt");
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag pal = tag.getList("Palette", 10);
        List<BlockState> palette = new ArrayList<>(pal.size());
        for (int i = 0; i < pal.size(); i++) palette.add(NbtUtils.readBlockState(blocks, pal.getCompound(i)));
        long[] positions = tag.getLongArray("Positions");
        int[] states = tag.getIntArray("States");
        for (int i = 0; i < positions.length && i < states.length; i++) {
            int idx = states[i];
            BlockState s = idx >= 0 && idx < palette.size() ? palette.get(idx) : Blocks.AIR.defaultBlockState();
            r.before.put(positions[i], s);
            r.grow(BlockPos.of(positions[i]));
        }
        if (tag.contains("Restoring")) {
            r.restoring = new ArrayList<>();
            for (long p : tag.getLongArray("Restoring")) r.restoring.add(new long[]{p});
            r.restoreTotal = tag.getInt("RestoreTotal");
        }
        return r;
    }
}
