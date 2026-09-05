package me.lovkar.wakingworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.resources.ResourceKey;

/**
 * A piece drawn column by column in its own local frame (origin under the entrance, front +z),
 * rotated into the world. Subclasses answer {@link #column} for every local column inside their
 * extents; every block they set is rotated with the piece. Only the columns inside the chunk being
 * generated are visited, so any chunk can be generated on its own.
 */
public abstract class LocalPiece extends StructurePiece {
    protected final int cx, cy, cz;
    protected final Rotation rot;
    protected final long seed;

    protected LocalPiece(StructurePieceType type, BlockPos origin, Rotation rot, long seed, int lx0, int lz0, int lx1, int lz1, int dy0, int dy1) {
        super(type, 0, box(origin, rot, lx0, lz0, lx1, lz1, dy0, dy1));
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.rot = rot;
        this.seed = seed;
    }

    protected LocalPiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.rot = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putInt("Rot", rot.ordinal());
        tag.putLong("Seed", seed);
    }

    private static BoundingBox box(BlockPos o, Rotation rot, int lx0, int lz0, int lx1, int lz1, int dy0, int dy1) {
        int[][] corners = {{lx0, lz0}, {lx1, lz0}, {lx0, lz1}, {lx1, lz1}};
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : corners) {
            int wx = worldX(o.getX(), rot, c[0], c[1]), wz = worldZ(o.getZ(), rot, c[0], c[1]);
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        return new BoundingBox(minX, o.getY() + dy0, minZ, maxX, o.getY() + dy1, maxZ);
    }

    static int worldX(int cx, Rotation rot, int lx, int lz) {
        return switch (rot) {
            case CLOCKWISE_90 -> cx - lz;
            case CLOCKWISE_180 -> cx - lx;
            case COUNTERCLOCKWISE_90 -> cx + lz;
            default -> cx + lx;
        };
    }

    static int worldZ(int cz, Rotation rot, int lx, int lz) {
        return switch (rot) {
            case CLOCKWISE_90 -> cz + lx;
            case CLOCKWISE_180 -> cz - lz;
            case COUNTERCLOCKWISE_90 -> cz - lx;
            default -> cz + lz;
        };
    }

    protected int worldX(int lx, int lz) {
        return worldX(cx, rot, lx, lz);
    }

    protected int worldZ(int lx, int lz) {
        return worldZ(cz, rot, lx, lz);
    }

    /** Local x of a world column. */
    protected int localX(int wx, int wz) {
        int dx = wx - cx, dz = wz - cz;
        return switch (rot) {
            case CLOCKWISE_90 -> dz;
            case CLOCKWISE_180 -> -dx;
            case COUNTERCLOCKWISE_90 -> -dz;
            default -> dx;
        };
    }

    protected int localZ(int wx, int wz) {
        int dx = wx - cx, dz = wz - cz;
        return switch (rot) {
            case CLOCKWISE_90 -> -dx;
            case CLOCKWISE_180 -> -dz;
            case COUNTERCLOCKWISE_90 -> dx;
            default -> dz;
        };
    }

    protected int hash(int x, int y, int z) {
        long h = x * 341873128712L + y * 97531234567L + z * 132897987541L + seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h & 0xFF);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox mine = this.boundingBox;
        int x0 = Math.max(box.minX(), mine.minX()), x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ()), z1 = Math.min(box.maxZ(), mine.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                column(level, pos, random, wx, wz, localX(wx, wz), localZ(wx, wz));
            }
        }
    }

    /** Draws one column: {@code wx, wz} in the world, {@code lx, lz} in the piece's frame. */
    protected abstract void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz);

    /** Sets a block at a world column and a height above the origin, rotated with the piece. */
    protected void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int dy, int wz, BlockState s) {
        pos.set(wx, cy + dy, wz);
        BlockState r = rot != Rotation.NONE ? s.rotate(rot) : s;
        level.setBlock(pos, r, 2);
        if (r.getBlock() instanceof StairBlock || r.getBlock() instanceof WallBlock || r.getBlock() instanceof FenceBlock || r.getBlock() instanceof IronBarsBlock
                || r.getBlock() instanceof DoorBlock || r.getBlock() instanceof TrapDoorBlock || r.is(Blocks.CHAIN) || r.getBlock() instanceof LadderBlock || r.is(Blocks.POINTED_DRIPSTONE)) {
            level.getChunk(pos).markPosForPostprocessing(pos.immutable());
        }
    }

    protected void fill(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int dy0, int dy1, int wz, BlockState s) {
        for (int dy = dy0; dy <= dy1; dy++) set(level, pos, wx, dy, wz, s);
    }

    protected void chest(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int dy, int wz, net.minecraft.core.Direction facing, ResourceKey<LootTable> loot) {
        set(level, pos, wx, dy, wz, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        pos.set(wx, cy + dy, wz);
        RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), loot);
    }

    protected void spawner(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int dy, int wz, EntityType<?> type) {
        set(level, pos, wx, dy, wz, Blocks.SPAWNER.defaultBlockState());
        pos.set(wx, cy + dy, wz);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) spawner.setEntityId(type, level.getRandom());
    }

    /** Clears a column of everything above the ground up to a height (trees, hillocks), leaving small plants alone. */
    protected void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int wz, int from, int to) {
        for (int dy = from; dy <= to; dy++) {
            pos.set(wx, cy + dy, wz);
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) {
                if (dy > from + 8) break;
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** Fills from below a block down to the ground (a foundation), for the entrance buildings. */
    protected void foundation(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int wz, int dy, BlockState s, int maxDown) {
        for (int y = dy - 1; y > dy - 1 - maxDown; y--) {
            pos.set(wx, cy + y, wz);
            if (level.getBlockState(pos).isSolid()) break;
            level.setBlock(pos, s, 2);
        }
    }
}
