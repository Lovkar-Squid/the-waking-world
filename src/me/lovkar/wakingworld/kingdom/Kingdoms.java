package me.lovkar.wakingworld.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The kingdom's name and temper. A kingdom is named from where it stands; an offence (a struck
 * guard, a robbed treasury, a hurt king) makes it angry with the offender for a while - the guards
 * turn, the traders refuse, the king will not speak - and the town is told.
 */
public final class Kingdoms {
    private Kingdoms() {
    }

    private static final String[] NAMES = {"Ardenhold", "Sarnmark", "Vellmoor", "Hearthgard", "Caldreth", "Ormsby", "Thornwall", "Eldermere", "Greyhaven", "Wyndham", "Stonewick", "Dunmarrow"};

    private static final String[] KINGS = {"Aldric", "Osric", "Theobald", "Leofric", "Godwin", "Edric", "Wulfstan", "Cynric", "Halvard", "Roderic", "Anselm", "Baldwin", "Ethelred", "Corwin"};

    /** A kingdom's name, fixed by where it stands. */
    public static String name(BlockPos center) {
        long h = center.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        return NAMES[(int) Math.floorMod(h, NAMES.length)];
    }

    /** The king's own name, fixed the same way; every successor takes the next name in the line. */
    public static String kingName(BlockPos center, int generation) {
        long h = (center.asLong() + 77) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        return KINGS[(int) Math.floorMod(h + generation * 5L, KINGS.length)];
    }

    /**
     * The succession: when a kingdom's throne has been empty long enough, one of its people - the
     * nearest townsperson to the throne, in the loaded world - is crowned. Called every second.
     */
    public static void tickSuccessions(ServerLevel level) {
        KingdomData data = KingdomData.get(level);
        long now = level.getGameTime();
        for (KingdomData.Kingdom k : data.all()) {
            if (!k.kingDead || k.crownAt < 0 || now < k.crownAt || k.throne == null) continue;
            BlockPos seat = BlockPos.containing(k.throne);
            if (!level.isLoaded(seat)) continue;
            // no crown while the old one is still lying there
            if (!level.getEntitiesOfClass(KingEntity.class, new AABB(seat).inflate(6)).isEmpty()) {
                data.crowned(k.center);
                continue;
            }
            TownsfolkEntity heir = null;
            double best = Double.MAX_VALUE;
            for (TownsfolkEntity t : level.getEntitiesOfClass(TownsfolkEntity.class, new AABB(seat).inflate(80, 40, 80))) {
                if (!t.isAlive() || !t.center().equals(k.center)) continue;
                double d = t.distanceToSqr(k.throne);
                if (d < best) {
                    best = d;
                    heir = t;
                }
            }
            if (heir == null) {
                k.crownAt = now + 20 * 60; // try again in a minute
                continue;
            }
            heir.discard();
            KingEntity king = me.lovkar.wakingworld.WakingWorld.KING.get().create(level);
            if (king == null) continue;
            king.moveTo(k.throne.x, k.throne.y, k.throne.z, 180f, 0);
            king.setYBodyRot(180f);
            king.setYHeadRot(180f);
            data.crowned(k.center);
            king.assign(k.center);
            level.addFreshEntity(king);
            level.playSound(null, seat, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 4.0F, 1.0F);
            level.playSound(null, seat, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 2.0F, 0.7F);
            for (ServerPlayer sp : level.getPlayers(p -> p.distanceToSqr(k.throne) < 200 * 200)) {
                sp.displayClientMessage(Component.translatable("kingdom.wakingworld.new_king", kingName(k.center, data.generation(k.center)), name(k.center)).withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    public static Component title(BlockPos center) {
        return Component.translatable("kingdom.wakingworld.title", name(center));
    }

    /** Anger the kingdom with a player: guards within reach turn, traders refuse, and the player is told why. */
    public static void offend(ServerLevel level, BlockPos center, Player player, int ticks, String reason) {
        KingdomData data = KingdomData.get(level);
        boolean already = data.isAngry(level, center, player.getUUID());
        data.anger(level, center, player, ticks);
        if (!already) {
            player.displayClientMessage(Component.translatable("kingdom.wakingworld.angry." + reason, name(center)).withStyle(ChatFormatting.RED), false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        // the alarm: every guard within 48 blocks that has no better business comes for the offender
        for (GuardEntity g : level.getEntitiesOfClass(GuardEntity.class, new AABB(player.blockPosition()).inflate(48))) {
            if (g.center().equals(center) && (g.getTarget() == null || !g.getTarget().isAlive())) g.setTarget(player);
        }
    }

    /** Whether a player may open the treasury: the king's leave, or a crown that has fallen. */
    public static boolean mayEnterTreasury(ServerLevel level, BlockPos center, Player player) {
        KingdomData data = KingdomData.get(level);
        return data.isPermitted(center, player.getUUID()) || data.isKingDead(center);
    }

    public static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text, false);
    }
}
