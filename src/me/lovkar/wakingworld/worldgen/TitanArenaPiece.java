package me.lovkar.wakingworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The arena, drawn column by column so any chunk of it can be generated on its own: a round
 * floor of end stone bricks (radius {@link #RADIUS}) with purpur rings and eight spokes, an
 * obsidian rim, a low parapet with merlons, eight obsidian pillars banded with crying obsidian and
 * crowned with end rods, a stepped altar in the middle with a crying-obsidian heart, and six
 * lesser altars on a ring round it, one for each land. The floor is six blocks thick and tapers
 * into the island below; everything above it is cleared. The Titan's Gate (the way home that opens
 * when the Titan falls) stands south of the altar; the altar raises it - see AltarBlockEntity.
 */
public class TitanArenaPiece extends StructurePiece {
    public static final int RADIUS = 52;
    public static final int CLEAR_HEIGHT = 36;
    public static final int PILLAR_HEIGHT = 26;
    private static final int PILLAR_RING = RADIUS - 10;
    /** The six lesser altars stand on this ring round the great one, at 30, 90, ... 330 degrees (Rites.LANDS order). */
    public static final int LESSER_RING = 16;
    /** The lesser altar of land {@code i} stands this far (dx, dz) from the great altar, and two blocks lower. */
    public static int[] lesserOffset(int i) {
        double a = Math.toRadians(30 + 60 * i);
        return new int[]{(int) Math.round(Math.cos(a) * LESSER_RING), (int) Math.round(Math.sin(a) * LESSER_RING)};
    }
    public static final int LESSER_DY = -2;
    /** Where the Titan's Gate rises when the Titan falls: this far south of the altar, at the rim (AltarBlockEntity.GATE_DZ). */
    public static final int GATE_DZ = me.lovkar.wakingworld.ritual.AltarBlockEntity.GATE_DZ;

    private final int cx, cy, cz;
    private final long seed;

    public TitanArenaPiece(BlockPos origin, long seed) {
        super(WakingStructures.TITAN_ARENA_PIECE.get(), 0, box(origin));
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.seed = seed;
    }

    public TitanArenaPiece(CompoundTag tag) {
        super(WakingStructures.TITAN_ARENA_PIECE.get(), tag);
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.seed = tag.getLong("Seed");
    }

    private static BoundingBox box(BlockPos o) {
        return new BoundingBox(o.getX() - RADIUS - 2, o.getY() - 18, o.getZ() - RADIUS - 2,
                o.getX() + RADIUS + 2, o.getY() + CLEAR_HEIGHT, o.getZ() + RADIUS + 2);
    }

    /** The middle of the altar, on top - where the Titan rises. */
    public BlockPos center() {
        return new BlockPos(cx, cy + 4, cz);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
    }

    private int hash(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L + seed * 0x9E3779B97F4A7C15L;
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
        BlockState bricks = Blocks.END_STONE_BRICKS.defaultBlockState();
        BlockState endStone = Blocks.END_STONE.defaultBlockState();
        BlockState purpur = Blocks.PURPUR_BLOCK.defaultBlockState();
        BlockState purpurPillar = Blocks.PURPUR_PILLAR.defaultBlockState();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState rod = Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - cx, dz = z - cz;
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > RADIUS + 0.5) continue;
                double angle = Math.toDegrees(Math.atan2(dz, dx));
                if (angle < 0) angle += 360;
                int h = hash(x, z);

                // 1. the sky above the floor is cleared (chorus, island stone poking through)
                for (int y = cy + 1; y <= cy + CLEAR_HEIGHT; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, 2);
                }

                // 2. the floor: top layer patterned, five layers under it, a taper into the island
                BlockState top;
                if (r > RADIUS - 2) top = obsidian;
                else if (dz == GATE_DZ && Math.abs(dx) >= 5 && Math.abs(dx) <= 6) top = crying; // the sockets the gate's jambs will rise from
                else if (Math.abs(dz - GATE_DZ) <= 1 && Math.abs(dx) <= 7) top = purpur;       // its threshold
                else if (((int) Math.round(r)) % 9 == 0 && r > 4) top = purpur;
                else if (r > 8 && r < RADIUS - 8 && spoke(angle, r)) top = purpur;
                else top = h < 22 ? endStone : bricks;
                set(level, pos, x, cy, z, top);
                set(level, pos, x, cy - 1, z, bricks);
                for (int y = cy - 2; y >= cy - 5; y--) set(level, pos, x, y, z, endStone);
                for (int d = 6; d <= 17; d++) {
                    double taper = RADIUS - (d - 5) * 4.2;
                    if (r > taper) break;
                    pos.set(x, cy - d, z);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, endStone, 2);
                }

                // 3. the parapet along the rim, merlons every fifteen degrees
                if (r >= RADIUS - 5 && r <= RADIUS - 3) {
                    set(level, pos, x, cy + 1, z, bricks);
                    set(level, pos, x, cy + 2, z, h < 40 ? endStone : bricks);
                    if (angle % 15 < 3.2) set(level, pos, x, cy + 3, z, purpur);
                }

                // 4. eight pillars on the ring between the parapet and the spokes
                int pillar = pillarAt(dx, dz);
                if (pillar >= 0) {
                    int[] c = pillarCenter(pillar);
                    boolean core = dx == c[0] && dz == c[1];
                    for (int i = 1; i <= PILLAR_HEIGHT; i++) {
                        set(level, pos, x, cy + i, z, i % 7 == 0 ? crying : obsidian);
                    }
                    for (int i = PILLAR_HEIGHT + 1; i <= PILLAR_HEIGHT + 3; i++) set(level, pos, x, cy + i, z, purpurPillar);
                    if (core) {
                        set(level, pos, x, cy + PILLAR_HEIGHT + 4, z, crying);
                        set(level, pos, x, cy + PILLAR_HEIGHT + 5, z, rod);
                    } else if (Math.abs(dx - c[0]) == 1 && Math.abs(dz - c[1]) == 1) {
                        set(level, pos, x, cy + PILLAR_HEIGHT + 4, z, rod);
                    }
                }

                // 5. the altar: three steps up to a crying-obsidian heart with a rod of light on it
                if (r <= 7.5) set(level, pos, x, cy + 1, z, bricks);
                if (r <= 5.5) set(level, pos, x, cy + 2, z, r <= 5.5 && r > 4.5 ? purpur : bricks);
                if (r <= 3.5) set(level, pos, x, cy + 3, z, purpur);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) set(level, pos, x, cy + 3, z, crying);
                if (dx == 0 && dz == 0) {
                    // the arena's altar sits on the heart of the dais
                    pos.set(x, cy + 4, z);
                    level.setBlock(pos, me.lovkar.wakingworld.ritual.WakingRitual.ALTAR.get().defaultBlockState(), 2);
                    if (level.getBlockEntity(pos) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity altar) altar.setKind("titan");
                }
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) set(level, pos, x, cy + 4, z, rod);
                // and four low lights around it
                if ((Math.abs(dx) == 10 && dz == 0) || (Math.abs(dz) == 10 && dx == 0)) {
                    set(level, pos, x, cy + 1, z, crying);
                    set(level, pos, x, cy + 2, z, rod);
                }

                // 6. the six lesser altars on their ring, one for each land: a low plinth of purpur with a
                // bricks border, the altar on top; each wants the runes of its land before the great one hears the horn
                for (int i = 0; i < me.lovkar.wakingworld.ritual.Rites.LANDS.length; i++) {
                    int[] o = lesserOffset(i);
                    int ax = dx - o[0], az = dz - o[1];
                    if (Math.abs(ax) > 1 || Math.abs(az) > 1) continue;
                    set(level, pos, x, cy, z, bricks);
                    set(level, pos, x, cy + 1, z, ax == 0 && az == 0 ? purpurPillar : (ax == 0 || az == 0) ? purpur : bricks);
                    if (ax == 0 && az == 0) {
                        pos.set(x, cy + 2, z);
                        level.setBlock(pos, me.lovkar.wakingworld.ritual.WakingRitual.ALTAR.get().defaultBlockState(), 2);
                        if (level.getBlockEntity(pos) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity altar) altar.setKind("titan_" + me.lovkar.wakingworld.ritual.Rites.LANDS[i]);
                    } else if (Math.abs(ax) == 1 && Math.abs(az) == 1) {
                        set(level, pos, x, cy + 2, z, rod); // a light at each corner of the plinth
                    }
                }
            }
        }
    }

    private static boolean spoke(double angle, double r) {
        // eight spokes, one block wide: the column's angle is within half a block of k*45 degrees
        double half = Math.toDegrees(0.55 / Math.max(1.0, r));
        double m = angle % 45;
        return m < half || 45 - m < half;
    }

    private static int[] pillarCenter(int i) {
        double a = Math.toRadians(22.5 + 45 * i);
        return new int[]{(int) Math.round(Math.cos(a) * PILLAR_RING), (int) Math.round(Math.sin(a) * PILLAR_RING)};
    }

    /** Which pillar's 3x3 footprint the column (dx, dz) belongs to, or -1. */
    private static int pillarAt(int dx, int dz) {
        for (int i = 0; i < 8; i++) {
            int[] c = pillarCenter(i);
            if (Math.abs(dx - c[0]) <= 1 && Math.abs(dz - c[1]) <= 1) return i;
        }
        return -1;
    }

    private void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, 2);
    }
}
