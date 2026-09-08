package me.lovkar.wakingworld.land;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Named Lands: the world is divided into large squares, and each one is given a name the first
 * time somebody walks into it.
 *
 * <p>The name is written by Gemini when the server has a key ({@link GeminiLands}) from what the
 * ground actually is, and by {@link LandNames} from the same facts when it does not - so the
 * feature works the same either way, only better with a model. Once written a name never changes:
 * it is saved with the world, and everyone who crosses that line afterwards sees the same words.</p>
 *
 * <p>Crossing into one shows a title card, once per player per land. That is the whole of the
 * interruption; the rest of it lives in {@code /wakingworld lands} and in the Almanac.</p>
 */
public final class Lands extends SavedData {
    public static final String NAME = "wakingworld_lands";
    private static final Factory<Lands> FACTORY = new Factory<>(Lands::new, Lands::load, null);

    /**
     * A named piece of the world: not one square any more but a region of them.
     *
     * <p>{@code cell} is the square it was grown from, which is its name for ever after; {@code
     * cells} is every square it owns. {@code kind} is kept so the Almanac can show it without
     * asking the world again.</p>
     */
    public record Land(String name, String lore, String kind, long cell, long[] cells) {
        public int cellX() {
            return (int) (cell >> 32);
        }

        public int cellZ() {
            return (int) cell;
        }
    }

    /**
     * A place somebody wanted to be able to find again. Kept per player, in world coordinates, and
     * shown on the Wayfarer's Chart - the lands tell you what the country is, a pin tells you where
     * you left something.
     */
    public record Pin(String name, int x, int z) {
    }

    /** Which land owns each square. Many squares point at the same land. */
    private final Map<Long, Land> owner = new LinkedHashMap<>();
    /** The lands themselves, by the square each was grown from. */
    private final Map<Long, Land> lands = new LinkedHashMap<>();
    /** Who has already been shown which land, so nobody is told twice. */
    private final Map<java.util.UUID, java.util.Set<Long>> seen = new java.util.HashMap<>();
    /**
     * How long each player has stood in the land they are in: {@code {land, checks}}. A region has
     * a ragged edge, and clipping the corner of one on your way somewhere else is not an arrival.
     */
    private final transient Map<java.util.UUID, long[]> dwell = new java.util.HashMap<>();
    /** Each player's own pins, newest last. */
    private final Map<java.util.UUID, List<Pin>> pins = new java.util.HashMap<>();

    /** Checks (one every two seconds) somebody must be inside a land before it introduces itself. */
    private static final int DWELL = 3;

    private Lands() {
    }

    public static Lands get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int cellOf(int block) {
        return Math.floorDiv(block, WakingConfig.landSize());
    }

    // ---- the tick --------------------------------------------------------------------------

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!WakingConfig.namedLands()) return;
        if (level.getGameTime() % 40 != 0) return;               // twice a second is far more than enough
        Lands lands = get(level);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            lands.visit(level, p);
        }
    }

    /** One player, wherever they now are. */
    private void visit(ServerLevel level, ServerPlayer p) {
        int cx = cellOf(p.getBlockX());
        int cz = cellOf(p.getBlockZ());
        long k = key(cx, cz);
        Land land = owner.get(k);
        if (land == null) {
            land = name(level, cx, cz);
            if (land == null) return;                            // a model is writing it; ask again shortly
        }
        long[] d = dwell.computeIfAbsent(p.getUUID(), id -> new long[]{Long.MIN_VALUE, 0});
        if (d[0] != land.cell()) {
            d[0] = land.cell();
            d[1] = 1;
            return;
        }
        if (++d[1] < DWELL) return;
        java.util.Set<Long> mine = seen.computeIfAbsent(p.getUUID(), id -> new java.util.HashSet<>());
        if (!mine.add(land.cell())) return;
        setDirty();
        announce(p, land);
        me.lovkar.wakingworld.advancement.WakingTriggers.LAND_WALKED.get().trigger(p, mine.size());
        // The chart is craftable from the first day and nothing in the game mentions it, so the
        // one moment a player is certain to be thinking about the lands is the moment to say so.
        if (mine.size() == 1) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.chart.hint")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Show a land's card to one player whether or not they have seen it before, and without marking
     * it seen. For the camera: a trailer has to be able to shoot the same card twice.
     */
    public static void card(ServerPlayer p, Land land) {
        if (land != null) announce(p, land);
    }

    /**
     * The card, and a line in the chat log so it can be read again.
     *
     * <p>The card is drawn by the mod ({@code client/LandCard}) rather than sent as a vanilla title
     * and subtitle. A subtitle is laid out as a single line and is never wrapped, so a sentence of
     * lore ran off both edges of the screen and only its middle was ever legible.</p>
     */
    private static void announce(ServerPlayer p, Land land) {
        // the middle of the land, for the waypoint. The height is only asked for when that chunk is
        // really loaded: a heightmap read on a chunk that is not there answers the bottom of the
        // world, and a waypoint at bedrock renders as a beam through the floor.
        int size = WakingConfig.landSize();
        // the middle of the REGION, not of the square it was grown from - a land now spreads, and a
        // waypoint on its seed square can sit right on its edge
        long sx = 0, sz = 0;
        for (long c : land.cells()) {
            sx += (int) (c >> 32);
            sz += (int) c;
        }
        int n = Math.max(1, land.cells().length);
        int mx = (int) Math.round(sx / (double) n) * size + size / 2;
        int mz = (int) Math.round(sz / (double) n) * size + size / 2;
        ServerLevel level = p.serverLevel();
        int my = level.getChunkSource().getChunkNow(mx >> 4, mz >> 4) != null
                ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mx, mz)
                : p.getBlockY();
        BlockPos middle = new BlockPos(mx, my, mz);
        me.lovkar.wakingworld.network.WakingNet.landCard(p, land.name(), land.lore(), land.kind(), middle);
        // The card IS the announcement. The chat line under it was there so the lore could be read
        // again after the card faded, and it was the wrong place for that: it says the same thing
        // twice in the same second, and every land you walk into pushes your conversation up the
        // screen. The Wayfarer's Chart holds the lore of every land you have walked, and
        // /wakingworld lands lists them, so nothing is lost by keeping the log quiet.
        if (WakingConfig.landChat()) {
            p.sendSystemMessage(Component.literal("You have come into ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(land.name()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(". " + land.lore()).withStyle(ChatFormatting.DARK_GRAY)));
        }
        // this one stays: it is rare, it is not on the card, and it is news
        Component held = holds(level, middle, land);
        if (held != null) p.sendSystemMessage(held);
    }

    /**
     * What this country is known for, if anything.
     *
     * <p>A name on its own is a label, and a label is not a reason to walk anywhere. What makes a
     * land worth entering is that entering it tells you something - so if a giant was woken or
     * killed here, or the ground has not settled since, the card says so.</p>
     *
     * <p>It costs nothing: both answers are already in memory ({@link
     * me.lovkar.wakingworld.story.Chronicle} and {@link me.lovkar.wakingworld.cataclysm.Unrest}),
     * and neither is a structure search. A land nothing has happened in stays quiet, which is also
     * information.</p>
     */
    private static Component holds(ServerLevel level, BlockPos middle, Land land) {
        java.util.Set<Long> mine = new java.util.HashSet<>();
        if (land != null) for (long c : land.cells()) mine.add(c);
        String saw = null;
        for (String type : new String[]{"slain", "woken"}) {
            for (me.lovkar.wakingworld.story.Chronicle.Event e
                    : me.lovkar.wakingworld.story.Chronicle.get(level).near(middle, type, 16)) {
                if (!mine.contains(key(cellOf(e.x()), cellOf(e.z())))) continue;
                saw = "slain".equals(type)
                        ? "Something the size of a hill fell in this country."
                        : "Something was woken in this country.";
                break;
            }
            if (saw != null) break;
        }
        double unquiet = me.lovkar.wakingworld.cataclysm.Unrest.at(level, middle);
        if (saw == null && unquiet <= 0.0) return null;
        String line = saw == null ? "" : saw;
        if (unquiet > 0.35) {
            line = (line.isEmpty() ? "The ground here has not settled." : line + " The ground has not settled since.");
        }
        return Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    /**
     * The name for a cell: from the model if one is already written, from the templates otherwise.
     * Returns null exactly once per cell - while the model is being asked - so the card is shown
     * with the better name if it arrives, and with the template name a moment later if it does not.
     */
    private Land name(ServerLevel level, int cx, int cz) {
        long k = key(cx, cz);
        int size = WakingConfig.landSize();
        BlockPos middle = new BlockPos(cx * size + size / 2, 0, cz * size + size / 2);
        middle = middle.atY(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle).getY());
        LandNames.Kind kind = LandNames.kindAt(level, middle);

        GeminiLands.Result written = GeminiLands.poll(k);
        if (written != null && written.ok()) {
            return place(level, cx, cz, kind,
                    unused(written.name(), () -> LandNames.template(kind, RandomSource.create(k * 31L + lands.size()))),
                    written.lore(), "gemini");
        }
        if (written == null && GeminiLands.enabled() && GeminiLands.start(k, level, middle, kind)) {
            return null;                                         // asked: come back in half a second
        }
        RandomSource rnd = RandomSource.create(k * 0x9E3779B97F4A7C15L ^ level.getSeed());
        // say WHY the templates wrote it. A land named by the model and one named from the word lists
        // look exactly alike in the game, so "I have never seen Gemini name one" is unanswerable
        // without this line - and the answer is usually that the model was busy.
        return place(level, cx, cz, kind,
                unused(LandNames.template(kind, rnd), () -> LandNames.template(kind, rnd)),
                LandNames.templateLore(kind, rnd), "templates: " + GeminiLands.why());
    }

    /**
     * Grow a land out of the square somebody has just walked into, and write it down.
     *
     * <p>A land used to <b>be</b> one square of a fixed grid, and that was wrong in two ways a
     * player felt at once: every land was the same rectangle, and you crossed one every couple of
     * minutes, so three cards would arrive in a row and none of them meant anything.</p>
     *
     * <p>A land is a <b>region</b> now: the square walked into, plus every square reachable from it
     * over country of the same kind, up to {@code landCells}. The squares are much smaller than
     * they were, so the border follows the coast or the treeline instead of a ruler, and a region
     * is several times bigger than the old square, so a card is rare and is a real place.</p>
     */
    private Land place(ServerLevel level, int cx, int cz, LandNames.Kind kind, String name, String lore, String how) {
        long k = key(cx, cz);
        long[] region = spread(level, cx, cz, kind);
        Land land = new Land(name, lore, kind.name(), k, region);
        for (long c : region) owner.put(c, land);
        lands.put(k, land);
        setDirty();
        WakingWorld.LOGGER.info("lands: {} named {} ({}, {} squares)", cellName(cx, cz), land.name(), how, region.length);
        return land;
    }

    /**
     * The squares a land takes: a breadth-first spread from the one walked into, over unowned
     * neighbours of the same kind, stopping at {@code landCells} - and then, if that came to fewer
     * than {@code landMinCells}, over the nearest unclaimed squares of any kind, so no land is left
     * one square across.
     *
     * <p>It asks only for biomes. {@code getBiome} is answered by the generator's own biome source
     * when the chunk is not loaded, so nothing here generates terrain; a heightmap position would
     * have generated every square it touched, which for two dozen squares spread over a couple of
     * thousand blocks is a visible stall.</p>
     */
    private long[] spread(ServerLevel level, int cx, int cz, LandNames.Kind kind) {
        int max = Math.max(1, WakingConfig.landCells());
        int floor = Math.min(max, Math.max(1, WakingConfig.landMinCells()));
        java.util.LinkedHashSet<Long> region = new java.util.LinkedHashSet<>();
        region.add(key(cx, cz));
        int[] seed = {cx, cz};

        // first over country of the same kind, which is what gives a land the shape of its country
        grow(level, region, seed, max, kind);
        // and then, if the country changed at once, over the nearest unclaimed squares whatever they
        // are. Without this the map fills with one-square lands: at this square size a river through
        // a wood or a ridge across a plain is enough to change the kind from one square to the next,
        // so a walk of two minutes hands you four names and every box is too small to write them in.
        if (region.size() < floor) grow(level, region, seed, floor, null);

        long[] out = new long[region.size()];
        int i = 0;
        for (long c : region) out[i++] = c;
        return out;
    }

    /** Breadth-first out of the squares already taken, to {@code target}; a null kind takes anything. */
    private void grow(ServerLevel level, java.util.LinkedHashSet<Long> region, int[] seed, int target, LandNames.Kind kind) {
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (long c : region) queue.add(new int[]{(int) (c >> 32), (int) c});
        if (queue.isEmpty()) queue.add(seed);
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && region.size() < target) {
            int[] c = queue.poll();
            for (int[] d : around) {
                if (region.size() >= target) break;
                int nx = c[0] + d[0], nz = c[1] + d[1];
                long nk = key(nx, nz);
                if (region.contains(nk) || owner.containsKey(nk)) continue;
                if (kind != null && kindOfCell(level, nx, nz) != kind) continue;
                region.add(nk);
                queue.add(new int[]{nx, nz});
            }
        }
    }

    /** What kind of country a square is, without loading a chunk to find out. */
    private static LandNames.Kind kindOfCell(ServerLevel level, int cx, int cz) {
        int size = WakingConfig.landSize();
        return LandNames.kindAt(level, new BlockPos(cx * size + size / 2, level.getSeaLevel(), cz * size + size / 2));
    }

    /**
     * A name no other land in this world is already using. Two neighbours called the same thing reads
     * as a bug even when the dice were fair, so a clash is re-rolled a few times; if the word lists are
     * genuinely exhausted the name is kept and numbered rather than left blank.
     */
    private String unused(String first, java.util.function.Supplier<String> again) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (Land l : lands.values()) taken.add(l.name());
        String name = first;
        for (int i = 0; i < 12 && taken.contains(name); i++) name = again.get();
        if (!taken.contains(name)) return name;
        for (int n = 2; n < 100; n++) {
            String tried = first + " " + roman(n);
            if (!taken.contains(tried)) return tried;
        }
        return first;
    }

    private static String roman(int n) {
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[(n / 10) % 10] + ones[n % 10];
    }

    private static String cellName(int cx, int cz) {
        return "cell " + cx + "," + cz;
    }

    // ---- what the command and the Almanac ask for -------------------------------------------

    /** Every land this player has been in, newest last. */
    public List<Land> visited(ServerPlayer p) {
        java.util.Set<Long> mine = seen.get(p.getUUID());
        if (mine == null) return List.of();
        List<Land> out = new ArrayList<>();
        for (Land l : lands.values()) if (mine.contains(l.cell())) out.add(l);
        return out;
    }

    /**
     * Name the cell containing this position now, whether or not anyone has walked into it.
     * The command uses it; so does anything that wants a name before a player gets there.
     */
    public Land nameAt(ServerLevel level, BlockPos at) {
        int cx = cellOf(at.getX()), cz = cellOf(at.getZ());
        Land land = owner.get(key(cx, cz));
        if (land != null) return land;
        land = name(level, cx, cz);
        if (land != null) return land;
        // a model is writing it: wait for it rather than making the caller poll
        for (int i = 0; i < 50 && land == null; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            land = name(level, cx, cz);
        }
        return land;
    }

    /**
     * The land at a spot without waiting for one: the name if it is already written, else the writing
     * of it is started and null comes back. {@link #nameAt} sleeps the calling thread for up to 25
     * seconds waiting on the model, which is fine for a command and ruinous anywhere near a tick.
     */
    public Land nameSoon(ServerLevel level, BlockPos at) {
        int cx = cellOf(at.getX()), cz = cellOf(at.getZ());
        Land land = owner.get(key(cx, cz));
        return land != null ? land : name(level, cx, cz);
    }

    /** The land somebody is standing in, if it has a name yet. */
    public Land here(ServerPlayer p) {
        return owner.get(key(cellOf(p.getBlockX()), cellOf(p.getBlockZ())));
    }

    public int count() {
        return lands.size();
    }

    // ---- pins -------------------------------------------------------------------------------

    public List<Pin> pins(ServerPlayer p) {
        return pins.getOrDefault(p.getUUID(), List.of());
    }

    /** Put one down, or move it if a pin of that name is already there. Twenty is plenty. */
    public void pin(ServerPlayer p, String name, int x, int z) {
        String clean = name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').trim();
        if (clean.isEmpty()) clean = x + ", " + z;
        if (clean.length() > 40) clean = clean.substring(0, 40);
        List<Pin> mine = pins.computeIfAbsent(p.getUUID(), id -> new ArrayList<>());
        final String named = clean;
        mine.removeIf(pin -> pin.name().equalsIgnoreCase(named));
        while (mine.size() >= 20) mine.remove(0);
        mine.add(new Pin(clean, x, z));
        setDirty();
    }

    /** Take away whichever pin is nearest that spot, if one is close enough to have been meant. */
    public boolean unpin(ServerPlayer p, int x, int z) {
        List<Pin> mine = pins.get(p.getUUID());
        if (mine == null) return false;
        Pin best = null;
        long bestD = Long.MAX_VALUE;
        for (Pin pin : mine) {
            long dx = pin.x() - x, dz = pin.z() - z;
            long d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = pin; }
        }
        if (best == null || bestD > 64L * 64L) return false;
        mine.remove(best);
        setDirty();
        return true;
    }

    /**
     * Send one player the lands THEY have walked - not every land in the world. The atlas is a record
     * of where somebody has been, and a book that filled itself in with places its owner had never
     * seen would be a map, which is a different thing and a much less interesting one.
     */
    public static void sendAtlas(ServerPlayer p) {
        Lands lands = get(p.serverLevel());
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(WakingConfig.landSize()).append('\n');   // the scale, so the chart can lay the ground under it
        for (Pin pin : lands.pins(p)) {
            sb.append('@').append(pin.x()).append('\t').append(pin.z()).append('\t').append(pin.name()).append('\n');
        }
        java.util.Set<Long> mine = lands.seen.getOrDefault(p.getUUID(), java.util.Set.of());
        for (Land l : lands.lands.values()) {
            if (!mine.contains(l.cell())) continue;
            if (sb.length() > 60000) break;                    // the payload is a single string
            sb.append(l.name().replace('\t', ' ')).append('\t')
              .append(l.lore().replace('\t', ' ')).append('\t')
              .append(l.kind()).append('\t')
              .append(l.cellX()).append('\t').append(l.cellZ()).append('\t');
            // the shape, so the chart can draw the land's real border rather than a rectangle
            for (int i = 0; i < l.cells().length; i++) {
                if (i > 0) sb.append(';');
                sb.append((int) (l.cells()[i] >> 32)).append(',').append((int) l.cells()[i]);
            }
            sb.append('\n');
        }
        me.lovkar.wakingworld.network.WakingNet.atlas(p, sb.toString());
    }

    // ---- saved with the world --------------------------------------------------------------

    private static Lands load(CompoundTag tag, HolderLookup.Provider registries) {
        Lands l = new Lands();
        ListTag list = tag.getList("Lands", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            long cell = t.getLong("Cell");
            long[] cells = t.contains("Cells") ? t.getLongArray("Cells") : new long[]{cell};
            if (cells.length == 0) cells = new long[]{cell};
            Land land = new Land(t.getString("Name"), t.getString("Lore"), t.getString("Kind"), cell, cells);
            l.lands.put(cell, land);
            for (long c : cells) l.owner.put(c, land);
        }
        ListTag pinned = tag.getList("Pins", Tag.TAG_COMPOUND);
        for (int i = 0; i < pinned.size(); i++) {
            CompoundTag t = pinned.getCompound(i);
            List<Pin> mine = new ArrayList<>();
            ListTag list2 = t.getList("Marks", Tag.TAG_COMPOUND);
            for (int j = 0; j < list2.size(); j++) {
                CompoundTag m = list2.getCompound(j);
                mine.add(new Pin(m.getString("Name"), m.getInt("X"), m.getInt("Z")));
            }
            l.pins.put(t.getUUID("Id"), mine);
        }
        ListTag people = tag.getList("Seen", Tag.TAG_COMPOUND);
        for (int i = 0; i < people.size(); i++) {
            CompoundTag t = people.getCompound(i);
            java.util.Set<Long> mine = new java.util.HashSet<>();
            for (long c : t.getLongArray("Cells")) mine.add(c);
            l.seen.put(t.getUUID("Id"), mine);
        }
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Land land : lands.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Cell", land.cell());
            t.putLongArray("Cells", land.cells());
            t.putString("Name", land.name());
            t.putString("Lore", land.lore());
            t.putString("Kind", land.kind());
            list.add(t);
        }
        tag.put("Lands", list);
        ListTag people = new ListTag();
        seen.forEach((id, cells) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            long[] arr = new long[cells.size()];
            int i = 0;
            for (long c : cells) arr[i++] = c;
            t.putLongArray("Cells", arr);
            people.add(t);
        });
        tag.put("Seen", people);
        ListTag pinned = new ListTag();
        pins.forEach((id, mine) -> {
            if (mine.isEmpty()) return;
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            ListTag list2 = new ListTag();
            for (Pin pin : mine) {
                CompoundTag m = new CompoundTag();
                m.putString("Name", pin.name());
                m.putInt("X", pin.x());
                m.putInt("Z", pin.z());
                list2.add(m);
            }
            t.put("Marks", list2);
            pinned.add(t);
        });
        tag.put("Pins", pinned);
        return tag;
    }
}
