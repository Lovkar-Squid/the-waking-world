package me.lovkar.wakingworld.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Where the Void Reliquary stands: on the body of an outer End island - the plinth's footprint must
 * be land all round (no void within eight blocks), reasonably level once the chorus is ignored, and
 * high enough that the spire clears the island's ridges.
 */
public class ReliquaryStructure extends Structure {
    public static final MapCodec<ReliquaryStructure> CODEC = simpleCodec(ReliquaryStructure::new);

    public ReliquaryStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    /**
     * Whether a Titan arena could stand within {@code chunks} of this chunk: the arena set's potential
     * chunk in this cell and the cells round about (what an exclusion zone checks - a structure set
     * only gets one of those, and the reliquary's is spent on the end cities). One reliquary grew in
     * the middle of an arena once.
     */
    static boolean nearArena(GenerationContext context, int chunks) {
        net.minecraft.world.level.levelgen.structure.StructureSet set = context.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(me.lovkar.wakingworld.WakingWorld.MODID, "titan_arenas"));
        if (set == null || !(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement p)) return false;
        ChunkPos here = context.chunkPos();
        int cx0 = Math.floorDiv(here.x, p.spacing()), cz0 = Math.floorDiv(here.z, p.spacing());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos c = p.getPotentialStructureChunk(context.seed(), cx0 + dx, cz0 + dz);
                if (Math.abs(c.x - here.x) <= chunks && Math.abs(c.z - here.z) <= chunks) return true;
            }
        }
        return false;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        if (nearArena(context, 8)) return Optional.empty(); // the arena is 52 wide, the spire 10; eight chunks keeps the spire off its rim and out of its view
        List<Integer> hs = new ArrayList<>();
        for (int dx = -9; dx <= 9; dx += 3) {
            for (int dz = -9; dz <= 9; dz += 3) {
                int h = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                if (h < 40) return Optional.empty(); // the void, or the island's underside
                hs.add(h);
            }
        }
        Collections.sort(hs);
        int median = hs.get(hs.size() / 2);
        // chorus trees stand on the islands: tolerate a quarter of the samples being tall, but the rest must be level
        int off = 0;
        for (int h : hs) if (Math.abs(h - median) > 4) off++;
        if (off > hs.size() / 4) return Optional.empty();
        BlockPos origin = new BlockPos(x, median + 1, z);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new ReliquaryPiece(origin, context.random().nextLong()))));
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.RELIQUARY.get();
    }
}
