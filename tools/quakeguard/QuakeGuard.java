package me.lovkar.wwquakeguard;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.cataclysm.Earthquake;
import me.lovkar.wakingworld.cataclysm.Weather;
import me.lovkar.wakingworld.compat.Colonies;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless check for earthquakeSafeRadius ("adding some protection from earthquakes will be good
 * too"). On the flat rig the world spawn is set at S and a quake is run through its real calls -
 * Earthquake.shake, Earthquake.second over the envelope for the configured seconds, then climax and
 * its blight - with the epicentre 30 blocks from S and the fault aimed through S, so it starts
 * inside the safe radius, runs across it and comes out the far side. Then:
 * <ul>
 *   <li>no block within the radius of S may have changed (a snapshot of the ground before and
 *       after, wheat on farmland included for the blight), while the quake must still have opened
 *       ground outside it and blighted a field outside it;</li>
 *   <li>villagers standing inside the radius are never shoved by second(), villagers outside are;</li>
 *   <li>the wave (ripple), which only moves blocks where a player can see it and so never runs on
 *       a rig without players, is run through its probe seam across the edge of the radius: no
 *       block thrown inside, some outside;</li>
 *   <li>the guard itself: off at radius 0, the world spawn found as a home, a bed found through
 *       the players seam (fake players are not in level.players()) and one in another dimension
 *       ignored;</li>
 *   <li>the scheduler's site for a quake near a player standing at S, asked with many seeds, is
 *       never inside the radius.</li>
 * </ul>
 * Then MineColonies, which rigmods.txt puts on the server: a colony is founded far from S and a
 * quake is centred 20 blocks outside its land (the claim and colonyBuffer round it) with the fault
 * aimed at the town hall. No block on its land may change, a villager standing on it is never
 * shoved while one outside is, and the wave across its edge throws nothing onto it. Prints CHECK
 * lines and RESULT, then stops the server.
 */
@Mod("wwquakeguard")
public class QuakeGuard {
    private static final Logger LOG = LoggerFactory.getLogger("wwquakeguard");
    private static final String TAG = "[quakeguard] ";
    /** The world spawn the test sets, well away from the generator's own. */
    private static final int SX = 160, SZ = 32;
    private static final int RADIUS = 48;
    /** The epicentre, 30 blocks east of S: inside the radius, so the fault starts in sheltered ground. */
    private static final int EX = SX + 30, EZ = SZ;
    /** The snapshot: RADIUS + 4 round S, and these heights round the surface. */
    private static final int BOX = RADIUS + 4, BELOW = 6, ABOVE = 4;
    /** Where opened ground is counted outside the radius: the fault walks west from E for a long way. */
    private static final int WEST = 230, EAST = 120, SIDE = 110;
    private static final int PICKS = 200, PICKS_PER_TICK = 5;
    /** The colony's town hall, a long way from S and from the first quake's fault. */
    private static final int CX = -480, CZ = 32;
    /** The colony quake: this far outside the colony's land, and the snapshot this far into it. */
    private static final int OUT = 20, INTO = 110, WIDE = 60;

    private int tick;
    private int stage;
    private int stageTick;
    private int failures;
    private int y0;
    private BlockState[] before;
    private final List<Villager> homeFolk = new ArrayList<>();
    private final List<Villager> farFolk = new ArrayList<>();
    private int shovesIn;
    private int shovesOut;
    private int mainSeconds;
    private int quakeTicks;
    private int total;
    private long shakeSeed;
    private int asked;
    private int picks;
    private int picksInside;
    private double closest = Double.MAX_VALUE;
    private FakePlayer fake;
    /** The first block east of the town hall that is no longer the colony's land. */
    private int edgeX;
    private BlockState[] colonyBefore;
    private boolean[] colonyLand;
    private Villager colonist;
    private Villager stranger;
    private int colonistShoves;
    private int strangerShoves;

    public QuakeGuard(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTick(final ServerTickEvent.Post e) {
        tick++;
        final ServerLevel level = e.getServer().overworld();
        try {
            switch (stage) {
                case 0 -> {
                    if (tick < 60) return;
                    setUp(level);
                    next();
                }
                case 1 -> {
                    // the villagers land and settle before anything shakes
                    if (tick - stageTick < 60) return;
                    int grounded = 0;
                    for (final Villager c : all()) if (c.onGround()) grounded++;
                    LOG.info(TAG + "{} of {} villagers on the ground", grounded, all().size());
                    total = WakingConfig.earthquakeSeconds() * 20;
                    quakeTicks = total;
                    final int opened = Earthquake.shake(level, new Vec3(EX + 0.5, y0, EZ + 0.5), WakingConfig.earthquakeSeconds(),
                            RandomSource.create(shakeSeed));
                    LOG.info(TAG + "the quake starts at {} {} {} ({} blocks opened at once, {} s of shaking)", EX, y0, EZ, opened,
                            WakingConfig.earthquakeSeconds());
                    next();
                }
                case 2 -> {
                    // once a second, as Weather runs it
                    if ((tick - stageTick) % 20 != 0) return;
                    quakeTicks -= 20;
                    final float progress = Math.min(1.0F, 1.0F - quakeTicks / (float) total);
                    final float strength = Earthquake.envelope(progress);
                    second(level, strength);
                    if (quakeTicks <= 0) {
                        Earthquake.climax(level, new Vec3(EX + 0.5, y0, EZ + 0.5), level.random);
                        LOG.info(TAG + "the ground settles");
                        next();
                    }
                }
                case 3 -> {
                    // everything thrown has come down
                    if (tick - stageTick < 120) return;
                    afterQuake(level);
                    next();
                }
                case 4 -> {
                    ripple(level);
                    next();
                }
                case 5 -> {
                    guard(level);
                    fake = FakePlayerFactory.getMinecraft(level);
                    fake.setPos(SX + 0.5, y0, SZ + 0.5);
                    asked = 0;
                    next();
                }
                case 6 -> {
                    for (int n = 0; n < PICKS_PER_TICK && asked < PICKS; n++, asked++) {
                        final Vec3 site = Weather.quakeSite(level, fake, RandomSource.create(9000L + asked));
                        if (site == null) continue;
                        picks++;
                        final BlockPos at = BlockPos.containing(site);
                        closest = Math.min(closest, Math.sqrt(d2(at.getX(), at.getZ())));
                        if (sheltered(at.getX(), at.getZ())) picksInside++;
                    }
                    if (asked < PICKS) return;
                    check("the scheduler never centres a quake inside the radius", picksInside == 0 && picks >= PICKS / 2,
                            "asked " + PICKS + " with a player standing at S: sites " + picks + ", skipped " + (PICKS - picks)
                                    + ", inside the radius " + picksInside + ", closest " + Math.round(closest) + " from S; at least "
                                    + PICKS / 2 + " sites wanted");
                    final Vec3 from = new Vec3(SX + 0.5, y0, SZ + 0.5);
                    final Vec3 plain = Cataclysms.openSite(level, from, RandomSource.create(5), 30, 90);
                    final Vec3 refused = Cataclysms.openSite(level, from, RandomSource.create(5), 30, 90, p -> true);
                    check("with everything refused there is no site, and the plain search still answers", refused == null && plain != null,
                            "everything refused: " + refused + "; nothing refused: " + plain);
                    next();
                }
                case 7 -> {
                    final int id = ColonyPart.found(level, new BlockPos(CX, y0, CZ));
                    LOG.info(TAG + "colony {} founded at {} {} {}", id, CX, y0, CZ);
                    next();
                }
                case 8 -> {
                    // its claims settle, then the ground beside it is measured and photographed
                    if (tick - stageTick < 100) return;
                    if (!colonySetUp(level)) {
                        stage = 50;
                        return;
                    }
                    next();
                }
                case 9 -> {
                    if (tick - stageTick < 60) return;
                    quakeTicks = total;
                    Earthquake.shake(level, colonyQuake(), WakingConfig.earthquakeSeconds(), RandomSource.create(shakeSeed));
                    LOG.info(TAG + "the colony quake starts at {} {} {}, {} blocks outside the colony's land", edgeX + OUT, y0, CZ, OUT);
                    next();
                }
                case 10 -> {
                    if ((tick - stageTick) % 20 != 0) return;
                    quakeTicks -= 20;
                    final float strength = Earthquake.envelope(Math.min(1.0F, 1.0F - quakeTicks / (float) total));
                    final boolean[] grounded = {colonist.onGround(), stranger.onGround()};
                    final double[] dy = {colonist.getDeltaMovement().y, stranger.getDeltaMovement().y};
                    Earthquake.second(level, colonyQuake(), strength);
                    if (grounded[0] && colonist.getDeltaMovement().y - dy[0] > 0.01) colonistShoves++;
                    if (grounded[1] && stranger.getDeltaMovement().y - dy[1] > 0.01) strangerShoves++;
                    if (quakeTicks <= 0) {
                        Earthquake.climax(level, colonyQuake(), level.random);
                        next();
                    }
                }
                case 11 -> {
                    if (tick - stageTick < 120) return;
                    afterColonyQuake(level);
                    stage = 50;
                }
                case 50 -> {
                    LOG.info(TAG + "RESULT quakeguard: {}", failures == 0 ? "PASS" : "FAIL");
                    stage = 99;
                    level.getServer().execute(() -> level.getServer().halt(false));
                }
                default -> {
                }
            }
        } catch (final Throwable t) {
            LOG.error(TAG + "EXCEPTION", t);
            LOG.info(TAG + "RESULT quakeguard: FAIL");
            stage = 99;
            level.getServer().execute(() -> level.getServer().halt(false));
        }
    }

    private Vec3 colonyQuake() {
        return new Vec3(edgeX + OUT + 0.5, y0, CZ + 0.5);
    }

    /**
     * The colony as the mod sees it: its land must be there, and the town hall on it. Then where its
     * land ends east of the hall, a villager each side of that edge, and the ground photographed.
     */
    private boolean colonySetUp(final ServerLevel level) {
        final BlockPos hall = new BlockPos(CX, y0, CZ);
        int claimedChunks = 0;
        for (int cx = (CX >> 4) - 12; cx <= (CX >> 4) + 12; cx++) {
            for (int cz = (CZ >> 4) - 12; cz <= (CZ >> 4) + 12; cz++) {
                if (Colonies.claimed(level, new BlockPos(cx << 4, y0, cz << 4))) claimedChunks++;
            }
        }
        edgeX = CX;
        while (edgeX < CX + 400 && Colonies.keepOff(level, new BlockPos(edgeX, y0, CZ))) edgeX++;
        final boolean seen = Colonies.active() && Colonies.keepOff(level, hall) && claimedChunks > 0;
        check("MineColonies is running and the mod sees the colony's land", seen,
                "active " + Colonies.active() + ", the hall kept off " + Colonies.keepOff(level, hall) + ", claimed chunks " + claimedChunks
                        + ", the land ends " + (edgeX - CX) + " blocks east of the hall");
        if (!seen) return false;
        colonist = villagerAt(level, edgeX - 12, CZ + 18);
        stranger = villagerAt(level, edgeX + OUT + 10, CZ + 18);
        if (!Colonies.keepOff(level, colonist.blockPosition()) || Colonies.keepOff(level, stranger.blockPosition())) {
            throw new IllegalStateException("the colony's villagers are on the wrong side of its edge");
        }
        final int side = INTO + OUT + 30 + 1;
        colonyLand = new boolean[side * (WIDE * 2 + 1)];
        int i = 0;
        for (int x = edgeX - INTO; x <= edgeX + OUT + 30; x++) {
            for (int z = CZ - WIDE; z <= CZ + WIDE; z++) colonyLand[i++] = Colonies.keepOff(level, new BlockPos(x, y0, z));
        }
        colonyBefore = snapshot(level, edgeX - INTO, edgeX + OUT + 30, CZ - WIDE, CZ + WIDE);
        return true;
    }

    /** The colony's land compared with its photograph, and the wave run across its edge. */
    private void afterColonyQuake(final ServerLevel level) {
        final BlockState[] after = snapshot(level, edgeX - INTO, edgeX + OUT + 30, CZ - WIDE, CZ + WIDE);
        int changedOn = 0, opened = 0, i = 0, column = 0;
        String first = null;
        for (int x = edgeX - INTO; x <= edgeX + OUT + 30; x++) {
            for (int z = CZ - WIDE; z <= CZ + WIDE; z++, column++) {
                for (int y = y0 - BELOW; y <= y0 + ABOVE; y++, i++) {
                    if (colonyBefore[i] == after[i]) continue;
                    if (colonyLand[column]) {
                        changedOn++;
                        if (first == null) first = x + " " + y + " " + z + ": " + colonyBefore[i] + " -> " + after[i];
                    } else if (y == y0 - 1 && after[i].isAir()) {
                        opened++;
                    }
                }
            }
        }
        check("a quake beside a colony changes no block on its land", changedOn == 0 && opened > 0,
                "changed on the colony's land " + changedOn + (first == null ? "" : " (first " + first + ")")
                        + "; columns opened beside it " + opened);
        check("a villager on the colony's land is not shoved, one beside it is", colonistShoves == 0 && strangerShoves > 0,
                "on the land " + colonistShoves + ", beside it " + strangerShoves);
        Earthquake.rippleUnwatched(level, colonyQuake(), OUT + 8, 1.0F, RandomSource.create(78));
        int on = 0, off = 0;
        final AABB box = new AABB(edgeX - INTO, y0 - 10, CZ - WIDE, edgeX + OUT + 60, y0 + 10, CZ + WIDE);
        for (final FallingBlockEntity fb : level.getEntitiesOfClass(FallingBlockEntity.class, box)) {
            if (Colonies.keepOff(level, fb.blockPosition())) on++;
            else off++;
        }
        check("the wave throws nothing onto the colony's land", on == 0 && off > 0, "onto the land " + on + ", beside it " + off);
    }

    /** The world spawn at S, the ground kept loaded, two wheat fields, the villagers, and the snapshot. */
    private void setUp(final ServerLevel level) {
        final MinecraftServer server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        // nothing grows, spreads or dries out while it is being watched: only the quake changes blocks
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        level.setDayTime(1000);
        for (int cx = (SX - WEST - 16) >> 4; cx <= (SX + EAST + 16) >> 4; cx++) {
            for (int cz = (SZ - SIDE - 16) >> 4; cz <= (SZ + SIDE + 16) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        for (int cx = (CX - 176) >> 4; cx <= (CX + 176) >> 4; cx++) {
            for (int cz = (CZ - 96) >> 4; cz <= (CZ + 96) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        y0 = Cataclysms.surface(level, SX, SZ).getY();
        level.setDefaultSpawnPos(new BlockPos(SX, y0, SZ), 0.0F);
        LOG.info(TAG + "surface y={}, world spawn set to {}, earthquakeSafeRadius={}", y0, level.getSharedSpawnPos(),
                WakingConfig.earthquakeSafeRadius());
        check("the server config reached the mod", WakingConfig.earthquakeSafeRadius() == RADIUS && level.getSharedSpawnPos().getX() == SX
                && level.getSharedSpawnPos().getZ() == SZ, "earthquakeSafeRadius " + WakingConfig.earthquakeSafeRadius()
                + ", world spawn " + level.getSharedSpawnPos());

        // a field inside the radius, in reach of the blight (34 round E), and one outside it
        final BlockState wheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        for (int x = SX + 15; x <= SX + 21; x++) {
            for (int z = SZ + 8; z <= SZ + 14; z++) field(level, x, z, wheat);
        }
        for (int x = SX + 50; x <= SX + 56; x++) {
            for (int z = SZ - 6; z <= SZ + 6; z++) field(level, x, z, wheat);
        }
        // villagers inside the radius and outside it, all within the 90 blocks the shove reaches. Not cows:
        // the rig runs with spawn-animals off, and a server like that discards every animal on its first tick
        for (final int[] at : new int[][] {{8, -12}, {-12, 8}, {16, -20}, {20, 24}}) homeFolk.add(villager(level, at));
        for (final int[] at : new int[][] {{64, 18}, {64, -18}, {76, 0}, {58, 40}}) farFolk.add(villager(level, at));
        for (final Villager c : homeFolk) {
            if (!sheltered(c.getBlockX(), c.getBlockZ())) throw new IllegalStateException("a villager meant for inside is outside: " + c.blockPosition());
        }
        for (final Villager c : farFolk) {
            if (sheltered(c.getBlockX(), c.getBlockZ())) throw new IllegalStateException("a villager meant for outside is inside: " + c.blockPosition());
        }

        // the fault aimed west, through S
        shakeSeed = seedFor(Math.PI, 0.25);
        before = snapshot(level);
    }

    /** The first seed whose fault sets off within {@code within} of {@code heading} (Earthquake.shake's first draw). */
    private static long seedFor(final double heading, final double within) {
        for (long seed = 1; ; seed++) {
            final double h = RandomSource.create(seed).nextDouble() * Math.PI * 2;
            if (Math.abs(h - heading) < within) {
                LOG.info(TAG + "seed {}: the fault sets off at {} rad", seed, String.format("%.2f", h));
                return seed;
            }
        }
    }

    private static void field(final ServerLevel level, final int x, final int z, final BlockState crop) {
        final int y = Cataclysms.surface(level, x, z).getY();
        level.setBlock(new BlockPos(x, y - 1, z), Blocks.FARMLAND.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, y, z), crop, 2);
    }

    /**
     * A villager that stays where it is put - a colonist as far as the quake can tell. Not NoAI: a
     * mob without AI has no physics in 1.21 either, so it never stands on the ground and nothing
     * shoves it. One that cannot walk still falls, lands and can be thrown.
     */
    private Villager villager(final ServerLevel level, final int[] off) {
        return villagerAt(level, SX + off[0], SZ + off[1]);
    }

    private Villager villagerAt(final ServerLevel level, final int x, final int z) {
        final Villager c = EntityType.VILLAGER.create(level);
        c.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        c.setPersistenceRequired();
        c.setPos(x + 0.5, y0 + 0.2, z + 0.5);
        level.addFreshEntity(c);
        return c;
    }

    private List<Villager> all() {
        final List<Villager> all = new ArrayList<>(homeFolk);
        all.addAll(farFolk);
        return all;
    }

    /** One second of the quake, with the villagers' vertical speed read on both sides of it. */
    private void second(final ServerLevel level, final float strength) {
        final List<Villager> folk = all();
        final boolean[] grounded = new boolean[folk.size()];
        final double[] dy = new double[folk.size()];
        for (int i = 0; i < folk.size(); i++) {
            grounded[i] = folk.get(i).onGround();
            dy[i] = folk.get(i).getDeltaMovement().y;
        }
        Earthquake.second(level, new Vec3(EX + 0.5, y0, EZ + 0.5), strength);
        if (strength >= 0.9F) mainSeconds++;
        for (int i = 0; i < folk.size(); i++) {
            if (!grounded[i]) continue;
            final boolean shoved = folk.get(i).getDeltaMovement().y - dy[i] > 0.01;
            if (!shoved) continue;
            if (i < homeFolk.size()) shovesIn++;
            else shovesOut++;
        }
    }

    /** The ground compared with the snapshot, and the quake's work outside the radius counted. */
    private void afterQuake(final ServerLevel level) {
        final BlockState[] after = snapshot(level);
        int changedIn = 0, changedRing = 0;
        // what kind of change, so a failure says which path let it through
        int ploughed = 0, landed = 0;
        String first = null;
        int i = 0;
        for (int x = SX - BOX; x <= SX + BOX; x++) {
            for (int z = SZ - BOX; z <= SZ + BOX; z++) {
                for (int y = y0 - BELOW; y <= y0 + ABOVE; y++, i++) {
                    if (before[i] == after[i]) continue;
                    if (sheltered(x, z)) {
                        changedIn++;
                        if (before[i].is(Blocks.FARMLAND)) ploughed++;               // the blight
                        else if (y >= y0 && before[i].isAir()) landed++;             // something thrown, come down here
                        if (first == null) first = x + " " + y + " " + z + ": " + before[i] + " -> " + after[i];
                    } else {
                        changedRing++;
                    }
                }
            }
        }
        // opened ground: columns outside the radius whose top block has gone
        int opened = 0;
        for (int x = SX - WEST; x <= SX + EAST; x++) {
            for (int z = SZ - SIDE; z <= SZ + SIDE; z++) {
                if (sheltered(x, z)) continue;
                if (level.getBlockState(new BlockPos(x, y0 - 1, z)).isAir()) opened++;
            }
        }
        int blighted = 0;
        for (int x = SX + 50; x <= SX + 56; x++) {
            for (int z = SZ - 6; z <= SZ + 6; z++) {
                if (!level.getBlockState(new BlockPos(x, y0 - 1, z)).is(Blocks.FARMLAND)) blighted++;
            }
        }
        check("no block within the radius changed", changedIn == 0,
                "changed inside " + changedIn + " (farmland ploughed " + ploughed + ", thrown blocks landed " + landed
                        + (first == null ? "" : "; first " + first) + "); changed in the box outside it " + changedRing);
        check("the quake still opened ground outside the radius", opened > 0, "opened columns outside " + opened);
        check("the blight still reached the field outside the radius", blighted > 0, "farmland ploughed under " + blighted + " of 91");
        check("no villager inside the radius was shoved, and villagers outside were", shovesIn == 0 && shovesOut > 0,
                "shoves inside " + shovesIn + ", outside " + shovesOut + " (" + mainSeconds + " seconds of the main shock)");
    }

    /** The wave across the edge of the radius, through its probe seam: a ring of 30 round E passes through S. */
    private void ripple(final ServerLevel level) {
        final int thrown = Earthquake.rippleUnwatched(level, new Vec3(EX + 0.5, y0, EZ + 0.5), 30, 1.0F, RandomSource.create(77));
        int in = 0, out = 0;
        final AABB box = new AABB(SX - 100, y0 - 10, SZ - 100, SX + 100, y0 + 10, SZ + 100);
        for (final FallingBlockEntity fb : level.getEntitiesOfClass(FallingBlockEntity.class, box)) {
            if (sheltered(fb.getBlockX(), fb.getBlockZ())) in++;
            else out++;
        }
        check("the wave throws no block inside the radius and still throws outside it", in == 0 && out > 0,
                "thrown " + thrown + ": inside " + in + ", outside " + out);
    }

    /** The guard on its own: radius 0, the world spawn, beds through the players seam. */
    private void guard(final ServerLevel level) {
        final BlockPos s = new BlockPos(SX, y0, SZ);
        final Earthquake.Shelter live = Earthquake.Shelter.of(level);
        check("the live guard holds next to S and lets go at the edge",
                live.holds(level, s.east()) && live.holds(level, s.offset(47, 0, 0)) && live.holds(level, s.offset(30, 0, 30))
                        && !live.holds(level, s.offset(48, 0, 0)) && !live.holds(level, s.offset(40, 0, 40)),
                "next to S " + live.holds(level, s.east()) + ", 47 east " + live.holds(level, s.offset(47, 0, 0))
                        + ", 30/30 " + live.holds(level, s.offset(30, 0, 30)) + ", 48 east " + live.holds(level, s.offset(48, 0, 0))
                        + ", 40/40 " + live.holds(level, s.offset(40, 0, 40)));
        final List<BlockPos> spawnOnly = Earthquake.Shelter.homes(level);
        check("the world spawn is a home", spawnOnly.contains(level.getSharedSpawnPos()), "homes " + spawnOnly);
        final Earthquake.Shelter off = new Earthquake.Shelter(0, List.of(s));
        check("radius 0 lets the quake right up to S", !off.holds(level, s.east()) && !off.holds(level, s)
                        && !off.holds(level, s.east(), Earthquake.Shelter.THROW),
                "next to S " + off.holds(level, s.east()) + ", on S " + off.holds(level, s) + ", next to S with the throw margin "
                        + off.holds(level, s.east(), Earthquake.Shelter.THROW));

        // a bed far from S, on a fake player handed to the seam; and one in the Nether, which is no home here
        final BlockPos bed = s.offset(400, 0, 0);
        final FakePlayer sleeper = FakePlayerFactory.getMinecraft(level);
        sleeper.setRespawnPosition(Level.OVERWORLD, bed, 0.0F, true, false);
        final List<BlockPos> withBed = Earthquake.Shelter.homes(level, List.of(sleeper));
        final Earthquake.Shelter beds = new Earthquake.Shelter(RADIUS, withBed);
        final boolean nearBed = beds.holds(level, bed.offset(20, 0, -20));
        final boolean farFromBed = beds.holds(level, bed.offset(60, 0, 0));
        sleeper.setRespawnPosition(Level.NETHER, bed, 0.0F, true, false);
        final List<BlockPos> netherBed = Earthquake.Shelter.homes(level, List.of(sleeper));
        sleeper.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        check("an online player's bed is a home, one in another dimension is not",
                withBed.contains(bed) && nearBed && !farFromBed && !netherBed.contains(bed),
                "homes with the bed " + withBed + ", 28 from it held " + nearBed + ", 60 from it held " + farFromBed
                        + "; homes with the bed in the Nether " + netherBed);
    }

    private BlockState[] snapshot(final ServerLevel level) {
        return snapshot(level, SX - BOX, SX + BOX, SZ - BOX, SZ + BOX);
    }

    /** Every block in the box, x then z then y from BELOW under the surface to ABOVE over it. */
    private BlockState[] snapshot(final ServerLevel level, final int x0, final int x1, final int z0, final int z1) {
        final BlockState[] states = new BlockState[(x1 - x0 + 1) * (z1 - z0 + 1) * (BELOW + ABOVE + 1)];
        int i = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0 - BELOW; y <= y0 + ABOVE; y++) states[i++] = level.getBlockState(new BlockPos(x, y, z));
            }
        }
        return states;
    }

    /** Inside the safe radius of S, measured the way the guard measures it: across the ground, block to block. */
    private static boolean sheltered(final int x, final int z) {
        return d2(x, z) < (long) RADIUS * RADIUS;
    }

    private static long d2(final int x, final int z) {
        final long dx = x - SX, dz = z - SZ;
        return dx * dx + dz * dz;
    }

    private void next() {
        stage++;
        stageTick = tick;
    }

    private void check(final String name, final boolean ok, final String detail) {
        if (!ok) failures++;
        LOG.info(TAG + "CHECK {}: {} {}", name, ok ? "OK" : "FAIL", detail);
    }
}
