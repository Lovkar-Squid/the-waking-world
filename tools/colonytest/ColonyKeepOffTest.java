package me.lovkar.wwcolonytest;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.ColonyUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Founds one colony at the origin on a headless server and prints where its claim reaches, so the
 * Waking World's colony guards can be driven from the console against a real colony.
 */
@Mod("wwcolonytest")
public class ColonyKeepOffTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("wwcolonytest");
    private int tick = 0;
    private static final java.nio.file.Path ORDER = java.nio.file.Path.of("/root/nfserver/colony-at.txt");

    public ColonyKeepOffTest(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    /** Founds a colony where a file says to, so the console can choose the spot after a kingdom is down. */
    private void onTick(final ServerTickEvent.Post event) {
        if (++tick % 20 != 0) return;
        if (!java.nio.file.Files.exists(ORDER)) return;
        int wx = 0, wz = 0;
        try {
            final String[] parts = java.nio.file.Files.readString(ORDER).trim().split("\\s+");
            wx = Integer.parseInt(parts[0]);
            wz = Integer.parseInt(parts[1]);
            java.nio.file.Files.delete(ORDER);
        } catch (Throwable t) {
            LOGGER.error("[wwcolonytest] cannot read the order: {}", t.toString());
            try { java.nio.file.Files.deleteIfExists(ORDER); } catch (Throwable ignored) { }
            return;
        }
        final ServerLevel level = event.getServer().overworld();
        try {
            make(level, wx, wz);
        } catch (Throwable t) {
            LOGGER.error("[wwcolonytest] FAILED {}", t.toString());
        }
    }

    private void make(final ServerLevel level, final int wx, final int wz) {
        final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
        final BlockPos center = new BlockPos(wx, y, wz);
        final Player owner = FakePlayerFactory.getMinecraft(level);
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        final IColony colony = IColonyManager.getInstance().createColony(level, center, owner, "Keep Off Test", "Medieval Oak");
        final Block townHall = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhuttownhall"));
        level.setBlock(center, townHall.defaultBlockState(), 3);
        try {
            if (level.getBlockEntity(center) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding hut) {
                colony.getServerBuildingManager().addNewBuilding(hut, level);
                LOGGER.info("[wwcolonytest] town hall registered");
            }
        } catch (Throwable t) {
            LOGGER.warn("[wwcolonytest] town hall not registered: {}", t.toString());
        }
        LOGGER.info("[wwcolonytest] colony {} '{}' at {} {} {}", colony.getID(), colony.getName(), center.getX(), center.getY(), center.getZ());
        // how far the claim reaches, in chunks, along +X
        final int c0x = center.getX() >> 4, c0z = center.getZ() >> 4;
        int minX = c0x, maxX = c0x, minZ = c0z, maxZ = c0z;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                var chunk = level.getChunkSource().getChunkNow(c0x + dx, c0z + dz);
                if (chunk == null) continue;
                boolean owned = ColonyUtils.getOwningColony(chunk) != 0 || !ColonyUtils.getStaticClaims(chunk).isEmpty();
                if (!owned) continue;
                minX = Math.min(minX, c0x + dx); maxX = Math.max(maxX, c0x + dx);
                minZ = Math.min(minZ, c0z + dz); maxZ = Math.max(maxZ, c0z + dz);
            }
        }
        LOGGER.info("[wwcolonytest] READY colony at {} {} {}; claim x {}..{}, z {}..{} (blocks x {}..{}, z {}..{})",
                center.getX(), center.getY(), center.getZ(), minX, maxX, minZ, maxZ,
                minX * 16, maxX * 16 + 15, minZ * 16, maxZ * 16 + 15);
    }
}
