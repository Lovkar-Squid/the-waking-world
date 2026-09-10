package me.lovkar.wakingworld.kingdom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

public final class KingdomBuild {
    private static final int PER_TICK = 4;
    private static final double WATCHED = 190.0;
    private static final Deque<KingdomBuild.Job> QUEUE = new ArrayDeque<>();

    private KingdomBuild() {
    }

    public static void begin(ServerLevel var0, BlockPos var1, KingdomBuild.Plan var2, String var3) {
        if (!var2.courses.isEmpty()) {
            ArrayList<KingdomBuild.Course> var4 = new ArrayList<>(var2.courses);
            var4.sort((var1x, var2x) -> {
                int var3x = Integer.compare(var1x.at().getY(), var2x.at().getY());
                if (var3x != 0) {
                    return var3x;
                } else {
                    double var4x = var1x.at().distSqr(var1);
                    double var6 = var2x.at().distSqr(var1);
                    return Double.compare(var4x, var6);
                }
            });
            QUEUE.add(new KingdomBuild.Job(var0, var1, var4, var3));
        }
    }

    public static int pending() {
        int var0 = 0;

        for (KingdomBuild.Job var2 : QUEUE) {
            var0 += var2.courses.size() - var2.next + var2.again.size();
        }

        return var0;
    }

    public static void tick(Post var0) {
        if (!QUEUE.isEmpty() && var0.getLevel() instanceof ServerLevel var1) {
            KingdomBuild.Job var6 = QUEUE.peek();
            if (var6.level == var1) {
                if (var1.getNearestPlayer((double)var6.centre.getX() + 0.5, (double)var6.centre.getY(), (double)var6.centre.getZ() + 0.5, 190.0, false) != null
                    )
                 {
                    int var3 = 0;

                    while (var3 < 4 && var6.more()) {
                        KingdomBuild.Course var4 = var6.courses.get(var6.next++);
                        if (var1.isLoaded(var4.at())) {
                            BlockState var5 = var1.getBlockState(var4.at());
                            if (var4.state().isAir()) {
                                if (!var5.isAir()) {
                                    var1.setBlock(var4.at(), var4.state(), 3);
                                    var3++;
                                }
                            } else if (var5.isAir() || var5.canBeReplaced() || KingdomExpansion.natural(var5)) {
                                if (!var4.state().canSurvive(var1, var4.at())) {
                                    if (!var6.retried) {
                                        var6.again.add(var4);
                                    }
                                } else {
                                    var1.setBlock(var4.at(), var4.state(), 3);
                                    var3++;
                                }
                            }
                        }
                    }

                    if (var3 > 0 && var1.getGameTime() % 6L == 0L) {
                        BlockPos var7 = var6.courses.get(Math.max(0, var6.next - 1)).at();
                        var1.playSound(null, var7, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.1F, 0.85F + var1.random.nextFloat() * 0.3F);
                        Cataclysms.puff(
                            var1,
                            ParticleTypes.CLOUD,
                            (double)var7.getX() + 0.5,
                            (double)var7.getY() + 1.0,
                            (double)var7.getZ() + 0.5,
                            2,
                            0.25,
                            0.25,
                            0.25,
                            0.01
                        );
                    }

                    if (!var6.more()) {
                        QUEUE.poll();
                        var1.playSound(null, var6.centre, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 2.0F, 1.2F);
                        Cataclysms.ring(
                            var1, 15257738, 6.0F, (double)var6.centre.getX() + 0.5, (double)var6.centre.getY() + 0.4, (double)var6.centre.getZ() + 0.5
                        );
                        WakingWorld.LOGGER.info("kingdom: finished raising a {} at {}", var6.what, var6.centre.toShortString());
                    }
                }
            }
        }
    }

    public static int drain(ServerLevel var0, int var1) {
        int var2 = 0;

        while (var2 < var1 && !QUEUE.isEmpty()) {
            KingdomBuild.Job var3 = QUEUE.peek();
            if (var3.level != var0) {
                break;
            }

            while (var2 < var1 && var3.more()) {
                KingdomBuild.Course var4 = var3.courses.get(var3.next++);
                if (var0.isLoaded(var4.at())) {
                    BlockState var5 = var0.getBlockState(var4.at());
                    if (var4.state().isAir()) {
                        if (!var5.isAir()) {
                            var0.setBlock(var4.at(), var4.state(), 3);
                            var2++;
                        }
                    } else if (var5.isAir() || var5.canBeReplaced() || KingdomExpansion.natural(var5)) {
                        if (!var4.state().canSurvive(var0, var4.at())) {
                            if (!var3.retried) {
                                var3.again.add(var4);
                            }
                        } else {
                            var0.setBlock(var4.at(), var4.state(), 3);
                            var2++;
                        }
                    }
                }
            }

            if (var3.more()) {
                break;
            }

            QUEUE.poll();
            WakingWorld.LOGGER.info("kingdom: finished raising a {} at {}", var3.what, var3.centre.toShortString());
        }

        return var2;
    }

    public static void forget(ServerLevel var0) {
        QUEUE.removeIf(var1 -> var1.level == var0);
    }

    public static boolean grounded(ServerLevel var0, BlockPos var1) {
        return !var0.getBlockState(var1.below()).isAir() || !var0.getBlockState(var1.below(2)).isAir();
    }

    static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static record Course(BlockPos at, BlockState state) {
    }

    private static final class Job {
        final ServerLevel level;
        final BlockPos centre;
        List<KingdomBuild.Course> courses;
        final String what;
        int next;
        final List<KingdomBuild.Course> again = new ArrayList<>();
        boolean retried;

        Job(ServerLevel var1, BlockPos var2, List<KingdomBuild.Course> var3, String var4) {
            this.level = var1;
            this.centre = var2;
            this.courses = var3;
            this.what = var4;
        }

        boolean more() {
            if (this.next < this.courses.size()) {
                return true;
            } else if (!this.retried && !this.again.isEmpty()) {
                this.retried = true;
                this.courses = new ArrayList<>(this.again);
                this.again.clear();
                this.next = 0;
                return true;
            } else {
                return false;
            }
        }
    }

    public static final class Plan {
        private final List<KingdomBuild.Course> courses = new ArrayList<>();

        public void set(int var1, int var2, int var3, BlockState var4) {
            this.courses.add(new KingdomBuild.Course(new BlockPos(var1, var2, var3), var4));
        }

        public void fill(int var1, int var2, int var3, int var4, BlockState var5) {
            for (int var6 = var2; var6 <= var3; var6++) {
                this.set(var1, var6, var4, var5);
            }
        }

        public int size() {
            return this.courses.size();
        }
    }
}
