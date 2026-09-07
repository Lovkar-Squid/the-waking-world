package me.lovkar.wakingworld.story;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.network.WakingNet;
import me.lovkar.wakingworld.ritual.AltarBlockEntity;
import me.lovkar.wakingworld.ritual.Rites;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The director: {@code /wakingworld cine <scene>} plays a scene for the trailer. The server sets the
 * stage (finds the shrine, the arena, the kingdom; sets the hour; starts the rite; gives the giant
 * something to fight) and sends the player's client a camera path ({@link Key}s: where the camera is
 * and what it looks at, tick by tick); the client puts the player in the camera's place, hides the
 * HUD but the boss bar, draws the letterbox and fades in and out ({@code client/Cinematic}). The
 * player is a spectator for the duration and comes back to their game mode and place afterwards.
 * Scenes: {@code shrine}, {@code rite}, {@code fight}, {@code kingdom}, {@code titan}, {@code all}
 * for the mod's first half, and {@code lands}, {@code tornado}, {@code earthquake}, {@code volcano},
 * {@code meteor}, {@code bloodmoon}, {@code cataclysms} for 0.2. The cataclysm scenes do not look for
 * a structure - they pick open ground themselves and then make the thing happen in front of the
 * camera, so they play anywhere.
 * <p>
 * Nothing may load in front of the camera. A run first <b>prepares</b> its stages: chunk tickets load
 * and generate every chunk the camera will see (render distance + 1 round each stage, in the background,
 * with the progress in the action bar), the client is asked to draw at that render distance for the
 * duration (the integrated server follows it), and only then does the first scene begin. Every camera
 * path then waits behind black until the client reports the stage drawn ({@code CineReady}) - the
 * server's clock stops while it waits, so the rite and the fight stay in step with the camera.
 */
public final class Cinematics {
    private Cinematics() {
    }

    /** The render distance the scenes are drawn at unless the command says otherwise. */
    public static final int DEFAULT_RENDER_DISTANCE = 24;
    /**
     * What the reel draws at unless the caller says otherwise. Every scene holds its own stage open,
     * and the six of them at 24 would be eighteen thousand chunks to generate before the first frame;
     * at 18 it is half that and the horizon is still well past anything the camera looks at.
     */
    public static final int REEL_RENDER_DISTANCE = 18;
    /** How long the server waits for the client's "ready" before rolling anyway (ticks). */
    private static final int MAX_CLIENT_WAIT = 400;
    /** How long a stage may take to load before the scene starts on what there is (ticks). */
    private static final int MAX_PREPARE = 20 * 60 * 8;
    /** Loaded chunks the stage must have, as a share of all of them, before the scene starts. */
    private static final double PREPARED = 0.999;

    private static final TicketType<ChunkPos> STAGE_TICKET = TicketType.create("wakingworld_cine", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * The shrines worth a camera, best first: the standing stones (an open ring, the altar in plain
     * sight), the overgrown sanctum (a roofless hall), the sand tomb and the frost cairn (their altars
     * are inside, but the outside is a shape). Not the barrow - a grass hill, nothing to see - and not
     * the sunken shrine, which lies under water.
     */
    private static final String[] CAMERA_SHRINES = {"stone", "moss", "sandstone", "ice"};
    /** How many different shrines the shrine scene shows when it can. */
    private static final int SHRINE_SHOTS = 3;

    /**
     * One camera key: at {@code tick} the camera stands at (x, y, z) and looks at (lx, ly, lz) - or, when
     * {@code entity} >= 0, at that entity's position plus (lx, ly, lz) - with field of view {@code fov}.
     * The client interpolates smoothly between keys.
     */
    public record Key(int tick, double x, double y, double z, double lx, double ly, double lz, int entity, float fov, boolean anchored) {
        public static final StreamCodec<FriendlyByteBuf, Key> CODEC = StreamCodec.of(
                (buf, k) -> {
                    buf.writeVarInt(k.tick);
                    buf.writeDouble(k.x).writeDouble(k.y).writeDouble(k.z);
                    buf.writeDouble(k.lx).writeDouble(k.ly).writeDouble(k.lz);
                    buf.writeVarInt(k.entity);
                    buf.writeFloat(k.fov);
                    buf.writeBoolean(k.anchored);
                },
                buf -> new Key(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readFloat(), buf.readBoolean()));

        static Key at(int tick, Vec3 pos, Vec3 look, float fov) {
            return new Key(tick, pos.x, pos.y, pos.z, look.x, look.y, look.z, -1, fov, false);
        }

        static Key at(int tick, Vec3 pos, Entity look, Vec3 offset, float fov) {
            return new Key(tick, pos.x, pos.y, pos.z, offset.x, offset.y, offset.z, look.getId(), fov, false);
        }

        /**
         * A key that rides with an entity: the camera stands at {@code offset} from where the entity is
         * every frame and looks at the entity plus {@code lookOffset} - the orbit follows a walking giant.
         */
        static Key around(int tick, Entity e, Vec3 offset, Vec3 lookOffset, float fov) {
            return new Key(tick, offset.x, offset.y, offset.z, lookOffset.x, lookOffset.y, lookOffset.z, e.getId(), fov, true);
        }
    }

    // ------------------------------------------------------------------ the runner

    private record Step(int at, Consumer<Run> action) {
    }

    /** A place the camera will see: the chunks round it are loaded before the run and held until the cut. */
    private record Stage(ServerLevel level, ChunkPos centre, int radius) {
        int chunks() {
            return (2 * radius + 1) * (2 * radius + 1);
        }

        /** Chunks that are really there (hasChunk only asks the ticket level in 1.21; getChunkNow never blocks). */
        int loaded() {
            int n = 0;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) if (level.getChunkSource().getChunkNow(centre.x + dx, centre.z + dz) != null) n++;
            return n;
        }
    }

    private static final class Run {
        final ServerPlayer player;
        final List<Step> steps = new ArrayList<>();
        final List<Stage> stages = new ArrayList<>();
        int renderDistance = DEFAULT_RENDER_DISTANCE;
        String scene;
        int tick;
        int length;
        boolean preparing;
        int prepTicks;
        boolean waiting;
        int waitTicks;
        GameType modeBefore;
        Vec3 posBefore;
        ServerLevel levelBefore;
        // what the scenes find and make, for the steps after them
        Found primary; // the shrine the rite is held at
        Vec3 anchor;
        AltarBlockEntity altar;
        ColossusEntity giant;
        ArmorStand dummy;
        me.lovkar.wakingworld.cataclysm.TornadoEntity column;
        boolean litTheMoon;                 // this scene lit the blood moon and owes the world a dawn
        boolean storming;                   // and this one called up the storm the tornado walks in

        Run(ServerPlayer player) {
            this.player = player;
        }

        void add(int at, Consumer<Run> action) {
            steps.add(new Step(at, action));
            length = Math.max(length, at);
        }

        /** Registers a stage; the same place twice is one stage. */
        void stage(ServerLevel level, BlockPos at) {
            ChunkPos c = new ChunkPos(at);
            for (Stage s : stages) if (s.level == level && s.centre.equals(c)) return;
            stages.add(new Stage(level, c, renderDistance + 1));
        }

        int stageChunks() {
            int n = 0;
            for (Stage s : stages) n += s.chunks();
            return n;
        }

        int stageLoaded() {
            int n = 0;
            for (Stage s : stages) n += s.loaded();
            return n;
        }
    }

    private static Run active;

    public static boolean running() {
        return active != null;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        Run run = active;
        if (run == null || !(event.getLevel() instanceof ServerLevel level) || run.player.serverLevel() != level) return;
        if (run.player.isRemoved() || run.player.hasDisconnected()) {
            releaseStages(run);
            restore(run);           // the world does not stay in the middle of a scene nobody is watching
            active = null;
            return;
        }
        if (run.preparing) {
            tickPrepare(run);
            return;
        }
        if (run.waiting) {
            // the client draws the stage behind black; the clock stands still until it says so
            if (++run.waitTicks < MAX_CLIENT_WAIT) return;
            run.waiting = false;
        }
        int now = run.tick++;
        for (Step s : run.steps) {
            if (s.at == now) {
                try {
                    s.action.accept(run);
                } catch (Exception e) {
                    WakingWorld.LOGGER.warn("cine step at {} failed: {}", now, e.toString());
                }
            }
        }
        if (now >= run.length) finish(run);
    }

    public static void stop() {
        if (active != null) finish(active);
    }

    /** The client says the stage is drawn: the camera rolls. */
    public static void ready(ServerPlayer player) {
        Run run = active;
        if (run != null && run.player == player) run.waiting = false;
    }

    private static void tickPrepare(Run run) {
        run.prepTicks++;
        if (run.prepTicks % 10 != 0) return;
        int total = run.stageChunks();
        int loaded = total == 0 ? 0 : run.stageLoaded();
        boolean done = total == 0 || loaded >= total * PREPARED || run.prepTicks >= MAX_PREPARE;
        if (done) {
            run.preparing = false;
            run.player.displayClientMessage(Component.literal("Rolling: " + run.scene + " (" + (run.length / 20) + " s). /wakingworld cine stop to cut."), false);
            return;
        }
        if (run.prepTicks % 40 == 0) {
            int pct = (int) (100.0 * loaded / total);
            run.player.displayClientMessage(Component.literal("Preparing the stage - " + pct + " % (" + loaded + " of " + total + " chunks)"), true);
        }
    }

    private static void releaseStages(Run run) {
        for (Stage s : run.stages) s.level.getChunkSource().removeRegionTicket(STAGE_TICKET, s.centre, s.radius, s.centre);
        run.stages.clear();
    }

    /**
     * Everything a scene borrowed from the world, given back. A take that is cut short - or a player
     * who simply logs out mid-shot - must not leave a blood moon standing over a world that has no
     * dawn coming (the scenes set the time to midnight, and a blood moon only ends when the day time
     * leaves the night), a tornado eating somebody's roof, or a week of rain.
     */
    private static void restore(Run run) {
        ServerLevel level = run.levelBefore != null ? run.levelBefore : run.player.server.overworld();
        try {
            if (run.litTheMoon) {
                me.lovkar.wakingworld.cataclysm.BloodMoon.force(level, false);
                run.litTheMoon = false;
            }
            if (run.column != null) {
                if (run.column.isAlive()) run.column.discard();
                run.column = null;
            }
            if (run.storming) {
                level.setWeatherParameters(24000, 0, false, false);
                run.storming = false;
            }
        } catch (Exception e) {
            WakingWorld.LOGGER.warn("cine: could not put the world back: {}", e.toString());
        }
        if (run.dummy != null && run.dummy.isAlive()) run.dummy.discard();
    }

    private static void finish(Run run) {
        active = null;
        releaseStages(run);
        restore(run);
        WakingNet.cineStop(run.player);
        if (run.levelBefore != null && run.posBefore != null) {
            run.player.teleportTo(run.levelBefore, run.posBefore.x, run.posBefore.y, run.posBefore.z, run.player.getYRot(), run.player.getXRot());
        }
        if (run.modeBefore != null) run.player.setGameMode(run.modeBefore);
        run.player.displayClientMessage(Component.literal("Cut."), false);
    }

    /** Starts a scene (or {@code all}); returns a message for the caller, or null if it could not start. */
    public static String start(ServerPlayer player, String scene, int renderDistance) {
        if (active != null) finish(active);
        Run run = new Run(player);
        run.scene = scene;
        run.renderDistance = Math.max(4, Math.min(24, renderDistance));
        run.modeBefore = player.gameMode.getGameModeForPlayer();
        run.posBefore = player.position();
        run.levelBefore = player.serverLevel();
        run.add(0, r -> r.player.setGameMode(GameType.SPECTATOR)); // first of the tick-0 steps: before any teleport
        int t = 0;
        switch (scene) {
            case "shrine" -> t = shrine(run, t);
            case "rite" -> t = rite(run, t, true);
            case "fight" -> t = fight(run, t, false);
            case "kingdom" -> t = kingdom(run, t);
            case "titan" -> t = titan(run, t);
            case "lands" -> t = lands(run, t, null);
            case "tornado" -> t = tornado(run, t, null);
            case "earthquake" -> t = earthquake(run, t, null);
            case "volcano" -> t = volcano(run, t, null);
            case "meteor" -> t = meteor(run, t, null);
            case "bloodmoon" -> t = bloodmoon(run, t, null);
            case "cataclysms" -> t = cataclysms(run, t);
            case "all" -> {
                // every scene that has a stage within reach, in order; the ones without are left out
                t = shrine(run, t);
                if (t < 0) return "The scene could not find its stage (no shrine within reach).";
                t = rite(run, t + 20, false);
                t = fight(run, t + 10, true);
                int k = kingdom(run, t + 20);
                if (k >= 0) t = k;
                else player.displayClientMessage(Component.literal("No kingdom within reach - left out."), false);
                int e = titan(run, t + 20);
                if (e >= 0) t = e;
                else player.displayClientMessage(Component.literal("No arena in the End - left out."), false);
            }
            default -> {
                return null;
            }
        }
        if (t < 0) return "The scene could not find its stage (no " + scene + " within reach).";
        run.length = t;
        // the stages load in the background from now on and stay loaded until the cut
        for (Stage s : run.stages) s.level.getChunkSource().addRegionTicket(STAGE_TICKET, s.centre, s.radius, s.centre);
        WakingNet.cineSetup(player, run.renderDistance);
        run.preparing = true;
        active = run;
        int chunks = run.stageChunks();
        return "Preparing the stage for " + scene + " (" + chunks + " chunks at render distance " + run.renderDistance + ")...";
    }

    // ------------------------------------------------------------------ stage hands

    /** The nearest structure of a tag from the player, as a ground position, or null. */
    private static BlockPos locate(ServerPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag, int cells) {
        BlockPos hit = player.serverLevel().findNearestMapStructure(tag, player.blockPosition(), cells, false);
        return hit == null ? null : hit.offset(8, 0, 8);
    }

    /** A shrine the search found: its kind, its ground position, how far from the player. */
    private record Found(String kind, BlockPos site, double dist) {
    }

    /** The nearest shrine of one kind within so many placement cells, or null. */
    private static Found shrineOf(ServerPlayer player, String kind, int cells) {
        ServerLevel level = player.serverLevel();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "shrine_" + kind)));
        if (holder.isEmpty()) return null;
        Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), player.blockPosition(), cells, false);
        if (hit == null) return null;
        BlockPos site = hit.getFirst().offset(8, 0, 8);
        return new Found(kind, site, Math.sqrt(site.distSqr(player.blockPosition())));
    }

    /**
     * The nearest shrine of every camera-worthy kind within reach, nearest first. The standing stones
     * are looked for twice as far when the first search comes up empty: the rite is always held there.
     */
    private static List<Found> shrines(ServerPlayer player, int cells) {
        List<Found> out = new ArrayList<>();
        for (String kind : CAMERA_SHRINES) {
            Found f = shrineOf(player, kind, cells);
            if (f == null && kind.equals(CAMERA_SHRINES[0])) f = shrineOf(player, kind, cells * 2);
            if (f != null) out.add(f);
        }
        out.sort(Comparator.comparingDouble(Found::dist));
        StringBuilder sb = new StringBuilder();
        for (Found f : out) sb.append(sb.isEmpty() ? "" : ", ").append(f.kind).append(' ').append(Math.round(f.dist)).append(" m");
        player.displayClientMessage(Component.literal("Shrines: " + (sb.isEmpty() ? "none within reach" : sb)), false);
        WakingWorld.LOGGER.info("cine: shrines {}", sb);
        return out;
    }

    /** The shrine to hold the rite at: the standing stones when there are any, else the next best kind, else any shrine. */
    private static Found riteShrine(ServerPlayer player, List<Found> found) {
        for (String kind : CAMERA_SHRINES) for (Found f : found) if (f.kind.equals(kind)) return f;
        BlockPos any = locate(player, Letters.SHRINES, 24);
        return any == null ? null : new Found("any", any, Math.sqrt(any.distSqr(player.blockPosition())));
    }

    /** Loads the chunks round a spot (the stage must stand before the camera rolls). */
    private static void load(ServerLevel level, BlockPos at, int chunks) {
        ChunkPos c = new ChunkPos(at);
        for (int dx = -chunks; dx <= chunks; dx++) for (int dz = -chunks; dz <= chunks; dz++) level.getChunk(c.x + dx, c.z + dz);
    }

    /** The altar of a kind in the loaded chunks round a spot (a shrine's own, or the arena's great altar). */
    private static AltarBlockEntity altarNear(ServerLevel level, BlockPos at, int chunks, boolean great) {
        ChunkPos c = new ChunkPos(at);
        AltarBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunk(c.x + dx, c.z + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof AltarBlockEntity a) || Rites.lesser(a.kind())) continue;
                    if (great != a.great()) continue;
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

    private static Vec3 ground(ServerLevel level, double x, double z) {
        BlockPos g = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
        return new Vec3(x, g.getY(), z);
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 at) {
        player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
    }

    private static Vec3 orbit(Vec3 centre, double radius, double height, double angleDeg) {
        double a = Math.toRadians(angleDeg);
        return centre.add(Math.cos(a) * radius, height, Math.sin(a) * radius);
    }

    /** The camera's least height over the ground under it, and the step it is lifted by while a hill blocks the view. */
    private static final double CLEARANCE = 4.5, LIFT = 1.5;

    /** The top of the ground (trees included) round a point: the highest surface within three blocks. */
    private static int roof(ServerLevel level, double x, double z) {
        int top = level.getMinBuildHeight();
        for (int dx = -3; dx <= 3; dx += 3) for (int dz = -3; dz <= 3; dz += 3) top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x + dx), (int) Math.floor(z + dz)));
        return top;
    }

    /**
     * A camera position lifted clear of the ground: never inside a hill or a tree, and with the line of
     * sight to what it looks at free of the terrain between (sampled every four blocks). The camera
     * only ever goes up - the framing survives, the clipping does not.
     */
    private static Vec3 clear(ServerLevel level, Vec3 cam, Vec3 look) {
        double y = Math.max(cam.y, roof(level, cam.x, cam.z) + CLEARANCE);
        double ceiling = y + 26;      // a shot framed from ten blocks up is not saved by moving to ninety
        for (int lift = 0; lift < 60 && y < ceiling; lift++) {
            Vec3 at = new Vec3(cam.x, y, cam.z);
            Vec3 d = look.subtract(at);
            double len = d.length();
            boolean blocked = false;
            for (double s = 3; s < len - 6 && !blocked; s += 2) {
                Vec3 p = at.add(d.scale(s / len));
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.x), (int) Math.floor(p.z)) > p.y + 1) blocked = true;
            }
            if (!blocked) return at;
            y += LIFT;
        }
        return new Vec3(cam.x, y, cam.z);
    }

    /** Where the Titan rises when no player is in sight: 24 south of the great altar (AltarBlockEntity.climax). */
    private static Vec3 spotOf(Vec3 altar) {
        return altar.add(0, 0, 24);
    }

    /** {@link Key#at} with the camera lifted clear of the ground and of the hills between it and the look point. */
    private static Key shot(ServerLevel level, int tick, Vec3 cam, Vec3 look, float fov) {
        return Key.at(tick, clear(level, cam, look), look, fov);
    }

    /** The same, looking at an entity: the line of sight is checked against where the entity is now. */
    private static Key shot(ServerLevel level, int tick, Vec3 cam, Entity look, Vec3 offset, float fov) {
        return Key.at(tick, clear(level, cam, look.position().add(offset)), look, offset, fov);
    }

    /** A key riding with the entity (see {@link Key#around}), cleared of the ground where the entity stands now. */
    private static Key ride(ServerLevel level, int tick, Entity e, Vec3 offset, Vec3 lookOffset, float fov) {
        Vec3 here = e.position();
        Vec3 cam = clear(level, here.add(offset), here.add(lookOffset));
        return Key.around(tick, e, cam.subtract(here), lookOffset, fov);
    }

    private static void roll(Run run, List<Key> keys, int fadeIn, int fadeOut) {
        roll(run, keys, fadeIn, fadeOut, false);
    }

    /**
     * Send a path. {@code bossBar} keeps the boss bar on screen - which a fight wants and a
     * cataclysm does not: a colossus that happens to be alive somewhere near the shot used to put
     * its health bar across the top of a shot about the weather.
     */
    private static void roll(Run run, List<Key> keys, int fadeIn, int fadeOut, boolean bossBar) {
        run.waiting = true;
        run.waitTicks = 0;
        WakingNet.cineStart(run.player, level(keys), fadeIn, fadeOut, bossBar);
    }

    /**
     * How far the camera may climb or fall between two keys - a second apart - without the move
     * reading as a hop rather than as flight.
     */
    private static final double CLIMB = 2.6;

    /**
     * Take the staircase out of a path's height.
     *
     * <p>Every key is lifted clear of the ground under it on its own ({@link #clear}), which is what
     * stops the camera flying through a hill. But two keys a second apart over broken country get
     * very different lifts, and the curve through them is then a flight of steps: the camera hops up
     * a ridge and drops off the far side. That is exactly what the second take looked like.</p>
     *
     * <p>The fix is not to smooth the heights - smoothing would pull some of them back down into the
     * hillside they were lifted out of. It is to bound the SLOPE, in both directions: a pass forward
     * says a key may not sit more than {@code CLIMB} below the one before it, a pass back says the
     * same of the one after. Every key can only ever be raised, so nothing that was clear stops being
     * clear, and no step in the result is steeper than one camera can fly.</p>
     */
    private static List<Key> level(List<Key> keys) {
        int n = keys.size();
        if (n < 3) return keys;
        double[] y = new double[n];
        boolean[] free = new boolean[n];
        for (int i = 0; i < n; i++) {
            y[i] = keys.get(i).y();
            free[i] = !keys.get(i).anchored();   // a riding key's y is an offset, not a height
        }
        for (int i = 1; i < n; i++) if (free[i] && free[i - 1]) y[i] = Math.max(y[i], y[i - 1] - CLIMB);
        for (int i = n - 2; i >= 0; i--) if (free[i] && free[i + 1]) y[i] = Math.max(y[i], y[i + 1] - CLIMB);
        List<Key> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Key k = keys.get(i);
            out.add(y[i] == k.y() ? k
                    : new Key(k.tick(), k.x(), y[i], k.z(), k.lx(), k.ly(), k.lz(), k.entity(), k.fov(), k.anchored()));
        }
        return out;
    }

    // ------------------------------------------------------------------ the scenes

    /**
     * The shrines at dusk: a slow circle round each of up to three shrines of different kinds (the
     * ones with something to see from outside), 16 s each, the best one for the rite last so the rite
     * scene finds its altar without another search. Falls back to the nearest shrine of any kind.
     */
    private static int shrine(Run run, int t0) {
        ServerLevel level = run.player.serverLevel();
        List<Found> found = shrines(run.player, 24);
        Found primary = riteShrine(run.player, found);
        if (primary == null) return -1;
        List<Found> shots = new ArrayList<>();
        for (Found f : found) if (shots.size() < SHRINE_SHOTS - 1 && f != primary) shots.add(f);
        shots.add(primary); // last: the rite follows at this one
        run.primary = primary;
        int t = t0;
        for (int i = 0; i < shots.size(); i++) {
            Found f = shots.get(i);
            run.stage(level, f.site);
            double start = 200 + i * 110; // every shrine from a different side
            run.add(t, r -> {
                load(level, f.site, 2);
                AltarBlockEntity altar = altarNear(level, f.site, 2, false);
                Vec3 c = altar != null ? Vec3.atCenterOf(altar.getBlockPos()) : ground(level, f.site.getX() + 0.5, f.site.getZ() + 0.5).add(0, 1, 0);
                r.anchor = c;
                r.altar = altar;
                level.setDayTime(12600); // dusk
                level.setWeatherParameters(24000, 0, false, false);
                teleport(r.player, level, orbit(c, 26, 10, start));
                List<Key> keys = new ArrayList<>();
                for (int k = 0; k <= 16; k++) keys.add(shot(level, k * 20, orbit(c, 26 - k * 0.4, 10 - k * 0.25, start + k * 16), c.add(0, 1.5, 0), 62f));
                roll(r, keys, 20, 20);
            });
            t += 330 + (i < shots.size() - 1 ? 20 : 0);
        }
        return t;
    }

    /**
     * The rite: the horn, the runes, the beam, and the giant coming up out of the ground past the
     * altar. ~25 s. Finds the altar itself unless the shrine scene ran before it.
     */
    private static int rite(Run run, int t0, boolean findStage) {
        ServerLevel level = run.player.serverLevel();
        BlockPos site = null;
        if (findStage) {
            Found primary = riteShrine(run.player, shrines(run.player, 24));
            if (primary == null) return -1;
            run.primary = primary;
            site = primary.site;
            run.stage(level, site);
        }
        BlockPos found = site;
        run.add(t0, r -> {
            if (findStage) {
                load(level, found, 2);
                r.altar = altarNear(level, found, 2, false);
                r.anchor = r.altar != null ? Vec3.atCenterOf(r.altar.getBlockPos()) : ground(level, found.getX() + 0.5, found.getZ() + 0.5);
            }
            if (r.altar == null) return;
            Vec3 c = r.anchor;
            Vec3 spot = c.add(0, 0, 36); // the giant rises here: no waking player in sight, so south of the altar (AltarBlockEntity.climax)
            level.setDayTime(12500); // sunset behind the rising giant
            teleport(r.player, level, orbit(c, 22, 6, 250));
            List<Key> keys = new ArrayList<>();
            // the rite: a slow push back and up while the runes climb
            for (int i = 0; i <= 11; i++) keys.add(shot(level, i * 20, orbit(c, 22 + i * 1.2, 6 + i * 0.9, 250 - i * 3), c.add(0, 1.5 + i * 0.4, 0), 60f));
            // the climax: the camera keeps to the altar until the ground opens (the giant comes up at ~250),
            // then swings to the far side and tilts up with it
            keys.add(shot(level, 245, orbit(c, 38, 9, 214), c.add(0, 3, 0), 60f));
            for (int i = 1; i <= 8; i++) {
                double p = i / 8.0;
                keys.add(shot(level, 245 + i * 16, orbit(c, 40 + p * 14, 10 + p * 12, 217 - p * 60), spot.add(0, 2 + p * 24, 0), 60f + (float) p * 8f));
            }
            keys.add(shot(level, 400, orbit(c, 58, 24, 150), spot.add(0, 26, 0), 66f));
            roll(r, keys, 15, 20);
        });
        run.add(t0 + 30, r -> {
            if (r.altar != null) r.altar.forceStart();
        });
        run.add(t0 + 30 + Rites.TICKS + 10, r -> r.giant = nearestGiant(level, r.anchor, 120));
        return t0 + 420;
    }

    private static ColossusEntity nearestGiant(ServerLevel level, Vec3 near, double range) {
        ColossusEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities(WakingWorld.COLOSSUS.get(), x -> x.isAlive())) {
            double d = e.position().distanceToSqr(near);
            if (d < bestD && d < range * range) {
                bestD = d;
                best = (ColossusEntity) e;
            }
        }
        return best;
    }

    /**
     * The fight: the giant is given something to break - a dummy on a post - and the camera circles
     * low and wide while it stomps, throws and slams. 30 s. Uses the giant the rite woke, or the
     * nearest one alive.
     */
    private static int fight(Run run, int t0, boolean fromRite) {
        ServerLevel level = run.player.serverLevel();
        if (!fromRite) run.stage(level, run.player.blockPosition()); // the giant is wherever the player is
        run.add(t0, r -> {
            if (r.giant == null || !r.giant.isAlive()) r.giant = nearestGiant(level, r.player.position(), 200);
            if (r.giant == null) {
                r.player.displayClientMessage(Component.literal("No giant to fight."), false);
                return;
            }
            ColossusEntity g = r.giant;
            double h = g.bodyHeight();
            Vec3 c = g.position();
            level.setDayTime(23300); // the sun comes up over the fight
            // the dummy: an armour stand on a post of nothing, just out of arm's reach, that never dies
            Vec3 at = c.add(g.facing().scale(h * 0.32));
            Vec3 stand = ground(level, at.x, at.z).add(0, 1, 0);
            ArmorStand dummy = EntityType.ARMOR_STAND.create(level);
            if (dummy != null) {
                dummy.moveTo(stand.x, stand.y, stand.z, 0, 0);
                dummy.setInvulnerable(true);
                dummy.setNoGravity(true);
                level.addFreshEntity(dummy);
                g.setTarget(dummy);
                r.dummy = dummy;
            }
            // the orbit rides with the giant: whole body in frame, never inside it, whatever it walks into
            List<Key> keys = new ArrayList<>();
            for (int i = 0; i <= 30; i++) {
                double p = i / 30.0;
                double radius = h * (1.45 - 0.2 * Math.sin(p * Math.PI));
                Vec3 offset = orbit(Vec3.ZERO, radius, h * (0.4 + 0.2 * Math.sin(p * Math.PI * 2)), p * 300);
                keys.add(ride(level, i * 20, g, offset, new Vec3(0, h * 0.5, 0), 64f));
            }
            Key first = keys.get(0);
            teleport(r.player, level, c.add(first.x(), first.y(), first.z()));
            roll(r, keys, fromRite ? 10 : 20, 20, true);
        });
        return t0 + 620;
    }

    /** The walled town from the air: over the wall, across the square, round the keep. 22 s. */
    private static int kingdom(Run run, int t0) {
        ServerLevel level = run.player.serverLevel();
        BlockPos site = locate(run.player, Letters.KINGDOMS, 6);
        if (site == null) return -1;
        run.stage(level, site);
        run.stage(level, site.offset(-150, 0, 40)); // the approach starts far out: its view must be there too
        run.add(t0, r -> {
            load(level, site, 1);
            Vec3 c = ground(level, site.getX() + 0.5, site.getZ() + 0.5);
            level.setDayTime(1200); // morning
            teleport(r.player, level, c.add(-150, 60, 40));
            List<Key> keys = new ArrayList<>();
            // a long approach over the wall, dropping towards the square
            for (int i = 0; i <= 12; i++) {
                double p = i / 12.0;
                keys.add(shot(level, i * 20, c.add(-150 + p * 130, 60 - p * 35, 40 - p * 30), c.add(20 - p * 20, 20 - p * 10, 0), 64f));
            }
            // then round the keep
            for (int i = 1; i <= 10; i++) {
                double p = i / 10.0;
                keys.add(shot(level, 240 + i * 20, orbit(c, 40, 30 + p * 10, 180 + p * 150), c.add(0, 18, 0), 62f));
            }
            roll(r, keys, 20, 20);
        });
        return t0 + 460;
    }

    /**
     * The End: the arena, the rite at the great altar, the Titan rising, a fight against the dummy,
     * the Titan felled and the gate rising at the rim. ~110 s. The player is taken to the End and back.
     */
    private static int titan(Run run, int t0) {
        ServerLevel end = run.player.server.getLevel(Level.END);
        if (end == null) return -1;
        BlockPos arena = end.findNearestMapStructure(me.lovkar.wakingworld.worldgen.WakingStructures.TITAN_ARENA_TAG, new BlockPos(1200, 64, 0), 40, false);
        if (arena == null) return -1;
        BlockPos site = arena.offset(8, 0, 8);
        run.stage(end, site);
        run.add(t0, r -> {
            load(end, site, 4);
            AltarBlockEntity altar = altarNear(end, site, 4, true);
            if (altar == null) {
                r.player.displayClientMessage(Component.literal("The arena has no altar?"), false);
                return;
            }
            r.altar = altar;
            Vec3 c = Vec3.atCenterOf(altar.getBlockPos());
            r.anchor = c;
            Vec3 spot = c.add(0, 0, 24);
            // The eight pillars stand on the ring of 42 at 22.5 + 45 k degrees (TitanArenaPiece): the camera
            // crosses that ring only at 135 or 180 degrees, midway between two of them, and otherwise keeps
            // inside 34 or outside 60.
            teleport(r.player, end, orbit(c, 95, 45, 195));
            List<Key> keys = new ArrayList<>();
            // the arena from high and far in the west, a long push in over the rim to the great altar
            for (int i = 0; i <= 10; i++) {
                double p = i / 10.0;
                keys.add(shot(end, i * 20, orbit(c, 95 - p * 61, 45 - p * 33, 195 - p * 15), c.add(0, 2, 0), 62f));
            }
            // the rite runs (220): a slow sweep inside the pillars while the beams and the lesser altars answer
            for (int i = 1; i <= 11; i++) {
                double p = i / 11.0;
                keys.add(shot(end, 200 + i * 20, orbit(c, 34 - p * 4, 12 + p * 8, 180 - p * 50), c.add(0, 2 + p * 6, 0), 62f));
            }
            // the Titan rises (220 more): out through the gap at 135 degrees, then back and up to take it all in
            for (int i = 1; i <= 11; i++) {
                double p = i / 11.0;
                double radius = p < 0.4 ? 30 + p / 0.4 * 30 : 60 + (p - 0.4) / 0.6 * 40;
                double angle = p < 0.4 ? 130 + p / 0.4 * 5 : 135 - (p - 0.4) / 0.6 * 25;
                double height = p < 0.4 ? 20 + p / 0.4 * 14 : 34 + (p - 0.4) / 0.6 * 26;
                keys.add(shot(end, 420 + i * 20, orbit(c, radius, height, angle), spot.add(0, 6 + p * 44, 0), 62f + (float) p * 10f));
            }
            roll(r, keys, 20, 10, true);
        });
        run.add(t0 + 200, r -> {
            if (r.altar != null && r.altar.great()) r.altar.forceStart();
        });
        run.add(t0 + 200 + Rites.TICKS + 10, r -> r.giant = nearestGiant(end, r.anchor, 160));
        // the fight against the dummy on the arena floor
        int f = t0 + 660;
        run.add(f, r -> {
            if (r.giant == null || !r.giant.isAlive()) return;
            ColossusEntity g = r.giant;
            double h = g.bodyHeight();
            Vec3 c = g.position();
            Vec3 at = r.anchor.add(-20, 0, 0);
            Vec3 stand = ground(end, at.x, at.z).add(0, 1, 0);
            ArmorStand dummy = EntityType.ARMOR_STAND.create(end);
            if (dummy != null) {
                dummy.moveTo(stand.x, stand.y, stand.z, 0, 0);
                dummy.setInvulnerable(true);
                dummy.setNoGravity(true);
                end.addFreshEntity(dummy);
                g.setTarget(dummy);
                r.dummy = dummy;
            }
            // the orbit rides with the Titan, wide enough for all of it: 1.4 heights out, a third to two thirds up
            List<Key> keys = new ArrayList<>();
            for (int i = 0; i <= 24; i++) {
                double p = i / 24.0;
                Vec3 offset = orbit(Vec3.ZERO, h * (1.4 + 0.1 * Math.sin(p * Math.PI * 2)), h * (0.32 + 0.3 * Math.sin(p * Math.PI)), 100 + p * 220);
                keys.add(ride(end, i * 20, g, offset, new Vec3(0, h * 0.5, 0), 66f));
            }
            Key first = keys.get(0);
            teleport(r.player, end, c.add(first.x(), first.y(), first.z()));
            roll(r, keys, 20, 10, true);
        });
        // the Titan falls: the long death, then the gate rises at the southern rim
        int d = f + 500;
        run.add(d, r -> {
            if (r.giant != null && r.giant.isAlive()) r.giant.kill();
            Vec3 c = r.anchor;
            // the middle of the sheet's nave: the sheet stands on the gate floor (altar - 4), rows 0-9 are the nave
            Vec3 gate = new Vec3(c.x, Math.floor(c.y) + AltarBlockEntity.GATE_FLOOR_DY + 6.0, c.z + AltarBlockEntity.GATE_DZ);
            List<Key> keys = new ArrayList<>();
            // the fall, from far out and high, the whole Titan in frame (the collapse comes at 470)
            ColossusEntity dying = r.giant != null && !r.giant.isRemoved() ? r.giant : null;
            Vec3 body = dying != null ? dying.position() : spotOf(c);
            double h = dying != null ? dying.bodyHeight() : 72;
            for (int i = 0; i <= 25; i++) {
                double p = i / 25.0;
                Vec3 cam = orbit(body, h * 1.55, h * (0.75 - p * 0.35), 60 + p * 40);
                Vec3 at = new Vec3(0, h * (0.45 - p * 0.35), 0);
                keys.add(dying != null ? shot(end, i * 20, cam, dying, at, 62f) : shot(end, i * 20, cam, body.add(at), 62f));
            }
            // the gate: in through the gap at 135 degrees, a slide across the floor towards it, then straight at
            // the nave and through the sheet
            Vec3 slideFrom = c.add(-30, 0, 30);
            Vec3 slideTo = gate.add(0, 0, -22);
            for (int i = 1; i <= 10; i++) {
                double p = i / 10.0;
                Vec3 cam = new Vec3(slideFrom.x + (slideTo.x - slideFrom.x) * p, gate.y + 6 - p * 6, slideFrom.z + (slideTo.z - slideFrom.z) * p);
                keys.add(shot(end, 500 + i * 20, cam, gate.add(0, 3 - p * 3, 0), 60f));
            }
            for (int i = 1; i <= 8; i++) {
                double p = i / 8.0;
                keys.add(Key.at(700 + i * 20, gate.add(0, 0, -22 + p * 25), gate.add(0, 0, 12), 60f + (float) p * 20f));
            }
            roll(r, keys, 10, 40);
        });
        return d + 880;
    }

    // ------------------------------------------------------------------ 0.2: the cataclysms

    /**
     * Ground height straight out of the generator's noise. Nothing is loaded or generated to ask it,
     * which is the whole point: a scout that walked the world with {@code getChunk} would freeze the
     * server for the seconds it took (the same trap {@code kingdomSite} fell into). It is still a
     * full noise column and costs milliseconds, so the number of calls is the cost of a search.
     */
    private static int baseHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
    }

    private static BlockPos openGround(ServerLevel level, BlockPos from, int minOut, int maxOut) {
        return openGround(level, from, minOut, maxOut, 28);
    }

    /**
     * Open ground for a cataclysm: dry, above the sea and as level as can be found, out in the ring
     * between {@code minOut} and {@code maxOut} blocks from a starting point. Candidates lie on a
     * golden-angle spiral so they spread evenly without ever falling into a grid, and each is scored
     * by the height spread of four samples 26 blocks out - the first spot within five blocks of level
     * is taken rather than the best of everything, because every sample is a noise column.
     */
    private static BlockPos openGround(ServerLevel level, BlockPos from, int minOut, int maxOut, int tries) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        int bestSpread = Integer.MAX_VALUE;
        for (int i = 0; i < tries; i++) {
            double a = i * 2.39996323;                       // the golden angle
            double d = minOut + (maxOut - minOut) * (i / (double) Math.max(1, tries - 1));
            int x = from.getX() + (int) Math.round(Math.cos(a) * d);
            int z = from.getZ() + (int) Math.round(Math.sin(a) * d);
            int h = baseHeight(level, x, z);
            if (h <= sea + 2) continue;                      // sea, lake or swamp floor
            int lo = h, hi = h;
            for (int k = 0; k < 4; k++) {                    // four quarters catch a hillside
                double b = k * Math.PI / 2 + 0.4;
                int sh = baseHeight(level, x + (int) (Math.cos(b) * 26), z + (int) (Math.sin(b) * 26));
                lo = Math.min(lo, sh);
                hi = Math.max(hi, sh);
            }
            if (lo <= sea) continue;                         // one foot in the water
            int spread = hi - lo;
            if (spread < bestSpread) {
                bestSpread = spread;
                best = new BlockPos(x, h, z);
            }
            if (spread <= 5) break;                          // good enough - stop paying for better
        }
        return best;
    }

    /**
     * The real ground at a scouted spot. {@link #baseHeight} answers the noise, which is a block or
     * two out once the surface rules have run - so the winner, and only the winner, is asked properly.
     */
    private static BlockPos settle(ServerLevel level, BlockPos scouted) {
        return me.lovkar.wakingworld.cataclysm.Cataclysms.surface(level, scouted.getX() + 0.5, scouted.getZ() + 0.5);
    }

    /**
     * Open ground the camera would use, for {@code /wakingworld site}: the same search the cataclysm
     * scenes run, so what it reports is what they will pick.
     */
    public static BlockPos scout(ServerLevel level, BlockPos from, int minOut, int maxOut) {
        BlockPos scouted = openGround(level, from, minOut, maxOut);
        return scouted == null ? null : settle(level, scouted);
    }

    /** How level the ground is round a spot: the height spread of four samples 26 blocks out. */
    public static int levelness(ServerLevel level, BlockPos at) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            double b = k * Math.PI / 2 + 0.4;
            int h = baseHeight(level, at.getX() + (int) (Math.cos(b) * 26), at.getZ() + (int) (Math.sin(b) * 26));
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return hi - lo;
    }

    /** Open ground for one cataclysm scene, or null when there is nothing but water within reach. */
    private static BlockPos cataclysmSite(Run run) {
        return openGround(run.player.serverLevel(), run.player.blockPosition(), 70, 260);
    }

    /**
     * The Named Lands: a long, high crossing of the country with the title card thrown up over it.
     * ~24 s.
     *
     * <p>The flight is 380 blocks long and looks 90 further, which is past a single stage - so it
     * stages the two ends as well as the middle, the way {@code kingdom} stages its approach.</p>
     */
    private static int lands(Run run, int t0, BlockPos site) {
        ServerLevel level = run.player.serverLevel();
        BlockPos where = site != null ? site : cataclysmSite(run);
        if (where == null) return -1;
        run.stage(level, where);
        run.stage(level, where.offset(190, 0, 40));           // the camera starts here, seeing further back
        run.stage(level, where.offset(-190, 0, -40));         // and ends here, looking further on
        run.add(t0, r -> {
            BlockPos g = settle(level, where);                // never a raw heightmap read: see settle()
            Vec3 c = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            level.setDayTime(23200);                          // first light, long shadows
            level.setWeatherParameters(24000, 0, false, false);
            // ask for the name now so the model has the whole flight to answer; nameSoon never blocks
            me.lovkar.wakingworld.land.Lands.get(level).nameSoon(level, where);
            // east to west: at first light the sun is low in the east, so flying the other way put
            // it straight down the lens for the whole crossing
            Vec3 from = c.add(190, 78, 40);
            Vec3 to = c.add(-190, 58, -40);
            teleport(r.player, level, from);
            List<Key> keys = new ArrayList<>();
            for (int i = 0; i <= 22; i++) {
                double p = i / 22.0;
                Vec3 cam = from.add(to.subtract(from).scale(p));
                Vec3 look = cam.add(to.subtract(from).normalize().scale(90)).add(0, -26, 0);
                keys.add(shot(level, i * 20, cam, look, 70f));
            }
            roll(r, keys, 25, 25);
        });
        // the card lands a third of the way in, once the country is on screen; by now the model has
        // either answered or the template name is standing in for it
        run.add(t0 + 160, r -> {
            me.lovkar.wakingworld.land.Lands book = me.lovkar.wakingworld.land.Lands.get(level);
            me.lovkar.wakingworld.land.Lands.Land land = book.nameSoon(level, where);
            if (land == null) WakingWorld.LOGGER.info("cine: the land here still has no name - no card");
            me.lovkar.wakingworld.land.Lands.card(r.player, land);
        });
        return t0 + 465;
    }

    /**
     * The Wandering Column: a tornado spawned in the open and ridden alongside, then let go past a
     * camera on the ground. ~30 s.
     *
     * <p>The spawn has to happen in the same step as the camera path - the keys ride the entity and
     * need its id to be built - so it is given a life long enough to survive the wait behind black
     * as well as the shot.</p>
     */
    private static int tornado(Run run, int t0, BlockPos site) {
        ServerLevel level = run.player.serverLevel();
        BlockPos where = site != null ? site : cataclysmSite(run);
        if (where == null) return -1;
        run.stage(level, where);
        run.add(t0, r -> {
            BlockPos g = settle(level, where);
            Vec3 c = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            level.setDayTime(6000);                           // full day: the debris has to read
            level.setWeatherParameters(0, 8000, true, false);  // storm, no thunder in the way
            r.storming = true;
            me.lovkar.wakingworld.cataclysm.TornadoEntity t =
                    me.lovkar.wakingworld.cataclysm.TornadoEntity.spawn(level, c, 75);
            r.column = t;
            // riding it: out to the side and a little above, framed on the lower half of the column
            List<Key> keys = new ArrayList<>();
            for (int i = 0; i <= 17; i++) {
                double p = i / 17.0;
                Vec3 offset = orbit(Vec3.ZERO, 44 - p * 10, 13 + p * 7, 40 + p * 150);
                keys.add(ride(level, i * 20, t, offset, new Vec3(0, 15, 0), 70f));
            }
            teleport(r.player, level, c.add(orbit(Vec3.ZERO, 44, 13, 40)));
            roll(r, keys, 20, 15);
        });
        // then a camera standing still on the ground while it works nearby
        run.add(t0 + 370, r -> {
            if (r.column == null || !r.column.isAlive()) return;
            Vec3 t = r.column.position();
            // its own y is the ground under it; the camera is then lifted clear the usual way, which
            // reads chunks the stage has held open all along
            Vec3 cam = clear(level, new Vec3(t.x + 46, t.y + 6, t.z + 46), t.add(0, 20, 0));
            teleport(r.player, level, cam);
            List<Key> keys = new ArrayList<>();
            for (int i = 0; i <= 9; i++) keys.add(shot(level, i * 20, cam, r.column, new Vec3(0, 20, 0), 72f));
            roll(r, keys, 15, 25);
        });
        // and it is put away with the shot. Left running it walks on into the next scene - in the reel
        // the earthquake takes this very field - roaring and shaking over somebody else's take.
        run.add(t0 + 570, r -> {
            if (r.column != null) {
                if (r.column.isAlive()) r.column.discard();
                r.column = null;
            }
        });
        return t0 + 575;
    }

    /**
     * The Turning Ground: a low camera on open ground while the faults open under it. ~26 s, which is
     * what {@code earthquakeSeconds} is set to.
     *
     * <p>It goes through {@code Weather.forceQuake}, the same door the command uses.
     * {@code Earthquake.shake} on its own opens the whole fault in a single tick and then nothing
     * moves again - the shaking that makes the shot is {@code Weather} driving it second by second.</p>
     */
    private static int earthquake(Run run, int t0, BlockPos site) {
        ServerLevel level = run.player.serverLevel();
        BlockPos where = site != null ? site : cataclysmSite(run);
        if (where == null) return -1;
        run.stage(level, where);
        run.add(t0, r -> {
            BlockPos g = settle(level, where);
            Vec3 c = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            r.anchor = c;
            level.setDayTime(5000);
            level.setWeatherParameters(24000, 0, false, false);
            // low and close, so the ground fills the frame and the shake is felt rather than watched
            List<Key> keys = new ArrayList<>();
            for (int i = 0; i <= 10; i++) {
                double p = i / 10.0;
                keys.add(shot(level, i * 20, orbit(c, 22 + p * 10, 3.5, 30 + p * 55), c.add(0, 1, 0), 74f));
            }
            // then up and back, to see what it left
            for (int i = 1; i <= 10; i++) {
                double p = i / 10.0;
                keys.add(shot(level, 200 + i * 20, orbit(c, 32 + p * 34, 4 + p * 30, 85 + p * 45), c.add(0, 1, 0), 68f));
            }
            teleport(r.player, level, orbit(c, 22, 8, 30));
            roll(r, keys, 20, 25);
        });
        // the ground opens once the client is through the fade, not behind it
        run.add(t0 + 25, r -> {
            if (r.anchor != null) me.lovkar.wakingworld.cataclysm.Weather.forceQuake(level, r.anchor);
        });
        return t0 + 425;
    }

    /**
     * The Rising Mountain: flat ground, a warning of smoke and tremors, then the cone coming up while
     * the camera pulls back and up to keep it in frame. ~55 s.
     *
     * <p>36 courses over 36 seconds, which is one course a second - the pace the pulse clock can
     * actually keep, since it only wakes once every twenty ticks. The configured minutes are right
     * for a world and far too slow for a shot.</p>
     */
    private static final int CONE = 36, CONE_FOOT = 30, CONE_SECONDS = 36;

    private static int volcano(Run run, int t0, BlockPos site) {
        ServerLevel level = run.player.serverLevel();
        BlockPos where = site != null ? site : cataclysmSite(run);
        if (where == null) return -1;
        run.stage(level, where);
        run.stage(level, where.offset(-120, 0, 0));            // the pull-back sees a long way west
        run.add(t0, r -> {
            BlockPos g = settle(level, where);
            Vec3 c = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            r.anchor = c;
            // late afternoon, not dusk: the shot runs 50 s and would otherwise walk the clock into
            // 13000-14000, which is exactly when the world rolls for a shower and a blood moon
            level.setDayTime(11400);
            // and the sun is low in the west by then. The first take swung the camera round to 350
            // degrees, which is standing in the east looking straight into it: the mountain came out
            // as a silhouette in a white frame. Everything below keeps the camera on the sun's side.
            level.setWeatherParameters(24000, 0, false, false);
            teleport(r.player, level, orbit(c, 70, 22, 200));
            List<Key> keys = new ArrayList<>();
            // the warning: smoke and shaking over ground that is still flat (Volcano.force gives it 5 s)
            for (int i = 0; i <= 6; i++) keys.add(shot(level, i * 20, orbit(c, 54 - i, 13, 200 - i * 2), c.add(0, 4, 0), 64f));
            // The rise. The last take pulled out to a hundred and twenty blocks at a height of
            // fifty-eight and looked DOWN at the thing: a thirty-six block cone came out as a bump
            // at the bottom of the frame and then left it altogether. A mountain has to be looked UP
            // at, and framed - the camera stays low and inside eighty blocks, and the look point
            // tracks the middle of the cone rather than its summit, so the whole of it is in shot.
            for (int i = 1; i <= 36; i++) {
                double p = i / 36.0;
                keys.add(shot(level, 120 + i * 20, orbit(c, 48 + p * 30, 10 + p * 17, 186 - p * 50),
                        c.add(0, 3 + p * CONE * 0.42, 0), 66f));
            }
            // and a last hold, three quarters of the frame filled by the mountain
            for (int i = 1; i <= 6; i++) {
                keys.add(shot(level, 840 + i * 20, orbit(c, 80 + i, 27, 136 - i * 2), c.add(0, CONE * 0.44, 0), 64f));
            }
            roll(r, keys, 25, 30);
        });
        run.add(t0 + 25, r -> {
            if (r.anchor != null) {
                me.lovkar.wakingworld.cataclysm.Volcano.force(
                        level, BlockPos.containing(r.anchor), CONE, CONE_FOOT, CONE_SECONDS);
            }
        });
        return t0 + 990;
    }

    /**
     * The Falling Sky: stars streak the night away over the country, then one comes down in front of
     * the camera and the shot pushes into the crater it leaves. ~30 s.
     *
     * <p>The far ones are placed relative to where the camera is looking rather than at fixed
     * compass angles, or two of the three fall behind it.</p>
     */
    private static int meteor(Run run, int t0, BlockPos site) {
        ServerLevel level = run.player.serverLevel();
        BlockPos where = site != null ? site : cataclysmSite(run);
        if (where == null) return -1;
        run.stage(level, where);
        run.add(t0, r -> {
            BlockPos g = settle(level, where);
            Vec3 c = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            level.setDayTime(18000);                           // midnight: the trails have to glow
            level.setWeatherParameters(24000, 0, false, false);
            r.anchor = c;
            teleport(r.player, level, orbit(c, 54, 16, 315));
            List<Key> keys = new ArrayList<>();
            // wide and low, the sky over the horizon, while the far ones come down
            for (int i = 0; i <= 7; i++) keys.add(shot(level, i * 20, orbit(c, 54 - i, 16 + i * 0.6, 315 + i * 3), c.add(0, 34, 0), 70f));
            // the near one lands at ~205: the camera is already on the spot it is aimed at
            for (int i = 1; i <= 6; i++) {
                double p = i / 6.0;
                keys.add(shot(level, 140 + i * 20, orbit(c, 47 + p * 6, 21 - p * 6, 336 + p * 16), c.add(0, 24 - p * 22, 0), 68f));
            }
            // and into the crater it left
            for (int i = 1; i <= 10; i++) {
                double p = i / 10.0;
                keys.add(shot(level, 260 + i * 20, orbit(c, 53 - p * 34, 15 - p * 6, 352 + p * 70), c.add(0, 1, 0), 66f + (float) p * 6f));
            }
            roll(r, keys, 25, 25);
        });
        // three away in the distance, staggered - spread across the view, which runs out from the
        // camera at 315 degrees through the middle, so roughly 135 degrees and either side of it
        for (int i = 0; i < 3; i++) {
            final int k = i;
            run.add(t0 + 40 + i * 34, r -> {
                Vec3 c = r.anchor;
                if (c == null) return;
                double a = Math.toRadians(135 + (k - 1) * 34);
                double d = 150 + k * 40;
                BlockPos g = me.lovkar.wakingworld.cataclysm.Cataclysms.surface(level, c.x + Math.cos(a) * d, c.z + Math.sin(a) * d);
                me.lovkar.wakingworld.cataclysm.Cataclysms.fall(level, Vec3.atBottomCenterOf(g), 1 + (k & 1), false);
            });
        }
        run.add(t0 + 168, r -> {
            if (r.anchor != null) me.lovkar.wakingworld.cataclysm.Cataclysms.fall(level, r.anchor, 3, true);
        });
        return t0 + 485;
    }

    /**
     * The Blood Moon: the sky turns over open ground, the siege lands around the camera and the shot
     * circles low through what came with it. ~32 s. The sky takes about 125 ticks to go over, so the
     * first section is cut to match rather than to the four seconds it looks like.
     */
    private static int bloodmoon(Run run, int t0, BlockPos site) {
        ServerLevel level = run.player.serverLevel();
        BlockPos where = site != null ? site : cataclysmSite(run);
        if (where == null) return -1;
        run.stage(level, where);
        run.add(t0, r -> {
            BlockPos g = settle(level, where);
            Vec3 c = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            level.setDayTime(18000);
            level.setWeatherParameters(24000, 0, false, false);
            r.anchor = c;
            teleport(r.player, level, orbit(c, 30, 9, 90));
            List<Key> keys = new ArrayList<>();
            // the sky going over, held wide on the horizon
            for (int i = 0; i <= 7; i++) keys.add(shot(level, i * 20, orbit(c, 30 + i * 1.4, 9 + i * 1.1, 90 + i * 5), c.add(0, 20, 0), 70f));
            // then down among them
            for (int i = 1; i <= 16; i++) {
                double p = i / 16.0;
                keys.add(shot(level, 140 + i * 20, orbit(c, 34 - p * 16, 8 - p * 4, 125 + p * 220), c.add(0, 2, 0), 66f));
            }
            roll(r, keys, 30, 25);
        });
        // lit after the fade, so the four seconds of sky are on camera and not behind black. A moon
        // the world had already raised is left alone - and not put out afterwards either.
        run.add(t0 + 20, r -> {
            boolean already = me.lovkar.wakingworld.cataclysm.BloodMoon.running(level);
            me.lovkar.wakingworld.cataclysm.BloodMoon.force(level, true);
            r.litTheMoon = !already;
        });
        // the siege comes in once the sky has finished turning
        run.add(t0 + 155, r -> {
            if (r.anchor == null) return;
            int n = me.lovkar.wakingworld.cataclysm.BloodMoon.siege(level, r.anchor, r.player, 24, level.random);
            WakingWorld.LOGGER.info("cine: the blood moon put {} of them on the ground", n);
            if (n == 0) r.player.displayClientMessage(Component.literal(
                    "Nothing would spawn - too many already about, or nowhere dark enough. Move and shoot it again."), false);
        });
        run.add(t0 + 470, r -> {
            if (r.litTheMoon) {
                me.lovkar.wakingworld.cataclysm.BloodMoon.force(level, false);
                r.litTheMoon = false;
            }
        });
        return t0 + 490;
    }

    /**
     * The 0.2 reel: the Named Lands, then all five cataclysms, in the order a trailer wants them -
     * the country first, then wind, ground, fire, sky, and the blood moon last, so the light runs
     * from first light to midnight across the cut. About three and a half minutes.
     *
     * <p>Six sites would be six stages to load, so these share four: the tornado and the earthquake
     * take the same field a moment apart, and the blood moon rises over the crater the meteor has
     * just made, which is the better shot anyway. The four are pushed well apart - a mountain with a
     * 260-block ash plume must not come up inside another scene - and a site the search cannot find
     * falls back to a fixed offset rather than to the middle, so a failure spreads the shots out
     * instead of piling them on one spot.</p>
     */
    private static int cataclysms(Run run, int t0) {
        ServerLevel level = run.player.serverLevel();
        BlockPos centre = cataclysmSite(run);
        if (centre == null) return -1;
        BlockPos country = sub(level, centre, -430, -180);
        BlockPos field = sub(level, centre, 380, -300);
        BlockPos mountain = sub(level, centre, 90, 430);
        BlockPos crater = sub(level, centre, -330, 360);
        if (country == null || field == null || mountain == null || crater == null) {
            run.player.displayClientMessage(Component.literal(
                    "Not enough dry ground round here for the whole reel - try again somewhere inland."), false);
            return -1;
        }
        int t = lands(run, t0, country);
        t = tornado(run, t + 10, field);
        t = earthquake(run, t + 10, field);
        t = volcano(run, t + 10, mountain);
        t = meteor(run, t + 10, crater);
        t = bloodmoon(run, t + 10, crater);
        return t;
    }

    /**
     * One of the reel's sites. The fallback matters more than the search: the first version handed
     * back the bare offset when it found nothing, which is a point with no ground under it at all -
     * and on the first take that put the tornado, the meteor and the blood moon over open water.
     * Now it widens the search twice and only then gives up, and what it gives up with is still a
     * spot that was checked for being dry.
     */
    private static BlockPos sub(ServerLevel level, BlockPos centre, int dx, int dz) {
        BlockPos want = centre.offset(dx, 0, dz);
        BlockPos found = openGround(level, want, 0, 90, 12);
        if (found == null) found = openGround(level, want, 60, 220, 20);
        if (found == null) found = openGround(level, centre, 90, 400, 24);
        return found;
    }
}
