package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The floating leaves, and how they get taken down.
 *
 * <p><b>What goes wrong.</b> A building generates during the surface-structures step; the trees go
 * in at the vegetal step, which is later, and a tree whose trunk stands in the next chunk can drop
 * its canopy into a chunk whose walls were finished long before. Cutting the trunk out afterwards
 * does not help either: worldgen writes blocks with flag 2 - no neighbour update - so the orphaned
 * leaves keep whatever distance-to-a-log they were given and never learn that the log is gone. They
 * hang there for the life of the world, and they are the first thing anybody notices.</p>
 *
 * <p><b>Why handing them to vanilla does not work.</b> The obvious fix - give every leaf one
 * scheduled tick and let the game's own rule decide - was tried and is wrong, and it is worth
 * writing down why. A leaf recounts its distance as one more than the smallest of its neighbours'
 * <i>stored</i> distances, and an orphaned canopy is stale <i>consistently</i>: the leaf that used
 * to touch the trunk says 1, its neighbour says 2, and so on. One tick each moves the whole gradient
 * up by exactly one. Nothing reaches 7, nothing decays, and the sweep reports hundreds of leaves
 * "asked to count again" while every one of them is still hanging there. It would take seven full
 * rounds of ticks, in the right order, to dissolve one canopy.</p>
 *
 * <p><b>So it is answered directly.</b> The sweep collects the logs and the leaves in the circle,
 * then walks outward from every log through connected leaves for six steps - which is exactly the
 * rule the game applies, done once and correctly instead of iterated blindly - and whatever it never
 * reaches has no tree and is taken out. Logs are gathered from seven blocks wider than the leaves,
 * so a canopy hanging over the edge of the circle is not orphaned by the edge of the circle.</p>
 *
 * <p>It is spread over ticks - {@value #SLAB} rows of the circle at a time, then {@value #PER_TICK}
 * removals - because a kingdom's circle is a hundred and twenty blocks across and forty deep, and
 * nobody should see the server stop to rake leaves.</p>
 */
public final class Tidy {
    /** How many rows of x a single tick scans. */
    private static final int SLAB = 4;
    /** How many leaves a single tick takes down once the scan is done. */
    private static final int PER_TICK = 1200;
    /** A leaf lives while it can reach a log in this many steps - vanilla's own number. */
    private static final int REACH = 6;
    /** How far outside the circle logs still count, so its edge does not orphan a canopy. */
    private static final int MARGIN = 7;

    private static final class Sweep {
        final BlockPos c;
        final int radius, inner, down, up;
        final Set<Long> logs = new HashSet<>();
        final Set<Long> leaves = new HashSet<>();
        int x, taken, cut;
        List<BlockPos> doomed;

        Sweep(BlockPos c, int radius, int inner, int down, int up) {
            this.c = c;
            this.radius = radius;
            this.inner = inner;
            this.down = down;
            this.up = up;
            this.x = c.getX() - radius - MARGIN;
        }
    }

    private static final Map<ResourceKey<Level>, List<Sweep>> ACTIVE = new HashMap<>();

    private Tidy() {
    }

    /**
     * Rake once round a building.
     *
     * @param inner  everything growing within this radius of the middle is taken out outright (0: none)
     * @param radius leaves out to here are judged, and the ones with no tree left come down
     */
    public static void begin(ServerLevel level, BlockPos centre, int radius, int inner, int down, int up) {
        List<Sweep> list = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        for (Sweep s : list) if (s.c.equals(centre)) return;
        list.add(new Sweep(centre, radius, inner, down, up));
    }

    /** One slab of the oldest sweep per tick, then its removals; a finished sweep says what it found. */
    public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<Sweep> list = ACTIVE.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        Sweep s = list.get(0);
        if (s.doomed == null) scan(level, s);
        else fell(level, s);
        if (s.doomed != null && s.cut >= s.doomed.size()) {
            list.remove(0);
            WakingWorld.LOGGER.info("tidy: swept round {} {} {} - {} taken out inside the walls, {} leaves had no tree left",
                    s.c.getX(), s.c.getY(), s.c.getZ(), s.taken, s.doomed.size());
        }
    }

    /** Read the circle: clear what is growing inside the walls, and write down every log and leaf. */
    private static void scan(ServerLevel level, Sweep s) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), s.c.getY() + s.down);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, s.c.getY() + s.up);
        int wide = s.radius + MARGIN;
        for (int n = 0; n < SLAB && s.x <= s.c.getX() + wide; n++, s.x++) {
            int dx = s.x - s.c.getX();
            int span = (int) Math.sqrt((double) wide * wide - (double) dx * dx);
            for (int z = s.c.getZ() - span; z <= s.c.getZ() + span; z++) {
                int dz = z - s.c.getZ();
                int d2 = dx * dx + dz * dz;
                boolean judged = d2 <= s.radius * s.radius;          // leaves only inside the circle proper
                boolean in = s.inner > 0 && d2 <= s.inner * s.inner;
                if (!level.hasChunkAt(new BlockPos(s.x, minY, z))) continue;
                for (int y = minY; y <= maxY; y++) {
                    pos.set(s.x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (in && growing(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        s.taken++;
                    } else if (state.is(BlockTags.LOGS)) {
                        s.logs.add(pos.asLong());
                    } else if (judged && state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
                        s.leaves.add(pos.asLong());
                    }
                }
            }
        }
        if (s.x > s.c.getX() + wide) judge(s);
    }

    /** Vanilla's rule, applied once: six steps out from every log, through leaves. */
    private static void judge(Sweep s) {
        Map<Long, Integer> reached = new HashMap<>(s.logs.size() * 2);
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long l : s.logs) {
            reached.put(l, 0);
            queue.add(l);
        }
        while (!queue.isEmpty()) {
            long p = queue.poll();
            int d = reached.get(p);
            if (d >= REACH) continue;
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            for (Direction dir : Direction.values()) {
                long n = BlockPos.asLong(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                if (s.leaves.contains(n) && !reached.containsKey(n)) {
                    reached.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        s.doomed = new ArrayList<>();
        for (long l : s.leaves) {
            if (!reached.containsKey(l)) s.doomed.add(BlockPos.of(l));
        }
        s.logs.clear();
        s.leaves.clear();
    }

    /** Take them down, a batch at a time, with the neighbour updates so the rest of the wood settles. */
    private static void fell(ServerLevel level, Sweep s) {
        int end = Math.min(s.doomed.size(), s.cut + PER_TICK);
        for (; s.cut < end; s.cut++) {
            BlockPos p = s.doomed.get(s.cut);
            if (level.hasChunkAt(p) && level.getBlockState(p).getBlock() instanceof LeavesBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Anything that grew here rather than being built here. */
    private static boolean growing(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;      // a pond by the door is not a weed
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)
                || state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.MOSS_CARPET);
    }
}
