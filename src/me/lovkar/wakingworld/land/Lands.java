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

    /** A named piece of the world. {@code kind} is kept so the Almanac can show it without asking the world again. */
    public record Land(String name, String lore, String kind, long cell) {
        public int cellX() {
            return (int) (cell >> 32);
        }

        public int cellZ() {
            return (int) cell;
        }
    }

    private final Map<Long, Land> named = new LinkedHashMap<>();
    /** Who has already been shown which land, so nobody is told twice. */
    private final Map<java.util.UUID, java.util.Set<Long>> seen = new java.util.HashMap<>();

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
        Land land = named.get(k);
        if (land == null) {
            land = name(level, cx, cz);
            if (land == null) return;                            // a model is writing it; ask again shortly
        }
        java.util.Set<Long> mine = seen.computeIfAbsent(p.getUUID(), id -> new java.util.HashSet<>());
        if (!mine.add(k)) return;
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
        int mx = land.cellX() * size + size / 2, mz = land.cellZ() * size + size / 2;
        ServerLevel level = p.serverLevel();
        int my = level.getChunkSource().getChunkNow(mx >> 4, mz >> 4) != null
                ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mx, mz)
                : p.getBlockY();
        BlockPos middle = new BlockPos(mx, my, mz);
        me.lovkar.wakingworld.network.WakingNet.landCard(p, land.name(), land.lore(), land.kind(), middle);
        p.sendSystemMessage(Component.literal("You have come into ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(land.name()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(". " + land.lore()).withStyle(ChatFormatting.DARK_GRAY)));
        Component held = holds(level, middle);
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
    private static Component holds(ServerLevel level, BlockPos middle) {
        int size = WakingConfig.landSize();
        int cx = cellOf(middle.getX()), cz = cellOf(middle.getZ());
        String saw = null;
        for (String type : new String[]{"slain", "woken"}) {
            for (me.lovkar.wakingworld.story.Chronicle.Event e
                    : me.lovkar.wakingworld.story.Chronicle.get(level).near(middle, type, 8)) {
                if (cellOf(e.x()) != cx || cellOf(e.z()) != cz) continue;
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
            Land land = new Land(unused(written.name(), () -> LandNames.template(kind, RandomSource.create(k * 31L + named.size()))),
                    written.lore(), kind.name(), k);
            named.put(k, land);
            setDirty();
            WakingWorld.LOGGER.info("lands: {} named {} ({})", cellName(cx, cz), land.name(), "gemini");
            return land;
        }
        if (written == null && GeminiLands.enabled() && GeminiLands.start(k, level, middle, kind)) {
            return null;                                         // asked: come back in half a second
        }
        RandomSource rnd = RandomSource.create(k * 0x9E3779B97F4A7C15L ^ level.getSeed());
        Land land = new Land(unused(LandNames.template(kind, rnd), () -> LandNames.template(kind, rnd)),
                LandNames.templateLore(kind, rnd), kind.name(), k);
        named.put(k, land);
        setDirty();
        WakingWorld.LOGGER.info("lands: {} named {} ({})", cellName(cx, cz), land.name(), "templates");
        return land;
    }

    /**
     * A name no other land in this world is already using. Two neighbours called the same thing reads
     * as a bug even when the dice were fair, so a clash is re-rolled a few times; if the word lists are
     * genuinely exhausted the name is kept and numbered rather than left blank.
     */
    private String unused(String first, java.util.function.Supplier<String> again) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (Land l : named.values()) taken.add(l.name());
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
        for (Land l : named.values()) if (mine.contains(l.cell())) out.add(l);
        return out;
    }

    /**
     * Name the cell containing this position now, whether or not anyone has walked into it.
     * The command uses it; so does anything that wants a name before a player gets there.
     */
    public Land nameAt(ServerLevel level, BlockPos at) {
        int cx = cellOf(at.getX()), cz = cellOf(at.getZ());
        Land land = named.get(key(cx, cz));
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
        Land land = named.get(key(cx, cz));
        return land != null ? land : name(level, cx, cz);
    }

    /** The land somebody is standing in, if it has a name yet. */
    public Land here(ServerPlayer p) {
        return named.get(key(cellOf(p.getBlockX()), cellOf(p.getBlockZ())));
    }

    public int count() {
        return named.size();
    }

    /**
     * Send one player the lands THEY have walked - not every land in the world. The atlas is a record
     * of where somebody has been, and a book that filled itself in with places its owner had never
     * seen would be a map, which is a different thing and a much less interesting one.
     */
    public static void sendAtlas(ServerPlayer p) {
        Lands lands = get(p.serverLevel());
        StringBuilder sb = new StringBuilder();
        java.util.Set<Long> mine = lands.seen.getOrDefault(p.getUUID(), java.util.Set.of());
        for (Land l : lands.named.values()) {
            if (!mine.contains(l.cell())) continue;
            if (sb.length() > 30000) break;                    // the payload is a single string
            sb.append(l.name().replace('\t', ' ')).append('\t')
              .append(l.lore().replace('\t', ' ')).append('\t')
              .append(l.kind()).append('\t')
              .append(l.cellX()).append('\t').append(l.cellZ()).append('\n');
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
            l.named.put(cell, new Land(t.getString("Name"), t.getString("Lore"), t.getString("Kind"), cell));
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
        for (Land land : named.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Cell", land.cell());
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
        return tag;
    }
}
