package me.lovkar.wwvolcanosite;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.cataclysm.Volcano;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.List;

/**
 * Headless check for where volcanoes open (0.3.0-beta.8). On the flat rig, whose grass is at y=-1
 * and whose generator puts the sea at -63:
 * <ul>
 *   <li>the world's own site choice, asked the way the morning roll asks it, finds sites for a fake
 *       player standing on the flat - and asked with 63, what {@code Level.getSeaLevel()} answers,
 *       it finds none, which is what every flat world got before;</li>
 *   <li>a cone is forced at a known spot and left to finish; then {@code /wakingworld volcano} with
 *       no position is run RUNS times through the server's own command dispatcher, at permission 4,
 *       from 70 blocks off that cone, and no vent it opens may have a foot on the cone. The same again
 *       with the vent list wiped, so only the rock test stands in the way; each vent opened is taken
 *       back before the next run, so nothing rises and the list does not grow;</li>
 *   <li>a named position still opens exactly there, even on the cone;</li>
 *   <li>a caller ringed by remembered vents gets the failure message and nothing opens.</li>
 * </ul>
 * Prints CHECK lines and RESULT, then stops the server.
 */
@Mod("wwvolcanosite")
public class VolcanoSite {
    private static final Logger LOG = LoggerFactory.getLogger("wwvolcanosite");
    private static final String TAG = "[volcanosite] ";
    /** Where the fake player stands for the site choice: far from the spawn, which site() keeps off. */
    private static final int SX = -600, SZ = 600;
    /** The cone, and the caller of the command 70 blocks east of it. */
    private static final int VX = 600, VZ = -400, CALLER_OUT = 70;
    /** A caller far from everything, ringed by remembered vents. */
    private static final int BX = -600, BZ = -600;
    /** Must match rigconfig: the command's vents take the config's foot. */
    private static final int HEIGHT = 12, FOOT = 24, RISE = 12;
    private static final int PICKS = 100, PICKS_PER_TICK = 5;
    private static final int RUNS = 50, RUNS_PER_TICK = 2;
    private static final int LOOK = 60;

    private int tick;
    private int stage;
    private int stageTick;
    private int idleAt;
    private int y0;
    private int failures;
    private int asked;
    private int picksNow, picksOld;
    /** How far the cone's rock goes, measured once it has cooled. */
    private double reach;
    private final Runs listed = new Runs();
    private final Runs unlisted = new Runs();

    public VolcanoSite(final IEventBus modBus) {
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
                    // the cone and the ground the command looks at round it, kept loaded so the rise runs
                    for (int cx = (VX - 128) >> 4; cx <= (VX + CALLER_OUT + 128) >> 4; cx++) {
                        for (int cz = (VZ - 128) >> 4; cz <= (VZ + 128) >> 4; cz++) {
                            level.setChunkForced(cx, cz, true);
                        }
                    }
                    y0 = Cataclysms.surface(level, VX, VZ).getY();
                    LOG.info(TAG + "surface y={} at {} {}; Level.getSeaLevel() {}, the generator's sea level {}", y0, VX, VZ,
                            level.getSeaLevel(), level.getChunkSource().getGenerator().getSeaLevel());
                    check("the server config reached the mod", WakingConfig.volcanoHeight() == HEIGHT && WakingConfig.volcanoRadius() == FOOT,
                            "volcanoHeight " + WakingConfig.volcanoHeight() + ", volcanoRadius " + WakingConfig.volcanoRadius());
                    next();
                }
                case 1 -> {
                    // the world's own choice, asked as the morning roll asks it, and then with 63
                    final FakePlayer fake = FakePlayerFactory.getMinecraft(level);
                    final Volcano v = Volcano.get(level);
                    for (int n = 0; n < PICKS_PER_TICK && asked < PICKS; n++, asked++) {
                        fake.setPos(SX + 0.5, y0, SZ + 0.5);
                        if (v.siteFor(level, fake, RandomSource.create(9000L + asked)) != null) picksNow++;
                        if (v.siteFor(level, fake, RandomSource.create(9000L + asked), 63) != null) picksOld++;
                    }
                    if (asked < PICKS) return;
                    check("the world's site choice finds ground on a flat world", picksNow >= PICKS / 2,
                            picksNow + " of " + PICKS + " questions found a site; at least " + PICKS / 2 + " wanted");
                    check("asked with Level.getSeaLevel() it finds none, as before beta.8", picksOld == 0,
                            picksOld + " of " + PICKS + " questions found a site with sea level 63");
                    check("the volcano opens", Volcano.force(level, new BlockPos(VX, y0, VZ), HEIGHT, FOOT, RISE),
                            "forced at " + VX + " " + y0 + " " + VZ + ", height " + HEIGHT + ", foot " + FOOT + ", rise " + RISE + " s");
                    next();
                }
                case 2 -> {
                    if (!finished(level)) return;
                    reach = reach(level);
                    LOG.info(TAG + "the cone's rock reaches {} blocks from its vent", Math.round(reach));
                    asked = 0;
                    next();
                }
                case 3 -> {
                    if (!run(level, listed)) return;
                    // it must also still open somewhere: a command that refuses everything keeps every
                    // foot off the cone too
                    check("the command keeps a new foot off the cone", listed.overlaps == 0 && listed.opened >= RUNS / 2,
                            listed.detail() + "; at least " + RUNS / 2 + " opened wanted");
                    // the list wiped, so only the rock test knows the cone is there
                    final HolderLookup.Provider registries = level.registryAccess();
                    final CompoundTag saved = Volcano.get(level).save(new CompoundTag(), registries);
                    saved.remove("Vents");
                    level.getDataStorage().set(Volcano.NAME, Volcano.loadFor(saved, registries));
                    asked = 0;
                    next();
                }
                case 4 -> {
                    if (!run(level, unlisted)) return;
                    check("with the list wiped, the command opens no vent on the cone",
                            Volcano.get(level).vents().isEmpty() && unlisted.onCone == 0 && unlisted.opened >= RUNS / 2,
                            "list " + Volcano.get(level).vents().size() + " vents; " + unlisted.detail() + "; at least " + RUNS / 2 + " opened wanted");
                    next();
                }
                case 5 -> {
                    // a named spot, on the cone's own flank: it opens there all the same
                    final BlockPos at = Cataclysms.surface(level, VX + 10, VZ);
                    final CompoundTag before = Volcano.get(level).save(new CompoundTag(), level.registryAccess());
                    final List<String> said = new ArrayList<>();
                    final int r = command(level, "wakingworld volcano " + at.getX() + " " + at.getY() + " " + at.getZ(),
                            new Vec3(VX + CALLER_OUT + 0.5, y0, VZ + 0.5), said);
                    final List<Volcano.Vent> vents = Volcano.get(level).vents();
                    final Volcano.Vent last = vents.isEmpty() ? null : vents.get(vents.size() - 1);
                    final boolean busy = Volcano.busy(level);
                    restore(level, before);
                    check("a named position still opens exactly there", r == 1 && busy && last != null
                                    && last.x() == at.getX() && last.y() == at.getY() && last.z() == at.getZ(),
                            "asked " + at.getX() + " " + at.getY() + " " + at.getZ() + "; returned " + r + ", newest vent " + last
                                    + ", busy " + busy + ", said " + said);
                    next();
                }
                case 6 -> {
                    // a caller ringed by remembered vents of a wide foot: every spot 60-100 out is taken
                    final HolderLookup.Provider registries = level.registryAccess();
                    final CompoundTag tag = Volcano.get(level).save(new CompoundTag(), registries);
                    final ListTag ring = new ListTag();
                    for (int i = 0; i < 24; i++) {
                        final double a = i * Math.PI / 12;
                        final CompoundTag t = new CompoundTag();
                        t.putInt("X", (int) Math.round(BX + Math.cos(a) * 80));
                        t.putInt("Y", y0);
                        t.putInt("Z", (int) Math.round(BZ + Math.sin(a) * 80));
                        t.putInt("Foot", FOOT);
                        ring.add(t);
                    }
                    tag.put("Vents", ring);
                    level.getDataStorage().set(Volcano.NAME, Volcano.loadFor(tag, registries));
                    final List<Volcano.Vent> before = Volcano.get(level).vents();
                    final List<String> said = new ArrayList<>();
                    final int r = command(level, "wakingworld volcano", new Vec3(BX + 0.5, y0, BZ + 0.5), said);
                    final List<Volcano.Vent> after = Volcano.get(level).vents();
                    final boolean busy = Volcano.busy(level);
                    final boolean told = said.stream().anyMatch(s -> s.startsWith("No room for a vent near here"));
                    check("with no room the command opens nothing and says so", r == 0 && !busy && after.equals(before)
                                    && before.size() == 24 && told,
                            "returned " + r + ", busy " + busy + ", vents " + before.size() + " -> " + after.size() + ", said " + said);
                    stage = 50;
                }
                case 50 -> {
                    LOG.info(TAG + "RESULT volcanosite: {}", failures == 0 ? "PASS" : "FAIL");
                    stage = 99;
                    level.getServer().execute(() -> level.getServer().halt(false));
                }
                default -> {
                }
            }
        } catch (final Throwable t) {
            LOG.error(TAG + "EXCEPTION", t);
            LOG.info(TAG + "RESULT volcanosite: FAIL");
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
     * A few more of the RUNS of the bare command from beside the cone, each vent it opens noted and
     * then taken back - the saved data is put back as it was, in the same tick, before the warning
     * has laid a block. True once all of them have run.
     */
    private boolean run(final ServerLevel level, final Runs into) throws CommandSyntaxException {
        final Vec3 caller = new Vec3(VX + CALLER_OUT + 0.5, y0, VZ + 0.5);
        for (int n = 0; n < RUNS_PER_TICK && asked < RUNS; n++, asked++) {
            final CompoundTag before = Volcano.get(level).save(new CompoundTag(), level.registryAccess());
            final int had = Volcano.get(level).vents().size();
            final List<String> said = new ArrayList<>();
            final int r = command(level, "wakingworld volcano", caller, said);
            final List<Volcano.Vent> vents = Volcano.get(level).vents();
            final Volcano.Vent opened = r == 1 && vents.size() > had ? vents.get(vents.size() - 1) : null;
            if (r == 1 && opened == null) {
                // the list was full or wiped: the vent is in the saved data all the same
                final CompoundTag now = Volcano.get(level).save(new CompoundTag(), level.registryAccess());
                into.add(new Volcano.Vent(now.getInt("X"), now.getInt("Y"), now.getInt("Z"), now.getInt("Foot")));
            } else {
                into.add(opened);
            }
            if (r == 0) into.refusals.add(String.join(" / ", said));
            restore(level, before);
        }
        return asked >= RUNS;
    }

    /** Puts the volcano's saved data back to a copy taken earlier. */
    private static void restore(final ServerLevel level, final CompoundTag tag) {
        level.getDataStorage().set(Volcano.NAME, Volcano.loadFor(tag, level.registryAccess()));
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
        final CommandSourceStack source = new CommandSourceStack(sink, at, Vec2.ZERO, level, 4, "volcanosite",
                Component.literal("volcanosite"), level.getServer(), null);
        return level.getServer().getCommands().getDispatcher().execute(line, source);
    }

    /**
     * What the command opened. A cone of foot FOOT lays its bottom course out to 1.2 x the foot on its
     * ridges, so a new vent nearer the old one than the old cone's measured reach plus that has a foot
     * that overlaps the old cone.
     */
    private final class Runs {
        int opened, refused, overlaps, onCone;
        double closest = Double.MAX_VALUE;
        final List<String> refusals = new ArrayList<>();

        void add(final Volcano.Vent v) {
            if (v == null) {
                refused++;
                return;
            }
            opened++;
            final double d = dist(v.x(), v.z(), VX, VZ);
            closest = Math.min(closest, d);
            if (d < reach + FOOT * 1.2 + 1) overlaps++;
            if (d < reach) onCone++;
        }

        String detail() {
            return "ran " + RUNS + ": opened " + opened + ", refused " + refused + ", feet overlapping the cone " + overlaps
                    + " (closest vent " + (opened == 0 ? "-" : Math.round(closest)) + " from the cone's; overlap below "
                    + Math.round(reach + FOOT * 1.2 + 1) + "), vents on the cone itself " + onCone
                    + (refusals.isEmpty() ? "" : "; first refusal said: " + refusals.get(0));
        }
    }

    /**
     * How far out the cone's own rock goes on the ground course: along 72 bearings from inside the
     * flank outwards, the last block of the mountain before three open blocks in a row (the thrown
     * bombs lie further out, beyond a gap of grass).
     */
    private double reach(final ServerLevel level) {
        double best = 0;
        for (int b = 0; b < 72; b++) {
            final double a = b * Math.PI / 36;
            double last = 0;
            int open = 0;
            for (double d = 6; d <= LOOK && open < 6; d += 0.5) {
                final BlockPos at = BlockPos.containing(VX + 0.5 + Math.cos(a) * d, y0, VZ + 0.5 + Math.sin(a) * d);
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
