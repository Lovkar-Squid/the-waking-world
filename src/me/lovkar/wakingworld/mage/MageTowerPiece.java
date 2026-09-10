package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.worldgen.Geo;
import me.lovkar.wakingworld.worldgen.LocalPiece;
import me.lovkar.wakingworld.worldgen.WakingStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class MageTowerPiece extends LocalPiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/mage_tower"));
    public static final ResourceKey<LootTable> STUDY = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/mage_study"));

    // radii
    private static final double R_IN = 4.9;      // interior air out to here
    private static final double R_WALL = 6.4;    // the shell's outer face
    private static final double STAIR_LO = 3.5, STAIR_HI = 4.9;
    private static final double SHELF_LO = 4.1;  // the ring of books hugs the wall
    private static final int STEP_DEG = 30;      // twelve steps to a turn

    // levels, all relative to the threshold (dy 0 is the hall's air, dy -1 its floor)
    private static final int BASE = -8;          // the cellar's floor slab
    private static final int CELLAR = -7;        // cellar air -7..-2
    private static final int HALL = 0;           // hall air 0..7, floor slab at -1
    private static final int STUDY_Y = 9;        // study air 9..16, floor slab at 8
    private static final int CHAMBER = 18;       // chamber air 18..26, floor slab at 17
    private static final int DECK = 27;          // the cone's foot
    private static final int SPIRE = 39;         // the point
    public static final int TOP = 43;            // the light above it

    private static final int[] FLOORS = {-1, 8, 17};

    private final boolean crag;

    public MageTowerPiece(BlockPos origin, Rotation rot, long seed, boolean crag) {
        super(WakingStructures.MAGE_TOWER_PIECE.get(), origin, rot, seed, -11, -11, 11, 13, BASE - 40, TOP + 2);
        this.crag = crag;
    }

    public MageTowerPiece(CompoundTag tag) {
        super(WakingStructures.MAGE_TOWER_PIECE.get(), tag);
        this.crag = tag.getBoolean("Crag");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("Crag", crag);
    }

    // ---- the palette -----------------------------------------------------------------------------

    private BlockState wall(int lx, int dy, int lz) {
        int h = hash(lx, dy, lz) % 100;
        return h < 38 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                : h < 56 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                : h < 71 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                : h < 84 ? Blocks.BLACKSTONE.defaultBlockState()
                : h < 93 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private BlockState roof(int lx, int dy, int lz) {
        int h = hash(lx, dy, lz) % 100;
        return h < 44 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                : h < 68 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                : h < 86 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private BlockState boards(int lx, int dy, int lz) {
        int h = hash(lx, dy, lz) % 100;
        return h < 70 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }

    private BlockState paving(int lx, int dy, int lz) {
        int h = hash(lx, dy, lz) % 100;
        return h < 52 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                : h < 76 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : h < 92 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.GILDED_BLACKSTONE.defaultBlockState();
    }

    // ---- geometry --------------------------------------------------------------------------------

    private static double dist(int lx, int lz) {
        return Math.sqrt((double) lx * lx + (double) lz * lz);
    }

    /** The smaller of the two ways round between two bearings, in degrees. */
    private static double arc(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static double stepAngle(int dy) {
        return Math.floorMod(dy * STEP_DEG, 360);
    }

    /** A step of the spiral stands in this column at this height. */
    private static boolean step(double d, double a, int dy) {
        return d >= STAIR_LO && d <= STAIR_HI && dy >= CELLAR && dy <= 17 && arc(a, stepAngle(dy)) <= 20;
    }

    /** The step itself and the two levels of headroom above it: nothing else may stand here. */
    private static boolean stairway(double d, double a, int dy) {
        return step(d, a, dy) || step(d, a, dy - 1) || step(d, a, dy - 2);
    }

    /** Where a floor is cut through so the spiral can come up: the approach, not the step on the level. */
    private static boolean well(double d, double a, int dy) {
        for (int f : FLOORS) {
            if (dy != f) continue;
            // the well is the quarter-turn of stair ahead of the landing, a little wider than the treads
            if (d < 3.0 || d > 5.4) return false;
            long turn = Math.floorMod(Math.round(stepAngle(f) - a), 360L);
            return turn >= 22 && turn <= 88;
        }
        return false;
    }

    /** The cone's outer radius at a height. */
    private static double cone(int dy) {
        return R_WALL * (1.0 - (dy - DECK) / (double) (SPIRE - DECK));
    }

    /** The balcony's footprint, out of the front of the chamber. */
    private static boolean balcony(int lx, int lz) {
        return Math.abs(lx) <= 3 && lz >= 4 && lz <= 10;
    }

    /** The way in and the landing outside it. */
    private static boolean porch(int lx, int lz) {
        return Math.abs(lx) <= 2 && lz >= 5 && lz <= 11;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz) {
        double d = dist(lx, lz);
        double a = Geo.angle(lx, lz);
        boolean near = d <= 9.5 || balcony(lx, lz) || porch(lx, lz);
        if (!near) return;

        // trees first: trunks and leaves out of the way, the ground left exactly as it lies
        clearGrowth(level, pos, wx, wz);

        boolean anything = false;
        int lowest = TOP;
        for (int dy = BASE; dy <= TOP; dy++) {
            BlockState s = at(level, pos, random, wx, wz, lx, lz, dy, d, a);
            if (s == null) continue;
            set(level, pos, wx, dy, wz, s);
            if (!s.isAir()) {
                anything = true;
                lowest = Math.min(lowest, dy);
            }
        }
        // and it carries its own ground down to the rock, whatever the rock is doing
        if (anything && lowest <= BASE + 2 && d <= R_WALL + 0.6) {
            foundation(level, pos, wx, wz, lowest, wall(lx, lowest - 1, lz), 48);
        } else if (anything && lowest <= 0 && (porch(lx, lz) || d <= R_WALL + 2.2)) {
            foundation(level, pos, wx, wz, lowest, wall(lx, lowest - 1, lz), 24);
        }

        fittings(level, pos, random, wx, wz, lx, lz, d, a);
    }

    /** What stands at one height of one column: null leaves the world alone, air carves it out. */
    private BlockState at(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random,
                          int wx, int wz, int lx, int lz, int dy, double d, double a) {
        boolean inside = d <= R_IN;
        boolean shell = d > R_IN && d <= R_WALL;

        // --- the cone and its ring of spikes ---
        if (dy >= DECK) {
            if (dy > SPIRE + 4) return null;
            if (dy <= SPIRE) {
                double r = cone(dy);
                if (d <= r - 1.15) return dy == DECK ? paving(lx, dy, lz) : Blocks.AIR.defaultBlockState();
                if (d <= r + 0.35) return roof(lx, dy, lz);
            }
            // the spikes: eight of them standing on corbels round the cone's foot
            if (dy <= DECK + 5) {
                double bear = Math.floorMod(Math.round(a / 45.0) * 45L, 360L);
                boolean onSpike = arc(a, bear) <= 7 && d >= R_WALL - 0.9 && d <= R_WALL + 0.9;
                if (onSpike && dy <= DECK + 4 - (int) Math.round(arc(a, bear))) return roof(lx, dy, lz);
            }
            // the light on the point, which is how you find the tower at night
            if (dy > SPIRE && d < 0.9) {
                return dy == SPIRE + 1 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                        : dy == SPIRE + 2 ? Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)
                        : null;
            }
            return null;
        }

        // --- the balcony (only where it is: below it the same columns are the porch's) ---
        if (balcony(lx, lz) && d > R_WALL && dy >= 15) {
            if (dy == 17) return paving(lx, dy, lz);
            if (dy == 16 && Math.abs(lx) <= 2 && lz <= 8) return wall(lx, dy, lz);
            if (dy == 15 && Math.abs(lx) <= 1 && lz <= 7) return wall(lx, dy, lz);
            boolean rail = lz == 10 || Math.abs(lx) == 3;
            if (dy == 18 && rail) return paving(lx, dy, lz);
            if (dy == 19 && rail && ((lx + lz) & 1) == 0) return paving(lx, dy, lz);
            if (dy >= 18 && dy <= 24) return Blocks.AIR.defaultBlockState();
            return null;
        }

        // --- the porch ---
        if (porch(lx, lz) && d > R_WALL) {
            int drop = Math.max(0, (lz - 8)) / 2;                 // two shallow steps down and then the ground
            if (dy == -1 - drop) return paving(lx, dy, lz);
            if (dy > -1 - drop && dy <= 4) return Blocks.AIR.defaultBlockState();
            return null;
        }

        // --- the body ---
        if (dy == BASE) return d <= R_WALL ? paving(lx, dy, lz) : null;
        if (!inside && !shell) return null;

        if (shell) {
            if (window(lx, lz, dy, d, a)) return Blocks.AIR.defaultBlockState();
            if (dy == 17 || dy == 27 - 1) return paving(lx, dy, lz);
            return wall(lx, dy, lz);
        }

        // inside: floors, and air between them
        for (int f : FLOORS) {
            if (dy != f) continue;
            if (well(d, a, dy)) return Blocks.AIR.defaultBlockState();
            return f == -1 ? paving(lx, dy, lz) : f == 8 ? boards(lx, dy, lz) : chamberFloor(lx, dy, lz, d);
        }
        if (dy == 26 + 1) return paving(lx, dy, lz);              // the chamber's ceiling
        if (step(d, a, dy)) return paving(lx, dy, lz);
        if (dy >= CELLAR && dy <= 26) return Blocks.AIR.defaultBlockState();
        return null;
    }

    /** The chamber's floor is a wheel: a ring of crying obsidian round a black heart. */
    private BlockState chamberFloor(int lx, int dy, int lz, double d) {
        if ((Math.abs(lx) == 2 && lz == 0) || (Math.abs(lz) == 2 && lx == 0)) return Blocks.SOUL_SOIL.defaultBlockState();
        if (d < 0.9) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        if (d >= 2.4 && d <= 3.3) {
            return ((lx + lz) & 1) == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        }
        if (d >= 1.2 && d < 2.4 && (Math.abs(lx) <= 0 || Math.abs(lz) <= 0)) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return paving(lx, dy, lz);
    }

    /** Slits at the low floors, tall arches at the chamber, and the door itself. */
    private boolean window(int lx, int lz, int dy, double d, double a) {
        // the door: dead ahead, three high
        if (Math.abs(lx) <= 1 && lz > 0 && dy >= 0 && dy <= 3) return true;
        // the balcony's door, above it
        if (Math.abs(lx) <= 1 && lz > 0 && dy >= 18 && dy <= 21) return true;
        if (dy >= 3 && dy <= 5) {                                  // hall: four slits on the diagonals
            double bear = Math.floorMod(Math.round((a - 45) / 90.0) * 90L + 45L, 360L);
            return arc(a, bear) <= 5;
        }
        if (dy >= 11 && dy <= 13) {                                // study: four slits on the cardinals
            double bear = Math.floorMod(Math.round(a / 90.0) * 90L, 360L);
            return arc(a, bear) <= 6 && arc(a, 90) > 20;
        }
        if (dy >= 19 && dy <= 24) {                                // chamber: two lancets a side, round a mullion
            double bear = Math.floorMod(Math.round(a / 90.0) * 90L, 360L);
            if (arc(a, 90) <= 25) return false;                    // that side is the balcony's
            double off = arc(a, bear);
            if (dy >= 24) return off <= 11 && off >= 3;             // the heads of the two lights meet over it
            if (dy == 23) return off <= 12 && off >= 3;
            return off <= 13 && off >= 3;                           // the mullion is the three degrees in the middle
        }
        return false;
    }

    // ---- what is in the rooms --------------------------------------------------------------------

    private void fittings(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random,
                          int wx, int wz, int lx, int lz, double d, double a) {
        if (d > R_IN) return;

        // the cellar: cobwebs in the corners, a chest he has not opened, a light he left burning
        if (d >= SHELF_LO && !stairway(d, a, CELLAR) && hash(lx, 3, lz) % 100 < 22) {
            set(level, pos, wx, CELLAR, wz, Blocks.COBWEB.defaultBlockState());
        }
        if (lx == -2 && lz == 1) chest(level, pos, random, wx, CELLAR, wz, Direction.EAST, LOOT);
        if (lx == 2 && lz == -1 && !stairway(d, a, CELLAR)) set(level, pos, wx, CELLAR, wz, Blocks.SOUL_SAND.defaultBlockState());
        if (lx == 0 && lz == 2) candles(level, pos, wx, CELLAR, wz, 3);

        // the hall
        if (lx == 0 && lz == -2) set(level, pos, wx, HALL, wz, Blocks.LECTERN.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (lx == -2 && lz == -1) set(level, pos, wx, HALL, wz, Blocks.BREWING_STAND.defaultBlockState());
        if (lx == -2 && lz == 1) set(level, pos, wx, HALL, wz, Blocks.CAULDRON.defaultBlockState());
        if (lx == 2 && lz == -2) chest(level, pos, random, wx, HALL, wz, Direction.WEST, LOOT);
        if (lx == 2 && lz == 1) candles(level, pos, wx, HALL, wz, 4);
        if (lx == 0 && lz == 2) set(level, pos, wx, HALL, wz, Blocks.DECORATED_POT.defaultBlockState());
        hanging(level, pos, lx, lz, wx, wz, 6, a);

        // the study: books all round the wall, and the table in the middle of them
        for (int dy = STUDY_Y; dy <= STUDY_Y + 3; dy++) {
            if (d < SHELF_LO || stairway(d, a, dy)) continue;
            int h = hash(lx, dy, lz) % 100;
            set(level, pos, wx, dy, wz, dy == STUDY_Y + 3 && h < 45 ? Blocks.AIR.defaultBlockState()
                    : h < 62 ? Blocks.BOOKSHELF.defaultBlockState()
                    : h < 82 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState()
                    : h < 92 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                    : Blocks.BOOKSHELF.defaultBlockState());
        }
        if (lx == 0 && lz == 0) set(level, pos, wx, STUDY_Y, wz, Blocks.ENCHANTING_TABLE.defaultBlockState());
        if (Math.abs(lx) == 2 && Math.abs(lz) == 2) candles(level, pos, wx, STUDY_Y, wz, 2);
        if (lx == -2 && lz == 0) chest(level, pos, random, wx, STUDY_Y, wz, Direction.EAST, STUDY);
        if (lx == 2 && lz == 0) set(level, pos, wx, STUDY_Y, wz, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        hanging(level, pos, lx, lz, wx, wz, 15, a);

        // the chamber: soul lanterns on chains, and the man himself
        if (Math.abs(lx) == 3 && Math.abs(lz) == 3) {
            for (int dy = 24; dy <= 25; dy++) set(level, pos, wx, dy, wz, Blocks.CHAIN.defaultBlockState());
            set(level, pos, wx, 23, wz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        if (lx == -3 && lz == 1) chest(level, pos, random, wx, CHAMBER, wz, Direction.EAST, STUDY);
        if (lx == 3 && lz == -1) {
            set(level, pos, wx, CHAMBER, wz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            set(level, pos, wx, CHAMBER + 1, wz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        if (lx == 0 && lz == -3) set(level, pos, wx, CHAMBER, wz, Blocks.LECTERN.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if ((Math.abs(lx) == 2 && lz == 0) || (Math.abs(lz) == 2 && lx == 0)) {
            set(level, pos, wx, CHAMBER, wz, Blocks.SOUL_FIRE.defaultBlockState());
        }
        if (lx == 0 && lz == 0) summon(level, wx, wz);
    }

    /** A soul lantern hung off the wall on a short chain, at four bearings. */
    private void hanging(WorldGenLevel level, BlockPos.MutableBlockPos pos, int lx, int lz, int wx, int wz, int dy, double a) {
        if (Math.abs(lx) != 3 || Math.abs(lz) != 3) return;
        double d = dist(lx, lz);
        if (stairway(d, a, dy) || stairway(d, a, dy - 1)) return;
        set(level, pos, wx, dy, wz, Blocks.CHAIN.defaultBlockState());
        set(level, pos, wx, dy - 1, wz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private void candles(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int dy, int wz, int n) {
        set(level, pos, wx, dy, wz, Blocks.BLACK_CANDLE.defaultBlockState()
                .setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n)))
                .setValue(BlockStateProperties.LIT, true));
    }

    /** Trunks and leaves out of the tower's way; the ground itself is never touched. */
    private void clearGrowth(WorldGenLevel level, BlockPos.MutableBlockPos pos, int wx, int wz) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
        int from = cy - 2;
        for (int y = from; y <= Math.min(top, cy + TOP); y++) {
            pos.set(wx, y, wz);
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES)
                    || s.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES) || s.is(net.minecraft.tags.BlockTags.SAPLINGS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** He is put in when the chamber's middle column is generated, and never anywhere else. */
    private void summon(WorldGenLevel level, int wx, int wz) {
        MageEntity mage = WakingWorld.DARK_MAGE.get().create(level.getLevel());
        if (mage == null) return;
        mage.moveTo(wx + 0.5, cy + CHAMBER, wz + 0.5, level.getRandom().nextFloat() * 360f, 0);
        mage.assign(new BlockPos(cx, cy, cz));
        level.addFreshEntityWithPassengers(mage);
        WakingWorld.LOGGER.info("mage: a tower at {} {} {} - {} is in it", cx, cy, cz, mage.mageName());
    }
}
