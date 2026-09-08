package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.item.Waker;
import me.lovkar.wakingworld.ritual.AltarBlockEntity;
import me.lovkar.wakingworld.ritual.Rites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * The world answers.
 *
 * <p>A rite is a person deciding to wake a giant: offerings laid out, a horn blown, a choice made.
 * This is the other way it can happen. A cataclysm breaking over a shrine can open it by itself -
 * no offerings, nobody's leave, and nobody to blame. It is the single moment where the two halves
 * of this mod are most obviously one mod: the ground the giants sleep in is the same ground the
 * sky is falling on, and enough of the second will eventually see to the first.</p>
 *
 * <p>Three rules keep it from being a nuisance. It only fires when somebody is close enough to
 * meet what comes out - a colossus woken in an empty country would stand there for ever, never
 * despawning and never being fought. It never opens the Titan's arena, which is a door the story
 * says a player has to unlock. And it is a config value away from never happening at all.</p>
 */
public final class Answer {
    /** How far from the cataclysm a shrine may be and still be shaken open. */
    private static final int CHUNKS = 3;
    /** Somebody has to be near enough to meet it. */
    private static final double WITNESS = 160.0;

    private Answer() {
    }

    /**
     * Try to shake a sleeper awake.
     *
     * @param at   where the cataclysm did its worst
     * @param kind which cataclysm, for the log and the line in the chat
     * @return the colossus, or null - which is the usual answer
     */
    public static ColossusEntity maybe(ServerLevel level, Vec3 at, Omen.Kind kind) {
        double chance = WakingConfig.answerChance();
        if (chance <= 0.0) return null;
        if (level.random.nextDouble() > chance) return null;

        BlockPos where = BlockPos.containing(at);
        // nobody near enough to meet it means it must not be woken at all
        ServerPlayer witness = null;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (p.distanceToSqr(at) <= WITNESS * WITNESS) { witness = p; break; }
        }
        if (witness == null) return null;

        AltarBlockEntity altar = altarNear(level, where);
        if (altar == null) return null;
        String k = altar.kind();
        if (k == null || k.isEmpty() || "titan".equals(Rites.base(k))) return null;   // never the arena

        BlockPos c = altar.getBlockPos();
        Vec3 spot = Vec3.atBottomCenterOf(c).add(0, 0, 36);
        ColossusEntity woken = Waker.wakeAt(level, witness.position(), Rites.palette(k), Rites.height(k), spot, null);
        if (woken == null) return null;                                   // one was already up
        woken.setAltar(c);

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 400 * 400) continue;
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.answer." + kind.key)
                    .withStyle(ChatFormatting.GOLD));
        }
        WakingWorld.LOGGER.info("cataclysm: the {} opened the {} shrine at {} {} {} - it woke on its own",
                kind.key, k, c.getX(), c.getY(), c.getZ());
        return woken;
    }

    /**
     * The nearest full shrine altar in the loaded chunks around a place.
     *
     * <p>Only loaded chunks are looked at, on purpose: a shrine nobody has ever been near has not
     * been generated as a block entity yet, and forcing it into memory to wake something out of it
     * would be a strange thing for an earthquake to do.</p>
     */
    private static AltarBlockEntity altarNear(ServerLevel level, BlockPos at) {
        ChunkPos home = new ChunkPos(at);
        AltarBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(home.x + dx, home.z + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof AltarBlockEntity a) || a.great()) continue;
                    if (Rites.lesser(a.kind())) continue;
                    double d = be.getBlockPos().distSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = a;
                    }
                }
            }
        }
        return best;
    }
}
