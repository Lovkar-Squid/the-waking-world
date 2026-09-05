package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One shrine, drawn cell by cell from a shape function so any chunk of it can be generated alone.
 * Six shapes, one per kind: standing stones (stone), a barrow (earth), a sand tomb (sandstone), a
 * frost cairn (ice), a sunken domed ruin (prismarine) and an overgrown sanctum (moss). Each holds
 * an altar of its kind at its heart (the rite is performed there) and a glowing core block as the
 * hint of what sleeps beneath.
 */
public class ShrinePiece extends StructurePiece {
    private static final int R = 9;      // horizontal half-size of every shrine's box
    private static final int DOWN = 4;   // foundation depth
    private static final int UP = 14;    // tallest shrine + clearing

    private final String kind;
    private final int cx, cy, cz;
    private final long seed;

    public ShrinePiece(String kind, BlockPos origin, long seed) {
        super(WakingStructures.SHRINE_PIECE.get(), 0, new BoundingBox(origin.getX() - R, origin.getY() - DOWN, origin.getZ() - R,
                origin.getX() + R, origin.getY() + UP, origin.getZ() + R));
        this.kind = kind;
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.seed = seed;
    }

    public ShrinePiece(CompoundTag tag) {
        super(WakingStructures.SHRINE_PIECE.get(), tag);
        this.kind = tag.getString("Kind");
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Kind", kind);
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
    }

    private int hash(int x, int y, int z) {
        long h = x * 341873128712L + y * 97531234567L + z * 132897987541L + seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h & 0xFF);
    }

    /** A weighted pick of the kind's building blocks (weights out of 100). */
    private BlockState wall(int dx, int dy, int dz) {
        int h = hash(dx, dy, dz) % 100;
        return switch (kind) {
            case "earth" -> h < 40 ? Blocks.DIRT.defaultBlockState() : h < 65 ? Blocks.COARSE_DIRT.defaultBlockState() : h < 85 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
            case "sandstone" -> h < 45 ? Blocks.SANDSTONE.defaultBlockState() : h < 70 ? Blocks.CUT_SANDSTONE.defaultBlockState() : h < 80 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case "ice" -> h < 55 ? Blocks.PACKED_ICE.defaultBlockState() : h < 75 ? Blocks.BLUE_ICE.defaultBlockState() : h < 90 ? Blocks.ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
            case "prismarine" -> h < 45 ? Blocks.PRISMARINE.defaultBlockState() : h < 80 ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState();
            case "moss" -> h < 40 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : h < 60 ? Blocks.STONE_BRICKS.defaultBlockState() : h < 75 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            default -> h < 40 ? Blocks.STONE_BRICKS.defaultBlockState() : h < 60 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : h < 80 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : h < 90 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        };
    }

    private BlockState floor(int dx, int dz) {
        int h = hash(dx, -100, dz) % 100;
        return switch (kind) {
            case "earth" -> h < 70 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            case "sandstone" -> h < 60 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case "ice" -> h < 70 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            case "prismarine" -> h < 60 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.PRISMARINE.defaultBlockState();
            case "moss" -> h < 50 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : h < 80 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            default -> h < 50 ? Blocks.COBBLESTONE.defaultBlockState() : h < 80 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        };
    }

    private BlockState core() {
        return switch (kind) {
            case "ice", "prismarine" -> Blocks.SEA_LANTERN.defaultBlockState();
            case "sandstone" -> Blocks.GLOWSTONE.defaultBlockState();
            case "moss" -> Blocks.SHROOMLIGHT.defaultBlockState();
            default -> Blocks.MAGMA_BLOCK.defaultBlockState();
        };
    }

    /** Headless preview hook: the shape of a kind at a seed, no registries beyond Blocks needed. */
    public static BlockState shapeOf(String kind, long seed, int dx, int dy, int dz) {
        return new ShrinePiece(kind, seed).shape(dx, dy, dz);
    }

    /** A shape-only instance (no piece type, no box) for previews. */
    private ShrinePiece(String kind, long seed) {
        super(null, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        this.kind = kind;
        this.cx = this.cy = this.cz = 0;
        this.seed = seed;
    }

    /** Where the kind's altar stands (dx, dy, dz). */
    private int[] altarAt() {
        return switch (kind) {
            case "earth", "ice" -> new int[]{0, 1, -2};
            case "sandstone" -> new int[]{0, 1, -1};
            case "moss" -> new int[]{0, 2, -4};
            default -> new int[]{0, 2, 0};
        };
    }

    /** The post the dais' lights stand on, in the kind's stuff. */
    private BlockState post() {
        return switch (kind) {
            case "earth" -> Blocks.PACKED_MUD.defaultBlockState();
            case "sandstone" -> Blocks.CHISELED_SANDSTONE.defaultBlockState();
            case "ice" -> Blocks.BLUE_ICE.defaultBlockState();
            case "prismarine" -> Blocks.DARK_PRISMARINE.defaultBlockState();
            case "moss" -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            default -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        };
    }

    /**
     * The shape: what stands at (dx, dy, dz) relative to the origin (dy 0 = the ground's top block), or null to leave the world alone.
     * Every kind's shape gets the altar's dais laid over it: four lit posts of the kind's stuff two blocks out from the altar, wherever
     * the shrine has room for them.
     */
    private BlockState shape(int dx, int dy, int dz) {
        BlockState base = kindShape(dx, dy, dz);
        int[] alt = altarAt();
        int ox = dx - alt[0], oz = dz - alt[2];
        if (Math.abs(ox) == 2 && Math.abs(oz) == 2 && (dy == 1 || dy == 2)) {
            boolean roomHere = base != null && (base.isAir() || base.is(Blocks.WATER) || base.is(Blocks.MOSS_CARPET) || base.is(Blocks.AZALEA) || base.is(Blocks.FLOWERING_AZALEA) || base.is(Blocks.HANGING_ROOTS));
            BlockState below = dy == 1 ? kindShape(dx, 0, dz) : kindShape(dx, 1, dz);
            boolean standing = dy == 1 ? (below != null && !below.isAir() && !below.is(Blocks.WATER)) : true;
            if (roomHere && standing) {
                if (dy == 1) return post();
                BlockState under = kindShape(dx, 1, dz);
                boolean postBelow = under != null && (under.isAir() || under.is(Blocks.WATER) || under.is(Blocks.MOSS_CARPET) || under.is(Blocks.AZALEA) || under.is(Blocks.FLOWERING_AZALEA));
                BlockState floor = kindShape(dx, 0, dz);
                if (postBelow && floor != null && !floor.isAir() && !floor.is(Blocks.WATER)) return core();
            }
        }
        return base;
    }

    private BlockState kindShape(int dx, int dy, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        int h = hash(dx, dy, dz) % 100;
        BlockState air = "prismarine".equals(kind) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        switch (kind) {
            case "stone" -> {
                if (dy == 0 && r <= 8.5) return floor(dx, dz);
                if (dy < 0) return null;
                // eight monoliths on a ring of seven, three to five tall, chiselled on top
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    int mx = (int) Math.round(Math.cos(a) * 7), mz = (int) Math.round(Math.sin(a) * 7);
                    int height = 3 + (hash(mx, 7, mz) % 3);
                    if (dx == mx && dz == mz && dy >= 1 && dy <= height) return dy == height ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : wall(dx, dy, dz);
                }
                // the altar: a 3x3 step, a pedestal, the chest
                if (dy == 1 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return dx == 0 && dz == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
                if (dy == 2 && dx == 0 && dz == 0) return chest();
                // the sleeper's hint: a heap in the corner with an ember in it
                if (dx >= 4 && dx <= 6 && dz <= -4 && dz >= -6 && dy >= 1 && dy <= 2 + (dx == 5 && dz == -5 ? 1 : 0)) {
                    return dx == 5 && dz == -5 && dy == 2 ? core() : wall(dx, dy, dz);
                }
                if (dy >= 1 && dy <= 10 && r <= 8.5) return air; // cleared
                return null;
            }
            case "earth" -> {
                // a dome of earth with a grass skin; hollow inside; a tunnel out to +z
                double dome = 7.2 * Math.sqrt(Math.max(0, 1 - (dy / 5.5) * (dy / 5.5)));
                boolean tunnel = dz >= 3 && dz <= 8 && dx >= 0 && dx <= 1 && dy >= 1 && dy <= 2;
                boolean chamber = dy >= 1 && dy <= 3 && (dx * dx) / 16.0 + ((dy - 2) * (dy - 2)) / 3.0 + (dz * dz) / 16.0 <= 1.0;
                if (tunnel) return air;
                if (chamber) {
                    if (dy == 1 && dx == 0 && dz == -2) return chest();
                    if (dy == 3 && h < 25) return Blocks.HANGING_ROOTS.defaultBlockState();
                    return air;
                }
                if (dy == 0 && r <= 7.5) return dx == 0 && dz == 0 ? core() : (r <= 4.5 ? Blocks.COARSE_DIRT.defaultBlockState() : floor(dx, dz));
                if (dy >= 1 && dy <= 5 && r <= dome) {
                    boolean skin = r > dome - 1.1 || dy == 5;
                    return skin ? (h < 85 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState()) : wall(dx, dy, dz);
                }
                if (dy >= 1 && dy <= 10 && r <= 8.5) return air;
                return null;
            }
            case "sandstone" -> {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                boolean chamber = m <= 2 && dy >= 1 && dy <= 3;
                boolean tunnel = dx == 0 && dz >= 2 && dz <= 6 && dy >= 1 && dy <= 2;
                if (chamber) {
                    if (dy == 1 && dx == 0 && dz == -1) return chest();
                    return air;
                }
                if (tunnel) return air;
                if (dy == 4 && dx == 0 && dz == 0) return core();
                // the stepped tomb: eleven wide at the base, narrowing a block a step
                if (dy >= 1 && dy <= 6 && m <= 6 - dy) return h < 8 ? Blocks.SAND.defaultBlockState() : wall(dx, dy, dz);
                if (dy == 0 && m <= 7) return floor(dx, dz);
                // three fingers of a buried hand reaching out of the sand
                if (dz == -7 && dy >= 1 && ((dx == 6 && dy <= 3) || (dx == 7 && dy <= 2) || (dx == 8 && dy <= 1))) return Blocks.CUT_SANDSTONE.defaultBlockState();
                if (dy >= 1 && dy <= 10 && m <= 8) return air;
                return null;
            }
            case "ice" -> {
                double cone = 5.5 - dy * 0.42;
                boolean chamber = dy >= 1 && dy <= 6 && r <= cone - 1.6;
                boolean door = dx == 0 && dz >= 3 && dz <= 6 && dy >= 1 && dy <= 2;
                if (door) return air;
                if (chamber) {
                    if (dy == 1 && dx == 0 && dz == -2) return chest();
                    return air;
                }
                if (dy == 0 && r <= 6.5) return dx == 0 && dz == 0 ? core() : floor(dx, dz);
                if (dy >= 1 && dy <= 12 && r <= cone) return wall(dx, dy, dz);
                if (dy >= 1 && dy <= 13 && r <= 8.5) return air;
                return null;
            }
            case "prismarine" -> {
                double dome = 5.8 * Math.sqrt(Math.max(0, 1 - (dy / 6.0) * (dy / 6.0)));
                boolean pillar = Math.abs(dx) == 4 && Math.abs(dz) == 4 && dy >= 1 && dy <= 4;
                if (pillar) return dy == 4 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState();
                if (dy == 1 && dx == 0 && dz == 0) return Blocks.DARK_PRISMARINE.defaultBlockState();
                if (dy == 2 && dx == 0 && dz == 0) return chest();
                if (dy == 0 && r <= 6.5) return floor(dx, dz);
                if (dy >= 1 && dy <= 6 && r <= dome && r > dome - 1.2) return h < 28 ? air : wall(dx, dy, dz); // a ruined dome, holed
                if (dy >= 1 && dy <= 6 && r < dome) return air;
                return null;
            }
            default -> { // moss: an overgrown hall along z
                int ax = Math.abs(dx), az = Math.abs(dz);
                boolean wallHere = (ax == 4 && az <= 6) || (az == 6 && ax <= 4);
                boolean pillar = ax == 3 && az == 4;
                boolean doorway = az == 6 && ax <= 1 && dy >= 1 && dy <= 3 && dz > 0;
                if (doorway) return air;
                if (pillar && dy >= 1 && dy <= 4) return dy == 4 ? core() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                if (wallHere && dy >= 1 && dy <= 4) return h < 25 + dy * 8 ? air : wall(dx, dy, dz); // ruined: more missing the higher up
                if (dy == 5 && ax <= 4 && az <= 6) return h < 55 ? air : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                if (dy == 1 && dx == 0 && dz == -4) return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                if (dy == 2 && dx == 0 && dz == -4) return chest();
                if (dy == 0 && ax <= 5 && az <= 7) return floor(dx, dz);
                if (dy == 1 && ax <= 3 && az <= 5) return h < 18 ? Blocks.MOSS_CARPET.defaultBlockState() : h < 22 ? Blocks.AZALEA.defaultBlockState() : h < 24 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : air;
                if (dy >= 1 && dy <= 10 && ax <= 8 && az <= 8) return air;
                return null;
            }
        }
    }

    private static final BlockState CHEST_MARKER = Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);

    private BlockState chest() {
        return CHEST_MARKER;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox mine = this.boundingBox;
        int x0 = Math.max(box.minX(), mine.minX()), x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ()), z1 = Math.min(box.maxZ(), mine.maxZ());
        boolean sunken = "prismarine".equals(kind);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - cx, dz = z - cz;
                boolean footprint = false;
                for (int dy = 0; dy <= UP; dy++) {
                    BlockState s = shape(dx, dy, dz);
                    if (s == null) continue;
                    footprint = true;
                    pos.set(x, cy + dy, z);
                    if (s == CHEST_MARKER) {
                        level.setBlock(pos, me.lovkar.wakingworld.ritual.WakingRitual.ALTAR.get().defaultBlockState(), 2);
                        if (level.getBlockEntity(pos) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity altar) altar.setKind(kind);
                        continue;
                    }
                    if (s.isAir()) {
                        // the clearing around it: trees, plants, snow and any ground poking up go
                        if (dy > 0 && !level.getBlockState(pos).isAir()) level.setBlock(pos, s, 2);
                        continue;
                    }
                    if (sunken && s.is(Blocks.WATER)) {
                        BlockState there = level.getBlockState(pos);
                        if (!there.isAir() && !there.is(Blocks.WATER)) level.setBlock(pos, s, 2);
                        continue;
                    }
                    if (!s.isAir()) level.setBlock(pos, s, 2);
                }
                if (footprint) {
                    // a foundation under the footprint, down to the rock, so nothing floats on a slope
                    for (int dy = -1; dy >= -DOWN; dy--) {
                        pos.set(x, cy + dy, z);
                        BlockState there = level.getBlockState(pos);
                        if (there.isAir() || (!sunken && !there.getFluidState().isEmpty()) || there.canBeReplaced()) level.setBlock(pos, wall(dx, dy, dz), 2);
                    }
                }
            }
        }
    }
}
