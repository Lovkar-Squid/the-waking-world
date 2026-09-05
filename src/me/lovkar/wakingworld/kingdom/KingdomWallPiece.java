package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.worldgen.WakingStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The kingdom's ring: the ground made level and green, a moat with bridges at the gates, a curtain
 * wall of stone brick on a battered plinth with buttresses, arrow slits, a corbelled parapet and
 * merlons, eight towers on the ring - round ones under slate cones and octagonal ones with open
 * battlements and a banner pole, turn and turn about - two barbican gatehouses with twin towers
 * under slate pyramids, a portcullis, murder holes and a chamber over the arch, the roads (two to
 * the keep, one ring, the spokes), lamp posts - and the guards on it all: archers on every tower,
 * knights at the gates, spearmen on the ring road. Drawn column by column so any chunk can be
 * generated on its own; the structure's terrain adaptation fills the ground under the whole ring.
 */
public class KingdomWallPiece extends StructurePiece {
    public static final int RADIUS = 56;
    public static final int WALL_H = 9, TOWER_H = 16, TOWER_R = 4;
    public static final int RING_ROAD = 40;
    /** The moat, outside the wall: the water between these radii, the banks a block either side. */
    static final double MOAT_IN = 59.5, MOAT_OUT = 62.5;
    static final int REACH = 70;

    private final int cx, cy, cz;
    private final long seed;
    /** The people about the town: {dx, dz, type (0 townsfolk, 1 guard), profession or guard kind}. */
    private final java.util.List<int[]> people;

    public KingdomWallPiece(BlockPos origin, long seed, java.util.List<int[]> people) {
        super(WakingStructures.KINGDOM_WALL_PIECE.get(), 0, new BoundingBox(origin.getX() - REACH, origin.getY(), origin.getZ() - REACH, origin.getX() + REACH, origin.getY() + 30, origin.getZ() + REACH));
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.seed = seed;
        this.people = new java.util.ArrayList<>(people);
    }

    public KingdomWallPiece(CompoundTag tag) {
        super(WakingStructures.KINGDOM_WALL_PIECE.get(), tag);
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.seed = tag.getLong("Seed");
        this.people = new java.util.ArrayList<>();
        int[] flat = tag.getIntArray("People");
        for (int i = 0; i + 3 < flat.length; i += 4) people.add(new int[]{flat[i], flat[i + 1], flat[i + 2], flat[i + 3]});
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
        int[] flat = new int[people.size() * 4];
        for (int i = 0; i < people.size(); i++) System.arraycopy(people.get(i), 0, flat, i * 4, 4);
        tag.putIntArray("People", flat);
    }

    // ------------------------------------------------------------------ materials

    private int hash(int x, int y, int z) {
        long h = x * 341873128712L + y * 97531234567L + z * 132897987541L + seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h & 0xFF);
    }

    static final BlockState BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    static final BlockState ANDESITE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    static final BlockState SLATE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState WATER = Blocks.WATER.defaultBlockState();
    static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    static final BlockState WALL = Blocks.STONE_BRICK_WALL.defaultBlockState();
    static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
    static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();

    /** The wall's face: stone brick, the odd cracked one, cobble and moss at the foot. */
    BlockState brick(int x, int y, int z, int dy) {
        int h = hash(x, y, z) % 100;
        if (dy <= 1) return h < 70 ? COBBLE : MOSSY_COBBLE;
        if (dy == 2) return h < 60 ? BRICK : h < 80 ? COBBLE : MOSSY;
        if (h < 80) return BRICK;
        if (h < 90) return CRACKED;
        if (h < 95 && dy <= 4) return MOSSY;
        return ANDESITE;
    }

    static BlockState stairs(Direction facing, boolean top) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState slateStairs(Direction facing) {
        return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    static BlockState slab(boolean top) {
        return Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    BlockState road(int x, int z) {
        int h = hash(x, 7, z) % 100;
        return h < 50 ? COBBLE : h < 72 ? BRICK : h < 86 ? ANDESITE : h < 94 ? Blocks.GRAVEL.defaultBlockState() : MOSSY_COBBLE;
    }

    // ------------------------------------------------------------------ geometry

    static double angleOf(int dx, int dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return a < 0 ? a + 360 : a;
    }

    /** The direction from a cell towards a centre (for stairs that lean in) - or away from it when {@code out}. */
    static Direction toward(int lx, int lz, boolean out) {
        Direction d = Math.abs(lx) >= Math.abs(lz) ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
        return out ? d.getOpposite() : d;
    }

    /** The eight towers' centres on the ring, 22.5 degrees off the gates. */
    static int[] towerCenter(int i) {
        double a = Math.toRadians(22.5 + 45 * i);
        return new int[]{(int) Math.round(Math.cos(a) * RADIUS), (int) Math.round(Math.sin(a) * RADIUS)};
    }

    /** Even towers are round drums; odd ones are broader octagons. The octagon metric: the larger of the square and the diamond. */
    static double towerDist(int i, int lx, int lz) {
        if ((i & 1) == 0) return Math.sqrt(lx * lx + lz * lz);
        return Math.max(Math.max(Math.abs(lx), Math.abs(lz)), (Math.abs(lx) + Math.abs(lz)) * 0.7071 + 0.3);
    }

    static double towerRadius(int i) {
        return (i & 1) == 0 ? TOWER_R + 0.5 : TOWER_R + 1.0;
    }

    static int towerAt(int dx, int dz) {
        for (int i = 0; i < 8; i++) {
            int[] c = towerCenter(i);
            if (towerDist(i, dx - c[0], dz - c[1]) <= towerRadius(i) + 2.3) return i;
        }
        return -1;
    }

    /** Which gate a column belongs to (0 south +z, 1 north -z), or -1. The gate sits where the wall crosses the z axis. */
    static int gateAt(int dx, int dz) {
        if (Math.abs(dx) <= 10 && dz >= RADIUS - 5 && dz <= RADIUS + 5) return 0;
        if (Math.abs(dx) <= 10 && dz <= -(RADIUS - 5) && dz >= -(RADIUS + 5)) return 1;
        return -1;
    }

    static boolean onRoad(int dx, int dz, double r) {
        if (Math.abs(dx) <= 1 && Math.abs(dz) > 21 && Math.abs(dz) <= REACH) return true; // the two roads to the keep, out past the gates
        if (r >= RING_ROAD - 1.5 && r <= RING_ROAD + 1.5) return true; // the ring road
        if (Math.abs(dz) <= 1 && Math.abs(dx) > 21 && Math.abs(dx) < RING_ROAD) return true; // four spokes from the ring to the bailey
        return false;
    }

    static boolean onBridge(int dx, int dz, double r) {
        // from just outside the gate's face (lz 3 is the arch itself, never the bridge) to the far bank
        return Math.abs(dx) <= 2 && Math.abs(dz) >= RADIUS + 4 && Math.abs(dz) <= MOAT_OUT + 1;
    }

    // ------------------------------------------------------------------ placing

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox mine = this.boundingBox;
        int x0 = Math.max(box.minX(), mine.minX()), x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ()), z1 = Math.min(box.maxZ(), mine.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - cx, dz = z - cz;
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > REACH + 0.5) continue;
                column(level, pos, random, x, z, dx, dz, r);
            }
        }
    }

    private void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, double r) {
        // 1. the ground: inside the moat everything above the plateau goes (trees, hillocks); beyond it only the
        //    trees whose trunks stood inside (their canopies would hang in the air otherwise)
        boolean inner = r <= MOAT_OUT + 1.5;
        for (int y = cy + 1; y <= cy + 40; y++) {
            pos.set(x, y, z);
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) {
                if (y > cy + 12) break;
                continue;
            }
            boolean plant = y == cy + 1 && (s.is(BlockTags.FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN));
            if (inner ? !plant : (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS))) level.setBlock(pos, AIR, 2);
        }
        boolean road = onRoad(dx, dz, r);
        boolean bridge = onBridge(dx, dz, r);
        if (inner || road) {
            pos.set(x, cy, z);
            if (road && !bridge) level.setBlock(pos, road(x, z), 2);
            else if (inner && !bridge) {
                boolean moat = r > MOAT_IN && r <= MOAT_OUT;
                boolean bank = !moat && r > MOAT_IN - 1.0 && r <= MOAT_OUT + 1.0;
                if (moat) {
                    level.setBlock(pos, WATER, 2);
                    level.setBlock(pos.set(x, cy - 1, z), WATER, 2);
                    level.setBlock(pos.set(x, cy - 2, z), hash(x, 3, z) % 3 == 0 ? MOSSY_COBBLE : COBBLE, 2);
                    clearPlant(level, pos.set(x, cy + 1, z));
                } else if (bank) {
                    level.setBlock(pos, hash(x, 5, z) % 4 == 0 ? MOSSY_COBBLE : COBBLE, 2);
                    clearPlant(level, pos.set(x, cy + 1, z));
                } else {
                    level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
            if (road) clearPlant(level, pos.set(x, cy + 1, z));
            for (int y = cy - 1; y >= cy - 4; y--) {
                pos.set(x, y, z);
                BlockState s = level.getBlockState(pos);
                boolean keepWater = y == cy - 1 && r > MOAT_IN && r <= MOAT_OUT;
                if (!keepWater && (s.isAir() || s.is(Blocks.WATER) || s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS))) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        if (bridge) {
            bridgeColumn(level, pos, x, z, dx, dz);
            return;
        }
        if (r > RADIUS + 6.5) return;

        // 2. towers - where a tower's foot meets the curtain wall, the wall runs on to the shell under the tower's roof
        int tower = towerAt(dx, dz);
        if (tower >= 0) {
            int[] c = towerCenter(tower);
            boolean wallBand = r >= RADIUS - 2.5 && r <= RADIUS + 2.5 && towerDist(tower, dx - c[0], dz - c[1]) > towerRadius(tower);
            if (wallBand) wallColumn(level, pos, random, x, z, dx, dz, r);
            towerColumn(level, pos, random, x, z, dx, dz, tower, wallBand, r);
            return;
        }
        // 3. gates
        int gate = gateAt(dx, dz);
        if (gate >= 0) {
            gateColumn(level, pos, random, x, z, dx, dz, gate);
            return;
        }
        // 4. the curtain wall: three thick, r 54.5..57.5, with its plinth and parapet a ring either side
        if (r >= RADIUS - 2.5 && r <= RADIUS + 2.5) {
            wallColumn(level, pos, random, x, z, dx, dz, r);
            return;
        }
        // 5. lamp posts along the roads and the ring
        if (r <= RADIUS - 3) lamps(level, pos, x, z, dx, dz, r);
        // 6. the people: townsfolk at their doors and about the roads, spearmen walking the ring road
        for (int[] p : people) {
            if (p[0] != dx || p[1] != dz) continue;
            if (p[2] == 1) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, p[3], 12);
            else KingdomSpawns.trader(level, cx, cy, cz, x, cy + 1, z, p[3]);
        }
        if (r >= RING_ROAD - 0.5 && r <= RING_ROAD + 0.5) {
            for (int k = 0; k < 4; k++) {
                double ga = 45 + 90 * k;
                int gx = (int) Math.round(Math.cos(Math.toRadians(ga)) * RING_ROAD), gz = (int) Math.round(Math.sin(Math.toRadians(ga)) * RING_ROAD);
                if (dx == gx && dz == gz) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.SPEARMAN, 10);
            }
        }
    }

    private static void clearPlant(WorldGenLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.isAir() && (s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS))) level.setBlock(pos, AIR, 2);
    }

    /** The bridges over the moat at the gates: planks on stone piers, a fence rail either side. */
    private void bridgeColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz) {
        int ax = Math.abs(dx);
        double az = Math.abs(dz);
        boolean overWater = az > MOAT_IN && az <= MOAT_OUT;
        set(level, pos, x, cy, z, ax == 2 ? Blocks.SPRUCE_LOG.defaultBlockState() : PLANKS);
        if (overWater) {
            set(level, pos, x, cy - 1, z, ax == 2 ? BRICK : WATER);
            set(level, pos, x, cy - 2, z, COBBLE);
        }
        if (ax == 2) {
            set(level, pos, x, cy + 1, z, FENCE);
            if (az == Math.round(MOAT_IN) || az == Math.round(MOAT_OUT)) {
                set(level, pos, x, cy + 2, z, FENCE);
                set(level, pos, x, cy + 3, z, lantern(false));
            }
        }
        if (ax == 1 && az == Math.round(MOAT_OUT) + 1) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.KNIGHT, 5);
    }

    private void wallColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, double r) {
        double angle = angleOf(dx, dz);
        int ia = (int) Math.round(angle * 4); // quarter degrees, for the rhythm along the ring
        Direction in = toward(dx, dz, false);
        // the outer ring beyond the wall's face: the battered plinth below, buttresses, the corbels and the parapet above
        if (r > RADIUS + 1.5) {
            boolean buttress = ia % 60 == 0; // every fifteen degrees
            set(level, pos, x, cy + 1, z, brick(x, 1, z, 1));
            set(level, pos, x, cy + 2, z, buttress ? brick(x, 2, z, 2) : stairs(in, false));
            if (buttress) {
                for (int dy = 3; dy <= WALL_H - 3; dy++) set(level, pos, x, cy + dy, z, dy == 5 ? CHISELED : ANDESITE);
                set(level, pos, x, cy + WALL_H - 2, z, stairs(in, true));
            }
            // the corbel course carries the parapet out over the face
            set(level, pos, x, cy + WALL_H - 1, z, stairs(in, true));
            boolean merlon = (ia / 6) % 2 == 0;
            set(level, pos, x, cy + WALL_H, z, brick(x, WALL_H, z, 5));
            set(level, pos, x, cy + WALL_H + 1, z, merlon ? brick(x, WALL_H + 1, z, 5) : slab(false));
            if (merlon && ia % 36 == 0) set(level, pos, x, cy + WALL_H + 2, z, lantern(false));
            return;
        }
        // the inner ring inside the wall's face: a step of the plinth, the wall walk's railing
        if (r < RADIUS - 1.5) {
            set(level, pos, x, cy + 1, z, stairs(toward(dx, dz, true), false));
            set(level, pos, x, cy + WALL_H - 1, z, slab(true));
            if (ia % 16 == 0) set(level, pos, x, cy + WALL_H, z, WALL);
            return;
        }
        boolean outer = r > RADIUS + 0.5, innerFace = r < RADIUS - 0.5;
        // the body of the wall up to the walkway
        for (int dy = 1; dy <= WALL_H - 2; dy++) {
            BlockState s = brick(x, dy, z, dy);
            if (dy == 5 && outer) s = CHISELED;                                     // the string course
            if (outer && (dy == 6 || dy == 7) && ia % 24 == 0) s = AIR;             // arrow slits
            if (innerFace && dy >= 2 && dy <= 4 && ia % 40 == 0) s = AIR;           // recessed arches on the inside
            if (innerFace && dy == 5 && ia % 40 == 0) s = stairs(toward(dx, dz, true), true);
            set(level, pos, x, cy + dy, z, s);
        }
        // the wall walk: the two rings within the face are the floor; the outer face rises a course to meet the parapet
        set(level, pos, x, cy + WALL_H - 1, z, outer ? brick(x, WALL_H - 1, z, 5) : slab(true));
        if (outer) set(level, pos, x, cy + WALL_H, z, brick(x, WALL_H, z, 5));
    }

    private void towerColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int tower, boolean wallHere, double r) {
        int[] c = towerCenter(tower);
        int lx = dx - c[0], lz = dz - c[1];
        double lr = towerDist(tower, lx, lz);
        double radius = towerRadius(tower);
        boolean round = (tower & 1) == 0;
        int height = round ? TOWER_H : TOWER_H + 3;
        Direction in = toward(lx, lz, false);
        double towerAngle = Math.toRadians(22.5 + 45 * tower);
        boolean inMoat = r > MOAT_IN - 1.0;
        // a tower that stands in the moat rises out of it on a foundation
        if (inMoat && lr <= radius + 1.0) for (int dy = -2; dy <= 0; dy++) set(level, pos, x, cy + dy, z, brick(x, dy, z, 1));
        // the rings outside the shell: the battered foot, the corbels and the parapet, the cone
        if (lr > radius) {
            if (wallHere) {
                if (round) cone(level, pos, x, z, lx, lz, lr, height + 5, radius + 2.2);
                return;
            }
            if (lr <= radius + 1.0) {
                set(level, pos, x, cy + 1, z, brick(x, 1, z, 1));
                set(level, pos, x, cy + 2, z, stairs(in, false));
                set(level, pos, x, cy + height + 1, z, stairs(in, true));
                boolean merlon = ((int) Math.round(angleOf(lx, lz) / 20)) % 2 == 0;
                set(level, pos, x, cy + height + 2, z, brick(x, height + 2, z, 5));
                set(level, pos, x, cy + height + 3, z, merlon ? brick(x, height + 3, z, 5) : slab(false));
                if (!round && merlon && ((int) Math.round(angleOf(lx, lz) / 20)) % 4 == 0) set(level, pos, x, cy + height + 4, z, lantern(false));
            } else if (!inMoat) {
                set(level, pos, x, cy + 1, z, stairs(in, false));
            }
            // banners on the outward face: hung on the cell just outside the shell that has the shell at its back
            if (lr <= radius + 1.0 && bannerCell(tower, lx, lz)) {
                Direction facing = toward(lx, lz, true);
                banner(level, pos, x, cy + 10, z, facing);
                banner(level, pos, x, cy + 14, z, facing);
            }
            if (round) cone(level, pos, x, z, lx, lz, lr, height + 5, radius + 2.2);
            return;
        }
        boolean shell = lr > radius - 1.0;
        boolean core = lx == 0 && lz == 0;
        boolean ladder = lx == 0 && lz == -(int) Math.floor(radius - 0.5);
        // the door from the walkway: the cells on the tower's shell that face along the wall, at walkway height
        double tx = -Math.sin(towerAngle), tz = Math.cos(towerAngle); // tangent along the ring
        boolean doorCell = shell && Math.abs(lx * tz - lz * tx) <= 0.6;
        boolean onAxis = (lx == 0 && Math.abs(lz) > radius - 1.5) || (lz == 0 && Math.abs(lx) > radius - 1.5);
        for (int dy = 1; dy <= height; dy++) {
            BlockState s;
            if (shell) {
                s = brick(x, dy, z, dy);
                if (dy == 5 || dy == 12) s = CHISELED;
                if (doorCell && (dy == WALL_H || dy == WALL_H + 1)) s = AIR;
                if (doorCell && dy == WALL_H + 2) s = stairs(in, true);
                if (!doorCell && onAxis && (dy == 3 || dy == 14)) s = PANE;                 // arched windows on the axes
                if (!doorCell && onAxis && (dy == 4 || dy == 15)) s = stairs(in, true);
                if (!doorCell && !onAxis && dy == 7 && hash(lx, dy, lz) % 3 == 0) s = AIR;   // slits between
            } else {
                // inside: floors at the walkway height and near the top, a ladder up the north wall
                if (dy == WALL_H - 1 || dy == height) s = ladder ? AIR : PLANKS;
                else s = AIR;
                if (ladder) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
            }
            set(level, pos, x, cy + dy, z, s);
        }
        // the top: the platform, the shell rising as its parapet
        set(level, pos, x, cy + height + 1, z, shell ? brick(x, height + 1, z, 5) : ladder ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH) : PLANKS);
        if (shell) set(level, pos, x, cy + height + 2, z, brick(x, height + 2, z, 5));
        if (round) {
            // four posts hold the cone over the open gallery
            if (shell && ((Math.abs(lx) == (int) Math.floor(radius) && lz == 0) || (Math.abs(lz) == (int) Math.floor(radius) && lx == 0))) {
                for (int dy = height + 3; dy <= height + 5; dy++) set(level, pos, x, cy + dy, z, FENCE);
            }
            cone(level, pos, x, z, lx, lz, lr, height + 5, radius + 2.2);
            if (core) set(level, pos, x, cy + height + 2, z, lantern(false));
        } else if (core) {
            // the octagonal towers: an open platform with a lantern pole
            for (int dy = height + 2; dy <= height + 6; dy++) set(level, pos, x, cy + dy, z, dy == height + 2 ? WALL : FENCE);
            set(level, pos, x, cy + height + 7, z, lantern(false));
        }
        if (!round && shell && ((Math.abs(lx) == (int) Math.floor(radius) && lz == 0) || (Math.abs(lz) == (int) Math.floor(radius) && lx == 0))) {
            // standing banners on the parapet at the four axis points
            standingBanner(level, pos, x, cy + height + 3, z, toward(lx, lz, true));
        }
        // the archers on the platform (beside the lantern pole, never in it)
        if (lx == 1 && lz == 0) KingdomSpawns.guard(level, cx, cy, cz, x, cy + height + 2, z, GuardEntity.ARCHER, 3);
        if (!round && lx == 2 && lz == 2) KingdomSpawns.guard(level, cx, cy, cz, x, cy + height + 2, z, GuardEntity.ARCHER, 3);
    }

    /** Whether (lx, lz) is the tower's banner cell: outside the shell, the shell behind it, and nearest the outward line of all such cells. */
    static boolean bannerCell(int tower, int lx, int lz) {
        double radius = towerRadius(tower);
        double want = Math.toDegrees(Math.toRadians(22.5 + 45 * tower));
        int bestX = 0, bestZ = 0;
        double best = Double.MAX_VALUE;
        int R = (int) Math.ceil(radius + 1.0);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = towerDist(tower, x, z);
                if (d <= radius || d > radius + 1.0) continue;
                Direction out = toward(x, z, true);
                if (towerDist(tower, x - out.getStepX(), z - out.getStepZ()) > radius) continue; // nothing solid behind
                double diff = Math.abs(((angleOf(x, z) - want) % 360 + 540) % 360 - 180);
                if (diff < best) {
                    best = diff;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return best < 90 && lx == bestX && lz == bestZ;
    }

    /** The slate cone over a round tower: one ring in per level, a post and a lantern on the point. */
    private void cone(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int lx, int lz, double lr, int base, double r0) {
        int steps = (int) Math.ceil(r0);
        for (int k = 0; k <= steps; k++) {
            double rr = r0 - k;
            int y = base + k;
            if (lr <= rr && lr > rr - 1.0) {
                set(level, pos, x, cy + y, z, k == steps ? SLATE : slateStairs(toward(lx, lz, false)));
            } else if (lr <= rr - 1.0 && k < steps) {
                set(level, pos, x, cy + y, z, k == 0 ? SLATE : AIR);
            }
        }
        if (lx == 0 && lz == 0) {
            set(level, pos, x, cy + base + steps, z, SLATE);
            set(level, pos, x, cy + base + steps + 1, z, FENCE);
            set(level, pos, x, cy + base + steps + 2, z, FENCE);
            set(level, pos, x, cy + base + steps + 3, z, lantern(false));
        }
    }

    /**
     * The barbican: the wall's line runs through two square towers (6 x 7) under slate pyramids; between
     * them the passage - an outer arch, the portcullis, a vaulted way with murder holes in its ceiling,
     * an inner arch - and over the passage the gate chamber with windows both ways, crowned with merlons.
     */
    private void gateColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int gate) {
        int sign = gate == 0 ? 1 : -1; // +z south gate, -z north gate
        int lz = dz * sign - RADIUS;  // -5..5 across the wall, 0 = the wall's middle, positive = outward
        int ax = Math.abs(dx);
        Direction outDir = sign > 0 ? Direction.SOUTH : Direction.NORTH, inDir = outDir.getOpposite();
        boolean passage = ax <= 2 && Math.abs(lz) <= 3;
        boolean towerBlock = ax >= 3 && ax <= 8 && Math.abs(lz) <= 3;
        boolean towerShell = towerBlock && (ax == 3 || ax == 8 || Math.abs(lz) == 3);
        if (passage) {
            set(level, pos, x, cy, z, road(x, z));
            for (int dy = 1; dy <= 6; dy++) set(level, pos, x, cy + dy, z, AIR);
            Direction away = dx > 0 ? Direction.EAST : Direction.WEST; // stairs' full half against the side wall
            // the vaulted way: the ceiling springs from upside-down stairs along both side walls
            if (ax == 2) set(level, pos, x, cy + 6, z, stairs(away, true));
            // the two arches at the ends: the sides step in over the opening, a chiseled keystone at the crown
            if (Math.abs(lz) == 3) {
                if (ax == 2) {
                    set(level, pos, x, cy + 5, z, stairs(away, true));
                    set(level, pos, x, cy + 6, z, brick(x, 6, z, 5));
                }
                if (ax == 1) set(level, pos, x, cy + 6, z, stairs(away, true));
            }
            // the portcullis, raised into the arch at the outer face
            if (lz == 2 && ax <= 1) for (int dy = 5; dy <= 6; dy++) set(level, pos, x, cy + dy, z, BARS);
            // the chamber's floor (with murder holes) and the gate chamber itself, up to the merlons
            for (int dy = 7; dy <= 12; dy++) {
                BlockState s = brick(x, dy, z, 5);
                if (dy == 7 && Math.abs(lz) == 3 && ax == 0) s = CHISELED;
                if (dy == 7 && Math.abs(lz) == 1 && ax <= 1) s = slab(true);
                if (dy >= 8 && dy <= 11 && Math.abs(lz) <= 2 && ax <= 1) s = AIR;
                if (dy == 12 && Math.abs(lz) <= 2 && ax <= 1) s = PLANKS;
                if ((dy == 9 || dy == 10) && Math.abs(lz) == 3 && ax == 0) s = PANE;
                if (dy == 11 && Math.abs(lz) == 3 && ax == 0) s = stairs(lz > 0 ? inDir : outDir, true);
                if ((dy == 9 || dy == 10) && Math.abs(lz) == 3 && ax == 1) s = CHISELED;
                set(level, pos, x, cy + dy, z, s);
            }
            if (ax == 0 && lz == 0) set(level, pos, x, cy + 4, z, lantern(true));
            if (ax == 1 && lz == -2) {
                set(level, pos, x, cy + 8, z, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dx > 0 ? Direction.WEST : Direction.EAST));
                pos.set(x, cy + 8, z);
                net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), KeepPiece.STORES);
            }
            if (ax == 0 && lz == -2) set(level, pos, x, cy + 8, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, outDir));
            // the corbels and the merlons over the passage
            set(level, pos, x, cy + 13, z, Math.abs(lz) == 3 ? stairs(lz > 0 ? inDir : outDir, true) : brick(x, 13, z, 5));
            set(level, pos, x, cy + 14, z, brick(x, 14, z, 5));
            // a flat roof between the towers, crenellated front and back: merlons, and low walls in the embrasures
            if (Math.abs(lz) == 3) {
                set(level, pos, x, cy + 15, z, ax % 2 == 0 ? brick(x, 15, z, 5) : slab(false));
                if (ax == 0) set(level, pos, x, cy + 16, z, lantern(false));
            }
            if (ax == 1 && lz == 0) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 15, z, GuardEntity.ARCHER, 2);
            return;
        }
        if (towerBlock) {
            int height = 17;
            if (Math.sqrt(dx * dx + dz * dz) > MOAT_IN - 1.0) for (int dy = -2; dy <= 0; dy++) set(level, pos, x, cy + dy, z, brick(x, dy, z, 1));
            for (int dy = 1; dy <= height; dy++) {
                BlockState s = towerShell ? brick(x, dy, z, dy) : AIR;
                if (towerShell && (dy == 5 || dy == 11)) s = CHISELED;
                boolean face = Math.abs(lz) == 3 && (ax == 5 || ax == 6);
                if (towerShell && face && (dy == 3 || dy == 9 || dy == 14)) s = PANE;
                if (towerShell && face && (dy == 4 || dy == 10 || dy == 15)) s = stairs(lz > 0 ? inDir : outDir, true);
                if (towerShell && ax == 8 && lz == 0 && (dy == WALL_H || dy == WALL_H + 1)) s = AIR;   // onto the wall walk
                if (towerShell && ax == 3 && lz == 0 && (dy == 8 || dy == 9)) s = AIR;                 // into the gate chamber
                if (!towerShell && (dy == WALL_H - 1 || dy == 13)) s = PLANKS;
                if (!towerShell && ax == 5 && lz == 0) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
                set(level, pos, x, cy + dy, z, s);
            }
            // the pyramid: rings of slate stepping in
            int k = Math.min(Math.min(ax - 3, 8 - ax), 3 - Math.abs(lz)); // 0 at the edge .. 2 at the ridge
            set(level, pos, x, cy + height + 1, z, brick(x, height + 1, z, 5));
            if (k == 0) set(level, pos, x, cy + height + 2, z, slateStairs(ax == 3 ? Direction.EAST : ax == 8 ? Direction.WEST : lz > 0 ? inDir : outDir));
            else {
                set(level, pos, x, cy + height + 2, z, SLATE);
                if (k == 1) set(level, pos, x, cy + height + 3, z, slateStairs(ax == 4 ? Direction.EAST : ax == 7 ? Direction.WEST : lz > 0 ? inDir : outDir));
                else {
                    set(level, pos, x, cy + height + 3, z, SLATE);
                    set(level, pos, x, cy + height + 4, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                    if (ax == 5 && lz == 0) {
                        set(level, pos, x, cy + height + 5, z, FENCE);
                        set(level, pos, x, cy + height + 6, z, lantern(false));
                    }
                }
            }
            if (towerShell && Math.abs(lz) == 3 && lz > 0 && ax == 4) banner(level, pos, x, cy + 7, z + sign, outDir);
            return;
        }
        // the plinth and the corbels wrap round the towers; the approach and its lanterns and knights
        double rr = Math.sqrt(dx * dx + dz * dz);
        if (ax == 9 && rr >= RADIUS - 2.5 && rr <= RADIUS + 2.5) {
            wallColumn(level, pos, random, x, z, dx, dz, rr); // the curtain wall meets the gate tower's side
            return;
        }
        if (ax <= 9 && Math.abs(lz) <= 4) {
            if (ax <= 2) {
                set(level, pos, x, cy, z, road(x, z));
                if (ax == 2 && Math.abs(lz) == 4) {
                    set(level, pos, x, cy + 1, z, WALL);   // a lamp post too tall for a knight to hop onto
                    set(level, pos, x, cy + 2, z, FENCE);
                    set(level, pos, x, cy + 3, z, lantern(false));
                }
                if (ax == 1 && lz == -4) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.KNIGHT, 5); // flanking the inner arch, on the road
                return;
            }
            Direction in = ax == 9 ? (dx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? inDir : outDir);
            if (rr > MOAT_IN - 1.0) for (int dy = -2; dy <= 0; dy++) set(level, pos, x, cy + dy, z, brick(x, dy, z, 1));
            else set(level, pos, x, cy + 1, z, stairs(in, false));
            // the drawbridge chains: from brackets on the towers' outer faces down to posts beside the bridge
            if (ax == 3 && lz == 4) {
                set(level, pos, x, cy + 1, z, WALL);
                for (int dy = 2; dy <= 7; dy++) set(level, pos, x, cy + dy, z, Blocks.CHAIN.defaultBlockState());
                set(level, pos, x, cy + 8, z, stairs(inDir, true));
            }
            set(level, pos, x, cy + 18, z, stairs(in, true));
            set(level, pos, x, cy + 19, z, ((ax + Math.abs(lz)) % 2 == 0) ? brick(x, 19, z, 5) : slab(false));
            return;
        }
        // ax 10: the wall runs on into the ring
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r >= RADIUS - 2.5 && r <= RADIUS + 2.5) wallColumn(level, pos, random, x, z, dx, dz, r);
    }

    private static final double[] LAMP_ANGLES = {45, 135, 225, 315};

    private void lamps(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz, double r) {
        // along the two main roads, beside them, every eight blocks (not at the ring road's crossing);
        // either side of the ring road on the diagonals, and outside it where the spokes end
        boolean mainRoadLamp = Math.abs(dx) == 3 && Math.abs(dz) > 23 && Math.abs(dz) % 8 == 0 && Math.abs(dz) != RING_ROAD && Math.abs(dz) < RADIUS - 6;
        boolean ringLamp = false;
        if (r >= RING_ROAD - 3.5 && r <= RING_ROAD + 3.5) {
            for (double la : LAMP_ANGLES) {
                for (int lr : new int[]{RING_ROAD - 3, RING_ROAD + 3}) {
                    if (dx == (int) Math.round(Math.cos(Math.toRadians(la)) * lr) && dz == (int) Math.round(Math.sin(Math.toRadians(la)) * lr)) ringLamp = true;
                }
            }
            if ((dx == RING_ROAD + 3 || dx == -(RING_ROAD + 3)) && dz == 0) ringLamp = true;
        }
        if (!mainRoadLamp && !ringLamp) return;
        set(level, pos, x, cy + 1, z, WALL);
        set(level, pos, x, cy + 2, z, FENCE);
        set(level, pos, x, cy + 3, z, FENCE);
        set(level, pos, x, cy + 4, z, lantern(false));
    }

    // ------------------------------------------------------------------ helpers

    private static void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, 2);
        if (state.getBlock() instanceof StairBlock || state.getBlock() instanceof WallBlock || state.is(Blocks.IRON_BARS) || state.is(Blocks.SPRUCE_FENCE) || state.is(Blocks.GLASS_PANE) || state.getBlock() instanceof LadderBlock) {
            level.getChunk(pos).markPosForPostprocessing(pos.immutable());
        }
    }

    /** The kingdom's banner on a pole, standing on a solid block, facing {@code facing}. */
    static void standingBanner(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, Direction facing) {
        pos.set(x, y, z);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) return;
        int rotation = switch (facing) {
            case NORTH -> 8;
            case EAST -> 12;
            case WEST -> 4;
            default -> 0;
        };
        level.setBlock(pos, Blocks.CYAN_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.BannerBlock.ROTATION, rotation), 2);
        pattern(level, pos);
    }

    private static void pattern(WorldGenLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BannerBlockEntity be) {
            try {
                var reg = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
                var lozenge = reg.getHolder(net.minecraft.world.level.block.entity.BannerPatterns.RHOMBUS_MIDDLE);
                var border = reg.getHolder(net.minecraft.world.level.block.entity.BannerPatterns.BORDER);
                net.minecraft.world.level.block.entity.BannerPatternLayers.Builder b = new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder();
                lozenge.ifPresent(h -> b.add(h, net.minecraft.world.item.DyeColor.YELLOW));
                border.ifPresent(h -> b.add(h, net.minecraft.world.item.DyeColor.YELLOW));
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CYAN_BANNER);
                stack.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, b.build());
                be.fromItem(stack, net.minecraft.world.item.DyeColor.CYAN);
            } catch (Exception e) {
                WakingWorld.LOGGER.warn("banner: {}", e.toString());
            }
        }
    }

    /** The kingdom's banner: cyan with a gold lozenge, hung on a wall - only where the wall is really behind it. */
    static void banner(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, Direction facing) {
        pos.set(x, y, z);
        if (!level.getBlockState(pos).isAir()) return;
        BlockPos back = pos.immutable().relative(facing.getOpposite());
        // the wall it hangs on may lie in a chunk not yet generated: only hang it when the block behind is already solid
        // (the piece visits the banner's own column; the shell behind is drawn by the same piece, columns in x-z order,
        // so behind-first is not guaranteed - hence the piece places banners only where the shell is a full block)
        BlockState behind = level.getBlockState(back);
        if (!behind.isSolid() && behind.isAir()) {
            // draw the support first: the callers always ask for a banner against their own masonry
            level.setBlock(back, BRICK, 2);
        }
        level.setBlock(pos, Blocks.CYAN_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, facing), 2);
        pattern(level, pos.immutable());
    }
}
