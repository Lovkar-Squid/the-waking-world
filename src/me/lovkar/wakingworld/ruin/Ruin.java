package me.lovkar.wakingworld.ruin;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.RubbleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import me.lovkar.wakingworld.cataclysm.Scars;
import net.minecraft.world.entity.EntityType;

public final class Ruin {
    private Ruin() {
    }

    private static RuinLedger ledger;
    private static FightRecord active;

    public static void begin(RuinLedger l, FightRecord r) {
        ledger = l;
        active = r;
    }

    public static void end() {
        ledger = null;
        active = null;
    }

    public static FightRecord active() {
        return active;
    }

    /** Before a block is changed: remember what stood there (first time only). */
    public static void mark(ServerLevel level, BlockPos pos) {
        if (active != null) {
            active.mark(level, pos);
            if (ledger != null) ledger.setDirty();
        }
        Scars.mark(level, pos);
    }

    /**
     * A block leaves the ground and flies: the spot is remembered and the block becomes rubble
     * that will report where it lands. Same contract as {@link FallingBlockEntity#fall}: the block
     * at pos is cleared (fluids stay), the entity is added to the level.
     */
    public static RubbleEntity fall(ServerLevel level, BlockPos pos, BlockState state) {
        mark(level, pos);
        RubbleEntity fb = new RubbleEntity(WakingWorld.RUBBLE.get(), level);
        fb.setUp(pos, state, active == null ? null : active.id);
        if (active != null) active.rubbleUp();
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(fb);
        return fb;
    }
}
