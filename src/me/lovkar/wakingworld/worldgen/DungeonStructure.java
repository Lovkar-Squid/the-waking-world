package me.lovkar.wakingworld.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The two underground dungeons: {@code {"type": "wakingworld:dungeon", "kind": "cistern"}} - the
 * Drowned Cistern under a pump house by the water - and {@code "kind": "forge"} - the Ember Forge
 * under a ruined smithy in the dry lands. Each wants a small patch of level dry ground for its
 * entrance, and solid ground over the whole of its hall: the four rotations are tried until the
 * hall lies buried, so no roof ever pokes out of a riverbed or a slope.
 */
public class DungeonStructure extends Structure {
    public static final MapCodec<DungeonStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
    ).apply(i, DungeonStructure::new));

    public final String kind;

    public DungeonStructure(Structure.StructureSettings settings, String kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        boolean forge = "forge".equals(kind);
        if (!Terrain.biomesOk(context, x, z, 0)) return Optional.empty(); // the biome first: it is the cheapest question
        int y = RuinStructure.siteHeight(context, x, z, forge ? 6 : 4, 3);
        if (y == Integer.MIN_VALUE) return Optional.empty();
        if (y < context.chunkGenerator().getSeaLevel() + 1 || y - 26 < context.heightAccessor().getMinBuildHeight() + 8) return Optional.empty();
        Rotation rot = null;
        int first = context.random().nextInt(4);
        for (int k = 0; k < 4 && rot == null; k++) {
            Rotation r = Rotation.values()[(first + k) % 4];
            if (buried(context, x, y, z, r, forge, true) && buried(context, x, y, z, r, forge, false)) rot = r; // the cheap look, then the real ground
        }
        if (rot == null) return Optional.empty();
        BlockPos origin = new BlockPos(x, y, z);
        Rotation chosen = rot;
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(origin, builder -> {
            if (forge) builder.addPiece(new ForgePiece(origin, chosen, seed));
            else builder.addPiece(new CisternPiece(origin, chosen, seed));
        }));
    }

    /** Whether the hall's roof (and the forge's stair) would lie under solid ground everywhere, in this rotation - judged on the preliminary surface when {@code quick}, else on the real columns. */
    private static boolean buried(GenerationContext context, int x, int y, int z, Rotation rot, boolean forge, boolean quick) {
        int[][] samples;
        int[] minFloor; // the lowest ground surface allowed at each sample, relative to the origin
        if (forge) {
            int h = ForgePiece.HALF, hz = ForgePiece.HZ, roof = ForgePiece.C + 1;
            samples = new int[][]{{0, hz}, {-h, hz - h}, {h, hz - h}, {-h, hz + h}, {h, hz + h}, {-h, hz}, {h, hz}, {0, hz - h}, {0, hz + h}, {0, -12}};
            minFloor = new int[samples.length];
            java.util.Arrays.fill(minFloor, roof + 3);
            minFloor[samples.length - 1] = -3; // the stair's tunnel halfway down
        } else {
            int h = CisternPiece.HALF, roof = CisternPiece.C + 1;
            samples = new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}, {0, -h}, {0, h}, {-h, 0}, {h, 0}};
            minFloor = new int[samples.length];
            java.util.Arrays.fill(minFloor, roof + 3);
        }
        for (int i = 0; i < samples.length; i++) {
            int wx = LocalPiece.worldX(x, rot, samples[i][0], samples[i][1]), wz = LocalPiece.worldZ(z, rot, samples[i][0], samples[i][1]);
            int floor = quick ? Terrain.fastSurface(context, wx, wz) : context.chunkGenerator().getBaseHeight(wx, wz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            if (floor == Integer.MIN_VALUE || floor < y + minFloor[i] - (quick ? 2 : 0)) return false; // the fast surface is good to a block or two
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.DUNGEON.get();
    }
}
