package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.item.WakingItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * What the mage will sell you, and what it costs.
 *
 * <p>The five cataclysms happen TO a world. That is the whole design of them and it is why they
 * work - but it also means a player who wants to see one has to wait for the dice, and a player who
 * wants to see one <i>on purpose</i>, in a place of their choosing, has had no way to ask. The mage
 * is that way. He is not a friend and he is not a shop: he tells you the price of a thing that
 * ought not to be for sale, and then he lets you pay it.</p>
 *
 * <p><b>Every price has a Sleeper's Ember in it.</b> That is deliberate and it is the whole of the
 * gate: an ember comes out of a vault or out of star iron, so nobody calls a mountain down in their
 * first hour. The rest of each price is the cataclysm's own nature said in items - feathers and
 * wind for the column, deepslate and echo for the turning ground - so that reading the list tells
 * you what you are about to do even if you skipped every word he said.</p>
 */
public final class DarkRites {
    /** One thing he can be asked for. */
    public record Rite(String id, int colour, List<Cost> costs) {
        /** The translation key for its name, shared with the king's news and the Almanac. */
        public String nameKey() {
            return "cataclysm.wakingworld.name." + id;
        }
    }

    public record Cost(Item item, int count) {
    }

    public static final List<Rite> ALL = List.of(
            new Rite("meteor", 0xE8D9A0, List.of(
                    new Cost(WakingItems.SLEEPERS_EMBER.get(), 1),
                    new Cost(WakingItems.STAR_IRON.get(), 3),
                    new Cost(Items.AMETHYST_SHARD, 4),
                    new Cost(Items.GLOWSTONE_DUST, 8))),
            new Rite("volcano", 0xE07A2A, List.of(
                    new Cost(WakingItems.SLEEPERS_EMBER.get(), 1),
                    new Cost(Items.MAGMA_BLOCK, 8),
                    new Cost(Items.BLACKSTONE, 12),
                    new Cost(Items.LAVA_BUCKET, 1))),
            new Rite("tornado", 0xBFC8D2, List.of(
                    new Cost(WakingItems.SLEEPERS_EMBER.get(), 1),
                    new Cost(Items.FEATHER, 16),
                    new Cost(Items.PHANTOM_MEMBRANE, 4),
                    new Cost(Items.WIND_CHARGE, 4))),
            new Rite("quake", 0x7A6A58, List.of(
                    new Cost(WakingItems.SLEEPERS_EMBER.get(), 1),
                    new Cost(Items.DEEPSLATE, 16),
                    new Cost(Items.TUFF, 12),
                    new Cost(Items.ECHO_SHARD, 2))),
            new Rite("bloodmoon", 0xB03A34, List.of(
                    new Cost(WakingItems.SLEEPERS_EMBER.get(), 1),
                    new Cost(Items.ROTTEN_FLESH, 16),
                    new Cost(Items.BONE, 12),
                    new Cost(Items.REDSTONE_BLOCK, 2))));

    private DarkRites() {
    }

    public static Rite byId(String id) {
        for (Rite r : ALL) if (r.id().equals(id)) return r;
        return null;
    }

    public static Rite byIndex(int i) {
        return ALL.get(Math.floorMod(i, ALL.size()));
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return i;
        return 0;
    }
}
