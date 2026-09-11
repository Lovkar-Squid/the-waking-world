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
        begin(var0, var1, var2, var3, null);
    }

    /** As above, with something to do once the last course is laid (the people moving in, say). */
    public static void begin(ServerLevel var0, BlockPos var1, KingdomBuild.Plan var2, String var3, Runnable done) {
        begin(var0, var1, var2, var3, done, false);
    }

    /**
     * As above; {@code force} lays every course over whatever stands there, not only into air and the
     * natural ground - the way a house is raised again over itself. A block that is already what the
     * plan wants is left alone.
     */
    public static void begin(ServerLevel var0, BlockPos var1, KingdomBuild.Plan var2, String var3, Runnable done, boolean force) {
        if (!var2.courses.isEmpty()) {
            ArrayList<KingdomBuild.Course> var4 = new ArrayList<>(var2.courses);
            boolean drawing = var2.where != null;
            var4.sort((var1x, var2x) -> {
                if (drawing) {
                    // a drawing is cut first, from the top down, and built after, from the ground up - so the
                    // hill is gone before the farmland that will not stand under it and the crop that wants the sky
                    boolean a = var1x.state().isAir(), b = var2x.state().isAir();
                    if (a != b) return a ? -1 : 1;
                    if (a) {
                        int y = Integer.compare(var2x.at().getY(), var1x.at().getY());
                        if (y != 0) return y;
                    }
                }
                int var3x = Integer.compare(var1x.at().getY(), var2x.at().getY());
                if (var3x != 0) {
                    return var3x;
                } else {
                    double var4x = var1x.at().distSqr(var1);
                    double var6 = var2x.at().distSqr(var1);
                    return Double.compare(var4x, var6);
                }
            });
            KingdomBuild.Job job = new KingdomBuild.Job(var0, var1, var4, var3);
            job.done = done;
            job.force = force;
            QUEUE.add(job);
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
                        var3 += lay(var1, var6, var6.courses.get(var6.next++));
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
                        if (var6.done != null) var6.done.run();
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
                var2 += lay(var0, var3, var3.courses.get(var3.next++));
            }

            if (var3.more()) {
                break;
            }

            QUEUE.poll();
            WakingWorld.LOGGER.info("kingdom: finished raising a {} at {}", var3.what, var3.centre.toShortString());
            if (var3.done != null) var3.done.run();
        }

        return var2;
    }

    /**
     * Lays one course: air takes out whatever is there; a block goes into air, into what can be replaced
     * and into the natural ground (or, when the job forces, into anything), and only where it can stand.
     * Returns how many blocks were set.
     */
    private static int lay(ServerLevel level, KingdomBuild.Job job, KingdomBuild.Course c) {
        if (!level.isLoaded(c.at())) return 0;
        BlockState was = level.getBlockState(c.at());
        if (c.state().isAir()) {
            if (was.isAir()) return 0;
            level.setBlock(c.at(), c.state(), 3);
            return 1;
        }
        if (was == c.state()) return 0;
        if (!(job.force || was.isAir() || was.canBeReplaced() || KingdomExpansion.natural(was))) return 0;
        int set = 0;
        if (!c.state().canSurvive(level, c.at())) {
            // carved into the ground: what stands on this block is the hill the drawing replaces, and
            // some blocks (farmland, a path) will not stand under anything solid - it comes off first
            BlockPos up = c.at().above();
            BlockState over = level.getBlockState(up);
            if (!was.isAir() && !over.isAir() && KingdomExpansion.natural(over)) {
                level.setBlock(up, Blocks.AIR.defaultBlockState(), 3);
                set++;
            }
            if (!c.state().canSurvive(level, c.at())) {
                if (!job.retried) job.again.add(c);
                return set;
            }
        }
        level.setBlock(c.at(), c.state(), 3);
        return set + 1;
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
        Runnable done;
        boolean force;

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
        /** Position -> index of its course, when the plan is a drawing; null when every course is kept. */
        private final java.util.Map<Long, Integer> where;

        /** The works' plan: every course is kept and laid in turn, so an air course before a block course clears the ground for it. */
        public Plan() {
            this(false);
        }

        /**
         * A drawing, when {@code lastWins}: a later course at a position replaces the earlier one, so a
         * door drawn into a wall is a door and a pane drawn into a wall is a window. Without it the mason
         * lays the wall first and then refuses the door, since a wall is not ground - which is how the
         * first suburb came up with no doors.
         */
        public Plan(boolean lastWins) {
            this.where = lastWins ? new java.util.HashMap<>() : null;
        }

        public void set(int var1, int var2, int var3, BlockState var4) {
            KingdomBuild.Course c = new KingdomBuild.Course(new BlockPos(var1, var2, var3), var4);
            if (this.where != null) {
                Integer i = this.where.putIfAbsent(c.at().asLong(), this.courses.size());
                if (i != null) {
                    this.courses.set(i, c);
                    return;
                }
            }
            this.courses.add(c);
        }

        public void fill(int var1, int var2, int var3, int var4, BlockState var5) {
            for (int var6 = var2; var6 <= var3; var6++) {
                this.set(var1, var6, var4, var5);
            }
        }

        public int size() {
            return this.courses.size();
        }

        /** Every course in the order it was drawn - for previews and tests; the mason sorts its own copy. */
        public void forEach(java.util.function.BiConsumer<BlockPos, BlockState> visitor) {
            for (KingdomBuild.Course c : this.courses) visitor.accept(c.at(), c.state());
        }
    }
}
