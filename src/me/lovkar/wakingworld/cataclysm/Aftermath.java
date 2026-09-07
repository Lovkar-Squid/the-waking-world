package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a cataclysm leaves behind.
 *
 * <p>Everything in this package used to be over the moment it stopped: the mountain stood there, the
 * crater sat there, and the rest of the country looked exactly as it had an hour before. A player
 * who missed the event had no way of knowing one had happened. These are the marks - ash downwind of
 * a vent, ground burnt round a strike, a swathe of snapped and stripped country where a tornado
 * walked - and they are what somebody finds days later and goes to look at.</p>
 *
 * <p>All of it is written thinly and at random, one block in a handful rather than a solid carpet:
 * the point is a country that looks as though something happened to it, not a country replaced.</p>
 */
public final class Aftermath {
    private Aftermath() {
    }

    /**
     * Ash on the ground downwind of a vent: heaviest near it, thinning out to nothing, and laid in a
     * lobe rather than a circle because ash goes where the wind takes it. The surface is dressed
     * rather than replaced - grass goes to coarse dirt, sand and snow keep their own colour - and
     * anything growing on it is killed.
     */
    public static int ashfall(ServerLevel level, BlockPos vent, double bearing, double reach, RandomSource rnd) {
        int laid = 0;
        // the budget is capped as well as scaled: a wide foot would otherwise put ten thousand
        // attempts through here inside one tick, and cover more country in grey than the mountain
        // itself covers in rock
        int tries = Math.min(4200, (int) (reach * reach * 0.42));
        for (int i = 0; i < tries; i++) {
            // a lobe: far along the wind, narrow across it
            double along = Math.pow(rnd.nextDouble(), 0.65) * reach;
            double across = (rnd.nextDouble() - 0.5) * reach * 0.62 * (0.35 + along / reach);
            double x = vent.getX() + Math.cos(bearing) * along - Math.sin(bearing) * across;
            double z = vent.getZ() + Math.sin(bearing) * along + Math.cos(bearing) * across;
            // thinner the further out, and never solid even at the vent
            // thin even at the vent: this is a dusting that tells you what happened, not a new biome
            double chance = 0.34 * (1.0 - along / reach) + 0.03;
            if (rnd.nextDouble() > chance) continue;
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockPos on = new BlockPos(ix, top, iz);
            BlockPos ground = on.below();
            BlockState state = level.getBlockState(ground);
            if (!state.getFluidState().isEmpty()) continue;                 // ash on water is nothing
            // whatever was growing here is dead
            BlockState above = level.getBlockState(on);
            if (above.is(BlockTags.REPLACEABLE) && !above.isAir()) {
                level.setBlock(on, Blocks.AIR.defaultBlockState(), 2);
            }
            Block dressed = ash(state, rnd);
            if (dressed == null) continue;
            level.setBlock(ground, dressed.defaultBlockState(), 2);
            laid++;
        }
        WakingWorld.LOGGER.info("cataclysm: {} blocks of ash fell downwind of {}", laid, vent);
        return laid;
    }

    /** What a block looks like with ash on it, or null when ash would not show on it anyway. */
    private static Block ash(BlockState state, RandomSource rnd) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK)) {
            return rnd.nextDouble() < 0.22 ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
        }
        if (state.is(BlockTags.SAND)) return rnd.nextDouble() < 0.4 ? Blocks.GRAVEL : Blocks.SUSPICIOUS_SAND;
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)) {
            return rnd.nextDouble() < 0.5 ? Blocks.TUFF : null;
        }
        if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW_BLOCK)) return Blocks.GRAVEL;
        return null;
    }

    /**
     * Ground burnt round a strike, past the crater the blast itself makes: grass scorched to coarse
     * dirt, the odd patch of it turned to glass where the heat sat longest, and everything growing
     * on it gone. Thin, and thinning outwards.
     */
    public static int scorch(ServerLevel level, BlockPos at, double from, double to, RandomSource rnd) {
        int burnt = 0;
        int tries = (int) (to * to * 2.2);
        for (int i = 0; i < tries; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = from + Math.sqrt(rnd.nextDouble()) * (to - from);
            int ix = (int) Math.floor(at.getX() + Math.cos(a) * d);
            int iz = (int) Math.floor(at.getZ() + Math.sin(a) * d);
            if (rnd.nextDouble() > 0.6 * (1.0 - (d - from) / Math.max(1, to - from)) + 0.06) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockPos on = new BlockPos(ix, top, iz);
            BlockState above = level.getBlockState(on);
            if (above.is(BlockTags.REPLACEABLE) && !above.isAir()) level.setBlock(on, Blocks.AIR.defaultBlockState(), 2);
            BlockPos ground = on.below();
            BlockState state = level.getBlockState(ground);
            if (!state.getFluidState().isEmpty() || state.isAir()) continue;
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM)) {
                level.setBlock(ground, rnd.nextDouble() < 0.12
                        ? Blocks.BLACK_GLAZED_TERRACOTTA.defaultBlockState()   // where the heat sat
                        : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                burnt++;
            } else if (state.is(BlockTags.SAND) && rnd.nextDouble() < 0.3) {
                level.setBlock(ground, Blocks.GLASS.defaultBlockState(), 2);   // sand, fused
                burnt++;
            }
        }
        return burnt;
    }

    /**
     * Where a tornado has been: a swathe of country with the tops taken off it. Trees are snapped -
     * the trunk keeps its stump and loses everything above it - the grass is scoured off, and what is
     * left is bare and obvious from the air for as long as nobody plants it again.
     *
     * <p>Called from the column as it walks, on the ground it has just crossed, so the damage lies
     * along the real path rather than in a circle round wherever it happened to stop.</p>
     */
    public static void swathe(ServerLevel level, double cx, double cz, double radius, RandomSource rnd) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                if (rnd.nextDouble() > 0.30 * (1.0 - d / radius) + 0.02) continue;
                int ix = (int) Math.floor(cx) + dx, iz = (int) Math.floor(cz) + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
                if (top <= level.getMinBuildHeight() + 1) continue;
                BlockPos on = new BlockPos(ix, top - 1, iz);
                BlockState state = level.getBlockState(on);
                if (state.is(BlockTags.LEAVES)) {
                    level.setBlock(on, Blocks.AIR.defaultBlockState(), 2);
                } else if (state.is(BlockTags.LOGS)) {
                    // snapped: everything above the stump goes
                    for (int y = on.getY(); y > on.getY() - 12; y--) {
                        BlockPos t = new BlockPos(ix, y, iz);
                        if (!level.getBlockState(t).is(BlockTags.LOGS)) break;
                        if (y <= groundOf(level, ix, iz) + 1 + rnd.nextInt(2)) break;   // leave a stump
                        level.setBlock(t, Blocks.AIR.defaultBlockState(), 2);
                    }
                } else if (state.is(Blocks.GRASS_BLOCK) && rnd.nextDouble() < 0.5) {
                    level.setBlock(on, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
        }
    }

    private static int groundOf(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
    }
}
