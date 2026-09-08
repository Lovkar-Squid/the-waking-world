package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.ruin.FightRecord;
import me.lovkar.wakingworld.ruin.RuinLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * What a cataclysm did to the country, so an Hourglass of Restoration can undo it.
 *
 * <p>The mod could already put back everything a colossus broke: the fight is written down block by
 * block in a {@link FightRecord}, and turning the hourglass over near a finished fight lays the
 * country back the way it was. A cataclysm is the same kind of event from a player's side - a thing
 * that happened TO their world, on a scale they did not choose - and there was no way to take one
 * back. Now there is, and it is the same item and the same gesture, which is the point: a player
 * does not have to learn a second mechanism for the same feeling.</p>
 *
 * <p><b>How it hooks in.</b> Every one of the five writes its blocks through a handful of methods,
 * so rather than teaching each of them about the ledger, a cataclysm declares its scar open for the
 * duration of a tick's work and every block written through {@link #set} is remembered first. The
 * static is safe because all of this runs on the server thread, and it is cleared in a finally.</p>
 *
 * <p><b>What it costs.</b> A volcano is the big one: its cone and its flows come to a few tens of
 * thousands of blocks, and each is remembered with its neighbours, which is what makes the
 * restoration seamless rather than leaving floating torches and dirt where grass was. The ledger's
 * own cap of {@value FightRecord#CAP} blocks a record and its habit of keeping only the last ten
 * are what stop a world's save file growing without end; {@code cataclysmRestore} switches the
 * whole thing off for a server that would rather have the disk.</p>
 */
public final class Scars {
    private static UUID open;
    private static ServerLevel where;

    private Scars() {
    }

    /** A new scar, opened where the thing began. Null when the recording is switched off. */
    public static UUID begin(ServerLevel level, BlockPos at, String what) {
        if (!WakingConfig.cataclysmRestore()) return null;
        UUID id = UUID.randomUUID();
        RuinLedger.get(level).open(id, at, level.getGameTime());
        WakingWorld.LOGGER.info("cataclysm: {} at {} {} {} is being written down, so it can be taken back",
                what, at.getX(), at.getY(), at.getZ());
        return id;
    }

    /** The thing is over: the hourglass may have it now. */
    public static void done(ServerLevel level, UUID id) {
        if (id == null) return;
        RuinLedger ledger = RuinLedger.get(level);
        FightRecord r = ledger.record(id);
        BlockPos where = r == null ? null : r.center();
        int blocks = r == null ? 0 : r.size();
        ledger.finish(id, level.getGameTime());
        WakingWorld.LOGGER.info("cataclysm: the scar is closed - {} blocks remembered, an hourglass can put them back", blocks);
        // and somebody has to be told. A volcano takes eight minutes to stop moving and said nothing
        // when it had: a player holding an hourglass had no way of knowing when it would work, so it
        // read as broken. This is the moment it starts working, and it is worth one line.
        if (where == null || blocks <= 0) return;
        for (net.minecraft.server.level.ServerPlayer p : level.getPlayers(pl -> pl.blockPosition().closerThan(where, 160))) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("cataclysm.wakingworld.settled")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }

    /** Everything written between here and {@link #close()} belongs to this scar. */
    public static void writing(ServerLevel level, UUID id) {
        open = id;
        where = level;
    }

    public static void close() {
        open = null;
        where = null;
    }

    /** Remember what stands here, before whatever is about to happen to it. */
    public static void mark(ServerLevel level, BlockPos at) {
        if (open == null || where != level) return;
        FightRecord r = RuinLedger.get(level).record(open);
        if (r != null) r.mark(level, at, false);
    }

    /**
     * Set a block the way a cataclysm sets one: remembered first.
     *
     * <p>Flag 2 rather than 3 on purpose, as everywhere else in this package - a cataclysm writes
     * thousands of blocks and cannot afford a neighbour update on each of them; the restoration
     * does its own settling pass afterwards.</p>
     */
    public static void set(ServerLevel level, BlockPos at, BlockState state) {
        mark(level, at);
        level.setBlock(at, state, 2);
    }
}
