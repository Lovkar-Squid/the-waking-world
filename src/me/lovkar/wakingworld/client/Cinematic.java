package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.network.WakingNet;
import me.lovkar.wakingworld.story.Cinematics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * The client's half of the director ({@link Cinematics}): while a camera path plays, the player is
 * the camera - put where the path says every frame (position and look interpolated between the
 * keys, a Catmull-Rom curve through the positions so the moves are smooth), the HUD hidden but
 * for the boss bar, a letterbox top and bottom, a fade from black at the start and to black at the
 * end. Mouse and keys change nothing until the cut.
 * <p>
 * Nothing may load on camera. A run begins with {@link #setup}: the world is drawn at the director's
 * render distance until the cut (the integrated server follows the option, so it also loads and sends
 * that much and no more). Every path then starts behind black and stays there until the stage is
 * <b>drawn</b> - the chunks round the camera have arrived and the surface sections in front of it are
 * built - and only then tells the server to roll ({@code CineReady}); the server's clock waits for it.
 * Between two paths of one run the screen stays black, so a cut is a cut.
 */
public final class Cinematic {
    private Cinematic() {
    }

    /** Chunks round the camera that must have arrived before the path starts (capped by the render distance). */
    private static final int ARRIVE_RADIUS = 16;
    /** Columns in front of the camera whose surface sections must be built (the near ground; the far is in the fog). */
    private static final int BUILD_RADIUS = 8;
    /** Share of the chunks in view that must be there, and of the near surface sections in front that must be built. */
    private static final double ARRIVED = 0.95, BUILT = 0.8;
    /** Columns count as "in front" within this cosine of the view direction (~53 degrees; the frustum at 16:9). */
    private static final double FRONT_COS = 0.6;
    /**
     * Ticks after the chunks arrived before the path starts (the last sections build); ticks the built
     * count may stand still before we take it as finished (what is hidden is never built); the longest wait.
     */
    private static final int SETTLE = 20, STILL = 30, MAX_WAIT = 240;

    private static List<Cinematics.Key> keys;
    private static int tick, fadeIn, fadeOut, length;
    private static boolean waiting, holding;
    private static int waitTicks, arrivedAt, lastBuilt, stillTicks;
    private static boolean wasHidingGui;
    private static int renderBefore = -1;
    /** Where the entity the anchored keys ride with is (interpolated), this frame; the last known place when it is gone. */
    private static Vec3 anchor = Vec3.ZERO, lastAnchor;

    public static boolean active() {
        return keys != null;
    }

    /** The letterbox's height in GUI pixels (0 when no scene plays), for the HUD pieces that stay. */
    public static int letterbox() {
        if (!active()) return 0;
        Minecraft mc = Minecraft.getInstance();
        return (int) (mc.getWindow().getGuiScaledHeight() * 0.11);
    }

    /** A run begins: draw at the director's render distance until the cut. */
    public static void setup(int renderDistance) {
        Minecraft mc = Minecraft.getInstance();
        int now = mc.options.renderDistance().get();
        if (renderBefore < 0) renderBefore = now;
        if (now != renderDistance) {
            mc.options.renderDistance().set(renderDistance);
            WakingWorld.LOGGER.info("cine: render distance {} -> {} for the run", now, renderDistance);
        }
    }

    public static void start(List<Cinematics.Key> path, int in, int out) {
        if (path == null || path.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active()) wasHidingGui = mc.options.hideGui;
        keys = path;
        tick = 0;
        fadeIn = in;
        fadeOut = out;
        length = path.get(path.size() - 1).tick();
        waiting = true;
        holding = false;
        waitTicks = 0;
        arrivedAt = -1;
        lastBuilt = -1;
        stillTicks = 0;
        lastAnchor = null;
        anchor(1f);
        if (mc.player != null) mc.player.setDeltaMovement(Vec3.ZERO);
    }

    /** Updates {@link #anchor}: the ridden entity's position at this partial tick, or where it last was. */
    private static void anchor(float partial) {
        Minecraft mc = Minecraft.getInstance();
        anchor = Vec3.ZERO;
        if (keys == null || mc.level == null) return;
        for (Cinematics.Key k : keys) {
            if (!k.anchored()) continue;
            Entity e = k.entity() < 0 ? null : mc.level.getEntity(k.entity());
            if (e != null) lastAnchor = e.getPosition(partial);
            anchor = lastAnchor != null ? lastAnchor : Vec3.ZERO;
            return;
        }
    }

    /** The cut: the player is a player again, the world is drawn as before. */
    public static void stop() {
        keys = null;
        waiting = false;
        holding = false;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = wasHidingGui;
        if (renderBefore >= 0) {
            if (mc.options.renderDistance().get() != renderBefore) mc.options.renderDistance().set(renderBefore);
            renderBefore = -1;
        }
    }

    public static void clientTick(ClientTickEvent.Post event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        mc.getToasts().clear(); // no advancement toasts over the picture
        if (mc.screen instanceof ReceivingLevelScreen) mc.setScreen(null); // our black covers the loading after a dimension change
        noTarget(mc);
        anchor(1f);
        if (waiting) {
            waitTicks++;
            if (stageDrawn() || waitTicks >= MAX_WAIT) {
                waiting = false;
                tick = 0;
                if (mc.getConnection() != null) WakingNet.cineReady();
                WakingWorld.LOGGER.info("cine: stage drawn after {} ticks, rolling", waitTicks);
            }
            return;
        }
        if (holding) return;
        tick++;
        if (tick > length) holding = true; // black until the next path or the cut
    }

    /**
     * Have the chunks round the camera arrived, and are the surface sections it looks at built? Only
     * the columns in front of the camera count for the building - the renderer never builds what is
     * behind it - and a short settle follows the arrival so the last sections can finish.
     */
    private static boolean stageDrawn() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || keys == null) return false;
        Vec3 cam = pos(0);
        Vec3 look = target(keys.get(0));
        Vec3 dir = look.subtract(cam);
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double dx = flat < 1e-3 ? 0 : dir.x / flat, dz = flat < 1e-3 ? 0 : dir.z / flat;
        int radius = Math.min(ARRIVE_RADIUS, mc.options.getEffectiveRenderDistance());
        ChunkPos c = new ChunkPos(BlockPos.containing(cam));
        int total = 0, arrived = 0, front = 0, built = 0;
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                if (!inView(cx, cz, radius)) continue; // the server sends a disc, not the square
                total++;
                LevelChunk chunk = level.getChunkSource().getChunk(c.x + cx, c.z + cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                arrived++;
                if (Math.max(Math.abs(cx), Math.abs(cz)) > BUILD_RADIUS) continue;
                double px = ((c.x + cx) << 4) + 8 - cam.x, pz = ((c.z + cz) << 4) + 8 - cam.z;
                double dist = Math.sqrt(px * px + pz * pz);
                if (dist > 24 && (px * dx + pz * dz) / dist < FRONT_COS) continue; // outside the frustum: never built, never seen
                front++;
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
                if (mc.levelRenderer.isSectionCompiled(new BlockPos(((c.x + cx) << 4) + 8, Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), ((c.z + cz) << 4) + 8))) built++;
            }
        }
        boolean here = arrived >= total * ARRIVED;
        if (here && arrivedAt < 0) arrivedAt = waitTicks;
        boolean settled = here && waitTicks >= arrivedAt + SETTLE;
        // the renderer builds only what the camera can see; when the count stops growing, it is done
        if (built == lastBuilt) stillTicks++;
        else stillTicks = 0;
        lastBuilt = built;
        boolean drawn = front == 0 || built >= front * BUILT || (settled && stillTicks >= STILL);
        if (waitTicks % 20 == 0) WakingWorld.LOGGER.info("cine: waiting {} ticks - {}/{} chunks here, {}/{} sections in front built", waitTicks, arrived, total, built, front);
        return settled && drawn;
    }

    /** The server's own rule for which chunks a player gets (ChunkTrackingView: a disc, one chunk of slack). */
    private static boolean inView(int dx, int dz, int radius) {
        long i = Math.max(0, Math.abs(dx) - 1), j = Math.max(0, Math.abs(dz) - 1);
        return i * i + j * j < (long) radius * radius;
    }

    /** Where on the path we are: still at the start while waiting, at the end while holding. */
    private static float when(float partial) {
        if (waiting) return 0f;
        if (holding) return length;
        return Math.min(tick + partial, length);
    }

    /** Every frame, before the world is drawn: the player stands where the camera should. */
    public static void renderFrame(RenderFrameEvent.Pre event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        anchor(partial);
        float t = when(partial);
        Vec3 pos = position(t);
        Vec3 look = lookAt(t);
        // never under the ground, whatever moved under the camera since the keys were laid
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (pos.y < ground + 2.5) pos = new Vec3(pos.x, ground + 2.5, pos.z);
        Vec3 eye = pos; // the camera is the eye: the feet go where the eye would be if there were feet
        double ex = eye.x, ey = eye.y - player.getEyeHeight(), ez = eye.z;
        player.setPos(ex, ey, ez);
        player.xo = ex;
        player.yo = ey;
        player.zo = ez;
        player.xOld = ex;
        player.yOld = ey;
        player.zOld = ez;
        player.setDeltaMovement(Vec3.ZERO);
        Vec3 d = look.subtract(eye);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yBodyRot = player.yBodyRotO = yaw;
        player.yHeadRot = player.yHeadRotO = yaw;
    }

    public static void computeFov(ViewportEvent.ComputeFov event) {
        if (!active()) return;
        event.setFOV(fov(when((float) event.getPartialTick())));
    }

    /**
     * The camera looks at nothing: the crosshair's block and entity are dropped every tick and again just
     * before the HUD draws, so tooltip mods (Jade, WTHIT, ...) that read them have nothing to show.
     */
    private static void noTarget(Minecraft mc) {
        mc.hitResult = null;
        mc.crosshairPickEntity = null;
    }

    /** Before the HUD draws: nothing under the crosshair (the frame's own pick ran since the tick). */
    public static void guiPre(RenderGuiEvent.Pre event) {
        if (!active()) return;
        noTarget(Minecraft.getInstance());
    }

    /** The HUD goes, but the boss bar stays for the fights. */
    public static void guiLayer(RenderGuiLayerEvent.Pre event) {
        if (!active()) return;
        if (event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY) && !waiting && !holding) return;
        event.setCanceled(true);
    }

    /** The letterbox and the fades, over everything; black while the stage is drawn and between paths. */
    public static void guiPost(RenderGuiEvent.Post event) {
        if (!active()) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (waiting || holding) {
            g.fill(0, 0, w, h, 0xFF000000);
            return;
        }
        int bar = letterbox();
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        float t = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float a = 0f;
        if (fadeIn > 0 && t < fadeIn) a = 1f - t / fadeIn;
        if (fadeOut > 0 && t > length - fadeOut) a = Math.max(a, (t - (length - fadeOut)) / fadeOut);
        if (a > 0f) g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24));
    }

    // ------------------------------------------------------------------ the path

    private static int segment(float t) {
        for (int i = 0; i < keys.size() - 1; i++) if (t < keys.get(i + 1).tick()) return i;
        return keys.size() - 2;
    }

    /** A key's camera position - absolute, or riding with the anchor entity when the key says so. */
    private static Vec3 pos(int i) {
        Cinematics.Key k = keys.get(Mth.clamp(i, 0, keys.size() - 1));
        Vec3 p = new Vec3(k.x(), k.y(), k.z());
        return k.anchored() ? p.add(anchor) : p;
    }

    /** Catmull-Rom through the key positions, so the camera never turns a corner. */
    private static Vec3 position(float t) {
        if (keys.size() == 1) return pos(0);
        int i = Math.max(0, segment(t));
        Cinematics.Key a = keys.get(i), b = keys.get(i + 1);
        float u = b.tick() == a.tick() ? 1f : Mth.clamp((t - a.tick()) / (float) (b.tick() - a.tick()), 0f, 1f);
        Vec3 p0 = pos(i - 1), p1 = pos(i), p2 = pos(i + 1), p3 = pos(i + 2);
        double u2 = u * u, u3 = u2 * u;
        return new Vec3(
                0.5 * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
                0.5 * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3),
                0.5 * (2 * p1.z + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3));
    }

    private static Vec3 target(Cinematics.Key k) {
        Vec3 fixed = new Vec3(k.lx(), k.ly(), k.lz());
        if (k.entity() < 0) return fixed;
        if (k.anchored()) return fixed.add(anchor);
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(k.entity());
        return e == null ? (lastAnchor != null ? fixed.add(lastAnchor) : fixed) : e.position().add(fixed);
    }

    private static Vec3 lookAt(float t) {
        if (keys.size() == 1) return target(keys.get(0));
        int i = Math.max(0, segment(t));
        Cinematics.Key a = keys.get(i), b = keys.get(i + 1);
        float u = b.tick() == a.tick() ? 1f : Mth.clamp((t - a.tick()) / (float) (b.tick() - a.tick()), 0f, 1f);
        float s = u * u * (3 - 2 * u);
        return target(a).lerp(target(b), s);
    }

    private static float fov(float t) {
        if (keys.size() == 1) return keys.get(0).fov();
        int i = Math.max(0, segment(t));
        Cinematics.Key a = keys.get(i), b = keys.get(i + 1);
        float u = b.tick() == a.tick() ? 1f : Mth.clamp((t - a.tick()) / (float) (b.tick() - a.tick()), 0f, 1f);
        return Mth.lerp(u, a.fov(), b.fov());
    }
}
