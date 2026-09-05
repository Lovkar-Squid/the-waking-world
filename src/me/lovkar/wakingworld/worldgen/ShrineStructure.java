package me.lovkar.wakingworld.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The shrines of the sleepers: six small temples and tombs, one per kind of colossus, each built
 * of that kind's stuff and each holding, in a chest at its heart, a Horn of Waking and the odd
 * treasure - and a hint of what sleeps under the land: a heap of the kind's blocks with a live
 * core glowing in it. {@code {"type": "wakingworld:shrine", "kind": "ice"}}. One procedural
 * piece ({@link ShrinePiece}) per shrine; the biomes come from the structure JSON as usual.
 */
public class ShrineStructure extends Structure {
    public static final MapCodec<ShrineStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
    ).apply(i, ShrineStructure::new));

    public final String kind;

    public ShrineStructure(Structure.StructureSettings settings, String kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        if (!Terrain.biomesOk(context, x, z, 0)) return Optional.empty(); // the biome first: it is the cheapest question
        boolean sunken = "prismarine".equals(kind);
        Heightmap.Types type = sunken ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, type, context.heightAccessor(), context.randomState());
        if (y <= context.heightAccessor().getMinBuildHeight() + 5) return Optional.empty();
        if (!sunken) {
            // not in a lake or on a cliff: the ground around must be roughly level and dry
            int lo = y, hi = y;
            for (int[] s : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
                int h = context.chunkGenerator().getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                int floor = context.chunkGenerator().getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                if (h != floor) return Optional.empty(); // water there
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > 6) return Optional.empty();
            y = lo;
        } else {
            int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            if (surface - y < 6) return Optional.empty(); // it wants deep water over it
        }
        BlockPos origin = new BlockPos(x, y, z);
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new ShrinePiece(kind, origin, seed))));
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.SHRINE.get();
    }
}
