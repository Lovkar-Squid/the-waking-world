package me.lovkar.wakingworld.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * What has happened in this world: giants woken and slain, rites performed - the last sixty-four
 * events, with where and when and who. The letters (and later the king) speak of them, so a letter
 * found after a fight can mention "the giant of moss that fell three hundred paces west".
 */
public class Chronicle extends SavedData {
    public static final String NAME = "wakingworld_chronicle";
    private static final Factory<Chronicle> FACTORY = new Factory<>(Chronicle::new, Chronicle::load, null);
    private static final int KEEP = 64;

    public record Event(String type, String kind, int x, int z, long day, String who) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Type", type);
            t.putString("Kind", kind);
            t.putInt("X", x);
            t.putInt("Z", z);
            t.putLong("Day", day);
            t.putString("Who", who);
            return t;
        }

        static Event load(CompoundTag t) {
            return new Event(t.getString("Type"), t.getString("Kind"), t.getInt("X"), t.getInt("Z"), t.getLong("Day"), t.getString("Who"));
        }
    }

    private final List<Event> events = new ArrayList<>();

    public static Chronicle get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static void record(ServerLevel level, String type, String kind, BlockPos at, String who) {
        Chronicle c = get(level);
        c.events.add(new Event(type, kind, at.getX(), at.getZ(), level.getDayTime() / 24000L, who == null ? "" : who));
        while (c.events.size() > KEEP) c.events.remove(0);
        c.setDirty();
    }

    public List<Event> events() {
        return events;
    }

    /** The events of a type nearest to a place, newest first, at most {@code n}. */
    public List<Event> near(BlockPos at, String type, int n) {
        List<Event> out = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && out.size() < n; i--) {
            Event e = events.get(i);
            if (type == null || e.type.equals(type)) out.add(e);
        }
        out.sort((a, b) -> Double.compare(dist(a, at), dist(b, at)));
        return out;
    }

    private static double dist(Event e, BlockPos at) {
        double dx = e.x - at.getX(), dz = e.z - at.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Event e : events) list.add(e.save());
        tag.put("Events", list);
        return tag;
    }

    private static Chronicle load(CompoundTag tag, HolderLookup.Provider registries) {
        Chronicle c = new Chronicle();
        ListTag list = tag.getList("Events", 10);
        for (int i = 0; i < list.size(); i++) c.events.add(Event.load(list.getCompound(i)));
        return c;
    }
}
