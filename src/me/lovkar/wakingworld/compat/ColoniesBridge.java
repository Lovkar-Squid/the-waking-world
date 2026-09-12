package me.lovkar.wakingworld.compat;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.ColonyUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The only class in the mod that names a MineColonies type. It is loaded on first use, and
 * {@link Colonies} uses it only when {@code minecolonies} is in the mod list - without the mod
 * the JVM never resolves these imports.
 *
 * <p>Built against MineColonies 1.1.1368 for 1.21.1. The two calls used are the ones the mod
 * itself uses for "is this colony land": the chunk's owning colony, and the colonies that hold a
 * static claim on it (the ring round a town hall). Reading is all that ever happens here.</p>
 */
final class ColoniesBridge {
    private ColoniesBridge() {
    }

    /** A colony owns this chunk, or holds it as part of its base claim. */
    static boolean claimed(ChunkAccess chunk) {
        if (ColonyUtils.getOwningColony(chunk) != 0) return true;
        var statics = ColonyUtils.getStaticClaims(chunk);
        return statics != null && !statics.isEmpty();
    }

    /** The name of the colony whose land this is, or null. Loads nothing new: the chunk is one we have just read. */
    static String nameAt(ServerLevel level, BlockPos pos) {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        return colony == null ? null : colony.getName();
    }
}
