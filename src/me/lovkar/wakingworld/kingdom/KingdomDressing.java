package me.lovkar.wakingworld.kingdom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

public final class KingdomDressing {
    private static final int SLAB = 6;
    private static final int REACH = 76;
    private static final int WALL = 56;
    private static final int KEEP = 27;
    private static final int BUDGET = 180;
    private static final Set<Block> STONEWORK = Set.of(
        Blocks.STONE_BRICKS,
        Blocks.CRACKED_STONE_BRICKS,
        Blocks.MOSSY_STONE_BRICKS,
        Blocks.CHISELED_STONE_BRICKS,
        Blocks.STONE_BRICK_SLAB,
        Blocks.STONE_BRICK_STAIRS,
        Blocks.COBBLESTONE,
        Blocks.MOSSY_COBBLESTONE,
        Blocks.ANDESITE,
        Blocks.POLISHED_ANDESITE,
        Blocks.STONE,
        Blocks.SMOOTH_STONE,
        Blocks.BRICKS,
        Blocks.DEEPSLATE_BRICKS,
        Blocks.POLISHED_DEEPSLATE,
        Blocks.OAK_PLANKS,
        Blocks.SPRUCE_PLANKS,
        Blocks.DARK_OAK_PLANKS
    );
    private static final Set<Block> STREET = Set.of(
        Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICK_SLAB, Blocks.GRAVEL, Blocks.DIRT_PATH, Blocks.ANDESITE
    );
    private static final BlockState[] POTS = new BlockState[]{
        Blocks.POTTED_POPPY.defaultBlockState(),
        Blocks.POTTED_DANDELION.defaultBlockState(),
        Blocks.POTTED_CORNFLOWER.defaultBlockState(),
        Blocks.POTTED_AZURE_BLUET.defaultBlockState(),
        Blocks.POTTED_OXEYE_DAISY.defaultBlockState(),
        Blocks.POTTED_RED_TULIP.defaultBlockState(),
        Blocks.POTTED_FERN.defaultBlockState(),
        Blocks.POTTED_ALLIUM.defaultBlockState()
    };
    private static final Deque<KingdomDressing.Job> QUEUE = new ArrayDeque<>();

    private KingdomDressing() {
    }

    public static void begin(ServerLevel var0, BlockPos var1, int var2) {
        for (KingdomDressing.Job var4 : QUEUE) {
            if (var4.centre.equals(var1)) {
                return;
            }
        }

        QUEUE.add(new KingdomDressing.Job(var0, var1, var2));
    }

    public static int pending() {
        return QUEUE.size();
    }

    private static int hash(int var0, int var1) {
        int var2 = var0 * 668265261 ^ var1 * 374761393;
        var2 ^= var2 >>> 15;
        var2 *= 625341585;
        var2 ^= var2 >>> 13;
        return var2 & 2147483647;
    }

    public static void tick(Post var0) {
        if (!QUEUE.isEmpty() && var0.getLevel() instanceof ServerLevel var1) {
            KingdomDressing.Job var11 = QUEUE.peek();
            if (var11.level == var1) {
                MutableBlockPos var3 = new MutableBlockPos();
                int var4 = Math.min(var11.x + 6, var11.centre.getX() + 76 + 1);

                for (int var5 = var11.x; var5 < var4; var5++) {
                    for (int var6 = var11.centre.getZ() - 76; var6 <= var11.centre.getZ() + 76 && var11.placed < 180; var6++) {
                        int var7 = var5 - var11.centre.getX();
                        int var8 = var6 - var11.centre.getZ();
                        double var9 = Math.sqrt((double)var7 * (double)var7 + (double)var8 * (double)var8);
                        if (!(var9 > 76.0) && var1.isLoaded(var3.set(var5, var11.groundY, var6))) {
                            dress(var11, var1, var3, var5, var6, var9);
                        }
                    }
                }

                var11.x = var4;
                if (var11.x > var11.centre.getX() + 76 || var11.placed >= 180) {
                    QUEUE.poll();
                    KingdomData var12 = KingdomData.get(var1);
                    KingdomData.Kingdom var13 = var12.kingdom(var11.centre);
                    var13.dressed = var11.tier;
                    var12.setDirty();
                    WakingWorld.LOGGER.info("kingdom {}: dressed to tier {} - {} pieces", new Object[]{Kingdoms.name(var11.centre), var11.tier, var11.placed});
                }
            }
        }
    }

    private static void dress(KingdomDressing.Job var0, ServerLevel var1, MutableBlockPos var2, int var3, int var4, double var5) {
        int var7 = var1.getHeight(Types.MOTION_BLOCKING, var3, var4);
        if (var7 > var1.getMinBuildHeight() + 1) {
            var2.set(var3, var7, var4);
            if (var1.getBlockState(var2).isAir()) {
                var2.set(var3, var7 - 1, var4);
                BlockState var8 = var1.getBlockState(var2);
                if (STONEWORK.contains(var8.getBlock()) || STREET.contains(var8.getBlock())) {
                    int var9 = hash(var3, var4);
                    int var10 = var7 - 1 - var0.groundY;
                    boolean var11 = Math.abs(var5 - 56.0) <= 6.0;
                    boolean var12 = var5 <= 29.0;
                    boolean var13 = var5 < 52.0;
                    if ((var11 || var12) && STONEWORK.contains(var8.getBlock()) && var10 >= 3) {
                        if (var0.tier >= 3 && var10 >= 8 && var9 % 17 == 0) {
                            put(var0, var1, var2.set(var3, var7, var4), banner(var0.centre));
                            return;
                        }

                        if (var10 >= 6 && var9 % 11 == 0) {
                            put(var0, var1, var2.set(var3, var7, var4), Blocks.LANTERN.defaultBlockState());
                            return;
                        }

                        if (var12) {
                            return;
                        }
                    }

                    if (var13) {
                        if (STONEWORK.contains(var8.getBlock()) && var10 >= -2 && var10 < 4 && var9 % 53 == 0) {
                            put(var0, var1, var2.set(var3, var7, var4), Blocks.LANTERN.defaultBlockState());
                        } else {
                            if (var0.tier >= 2 && STREET.contains(var8.getBlock()) && var10 >= -3 && var10 < 4 && var9 % 61 == 0) {
                                put(var0, var1, var2.set(var3, var7, var4), POTS[var9 % POTS.length]);
                            }
                        }
                    }
                }
            }
        }
    }

    private static BlockState banner(BlockPos var0) {
        return Kingdoms.banner(var0).defaultBlockState();
    }

    private static void put(KingdomDressing.Job var0, ServerLevel var1, BlockPos var2, BlockState var3) {
        if (var3.canSurvive(var1, var2) && !me.lovkar.wakingworld.compat.Colonies.keepOff(var1, var2)) {
            var1.setBlock(var2, var3, 3);
            var0.placed++;
        }
    }

    public static List<BlockPos> owed(ServerLevel var0) {
        ArrayList var1 = new ArrayList();

        for (KingdomData.Kingdom var3 : KingdomData.get(var0).all()) {
            if (var3.dressed < var3.tier) {
                var1.add(var3.center);
            }
        }

        return var1;
    }

    private static final class Job {
        final ServerLevel level;
        final BlockPos centre;
        final int tier;
        final int groundY;
        int x;
        int placed;

        Job(ServerLevel var1, BlockPos var2, int var3) {
            this.level = var1;
            this.centre = var2;
            this.tier = var3;
            this.groundY = var2.getY();
            this.x = var2.getX() - 76;
        }
    }
}
