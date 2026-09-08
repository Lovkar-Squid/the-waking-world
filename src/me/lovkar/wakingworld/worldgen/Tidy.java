package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p><b>What this does.</b> The first time somebody comes near a building, it sweeps the ground
 * around it once: everything green inside the walls is simply taken out, and every leaf in the wider
 * circle is handed one scheduled tick. That tick is vanilla's own: the leaf recounts its distance
 * from the nearest log, and one with no log left decays properly, with its particles and its sapling
 * - and takes its neighbours with it, because they get ticks of their own. So the wood tidies itself
 * and the mod does not have to guess which leaf belonged to which tree.</p>
 *
 * <p>It is spread over ticks - {@value #SLAB} rows of a circle at a time - because a kingdom's circle
 * is a hundred blocks across and forty deep, and nobody should see the server stop to rake leaves.</p>
 */
public final class Tidy {
    /** How many rows of x a single tick gets through. */
    private static final int SLAB = 4;

    private static final class Sweep {
        final BlockPos c;
        final int radius, inner, down, up;
        int x, taken, ticked;

        Sweep(BlockPos c, int radius, int inner, int down, int up) {
            this.c = c;
            this.radius = radius;
            this.inner = inner;
            this.down = down;
            this.up = up;
            this.x = c.getX() - radius;
        }
    }

    private static final Map<ResourceKey<Level>, List<Sweep>> ACTIVE = new HashMap<>();

    private Tidy() {
    }

    /**
     * Rake once round a building.
     *
     * @param inner  everything growing within this radius of the middle is taken out outright (0: none)
     * @param radius leaves out to here are asked to recount, and the orphans fall
     */
    public static void begin(ServerLevel level, BlockPos centre, int radius, int inner, int down, int up) {
        List<Sweep> list = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        for (Sweep s : list) if (s.c.equals(centre)) return;
        list.add(new Sweep(centre, radius, inner, down, up));
    }

    /** One slab of the oldest sweep per tick; a finished sweep says what it found and goes. */
    public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<Sweep> list = ACTIVE.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        Sweep s = list.get(0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), s.c.getY() + s.down);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, s.c.getY() + s.up);
        for (int n = 0; n < SLAB && s.x <= s.c.getX() + s.radius; n++, s.x++) {
            int dx = s.x - s.c.getX();
            int span = (int) Math.sqrt((double) s.radius * s.radius - (double) dx * dx);
            for (int z = s.c.getZ() - span; z <= s.c.getZ() + span; z++) {
                int dz = z - s.c.getZ();
                boolean in = s.inner > 0 && dx * dx + dz * dz <= s.inner * s.inner;
                if (!level.hasChunkAt(new BlockPos(s.x, minY, z))) continue;
                for (int y = minY; y <= maxY; y++) {
                    pos.set(s.x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (in && growing(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        s.taken++;
                    } else if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
                        level.scheduleTick(pos.immutable(), state.getBlock(), 1 + ((s.x + y + z) & 7));
                        s.ticked++;
                    }
                }
            }
        }
        if (s.x > s.c.getX() + s.radius) {
            list.remove(0);
            WakingWorld.LOGGER.info("tidy: swept round {} {} {} - {} taken out, {} leaves asked to count again",
                    s.c.getX(), s.c.getY(), s.c.getZ(), s.taken, s.ticked);
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
