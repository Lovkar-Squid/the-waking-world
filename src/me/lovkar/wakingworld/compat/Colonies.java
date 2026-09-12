package me.lovkar.wakingworld.compat;

import java.util.HashMap;
import java.util.Map;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

/**
 * Keeps the mod off other people's land - today that means MineColonies.
 *
 * <p>A colony claims chunks: its town hall's chunk and a ring round it at first, then more as it
 * builds. Everything in this mod that changes the world on its own asks here first: a cataclysm
 * choosing where to land, a crater eating the ground, a volcano growing, a tornado lifting a roof,
 * a kingdom siting a farm or a house, its masons laying a course, its engines choosing a target,
 * a colossus treading or falling. A claimed chunk - and a buffer of {@link WakingConfig#colonyBuffer}
 * blocks round every claimed chunk - answers "keep off", and the thing is not done there. A house
 * plot is passed over, a star falls somewhere else, a wall gets a gap.</p>
 *
 * <p>The mod does not depend on MineColonies. Nothing in this class names a MineColonies type;
 * {@link ColoniesBridge} does, and it is only ever loaded when {@code minecolonies} is in the mod
 * list. Chunks are never loaded to answer the question: a chunk that is not in memory is not one
 * this mod is about to write into, and an unloaded chunk of a colony is protected by being
 * unloaded.</p>
 *
 * <p>Claims change slowly, and the question is asked for every block of a crater, so the answers
 * are kept for ten seconds per chunk.</p>
 */
public final class Colonies {
    private Colonies() {
    }

    private static final boolean PRESENT = lookFor();

    /** Asked once, at class load. Outside a running game (the headless checks) there is no mod list, and the answer is no. */
    private static boolean lookFor() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("minecolonies");
        } catch (Throwable t) {
            return false;
        }
    }
    private static final int KEEP_TICKS = 200;
    private static final int CACHE_LIMIT = 8192;

    /** dimension -> chunk (ChunkPos.asLong) -> {claimed 0/1, good until game time} */
    private static final Map<ResourceKey<Level>, Map<Long, long[]>> CACHE = new HashMap<>();

    /** MineColonies is installed. */
    public static boolean present() {
        return PRESENT;
    }

    /** MineColonies is installed and the config asks for its colonies to be left alone. */
    public static boolean active() {
        return PRESENT && !failed && WakingConfig.protectColonies();
    }

    /** True if this block is in a colony's chunk, or within the configured buffer of one. */
    public static boolean keepOff(ServerLevel level, BlockPos pos) {
        return active() && near(level, pos, WakingConfig.colonyBuffer());
    }

    /**
     * True if anything within {@code margin} blocks of this position is in a colony's chunk or its
     * buffer - for things with a footprint: a house plot, a work, a crater, a volcano's cone.
     */
    public static boolean keepOff(ServerLevel level, BlockPos pos, int margin) {
        return active() && near(level, pos, WakingConfig.colonyBuffer() + Math.max(0, margin));
    }

    /** True if this block's own chunk is claimed - no buffer. For a thing already in the air that must not come down here. */
    public static boolean claimed(ServerLevel level, BlockPos pos) {
        return active() && claimed(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** The colony's name for a log line, or null. Only for a chunk that is loaded and claimed. */
    public static String nameAt(ServerLevel level, BlockPos pos) {
        if (!active()) return null;
        try {
            return ColoniesBridge.nameAt(level, pos);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The same question, but chunks that are not in memory are loaded to answer it.
     *
     * <p>{@link #keepOff} is asked for every block of a crater, so it must be cheap and treats a
     * chunk it cannot see as unclaimed. That is wrong for the handful of places where the mod
     * DECIDES something once - where a volcano opens, where a house or a work goes - because the
     * spot is often chosen a hundred blocks out, past what is in memory, and "I could not see it"
     * would read as "nobody lives there". Those ask this instead.</p>
     */
    public static boolean keepOffLoading(ServerLevel level, BlockPos pos, int margin) {
        if (!active()) return false;
        int blocks = WakingConfig.colonyBuffer() + Math.max(0, margin);
        int r = (blocks + 15) >> 4;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int gapX = dx == 0 ? 0 : (Math.abs(dx) - 1) * 16 + edgeGap(pos.getX(), dx);
                int gapZ = dz == 0 ? 0 : (Math.abs(dz) - 1) * 16 + edgeGap(pos.getZ(), dz);
                if (Math.max(gapX, gapZ) > blocks) continue;
                try {
                    if (ColoniesBridge.claimed(level.getChunk(cx + dx, cz + dz))) return true;
                } catch (Throwable t) {
                    WakingWorld.LOGGER.warn("colonies: cannot read MineColonies claims ({}) - colony protection is off", t.toString());
                    failed = true;
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean near(ServerLevel level, BlockPos pos, int blocks) {
        int r = (blocks + 15) >> 4;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (claimed(level, cx, cz)) return true;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) continue;
                // the buffer is measured in blocks from the claimed chunk's nearest edge, not in whole chunks
                int gapX = dx == 0 ? 0 : (Math.abs(dx) - 1) * 16 + edgeGap(pos.getX(), dx);
                int gapZ = dz == 0 ? 0 : (Math.abs(dz) - 1) * 16 + edgeGap(pos.getZ(), dz);
                if (Math.max(gapX, gapZ) > blocks) continue;
                if (claimed(level, cx + dx, cz + dz)) return true;
            }
        }
        return false;
    }

    /** Blocks from {@code coord} to the near edge of the neighbouring chunk in direction {@code d} (0 for the same column). */
    private static int edgeGap(int coord, int d) {
        int inChunk = coord & 15;
        if (d > 0) return 16 - inChunk;
        if (d < 0) return inChunk + 1;
        return 0;
    }

    private static boolean claimed(ServerLevel level, int cx, int cz) {
        long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
        long now = level.getGameTime();
        Map<Long, long[]> dim = CACHE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long[] hit = dim.get(key);
        if (hit != null && hit[1] > now) return hit[0] != 0;
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return false;                       // not in memory: nothing of ours goes there either
        boolean claimed;
        try {
            claimed = ColoniesBridge.claimed(chunk);
        } catch (Throwable t) {
            // a MineColonies build this bridge does not fit: say so once, and from then on keep off nothing
            WakingWorld.LOGGER.warn("colonies: cannot read MineColonies claims ({}) - colony protection is off", t.toString());
            failed = true;
            return false;
        }
        if (dim.size() > CACHE_LIMIT) dim.clear();
        dim.put(key, new long[]{claimed ? 1 : 0, now + KEEP_TICKS});
        return claimed;
    }

    private static volatile boolean failed = false;

    /** Forget every cached answer - a colony was founded or abandoned, or a test wants the truth now. */
    public static void forget() {
        CACHE.clear();
    }

    static boolean broken() {
        return failed;
    }
}
