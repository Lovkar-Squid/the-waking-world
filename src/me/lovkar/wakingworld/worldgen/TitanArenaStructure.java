package me.lovkar.wakingworld.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The Titan's arena: a great round floor of end stone bricks built out over the edge of an End
 * island, ringed by obsidian pillars, an altar in the middle. Only there does the Key of the
 * Titan work, and the Titan rises from the altar. Placed by the structure set
 * {@code wakingworld:titan_arenas} in the End highlands and midlands, well away from End Cities.
 * One procedural piece ({@link TitanArenaPiece}), so no NBT template is needed.
 */
public class TitanArenaStructure extends Structure {
    public static final MapCodec<TitanArenaStructure> CODEC = simpleCodec(TitanArenaStructure::new);
    /** The floor sits at least this high, whatever the island does. */
    public static final int MIN_Y = 52;
    public static final int MAX_Y = 92;

    public TitanArenaStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        // it wants the body of an island under it: the centre and four points 24 blocks out must all be land
        int highest = Integer.MIN_VALUE, lowest = Integer.MAX_VALUE;
        int[][] samples = {{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}, {17, 17}, {-17, -17}};
        for (int[] s : samples) {
            int h = context.chunkGenerator().getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            if (h < 40) return Optional.empty();
            highest = Math.max(highest, h);
            lowest = Math.min(lowest, h);
        }
        int floorY = Math.max(MIN_Y, Math.min(MAX_Y, highest + 1));
        BlockPos origin = new BlockPos(x, floorY, z);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new TitanArenaPiece(origin, context.random().nextLong()))));
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.TITAN_ARENA.get();
    }
}
