package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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

    private enum Phase { IDLE, WARNING, RISING, SETTLING, COOLING }

    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownUntilDay;
    private int cx, cz, baseY;       // the vent
    private int course;              // how many rings are already laid
    private int courses;             // how many there will be
    private int baseR;               // the radius of the foot
    private int barren;              // courses in a row that laid no block (a colony under the cone)
    private int nextPulse;
    /** How far up the flow has set. Everything below this is rock again; the crater is left glowing. */
    private int cooledTo;
    /** Which eighth of the cone this pulse looks at; the sweep goes round and round. */
    private transient int coolSlice;
    private transient int cooledBlocks;
    private static final int COOL_SLICES = 8;
    private transient int voice;      // ticks until the rumble is started again
    /** The bearing the lava runs down, in radians. Chosen once so the flow does not wander. */
    private float spill;
    /**
     * How long the whole rise should take, in seconds; 0 = whatever the config says. The camera sets
     * it: a mountain that takes the configured minutes to grow is right in a world and far too slow
     * inside a shot.
     */
    private int riseSeconds;
    /** The scar this mountain is writing, so an hourglass can put the country back under it. */
    private java.util.UUID scar;

    /** One vent this world has opened: where it stood and how wide its foot was. */
    public record Vent(int x, int y, int z, int foot) {
    }

    /**
     * Every vent this world has opened since 0.3.0-beta.7, oldest first. The save used to hold only
     * the one mountain going up, so the next vent knew nothing about the last: the summit of a wide
     * cone is flat enough for the site test, and a vent opened there counted its height from the
     * summit - two eruptions stood nearly twice volcanoHeight above the plain, and every one after
     * that added the whole height again. site() keeps a new foot clear of every foot in here.
     *
     * <p>Bounded at {@link #VENTS_KEPT}: the save stays small however long the world runs, and a
     * cone that has dropped off the end is still found by its rock, as the ones from before the list
     * are (see {@link #oldCone}).</p>
     */
    private final List<Vent> vents = new ArrayList<>();
    private static final int VENTS_KEPT = 64;
    /** Open ground left between the toes of two cones, so their flanks never merge into one hill. */
    private static final int VENT_GAP = 8;

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
        Scars.writing(level, scar);
        try {
            body(level);
        } finally {
            Scars.close();
        }
    }

    private void body(ServerLevel level) {
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
                if (rnd.nextDouble() > WakingConfig.volcanoChance() * Unrest.factor(level)) {
                    cooldownUntilDay = day + 1;
                    setDirty();
                    return;
                }
                begin(level, players.get(rnd.nextInt(players.size())), rnd);
            }
            case WARNING -> {
                phaseTicks -= 20;
                smoke(level);
                Omen.tick(level, new Vec3(cx, baseY, cz), Omen.Kind.VOLCANO, phaseTicks / 20);
                // the skirt is thousands of columns; laid in one go it is a two-second freeze in the
                // middle of the shot, so it goes down in strips, one a second, under the smoke
                int slice = FOOT_SLICES - 1 - Math.max(0, Math.min(FOOT_SLICES - 1, phaseTicks / 20));
                if (slice >= 0 && slice < FOOT_SLICES) {
                    foot(level, rnd, (int) Math.ceil(outerAt(0.0)) + 1, slice, FOOT_SLICES);
                }
                if (phaseTicks % 60 == 0) {
                    level.playSound(null, cx, baseY, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 10.0F, 0.35F);
                    WakingWorld.hooks.shakeAt(new Vec3(cx, baseY, cz), 1.2F, 160);
                }
                if (phaseTicks <= 0) {
                    phase = Phase.RISING;
                    nextPulse = 0;
                    // whatever the strips did not reach - a short warning, or a reload part way through
                    for (int i = 0; i < FOOT_SLICES; i++) foot(level, rnd, (int) Math.ceil(outerAt(0.0)) + 1, i, FOOT_SLICES);
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
                if (phaseTicks <= 0) {
                    phase = Phase.COOLING;
                    cooledTo = baseY - 1;
                    phaseTicks = Math.max(60, WakingConfig.volcanoCoolMinutes() * 60) * 20;
                    for (ServerPlayer p : level.players()) {
                        p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.volcano.over").withStyle(ChatFormatting.GRAY));
                    }
                    Survived.near(level, Omen.Kind.VOLCANO, new net.minecraft.world.phys.Vec3(cx, baseY, cz));
                    WakingWorld.LOGGER.info("cataclysm: the mountain is finished at {} {} {}; it is cooling", cx, baseY + courses, cz);
                    // several minutes of cooling follow this, and it used to say nothing at all: the
                    // silence between "it has stopped moving" and the hourglass working read as the
                    // hourglass being broken. Two lines, some minutes apart, are the whole difference.
                    for (ServerPlayer p : level.players()) {
                        p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.volcano.cooling").withStyle(ChatFormatting.GOLD));
                    }
                    setDirty();
                }
            }
            // The flow crusts over from the toe up while the vent is still bright, which is what a
            // real one does and what tells a player at a distance that it is over. The crater pool
            // is never reached: a volcano with no lava in it at all is only a hill.
            case COOLING -> {
                phaseTicks -= 20;
                smoke(level);
                int rim = baseY + courses;
                int pool = rim - 4;                              // crown() lays the pool at rim-3 and rim-2
                int total = Math.max(60, WakingConfig.volcanoCoolMinutes() * 60) * 20;
                float done = 1.0F - Math.max(0, phaseTicks) / (float) total;
                // The whole flank is in play from the first minute; what changes is how readily a
                // block sets. Creeping a ceiling up from the foot was the obvious design and the
                // wrong one - for most of the cooling it swept solid rock, because the lava is up
                // near the crater and only reaches the foot later, by flowing.
                double chance = 0.05 + 0.55 * done;
                cooledTo = pool;
                int setBlocks = Aftermath.cool(level, cx, cz, outerAt(0.0) + 2, baseY - 1,
                        pool, coolSlice, COOL_SLICES, chance, level.random);
                coolSlice = (coolSlice + 1) % COOL_SLICES;
                cooledBlocks += setBlocks;
                if (setBlocks > 0) {
                    Cataclysms.puff(level, net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                            cx, cooledTo + 1.5, cz, 20, outerAt(0.0) * 0.5, 1.0, outerAt(0.0) * 0.5, 0.02);
                }
                setDirty();
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
        spill = rnd.nextFloat() * (float) (Math.PI * 2);
        riseSeconds = 0;                // a volcano the world raised keeps the world's pace
        barren = 0;
        remember();
        phase = Phase.WARNING;
        phaseTicks = 22 * 20;
        cooldownUntilDay = (int) (level.getDayTime() / 24000L) + WakingConfig.daysBetweenVolcanoes();
        setDirty();
        scar = Scars.begin(level, new BlockPos(cx, baseY, cz), "a volcano");
        Omen.begin(level, new Vec3(cx, baseY, cz), Omen.Kind.VOLCANO, phaseTicks / 20);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.volcano.warning").withStyle(ChatFormatting.GOLD));
        }
        // the chronicle is what the kings read: a mountain coming up in their country is news
        me.lovkar.wakingworld.story.Chronicle.record(level, "cataclysm", "volcano", new BlockPos(cx, baseY, cz), null);
        WakingWorld.LOGGER.info("cataclysm: a volcano opens at {} {} {} ({} courses, foot {})", cx, baseY, cz, courses, baseR);
    }

    /** One course of the cone: a ring of rock at the current height, and something thrown out of it. */
    private void pulse(ServerLevel level, RandomSource rnd) {
        int laid = 0;
        int h = course;
        double climbed = (double) h / courses;
        double outer = outerAt(climbed);
        double vent = ventAt(climbed);
        int y = baseY + h;
        int r = (int) Math.ceil(outer) + 1;

        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = outerAt(climbed, Math.atan2(dz, dx)) + (rnd.nextDouble() - 0.5) * 1.6;
                if (d > edge) continue;
                BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                if (d < vent) {
                    // The throat used to be cleared to AIR at every course, which left the thing an
                    // empty black chimney for the whole of its rise - you could stand on the rim of
                    // a volcano that was actively building itself and look down a dark hole. It is
                    // full of lava now, one course at a time, so the shaft glows all the way up and
                    // the eye has something to look at while the mountain grows round it. It cannot
                    // spill: this course's ring is laid at the same height and encloses it, and the
                    // course below is lava already, so there is nowhere for it to go.
                    if (Scars.set(level, at, Blocks.LAVA.defaultBlockState())) laid++;
                    continue;
                }
                BlockState state = level.getBlockState(at);
                if (!state.isAir() && state.getFluidState().isEmpty() && h > 0 && rnd.nextDouble() < 0.35) continue;
                if (Scars.set(level, at, wall(rnd, climbed))) laid++;
            }
        }

        // A course that laid not one block is a mountain being refused - it is standing on a
        // colony, or on ground the config will not let it touch. It gets no smoke, no bang and no
        // shaking: an eruption nobody can see the result of is just noise over somebody's town.
        // Three of them in a row and the whole thing is called off.
        if (laid == 0) {
            barren++;
            course++;
            if (barren >= 3) {
                WakingWorld.LOGGER.info("cataclysm: the mountain at {} {} {} can raise nothing (a colony's land) - it is called off", cx, baseY, cz);
                end(level);
            }
            return;
        }
        barren = 0;

        // and from a third of the way up, the flank is open and running
        if (climbed > 0.30) channel(level, rnd);

        // 16 blocks of range per unit of volume, and a sound is never louder than 1.0 where you are
        // standing: a big number here is DISTANCE, not noise. Two hundred and forty blocks means the
        // country hears the mountain going up, which is the point of it.
        level.playSound(null, cx, y, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 15.0F, 0.28F + rnd.nextFloat() * 0.12F);
        Cataclysms.puff(level, ParticleTypes.LARGE_SMOKE, cx + 0.5, y + 1.5, cz + 0.5, 60, outer * 0.4, 2.0, outer * 0.4, 0.05);
        Cataclysms.puff(level, ParticleTypes.LAVA, cx + 0.5, y + 1.0, cz + 0.5, 24, vent, 0.5, vent, 0.0);
        WakingWorld.hooks.shakeAt(new Vec3(cx, y, cz), 2.2F, 200);

        bombs(level, rnd, y, vent);

        course++;
    }

    /**
     * What the vent throws.
     *
     * <p>These used to be stars: spawned a hundred and thirty blocks up and forty to one side of
     * wherever they were going to land, exactly like a meteor. On camera they came down out of an
     * empty sky beside the mountain with no connection to it at all - which is precisely what he
     * said when he watched it back.</p>
     *
     * <p>They are fired out of the throat now. A block of the mountain's own rock is launched from
     * the crater with enough of an arc to clear the flanks and land forty to seventy blocks out, so
     * the eye follows it the whole way: out of the vent, over the rim, down the sky, into the
     * ground. It lands as a block, which is also one more mark the thing leaves behind.</p>
     */
    private void bombs(ServerLevel level, RandomSource rnd, int y, double vent) {
        // ABOVE the rim, not inside the throat. Fired from within the bowl, a bomb was already
        // against the crater wall on its first tick: it lost all of its horizontal speed to that
        // collision and dropped straight back in, which is exactly what he watched happen. Nothing
        // about the arc was wrong - it never got to fly it.
        // The rim is not a circle: the cone is ridged on purpose, so one bearing can stand several
        // blocks above another, and a launch cleared for the average was still inside the wall on
        // the high side. Take the highest point of a ring of samples at three radii, and go over it.
        int rim = y;
        for (double f : new double[]{1.0, 1.6, 2.4}) {
            double r = Math.max(vent + 1.0, vent * f);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6.0;
                int rx = (int) Math.round(cx + Math.cos(a) * r);
                int rz = (int) Math.round(cz + Math.sin(a) * r);
                rim = Math.max(rim, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, rx, rz));
            }
        }
        int launch = rim + 4;

        int count = rnd.nextInt(3) == 0 ? 3 : 2;
        for (int i = 0; i < count; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double bx = cx + 0.5 + Math.cos(angle) * 0.6;
            double bz = cz + 0.5 + Math.sin(angle) * 0.6;
            // aim for open country beyond the foot, and work the speed back from that. A falling
            // block keeps 98% of its speed a tick, which comes to about 43 blocks of ground for
            // every 1.0 of horizontal it leaves with.
            double reach = baseR + 14 + rnd.nextDouble() * 46;
            double speed = reach / 43.0;

            if (rnd.nextInt(3) == 0 && WakingConfig.lavaBombs()) {
                // one throw in three is molten: it arcs out under its own gravity and leaves lava
                MeteorEntity bomb = WakingWorld.METEOR.get().create(level);
                if (bomb != null) {
                    bomb.hurl(new Vec3(bx, launch + 1.0, bz),
                            new Vec3(Math.cos(angle) * speed * 1.6, 1.5 + rnd.nextDouble() * 0.5, Math.sin(angle) * speed * 1.6));
                    level.addFreshEntity(bomb);
                }
            } else {
                BlockState thrown = rnd.nextDouble() < 0.45 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : (rnd.nextBoolean() ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                BlockPos from = new BlockPos((int) Math.floor(bx), launch, (int) Math.floor(bz));
                net.minecraft.world.entity.item.FallingBlockEntity fb =
                        net.minecraft.world.entity.item.FallingBlockEntity.fall(level, from, thrown);
                fb.setHurtsEntities(2.0F, 14);
                fb.setDeltaMovement(Math.cos(angle) * speed, 1.6 + rnd.nextDouble() * 0.6, Math.sin(angle) * speed);
            }
            // and the muzzle flash, so the launch itself is seen and not only the arrival
            Cataclysms.puff(level, ParticleTypes.LAVA, bx, launch + 0.5, bz, 12, 0.6, 0.4, 0.6, 0.0);
            Cataclysms.puff(level, ParticleTypes.FLAME, bx, launch + 1.0, bz, 14, 0.5, 0.5, 0.5, 0.22);
            Cataclysms.puff(level, ParticleTypes.LARGE_SMOKE, bx, launch + 1.5, bz, 10, 0.8, 0.6, 0.8, 0.10);
        }
    }

    /**
     * The cone, as two curves. It tapers to a bit under half its foot rather than to a point, and the
     * throat widens as it climbs - so the thing ends with a crater you could stand in, not a chimney.
     */
    private double outerAt(double climbed) {
        return baseR * (1.0 - 0.62 * climbed);
    }

    /**
     * The rim at one bearing. A cone whose every ring is a true circle comes out as a stack of discs
     * - which is exactly what the first one looked like on camera, a cake rather than a mountain. Two
     * slow waves round the compass, seeded off the spill so a volcano does not look like the last
     * one, give it ridges and gullies that run all the way down; and the rim is pulled in hard on the
     * spill bearing so the lava has a notch to come over instead of a wall to climb.
     */
    private double outerAt(double climbed, double angle) {
        double r = outerAt(climbed);
        double ridges = 1.0 + 0.13 * Math.sin(angle * 3 + spill * 2.0) + 0.07 * Math.cos(angle * 5 - spill);
        double notch = 1.0 - 0.30 * Math.exp(-sq(angleTo(angle, spill)) / 0.06) * climbed;
        return r * ridges * notch;
    }

    private static double sq(double x) {
        return x * x;
    }

    /** The shortest way round from one bearing to another, in radians. */
    private static double angleTo(double a, double b) {
        double d = (a - b) % (Math.PI * 2);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return d;
    }

    private double ventAt(double climbed) {
        return Math.max(1.5, outerAt(climbed) * (0.20 + 0.35 * climbed));
    }

    /** How many ticks of the warning the skirt is spread over. */
    private static final int FOOT_SLICES = 5;

    /**
     * Where the ground meets the new mountain: a skirt of rock so it does not stand on a lip of air.
     * One strip of it, so a wide foot can be laid a piece at a time instead of all inside one tick.
     */
    private void foot(ServerLevel level, RandomSource rnd, int r, int slice, int slices) {
        int span = 2 * (r + 3) + 1;
        int from = -r - 3 + (int) ((long) span * slice / slices);
        int to = -r - 3 + (int) ((long) span * (slice + 1) / slices);
        for (int dx = from; dx < to; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 3) continue;
                // the heightmap, not Cataclysms.surface: the pulse has just written blocks into every
                // one of these chunks, so they are loaded, and a getChunk per column is thousands of
                // them in one tick once the foot is wide
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz);
                for (int y = Math.min(top, baseY) - 1; y <= baseY; y++) {
                    BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                    if (level.getBlockState(at).isAir() || !level.getFluidState(at).isEmpty()) {
                        Scars.set(level, at, wall(rnd, 0.0));
                    }
                }
                if (d > r && rnd.nextDouble() < 0.4) {           // scorched ground around the foot
                    BlockPos at = new BlockPos(cx + dx, baseY, cz + dz);
                    if (!level.getBlockState(at).isAir()) {
                        Scars.set(level, at, rnd.nextBoolean() ? Blocks.TUFF.defaultBlockState() : Blocks.BASALT.defaultBlockState());
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
                Scars.set(level, new BlockPos(cx + dx, rimY - 5, cz + dz), Blocks.BLACKSTONE.defaultBlockState());
                Scars.set(level, new BlockPos(cx + dx, rimY - 4, cz + dz), Blocks.MAGMA_BLOCK.defaultBlockState());
                Scars.set(level, new BlockPos(cx + dx, rimY - 3, cz + dz), Blocks.LAVA.defaultBlockState());
                Scars.set(level, new BlockPos(cx + dx, rimY - 2, cz + dz), Blocks.LAVA.defaultBlockState());
            }
        }
        // the live channel is cut one last time, now that the rim is at its full height
        channel(level, rnd);
        // and two older flows that have already set, off to either side of it
        for (int f = 0; f < 2; f++) {
            double angle = spill + (f == 0 ? 2.1 : -2.4);
            for (int h = courses - 1; h >= 0; h--) {
                double ring = outerAt((double) h / courses, angle) - 0.4;
                double px = cx + 0.5 + Math.cos(angle) * ring;
                double pz = cz + 0.5 + Math.sin(angle) * ring;
                angle += (rnd.nextDouble() - 0.5) * 0.16;
                for (int w = -1; w <= 1; w++) {
                    BlockPos at = BlockPos.containing(px + w * Math.sin(angle), baseY + h, pz - w * Math.cos(angle));
                    if (level.getBlockState(at).isAir()) continue;
                    Scars.set(level, at, rnd.nextDouble() < 0.55 ? Blocks.OBSIDIAN.defaultBlockState()
                            : Blocks.MAGMA_BLOCK.defaultBlockState());
                }
            }
        }
        level.playSound(null, cx, rimY, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 18.0F, 0.3F);
        // and the ash, carried away on the opposite bearing to the flow so the two are not the same
        // side of the mountain. This is what somebody finds a week later and follows back to here.
        Aftermath.ashfall(level, new BlockPos(cx, baseY, cz), spill + Math.PI,
                Math.max(48, baseR * 2.6), rnd);
    }

    private void end(ServerLevel level) {
        Scars.done(level, scar);
        scar = null;
        phase = Phase.IDLE;
        phaseTicks = 0;
        course = 0;
        cooledTo = 0;
        setDirty();
        WakingWorld.LOGGER.info("cataclysm: the flow at {} {} has set ({} blocks turned to rock; the crater keeps its pool)", cx, cz, cooledBlocks);
        cooledBlocks = 0;
    }

    /** Ash and smoke drifting over anyone near enough to be under it. */
    /**
     * The plume. The first version put a dozen particles over the vent and was invisible from any
     * distance worth filming from; this stacks them up the column so the thing has a shape against
     * the sky, widening as it climbs the way real ash does, with embers near the throat and ash
     * falling over anyone close enough to be under it.
     */
    private void smoke(ServerLevel level) {
        int top = baseY + Math.max(1, course);
        double vent = Math.max(2.0, ventAt(courses == 0 ? 0 : (double) course / courses));
        // the column: eight stations up the sky, each wider and slower than the one below it
        for (int i = 0; i < 11; i++) {
            double up = 3 + i * 6.5;
            double spread = vent * (0.8 + i * 0.62);
            Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, cx + 0.5, top + up, cz + 0.5,
                    14 + i * 3, spread, 2.0, spread, 0.010 + i * 0.004);
            Cataclysms.puff(level, ParticleTypes.LARGE_SMOKE, cx + 0.5, top + up, cz + 0.5,
                    i < 5 ? 16 : 8, spread * 0.8, 1.6, spread * 0.8, 0.03);
        }
        // what is still burning, right at the throat
        Cataclysms.puff(level, ParticleTypes.LAVA, cx + 0.5, top + 1.5, cz + 0.5, 6, vent * 0.6, 0.6, vent * 0.6, 0.0);
        // its own voice, under everything, restarted just before the loop runs out
        if (voice-- <= 0) {
            voice = 100;                       // the loop is 5.4 s; 5 s keeps it seamless
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(cx, top, cz) > 320 * 320) continue;
                level.playSound(null, cx, top, cz, me.lovkar.wakingworld.WakingSounds.VOLCANO_RUMBLE.get(),
                        SoundSource.WEATHER, 8.0F, 0.9F + level.random.nextFloat() * 0.15F);
                break;                          // it is one sound in the world, not one per listener
            }
        }
        Cataclysms.puff(level, ParticleTypes.FLAME, cx + 0.5, top + 2.5, cz + 0.5, 10, vent * 0.5, 1.2, vent * 0.5, 0.06);
        for (ServerPlayer p : level.players()) {
            double away = p.distanceToSqr(cx, top, cz);
            if (away > 300 * 300) continue;
            level.sendParticles(p, ParticleTypes.WHITE_ASH, true, p.getX(), p.getY() + 12, p.getZ(),
                    away < 140 * 140 ? 60 : 24, 18, 8, 18, 0.0);
        }
    }

    /**
     * The lava that runs down one flank while the mountain is still going up.
     *
     * <p>It is cut as a groove rather than poured: a one-block channel down the spill bearing, walls
     * of blackstone either side and lava standing in it. Real flowing lava down an open slope floods
     * the valley, sets fire to everything in it and costs a fluid tick per block; standing lava in a
     * walled groove goes nowhere, costs nothing after it is laid, and from any distance reads as
     * exactly the thing it is meant to be - a line of fire down a black cone.</p>
     */
    private void channel(ServerLevel level, RandomSource rnd) {
        double angle = spill;
        int from = Math.max(0, course - 1);
        for (int h = from; h >= 0; h--) {
            double climbed = (double) h / courses;
            double ring = outerAt(climbed, angle) - 0.6;
            angle += (rnd.nextDouble() - 0.5) * 0.05;              // it wanders a little, not much
            double px = cx + 0.5 + Math.cos(angle) * ring;
            double pz = cz + 0.5 + Math.sin(angle) * ring;
            int y = baseY + h;
            double nx = Math.sin(angle), nz = -Math.cos(angle);
            // the groove itself - two wide, so it reads as a river and not as a seam, and open to
            // the sky above it or the rock closes over and the whole thing is invisible from outside
            for (int c = 0; c <= 1; c++) {
                BlockPos at = BlockPos.containing(px + nx * c * 0.9, y, pz + nz * c * 0.9);
                Scars.set(level, at, Blocks.LAVA.defaultBlockState());
                Scars.set(level, at.above(), Blocks.AIR.defaultBlockState());
                Scars.set(level, at.above(2), Blocks.AIR.defaultBlockState());
            }
            BlockPos at = BlockPos.containing(px, y, pz);
            // and its banks, so it cannot go anywhere
            for (int w = -1; w <= 2; w += 3) {
                BlockPos bank = BlockPos.containing(px + nx * w, y, pz + nz * w);
                if (level.getBlockState(bank).getFluidState().isEmpty()) {
                    Scars.set(level, bank, rnd.nextDouble() < 0.25 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState());
                }
                if (level.getBlockState(bank.above()).getFluidState().isEmpty()
                        && !level.getBlockState(bank.above()).isAir() && rnd.nextDouble() < 0.5) {
                    Scars.set(level, bank.above(), Blocks.BLACKSTONE.defaultBlockState());
                }
            }
            for (int c = 0; c <= 1; c++) {
                BlockPos under = BlockPos.containing(px + nx * c * 0.9, y - 1, pz + nz * c * 0.9);
                if (level.getBlockState(under).isAir() || !level.getFluidState(under).isEmpty()) {
                    Scars.set(level, under, Blocks.BLACKSTONE.defaultBlockState());
                }
            }
        }
        // where it reaches the ground it spreads into a small burnt fan
        double ex = cx + 0.5 + Math.cos(angle) * (outerAt(0, angle) + 2);
        double ez = cz + 0.5 + Math.sin(angle) * (outerAt(0, angle) + 2);
        for (int i = 0; i < 14; i++) {
            int fx = (int) (ex + (rnd.nextDouble() - 0.5) * 7);
            int fz = (int) (ez + (rnd.nextDouble() - 0.5) * 7);
            BlockPos g = new BlockPos(fx, level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz), fz);
            if (g.getY() > baseY + 3 || g.getY() <= level.getMinBuildHeight() + 1) continue;
            Scars.set(level, g.below(), rnd.nextDouble() < 0.3 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState());
        }
    }

    /**
     * What the wall is made of. Magma is kept to a twentieth: it is the thing the eye goes to, and a
     * cone that is a fifth magma reads as a pile of lava rather than as rock with fire still in it -
     * and every one of them burns whoever climbs.
     */
    private static BlockState wall(RandomSource rnd, double climbed) {
        double v = rnd.nextDouble();
        if (v < 0.018 + 0.022 * climbed) return Blocks.MAGMA_BLOCK.defaultBlockState();  // hotter near the top
        if (climbed < 0.34 && v < 0.13) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        if (v < 0.44) return Blocks.BASALT.defaultBlockState();
        if (v < 0.74) return Blocks.TUFF.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    /** {@link #site(ServerLevel, ServerPlayer, RandomSource, int)} at the sea level the world was made with. */
    private BlockPos site(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        return site(level, near, rnd, seaLevel(level));
    }

    /**
     * The sea level the world was generated with. {@code Level.getSeaLevel()} is no help: in 1.21.1
     * it answers 63 on every world, so on a superflat world, or one whose generator lays its sea and
     * its ground lower down, every spot looked like open water and no volcano ever opened there. The
     * generator knows its own water line - a superflat one says -63, under its whole surface.
     */
    private static int seaLevel(ServerLevel level) {
        return level.getChunkSource().getGenerator().getSeaLevel();
    }

    /** Dry land, or the cone comes up as an island and the shore is a wall of rock. */
    private static boolean dry(ServerLevel level, BlockPos top, int sea) {
        return top.getY() >= sea + 2 && level.getFluidState(top.below()).is(Fluids.EMPTY);
    }

    /**
     * Open, dry, level-ish ground a good way off - never on top of anyone's home, and never on or
     * against an older mountain.
     */
    private BlockPos site(ServerLevel level, ServerPlayer near, RandomSource rnd, int sea) {
        int foot = WakingConfig.volcanoRadius();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 90 + rnd.nextDouble() * 70;
            int x = (int) (near.getX() + Math.cos(angle) * dist);
            int z = (int) (near.getZ() + Math.sin(angle) * dist);
            // the cones this world remembers come first: it is arithmetic, and needs no chunk
            if (!clearOfVents(x, z, foot)) continue;
            BlockPos top = Cataclysms.surface(level, x, z);
            if (!dry(level, top, sea)) continue;                              // not in the sea
            if (!Cataclysms.away(level, top)) continue;
            // a mountain's whole foot must be the world's own ground, not a colony's
            if (me.lovkar.wakingworld.compat.Colonies.keepOffLoading(level, top, WakingConfig.volcanoRadius() + 6)) continue;
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
            // and the ones it does not: last, because it reads the ground under the whole new foot
            if (oldCone(level, x, z, foot)) continue;
            return new BlockPos(x, lo, z);
        }
        return null;
    }

    /** How many spots the command looks at before it says there is no room. */
    private static final int COMMAND_TRIES = 48;

    /**
     * Where {@code /wakingworld volcano} opens when it is not told where: dry ground 60 to 100 blocks
     * from whoever asked, kept off the remembered vents and the old volcanic rock exactly as the
     * world's own choice is. It used to look only for dry ground, so a second call from the same
     * spot could put its vent on the summit of the first cone and stack one mountain on another -
     * the very thing beta.7 stopped the world itself from doing. It is nearer in than {@link #site},
     * and it does not keep off players or the world spawn: whoever runs it wants to watch it.
     *
     * @param foot the foot the vent will have; 0 for the config's
     * @return the ground to open on, or null when every spot looked at is wet or taken by a cone
     */
    public static BlockPos commandSite(ServerLevel level, Vec3 from, int foot, RandomSource rnd) {
        Volcano v = get(level);
        int f = foot > 0 ? foot : WakingConfig.volcanoRadius();
        int sea = seaLevel(level);
        for (int tries = 0; tries < COMMAND_TRIES; tries++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 60 + rnd.nextDouble() * 40;
            int x = net.minecraft.util.Mth.floor(from.x + Math.cos(angle) * dist);
            int z = net.minecraft.util.Mth.floor(from.z + Math.sin(angle) * dist);
            // in the same order as site(): the list is arithmetic, the rock test reads the most ground
            if (!v.clearOfVents(x, z, f)) continue;
            BlockPos top = Cataclysms.surface(level, x, z);
            if (!dry(level, top, sea)) continue;
            if (oldCone(level, x, z, f)) continue;
            return top;
        }
        return null;
    }

    /** Writes the vent that has just opened into the list, dropping the oldest past VENTS_KEPT. */
    private void remember() {
        vents.add(new Vent(cx, baseY, cz, baseR));
        while (vents.size() > VENTS_KEPT) vents.remove(0);
        setDirty();
    }

    /**
     * How far from its vent a cone of this foot lays rock. The ridges in {@link #outerAt(double,
     * double)} swell the bottom course by up to a fifth on some bearings and its edge is ragged by
     * under a block; on a small foot the skirt laid in the warning, four blocks past the foot, is
     * wider still.
     */
    private static int reach(int foot) {
        return Math.max((int) Math.ceil(foot * 1.2) + 1, foot + 4);
    }

    /** True when a vent here, with this foot, keeps its rock and VENT_GAP of open ground off every remembered cone. */
    private boolean clearOfVents(int x, int z, int foot) {
        for (Vent v : vents) {
            long dx = x - v.x(), dz = z - v.z();
            long need = reach(v.foot()) + reach(foot) + VENT_GAP;
            if (dx * dx + dz * dz < need * need) return false;
        }
        return true;
    }

    /**
     * Whether an older cone stands under or beside this vent: one from before this world remembered
     * its vents, or one that has dropped off the end of the list. Hardly anything else in the
     * Overworld is floored with this rock - basalt deltas are a Nether biome, and a ruined portal or
     * a lava pool is a few blocks across - so ground that is the volcano's own stone is a volcano.
     *
     * <p>It reads the 25 columns of the flatness test under the vent, a ring of 12 half-way out and
     * a ring of 24 where the new cone's rock will end ({@link #reach}), so it finds a cone under the
     * vent and one the new foot would lap onto. Three of the 61 are enough to refuse. A cone floors
     * its ground solid, so one beside the new foot shows on several bearings of the outer ring at
     * once; what its bombs threw lies a block or a small set pool here and there, and a foot that
     * takes in much of that is reaching back onto the cone anyway. It stops at the third: every
     * sample may load a chunk, and a spot already refused needs no more of them.</p>
     */
    private static boolean oldCone(ServerLevel level, int x, int z, int foot) {
        int rock = 0;
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                if (coneGround(level, x + dx, z + dz) && ++rock >= 3) return true;
            }
        }
        int edge = reach(foot);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            if (coneGround(level, x + Math.cos(a) * edge * 0.5, z + Math.sin(a) * edge * 0.5) && ++rock >= 3) return true;
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12.0;
            if (coneGround(level, x + Math.cos(a) * edge, z + Math.sin(a) * edge) && ++rock >= 3) return true;
        }
        return false;
    }

    /**
     * The top of this column is what a volcano leaves: its rock, the obsidian and magma of a set
     * flow, or the crater's lava. Snow is looked under, or a cone in a cold country would hide.
     */
    private static boolean coneGround(ServerLevel level, double x, double z) {
        BlockPos ground = Cataclysms.surface(level, x, z).below();
        BlockState s = level.getBlockState(ground);
        if (s.is(BlockTags.SNOW)) s = level.getBlockState(ground.below());
        return s.is(Blocks.BASALT) || s.is(Blocks.TUFF) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.GILDED_BLACKSTONE)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.LAVA);
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
        if (tag.hasUUID("Scar")) v.scar = tag.getUUID("Scar");
        v.cooledTo = tag.getInt("CooledTo");
        v.riseSeconds = tag.getInt("RiseSeconds");
        v.spill = tag.getFloat("Spill");
        // a save from before 0.3.0-beta.7 has no list: it loads empty, and the rock test covers its cones
        ListTag list = tag.getList("Vents", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            v.vents.add(new Vent(t.getInt("X"), t.getInt("Y"), t.getInt("Z"), t.getInt("Foot")));
        }
        while (v.vents.size() > VENTS_KEPT) v.vents.remove(0);   // trims a list saved under a larger bound
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
        if (scar != null) tag.putUUID("Scar", scar);
        tag.putInt("CooledTo", cooledTo);
        tag.putInt("RiseSeconds", riseSeconds);
        tag.putFloat("Spill", spill);
        ListTag list = new ListTag();
        for (Vent vent : vents) {
            CompoundTag t = new CompoundTag();
            t.putInt("X", vent.x());
            t.putInt("Y", vent.y());
            t.putInt("Z", vent.z());
            t.putInt("Foot", vent.foot());
            list.add(t);
        }
        tag.put("Vents", list);
        return tag;
    }

    /** For the debug command: open one here, now. */
    public static boolean force(ServerLevel level, BlockPos at, int height, int foot) {
        return force(level, at, height, foot, 0);
    }

    /**
     * The same, at a pace of the caller's choosing: {@code riseSeconds} is how long the whole cone
     * should take to come up (0 = the config's minutes). The camera uses it.
     */
    public static boolean force(ServerLevel level, BlockPos at, int height, int foot, int riseSeconds) {
        if (me.lovkar.wakingworld.compat.Colonies.keepOffLoading(level, at, 8)) {
            WakingWorld.LOGGER.info("cataclysm: a volcano was called at {} {} {} - that is a colony's land, nothing opens",
                    at.getX(), at.getY(), at.getZ());
            return false;
        }
        Volcano v = get(level);
        v.riseSeconds = riseSeconds;
        v.spill = level.random.nextFloat() * (float) (Math.PI * 2);
        v.cx = at.getX();
        v.cz = at.getZ();
        v.baseY = at.getY();
        v.baseR = foot > 0 ? foot : WakingConfig.volcanoRadius();
        v.courses = height > 0 ? height : Math.max(6, WakingConfig.volcanoHeight());
        v.course = 0;
        // a forced vent still opens exactly where it was asked, but the world's own next one keeps off it
        v.remember();
        v.phase = Phase.WARNING;
        v.phaseTicks = 5 * 20;
        v.scar = Scars.begin(level, at, "a volcano");   // a forced one is written down like any other
        v.cooldownUntilDay = (int) (level.getDayTime() / 24000L) + WakingConfig.daysBetweenVolcanoes();
        v.barren = 0;
        v.setDirty();
        return true;
    }

    // ---- for the headless probe (tools/volcanoheight) --------------------------------------

    /**
     * Where the world would open its next vent near {@code near}, without opening anything, asked
     * exactly as the morning roll asks it. Only the headless probes call this: nobody can join the
     * test server, so that roll never comes round, and they ask it directly with a fake player.
     */
    public BlockPos siteFor(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        return site(level, near, rnd);
    }

    /**
     * The same at a sea level of the caller's choosing. tools/volcanoheight passed the flat world's
     * own here before site() read the generator's itself, and tools/volcanosite passes 63 - what
     * {@code Level.getSeaLevel()} answers - to show that the old way found nowhere on a flat world.
     */
    public BlockPos siteFor(ServerLevel level, ServerPlayer near, RandomSource rnd, int sea) {
        return site(level, near, rnd, sea);
    }

    /** The remembered vents, oldest first, as a copy. For the headless probe. */
    public List<Vent> vents() {
        return List.copyOf(vents);
    }

    /** {@link #oldCone}, for the headless probe: it asks it on, beside and well away from a cone it raised. */
    public static boolean oldConeFor(ServerLevel level, int x, int z, int foot) {
        return oldCone(level, x, z, foot);
    }

    /**
     * Reads a saved tag into a Volcano of its own, outside the world's data storage. For the headless
     * probe: it checks that the vent list survives a save and that a tag without one loads empty, and
     * asks site() of a copy with no list to show the rock test working alone.
     */
    public static Volcano loadFor(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag, registries);
    }
}
