package me.lovkar.wakingworld.kingdom;

import java.util.List;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.land.Lands;
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
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class KingdomExpansion {
    /** The civil works, in the order a town raises them; the engine comes between them once the town is a city. */
    private static final String[] KINDS = new String[]{"farm", "mill", "tower", "market"};
    /** The tier at which a town keeps an engine on its border. */
    static final int ENGINE_TIER = 4;
    /** The rings the works stand on: past the town wall and the last house plot's eave, inside the march wall at 118. */
    private static final int[] RADII = new int[]{75, 83, 91, 99, 107};
    /** Angles tried on each ring. */
    private static final int STEPS = 36;
    /** How many plots get the full ground survey in one review - the cheap checks run on all of them. */
    private static final int LOOKS = 60;
    /** Centre-to-centre room between two works (the widest is the market at 15). */
    private static final double SPACING = 40.0;
    private static final int OUTER = 118;
    private static final int ARCS = 24;
    private static final int WALL_H = 5;

    private KingdomExpansion() {
    }

    public static int wanted(int var0) {
        return Math.max(0, (var0 - 1) * 2);
    }

    /**
     * What the town raises next. The civil works cycle farm, mill, tower, market; a city with no engine
     * raises its catapult before anything else, so a town that grew fast on rough ground is not left
     * with a full wall and nothing to answer a horn with. (A city that grew the ordinary way gets the
     * same order as before: farm, mill, tower, market, catapult, farm.)
     */
    static String nextKind(KingdomData.Kingdom k) {
        if (k.tier >= ENGINE_TIER && k.catapults.isEmpty()) return "catapult";
        int civil = Math.max(0, k.works.size() - k.catapults.size());
        return KINDS[civil % KINDS.length];
    }

    public static boolean works(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, List<ServerPlayer> var3) {
        if (var2.works.size() >= wanted(var2.tier)) {
            return false;
        } else {
            String var5 = nextKind(var2);
            boolean var6 = var5.equals("tower");
            BlockPos var7 = var6 ? towerSite(var0, var2) : null;
            if (var7 == null) {
                var7 = ring(var0, var2, var6);
            }

            if (var7 == null) {
                WakingWorld.LOGGER.info("kingdom {}: found no ground for a {} this time ({} works standing)", Kingdoms.name(var2.center), var5, var2.works.size());
                return false;
            } else {
                KingdomBuild.Plan var8 = new KingdomBuild.Plan();
                switch (var5) {
                    case "tower":
                        watchtower(var0, var8, var2, var7);
                        break;
                    case "mill":
                        mill(var0, var8, var2, var7);
                        break;
                    case "market":
                        market(var0, var8, var2, var7);
                        break;
                    case "catapult":
                        catapult(var0, var8, var2, var7);
                        break;
                    default:
                        farmstead(var0, var8, var2, var7);
                }

                KingdomBuild.begin(var0, var7, var8, var5);
                var2.works.add(var7.asLong());
                if (var5.equals("catapult")) {
                    var2.catapults.add(var7.asLong());
                    Tidy.begin(var0, var7, 11, 6, -2, 26);
                }

                var1.setDirty();
                var0.playSound(null, var7, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2.4F, 1.2F);

                for (ServerPlayer var11 : var3) {
                    var11.displayClientMessage(
                        Component.translatable("kingdom.wakingworld.built." + var5, new Object[]{Kingdoms.name(var2.center)}).withStyle(ChatFormatting.GOLD),
                        false
                    );
                }

                WakingWorld.LOGGER
                    .info(
                        "kingdom {}: begins raising a {} at {} ({} blocks)", new Object[]{Kingdoms.name(var2.center), var5, var7.toShortString(), var8.size()}
                    );
                return true;
            }
        }
    }

    /**
     * A plot for a work in the quarters between the roads. The rings are walked from a random angle,
     * so a review that finds nothing does not try the same spots for ever; the cheap tests (sea, lane,
     * room) run on every candidate and the ground survey on the first {@link #LOOKS} that pass them.
     * If no plot is flat enough, the second pass takes a rougher one - the builders plinth and pave.
     */
    private static BlockPos ring(ServerLevel var0, KingdomData.Kingdom var1, boolean var2) {
        int var3 = var2 ? 3 : 5;
        int var4 = var2 ? 13 : 3;
        int var5 = var2 ? 3 : 4;
        double start = var0.random.nextDouble() * 360.0;

        for (int pass = 0; pass < 2; pass++) {
            int looked = 0;
            for (int ri = 0; ri < RADII.length; ri++) {
                int var9 = RADII[ri];
                for (int var6 = 0; var6 < STEPS && looked < LOOKS; var6++) {
                    double var7 = Math.toRadians(start + (double)var6 * 360.0 / (double)STEPS + (double)(ri * 5));
                    int var10 = var1.center.getX() + (int)Math.round(Math.cos(var7) * (double)var9);
                    int var11 = var1.center.getZ() + (int)Math.round(Math.sin(var7) * (double)var9);
                    BlockPos var12 = ground(var0, var10, var11);
                    if (var12 != null && !inLane(var1, var12) && !occupied(var0, var12)) {
                        looked++;
                        if (clear(var0, var12, var3, var4, var5 + pass * 2)) {
                            return var12;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static BlockPos towerSite(ServerLevel var0, KingdomData.Kingdom var1) {
        if (!WakingConfig.namedLands()) {
            return null;
        } else {
            Lands var2 = Lands.get(var0);
            Lands.Land var3 = var2.landAtCell(Lands.cellOf(var1.center.getX()), Lands.cellOf(var1.center.getZ()));
            int var4 = WakingConfig.landSize();

            for (long var6 : var1.claims) {
                Lands.Land var8 = var2.byCell(var6);
                if (var8 != null && (var3 == null || var8.cell() != var3.cell())) {
                    int var9 = var8.cellX() * var4 + var4 / 2;
                    int var10 = var8.cellZ() * var4 + var4 / 2;

                    for (int var11 = 0; var11 < 14; var11++) {
                        double var12 = Math.toRadians((double)var11 * 360.0 / 14.0);
                        int var14 = var11 % 4 * 14;
                        BlockPos var15 = ground(
                            var0, var9 + (int)Math.round(Math.cos(var12) * (double)var14), var10 + (int)Math.round(Math.sin(var12) * (double)var14)
                        );
                        if (var15 != null && !(var15.distSqr(var1.center) < 8100.0) && !occupied(var0, var15) && clear(var0, var15, 3, 13, 3)) {
                            return var15;
                        }
                    }
                }
            }

            return null;
        }
    }

    /** The four roads out of the town and the rows of houses along them are the suburb's; the works stay in the quarters between. */
    static boolean inLane(KingdomData.Kingdom k, BlockPos at) {
        int dx = Math.abs(at.getX() - k.center.getX()), dz = Math.abs(at.getZ() - k.center.getZ());
        return dx <= 34 && dz <= 118 || dz <= 34 && dx <= 118;
    }

    private static boolean occupied(ServerLevel var0, BlockPos var1) {
        for (KingdomData.Kingdom var3 : KingdomData.get(var0).all()) {
            for (long var5 : var3.works) {
                if (BlockPos.of(var5).distSqr(var1) < SPACING * SPACING) {
                    return true;
                }
            }
            // the suburb's houses are smaller, but a work is wide and a house runs ten blocks back from its
            // doorstep: keep the work's centre 23 blocks off any doorstep, so there is a lane's width between
            for (long h : var3.houses) {
                if (BlockPos.of(h).distSqr(var1) < 529.0) {
                    return true;
                }
            }
        }

        return false;
    }

    static int groundY(ServerLevel var0, int var1, int var2) {
        int var3 = var0.getHeight(Types.OCEAN_FLOOR_WG, var1, var2) - 1;
        int var4 = var0.getMinBuildHeight() + 1;

        for (int var5 = 0; var5 < 40 && var3 > var4; var3--) {
            BlockState var6 = var0.getBlockState(new BlockPos(var1, var3, var2));
            if (!var6.isAir()
                && !var6.is(BlockTags.LEAVES)
                && !var6.is(BlockTags.LOGS)
                && !var6.is(BlockTags.REPLACEABLE)
                && !var6.is(Blocks.SNOW)
                && !var6.is(BlockTags.FLOWERS)
                && !var6.is(BlockTags.SAPLINGS)) {
                return var3;
            }

            var5++;
        }

        return var3;
    }

    private static BlockPos ground(ServerLevel var0, int var1, int var2) {
        BlockPos var3 = new BlockPos(var1, groundY(var0, var1, var2) + 1, var2);
        if (!var0.isLoaded(var3)) {
            return null;
        } else {
            return var3.getY() <= var0.getSeaLevel() - 1 ? null : var3;
        }
    }

    private static boolean clear(ServerLevel var0, BlockPos var1, int var2, int var3, int var4) {
        int var5 = Integer.MAX_VALUE;
        int var6 = Integer.MIN_VALUE;

        for (int var7 = -var2 - 1; var7 <= var2 + 1; var7++) {
            for (int var8 = -var2 - 1; var8 <= var2 + 1; var8++) {
                int var9 = var1.getX() + var7;
                int var10 = var1.getZ() + var8;
                int var11 = groundY(var0, var9, var10) + 1;
                var5 = Math.min(var5, var11);
                var6 = Math.max(var6, var11);
                BlockState var12 = var0.getBlockState(new BlockPos(var9, var11 - 1, var10));
                if (!natural(var12) || var12.getFluidState().isSource()) {
                    return false;
                }

                for (int var13 = 0; var13 <= var3; var13++) {
                    if (!natural(var0.getBlockState(new BlockPos(var9, var11 + var13, var10)))) {
                        return false;
                    }
                }
            }
        }

        return var6 - var5 <= var4;
    }

    public static boolean natural(BlockState var0) {
        if (var0.isAir()) {
            return true;
        } else {
            return !var0.is(BlockTags.DIRT)
                    && !var0.is(BlockTags.BASE_STONE_OVERWORLD)
                    && !var0.is(BlockTags.SAND)
                    && !var0.is(BlockTags.LEAVES)
                    && !var0.is(BlockTags.LOGS)
                    && !var0.is(BlockTags.FLOWERS)
                    && !var0.is(BlockTags.SAPLINGS)
                    && !var0.is(BlockTags.CROPS)
                    && !var0.is(BlockTags.SNOW)
                    && !var0.is(BlockTags.REPLACEABLE)
                    && !var0.is(BlockTags.ICE)
                ? var0.is(Blocks.GRAVEL)
                    || var0.is(Blocks.CLAY)
                    || var0.is(Blocks.MOSS_BLOCK)
                    || var0.is(Blocks.MUD)
                    || var0.is(Blocks.SANDSTONE)
                    || var0.is(Blocks.RED_SANDSTONE)
                    || var0.is(Blocks.TERRACOTTA)
                    || var0.is(Blocks.CALCITE)
                    || var0.is(Blocks.TUFF)
                    || var0.is(Blocks.SNOW_BLOCK)
                    || var0.is(Blocks.POWDER_SNOW)
                    || var0.is(Blocks.PUMPKIN)
                    || var0.is(Blocks.MELON)
                : true;
        }
    }

    public static boolean march(ServerLevel var0, KingdomData var1, KingdomData.Kingdom var2, List<ServerPlayer> var3) {
        if (var2.tier < 3) {
            return false;
        } else {
            int var4 = -1;

            for (int var5 = 0; var5 < 24; var5++) {
                if ((var2.wallArcs & 1 << var5) == 0) {
                    var4 = var5;
                    break;
                }
            }

            if (var4 < 0) {
                return false;
            } else {
                double var24 = (double)var4 * (Math.PI / 12);
                double var7 = (double)(var4 + 1) * (Math.PI / 12);
                BlockState var9 = Blocks.STONE_BRICKS.defaultBlockState();
                BlockState var10 = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                BlockState var11 = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                KingdomBuild.Plan var12 = new KingdomBuild.Plan();
                BlockPos var13 = null;
                int var14 = 0;
                int var15 = (int)Math.ceil((var7 - var24) * 118.0) + 1;

                for (int var16 = 0; var16 <= var15; var16++) {
                    double var17 = var24 + (var7 - var24) * ((double)var16 / (double)var15);
                    int var19 = var2.center.getX() + (int)Math.round(Math.cos(var17) * 118.0);
                    int var20 = var2.center.getZ() + (int)Math.round(Math.sin(var17) * 118.0);
                    if (Math.abs(var19 - var2.center.getX()) > 3
                        && Math.abs(var20 - var2.center.getZ()) > 3
                        && var0.isLoaded(new BlockPos(var19, var2.center.getY(), var20))) {
                        int var21 = groundY(var0, var19, var20);
                        if (var21 > var0.getSeaLevel() - 6 && Math.abs(var21 - var2.center.getY()) <= 40) {
                            if (var13 == null || var16 == var15 / 2) {
                                var13 = new BlockPos(var19, var21, var20);
                            }

                            for (int var22 = var21 + 1; var22 <= var21 + 5 + 3; var22++) {
                                BlockState var23 = var0.getBlockState(new BlockPos(var19, var22, var20));
                                if (!var23.isAir()) {
                                    var12.set(var19, var22, var20, Blocks.AIR.defaultBlockState());
                                }
                            }

                            for (int var27 = var21; var27 > var21 - 6; var27--) {
                                BlockState var29 = var0.getBlockState(new BlockPos(var19, var27, var20));
                                if (!var29.isAir() && !var29.canBeReplaced()) {
                                    break;
                                }

                                var12.set(var19, var27, var20, hash(var19, var27 + var20) % 7 == 0 ? var11 : var9);
                            }

                            for (int var28 = 1; var28 <= 5; var28++) {
                                var12.set(var19, var21 + var28, var20, hash(var19, var28 * 5 + var20) % 6 == 0 ? var10 : var9);
                            }

                            if ((var19 + var20 & 1) == 0) {
                                var12.set(var19, var21 + 5 + 1, var20, Blocks.STONE_BRICK_WALL.defaultBlockState());
                            } else {
                                var12.set(var19, var21 + 5 + 1, var20, Blocks.STONE_BRICK_SLAB.defaultBlockState());
                            }

                            if (hash(var19, var20) % 23 == 0) {
                                var12.set(var19, var21 + 5 + 1, var20, Blocks.STONE_BRICKS.defaultBlockState());
                                var12.set(var19, var21 + 5 + 2, var20, (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                            }

                            var14++;
                        }
                    }
                }

                var2.wallArcs |= 1 << var4;
                var1.setDirty();
                if (var14 != 0 && var13 != null) {
                    KingdomBuild.begin(var0, var13, var12, "march wall");
                    int var25 = Integer.bitCount(var2.wallArcs);

                    for (ServerPlayer var18 : var3) {
                        var18.displayClientMessage(
                            Component.translatable("kingdom.wakingworld.march", new Object[]{Kingdoms.name(var2.center), var25, 24})
                                .withStyle(ChatFormatting.GOLD),
                            false
                        );
                    }

                    WakingWorld.LOGGER
                        .info(
                            "kingdom {}: march wall arc {}/{} - {} columns, {} blocks",
                            new Object[]{Kingdoms.name(var2.center), var25, 24, var14, var12.size()}
                        );
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    private static void put(KingdomBuild.Plan var0, int var1, int var2, int var3, BlockState var4) {
        var0.set(var1, var2, var3, var4);
    }

    private static int hash(int var0, int var1) {
        int var2 = var0 * 668265261 ^ var1 * 374761393;
        var2 ^= var2 >>> 15;
        var2 *= 625341585;
        return (var2 ^ var2 >>> 13) & 2147483647;
    }

    private static void farmstead(ServerLevel var0, KingdomBuild.Plan var1, KingdomData.Kingdom var2, BlockPos var3) {
        int var4 = var3.getY();
        BlockState[] var5 = new BlockState[]{
            (BlockState)Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7),
            (BlockState)Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7),
            (BlockState)Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE, 7),
            (BlockState)Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE, 3)
        };
        BlockState var6 = var5[hash(var3.getX(), var3.getZ()) % var5.length];

        for (int var7 = -5; var7 <= 5; var7++) {
            for (int var8 = -5; var8 <= 5; var8++) {
                boolean var9 = Math.abs(var7) == 5 || Math.abs(var8) == 5;
                int var10 = var3.getX() + var7;
                int var11 = var3.getZ() + var8;
                int var12 = groundY(var0, var10, var11) + 1;
                if (var9) {
                    boolean var13 = var7 == 0 && var8 == 5 || var7 == 0 && var8 == -5 || var8 == 0 && Math.abs(var7) == 5;
                    if (!var13) {
                        put(var1, var10, var12, var11, Blocks.OAK_FENCE.defaultBlockState());
                    }
                } else if (var7 == 0) {
                    put(var1, var10, var12 - 1, var11, Blocks.WATER.defaultBlockState());
                } else {
                    put(var1, var10, var12 - 1, var11, (BlockState)Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                    put(var1, var10, var12, var11, var6);
                }
            }
        }

        put(var1, var3.getX() + 4, var4, var3.getZ() + 4, Blocks.HAY_BLOCK.defaultBlockState());
        put(var1, var3.getX() + 4, var4 + 1, var3.getZ() + 4, Blocks.HAY_BLOCK.defaultBlockState());
        put(var1, var3.getX() + 3, var4, var3.getZ() + 4, Blocks.BARREL.defaultBlockState());
        put(var1, var3.getX() - 4, var4, var3.getZ() - 4, Blocks.OAK_FENCE.defaultBlockState());
        put(var1, var3.getX() - 4, var4 + 1, var3.getZ() - 4, Blocks.OAK_FENCE.defaultBlockState());
        put(var1, var3.getX() - 4, var4 + 2, var3.getZ() - 4, (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static void mill(ServerLevel var0, KingdomBuild.Plan var1, KingdomData.Kingdom var2, BlockPos var3) {
        int var4 = var3.getY();
        BlockState var5 = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState var6 = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState var7 = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState var8 = Blocks.SPRUCE_FENCE.defaultBlockState();

        for (int var9 = -4; var9 <= 4; var9++) {
            for (int var10 = -4; var10 <= 4; var10++) {
                double var11 = Math.sqrt((double)(var9 * var9 + var10 * var10));
                int var13 = var3.getX() + var9;
                int var14 = var3.getZ() + var10;
                if (!(var11 > 4.3)) {
                    int var15 = groundY(var0, var13, var14) + 1;
                    if (!(var11 > 3.2)) {
                        put(var1, var13, var4, var14, hash(var13, var14) % 4 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : var5);

                        for (int var28 = 1; var28 <= 7; var28++) {
                            put(var1, var13, var4 + var28, var14, Blocks.AIR.defaultBlockState());
                        }
                    } else {
                        for (int var16 = Math.min(var15, var4) - 1; var16 <= var4; var16++) {
                            put(var1, var13, var16, var14, var5);
                        }

                        for (int var27 = 1; var27 <= 7; var27++) {
                            boolean var17 = var10 == 4 && Math.abs(var9) <= 1 && var27 <= 3;
                            boolean var18 = var27 == 5 && (Math.abs(var9) == 4 || Math.abs(var10) == 4);
                            put(
                                var1,
                                var13,
                                var4 + var27,
                                var14,
                                var17
                                    ? Blocks.AIR.defaultBlockState()
                                    : (var18 ? Blocks.GLASS_PANE.defaultBlockState() : (hash(var13, var27 * 3 + var14) % 6 == 0 ? var6 : var5))
                            );
                        }
                    }
                }
            }
        }

        for (int var19 = 0; var19 <= 3; var19++) {
            double var21 = 4.3 - (double)var19 * 1.1;

            for (int var12 = -4; var12 <= 4; var12++) {
                for (int var24 = -4; var24 <= 4; var24++) {
                    if (!(Math.sqrt((double)(var12 * var12 + var24 * var24)) > var21)) {
                        put(
                            var1,
                            var3.getX() + var12,
                            var4 + 8 + var19,
                            var3.getZ() + var24,
                            var19 == 3 ? Blocks.DARK_OAK_SLAB.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState()
                        );
                    }
                }
            }
        }

        put(var1, var3.getX(), var4 + 7, var3.getZ() + 5, Blocks.OAK_LOG.defaultBlockState());

        for (int var20 = 0; var20 < 4; var20++) {
            double var22 = Math.toRadians((double)(45 + var20 * 90));

            for (int var23 = 1; var23 <= 5; var23++) {
                int var25 = var3.getX() + (int)Math.round(Math.cos(var22) * (double)var23);
                int var26 = var4 + 7 + (int)Math.round(Math.sin(var22) * (double)var23);
                put(var1, var25, var26, var3.getZ() + 5, var23 == 5 ? var7 : var8);
                if (var23 >= 3) {
                    put(var1, var25, var26, var3.getZ() + 6, var7);
                }
            }
        }

        put(var1, var3.getX() - 6, var4 + 1, var3.getZ(), Blocks.HAY_BLOCK.defaultBlockState());
        put(var1, var3.getX() - 6, var4 + 2, var3.getZ(), Blocks.HAY_BLOCK.defaultBlockState());
        put(var1, var3.getX() - 5, var4 + 1, var3.getZ() + 1, Blocks.BARREL.defaultBlockState());
        put(var1, var3.getX() + 5, var4 + 1, var3.getZ(), Blocks.SPRUCE_FENCE.defaultBlockState());
        put(var1, var3.getX() + 5, var4 + 2, var3.getZ(), Blocks.SPRUCE_FENCE.defaultBlockState());
        put(var1, var3.getX() + 5, var4 + 3, var3.getZ(), (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static void market(ServerLevel var0, KingdomBuild.Plan var1, KingdomData.Kingdom var2, BlockPos var3) {
        int var4 = var3.getY();
        BlockState[] var5 = new BlockState[]{
            Blocks.WHITE_WOOL.defaultBlockState(),
            Blocks.LIGHT_GRAY_WOOL.defaultBlockState(),
            Blocks.CYAN_WOOL.defaultBlockState(),
            Blocks.BROWN_WOOL.defaultBlockState()
        };

        for (int var6 = -7; var6 <= 7; var6++) {
            for (int var7 = -7; var7 <= 7; var7++) {
                int var8 = var3.getX() + var6;
                int var9 = var3.getZ() + var7;
                boolean var10 = Math.abs(var6) <= 2 || Math.abs(var7) <= 2;
                if (var10 || Math.abs(var6) + Math.abs(var7) <= 9) {
                    int var11 = groundY(var0, var8, var9);
                    put(
                        var1,
                        var8,
                        var11,
                        var9,
                        var10
                            ? (hash(var8, var9) % 5 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                            : Blocks.GRASS_BLOCK.defaultBlockState()
                    );
                }
            }
        }

        int[][] var15 = new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}};

        for (int var16 = 0; var16 < var15.length; var16++) {
            int var18 = var3.getX() + var15[var16][0];
            int var19 = var3.getZ() + var15[var16][1];
            BlockState var20 = var5[(hash(var18, var19) + var16) % var5.length];

            for (int var21 = -1; var21 <= 1; var21++) {
                for (int var12 = -1; var12 <= 1; var12++) {
                    boolean var13 = Math.abs(var21) == 1 && Math.abs(var12) == 1;
                    if (var13) {
                        for (int var14 = 1; var14 <= 3; var14++) {
                            put(var1, var18 + var21, var4 + var14, var19 + var12, Blocks.OAK_FENCE.defaultBlockState());
                        }
                    }

                    put(var1, var18 + var21, var4 + 4, var19 + var12, var20);
                }
            }

            put(var1, var18, var4 + 1, var19 - 1, Blocks.OAK_SLAB.defaultBlockState());
            put(var1, var18 + 1, var4 + 1, var19, var16 % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.COMPOSTER.defaultBlockState());
            put(var1, var18 - 1, var4 + 1, var19, Blocks.OAK_FENCE.defaultBlockState());
            put(var1, var18 - 1, var4 + 2, var19, (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }

        for (int var17 = 1; var17 <= 4; var17++) {
            put(var1, var3.getX(), var4 + var17, var3.getZ(), Blocks.STONE_BRICK_WALL.defaultBlockState());
        }

        put(var1, var3.getX(), var4 + 5, var3.getZ(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        put(var1, var3.getX(), var4 + 6, var3.getZ(), (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        put(var1, var3.getX() + 1, var4 + 4, var3.getZ(), banner(var2.center));
        put(var1, var3.getX() - 1, var4 + 4, var3.getZ(), (BlockState)banner(var2.center).setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    public static boolean engine(ServerLevel var0, BlockPos var1) {
        KingdomData var2 = KingdomData.get(var0);
        KingdomData.Kingdom var3 = var2.kingdomAt(var1);
        if (var3 == null) {
            return false;
        } else {
            BlockPos var4 = var1.atY(groundY(var0, var1.getX(), var1.getZ()) + 1);
            KingdomBuild.Plan var5 = new KingdomBuild.Plan();
            catapult(var0, var5, var3, var4);
            KingdomBuild.begin(var0, var4, var5, "catapult");
            Tidy.begin(var0, var4, 11, 6, -2, 26);
            var3.works.add(var4.asLong());
            var3.catapults.add(var4.asLong());
            var2.setDirty();
            return true;
        }
    }

    private static void catapult(ServerLevel var0, KingdomBuild.Plan var1, KingdomData.Kingdom var2, BlockPos var3) {
        int var4 = var3.getY();
        int var5 = var3.getX() - var2.center.getX();
        int var6 = var3.getZ() - var2.center.getZ();
        Direction var7 = Math.abs(var5) >= Math.abs(var6) ? (var5 >= 0 ? Direction.EAST : Direction.WEST) : (var6 >= 0 ? Direction.SOUTH : Direction.NORTH);
        Direction var8 = var7.getClockWise();
        int var9 = var7.getStepX();
        int var10 = var7.getStepZ();
        int var11 = var8.getStepX();
        int var12 = var8.getStepZ();
        BlockState var13 = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        BlockState var14 = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        BlockState var15 = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState var16 = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState var17 = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState var18 = (BlockState)var13.setValue(RotatedPillarBlock.AXIS, var7.getAxis());
        BlockState var19 = (BlockState)var13.setValue(RotatedPillarBlock.AXIS, var8.getAxis());
        BlockState var20 = (BlockState)var15.setValue(RotatedPillarBlock.AXIS, var8.getAxis());
        BlockState var21 = (BlockState)var14.setValue(RotatedPillarBlock.AXIS, var8.getAxis());

        for (int var22 = -4; var22 <= 4; var22++) {
            for (int var23 = -7; var23 <= 6; var23++) {
                if (Math.abs(var22) != 4 || var23 >= -6 && var23 <= 5) {
                    int var24 = var3.getX() + var22 * var11 + var23 * var9;
                    int var25 = var3.getZ() + var22 * var12 + var23 * var10;
                    int var26 = groundY(var0, var24, var25);

                    for (int var27 = Math.min(var26, var4); var27 <= var4; var27++) {
                        put(var1, var24, var27, var25, hash(var24, var27 + var25) % 5 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : var17);
                    }
                }
            }
        }

        for (byte var28 = -1; var28 <= 1; var28 += 2) {
            for (int var47 : new int[]{-4, 3}) {
                int var49 = var28 * 3;
                put(var1, var3, var11, var12, var9, var10, var49, var4 + 2, var47, (BlockState)var14.setValue(RotatedPillarBlock.AXIS, var8.getAxis()));
                put(var1, var3, var11, var12, var9, var10, var49, var4 + 1, var47, var21);
                put(var1, var3, var11, var12, var9, var10, var49, var4 + 3, var47, var21);
                put(var1, var3, var11, var12, var9, var10, var49, var4 + 2, var47 - 1, var21);
                put(var1, var3, var11, var12, var9, var10, var49, var4 + 2, var47 + 1, var21);
            }
        }

        for (int var29 = -2; var29 <= 2; var29++) {
            for (int var38 = -6; var38 <= 5; var38++) {
                put(var1, var3, var11, var12, var9, var10, var29, var4 + 3, var38, Math.abs(var29) == 2 ? var18 : var16);
            }
        }

        for (byte var30 = -1; var30 <= 1; var30 += 2) {
            for (int var39 = -5; var39 <= 4; var39++) {
                put(var1, var3, var11, var12, var9, var10, var30 * 2, var4 + 1, var39, var18);
                put(var1, var3, var11, var12, var9, var10, var30 * 2, var4 + 2, var39, var18);
            }
        }

        for (int var46 : new int[]{-4, 3}) {
            for (int var48 = -3; var48 <= 3; var48++) {
                put(var1, var3, var11, var12, var9, var10, var48, var4 + 2, var46, var19);
                put(var1, var3, var11, var12, var9, var10, var48, var4 + 1, var46, var19);
            }
        }

        for (byte var32 = -1; var32 <= 1; var32 += 2) {
            int var41 = var32 * 2;
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 4, -2, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 5, -2, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 6, -1, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 4, 2, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 5, 2, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 6, 1, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 7, 0, var13);
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 5, -1, Blocks.OAK_FENCE.defaultBlockState());
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 5, 0, Blocks.OAK_FENCE.defaultBlockState());
            put(var1, var3, var11, var12, var9, var10, var41, var4 + 5, 1, Blocks.OAK_FENCE.defaultBlockState());
        }

        for (int var33 = -2; var33 <= 2; var33++) {
            put(var1, var3, var11, var12, var9, var10, var33, var4 + 7, 0, var19);
        }

        for (int var34 = 1; var34 <= 4; var34++) {
            put(var1, var3, var11, var12, var9, var10, 0, var4 + 7 - var34, -var34, var20);
            put(var1, var3, var11, var12, var9, var10, 0, var4 + 8 - var34, -var34, var20);
        }

        put(var1, var3, var11, var12, var9, var10, 0, var4 + 4, -5, Blocks.CHAIN.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, 0, var4 + 4, -6, Blocks.MAGMA_BLOCK.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, 0, var4 + 8, 1, var20);
        put(var1, var3, var11, var12, var9, var10, 0, var4 + 9, 2, var20);
        put(var1, var3, var11, var12, var9, var10, 0, var4 + 8, 2, Blocks.CHAIN.defaultBlockState());

        for (int var35 = -1; var35 <= 1; var35++) {
            for (int var42 = 2; var42 <= 3; var42++) {
                put(var1, var3, var11, var12, var9, var10, var35, var4 + 6, var42, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                put(var1, var3, var11, var12, var9, var10, var35, var4 + 7, var42, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            }
        }

        put(var1, var3, var11, var12, var9, var10, 1, var4 + 4, -6, Blocks.BARREL.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, -1, var4 + 4, -6, var19);

        for (int var36 = 0; var36 < 3; var36++) {
            put(var1, var3, var11, var12, var9, var10, 4, var4 + 1, var36 - 1, Blocks.MAGMA_BLOCK.defaultBlockState());
        }

        put(var1, var3, var11, var12, var9, var10, 4, var4 + 2, 0, Blocks.MAGMA_BLOCK.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, -4, var4 + 1, 1, Blocks.CAMPFIRE.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, -4, var4 + 1, -1, Blocks.OAK_FENCE.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, -4, var4 + 2, -1, Blocks.OAK_FENCE.defaultBlockState());
        put(var1, var3, var11, var12, var9, var10, -4, var4 + 3, -1, banner(var2.center));
    }

    private static void put(KingdomBuild.Plan var0, BlockPos var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, BlockState var9) {
        put(var0, var1.getX() + var6 * var2 + var8 * var4, var7, var1.getZ() + var6 * var3 + var8 * var5, var9);
    }

    private static void watchtower(ServerLevel var0, KingdomBuild.Plan var1, KingdomData.Kingdom var2, BlockPos var3) {
        int var4 = var3.getY();
        BlockState var5 = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState var6 = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        int var7 = var4 + 11;

        for (int var8 = -3; var8 <= 3; var8++) {
            for (int var9 = -3; var9 <= 3; var9++) {
                if (Math.abs(var8) != 3 || Math.abs(var9) != 3) {
                    int var10 = var3.getX() + var8;
                    int var11 = var3.getZ() + var9;
                    int var12 = groundY(var0, var10, var11) + 1;

                    for (int var13 = var12 - 1; var13 <= var4 - 1; var13++) {
                        put(var1, var10, var13, var11, hash(var10, var13 + var11) % 6 == 0 ? var6 : var5);
                    }
                }
            }
        }

        for (int var16 = var4; var16 <= var7; var16++) {
            for (int var19 = -2; var19 <= 2; var19++) {
                for (int var21 = -2; var21 <= 2; var21++) {
                    boolean var23 = Math.abs(var19) == 2 || Math.abs(var21) == 2;
                    int var25 = var3.getX() + var19;
                    int var27 = var3.getZ() + var21;
                    if (!var23) {
                        put(var1, var25, var16, var27, var16 == var4 ? (hash(var25, var27) % 5 == 0 ? var6 : var5) : Blocks.AIR.defaultBlockState());
                    } else {
                        boolean var14 = var21 == 2 && Math.abs(var19) <= 0 && var16 <= var4 + 2 && var16 > var4;
                        boolean var15 = Math.abs(var19) + Math.abs(var21) == 3 && (var16 == var4 + 5 || var16 == var4 + 6) && (var19 == 0 || var21 == 0);
                        if (!var14 && !var15) {
                            put(var1, var25, var16, var27, hash(var25, var16 * 7 + var27) % 7 == 0 ? var6 : var5);
                        } else {
                            put(var1, var25, var16, var27, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }

        for (int var17 = var4 + 1; var17 <= var7; var17++) {
            put(var1, var3.getX(), var17, var3.getZ() + 1, (BlockState)Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }

        for (int var18 = -2; var18 <= 2; var18++) {
            for (int var20 = -2; var20 <= 2; var20++) {
                int var22 = var3.getX() + var18;
                int var24 = var3.getZ() + var20;
                boolean var26 = Math.abs(var18) == 2 || Math.abs(var20) == 2;
                put(var1, var22, var7, var24, var26 ? var5 : Blocks.STONE_BRICK_SLAB.defaultBlockState());
                if (var26 && (var18 + var20 & 1) == 0) {
                    put(var1, var22, var7 + 1, var24, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        }

        put(var1, var3.getX(), var7 + 1, var3.getZ(), (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        put(var1, var3.getX() + 2, var7 - 3, var3.getZ(), banner(var2.center));
        put(var1, var3.getX() - 2, var7 - 3, var3.getZ(), banner(var2.center));
        put(var1, var3.getX() + 2, var4 + 1, var3.getZ() + 3, (BlockState)Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static BlockState banner(BlockPos var0) {
        return (BlockState)Kingdoms.wallBanner(var0).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
    }
}
