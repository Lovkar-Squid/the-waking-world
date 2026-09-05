package me.lovkar.wakingworld.story;

import com.mojang.datafixers.util.Pair;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.item.WakingItems;
import me.lovkar.wakingworld.ritual.Rites;
import me.lovkar.wakingworld.worldgen.ShrineStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dead Letters: what the people who lived here wrote before they left, found in the ruins and
 * the vaults. Every letter is written when it is first picked up, for the place it was found in:
 * it points at the nearest shrine or vault (distance, direction, and a surveyor's mark in the
 * margin) in the voice of one of the five hands below, and says what that place wants - a shrine's
 * letter names the rite's third offering, a vault's letter tells where the embers are kept. One
 * letter in twenty is the scholar's last note about the Titan, which points nowhere anyone can walk.
 */
public final class Letters {
    private Letters() {
    }

    public static final TagKey<Structure> SHRINES = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "shrines"));
    public static final TagKey<Structure> VAULTS = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "vaults"));
    public static final TagKey<Structure> KINGDOMS = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "kingdoms"));
    /** Kingdoms are sought over fewer placement cells (theirs are 40 chunks wide): about three kilometres. */
    static final int KINGDOM_SEARCH_CELLS = 5;
    /** How far a letter looks for its target - in placement cells of the structure set, not chunks: 24 cells of 34-36 chunks is over ten kilometres. */
    private static final int SEARCH_CHUNKS = 24;

    /** The five hands the letters are written in. */
    public enum Voice {
        MINER("Aldo, who dug", "Aldo"),
        PILGRIM("Wren of the long road", "Wren"),
        SCHOLAR("Brother Halm", "Halm"),
        CHILD("Pip", "Pip"),
        SOLDIER("Sergeant Osk", "Osk");

        public final String signature, name;

        Voice(String signature, String name) {
            this.signature = signature;
            this.name = name;
        }
    }

    /** What a shrine of a kind looks like to someone who has seen it, and the name the old maps gave it. */
    static String shrineSight(String kind) {
        return switch (kind) {
            case "earth" -> "a green mound with a door of stone in its side - the old maps call it a Barrow";
            case "sandstone" -> "a tomb of cut sandstone half swallowed by the dunes - the Sand Tomb of the maps";
            case "ice" -> "a cairn of ice and packed snow with a light inside it - the Frost Cairn";
            case "prismarine" -> "a hall of green stone on the sea floor, deep under the water - the Sunken Shrine";
            case "moss" -> "a temple the wood has taken back, all moss and roots - the Overgrown Sanctum";
            default -> "a ring of standing stones with a table of rock in the middle - the Standing Stones";
        };
    }

    static String shrineShort(String kind) {
        return switch (kind) {
            case "earth" -> "the barrow";
            case "sandstone" -> "the sand tomb";
            case "ice" -> "the frost cairn";
            case "prismarine" -> "the sunken hall";
            case "moss" -> "the overgrown temple";
            default -> "the standing stones";
        };
    }

    static String colossusOf(String kind) {
        return switch (kind) {
            case "earth" -> "a giant of earth and roots";
            case "sandstone" -> "a giant of sandstone, gold in its joints";
            case "ice" -> "a giant of ice, blue as the deep of a glacier";
            case "prismarine" -> "a giant of sea-stone with lanterns for eyes";
            case "moss" -> "a giant grown over with moss and flowers";
            default -> "a giant of grey stone";
        };
    }

    /** Eight winds. */
    public static String direction(int dx, int dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        if (a < 0) a += 360;
        String[] winds = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return winds[(int) Math.round(a / 45) % 8];
    }

    public static String paces(double d) {
        if (d < 60) return "a stone's throw";
        if (d < 200) return "some " + (Math.round(d / 10) * 10) + " paces";
        if (d < 600) return "about " + (Math.round(d / 50) * 50) + " paces";
        return "a good " + (Math.round(d / 100) * 100) + " paces";
    }

    /** Where the letter points (public on the item, so the client can point at it). */
    public record Target(String type, String kind, BlockPos pos) {
    }

    private static Target find(ServerLevel level, BlockPos from, RandomSource rng) {
        // one letter in six speaks of the walled town where a king still sits - if there is one within reach
        if (rng.nextInt(6) == 0) {
            Target k = nearest(level, from, KINGDOMS);
            if (k != null && k.pos.distSqr(from) > 200 * 200) return k; // not the town the letter was found in
        }
        boolean shrineFirst = rng.nextInt(100) < 58;
        Target t = nearest(level, from, shrineFirst ? SHRINES : VAULTS);
        if (t == null) t = nearest(level, from, shrineFirst ? VAULTS : SHRINES);
        return t;
    }

    /** The last searches, so a chest full of letters does not search the world sixteen times over. */
    private record Cached(ServerLevel level, TagKey<Structure> tag, BlockPos from, long time, Target target) {
    }

    private static final List<Cached> CACHE = new ArrayList<>();

    private static Target nearest(ServerLevel level, BlockPos from, TagKey<Structure> tag) {
        long now = level.getGameTime();
        for (Cached c : CACHE) {
            if (c.level == level && c.tag == tag && now - c.time < 1200 && c.from.distSqr(from) < 64 * 64) return c.target;
        }
        HolderSet<Structure> set = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tag).orElse(null);
        if (set == null) return null;
        Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator().findNearestMapStructure(level, set, from, tag == KINGDOMS ? KINGDOM_SEARCH_CELLS : SEARCH_CHUNKS, false);
        Target t = null;
        if (hit != null) {
            Structure s = hit.getSecond().value();
            BlockPos at = hit.getFirst().offset(8, 0, 8);
            if (s instanceof ShrineStructure shrine) t = new Target("shrine", shrine.kind, at);
            else if (s instanceof me.lovkar.wakingworld.worldgen.DungeonStructure d) t = new Target("dungeon", d.kind, at);
            else if (s instanceof me.lovkar.wakingworld.kingdom.KingdomStructure) t = new Target("kingdom", "kingdom", at);
            else t = new Target("vault", "vault", at);
        }
        CACHE.removeIf(c -> now - c.time >= 1200);
        if (CACHE.size() > 32) CACHE.remove(0);
        CACHE.add(new Cached(level, tag, from.immutable(), now, t));
        return t;
    }

    /** Everything a letter is written from: where it points, where it was found, and what the world remembers. */
    public record Facts(Target target, String dir, String dist, double distance, String foundIn, String biome, List<String> around,
                        long day, String weather, String player, List<String> events, Voice voice, boolean titanNote) {
        public boolean pointsSomewhere() {
            return target != null && !titanNote;
        }
    }

    /** A written letter and where it points (null for the notes that point nowhere). */
    public record Written(WrittenBookContent book, Target target) {
    }

    /** Gathers the facts for a letter picked up at {@code from} by {@code player}. Does the structure search. */
    public static Facts gather(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos from, RandomSource rng) {
        Voice voice = Voice.values()[rng.nextInt(Voice.values().length)];
        boolean titan = rng.nextInt(20) == 0;
        Target t = titan ? null : find(level, from, rng);
        String dir = "", dist = "";
        double distance = 0;
        if (t != null) {
            int dx = t.pos.getX() - from.getX(), dz = t.pos.getZ() - from.getZ();
            dir = direction(dx, dz);
            distance = Math.sqrt((double) dx * dx + (double) dz * dz);
            dist = paces(distance);
        }
        String foundIn = structureAt(level, from);
        String biome = biomeName(level, from);
        List<String> around = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            String b = biomeName(level, from.offset((int) Math.round(Math.cos(a) * 140), 0, (int) Math.round(Math.sin(a) * 140)));
            if (!b.equals(biome) && !around.contains(b)) around.add(direction((int) Math.round(Math.cos(a) * 140), (int) Math.round(Math.sin(a) * 140)) + ": " + b);
        }
        long day = level.getDayTime() / 24000L;
        String weather = level.isThundering() ? "a thunderstorm" : level.isRaining() ? "rain" : "clear sky";
        List<String> events = new ArrayList<>();
        for (Chronicle.Event e : Chronicle.get(level).near(from, null, 4)) {
            int dx = e.x() - from.getX(), dz = e.z() - from.getZ();
            String where = paces(Math.sqrt((double) dx * dx + (double) dz * dz)) + " to the " + direction(dx, dz);
            String what = switch (e.type()) {
                case "slain" -> "a giant of " + kindWord(e.kind()) + " was brought down" + (e.who().isEmpty() ? "" : " by " + e.who());
                case "woken" -> "a giant of " + kindWord(e.kind()) + " rose out of the ground";
                default -> "a rite was performed at a " + kindWord(e.kind()) + " altar" + (e.who().isEmpty() ? "" : " by " + e.who());
            };
            events.add(what + ", " + where + ", on day " + e.day());
        }
        return new Facts(t, dir, dist, distance, foundIn, biome, around, day, weather, player == null ? "a traveller" : player.getGameProfile().getName(), events, voice, titan);
    }

    static String kindWord(String kind) {
        return switch (kind) {
            case "sandstone" -> "sand";
            case "prismarine" -> "the sea";
            case "moss" -> "the grove";
            case "titan" -> "the End";
            default -> kind;
        };
    }

    private static String biomeName(ServerLevel level, BlockPos at) {
        return level.getBiome(at).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("wild country");
    }

    private static String structureAt(ServerLevel level, BlockPos at) {
        try {
            for (Structure s : level.structureManager().getAllStructuresAt(at).keySet()) {
                if (s instanceof me.lovkar.wakingworld.worldgen.RuinStructure r) return "hamlet".equals(r.kind) ? "an abandoned hamlet" : "a ruined " + r.kind;
                if (s instanceof me.lovkar.wakingworld.worldgen.VaultStructure) return "one of the old vaults";
                if (s instanceof me.lovkar.wakingworld.kingdom.KingdomStructure) return "a walled town";
                if (s instanceof ShrineStructure sh) return shrineShort(sh.kind);
            }
        } catch (Exception ignored) {
        }
        return "the open country";
    }

    /** The template letter for the facts (no AI involved). */
    public static Written compose(Facts f, RandomSource rng) {
        Voice voice = f.voice;
        if (f.titanNote) return new Written(titanNote(rng), null);
        Target t = f.target;
        if (t == null) return new Written(lostNote(voice, rng), null);
        List<Component> chapters = new ArrayList<>();
        String title;
        if (t.type.equals("shrine")) {
            title = titleFor(voice, t.kind, rng);
            chapters.add(shrineLetter(voice, t.kind, f.dir, f.dist, rng));
            chapters.add(shrineHint(voice, t.kind, rng));
        } else if (t.type.equals("dungeon")) {
            title = dungeonTitle(voice, t.kind, rng);
            chapters.add(dungeonLetter(voice, t.kind, f.dir, f.dist, rng));
            chapters.add(dungeonHint(voice, t.kind, rng));
        } else if (t.type.equals("kingdom")) {
            title = kingdomTitle(voice, rng);
            chapters.add(kingdomLetter(voice, f.dir, f.dist, rng));
            chapters.add(kingdomHint(voice, rng));
        } else {
            title = vaultTitle(voice, rng);
            chapters.add(vaultLetter(voice, f.dir, f.dist, rng));
            chapters.add(vaultHint(voice, rng));
        }
        chapters.add(margin(t, rng));
        return new Written(book(title, voice.signature, chapters), t);
    }

    /** What a dungeon is called in a letter. */
    public static String dungeonName(String kind) {
        return "forge".equals(kind) ? "the ember forge" : "the drowned cistern";
    }

    /** The exact offerings line for a shrine (the part an AI letter must not get wrong), or the vault's stores. */
    public static Component exactHint(Target t) {
        if (t.type.equals("kingdom")) {
            return Component.empty()
                    .append(text("What the town keeps: a king in his hall who will hear you out and tell you of the sleepers, the letters, the vaults and worse; a market - the surveyor sells maps to the shrines and the vaults and to the next town, the relic-monger "))
                    .append(Component.translatable(WakingItems.SLEEPERS_EMBER.get().getDescriptionId()).withStyle(ChatFormatting.DARK_RED))
                    .append(text("s and runes for emeralds; guards on the walls. Keep your hands off the treasury under the keep and off the king's people, or the whole town turns on you."));
        }
        if (t.type.equals("dungeon")) {
            boolean forge = "forge".equals(t.kind);
            return Component.empty()
                    .append(text(forge ? "What the forge keeps: iron and gold, the smiths' own tools, the odd piece of the black metal - and " : "What the cistern keeps: the sea's things - prismarine, shells, a trident if you are lucky - and "))
                    .append(Component.translatable(WakingItems.SLEEPERS_EMBER.get().getDescriptionId()).withStyle(ChatFormatting.DARK_RED))
                    .append(text(forge ? "s with the runes of stone and of sand. The keepers there burn; water is your friend." : "s with the rune of the sea. The keepers there drag you under; keep to the walkways."));
        }
        if (t.type.equals("shrine")) {
            List<Rites.Offering> offers = Rites.offerings(t.kind);
            Rites.Offering gift = offers.get(offers.size() - 1);
            return Component.empty()
                    .append(text("A coal that never went out - a ")).append(Component.translatable(WakingItems.SLEEPERS_EMBER.get().getDescriptionId()).withStyle(ChatFormatting.DARK_RED))
                    .append(text(". The rune cut for that place - the ")).append(Component.translatable(runeFor(t.kind).getDescriptionId()).withStyle(ChatFormatting.DARK_PURPLE))
                    .append(text(". And a gift of the land itself: " + gift.count() + " of ")).append(Component.translatable(gift.item().getDescriptionId()).withStyle(ChatFormatting.DARK_GREEN)).append(text("."));
        }
        return Component.empty()
                .append(text("The ")).append(Component.translatable(WakingItems.SLEEPERS_EMBER.get().getDescriptionId()).withStyle(ChatFormatting.DARK_RED))
                .append(text(" - every rite burns one. The runes, one kind for each kind of altar. The "))
                .append(Component.translatable(WakingItems.HORN_OF_WAKING.get().getDescriptionId()).withStyle(ChatFormatting.GOLD))
                .append(text(" - sound it over an altar once every offering is laid down."));
    }

    /** The same, in plain words, for the AI's briefing. */
    public static String exactHintPlain(Target t) {
        if (t.type.equals("kingdom")) {
            return "a king in his hall who talks (of the sleepers, the letters, the vaults, the Titan), a market where a surveyor sells maps to shrines, vaults and the next town and a relic-monger sells Sleeper's Embers and runes for emeralds, archers, knights and spearmen on the walls; the treasury under the keep is guarded and robbing it or harming the king's people turns the whole town hostile";
        }
        if (t.type.equals("dungeon")) {
            return "forge".equals(t.kind) ? "iron, gold, smiths' tools, netherite scraps, Sleeper's Embers and runes of stone and sand; guarded by Ember Wraiths (burning dead that set you alight, put out by water) and a Rune Sentinel"
                    : "prismarine, shells, sometimes a trident, Sleeper's Embers and runes of the sea; guarded by Drowned Keepers (big drowned with tridents whose blows slow you)";
        }
        if (t.type.equals("shrine")) {
            List<Rites.Offering> offers = Rites.offerings(t.kind);
            Rites.Offering gift = offers.get(offers.size() - 1);
            return "a Sleeper's Ember, the " + plainName(runeFor(t.kind)) + ", and " + gift.count() + " " + plainName(gift.item());
        }
        return "Sleeper's Embers and runes in the four rooms, the Horn of Waking and treasure in the crypt under the hall; stone thralls guard the hall";
    }

    private static String plainName(net.minecraft.world.item.Item item) {
        String key = item.getDescriptionId();
        return switch (key) {
            case "item.wakingworld.rune_stone" -> "Rune of Stone";
            case "item.wakingworld.rune_earth" -> "Rune of Earth";
            case "item.wakingworld.rune_sandstone" -> "Rune of Sand";
            case "item.wakingworld.rune_ice" -> "Rune of Ice";
            case "item.wakingworld.rune_prismarine" -> "Rune of the Sea";
            case "item.wakingworld.rune_moss" -> "Rune of the Grove";
            default -> {
                String path = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ');
                yield path;
            }
        };
    }

    /** A letter from an AI's pieces: the title, the letter, the hint's own words around our exact line, and the margin. */
    public static Written assemble(Facts f, String title, String letter, String hintBefore, String hintAfter, String marginHand) {
        Target t = f.target;
        if (title.length() > 32) title = title.substring(0, 32).trim();
        List<Component> chapters = new ArrayList<>();
        chapters.add(text(letter));
        MutableComponent hint = Component.empty();
        if (!hintBefore.isBlank()) hint.append(text(hintBefore.trim() + "\n\n"));
        hint.append(exactHint(t));
        if (!hintAfter.isBlank()) hint.append(text("\n\n" + hintAfter.trim()));
        chapters.add(hint);
        String what = targetName(t);
        chapters.add(faded((marginHand == null || marginHand.isBlank() ? "Scrawled in the margin, in another hand:" : marginHand.trim()) + "\n\n"
                + what + " - the surveyor's marks put it at\n\nx " + t.pos.getX() + "   z " + t.pos.getZ()));
        return new Written(book(title, f.voice.signature, chapters), t);
    }

    public static String signatureOf(Voice v) {
        return v.signature;
    }

    private static WrittenBookContent book(String title, String author, List<Component> chapters) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (Component c : chapters) pages.add(Filterable.passThrough(c));
        return new WrittenBookContent(Filterable.passThrough(title), author, 0, pages, true);
    }

    private static MutableComponent text(String s) {
        return Component.literal(s);
    }

    private static MutableComponent faded(String s) {
        return Component.literal(s).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    private static String pick(RandomSource rng, String... options) {
        return options[rng.nextInt(options.length)];
    }

    private static String titleFor(Voice v, String kind, RandomSource rng) {
        return switch (v) {
            case MINER -> pick(rng, "Aldo's last shift", "What I found past the seam", "To whoever takes my pick");
            case PILGRIM -> pick(rng, "The road to " + shrineShort(kind), "A pilgrim's directions", "Wren, to the next one");
            case SCHOLAR -> pick(rng, "Notes on the sleepers", "On the rite, briefly", "Brother Halm's survey");
            case CHILD -> pick(rng, "Pip's map", "Don't tell mum", "The giant is real");
            case SOLDIER -> pick(rng, "Report, unsent", "Osk to the captain", "Orders for the relief");
        };
    }

    private static String vaultTitle(Voice v, RandomSource rng) {
        return switch (v) {
            case MINER -> pick(rng, "The hollow under the tower", "Aldo's shift, the last one", "Don't go down there");
            case PILGRIM -> pick(rng, "What the vault keeps", "Wren, on the vault", "The stair under the stump");
            case SCHOLAR -> pick(rng, "The Sleeper's Vault", "An inventory, unfinished", "Halm's catalogue");
            case CHILD -> pick(rng, "The stone man", "Pip went down", "There's a hole under the tower");
            case SOLDIER -> pick(rng, "Osk, on the vault", "Casualty report", "Do not enter alone");
        };
    }

    // ---- shrine letters ----

    private static Component shrineLetter(Voice v, String kind, String dir, String dist, RandomSource rng) {
        String sight = shrineSight(kind);
        String giant = colossusOf(kind);
        String body = switch (v) {
            case MINER -> pick(rng,
                    "I went looking for the seam and found the old place instead: " + sight + ". It lies " + dist + " to the " + dir + " of here. Don't laugh. The stones hum. I put my hand on the table in the middle and the ground under my boots went warm, like a hearth with the fire out an hour. I am not going back without a rope and a reason. If I don't come back for my pick, it is yours.",
                    "Hear me out. " + dist.substring(0, 1).toUpperCase() + dist.substring(1) + " " + dir + " of the cottage there is " + sight + ". I have dug forty years and I know when rock is only rock. That rock is not. Something the size of a hill is lying under it with its knees drawn up, and the table in the middle is where they used to wake it. The old woman at the crossing told me what it wants. I wrote it on the back.");
            case PILGRIM -> pick(rng,
                    "To the one who walks this road after me. Keep " + dir + " for " + dist + " and you will come to " + sight + ". I slept there one night. In the dark I heard the ground breathe - slow, once an hour, like a sleeper who does not mean to wake. The pilgrims before me left offerings on the table. I left bread, which was foolish. It wants other things. I have written them on the next leaf.",
                    "I have seen " + giant + ". Not in a dream. It was standing " + dist + " to the " + dir + " of here, over " + sight + "; then it lay down and was hill again. I am going on to the next one. If you have a coal that never goes out, and the rune, and what the land asks, go and wake it. Someone should. They have been asleep so long.");
            case SCHOLAR -> pick(rng,
                    "Entry the fourth. The site lies " + dist + " " + dir + " of this camp: " + sight + ". It is not a grave. It is a bed. The people who built these did not bury their giants, they laid them down and left an altar over each one so that the giant could be called up again when the land needed it. The altar still works. I have watched it take an offering from a shepherd's hand and hum. The offerings are particular - see overleaf.",
                    "On the sleepers, briefly. There are six kinds - of stone, of earth, of sand, of ice, of the sea, and of the grove - and each sleeps under an altar of its own kind. One cannot wake a giant of the sea at a barrow, nor a giant of stone under the ice: the altar knows what lies under it. The nearest, " + dist + " to the " + dir + " of here, is " + sight + ". It holds " + giant + ".");
            case CHILD -> pick(rng,
                    "Its " + dist + " that way (" + dir + ", dad says). " + sight.substring(0, 1).toUpperCase() + sight.substring(1) + ". I went inside even though mum said not to and there is a table and when you put stuff on it it GLOWS. Aldo says a giant sleeps under it. " + giant.substring(0, 1).toUpperCase() + giant.substring(1) + "!! I am going to wake it up when I am big. I know what it wants because I listened at the door. Its on the other side.",
                    "Dear whoever. We had to leave. Mum says the ground was shaking because of the giants and dad says its because of the mine. I think its the giants. I saw one lying down " + dist + " " + dir + " of our house where " + sight + " is. It was pretending to be a hill but I saw its hand. Ill tell you the secret of the table if you promise to be nice to it.");
            case SOLDIER -> pick(rng,
                    "Report. Patrol reached the site " + dist + " " + dir + " of the post: " + sight + ". No enemy. One structure, intact, with an altar at its centre. Private Dunn laid his ration on the altar as a joke and the thing lit up and the ground moved. Withdrew in good order. Recommend the site be either garrisoned or left very much alone. What the altar wants is listed below, from the prisoner's statement.",
                    "To the captain, if this reaches you. We found what the villagers were running from. " + dist.substring(0, 1).toUpperCase() + dist.substring(1) + " to the " + dir + ", " + sight + ". Under it, sir, is " + giant + " - I saw its shoulder when the dust blew off. The locals say their grandfathers woke it with a rite when the raiders came, and it walked, and the raiders did not come again. I have the rite from their elder. It is on the next page.");
        };
        return text(body);
    }

    private static Component shrineHint(Voice v, String kind, RandomSource rng) {
        // the offerings: ember, the rune of the kind, and the land's own gift
        List<Rites.Offering> offers = Rites.offerings(kind);
        Rites.Offering gift = offers.get(offers.size() - 1);
        Component giftName = Component.translatable(gift.item().getDescriptionId()).withStyle(ChatFormatting.DARK_GREEN);
        Component runeName = Component.translatable(runeFor(kind).getDescriptionId()).withStyle(ChatFormatting.DARK_PURPLE);
        Component emberName = Component.translatable(WakingItems.SLEEPERS_EMBER.get().getDescriptionId()).withStyle(ChatFormatting.DARK_RED);
        MutableComponent c = Component.empty();
        String lead = switch (v) {
            case MINER -> "What the old woman said the altar wants, in her words:\n\n";
            case PILGRIM -> "The offerings, as the pilgrims before me knew them:\n\n";
            case SCHOLAR -> "The rite, as far as I have it:\n\n";
            case CHILD -> "The SECRET of the table (I heard Aldo say it):\n\n";
            case SOLDIER -> "The prisoner's statement, as taken down:\n\n";
        };
        c.append(text(lead));
        c.append(text("A coal that never went out - a ")).append(emberName).append(text(". The rune cut for that place - the ")).append(runeName)
                .append(text(". And a gift of the land itself: ")).append(text(gift.count() + " of ")).append(giftName).append(text("."));
        String tail = switch (v) {
            case MINER -> "\n\nThe coal and the rune are not to be had for money. She said the old vaults have them - the ones under the broken towers. Then you sound the horn over the table. I have no horn. Perhaps you do.";
            case PILGRIM -> "\n\nLay them on the table one by one. When the last is down, sound the horn. The horn is in the vaults, with the coals and the runes. Stand back when it rises - it comes up where you are not.";
            case SCHOLAR -> "\n\nThe coal and the rune the old people kept in their vaults, under towers now fallen; the horn likewise. The gift is of the land and can be gathered. All laid down, a horn sounded over the altar, and the ground gets up.";
            case CHILD -> "\n\nYou get the coal and the rune from the scary tower cellar and the horn too and you blow it REALLY loud on the table. Then it wakes up. Dont stand in front of it.";
            case SOLDIER -> "\n\nCoal, rune and horn are kept in the vaults under the old watchtowers, guarded. The gift the country provides. All three on the altar, then the horn. The elder says it rises facing away from whoever blows.";
        };
        c.append(text(tail));
        return c;
    }

    private static net.minecraft.world.item.Item runeFor(String kind) {
        return switch (kind) {
            case "earth" -> WakingItems.RUNE_EARTH.get();
            case "sandstone" -> WakingItems.RUNE_SANDSTONE.get();
            case "ice" -> WakingItems.RUNE_ICE.get();
            case "prismarine" -> WakingItems.RUNE_PRISMARINE.get();
            case "moss" -> WakingItems.RUNE_MOSS.get();
            default -> WakingItems.RUNE_STONE.get();
        };
    }

    /** What a letter calls its target in the margin. */
    static String targetName(Target t) {
        return switch (t.type) {
            case "shrine" -> shrineShort(t.kind);
            case "dungeon" -> dungeonName(t.kind);
            case "kingdom" -> "the walled town";
            default -> "the vault";
        };
    }

    // ---- kingdom letters ----

    private static String kingdomTitle(Voice v, RandomSource rng) {
        return switch (v) {
            case MINER -> pick(rng, "Where the market is", "Aldo, on the town", "They still have a king");
            case PILGRIM -> pick(rng, "The town with walls", "Wren, at the gate", "Where people still are");
            case SCHOLAR -> pick(rng, "Of the last kingdom", "Halm, on the walled town", "Notes from the king's hall");
            case CHILD -> pick(rng, "The castle!!", "Pip saw the king", "A town that isn't broken");
            case SOLDIER -> pick(rng, "Garrison report", "Osk, on the walled town", "Friendly walls");
        };
    }

    private static Component kingdomLetter(Voice v, String dir, String dist, RandomSource rng) {
        String body = switch (v) {
            case MINER -> pick(rng,
                    "Not everyone left. " + dist.substring(0, 1).toUpperCase() + dist.substring(1) + " to the " + dir + " there is a town with a proper wall round it, towers and a moat and a gate with a bridge you can pull up, and inside people still live like nothing happened. There is a market. A fellow there buys my amethyst and sells coals like the ones from the vaults, only he wants emeralds for them, a lot of emeralds. There is a castle in the middle and a king in it. I did not go in. Kings and miners.",
                    "Go " + dir + " for " + dist + " and you will hit a wall, and that is good news for once. Behind it: houses with roofs, a smith who will sharpen a pick, bread, and a surveyor who draws you a map to any of the old places for a price. Mind the guards - they are polite until you are not. A man in the market said the king knows where the giants sleep. He knows a good deal, the king.");
            case PILGRIM -> pick(rng,
                    "To the next one on the road: there is rest " + dist + " to the " + dir + ". A walled town, the only one I have found with people still in it. They took me in at the gate, gave me bread, and the king himself - a tired man on a great chair - let me sit and told me what the old people knew about the sleepers. Go and listen to him. Buy a map from the surveyor. Do not touch what is not yours; they have long memories there, and long spears.",
                    "Wren, at the gate. " + dist.substring(0, 1).toUpperCase() + dist.substring(1) + " " + dir + " of here the road ends at a town that never fell. Eight towers, a keep, a market in the square. I sold them a story and they sold me a coal that never goes out. The king asks every traveller what they have seen of the giants. Tell him. He remembers everything, and he pays for news in kindness.");
            case SCHOLAR -> pick(rng,
                    "Of the last kingdom. It lies " + dist + " " + dir + " of this camp: a walled town of the old pattern - a ring wall with eight towers, a moat, two barbicans, and at its heart a keep with a hall and a donjon, the treasury beneath. It is inhabited. Its king holds audience and knows the lore of the sleepers better than I do, which I record with some irritation. The market keeps a surveyor, a relic-monger, a smith, a chandler, a provisioner and a scribe. Recommended.",
                    "Halm, on the walled town " + dist + " to the " + dir + ". Observations: 1. It stands. 2. It is governed - a king, guards in three kinds, a council that crowns a new king when the old one dies. 3. It trades: maps, embers, runes, iron, bread, light, paper. 4. It punishes: theft from the treasury or a hand raised against its people brings the whole garrison down on you, and the memory of it lasts days. 5. The king talks. Ask him about the Titan.");
            case CHILD -> pick(rng,
                    "THERES A CASTLE. Its " + dist + " " + dir + " from here and it has a REAL wall and towers and a bridge over water and knights with plumes and a KING on a big chair!! I saw him. He said hello to me. Dad bought bread and a map from a man with a hat and mum bought candles. The guards have bows. Dont steal anything, Aldo says they get very very cross.",
                    "Dear whoever. If your town is broken like ours was theres one that isnt, " + dist + " " + dir + " of the well. It has walls so tall and a king and shops. The man in the market has glowing coals in a box and stones with pictures. He wouldnt let me touch them. The king knows about the giants, he told dad where one sleeps. Go there, its safe. Mostly.");
            case SOLDIER -> pick(rng,
                    "Garrison report. Friendly walled town " + dist + " " + dir + " of the post. Curtain wall, eight towers, moat, two barbicans with portcullis and drawbridge. Garrison: archers on the towers, knights at the gates, spearmen in the streets - well drilled. Sovereign: a king, holds audience, cooperative, well informed on the giants. Market includes a surveyor (maps) and a dealer in rite-goods. Recommend resupply there. Do not provoke them; they outnumber us.",
                    "Osk to the captain. There is a kingdom still standing " + dist + " to the " + dir + ". I was inside its walls two days. Their guards are better than ours. Their king asked about the giants and knew more than I did. Their market sells the coals and the runes the rite wants, for emeralds. Their treasury is under the keep behind an iron door and I strongly advise against the idea Dunn had about it. Map on the back.");
        };
        return text(body);
    }

    private static Component kingdomHint(Voice v, RandomSource rng) {
        MutableComponent c = Component.empty();
        c.append(text(switch (v) {
            case MINER -> "What's to be had there:\n\n";
            case PILGRIM -> "What the town offers a traveller:\n\n";
            case SCHOLAR -> "The town, in short:\n\n";
            case CHILD -> "Whats in the castle town:\n\n";
            case SOLDIER -> "Assets:\n\n";
        }));
        c.append(exactHint(new Target("kingdom", "kingdom", BlockPos.ZERO)));
        c.append(text(switch (v) {
            case MINER -> "\n\nThe coal-seller wants emeralds. Bring what you dug.";
            case PILGRIM -> "\n\nAsk for the king. He sees everyone who asks.";
            case SCHOLAR -> "\n\nThe king's news is a day old at most; the council's memory is longer.";
            case CHILD -> "\n\nSay hello to the king, he likes it.";
            case SOLDIER -> "\n\nWatch your hands in the keep.";
        }));
        return c;
    }

    // ---- vault letters ----

    private static Component vaultLetter(Voice v, String dir, String dist, RandomSource rng) {
        String body = switch (v) {
            case MINER -> pick(rng,
                    "There is a broken tower " + dist + " to the " + dir + ". Round, no roof, and in the middle of it a hole with a ladder going down further than a ladder should. I went down. There is a hall with pillars, and rooms off it, and in the rooms there are chests the old people left, and there are things walking in the dark that are not people any more. One of them is made of stone. My pick bounced off it. Go with friends.",
                    "Mark this: " + dist + " " + dir + " of here, the stump of a tower. Under it, a hall. The old people kept their rite-things down there - the coals that don't go out, the cut runes, the horns - and they set guards on them that don't die because they were never alive. I took two coals and a rune and I lost Tam. Whoever reads this: it is worth it, and it is not worth it. Both.");
            case PILGRIM -> pick(rng,
                    "Wren, to the next one. The vault is " + dist + " to the " + dir + " - look for a round ruin of a tower on level ground, the walls no higher than your chest. The way down is a shaft in the middle of it. The rite-things are kept below: the coal, the rune, the horn. There is a room under the hall, reached by a hole in the floor, and that is where the best of it is. The stone men are slow. Do not let them corner you.",
                    "I have been to the vault " + dist + " " + dir + " of here and come out again, which is more than most. Bring light - the lanterns down there are the blue kind and do not warm you. Bring a good sword, and don't waste arrows on the stone ones, they only laugh, if they laugh. Under the hall there is a crypt. That is where the horns are.");
            case SCHOLAR -> pick(rng,
                    "The vaults. Each stands under a round tower, and the nearest to this place is " + dist + " to the " + dir + ". Their builders laid up in them everything the rite of waking needs, against the day the land would need its giants again: the coal that does not go out (they called it the Sleeper's Ember), the runes cut for each kind of altar, and the horns. And they set thralls of stone to keep them. The thralls keep them still.",
                    "Halm's catalogue, item nine: a vault, " + dist + " " + dir + " of this camp, entered by a shaft in a ruined tower. Hall, four rooms, crypt beneath. Contents: embers (three seen), runes of stone and of the sea, one horn, sundry treasure, two thralls of stone and a number of the ordinary dead. I have taken nothing yet. I am too old to run.");
            case CHILD -> pick(rng,
                    "There's a hole under the broken tower " + dist + " " + dir + " of here with a ladder!! I went down a bit. There's blue lamps. And a stone man walked past under me and he was ALL stone and his eyes were orange. I came back up very fast. Aldo says the glowing coals are down there and the runes and thats what you need for the giants. When I am big.",
                    "Dont go in the tower cellar. Its " + dist + " " + dir + " from the well. Dad went and came back with a burnt coal that is still hot and a stone with a picture on it and a big cut on his arm. He said the stone man did it. He said the coal is for waking the giant. Mum cried. Im keeping the coal under my bed.");
            case SOLDIER -> pick(rng,
                    "Do not enter alone. The vault lies " + dist + " to the " + dir + ": a tower stump, shaft in the centre, ladder. Hall below with four rooms and a crypt under the hall. Hostiles: the walking dead, and something worse - men of stone, slow, hard to kill, hit like a mule. We lost Dunn to one. Objective recovered: two embers, one rune, one horn. The horn is why we came.",
                    "Casualty report, and a map. Vault " + dist + " " + dir + " of the post. Entered with six, came out with four. The chests in the four rooms hold the coals and the runes; the crypt under the hall holds the horns and the gold. The stone men guard the hall. They do not leave it. Take the rooms one at a time and never fight in the hall.");
        };
        return text(body);
    }

    private static Component vaultHint(Voice v, RandomSource rng) {
        Component ember = Component.translatable(WakingItems.SLEEPERS_EMBER.get().getDescriptionId()).withStyle(ChatFormatting.DARK_RED);
        Component horn = Component.translatable(WakingItems.HORN_OF_WAKING.get().getDescriptionId()).withStyle(ChatFormatting.GOLD);
        MutableComponent c = Component.empty();
        c.append(text(switch (v) {
            case MINER -> "What's down there, for the record:\n\n";
            case PILGRIM -> "What the vault keeps:\n\n";
            case SCHOLAR -> "Inventory of a vault:\n\n";
            case CHILD -> "Whats in the cellar:\n\n";
            case SOLDIER -> "Objectives:\n\n";
        }));
        c.append(text("The ")).append(ember).append(text(" - every rite burns one. The runes, one kind for each kind of altar. The "))
                .append(horn).append(text(" - sound it over an altar once every offering is laid down."));
        c.append(text(switch (v) {
            case MINER -> "\n\nThe rooms have the coals and the runes. The crypt under the hall has the rest. Mind the stone ones.";
            case PILGRIM -> "\n\nThe rooms first, then the crypt. The horn is more often in the crypt.";
            case SCHOLAR -> "\n\nThe rooms hold the common stores; the crypt the rare. The thralls are bound to the hall and will not follow far.";
            case CHILD -> "\n\nThe best stuff is under the floor of the big room. Dad said. Dont fall in.";
            case SOLDIER -> "\n\nRooms: embers, runes. Crypt: horns, valuables. Hall: hostiles. Plan accordingly.";
        }));
        return c;
    }

    // ---- dungeon letters: the cistern and the forge ----

    private static String dungeonTitle(Voice v, String kind, RandomSource rng) {
        boolean forge = "forge".equals(kind);
        return switch (v) {
            case MINER -> pick(rng, forge ? "The forge under the sand" : "The cistern", forge ? "Where the fires still burn" : "Under the pump house");
            case PILGRIM -> pick(rng, forge ? "Wren, on the forge" : "Wren, on the cistern", "A pilgrim's warning");
            case SCHOLAR -> pick(rng, forge ? "On the Ember Forge" : "On the Drowned Cistern", "Halm's catalogue, continued");
            case CHILD -> pick(rng, forge ? "The hot place" : "The wet place", "DONT GO DOWN THERE");
            case SOLDIER -> pick(rng, forge ? "Report: the forge" : "Report: the cistern", "Do not enter alone");
        };
    }

    private static Component dungeonLetter(Voice v, String kind, String dir, String dist, RandomSource rng) {
        boolean forge = "forge".equals(kind);
        String body;
        if (forge) {
            body = switch (v) {
                case MINER -> pick(rng,
                        "There is a smithy fallen in " + dist + " to the " + dir + " - an anvil in the open and a chimney still standing off by itself. Behind the anvil a stair goes down. I smelled the smoke before I saw the fire. Down there the forge is still lit, all of it - lava in the gutters, a furnace the size of a house. And the smiths are still at it, if you can call them smiths. They are black and they glow inside and my sleeve caught before one touched me. I threw myself in the trough. That saved me.",
                        "Mark it: " + dist + " " + dir + " of here, under the ruined smithy. The old people's forge. The iron is still in the chests down there, and gold, and the coals that don't go out, and the cut runes - the ones for the stone altars and the sand altars. What walks down there burns. Take water. Take a lot of water.");
                case PILGRIM -> pick(rng,
                        "Wren, on the forge. It lies " + dist + " to the " + dir + " under a smithy with no roof - look for the chimney that smokes with no one to feed it. The stair behind the anvil goes deep. The hall below is lit by its own fires and by the things that keep it; they were smiths once and they have not put the fire down since. Keep to the middle walkways, keep off the gutters, and if one touches you, run for the troughs. There is a locked room at the far end behind bars; the master's tools are there, and the best of the coals.",
                        "I went into the forge " + dist + " " + dir + " of here for the runes - the ember forge keeps the runes of stone and of sand, both - and I came out with two and no eyebrows. The keepers burn. Water puts them out for good; the troughs down there are full of it. Bring more of your own.");
                case SCHOLAR -> pick(rng,
                        "The Ember Forge, " + dist + " to the " + dir + ". The old people cut their runes here and forged whatever needed forging for the rites, and they kept the place hot with lava drawn up from below. It is hot still. Its hands - I will not call them smiths - are the burnt dead, and they are bound to the fire the way the thralls are bound to the vaults. Water undoes them; note the quenching troughs. Contents: iron and gold in quantity, embers, runes of stone and of sand, and in the master's vault behind the bars a sentinel that shoots.",
                        "Halm's catalogue, item fourteen: a forge underground, entered by a stair from a ruined smithy " + dist + " " + dir + " of this camp. A furnace the size of a chapel, its chimney reaching the surface; lava channels; fire pits; workshops. Guardians: the burning dead (many), one bound archer at the vault. Took: nothing. Saw: everything a rite of stone or sand could want.");
                case CHILD -> pick(rng,
                        "Theres a HOT place under the old smithy " + dist + " " + dir + " of here!! You go down the stairs behind the anvil and its all orange and theres lava in little rivers and the smiths are still working but they are BURNING and they dont stop. Aldo says they keep the runes for the stone giants down there. I am not going back. Maybe when I am big.",
                        "Dad went to the forge " + dist + " " + dir + " from the well and came back with a burnt coal and a rune with a picture of a mountain and his beard was gone. He said dont touch the black men, they are hot. He said jump in the water if they touch you. Im not touching anything.");
                case SOLDIER -> pick(rng,
                        "Do not enter alone. The forge lies " + dist + " to the " + dir + " under a roofless smithy; stair behind the anvil, sixteen steps, hall below. Hostiles: burning dead, fast, set you alight on contact; one archer in the vault at the north end, its arrows burn. Hazards: lava channels along the walls, fire pits at the corners. Counter: the quenching troughs - water finishes the burning ones. Objective: embers, runes of stone and sand, the master's chests.",
                        "Report and map. Ember Forge, " + dist + " " + dir + " of the post. Entered with five, out with five, two badly burned. Contents recovered: three embers, two runes, iron, gold, a scrap of the black metal. Keepers were put down with water buckets - the troughs down there refill them. The vault is barred; the bars come off with a pick.");
            };
        } else {
            body = switch (v) {
                case MINER -> pick(rng,
                        "There is a little stone house by the water " + dist + " to the " + dir + " - a pump house, mossy, with a hatch in the floor. The ladder goes down a long way. Under it the old people built a cistern: a hall of pillars with water to your knees and lamps under the water, green. Things stand in the water. Drowned, but big, with the three-pronged spears, and when one hit me I could hardly move after. I got out along the walkway. Just.",
                        "Mark it: " + dist + " " + dir + " of here, the pump house. The cistern under it. The old people kept the sea-rune down there, and coals, and the sea's own things - I saw shells and the glowing stone. The keepers are drowned men that never rot. Stay on the walkways and they have to come up to you.");
                case PILGRIM -> pick(rng,
                        "Wren, on the cistern. It lies " + dist + " to the " + dir + " under a pump house you would walk past. Down the ladder, a flooded hall with sixteen pillars and a walkway round the walls and across the middle to an island where the keepers' hoard is: the sea-rune, the embers, prismarine, and a trident if the drowned have not taken it back. They hit hard and their blows drag at you. Keep to the walkways; do not fight in the water.",
                        "I went down to the cistern " + dist + " " + dir + " of here for the rune of the sea, and I have it. The keepers are slow out of the water and quick in it. Cross by the walkways, loot the island, come back the same way, and never, ever swim.");
                case SCHOLAR -> pick(rng,
                        "The Drowned Cistern, " + dist + " to the " + dir + ". Built to hold water for a town that is gone; kept, since, by its drowned - larger than the sea's drowned and armed with tridents, and bound to the water as the thralls are bound to the vaults. The rune of the sea is laid up there, with embers and the ordinary things of the deep: prismarine, shells, once a heart of the sea. The lamps under the water are the old people's; they still burn.",
                        "Halm's catalogue, item eleven: a cistern under a pump house " + dist + " " + dir + " of this camp. Hall of sixteen pillars, flooded to the knee, walkways, an island at the centre with the hoard. Keepers: drowned of unusual size, three at least, with tridents. Their blows weaken and slow. I stayed on the walkway and took the rune of the sea and one ember and left the rest to them.");
                case CHILD -> pick(rng,
                        "Theres a WET place under the little house by the water " + dist + " " + dir + " of here!! Theres a trapdoor and a ladder and then a big room full of water with green lights in it and drowned men with FORKS. One threw his fork at me!! Aldo says the sea rune is down there for the water giant. I am not going back.",
                        "Dont go down the pump house. Its " + dist + " " + dir + " from the well. Mum went for the shells and came back all slow and wet and shaking and she said the drowned man touched her and she couldnt run. She got the rune with the waves on it tho. Its on the shelf.");
                case SOLDIER -> pick(rng,
                        "Do not enter alone. The cistern lies " + dist + " to the " + dir + " under a pump house; hatch and ladder in the floor, hall below, flooded. Hostiles: drowned keepers, three or more, tridents, blows cause slowing and weakness; ordinary drowned with them. Terrain: walkways round the walls and across the middle, water everywhere else. Hold the walkways. Objective: the island at the centre - the sea-rune, embers, prismarine.",
                        "Report and map. Drowned Cistern, " + dist + " " + dir + " of the post. Entered with four, out with four, one lost his sword to the water. Contents recovered: the rune of the sea, two embers, a trident, shells. The keepers do not leave the water; do not join them in it.");
            };
        }
        return text(body);
    }

    private static Component dungeonHint(Voice v, String kind, RandomSource rng) {
        MutableComponent c = Component.empty();
        c.append(text(switch (v) {
            case MINER -> "What's down there, for the record:\n\n";
            case PILGRIM -> "What it keeps:\n\n";
            case SCHOLAR -> "Inventory:\n\n";
            case CHILD -> "Whats down there:\n\n";
            case SOLDIER -> "Objectives:\n\n";
        }));
        c.append(exactHint(new Target("dungeon", kind, BlockPos.ZERO)));
        return c;
    }

    // ---- the margin, the titan, the lost ----

    private static Component margin(Target t, RandomSource rng) {
        String hand = pick(rng, "Scrawled in the margin, in another hand:", "Added later, in charcoal:", "On the back, pressed hard:");
        String what = targetName(t);
        return faded(hand + "\n\n" + what + " - the surveyor's marks put it at\n\nx " + t.pos.getX() + "   z " + t.pos.getZ() + "\n\n"
                + pick(rng, "Go by day.", "Take the horn.", "It is still there. I looked.", "Burn this after."));
    }

    private static WrittenBookContent titanNote(RandomSource rng) {
        List<Component> chapters = List.of(
                text("Brother Halm, the last entry.\n\nSix kinds of giant, six sigils - one falls out of each of them when they die, I have held two. The old people say six sigils and a heart, forged together, make a key. Not for a door. For a place.\n\nThe key wants the island under the dragon's sky, where the sun does not rise. There is an arena of black glass there, with an altar on a dais, and something sleeping under it that makes the others look like children."),
                text("I will not go. I am old and I have seen enough. But whoever forges the key: the arena's altar wants the key, and two things more. A seventh sigil that no giant carries: the old people kept it out among the far islands of that place, in spires they called reliquaries, open to the void down their middles. And the egg the dragon leaves when it dies - the only one there is, and the altar will not turn without it. Round the great altar stand six lesser ones, one for each land; each wants the runes that wake that land - two, in the old count - and the great one hears no horn until all six burn. Lay all three on the altar, the runes on the six, and sound the horn. It gives the key and the egg back when the thing falls, and its heart - the old people say the heart can be eaten, and that whoever eats it is never quite so easy to kill again. They wrote, too, that when it falls the arena opens a door home, so that whoever did it need not walk.\n\nGod help you. Sound the horn from the far side of the dais."),
                faded("There are no marks in the margin. Only a small drawing of a tall thing with a light in its chest, and under it, one word: awake."));
        return book(pick(rng, "The last entry", "On the key", "Halm, to no one"), "Brother Halm", chapters);
    }

    private static WrittenBookContent lostNote(Voice v, RandomSource rng) {
        List<Component> chapters = List.of(
                text(switch (v) {
                    case MINER -> "Aldo. The ground moved again in the night. The whole ridge stood up and lay down, I swear it on my pick. We leave in the morning. If you are reading this the cottage is yours, and the well, and whatever the giants leave of the field.";
                    case PILGRIM -> "Wren. I have walked past a hundred sleeping hills and never known which ones were hills. Tonight one of them turned over. I am going on. If you find one of the old altars, treat it kindly; there is someone under it.";
                    case SCHOLAR -> "Halm. The village has emptied. They saw a giant on the ridge and would not stay for my explanations - that it was only walking, that the old rites were meant for this. I am staying. Someone should write it down.";
                    case CHILD -> "We are leaving and I am not allowed to take the cat. The giant walked past our house last night and it didnt even step on anything, it was careful. I waved. I think it saw. If you find the cat her name is Biscuit.";
                    case SOLDIER -> "Osk. Post abandoned by order. The thing on the ridge is not hostile unless provoked and we have orders not to provoke it. Someone will. Someone always does. Rations and arrows in the chest, for the relief that is not coming.";
                }),
                faded("The rest is water-stained past reading."));
        return book(pick(rng, "Unsent", "Left on the table", "For whoever comes"), v.signature, chapters);
    }
}
