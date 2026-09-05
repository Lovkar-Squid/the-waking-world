package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Drowned Cistern: under a mossy pump house by the water, a ladder drops into a vaulted hall
 * twenty-five blocks across, flooded knee-deep - sixteen pillars carry the vault, walkways run
 * round the walls and across the middle to a dais where the keepers' hoard stands, sea lanterns
 * glow under the water, dripstone hangs from the vault and lily pads drift between the pillars.
 * Drowned Keepers walk the water; the drowned of the sea follow them.
 */
public class CisternPiece extends LocalPiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/cistern"));
    static final int HALF = 13;           // the hall's walls at |l| = 13
    static final int F = -22, C = F + 9;  // floor and ceiling
    static final int WATER = F + 2;       // water fills F+1..F+2

    public CisternPiece(BlockPos origin, Rotation rot, long seed) {
        super(WakingStructures.DUNGEON_PIECE.get(), origin, rot, seed, -HALF - 1, -HALF - 1, HALF + 1, HALF + 1, F - 2, 12);
    }

    public CisternPiece(CompoundTag tag) {
        super(WakingStructures.DUNGEON_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Kind", "cistern");
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState WATER_B = Blocks.WATER.defaultBlockState();
    static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();

    private BlockState stone(int x, int y, int z) {
        int h = hash(x, y, z) % 100;
        return h < 55 ? Blocks.STONE_BRICKS.defaultBlockState() : h < 75 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : h < 88 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : h < 95 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    private BlockState floorStone(int x, int z) {
        int h = hash(x, 0, z) % 100;
        return h < 50 ? Blocks.STONE_BRICKS.defaultBlockState() : h < 80 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState();
    }

    static BlockState stairs(Direction facing, boolean top) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz) {
        int ax = Math.abs(lx), az = Math.abs(lz);
        int m = Math.max(ax, az);
        if (m > HALF + 1) return;
        // ---- the pump house on the surface: 7 x 7 with a mossy gable, the hatch and ladder in the middle ----
        if (m <= 4) pumpHouse(level, pos, random, wx, wz, lx, lz, ax, az, m);
        // ---- the shaft: ladder from the house down to the hall's ceiling ----
        if (lx == 0 && lz == 0) {
            for (int dy = -1; dy > C; dy--) set(level, pos, wx, dy, wz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        } else if (ax <= 1 && az <= 1) {
            for (int dy = -1; dy > C; dy--) set(level, pos, wx, dy, wz, stone(lx, dy, lz)); // the shaft's lining
        }
        // ---- the hall ----
        if (m > HALF) {
            // the outer shell, a block thick, so no cave or aquifer breaks in
            for (int dy = F - 1; dy <= C + 1; dy++) set(level, pos, wx, dy, wz, stone(lx, dy, lz));
            return;
        }
        if (m == HALF) {
            wall(level, pos, random, wx, wz, lx, lz, ax, az);
            return;
        }
        interior(level, pos, random, wx, wz, lx, lz, ax, az, m);
    }

    private void pumpHouse(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz, int ax, int az, int m) {
        clearAbove(level, pos, wx, wz, 1, 12);
        boolean wallCell = ax == 3 || az == 3;
        boolean door = lz == 3 && lx == 0;
        if (m <= 3) {
            set(level, pos, wx, 0, wz, wallCell ? stone(lx, 0, lz) : (lx == 0 && lz == 0 ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.TOP) : floorStone(lx, lz)));
            foundation(level, pos, wx, wz, 0, Blocks.COBBLESTONE.defaultBlockState(), 6);
            for (int dy = 1; dy <= 3; dy++) {
                BlockState s = wallCell ? stone(lx, dy, lz) : AIR;
                if (wallCell && dy == 2 && ((az == 3 && ax == 2) || (ax == 3 && az == 1))) s = Blocks.GLASS_PANE.defaultBlockState();
                if (door && dy <= 2) s = AIR;
                set(level, pos, wx, dy, wz, s);
            }
            if (door) {
                set(level, pos, wx, 1, wz, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH));
                set(level, pos, wx, 2, wz, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            // inside: a cauldron of water, a chain wheel, a lantern, barrels
            if (lx == -2 && lz == -2) set(level, pos, wx, 1, wz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
            if (lx == 2 && lz == -2) set(level, pos, wx, 1, wz, Blocks.BARREL.defaultBlockState());
            if (lx == 2 && lz == 2) set(level, pos, wx, 1, wz, Blocks.BARREL.defaultBlockState());
            if (lx == 0 && lz == -2) {
                for (int dy = 3; dy <= 7; dy++) set(level, pos, wx, dy, wz, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                set(level, pos, wx, 2, wz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            if (lx == -2 && lz == 2) set(level, pos, wx, 1, wz, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST));
        }
        // the gable roof of stone stairs along x, over the whole 9 x 9 including eaves
        int k = 4 - ax; // 0 at the eave (ax 4) .. 4 at the ridge
        if (k == 4) set(level, pos, wx, 4 + k, wz, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        else {
            set(level, pos, wx, 4 + k, wz, hash(lx, 9, lz) % 4 == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, lx > 0 ? Direction.WEST : Direction.EAST) : stairs(lx > 0 ? Direction.WEST : Direction.EAST, false));
        }
        // under the roof: the gable ends and the side walls' top course are stone, the attic is open
        if (m <= 3 && k > 0 && !(lx == 0 && lz == -2)) for (int dy = 4; dy < 4 + k; dy++) set(level, pos, wx, dy, wz, (ax == 3 || az == 3) ? stone(lx, dy, lz) : AIR);
        // a vine or two, moss on the roof edge
        if (m == 4 && hash(lx, 4, lz) % 5 == 0) set(level, pos, wx, 4, wz, Blocks.MOSS_CARPET.defaultBlockState());
    }

    private void wall(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz, int ax, int az) {
        for (int dy = F - 1; dy <= C + 1; dy++) {
            BlockState s = stone(lx, dy, lz);
            // pilasters every four blocks, arches between them at the vault
            int along = ax == HALF ? lz : lx;
            boolean pilaster = Math.floorMod(along, 4) == 2;
            if (pilaster && dy >= F && dy <= C) s = Blocks.POLISHED_ANDESITE.defaultBlockState();
            if (!pilaster && dy == C - 1 && Math.floorMod(along, 4) != 0) s = stairs(ax == HALF ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH), true);
            // inflow grates high on the walls
            if (!pilaster && dy == C - 2 && Math.floorMod(along, 8) == 0) s = Blocks.IRON_BARS.defaultBlockState();
            if (dy == WATER || dy == WATER - 1) s = hash(lx, dy, lz) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState(); // the water line
            set(level, pos, wx, dy, wz, s);
        }
        // glow lichen and sea lanterns set into the wall above the walkway
        if (Math.floorMod(ax == HALF ? lz : lx, 8) == 4) set(level, pos, wx, WATER + 3, wz, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private void interior(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz, int ax, int az, int m) {
        boolean ledge = m >= HALF - 2;                 // two wide round the walls
        boolean cross = (ax <= 0 && az > 3) || (az <= 0 && ax > 3); // the walkways across the middle
        boolean dais = m <= 3;                        // the hoard's island
        boolean pillar = (ax == 4 || ax == 8) && (az == 4 || az == 8);
        boolean walk = ledge || cross || dais;
        // the floor and what stands on it
        set(level, pos, wx, F - 1, wz, stone(lx, F - 1, lz));
        set(level, pos, wx, F, wz, pillar ? Blocks.SEA_LANTERN.defaultBlockState() : floorStone(lx, lz));
        if (walk) {
            for (int dy = F + 1; dy <= WATER + 1; dy++) set(level, pos, wx, dy, wz, dy == WATER + 1 ? (dais ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)) : stone(lx, dy, lz));
            if (dais && m == 3) set(level, pos, wx, WATER + 1, wz, stairs(ax == 3 ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH), false));
            for (int dy = WATER + 2; dy <= C - 1; dy++) set(level, pos, wx, dy, wz, AIR);
        } else {
            for (int dy = F + 1; dy <= WATER; dy++) set(level, pos, wx, dy, wz, pillar ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : WATER_B);
            for (int dy = WATER + 1; dy <= C - 1; dy++) set(level, pos, wx, dy, wz, AIR);
            if (!pillar) {
                if (hash(lx, 1, lz) % 7 == 0) set(level, pos, wx, F + 1, wz, Blocks.SEAGRASS.defaultBlockState());
                if (hash(lx, 2, lz) % 9 == 0) set(level, pos, wx, WATER + 1, wz, Blocks.LILY_PAD.defaultBlockState());
            }
        }
        // the pillars: mossy shafts, a capital of upside-down stairs on four sides under the vault
        if (pillar) {
            for (int dy = WATER + 1; dy <= C - 1; dy++) set(level, pos, wx, dy, wz, dy == C - 1 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : hash(lx, dy, lz) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
        }
        boolean capital = ((Math.abs(ax - 4) == 1 || Math.abs(ax - 8) == 1) && (az == 4 || az == 8)) || ((Math.abs(az - 4) == 1 || Math.abs(az - 8) == 1) && (ax == 4 || ax == 8));
        if (capital) {
            int px = ax == 5 || ax == 3 ? 4 : ax == 9 || ax == 7 ? 8 : ax; // the pillar this capital belongs to (by |x|)
            int pz = az == 5 || az == 3 ? 4 : az == 9 || az == 7 ? 8 : az;
            int sx = Integer.signum(lx), sz = Integer.signum(lz);
            int dxp = lx - sx * px, dzp = lz - sz * pz; // offset from the pillar, -1/0/1
            Direction toward = dxp != 0 ? (dxp > 0 ? Direction.WEST : Direction.EAST) : (dzp > 0 ? Direction.NORTH : Direction.SOUTH);
            set(level, pos, wx, C - 1, wz, stairs(toward, true));
        }
        // the vault: the ceiling with hanging dripstone, chains with lanterns over the walkways; the shaft comes through the middle
        boolean shaft = lx == 0 && lz == 0;
        set(level, pos, wx, C, wz, shaft ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH) : stone(lx, C, lz));
        set(level, pos, wx, C + 1, wz, shaft ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH) : stone(lx, C + 1, lz));
        if (!walk && !pillar && !capital && hash(lx, 5, lz) % 11 == 0) set(level, pos, wx, C - 1, wz, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
        if (cross && Math.floorMod(ax + az, 6) == 3) {
            set(level, pos, wx, C - 1, wz, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            set(level, pos, wx, C - 2, wz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // the hoard on the dais: chests, a prismarine plinth with a sea lantern, pots
        if (dais) {
            if (lx == 0 && lz == 0) {
                set(level, pos, wx, WATER + 2, wz, Blocks.DARK_PRISMARINE.defaultBlockState());
                set(level, pos, wx, WATER + 3, wz, Blocks.SEA_LANTERN.defaultBlockState());
            }
            if (ax == 2 && lz == 0) chest(level, pos, random, wx, WATER + 2, wz, lx > 0 ? Direction.WEST : Direction.EAST, LOOT);
            if (lx == 0 && lz == -2) chest(level, pos, random, wx, WATER + 2, wz, Direction.SOUTH, LOOT);
            if (ax == 2 && az == 2) set(level, pos, wx, WATER + 2, wz, hash(lx, 3, lz) % 2 == 0 ? Blocks.DECORATED_POT.defaultBlockState() : Blocks.SEA_PICKLE.defaultBlockState().setValue(net.minecraft.world.level.block.SeaPickleBlock.PICKLES, 3).setValue(net.minecraft.world.level.block.SeaPickleBlock.WATERLOGGED, false));
            if (lx == 0 && lz == 2) spawner(level, pos, wx, WATER + 2, wz, WakingWorld.DROWNED_KEEPER.get());
        }
        // the keepers' spawners in the water at the far corners, a drowned spawner by the ladder's foot
        if (ax == 10 && az == 10 && (lx < 0 || lz < 0)) spawner(level, pos, wx, F + 1, wz, WakingWorld.DROWNED_KEEPER.get());
        if (lx == 10 && lz == 10) spawner(level, pos, wx, F + 1, wz, EntityType.DROWNED);
        // the ladder lands on the middle of the north walkway: the shaft comes down at (0,0) - the dais - so the last
        // rungs run down the plinth's north face
        if (lx == 0 && lz == 0) for (int dy = C - 1; dy >= WATER + 4; dy--) set(level, pos, wx, dy, wz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        if (lx == 0 && lz == -1) for (int dy = C - 1; dy >= WATER + 2; dy--) set(level, pos, wx, dy, wz, dy > WATER + 3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : AIR); // the ladder's backing
        // spare chests along the ledge, moss and lichen on the walkways
        if (ledge && (lx == HALF - 2) && (lz == 6 || lz == -6)) chest(level, pos, random, wx, WATER + 2, wz, Direction.WEST, LOOT);
        if (walk && !dais && hash(lx, 6, lz) % 9 == 0) set(level, pos, wx, WATER + 2, wz, Blocks.MOSS_CARPET.defaultBlockState());
    }
}
