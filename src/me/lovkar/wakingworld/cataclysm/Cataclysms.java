package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import java.util.UUID;
import me.lovkar.wakingworld.particle.WakingParticles;
import me.lovkar.wakingworld.story.Chronicle;
import me.lovkar.wakingworld.story.Cinematics;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

public final class Cataclysms extends SavedData {
    public static final String NAME = "wakingworld_cataclysms";
    private static final Factory<Cataclysms> FACTORY = new Factory<>(Cataclysms::new, Cataclysms::load, null);

    /** Keeps a falling star's chunks ticking for 30s, then expires by itself. */
    private static final TicketType<ChunkPos> METEOR_TICKET =
            TicketType.create("wakingworld_meteor", Comparator.comparingLong(ChunkPos::toLong), 600);

    /** Nothing / the warning / the fall itself. */
    private enum Phase { IDLE, WARNING, FALLING }

    private Phase phase = Phase.IDLE;
    private int phaseTicks;          // ticks left in this phase
    private int meteorsLeft;
    private int cooldownUntilDay;    // the earliest day another shower may start
    private int nextDrop;            // ticks until the next star during a fall

    private Cataclysms() {
    }

    public static Cataclysms get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // ---- the tick --------------------------------------------------------------------------

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 20 != 0) return;              // once a second is plenty
        if (WakingConfig.meteorShowers()) get(level).tick(level);
        Volcano.onLevelTick(level);
        BloodMoon.onLevelTick(level);
        Weather.onLevelTick(level);
    }

    public static void ring(ServerLevel var0, int var1, float var2, double var3, double var5, double var7) {
        ring(var0, var1, var2, var3, var5, var7, 0.45);
    }

    public static void ring(ServerLevel var0, int var1, float var2, double var3, double var5, double var7, double var9) {
        puff(var0, WakingParticles.ring(var1, var2 / 3.0F), var3, var5, var7, 0, 0.0, var9, 0.0, 1.0);
    }

    public static void runes(ServerLevel var0, int var1, float var2, double var3, double var5, double var7, int var9, double var10, double var12, double var14) {
        puff(var0, WakingParticles.rune(var1, var2), var3, var5, var7, var9, var10, var12, var14, 0.02);
    }

    public static void embers(
        ServerLevel var0, int var1, float var2, double var3, double var5, double var7, int var9, double var10, double var12, double var14, double var16
    ) {
        puff(var0, WakingParticles.ember(var1, var2), var3, var5, var7, var9, var10, var12, var14, var16);
    }

    /**
     * Particles anybody can actually see.
     *
     * <p>{@code ServerLevel.sendParticles(type, x, y, z, ...)} only reaches players within
     * <b>32 blocks</b>. Everything a cataclysm draws - the volcano's plume, the tornado's column,
     * the dust off an earthquake, the ring a star throws out - is meant to be seen from much
     * further away than that, and a camera filming from sixty or a hundred blocks got none of it.
     * That is the whole reason the first two takes came back with no smoke and a dull tornado.</p>
     *
     * <p>The per-player overload with {@code force} set carries to 512 blocks. Everything in this
     * package goes through here.</p>
     */
    public static <T extends net.minecraft.core.particles.ParticleOptions> void puff(
            ServerLevel level, T type, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) > 400 * 400) continue;
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /**
     * Open ground near a player: dry, above the sea, level enough, and not under a wood.
     *
     * <p>{@link Earthquake#site} was one unfiltered sample - an angle, a distance and whatever was
     * there. Most of the time that is a field and it is fine; the rest of the time it is a lake, a
     * cliff or the middle of a jungle, and a tornado in a jungle is sixteen seconds of leaves. That
     * is exactly what cost the trailer its second shot.</p>
     *
     * <p>The canopy test is the interesting one. There is no way to ask the world generator whether
     * a column has a tree on it - trees are placed after the noise, so every generator heightmap
     * answers bare ground. On a <em>loaded</em> chunk, though, the difference between
     * {@code MOTION_BLOCKING} and {@code MOTION_BLOCKING_NO_LEAVES} is precisely the depth of the
     * canopy over that column. {@link #surface} loads the chunk anyway, so the five extra samples
     * are free and stay inside that same chunk.</p>
     *
     * @return the best spot found, or the last one looked at if nothing passed - a cataclysm that
     *         refuses to happen is worse than one in a slightly poor place
     */
    public static Vec3 openSite(ServerLevel level, Vec3 from, RandomSource rnd,
                                double minOut, double maxOut) {
        Vec3 fallback = null;
        Vec3 best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < 14; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = minOut + rnd.nextDouble() * (maxOut - minOut);
            double x = from.x + Math.cos(angle) * dist;
            double z = from.z + Math.sin(angle) * dist;
            BlockPos ground = surface(level, x, z);              // loads the chunk
            Vec3 here = new Vec3(x, ground.getY(), z);
            if (fallback == null) fallback = here;
            if (ground.getY() <= level.getSeaLevel() + 1) continue;                  // water, or a shore
            if (!level.getFluidState(ground.below()).isEmpty()) continue;            // a lake or a river

            // five columns inside the chunk we have just loaded: how uneven, and how wooded
            int lo = ground.getY(), hi = ground.getY(), canopy = 0;
            for (int k = 0; k < 5; k++) {
                int sx = (int) x + (k % 3 - 1) * 6, sz = (int) z + (k / 3 - 1) * 6;
                int bare = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                int over = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
                lo = Math.min(lo, bare);
                hi = Math.max(hi, bare);
                canopy = Math.max(canopy, over - bare);
            }
            if (canopy > 4) continue;                            // standing under a wood
            int spread = hi - lo;
            if (spread > 14) continue;                           // a cliff or a gorge
            if (spread < bestScore) {
                bestScore = spread;
                best = here;
            }
            if (spread <= 4) break;                              // good enough; stop paying for better
        }
        if (best == null) {
            WakingWorld.LOGGER.info("cataclysm: no open ground found near {} {} - taking what there is",
                    (int) from.x, (int) from.z);
        }
        return best != null ? best : (fallback != null ? fallback : Vec3.atBottomCenterOf(surface(level, from.x, from.z)));
    }

    /** True while the sky is falling - the other cataclysms wait their turn. */
    public static boolean busy(ServerLevel level) {
        return get(level).phase != Phase.IDLE;
    }

    private void tick(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        RandomSource rnd = level.random;
        // a shower with nobody left to see it is over: stars are aimed at players, so with none
        // there is nothing to aim at, and the phase would otherwise sit there for good
        if (players.isEmpty()) {
            if (phase != Phase.IDLE) end(level);
            return;
        }

        switch (phase) {
            case IDLE -> {
        // nothing new starts while the camera is rolling: a world-driven cataclysm on top of a
        // scene is a ruined take, and there is no way to tell from the footage what happened
        if (me.lovkar.wakingworld.story.Cinematics.running()) return;
                int day = (int) (level.getDayTime() / 24000L);
                if (day < cooldownUntilDay) return;
                // it starts at dusk, and only when somebody is awake to see it
                long t = level.getDayTime() % 24000L;
                if (t < 13000 || t > 14000) return;
                if (rnd.nextDouble() > WakingConfig.meteorChance() * Unrest.factor(level)) {
                    cooldownUntilDay = day + 1;                 // rolled and missed: try again tomorrow
                    setDirty();
                    return;
                }
                begin(level);
            }
            case WARNING -> {
                phaseTicks -= 20;
                if (phaseTicks % 100 == 0) {
                    for (ServerPlayer p : players) {
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6F, 0.35F);
                    }
                }
                if (phaseTicks <= 0) {
                    phase = Phase.FALLING;
                    phaseTicks = WakingConfig.showerLength() * 20;
                    nextDrop = 0;
                    setDirty();
                }
            }
            case FALLING -> {
                phaseTicks -= 20;
                nextDrop -= 20;
                if (nextDrop <= 0 && meteorsLeft > 0) {
                    drop(level, players.get(rnd.nextInt(players.size())), rnd);
                    meteorsLeft--;
                    nextDrop = 100 + rnd.nextInt(160);          // 5 - 13 s apart
                    setDirty();
                }
                if (phaseTicks <= 0 || meteorsLeft <= 0) end(level);
            }
        }
    }

    // ---- the shower ------------------------------------------------------------------------

    /** The scar the whole shower writes: one for the night, not one per stone. */
    private java.util.UUID scar;

    private void begin(ServerLevel level) {
        phase = Phase.WARNING;
        phaseTicks = 30 * 20;
        meteorsLeft = WakingConfig.meteorsPerShower();
        cooldownUntilDay = (int) (level.getDayTime() / 24000L) + WakingConfig.daysBetweenShowers();
        setDirty();
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.meteor.warning").withStyle(ChatFormatting.GOLD));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 0.3F);
        }
        for (ServerPlayer p : level.players()) {
            // one entry for the shower, not one per star: a king telling you about fourteen stones
            // in a row is a list, and a list is not news
            me.lovkar.wakingworld.story.Chronicle.record(level, "cataclysm", "meteor", p.blockPosition(), null);
            break;
        }
        for (ServerPlayer p : level.players()) {
            scar = Scars.begin(level, p.blockPosition(), "a meteor shower");
            break;
        }
        WakingWorld.LOGGER.info("cataclysm: a meteor shower begins ({} stars)", meteorsLeft);
    }

    /** The scar the stars are writing into, for as long as the shower lasts. */
    public static java.util.UUID scarOf(ServerLevel level) {
        return get(level).scar;
    }

    private void end(ServerLevel level) {
        Scars.done(level, scar);
        scar = null;
        boolean wasFalling = phase == Phase.FALLING;
        phase = Phase.IDLE;
        phaseTicks = 0;
        meteorsLeft = 0;
        setDirty();
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.meteor.over").withStyle(ChatFormatting.GRAY));
        }
        // only a shower that actually fell counts as lived through; one called off before the
        // first star is not something anybody survived
        if (wasFalling) Survived.everyone(level, Omen.Kind.METEOR);
    }

    /** One star, aimed at open ground near a player - never at their bed, never at the world spawn. */
    private void drop(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 40 + rnd.nextDouble() * 72;
            double x = near.getX() + Math.cos(angle) * dist;
            double z = near.getZ() + Math.sin(angle) * dist;
            BlockPos ground = surface(level, x, z);
            if (!away(level, ground)) continue;
            int size = rnd.nextInt(10) < 6 ? 1 : (rnd.nextInt(10) < 8 ? 2 : 3);
            fall(level, new Vec3(x, ground.getY(), z), size, true);
            return;
        }
    }

    /** Everything a star needs: spawned high, aimed down, on its way. */
    public static MeteorEntity fall(ServerLevel level, Vec3 at, int size, boolean carriesStar) {
        hold(level, at);
        MeteorEntity meteor = new MeteorEntity(WakingWorld.METEOR.get(), level);
        // a lone star (nobody else's scar open) writes its own record, so the crater is remembered
        if (scarOf(level) == null) meteor.ownScar(Scars.begin(level, BlockPos.containing(at), "a falling star"));
        meteor.setSize(size);
        meteor.setCarriesStar(carriesStar);
        meteor.aimAt(at, 110 + level.random.nextInt(40), 20 + level.random.nextInt(24), 2.6 + size * 0.3);
        level.addFreshEntity(meteor);
        return meteor;
    }

    /**
     * A star spawns above and to one side of where it will land, which can be past the edge of what
     * anyone has loaded - and an entity in a chunk that is not ticking does not fall. This holds the
     * few chunks it needs open for half a minute and then lets go of them on its own.
     */
    public static void hold(ServerLevel level, Vec3 at) {
        ChunkPos cp = new ChunkPos(net.minecraft.core.BlockPos.containing(at));
        level.getChunkSource().addRegionTicket(METEOR_TICKET, cp, 4, cp);
    }

    /**
     * The ground at (x, z) - with the chunk loaded first.
     *
     * <p>{@code getHeightmapPos} on a chunk that is not there yet answers the bottom of the world, and
     * a cataclysm aimed at the bottom of the world is a cataclysm at bedrock. Everything that picks a
     * spot out at the edge of what is loaded goes through here.</p>
     */
    public static BlockPos surface(ServerLevel level, double x, double z) {
        BlockPos guess = BlockPos.containing(x, 0, z);
        level.getChunk(guess);                                  // loads or generates it
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, guess);
    }

    /** Not on a player's doorstep: their spawn point, the world spawn, and anywhere too close to a player. */
    static boolean away(ServerLevel level, BlockPos at) {
        int keep = WakingConfig.meteorSafeRadius();
        if (keep > 0) {
            if (level.getSharedSpawnPos().closerThan(at, keep)) return false;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(at.getX(), at.getY(), at.getZ()) < 24 * 24) return false;  // never on their head
                BlockPos bed = p.getRespawnPosition();
                if (bed != null && p.getRespawnDimension() == level.dimension() && bed.closerThan(at, keep)) return false;
            }
        }
        return true;
    }

    // ---- saved with the world --------------------------------------------------------------

    private static Cataclysms load(CompoundTag tag, HolderLookup.Provider registries) {
        Cataclysms c = new Cataclysms();
        c.phase = Phase.values()[Math.min(tag.getInt("Phase"), Phase.values().length - 1)];
        c.phaseTicks = tag.getInt("Ticks");
        c.meteorsLeft = tag.getInt("Left");
        c.cooldownUntilDay = tag.getInt("Cooldown");
        c.nextDrop = tag.getInt("Next");
        return c;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Phase", phase.ordinal());
        tag.putInt("Ticks", phaseTicks);
        tag.putInt("Left", meteorsLeft);
        tag.putInt("Cooldown", cooldownUntilDay);
        tag.putInt("Next", nextDrop);
        return tag;
    }

    /** For the debug command: start one right now. */
    public static void force(ServerLevel level) {
        Cataclysms c = get(level);
        c.cooldownUntilDay = 0;
        c.begin(level);
    }
}
