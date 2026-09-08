package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The country under the Wayfarer's Chart.
 *
 * <p>The chart used to be squares on blank vellum, which told you the names of places but not one
 * thing about them: no coast, no mountain, no wood, no idea which way the river ran. It draws the
 * ground now, the way a map in this game is expected to - and it keeps drawing it, so a volcano
 * that grew last night, a crater, a tornado's swathe or a wall you built yourself all turn up on
 * the chart within a minute of you being near them.</p>
 *
 * <p><b>Where the colours come from.</b> The client already holds every block of every chunk in its
 * render distance, so nothing here asks the server for anything. It walks the chunks around the
 * player on a slow rolling sweep and samples the surface every {@value #RES} blocks with vanilla's
 * own map algorithm - the top block's {@link MapColor}, shaded by how much higher it stands than
 * its neighbour to the north, water shaded by depth instead - so the chart and a vanilla map agree
 * about what a birch forest looks like.</p>
 *
 * <p><b>Why a rolling sweep rather than block-change events.</b> A cataclysm changes tens of
 * thousands of blocks at once, and a hook on each of them would spend more time bookkeeping than
 * drawing. Re-reading everything near the player every couple of seconds costs about four thousand
 * height lookups a sweep, which is nothing, and it catches world changes, chunk loads, other
 * players' building and terrain the server quietly corrected, all by the same rule.</p>
 *
 * <p>Samples are kept in {@value #TILE}-block tiles of {@value #N}x{@value #N} and written under
 * {@code .minecraft/wakingworld/maps/<world>/<dimension>/}, so the country you explored is still
 * on the chart tomorrow. Tiles are saved on a background thread; nothing here blocks a frame.</p>
 */
public final class LandMap {
    /** How many blocks a tile covers, each way. */
    public static final int TILE = 256;
    /** Blocks per sample. At the chart's closest zoom one pixel is about eight blocks, so this is fine detail. */
    public static final int RES = 4;
    /** Samples along a tile's edge. */
    public static final int N = TILE / RES;

    /** How far from the player, in chunks, the sweep reaches. */
    private static final int REACH = 8;
    /** Chunks read per client tick. The whole reach is swept in about two and a half seconds. */
    private static final int PER_TICK = 6;
    /** Ticks between writes of whatever has changed. */
    private static final int SAVE_EVERY = 400;

    private LandMap() {
    }

    /** One tile: its samples, the texture they are uploaded to, and whether either is out of date. */
    private static final class Tile {
        final int tx, tz;
        final int[] argb = new int[N * N];
        DynamicTexture texture;
        ResourceLocation id;
        boolean changed;          // samples differ from the texture
        boolean unsaved;          // samples differ from the file

        Tile(int tx, int tz) {
            this.tx = tx;
            this.tz = tz;
        }
    }

    private static final Map<Long, Tile> tiles = new HashMap<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wakingworld-map");
        t.setDaemon(true);
        return t;
    });

    private static String world = "";
    private static Path dir;
    private static int cursor;
    private static int sinceSave;

    private static long key(int tx, int tz) {
        return ((long) tx << 32) | (tz & 0xFFFFFFFFL);
    }

    private static int tileOf(int block) {
        return Math.floorDiv(block, TILE);
    }

    // ---- the sweep ------------------------------------------------------------------------

    public static void clientTick(LevelTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getLevel() instanceof ClientLevel level) || mc.player == null) return;
        if (!where(mc, level)) return;

        int side = REACH * 2 + 1;
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        for (int i = 0; i < PER_TICK; i++) {
            int at = cursor++ % (side * side);
            int cx = pcx - REACH + at % side, cz = pcz - REACH + at / side;
            if (level.hasChunk(cx, cz)) read(level, cx, cz);
        }
        if (++sinceSave >= SAVE_EVERY) {
            sinceSave = 0;
            flush();
        }
    }

    /** Leaving a world: write what is left and let go of the textures. */
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        flush();
        Minecraft mc = Minecraft.getInstance();
        for (Tile t : tiles.values()) {
            if (t.id != null) mc.getTextureManager().release(t.id);
        }
        tiles.clear();
        world = "";
        dir = null;
        cursor = 0;
    }

    /** One chunk, sampled into whichever tiles it falls in. */
    private static void read(ClientLevel level, int cx, int cz) {
        ChunkAccess chunk = level.getChunk(cx, cz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        for (int ox = 0; ox < 16; ox += RES) {
            for (int oz = 0; oz < 16; oz += RES) {
                int x = (cx << 4) + ox, z = (cz << 4) + oz;
                Tile tile = tile(tileOf(x), tileOf(z));
                int i = Math.floorMod(x, TILE) / RES + Math.floorMod(z, TILE) / RES * N;
                int colour = sample(level, chunk, pos, x, z, minY);
                if (colour != tile.argb[i]) {
                    tile.argb[i] = colour;
                    tile.changed = true;
                    tile.unsaved = true;
                }
            }
        }
    }

    /**
     * One column, by vanilla's own map rules: down from the heightmap to the first block that has a
     * colour at all, water shaded by how deep it is and everything else by how much it stands above
     * the sample to the north. The checkerboard term is vanilla's too - it breaks up the banding a
     * flat plain would otherwise show.
     */
    private static int sample(ClientLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int minY) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
        BlockState state;
        int depth = 0;
        if (y <= minY + 1) {
            state = Blocks.BEDROCK.defaultBlockState();
            pos.set(x, minY, z);
        } else {
            do {
                y--;
                pos.set(x, y, z);
                state = chunk.getBlockState(pos);
            } while (state.getMapColor(level, pos) == MapColor.NONE && y > minY);
            if (y > minY && !state.getFluidState().isEmpty()) {
                int under = y - 1;
                BlockState below;
                do {
                    pos.set(x, under--, z);
                    below = chunk.getBlockState(pos);
                    depth++;
                } while (under > minY && !below.getFluidState().isEmpty());
            }
        }
        MapColor colour = state.getMapColor(level, pos);
        if (colour == MapColor.NONE) return 0;
        MapColor.Brightness shade;
        if (depth > 0) {
            double d = depth * 0.1 + ((x + z) & 1) * 0.2;
            shade = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        } else {
            int north = northOf(level, chunk, x, z, y);
            double d = (y - north) * 4.0 / 5.0 + (((x + z) & 1) - 0.5) * 0.4;
            shade = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        return colour.calculateRGBColor(shade);
    }

    /** The surface a sample north; the same height when that chunk is not here, so the shade is simply flat. */
    private static int northOf(ClientLevel level, ChunkAccess chunk, int x, int z, int fallback) {
        int nz = z - RES;
        if ((nz >> 4) == (z >> 4)) return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, nz & 15);
        if (!level.hasChunk(x >> 4, nz >> 4)) return fallback;
        return level.getChunk(x >> 4, nz >> 4).getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, nz & 15);
    }

    // ---- what the chart asks for ----------------------------------------------------------

    /** Every tile that has anything drawn in it, with its texture uploaded and up to date. */
    public static List<Drawn> drawable() {
        Minecraft mc = Minecraft.getInstance();
        List<Drawn> out = new ArrayList<>(tiles.size());
        for (Tile t : tiles.values()) {
            if (t.texture == null) {
                NativeImage image = new NativeImage(NativeImage.Format.RGBA, N, N, true);
                t.texture = new DynamicTexture(image);
                t.id = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "map/" + t.tx + "_" + t.tz);
                mc.getTextureManager().register(t.id, t.texture);
                t.changed = true;
            }
            if (t.changed) {
                NativeImage image = t.texture.getPixels();
                if (image != null) {
                    for (int i = 0; i < t.argb.length; i++) image.setPixelRGBA(i % N, i / N, t.argb[i]);
                    t.texture.upload();
                }
                t.changed = false;
            }
            out.add(new Drawn(t.tx, t.tz, t.id));
        }
        return out;
    }

    /** A tile ready to be blitted: where it is in the world, and the texture holding it. */
    public record Drawn(int tileX, int tileZ, ResourceLocation texture) {
    }

    public static boolean anything() {
        return !tiles.isEmpty();
    }

    // ---- kept on disk ----------------------------------------------------------------------

    /**
     * Which world this is, and where its tiles live. Returns false until that can be answered, which
     * is the tick or two between joining and the level being ready.
     */
    private static boolean where(Minecraft mc, ClientLevel level) {
        if (dir != null) return true;
        String name;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            name = "sp-" + safe(mc.getSingleplayerServer().getWorldData().getLevelName());
        } else {
            ServerData server = mc.getCurrentServer();
            name = "mp-" + safe(server == null ? "world" : server.ip);
        }
        world = name;
        dir = mc.gameDirectory.toPath().resolve("wakingworld").resolve("maps")
                .resolve(name).resolve(safe(level.dimension().location().toString()));
        return true;
    }

    private static String safe(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    /** The tile for a square, read off disk the first time it is asked for. */
    private static Tile tile(int tx, int tz) {
        return tiles.computeIfAbsent(key(tx, tz), k -> {
            Tile t = new Tile(tx, tz);
            load(t);
            return t;
        });
    }

    private static Path fileOf(Tile t) {
        return dir.resolve("t" + t.tx + "_" + t.tz + ".bin");
    }

    private static void load(Tile t) {
        if (dir == null) return;
        Path f = fileOf(t);
        if (!Files.isRegularFile(f)) return;
        try (InputStream raw = Files.newInputStream(f); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readInt() != N) return;                       // written by another resolution: start again
            for (int i = 0; i < t.argb.length; i++) t.argb[i] = in.readInt();
            t.changed = true;
        } catch (IOException e) {
            WakingWorld.LOGGER.warn("map: could not read {} ({})", f.getFileName(), e.toString());
        }
    }

    /** Write everything that has changed, off the render thread. */
    private static void flush() {
        if (dir == null) return;
        List<Tile> out = new ArrayList<>();
        for (Tile t : tiles.values()) {
            if (t.unsaved) {
                t.unsaved = false;
                out.add(t);
            }
        }
        if (out.isEmpty()) return;
        Path into = dir;
        List<int[]> copies = new ArrayList<>(out.size());
        List<Path> paths = new ArrayList<>(out.size());
        for (Tile t : out) {
            copies.add(t.argb.clone());                          // the sweep goes on writing into the original
            paths.add(fileOf(t));
        }
        WRITER.execute(() -> {
            try {
                Files.createDirectories(into);
            } catch (IOException e) {
                WakingWorld.LOGGER.warn("map: no folder to write into ({})", e.toString());
                return;
            }
            for (int i = 0; i < copies.size(); i++) {
                int[] argb = copies.get(i);
                try (OutputStream raw = Files.newOutputStream(paths.get(i));
                     DataOutputStream w = new DataOutputStream(new GZIPOutputStream(raw))) {
                    w.writeInt(N);
                    for (int v : argb) w.writeInt(v);
                } catch (IOException e) {
                    WakingWorld.LOGGER.warn("map: could not write {} ({})", paths.get(i).getFileName(), e.toString());
                }
            }
        });
    }
}
