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
import me.lovkar.wakingworld.ruin.Ruin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap.Types;

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
                Scars.set(level, on, Blocks.AIR.defaultBlockState());
            }
            Block dressed = ash(state, rnd);
            if (dressed == null) continue;
            Scars.set(level, ground, dressed.defaultBlockState());
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
            if (above.is(BlockTags.REPLACEABLE) && !above.isAir()) Scars.set(level, on, Blocks.AIR.defaultBlockState());
            BlockPos ground = on.below();
            BlockState state = level.getBlockState(ground);
            if (!state.getFluidState().isEmpty() || state.isAir()) continue;
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM)) {
                Scars.set(level, ground, rnd.nextDouble() < 0.12
                        ? Blocks.BLACK_GLAZED_TERRACOTTA.defaultBlockState()   // where the heat sat
                        : Blocks.COARSE_DIRT.defaultBlockState());
                burnt++;
            } else if (state.is(BlockTags.SAND) && rnd.nextDouble() < 0.3) {
                Scars.set(level, ground, Blocks.GLASS.defaultBlockState());   // sand, fused
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
                    Scars.set(level, on, Blocks.AIR.defaultBlockState());
                } else if (state.is(BlockTags.LOGS)) {
                    // snapped: everything above the stump goes
                    for (int y = on.getY(); y > on.getY() - 12; y--) {
                        BlockPos t = new BlockPos(ix, y, iz);
                        if (!level.getBlockState(t).is(BlockTags.LOGS)) break;
                        if (y <= groundOf(level, ix, iz) + 1 + rnd.nextInt(2)) break;   // leave a stump
                        Scars.set(level, t, Blocks.AIR.defaultBlockState());
                    }
                } else if (state.is(Blocks.GRASS_BLOCK) && rnd.nextDouble() < 0.5) {
                    Scars.set(level, on, Blocks.COARSE_DIRT.defaultBlockState());
                }
            }
        }
    }

    private static int groundOf(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
    }

    /**
     * Lava setting into rock, from the bottom of the flow upward.
     *
     * <p>A volcano that leaves lava is not a mountain, it is a hazard that never goes away: a
     * player who comes back in a week finds the same glowing channel, and nothing about the place
     * says the eruption is over. Real flows crust over from the toe up while the vent is still
     * bright, so that is what this does - the volcano raises {@code upTo} a little at a time and
     * everything below it turns to rock, leaving the crater pool last and, if the caller likes,
     * for good.</p>
     *
     * @param upTo the highest level that has cooled so far; lava above it is left alone
     * @return how many blocks set
     */
    public static int cool(ServerLevel level, int cx, int cz, double radius, int fromY, int upTo,
                           int slice, int slices, double chance, RandomSource rnd) {
        int set = 0;
        int r = (int) Math.ceil(radius);
        int n = -1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                // One slice of the disc per pulse. The whole cone has to be swept over and over
                // rather than in a single rising band, because lava that has already been passed
                // flows down into ground that was swept a minute ago - the first version left a
                // channel that was still running an hour later, because it had looked at that
                // height once, before the flow got there.
                if (++n % slices != slice) continue;
                int x = cx + dx, z = cz + dz;
                for (int y = fromY; y <= upTo; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(at);
                    if (!state.is(Blocks.LAVA)) continue;
                    // The toe sets first: a block near the bottom of the flow is much likelier to
                    // go on any given pass than one just under the crater, so the black creeps up
                    // the mountain instead of the whole flank turning at once.
                    double deep = (upTo - y) / (double) Math.max(1, upTo - fromY);
                    if (rnd.nextDouble() > chance * (0.35 + 0.65 * deep)) continue;
                    // what it sets into: mostly the black rock a flow leaves, obsidian where it
                    // stood deepest, and a little magma still holding its heat
                    double roll = rnd.nextDouble();
                    Block into = roll < 0.62 ? Blocks.BASALT
                            : roll < 0.82 ? Blocks.BLACKSTONE
                            : roll < 0.94 ? Blocks.OBSIDIAN
                            : Blocks.MAGMA_BLOCK;
                    Scars.set(level, at, into.defaultBlockState());
                    set++;
                    // Steam where it actually sets, not a puff over the crater. Cooling is slow by
                    // design and the only way to tell it apart from nothing happening is to watch
                    // the crust travel: this is what turns "it did not work" into "look at it".
                    if (rnd.nextInt(5) == 0) {
                        Cataclysms.puff(level, net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                x + 0.5, y + 1.0, z + 0.5, 2, 0.22, 0.10, 0.22, 0.012);
                        if (rnd.nextInt(4) == 0) {
                            Cataclysms.puff(level, net.minecraft.core.particles.ParticleTypes.LAVA,
                                    x + 0.5, y + 1.0, z + 0.5, 1, 0.15, 0.05, 0.15, 0.0);
                        }
                    }
                }
            }
        }
        return set;
    }

    /**
     * What a cataclysm does to a field.
     *
     * <p>Everything the five of them do is to the landscape, and a landscape is not what a player
     * has feelings about. A wheat field flattened and a fence knocked flat is worth more than
     * another acre of coarse dirt, because somebody planted that.</p>
     *
     * <p>It only touches what grows: crops go, farmland reverts to dirt in patches, grass is
     * scoured. It never breaks a block a player laid - the same rule the earthquake already
     * keeps - so a house in the path loses its garden and not its walls.</p>
     */
    public static int blight(ServerLevel level, BlockPos at, double radius, double strength, RandomSource rnd) {
        int hit = 0;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                if (rnd.nextDouble() > strength * (1.0 - d / radius) + 0.03) continue;
                int x = at.getX() + dx, z = at.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (top <= level.getMinBuildHeight() + 1) continue;
                BlockPos on = new BlockPos(x, top - 1, z);
                BlockState state = level.getBlockState(on);
                if (state.is(BlockTags.CROPS) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)
                        || state.is(Blocks.SUGAR_CANE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) {
                    if (me.lovkar.wakingworld.compat.Colonies.keepOff(level, on)) continue;   // a colony's fields are not blighted
                    Ruin.mark(level, on);
                    level.destroyBlock(on, false);
                    hit++;
                    continue;
                }
                if (state.is(Blocks.FARMLAND) && rnd.nextDouble() < 0.55) {
                    Scars.set(level, on, Blocks.DIRT.defaultBlockState());      // ploughed under
                    hit++;
                }
            }
        }
        return hit;
    }
}
