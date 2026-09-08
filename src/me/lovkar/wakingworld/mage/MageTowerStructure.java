package me.lovkar.wakingworld.mage;

import com.mojang.serialization.MapCodec;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.worldgen.Terrain;
import me.lovkar.wakingworld.worldgen.WakingStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Where the dark mage's tower stands.
 *
 * <p>Two moods, and the site decides which: <b>deep in a wood</b>, on ground level enough to build
 * on, where the tower's spire is the only thing above the canopy for a long way; or <b>out of a
 * mountain</b>, planted on the lip of a real drop so that its uphill half is swallowed by the rock
 * and its downhill half stands clear on a spur, with the balcony hanging over nothing. Both are the
 * same tower - it is the country that makes them look different, which is much better than two
 * buildings, because a player who finds the second one after the first says "it's the same tower"
 * and means it as a compliment.</p>
 *
 * <p><b>It is never in a kingdom.</b> He is not the king's man and he is not anybody's neighbour;
 * the structure set keeps its distance from villages, and the spacing is wide enough that finding
 * one is a journey. The door faces whichever of the four ways has ground nearest the threshold, so
 * a tower on a cliff does not open onto thin air.</p>
 */
public class MageTowerStructure extends Structure {
    public static final MapCodec<MageTowerStructure> CODEC = simpleCodec(MageTowerStructure::new);

    /** How far out the footprint is judged - the tower is twelve across, the balcony reaches ten. */
    private static final int REACH = 11;

    public MageTowerStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WakingConfig.mage()) return Optional.empty();
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        if (!Terrain.biomesOk(context, x, z, 0)) return Optional.empty();   // the cheapest question first

        int here = Terrain.fastDry(context, x, z);
        if (here == Integer.MIN_VALUE) return Optional.empty();
        if (here < context.chunkGenerator().getSeaLevel() + 2) return Optional.empty();

        // eight bearings around the footprint: how the ground lies decides the mood, and whether there is one at all
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            int sx = x + (int) Math.round(Math.cos(a) * REACH), sz = z + (int) Math.round(Math.sin(a) * REACH);
            int h = Terrain.fastSurface(context, sx, sz);
            if (h == Integer.MIN_VALUE) return Optional.empty();
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        boolean crag = hi - here >= 7 && here - lo >= 6;      // a real face: rock above, a drop below
        boolean wood = hi - lo <= 5;                           // level enough to build flat
        if (!crag && !wood) return Optional.empty();
        if (crag && hi - here > 22) return Optional.empty();    // any deeper and the whole tower is inside the hill

        // the real column, once the site is worth it
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        if (y != floor) return Optional.empty();               // dry: no towers in a lake
        if (y + MageTowerPiece.TOP + 4 >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();

        // the door takes whichever way has ground nearest the threshold
        Rotation rot = Rotation.NONE;
        int best = Integer.MAX_VALUE;
        int[][] ways = {{0, 1, Rotation.NONE.ordinal()}, {1, 0, Rotation.COUNTERCLOCKWISE_90.ordinal()},
                {0, -1, Rotation.CLOCKWISE_180.ordinal()}, {-1, 0, Rotation.CLOCKWISE_90.ordinal()}};
        for (int[] w : ways) {
            int h = Terrain.fastSurface(context, x + w[0] * 10, z + w[1] * 10);
            if (h == Integer.MIN_VALUE) continue;
            int cost = Math.abs(h - y) * 2 + (h > y ? 6 : 0);  // ground above the door costs more than ground below it
            if (cost < best) {
                best = cost;
                rot = Rotation.values()[w[2]];
            }
        }

        BlockPos origin = new BlockPos(x, y + 1, z);
        long seed = context.random().nextLong();
        Rotation facing = rot;
        boolean onCrag = crag;
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new MageTowerPiece(origin, facing, seed, onCrag))));
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.MAGE_TOWER.get();
    }
}
