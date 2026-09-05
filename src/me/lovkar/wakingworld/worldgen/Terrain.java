package me.lovkar.wakingworld.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Cheap terrain questions for structure placement. A structure's {@code findGenerationPoint} runs
 * for every candidate chunk that {@code /locate}, a surveyor's map or a Dead Letter looks at - long
 * before the biome is checked - so it must not sample whole noise columns by the hundred: that froze
 * the game for four minutes locating a kingdom. The quick surface here is the preliminary surface
 * level the game's own surface rules use (the {@code initial_density_without_jaggedness} router
 * function, no caves, no 3-D noise): a couple of dozen cheap density samples per column instead of
 * two full columns, and it runs a dozen blocks low; the fast surface refines it with the final
 * density read block by block from just above - the real surface to a block or two, three times
 * cheaper than one real column, six times cheaper than the wet-or-dry pair. The exact heights are
 * still read from the real columns, but only for sites that pass.
 */
public final class Terrain {
    private Terrain() {
    }

    /** The density above which the preliminary surface counts as solid ground (as in {@code NoiseChunk}). */
    private static final double SOLID = 0.390625;

    /** Whether the structure's biome predicate accepts the biome at (x, y, z). */
    public static boolean biomeOk(Structure.GenerationContext context, int x, int y, int z) {
        Holder<Biome> b = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), context.randomState().sampler());
        return context.validBiome().test(b);
    }

    /** The biome test at the centre and at four points {@code reach} blocks out, all at a surface-ish height. */
    public static boolean biomesOk(Structure.GenerationContext context, int x, int z, int reach) {
        int y = context.chunkGenerator().getSeaLevel() + 16;
        if (!biomeOk(context, x, y, z)) return false;
        if (reach <= 0) return true;
        return biomeOk(context, x + reach, y, z) && biomeOk(context, x - reach, y, z) && biomeOk(context, x, y, z + reach) && biomeOk(context, x, y, z - reach);
    }

    /** The preliminary surface at (x, z): the highest y whose ground density is solid, or MIN_VALUE when there is none. */
    public static int quickSurface(Structure.GenerationContext context, int x, int z) {
        return surface(context, context.randomState().router().initialDensityWithoutJaggedness(), SOLID, x, z);
    }

    /**
     * The surface as the real column would report it - the first solid block from the top of the final
     * density, caves and all - at a fraction of the column's cost: the preliminary surface says roughly
     * where to look (it runs a dozen blocks low), and the final density is then read block by block
     * from a little above that down to the first solid one. MIN_VALUE when there is no ground.
     */
    public static int fastSurface(Structure.GenerationContext context, int x, int z) {
        int q = quickSurface(context, x, z);
        if (q == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        DensityFunction f = context.randomState().router().finalDensity();
        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();
        int y = Math.min(maxY - 1, q + 24);
        if (f.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
            // higher ground than the hint allowed for (jagged peaks): climb until the air
            while (y + 1 < maxY && f.compute(new DensityFunction.SinglePointContext(x, y + 1, z)) > 0) y++;
            return y;
        }
        for (y--; y >= minY; y--) {
            if (f.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** The fast surface when it is dry land (at or above the sea), else MIN_VALUE - rivers, lakes and seas lie below the sea level. */
    public static int fastDry(Structure.GenerationContext context, int x, int z) {
        int h = fastSurface(context, x, z);
        return h == Integer.MIN_VALUE || h < context.chunkGenerator().getSeaLevel() ? Integer.MIN_VALUE : h;
    }

    private static int surface(Structure.GenerationContext context, DensityFunction f, double solid, int x, int z) {
        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();
        int start = Math.min(maxY - 1, 248);
        if (f.compute(new DensityFunction.SinglePointContext(x, start, z)) > solid) {
            int y = start;
            while (y + 1 < maxY && f.compute(new DensityFunction.SinglePointContext(x, y + 1, z)) > solid) y++;
            return y;
        }
        int found = Integer.MIN_VALUE;
        for (int y = start - 8; y >= minY; y -= 8) {
            if (f.compute(new DensityFunction.SinglePointContext(x, y, z)) > solid) {
                found = y;
                break;
            }
        }
        if (found == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        for (int k = 1; k < 8; k++) {
            if (f.compute(new DensityFunction.SinglePointContext(x, found + 1, z)) > solid) found++;
            else break;
        }
        return found;
    }

    /** The preliminary surface when it is dry land (above the sea), else MIN_VALUE - rivers, lakes and seas lie below the sea level. */
    public static int quickDry(Structure.GenerationContext context, int x, int z) {
        int h = quickSurface(context, x, z);
        return h == Integer.MIN_VALUE || h < context.chunkGenerator().getSeaLevel() ? Integer.MIN_VALUE : h;
    }

    /**
     * A quick verdict on a footprint: every {@code step}th column within {@code reach} must be dry and
     * within {@code slack} of the middle's fast surface. False rules a site out cheaply; true means
     * the real columns are worth reading.
     */
    public static boolean quickLevel(Structure.GenerationContext context, int x, int z, int reach, int step, int slack) {
        int h0 = fastDry(context, x, z);
        if (h0 == Integer.MIN_VALUE) return false;
        for (int dx = -reach; dx <= reach; dx += step) {
            for (int dz = -reach; dz <= reach; dz += step) {
                if (dx == 0 && dz == 0) continue;
                int h = fastDry(context, x + dx, z + dz);
                if (h == Integer.MIN_VALUE || Math.abs(h - h0) > slack) return false;
            }
        }
        return true;
    }

    /** The real surface block's y at (x, z), or MIN_VALUE when it is under water - two full noise columns, use sparingly. */
    public static int dryHeight(Structure.GenerationContext context, int x, int z) {
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        if (y != floor || y <= context.heightAccessor().getMinBuildHeight() + 5) return Integer.MIN_VALUE;
        return y;
    }
}
