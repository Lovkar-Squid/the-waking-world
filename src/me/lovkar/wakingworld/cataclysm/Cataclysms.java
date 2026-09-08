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

/**
 * The Cataclysms: the world does not only wake, it answers. This is the scheduler - one per level,
 * saved with the world - and the first of them, the meteor shower.
 *
 * <p><b>The Falling Sky.</b> On a rare night the sky turns and the stars come down. Half a minute
 * of warning (a rumble you feel more than hear, a word in the chat), then two to four minutes of
 * stars falling around whoever is out in the open: each one a burning mass that tears a crater,
 * scorches the ground and leaves its Starstone glowing at the bottom. It never aims at a bed or at
 * the world spawn, and it can be switched off entirely in the config.</p>
 */
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
        WakingWorld.LOGGER.info("cataclysm: a meteor shower begins ({} stars)", meteorsLeft);
    }

    private void end(ServerLevel level) {
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
    private static void hold(ServerLevel level, Vec3 at) {
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
