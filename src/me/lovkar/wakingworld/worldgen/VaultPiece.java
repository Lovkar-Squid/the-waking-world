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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A Sleeper's Vault: the stump of a round tower on the surface with a two-wide shaft (ladder on
 * the north wall) dropping into a pillared hall eighteen blocks down, four corridors to four
 * rooms, and under the hall a treasure crypt reached through a hole in the floor. Deepslate and
 * tuff, cobwebs in the corners, soul lanterns in the corridors, spawners in two rooms and the
 * hall, a chest in every room, two in the crypt. Where the embers, the runes and the horns are.
 */
public class VaultPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/vault"));
    public static final ResourceKey<LootTable> TREASURE = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/vault_treasure"));
    public static final ResourceKey<LootTable> ARCHAEOLOGY = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "archaeology/vault"));
    private static final int R = 19, DOWN = 27, UP = 8;
    private static final int HALL_FLOOR = -18, HALL_TOP = -13; // interior y range of the hall
    private static final int CRYPT_FLOOR = -25, CRYPT_TOP = -22;

    private final int cx, cy, cz;
    private final long seed;

    public VaultPiece(BlockPos origin, long seed) {
        super(WakingStructures.VAULT_PIECE.get(), 0, new BoundingBox(origin.getX() - R, origin.getY() - DOWN, origin.getZ() - R, origin.getX() + R, origin.getY() + UP, origin.getZ() + R));
        this.cx = origin.getX();
        this.cy = origin.getY();
        this.cz = origin.getZ();
        this.seed = seed;
    }

    public VaultPiece(CompoundTag tag) {
        super(WakingStructures.VAULT_PIECE.get(), tag);
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

    private BlockState wall(int dx, int dy, int dz) {
        int h = hash(dx, dy, dz) % 100;
        return h < 45 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : h < 65 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                : h < 80 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : h < 92 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private BlockState floor(int dx, int dy, int dz) {
        int h = hash(dx, dy, dz) % 100;
        return h < 50 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : h < 75 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : h < 90 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    private BlockState ruin(int dx, int dy, int dz) {
        int h = hash(dx, dy, dz) % 100;
        return h < 45 ? Blocks.COBBLESTONE.defaultBlockState() : h < 70 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : h < 90 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    }

    // markers for things that need a block entity set up afterwards
    private static final BlockState CHEST = Blocks.CHEST.defaultBlockState();
    private static final BlockState TREASURE_CHEST = Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
    private static final BlockState SPAWNER_Z = Blocks.SPAWNER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Is (dx, dz) inside a room's interior? Rooms sit at ±13 on one axis; interior 7x7. */
    private static int room(int dx, int dz) {
        if (Math.abs(dx - 13) <= 3 && Math.abs(dz) <= 3) return 0;
        if (Math.abs(dx + 13) <= 3 && Math.abs(dz) <= 3) return 1;
        if (Math.abs(dz - 13) <= 3 && Math.abs(dx) <= 3) return 2;
        if (Math.abs(dz + 13) <= 3 && Math.abs(dx) <= 3) return 3;
        return -1;
    }

    private static boolean roomShell(int dx, int dz) {
        return (Math.abs(dx - 13) <= 4 && Math.abs(dz) <= 4) || (Math.abs(dx + 13) <= 4 && Math.abs(dz) <= 4)
                || (Math.abs(dz - 13) <= 4 && Math.abs(dx) <= 4) || (Math.abs(dz + 13) <= 4 && Math.abs(dx) <= 4);
    }

    private static boolean corridor(int dx, int dz) {
        return (Math.abs(dz) <= 1 && Math.abs(dx) >= 6 && Math.abs(dx) <= 9) || (Math.abs(dx) <= 1 && Math.abs(dz) >= 6 && Math.abs(dz) <= 9);
    }

    private static boolean corridorShell(int dx, int dz) {
        return (Math.abs(dz) <= 2 && Math.abs(dx) >= 5 && Math.abs(dx) <= 10) || (Math.abs(dx) <= 2 && Math.abs(dz) >= 5 && Math.abs(dz) <= 10);
    }

    /** What the vault puts at (dx, dy, dz): a block, AIR for carved space, or null to leave the world alone. */
    BlockState shape(int dx, int dy, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        int h = hash(dx, dy, dz) % 100;
        boolean shaft = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;

        // ---- the surface: a ruined round tower with the shaft in its middle ----
        if (dy >= 0) {
            double r = Math.sqrt((dx - 0.5) * (dx - 0.5) + (dz - 0.5) * (dz - 0.5));
            if (dy == 0 && r <= 4.5) return shaft ? AIR : (r <= 3.6 ? floor(dx, dy, dz) : ruin(dx, dy, dz));
            if (dy >= 1 && r >= 2.6 && r <= 3.6) {
                boolean door = dz > 2 && ax <= 1;
                int height = 1 + hash(dx, 100, dz) % 5; // a broken rim
                if (!door && dy <= height && h > 12) return ruin(dx, dy, dz);
                return dy <= 6 ? AIR : null;
            }
            if (dy >= 1 && r < 2.6) return AIR;
            if (dy >= 1 && dy <= 6 && r <= 5.5) return AIR; // cleared around it
            return null;
        }

        // ---- the shaft down to the hall, ladder on its north wall ----
        if (dy < 0 && dy > HALL_TOP) {
            if (shaft) return dz == 0 ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH) : AIR;
            if (dx >= -1 && dx <= 2 && dz >= -1 && dz <= 2) return wall(dx, dy, dz);
            return null;
        }

        // ---- the hall: interior 11x6x11, pillars, the shaft opening in the ceiling ----
        boolean hallInterior = ax <= 5 && az <= 5;
        boolean hallShell = ax <= 6 && az <= 6;
        if (dy >= HALL_FLOOR && dy <= HALL_TOP) {
            if (hallInterior) {
                boolean pillar = ax == 3 && az == 3;
                if (pillar) return dy == HALL_TOP ? Blocks.DEEPSLATE_TILES.defaultBlockState() : dy == HALL_FLOOR ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                if (dy == HALL_TOP && ax >= 4 && az >= 4 && h < 40) return Blocks.COBWEB.defaultBlockState();
                if (dy == HALL_FLOOR && dx == -4 && dz == 4) return SPAWNER_Z;
                if (dy == HALL_FLOOR && dx == 0 && dz == 0) return Blocks.CHISELED_DEEPSLATE.defaultBlockState(); // a plinth
                if (dy == HALL_FLOOR + 1 && dx == 0 && dz == 0) return Blocks.SOUL_LANTERN.defaultBlockState();
                // braziers in the corners, chains and lanterns from the ceiling, candles on the plinth's step
                if (dy == HALL_FLOOR && ax == 5 && az == 5) return Blocks.SOUL_CAMPFIRE.defaultBlockState();
                if (ax == 0 && az == 3 && dy >= HALL_TOP - 1) return Blocks.CHAIN.defaultBlockState();
                if (ax == 0 && az == 3 && dy == HALL_TOP - 2) return Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                if (dy == HALL_FLOOR && ((ax == 1 && az == 0) || (ax == 0 && az == 1))) return Blocks.BLUE_CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + (h % 3)).setValue(net.minecraft.world.level.block.CandleBlock.LIT, true);
                if (dy == HALL_FLOOR && ax == 5 && az <= 2 && h < 30) return Blocks.DECORATED_POT.defaultBlockState();
                if (dy == HALL_FLOOR && ax == 2 && az == 5 && h < 50) return Blocks.SKELETON_SKULL.defaultBlockState();
                return AIR;
            }
            if (hallShell) {
                boolean doorway = (ax == 6 && az <= 1) || (az == 6 && ax <= 1);
                if (doorway && dy >= HALL_FLOOR && dy <= HALL_FLOOR + 2) return AIR;
                return wall(dx, dy, dz);
            }
            if (corridor(dx, dz)) {
                if (dy > HALL_FLOOR + 2) return dy == HALL_FLOOR + 3 ? wall(dx, dy, dz) : null;
                if (dy == HALL_FLOOR + 2 && ((ax == 8 && az == 0) || (az == 8 && ax == 0))) return Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                // a tripwire across the corridor half way along, its hooks on the walls; the dispensers sit in the walls above
                if (dy == HALL_FLOOR && ax == 7 && az <= 1) {
                    if (dz == 0) return Blocks.TRIPWIRE.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireBlock.NORTH, true).setValue(net.minecraft.world.level.block.TripWireBlock.SOUTH, true).setValue(net.minecraft.world.level.block.TripWireBlock.ATTACHED, true);
                    return Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dz < 0 ? Direction.SOUTH : Direction.NORTH).setValue(net.minecraft.world.level.block.TripWireHookBlock.ATTACHED, true);
                }
                if (dy == HALL_FLOOR && az == 7 && ax <= 1) {
                    if (dx == 0) return Blocks.TRIPWIRE.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireBlock.EAST, true).setValue(net.minecraft.world.level.block.TripWireBlock.WEST, true).setValue(net.minecraft.world.level.block.TripWireBlock.ATTACHED, true);
                    return Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dx < 0 ? Direction.EAST : Direction.WEST).setValue(net.minecraft.world.level.block.TripWireHookBlock.ATTACHED, true);
                }
                return AIR;
            }
            if (corridorShell(dx, dz) && dy <= HALL_FLOOR + 3) {
                if (dy == HALL_FLOOR + 1 && ax == 7 && az == 2) return Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, dz < 0 ? Direction.SOUTH : Direction.NORTH);
                if (dy == HALL_FLOOR + 1 && az == 7 && ax == 2) return Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, dx < 0 ? Direction.EAST : Direction.WEST);
                return wall(dx, dy, dz);
            }
            int rm = room(dx, dz);
            if (rm >= 0) {
                if (dy > HALL_FLOOR + 3) return dy == HALL_FLOOR + 4 ? wall(dx, dy, dz) : null;
                int rcx = rm == 0 ? 13 : rm == 1 ? -13 : 0, rcz = rm == 2 ? 13 : rm == 3 ? -13 : 0;
                int lx = dx - rcx, lz = dz - rcz;
                int ly = dy - HALL_FLOOR;
                // the chest at the far wall (every room), then the room's own furnishing
                if (ly == 0) {
                    if (rm == 0 && lx == 3 && lz == 0) return Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
                    if (rm == 1 && lx == -3 && lz == 0) return Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
                    if (rm == 2 && lx == 0 && lz == 3) return Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
                    if (rm == 3 && lx == 0 && lz == -3) return Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
                }
                if (ly == 3 && Math.abs(lx) == 3 && Math.abs(lz) == 3 && h < 60) return Blocks.COBWEB.defaultBlockState();
                BlockState furnished = roomFurnishing(roomType(rm), lx, ly, lz, h);
                if (furnished != null) return furnished;
                if (ly == 3 && lx == 0 && lz == 0) return Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                return AIR;
            }
            if (roomShell(dx, dz) && dy <= HALL_FLOOR + 4) {
                return wall(dx, dy, dz);
            }
            return null;
        }
        // floor of the hall, rooms and corridors (one below the interior), with the hole into the crypt
        if (dy == HALL_FLOOR - 1) {
            if (dx >= -1 && dx <= 0 && dz >= -1 && dz <= 0) return AIR; // down into the crypt
            int rm = room(dx, dz);
            if (rm >= 0 && roomType(rm) == 3) {
                // the flooded room: its floor is a hand lower and full of water, but for the stepping stones
                int rcx = rm == 0 ? 13 : rm == 1 ? -13 : 0, rcz = rm == 2 ? 13 : rm == 3 ? -13 : 0;
                int lx = dx - rcx, lz = dz - rcz;
                boolean step = (lx == 0 && Math.abs(lz) <= 3 && (rm >= 2)) || (lz == 0 && Math.abs(lx) <= 3 && rm < 2) || (Math.abs(lx) == 3 && Math.abs(lz) == 3);
                return step ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.WATER.defaultBlockState();
            }
            if (hallShell || corridorShell(dx, dz) || roomShell(dx, dz)) return floor(dx, dy, dz);
            return null;
        }
        if (dy == HALL_FLOOR - 2) {
            int rm = room(dx, dz);
            if (rm >= 0 && roomType(rm) == 3) return floor(dx, dy, dz);
            return null;
        }

        // ---- the crypt under the hall ----
        boolean cryptInterior = ax <= 2 && az <= 2;
        boolean cryptShell = ax <= 3 && az <= 3;
        if (dy >= CRYPT_FLOOR && dy <= CRYPT_TOP) {
            if (cryptInterior) {
                if (dy < CRYPT_TOP && dx == -1 && dz == -1) return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
                if (dy == CRYPT_FLOOR && dx == 2 && dz == 0) return TREASURE_CHEST;
                if (dy == CRYPT_FLOOR && dx == 2 && dz == -2) return Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
                if (dy == CRYPT_FLOOR && dx == -2 && dz == 2) return Blocks.SOUL_LANTERN.defaultBlockState();
                if (dy == CRYPT_FLOOR && dx == 0 && dz == 2) return Blocks.MAGMA_BLOCK.defaultBlockState(); // the ember that never went out
                if (dy == CRYPT_FLOOR && dx == -2 && dz == -2) return Blocks.DECORATED_POT.defaultBlockState();
                if (dy == CRYPT_FLOOR && dx == 2 && dz == 2) return Blocks.BLUE_CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3).setValue(net.minecraft.world.level.block.CandleBlock.LIT, true);
                if (dy == CRYPT_FLOOR && dx == 0 && dz == -2) return Blocks.BONE_BLOCK.defaultBlockState();
                if (dy == CRYPT_FLOOR + 1 && dx == 0 && dz == -2) return Blocks.SKELETON_SKULL.defaultBlockState();
                if (dy == CRYPT_TOP && Math.abs(dx) == 1 && dz == 1) return Blocks.CHAIN.defaultBlockState();
                return AIR;
            }
            if (cryptShell) return wall(dx, dy, dz);
            return null;
        }
        if (dy == CRYPT_FLOOR - 1 && cryptShell) return floor(dx, dy, dz);
        if (dy < HALL_FLOOR - 1 && dy > CRYPT_TOP && dx >= -2 && dx <= 1 && dz >= -2 && dz <= 1) {
            // the hole down: a 2x2 drop, ladder on its north-west
            if (dx >= -1 && dx <= 0 && dz >= -1 && dz <= 0) return dx == -1 && dz == -1 ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH) : AIR;
            return wall(dx, dy, dz);
        }
        return null;
    }

    /** 0 armoury, 1 library, 2 ossuary, 3 flooded, 4 shrine - each vault deals its rooms differently. */
    private int roomType(int rm) {
        return Math.floorMod(hash(rm * 17 + 3, 11, rm * 5 - 7) + rm, 5);
    }

    private static final BlockState SPAWNER_S = Blocks.SPAWNER.defaultBlockState();

    /** What a room of a type holds at (lx, ly, lz) from its centre, or null for the plain room. */
    private BlockState roomFurnishing(int type, int lx, int ly, int lz, int h) {
        switch (type) {
            case 0 -> { // the armoury: anvil, grindstone, smithing table, barrels, chains, a sentinel
                if (ly == 0 && lx == -2 && lz == -2) return Blocks.ANVIL.defaultBlockState();
                if (ly == 0 && lx == 2 && lz == -2) return Blocks.GRINDSTONE.defaultBlockState();
                if (ly == 0 && lx == -2 && lz == 2) return Blocks.SMITHING_TABLE.defaultBlockState();
                if (ly <= 1 && lx == 2 && lz == 2) return Blocks.BARREL.defaultBlockState();
                if (ly == 0 && lx == 2 && lz == 1) return Blocks.BARREL.defaultBlockState();
                if (ly >= 2 && Math.abs(lx) == 1 && Math.abs(lz) == 1) return Blocks.CHAIN.defaultBlockState();
                if (ly == 1 && Math.abs(lx) == 1 && Math.abs(lz) == 1) return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                if (ly == 0 && lx == 0 && lz == 0) return SPAWNER_S;
            }
            case 1 -> { // the library: shelves along the side walls, a lectern, candles, more cobwebs
                if (ly <= 2 && Math.abs(lx) == 2 && Math.abs(lz) <= 2 && lz != 0) return h < 20 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState();
                if (ly <= 1 && Math.abs(lz) == 2 && Math.abs(lx) <= 1) return Blocks.BOOKSHELF.defaultBlockState();
                if (ly == 0 && lx == 0 && lz == 0) return Blocks.LECTERN.defaultBlockState();
                if (ly == 0 && lx == 0 && Math.abs(lz) == 1) return Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 2 + (h % 3)).setValue(net.minecraft.world.level.block.CandleBlock.LIT, h < 50);
                if (ly == 2 && Math.abs(lx) <= 1 && h < 25) return Blocks.COBWEB.defaultBlockState();
                if (ly == 0 && lx == -1 && lz == 0) return SPAWNER_Z;
            }
            case 2 -> { // the ossuary: bones underfoot, skulls, gravel worth brushing, skeletons
                if (ly == 0 && (Math.abs(lx) + Math.abs(lz)) % 3 == 0 && Math.abs(lx) <= 2 && Math.abs(lz) <= 2) return Blocks.SKELETON_SKULL.defaultBlockState();
                if (ly == 0 && Math.abs(lx) == 2 && Math.abs(lz) == 2) return Blocks.BONE_BLOCK.defaultBlockState();
                if (ly == 1 && Math.abs(lx) == 2 && Math.abs(lz) == 2) return Blocks.BONE_BLOCK.defaultBlockState();
                if (ly == 0 && lx == 0 && lz == 0) return SPAWNER_S;
                if (ly == 0 && ((lx == 1 && lz == -1) || (lx == -1 && lz == 1))) return Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();
                if (ly == 0 && Math.abs(lx) == 1 && Math.abs(lz) == 1) return Blocks.GRAVEL.defaultBlockState();
                if (ly == 2 && Math.abs(lx) <= 2 && Math.abs(lz) <= 2 && h < 20) return Blocks.COBWEB.defaultBlockState();
            }
            case 3 -> { // the flooded room: dripstone from the ceiling, a drowned in the water, the chest on a stone
                if (ly == 3 && Math.abs(lx) <= 2 && Math.abs(lz) <= 2 && h < 35) return Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
                if (ly == 0 && Math.abs(lx) <= 2 && Math.abs(lz) <= 2 && h < 12) return Blocks.MOSS_CARPET.defaultBlockState();
                if (ly == 0 && lx == 0 && lz == 0) return SPAWNER_Z;
            }
            default -> { // the shrine room: a plinth with an amethyst cluster, candles, a lodestone
                if (ly == 0 && lx == 0 && lz == 0) return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                if (ly == 1 && lx == 0 && lz == 0) return Blocks.AMETHYST_CLUSTER.defaultBlockState();
                if (ly == 0 && Math.abs(lx) == 1 && Math.abs(lz) == 1) return Blocks.BLUE_CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + (h % 4)).setValue(net.minecraft.world.level.block.CandleBlock.LIT, true);
                if (ly == 0 && lx == 2 && lz == 2) return Blocks.LODESTONE.defaultBlockState();
                if (ly == 0 && lx == -2 && lz == -2) return Blocks.AMETHYST_BLOCK.defaultBlockState();
                if (ly == 0 && lx == -2 && lz == 2) return Blocks.DECORATED_POT.defaultBlockState();
                if (ly == 0 && lx == 2 && lz == -2) return Blocks.DECORATED_POT.defaultBlockState();
            }
        }
        return null;
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
                for (int dy = -DOWN; dy <= UP; dy++) {
                    BlockState s = shape(dx, dy, dz);
                    if (s == null) continue;
                    pos.set(x, cy + dy, z);
                    if (s.isAir()) {
                        if (!level.getBlockState(pos).isAir()) level.setBlock(pos, s, 2);
                        continue;
                    }
                    level.setBlock(pos, s, 2);
                    if (s.is(Blocks.CHEST)) {
                        boolean treasure = dy <= CRYPT_TOP;
                        RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), treasure ? TREASURE : LOOT);
                    } else if (s.is(Blocks.SPAWNER) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                        int rm = room(dx, dz);
                        int type = rm >= 0 ? roomType(rm) : -1;
                        EntityType<?> et;
                        if (type == 3) et = EntityType.DROWNED;
                        else if (type == 0 || type == 2) et = EntityType.SKELETON;
                        else {
                            int pick = hash(dx, dy, dz) % 4;
                            et = pick == 0 ? EntityType.SKELETON : pick == 1 ? EntityType.ZOMBIE : WakingWorld.STONE_THRALL.get();
                        }
                        spawner.setEntityId(et, random);
                    } else if (s.is(Blocks.DISPENSER) && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity d) {
                        d.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 5 + random.nextInt(6)));
                        d.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 4 + random.nextInt(4)));
                    } else if (s.is(Blocks.SUSPICIOUS_GRAVEL) && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity b) {
                        b.setLootTable(ARCHAEOLOGY, random.nextLong());
                    }
                }
            }
        }
    }
}
