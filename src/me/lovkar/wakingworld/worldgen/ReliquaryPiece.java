package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.StoneThrallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Void Reliquary: a spire on the End's outer islands, eight-sided, end stone brick ribbed
 * with obsidian, banded with purpur, lit with end rods - and hollow: a shaft down its middle
 * opens on the void itself, and the only way up is the ramp that winds round the shaft past two
 * galleries of endermites, shulkers and Hollow Thralls to the reliquary at the top, where the
 * Void Sigil waits in its chest on a bridge of crying obsidian over the drop. The Key of the Titan
 * turns nothing without it.
 */
public class ReliquaryPiece extends StructurePiece {
    public static final ResourceKey<LootTable> RELIC = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/reliquary"));
    public static final ResourceKey<LootTable> STORES = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/reliquary_stores"));
    static final int PLINTH = 8, TOP = 24, CROWN = 30, REACH = 10;
    static final double R_OUT = 5.5, R_IN = 4.5, SHAFT = 1.5;

    private final int cx, cy, cz;
    private final long seed;

    public ReliquaryPiece(BlockPos origin, long seed) {
        super(WakingStructures.RELIQUARY_PIECE.get(), 0, new BoundingBox(origin.getX() - REACH, origin.getY() - 20, origin.getZ() - REACH, origin.getX() + REACH, origin.getY() + CROWN + 10, origin.getZ() + REACH));
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.seed = seed;
    }

    public ReliquaryPiece(CompoundTag tag) {
        super(WakingStructures.RELIQUARY_PIECE.get(), tag);
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

    private int hash(int x, int y, int z) {
        long h = x * 341873128712L + y * 97531234567L + z * 132897987541L + seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h & 0xFF);
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState BRICK = Blocks.END_STONE_BRICKS.defaultBlockState();
    static final BlockState END = Blocks.END_STONE.defaultBlockState();
    static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    static final BlockState PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState();
    static final BlockState PURPUR_PILLAR = Blocks.PURPUR_PILLAR.defaultBlockState();
    static final BlockState ROD = Blocks.END_ROD.defaultBlockState();
    static final BlockState GLASS = Blocks.PURPLE_STAINED_GLASS_PANE.defaultBlockState();
    static final BlockState CANDLE = Blocks.PURPLE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);

    /** The octagon metric: the larger of the square and the diamond distances, so the walls are eight flats. */
    static double oct(int lx, int lz) {
        return Math.max(Math.max(Math.abs(lx), Math.abs(lz)), (Math.abs(lx) + Math.abs(lz)) * 0.7071 + 0.3);
    }

    static BlockState purpurStairs(Direction facing, boolean top) {
        return Blocks.PURPUR_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState brickStairs(Direction facing, boolean top) {
        return Blocks.END_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    private BlockState wall(int x, int y, int z) {
        int h = hash(x, y, z) % 100;
        return h < 84 ? BRICK : h < 94 ? END : PURPUR;
    }

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
        if (s.getBlock() instanceof StairBlock || s.getBlock() instanceof ChainBlock || s.is(Blocks.PURPLE_STAINED_GLASS_PANE) || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock) {
            level.getChunk(pos).markPosForPostprocessing(pos.immutable());
        }
    }

    private void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz) {
        double o = oct(dx, dz);
        double r = Math.sqrt(dx * dx + dz * dz);
        int m = Math.max(Math.abs(dx), Math.abs(dz));
        boolean shaft = r <= SHAFT;
        // the sky: chorus and stone above the plinth go
        if (m <= PLINTH) {
            for (int dy = 1; dy <= CROWN + 6; dy++) {
                pos.set(x, cy + dy, z);
                if (!level.getBlockState(pos).isAir()) level.setBlock(pos, AIR, 2);
            }
        }
        // the plinth: a square of end stone brick with a purpur border, tapering into the island below; the shaft goes right through
        if (m <= PLINTH) {
            if (shaft) {
                for (int dy = 0; dy >= -20; dy--) set(level, pos, x, dy, z, AIR);
            } else {
                set(level, pos, x, 0, z, m == PLINTH ? PURPUR : (m == PLINTH - 1 && ((dx + dz) & 1) == 0) ? Blocks.PURPUR_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP) : wall(x, 0, z));
                if (m == PLINTH - 1 && ((dx + dz) & 1) == 0) set(level, pos, x, 0, z, PURPUR);
                for (int d = 1; d <= 14; d++) {
                    double taper = PLINTH + 0.5 - d * 0.6;
                    if (m > taper) break;
                    pos.set(x, cy - d, z);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, d <= 2 ? BRICK : END, 2);
                }
                // the shaft's lining, lit
                if (r <= SHAFT + 1.0) {
                    for (int dy = 0; dy >= -12; dy--) set(level, pos, x, dy, z, dy % 4 == 0 && (Math.abs(dx) == 2 || Math.abs(dz) == 2) ? ROD : OBSIDIAN);
                }
            }
            // the plinth's edge: a low parapet of purpur slabs and end rods at the corners
            if (m == PLINTH) {
                set(level, pos, x, 1, z, Math.abs(dx) == PLINTH && Math.abs(dz) == PLINTH ? PURPUR_PILLAR : Blocks.PURPUR_SLAB.defaultBlockState());
                if (Math.abs(dx) == PLINTH && Math.abs(dz) == PLINTH) set(level, pos, x, 2, z, ROD);
            }
        }
        if (o > R_OUT + 1.0) return;
        // the outer ribs and the crown reach a block beyond the wall
        if (o > R_OUT) {
            rib(level, pos, x, z, dx, dz, o);
            return;
        }
        if (o > R_IN) {
            shell(level, pos, random, x, z, dx, dz);
            return;
        }
        interior(level, pos, random, x, z, dx, dz, r, o);
    }

    /** Obsidian ribs on the eight vertices, end rods on them at every band, the crown's outer step. */
    private void rib(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz, double o) {
        double a = Geo.angle(dx, dz);
        double m = a % 45;
        boolean vertex = m < 7 || 45 - m < 7;
        if (vertex && o <= R_OUT + 1.0) {
            for (int dy = 1; dy <= 6; dy++) set(level, pos, x, dy, z, OBSIDIAN);
            set(level, pos, x, 7, z, brickStairs(toward(dx, dz), true));
            for (int band : new int[]{8, 16, TOP}) set(level, pos, x, band + 1, z, ROD);
        }
        // the crown: purpur stairs stepping in from a ring one block out
        if (o <= R_OUT + 1.0) set(level, pos, x, CROWN, z, purpurStairs(toward(dx, dz), false));
    }

    static Direction toward(int lx, int lz) {
        return Math.abs(lx) >= Math.abs(lz) ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
    }

    private void shell(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz) {
        double a = Geo.angle(dx, dz);
        double m = a % 45;
        boolean flatMiddle = Math.abs(m - 22.5) < 6; // the middle of each of the eight flats
        boolean door = dz > 0 && Math.abs(dx) <= 1;    // the door on the south flat
        for (int dy = 1; dy <= TOP + 5; dy++) {
            BlockState s = wall(x, dy, z);
            if (dy == 8 || dy == 16 || dy == TOP) s = PURPUR;                       // the bands at every floor
            if (dy == 7 || dy == 15 || dy == TOP - 1) s = PURPUR_PILLAR;
            if (flatMiddle && (dy == 4 || dy == 5 || dy == 12 || dy == 13 || dy == 20 || dy == 21)) s = GLASS; // tall windows on every flat
            if (flatMiddle && (dy == 6 || dy == 14 || dy == 22)) s = brickStairs(toward(dx, dz), true);       // their arch heads
            if (door && dy <= 3) s = AIR;
            if (door && dy == 4) s = Math.abs(dx) == 1 ? brickStairs(dx > 0 ? Direction.EAST : Direction.WEST, true) : CRYING;
            if (dy > TOP && dy <= TOP + 5) {
                // the reliquary chamber's wall: crying obsidian pierced by rods, then the merlons under the crown
                s = dy == TOP + 5 ? (((int) Math.round(a / 15)) % 2 == 0 ? PURPUR : Blocks.PURPUR_SLAB.defaultBlockState()) : (flatMiddle && dy == TOP + 3 ? GLASS : hash(dx, dy, dz) % 6 == 0 ? CRYING : wall(x, dy, z));
            }
            set(level, pos, x, dy, z, s);
        }
        set(level, pos, x, CROWN, z, purpurStairs(toward(dx, dz), false));
        set(level, pos, x, CROWN + 1, z, PURPUR);
        // chains and a lantern of the void beside the door
        if (dz > 0 && Math.abs(dx) == 2) {
            set(level, pos, x, 5, z + 1, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            set(level, pos, x, 4, z + 1, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            set(level, pos, x, 6, z + 1, PURPUR);
        }
    }

    /**
     * Inside: the shaft open to the void down the middle with a ring of end rods, the ramp winding
     * up the wall (solid purpur under a stair, one step a block), landings at 8 and 16 with their
     * spawners and chests, the reliquary at 24 on a bridge of crying obsidian, the crown above.
     */
    private void interior(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, double r, double o) {
        boolean shaft = r <= SHAFT;
        boolean rampRing = o > R_IN - 1.5 && !shaft; // the outer ring of the interior carries the ramp
        double a = Geo.angle(dx, dz);
        // the ramp: 16 steps a turn, one block a step, starting at the door's left and climbing anticlockwise
        int step = (int) Math.floor(((a + 270) % 360) / 22.5); // 0..15 from the south flat
        boolean landing1 = false, landing2 = false;
        for (int dy = 1; dy <= TOP + 4; dy++) {
            BlockState s = AIR;
            // the floors: the ground floor is the plinth; landings at 8 and 16 leave the shaft open; the top floor bridges it
            if (dy == 8 || dy == 16) s = shaft ? AIR : ((dx + dz) & 1) == 0 ? BRICK : PURPUR;
            if (dy == TOP) s = shaft ? ((dx == 0 || dz == 0) ? CRYING : AIR) : ((dx + dz) & 1) == 0 ? BRICK : PURPUR; // a cross of crying obsidian bridges the shaft
            if (dy == TOP && r > SHAFT && r <= SHAFT + 1.0) s = OBSIDIAN;
            // the ramp on the outer ring: solid up to the step's height in each of the three turns
            if (rampRing) {
                for (int turn = 0; turn < 3; turn++) {
                    int top = 1 + step + 16 * turn; // the step's walking height
                    if (top > TOP - 1) continue;
                    if (dy < top) s = PURPUR;
                    else if (dy == top) s = purpurStairs(rampDir(a), false);
                }
                // the landings' floors override the ramp where they meet
                if (dy == 8 || dy == 16) s = ((dx + dz) & 1) == 0 ? BRICK : PURPUR;
            }
            // the shaft's ring of rods every four blocks, and the void below
            if (!shaft && r <= SHAFT + 1.0 && dy % 4 == 2 && dy < TOP && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) s = ROD;
            set(level, pos, x, dy, z, s);
        }
        // the crown over the reliquary: a purpur cone with a rod at its point (its first ring is the chamber's ceiling)
        int steps = (int) Math.ceil(R_OUT + 1.0);
        for (int k = 1; k <= steps; k++) {
            double rr = R_OUT + 1.0 - k;
            int y = CROWN + k;
            if (o <= rr && o > rr - 1.0) set(level, pos, x, y, z, k == steps ? PURPUR : purpurStairs(toward(dx, dz), false));
            else if (o <= rr - 1.0 && k < steps) set(level, pos, x, y, z, k == 1 ? PURPUR : AIR);
        }
        if (dx == 0 && dz == 0) {
            set(level, pos, x, CROWN + steps, z, PURPUR);
            set(level, pos, x, CROWN + steps + 1, z, ROD);
        }
        if (shaft) {
            // open down into the void: the plinth and the island under the shaft are already cut away
            set(level, pos, x, TOP + 1, z, dx == 0 && dz == 0 ? Blocks.PURPUR_PILLAR.defaultBlockState() : AIR);
            if (dx == 0 && dz == 0) {
                set(level, pos, x, TOP + 2, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), RELIC);
                set(level, pos, x, TOP + 4, z, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                set(level, pos, x, TOP + 3, z, ROD);
            }
            if ((Math.abs(dx) == 1 && dz == 0) || (Math.abs(dz) == 1 && dx == 0)) set(level, pos, x, TOP + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            return;
        }
        // the landings' furniture: spawners against the walls, chests, candles, rods
        furnish(level, pos, random, x, z, dx, dz, o, a);
    }

    private static Direction rampDir(double a) {
        // the ramp climbs anticlockwise (increasing angle): a stair faces the way it rises, along the tangent
        double t = Math.toRadians(a + 90);
        double tx = Math.cos(t), tz = Math.sin(t);
        return Math.abs(tx) >= Math.abs(tz) ? (tx > 0 ? Direction.EAST : Direction.WEST) : (tz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private void furnish(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int z, int dx, int dz, double o, double a) {
        boolean inner = o <= R_IN - 1.5 && Math.sqrt(dx * dx + dz * dz) > SHAFT + 1.0; // between the shaft's rods and the ramp
        // the first gallery (9..15): endermite and shulker spawners on the east and west, a chest to the north
        if (dx == 3 && dz == 0) spawner(level, pos, x, 9, z, EntityType.ENDERMITE, 0);
        if (dx == -3 && dz == 0) spawner(level, pos, x, 9, z, EntityType.SHULKER, 0);
        if (dx == 0 && dz == -3) chest(level, pos, random, x, 9, z, Direction.SOUTH, STORES);
        if (Math.abs(dx) == 2 && Math.abs(dz) == 2) set(level, pos, x, 9, z, CANDLE);
        // the second gallery (17..23): Hollow Thralls and a shulker, chests either side
        if (dx == 3 && dz == 0) spawner(level, pos, x, 17, z, WakingWorld.STONE_THRALL.get(), 1);
        if (dx == -3 && dz == 0) spawner(level, pos, x, 17, z, WakingWorld.STONE_THRALL.get(), 1);
        if (dx == 0 && dz == -3) spawner(level, pos, x, 17, z, EntityType.SHULKER, 0);
        if (dx == 2 && dz == -2) chest(level, pos, random, x, 17, z, Direction.SOUTH, STORES);
        if (dx == -2 && dz == -2) set(level, pos, x, 17, z, Blocks.DECORATED_POT.defaultBlockState());
        if (Math.abs(dx) == 2 && dz == 2) set(level, pos, x, 17, z, Blocks.SOUL_LANTERN.defaultBlockState());
        // the reliquary (25..29): candles round the bridge, two Hollow Thralls standing watch, amethyst
        if (Math.abs(dx) == 2 && Math.abs(dz) == 2) set(level, pos, x, TOP + 1, z, CANDLE);
        if ((Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0)) set(level, pos, x, TOP + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        if (dx == 3 && dz == 2) thrall(level, x, TOP + 1, z);
        if (dx == -3 && dz == -2) thrall(level, x, TOP + 1, z);
    }

    private void spawner(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int dy, int z, EntityType<?> type, int variant) {
        set(level, pos, x, dy, z, Blocks.SPAWNER.defaultBlockState());
        pos.set(x, cy + dy, z);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(type, level.getRandom());
            if (variant != 0) {
                // the variant rides in the spawner's NBT: load it the way a saved spawner is loaded
                CompoundTag entity = new CompoundTag();
                entity.putString("id", EntityType.getKey(type).toString());
                entity.putInt("Variant", variant);
                CompoundTag data = new CompoundTag();
                data.put("entity", entity);
                CompoundTag tag = new CompoundTag();
                tag.put("SpawnData", data);
                spawner.loadWithComponents(tag, level.registryAccess());
            }
        }
    }

    private void chest(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int x, int dy, int z, Direction facing, ResourceKey<LootTable> loot) {
        set(level, pos, x, dy, z, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        pos.set(x, cy + dy, z);
        RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), loot);
    }

    private void thrall(WorldGenLevel level, int x, int dy, int z) {
        StoneThrallEntity t = WakingWorld.STONE_THRALL.get().create(level.getLevel());
        if (t == null) return;
        t.moveTo(x + 0.5, cy + dy, z + 0.5, level.getRandom().nextFloat() * 360f, 0);
        t.setVariant(StoneThrallEntity.HOLLOW);
        t.setPersistenceRequired();
        level.addFreshEntityWithPassengers(t);
    }
}
