package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One abandoned building or plot, drawn from a procedural template in local coordinates (origin on
 * the floor, front towards +z), rotated with vanilla {@link Rotation}, weathered by a decay value
 * and set on the ground. A template is a list of <em>objects</em>, each with its own anchor
 * column: flat objects (a house, a tower, the crypt) sit at the piece's height on a foundation
 * with everything above their footprint cleared; ground objects (a grave, a tent, a fence post)
 * stand on the terrain under their own anchor. A trapped chest in the template marks the chest
 * that holds a Dead Letter - only where the variant allows one.
 */
public class RuinPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/ruin"));
    public static final ResourceKey<LootTable> LETTER = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/ruin_letter"));
    public static final ResourceKey<LootTable> HOUSE = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/kingdom_house"));
    public static final int STYLE_OAK = 0, STYLE_COLD = 1, STYLE_DRY = 2;
    /**
     * Variant bits: part of a hamlet; may hold a letter; the big two-storey house; intact - a lived-in
     * building of the kingdom: nothing fallen, nothing overgrown, every window glazed, every door hung,
     * the fires lit, the crops alive, and it stands on the kingdom's levelled ground.
     */
    public static final int V_HAMLET = 1, V_LETTER = 2, V_BIG = 4, V_INTACT = 8;

    public enum Kind {
        COTTAGE(5, 3, 9, 14, 6),
        WATCHTOWER(4, 3, 5, 20, 6),
        WELL(4, 3, 8, 8, 12),
        GRAVEYARD(8, 5, 9, 12, 14),
        CAMP(7, 4, 8, 8, 6),
        CHAPEL(8, 4, 9, 16, 6),
        MARKET(8, 4, 9, 8, 6),
        FARM(8, 3, 7, 6, 6),
        PALISADE(0, 99, 40, 8, 8);

        public final int reach, maxSlope, half, up, down;

        Kind(int reach, int maxSlope, int half, int up, int down) {
            this.reach = reach;
            this.maxSlope = maxSlope;
            this.half = half;
            this.up = up;
            this.down = down;
        }

        public static Kind of(String name) {
            for (Kind k : values()) if (k.name().equalsIgnoreCase(name)) return k;
            return COTTAGE;
        }
    }

    private final Kind kind;
    private final int cx, cy, cz;
    private final Rotation rot;
    private final int style, variant;
    private final float decay;
    private final long seed;

    /** One thing in the template with its own footing. */
    private static final class Obj {
        final int ax, az;
        final boolean flat;
        final Map<Long, BlockState> blocks = new HashMap<>();
        int minX = 999, maxX = -999, minZ = 999, maxZ = -999, maxY = -999;

        Obj(int ax, int az, boolean flat) {
            this.ax = ax;
            this.az = az;
            this.flat = flat;
        }
    }

    private List<Obj> objects;
    private Obj cur;
    /** Ground marks: (lx, lz, what) resolved against the terrain. what: 0 = path, 1 = bare earth, 2 = plant, 3 = farmland. */
    private List<int[]> ground;

    public RuinPiece(Kind kind, BlockPos origin, Rotation rot, int style, int variant, float decay, long seed) {
        super(WakingStructures.RUIN_PIECE.get(), 0, new BoundingBox(origin.getX() - kind.half - 1 - ((variant & V_HAMLET) != 0 ? 6 : 0), origin.getY() - ((variant & V_INTACT) != 0 ? 0 : kind.down),
                origin.getZ() - kind.half - 1 - ((variant & V_HAMLET) != 0 ? 6 : 0), origin.getX() + kind.half + 1 + ((variant & V_HAMLET) != 0 ? 6 : 0),
                origin.getY() + kind.up, origin.getZ() + kind.half + 1 + ((variant & V_HAMLET) != 0 ? 6 : 0)));
        this.kind = kind;
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.rot = rot;
        this.style = style;
        this.variant = variant;
        this.decay = decay;
        this.seed = seed;
    }

    /** The same piece, allowed to hold a letter. */
    public RuinPiece withLetter() {
        return new RuinPiece(kind, new BlockPos(cx, cy, cz), rot, style, variant | V_LETTER, decay, seed);
    }

    private boolean intact() {
        return (variant & V_INTACT) != 0;
    }

    public RuinPiece(CompoundTag tag) {
        super(WakingStructures.RUIN_PIECE.get(), tag);
        this.kind = Kind.values()[Math.floorMod(tag.getInt("Kind"), Kind.values().length)];
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.rot = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
        this.style = tag.getInt("Style");
        this.variant = tag.getInt("Variant");
        this.decay = tag.getFloat("Decay");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Kind", kind.ordinal());
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putInt("Rot", rot.ordinal());
        tag.putInt("Style", style);
        tag.putInt("Variant", variant);
        tag.putFloat("Decay", decay);
        tag.putLong("Seed", seed);
    }

    // ---------------------------------------------------------------- materials

    private BlockState planks() {
        return switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case STYLE_DRY -> Blocks.ACACIA_PLANKS.defaultBlockState();
            default -> Blocks.OAK_PLANKS.defaultBlockState();
        };
    }

    private BlockState log() {
        return switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_LOG.defaultBlockState();
            case STYLE_DRY -> Blocks.ACACIA_LOG.defaultBlockState();
            default -> Blocks.OAK_LOG.defaultBlockState();
        };
    }

    private BlockState stairs(Direction facing) {
        Block b = switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_STAIRS;
            case STYLE_DRY -> Blocks.SANDSTONE_STAIRS;
            default -> Blocks.OAK_STAIRS;
        };
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private BlockState stoneStairs(Direction facing, boolean upsideDown) {
        Block b = style == STYLE_DRY ? Blocks.SANDSTONE_STAIRS : Blocks.STONE_BRICK_STAIRS;
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }

    private BlockState slab(boolean top) {
        Block b = switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_SLAB;
            case STYLE_DRY -> Blocks.SANDSTONE_SLAB;
            default -> Blocks.OAK_SLAB;
        };
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private BlockState fence() {
        return switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_FENCE.defaultBlockState();
            case STYLE_DRY -> Blocks.ACACIA_FENCE.defaultBlockState();
            default -> Blocks.OAK_FENCE.defaultBlockState();
        };
    }

    private BlockState gate(Direction facing) {
        Block b = switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_FENCE_GATE;
            case STYLE_DRY -> Blocks.ACACIA_FENCE_GATE;
            default -> Blocks.OAK_FENCE_GATE;
        };
        return b.defaultBlockState().setValue(FenceGateBlock.FACING, facing);
    }

    private BlockState door(Direction facing, boolean upper) {
        Block b = switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_DOOR;
            case STYLE_DRY -> Blocks.ACACIA_DOOR;
            default -> Blocks.OAK_DOOR;
        };
        return b.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    private BlockState trapdoor(Direction facing, boolean open) {
        Block b = switch (style) {
            case STYLE_COLD -> Blocks.SPRUCE_TRAPDOOR;
            case STYLE_DRY -> Blocks.ACACIA_TRAPDOOR;
            default -> Blocks.OAK_TRAPDOOR;
        };
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, open).setValue(TrapDoorBlock.HALF, Half.TOP);
    }

    private BlockState stone(RandomSource r) {
        if (style == STYLE_DRY) return r.nextInt(100) < 70 ? Blocks.SANDSTONE.defaultBlockState() : r.nextInt(2) == 0 ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        return r.nextFloat() < (intact() ? 0.06F : 0.25F + decay * 0.4F) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    private BlockState bricks(RandomSource r) {
        if (style == STYLE_DRY) return r.nextInt(100) < 60 ? Blocks.CUT_SANDSTONE.defaultBlockState() : r.nextInt(2) == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
        float f = r.nextFloat();
        if (intact()) return f < 0.9F ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return f < 0.5F - decay * 0.2F ? Blocks.STONE_BRICKS.defaultBlockState() : f < 0.75F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    }

    private BlockState stoneSlab() {
        return (style == STYLE_DRY ? Blocks.SANDSTONE_SLAB : Blocks.COBBLESTONE_SLAB).defaultBlockState();
    }

    private BlockState brickSlab(boolean top) {
        return (style == STYLE_DRY ? Blocks.SANDSTONE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private BlockState wall(RandomSource r) {
        if (style == STYLE_DRY) return Blocks.SANDSTONE_WALL.defaultBlockState();
        return r.nextFloat() < 0.4F ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState();
    }

    private BlockState brickWall() {
        return (style == STYLE_DRY ? Blocks.SANDSTONE_WALL : Blocks.STONE_BRICK_WALL).defaultBlockState();
    }

    private BlockState floorBlock(RandomSource r) {
        float f = r.nextFloat();
        if (style == STYLE_DRY) return f < 0.6F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : f < 0.85F ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        return f < 0.55F ? planks() : f < 0.8F ? Blocks.COBBLESTONE.defaultBlockState() : f < 0.9F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    private BlockState woolTent(RandomSource r) {
        return switch (r.nextInt(5)) {
            case 0 -> Blocks.LIGHT_GRAY_WOOL.defaultBlockState();
            case 1 -> Blocks.BROWN_WOOL.defaultBlockState();
            case 2 -> Blocks.WHITE_WOOL.defaultBlockState();
            case 3 -> Blocks.RED_WOOL.defaultBlockState();
            default -> Blocks.GREEN_WOOL.defaultBlockState();
        };
    }

    private static BlockState candles(int n, boolean lit) {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n))).setValue(CandleBlock.LIT, lit);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LETTER_CHEST = Blocks.TRAPPED_CHEST.defaultBlockState();

    // ---------------------------------------------------------------- the template

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 42) | ((long) (y + 512) << 21) | (z + 512);
    }

    private static int kx(long k) {
        return (int) ((k >>> 42) & 0x1FFFFF) - 512;
    }

    private static int ky(long k) {
        return (int) ((k >>> 21) & 0x1FFFFF) - 512;
    }

    private static int kz(long k) {
        return (int) (k & 0x1FFFFF) - 512;
    }

    /** Starts a new object anchored at (ax, az). */
    private Obj obj(int ax, int az, boolean flat) {
        cur = new Obj(ax, az, flat);
        objects.add(cur);
        return cur;
    }

    private void put(int x, int y, int z, BlockState s) {
        cur.blocks.put(key(x, y, z), s);
        if (!s.isAir()) {
            cur.minX = Math.min(cur.minX, x);
            cur.maxX = Math.max(cur.maxX, x);
            cur.minZ = Math.min(cur.minZ, z);
            cur.maxZ = Math.max(cur.maxZ, z);
            cur.maxY = Math.max(cur.maxY, y);
        }
    }

    private BlockState at(int x, int y, int z) {
        return cur.blocks.get(key(x, y, z));
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) put(x, y, z, s);
    }

    private void build() {
        objects = new ArrayList<>();
        ground = new ArrayList<>();
        RandomSource r = RandomSource.create(seed);
        switch (kind) {
            case COTTAGE -> cottage(r, (variant & V_BIG) != 0);
            case WATCHTOWER -> tower(r);
            case WELL -> well(r);
            case GRAVEYARD -> graveyard(r);
            case CAMP -> camp(r);
            case CHAPEL -> chapel(r);
            case MARKET -> market(r);
            case FARM -> farm(r);
            case PALISADE -> palisade(r);
        }
        pruneHanging();
        if (intact()) tidy();
        // letters only where allowed: elsewhere the letter chest is an ordinary one
        boolean letter = variant == 0 || (variant & V_LETTER) != 0;
        if (!letter) {
            for (Obj o : objects) {
                for (Map.Entry<Long, BlockState> e : o.blocks.entrySet()) {
                    if (e.getValue().is(Blocks.TRAPPED_CHEST)) e.setValue(Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, e.getValue().getValue(HorizontalDirectionalBlock.FACING)));
                }
            }
        }
    }

    /** A lived-in building: no cobwebs, the hearths and candles lit, no spawners, nothing dead in the pots. */
    private void tidy() {
        for (Obj o : objects) {
            for (Map.Entry<Long, BlockState> e : o.blocks.entrySet()) {
                BlockState st = e.getValue();
                if (st.is(Blocks.COBWEB) || st.is(Blocks.DEAD_BUSH) || st.is(Blocks.SKELETON_SKULL) || st.is(Blocks.MOSS_CARPET)) e.setValue(AIR);
                else if (st.is(Blocks.CAMPFIRE)) e.setValue(st.setValue(CampfireBlock.LIT, true));
                else if (st.is(Blocks.CANDLE)) e.setValue(st.setValue(CandleBlock.LIT, true));
                else if (st.is(Blocks.SPAWNER)) e.setValue(Blocks.BARREL.defaultBlockState());
                else if (st.is(Blocks.MOSSY_COBBLESTONE)) e.setValue(Blocks.COBBLESTONE.defaultBlockState());
                else if (st.is(Blocks.MOSSY_STONE_BRICKS)) e.setValue(Blocks.STONE_BRICKS.defaultBlockState());
                else if (st.is(Blocks.VINE)) e.setValue(AIR);
            }
        }
        ground.removeIf(g -> g[2] == 4 || g[2] == 2);
    }

    /** A house: log corners, cobble footing, plank walls, a gable roof of stairs - fallen in - and a chimney. The big one has an upper floor. */
    private void cottage(RandomSource r, boolean big) {
        int hw = big ? 4 : 2 + r.nextInt(2), hd = big ? 3 + r.nextInt(2) : 2 + r.nextInt(2);
        int floors = big ? 2 : 1;
        int wallTop = 3 * floors + (floors - 1);
        boolean dry = style == STYLE_DRY;
        obj(0, 0, true);
        for (int x = -hw; x <= hw; x++) for (int z = -hd; z <= hd; z++) put(x, 0, z, floorBlock(r));
        fill(-hw + 1, 1, -hd + 1, hw - 1, wallTop, hd - 1, AIR);
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hd; z <= hd; z++) {
                boolean edge = Math.abs(x) == hw || Math.abs(z) == hd;
                if (!edge) continue;
                boolean corner = Math.abs(x) == hw && Math.abs(z) == hd;
                for (int y = 1; y <= wallTop; y++) {
                    BlockState s = corner ? log() : y == 1 ? stone(r) : dry ? bricks(r) : (big && y == 4 ? log() : planks());
                    put(x, y, z, s);
                }
            }
        }
        BlockState pane = dry ? AIR : Blocks.GLASS_PANE.defaultBlockState();
        boolean whole = intact();
        for (int f = 0; f < floors; f++) {
            int wy = 2 + f * 4;
            put(-hw, wy, 0, r.nextInt(3) == 0 && !whole ? AIR : pane);
            put(hw, wy, 0, r.nextInt(3) == 0 && !whole ? AIR : pane);
            put(0, wy, -hd, r.nextInt(3) == 0 && !whole ? AIR : pane);
            if (big) {
                put(-hw, wy, hd - 2, r.nextInt(3) == 0 && !whole ? AIR : pane);
                put(hw, wy, -hd + 2, r.nextInt(3) == 0 && !whole ? AIR : pane);
                if (f == 1) put(0, wy, hd, pane);
            }
        }
        put(0, 1, hd, AIR);
        put(0, 2, hd, AIR);
        if (r.nextInt(3) != 0 || whole) {
            put(0, 1, hd, door(Direction.SOUTH, false));
            put(0, 2, hd, door(Direction.SOUTH, true));
        }
        if (big) {
            // the upper floor and the ladder to it
            for (int x = -hw + 1; x <= hw - 1; x++) for (int z = -hd + 1; z <= hd - 1; z++) put(x, 4, z, planks());
            put(hw - 1, 4, -hd + 1, AIR);
            for (int y = 1; y <= 4; y++) put(hw - 1, y, -hd + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            put(hw - 2, 5, -hd + 1, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
            put(-hw + 1, 5, hd - 2, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            put(-hw + 1, 5, hd - 1, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
            put(0, 5, -hd + 1, Blocks.BOOKSHELF.defaultBlockState());
            put(1, 5, -hd + 1, Blocks.BOOKSHELF.defaultBlockState());
        }
        int roofBase = wallTop + 1;
        if (dry) {
            for (int x = -hw - 1; x <= hw + 1; x++) for (int z = -hd - 1; z <= hd + 1; z++) put(x, roofBase, z, Math.abs(x) <= hw && Math.abs(z) <= hd ? bricks(r) : stoneSlab());
            for (int x = -hw; x <= hw; x++) for (int z = -hd; z <= hd; z++) if ((Math.abs(x) == hw || Math.abs(z) == hd) && ((x + z) & 1) == 0) put(x, roofBase + 1, z, wall(r));
        } else {
            for (int k = 0; k <= hw + 1; k++) {
                int y = roofBase + k, xr = hw + 1 - k;
                for (int z = -hd - 1; z <= hd + 1; z++) {
                    if (xr == 0) {
                        put(0, y, z, slab(false));
                    } else {
                        put(-xr, y, z, stairs(Direction.EAST));
                        put(xr, y, z, stairs(Direction.WEST));
                        for (int x = -xr + 1; x <= xr - 1; x++) if (Math.abs(z) < hd) put(x, y, z, AIR);
                    }
                }
                if (xr > 0) for (int x = -xr + 1; x <= xr - 1; x++) {
                    put(x, y, -hd, planks());
                    put(x, y, hd, planks());
                }
            }
            // the chimney on the back gable, a cold hearth under it
            int chx = hw - 1;
            for (int y = 1; y <= roofBase + 2; y++) put(chx, y, -hd, y <= 2 ? stone(r) : bricks(r));
            put(chx, roofBase + 3, -hd, brickWall());
            put(chx, 1, -hd + 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
            put(chx, 2, -hd + 1, AIR);
        }
        // furniture
        put(0, 1, -hd + 1, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        put(-hw + 1, 1, -hd + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        if (!big) put(hw - 1, 1, hd - 1, r.nextInt(2) == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
        if (r.nextInt(2) == 0 && !big) {
            put(-hw + 1, 1, hd - 2, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            put(-hw + 1, 1, hd - 1, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        }
        if (r.nextInt(2) == 0) {
            // a table (a post with a plate on it) and a chair
            put(-1, 1, 0, stairs(Direction.EAST));
            put(0, 1, 0, fence());
            put(0, 2, 0, style == STYLE_COLD ? Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState() : style == STYLE_DRY ? Blocks.ACACIA_PRESSURE_PLATE.defaultBlockState() : Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
        if (!dry) put(0, roofBase + hw, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        if (r.nextInt(3) == 0) put(0, 1, hd + 1, Blocks.FLOWER_POT.defaultBlockState());
        if (r.nextInt(2) == 0) {
            put(-hw - 1, 1, hd - 1, Blocks.COMPOSTER.defaultBlockState());
        }
        for (int z = hd + 1; z <= hd + ((variant & V_HAMLET) != 0 ? 12 : 4); z++) {
            ground.add(new int[]{0, z, 0});
            if (r.nextInt(3) == 0) ground.add(new int[]{r.nextInt(2) == 0 ? -1 : 1, z, 0});
        }
        weather(r, 1, wallTop, roofBase, roofBase + hw + 1);
    }

    /** The watchtower: a round-ish shell, floors and a ladder inside, a chest on top; the top has come down on one side. */
    private void tower(RandomSource r) {
        int h = 9 + r.nextInt(5);
        int fallen = r.nextInt(4);
        obj(0, 0, true);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                if (corner) continue;
                boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2;
                put(x, 0, z, shell ? stone(r) : floorBlock(r));
                for (int y = 1; y <= h + 1; y++) {
                    if (!shell) {
                        put(x, y, z, AIR);
                        continue;
                    }
                    put(x, y, z, y <= 2 ? stone(r) : bricks(r));
                }
                if (shell && ((x + z) & 1) == 0) put(x, h + 2, z, bricks(r));
            }
        }
        put(0, 1, 2, AIR);
        put(0, 2, 2, AIR);
        for (int y = 4; y <= h - 3; y += 4) {
            put(2, y, 0, AIR);
            put(-2, y, 0, AIR);
            put(0, y, -2, AIR);
        }
        for (int y = 4; y < h; y += 4) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!(x == 0 && z == -1)) put(x, y, z, planks());
        }
        for (int y = 1; y <= h; y++) put(0, y, -1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!(x == 0 && z == -1)) put(x, h, z, planks());
        put(1, h + 1, 1, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        put(-1, h + 1, 1, Blocks.LANTERN.defaultBlockState());
        put(0, h + 1, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        if (r.nextInt(10) < 4) put(-1, 5, 1, Blocks.SPAWNER.defaultBlockState());
        else put(-1, 5, 1, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        put(1, 5, -1, Blocks.BARREL.defaultBlockState());
        int[] sx = {2, -2, 0, 0}, sz = {0, 0, 2, -2};
        if (intact()) {
            weather(r, 3, h + 2, 99, 99);
            return;
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            boolean shell = (Math.abs(x) == 2 || Math.abs(z) == 2) && !(Math.abs(x) == 2 && Math.abs(z) == 2);
            if (!shell) continue;
            int side = x == 2 ? 0 : x == -2 ? 1 : z == 2 ? 2 : 3;
            double near = side == fallen ? 1.0 : (Math.abs(x - sx[fallen]) + Math.abs(z - sz[fallen])) <= 3 ? 0.5 : 0.1;
            int top = h + 2 - (int) Math.round(near * (3 + decay * 6) * (0.6 + r.nextFloat() * 0.6));
            for (int y = top + 1; y <= h + 2; y++) if (at(x, y, z) != null && !at(x, y, z).isAir()) put(x, y, z, AIR);
        }
        // rubble of the fallen side around the foot
        for (int i = 0; i < 6 + (int) (decay * 8); i++) {
            int gx = sx[fallen] * 2 + r.nextInt(5) - 2 + (sx[fallen] != 0 ? sx[fallen] : 0), gz = sz[fallen] * 2 + r.nextInt(5) - 2 + (sz[fallen] != 0 ? sz[fallen] : 0);
            if (Math.abs(gx) <= 2 && Math.abs(gz) <= 2) continue;
            ground.add(new int[]{gx, gz, 4});
        }
        weather(r, 3, h + 2, 99, 99);
    }

    /** The well: a ring of stone, water down a shaft, and at the bottom a chest someone dropped a letter into. */
    private void well(RandomSource r) {
        int depth = 8;
        obj(0, 0, true);
        for (int y = -depth; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) put(0, y, 0, y == -depth ? LETTER_CHEST : y == 1 ? AIR : Blocks.WATER.defaultBlockState());
                else put(x, y, z, y >= 0 ? stone(r) : (r.nextInt(4) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()));
            }
        }
        put(0, -depth - 1, 0, Blocks.COBBLESTONE.defaultBlockState());
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
            if (Math.abs(x) <= 2 && Math.abs(z) <= 2) put(x, 0, z, r.nextInt(3) == 0 ? Blocks.GRAVEL.defaultBlockState() : stone(r));
            else if (r.nextInt(2) == 0) ground.add(new int[]{x, z, 0});
        }
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            put(sx, 2, sz, fence());
            put(sx, 3, sz, fence());
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) put(x, 4, z, stoneSlab());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) put(x, 5, z, stoneSlab());
        put(0, 3, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(0, 2, 0, AIR);
        put(1, 2, 0, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        if ((variant & V_HAMLET) != 0) {
            // the hamlet's square: a wide worn patch, lantern posts, benches, a notice post with a lantern, a cart's remains
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                if (Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;
                if (x * x + z * z <= 64 && r.nextInt(100) < 60) ground.add(new int[]{x, z, 0});
            }
            for (int[] p : new int[][]{{-6, -6}, {6, 6}, {-6, 6}, {6, -6}}) {
                obj(p[0], p[1], false);
                put(p[0], 1, p[1], fence());
                put(p[0], 2, p[1], fence());
                put(p[0], 3, p[1], r.nextInt(4) == 0 ? AIR : Blocks.LANTERN.defaultBlockState());
            }
            obj(0, 6, false);
            put(-1, 1, 6, stairs(Direction.NORTH));
            put(0, 1, 6, stairs(Direction.NORTH));
            put(1, 1, 6, stairs(Direction.NORTH));
            obj(0, -6, false);
            put(-1, 1, -6, stairs(Direction.SOUTH));
            put(0, 1, -6, stairs(Direction.SOUTH));
            put(1, 1, -6, stairs(Direction.SOUTH));
            // a broken cart: two wheels of trapdoors, a bed of planks, a shaft
            obj(6, 0, false);
            put(6, 1, 0, planks());
            put(6, 1, 1, planks());
            put(5, 1, 0, trapdoor(Direction.WEST, true));
            put(7, 1, 1, trapdoor(Direction.EAST, true));
            put(6, 1, -1, fence());
            put(6, 2, 0, Blocks.HAY_BLOCK.defaultBlockState());
            put(6, 2, 1, Blocks.BARREL.defaultBlockState());
        }
        weather(r, 2, 3, 4, 5);
    }

    /** The graveyard: a fenced plot of graves that each stand on their own ground, and a crypt with a room under it. */
    private void graveyard(RandomSource r) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            if (Math.abs(x) != 6 && Math.abs(z) != 6) continue;
            if (x == 0 && z == 6) {
                obj(0, 6, false);
                put(0, 1, 6, gate(Direction.SOUTH));
                continue;
            }
            if (r.nextFloat() < decay * 0.35F) continue;
            obj(x, z, false);
            put(x, 1, z, fence());
            if ((x == -6 || x == 6) && (z == -6 || z == 6)) {
                put(x, 2, z, fence());
                put(x, 3, z, r.nextInt(2) == 0 ? Blocks.SOUL_LANTERN.defaultBlockState() : AIR);
            }
        }
        int[] cols = {-5, -3, 3, 5};
        int[] rows = {-5, -1, 3};
        for (int gx : cols) for (int gz : rows) grave(r, gx, gz);
        for (int gx : new int[]{-1, 1}) for (int gz : new int[]{-1, 3}) grave(r, gx, gz);
        // the crypt, flat, at the back
        obj(0, -4, true);
        for (int x = -2; x <= 2; x++) for (int z = -6; z <= -2; z++) {
            boolean shell = Math.abs(x) == 2 || z == -6 || z == -2;
            put(x, 0, z, shell ? bricks(r) : Blocks.STONE_BRICKS.defaultBlockState());
            for (int y = 1; y <= 3; y++) put(x, y, z, shell ? bricks(r) : AIR);
            put(x, 4, z, shell ? bricks(r) : brickSlab(true));
        }
        for (int x = -3; x <= 3; x++) for (int z = -7; z <= -1; z++) {
            if (Math.abs(x) == 3 || z == -7 || z == -1) put(x, 4, z, stoneStairs(x == 3 ? Direction.WEST : x == -3 ? Direction.EAST : z == -7 ? Direction.SOUTH : Direction.NORTH, false));
        }
        put(0, 5, -4, brickWall());
        put(0, 6, -4, Blocks.SOUL_LANTERN.defaultBlockState());
        put(0, 1, -2, AIR);
        put(0, 2, -2, r.nextInt(2) == 0 ? Blocks.COBWEB.defaultBlockState() : AIR);
        put(-2, 2, -4, Blocks.IRON_BARS.defaultBlockState());
        put(2, 2, -4, Blocks.IRON_BARS.defaultBlockState());
        put(-1, 3, -5, Blocks.COBWEB.defaultBlockState());
        put(0, 3, -5, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(-1, 1, -5, candles(3, false));
        put(1, 1, -5, candles(2, false));
        put(0, 0, -4, AIR);
        put(0, -1, -4, AIR);
        for (int x = -3; x <= 3; x++) for (int y = -6; y <= -1; y++) for (int z = -7; z <= -1; z++) {
            boolean shell = Math.abs(x) == 3 || z == -7 || z == -1 || y == -6 || y == -1;
            if (y == -1 && x == 0 && z == -4) continue;
            put(x, y, z, shell ? bricks(r) : AIR);
        }
        put(0, -5, -4, Blocks.SPAWNER.defaultBlockState());
        put(2, -5, -6, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        put(-2, -5, -6, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        put(-2, -2, -2, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(2, -2, -6, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(-2, -5, -2, Blocks.COBWEB.defaultBlockState());
        put(2, -3, -2, Blocks.COBWEB.defaultBlockState());
        put(0, -5, -2, Blocks.SKELETON_SKULL.defaultBlockState());
        put(-2, -5, -4, Blocks.BONE_BLOCK.defaultBlockState());
        put(2, -5, -3, Blocks.CHAIN.defaultBlockState());
        for (int z = 7; z <= 10; z++) ground.add(new int[]{0, z, 0});
        weather(r, 1, 4, 99, 99);
    }

    private void grave(RandomSource r, int x, int z) {
        obj(x, z, false);
        put(x, 1, z, r.nextInt(5) == 0 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : r.nextInt(6) == 0 ? Blocks.SKELETON_SKULL.defaultBlockState() : wall(r));
        ground.add(new int[]{x, z + 1, 1});
        ground.add(new int[]{x, z + 2, 1});
        if (r.nextInt(3) == 0) ground.add(new int[]{x, z + 2, 2});
    }

    /** The camp: three tents around a cold fire, a bench of logs, a cauldron, a lantern on a post - each tent on its own ground. */
    private void camp(RandomSource r) {
        obj(0, 0, false);
        put(0, 1, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, r.nextInt(6) == 0));
        for (int x = -1; x <= 1; x++) put(x, 1, -3, log().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        put(-3, 1, -2, Blocks.CAULDRON.defaultBlockState());
        put(3, 1, -2, Blocks.CRAFTING_TABLE.defaultBlockState());
        put(2, 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
        obj(0, -5, false);
        put(0, 1, -5, fence());
        put(0, 2, -5, fence());
        put(0, 3, -5, Blocks.LANTERN.defaultBlockState());
        // tent A: front at z = 3, back at z = 6
        obj(0, 4, false);
        BlockState wool = woolTent(r);
        for (int z = 3; z <= 6; z++) {
            put(-1, 1, z, wool);
            put(1, 1, z, wool);
            put(0, 2, z, wool);
            put(0, 1, z, AIR);
        }
        put(0, 1, 6, wool);
        put(0, 1, 5, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        // tent B: front at x = -3, back at x = -6
        obj(-4, 0, false);
        wool = woolTent(r);
        for (int x = -3; x >= -6; x--) {
            put(x, 1, -1, wool);
            put(x, 1, 1, wool);
            put(x, 2, 0, wool);
            put(x, 1, 0, AIR);
        }
        put(-5, 1, 0, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.FOOT));
        put(-6, 1, 0, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.HEAD));
        put(-7, 1, 0, wool);
        // tent C: front at x = 3, back at x = 6
        obj(4, 0, false);
        wool = woolTent(r);
        for (int x = 3; x <= 6; x++) {
            put(x, 1, -1, wool);
            put(x, 1, 1, wool);
            put(x, 2, 0, wool);
            put(x, 1, 0, AIR);
        }
        put(6, 1, 0, wool);
        put(5, 1, 0, Blocks.BARREL.defaultBlockState());
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (x * x + z * z <= 16 && r.nextInt(100) < 45) ground.add(new int[]{x, z, 0});
        weather(r, 1, 2, 99, 99);
    }

    /** The chapel: a stone hall with pointed windows, pews, an altar table with candles, a bell tower stump; the roof is half gone. */
    private void chapel(RandomSource r) {
        int hw = 3, hd = 6;
        obj(0, 0, true);
        for (int x = -hw; x <= hw; x++) for (int z = -hd; z <= hd; z++) put(x, 0, z, Math.abs(x) == hw || Math.abs(z) == hd ? bricks(r) : (r.nextInt(3) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()));
        fill(-hw + 1, 1, -hd + 1, hw - 1, 6, hd - 1, AIR);
        for (int x = -hw; x <= hw; x++) for (int z = -hd; z <= hd; z++) {
            boolean edge = Math.abs(x) == hw || Math.abs(z) == hd;
            if (!edge) continue;
            for (int y = 1; y <= 5; y++) put(x, y, z, bricks(r));
            // buttresses every three blocks along the long walls
            if (Math.abs(x) == hw && Math.abs(z) < hd && (z + hd) % 3 == 0) {
                int bx = x > 0 ? x + 1 : x - 1;
                put(bx, 1, z, bricks(r));
                put(bx, 2, z, stoneStairs(x > 0 ? Direction.EAST : Direction.WEST, false));
            }
        }
        // pointed windows: a pane with an upside-down stair above, on the long walls
        for (int z = -hd + 2; z <= hd - 2; z += 3) {
            for (int x : new int[]{-hw, hw}) {
                put(x, 2, z, Blocks.GLASS_PANE.defaultBlockState());
                put(x, 3, z, r.nextInt(3) == 0 && !intact() ? AIR : Blocks.GLASS_PANE.defaultBlockState());
                put(x, 4, z, stoneStairs(x > 0 ? Direction.WEST : Direction.EAST, true));
            }
        }
        // the door and a rose window on the front, the apse window at the back
        put(0, 1, hd, AIR);
        put(0, 2, hd, AIR);
        put(0, 3, hd, AIR);
        put(0, 4, hd, Blocks.GLASS_PANE.defaultBlockState());
        put(0, 3, -hd, Blocks.GLASS_PANE.defaultBlockState());
        put(0, 2, -hd, Blocks.GLASS_PANE.defaultBlockState());
        // the roof: a steep gable of stone-brick stairs
        for (int k = 0; k <= hw + 1; k++) {
            int y = 6 + k, xr = hw + 1 - k;
            for (int z = -hd - 1; z <= hd + 1; z++) {
                if (xr == 0) put(0, y, z, brickSlab(false));
                else {
                    put(-xr, y, z, stoneStairs(Direction.EAST, false));
                    put(xr, y, z, stoneStairs(Direction.WEST, false));
                    for (int x = -xr + 1; x <= xr - 1; x++) if (Math.abs(z) < hd) put(x, y, z, AIR);
                }
            }
            if (xr > 0) for (int x = -xr + 1; x <= xr - 1; x++) {
                put(x, y, -hd, bricks(r));
                put(x, y, hd, bricks(r));
            }
        }
        // the bell tower over the front: a stump with a bell
        for (int x = -1; x <= 1; x++) for (int z = hd - 2; z <= hd; z++) {
            boolean shell = Math.abs(x) == 1 || z == hd - 2 || z == hd;
            for (int y = 6; y <= 11; y++) put(x, y, z, shell ? (y >= 9 && r.nextFloat() < decay * 0.5F ? AIR : bricks(r)) : AIR);
        }
        put(0, 10, hd - 1, planks());
        put(0, 9, hd - 1, Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        put(0, 12, hd - 1, brickWall());
        // inside: pews (stairs) in two rows, an aisle, the altar table with candles, a lectern
        for (int z = -hd + 3; z <= hd - 2; z += 2) {
            put(-2, 1, z, stairs(Direction.NORTH));
            put(2, 1, z, stairs(Direction.NORTH));
            if (r.nextFloat() < decay * 0.3F) put(r.nextInt(2) == 0 ? -2 : 2, 1, z, AIR);
        }
        put(-1, 1, -hd + 2, brickSlab(false));
        put(0, 1, -hd + 2, brickSlab(false));
        put(1, 1, -hd + 2, brickSlab(false));
        put(0, 1, -hd + 1, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        put(-1, 2, -hd + 2, candles(3, false));
        put(1, 2, -hd + 2, candles(2, false));
        put(0, 2, -hd + 2, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        // chandeliers on chains from the ridge
        for (int z : new int[]{0, hd - 4, -hd + 3}) {
            put(0, 4, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int y = 5; y <= 6 + hw; y++) put(0, y, z, Blocks.CHAIN.defaultBlockState());
        }
        put(-2, 4, -hd + 1, Blocks.COBWEB.defaultBlockState());
        put(2, 4, hd - 1, Blocks.COBWEB.defaultBlockState());
        for (int z = hd + 1; z <= hd + ((variant & V_HAMLET) != 0 ? 10 : 4); z++) {
            ground.add(new int[]{0, z, 0});
            ground.add(new int[]{r.nextInt(2) == 0 ? -1 : 1, z, 0});
        }
        weather(r, 2, 5, 6, 10);
    }

    /** The market: three or four stalls with the awnings half gone, barrels, hay, crates, a scale and a butcher's block. */
    private void market(RandomSource r) {
        int[][] stalls = {{-5, -3}, {4, -3}, {-5, 4}, {4, 4}};
        int n = 3 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            int sx = stalls[i][0], sz = stalls[i][1];
            obj(sx, sz, false);
            BlockState wool = woolTent(r), wool2 = i % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : wool;
            // posts
            for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) {
                put(sx + dx, 1, sz + dz, fence());
                put(sx + dx, 2, sz + dz, fence());
            }
            // the counter along the front
            for (int dx = -1; dx <= 1; dx++) put(sx + dx, 1, sz + 1, dx == 0 ? slab(false) : planks());
            // the awning: striped, with pieces missing
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (r.nextFloat() < decay * 0.4F) continue;
                put(sx + dx, 3, sz + dz, (dx + dz) % 2 == 0 ? wool : wool2);
            }
            // wares
            switch (i) {
                case 0 -> {
                    put(sx, 1, sz, Blocks.HAY_BLOCK.defaultBlockState());
                    put(sx - 1, 1, sz, Blocks.BARREL.defaultBlockState());
                    put(sx, 2, sz + 1, Blocks.FLOWER_POT.defaultBlockState());
                }
                case 1 -> {
                    put(sx, 1, sz, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    put(sx + 1, 1, sz, Blocks.BARREL.defaultBlockState());
                    put(sx, 2, sz + 1, Blocks.CAKE.defaultBlockState());
                }
                case 2 -> {
                    put(sx, 1, sz, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    put(sx - 1, 1, sz, Blocks.LOOM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    put(sx + 1, 2, sz + 1, Blocks.LANTERN.defaultBlockState());
                }
                default -> {
                    put(sx, 1, sz, Blocks.ANVIL.defaultBlockState());
                    put(sx + 1, 1, sz, Blocks.GRINDSTONE.defaultBlockState());
                    put(sx - 1, 2, sz + 1, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                }
            }
        }
        if (n == 3) {
            obj(4, 4, false);
            put(4, 1, 4, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        }
        // crates and a cart between the stalls, the trodden square
        obj(0, 0, false);
        put(0, 1, 0, Blocks.BARREL.defaultBlockState());
        put(1, 1, 0, Blocks.HAY_BLOCK.defaultBlockState());
        put(0, 2, 0, Blocks.HAY_BLOCK.defaultBlockState());
        put(-1, 1, 1, Blocks.COMPOSTER.defaultBlockState());
        for (int x = -7; x <= 7; x++) for (int z = -5; z <= 6; z++) if (r.nextInt(100) < 55) ground.add(new int[]{x, z, 0});
        weather(r, 1, 3, 99, 99);
    }

    /** The farm: a fenced plot of dry furrows with dead crops, a water channel gone stale, a scarecrow, a shed with tools. */
    private void farm(RandomSource r) {
        int hw = 5, hd = 4;
        for (int x = -hw; x <= hw; x++) for (int z = -hd; z <= hd; z++) {
            boolean edge = Math.abs(x) == hw || Math.abs(z) == hd;
            if (edge) {
                if (x == hw && z == 0) continue; // the gap where the gate was
                if (r.nextFloat() < decay * 0.4F) continue;
                obj(x, z, false);
                put(x, 1, z, fence());
            } else if (z == 0) {
                ground.add(new int[]{x, z, 5}); // the channel
            } else {
                ground.add(new int[]{x, z, 3}); // furrows
            }
        }
        obj(0, -2, false);
        put(0, 1, -2, fence());
        put(0, 2, -2, fence());
        put(0, 3, -2, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        put(-1, 2, -2, fence());
        put(1, 2, -2, fence());
        // the shed beside the plot
        obj(-hw - 3, 0, true);
        for (int x = -hw - 4; x <= -hw - 2; x++) for (int z = -1; z <= 1; z++) {
            put(x, 0, z, floorBlock(r));
            for (int y = 1; y <= 2; y++) {
                boolean shell = x == -hw - 4 || z == -1 || z == 1;
                put(x, y, z, shell ? planks() : AIR);
            }
            put(x, 3, z, slab(false));
        }
        put(-hw - 3, 1, 0, Blocks.COMPOSTER.defaultBlockState());
        put(-hw - 3, 1, -1, Blocks.BARREL.defaultBlockState());
        put(-hw - 2, 1, 0, AIR);
        put(-hw - 2, 2, 0, AIR);
        put(-hw - 4, 1, 0, LETTER_CHEST.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        weather(r, 1, 2, 3, 3);
    }

    /** The palisade's radius and two gate angles for a seed - the structure uses the same to put the towers at the gates. */
    public static double[] palisadeGates(long seed) {
        RandomSource r = RandomSource.create(seed);
        int radius = 33 + r.nextInt(4);
        double gateA = r.nextDouble() * Math.PI * 2, gateB = gateA + Math.PI + (r.nextDouble() - 0.5);
        return new double[]{radius, gateA, gateB};
    }

    /** The palisade: a ring of sharpened logs on a footing of cobble, gaps where it fell, two gates with lantern posts. */
    private void palisade(RandomSource r) {
        double[] gates = palisadeGates(seed);
        int radius = (int) gates[0];
        double gateA = gates[1], gateB = gates[2];
        r.nextInt(4); r.nextDouble(); r.nextDouble(); // keep the stream in step with palisadeGates
        for (int i = 0; i < 360; i += 1) {
            double a = Math.toRadians(i);
            int x = (int) Math.round(Math.cos(a) * radius), z = (int) Math.round(Math.sin(a) * radius);
            double dA = Math.abs(Math.atan2(Math.sin(a - gateA), Math.cos(a - gateA))), dB = Math.abs(Math.atan2(Math.sin(a - gateB), Math.cos(a - gateB)));
            if (dA < 0.06 || dB < 0.06) continue; // the gates
            // fallen stretches
            long h = (long) (i / 7) * 0x9E3779B97F4A7C15L + seed;
            h ^= h >>> 29;
            if (((h >>> 20) & 0xFF) < decay * 90) continue;
            obj(x, z, false);
            BlockState post = log().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            int height = 3 + ((int) ((h >>> 12) & 3) == 0 ? 0 : 1);
            put(x, 0, z, stone(r));
            for (int y = 1; y <= height; y++) put(x, y, z, post);
            put(x, height + 1, z, fence());
        }
        for (double g : new double[]{gateA, gateB}) {
            for (int side = -1; side <= 1; side += 2) {
                double a = g + side * 0.075;
                int x = (int) Math.round(Math.cos(a) * radius), z = (int) Math.round(Math.sin(a) * radius);
                obj(x, z, false);
                for (int y = 1; y <= 5; y++) put(x, y, z, log().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                put(x, 6, z, Blocks.LANTERN.defaultBlockState());
            }
            for (int d = 1; d <= 5; d++) {
                int x = (int) Math.round(Math.cos(g) * (radius - d)), z = (int) Math.round(Math.sin(g) * (radius - d));
                ground.add(new int[]{x, z, 0});
                ground.add(new int[]{x + 1, z, 0});
            }
        }
    }

    /**
     * Age: walls crumble more the higher they are, roofs fall in in patches, and what is left goes
     * green - vines outside, moss and grass on the floor, cobwebs in the corners.
     */
    private void weather(RandomSource r, int wallLo, int wallHi, int roofLo, int roofHi) {
        if (intact()) return;
        for (Obj o : objects) {
            cur = o;
            List<Long> keys = new ArrayList<>(o.blocks.keySet());
            for (long k : keys) {
                BlockState s = o.blocks.get(k);
                if (s == null || s.isAir()) continue;
                int x = kx(k), y = ky(k), z = kz(k);
                boolean marker = s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.CHEST) || s.is(Blocks.SPAWNER) || s.is(Blocks.LADDER) || s.is(Blocks.WATER) || s.is(Blocks.BELL);
                if (marker) continue;
                boolean roof = y >= roofLo && y <= roofHi;
                boolean wallish = y >= wallLo && y <= wallHi && (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
                        || s.is(BlockTags.STONE_BRICKS) || s.is(Blocks.SANDSTONE) || s.is(Blocks.CUT_SANDSTONE) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.CHISELED_SANDSTONE) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.WOOL));
                if (roof && (s.getBlock() instanceof StairBlock || s.getBlock() instanceof SlabBlock || s.is(BlockTags.PLANKS) || s.is(BlockTags.STONE_BRICKS))) {
                    int px = Math.floorDiv(x + 1, 3), pz = Math.floorDiv(z + 1, 3);
                    long ph = (px * 7349L + pz * 1907L + seed) * 0x9E3779B97F4A7C15L;
                    boolean patch = ((ph >>> 40) % 100) < decay * 55;
                    if (patch || r.nextFloat() < decay * 0.08F) o.blocks.put(k, AIR);
                    continue;
                }
                if (wallish) {
                    float chance = Math.min(0.16F, decay * (0.02F + 0.03F * (y - wallLo)));
                    if (r.nextFloat() < chance) {
                        o.blocks.put(k, AIR);
                        continue;
                    }
                    if (s.is(Blocks.COBBLESTONE) && r.nextFloat() < decay * 0.5F) o.blocks.put(k, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                    if (s.is(Blocks.STONE_BRICKS) && r.nextFloat() < decay * 0.5F) o.blocks.put(k, r.nextInt(2) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                }
            }
            if (style == STYLE_DRY) continue;
            keys = new ArrayList<>(o.blocks.keySet());
            for (long k : keys) {
                BlockState s = o.blocks.get(k);
                if (s == null || s.isAir()) continue;
                int x = kx(k), y = ky(k), z = kz(k);
                if (y >= 1 && (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(BlockTags.STONE_BRICKS))) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int nx = x + d.getStepX(), nz = z + d.getStepZ();
                        if (at(nx, y, nz) == null && r.nextFloat() < decay * 0.22F) put(nx, y, nz, vine(d.getOpposite()));
                    }
                }
                if (y == 0 && (s.is(BlockTags.PLANKS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.POLISHED_ANDESITE))) {
                    BlockState above = at(x, 1, z);
                    if (above != null && above.isAir()) {
                        float f = r.nextFloat();
                        if (f < decay * 0.12F) put(x, 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                        else if (f < decay * 0.22F) put(x, 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                        else if (f < decay * 0.25F) put(x, 1, z, Blocks.COBWEB.defaultBlockState());
                    }
                }
            }
        }
    }

    /** Nothing hangs from nothing: chains and hanging lanterns whose support is gone go too (top down, so a chain collapses). */
    private void pruneHanging() {
        for (Obj o : objects) {
            List<Long> keys = new ArrayList<>(o.blocks.keySet());
            keys.sort((k1, k2) -> Integer.compare(ky(k2), ky(k1)));
            for (long k : keys) {
                BlockState s = o.blocks.get(k);
                if (s == null || s.isAir()) continue;
                boolean hanging = s.is(Blocks.CHAIN) || ((s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)) && s.getValue(LanternBlock.HANGING));
                if (!hanging) continue;
                BlockState above = o.blocks.get(key(kx(k), ky(k) + 1, kz(k)));
                if (above == null || above.isAir()) o.blocks.put(k, AIR);
            }
        }
    }

    private static BlockState vine(Direction attached) {
        BlockState v = Blocks.VINE.defaultBlockState();
        return switch (attached) {
            case NORTH -> v.setValue(VineBlock.NORTH, true);
            case SOUTH -> v.setValue(VineBlock.SOUTH, true);
            case EAST -> v.setValue(VineBlock.EAST, true);
            default -> v.setValue(VineBlock.WEST, true);
        };
    }

    // ---------------------------------------------------------------- placing

    private int worldX(int lx, int lz) {
        return switch (rot) {
            case CLOCKWISE_90 -> cx - lz;
            case CLOCKWISE_180 -> cx - lx;
            case COUNTERCLOCKWISE_90 -> cx + lz;
            default -> cx + lx;
        };
    }

    private int worldZ(int lx, int lz) {
        return switch (rot) {
            case CLOCKWISE_90 -> cz + lx;
            case CLOCKWISE_180 -> cz - lz;
            case COUNTERCLOCKWISE_90 -> cz - lx;
            default -> cz + lz;
        };
    }

    private static boolean needsShapeUpdate(BlockState s) {
        Block b = s.getBlock();
        return b instanceof StairBlock || b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof FenceGateBlock || b instanceof VineBlock || b instanceof DoorBlock
                || b instanceof net.minecraft.world.level.block.ChainBlock || b instanceof TrapDoorBlock;
    }

    /** The surface block's y in a column of the region (clamped into the chunk box, so far columns never leave the region). */
    private static int surface(WorldGenLevel level, BoundingBox box, int x, int z) {
        int sx = Math.max(box.minX(), Math.min(box.maxX(), x)), sz = Math.max(box.minZ(), Math.min(box.maxZ(), z));
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, sx, sz) - 1;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (objects == null) build();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(seed ^ (chunkPos.toLong() * 31L));
        // 1. the ground marks
        for (int[] g : ground) {
            int x = worldX(g[0], g[1]), z = worldZ(g[0], g[1]);
            if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) continue;
            int sy = intact() ? cy : surface(level, box, x, z);
            if (Math.abs(sy - cy) > 7) continue;
            pos.set(x, sy, z);
            BlockState under = level.getBlockState(pos);
            boolean soil = under.is(BlockTags.DIRT) || under.is(Blocks.GRASS_BLOCK) || under.is(BlockTags.SAND) || under.is(Blocks.SNOW_BLOCK) || under.is(Blocks.GRAVEL);
            if (!soil) continue;
            switch (g[2]) {
                case 0 -> {
                    BlockState path = under.is(BlockTags.SAND) ? (r.nextInt(3) == 0 ? Blocks.SANDSTONE.defaultBlockState() : null)
                            : r.nextInt(100) < 60 ? Blocks.DIRT_PATH.defaultBlockState() : r.nextInt(2) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    if (path != null) level.setBlock(pos, path, 2);
                    clearPlant(level, pos.above());
                }
                case 1 -> {
                    level.setBlock(pos, under.is(BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState() : r.nextInt(3) == 0 ? Blocks.PODZOL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                    clearPlant(level, pos.above());
                }
                case 2 -> {
                    pos.move(0, 1, 0);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, r.nextInt(3) == 0 ? Blocks.DEAD_BUSH.defaultBlockState() : r.nextInt(2) == 0 ? Blocks.POPPY.defaultBlockState() : Blocks.OXEYE_DAISY.defaultBlockState(), 2);
                }
                case 3 -> {
                    if (intact()) {
                        // a tended field: wet furrows, the crop standing
                        level.setBlock(pos, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
                        pos.move(0, 1, 0);
                        BlockState above = level.getBlockState(pos);
                        if (above.isAir() || above.is(BlockTags.REPLACEABLE_BY_TREES)) {
                            int c = (seed & 1) == 0 ? 0 : (int) ((seed >>> 8) % 3);
                            BlockState crop = switch (c) {
                                case 1 -> Blocks.CARROTS.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 4 + r.nextInt(4));
                                case 2 -> Blocks.POTATOES.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 4 + r.nextInt(4));
                                default -> Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 4 + r.nextInt(4));
                            };
                            level.setBlock(pos, r.nextInt(12) == 0 ? Blocks.PUMPKIN.defaultBlockState() : crop, 2);
                        }
                        break;
                    }
                    // a dry furrow with what is left of the crop
                    level.setBlock(pos, r.nextInt(4) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.FARMLAND.defaultBlockState(), 2);
                    pos.move(0, 1, 0);
                    BlockState above = level.getBlockState(pos);
                    if (above.isAir() || above.is(BlockTags.REPLACEABLE_BY_TREES)) {
                        int c = r.nextInt(10);
                        level.setBlock(pos, c < 4 ? Blocks.DEAD_BUSH.defaultBlockState() : c < 6 ? Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 2 + r.nextInt(4)) : c < 7 ? Blocks.PUMPKIN.defaultBlockState() : AIR, 2);
                    }
                }
                case 4 -> {
                    // fallen masonry
                    pos.move(0, 1, 0);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, r.nextInt(2) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : r.nextInt(2) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE_SLAB.defaultBlockState(), 2);
                }
                default -> {
                    // the channel: a trench of water
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                    clearPlant(level, pos.above());
                }
            }
        }
        // 2. the objects
        for (Obj o : objects) {
            int base = cy;
            if (!o.flat && !intact()) {
                int ax = worldX(o.ax, o.az), az = worldZ(o.ax, o.az);
                base = surface(level, box, ax, az);
                if (ax >= box.minX() && ax <= box.maxX() && az >= box.minZ() && az <= box.maxZ()) {
                    // nothing stands in water or over a drop: no post in the lake, no grave on the cliff
                    BlockState ground = level.getBlockState(pos.set(ax, base, az));
                    if (ground.is(Blocks.WATER) || ground.is(Blocks.LAVA) || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS)) continue;
                    if (level.getBlockState(pos.set(ax, base + 1, az)).is(Blocks.WATER)) continue;
                }
                if (Math.abs(base - cy) > 6) {
                    if (kind == Kind.PALISADE) continue;
                    base = cy;
                }
            } else if (o.maxX >= o.minX) {
                // clear the sky over a flat object's footprint (trees, hillocks), one block of margin
                for (int lx = o.minX - 1; lx <= o.maxX + 1; lx++) for (int lz = o.minZ - 1; lz <= o.maxZ + 1; lz++) {
                    int x = worldX(lx, lz), z = worldZ(lx, lz);
                    if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) continue;
                    boolean inside = lx >= o.minX && lx <= o.maxX && lz >= o.minZ && lz <= o.maxZ;
                    for (int y = cy + 1; y <= cy + o.maxY + 2 && y <= box.maxY(); y++) {
                        pos.set(x, y, z);
                        BlockState st = level.getBlockState(pos);
                        if (st.isAir()) continue;
                        if (inside || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.is(BlockTags.REPLACEABLE_BY_TREES)) level.setBlock(pos, AIR, 2);
                    }
                }
            }
            for (Map.Entry<Long, BlockState> e : o.blocks.entrySet()) {
                long k = e.getKey();
                int lx = kx(k), ly = ky(k), lz = kz(k);
                int x = worldX(lx, lz), z = worldZ(lx, lz);
                if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) continue;
                int y = base + ly;
                if (y < box.minY() || y > box.maxY()) continue;
                BlockState s = e.getValue();
                pos.set(x, y, z);
                if (s.isAir()) {
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, AIR, 2);
                    continue;
                }
                boolean letter = s.is(Blocks.TRAPPED_CHEST);
                if (letter) s = Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, s.getValue(HorizontalDirectionalBlock.FACING));
                if (rot != Rotation.NONE) s = s.rotate(rot);
                level.setBlock(pos, s, 2);
                if (needsShapeUpdate(s)) level.getChunk(pos).markPosForPostprocessing(pos.immutable());
                if (s.is(Blocks.CHEST)) {
                    RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), letter ? LETTER : intact() ? HOUSE : LOOT);
                } else if (s.is(Blocks.SPAWNER) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                    EntityType<?> type = kind == Kind.WATCHTOWER ? EntityType.SKELETON : r.nextInt(10) < 3 ? WakingWorld.STONE_THRALL.get() : EntityType.ZOMBIE;
                    spawner.setEntityId(type, random);
                }
                // the foundation: fill from under the object's lowest layer down to the ground
                if (ly == 0 || (!o.flat && ly == 1)) {
                    int sy = surface(level, box, x, z);
                    for (int fy = y - 1; fy > sy - 1 && fy > y - kind.down; fy--) {
                        pos.set(x, fy, z);
                        if (!level.getBlockState(pos).isSolid()) level.setBlock(pos, style == STYLE_DRY ? Blocks.SANDSTONE.defaultBlockState() : (random.nextInt(3) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState()), 2);
                    }
                }
            }
        }
    }

    private static void clearPlant(WorldGenLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.isAir() && (s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS))) level.setBlock(pos, AIR, 2);
    }
}
