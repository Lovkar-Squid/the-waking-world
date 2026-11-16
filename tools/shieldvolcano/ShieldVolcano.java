package me.lovkar.wwshieldvolcano;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.cataclysm.Volcano;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless check for the shield volcano (0.3.0-beta.8, volcanoShape). On the flat rig, whose grass
 * lies at y=-1, a shield of height 28 is opened through the real command at a known spot with a short
 * rise and left to erupt and cool; then:
 * <ul>
 *   <li>its highest block, its slope along 16 radial lines from the foot to the rim, and how far its
 *       foot lies beyond the rim are measured;</li>
 *   <li>at the crown, the caldera's width and its lava lake, and the flows of lava on the flanks;
 *       after the cooling, the caldera is rock round a small glowing heart, every block where a flow
 *       ran is rock, mostly basalt, and no lava is left anywhere on the flanks;</li>
 *   <li>a save and load keeps the shape, and a tag from before shapes loads as a cone;</li>
 *   <li>mixed mode, rolled through Volcano.shapeFor with fixed random sources, makes about one in
 *       three a shield, and volcanoShape is mixed when nothing sets it;</li>
 *   <li>the world's site choice, asked by a fake player near the finished shield for a cone and for
 *       another shield, never puts the new foot on it; asked for a shield by one standing on the world
 *       spawn, it keeps the shield's toe meteorSafeRadius off the spawn;</li>
 *   <li>the cost of the volcano's work in each server tick is logged by phase (rig timings are noisy:
 *       it is never a failure).</li>
 * </ul>
 * Prints CHECK lines and RESULT, then stops the server.
 */
@Mod("wwshieldvolcano")
public class ShieldVolcano {
    private static final Logger LOG = LoggerFactory.getLogger("wwshieldvolcano");
    private static final String TAG = "[shieldvolcano] ";
    /** The vent: well away from the world spawn, which the site choice keeps 64 blocks off. */
    private static final int VX = 700, VZ = 200;
    /** The cone height asked for; a shield is half of it. Must match rigconfig's volcanoHeight. */
    private static final int HEIGHT = 28, COURSES = 14, RISE = 40;
    /** rigconfig's volcanoRadius: the cap a cone's foot has and a shield's does not. */
    private static final int CONFIG_FOOT = 48;
    /** The design: a foot about four blocks out for every course, beyond the rim. */
    private static final double BEYOND = 4.0 * COURSES;
    private static final int RAYS = 16;
    private static final int PICKS = 150, PICKS_PER_TICK = 5;
    private static final int ROLLS = 3000;
    /** How far out the probe looks for the shield. */
    private static final int LOOK = 100;

    private int tick;
    private int stage;
    private int stageTick;
    private int idleAt;
    private int y0;
    private int failures;
    private int risenAt;
    private int asked;
    /** The shield's widest foot measured along 72 bearings once it has cooled, and its foot as the volcano gives it. */
    private double measuredFoot;
    private int footBound;
    /** Every block of lava on the flanks at the crown, and the lake's own. */
    private final Set<BlockPos> flowCells = new HashSet<>();
    private final Tally cones = new Tally("a cone");
    private final Tally shields = new Tally("another shield");
    private final SpawnTally atSpawn = new SpawnTally();
    /** Where the world spawn is moved for the last site questions: some 600 blocks from the shield. */
    private static final int SPAWN_X = 100, SPAWN_Z = 200, SPAWN_PICKS = 60;

    public ShieldVolcano(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTick(final ServerTickEvent.Post e) {
        tick++;
        final ServerLevel level = e.getServer().overworld();
        try {
            if (stage == 1 || stage == 2) timing(level);
            switch (stage) {
                case 0 -> {
                    if (tick < 60) return;
                    final MinecraftServer server = level.getServer();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
                    level.setDayTime(1000);
                    // the shield, its bombs and its ash, kept loaded so the rise and the cooling run
                    for (int cx = (VX - 176) >> 4; cx <= (VX + 176) >> 4; cx++) {
                        for (int cz = (VZ - 176) >> 4; cz <= (VZ + 176) >> 4; cz++) {
                            level.setChunkForced(cx, cz, true);
                        }
                    }
                    y0 = Cataclysms.surface(level, VX, VZ).getY();
                    LOG.info(TAG + "surface y={} at {} {}", y0, VX, VZ);
                    check("the server config reached the mod", WakingConfig.volcanoHeight() == HEIGHT
                                    && WakingConfig.volcanoRadius() == CONFIG_FOOT,
                            "volcanoHeight " + WakingConfig.volcanoHeight() + ", volcanoRadius " + WakingConfig.volcanoRadius());
                    rolls();
                    // through the real command, as an operator would: the shape is its last argument
                    final List<String> said = new ArrayList<>();
                    final String line = "wakingworld volcano " + VX + " " + y0 + " " + VZ + " " + HEIGHT + " 16 " + RISE + " shield";
                    final int r = command(level, line, new Vec3(VX + 120.5, y0, VZ + 0.5), said);
                    final Volcano v = Volcano.get(level);
                    check("the command opens a shield", r == 1 && Volcano.busy(level) && v.shape() == Volcano.Shape.SHIELD
                                    && v.courses() == COURSES && v.foot() > CONFIG_FOOT,
                            "/" + line + " returned " + r + ", shape " + v.shape() + ", " + v.courses() + " courses, foot "
                                    + v.foot() + ", said " + said);
                    footBound = v.foot();
                    Volcano.resetSlowest();
                    next();
                }
                case 1 -> {
                    final Volcano v = Volcano.get(level);
                    final String phase = v.phaseName();
                    if (phase.equals("WARNING") || phase.equals("RISING")) {
                        if (tick - stageTick > 20 * 300) throw new IllegalStateException("the shield is still rising after five minutes");
                        return;
                    }
                    if (!phase.equals("SETTLING")) throw new IllegalStateException("the shield went from rising to " + phase);
                    risenAt = tick;
                    LOG.info(TAG + "the shield is crowned {} s after the command", (tick - stageTick) / 20);
                    erupting(level);
                    next();
                }
                case 2 -> {
                    if (!finished(level)) return;
                    final StringBuilder by = new StringBuilder();
                    for (final String phase : List.of("WARNING", "RISING", "crown", "SETTLING", "COOLING")) {
                        final long[] t = costs.get(phase);
                        if (t == null) continue;
                        by.append(by.length() == 0 ? "" : "; ").append(phase).append(" ").append(t[2]).append(" ticks, slowest ")
                                .append(String.format("%.1f", t[0] / 1e6)).append(" ms, mean ")
                                .append(String.format("%.1f", t[1] / 1e6 / Math.max(1, t[2]))).append(" ms");
                    }
                    LOG.info(TAG + "the volcano's own work per call - once a second, and every tick while a shield's course"
                            + " is being laid (logged only, never a failure): {}", by);
                    cooled(level);
                    asked = 0;
                    next();
                }
                case 3 -> {
                    if (!ask(level)) return;
                    check("site() keeps a new cone's foot off the shield", cones.overlaps == 0 && cones.picks >= PICKS / 4,
                            cones.detail() + "; at least " + PICKS / 4 + " picks wanted");
                    check("site() keeps another shield's foot off the shield", shields.overlaps == 0 && shields.picks >= PICKS / 4,
                            shields.detail() + "; at least " + PICKS / 4 + " picks wanted");
                    asked = 0;
                    next();
                }
                case 4 -> {
                    if (!askAtSpawn(level)) return;
                    final int keep = WakingConfig.meteorSafeRadius() + footBound + 4;
                    check("site() keeps a shield's toe meteorSafeRadius off the world spawn",
                            atSpawn.picks >= SPAWN_PICKS / 4 && atSpawn.closest >= keep,
                            "asked " + SPAWN_PICKS + " for a fake player standing on the spawn: picks " + atSpawn.picks + ", nulls "
                                    + atSpawn.nulls + ", the closest vent " + (atSpawn.picks == 0 ? "-" : Math.round(atSpawn.closest))
                                    + " from the spawn; " + keep + " wanted = meteorSafeRadius " + WakingConfig.meteorSafeRadius()
                                    + " + the shield's foot " + footBound + " + its toe 4");
                    saves(level);
                    stage = 50;
                }
                case 50 -> {
                    LOG.info(TAG + "RESULT shieldvolcano: {}", failures == 0 ? "PASS" : "FAIL");
                    stage = 99;
                    level.getServer().execute(() -> level.getServer().halt(false));
                }
                default -> {
                }
            }
        } catch (final Throwable t) {
            LOG.error(TAG + "EXCEPTION", t);
            LOG.info(TAG + "RESULT shieldvolcano: FAIL");
            stage = 99;
            level.getServer().execute(() -> level.getServer().halt(false));
        }
    }

    // ---- the checks ------------------------------------------------------------------------

    /**
     * volcanoShape's default, and mixed mode rolled ROLLS times through the seam on one random source
     * with a fixed seed, as the world rolls its own one volcano after another. (A fresh source for
     * each roll, seeded 4000, 4001, ..., is no good: the first numbers of neighbouring seeds lean
     * together, and a coin tossed that way came up heads 78 times in a hundred.)
     */
    private void rolls() {
        int mixed = 0, cone = 0, shield = 0;
        final RandomSource rnd = RandomSource.create(4000L);
        for (int i = 0; i < ROLLS; i++) {
            if (Volcano.shapeFor("mixed", rnd) == Volcano.Shape.SHIELD) mixed++;
            if (Volcano.shapeFor("cone", rnd) == Volcano.Shape.SHIELD) cone++;
            if (Volcano.shapeFor("shield", rnd) == Volcano.Shape.SHIELD) shield++;
        }
        final double share = mixed / (double) ROLLS;
        check("volcanoShape is mixed when nothing sets it", "mixed".equals(WakingConfig.volcanoShape()),
                "volcanoShape = " + WakingConfig.volcanoShape() + " (rigconfig leaves it out)");
        check("mixed makes about one in three a shield", share >= 0.30 && share <= 0.37 && cone == 0 && shield == ROLLS,
                "mixed: " + mixed + " shields of " + ROLLS + " (" + String.format("%.3f", share) + ", 0.30-0.37 wanted); cone: "
                        + cone + "; shield: " + shield);
    }

    /** At the crown: the caldera and its lake, and the flows on the flanks. Remembers every block of flow. */
    private void erupting(final ServerLevel level) {
        final int rimY = y0 + COURSES - 1, lakeY = rimY - 1;
        // the caldera: from the middle, how far each way at the rim's height before the rim itself
        final List<String> across = new ArrayList<>();
        boolean widthOk = true;
        int measured = 0;
        for (int b = 0; b < 4; b++) {
            final double a = b * Math.PI / 4;
            final double one = toRim(level, a, rimY), other = toRim(level, a + Math.PI, rimY);
            if (one < 0 || other < 0) {
                across.add(Math.round(Math.toDegrees(a)) + " deg: through a notch");
                continue;
            }
            measured++;
            final double w = one + other;
            widthOk &= w >= 12 && w <= 16;
            across.add(Math.round(Math.toDegrees(a)) + " deg: " + String.format("%.1f", w));
        }
        int lake = 0, cells = 0, deep = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz > 36) continue;
                cells++;
                if (level.getBlockState(new BlockPos(VX + dx, lakeY, VZ + dz)).is(Blocks.LAVA)) lake++;
                if (level.getBlockState(new BlockPos(VX + dx, lakeY - 1, VZ + dz)).is(Blocks.LAVA)) deep++;
            }
        }
        check("the caldera is 12-16 across and holds a lava lake", widthOk && measured >= 2 && lake >= cells * 0.9,
                "across " + across + "; lava at the lake's surface (y=" + lakeY + ", one below the rim) in " + lake + " of "
                        + cells + " columns within 6 of the middle, " + deep + " of them two deep");
        // the flows: lava lying open on the flanks outside the caldera, grouped by bearing
        final int edge = footBound + 2;
        final boolean[] bins = new boolean[72];
        final List<BlockPos> lava = new ArrayList<>();
        double furthest = 0;
        for (int dx = -edge; dx <= edge; dx++) {
            for (int dz = -edge; dz <= edge; dz++) {
                final double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 10 || d > edge) continue;
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, VX + dx, VZ + dz) - 1;
                final BlockPos at = new BlockPos(VX + dx, top, VZ + dz);
                if (!level.getBlockState(at).is(Blocks.LAVA)) continue;
                lava.add(at);
                flowCells.add(at);
                furthest = Math.max(furthest, d);
                bins[bin(dx, dz)] = true;
            }
        }
        final List<int[]> groups = groups(bins);
        final List<String> flows = new ArrayList<>();
        boolean ranDown = true;
        for (final int[] g : groups) {
            double near = Double.MAX_VALUE, far = 0;
            int n = 0;
            for (final BlockPos p : lava) {
                if (!inGroup(bin(p.getX() - VX, p.getZ() - VZ), g)) continue;
                final double d = dist(p.getX(), p.getZ(), VX, VZ);
                near = Math.min(near, d);
                far = Math.max(far, d);
                n++;
            }
            ranDown &= near <= 16 && far >= footBound * 0.75;
            flows.add(n + " blocks from " + Math.round(near) + " to " + Math.round(far) + " out, bearing "
                    + (g[0] % 72) * 5 + "-" + ((g[1] % 72) * 5 + 5) + " deg");
        }
        check("two or three flows run from the rim down the flanks", groups.size() >= 2 && groups.size() <= 3 && ranDown
                        && furthest <= footBound,
                lava.size() + " blocks of lava on the flanks in " + groups.size() + " flows " + flows + "; the furthest "
                        + Math.round(furthest) + " out, the shield's foot " + footBound);
        // and the shape survives a save part way through
        final HolderLookup.Provider registries = level.registryAccess();
        final CompoundTag tag = Volcano.get(level).save(new CompoundTag(), registries);
        final Volcano back = Volcano.loadFor(tag, registries);
        check("a save during the eruption keeps the shield", back.shape() == Volcano.Shape.SHIELD && back.courses() == COURSES
                        && back.foot() == footBound && back.phaseName().equals("SETTLING"),
                "saved Shape=" + tag.getString("Shape") + ", loaded " + back.shape() + ", " + back.courses() + " courses, foot "
                        + back.foot() + ", " + back.phaseName());
    }

    /** From the middle along bearing a at height y: how far to the first solid block of the rim, or -1 within 12. */
    private static double toRim(final ServerLevel level, final double a, final int y) {
        for (double t = 0; t <= 12; t += 0.05) {
            final BlockPos at = BlockPos.containing(VX + 0.5 + Math.cos(a) * t, y, VZ + 0.5 + Math.sin(a) * t);
            final BlockState s = level.getBlockState(at);
            if (!s.isAir() && s.getFluidState().isEmpty()) return t;
        }
        return -1;
    }

    /** Once it has cooled: height, slope, foot, the caldera, the flows set into rock and no lava left on the flanks. */
    private void cooled(final ServerLevel level) {
        final int rimY = y0 + COURSES - 1, lakeY = rimY - 1;
        final BlockPos top = highest(level);
        check("the shield stands at most 14 above the ground", top.getY() <= rimY,
                "highest block y=" + top.getY() + " at " + top.getX() + " " + top.getZ() + " ("
                        + Math.round(dist(top.getX(), top.getZ(), VX, VZ)) + " from the vent), " + (top.getY() - (y0 - 1))
                        + " above the grass at y=" + (y0 - 1) + "; allowed y<=" + rimY + " (no extra block for the lip)");
        // the slope and the foot along RAYS radial lines
        double worstRun = Double.MAX_VALUE;
        String worst = "-";
        int cliffs = 0;
        String cliff = "";
        final List<Double> beyond = new ArrayList<>();
        double footMin = Double.MAX_VALUE, footMax = 0;
        for (int i = 0; i < RAYS; i++) {
            final Ray ray = ray(level, i * Math.PI * 2 / RAYS);
            for (int lv = y0; lv < ray.peak; lv++) {
                final double run = ray.reach[lv - y0] - ray.reach[lv - y0 + 1];
                if (run < worstRun) {
                    worstRun = run;
                    worst = "ray " + Math.round(i * 360.0 / RAYS) + " deg, from y=" + lv + " to y=" + (lv + 1) + ": "
                            + String.format("%.2f", run) + " (steps at " + String.format("%.1f", ray.reach[lv - y0]) + " and "
                            + String.format("%.1f", ray.reach[lv - y0 + 1]) + " from the vent)";
                }
            }
            if (ray.cliff != null) {
                cliffs++;
                cliff = ray.cliff;
            }
            final double foot = ray.reach[0];
            footMin = Math.min(footMin, foot);
            footMax = Math.max(footMax, foot);
            beyond.add(foot - ray.reach[ray.peak - y0]);
        }
        check("the flank never rises more than one block in three", worstRun >= 3.0 && cliffs == 0,
                "the shortest tread between two steps up along " + RAYS + " radial lines: " + worst
                        + (cliffs > 0 ? "; " + cliffs + " lines climb more than one block at once, e.g. " + cliff : "; no step is more than one block"));
        double sum = 0, lo = Double.MAX_VALUE, hi = 0;
        for (final double b : beyond) {
            sum += b;
            lo = Math.min(lo, b);
            hi = Math.max(hi, b);
        }
        final double mean = sum / beyond.size();
        check("the foot lies about 4 x 14 beyond the rim, wider than volcanoRadius",
                lo >= BEYOND * 0.8 && hi <= BEYOND * 1.2 && footMin > CONFIG_FOOT,
                "beyond the rim " + String.format("%.1f", lo) + " to " + String.format("%.1f", hi) + " (mean "
                        + String.format("%.1f", mean) + "; " + Math.round(BEYOND * 0.8) + "-" + Math.round(BEYOND * 1.2)
                        + " wanted); the foot " + String.format("%.1f", footMin) + " to " + String.format("%.1f", footMax)
                        + " from the vent, volcanoRadius " + CONFIG_FOOT);
        measuredFoot = 0;
        for (int b = 0; b < 72; b++) measuredFoot = Math.max(measuredFoot, ray(level, b * Math.PI / 36).reach[0]);
        // the caldera: rock round a small glowing heart
        int glow = 0, glowOut = 0, rock = 0, other = 0;
        String odd = "";
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz > 36) continue;
                final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, VX + dx, VZ + dz) - 1;
                final BlockState s = level.getBlockState(new BlockPos(VX + dx, y, VZ + dz));
                if (s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK)) {
                    glow++;
                    if (dx * dx + dz * dz > 4) glowOut++;
                } else if (y == lakeY && (s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE))) {
                    rock++;
                } else {
                    other++;
                    odd = s.getBlock() + " at y=" + y;
                }
            }
        }
        check("the cooled caldera is black rock round a small glowing heart", glow >= 1 && glow <= 12 && glowOut == 0 && other == 0,
                glow + " glowing blocks on top (lava or magma; " + glowOut + " of them more than 2 from the middle), " + rock
                        + " of basalt or blackstone at the old lake's surface y=" + lakeY + ", " + other + " anything else"
                        + (other > 0 ? " (e.g. " + odd + ")" : ""));
        // where the flows ran
        int basalt = 0, black = 0, still = 0;
        String left = "";
        for (final BlockPos p : flowCells) {
            final BlockState s = level.getBlockState(p);
            if (s.is(Blocks.BASALT)) basalt++;
            else if (s.is(Blocks.BLACKSTONE)) black++;
            else {
                still++;
                left = s.getBlock() + " at " + p.getX() + " " + p.getY() + " " + p.getZ();
            }
        }
        check("where the flows ran is basalt now", !flowCells.isEmpty() && still == 0 && basalt * 2 >= flowCells.size(),
                flowCells.size() + " blocks the flows lay in: " + basalt + " basalt, " + black + " blackstone, " + still + " anything else"
                        + (still > 0 ? " (e.g. " + left + ")" : ""));
        // and no lava at all on the flanks or inside them, but for the heart
        int molten = 0, flowing = 0;
        String where = "";
        final int edge = (int) Math.ceil(measuredFoot) + 2;
        for (int dx = -edge; dx <= edge; dx++) {
            for (int dz = -edge; dz <= edge; dz++) {
                if (dx * dx + dz * dz > edge * edge) continue;
                for (int y = y0 - 1; y <= rimY; y++) {
                    if (y == lakeY && dx * dx + dz * dz <= 2) continue;          // the heart
                    final BlockState s = level.getBlockState(new BlockPos(VX + dx, y, VZ + dz));
                    if (!s.is(Blocks.LAVA)) continue;
                    molten++;
                    if (!s.getFluidState().isSource()) flowing++;
                    where = (VX + dx) + " " + y + " " + (VZ + dz);
                }
            }
        }
        check("no lava is left on the flanks", molten == 0,
                molten + " blocks of lava (" + flowing + " flowing) within " + edge + " of the vent outside the heart"
                        + (molten > 0 ? ", e.g. at " + where : ""));
    }

    /** One radial line, from the vent out past the foot: how far out each height is reached, and the first cliff. */
    private final class Ray {
        /** reach[h] = the furthest column of the shield, out from the vent, whose top is at y0 + h or above. */
        final double[] reach = new double[COURSES + 2];
        int peak;
        String cliff;
    }

    /**
     * Walks a line from the vent outwards, column by column, until three columns in a row are the
     * plain grass again. For each height it keeps the furthest column that reaches it: going up the
     * flank from the foot, those are where the steps are, and the run between two of them is the
     * tread. A step up of more than one block between neighbouring columns is a cliff.
     */
    private Ray ray(final ServerLevel level, final double a) {
        final Ray ray = new Ray();
        ray.peak = y0;
        long last = Long.MIN_VALUE;
        int prevTop = Integer.MIN_VALUE, plain = 0;
        for (double t = 0; t <= LOOK && plain < 3; t += 0.05) {
            final int x = (int) Math.floor(VX + 0.5 + Math.cos(a) * t), z = (int) Math.floor(VZ + 0.5 + Math.sin(a) * t);
            final long key = BlockPos.asLong(x, 0, z);
            if (key == last) continue;
            last = key;
            final int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (top < y0) {
                plain++;
            } else {
                plain = 0;
                final double d = dist(x, z, VX, VZ);
                for (int h = y0; h <= Math.min(top, y0 + COURSES); h++) ray.reach[h - y0] = Math.max(ray.reach[h - y0], d);
                ray.peak = Math.max(ray.peak, Math.min(top, y0 + COURSES));
            }
            if (prevTop != Integer.MIN_VALUE && Math.abs(top - prevTop) > 1 && ray.cliff == null) {
                ray.cliff = "from y=" + prevTop + " to y=" + top + " at " + x + " " + z;
            }
            prevTop = top;
        }
        return ray;
    }

    /** The save and load of the finished shield, and a tag from before shapes. */
    private void saves(final ServerLevel level) {
        final HolderLookup.Provider registries = level.registryAccess();
        final Volcano live = Volcano.get(level);
        final CompoundTag tag = live.save(new CompoundTag(), registries);
        final Volcano back = Volcano.loadFor(tag, registries);
        final Volcano.Vent vent = new Volcano.Vent(VX, y0, VZ, footBound, true);
        check("a save and load keeps the shape and the shield's vent", back.shape() == Volcano.Shape.SHIELD
                        && "shield".equals(tag.getString("Shape")) && back.vents().equals(live.vents()) && live.vents().contains(vent),
                "saved Shape=" + tag.getString("Shape") + ", loaded " + back.shape() + "; vents " + live.vents() + ", loaded " + back.vents());
        final CompoundTag beta7 = tag.copy();
        beta7.remove("Shape");
        final CompoundTag ventTag = beta7.getList("Vents", 10).getCompound(0);
        ventTag.remove("Shield");
        final Volcano old = Volcano.loadFor(beta7, registries);
        check("a tag without the shape key loads as a cone", old.shape() == Volcano.Shape.CONE && !old.vents().get(0).shield(),
                "loaded " + old.shape() + ", its vent " + old.vents().get(0));
    }

    // ---- the site choice -------------------------------------------------------------------

    /** A few more of the PICKS questions to site(), for a cone and for a shield. True once all are asked. */
    private boolean ask(final ServerLevel level) {
        final FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        final Volcano v = Volcano.get(level);
        for (int n = 0; n < PICKS_PER_TICK && asked < PICKS; n++, asked++) {
            cones.add(v.siteFor(level, stand(fake, asked), RandomSource.create(7000L + asked)), CONFIG_FOOT * 1.2 + 1);
            shields.add(v.siteFor(level, stand(fake, asked), RandomSource.create(9000L + asked), Volcano.Shape.SHIELD), footBound + 4);
        }
        return asked >= PICKS;
    }

    /**
     * The world spawn moved under a fake player far from the shield, and a few of the shield's site
     * questions asked for it: a shield opens 129-199 blocks from whoever it opens near, so a toe kept
     * only meteorSafeRadius off the vent, as a cone's is, would come down on the spawn about one time
     * in five. True once all are asked.
     */
    private boolean askAtSpawn(final ServerLevel level) {
        final BlockPos spawn = new BlockPos(SPAWN_X, y0, SPAWN_Z);
        if (asked == 0) level.setDefaultSpawnPos(spawn, 0.0F);
        final FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setPos(SPAWN_X + 0.5, y0, SPAWN_Z + 0.5);
        final Volcano v = Volcano.get(level);
        for (int n = 0; n < PICKS_PER_TICK && asked < SPAWN_PICKS; n++, asked++) {
            final BlockPos pick = v.siteFor(level, fake, RandomSource.create(11000L + asked), Volcano.Shape.SHIELD);
            if (pick == null) {
                atSpawn.nulls++;
                continue;
            }
            atSpawn.picks++;
            atSpawn.closest = Math.min(atSpawn.closest, dist(pick.getX(), pick.getZ(), SPAWN_X, SPAWN_Z));
        }
        return asked >= SPAWN_PICKS;
    }

    /** What the spawn questions answered: how many sites, how many nulls, and the nearest vent to the spawn. */
    private static final class SpawnTally {
        int picks, nulls;
        double closest = Double.MAX_VALUE;
    }

    /** The fake player for one question: 60-120 blocks from the shield's vent, on its own bearing. */
    private FakePlayer stand(final FakePlayer fake, final int i) {
        final java.util.Random r = new java.util.Random(31L * i + 5);
        final double a = r.nextDouble() * Math.PI * 2;
        final double d = 60 + r.nextDouble() * 60;
        fake.setPos(VX + Math.cos(a) * d, y0, VZ + Math.sin(a) * d);
        return fake;
    }

    /** The answers site() gave: a new vent nearer than the shield's measured foot plus the new foot's own reach overlaps it. */
    private final class Tally {
        final String what;
        int picks, nulls, overlaps;
        double closest = Double.MAX_VALUE, need;

        Tally(final String what) {
            this.what = what;
        }

        void add(final BlockPos pick, final double newReach) {
            need = measuredFoot + newReach;
            if (pick == null) {
                nulls++;
                return;
            }
            picks++;
            final double d = dist(pick.getX(), pick.getZ(), VX, VZ);
            closest = Math.min(closest, d);
            if (d < need) overlaps++;
        }

        String detail() {
            return what + ", asked " + PICKS + ": picks " + picks + ", nulls " + nulls + ", feet on the shield " + overlaps
                    + " (closest vent " + (picks == 0 ? "-" : Math.round(closest)) + " from the shield's; overlap below "
                    + Math.round(need) + " = its measured foot " + Math.round(measuredFoot) + " + the new foot's reach)";
        }
    }

    // ---- plumbing --------------------------------------------------------------------------

    /** Per phase: the slowest volcano tick, the sum of them and how many, in nanoseconds. */
    private final java.util.Map<String, long[]> costs = new java.util.HashMap<>();
    private String lastPhase = "";

    /**
     * Files the cost of the volcano's work in the server tick that has just run (once a second, and
     * every tick while a course is laid) under the phase it ran in; the tick that crowns the shield
     * is filed apart as "crown".
     */
    private void timing(final ServerLevel level) {
        final long ns = Volcano.slowestTickNanos();
        final String now = Volcano.get(level).phaseName();
        if (ns > 0) {
            final String phase = lastPhase.equals("RISING") && now.equals("SETTLING") ? "crown"
                    : lastPhase.equals("SETTLING") && now.equals("COOLING") ? "SETTLING" : lastPhase.equals("COOLING") ? "COOLING" : now;
            final long[] t = costs.computeIfAbsent(phase, k -> new long[3]);
            t[0] = Math.max(t[0], ns);
            t[1] += ns;
            t[2]++;
            Volcano.resetSlowest();
        }
        lastPhase = now;
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

    /** True once the volcano is idle again and ten more seconds have passed. Still busy after five minutes fails the run. */
    private boolean finished(final ServerLevel level) {
        if (Volcano.busy(level)) {
            if (tick - stageTick > 20 * 300) throw new IllegalStateException("the shield is still cooling after five minutes");
            return false;
        }
        if (idleAt == 0) {
            idleAt = tick;
            LOG.info(TAG + "the shield has cooled {} s after its crown", (tick - risenAt) / 20);
        }
        return tick - idleAt >= 200;
    }

    /** The highest block within LOOK of the vent. */
    private static BlockPos highest(final ServerLevel level) {
        BlockPos best = new BlockPos(VX, level.getMinBuildHeight(), VZ);
        for (int x = VX - LOOK; x <= VX + LOOK; x++) {
            for (int z = VZ - LOOK; z <= VZ + LOOK; z++) {
                if (dist(x, z, VX, VZ) > LOOK) continue;
                final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (y > best.getY()) best = new BlockPos(x, y, z);
            }
        }
        return best;
    }

    /** Which of 72 five-degree bearings a column off the vent lies on. */
    private static int bin(final int dx, final int dz) {
        final double deg = Math.toDegrees(Math.atan2(dz, dx));
        return ((int) Math.floor((deg + 360) / 5)) % 72;
    }

    /**
     * The runs of occupied bearings round the compass, as [first, last] bins counted on from a gap,
     * so last may be past 71 where a run wraps through north. Two runs with no more than one empty
     * bin between them are one flow: a flow wanders a little.
     */
    private static List<int[]> groups(final boolean[] bins) {
        final List<int[]> out = new ArrayList<>();
        // start counting inside a gap of two empty bins, so no flow is cut in half where the count begins
        int start = -1;
        for (int i = 0; i < 72; i++) {
            if (!bins[i] && !bins[(i + 71) % 72]) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            out.add(new int[]{0, 71});                      // no gap that wide anywhere: one ring of lava
            return out;
        }
        int[] cur = null;
        int gap = 0;
        for (int k = 0; k < 72; k++) {
            if (bins[(start + k) % 72]) {
                if (cur != null && gap <= 1) {
                    cur[1] = start + k;
                } else {
                    cur = new int[]{start + k, start + k};
                    out.add(cur);
                }
                gap = 0;
            } else {
                gap++;
            }
        }
        return out;
    }

    private static boolean inGroup(final int b, final int[] g) {
        for (int i = g[0]; i <= g[1]; i++) {
            if (i % 72 == b) return true;
        }
        return false;
    }

    /**
     * Runs a command through the server's own dispatcher as an operator (permission 4) standing at
     * {@code at}, and collects what it says back. Returns the command's result.
     */
    private static int command(final ServerLevel level, final String line, final Vec3 at, final List<String> said)
            throws CommandSyntaxException {
        final CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(final Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        final CommandSourceStack source = new CommandSourceStack(sink, at, Vec2.ZERO, level, 4, "shieldvolcano",
                Component.literal("shieldvolcano"), level.getServer(), null);
        return level.getServer().getCommands().getDispatcher().execute(line, source);
    }

    private static double dist(final double x, final double z, final double x2, final double z2) {
        return Math.sqrt((x - x2) * (x - x2) + (z - z2) * (z - z2));
    }
}
