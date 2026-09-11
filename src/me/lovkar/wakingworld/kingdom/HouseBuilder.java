package me.lovkar.wakingworld.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The houses of a kingdom's suburb, drawn in a local frame and turned to face their lane.
 *
 * <p>Every design is written once, facing SOUTH: {@code u} runs along the facade (+u is to the right of
 * somebody standing in the lane looking at the door), {@code v} runs from the facade wall into the house
 * (so +v is north on the drawing), {@code y} is height with 0 the first air block over the floor - the
 * door's lower half stands at y 0, the floor itself is at y -1. {@link Frame} maps that onto the world
 * for any of the four facings and rotates every directional block state with it, so a stair that climbs
 * toward the ridge on the drawing climbs toward the ridge in the world.
 *
 * <p>The ground is whatever the terrain says: walls are footed with the plinth stone down to the ground
 * under every column, the floor is laid over whatever was there, and the room is cleared to air. The
 * mason only ever places into air or into natural ground, so a plot somebody has built on is protected
 * by the site being refused before a plan is drawn, not by anything here.
 */
public final class HouseBuilder {
    private HouseBuilder() {
    }

    /** Where the ground is. */
    public interface Terrain {
        int groundY(int x, int z);
    }

    /** The wood, the wall fill and the roof of one house. Chosen by the plot, so a lane is never two of the same in a row. */
    public record Palette(BlockState log, BlockState planks, BlockState infill, BlockState plinth, BlockState plinthAlt,
                          Block roofStairs, Block roofSlab, BlockState roofBlock, Block door, Block trapdoor, Block fence, Block leaves, String name) {
        public static Palette of(int seed) {
            return ALL[Math.floorMod(seed, ALL.length)];
        }

        public static final Palette[] ALL = {
            new Palette(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                    Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                    Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE, Blocks.OAK_LEAVES, "oak and lime"),
            new Palette(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LEAVES, "spruce and mud"),
            new Palette(Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
                    Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LEAVES, "dark oak and ash"),
            new Palette(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                    Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS.defaultBlockState(),
                    Blocks.BIRCH_DOOR, Blocks.BIRCH_TRAPDOOR, Blocks.BIRCH_FENCE, Blocks.BIRCH_LEAVES, "birch and tile"),
            new Palette(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                    Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_FENCE, Blocks.OAK_LEAVES, "spruce and lime"),
        };
    }

    // ------------------------------------------------------------------ the frame

    /** A local drawing frame: the origin is the doorstep (where the door's lower half stands), the facing is where the lane is. */
    public static final class Frame {
        final KingdomBuild.Plan plan;
        final Terrain terrain;
        final BlockPos origin;
        final Direction facing;   // out of the door, toward the lane
        final Direction right;    // +u
        final Direction depth;    // +v, into the house
        final Rotation rotation;
        int placed;

        public Frame(KingdomBuild.Plan plan, Terrain terrain, BlockPos doorstep, Direction facing) {
            this.plan = plan;
            this.terrain = terrain;
            this.origin = doorstep;
            this.facing = facing;
            this.right = facing.getCounterClockWise();
            this.depth = facing.getOpposite();
            this.rotation = switch (facing) {
                case WEST -> Rotation.CLOCKWISE_90;
                case NORTH -> Rotation.CLOCKWISE_180;
                case EAST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }

        public BlockPos at(int u, int y, int v) {
            return origin.offset(right.getStepX() * u + depth.getStepX() * v, y, right.getStepZ() * u + depth.getStepZ() * v);
        }

        /** The ground under a local column as a local y: -1 means the floor level is the ground itself. */
        int ground(int u, int v) {
            BlockPos p = at(u, 0, v);
            return terrain.groundY(p.getX(), p.getZ()) - origin.getY();
        }

        public void put(int u, int y, int v, BlockState state) {
            BlockPos p = at(u, y, v);
            plan.set(p.getX(), p.getY(), p.getZ(), state.rotate(rotation));
            placed++;
        }

        void fill(int u0, int u1, int y0, int y1, int v0, int v1, BlockState state) {
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
                for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++)
                    for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) put(u, y, v, state);
        }

        void air(int u0, int u1, int y0, int y1, int v0, int v1) {
            fill(u0, u1, y0, y1, v0, v1, AIR);
        }

        /** Plinth stone from just under the floor down to the ground, so a wall on a slope stands on something. */
        void foot(int u, int v, BlockState stone, BlockState alt) {
            int g = ground(u, v);
            for (int y = -1; y >= Math.min(-1, g); y--) put(u, y, v, hash(u, y, v) % 5 == 0 ? alt : stone);
        }

        int hash(int a, int b, int c) {
            int h = (origin.getX() + a) * 668265261 ^ (origin.getZ() + c) * 374761393 ^ (b + 17) * 1274126177;
            h ^= h >>> 15;
            h *= 625341585;
            return (h ^ h >>> 13) & 0x7fffffff;
        }
    }

    // ------------------------------------------------------------------ block shorthands (all on the SOUTH-facing drawing)

    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** A stair whose high side is toward {@code high} - the direction it climbs toward. */
    static BlockState stair(Block stairs, Direction high, boolean upsideDown) {
        return stairs.defaultBlockState().setValue(StairBlock.FACING, high).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }

    static BlockState slab(Block slab, boolean top) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState log(BlockState log, Direction.Axis axis) {
        return log.setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** A shutter hung flat against a wall: the trapdoor stands in the block outside the wall, {@code out} is the wall's outward normal. */
    static BlockState shutter(Block trapdoor, Direction out) {
        return trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, out).setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    static BlockState leaves(Block leaves) {
        return leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState campfire() {
        return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false);
    }

    static BlockState facing(Block block, Direction d) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
    }

    static BlockState chest(Direction d) {
        return facing(Blocks.CHEST, d);
    }

    /** The door faces SOUTH, out of the house; the hinge side decides which way it swings. */
    static void door(Frame f, Block door, int u, int v, boolean hingeRight) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HINGE, hingeRight ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        f.put(u, 0, v, lower);
        f.put(u, 1, v, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A bed with its foot at (u, v) and its head one block toward {@code head} - FACING points foot to head. */
    static void bed(Frame f, Block bed, int u, int y, int v, Direction head) {
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, head).setValue(BedBlock.PART, BedPart.FOOT);
        f.put(u, y, v, foot);
        f.put(u + head.getStepX(), y, v - head.getStepZ(), foot.setValue(BedBlock.PART, BedPart.HEAD)); // on the drawing north is +v
    }

    // ------------------------------------------------------------------ the parts every house is made of

    /** A ring of wall y0..y1: corner posts of log, {@code infill} between. */
    private static void walls(Frame f, Palette p, int hw, int v0, int v1, int y0, int y1, BlockState infill) {
        for (int y = y0; y <= y1; y++) {
            for (int u = -hw; u <= hw; u++) {
                for (int v = v0; v <= v1; v++) {
                    boolean edge = Math.abs(u) == hw || v == v0 || v == v1;
                    if (!edge) continue;
                    boolean corner = Math.abs(u) == hw && (v == v0 || v == v1);
                    f.put(u, y, v, corner ? log(p.log, Direction.Axis.Y) : infill);
                }
            }
        }
    }

    /** The wall plate: a ring of horizontal logs along each wall at height y. */
    private static void plate(Frame f, Palette p, int hw, int v0, int v1, int y) {
        for (int u = -hw; u <= hw; u++) {
            f.put(u, y, v0, log(p.log, Direction.Axis.X));
            f.put(u, y, v1, log(p.log, Direction.Axis.X));
        }
        for (int v = v0 + 1; v < v1; v++) {
            f.put(-hw, y, v, log(p.log, Direction.Axis.Z));
            f.put(hw, y, v, log(p.log, Direction.Axis.Z));
        }
        for (int v = v0; v <= v1; v += v1 - v0) {
            f.put(-hw, y, v, log(p.log, Direction.Axis.Y));
            f.put(hw, y, v, log(p.log, Direction.Axis.Y));
        }
    }

    /**
     * A gable roof whose ridge runs along v (front to back), eaves one block out on every side, the gable
     * triangles at v0 and v1 closed with {@code gable} between log posts. The lowest course of stairs sits
     * at {@code y0} on {@code u = ±(hw + 1)}; the ridge cap ends up at {@code y0 + hw + 1}, which is returned.
     */
    private static int gableRoof(Frame f, Palette p, Block stairs, Block slabs, BlockState ridgeBlock, int hw, int v0, int v1, int y0, BlockState gable) {
        int ridgeY = y0 + hw + 1;
        // the attic is cleared first, so a tree that stood on the plot is not left inside the roof
        for (int k = 1; k <= hw; k++) for (int u = -(hw - k); u <= hw - k; u++) for (int v = v0 + 1; v < v1; v++) f.put(u, y0 + k, v, AIR);
        for (int k = 0; k <= hw; k++) {
            int u = hw + 1 - k;
            int y = y0 + k;
            for (int v = v0 - 1; v <= v1 + 1; v++) {
                f.put(-u, y, v, stair(stairs, Direction.EAST, false));   // the west slope climbs east, toward the ridge
                f.put(u, y, v, stair(stairs, Direction.WEST, false));
            }
        }
        for (int v = v0 - 1; v <= v1 + 1; v++) {
            f.put(0, ridgeY - 1, v, ridgeBlock);        // the ridge beam
            f.put(0, ridgeY, v, slab(slabs, false));   // and the cap on it
        }
        // the gable walls: a triangle over the plate at both ends, posts up its edges
        for (int k = 1; k <= hw; k++) {
            int y = y0 + k;
            int reach = hw - k;
            for (int u = -reach; u <= reach; u++) {
                BlockState s = Math.abs(u) == reach ? log(p.log, Direction.Axis.Y) : gable;
                f.put(u, y, v0, s);
                f.put(u, y, v1, s);
            }
        }
        return ridgeY;
    }

    /** A window: the pane, a shutter to each side hung on the outside, a folded trapdoor as the sill below. Only for the facade (out = SOUTH). */
    private static void window(Frame f, Palette p, int u, int y, int v) {
        f.put(u, y, v, Blocks.GLASS_PANE.defaultBlockState());
        f.put(u - 1, y, v - 1, shutter(p.trapdoor, Direction.SOUTH));
        f.put(u + 1, y, v - 1, shutter(p.trapdoor, Direction.SOUTH));
        f.put(u, y - 1, v - 1, p.trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.SOUTH).setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, false));
    }

    private static void pane(Frame f, int u, int y, int v) {
        f.put(u, y, v, Blocks.GLASS_PANE.defaultBlockState());
    }

    /** Foots every column, lays the floor (plinth under the walls, {@code floor} inside) and clears the room to {@code height}. */
    private static void floor(Frame f, Palette p, int hw, int v0, int v1, int height, BlockState floor) {
        for (int u = -hw; u <= hw; u++) {
            for (int v = v0; v <= v1; v++) {
                f.foot(u, v, p.plinth, p.plinthAlt);
                boolean edge = Math.abs(u) == hw || v == v0 || v == v1;
                f.put(u, -1, v, edge ? p.plinth : floor);
                if (!edge) f.air(u, u, 0, height, v, v);
            }
        }
    }

    /** A brick flue from y0 up to {@code top}, the fire on top of it. */
    private static void chimney(Frame f, int u, int v, int y0, int top) {
        for (int y = y0; y < top; y++) f.put(u, y, v, Blocks.BRICKS.defaultBlockState());
        f.put(u, top, v, campfire());
    }

    /** A hearth in the room: the fire in a brick surround with its flue going up through the roof. */
    private static void hearth(Frame f, int u, int v, int roofTop) {
        f.put(u, 0, v, campfire());
        f.put(u, 1, v, Blocks.BRICKS.defaultBlockState());
        for (int y = 2; y < roofTop; y++) f.put(u, y, v, Blocks.BRICKS.defaultBlockState());
        f.put(u, roofTop, v, campfire());
    }

    private static void table(Frame f, Palette p, int u, int v) {
        f.put(u, 0, v, p.fence.defaultBlockState());
        f.put(u, 1, v, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
    }

    private static final BlockState[] POTS = {
        Blocks.POTTED_POPPY.defaultBlockState(), Blocks.POTTED_DANDELION.defaultBlockState(), Blocks.POTTED_CORNFLOWER.defaultBlockState(),
        Blocks.POTTED_AZURE_BLUET.defaultBlockState(), Blocks.POTTED_RED_TULIP.defaultBlockState(), Blocks.POTTED_OXEYE_DAISY.defaultBlockState()};

    private static BlockState pot(Frame f, int u, int v) {
        return POTS[f.hash(u, 3, v) % POTS.length];
    }

    /** The lowest course of the walls in the plinth stone, the corner posts left alone, the door left open. */
    private static void plinthCourse(Frame f, Palette p, int hw, int v0, int v1, int doorU) {
        for (int u = -hw + 1; u < hw; u++) {
            if (u != doorU) f.put(u, 0, v0, p.plinth);
            f.put(u, 0, v1, p.plinth);
        }
        for (int v = v0 + 1; v < v1; v++) {
            f.put(-hw, 0, v, p.plinth);
            f.put(hw, 0, v, p.plinth);
        }
    }

    /** A little hood over the door: three roof stairs turned up, held against the wall. */
    private static void awning(Frame f, Palette p, int u, int y, int v) {
        for (int du = -1; du <= 1; du++) f.put(u + du, y, v, stair(p.roofStairs, Direction.NORTH, true));
    }

    private static void doorstep(Frame f, int u0, int u1) {
        for (int u = u0; u <= u1; u++) f.put(u, -1, -1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
    }

    private static void ladder(Frame f, int u, int v, int y0, int y1) {
        for (int y = y0; y <= y1; y++) f.put(u, y, v, Blocks.LADDER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
    }

    // ------------------------------------------------------------------ the designs

    /** 7 x 7, one room, the gable to the lane. The bread and butter of every lane. */
    public static void cottage(Frame f, Palette p) {
        int hw = 3, v1 = 6;
        floor(f, p, hw, 0, v1, 6, p.planks);
        walls(f, p, hw, 0, v1, 0, 2, p.infill);
        plinthCourse(f, p, hw, 0, v1, 0);
        plate(f, p, hw, 0, v1, 3);
        // the frame on the long walls: a post in the middle bay, the plank brace over its window
        for (int y = 1; y <= 2; y++) { f.put(-hw, y, 3, y == 1 ? Blocks.GLASS_PANE.defaultBlockState() : p.planks); f.put(hw, y, 3, y == 1 ? Blocks.GLASS_PANE.defaultBlockState() : p.planks); }
        door(f, p.door, 0, 0, false);
        awning(f, p, 0, 2, -1);
        window(f, p, -2, 1, 0);
        window(f, p, 2, 1, 0);
        pane(f, 0, 1, v1);
        int ridge = gableRoof(f, p, p.roofStairs, p.roofSlab, p.roofBlock, hw, 0, v1, 3, p.infill);
        chimney(f, 2, 5, 3, ridge - 1);
        // the beam across the room carries the lamp
        for (int u = -hw + 1; u < hw; u++) f.put(u, 3, 3, log(p.log, Direction.Axis.X));
        f.put(0, 2, 3, lantern(true));
        // inside: the bed under the back window, the table by the door, the chest and the barrel, a rug
        bed(f, Blocks.RED_BED, -2, 0, 4, Direction.NORTH);
        f.put(2, 0, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
        f.put(2, 0, 4, Blocks.BARREL.defaultBlockState());
        f.put(1, 0, 5, chest(Direction.SOUTH));
        table(f, p, -2, 1);
        f.put(2, 0, 1, stair(Blocks.OAK_STAIRS, Direction.EAST, false));
        f.put(0, 0, 3, Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
        f.put(0, 0, 2, Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
        f.put(-1, 0, 3, Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
        // the doorstep, the lanterns under the eaves, and flowers either side of the door
        doorstep(f, 0, 0);
        f.put(-hw - 1, 2, -1, lantern(true));
        f.put(hw + 1, 2, -1, lantern(true));
        f.put(-1, 0, -1, pot(f, -1, -1));
        f.put(1, 0, -1, pot(f, 1, -1));
    }

    /** 11 x 7, the door in the long side under a long roof, two rooms and the hearth - a farmer's house. */
    public static void longhouse(Frame f, Palette p) {
        int hw = 5, v1 = 6;
        floor(f, p, hw, 0, v1, 6, p.planks);
        walls(f, p, hw, 0, v1, 0, 2, p.infill);
        plinthCourse(f, p, hw, 0, v1, 0);
        // posts every third block along the long walls, so the frame reads as bays
        for (int u = -2; u <= 2; u += 4) for (int y = 0; y <= 2; y++) { f.put(u, y, 0, log(p.log, Direction.Axis.Y)); f.put(u, y, v1, log(p.log, Direction.Axis.Y)); }
        plate(f, p, hw, 0, v1, 3);
        door(f, p.door, 0, 0, false);
        awning(f, p, 0, 2, -1);
        window(f, p, -4, 1, 0);
        window(f, p, 4, 1, 0);
        pane(f, -4, 1, v1);
        pane(f, 0, 1, v1);
        pane(f, 4, 1, v1);
        pane(f, -hw, 1, 3);
        pane(f, hw, 1, 3);
        // the roof runs the long way: the ridge along u at v 3, the slopes climbing from the front and back eaves
        int half = 3, y0 = 3, ridgeY = y0 + half + 1;
        for (int k = 1; k <= half; k++) for (int u = -hw + 1; u < hw; u++) for (int v = k; v <= v1 - k; v++) f.put(u, y0 + k, v, AIR);
        for (int k = 0; k <= half; k++) {
            int y = y0 + k;
            for (int u = -hw - 1; u <= hw + 1; u++) {
                f.put(u, y, -1 + k, stair(p.roofStairs, Direction.NORTH, false));      // the front slope climbs north, into the house
                f.put(u, y, v1 + 1 - k, stair(p.roofStairs, Direction.SOUTH, false));
            }
        }
        for (int u = -hw - 1; u <= hw + 1; u++) { f.put(u, ridgeY - 1, half, p.roofBlock); f.put(u, ridgeY, half, slab(p.roofSlab, false)); }
        for (int k = 1; k <= half; k++) {
            int y = y0 + k;
            int reach = half - k;
            for (int v = half - reach; v <= half + reach; v++) {
                BlockState s = v == half - reach || v == half + reach ? log(p.log, Direction.Axis.Y) : p.infill;
                f.put(-hw, y, v, s);
                f.put(hw, y, v, s);
            }
        }
        f.put(-hw - 1, 2, -1, lantern(true));
        f.put(hw + 1, 2, -1, lantern(true));
        // the hearth on the end wall, its flue up through the roof; the long table; the beds at the other end
        hearth(f, 4, 5, ridgeY - 1);
        f.put(3, 0, 5, facing(Blocks.SMOKER, Direction.SOUTH));
        f.put(4, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
        f.put(3, 0, 1, Blocks.COMPOSTER.defaultBlockState());
        for (int u = -1; u <= 1; u++) table(f, p, u, 3);
        f.put(-2, 0, 3, stair(Blocks.OAK_STAIRS, Direction.EAST, false));
        f.put(2, 0, 3, stair(Blocks.OAK_STAIRS, Direction.WEST, false));
        bed(f, Blocks.WHITE_BED, -4, 0, 4, Direction.NORTH);
        bed(f, Blocks.RED_BED, -2, 0, 4, Direction.NORTH);
        f.put(-4, 0, 1, Blocks.BARREL.defaultBlockState());
        f.put(-4, 0, 2, chest(Direction.EAST));
        for (int u = -hw + 1; u < hw; u++) f.put(u, 3, 3, log(p.log, Direction.Axis.X));
        f.put(-3, 2, 3, lantern(true));
        f.put(3, 2, 3, lantern(true));
        doorstep(f, 0, 0);
        f.put(-1, 0, -1, pot(f, -1, -1));
        f.put(1, 0, -1, pot(f, 1, -1));
    }

    /** 7 x 7 in stone below, a jettied timber storey above on stair corbels, the gable to the lane - a townhouse. */
    public static void townhouse(Frame f, Palette p) {
        int hw = 3, v1 = 6, hw2 = 4, v0u = -1;
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        floor(f, p, hw, 0, v1, 2, p.planks);
        walls(f, p, hw, 0, v1, 0, 2, stone);
        for (int u = -hw; u <= hw; u++) if (u != 0) f.put(u, 0, 0, p.plinth);
        door(f, p.door, 0, 0, false);
        pane(f, -2, 1, 0);
        pane(f, 2, 1, 0);
        pane(f, -hw, 1, 3);
        pane(f, hw, 1, 3);
        pane(f, 0, 1, v1);
        plate(f, p, hw, 0, v1, 3);
        // the jetty: the upper floor hangs a block out over the lane and both sides, carried on corbels
        for (int u = -hw2; u <= hw2; u++) for (int v = v0u; v <= v1; v++) {
            boolean edge = Math.abs(u) == hw2 || v == v0u;
            f.put(u, 3, v, edge ? log(p.log, v == v0u ? Direction.Axis.X : Direction.Axis.Z) : (Math.abs(u) <= hw && v >= 0 && v <= v1 && (Math.abs(u) == hw || v == v1) ? log(p.log, v == v1 ? Direction.Axis.X : Direction.Axis.Z) : p.planks));
        }
        for (int u = -hw; u <= hw; u++) f.put(u, 2, v0u, stair(p.roofStairs, Direction.NORTH, true));
        for (int v = 0; v <= v1; v++) { f.put(-hw2, 2, v, stair(p.roofStairs, Direction.EAST, true)); f.put(hw2, 2, v, stair(p.roofStairs, Direction.WEST, true)); }
        // the upper storey: studs every other block, infill between, the windows in the bays
        walls(f, p, hw2, v0u, v1, 4, 5, p.infill);
        for (int y = 4; y <= 5; y++) {
            for (int u = -2; u <= 2; u += 2) { f.put(u, y, v0u, log(p.log, Direction.Axis.Y)); f.put(u, y, v1, log(p.log, Direction.Axis.Y)); }
            for (int v = 1; v <= 5; v += 2) { f.put(-hw2, y, v, log(p.log, Direction.Axis.Y)); f.put(hw2, y, v, log(p.log, Direction.Axis.Y)); }
        }
        pane(f, -1, 4, v0u);
        pane(f, 1, 4, v0u);
        pane(f, -3, 4, v0u);
        pane(f, 3, 4, v0u);
        pane(f, -hw2, 4, 2);
        pane(f, -hw2, 4, 4);
        pane(f, hw2, 4, 2);
        pane(f, hw2, 4, 4);
        pane(f, -1, 4, v1);
        pane(f, 1, 4, v1);
        f.air(-hw2 + 1, hw2 - 1, 4, 5, v0u + 1, v1 - 1);
        plate(f, p, hw2, v0u, v1, 6);
        int ridge = gableRoof(f, p, p.roofStairs, p.roofSlab, p.roofBlock, hw2, v0u, v1, 6, p.infill);
        chimney(f, 3, 5, 6, ridge - 2);
        f.put(-hw2 - 1, 5, v0u - 1, lantern(true));
        f.put(hw2 + 1, 5, v0u - 1, lantern(true));
        // the ladder up through the back corner of the floor
        f.put(hw - 1, 3, v1 - 1, AIR);
        ladder(f, hw - 1, v1 - 1, 0, 3);
        // downstairs a shop: the counter, the shelves, the goods; upstairs the beds
        for (int v = 2; v <= 4; v++) { f.put(-2, 0, v, p.fence.defaultBlockState()); f.put(-2, 1, v, slab(p.roofSlab, false)); }
        f.put(-1, 0, 5, Blocks.BOOKSHELF.defaultBlockState());
        f.put(0, 0, 5, Blocks.BOOKSHELF.defaultBlockState());
        f.put(1, 0, 5, chest(Direction.SOUTH));
        f.put(-1, 1, 5, Blocks.BOOKSHELF.defaultBlockState());
        f.put(0, 1, 5, Blocks.BOOKSHELF.defaultBlockState());
        f.put(2, 0, 1, Blocks.BARREL.defaultBlockState());
        f.put(2, 0, 2, Blocks.BARREL.defaultBlockState());
        f.put(2, 1, 2, Blocks.BARREL.defaultBlockState());
        f.put(0, 2, 2, lantern(true));
        bed(f, Blocks.BLUE_BED, -3, 4, 3, Direction.NORTH);
        bed(f, Blocks.BLUE_BED, 1, 4, 3, Direction.NORTH);
        f.put(3, 4, 5, chest(Direction.SOUTH));
        f.put(-3, 4, 1, Blocks.BARREL.defaultBlockState());
        f.put(0, 6, 2, log(p.log, Direction.Axis.X));
        f.put(0, 5, 2, lantern(true));
        doorstep(f, -1, 1);
        f.put(-2, 0, -1, pot(f, -2, -1));
        f.put(2, 0, -1, pot(f, 2, -1));
    }

    /** 7 x 7, the front open between the posts: the forge, the anvil, the quench, the flue over it. */
    public static void smithy(Frame f, Palette p) {
        int hw = 3, v1 = 6;
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        floor(f, p, hw, 0, v1, 6, Blocks.STONE_BRICKS.defaultBlockState());
        walls(f, p, hw, 0, v1, 0, 2, cobble);
        f.air(-2, 2, 0, 2, 0, 0);
        for (int y = 0; y <= 2; y++) f.put(0, y, 0, log(p.log, Direction.Axis.Y));
        plate(f, p, hw, 0, v1, 3);
        pane(f, -hw, 1, 3);
        pane(f, hw, 1, 2);
        int ridge = gableRoof(f, p, p.roofStairs, p.roofSlab, p.roofBlock, hw, 0, v1, 3, cobble);
        // the forge against the back wall with its own flue
        f.put(-2, 0, 5, facing(Blocks.BLAST_FURNACE, Direction.SOUTH));
        f.put(-1, 0, 5, facing(Blocks.FURNACE, Direction.SOUTH));
        f.put(-2, 1, 5, Blocks.BRICKS.defaultBlockState());
        f.put(-1, 1, 5, Blocks.BRICKS.defaultBlockState());
        f.put(-2, 2, 5, Blocks.BRICKS.defaultBlockState());
        chimney(f, -2, 5, 3, ridge - 1);
        f.put(1, 0, 4, facing(Blocks.ANVIL, Direction.EAST));
        f.put(2, 0, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        f.put(2, 0, 3, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR).setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        f.put(-2, 0, 3, Blocks.SMITHING_TABLE.defaultBlockState());
        f.put(-2, 0, 2, chest(Direction.EAST));
        f.put(2, 0, 1, Blocks.BARREL.defaultBlockState());
        f.put(0, 2, 3, lantern(true));
        for (int u = -hw + 1; u < hw; u++) f.put(u, 3, 3, log(p.log, Direction.Axis.X));
        // outside: the lantern post and the barrel of scrap by the open front
        f.put(-hw - 1, 0, -1, Blocks.COBBLESTONE_WALL.defaultBlockState());
        f.put(-hw - 1, 1, -1, lantern(false));
        f.put(hw + 1, 0, -1, Blocks.BARREL.defaultBlockState());
        doorstep(f, -1, 1);
    }

    /** 9 x 9, two storeys, the sign and the lanterns over the door: the tavern. Beds upstairs. */
    public static void tavern(Frame f, Palette p) {
        int hw = 4, v1 = 8;
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        floor(f, p, hw, 0, v1, 2, p.planks);
        walls(f, p, hw, 0, v1, 0, 2, stone);
        for (int u = -hw; u <= hw; u++) if (Math.abs(u) > 1) f.put(u, 0, 0, p.plinth);
        door(f, p.door, 0, 0, false);
        pane(f, -2, 1, 0);
        pane(f, 2, 1, 0);
        pane(f, -3, 1, 0);
        pane(f, 3, 1, 0);
        for (int v = 2; v <= v1 - 2; v += 2) { pane(f, -hw, 1, v); pane(f, hw, 1, v); }
        pane(f, -2, 1, v1);
        pane(f, 2, 1, v1);
        plate(f, p, hw, 0, v1, 3);
        // the upper storey straight above, timber framed, the studs on every other block
        for (int u = -hw + 1; u < hw; u++) for (int v = 1; v < v1; v++) f.put(u, 3, v, p.planks);
        walls(f, p, hw, 0, v1, 4, 5, p.infill);
        for (int y = 4; y <= 5; y++) {
            for (int u = -hw; u <= hw; u += 2) { f.put(u, y, 0, log(p.log, Direction.Axis.Y)); f.put(u, y, v1, log(p.log, Direction.Axis.Y)); }
            for (int v = 0; v <= v1; v += 2) { f.put(-hw, y, v, log(p.log, Direction.Axis.Y)); f.put(hw, y, v, log(p.log, Direction.Axis.Y)); }
        }
        for (int u = -3; u <= 3; u += 2) { pane(f, u, 4, 0); pane(f, u, 4, v1); }
        for (int v = 1; v < v1; v += 2) { pane(f, -hw, 4, v); pane(f, hw, 4, v); }
        f.air(-hw + 1, hw - 1, 4, 6, 1, v1 - 1);
        plate(f, p, hw, 0, v1, 6);
        int ridge = gableRoof(f, p, p.roofStairs, p.roofSlab, p.roofBlock, hw, 0, v1, 6, p.infill);
        f.put(hw - 1, 3, v1 - 1, AIR);
        ladder(f, hw - 1, v1 - 1, 0, 3);
        // the sign over the door on its bracket, a lantern hung either side
        f.put(1, 3, -1, log(p.log, Direction.Axis.X));
        f.put(2, 3, -1, log(p.log, Direction.Axis.X));
        f.put(2, 2, -1, log(p.log, Direction.Axis.Y));
        f.put(1, 2, -1, facing(Blocks.OAK_WALL_HANGING_SIGN, Direction.SOUTH));
        f.put(-1, 3, -1, log(p.log, Direction.Axis.X));
        f.put(-2, 3, -1, log(p.log, Direction.Axis.X));
        f.put(-1, 2, -1, lantern(true));
        f.put(-2, 2, -1, lantern(true));
        // the taproom: the bar along the right wall, two tables, the hearth in the back corner, casks
        for (int v = 2; v <= 5; v++) { f.put(2, 0, v, p.fence.defaultBlockState()); f.put(2, 1, v, slab(p.roofSlab, false)); }
        for (int v = 2; v <= 6; v++) f.put(3, 0, v, Blocks.BARREL.defaultBlockState());
        f.put(3, 1, 5, Blocks.BARREL.defaultBlockState());
        f.put(3, 1, 6, Blocks.BARREL.defaultBlockState());
        table(f, p, -2, 2);
        table(f, p, -2, 5);
        f.put(-3, 0, 2, stair(Blocks.OAK_STAIRS, Direction.EAST, false));
        f.put(-1, 0, 2, stair(Blocks.OAK_STAIRS, Direction.WEST, false));
        f.put(-3, 0, 5, stair(Blocks.OAK_STAIRS, Direction.EAST, false));
        f.put(-1, 0, 5, stair(Blocks.OAK_STAIRS, Direction.WEST, false));
        hearth(f, -3, 7, ridge - 2);
        f.put(-1, 0, 7, Blocks.CAKE.defaultBlockState());
        f.put(0, 2, 3, lantern(true));
        f.put(0, 2, 6, lantern(true));
        // the beds upstairs
        bed(f, Blocks.RED_BED, -3, 4, 1, Direction.NORTH);
        bed(f, Blocks.RED_BED, -1, 4, 1, Direction.NORTH);
        bed(f, Blocks.RED_BED, 1, 4, 1, Direction.NORTH);
        bed(f, Blocks.WHITE_BED, -3, 4, 6, Direction.NORTH);
        bed(f, Blocks.WHITE_BED, -1, 4, 6, Direction.NORTH);
        f.put(1, 4, 7, chest(Direction.SOUTH));
        f.put(2, 4, 7, chest(Direction.SOUTH));
        f.put(0, 6, 4, log(p.log, Direction.Axis.X));
        f.put(0, 5, 4, lantern(true));
        doorstep(f, -1, 1);
    }

    /** 7 x 11 in stone, tall windows in the town's colour, a belfry over the door. Only a city has one. */
    public static void chapel(Frame f, Palette p) {
        int hw = 3, v1 = 10;
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState chiseled = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.CYAN_STAINED_GLASS_PANE.defaultBlockState();
        floor(f, p, hw, 0, v1, 9, Blocks.POLISHED_ANDESITE.defaultBlockState());
        walls(f, p, hw, 0, v1, 0, 4, stone);
        for (int y = 0; y <= 4; y++) for (int v = 0; v <= v1; v += 5) { f.put(-hw, y, v, chiseled); f.put(hw, y, v, chiseled); }
        for (int y = 0; y <= 4; y++) { f.put(-hw, y, 0, chiseled); f.put(hw, y, 0, chiseled); f.put(-hw, y, v1, chiseled); f.put(hw, y, v1, chiseled); }
        for (int v = 2; v <= v1 - 2; v += 3) for (int y = 1; y <= 3; y++) { f.put(-hw, y, v, glass); f.put(hw, y, v, glass); }
        for (int y = 1; y <= 3; y++) f.put(0, y, v1, glass);
        f.put(-1, 2, v1, glass);
        f.put(1, 2, v1, glass);
        door(f, Blocks.DARK_OAK_DOOR, 0, 0, false);
        // the steep tiled roof
        int ridge = gableRoof(f, p, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES.defaultBlockState(), hw, 0, v1, 5, stone);
        for (int k = 1; k <= hw; k++) for (int u = -(hw - k); u <= hw - k; u++) { f.put(u, 5 + k, 0, stone); f.put(u, 5 + k, v1, stone); }
        // the belfry: a 3 x 3 tower on the front bay up past the ridge, open arches round the bell, a tiled cap
        int top = ridge + 2;
        for (int y = 5; y <= top; y++) {
            for (int u = -1; u <= 1; u++) for (int v = 0; v <= 2; v++) {
                boolean edge = Math.abs(u) == 1 || v == 0 || v == 2;
                boolean arch = y >= top - 3 && y <= top - 2 && (u == 0 || v == 1) && edge;
                boolean cap = y >= top - 1;
                f.put(u, y, v, arch ? AIR : (!edge && !cap ? AIR : (y == top ? chiseled : stone)));
            }
        }
        f.put(0, top - 2, 1, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, Direction.SOUTH));
        for (int u = -2; u <= 2; u++) for (int v = -1; v <= 3; v++) {
            int d = Math.max(Math.abs(u), Math.abs(v - 1));
            Direction toward = Math.abs(u) >= Math.abs(v - 1) ? (u < 0 ? Direction.EAST : Direction.WEST) : (v - 1 < 0 ? Direction.NORTH : Direction.SOUTH);
            if (d == 2) f.put(u, top + 1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, toward, false));
            else if (d == 1) f.put(u, top + 2, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, toward, false));
        }
        f.put(0, top + 2, 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        f.put(0, top + 3, 1, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        f.put(0, top + 4, 1, lantern(false));
        // inside: the carpet up the aisle, the benches, the lectern under the east window, the candles
        for (int v = 1; v < v1; v++) f.put(0, 0, v, Blocks.CYAN_CARPET.defaultBlockState());
        for (int v = 3; v <= v1 - 3; v += 2) for (int u = -2; u <= 2; u++) if (u != 0) f.put(u, 0, v, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        f.put(0, 0, v1 - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        f.put(0, 1, v1 - 1, facing(Blocks.LECTERN, Direction.SOUTH));
        f.put(-2, 0, v1 - 1, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
        f.put(2, 0, v1 - 1, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
        for (int v = 4; v <= v1 - 3; v += 3) { for (int y = 5; y <= 7; y++) f.put(0, y, v, Blocks.CHAIN.defaultBlockState()); f.put(0, 4, v, lantern(true)); }
        doorstep(f, -1, 1);
        f.put(-2, 0, -1, pot(f, -2, -1));
        f.put(2, 0, -1, pot(f, 2, -1));
    }

    // ------------------------------------------------------------------ the small things between the houses

    /** A well: a cobble rim round the water, two posts, a little roof, the bucket on its chain. */
    public static void well(Frame f, Palette p) {
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) {
            f.foot(u, v, Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            boolean rim = Math.abs(u) == 1 || Math.abs(v) == 1;
            f.put(u, -2, v, rim ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
            f.put(u, -1, v, rim ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
            f.put(u, 0, v, rim ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.WATER.defaultBlockState());
        }
        f.put(0, -3, 0, Blocks.COBBLESTONE.defaultBlockState());
        for (int y = 1; y <= 2; y++) { f.put(-1, y, 0, p.fence.defaultBlockState()); f.put(1, y, 0, p.fence.defaultBlockState()); }
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) f.put(u, 3, v, slab(p.roofSlab, false));
        f.put(0, 3, 0, p.roofBlock);
        f.put(0, 2, 0, Blocks.CHAIN.defaultBlockState());
        f.put(0, 1, 0, Blocks.CAULDRON.defaultBlockState());
        f.put(0, 4, 0, lantern(false));
    }

    /** A lamp post on the lane's verge. */
    public static void lamp(Frame f, Palette p) {
        f.foot(0, 0, Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        f.put(0, -1, 0, Blocks.COBBLESTONE.defaultBlockState());
        f.put(0, 0, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
        f.put(0, 1, 0, p.fence.defaultBlockState());
        f.put(0, 2, 0, p.fence.defaultBlockState());
        f.put(0, 3, 0, lantern(false));
    }

    /**
     * A kitchen garden: a fenced bed of crops watered down the middle. The bed is one level - the highest
     * ground under it, filled up with dirt where the slope falls away - because a channel dug into a
     * hillside is a spring, and the first one drained itself down the lane.
     */
    public static void garden(Frame f, Palette p, int hw, int depth) {
        BlockState[] crops = {
            Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7),
            Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE, 7), Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE, 3)};
        BlockState crop = crops[f.hash(hw, 5, depth) % crops.length];
        int top = Integer.MIN_VALUE;
        for (int u = -hw; u <= hw; u++) for (int v = 0; v <= depth; v++) top = Math.max(top, f.ground(u, v));
        for (int u = -hw; u <= hw; u++) for (int v = 0; v <= depth; v++) {
            int g = f.ground(u, v);
            for (int y = g + 1; y < top; y++) f.put(u, y, v, Blocks.DIRT.defaultBlockState());
            boolean edge = Math.abs(u) == hw || v == 0 || v == depth;
            if (edge) {
                f.put(u, top, v, Blocks.COARSE_DIRT.defaultBlockState());
                if (u == 0 && v == 0) continue;   // the way in stays open
                f.put(u, top + 1, v, p.fence.defaultBlockState());
            } else if (u == 0) {
                f.put(u, top, v, Blocks.WATER.defaultBlockState());
                f.put(u, top + 1, v, AIR);
            } else {
                f.put(u, top, v, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                f.put(u, top + 1, v, crop);
            }
            for (int y = top + 2; y <= top + 3; y++) f.put(u, y, v, AIR);
        }
        f.put(hw, top + 2, depth, lantern(false));
    }

    /** A tree of the kingdom's own: a trunk and a crown of persistent leaves, so it neither grows nor drops. */
    public static void tree(Frame f, Palette p, boolean big) {
        int g = f.ground(0, 0);
        int h = big ? 5 : 4;
        BlockState trunk = log(p.log.is(Blocks.STRIPPED_OAK_LOG) ? Blocks.OAK_LOG.defaultBlockState() : p.log, Direction.Axis.Y);
        BlockState leaf = leaves(p.leaves);
        for (int y = 1; y <= h; y++) f.put(0, g + y, 0, trunk);
        int r = big ? 2 : 1;
        for (int dy = -1; dy <= 1; dy++) {
            int rr = dy == 0 ? r + 1 : r;
            for (int u = -rr; u <= rr; u++) for (int v = -rr; v <= rr; v++) {
                if (u == 0 && v == 0 && dy <= 0) continue;
                if (Math.abs(u) + Math.abs(v) > rr + (dy == 0 ? 1 : 0)) continue;
                f.put(u, g + h + dy, v, leaf);
            }
        }
        f.put(0, g + h + 1, 0, leaf);
        f.put(0, g + h + 2, 0, leaf);
    }

    /** A hedge of leaves along u at the given v, on the ground - with a gap at u = 0 for the path when asked. */
    public static void hedge(Frame f, Palette p, int u0, int u1, int v, boolean gap) {
        for (int u = u0; u <= u1; u++) {
            if (gap && u == 0) continue;
            f.put(u, f.ground(u, v) + 1, v, leaves(p.leaves));
        }
    }

    /** A low cobble wall along u at the given v, with a gap at u = 0. */
    public static void yardWall(Frame f, int u0, int u1, int v) {
        for (int u = u0; u <= u1; u++) {
            if (u == 0) continue;
            f.put(u, f.ground(u, v) + 1, v, Blocks.COBBLESTONE_WALL.defaultBlockState());
        }
    }
}
