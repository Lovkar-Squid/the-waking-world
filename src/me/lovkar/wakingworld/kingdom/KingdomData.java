package me.lovkar.wakingworld.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What every kingdom remembers: who it is angry with (and until when), whether its king still
 * lives, whom the king has given the freedom of the treasury, and where the treasury is. Keyed by
 * the kingdom's centre. Guards and townsfolk carry their kingdom's centre and ask here.
 */
public class KingdomData extends SavedData {
    public static final String NAME = "wakingworld_kingdoms";
    private static final Factory<KingdomData> FACTORY = new Factory<>(KingdomData::new, KingdomData::load, null);
    /** How long a kingdom stays angry after the last offence: a Minecraft day. */
    public static final int ANGER_TICKS = 24000;

    public static final class Kingdom {
        public final BlockPos center;
        public final Map<UUID, Long> angryUntil = new HashMap<>();
        public final java.util.Set<UUID> permitted = new java.util.HashSet<>();
        /** No king on the throne right now (a successor is on the way, or none could be found yet). */
        public boolean kingDead;
        /** How many kings have followed the first; names the current one. */
        public int generation;
        /** Where the throne is (the seat's exact spot), and the game time when the next king is crowned. */
        public net.minecraft.world.phys.Vec3 throne;
        public long crownAt = -1;
        public BoundingBox treasury;

        Kingdom(BlockPos center) {
            this.center = center;
        }
    }

    private final Map<Long, Kingdom> kingdoms = new HashMap<>();

    public static KingdomData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Kingdom kingdom(BlockPos center) {
        return kingdoms.computeIfAbsent(center.asLong(), k -> {
            setDirty();
            return new Kingdom(center);
        });
    }

    public Kingdom kingdomAt(BlockPos pos) {
        Kingdom best = null;
        double bestD = Double.MAX_VALUE;
        for (Kingdom k : kingdoms.values()) {
            double d = k.center.distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best != null && bestD < 120 * 120 ? best : null;
    }

    public void setTreasury(BlockPos center, BoundingBox box) {
        kingdom(center).treasury = box;
        setDirty();
    }

    /** The kingdom whose treasury holds this position, or null. */
    public Kingdom treasuryAt(BlockPos pos) {
        for (Kingdom k : kingdoms.values()) if (k.treasury != null && k.treasury.isInside(pos)) return k;
        return null;
    }

    public void anger(ServerLevel level, BlockPos center, Player player, int ticks) {
        Kingdom k = kingdom(center);
        long until = level.getGameTime() + ticks;
        k.angryUntil.merge(player.getUUID(), until, Math::max);
        k.permitted.remove(player.getUUID());
        setDirty();
    }

    public boolean isAngry(ServerLevel level, BlockPos center, UUID player) {
        Kingdom k = kingdoms.get(center.asLong());
        if (k == null) return false;
        Long until = k.angryUntil.get(player);
        if (until == null) return false;
        if (until < level.getGameTime()) {
            k.angryUntil.remove(player);
            setDirty();
            return false;
        }
        return true;
    }

    public boolean isPermitted(BlockPos center, UUID player) {
        Kingdom k = kingdoms.get(center.asLong());
        return k != null && k.permitted.contains(player);
    }

    public void permit(BlockPos center, UUID player) {
        kingdom(center).permitted.add(player);
        setDirty();
    }

    public void kingDied(BlockPos center, long crownAt) {
        Kingdom k = kingdom(center);
        k.kingDead = true;
        k.crownAt = crownAt;
        k.permitted.clear(); // the new king gives his own leave
        setDirty();
    }

    /** The throne's seat, remembered when the first king is set on it. */
    public void setThrone(BlockPos center, net.minecraft.world.phys.Vec3 seat) {
        kingdom(center).throne = seat;
        setDirty();
    }

    /** A successor has been crowned. */
    public void crowned(BlockPos center) {
        Kingdom k = kingdom(center);
        k.kingDead = false;
        k.crownAt = -1;
        k.generation++;
        setDirty();
    }

    public int generation(BlockPos center) {
        Kingdom k = kingdoms.get(center.asLong());
        return k == null ? 0 : k.generation;
    }

    public java.util.Collection<Kingdom> all() {
        return kingdoms.values();
    }

    public boolean isKingDead(BlockPos center) {
        Kingdom k = kingdoms.get(center.asLong());
        return k != null && k.kingDead;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Kingdom k : kingdoms.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Center", k.center.asLong());
            t.putBoolean("KingDead", k.kingDead);
            t.putInt("Generation", k.generation);
            t.putLong("CrownAt", k.crownAt);
            if (k.throne != null) {
                t.putDouble("ThroneX", k.throne.x);
                t.putDouble("ThroneY", k.throne.y);
                t.putDouble("ThroneZ", k.throne.z);
            }
            if (k.treasury != null) t.putIntArray("Treasury", new int[]{k.treasury.minX(), k.treasury.minY(), k.treasury.minZ(), k.treasury.maxX(), k.treasury.maxY(), k.treasury.maxZ()});
            ListTag angry = new ListTag();
            for (Map.Entry<UUID, Long> e : k.angryUntil.entrySet()) {
                CompoundTag a = new CompoundTag();
                a.putUUID("Who", e.getKey());
                a.putLong("Until", e.getValue());
                angry.add(a);
            }
            t.put("Angry", angry);
            ListTag perm = new ListTag();
            for (UUID u : k.permitted) {
                CompoundTag a = new CompoundTag();
                a.putUUID("Who", u);
                perm.add(a);
            }
            t.put("Permitted", perm);
            list.add(t);
        }
        tag.put("Kingdoms", list);
        return tag;
    }

    private static KingdomData load(CompoundTag tag, HolderLookup.Provider registries) {
        KingdomData d = new KingdomData();
        ListTag list = tag.getList("Kingdoms", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Kingdom k = new Kingdom(BlockPos.of(t.getLong("Center")));
            k.kingDead = t.getBoolean("KingDead");
            k.generation = t.getInt("Generation");
            k.crownAt = t.contains("CrownAt") ? t.getLong("CrownAt") : -1;
            if (t.contains("ThroneX")) k.throne = new net.minecraft.world.phys.Vec3(t.getDouble("ThroneX"), t.getDouble("ThroneY"), t.getDouble("ThroneZ"));
            if (t.contains("Treasury")) {
                int[] b = t.getIntArray("Treasury");
                if (b.length == 6) k.treasury = new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
            }
            ListTag angry = t.getList("Angry", 10);
            for (int j = 0; j < angry.size(); j++) k.angryUntil.put(angry.getCompound(j).getUUID("Who"), angry.getCompound(j).getLong("Until"));
            ListTag perm = t.getList("Permitted", 10);
            for (int j = 0; j < perm.size(); j++) k.permitted.add(perm.getCompound(j).getUUID("Who"));
            d.kingdoms.put(k.center.asLong(), k);
        }
        return d;
    }
}
