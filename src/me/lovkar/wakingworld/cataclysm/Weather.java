package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
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
        // the quake first: it is already running, and it does not care what else is going on
        if (quakeTicks > 0) {
            quakeTicks -= 20;
            float left = Math.min(1.0F, quakeTicks / (float) (WakingConfig.earthquakeSeconds() * 20 * 0.5F));
            Earthquake.second(level, new Vec3(qx, qy, qz), Math.max(0.25F, left));
            if (quakeTicks <= 0) {
                for (ServerPlayer p : level.players()) {
                    p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.earthquake.over").withStyle(ChatFormatting.GRAY));
                }
                WakingWorld.LOGGER.info("cataclysm: the ground settles");
            }
            setDirty();
            return;
        }

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        if (Cataclysms.busy(level) || Volcano.busy(level) || BloodMoon.running(level)) return;
        RandomSource rnd = level.random;
        int day = (int) (level.getDayTime() / 24000L);
        long t = level.getDayTime() % 24000L;

        // a tornado comes in the afternoon, when the air has had all day to go wrong
        if (WakingConfig.tornadoes() && day >= tornadoCooldownDay && t >= 9000 && t <= 9600) {
            if (rnd.nextDouble() <= WakingConfig.tornadoChance()) {
                ServerPlayer near = players.get(rnd.nextInt(players.size()));
                Vec3 at = Earthquake.site(level, near, rnd);
                TornadoEntity.spawn(level, at, WakingConfig.tornadoSeconds());
                tornadoCooldownDay = day + WakingConfig.daysBetweenTornadoes();
                for (ServerPlayer p : level.players()) {
                    p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.tornado.warning").withStyle(ChatFormatting.GRAY));
                }
                WakingWorld.LOGGER.info("cataclysm: a tornado forms at {} {} {}", (int) at.x, (int) at.y, (int) at.z);
            } else {
                tornadoCooldownDay = day + 1;
            }
            setDirty();
            return;
        }

        // the ground turns at any hour, but it is rolled once, early
        if (WakingConfig.earthquakes() && day >= quakeCooldownDay && t >= 2000 && t <= 2600) {
            if (rnd.nextDouble() <= WakingConfig.earthquakeChance()) {
                ServerPlayer near = players.get(rnd.nextInt(players.size()));
                startQuake(level, Earthquake.site(level, near, rnd), rnd);
                quakeCooldownDay = day + WakingConfig.daysBetweenEarthquakes();
            } else {
                quakeCooldownDay = day + 1;
            }
            setDirty();
        }
    }

    private void startQuake(ServerLevel level, Vec3 at, RandomSource rnd) {
        qx = at.x;
        qy = at.y;
        qz = at.z;
        quakeTicks = WakingConfig.earthquakeSeconds() * 20;
        int opened = Earthquake.shake(level, at, WakingConfig.earthquakeSeconds(), rnd);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.earthquake.warning").withStyle(ChatFormatting.GOLD));
        }
        WakingWorld.LOGGER.info("cataclysm: an earthquake at {} {} {} ({} blocks of fault)", (int) at.x, (int) at.y, (int) at.z, opened);
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
        tag.putDouble("QX", qx);
        tag.putDouble("QY", qy);
        tag.putDouble("QZ", qz);
        return tag;
    }
}
