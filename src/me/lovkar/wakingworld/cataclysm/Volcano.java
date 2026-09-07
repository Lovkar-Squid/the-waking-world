package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Rising Mountain: a volcano that is built while you watch it.
 *
 * <p>A vent opens in open ground a hundred blocks or so from someone, and then, once every few
 * seconds for several minutes, the mountain gains a course. Each pulse lays one ring of rock at the
 * current height - basalt, tuff and blackstone with magma through it, gilded blackstone down in the
 * old rock - throws a lava bomb or two out over the country, and shakes the ground. The cone tapers
 * as it climbs, so it ends as a real mountain with a crater; the crater fills with lava, three
 * cooled flows run down the outside, and then it is quiet.</p>
 *
 * <p>Nothing here flows: the only lava placed is the pool inside the crater, and the "flows" down
 * the flanks are magma and obsidian that have already set. A volcano changes the map; it does not
 * chase anybody's house down a hill.</p>
 */
public final class Volcano extends SavedData {
    public static final String NAME = "wakingworld_volcano";
    private static final Factory<Volcano> FACTORY = new Factory<>(Volcano::new, Volcano::load, null);

    private enum Phase { IDLE, WARNING, RISING, SETTLING }

    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownUntilDay;
    private int cx, cz, baseY;       // the vent
    private int course;              // how many rings are already laid
    private int courses;             // how many there will be
    private int baseR;               // the radius of the foot
    private int nextPulse;
    /**
     * How long the whole rise should take, in seconds; 0 = whatever the config says. The camera sets
     * it: a mountain that takes the configured minutes to grow is right in a world and far too slow
     * inside a shot.
     */
    private int riseSeconds;

    private Volcano() {
    }

    public static Volcano get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** True while a mountain is going up - the other cataclysms wait their turn. */
    public static boolean busy(ServerLevel level) {
        return get(level).phase != Phase.IDLE;
    }

    // ---- the tick --------------------------------------------------------------------------

    public static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        // The switch stops the world from opening new vents; it does not freeze one that is already
        // going up, or the config's own promise that "the command still works" would be false - a
        // forced cone would stand half-built for ever.
        Volcano v = get(level);
        if (!WakingConfig.volcanoes() && v.phase == Phase.IDLE) return;
        v.tick(level);
    }

    private void tick(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        RandomSource rnd = level.random;

        switch (phase) {
            case IDLE -> {
                if (players.isEmpty()) return;                   // nobody to open one near
                if (Cataclysms.busy(level)) return;              // one cataclysm at a time
        // nothing new starts while the camera is rolling: a world-driven cataclysm on top of a
        // scene is a ruined take, and there is no way to tell from the footage what happened
        if (me.lovkar.wakingworld.story.Cinematics.running()) return;

                int day = (int) (level.getDayTime() / 24000L);
                if (day < cooldownUntilDay) return;
                long t = level.getDayTime() % 24000L;
                if (t < 500 || t > 1500) return;                 // it starts in the morning: worth seeing
                if (rnd.nextDouble() > WakingConfig.volcanoChance()) {
                    cooldownUntilDay = day + 1;
                    setDirty();
                    return;
                }
                begin(level, players.get(rnd.nextInt(players.size())), rnd);
            }
            case WARNING -> {
                phaseTicks -= 20;
                smoke(level);
                if (phaseTicks % 60 == 0) {
                    level.playSound(null, cx, baseY, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 3.0F, 0.35F);
                    WakingWorld.hooks.shakeAt(new Vec3(cx, baseY, cz), 1.2F, 160);
                }
                if (phaseTicks <= 0) {
                    phase = Phase.RISING;
                    nextPulse = 0;
                    setDirty();
                }
            }
            case RISING -> {
                nextPulse -= 20;
                smoke(level);
                if (nextPulse <= 0) {
                    pulse(level, rnd);
                    // this runs once every 20 ticks and takes 20 off, so anything that is not a whole
                    // number of seconds silently rounds up to one: ask for the seconds outright
                    int rise = riseSeconds > 0 ? riseSeconds : WakingConfig.volcanoMinutes() * 60;
                    int perCourse = Math.max(1, Math.round(rise / (float) Math.max(1, courses)));
                    nextPulse = perCourse * 20;
                    setDirty();
                }
                if (course >= courses) {
                    phase = Phase.SETTLING;
                    phaseTicks = 10 * 20;
                    crown(level, rnd);
                    setDirty();
                }
            }
            case SETTLING -> {
                phaseTicks -= 20;
                smoke(level);
                if (phaseTicks <= 0) end(level);
            }
        }
    }

    // ---- building it -----------------------------------------------------------------------

    // NOTE: only the IDLE roll needs a player. Once the ground has opened the mountain finishes on
    // its own - leaving a half-built cone standing there because everyone logged off would be worse.
    private void begin(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        BlockPos site = site(level, near, rnd);
        if (site == null) {                                      // nowhere sensible: try again tomorrow
            cooldownUntilDay = (int) (level.getDayTime() / 24000L) + 1;
            setDirty();
            return;
        }
        cx = site.getX();
        cz = site.getZ();
        baseY = site.getY();
        baseR = WakingConfig.volcanoRadius();
        courses = Math.max(6, WakingConfig.volcanoHeight());
        course = 0;
        riseSeconds = 0;                // a volcano the world raised keeps the world's pace
        phase = Phase.WARNING;
        phaseTicks = 40 * 20;
        cooldownUntilDay = (int) (level.getDayTime() / 24000L) + WakingConfig.daysBetweenVolcanoes();
        setDirty();
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.volcano.warning").withStyle(ChatFormatting.GOLD));
        }
        WakingWorld.LOGGER.info("cataclysm: a volcano opens at {} {} {} ({} courses, foot {})", cx, baseY, cz, courses, baseR);
    }

    /** One course of the cone: a ring of rock at the current height, and something thrown out of it. */
    private void pulse(ServerLevel level, RandomSource rnd) {
        int h = course;
        double climbed = (double) h / courses;
        double outer = outerAt(climbed);
        double vent = ventAt(climbed);
        int y = baseY + h;
        int r = (int) Math.ceil(outer) + 1;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = outer + (rnd.nextDouble() - 0.5) * 1.6;   // a rough, uneven rim
                if (d > edge) continue;
                BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                if (d < vent) {                                   // the throat stays open
                    if (!level.getBlockState(at).isAir()) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                    continue;
                }
                BlockState state = level.getBlockState(at);
                if (!state.isAir() && state.getFluidState().isEmpty() && h > 0 && rnd.nextDouble() < 0.35) continue;
                level.setBlock(at, wall(rnd, climbed), 2);
            }
        }

        // the first course also beds the mountain into the ground beneath it
        if (h == 0) foot(level, rnd, r);

        level.playSound(null, cx, y, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 4.0F, 0.4F + rnd.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx + 0.5, y + 1.5, cz + 0.5, 60, outer * 0.4, 2.0, outer * 0.4, 0.05);
        level.sendParticles(ParticleTypes.LAVA, cx + 0.5, y + 1.0, cz + 0.5, 24, vent, 0.5, vent, 0.0);
        WakingWorld.hooks.shakeAt(new Vec3(cx, y, cz), 2.2F, 200);

        // lava bombs: the same falling mass as a star, but small, and it carries nothing
        int bombs = rnd.nextInt(3) == 0 ? 2 : 1;
        for (int i = 0; i < bombs; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 30 + rnd.nextDouble() * 70;
            double bx = cx + Math.cos(angle) * dist;
            double bz = cz + Math.sin(angle) * dist;
            BlockPos ground = Cataclysms.surface(level, bx, bz);
            if (!Cataclysms.away(level, ground)) continue;
            Cataclysms.fall(level, new Vec3(bx, ground.getY(), bz), 1, false);
        }

        course++;
    }

    /**
     * The cone, as two curves. It tapers to a bit under half its foot rather than to a point, and the
     * throat widens as it climbs - so the thing ends with a crater you could stand in, not a chimney.
     */
    private double outerAt(double climbed) {
        return baseR * (1.0 - 0.55 * climbed);
    }

    private double ventAt(double climbed) {
        return Math.max(1.5, outerAt(climbed) * (0.20 + 0.35 * climbed));
    }

    /** Where the ground meets the new mountain: a skirt of rock so it does not stand on a lip of air. */
    private void foot(ServerLevel level, RandomSource rnd, int r) {
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 3) continue;
                int top = Cataclysms.surface(level, cx + dx, cz + dz).getY();
                for (int y = Math.min(top, baseY) - 1; y <= baseY; y++) {
                    BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                    if (level.getBlockState(at).isAir() || !level.getFluidState(at).isEmpty()) {
                        level.setBlock(at, wall(rnd, 0.0), 2);
                    }
                }
                if (d > r && rnd.nextDouble() < 0.4) {           // scorched ground around the foot
                    BlockPos at = new BlockPos(cx + dx, baseY, cz + dz);
                    if (!level.getBlockState(at).isAir()) {
                        level.setBlock(at, rnd.nextBoolean() ? Blocks.TUFF.defaultBlockState() : Blocks.BASALT.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * The crater at the end: the throat is plugged, two courses of lava are poured into the bowl with
     * a rim of rock standing above them, and three flows that have already set run down the flanks.
     *
     * <p>The plug matters. Without it the pool drains straight down the open chimney and out at the
     * foot of the mountain, and a volcano that floods the valley is not a landmark, it is a grief.</p>
     */
    private void crown(ServerLevel level, RandomSource rnd) {
        int rimY = baseY + courses - 1;                          // the highest ring the pulses laid
        double vent = ventAt(1.0) + 0.5;
        int r = (int) Math.ceil(vent) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > vent) continue;
                level.setBlock(new BlockPos(cx + dx, rimY - 5, cz + dz), Blocks.BLACKSTONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, rimY - 4, cz + dz), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, rimY - 3, cz + dz), Blocks.LAVA.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, rimY - 2, cz + dz), Blocks.LAVA.defaultBlockState(), 2);
            }
        }
        // three cooled flows down the flanks, following the same cone the pulses built
        for (int f = 0; f < 3; f++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            for (int h = courses - 1; h >= 0; h--) {
                double ring = outerAt((double) h / courses) - 0.4;
                double px = cx + 0.5 + Math.cos(angle) * ring;
                double pz = cz + 0.5 + Math.sin(angle) * ring;
                angle += (rnd.nextDouble() - 0.5) * 0.16;
                for (int w = -1; w <= 1; w++) {
                    BlockPos at = BlockPos.containing(px + w * Math.sin(angle), baseY + h, pz - w * Math.cos(angle));
                    if (level.getBlockState(at).isAir()) continue;
                    level.setBlock(at, rnd.nextDouble() < 0.55 ? Blocks.OBSIDIAN.defaultBlockState()
                            : Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                }
            }
        }
        level.playSound(null, cx, rimY, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 6.0F, 0.3F);
    }

    private void end(ServerLevel level) {
        phase = Phase.IDLE;
        phaseTicks = 0;
        course = 0;
        setDirty();
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.volcano.over").withStyle(ChatFormatting.GRAY));
        }
        WakingWorld.LOGGER.info("cataclysm: the mountain is finished at {} {} {}", cx, baseY + courses, cz);
    }

    /** Ash and smoke drifting over anyone near enough to be under it. */
    private void smoke(ServerLevel level) {
        int top = baseY + Math.max(1, course);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, cx + 0.5, top + 3.0, cz + 0.5, 12, 2.0, 1.0, 2.0, 0.02);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(cx, top, cz) > 260 * 260) continue;
            level.sendParticles(p, ParticleTypes.WHITE_ASH, false, p.getX(), p.getY() + 12, p.getZ(), 30, 16, 6, 16, 0.0);
        }
    }

    /**
     * What the wall is made of. Magma is kept to a twentieth: it is the thing the eye goes to, and a
     * cone that is a fifth magma reads as a pile of lava rather than as rock with fire still in it -
     * and every one of them burns whoever climbs.
     */
    private static BlockState wall(RandomSource rnd, double climbed) {
        double v = rnd.nextDouble();
        if (v < 0.05 + 0.05 * climbed) return Blocks.MAGMA_BLOCK.defaultBlockState();   // hotter near the top
        if (climbed < 0.34 && v < 0.13) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        if (v < 0.44) return Blocks.BASALT.defaultBlockState();
        if (v < 0.74) return Blocks.TUFF.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    /** Open, dry, level-ish ground a good way off - and never on top of anyone's home. */
    private BlockPos site(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 90 + rnd.nextDouble() * 70;
            int x = (int) (near.getX() + Math.cos(angle) * dist);
            int z = (int) (near.getZ() + Math.sin(angle) * dist);
            BlockPos top = Cataclysms.surface(level, x, z);
            if (top.getY() < level.getSeaLevel() + 2) continue;               // not in the sea
            if (!level.getFluidState(top.below()).is(Fluids.EMPTY)) continue;
            if (!Cataclysms.away(level, top)) continue;
            // reasonably flat, or the cone hangs off a cliff
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int dx = -8; dx <= 8; dx += 4) {
                for (int dz = -8; dz <= 8; dz += 4) {
                    int y = Cataclysms.surface(level, x + dx, z + dz).getY();
                    lo = Math.min(lo, y);
                    hi = Math.max(hi, y);
                }
            }
            if (hi - lo > 10) continue;
            return new BlockPos(x, lo, z);
        }
        return null;
    }

    // ---- saved with the world --------------------------------------------------------------

    private static Volcano load(CompoundTag tag, HolderLookup.Provider registries) {
        Volcano v = new Volcano();
        v.phase = Phase.values()[Math.min(tag.getInt("Phase"), Phase.values().length - 1)];
        v.phaseTicks = tag.getInt("Ticks");
        v.cooldownUntilDay = tag.getInt("Cooldown");
        v.cx = tag.getInt("X");
        v.cz = tag.getInt("Z");
        v.baseY = tag.getInt("Y");
        v.course = tag.getInt("Course");
        v.courses = tag.getInt("Courses");
        v.baseR = tag.getInt("Foot");
        v.nextPulse = tag.getInt("Next");
        v.riseSeconds = tag.getInt("RiseSeconds");
        return v;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Phase", phase.ordinal());
        tag.putInt("Ticks", phaseTicks);
        tag.putInt("Cooldown", cooldownUntilDay);
        tag.putInt("X", cx);
        tag.putInt("Z", cz);
        tag.putInt("Y", baseY);
        tag.putInt("Course", course);
        tag.putInt("Courses", courses);
        tag.putInt("Foot", baseR);
        tag.putInt("Next", nextPulse);
        tag.putInt("RiseSeconds", riseSeconds);
        return tag;
    }

    /** For the debug command: open one here, now. */
    public static void force(ServerLevel level, BlockPos at, int height, int foot) {
        force(level, at, height, foot, 0);
    }

    /**
     * The same, at a pace of the caller's choosing: {@code riseSeconds} is how long the whole cone
     * should take to come up (0 = the config's minutes). The camera uses it.
     */
    public static void force(ServerLevel level, BlockPos at, int height, int foot, int riseSeconds) {
        Volcano v = get(level);
        v.riseSeconds = riseSeconds;
        v.cx = at.getX();
        v.cz = at.getZ();
        v.baseY = at.getY();
        v.baseR = foot > 0 ? foot : WakingConfig.volcanoRadius();
        v.courses = height > 0 ? height : Math.max(6, WakingConfig.volcanoHeight());
        v.course = 0;
        v.phase = Phase.WARNING;
        v.phaseTicks = 5 * 20;
        v.cooldownUntilDay = (int) (level.getDayTime() / 24000L) + WakingConfig.daysBetweenVolcanoes();
        v.setDirty();
    }
}
