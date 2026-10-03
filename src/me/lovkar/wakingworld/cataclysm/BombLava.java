package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lava a volcano's bombs leave in the country, written down so that it can set.
 *
 * <p>A molten bomb splashes three or four source blocks of real lava onto whatever it hits, and lava
 * in the Overworld never cools by itself. Every eruption used to leave a ring of little burning
 * pools round the mountain for good, and nobody could build within a long walk of a volcano without
 * fire resistance. (A player building a town under one asked for exactly this.) Now each source a
 * bomb pours is remembered with the moment it landed, and after {@code volcanoBombCoolSeconds} it
 * crusts over into the black rock the mountain's own flow sets into. The little flow it fed dries
 * up by itself once its source is rock.</p>
 *
 * <p>Only a source still standing is turned: one scooped up in a bucket, or met by water first, is
 * simply forgotten. A source in an unloaded chunk waits for its chunk, so whoever comes back an
 * hour later finds rock and not lava. The crater pool is not a bomb's and is left alone - that one
 * glows for good, on purpose.</p>
 */
public final class BombLava extends SavedData {
    public static final String NAME = "wakingworld_bomb_lava";
    private static final Factory<BombLava> FACTORY = new Factory<>(BombLava::new, BombLava::load, null);
    /** Remembered at most; the oldest are forgotten first, so a run-away world cannot grow the file without end. */
    private static final int MOST = 4096;

    /** Position to the game time it was poured, in the order they were poured; a source poured twice keeps its first time. */
    private final LinkedHashMap<Long, Long> pours = new LinkedHashMap<>();

    public static BombLava get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** A bomb has just poured a source of lava here. */
    public static void poured(ServerLevel level, BlockPos at) {
        if (!WakingConfig.bombsCool()) return;
        BombLava data = get(level);
        if (data.pours.containsKey(at.asLong())) return;
        if (data.pours.size() >= MOST) {
            Iterator<Long> oldest = data.pours.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        data.pours.put(at.asLong(), level.getGameTime());
        data.setDirty();
    }

    /** Once a second (from {@link Cataclysms}): set every source whose time has come and whose chunk is loaded. */
    public static void onLevelTick(ServerLevel level) {
        if (!WakingConfig.bombsCool()) return;
        BombLava data = get(level);
        if (data.pours.isEmpty()) return;
        long due = level.getGameTime() - WakingConfig.bombCoolSeconds() * 20L;
        boolean changed = false;
        Iterator<Map.Entry<Long, Long>> it = data.pours.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> pour = it.next();
            if (pour.getValue() > due) break;               // poured in order: the rest are younger
            BlockPos at = BlockPos.of(pour.getKey());
            if (!level.isLoaded(at)) continue;              // its chunk is away: try again when it is back
            set(level, at);
            it.remove();
            changed = true;
        }
        if (changed) data.setDirty();
    }

    /** Turns one source into rock, if it is still a source: mostly basalt, some blackstone, never magma. */
    static boolean set(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (!state.is(Blocks.LAVA) || !state.getFluidState().isSource()) return false;
        RandomSource rnd = level.getRandom();
        Block into = rnd.nextDouble() < 0.7 ? Blocks.BASALT : Blocks.BLACKSTONE;
        // neighbours told (flag 3), so the flow this source fed notices and dries up
        level.setBlock(at, into.defaultBlockState(), 3);
        level.playSound(null, at, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.2F + rnd.nextFloat() * 0.6F);
        Cataclysms.puff(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.012);
        return true;
    }

    /** How many sources are waiting to set (for the debug command and the tests). */
    public int waiting() {
        return pours.size();
    }

    // ---- saved with the world --------------------------------------------------------------

    private static BombLava load(CompoundTag tag, HolderLookup.Provider registries) {
        BombLava data = new BombLava();
        long[] flat = tag.getLongArray("Pours");
        for (int i = 0; i + 1 < flat.length; i += 2) data.pours.putIfAbsent(flat[i], flat[i + 1]);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] flat = new long[pours.size() * 2];
        int i = 0;
        for (Map.Entry<Long, Long> pour : pours.entrySet()) {
            flat[i++] = pour.getKey();
            flat[i++] = pour.getValue();
        }
        tag.put("Pours", new LongArrayTag(flat));
        return tag;
    }
}
