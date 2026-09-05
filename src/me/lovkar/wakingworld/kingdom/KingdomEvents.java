package me.lovkar.wakingworld.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The treasury is warded: open a chest in it, or break anything in it, without the king's leave
 * and the kingdom is angry with you for a day - the guards come running. The king's leave is a
 * Colossus Heart laid before him; a dead king guards nothing.
 */
public final class KingdomEvents {
    private KingdomEvents() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity)) return;
        KingdomData.Kingdom k = KingdomData.get(level).treasuryAt(pos);
        if (k == null || Kingdoms.mayEnterTreasury(level, k.center, player)) return;
        Kingdoms.offend(level, k.center, player, KingdomData.ANGER_TICKS, "treasury");
    }

    /** Once a second: empty thrones get their successors. */
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 7) Kingdoms.tickSuccessions(level);
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        KingdomData.Kingdom k = KingdomData.get(level).treasuryAt(event.getPos());
        if (k == null || Kingdoms.mayEnterTreasury(level, k.center, player)) return;
        Kingdoms.offend(level, k.center, player, KingdomData.ANGER_TICKS, "treasury");
    }
}
