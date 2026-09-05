package me.lovkar.wakingworld.ritual;

import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.item.WakingItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * What each rite asks for. Every shrine's altar wants a Sleeper's Ember (only the vaults have
 * them), the Rune of its own kind (vaults again) and something of the land itself; the arena's
 * altar wants the Key of the Titan, the Void Sigil out of a reliquary and the egg the dragon left,
 * and the six lesser altars round it each want two runes of their land - the End does not wake for
 * less. Sound the Horn of Waking over a full altar and the land answers.
 */
public final class Rites {
    private Rites() {
    }

    public record Offering(Item item, int count) {
        public boolean matches(ItemStack stack) {
            return stack.is(item);
        }
    }

    public static final int TICKS = 220;

    /** The six lands, in the order their lesser altars stand round the Titan's (30, 90, ... 330 degrees). */
    public static final String[] LANDS = {"stone", "earth", "sandstone", "ice", "prismarine", "moss"};
    /** What each lesser altar of the arena wants by default: this many runes of its land (config `lesserAltarRunes`). */
    public static final int LESSER_RUNES = 2;

    /** The lesser altars of the arena are altars of kind {@code titan_<land>}: one for each land, round the great one. */
    public static boolean lesser(String kind) {
        return kind.startsWith("titan_");
    }

    /** The land an altar speaks for: {@code titan_ice} -> {@code ice}; the great altar and the shrines are their own. */
    public static String base(String kind) {
        return lesser(kind) ? kind.substring(6) : kind;
    }

    /** The rune cut for a land. */
    public static Item runeFor(String land) {
        return switch (land) {
            case "earth" -> WakingItems.RUNE_EARTH.get();
            case "sandstone" -> WakingItems.RUNE_SANDSTONE.get();
            case "ice" -> WakingItems.RUNE_ICE.get();
            case "prismarine" -> WakingItems.RUNE_PRISMARINE.get();
            case "moss" -> WakingItems.RUNE_MOSS.get();
            default -> WakingItems.RUNE_STONE.get();
        };
    }

    /** The land's gift for a shrine kind, at its default count (the config scales it). */
    public static Offering gift(String kind) {
        return switch (kind) {
            case "earth" -> new Offering(Items.ROOTED_DIRT, 8);
            case "sandstone" -> new Offering(Items.GOLD_INGOT, 4);
            case "ice" -> new Offering(Items.BLUE_ICE, 4);
            case "prismarine" -> new Offering(Items.PRISMARINE_CRYSTALS, 8);
            case "moss" -> new Offering(Items.GLOW_BERRIES, 8);
            default -> new Offering(Items.AMETHYST_SHARD, 4);
        };
    }

    /**
     * What an altar wants, as the server's config has it ({@code [rites]}): the shrines an ember, the
     * rune of their kind and the land's gift; the arena's great altar the Key and, unless switched
     * off, the Void Sigil and the Dragon Egg; each lesser altar so many runes of its land (none:
     * the lesser altars are not in the rite). Counts scale with {@code riteCostMultiplier}.
     */
    public static List<Offering> offerings(String kind) {
        if (lesser(kind)) {
            int runes = me.lovkar.wakingworld.WakingConfig.lesserAltarRunes();
            return runes <= 0 ? List.of() : List.of(new Offering(runeFor(base(kind)), runes));
        }
        if ("titan".equals(kind)) {
            List<Offering> out = new java.util.ArrayList<>();
            out.add(new Offering(WakingItems.TITAN_KEY.get(), 1));
            if (me.lovkar.wakingworld.WakingConfig.titanNeedsSigil()) out.add(new Offering(WakingItems.VOID_SIGIL.get(), 1));
            if (me.lovkar.wakingworld.WakingConfig.titanNeedsEgg()) out.add(new Offering(Items.DRAGON_EGG, 1));
            return out;
        }
        Offering gift = gift(kind);
        return List.of(new Offering(WakingItems.SLEEPERS_EMBER.get(), me.lovkar.wakingworld.WakingConfig.riteEmbers()),
                new Offering(runeFor(kind), me.lovkar.wakingworld.WakingConfig.riteRunes()),
                new Offering(gift.item(), me.lovkar.wakingworld.WakingConfig.riteGift(gift.count())));
    }

    public static Palette palette(String kind) {
        Palette p = Palette.preset(kind);
        return p == null ? Palette.STONE : p;
    }

    public static int height(String kind) {
        return "titan".equals(kind) ? 72 : 40;
    }

    /** The kind's colour (0xRRGGBB) for its beam, runes and sparks. */
    public static int color(String kind) {
        return switch (base(kind)) {
            case "earth" -> 0xB8843C;
            case "sandstone" -> 0xF2D27A;
            case "ice" -> 0xA6E6FF;
            case "prismarine" -> 0x62D8C8;
            case "moss" -> 0x8CE664;
            case "titan" -> 0xB266FF;
            default -> 0xD8CFC0;
        };
    }
}
