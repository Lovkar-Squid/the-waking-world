package me.lovkar.wakingworld.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** The Sleeper's Vault: a ruined tower stump on dry, fairly level ground with the vault dug under it (see {@link VaultPiece}). */
public class VaultStructure extends Structure {
    public static final MapCodec<VaultStructure> CODEC = simpleCodec(VaultStructure::new);

    public VaultStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        if (!Terrain.biomesOk(context, x, z, 0)) return Optional.empty(); // the biome first: it is the cheapest question
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        if (y != floor || y < context.heightAccessor().getMinBuildHeight() + 40) return Optional.empty(); // dry, and deep enough to dig
        int lo = y, hi = y;
        for (int[] s : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            int h = context.chunkGenerator().getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        if (hi - lo > 4) return Optional.empty();
        BlockPos origin = new BlockPos(x, lo, z);
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new VaultPiece(origin, seed))));
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.VAULT.get();
    }
}
