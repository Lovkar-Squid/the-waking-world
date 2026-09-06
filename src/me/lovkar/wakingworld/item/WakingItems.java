package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * What a colossus leaves behind, and what wakes one. The Heart drops from every colossus (a
 * crafting material and a trophy that glows in the hand); each kind also drops its Sigil; the
 * six Sigils and a Heart make the Key that wakes the Titan in the End. The Hammer is a heavy
 * weapon forged from a Heart that slams the ground; the Horn of Waking calls the land's own
 * colossus up out of the ground wherever it is blown.
 */
public final class WakingItems {
    private WakingItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WakingWorld.MODID);

    public static final DeferredItem<Item> COLOSSUS_HEART = ITEMS.registerItem("colossus_heart",
            p -> new LoreItem(p, "item.wakingworld.colossus_heart.tooltip", true),
            new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    public static final DeferredItem<Item> SIGIL_STONE = sigil("stone");
    public static final DeferredItem<Item> SIGIL_EARTH = sigil("earth");
    public static final DeferredItem<Item> SIGIL_SANDSTONE = sigil("sandstone");
    public static final DeferredItem<Item> SIGIL_ICE = sigil("ice");
    public static final DeferredItem<Item> SIGIL_PRISMARINE = sigil("prismarine");
    public static final DeferredItem<Item> SIGIL_MOSS = sigil("moss");
    /** The seventh sigil, which no colossus drops: kept in the End's reliquaries, needed with the Key to wake the Titan. */
    public static final DeferredItem<Item> VOID_SIGIL = ITEMS.registerItem("void_sigil", p -> new LoreItem(p, "item.wakingworld.void_sigil.tooltip", true),
            new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(4));
    public static final DeferredItem<ColossusHammerItem> COLOSSUS_HAMMER = ITEMS.registerItem("colossus_hammer", ColossusHammerItem::new,
            new Item.Properties().rarity(Rarity.EPIC).fireResistant().durability(1561).attributes(ColossusHammerItem.attributes()));
    public static final DeferredItem<HornOfWakingItem> HORN_OF_WAKING = ITEMS.registerItem("horn_of_waking", HornOfWakingItem::new,
            new Item.Properties().rarity(Rarity.RARE).stacksTo(1));
    public static final DeferredItem<TitanKeyItem> TITAN_KEY = ITEMS.registerItem("titan_key", TitanKeyItem::new,
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant());
    public static final DeferredItem<HourglassItem> HOURGLASS = ITEMS.registerItem("hourglass_of_restoration", HourglassItem::new,
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(4).fireResistant());
    public static final DeferredItem<HeartOfTheEndItem> HEART_OF_THE_END = ITEMS.registerItem("heart_of_the_end", HeartOfTheEndItem::new,
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant());
    /** The rite's fuel - only the vaults have them. */
    public static final DeferredItem<Item> SLEEPERS_EMBER = ITEMS.registerItem("sleepers_ember",
            p -> new LoreItem(p, "item.wakingworld.sleepers_ember.tooltip", true), new Item.Properties().rarity(Rarity.RARE).fireResistant());
    public static final DeferredItem<Item> RUNE_STONE = rune("stone");
    public static final DeferredItem<Item> RUNE_EARTH = rune("earth");
    public static final DeferredItem<Item> RUNE_SANDSTONE = rune("sandstone");
    public static final DeferredItem<Item> RUNE_ICE = rune("ice");
    public static final DeferredItem<Item> RUNE_PRISMARINE = rune("prismarine");
    public static final DeferredItem<Item> RUNE_MOSS = rune("moss");

    /** A colossus' own theme, pressed onto a disc: every giant leaves its music behind when it falls (the Titan its own). */
    public static final DeferredItem<Item> DISC_STONE = disc("stone");
    public static final DeferredItem<Item> DISC_EARTH = disc("earth");
    public static final DeferredItem<Item> DISC_SANDSTONE = disc("sandstone");
    public static final DeferredItem<Item> DISC_ICE = disc("ice");
    public static final DeferredItem<Item> DISC_PRISMARINE = disc("prismarine");
    public static final DeferredItem<Item> DISC_MOSS = disc("moss");
    public static final DeferredItem<Item> DISC_TITAN = disc("titan");

    private static DeferredItem<Item> disc(String kind) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.JukeboxSong> song = net.minecraft.resources.ResourceKey.create(
                Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "colossus_" + kind));
        return ITEMS.registerItem("music_disc_" + kind, Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song));
    }

    private static final Map<String, DeferredItem<Item>> DISCS = Map.of(
            "stone", DISC_STONE, "earth", DISC_EARTH, "sandstone", DISC_SANDSTONE, "ice", DISC_ICE,
            "prismarine", DISC_PRISMARINE, "moss", DISC_MOSS, "titan", DISC_TITAN);

    /** The music disc a colossus of this kind leaves behind, or null. */
    public static Item discFor(String kind) {
        DeferredItem<Item> d = DISCS.get(kind);
        return d == null ? null : d.get();
    }

    public static List<DeferredItem<Item>> discs() {
        return List.of(DISC_STONE, DISC_EARTH, DISC_SANDSTONE, DISC_ICE, DISC_PRISMARINE, DISC_MOSS, DISC_TITAN);
    }

    private static DeferredItem<Item> rune(String kind) {
        return ITEMS.registerItem("rune_" + kind, p -> new LoreItem(p, "item.wakingworld.rune.tooltip", false), new Item.Properties().rarity(Rarity.UNCOMMON));
    }

    /** The story: letters the people who left wrote (written for the place they are found in), and the guide. */
    public static final DeferredItem<me.lovkar.wakingworld.story.DeadLetterItem> DEAD_LETTER = ITEMS.registerItem("dead_letter", me.lovkar.wakingworld.story.DeadLetterItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<me.lovkar.wakingworld.story.AlmanacItem> ALMANAC = ITEMS.registerItem("almanac", me.lovkar.wakingworld.story.AlmanacItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> STONE_THRALL_EGG = ITEMS.registerItem("stone_thrall_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(WakingWorld.STONE_THRALL, 0x6E6A70, 0xFF8A2A, p), new Item.Properties());
    public static final DeferredItem<Item> EMBER_WRAITH_EGG = ITEMS.registerItem("ember_wraith_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(WakingWorld.EMBER_WRAITH, 0x2A1E1C, 0xFF6A1A, p), new Item.Properties());
    public static final DeferredItem<Item> RUNE_SENTINEL_EGG = ITEMS.registerItem("rune_sentinel_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(WakingWorld.RUNE_SENTINEL, 0x5A5A66, 0x66D8FF, p), new Item.Properties());
    public static final DeferredItem<Item> DROWNED_KEEPER_EGG = ITEMS.registerItem("drowned_keeper_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(WakingWorld.DROWNED_KEEPER, 0x2C6E68, 0x9AE0D8, p), new Item.Properties());

    public static List<DeferredItem<Item>> runes() {
        return List.of(RUNE_STONE, RUNE_EARTH, RUNE_SANDSTONE, RUNE_ICE, RUNE_PRISMARINE, RUNE_MOSS);
    }

    /** The mod's own creative tab. */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WakingWorld.MODID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("wakingworld",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.wakingworld"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(COLOSSUS_HEART.get()))
                    .displayItems((params, out) -> {
                        out.accept(ALMANAC.get());
                        out.accept(DEAD_LETTER.get());
                        out.accept(COLOSSUS_HEART.get());
                        for (DeferredItem<Item> sg : sigils()) out.accept(sg.get());
                        out.accept(VOID_SIGIL.get());
                        out.accept(COLOSSUS_HAMMER.get());
                        out.accept(HORN_OF_WAKING.get());
                        out.accept(TITAN_KEY.get());
                        out.accept(HOURGLASS.get());
                        out.accept(HEART_OF_THE_END.get());
                        out.accept(SLEEPERS_EMBER.get());
                        for (DeferredItem<Item> r : runes()) out.accept(r.get());
                        for (DeferredItem<Item> d : discs()) out.accept(d.get());
                        out.accept(me.lovkar.wakingworld.ritual.WakingRitual.ALTAR_ITEM.get());
                        out.accept(me.lovkar.wakingworld.kingdom.KingdomBlocks.THRONE_ITEM.get());
                        out.accept(STONE_THRALL_EGG.get());
                        out.accept(EMBER_WRAITH_EGG.get());
                        out.accept(RUNE_SENTINEL_EGG.get());
                        out.accept(DROWNED_KEEPER_EGG.get());
                    })
                    .build());

    private static final Map<String, DeferredItem<Item>> SIGILS = Map.of(
            "stone", SIGIL_STONE, "earth", SIGIL_EARTH, "sandstone", SIGIL_SANDSTONE,
            "ice", SIGIL_ICE, "prismarine", SIGIL_PRISMARINE, "moss", SIGIL_MOSS);

    private static DeferredItem<Item> sigil(String kind) {
        return ITEMS.registerItem("sigil_" + kind, p -> new LoreItem(p, "item.wakingworld.sigil.tooltip", false),
                new Item.Properties().rarity(Rarity.RARE).fireResistant());
    }

    /** The sigil a kind drops, or null (the Titan drops none - it is the end of the road). */
    public static Item sigilFor(String kind) {
        DeferredItem<Item> d = SIGILS.get(kind);
        return d == null ? null : d.get();
    }

    public static List<DeferredItem<Item>> sigils() {
        return List.of(SIGIL_STONE, SIGIL_EARTH, SIGIL_SANDSTONE, SIGIL_ICE, SIGIL_PRISMARINE, SIGIL_MOSS);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(WakingItems::creativeTabs);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ColossusHammerItem::onFall);
    }

    private static ResourceKey<CreativeModeTab> tab(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(name));
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == tab("combat")) {
            event.accept(COLOSSUS_HAMMER);
        } else if (event.getTabKey() == tab("ingredients")) {
            event.accept(COLOSSUS_HEART);
            for (DeferredItem<Item> s : sigils()) event.accept(s);
            event.accept(SLEEPERS_EMBER);
            for (DeferredItem<Item> r : runes()) event.accept(r);
        } else if (event.getTabKey() == tab("tools_and_utilities")) {
            event.accept(HORN_OF_WAKING);
            event.accept(TITAN_KEY);
            event.accept(HOURGLASS);
            event.accept(HEART_OF_THE_END);
            event.accept(ALMANAC);
        }
    }
}
