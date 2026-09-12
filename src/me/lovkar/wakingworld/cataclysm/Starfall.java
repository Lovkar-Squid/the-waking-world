package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.item.WakingItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
                    Scars.set(level, top, rnd.nextDouble() < 0.30 * heat ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : (rnd.nextBoolean() ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState()));
                } else if (edge > 0.55 && rnd.nextDouble() < 0.25) {
                    // the rim is scorched rather than melted
                    if (state.is(BlockTags.DIRT)) Scars.set(level, top, Blocks.COARSE_DIRT.defaultBlockState());
                    else if (state.is(BlockTags.SAND)) Scars.set(level, top, Blocks.SOUL_SAND.defaultBlockState());
                }
                // a few small fires near the middle, on solid ground only
                if (heat > 0.4 && rnd.nextDouble() < 0.06) {
                    BlockPos above = top.above();
                    if (level.isEmptyBlock(above) && level.getBlockState(top).isFaceSturdy(level, top, net.minecraft.core.Direction.UP)) {
                        Scars.set(level, above, Blocks.FIRE.defaultBlockState());
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
        BlockState star = CataclysmBlocks.STARSTONE.get().defaultBlockState();
        int n = 3 + size * 3;

        // A star that came down on a colony tore nothing: there is no crater to put its core in the
        // floor of. The thing it was carrying is still worth having, though, and a rock lying on the
        // grass costs the town nothing - so it is LEFT there, on top of the ground, and if there is
        // no room for even that (it came down through a roof) it is handed over as Star Iron.
        if (me.lovkar.wakingworld.compat.Colonies.keepOff(level, floor)) {
            int laid = 0;
            for (int i = 0; i <= n && laid < 1 + size; i++) {
                BlockPos p = floor.above().offset(rnd.nextInt(3) - 1, rnd.nextInt(2), rnd.nextInt(3) - 1);
                if (Scars.gift(level, p, star)) laid++;
            }
            if (laid == 0) {
                ItemStack iron = new ItemStack(WakingItems.STAR_IRON.get(), 1 + size);
                ItemEntity drop = new ItemEntity(level, floor.getX() + 0.5, floor.getY() + 1.2, floor.getZ() + 0.5, iron);
                drop.setDeltaMovement(0, 0.2, 0);
                level.addFreshEntity(drop);
            }
            WakingWorld.LOGGER.info("cataclysm: a star came down on a colony at {} {} {} - no crater; {}",
                    floor.getX(), floor.getY(), floor.getZ(),
                    laid > 0 ? laid + " starstone left on the ground" : "its star iron was dropped");
            return;
        }

        BlockPos at = floor.below(1 + rnd.nextInt(2));
        Scars.set(level, at, star);
        for (int i = 0; i < n; i++) {
            BlockPos p = at.offset(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1, rnd.nextInt(3) - 1);
            BlockState there = level.getBlockState(p);
            if (there.isAir() || there.canOcclude()) Scars.set(level, p, star);
        }
    }

    /** The highest solid block in this column near the crater, or null if there is nothing to stand on. */
    private static BlockPos surface(ServerLevel level, BlockPos at, int reach) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below();
        if (Math.abs(top.getY() - at.getY()) > reach + 16) return null;
        return top;
    }
}
