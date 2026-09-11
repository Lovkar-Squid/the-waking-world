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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.Vec3;

public class KingdomData extends SavedData {
    public static final String NAME = "wakingworld_kingdoms";
    private static final Factory<KingdomData> FACTORY = new Factory<>(KingdomData::new, KingdomData::load, null);
    /** How long a kingdom stays angry after the last offence: a Minecraft day. */
    public static final int ANGER_TICKS = 24000;

    public static final class Kingdom {
        public final BlockPos center;
        public final Map<UUID, Long> angryUntil = new HashMap<>();
        public final Set<UUID> permitted = new HashSet<>();
        public boolean kingDead;
        public int generation;
        public Vec3 throne;
        public long crownAt = -1L;
        public BoundingBox treasury;
        public boolean tidied;
        public int standing;
        public int tier = 1;
        public int dressed = 1;
        public long reviewedAt = -1L;
        public final Set<Long> claims = new LinkedHashSet<>();
        public boolean rebuilding;
        public int watchX = Integer.MIN_VALUE;
        public long watchUntil;
        public final Set<Long> works = new LinkedHashSet<>();
        public String chargeKind = "";
        public String chargeItem = "";
        public int chargeCount;
        public int chargeGot;
        public long chargeAt;
        public int chargesPaid;
        public long knownTower;
        public int wallArcs;
        public final Set<Long> catapults = new LinkedHashSet<>();
        /** The doorsteps of the houses outside the walls, in the order they were raised (see {@link KingdomHouses}). */
        public final Set<Long> houses = new LinkedHashSet<>();
        /** Lane slots that were tried and found unfit (water, a cliff, somebody's build) - never tried again. */
        public final Set<Integer> badSlots = new LinkedHashSet<>();
        /** Which roads (bits 0-3) and back lanes (bits 4-7) the suburb has paved. */
        public int lanes;

        Kingdom(BlockPos var1) {
            this.center = var1;
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

    public int moveStanding(BlockPos var1, int var2) {
        KingdomData.Kingdom var3 = this.kingdom(var1);
        var3.standing = Math.max(-100, Math.min(100, var3.standing + var2));
        this.setDirty();
        return var3.standing;
    }

    public int standing(BlockPos var1) {
        KingdomData.Kingdom var2 = this.kingdoms.get(var1.asLong());
        return var2 == null ? 0 : var2.standing;
    }

    public int tier(BlockPos var1) {
        KingdomData.Kingdom var2 = this.kingdoms.get(var1.asLong());
        return var2 == null ? 1 : var2.tier;
    }

    public static int tierFor(int var0, int var1) {
        int var2 = var0 >= 80 ? 4 : (var0 >= 45 ? 3 : (var0 >= 15 ? 2 : 1));
        int var3 = var0 >= 72 ? 4 : (var0 >= 37 ? 3 : (var0 >= 7 ? 2 : 1));
        if (var2 > var1) {
            return var1 + 1;
        } else {
            return var3 < var1 ? var1 - 1 : var1;
        }
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
            t.putBoolean("Tidied", k.tidied);
            t.putInt("Generation", k.generation);
            t.putLong("CrownAt", k.crownAt);
            t.putInt("Standing", k.standing);
            t.putInt("Tier", k.tier);
            t.putInt("Dressed", k.dressed);
            t.putLong("ReviewedAt", k.reviewedAt);
            t.putString("ChargeKind", k.chargeKind);
            t.putString("ChargeItem", k.chargeItem);
            t.putInt("ChargeCount", k.chargeCount);
            t.putInt("ChargeGot", k.chargeGot);
            t.putLong("ChargeAt", k.chargeAt);
            t.putInt("ChargesPaid", k.chargesPaid);
            t.putLong("KnownTower", k.knownTower);
            t.putInt("WallArcs", k.wallArcs);
            if (!k.catapults.isEmpty()) t.putLongArray("Catapults", longs(k.catapults));
            if (!k.works.isEmpty()) t.putLongArray("Works", longs(k.works));
            if (!k.claims.isEmpty()) t.putLongArray("Claims", longs(k.claims));
            if (!k.houses.isEmpty()) t.putLongArray("Houses", longs(k.houses));
            if (!k.badSlots.isEmpty()) t.putIntArray("BadSlots", k.badSlots.stream().mapToInt(Integer::intValue).toArray());
            t.putInt("Lanes", k.lanes);
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

    private static long[] longs(java.util.Collection<Long> c) {
        long[] out = new long[c.size()];
        int i = 0;
        for (long v : c) out[i++] = v;
        return out;
    }

    private static KingdomData load(CompoundTag tag, HolderLookup.Provider registries) {
        KingdomData d = new KingdomData();
        ListTag list = tag.getList("Kingdoms", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Kingdom k = new Kingdom(BlockPos.of(t.getLong("Center")));
            k.kingDead = t.getBoolean("KingDead");
            k.tidied = t.getBoolean("Tidied");
            k.generation = t.getInt("Generation");
            k.crownAt = t.contains("CrownAt") ? t.getLong("CrownAt") : -1;
            k.standing = t.getInt("Standing");
            k.tier = Math.max(1, t.contains("Tier") ? t.getInt("Tier") : 1);
            k.dressed = Math.max(1, t.contains("Dressed") ? t.getInt("Dressed") : 1);
            k.reviewedAt = t.contains("ReviewedAt") ? t.getLong("ReviewedAt") : -1;
            k.chargeKind = t.getString("ChargeKind");
            k.chargeItem = t.getString("ChargeItem");
            k.chargeCount = t.getInt("ChargeCount");
            k.chargeGot = t.getInt("ChargeGot");
            k.chargeAt = t.getLong("ChargeAt");
            k.chargesPaid = t.getInt("ChargesPaid");
            k.knownTower = t.getLong("KnownTower");
            k.wallArcs = t.getInt("WallArcs");
            for (long c : t.getLongArray("Catapults")) k.catapults.add(c);
            for (long c : t.getLongArray("Claims")) k.claims.add(c);
            for (long c : t.getLongArray("Works")) k.works.add(c);
            for (long c : t.getLongArray("Houses")) k.houses.add(c);
            for (int c : t.getIntArray("BadSlots")) k.badSlots.add(c);
            k.lanes = t.getInt("Lanes");
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
