package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.ruin.FightRecord;
import me.lovkar.wakingworld.ruin.RuinLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A block a colossus sent flying: vanilla's falling block in every way (it renders, hurts and
 * lands the same), except that it knows which fight it belongs to and, when it comes to rest as a
 * block again, tells that fight's record where - so an Hourglass of Restoration can take it away.
 */
public class RubbleEntity extends FallingBlockEntity {
    private UUID fight;
    private boolean reported;
    /** What the rubble may land in this tick (probed before the move): plants, snow, water - remembered as the 'before'. */
    private final java.util.Map<BlockPos, BlockState> probe = new java.util.HashMap<>();
    /** Debug counters: landings that could not be matched to a placed block / rubble with no fight. */
    public static int unmatched, orphans;

    public RubbleEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    /** Sets the block, the start position and the owning fight - what vanilla's private constructor does, plus the fight. */
    public void setUp(BlockPos pos, BlockState state, UUID fight) {
        CompoundTag tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(state));
        tag.putInt("Time", 1);
        tag.putBoolean("DropItem", false);
        tag.putBoolean("HurtEntities", false);
        tag.putFloat("FallHurtAmount", 0);
        tag.putInt("FallHurtMax", 0);
        this.readAdditionalSaveData(tag);
        this.blocksBuilding = true;
        this.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        this.setDeltaMovement(0, 0, 0);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setStartPos(pos);
        this.fight = fight;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isRemoved()) {
            // where might it be after this tick's move? what is there now is what it may replace
            probe.clear();
            net.minecraft.world.phys.Vec3 m = this.getDeltaMovement();
            int x0 = (int) Math.floor(this.getX()), x1 = (int) Math.floor(this.getX() + m.x);
            int z0 = (int) Math.floor(this.getZ()), z1 = (int) Math.floor(this.getZ() + m.z);
            int yTop = (int) Math.floor(this.getY()) + 1, yBot = (int) Math.floor(this.getY() + m.y - 0.1) - 1;
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                    for (int y = yBot; y <= yTop; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        probe.put(p, this.level().getBlockState(p));
                    }
        }
        super.tick();
        if (!reported && this.isRemoved() && this.level() instanceof ServerLevel server) {
            reported = true;
            if (fight == null) {
                orphans++;
                return;
            }
            RuinLedger ledger = RuinLedger.get(server);
            FightRecord r = ledger.record(fight);
            if (r != null) {
                BlockPos at = this.blockPosition();
                if (server.getBlockState(at) == this.getBlockState()) {
                    BlockState was = probe.get(at);
                    if (was == this.getBlockState()) was = null;
                    r.placed(server, at, was);
                } else {
                    unmatched++;
                }
                r.rubbleDown();
                ledger.setDirty();
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // unloaded or otherwise gone without landing: it no longer counts as in flight
        if (!reported && fight != null && this.level() instanceof ServerLevel server && reason != RemovalReason.DISCARDED) {
            reported = true;
            FightRecord r = RuinLedger.get(server).record(fight);
            if (r != null) r.rubbleDown();
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (fight != null) tag.putUUID("Fight", fight);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Fight")) fight = tag.getUUID("Fight");
    }
}
