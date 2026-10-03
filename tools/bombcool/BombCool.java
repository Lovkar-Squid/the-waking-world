package me.lovkar.wwbombcool;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.BombLava;
import me.lovkar.wakingworld.cataclysm.MeteorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless check for volcanoBombsCool: six real molten bombs (MeteorEntity.hurl, the volcano's own
 * throw) are dropped onto open ground; their lava must be remembered, and once
 * volcanoBombCoolSeconds have passed every source must have set into basalt or blackstone and the
 * flow it fed must have dried up. Prints CHECK lines and RESULT, then stops the server.
 */
@Mod("wwbombcool")
public class BombCool {
    private static final Logger LOG = LoggerFactory.getLogger("wwbombcool");
    private static final String TAG = "[bombcool] ";
    private static final int R = 40;

    private int tick;
    private int stage;
    private int stageTick;
    private int y;
    private int failures;
    private int poured;
    private int landed;

    public BombCool(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTick(final ServerTickEvent.Post e) {
        tick++;
        final ServerLevel level = e.getServer().overworld();
        try {
            switch (stage) {
                case 0 -> {
                    if (tick < 60) return;
                    final MinecraftServer server = level.getServer();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
                    level.setDayTime(1000);
                    for (int cx = -4; cx <= 4; cx++) {
                        for (int cz = -4; cz <= 4; cz++) {
                            level.setChunkForced(cx, cz, true);
                        }
                    }
                    y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
                    LOG.info(TAG + "surface y={}, volcanoBombsCool={}, volcanoBombCoolSeconds={}", y, WakingConfig.bombsCool(),
                            WakingConfig.bombCoolSeconds());
                    for (int i = 0; i < 6; i++) {
                        final MeteorEntity bomb = WakingWorld.METEOR.get().create(level);
                        bomb.hurl(new Vec3(-25 + i * 10 + 0.5, y + 30, 0.5), new Vec3(0, -0.2, 0));
                        level.addFreshEntity(bomb);
                    }
                    LOG.info(TAG + "six molten bombs thrown");
                    next();
                }
                case 1 -> {
                    // wait until they have landed
                    if (level.getEntities(WakingWorld.METEOR.get(), m -> true).isEmpty() || tick - stageTick > 600) {
                        poured = BombLava.get(level).waiting();
                        final int lava = count(level, true);
                        landed = lava;
                        check("bombs landed and their lava is remembered, each source once", poured > 0 && lava > 0 && poured == lava,
                                "remembered " + poured + ", lava sources on the ground " + lava);
                        next();
                    }
                }
                case 2 -> {
                    final int wait = (WakingConfig.bombCoolSeconds() + 10) * 20;
                    if (tick - stageTick == 200) {
                        check("still molten before its time", count(level, true) > 0 && BombLava.get(level).waiting() == poured,
                                "lava sources " + count(level, true) + ", remembered " + BombLava.get(level).waiting());
                    }
                    if (tick - stageTick >= wait) {
                        final int sources = count(level, true);
                        final int flowing = count(level, false);
                        final int rock = rock(level);
                        check("every source has set", sources == 0 && BombLava.get(level).waiting() == 0,
                                "lava sources left " + sources + ", remembered " + BombLava.get(level).waiting());
                        check("the flow has dried up", flowing == 0, "flowing lava left " + flowing);
                        check("set into basalt or blackstone", rock >= landed, "basalt/blackstone blocks " + rock + " for " + landed + " sources");
                        LOG.info(TAG + "RESULT bombcool: {}", failures == 0 ? "PASS" : "FAIL");
                        stage = 99;
                        level.getServer().execute(() -> level.getServer().halt(false));
                    }
                }
                default -> {
                }
            }
        } catch (final Throwable t) {
            LOG.error(TAG + "EXCEPTION", t);
            LOG.info(TAG + "RESULT bombcool: FAIL");
            stage = 99;
            level.getServer().execute(() -> level.getServer().halt(false));
        }
    }

    private void next() {
        stage++;
        stageTick = tick;
    }

    private void check(final String name, final boolean ok, final String detail) {
        if (!ok) failures++;
        LOG.info(TAG + "CHECK {}: {} {}", name, ok ? "OK" : "FAIL", detail);
    }

    /** Lava in the test field: sources, or flowing lava when {@code sources} is false. */
    private int count(final ServerLevel level, final boolean sources) {
        int n = 0;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int dy = -3; dy <= 4; dy++) {
                    final BlockState s = level.getBlockState(new BlockPos(x, y + dy, z));
                    if (s.is(Blocks.LAVA) && s.getFluidState().isSource() == sources) n++;
                }
            }
        }
        return n;
    }

    private int rock(final ServerLevel level) {
        int n = 0;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int dy = -3; dy <= 4; dy++) {
                    final BlockState s = level.getBlockState(new BlockPos(x, y + dy, z));
                    if (s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE)) n++;
                }
            }
        }
        return n;
    }
}
