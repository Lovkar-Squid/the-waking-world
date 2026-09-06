package me.lovkar.wakingworld.cataclysm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What is left where a star came down. The crater itself is torn by {@link me.lovkar.wakingworld.entity.Crater};
 * this dresses it: a glassed, still-glowing floor of basalt and magma, a scorched rim, a few fires
 * that go out on their own, and - at the very bottom - the Starstone the thing was carrying.
 *
 * <p>Nothing here touches blocks that were not already broken by the impact, and the fire only
 * lands on solid ground, so a meteor cannot burn down a forest it did not hit.</p>
 */
public final class Starfall {
    private Starfall() {
    }

    public static void dress(ServerLevel level, BlockPos center, double radius, int size, boolean carriesStar, RandomSource rnd) {
        int r = (int) Math.ceil(radius);
        // the bowl's floor: whatever the blast left, glassed over
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                BlockPos top = surface(level, center.offset(dx, 0, dz), r);
                if (top == null) continue;
                BlockState state = level.getBlockState(top);
                if (state.isAir() || state.liquid() || !state.canOcclude()) continue;
                double edge = d / radius;                    // 0 in the middle, 1 at the rim
                double heat = 1.0 - edge;
                if (rnd.nextDouble() < heat * 0.55) {
                    level.setBlock(top, rnd.nextDouble() < 0.30 * heat ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : (rnd.nextBoolean() ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState()), 3);
                } else if (edge > 0.55 && rnd.nextDouble() < 0.25) {
                    // the rim is scorched rather than melted
                    if (state.is(BlockTags.DIRT)) level.setBlock(top, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                    else if (state.is(BlockTags.SAND)) level.setBlock(top, Blocks.SOUL_SAND.defaultBlockState(), 3);
                }
                // a few small fires near the middle, on solid ground only
                if (heat > 0.4 && rnd.nextDouble() < 0.06) {
                    BlockPos above = top.above();
                    if (level.isEmptyBlock(above) && level.getBlockState(top).isFaceSturdy(level, top, net.minecraft.core.Direction.UP)) {
                        level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
                    }
                }
            }
        }
        if (carriesStar) core(level, center, size, rnd);
    }

    /** The star itself: a small knot of Starstone in the floor of the crater, lit from inside. */
    private static void core(ServerLevel level, BlockPos center, int size, RandomSource rnd) {
        BlockPos floor = surface(level, center, 24);
        if (floor == null) floor = center;
        BlockPos at = floor.below(1 + rnd.nextInt(2));
        int n = 3 + size * 3;
        BlockState star = CataclysmBlocks.STARSTONE.get().defaultBlockState();
        level.setBlock(at, star, 3);
        for (int i = 0; i < n; i++) {
            BlockPos p = at.offset(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1, rnd.nextInt(3) - 1);
            BlockState there = level.getBlockState(p);
            if (there.isAir() || there.canOcclude()) level.setBlock(p, star, 3);
        }
    }

    /** The highest solid block in this column near the crater, or null if there is nothing to stand on. */
    private static BlockPos surface(ServerLevel level, BlockPos at, int reach) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below();
        if (Math.abs(top.getY() - at.getY()) > reach + 16) return null;
        return top;
    }
}
