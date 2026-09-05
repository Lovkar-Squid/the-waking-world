package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Ember Forge: under a ruined smithy in the dry lands, a stair drops into a hall of deepslate
 * and basalt where the fires never went out - lava runs in channels along the walls, a great
 * furnace stands in the middle with its chimney climbing to the surface, anvils and grindstones
 * and quenching troughs stand about, fire pits burn in the corners and the master's vault at the
 * far end holds what was forged here. Ember Wraiths keep it, with a Rune Sentinel at the vault.
 */
public class ForgePiece extends LocalPiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "chests/forge"));
    static final int HALF = 12, HZ = -32;      // the hall: |lx| <= 12, lz in HZ-12..HZ+12 (walls included)
    static final int F = -16, C = F + 10;      // the hall's floor and ceiling
    static final int STAIR_Z0 = -4, STEPS = 16; // the stair down: from lz -4 to -19, a block a step, arriving at the hall's door

    public ForgePiece(BlockPos origin, Rotation rot, long seed) {
        super(WakingStructures.DUNGEON_PIECE.get(), origin, rot, seed, -HALF - 1, HZ - HALF - 1, HALF + 1, 5, F - 2, 12);
    }

    public ForgePiece(CompoundTag tag) {
        super(WakingStructures.DUNGEON_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Kind", "forge");
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    static final BlockState BASALT = Blocks.POLISHED_BASALT.defaultBlockState();
    static final BlockState BLACK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();

    private BlockState stone(int x, int y, int z) {
        int h = hash(x, y, z) % 100;
        return h < 55 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : h < 75 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : h < 88 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : h < 95 ? Blocks.POLISHED_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    private BlockState floor(int x, int z) {
        int h = hash(x, 0, z) % 100;
        return h < 50 ? Blocks.POLISHED_BASALT.defaultBlockState() : h < 85 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    /** The ruin's stone, sandy: sandstone and cut sandstone with cobble at the foot. */
    private BlockState ruin(int x, int y, int z) {
        int h = hash(x, y, z) % 100;
        return y <= 1 ? (h < 60 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState()) : h < 60 ? Blocks.SANDSTONE.defaultBlockState() : h < 85 ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    }

    static BlockState stairs(Direction facing, boolean top) {
        return Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz) {
        int ax = Math.abs(lx);
        // ---- the ruined smithy on the surface: 9 x 9 round the origin, open to the sky ----
        if (ax <= 5 && lz >= -5 && lz <= 5) smithy(level, pos, random, wx, wz, lx, lz, ax);
        // ---- the chimney: a hollow 3 x 3 shaft over the great furnace, from the hall up through the smithy ----
        int fz = lz - HZ; // relative to the hall's middle
        int afz = Math.abs(fz);
        if (ax <= 1 && afz <= 1) {
            for (int dy = C; dy <= 6; dy++) set(level, pos, wx, dy, wz, (ax == 1 || afz == 1) ? (dy > 0 ? ruin(lx, dy, lz) : stone(lx, dy, lz)) : AIR);
            set(level, pos, wx, 7, wz, (ax == 1 || afz == 1) ? (((ax + afz) & 1) == 0 ? Blocks.SANDSTONE_WALL.defaultBlockState() : ruin(lx, 7, lz)) : AIR);
            if (ax == 0 && afz == 0) clearAbove(level, pos, wx, wz, 8, 20);
        }
        // ---- the stair down: two wide, from the smithy's back wall to the hall's south door ----
        if (ax <= 1 && lz <= STAIR_Z0 && lz > STAIR_Z0 - STEPS) {
            int k = STAIR_Z0 - lz;   // 0 at the top step
            int y = -k - 1;          // the step's walking surface is at y + 1 = -k
            set(level, pos, wx, y, wz, stone(lx, y, lz));
            set(level, pos, wx, y + 1, wz, stairs(Direction.SOUTH, false));
            for (int dy = y + 2; dy <= y + 4; dy++) set(level, pos, wx, dy, wz, AIR);
            set(level, pos, wx, y + 5, wz, stone(lx, y + 5, lz));
            if (k % 4 == 2 && lx == 0) set(level, pos, wx, y + 4, wz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        if ((ax == 2) && lz <= STAIR_Z0 && lz > STAIR_Z0 - STEPS) {
            int k = STAIR_Z0 - lz;
            int y = -k - 1;
            for (int dy = y - 1; dy <= y + 5; dy++) set(level, pos, wx, dy, wz, stone(lx, dy, lz)); // the stair's walls
        }
        // ---- the hall ----
        if (ax > HALF + 1 || afz > HALF + 1) return;
        int m = Math.max(ax, afz);
        if (m > HALF) {
            for (int dy = F - 1; dy <= C + 1; dy++) set(level, pos, wx, dy, wz, stone(lx, dy, lz)); // the shell
            // the stair's last step comes through the south shell
            if (fz == HALF + 1 && ax <= 1) {
                set(level, pos, wx, F, wz, stone(lx, F, lz));
                set(level, pos, wx, F + 1, wz, stairs(Direction.SOUTH, false));
                for (int dy = F + 2; dy <= F + 4; dy++) set(level, pos, wx, dy, wz, AIR);
            }
            return;
        }
        if (m == HALF) {
            wall(level, pos, random, wx, wz, lx, fz, ax, afz);
            return;
        }
        interior(level, pos, random, wx, wz, lx, fz, ax, afz, m);
    }

    private void smithy(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int lz, int ax) {
        clearAbove(level, pos, wx, wz, 1, 12);
        int m = Math.max(ax, Math.abs(lz));
        boolean wallCell = ax == 4 || Math.abs(lz) == 4;
        if (m > 4) return;
        set(level, pos, wx, 0, wz, wallCell ? ruin(lx, 0, lz) : (hash(lx, 0, lz) % 3 == 0 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState()));
        foundation(level, pos, wx, wz, 0, Blocks.SANDSTONE.defaultBlockState(), 6);
        if (wallCell) {
            // walls half fallen: a height from a hash, the door in the south wall, the corner posts standing
            boolean corner = ax == 4 && Math.abs(lz) == 4;
            int h = corner ? 4 : 1 + hash(lx, 20, lz) % 4;
            if (Math.abs(lz) == 4 && ax <= 1) h = 0; // the door at the front, the stair's mouth at the back
            for (int dy = 1; dy <= h; dy++) set(level, pos, wx, dy, wz, ruin(lx, dy, lz));
            if (h >= 2 && !corner && hash(lx, 21, lz) % 3 == 0) set(level, pos, wx, h, wz, Blocks.SANDSTONE_WALL.defaultBlockState());
            if (corner) set(level, pos, wx, 5, wz, Blocks.LANTERN.defaultBlockState());
            return;
        }
        // inside: the anvil, a grindstone, a barrel, the stair's mouth at the back (lz -3, -4 -> the stair starts at -4)
        if (lx == -2 && lz == 0) set(level, pos, wx, 1, wz, Blocks.ANVIL.defaultBlockState());
        if (lx == 2 && lz == -1) set(level, pos, wx, 1, wz, Blocks.GRINDSTONE.defaultBlockState());
        if (lx == -3 && lz == 2) set(level, pos, wx, 1, wz, Blocks.BARREL.defaultBlockState());
        if (lx == 3 && lz == 2) set(level, pos, wx, 1, wz, Blocks.SMITHING_TABLE.defaultBlockState());
        if (lx == 2 && lz == 3) set(level, pos, wx, 1, wz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2));
        if (ax <= 1 && lz == -3) {
            set(level, pos, wx, 0, wz, stairs(Direction.SOUTH, false)); // the first step down is already in the floor
        }
    }

    private void wall(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int fz, int ax, int afz) {
        boolean door = fz == HALF && ax <= 1;
        for (int dy = F - 1; dy <= C + 1; dy++) {
            BlockState s = stone(lx, dy, fz);
            int along = ax == HALF ? fz : lx;
            boolean pilaster = Math.floorMod(along, 4) == 2;
            if (pilaster && dy >= F && dy <= C - 1) s = BASALT;
            if (pilaster && dy == C - 1) s = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            if (!pilaster && dy == C - 1 && Math.floorMod(along, 4) != 0) s = stairs(ax == HALF ? (lx > 0 ? Direction.WEST : Direction.EAST) : (fz > 0 ? Direction.NORTH : Direction.SOUTH), true);
            if (dy == F + 4 && Math.floorMod(along, 4) == 0 && !door) s = Blocks.MAGMA_BLOCK.defaultBlockState(); // glowing vents
            if (door && dy == F) s = floor(lx, fz);
            if (door && dy >= F + 1 && dy <= F + 4) s = AIR;
            if (door && dy == F + 5) s = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            set(level, pos, wx, dy, wz, s);
        }
        // wall braziers between the pilasters: a campfire on a wall block just off the wall is the interior's business; here, lanterns
        if (Math.floorMod(ax == HALF ? fz : lx, 8) == 4) set(level, pos, wx, F + 5, wz, Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private void interior(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, int wx, int wz, int lx, int fz, int ax, int afz, int m) {
        boolean channel = m == 10 && !(ax <= 1 && fz > 0) && !(afz <= 1) && !(ax <= 1); // the lava channel ring, broken where the walkways cross
        boolean furnace = ax <= 2 && afz <= 2;
        boolean firePit = (ax == 6 && afz == 6);
        boolean vaultStep = fz <= -9 && ax <= 4; // the master's vault: a raised alcove at the north end
        // the floor
        set(level, pos, wx, F - 1, wz, stone(lx, F - 1, fz));
        if (channel) {
            set(level, pos, wx, F, wz, LAVA);
        } else if (m == 9 || m == 11) {
            set(level, pos, wx, F, wz, hash(lx, 3, fz) % 3 == 0 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : BLACK); // the channel's kerbs
        } else {
            set(level, pos, wx, F, wz, firePit ? Blocks.NETHERRACK.defaultBlockState() : floor(lx, fz));
        }
        for (int dy = F + 1; dy <= C - 1; dy++) set(level, pos, wx, dy, wz, AIR);
        // the ceiling: basalt with the odd magma vein, chains and lanterns down the two aisles
        set(level, pos, wx, C, wz, hash(lx, C, fz) % 9 == 0 ? MAGMA : stone(lx, C, fz));
        set(level, pos, wx, C + 1, wz, stone(lx, C + 1, fz));
        if ((ax == 5 || ax == 7) && Math.floorMod(fz, 6) == 0 && afz > 2) {
            set(level, pos, wx, C - 1, wz, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            set(level, pos, wx, C - 2, wz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        if (firePit) {
            set(level, pos, wx, F + 1, wz, Blocks.FIRE.defaultBlockState());
            return;
        }
        if (Math.abs(ax - 6) <= 1 && Math.abs(afz - 6) <= 1 && !firePit && (ax == 5 || ax == 7 || afz == 5 || afz == 7) && ((ax + afz) & 1) == 1) {
            set(level, pos, wx, F + 1, wz, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState()); // the fire pits' surrounds
        }
        // the great furnace: blackstone bricks with lit blast furnaces on its four faces, magma at the corners, a lava eye in a ring at the top, the chimney above
        if (furnace) {
            for (int dy = F + 1; dy <= F + 6; dy++) {
                BlockState s = BLACK;
                if (dy == F + 1 && ((ax == 2 && afz == 0) || (afz == 2 && ax == 0))) {
                    Direction facing = ax == 2 ? (lx > 0 ? Direction.EAST : Direction.WEST) : (fz > 0 ? Direction.SOUTH : Direction.NORTH);
                    s = Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing).setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true);
                }
                if (dy == F + 2 && ax == 2 && afz == 2) s = MAGMA;
                if (dy == F + 4 && (ax == 2 || afz == 2) && hash(lx, dy, fz) % 3 == 0) s = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                if (dy == F + 6 && ax == 1 && afz == 1) s = LAVA;                     // the eye
                if (dy == F + 6 && ((ax == 1 && afz == 0) || (ax == 0 && afz == 1))) s = LAVA;
                if (dy == F + 6 && ax == 0 && afz == 0) s = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true);
                set(level, pos, wx, dy, wz, s);
            }
            // the chimney's foot: the 3 x 3 shell rises from F+7 to the ceiling, hollow
            for (int dy = F + 7; dy <= C + 1; dy++) {
                BlockState s;
                if (ax == 2 || afz == 2) s = dy <= F + 8 ? BLACK : dy >= C ? stone(lx, dy, fz) : AIR; // the furnace's crown, then the ceiling closes over
                else s = (ax == 1 || afz == 1) ? BLACK : AIR;
                set(level, pos, wx, dy, wz, s);
            }
            return;
        }
        // the master's vault at the north end: two steps up, a barred front, chests and the sentinel
        if (vaultStep) {
            int up = fz <= -10 ? 2 : 1;
            for (int dy = F + 1; dy <= F + up; dy++) set(level, pos, wx, dy, wz, dy == F + up ? Blocks.POLISHED_BASALT.defaultBlockState() : BLACK);
            if (fz == -9) set(level, pos, wx, F + 1, wz, stairs(Direction.NORTH, false));
            if (fz == -10 && ax == 4) for (int dy = F + 3; dy <= F + 5; dy++) set(level, pos, wx, dy, wz, Blocks.IRON_BARS.defaultBlockState());
            if (fz == -11 && (ax == 2)) chest(level, pos, random, wx, F + 3, wz, Direction.SOUTH, LOOT);
            if (fz == -11 && ax == 0) {
                set(level, pos, wx, F + 3, wz, Blocks.SMITHING_TABLE.defaultBlockState());
                set(level, pos, wx, F + 4, wz, Blocks.LANTERN.defaultBlockState());
            }
            if (fz == -11 && ax == 4) set(level, pos, wx, F + 3, wz, Blocks.DECORATED_POT.defaultBlockState());
            if (fz == -10 && ax == 0) spawner(level, pos, wx, F + 3, wz, WakingWorld.RUNE_SENTINEL.get());
            return;
        }
        // the workshops along the aisles: anvils, grindstones, quenching troughs, tool chests, smokers
        if (afz == 5 && ax == 4) set(level, pos, wx, F + 1, wz, Blocks.ANVIL.defaultBlockState());
        if (afz == 5 && ax == 8) set(level, pos, wx, F + 1, wz, Blocks.GRINDSTONE.defaultBlockState());
        if (afz == 3 && ax == 8) set(level, pos, wx, F + 1, wz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        if (afz == 7 && ax == 4 && fz > 0) chest(level, pos, random, wx, F + 1, wz, Direction.NORTH, LOOT);
        if (afz == 7 && ax == 4 && fz < 0) set(level, pos, wx, F + 1, wz, Blocks.SMITHING_TABLE.defaultBlockState());
        if (afz == 3 && ax == 4) set(level, pos, wx, F + 1, wz, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, fz > 0 ? Direction.SOUTH : Direction.NORTH).setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true));
        if (afz == 0 && ax == 8) set(level, pos, wx, F + 1, wz, Blocks.CHIPPED_ANVIL.defaultBlockState());
        // the wraiths' spawners: in the aisles' far corners and beside the furnace
        if (ax == 8 && afz == 8 && lx * fz > 0) spawner(level, pos, wx, F + 1, wz, WakingWorld.EMBER_WRAITH.get());
        if (lx == 4 && fz == 0) spawner(level, pos, wx, F + 1, wz, WakingWorld.EMBER_WRAITH.get());
    }
}
