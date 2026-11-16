package me.lovkar.wwvolcanoheight;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.cataclysm.Volcano;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Headless check for the volcano height report ("the volcano keeps building up ignoring the 24
 * height configuration"). On the flat rig a volcano of height 24 and foot 48 is forced at a known
 * spot and left to rise, crown and cool; then:
 * <ul>
 *   <li>the highest block it left is measured against the ground it rose from;</li>
 *   <li>site() - the world's own choice of where the next vent opens - is asked PICKS times by a
 *       fake player standing 60-120 blocks from the old vent, and no pick may put its foot on the
 *       old cone. If one lands on the cone itself, a second volcano is raised there the way the world
 *       would raise it, to show how high the two stand together;</li>
 *   <li>the rock test that finds cones the list does not hold is asked on, beside and well away from
 *       the cone, and site() is asked PICKS times more of a copy whose list has been wiped;</li>
 *   <li>the vent list must survive a save, and a tag from before it must load with an empty one.</li>
 * </ul>
 * Prints CHECK lines and RESULT, then stops the server.
 */
@Mod("wwvolcanoheight")
public class VolcanoHeight {
    private static final Logger LOG = LoggerFactory.getLogger("wwvolcanoheight");
    private static final String TAG = "[volcanoheight] ";
    /** Well away from the world spawn: site() keeps meteorSafeRadius (64) off it, which would hide the bug. */
    private static final int VX = 600, VZ = 40;
    private static final int HEIGHT = 24, FOOT = 48, RISE = 30;
    private static final int PICKS = 300, PICKS_PER_TICK = 5;
    /**
     * How many of the PICKS must come back with a site. From 60-120 blocks off the old vent, a pick
     * 90-160 blocks out clears the remembered cone on well over a third of its bearings, and site()
     * has 24 tries at it, so nearly every question finds somewhere; a quarter is a floor that only a
     * site() refusing nearly everything falls through.
     */
    private static final int MIN_PICKS = PICKS / 4;
    /** How far out the probe looks for the cone and anything it threw. */
    private static final int LOOK = 80;

    private int tick;
    private int stage;
    private int stageTick;
    private int idleAt;
    private int y0;
    /** The flat world's own sea level, under its ground: see Volcano.siteFor. */
    private int sea;
    private int failures;
    /** How far the first cone's rock goes, measured once it has cooled. */
    private double reach;
    private int asked;
    private final Tally listed = new Tally();
    private final Tally unlisted = new Tally();
    /** A copy of the volcano's data with its vent list wiped, so only the rock test stands in the way. */
    private Volcano bare;

    public VolcanoHeight(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTick(final ServerTickEvent.Post e) {
        tick++;
        final ServerLevel level = e.getServer().overworld();
        try {
            switch (stage) {
                case 0 -> {
                    if (tick < 60) return;
                    final MinecraftServer server = level.getServer();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
                    level.setDayTime(1000);
                    // the cone, its bombs and its ash, kept loaded so the rise and the cooling run
                    for (int cx = (VX - 128) >> 4; cx <= (VX + 128) >> 4; cx++) {
                        for (int cz = (VZ - 128) >> 4; cz <= (VZ + 128) >> 4; cz++) {
                            level.setChunkForced(cx, cz, true);
                        }
                    }
                    y0 = Cataclysms.surface(level, VX, VZ).getY();
                    sea = level.getChunkSource().getGenerator().getSeaLevel();
                    LOG.info(TAG + "surface y={} at {} {}, the generator's sea level {}", y0, VX, VZ, sea);
                    check("the server config reached the mod", WakingConfig.volcanoHeight() == HEIGHT && WakingConfig.volcanoRadius() == FOOT,
                            "volcanoHeight " + WakingConfig.volcanoHeight() + ", volcanoRadius " + WakingConfig.volcanoRadius());
                    check("the first volcano opens", Volcano.force(level, new BlockPos(VX, y0, VZ), HEIGHT, FOOT, RISE),
                            "forced at " + VX + " " + y0 + " " + VZ + ", height " + HEIGHT + ", foot " + FOOT + ", rise " + RISE + " s");
                    next();
                }
                case 1 -> {
                    if (!finished(level)) return;
                    reach = reach(level, VX, VZ);
                    final BlockPos top = highest(level, VX, VZ);
                    // the courses lie at baseY .. baseY + height - 1: a cone of height 24 on ground whose
                    // first air block is y0 has its highest block at y0 + 23, 24 blocks above the grass
                    check("the finished cone stands at most volcanoHeight above the ground", top.getY() <= y0 + HEIGHT - 1,
                            "highest block y=" + top.getY() + " at " + top.getX() + " " + top.getZ() + " ("
                                    + Math.round(dist(top.getX(), top.getZ(), VX, VZ)) + " from the vent), allowed y<=" + (y0 + HEIGHT - 1)
                                    + "; the cone's rock reaches " + Math.round(reach) + " blocks out");
                    next();
                }
                case 2 -> {
                    if (!ask(level, Volcano.get(level), listed)) return;
                    // it must also still find somewhere: a site() that refuses everything keeps every
                    // foot off the cone too, and would pass this on a world where no volcano can open
                    check("site() keeps a new foot off the first cone", listed.overlaps == 0 && listed.picks >= MIN_PICKS,
                            listed.detail() + "; at least " + MIN_PICKS + " picks wanted");
                    if (listed.highestOnCone == null) {
                        LOG.info(TAG + "no pick on the first cone, so no second eruption to watch");
                        stage = 4;
                        return;
                    }
                    // what the world would do with that pick: open a vent there, at the pick's height
                    final BlockPos at = listed.highestOnCone;
                    check("the second volcano opens where site() chose", Volcano.force(level, at, HEIGHT, FOOT, RISE),
                            "forced at " + at.getX() + " " + at.getY() + " " + at.getZ() + ", "
                                    + Math.round(dist(at.getX(), at.getZ(), VX, VZ)) + " from the first vent");
                    next();
                }
                case 3 -> {
                    if (!finished(level)) return;
                    final BlockPos top = highest(level, VX, VZ);
                    check("two eruptions together stand at most volcanoHeight above the ground", top.getY() <= y0 + HEIGHT - 1,
                            "highest block y=" + top.getY() + " at " + top.getX() + " " + top.getZ() + ", "
                                    + (top.getY() - y0 + 1) + " blocks above the grass; allowed y<=" + (y0 + HEIGHT - 1));
                    next();
                }
                case 4 -> {
                    // the rock test on its own, which knows nothing of the list: on the summit beside the
                    // crater, half-way down the flank, and a vent off the cone whose foot would lap some 25
                    // blocks onto it; then grass far off, and grass in among the rock the bombs threw
                    final boolean summit = Volcano.oldConeFor(level, VX + 14, VZ, FOOT);
                    final boolean flank = Volcano.oldConeFor(level, VX - 35, VZ, FOOT);
                    final boolean lapping = Volcano.oldConeFor(level, VX, VZ + 90, FOOT);
                    final boolean far = Volcano.oldConeFor(level, VX + 300, VZ, FOOT);
                    final boolean bombs = Volcano.oldConeFor(level, VX - 140, VZ, FOOT);
                    check("the rock test finds the cone the list is not asked about", summit && flank && lapping,
                            "summit (14 from the vent) " + summit + ", flank (35) " + flank + ", a foot lapping onto it (90) " + lapping);
                    check("the rock test leaves plain grass alone", !far && !bombs,
                            "grass 300 from the vent " + far + ", grass 140 from it among the thrown rock " + bombs);
                    final HolderLookup.Provider registries = level.registryAccess();
                    final CompoundTag saved = Volcano.get(level).save(new CompoundTag(), registries);
                    saved.remove("Vents");
                    bare = Volcano.loadFor(saved, registries);
                    asked = 0;
                    next();
                }
                case 5 -> {
                    if (!ask(level, bare, unlisted)) return;
                    check("with the list wiped, site() still opens no vent on the old cone",
                            bare.vents().isEmpty() && unlisted.onCone == 0 && unlisted.picks >= MIN_PICKS,
                            "list " + bare.vents().size() + " vents; " + unlisted.detail() + "; at least " + MIN_PICKS + " picks wanted");
                    next();
                }
                case 6 -> {
                    final HolderLookup.Provider registries = level.registryAccess();
                    final Volcano live = Volcano.get(level);
                    final CompoundTag saved = live.save(new CompoundTag(), registries);
                    final List<Volcano.Vent> back = Volcano.loadFor(saved, registries).vents();
                    final Volcano.Vent first = new Volcano.Vent(VX, y0, VZ, FOOT);
                    check("a save and load keeps the vent list", live.vents().contains(first) && back.equals(live.vents()),
                            "saved " + live.vents() + ", loaded " + back);
                    // a tag as 0.3.0-beta.6 wrote it: the same keys, no list
                    final CompoundTag beta6 = saved.copy();
                    beta6.remove("Vents");
                    final List<Volcano.Vent> none = Volcano.loadFor(beta6, registries).vents();
                    check("a save from before the list loads with an empty one", none.isEmpty()
                                    && beta6.getInt("X") == VX && beta6.getInt("Y") == y0 && beta6.getInt("Z") == VZ
                                    && beta6.getInt("Foot") == FOOT && beta6.getInt("Courses") == HEIGHT,
                            "loaded " + none.size() + " vents; X Y Z Foot Courses = " + beta6.getInt("X") + " " + beta6.getInt("Y") + " "
                                    + beta6.getInt("Z") + " " + beta6.getInt("Foot") + " " + beta6.getInt("Courses"));
                    stage = 50;
                }
                case 50 -> {
                    LOG.info(TAG + "RESULT volcanoheight: {}", failures == 0 ? "PASS" : "FAIL");
                    stage = 99;
                    level.getServer().execute(() -> level.getServer().halt(false));
                }
                default -> {
                }
            }
        } catch (final Throwable t) {
            LOG.error(TAG + "EXCEPTION", t);
            LOG.info(TAG + "RESULT volcanoheight: FAIL");
            stage = 99;
            level.getServer().execute(() -> level.getServer().halt(false));
        }
    }

    private void next() {
        stage++;
        stageTick = tick;
        idleAt = 0;
    }

    private void check(final String name, final boolean ok, final String detail) {
        if (!ok) failures++;
        LOG.info(TAG + "CHECK {}: {} {}", name, ok ? "OK" : "FAIL", detail);
    }

    /**
     * True once the volcano is idle again and ten more seconds have passed, so the last bombs and
     * thrown blocks have come down. A volcano still busy after six minutes fails the run.
     */
    private boolean finished(final ServerLevel level) {
        if (Volcano.busy(level)) {
            if (tick - stageTick > 20 * 360) throw new IllegalStateException("the volcano is still going after six minutes");
            return false;
        }
        if (idleAt == 0) {
            idleAt = tick;
            LOG.info(TAG + "the volcano is finished and cooled after {} s", (tick - stageTick) / 20);
        }
        return tick - idleAt >= 200;
    }

    /**
     * A few more of the PICKS questions to site(), a handful a tick so the chunks it reads are
     * generated over several ticks rather than in one. True once all of them have been asked.
     */
    private boolean ask(final ServerLevel level, final Volcano v, final Tally into) {
        final FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        for (int n = 0; n < PICKS_PER_TICK && asked < PICKS; n++, asked++) {
            into.add(v.siteFor(level, stand(fake, asked), RandomSource.create(7000L + asked), sea));
        }
        return asked >= PICKS;
    }

    /** The fake player for one question: 60-120 blocks from the old vent, on its own bearing. */
    private FakePlayer stand(final FakePlayer fake, final int i) {
        final java.util.Random r = new java.util.Random(31L * i + 5);
        final double a = r.nextDouble() * Math.PI * 2;
        final double d = 60 + r.nextDouble() * 60;
        fake.setPos(VX + Math.cos(a) * d, y0, VZ + Math.sin(a) * d);
        return fake;
    }

    /**
     * The answers site() gave. A cone of foot 48 lays its bottom course out to 1.2 x the foot on its
     * ridges (Volcano.outerAt), so a new vent nearer to the old one than the old cone's measured
     * reach plus that has a foot that overlaps the old cone.
     */
    private final class Tally {
        int picks, nulls, overlaps, onCone;
        double closest = Double.MAX_VALUE;
        BlockPos highestOnCone;

        void add(final BlockPos pick) {
            if (pick == null) {
                nulls++;
                return;
            }
            picks++;
            final double d = dist(pick.getX(), pick.getZ(), VX, VZ);
            closest = Math.min(closest, d);
            if (d < reach + FOOT * 1.2 + 1) overlaps++;
            if (d < reach) {
                onCone++;
                if (highestOnCone == null || pick.getY() > highestOnCone.getY()) highestOnCone = pick;
            }
        }

        String detail() {
            return "asked " + PICKS + ": picks " + picks + ", nulls " + nulls + ", feet overlapping the first cone " + overlaps
                    + " (closest vent " + (picks == 0 ? "-" : Math.round(closest)) + " from the old one; overlap below "
                    + Math.round(reach + FOOT * 1.2 + 1) + "), vents on the cone itself " + onCone
                    + (highestOnCone == null ? "" : " (highest pick y=" + highestOnCone.getY() + ", " + (highestOnCone.getY() - y0)
                    + " above the ground: a cone of " + HEIGHT + " opened there tops out at y=" + (highestOnCone.getY() + HEIGHT - 1) + ")");
        }
    }

    /** The highest non-air block within LOOK of the vent. */
    private static BlockPos highest(final ServerLevel level, final int vx, final int vz) {
        BlockPos best = new BlockPos(vx, level.getMinBuildHeight(), vz);
        for (int x = vx - LOOK; x <= vx + LOOK; x++) {
            for (int z = vz - LOOK; z <= vz + LOOK; z++) {
                if (dist(x, z, vx, vz) > LOOK) continue;
                final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (y > best.getY()) best = new BlockPos(x, y, z);
            }
        }
        return best;
    }

    /**
     * How far out the cone's own rock goes on the ground course: along 72 bearings from inside the
     * flank outwards, the last block of the mountain before three open blocks in a row (the thrown
     * bombs lie further out, beyond a gap of grass).
     */
    private double reach(final ServerLevel level, final int vx, final int vz) {
        double best = 0;
        for (int b = 0; b < 72; b++) {
            final double a = b * Math.PI / 36;
            double last = 0;
            int open = 0;
            for (double d = 12; d <= LOOK && open < 6; d += 0.5) {
                final BlockPos at = BlockPos.containing(vx + 0.5 + Math.cos(a) * d, y0, vz + 0.5 + Math.sin(a) * d);
                if (cone(level.getBlockState(at))) {
                    last = d;
                    open = 0;
                } else {
                    open++;
                }
            }
            best = Math.max(best, last);
        }
        return best;
    }

    private static boolean cone(final BlockState s) {
        return s.is(Blocks.BASALT) || s.is(Blocks.TUFF) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.GILDED_BLACKSTONE)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.LAVA);
    }

    private static double dist(final double x, final double z, final double x2, final double z2) {
        return Math.sqrt((x - x2) * (x - x2) + (z - z2) * (z - z2));
    }
}
