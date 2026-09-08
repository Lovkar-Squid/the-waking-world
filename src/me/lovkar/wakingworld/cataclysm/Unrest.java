package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ground does not settle where a giant has been.
 *
 * <p>Until now the mod was two mods sharing a jar. The colossi were 0.1 and the cataclysms were
 * 0.2, and nothing that happened in one was of the slightest interest to the other: a player could
 * wake and kill six giants without the weather noticing, and a volcano could open on a shrine
 * without the shrine caring. This is the piece that makes them one thing.</p>
 *
 * <p>Waking a giant leaves the land around it unquiet, and killing one leaves it far worse. While
 * that lasts - a couple of weeks, and it fades - a cataclysm is likelier over that ground, and
 * when one does come it aims for it. So the world answers what the player has been doing to it,
 * in the only language it has, and a country full of dead colossi is a country that shakes.</p>
 *
 * <p>The unit is the named land, because the world is already divided into those and a player
 * already knows their names. Nothing here is expensive: there are never more than a few dozen
 * unquiet cells, they are read once a second at most, and they forget themselves.</p>
 */
public final class Unrest extends SavedData {
    public static final String NAME = "wakingworld_unrest";
    private static final Factory<Unrest> FACTORY = new Factory<>(Unrest::new, Unrest::load, null);

    /** What waking one is worth, and what killing one is worth. Death tears the ground; waking only stirs it. */
    public static final double WOKEN = 0.45;
    public static final double SLAIN = 1.00;
    /** More than this many cells and the oldest are dropped: a world does not need an eternal ledger. */
    private static final int KEEP = 64;

    /** One unquiet cell: how bad it was, and the day it was made that bad. */
    private record Scar(double peak, long day) {
    }

    private final Map<Long, Scar> scars = new HashMap<>();

    private Unrest() {
    }

    public static Unrest get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int cell(int block) {
        return me.lovkar.wakingworld.land.Lands.cellOf(block);
    }

    private static long today(ServerLevel level) {
        return level.getDayTime() / 24000L;
    }

    /** What is left of a scar today: it fades to nothing over {@code unrestDays}. */
    private static double left(Scar s, long day) {
        int over = Math.max(1, WakingConfig.unrestDays());
        double age = day - s.day();
        if (age < 0) return s.peak();                     // the clock went backwards; be generous
        return Math.max(0.0, s.peak() * (1.0 - age / over));
    }

    // ---- writing ---------------------------------------------------------------------------

    /**
     * Something happened here that the ground will remember.
     *
     * @param amount {@link #WOKEN} or {@link #SLAIN}; it adds to whatever has not yet faded, and
     *               never goes above one, so a hundred giants in one field is not a hundred times
     *               worse than one - it is simply as bad as ground gets.
     */
    public static void stir(ServerLevel level, BlockPos at, double amount) {
        if (!WakingConfig.unrest()) return;
        Unrest u = get(level);
        long day = today(level);
        long k = key(cell(at.getX()), cell(at.getZ()));
        Scar was = u.scars.get(k);
        double now = Math.min(1.0, (was == null ? 0.0 : left(was, day)) + amount);
        u.scars.put(k, new Scar(now, day));
        u.forget(day);
        u.setDirty();
        WakingWorld.LOGGER.info("unrest: the ground at {} {} is at {}", at.getX(), at.getZ(), String.format("%.2f", now));
    }

    /** Drop what has faded, and the oldest if there are somehow too many. */
    private void forget(long day) {
        scars.entrySet().removeIf(e -> left(e.getValue(), day) <= 0.001);
        while (scars.size() > KEEP) {
            Long oldest = null;
            long when = Long.MAX_VALUE;
            for (Map.Entry<Long, Scar> e : scars.entrySet()) {
                if (e.getValue().day() < when) {
                    when = e.getValue().day();
                    oldest = e.getKey();
                }
            }
            if (oldest == null) break;
            scars.remove(oldest);
        }
    }

    // ---- reading ---------------------------------------------------------------------------

    /** How unquiet one place is, from 0 to 1. */
    public static double at(ServerLevel level, BlockPos pos) {
        if (!WakingConfig.unrest()) return 0.0;
        Scar s = get(level).scars.get(key(cell(pos.getX()), cell(pos.getZ())));
        return s == null ? 0.0 : left(s, today(level));
    }

    /**
     * The worst ground anybody is standing anywhere near, from 0 to 1.
     *
     * <p>This is what the daily rolls ask, because they are asking a question about the world and
     * not about a place: is it a quiet year, or has somebody been waking things. Only cells with a
     * player within a couple of lands of them count - unrest in a country nobody has returned to
     * is not what makes tonight worse.</p>
     */
    public static double near(ServerLevel level) {
        if (!WakingConfig.unrest()) return 0.0;
        Unrest u = get(level);
        if (u.scars.isEmpty()) return 0.0;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return 0.0;
        long day = today(level);
        int size = WakingConfig.landSize();
        double reach = size * 2.0;
        double worst = 0.0;
        for (Map.Entry<Long, Scar> e : u.scars.entrySet()) {
            double v = left(e.getValue(), day);
            if (v <= worst) continue;
            double mx = cellX(e.getKey()) * (double) size + size / 2.0;
            double mz = cellZ(e.getKey()) * (double) size + size / 2.0;
            for (ServerPlayer p : players) {
                double dx = p.getX() - mx, dz = p.getZ() - mz;
                if (dx * dx + dz * dz <= reach * reach) {
                    worst = v;
                    break;
                }
            }
        }
        return worst;
    }

    /**
     * What to multiply a daily chance by.
     *
     * <p>One over quiet ground, and up to {@code 1 + unrestFactor} over the worst of it. Every one
     * of the five rolls goes through this, which is the whole of the connection as far as the
     * scheduler is concerned.</p>
     */
    public static double factor(ServerLevel level) {
        return 1.0 + WakingConfig.unrestFactor() * near(level);
    }

    /**
     * The middle of the most unquiet land within reach of a player, or null if there is none.
     *
     * <p>The tornado and the earthquake use this to choose where to happen. A cataclysm that opens
     * exactly where a giant was killed a week ago is the part a player will actually notice - and
     * it is true, which is better than being dramatic.</p>
     */
    public static BlockPos worst(ServerLevel level, ServerPlayer near, double reach) {
        if (!WakingConfig.unrest()) return null;
        Unrest u = get(level);
        if (u.scars.isEmpty()) return null;
        long day = today(level);
        int size = WakingConfig.landSize();
        double best = 0.0;
        BlockPos where = null;
        for (Map.Entry<Long, Scar> e : u.scars.entrySet()) {
            double v = left(e.getValue(), day);
            if (v <= best) continue;
            int mx = (int) (cellX(e.getKey()) * (long) size + size / 2);
            int mz = (int) (cellZ(e.getKey()) * (long) size + size / 2);
            double dx = near.getX() - mx, dz = near.getZ() - mz;
            if (dx * dx + dz * dz > reach * reach) continue;
            best = v;
            where = new BlockPos(mx, near.getBlockY(), mz);
        }
        return where;
    }

    private static int cellX(long k) {
        return (int) (k >> 32);
    }

    private static int cellZ(long k) {
        return (int) k;
    }

    /** For the debug command: every unquiet cell, worst first. */
    public static List<String> report(ServerLevel level) {
        Unrest u = get(level);
        long day = today(level);
        int size = WakingConfig.landSize();
        List<String> out = new ArrayList<>();
        u.scars.entrySet().stream()
                .sorted((a, b) -> Double.compare(left(b.getValue(), day), left(a.getValue(), day)))
                .forEach(e -> out.add(String.format("%d %d  %.2f  (%d day(s) ago)",
                        cellX(e.getKey()) * size + size / 2, cellZ(e.getKey()) * size + size / 2,
                        left(e.getValue(), day), day - e.getValue().day())));
        return out;
    }

    // ---- saved with the world --------------------------------------------------------------

    private static Unrest load(CompoundTag tag, HolderLookup.Provider registries) {
        Unrest u = new Unrest();
        ListTag list = tag.getList("Scars", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            u.scars.put(t.getLong("Cell"), new Scar(t.getDouble("Peak"), t.getLong("Day")));
        }
        return u;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        scars.forEach((k, s) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("Cell", k);
            t.putDouble("Peak", s.peak());
            t.putLong("Day", s.day());
            list.add(t);
        });
        tag.put("Scars", list);
        return tag;
    }
}
