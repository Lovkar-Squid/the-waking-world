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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The two that come out of the weather rather than out of the sky: the tornado and the earthquake.
 *
 * <p>They share a scheduler because they share a shape - one roll a day, a cooldown of their own,
 * and then something that runs for a minute or two and is gone. Neither leaves a landmark the way a
 * crater or a mountain does; what they leave is a story and a mess to tidy.</p>
 */
public final class Weather extends SavedData {
    public static final String NAME = "wakingworld_weather";
    private static final Factory<Weather> FACTORY = new Factory<>(Weather::new, Weather::load, null);

    private int tornadoCooldownDay;
    private int quakeCooldownDay;
    private int quakeTicks;              // how much shaking is left
    /**
     * The warning. Both of these are rolled the moment the world decides on them and then held for
     * {@code omenSeconds} while the light goes wrong and the animals leave, so a player has time to
     * be somewhere else - and time to be frightened, which is the whole point.
     */
    private int omenTicks;
    private double ox, oy, oz;
    private boolean omenIsTornado;
    private double qx, qy, qz;           // where it is centred

    private Weather() {
    }

    public static Weather get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static boolean busy(ServerLevel level) {
        return get(level).quakeTicks > 0;
    }

    public static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        get(level).tick(level);
    }

    private void tick(ServerLevel level) {
        // a warning that is running comes before anything else is rolled
        if (omenTicks > 0) {
            omenTicks -= 20;
            Vec3 where = new Vec3(ox, oy, oz);
            Omen.tick(level, where, omenIsTornado ? Omen.Kind.TORNADO : Omen.Kind.EARTHQUAKE, omenTicks / 20);
            if (omenTicks <= 0) {
                if (omenIsTornado) {
                    TornadoEntity.spawn(level, where, WakingConfig.tornadoSeconds());
                    for (ServerPlayer p : level.players()) {
                        p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.tornado.warning").withStyle(ChatFormatting.GRAY));
                    }
                    me.lovkar.wakingworld.story.Chronicle.record(level, "cataclysm", "tornado", BlockPos.containing(ox, oy, oz), null);
                    WakingWorld.LOGGER.info("cataclysm: a tornado forms at {} {} {}", (int) ox, (int) oy, (int) oz);
                } else {
                    startQuake(level, where, level.random);
                }
            }
            setDirty();
            return;
        }

        // the quake first: it is already running, and it does not care what else is going on
        if (quakeTicks > 0) {
            Scars.writing(level, scar);
            quakeTicks -= 20;
            int total = Math.max(1, WakingConfig.earthquakeSeconds() * 20);
            float progress = Math.min(1.0F, 1.0F - quakeTicks / (float) total);
            Earthquake.second(level, new Vec3(qx, qy, qz), Earthquake.envelope(progress));
            if (quakeTicks <= 0) {
                Earthquake.climax(level, new Vec3(qx, qy, qz), level.random);
                for (ServerPlayer p : level.players()) {
                    p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.earthquake.over").withStyle(ChatFormatting.GRAY));
                }
                // until now the quake was the only one of the five that left nothing at all
                if (WakingConfig.blight()) {
                    Aftermath.blight(level, BlockPos.containing(qx, qy, qz), 34, 0.45, level.random);
                }
                Survived.near(level, Omen.Kind.EARTHQUAKE, new Vec3(qx, qy, qz));
                Scars.close();
                Scars.done(level, scar);
                scar = null;
                // and a shrine that was under it may not have survived being shaken
                Answer.maybe(level, new Vec3(qx, qy, qz), Omen.Kind.EARTHQUAKE);
                WakingWorld.LOGGER.info("cataclysm: the ground settles");
            }
            Scars.close();
            setDirty();
            return;
        }

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        if (Cataclysms.busy(level) || Volcano.busy(level) || BloodMoon.running(level)) return;
        // nothing new starts while the camera is rolling: a world-driven cataclysm on top of a
        // scene is a ruined take, and there is no way to tell from the footage what happened
        if (me.lovkar.wakingworld.story.Cinematics.running()) return;

        RandomSource rnd = level.random;
        int day = (int) (level.getDayTime() / 24000L);
        long t = level.getDayTime() % 24000L;

        // a tornado comes in the afternoon, when the air has had all day to go wrong
        if (WakingConfig.tornadoes() && day >= tornadoCooldownDay && t >= 9000 && t <= 9600) {
            if (rnd.nextDouble() <= WakingConfig.tornadoChance() * Unrest.factor(level)) {
                ServerPlayer near = players.get(rnd.nextInt(players.size()));
                Vec3 at = where(level, near, rnd);
                tornadoCooldownDay = day + WakingConfig.daysBetweenTornadoes();
                warn(level, at, true);
            } else {
                tornadoCooldownDay = day + 1;
            }
            setDirty();
            return;
        }

        // the ground turns at any hour, but it is rolled once, early
        if (WakingConfig.earthquakes() && day >= quakeCooldownDay && t >= 2000 && t <= 2600) {
            if (rnd.nextDouble() <= WakingConfig.earthquakeChance() * Unrest.factor(level)) {
                ServerPlayer near = players.get(rnd.nextInt(players.size()));
                quakeCooldownDay = day + WakingConfig.daysBetweenEarthquakes();
                warn(level, where(level, near, rnd), false);
            } else {
                quakeCooldownDay = day + 1;
            }
            setDirty();
        }
    }

    /**
     * Where it happens.
     *
     * <p>Ordinarily somewhere near a player, as it always was. But ground a giant rose or died on
     * is unquiet, and the more unquiet it is the likelier the storm is to find it instead: at the
     * worst of it, four times in five. That is the part a player actually notices - a tornado that
     * comes back to the field where they killed something a week ago.</p>
     */
    private static Vec3 where(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        double unquiet = Unrest.at(level, near.blockPosition());
        BlockPos scar = Unrest.worst(level, near, WakingConfig.landSize() * 2.0);
        if (scar == null) return Cataclysms.openSite(level, near.position(), rnd, 30, 90);
        double pull = Math.max(unquiet, Unrest.at(level, scar));
        if (rnd.nextDouble() > pull * 0.8) return Cataclysms.openSite(level, near.position(), rnd, 30, 90);
        // somewhere inside that land, not exactly on its middle stone. Still open ground: the
        // unquiet country is a reason to go there, not a reason to put a tornado in a jungle.
        int size = WakingConfig.landSize();
        int x = scar.getX() + rnd.nextInt(size / 2) - size / 4;
        int z = scar.getZ() + rnd.nextInt(size / 2) - size / 4;
        Vec3 mid = Vec3.atBottomCenterOf(Cataclysms.surface(level, x, z));
        return Cataclysms.openSite(level, mid, rnd, 0, 60);
    }

    /** Hold the thing back and sound the warning; the tick above lets it go when the time is up. */
    private void warn(ServerLevel level, Vec3 at, boolean tornado) {
        ox = at.x;
        oy = at.y;
        oz = at.z;
        omenIsTornado = tornado;
        if (!WakingConfig.omens()) {
            omenTicks = 20;                       // straight through, next tick
            return;
        }
        omenTicks = WakingConfig.omenSeconds() * 20;
        Omen.begin(level, at, tornado ? Omen.Kind.TORNADO : Omen.Kind.EARTHQUAKE, WakingConfig.omenSeconds());
    }

    /** The quake's scar; the tornado carries its own on the entity. */
    private java.util.UUID scar;

    private void startQuake(ServerLevel level, Vec3 at, RandomSource rnd) {
        scar = Scars.begin(level, BlockPos.containing(at.x, at.y, at.z), "an earthquake");
        qx = at.x;
        qy = at.y;
        qz = at.z;
        quakeTicks = WakingConfig.earthquakeSeconds() * 20;
        int opened = Earthquake.shake(level, at, WakingConfig.earthquakeSeconds(), rnd);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.earthquake.warning").withStyle(ChatFormatting.GOLD));
        }
        // "blocks of fault" was written when the whole fault was cut in one tick. It walks now, a
        // few blocks a second for the whole of the shaking, so this count is only what opened in the
        // first instant - it read as nearly always zero, which looked like a broken earthquake.
        me.lovkar.wakingworld.story.Chronicle.record(level, "cataclysm", "quake", BlockPos.containing(at.x, at.y, at.z), null);
        WakingWorld.LOGGER.info("cataclysm: an earthquake at {} {} {} ({} s of shaking, {} blocks opened at once)",
                (int) at.x, (int) at.y, (int) at.z, WakingConfig.earthquakeSeconds(), opened);
        setDirty();
    }

    /** For the debug command. */
    public static void forceQuake(ServerLevel level, Vec3 at) {
        Weather w = get(level);
        w.startQuake(level, at, level.random);
    }

    // ---- saved with the world --------------------------------------------------------------

    private static Weather load(CompoundTag tag, HolderLookup.Provider registries) {
        Weather w = new Weather();
        w.tornadoCooldownDay = tag.getInt("TornadoCooldown");
        w.quakeCooldownDay = tag.getInt("QuakeCooldown");
        w.quakeTicks = tag.getInt("QuakeTicks");
        if (tag.hasUUID("Scar")) w.scar = tag.getUUID("Scar");
        w.omenTicks = tag.getInt("OmenTicks");
        w.ox = tag.getDouble("OmenX");
        w.oy = tag.getDouble("OmenY");
        w.oz = tag.getDouble("OmenZ");
        w.omenIsTornado = tag.getBoolean("OmenTornado");
        w.qx = tag.getDouble("QX");
        w.qy = tag.getDouble("QY");
        w.qz = tag.getDouble("QZ");
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("TornadoCooldown", tornadoCooldownDay);
        tag.putInt("QuakeCooldown", quakeCooldownDay);
        tag.putInt("QuakeTicks", quakeTicks);
        if (scar != null) tag.putUUID("Scar", scar);
        tag.putInt("OmenTicks", omenTicks);
        tag.putDouble("OmenX", ox);
        tag.putDouble("OmenY", oy);
        tag.putDouble("OmenZ", oz);
        tag.putBoolean("OmenTornado", omenIsTornado);
        tag.putDouble("QX", qx);
        tag.putDouble("QY", qy);
        tag.putDouble("QZ", qz);
        return tag;
    }
}
