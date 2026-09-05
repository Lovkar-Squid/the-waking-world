package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.worldgen.WakingStructures;
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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The castle at the kingdom's heart. A square bailey wall on a battered plinth, buttressed,
 * with a corbelled parapet, round corner towers under slate cones and a gatehouse with a
 * portcullis on the south; inside it the great hall - a long buttressed nave under a steep slate
 * gable, arched windows, two square turrets flanking the door, pillars, a hearth, long tables and
 * the throne on its dais at the far end - and behind the hall the donjon: a round tower on a
 * battered base, three floors (a guard room, the king's chamber, the library) under a machicolated
 * parapet and a cone, with the treasury dug beneath it behind an iron door. Stables, a forge,
 * barracks, a well and a fountain fill the courtyard. Drawn column by column so any chunk can be
 * generated on its own.
 */
public class KeepPiece extends StructurePiece {
    public static final ResourceKey<LootTable> TREASURY = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/treasury"));
    public static final ResourceKey<LootTable> STORES = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/kingdom_house"));
    static final int BAILEY = 22, REACH = 27;
    /** The great hall: x -7..7, z -6..12 (walls included); the donjon's centre and radii; the treasury under it. */
    static final int HALL_HW = 7, HALL_Z0 = -6, HALL_Z1 = 12, HALL_WALL_TOP = 8, RIDGE = 17;
    static final int DON_Z = -12;
    static final double DON_R = 6.5, DON_IN = 5.5;
    static final int F0 = 2, F1 = 9, F2 = 16, PLATFORM = 23;
    static final int TR_FLOOR = -6, TR_TOP = -2;
    static final double TR_R = 3.5;

    private final int cx, cy, cz;
    private final long seed;

    public KeepPiece(BlockPos origin, long seed) {
        super(WakingStructures.KEEP_PIECE.get(), 0, new BoundingBox(origin.getX() - REACH, origin.getY(), origin.getZ() - REACH, origin.getX() + REACH, origin.getY() + 36, origin.getZ() + REACH));
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.seed = seed;
    }

    public KeepPiece(CompoundTag tag) {
        super(WakingStructures.KEEP_PIECE.get(), tag);
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
    }

    // ------------------------------------------------------------------ materials

    private int hash(int x, int y, int z) {
        long h = x * 341873128712L + y * 97531234567L + z * 132897987541L + seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h & 0xFF);
    }

    /** Stone brick with the odd cracked block; the foot of a wall is cobble, a little mossy. */
    private BlockState brick(int x, int y, int z) {
        int h = hash(x, y, z) % 100;
        if (y <= 1) return h < 75 ? Blocks.COBBLESTONE.defaultBlockState() : h < 90 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : KingdomWallPiece.BRICK;
        return h < 84 ? KingdomWallPiece.BRICK : h < 93 ? KingdomWallPiece.CRACKED : h < 97 ? KingdomWallPiece.ANDESITE : Blocks.STONE.defaultBlockState();
    }

    private BlockState floorTile(int x, int z) {
        return ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
    }

    static BlockState stairs(Direction facing, boolean top) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState slate(Direction facing) {
        return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    /** The direction from a cell towards a centre (for stairs that lean in) - or away from it when {@code out}. */
    static Direction toward(int lx, int lz, boolean out) {
        Direction d = Math.abs(lx) >= Math.abs(lz) ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
        return out ? d.getOpposite() : d;
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    static final BlockState CARPET = Blocks.RED_CARPET.defaultBlockState();
    static final BlockState ANDESITE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    static final BlockState SLATE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
    static final BlockState WALL = Blocks.STONE_BRICK_WALL.defaultBlockState();
    static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    static final BlockState DEEP = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    static final BlockState DEEP_BRICK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();

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
                if (Math.abs(dx) > REACH || Math.abs(dz) > REACH) continue;
                column(level, pos, random, x, z, dx, dz);
            }
        }
    }

    private void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int dy, int z, BlockState s) {
        pos.set(x, cy + dy, z);
        level.setBlock(pos, s, 2);
        if (s.getBlock() instanceof StairBlock || s.getBlock() instanceof WallBlock || s.getBlock() instanceof FenceBlock || s.getBlock() instanceof IronBarsBlock
                || s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapDoorBlock || s.is(Blocks.CHAIN)) {
            level.getChunk(pos).markPosForPostprocessing(pos.immutable());
        }
    }

    private void fill(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int dy0, int dy1, int z, BlockState s) {
        for (int dy = dy0; dy <= dy1; dy++) set(level, pos, x, dy, z, s);
    }

    private static double dist(int dx, int dz, double ox, double oz) {
        return Math.sqrt((dx - ox) * (dx - ox) + (dz - oz) * (dz - oz));
    }

    private void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        int m = Math.max(ax, az);
        int cornerX = dx > 0 ? 21 : -21, cornerZ = dz > 0 ? 21 : -21;
        double cornerD = dist(dx, dz, cornerX, cornerZ);
        if (m > BAILEY + 2 && cornerD > 5.0) return;

        // the courtyard: everything above the ground goes, the ground is lawn with stone paths
        if (m <= BAILEY) {
            for (int dy = 1; dy <= 36; dy++) {
                pos.set(x, cy + dy, z);
                if (!level.getBlockState(pos).isAir()) level.setBlock(pos, AIR, 2);
                else if (dy > 10) break;
            }
            boolean path = (ax <= 1 && dz > HALL_Z1 && dz <= BAILEY)               // gate to the hall door
                    || (dz == 15 && ax <= 15)                                          // across the yard past the door
                    || (ax == 15 && dz >= -8 && dz <= 15)                              // to the stables and the forge
                    || (dz == -8 && ax >= 9 && ax <= 15);                              // on to the barracks and the smithy
            set(level, pos, x, 0, z, path ? floorTile(x, z) : Blocks.GRASS_BLOCK.defaultBlockState());
        }

        // ---- the round corner towers of the bailey, with their battered feet; the wall runs on to their shells ----
        if (cornerD <= 5.0) {
            boolean onWall = cornerD > 3.5 && (m == BAILEY || m == BAILEY - 1);
            if (onWall) {
                baileyWall(level, pos, random, x, z, dx, dz, ax, az, m);
                cone(level, pos, x, z, dx - cornerX, dz - cornerZ, cornerD, 15 + 4, 3.5 + 2.2);
            } else {
                roundTower(level, pos, x, z, dx - cornerX, dz - cornerZ, cornerD, 3.5, 15, ax == 20 && az == 21); // the archer beside the lantern, not in it
            }
            return;
        }
        // ---- outside the bailey wall: the plinth, the buttresses, the knights at the gate ----
        if (m == BAILEY + 1 || m == BAILEY + 2) {
            outerWorks(level, pos, x, z, dx, dz, ax, az, m);
            return;
        }
        // ---- the bailey wall and its gatehouse ----
        if (m == BAILEY || m == BAILEY - 1) {
            baileyWall(level, pos, random, x, z, dx, dz, ax, az, m);
            return;
        }
        // ---- the donjon (round, north of the hall) and the treasury under it ----
        double dr = dist(dx, dz, 0, DON_Z);
        if (dr <= DON_R + 1.0) {
            donjon(level, pos, random, x, z, dx, dz, dr);
            if (dr <= DON_R) return;
        }
        // ---- the great hall's front turrets ----
        if (ax >= 8 && ax <= 10 && dz >= HALL_Z1 - 2 && dz <= HALL_Z1) {
            squareTurret(level, pos, x, z, dx, dz, ax == 8 || ax == 10 || dz == HALL_Z1 - 2 || dz == HALL_Z1, 14, ax == 9 && dz == HALL_Z1 - 1);
            return;
        }
        // ---- the great hall ----
        if (ax <= HALL_HW + 1 && dz >= HALL_Z0 - 1 && dz <= HALL_Z1 + 1) {
            hall(level, pos, random, x, z, dx, dz, ax, az);
            return;
        }
        // ---- the courtyard's buildings ----
        courtyard(level, pos, random, x, z, dx, dz, ax, az);
    }

    /** A round tower: a battered foot, shell with string courses and slit windows, floors and a ladder, a corbelled parapet, a slate cone. */
    private void roundTower(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int lx, int lz, double lr, double radius, int height, boolean archer) {
        if (lr > radius + 1.0) {
            // the battered foot: a ring of stairs leaning against the tower
            if (lr <= radius + 2.0) {
                set(level, pos, x, 1, z, stairs(toward(lx, lz, false), false));
            }
            return;
        }
        if (lr > radius) {
            // the ring just outside the shell: the foot's second step, then the corbels and the parapet high up
            set(level, pos, x, 1, z, brick(x, 1, z));
            set(level, pos, x, 2, z, stairs(toward(lx, lz, false), false));
            set(level, pos, x, height + 1, z, stairs(toward(lx, lz, false), true));
            set(level, pos, x, height + 2, z, ((int) Math.round(KingdomWallPiece.angleOf(lx, lz) / 24)) % 2 == 0 ? brick(x, height + 2, z) : KingdomWallPiece.slab(false));
            cone(level, pos, x, z, lx, lz, lr, height + 4, radius + 2.2);
            return;
        }
        boolean shell = lr > radius - 1.0;
        boolean core = lx == 0 && lz == 0;
        boolean ladder = lx == 0 && lz == -(int) Math.floor(radius - 0.5);
        for (int dy = 1; dy <= height; dy++) {
            BlockState s;
            if (shell) {
                s = brick(x, dy, z);
                if (dy == 6 || dy == 12) s = KingdomWallPiece.CHISELED;
                boolean onAxis = (lx == 0 && Math.abs(lz) > radius - 1.5) || (lz == 0 && Math.abs(lx) > radius - 1.5);
                if (onAxis && (dy == 4 || dy == 10)) s = PANE;
                if (onAxis && (dy == 5 || dy == 11)) s = stairs(toward(lx, lz, false), true);
            } else {
                s = (dy == 7 || dy == height) ? (ladder ? AIR : PLANKS) : AIR;
                if (ladder) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
            }
            set(level, pos, x, dy, z, s);
        }
        // the platform, its parapet and the cone on posts above the open gallery
        set(level, pos, x, height + 1, z, shell ? brick(x, height + 1, z) : PLANKS);
        if (shell) set(level, pos, x, height + 2, z, brick(x, height + 2, z));
        if (shell && ((Math.abs(lx) == (int) Math.floor(radius) && lz == 0) || (Math.abs(lz) == (int) Math.floor(radius) && lx == 0))) fill(level, pos, x, height + 3, height + 5, z, FENCE);
        cone(level, pos, x, z, lx, lz, lr, height + 4, radius + 2.2);
        if (core) {
            set(level, pos, x, height + 2, z, lantern(false));
        }
        if (archer) KingdomSpawns.guard(level, cx, cy, cz, x, cy + height + 2, z, GuardEntity.ARCHER, 3);
    }

    /** The slate cone over a round tower: one ring in per level, a fence post and a lantern on the point. */
    private void cone(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int lx, int lz, double lr, int base, double r0) {
        int steps = (int) Math.ceil(r0);
        for (int k = 0; k <= steps; k++) {
            double rr = r0 - k;
            int y = base + k;
            if (lr <= rr && lr > rr - 1.0) {
                set(level, pos, x, y, z, k == steps ? SLATE : slate(toward(lx, lz, false)));
            } else if (lr <= rr - 1.0 && k < steps) {
                set(level, pos, x, y, z, k == 0 ? SLATE : AIR);
            }
        }
        if (lx == 0 && lz == 0) {
            set(level, pos, x, base + steps, z, SLATE);
            set(level, pos, x, base + steps + 1, z, FENCE);
            set(level, pos, x, base + steps + 2, z, FENCE);
            set(level, pos, x, base + steps + 3, z, lantern(false));
        }
    }

    /** A square turret shell of a given height with corbels and a battlemented top; a ladder up the middle. */
    private void squareTurret(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz, boolean shell, int height, boolean archer) {
        for (int dy = 1; dy <= height; dy++) {
            BlockState s = shell ? brick(x, dy, z) : AIR;
            if (shell && (dy == 6 || dy == 11)) s = KingdomWallPiece.CHISELED;
            if (shell && (dy == 4 || dy == 9) && ((Math.abs(dx) == 10 && dz == HALL_Z1 - 1) || (dz == HALL_Z1 && Math.abs(dx) == 9))) s = PANE;
            if (shell && (dy == 5 || dy == 10) && ((Math.abs(dx) == 10 && dz == HALL_Z1 - 1) || (dz == HALL_Z1 && Math.abs(dx) == 9))) s = stairs(Math.abs(dx) == 10 ? (dx > 0 ? Direction.WEST : Direction.EAST) : Direction.NORTH, true);
            if (!shell) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
            set(level, pos, x, dy, z, s);
        }
        set(level, pos, x, height + 1, z, shell ? brick(x, height + 1, z) : PLANKS);
        if (shell) set(level, pos, x, height + 2, z, ((dx + dz) & 1) == 0 ? brick(x, height + 2, z) : KingdomWallPiece.slab(false));
        if (shell && Math.abs(dx) == 10 && dz == HALL_Z1 - 1) KingdomWallPiece.banner(level, pos, x + (dx > 0 ? 1 : -1), cy + 8, z, dx > 0 ? Direction.EAST : Direction.WEST);
        if (archer) KingdomSpawns.guard(level, cx, cy, cz, x, cy + height + 2, z, GuardEntity.ARCHER, 2);
    }

    /** Just outside the bailey wall: a battered plinth of stairs, buttresses every six blocks, the corbels carrying the parapet, the gate's knights. */
    private void outerWorks(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz, int ax, int az, int m) {
        boolean gateApproach = dz > 0 && ax <= 2;
        if (gateApproach) {
            set(level, pos, x, 0, z, floorTile(x, z));
            return;
        }
        Direction in = toward(dx, dz, false);
        if (m == BAILEY + 2) {
            set(level, pos, x, 1, z, stairs(in, false));
            if (dz > 0 && ax == 3 && m == BAILEY + 2) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 2, z, GuardEntity.KNIGHT, 4);
            return;
        }
        // m == BAILEY + 1: the plinth's upper step, buttresses, and the corbel course under the parapet
        boolean buttress = (ax > az ? dz : dx) % 6 == 0 && !(dz > 0 && ax <= 6);
        set(level, pos, x, 1, z, brick(x, 1, z));
        set(level, pos, x, 2, z, buttress ? brick(x, 2, z) : stairs(in, false));
        if (buttress) {
            fill(level, pos, x, 3, 5, z, ANDESITE);
            set(level, pos, x, 6, z, stairs(in, true)); // meets the corbel course
        }
        if (!(dz > 0 && ax <= 6)) {
            set(level, pos, x, 6, z, stairs(in, true));
            boolean merlon = ((ax > az ? dz : dx) & 1) == 0;
            set(level, pos, x, 7, z, merlon ? brick(x, 7, z) : KingdomWallPiece.slab(false));
            if (merlon && (dx + dz) % 9 == 0) set(level, pos, x, 8, z, lantern(false));
        }
    }

    private void baileyWall(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int ax, int az, int m) {
        boolean outer = m == BAILEY;
        // the gate on the south side, between two square turrets that carry the wall through them
        if (dz > 0 && ax <= 6 && az >= BAILEY - 1) {
            if (ax <= 2) {
                fill(level, pos, x, 1, 5, z, AIR);
                if (ax == 2) set(level, pos, x, 5, z, stairs(dx > 0 ? Direction.EAST : Direction.WEST, true));
                if (outer && ax <= 1) fill(level, pos, x, 4, 5, z, BARS); // the portcullis, raised
                set(level, pos, x, 0, z, floorTile(x, z));
                for (int dy = 6; dy <= 10; dy++) set(level, pos, x, dy, z, dy == 6 && ax == 0 ? KingdomWallPiece.CHISELED : brick(x, dy, z));
                if (dy8Window(ax, outer)) set(level, pos, x, 8, z, PANE);
                set(level, pos, x, 11, z, (ax & 1) == 0 ? brick(x, 11, z) : KingdomWallPiece.slab(false));
                if (ax == 0 && !outer) set(level, pos, x, 3, z, lantern(true));
                return;
            }
            boolean shell = ax == 3 || ax == 6 || m == BAILEY || az == BAILEY - 1;
            for (int dy = 1; dy <= 12; dy++) {
                BlockState s = shell ? brick(x, dy, z) : (dy == 6 ? PLANKS : AIR);
                if (shell && dy == 7) s = KingdomWallPiece.CHISELED;
                if (shell && (dy == 4 || dy == 9) && ((ax == 6 && az == BAILEY) || (m == BAILEY && ax == 4))) s = PANE;
                if (!shell && ax == 4 && az == BAILEY - 1 && dy > 6) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
                set(level, pos, x, dy, z, s);
            }
            set(level, pos, x, 13, z, shell ? brick(x, 13, z) : PLANKS);
            // a slate pyramid on each gate turret
            int k = Math.min(Math.min(ax - 3, 6 - ax), Math.min(az - (BAILEY - 1), BAILEY - az)); // 0 at the edge, 1 in the middle ring
            if (k == 0) set(level, pos, x, 14, z, slate(ax == 3 ? Direction.EAST : ax == 6 ? Direction.WEST : az == BAILEY ? Direction.NORTH : Direction.SOUTH));
            else {
                set(level, pos, x, 14, z, SLATE);
                set(level, pos, x, 15, z, SLATE);
                if (ax == 4 && az == BAILEY - 1) {
                    set(level, pos, x, 16, z, FENCE);
                    set(level, pos, x, 17, z, lantern(false));
                }
            }
            if (outer && ax == 6) KingdomWallPiece.banner(level, pos, x, cy + 8, z + 1, Direction.SOUTH);
            return;
        }
        for (int dy = 1; dy <= 5; dy++) set(level, pos, x, dy, z, dy == 3 && outer ? KingdomWallPiece.CHISELED : brick(x, dy, z));
        // arrow slits on the outer face
        if (outer && (dy4Slit(dx, dz))) set(level, pos, x, 4, z, AIR);
        if (outer) {
            set(level, pos, x, 6, z, brick(x, 6, z)); // the wall walk, level with the corbelled parapet outside it
        } else {
            set(level, pos, x, 6, z, KingdomWallPiece.slab(true));
            if ((dx + dz) % 4 == 0) set(level, pos, x, 7, z, WALL);
        }
    }

    private static boolean dy8Window(int ax, boolean outer) {
        return ax == 1 && outer;
    }

    private static boolean dy4Slit(int dx, int dz) {
        int along = Math.abs(dx) > Math.abs(dz) ? dz : dx;
        return along % 6 == 3;
    }

    // ------------------------------------------------------------------ the great hall

    private void hall(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int ax, int az) {
        boolean inX = ax <= HALL_HW, inZ = dz >= HALL_Z0 && dz <= HALL_Z1;
        boolean wall = inX && inZ && (ax == HALL_HW || dz == HALL_Z0 || dz == HALL_Z1);
        boolean inside = inX && inZ && !wall;
        boolean longWall = ax == HALL_HW && dz > HALL_Z0 && dz < HALL_Z1;
        boolean pilaster = ax == HALL_HW + 1 && (dz + 5) % 4 == 0 && dz >= -5 && dz <= 11;
        boolean window = longWall && (dz + 3) % 4 == 0 && dz >= -3 && dz <= 9;
        boolean door = dz == HALL_Z1 && ax <= 1;
        boolean gableEnd = (dz == HALL_Z0 || dz == HALL_Z1) && ax <= HALL_HW;
        int roofY = HALL_WALL_TOP + 1 + (HALL_HW + 1 - ax); // eave at 9 over ax 8, ridge at 17 over ax 0

        if (wall) {
            set(level, pos, x, 0, z, brick(x, 0, z));
            for (int dy = 1; dy <= HALL_WALL_TOP; dy++) {
                BlockState s = brick(x, dy, z);
                if (dy == HALL_WALL_TOP) s = KingdomWallPiece.CHISELED; // the cornice
                if (dy == 1) s = KingdomWallPiece.ANDESITE;              // a plinth course
                if (window && dy >= 3 && dy <= 5) s = PANE;
                if (window && dy == 6) s = stairs(dx > 0 ? Direction.WEST : Direction.EAST, true); // the arch head
                if (door && dy <= 3) s = AIR;
                if (door && dy == 4) s = ax == 1 ? stairs(dx > 0 ? Direction.EAST : Direction.WEST, true) : KingdomWallPiece.CHISELED;
                set(level, pos, x, dy, z, s);
            }
            if (gableEnd) {
                for (int dy = HALL_WALL_TOP + 1; dy < roofY; dy++) {
                    BlockState s = brick(x, dy, z);
                    // a rose window in the south gable, a slit in the north
                    if (dz == HALL_Z1 && ((ax <= 1 && dy >= 11 && dy <= 12) || (ax == 2 && dy == 11) || (ax == 0 && (dy == 10 || dy == 13)))) s = PANE;
                    if (dz == HALL_Z0 && ax == 0 && dy >= 10 && dy <= 11) s = PANE;
                    set(level, pos, x, dy, z, s);
                }
            }
            if (door && ax == 1) KingdomWallPiece.banner(level, pos, x + (dx > 0 ? 1 : -1), cy + 6, z + 1, Direction.SOUTH);
        } else if (inside) {
            set(level, pos, x, 0, z, floorTile(x, z));
            for (int dy = 1; dy <= HALL_WALL_TOP; dy++) set(level, pos, x, dy, z, AIR);
        } else if (pilaster) {
            // pilasters rise the full height of the wall to carry the eaves
            set(level, pos, x, 0, z, brick(x, 0, z));
            fill(level, pos, x, 1, 7, z, ANDESITE);
            set(level, pos, x, 8, z, KingdomWallPiece.CHISELED);
        } else if (ax == HALL_HW + 1 && dz > HALL_Z0 && dz < HALL_Z1) {
            // between the pilasters, under the eave: corbels
            set(level, pos, x, 8, z, stairs(dx > 0 ? Direction.WEST : Direction.EAST, true));
        } else if (dz == HALL_Z1 + 1 && ax <= 2) {
            set(level, pos, x, 1, z, stairs(Direction.NORTH, false)); // the step up to the door
        }
        // the roof: stairs on both slopes, a slab at the ridge, the eaves a block out from the walls
        if (ax <= HALL_HW + 1 && dz >= HALL_Z0 - 1 && dz <= HALL_Z1 + 1) {
            if (ax == 0) set(level, pos, x, roofY, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
            else set(level, pos, x, roofY, z, slate(dx > 0 ? Direction.WEST : Direction.EAST));
            if (inside) for (int dy = HALL_WALL_TOP + 1; dy < roofY; dy++) set(level, pos, x, dy, z, AIR);
            // small dormers: a slate step out over the third and seventh bay
            if (ax == 3 && (dz == 2 || dz == 8)) {
                set(level, pos, x, roofY + 1, z, slate(dx > 0 ? Direction.WEST : Direction.EAST));
                set(level, pos, x, roofY, z, PANE);
            }
            // the two gable ends carry a stone wall with a finial
            if (ax == 0 && (dz == HALL_Z0 - 1 || dz == HALL_Z1 + 1)) {
                set(level, pos, x, roofY, z, KingdomWallPiece.CHISELED);
                set(level, pos, x, roofY + 1, z, WALL);
            }
        }
        if (inside) interior(level, pos, random, x, z, dx, dz, ax, az);
        // the chimney of the hearth on the east wall, up through the roof
        if (dx == HALL_HW && dz == -2) {
            for (int dy = HALL_WALL_TOP + 1; dy <= RIDGE + 1; dy++) set(level, pos, x, dy, z, brick(x, dy, z));
            set(level, pos, x, RIDGE + 2, z, WALL);
            set(level, pos, x, RIDGE + 3, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
        }
    }

    private void interior(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int ax, int az) {
        // the red carpet from the door to the dais
        if (ax <= 1 && dz >= -1 && dz <= HALL_Z1 - 1) set(level, pos, x, 1, z, CARPET);
        // pillars in two rows carry the roof; chiselled capitals; arches of upside-down stairs between them and the wall
        boolean pillar = ax == 4 && (dz == -1 || dz == 3 || dz == 7 || dz == 11);
        if (pillar) {
            fill(level, pos, x, 1, 7, z, ANDESITE);
            set(level, pos, x, HALL_WALL_TOP, z, KingdomWallPiece.CHISELED);
            set(level, pos, x, HALL_WALL_TOP + 1, z, KingdomWallPiece.CHISELED);
        }
        if (ax == 5 && (dz == -1 || dz == 3 || dz == 7 || dz == 11)) set(level, pos, x, HALL_WALL_TOP, z, stairs(dx > 0 ? Direction.WEST : Direction.EAST, true));
        if (ax == 6 && (dz == -1 || dz == 3 || dz == 7 || dz == 11)) set(level, pos, x, HALL_WALL_TOP, z, stairs(dx > 0 ? Direction.EAST : Direction.WEST, true));
        // chandeliers: chains from the ridge to a ring of lanterns
        if (ax == 0 && (dz == 1 || dz == 6)) {
            for (int dy = 9; dy <= RIDGE - 1; dy++) set(level, pos, x, dy, z, Blocks.CHAIN.defaultBlockState());
            set(level, pos, x, 8, z, lantern(true));
        }
        if (ax == 1 && (dz == 1 || dz == 6)) {
            set(level, pos, x, 9, z, FENCE);
            set(level, pos, x, 8, z, lantern(true));
        }
        // long tables and benches down the sides of the hall
        if (ax == 5 && dz >= 1 && dz <= 9) {
            set(level, pos, x, 1, z, dz == 5 ? FENCE : Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            if (dz == 5) set(level, pos, x, 2, z, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
            if (dz == 2 || dz == 8) set(level, pos, x, 2, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        }
        if (ax == 6 && dz >= 1 && dz <= 9 && dz % 2 == 1) set(level, pos, x, 1, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dx > 0 ? Direction.EAST : Direction.WEST));
        // the hearth on the east wall
        if (dx == 6 && dz == -2) {
            set(level, pos, x, 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            set(level, pos, x, 2, z, AIR);
            for (int dy = 3; dy <= HALL_WALL_TOP; dy++) set(level, pos, x, dy, z, brick(x, dy, z));
        }
        if (dx == 6 && (dz == -3 || dz == -1)) {
            fill(level, pos, x, 1, 2, z, brick(x, 2, z));
            set(level, pos, x, 3, z, stairs(Direction.WEST, true));
        }
        // the dais and the throne at the north end
        if (dz <= -2 && dz >= HALL_Z0 + 1 && ax <= 4) {
            if (dz == -2) set(level, pos, x, 1, z, stairs(Direction.NORTH, false));
            else {
                set(level, pos, x, 1, z, ANDESITE);
                if (dz == -3) set(level, pos, x, 2, z, stairs(Direction.NORTH, false));
                else set(level, pos, x, 2, z, ax <= 1 ? CARPET : ANDESITE);
            }
            if (ax == 0 && dz == -4) {
                set(level, pos, x, 2, z, GOLD);
                set(level, pos, x, 3, z, me.lovkar.wakingworld.kingdom.KingdomBlocks.THRONE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                set(level, pos, x, 4, z, AIR); // the throne's back rises into this block
                KingdomSpawns.king(level, cx, cy, cz, x + 0.5, cy + 2.8, z + 0.55);
            }
            if (ax == 0 && dz == -5) {
                fill(level, pos, x, 2, 4, z, GOLD);
                set(level, pos, x, 5, z, KingdomWallPiece.CHISELED);
            }
            if (ax == 1 && dz == -4) {
                set(level, pos, x, 2, z, GOLD);
                set(level, pos, x, 3, z, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
                set(level, pos, x, 4, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
            if (ax == 1 && dz == -5) {
                fill(level, pos, x, 2, 3, z, GOLD);
                set(level, pos, x, 4, z, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
            }
            if (ax == 3 && dz == -5) {
                set(level, pos, x, 3, z, WALL);
                set(level, pos, x, 4, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
        if (ax == 3 && dz == HALL_Z0 + 1) KingdomWallPiece.banner(level, pos, x, cy + 5, z, Direction.SOUTH);
        if (ax == 3 && dz == -1) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.KNIGHT, 3);
        // the scribe's desk by the door
        if (dx == -5 && dz == 11) set(level, pos, x, 1, z, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        if (dx == -6 && dz == 11) KingdomSpawns.trader(level, cx, cy, cz, x, cy + 1, z, TownsfolkEntity.SCRIBE);
    }

    // ------------------------------------------------------------------ the donjon

    private void donjon(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, double dr) {
        int lx = dx, lz = dz - DON_Z;
        boolean shell = dr > DON_IN && dr <= DON_R;
        boolean inside = dr <= DON_IN;
        boolean ladder = lx == 0 && lz == -5;
        boolean shaft = lx == -5 && lz == 0;
        boolean hallDoor = shell && dz >= HALL_Z0 && Math.abs(dx) == 2;
        if (dr > DON_R) {
            // the ring just outside the shell: the battered foot, the corbels and the parapet, then the cone
            if (!(dz >= HALL_Z0 - 1 && Math.abs(dx) <= 3)) {
                set(level, pos, x, 1, z, brick(x, 1, z));
                set(level, pos, x, 2, z, stairs(toward(lx, lz, false), false));
            }
            set(level, pos, x, PLATFORM, z, stairs(toward(lx, lz, false), true));
            set(level, pos, x, PLATFORM + 1, z, ((int) Math.round(KingdomWallPiece.angleOf(lx, lz) / 20)) % 2 == 0 ? brick(x, PLATFORM + 1, z) : KingdomWallPiece.slab(false));
            cone(level, pos, x, z, lx, lz, dr, PLATFORM + 3, DON_R + 1.7);
            return;
        }
        underground(level, pos, random, x, z, dx, dz, lx, lz, dr, shaft);
        if (shell) {
            for (int dy = 1; dy <= PLATFORM - 1; dy++) {
                BlockState s = brick(x, dy, z);
                if (dy == F1 - 1 || dy == F2 - 1 || dy == PLATFORM - 2) s = KingdomWallPiece.CHISELED; // string courses under every floor
                boolean onAxis = (lx == 0 && Math.abs(lz) > DON_IN) || (lz == 0 && Math.abs(lx) > DON_IN);
                boolean diag = Math.abs(Math.abs(lx) - Math.abs(lz)) <= 0 && dr > DON_IN;
                if (onAxis && ((dy >= F0 + 2 && dy <= F0 + 3) || (dy >= F1 + 2 && dy <= F1 + 3) || (dy >= F2 + 2 && dy <= F2 + 3))) s = PANE;
                if (onAxis && (dy == F0 + 4 || dy == F1 + 4 || dy == F2 + 4)) s = stairs(toward(lx, lz, false), true);
                if (diag && (dy == F1 + 3 || dy == F2 + 3)) s = BARS;
                if (hallDoor && dy >= F0 + 1 && dy <= F0 + 3) s = AIR;
                if (hallDoor && dy == F0 + 4) s = KingdomWallPiece.CHISELED;
                set(level, pos, x, dy, z, s);
            }
            set(level, pos, x, PLATFORM, z, brick(x, PLATFORM, z));
            set(level, pos, x, PLATFORM + 1, z, brick(x, PLATFORM + 1, z));
            if ((lx == 4 || lx == -4) && lz == 4) KingdomWallPiece.banner(level, pos, x + (lx > 0 ? 1 : -1), cy + F1 + 5, z + 1, Direction.SOUTH);
        } else if (inside) {
            if (!shaft) fill(level, pos, x, 0, F0 - 1, z, brick(x, 1, z));
            for (int dy = F0; dy <= PLATFORM - 1; dy++) {
                BlockState s = AIR;
                if (dy == F0) s = shaft ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.EAST).setValue(TrapDoorBlock.HALF, Half.TOP) : floorTile(x, z);
                else if (dy == F1 || dy == F2) s = ladder ? AIR : PLANKS;
                if (ladder && dy > F0) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
                set(level, pos, x, dy, z, s);
            }
            set(level, pos, x, PLATFORM, z, ladder ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH) : SLATE);
            furnish(level, pos, random, x, z, lx, lz, dr);
        }
        cone(level, pos, x, z, lx, lz, dr, PLATFORM + 3, DON_R + 1.7);
    }

    /** The donjon's rooms: the guard room below, the king's chamber, the library. */
    private void furnish(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int lx, int lz, double dr) {
        boolean byWall = dr > DON_IN - 1.0;
        // the guard room: a chest, a barrel, a table, lanterns on chains from every ceiling
        if (lx == 3 && lz == -3) {
            set(level, pos, x, F0 + 1, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), STORES);
        }
        if (lx == -3 && lz == -3) set(level, pos, x, F0 + 1, z, Blocks.BARREL.defaultBlockState());
        if (lx == 0 && lz == 0) {
            for (int f : new int[]{F0, F1, F2}) {
                set(level, pos, x, f + 6, z, Blocks.CHAIN.defaultBlockState());
                set(level, pos, x, f + 5, z, lantern(true));
            }
        }
        if (lx == 3 && lz == 3) KingdomSpawns.guard(level, cx, cy, cz, x, cy + F0 + 1, z, GuardEntity.KNIGHT, 3);
        // the king's chamber: a canopied bed, carpets, chests, a table by the window
        if (Math.abs(lx) <= 2 && lz >= -3 && lz <= 1 && !(lx == 0 && lz == 0)) set(level, pos, x, F1 + 1, z, Blocks.BLUE_CARPET.defaultBlockState());
        if (lx == 0 && lz == 2) set(level, pos, x, F1 + 1, z, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        if (lx == 0 && lz == 3) set(level, pos, x, F1 + 1, z, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        if (Math.abs(lx) == 1 && lz == 3) fill(level, pos, x, F1 + 1, F1 + 3, z, FENCE);
        if (Math.abs(lx) <= 1 && lz >= 2 && lz <= 3) set(level, pos, x, F1 + 4, z, Blocks.RED_WOOL.defaultBlockState());
        if (lx == 4 && lz == -2) {
            set(level, pos, x, F1 + 1, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), STORES);
        }
        if (lx == -4 && lz == -2) set(level, pos, x, F1 + 1, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        if (lx == -4 && lz == 1) {
            set(level, pos, x, F1 + 1, z, FENCE);
            set(level, pos, x, F1 + 2, z, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
        }
        if (lx == -4 && lz == 2) set(level, pos, x, F1 + 1, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        if (lx == 4 && lz == 2) set(level, pos, x, F1 + 1, z, Blocks.FLOWER_POT.defaultBlockState());
        // the library: shelves round the wall (not before the windows or the ladder), a lectern, candles
        boolean beforeWindow = (lx == 0 || lz == 0) && Math.abs(lx) + Math.abs(lz) >= 4;
        if (byWall && !(lx == 0 && lz == -5) && !beforeWindow) {
            set(level, pos, x, F2 + 1, z, Blocks.BOOKSHELF.defaultBlockState());
            set(level, pos, x, F2 + 2, z, hash(lx, F2, lz) % 4 == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, toward(lx, lz, false)) : Blocks.BOOKSHELF.defaultBlockState());
        }
        if (lx == 0 && lz == 1) set(level, pos, x, F2 + 1, z, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (Math.abs(lx) == 2 && lz == 1) set(level, pos, x, F2 + 1, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        if (lx == 2 && lz == -2) set(level, pos, x, F2 + 1, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        if (lx == -2 && lz == -2) KingdomSpawns.trader(level, cx, cy, cz, x, cy + F2 + 1, z, TownsfolkEntity.RELIC_MONGER);
    }

    /** Under the donjon: a solid plinth down to the treasury's level, the vault itself, its shaft and iron door. */
    private void underground(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int lx, int lz, double dr, boolean shaft) {
        boolean vault = dr <= TR_R;
        boolean door = lx == -4 && lz == 0;
        boolean corridor = lx == -5 && lz == 0; // the shaft comes down here
        for (int dy = TR_FLOOR - 1; dy <= -1; dy++) {
            BlockState s = hash(dx, dy, dz) % 5 == 0 ? DEEP_BRICK : DEEP;
            if (vault && dy >= TR_FLOOR && dy <= TR_TOP) s = AIR;
            if (vault && dy == TR_FLOOR - 1) s = ((lx + lz) & 1) == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : DEEP;
            if (door && (dy == TR_FLOOR || dy == TR_FLOOR + 1)) s = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, dy == TR_FLOOR ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
            if (corridor && dy >= TR_FLOOR) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
            set(level, pos, x, dy, z, s);
        }
        if (shaft) for (int dy = 0; dy < F0; dy++) set(level, pos, x, dy, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        if (!vault) return;
        if (lx == 0 && lz == 0) {
            set(level, pos, x, TR_FLOOR, z, KingdomWallPiece.CHISELED);
            set(level, pos, x, TR_FLOOR + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            set(level, pos, x, TR_TOP, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            if (level instanceof net.minecraft.server.level.WorldGenRegion region) {
                int r = (int) Math.ceil(TR_R) + 2;
                KingdomData.get(region.getLevel()).setTreasury(new BlockPos(cx, cy, cz), new BoundingBox(cx - r, cy + TR_FLOOR - 1, cz + DON_Z - r, cx + r, cy + TR_TOP + 1, cz + DON_Z + r));
            }
        }
        if (Math.abs(lx) == 2 && Math.abs(lz) == 2) {
            set(level, pos, x, TR_FLOOR, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, lz > 0 ? Direction.NORTH : Direction.SOUTH));
            RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), TREASURY);
        }
        if (lx == 3 && lz == 0) {
            set(level, pos, x, TR_FLOOR, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), TREASURY);
        }
        if ((lx == 0 && Math.abs(lz) == 3) || (Math.abs(lx) == 3 && Math.abs(lz) == 1)) {
            set(level, pos, x, TR_FLOOR, z, GOLD);
            if (hash(lx, 1, lz) % 2 == 0) set(level, pos, x, TR_FLOOR + 1, z, GOLD);
        }
        if (Math.abs(lx) == 1 && Math.abs(lz) == 3) set(level, pos, x, TR_FLOOR, z, Blocks.DECORATED_POT.defaultBlockState());
        if (lx == 0 && Math.abs(lz) == 2) set(level, pos, x, TR_FLOOR, z, GOLD);
    }

    // ------------------------------------------------------------------ the courtyard

    private void courtyard(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, int ax, int az) {
        // the fountain, south-west
        if (dx >= -16 && dx <= -12 && dz >= 6 && dz <= 10) {
            boolean rim = dx == -16 || dx == -12 || dz == 6 || dz == 10;
            boolean center = dx == -14 && dz == 8;
            set(level, pos, x, 0, z, ANDESITE);
            if (rim) set(level, pos, x, 1, z, ((dx + dz) & 1) == 0 ? KingdomWallPiece.slab(false) : WALL);
            else set(level, pos, x, 1, z, center ? KingdomWallPiece.CHISELED : Blocks.WATER.defaultBlockState());
            if (center) {
                set(level, pos, x, 2, z, WALL);
                set(level, pos, x, 3, z, Blocks.SEA_LANTERN.defaultBlockState());
                KingdomSpawns.trader(level, cx, cy, cz, x, cy + 1, z + 4, TownsfolkEntity.CHANDLER);
            }
            return;
        }
        // the stables, east: an open shed of spruce posts under a sloping roof, stalls of fences, hay
        if (dx >= 11 && dx <= 18 && dz >= -4 && dz <= 6) {
            boolean post = (dx == 11 || dx == 18) && (dz == -4 || dz == 1 || dz == 6);
            boolean back = dx == 18;
            set(level, pos, x, 0, z, dz % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
            if (post) fill(level, pos, x, 1, 3, z, Blocks.SPRUCE_LOG.defaultBlockState());
            else if (back) fill(level, pos, x, 1, 3, z, PLANKS);
            else if (dx == 11 && dz % 3 != 0) set(level, pos, x, 1, z, FENCE);
            else if (dx == 17 && hash(dx, 1, dz) % 2 == 0) set(level, pos, x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            if (dx >= 12 && dx <= 17 && (dz == -1 || dz == 3)) set(level, pos, x, 1, z, FENCE);
            // the roof slopes down towards the yard: stairs, higher at the back
            if (dx >= 16) set(level, pos, x, 5, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            else if (dx >= 13) set(level, pos, x, 4, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            else set(level, pos, x, 4, z, Blocks.SPRUCE_SLAB.defaultBlockState());
            if (dx == 18) set(level, pos, x, 4, z, PLANKS);
            if (dx == 14 && dz == 1) set(level, pos, x, 3, z, lantern(true));
            return;
        }
        // the forge, west: a stone hut open to the yard - anvil, blast furnace, cauldron, grindstone, the smith
        if (dx >= -18 && dx <= -11 && dz >= -4 && dz <= 4) {
            boolean shell = dx == -18 || dz == -4 || dz == 4;
            set(level, pos, x, 0, z, floorTile(x, z));
            for (int dy = 1; dy <= 3; dy++) set(level, pos, x, dy, z, shell ? brick(x, dy, z) : AIR);
            if (dx == -18) set(level, pos, x, 4, z, brick(x, 4, z));
            else set(level, pos, x, 4, z, dx <= -15 ? stairs(Direction.EAST, false) : dx <= -13 ? KingdomWallPiece.slab(true) : KingdomWallPiece.slab(false));
            if (dx == -17 && dz == -3) set(level, pos, x, 1, z, Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            if (dx == -17 && dz == -2) set(level, pos, x, 1, z, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            if (dx == -15 && dz == 0) set(level, pos, x, 1, z, Blocks.ANVIL.defaultBlockState());
            if (dx == -17 && dz == 2) set(level, pos, x, 1, z, Blocks.LAVA_CAULDRON.defaultBlockState());
            if (dx == -17 && dz == 3) set(level, pos, x, 1, z, Blocks.GRINDSTONE.defaultBlockState());
            if (dx == -14 && dz == -3) {
                set(level, pos, x, 1, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
                RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), STORES);
            }
            if (dx == -17 && dz == 0) {
                set(level, pos, x, 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
                fill(level, pos, x, 2, 3, z, AIR);
                fill(level, pos, x, 4, 7, z, brick(x, 4, z)); // the chimney
                set(level, pos, x, 8, z, WALL);
            }
            if (dx == -15 && dz == 3) KingdomSpawns.trader(level, cx, cy, cz, x, cy + 1, z, TownsfolkEntity.SMITH);
            if (dx == -14 && dz == 2) set(level, pos, x, 3, z, lantern(true));
            return;
        }
        // the barracks, north-east: a stone house with bunks and chests under a low gable, spearmen at the door
        if (dx >= 9 && dx <= 18 && dz >= -19 && dz <= -12) {
            boolean shell = dx == 9 || dx == 18 || dz == -19 || dz == -12;
            boolean door = dx == 13 && dz == -12;
            set(level, pos, x, 0, z, shell ? brick(x, 0, z) : floorTile(x, z));
            for (int dy = 1; dy <= 3; dy++) {
                BlockState s = shell ? brick(x, dy, z) : AIR;
                if (shell && dy == 2 && dz == -12 && (dx == 11 || dx == 15)) s = PANE;
                if (shell && dy == 2 && dx == 18 && (dz == -14 || dz == -17)) s = PANE;
                if (door && dy <= 2) s = AIR;
                set(level, pos, x, dy, z, s);
            }
            int k = Math.min(dz + 19, -12 - dz); // 0 at the eaves, 3 at the ridge
            if (k >= 3) set(level, pos, x, 7, z, KingdomWallPiece.slab(false));
            else set(level, pos, x, 4 + k, z, stairs(dz < -15 ? Direction.SOUTH : Direction.NORTH, false));
            if (k > 0 && (dx == 9 || dx == 18)) fill(level, pos, x, 4, 3 + Math.min(k, 3), z, brick(x, 4, z)); // the gable ends
            if (door) {
                set(level, pos, x, 1, z, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH));
                set(level, pos, x, 2, z, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            if (dz == -18 && dx >= 10 && dx <= 17 && dx % 2 == 0) set(level, pos, x, 1, z, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            if (dz == -17 && dx >= 10 && dx <= 17 && dx % 2 == 0) set(level, pos, x, 1, z, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
            if (dz == -13 && (dx == 10 || dx == 17)) {
                set(level, pos, x, 1, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), STORES);
            }
            if (dx == 13 && dz == -15) {
                set(level, pos, x, 3, z, lantern(true));
                KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.SPEARMAN, 6);
            }
            return;
        }
        if (dx == 13 && dz == -10) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.SPEARMAN, 8);
        // a well in the north-west corner of the yard
        if (Math.abs(dx + 15) <= 1 && Math.abs(dz + 16) <= 1) {
            boolean centre = dx == -15 && dz == -16;
            boolean corner = Math.abs(dx + 15) == 1 && Math.abs(dz + 16) == 1;
            set(level, pos, x, 0, z, centre ? Blocks.WATER.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
            if (!centre && !corner) set(level, pos, x, 1, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
            if (corner) fill(level, pos, x, 1, 3, z, FENCE);
            set(level, pos, x, 4, z, centre ? PLANKS : Blocks.SPRUCE_SLAB.defaultBlockState());
            if (centre) set(level, pos, x, 3, z, lantern(true));
            return;
        }
        // the relic-monger's stall by the hall's door, knights before it
        if (dx == -6 && dz == 15) {
            set(level, pos, x, 1, z, Blocks.BARREL.defaultBlockState());
            set(level, pos, x, 2, z, lantern(false));
            KingdomSpawns.trader(level, cx, cy, cz, x, cy + 1, z + 1, TownsfolkEntity.RELIC_MONGER);
        }
        if (dz == 14 && ax == 3) KingdomSpawns.guard(level, cx, cy, cz, x, cy + 1, z, GuardEntity.KNIGHT, 3);
        // lamp posts at the path corners and along the yard
        if ((ax == 3 && dz == 17) || (ax == 15 && dz == 17) || (ax == 17 && dz == -8) || (ax == 3 && dz == 21) || (ax == 13 && dz == 15)) {
            set(level, pos, x, 1, z, WALL);
            set(level, pos, x, 2, z, FENCE);
            set(level, pos, x, 3, z, lantern(false));
        }
    }
}
