package me.lovkar.wwspawngate;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.WakingSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does the switch switch, and does a colony's pond stay empty?
 *
 * <p>Matchu Centric reported Drowned Keepers spawning in the water inside a colony and killing the
 * colonists, with nothing to turn off but the whole dead world. 0.3.0-beta.5 gives the three
 * wanderers a switch of their own ({@code spawns.naturalSpawns}, and one line per creature) and
 * pushes their natural spawns {@code spawns.spawnColonyMargin} blocks further out from a colony
 * than the rest of the mod's keep-off.</p>
 *
 * <p>This probe digs two covered ponds - one inside a real colony, one six hundred blocks away -
 * sets the clock to midnight and asks MineCraft's own {@code SpawnPlacements} whether a Keeper may
 * stand there. It reads the config rather than assuming it, so the same jar proves both halves:
 * the rig runs it once with the switch on and once with it off.</p>
 */
@Mod("wwspawngate")
public class SpawnGate {
    private static final Logger LOGGER = LoggerFactory.getLogger("wwspawngate");

    private int tick;
    private boolean done;
    private boolean dug;
    private int bad;
    private int checks;
    private BlockPos townPond;
    private BlockPos farPond;

    public SpawnGate(final IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTick(final ServerTickEvent.Post event) {
        if (done) {
            return;
        }
        tick++;
        final ServerLevel level = event.getServer().overworld();
        if (tick == 60 && !dug) {
            dug = true;
            try {
                dig(level);
            } catch (final Throwable t) {
                LOGGER.error("[wwspawngate] could not set the scene", t);
                LOGGER.info("[wwspawngate] RESULT FAILED 98");
                done = true;
            }
            return;                          // the light engine needs a moment on a sealed box
        }
        if (tick != 160 || !dug) {
            return;
        }
        done = true;
        try {
            run(level);
            LOGGER.info(bad == 0
                    ? "[wwspawngate] RESULT ok (" + checks + " checks)"
                    : "[wwspawngate] RESULT FAILED " + bad + " of " + checks);
        } catch (final Throwable t) {
            LOGGER.error("[wwspawngate] blew up", t);
            LOGGER.info("[wwspawngate] RESULT FAILED 99");
        }
    }

    /** The scene: midnight, a colony at the origin, a pond in it and a pond far away. */
    private void dig(final ServerLevel level) {
        // midnight, set here rather than from the console, so the probe does not depend on when the
        // run script gets round to it: a Keeper only stands in the water at night.
        level.setDayTime(18000L);
        final BlockPos centre = new BlockPos(0, level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0), 0);
        final Player owner = FakePlayerFactory.getMinecraft(level);
        owner.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
        final IColony colony = IColonyManager.getInstance().createColony(level, centre, owner, "Spawn Gate", "Medieval Oak");
        final Block townHall = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhuttownhall"));
        level.setBlock(centre, townHall.defaultBlockState(), 3);
        try {
            if (level.getBlockEntity(centre) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding hut) {
                colony.getServerBuildingManager().addNewBuilding(hut, level);
            }
        } catch (final Throwable t) {
            LOGGER.warn("[wwspawngate] town hall not registered: {}", t.toString());
        }
        LOGGER.info("[wwspawngate] colony {} at {} {} {}", colony.getID(), centre.getX(), centre.getY(), centre.getZ());
        townPond = pond(level, 6, 6);
        farPond = pond(level, 600, 600);
        LOGGER.info("[wwspawngate] ponds: colony {} / far {}", townPond, farPond);
    }

    private void run(final ServerLevel level) {
        final boolean on = WakingConfig.naturalSpawns();
        LOGGER.info("[wwspawngate] config: ruins={} naturalSpawns={} thralls={} wraiths={} keepers={} colonyMargin={} protectColonies={} buffer={}",
                WakingConfig.ruins(), on, WakingConfig.spawnThralls(), WakingConfig.spawnWraiths(),
                WakingConfig.spawnKeepers(), WakingConfig.spawnColonyMargin(),
                WakingConfig.protectColonies(), WakingConfig.colonyBuffer());

        final EntityType<?> keeper = WakingWorld.DROWNED_KEEPER.get();
        final EntityType<?> thrall = WakingWorld.STONE_THRALL.get();
        final EntityType<?> wraith = WakingWorld.EMBER_WRAITH.get();

        want("the Keeper's own line and the master switch agree",
                WakingSpawns.allowedByConfig(keeper)
                        == (WakingConfig.ruins() && on && WakingConfig.spawnKeepers()));
        want("so do the Thrall's and the Wraith's",
                WakingSpawns.allowedByConfig(thrall) == (WakingConfig.ruins() && on && WakingConfig.spawnThralls())
                        && WakingSpawns.allowedByConfig(wraith) == (WakingConfig.ruins() && on && WakingConfig.spawnWraiths()));

        LOGGER.info("[wwspawngate] far pond: night={} water={} under={} light={} difficulty={}",
                level.isNight(), level.getFluidState(farPond).is(net.minecraft.tags.FluidTags.WATER),
                level.getFluidState(farPond.below()).is(net.minecraft.tags.FluidTags.WATER),
                level.getMaxLocalRawBrightness(farPond), level.getDifficulty());

        want("the far pond is dark, so the answer is the switch and not a dice roll",
                level.getMaxLocalRawBrightness(farPond) == 0);
        want("the colony's own pond is inside the keep-off", WakingSpawns.colony(level, townPond));
        want("six hundred blocks away is not", !WakingSpawns.colony(level, farPond));

        final boolean farNatural = SpawnPlacements.checkSpawnRules(keeper, level, MobSpawnType.NATURAL, farPond, level.random);
        final boolean townNatural = SpawnPlacements.checkSpawnRules(keeper, level, MobSpawnType.NATURAL, townPond, level.random);
        final boolean townSpawner = SpawnPlacements.checkSpawnRules(keeper, level, MobSpawnType.SPAWNER, townPond, level.random);
        LOGGER.info("[wwspawngate] keeper may stand: far={} town={} (spawner in town={})", farNatural, townNatural, townSpawner);

        want("out in the country the switch decides it: " + (on ? "on, so yes" : "off, so no"), farNatural == on);
        want("in the colony's pond, never", !townNatural);
        want("a spawner, an egg or a command is not bound by any of it", townSpawner);
    }

    /** A covered three-by-three pool of water at ground level, so darkness is not in question. */
    private BlockPos pond(final ServerLevel level, final int x, final int z) {
        for (int cx = (x >> 4) - 1; cx <= (x >> 4) + 1; cx++) {
            for (int cz = (z >> 4) - 1; cz <= (z >> 4) + 1; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        level.getChunk(x >> 4, z >> 4);            // generated and in memory before anything is asked of it
        final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        // A sealed stone box with water in it. Sealed because MineCraft's own darkness test is a
        // coin toss at light 4 (i <= random.nextInt(8)) and a check that passes four times in five
        // is not a check; at light 0 it is settled.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    final boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (dy == 0 || dy == -1);
                    level.setBlock(new BlockPos(x + dx, y + dy, z + dz),
                            (core ? Blocks.WATER : Blocks.STONE).defaultBlockState(), 3);
                }
            }
        }
        return new BlockPos(x, y, z);
    }

    private void want(final String what, final boolean ok) {
        checks++;
        if (!ok) {
            bad++;
        }
        LOGGER.info("[wwspawngate] {}: {}", what, ok ? "yes" : "NO");
    }
}
