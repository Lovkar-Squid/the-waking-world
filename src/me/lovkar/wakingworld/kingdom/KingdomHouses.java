package me.lovkar.wakingworld.kingdom;

import java.util.ArrayList;
import java.util.List;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.worldgen.Tidy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The suburb: the houses a kingdom raises outside its walls as it grows, along the roads out of its two
 * gates - and, when those lanes are full or unfit, along two more lanes east and west.
 *
 * <p>Every road has a front row of plots facing it (doors four blocks off the road) and, behind a back
 * lane, a second row facing the same way. A plot is a fixed slot: the same kingdom always fills the same
 * places in the same order, so a town seen at tier 2 and again at tier 4 has grown, not shuffled. A slot
 * that turns out unfit (water, a cliff, somebody's build, one of the town's own works) is written down
 * and never tried again. A tier says how many houses the town keeps; a review raises up to {@link #PER_REVIEW}
 * of the shortfall, so growth is watched happening rather than found done.
 *
 * <p>What stands on a slot depends on how many houses the town already has: the first are cottages and
 * longhouses, a town gets its tavern and its smithy and the first townhouses, a city its chapel. When
 * the masons finish a house, its people arrive: townsfolk of the trade the house suggests, kept to the
 * lane they live on.
 */
public final class KingdomHouses {
    /** Houses per review while the town is short of what its tier keeps. */
    static final int PER_REVIEW = 3;
    /** The plot centres along a road, measured from the town's centre. The last plot's eave stays inside the march wall. */
    private static final int[] DEPTHS = {76, 87, 98};
    /** Where the doors stand off the road's centre line: the front row and, past the back lane, the second row. */
    private static final int FRONT = 4, BACK = 19, LANE_NEAR = 15, LANE_FAR = 16;
    /** Path blocks between the doorstep and the edge of the road (or the back lane): the same for both rows. */
    private static final int LANE_STEPS = 2;
    private static final int ROAD_END = 116;
    private static final int LANE_END = 106;
    /** Works are wide; a house keeps this far from the centre of one. */
    private static final double WORK_CLEARANCE = 15.0;
    /** How much the ground may rise and fall across a plot: the cut takes the hill, the plinth takes the hollow. */
    private static final int SLOPE = 12;
    /** Trees are felled this far beyond the plot, so no canopy hangs over a roof. */
    private static final int FELL = 4;
    /** The plot round a house: a step beyond the eaves to the sides and the front, the yard behind. */
    private static final int APRON = 2, YARD = 4;

    private KingdomHouses() {
    }

    /** How many houses a tier keeps outside the walls. */
    public static int wanted(int tier) {
        return switch (tier) {
            case 1 -> 0;
            case 2 -> 6;
            case 3 -> 13;
            default -> 22;
        };
    }

    // ------------------------------------------------------------------ the slots

    /** The four roads: south and north are the gate roads, east and west are lanes the suburb lays itself. */
    private static final Direction[] ROADS = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};

    record Slot(int index, int road, int depth, int side, boolean back) {
        Direction along() {
            return ROADS[road];
        }

        /** The lateral direction of side +1 (clockwise off the road, seen from above). */
        Direction lateral() {
            return along().getClockWise();
        }

        int off() {
            return back ? BACK : FRONT;
        }

        /** The doorstep column: down the road, then off to the side. */
        BlockPos column(BlockPos centre) {
            Direction a = along(), l = lateral();
            return centre.offset(a.getStepX() * depth + l.getStepX() * side * off(), 0, a.getStepZ() * depth + l.getStepZ() * side * off());
        }

        /** The house faces back toward the road. */
        Direction facing() {
            return side > 0 ? lateral().getOpposite() : lateral();
        }
    }

    /** The slots in the order they are filled: the gate roads first, front rows before back rows, the side lanes as overflow. */
    static List<Slot> slots() {
        List<Slot> out = new ArrayList<>();
        int i = 0;
        for (boolean back : new boolean[]{false, true})
            for (int d : DEPTHS)
                for (int road = 0; road < 2; road++)
                    for (int side = 1; side >= -1; side -= 2) out.add(new Slot(i++, road, d, side, back));
        for (boolean back : new boolean[]{false, true})
            for (int d : DEPTHS)
                for (int road = 2; road < 4; road++)
                    for (int side = 1; side >= -1; side -= 2) out.add(new Slot(i++, road, d, side, back));
        return out;
    }

    // ------------------------------------------------------------------ the kinds

    enum Kind {
        COTTAGE(3, 6, 8), LONGHOUSE(5, 6, 8), TOWNHOUSE(4, 6, 12), SMITHY(3, 6, 8), TAVERN(4, 8, 12), CHAPEL(3, 10, 17);

        final int hw, depth, height;

        Kind(int hw, int depth, int height) {
            this.hw = hw;
            this.depth = depth;
            this.height = height;
        }

        String key() {
            return name().toLowerCase();
        }
    }

    /** What the n-th house of a town is. */
    static Kind kindFor(int n) {
        Kind[] order = {
            Kind.COTTAGE, Kind.COTTAGE, Kind.LONGHOUSE, Kind.COTTAGE, Kind.COTTAGE, Kind.LONGHOUSE,                                        // a town
            Kind.TAVERN, Kind.SMITHY, Kind.TOWNHOUSE, Kind.COTTAGE, Kind.TOWNHOUSE, Kind.LONGHOUSE, Kind.COTTAGE,                          // a walled town
            Kind.CHAPEL, Kind.TOWNHOUSE, Kind.TOWNHOUSE, Kind.COTTAGE, Kind.LONGHOUSE, Kind.TOWNHOUSE, Kind.COTTAGE, Kind.TOWNHOUSE, Kind.COTTAGE // a city
        };
        return n < order.length ? order[n] : (n % 3 == 0 ? Kind.TOWNHOUSE : n % 3 == 1 ? Kind.COTTAGE : Kind.LONGHOUSE);
    }

    /** The trades that live in a house of this kind (townsfolk professions), one per resident. */
    static int[] residents(Kind kind, int seed) {
        return switch (kind) {
            case SMITHY -> new int[]{TownsfolkEntity.SMITH};
            case TAVERN -> new int[]{TownsfolkEntity.PROVISIONER, TownsfolkEntity.CHANDLER};
            case CHAPEL -> new int[]{TownsfolkEntity.SCRIBE};
            case TOWNHOUSE -> new int[]{seed % 2 == 0 ? TownsfolkEntity.SCRIBE : TownsfolkEntity.SURVEYOR};
            case LONGHOUSE -> new int[]{TownsfolkEntity.PROVISIONER, TownsfolkEntity.CHANDLER};
            default -> new int[]{seed % 2 == 0 ? TownsfolkEntity.PROVISIONER : TownsfolkEntity.CHANDLER};
        };
    }

    // ------------------------------------------------------------------ growing

    /**
     * Raises up to {@link #PER_REVIEW} houses if the town has fewer than its tier keeps. Called from the
     * review; returns how many were begun.
     */
    public static int grow(ServerLevel level, KingdomData data, KingdomData.Kingdom k, List<ServerPlayer> players) {
        return grow(level, data, k, players, Math.min(PER_REVIEW, wanted(k.tier) - k.houses.size()));
    }

    /** Raises up to {@code count} houses regardless of tier - the dev command's way in. */
    public static int grow(ServerLevel level, KingdomData data, KingdomData.Kingdom k, List<ServerPlayer> players, int count) {
        if (count <= 0) return 0;
        int begun = 0;
        for (Slot slot : slots()) {
            if (begun >= count) break;
            if (k.badSlots.contains(slot.index) || taken(k, slot)) continue;
            BlockPos column = slot.column(k.center);
            if (!level.isLoaded(column)) continue;                   // out of sight: try again another day
            Kind kind = kindFor(k.houses.size());
            int[] why = new int[2];
            BlockPos doorstep = site(level, k, slot, kind, why);
            if (doorstep == null && why[0] == 1 && why[1] == 3 && kind != Kind.COTTAGE) {
                // a wide house does not fit beside what is already there: a cottage might
                kind = Kind.COTTAGE;
                doorstep = site(level, k, slot, kind, why);
            }
            if (doorstep == null) {
                WakingWorld.LOGGER.info("kingdom {}: plot {} ({} road, {} {}, {}) refused: {}", Kingdoms.name(k.center), slot.index, slot.along().getName(), slot.depth,
                        slot.side > 0 ? "right" : "left", slot.back ? "back row" : "front row", why[0] == 1 ? WHY[why[1]] : why[0] == 2 ? "a work in the way" : "not loaded");
                if (why[0] == 1 && why[1] != 3 && why[1] != 5) {   // the ground itself: never again. Something built there, or a colony, may yet go
                    k.badSlots.add(slot.index);
                    data.setDirty();
                }
                continue;
            }
            raise(level, data, k, slot, kind, doorstep);
            begun++;
        }
        if (begun > 0) {
            level.playSound(null, k.center, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2.4F, 1.2F);
            for (ServerPlayer p : players) {
                p.displayClientMessage(Component.translatable(begun == 1 ? "kingdom.wakingworld.built.house" : "kingdom.wakingworld.built.houses",
                        Kingdoms.name(k.center), begun).withStyle(ChatFormatting.GOLD), false);
            }
        }
        return begun;
    }

    private static boolean taken(KingdomData.Kingdom k, Slot slot) {
        BlockPos c = slot.column(k.center);
        for (long h : k.houses) {
            BlockPos p = BlockPos.of(h);
            if (p.getX() == c.getX() && p.getZ() == c.getZ()) return true;
        }
        return false;
    }

    static final String[] WHY = {"", "the door would be under the sea", "water or lava on the plot", "something built on the plot", "the ground is too uneven", "a colony's land"};

    /** The doorstep for a house of this kind on this slot, or null if the plot will not take it (why[0]: 1 the ground, 2 a work, 3 not loaded; why[1] the detail). */
    static BlockPos site(ServerLevel level, KingdomData.Kingdom k, Slot slot, Kind kind, int[] why) {
        BlockPos column = slot.column(k.center);
        Direction facing = slot.facing();
        // the floor stands at the level of the lane's edge in front of the door, so the door meets the road
        // whatever the ground does behind the house: uphill is cut away, downhill is stood on a plinth
        BlockPos edge = column.relative(facing, LANE_STEPS + 1);
        if (!level.isLoaded(edge)) { why[0] = 3; return null; }
        int gy = KingdomExpansion.groundY(level, edge.getX(), edge.getZ());
        why[0] = 1;
        why[1] = 1;
        if (gy <= level.getSeaLevel() - 1) return null;
        BlockPos doorstep = new BlockPos(column.getX(), gy + 1, column.getZ());
        // a plot on (or hard against) a colony's land is somebody's, like anything built: looked at again later, never written off
        if (me.lovkar.wakingworld.compat.Colonies.keepOff(level, doorstep, kind.depth + APRON + FELL)) { why[1] = 5; return null; }
        Direction right = facing.getCounterClockWise(), depth = facing.getOpposite();
        int lo = gy, hi = gy;
        for (int u = -kind.hw - 1; u <= kind.hw + 1; u++) {
            for (int v = -1; v <= kind.depth + 1; v++) {
                int x = doorstep.getX() + right.getStepX() * u + depth.getStepX() * v;
                int z = doorstep.getZ() + right.getStepZ() * u + depth.getStepZ() * v;
                if (!level.isLoaded(new BlockPos(x, gy, z))) { why[0] = 3; return null; }
                int g = KingdomExpansion.groundY(level, x, z);
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
                BlockState ground = level.getBlockState(new BlockPos(x, g, z));
                if (ground.getFluidState().isSource()) { why[1] = 2; return null; }
                if (!KingdomExpansion.natural(ground)) { why[1] = 3; return null; }
                // what stands on the plot: a tree is felled, water is a pond, anything built is somebody's
                for (int y = g + 1; y <= Math.max(g, gy) + kind.height; y++) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    if (s.is(Blocks.WATER) || s.is(Blocks.LAVA)) { why[1] = 2; return null; }
                    if (!KingdomExpansion.natural(s)) { why[1] = 3; return null; }
                }
            }
        }
        if (hi - lo > SLOPE) { why[1] = 4; return null; }
        // the town's own works are wide: keep clear of their centres
        why[0] = 2;
        for (long w : k.works) if (BlockPos.of(w).distSqr(doorstep) < WORK_CLEARANCE * WORK_CLEARANCE) return null;
        why[0] = 0;
        return doorstep;
    }

    /** A house drawn on its plot: the plan, and the palette and seed it was drawn with. */
    record Drawn(KingdomBuild.Plan plan, HouseBuilder.Palette palette, int seed) {
    }

    /**
     * Draws the {@code index}-th house of the town on its plot - the plot cleared, the design, its yard.
     * The plan is a drawing (last course at a position wins), so a door cut into a wall is a door.
     */
    private static Drawn draw(ServerLevel level, Slot slot, Kind kind, BlockPos doorstep, int index) {
        KingdomBuild.Plan plan = new KingdomBuild.Plan(true);
        HouseBuilder.Terrain terrain = (x, z) -> ground(level, x, z);
        int seed = hash(doorstep.getX(), doorstep.getZ());
        HouseBuilder.Palette palette = HouseBuilder.Palette.of(seed + index);
        Direction facing = slot.facing(), right = facing.getCounterClockWise(), depth = facing.getOpposite();
        HouseBuilder.Frame f = new HouseBuilder.Frame(plan, terrain, doorstep, facing);
        int u0 = -kind.hw - APRON, u1 = kind.hw + APRON, v0 = -APRON, v1 = kind.depth + YARD;
        // 1. the trees: every log this far round the plot is felled from the ground up; the leaves come down when the masons are done
        for (int u = u0 - FELL; u <= u1 + FELL; u++) for (int v = v0 - FELL; v <= v1 + FELL; v++) fell(level, f, u, v);
        // 2. the terrace: where the ground round the plot stands above the floor, the cut is faced with the plinth stone
        for (int v = v0; v <= v1 + 1; v++) { terrace(level, f, palette, u0 - 1, v); terrace(level, f, palette, u1 + 1, v); }
        for (int u = u0; u <= u1; u++) terrace(level, f, palette, u, v1 + 1);
        // 3. the cut: the plot is cleared to the sky over the house and, uphill, down to the floor; the apron round the house is grass again
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                int g = f.ground(u, v);
                f.fill(u, u, 0, Math.max(kind.height, g + 3), v, v, HouseBuilder.AIR);
                boolean house = Math.abs(u) <= kind.hw && v >= 0 && v <= kind.depth;
                if (g >= 0 && !house) f.put(u, -1, v, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // 4. the house, footed to the natural ground
        switch (kind) {
            case LONGHOUSE -> HouseBuilder.longhouse(f, palette);
            case TOWNHOUSE -> HouseBuilder.townhouse(f, palette);
            case SMITHY -> HouseBuilder.smithy(f, palette);
            case TAVERN -> HouseBuilder.tavern(f, palette);
            case CHAPEL -> HouseBuilder.chapel(f, palette);
            default -> HouseBuilder.cottage(f, palette);
        }
        // 5. the yard, on the ground as it is after the cut
        HouseBuilder.Terrain cut = (x, z) -> {
            int g = ground(level, x, z);
            int dx = x - doorstep.getX(), dz = z - doorstep.getZ();
            int u = dx * right.getStepX() + dz * right.getStepZ(), v = dx * depth.getStepX() + dz * depth.getStepZ();
            return u >= u0 && u <= u1 && v >= v0 && v <= v1 ? Math.min(g, doorstep.getY() - 1) : g;
        };
        yard(new HouseBuilder.Frame(plan, cut, doorstep, facing), palette, kind, seed);
        return new Drawn(plan, palette, seed);
    }

    /**
     * The natural ground under a column, looked for through anything grown or built on it - so a house
     * raised again is footed to the ground it was footed to, not to its own roof.
     */
    static int ground(ServerLevel level, int x, int z) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int bottom = level.getMinBuildHeight() + 1;
        for (int n = 0; n < 80 && y > bottom; y--, n++) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.REPLACEABLE) || s.is(Blocks.SNOW)
                    || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS) || !KingdomExpansion.natural(s)) continue;
            return y;
        }
        return y;
    }

    /** Every log standing on a column, from its ground up, is drawn as air; the leaves are the sweep's. */
    private static void fell(ServerLevel level, HouseBuilder.Frame f, int u, int v) {
        BlockPos base = f.at(u, 0, v);
        if (!level.isLoaded(base)) return;
        int g = f.ground(u, v);
        for (int y = g + 1; y <= g + 40; y++) {
            if (level.getBlockState(f.at(u, y, v)).is(BlockTags.LOGS)) f.put(u, y, v, HouseBuilder.AIR);
        }
    }

    /** One column of the terrace: the ground above the floor level is faced with stone, a low wall on top of a tall face. */
    private static void terrace(ServerLevel level, HouseBuilder.Frame f, HouseBuilder.Palette p, int u, int v) {
        BlockPos base = f.at(u, 0, v);
        if (!level.isLoaded(base)) return;
        int g = f.ground(u, v);
        if (g < 0) return;
        for (int y = 0; y <= g; y++) {
            BlockState s = level.getBlockState(f.at(u, y, v));
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.REPLACEABLE) || !KingdomExpansion.natural(s)) continue;
            f.put(u, y, v, f.hash(u, y, v) % 4 == 0 ? p.plinthAlt() : p.plinth());
        }
        if (g >= 2 && level.getBlockState(f.at(u, g + 1, v)).canBeReplaced()) f.put(u, g + 1, v, Blocks.COBBLESTONE_WALL.defaultBlockState());
    }

    /** Logs over a paved column come down, and the leaves at head height with them; the rest of a canopy is the sweep's. */
    private static void fellAbove(ServerLevel level, KingdomBuild.Plan plan, int x, int g, int z) {
        for (int y = g + 1; y <= g + 40; y++) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (s.is(BlockTags.LOGS) || (y <= g + 3 && s.is(BlockTags.LEAVES))) plan.set(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    private static void raise(ServerLevel level, KingdomData data, KingdomData.Kingdom k, Slot slot, Kind kind, BlockPos doorstep) {
        Drawn drawn = draw(level, slot, kind, doorstep, k.houses.size());
        KingdomBuild.Plan plan = drawn.plan();
        HouseBuilder.Palette palette = drawn.palette();
        int seed = drawn.seed();
        lane(level, k, slot, plan, palette, doorstep);
        // the road itself, once per road: paved out to the march wall with its lamps
        int roadBit = 1 << slot.road;
        boolean roadLaid = (k.lanes & roadBit) == 0;
        if (roadLaid) {
            road(level, k, slot, plan, palette);
            k.lanes |= roadBit;
        }
        int backBit = 1 << (4 + slot.road);
        if (slot.back && (k.lanes & backBit) == 0) {
            backLane(level, k, slot, plan, palette);
            k.lanes |= backBit;
        }
        k.houses.add(doorstep.asLong());
        data.setDirty();
        int[] trades = residents(kind, seed);
        BlockPos centre = k.center;
        KingdomBuild.begin(level, doorstep, plan, kind.key(), () -> {
            for (int i = 0; i < trades.length; i++) {
                BlockPos at = doorstep.relative(slot.facing(), 2 + i);
                KingdomSpawns.trader(level, centre.getX(), centre.getY(), centre.getZ(), at.getX(), KingdomExpansion.groundY(level, at.getX(), at.getZ()) + 1, at.getZ(), trades[i]);
            }
            sweep(level, k, slot, kind, doorstep, roadLaid);
        });
        WakingWorld.LOGGER.info("kingdom {}: raises a {} ({}) at {} - house {}", Kingdoms.name(k.center), kind.key(), palette.name(), doorstep.toShortString(), k.houses.size());
    }

    /**
     * Raises every standing house again, over itself, with the design as it is drawn today - the way a
     * suburb built by an older version gets its doors. The masons lay every course, so anything a player
     * changed inside a house is lost; the people already living there stay. Returns how many were begun.
     */
    public static int redo(ServerLevel level, KingdomData.Kingdom k) {
        int begun = 0, index = 0;
        for (long h : k.houses) {
            BlockPos doorstep = BlockPos.of(h);
            Slot slot = slotOf(k, doorstep);
            if (slot != null && level.isLoaded(doorstep)) {
                Kind kind = standingKind(level, slot, doorstep, index);
                Drawn drawn = draw(level, slot, kind, doorstep, index);
                Slot slotHere = slot;
                Kind kindHere = kind;
                KingdomBuild.begin(level, doorstep, drawn.plan(), kind.key(), () -> sweep(level, k, slotHere, kindHere, doorstep, false), true);
                WakingWorld.LOGGER.info("kingdom {}: raises the {} ({}) at {} again - house {}", Kingdoms.name(k.center), kind.key(), drawn.palette().name(), doorstep.toShortString(), index + 1);
                begun++;
            }
            index++;
        }
        return begun;
    }

    /** The slot a doorstep stands on, or null if it is on none (a house from before the slots, say). */
    static Slot slotOf(KingdomData.Kingdom k, BlockPos doorstep) {
        for (Slot slot : slots()) {
            BlockPos c = slot.column(k.center);
            if (c.getX() == doorstep.getX() && c.getZ() == doorstep.getZ()) return slot;
        }
        return null;
    }

    /**
     * What kind the {@code index}-th house was built as: its kind by the order, unless what stands there
     * matches a cottage better - the fallback a wide house takes when it does not fit beside its neighbour.
     */
    static Kind standingKind(ServerLevel level, Slot slot, BlockPos doorstep, int index) {
        Kind byOrder = kindFor(index);
        if (byOrder == Kind.COTTAGE) return byOrder;
        int asDrawn = matches(level, draw(level, slot, byOrder, doorstep, index).plan(), doorstep);
        int asCottage = matches(level, draw(level, slot, Kind.COTTAGE, doorstep, index).plan(), doorstep);
        return asCottage > asDrawn ? Kind.COTTAGE : byOrder;
    }

    /** How many of a plan's blocks above the doorstep stand in the world as the same block. */
    private static int matches(ServerLevel level, KingdomBuild.Plan plan, BlockPos doorstep) {
        int[] n = new int[1];
        plan.forEach((at, state) -> {
            if (!state.isAir() && at.getY() >= doorstep.getY() && level.getBlockState(at).is(state.getBlock())) n[0]++;
        });
        return n[0];
    }

    /**
     * Once the masons are done: the leaves of the trees they felled have no tree left and come down
     * ({@link Tidy}), round the house and - when this house laid the road - along the whole road.
     */
    private static void sweep(ServerLevel level, KingdomData.Kingdom k, Slot slot, Kind kind, BlockPos doorstep, boolean road) {
        BlockPos middle = doorstep.relative(slot.facing().getOpposite(), kind.depth / 2);
        Tidy.begin(level, middle, kind.hw + APRON + FELL + 8, 0, -8, 40);
        if (road) {
            Direction a = slot.along();
            int d = (KingdomWallPiece.REACH + 1 + ROAD_END) / 2;
            Tidy.begin(level, new BlockPos(k.center.getX() + a.getStepX() * d, doorstep.getY(), k.center.getZ() + a.getStepZ() * d), 46, 0, -10, 40);
        }
    }

    /** What stands behind and beside a house: a garden, a tree, a hedge or a low wall, by kind and chance. */
    private static void yard(HouseBuilder.Frame f, HouseBuilder.Palette p, Kind kind, int seed) {
        int back = kind.depth + 2;   // one past the back eave
        switch (kind) {
            case COTTAGE -> {
                HouseBuilder.garden(shifted(f, seed % 2 == 0 ? -1 : 1, back), p, 2, 2);
                HouseBuilder.tree(shifted(f, seed % 2 == 0 ? 4 : -4, back + 1), p, false);
                if (seed % 3 == 0) HouseBuilder.hedge(f, p, -4, 4, -2, true);
            }
            case LONGHOUSE -> {
                HouseBuilder.garden(shifted(f, -2, back), p, 3, 2);
                HouseBuilder.tree(shifted(f, 5, back + 1), p, true);
                HouseBuilder.yardWall(f, -6, 6, -2);
            }
            case TOWNHOUSE -> {
                HouseBuilder.tree(shifted(f, seed % 2 == 0 ? 4 : -4, back), p, false);
                HouseBuilder.hedge(f, p, -4, 4, back + 1, false);
            }
            case SMITHY -> HouseBuilder.tree(shifted(f, -4, back), p, true);
            case TAVERN -> {
                HouseBuilder.tree(shifted(f, 3, back), p, true);
                HouseBuilder.tree(shifted(f, -3, back), p, true);
            }
            default -> {
                HouseBuilder.tree(shifted(f, 5, 3), p, true);
                HouseBuilder.tree(shifted(f, -5, 3), p, true);
                HouseBuilder.hedge(f, p, -4, 4, -2, true);
            }
        }
    }

    /** The same frame moved by (du, dv) on the drawing. */
    private static HouseBuilder.Frame shifted(HouseBuilder.Frame f, int du, int dv) {
        return new HouseBuilder.Frame(f.plan, f.terrain, f.at(du, 0, dv), f.facing);
    }

    /** The path from the door to the road (or the back lane): level with the floor, filled beneath, the plot's cut above it. */
    private static void lane(ServerLevel level, KingdomData.Kingdom k, Slot slot, KingdomBuild.Plan plan, HouseBuilder.Palette p, BlockPos doorstep) {
        Direction out = slot.facing();
        int y = doorstep.getY() - 1;
        for (int i = 1; i <= LANE_STEPS; i++) {
            BlockPos at = doorstep.relative(out, i);
            int g = ground(level, at.getX(), at.getZ());
            for (int yy = g + 1; yy < y; yy++) plan.set(at.getX(), yy, at.getZ(), Blocks.DIRT.defaultBlockState());
            plan.set(at.getX(), y, at.getZ(), (hash(at.getX(), at.getZ()) & 3) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
            plan.set(at.getX(), y + 1, at.getZ(), Blocks.AIR.defaultBlockState());
        }
    }

    /** The road out of the gate paved on to the march wall (or, on the side lanes, laid from the moat out), lamp posts on its verges. */
    private static void road(ServerLevel level, KingdomData.Kingdom k, Slot slot, KingdomBuild.Plan plan, HouseBuilder.Palette p) {
        Direction a = slot.along(), l = slot.lateral();
        int from = KingdomWallPiece.REACH + 1;
        for (int d = from; d <= ROAD_END; d++) {
            for (int s = -1; s <= 1; s++) {
                int x = k.center.getX() + a.getStepX() * d + l.getStepX() * s;
                int z = k.center.getZ() + a.getStepZ() * d + l.getStepZ() * s;
                if (!level.isLoaded(new BlockPos(x, k.center.getY(), z))) continue;
                int g = KingdomExpansion.groundY(level, x, z);
                if (g <= level.getSeaLevel() - 1) continue;
                plan.set(x, g, z, roadBlock(x, z));
                plan.set(x, g + 1, z, Blocks.AIR.defaultBlockState());
                fellAbove(level, plan, x, g, z);
            }
        }
        for (int d = 81; d <= 103; d += 11) {
            for (int s = -2; s <= 2; s += 4) {
                int x = k.center.getX() + a.getStepX() * d + l.getStepX() * s;
                int z = k.center.getZ() + a.getStepZ() * d + l.getStepZ() * s;
                if (!level.isLoaded(new BlockPos(x, k.center.getY(), z))) continue;
                int g = KingdomExpansion.groundY(level, x, z);
                HouseBuilder.lamp(new HouseBuilder.Frame(plan, (xx, zz) -> KingdomExpansion.groundY(level, xx, zz), new BlockPos(x, g + 1, z), Direction.SOUTH), p);
            }
        }
    }

    /** The lane behind the front row: two blocks of gravel and path with a well at its head and a lamp at its far end. */
    private static void backLane(ServerLevel level, KingdomData.Kingdom k, Slot slot, KingdomBuild.Plan plan, HouseBuilder.Palette p) {
        Direction a = slot.along(), l = slot.lateral();
        HouseBuilder.Terrain terrain = (xx, zz) -> KingdomExpansion.groundY(level, xx, zz);
        for (int side = -1; side <= 1; side += 2) {
            for (int d = DEPTHS[0] - 5; d <= LANE_END; d++) {
                for (int s = LANE_NEAR; s <= LANE_FAR; s++) {
                    int x = k.center.getX() + a.getStepX() * d + l.getStepX() * s * side;
                    int z = k.center.getZ() + a.getStepZ() * d + l.getStepZ() * s * side;
                    if (!level.isLoaded(new BlockPos(x, k.center.getY(), z))) continue;
                    int g = KingdomExpansion.groundY(level, x, z);
                    if (g <= level.getSeaLevel() - 1) continue;
                    plan.set(x, g, z, (hash(x, z) % 3) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
                    plan.set(x, g + 1, z, Blocks.AIR.defaultBlockState());
                    fellAbove(level, plan, x, g, z);
                }
            }
            // the well where the lane leaves the road's end, the lamp at its far end
            int wx = k.center.getX() + a.getStepX() * (DEPTHS[0] - 8) + l.getStepX() * (LANE_NEAR + 1) * side;
            int wz = k.center.getZ() + a.getStepZ() * (DEPTHS[0] - 8) + l.getStepZ() * (LANE_NEAR + 1) * side;
            if (level.isLoaded(new BlockPos(wx, k.center.getY(), wz))) {
                HouseBuilder.well(new HouseBuilder.Frame(plan, terrain, new BlockPos(wx, KingdomExpansion.groundY(level, wx, wz) + 1, wz), Direction.SOUTH), p);
            }
            int lx = k.center.getX() + a.getStepX() * (LANE_END + 2) + l.getStepX() * (LANE_NEAR + 1) * side;
            int lz = k.center.getZ() + a.getStepZ() * (LANE_END + 2) + l.getStepZ() * (LANE_NEAR + 1) * side;
            if (level.isLoaded(new BlockPos(lx, k.center.getY(), lz))) {
                HouseBuilder.lamp(new HouseBuilder.Frame(plan, terrain, new BlockPos(lx, KingdomExpansion.groundY(level, lx, lz) + 1, lz), Direction.SOUTH), p);
            }
        }
    }

    static BlockState roadBlock(int x, int z) {
        int h = hash(x, z) % 100;
        return h < 50 ? Blocks.COBBLESTONE.defaultBlockState() : h < 72 ? Blocks.STONE_BRICKS.defaultBlockState()
                : h < 86 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : h < 94 ? Blocks.GRAVEL.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    static int hash(int x, int z) {
        int h = x * 668265261 ^ z * 374761393;
        h ^= h >>> 15;
        h *= 625341585;
        return (h ^ h >>> 13) & 0x7fffffff;
    }
}
