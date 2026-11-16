package me.lovkar.wwquakeguard;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The only part of the probe that names a MineColonies type, kept apart the way the mod keeps its
 * ColoniesBridge apart: founds one colony with a town hall, as tools/colonytest did.
 */
final class ColonyPart {
    private ColonyPart() {
    }

    /** A colony at {@code center}, its town hall registered so the hall's chunks are claimed. Returns its id. */
    static int found(final ServerLevel level, final BlockPos center) {
        final Player owner = FakePlayerFactory.getMinecraft(level);
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        final IColony colony = IColonyManager.getInstance().createColony(level, center, owner, "Quake Guard Test", "Medieval Oak");
        final Block townHall = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhuttownhall"));
        level.setBlock(center, townHall.defaultBlockState(), 3);
        if (level.getBlockEntity(center) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding hut) {
            colony.getServerBuildingManager().addNewBuilding(hut, level);
        }
        return colony.getID();
    }
}
