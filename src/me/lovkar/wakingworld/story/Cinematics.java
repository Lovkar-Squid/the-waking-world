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
 * Scenes: {@code shrine}, {@code rite}, {@code fight}, {@code kingdom}, {@code titan}, {@code all}.
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
    public static final int DEFAULT_RENDER_DISTANCE = 16;
    /** How long the server waits for the client's "ready" before rolling anyway (ticks). */
    private static final int MAX_CLIENT_WAIT = 400;
    /** How long a stage may take to load before the scene starts on what there is (ticks). */
    private static final int MAX_PREPARE = 20 * 60 * 5;
    /** Loaded chunks the stage must have, as a share of all of them, before the scene starts. */
    private static final double PREPARED = 0.98;

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

    private static void finish(Run run) {
        active = null;
        releaseStages(run);
        WakingNet.cineStop(run.player);
        if (run.dummy != null && run.dummy.isAlive()) run.dummy.discard();
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
        for (int lift = 0; lift < 60; lift++) {
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
        run.waiting = true;
        run.waitTicks = 0;
        WakingNet.cineStart(run.player, keys, fadeIn, fadeOut);
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
            roll(r, keys, fromRite ? 10 : 20, 20);
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
            roll(r, keys, 20, 10);
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
            roll(r, keys, 20, 10);
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
}
