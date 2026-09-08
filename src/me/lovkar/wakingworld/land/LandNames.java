package me.lovkar.wakingworld.land;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Locale;

/**
 * What a country is called when nobody has asked a model about it.
 *
 * <p>These are the fallback names, and they are not filler: the templates are picked by what the
 * ground actually is, so "The Salt Reach" only ever lands on a shore and "The Cold Teeth" only on
 * peaks. If Gemini is off, misconfigured, slow or down, the world still gets names it can live
 * with - the same way the Dead Letters do.</p>
 */
public final class LandNames {
    private LandNames() {
    }

    /** The rough kind of country a cell is, which decides both the word lists and what the model is told. */
    public enum Kind {
        PLAINS("open grassland"),
        FOREST("deep forest"),
        TAIGA("cold pine forest"),
        JUNGLE("hot jungle"),
        DESERT("desert"),
        BADLANDS("red mesa badlands"),
        SAVANNA("dry savanna"),
        SWAMP("swamp"),
        MOUNTAIN("high mountains"),
        SNOW("frozen country"),
        SHORE("coast"),
        OCEAN("open sea"),
        CAVE("underground"),
        END("the End"),
        NETHER("the Nether"),
        WILD("wild country");

        public final String english;

        Kind(String english) {
            this.english = english;
        }
    }

    private static final List<String> HEADS = List.of("The", "The", "The", "");

    private record Words(List<String> adjectives, List<String> nouns) {
    }

    private static Words words(Kind kind) {
        return switch (kind) {
            case PLAINS -> new Words(
                    List.of("Long", "Wide", "Quiet", "Green", "Old", "Wind-worn", "Endless", "Hollow", "Open", "Unfenced"),
                    List.of("Meadows", "Furrows", "Commons", "Downs", "Sweep", "Grasslands", "Fields", "Pasture", "Levels", "Green"));
            case FOREST -> new Words(
                    List.of("Deep", "Old", "Whispering", "Shaded", "Root-bound", "Unlit", "Tangled", "Close", "Standing", "Patient"),
                    List.of("Wood", "Thicket", "Weald", "Hollows", "Canopy", "Shade", "Timberland", "Grove", "Boughs", "Understory"));
            case TAIGA -> new Words(
                    List.of("Silent", "Cold", "Needle", "Grey", "Frost-bitten", "Standing", "Bitter", "Sharp", "Slow", "Iron"),
                    List.of("Pines", "Stand", "Firs", "Woods", "Highwood", "Needles", "Cones", "Rime", "Spires", "Hush"));
            case JUNGLE -> new Words(
                    List.of("Steaming", "Choked", "Fevered", "Loud", "Wet", "Devouring", "Sleepless", "Bright", "Crowded", "Root-thick"),
                    List.of("Canopy", "Tangle", "Rainlands", "Understory", "Vines", "Deeps", "Green", "Riot", "Roof", "Ferns"));
            case DESERT -> new Words(
                    List.of("Thirsting", "Burning", "Glass", "Bleached", "Endless", "Wandering", "Sun-struck", "Patient", "White", "Slow"),
                    List.of("Waste", "Sands", "Flats", "Dunes", "Barrens", "Glare", "Wind", "Bones", "Pan", "Sea"));
            case BADLANDS -> new Words(
                    List.of("Broken", "Rusted", "Layered", "Cracked", "Ochre", "Bleeding", "Standing", "Burnt", "Sheer", "Old"),
                    List.of("Mesas", "Steps", "Terraces", "Bluffs", "Cuts", "Stacks", "Shelves", "Ledges", "Spires", "Strata"));
            case SAVANNA -> new Words(
                    List.of("Dry", "Golden", "Lion", "Wide", "Burnt", "Thorn", "Slow", "Brass", "Hunting", "Open"),
                    List.of("Plateau", "Grass", "Veldt", "Range", "Acacias", "Flats", "Basin", "Sweep", "Scrub", "Rise"));
            case SWAMP -> new Words(
                    List.of("Drowned", "Sunken", "Still", "Black", "Rotting", "Fever", "Sighing", "Patient", "Green", "Slow"),
                    List.of("Mire", "Marsh", "Fens", "Bog", "Sloughs", "Waters", "Reeds", "Shallows", "Sedge", "Rot"));
            case MOUNTAIN -> new Words(
                    List.of("High", "Cold", "Broken", "Standing", "Grey", "Sheer", "Wind-cut", "Sleeping", "Bare", "Old"),
                    List.of("Teeth", "Peaks", "Spine", "Crags", "Heights", "Stones", "Shoulders", "Wall", "Saddles", "Scree"));
            case SNOW -> new Words(
                    List.of("White", "Silent", "Frozen", "Glass", "Long", "Killing", "Pale", "Clean", "Sleeping", "Hard"),
                    List.of("Waste", "Snows", "Fields", "Winter", "Drifts", "Glare", "Sheet", "Barrens", "Wind", "Hush"));
            case SHORE -> new Words(
                    List.of("Salt", "Grey", "Long", "Wrack", "Loud", "Broken", "Shell", "Patient", "Bleached", "Cold"),
                    List.of("Shore", "Strand", "Coast", "Sands", "Margin", "Edge", "Spit", "Shingle", "Tideline", "Reach"));
            case OCEAN -> new Words(
                    List.of("Deep", "Cold", "Green", "Wide", "Sunless", "Grey", "Drowning", "Slow", "Old", "Restless"),
                    List.of("Deep", "Sea", "Expanse", "Swell", "Blue", "Trough", "Fathoms", "Waters", "Shelf", "Dark"));
            case CAVE -> new Words(
                    List.of("Lightless", "Dripping", "Deep", "Hollow", "Bone", "Sleeping", "Blind", "Patient", "Cold", "Nameless"),
                    List.of("Under", "Hollows", "Deeps", "Vaults", "Roots", "Throat", "Galleries", "Bore", "Wells", "Seams"));
            case END -> new Words(
                    List.of("Void", "Pale", "Sleeping", "Last", "Outer", "Hollow", "Unlit", "Drifting", "Thin", "Quiet"),
                    List.of("Isles", "Shelf", "Rim", "Dark", "Shards", "Marches", "Verge", "Stones", "Drift", "Edge"));
            case NETHER -> new Words(
                    List.of("Burning", "Screaming", "Ash", "Red", "Sunless", "Blistered", "Old", "Hungry", "Choking", "Bright"),
                    List.of("Waste", "Fields", "Country", "Deeps", "Cinders", "Flues", "Wastes", "Glow", "Furnace", "Smoke"));
            case WILD -> new Words(
                    List.of("Nameless", "Far", "Empty", "Wild", "Untravelled", "Quiet", "Unmapped", "Long", "Old", "Open"),
                    List.of("Country", "Reach", "Lands", "March", "Waste", "Marches", "Bounds", "Ground", "Parts", "Quarter"));
        };
    }

    /**
     * A name the world can live with, built from what the ground is.
     *
     * <p>Two shapes out of the same two word lists, because one shape is what makes a generated
     * name sound generated. "The Deep Weald" and "The Weald of the Deep" are the same two words
     * and read as different places; with ten adjectives and ten nouns per kind that is four
     * hundred names for a country instead of sixty-four, and a world of thirty lands stops
     * colliding with itself.</p>
     *
     * <p>A pairing is refused when the adjective and the noun mean the same thing. "The Red
     * Badlands", "The Golden Savanna" and "Burning Waste" were all real output, and every one of
     * them says the biome twice.</p>
     */
    public static String template(Kind kind, RandomSource rnd) {
        Words w = words(kind);
        String head = HEADS.get(rnd.nextInt(HEADS.size()));
        String adjective = w.adjectives().get(rnd.nextInt(w.adjectives().size()));
        String noun = w.nouns().get(rnd.nextInt(w.nouns().size()));
        for (int i = 0; i < 6 && says(adjective, noun); i++) {
            noun = w.nouns().get(rnd.nextInt(w.nouns().size()));
        }
        boolean of = rnd.nextInt(4) == 0;                       // one name in four turns itself round
        String body = of ? noun + " of the " + adjective : adjective + " " + noun;
        return (head.isEmpty() ? "" : head + " ") + body;
    }

    /** Whether the two halves say the same thing, in which case the name says nothing. */
    private static boolean says(String adjective, String noun) {
        String a = adjective.toLowerCase(Locale.ROOT), n = noun.toLowerCase(Locale.ROOT);
        if (a.equals(n)) return true;
        return switch (a) {
            case "burning", "ash", "red" -> n.equals("cinders") || n.equals("glow") || n.equals("furnace") || n.equals("smoke");
            case "frozen", "white", "pale" -> n.equals("snows") || n.equals("winter") || n.equals("drifts") || n.equals("sheet");
            case "deep", "sunless", "drowning" -> n.equals("deeps") || n.equals("deep") || n.equals("fathoms") || n.equals("dark");
            case "wet", "steaming" -> n.equals("rainlands") || n.equals("waters");
            case "green" -> n.equals("green") || n.equals("grass") || n.equals("grasslands");
            case "cold" -> n.equals("rime") || n.equals("winter") || n.equals("snows");
            case "salt", "wrack", "shell" -> n.equals("tideline") || n.equals("shingle");
            case "wild", "empty", "nameless", "untravelled" -> n.equals("waste") || n.equals("wastes") || n.equals("lands");
            case "high", "sheer" -> n.equals("heights") || n.equals("peaks");
            case "root-bound", "root-thick", "tangled" -> n.equals("roots") || n.equals("tangle") || n.equals("vines");
            default -> false;
        };
    }

    /** One line about it, for the Almanac, when there is no model to write a better one. */
    public static String templateLore(Kind kind, RandomSource rnd) {
        List<String> lines = switch (kind) {
            case PLAINS, SAVANNA -> List.of(
                    "Grass to the edge of sight, and nothing standing that did not grow there.",
                    "The wind here has had a long run at it and never once been stopped.",
                    "Good country to walk and bad country to hide in.");
            case FOREST, TAIGA, JUNGLE -> List.of(
                    "The trees were here first and have not agreed to leave.",
                    "You can walk a whole day under this and never see the sun move.",
                    "Something in here is older than the trees, and the trees know it.");
            case DESERT, BADLANDS -> List.of(
                    "Everything soft has already gone; what is left is stone and patience.",
                    "The heat comes up out of the ground as much as down from the sky.",
                    "Water here is a story people tell, not a thing they carry.");
            case SWAMP -> List.of(
                    "Ground that has not decided whether it is land.",
                    "Everything that dies here stays where it fell and keeps its shape a while.",
                    "The quiet is not peace; it is everything holding still.");
            case MOUNTAIN, SNOW -> List.of(
                    "The stone up here remembers being the bottom of something.",
                    "Cold enough that the world stops arguing with you and simply wins.",
                    "There is nothing above this but weather.");
            case SHORE, OCEAN -> List.of(
                    "The sea takes the edge of it back a little every year.",
                    "Whatever the water is finished with, it leaves along this line.",
                    "Deep enough that the light gives up before the bottom does.");
            case CAVE -> List.of(
                    "No day has ever reached this.",
                    "The dark here is not the absence of a light; it is the thing itself.");
            case END -> List.of(
                    "Nothing here is growing, and nothing here has finished dying.",
                    "The stone floats because there is nothing left to hold it down.");
            case NETHER -> List.of("It has been burning for so long that burning is simply the weather.");
            case WILD -> List.of("Nobody has written anything about this yet.");
        };
        return lines.get(rnd.nextInt(lines.size()));
    }

    /** What kind of country the middle of a cell is. */
    public static Kind kindAt(ServerLevel level, BlockPos at) {
        if (level.dimension() == net.minecraft.world.level.Level.END) return Kind.END;
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) return Kind.NETHER;
        Holder<Biome> biome = level.getBiome(at);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return Kind.OCEAN;
        if (biome.is(BiomeTags.IS_BEACH)) return Kind.SHORE;
        if (biome.is(BiomeTags.IS_RIVER)) return Kind.SHORE;
        if (biome.is(BiomeTags.IS_MOUNTAIN)) return Kind.MOUNTAIN;
        if (biome.is(BiomeTags.IS_BADLANDS)) return Kind.BADLANDS;
        if (biome.is(BiomeTags.IS_JUNGLE)) return Kind.JUNGLE;
        if (biome.is(BiomeTags.IS_SAVANNA)) return Kind.SAVANNA;
        if (biome.is(BiomeTags.IS_TAIGA)) return Kind.TAIGA;
        if (biome.is(BiomeTags.IS_FOREST)) return Kind.FOREST;
        String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (path.contains("desert")) return Kind.DESERT;
        if (path.contains("swamp")) return Kind.SWAMP;
        if (path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.contains("grove")) return Kind.SNOW;
        if (path.contains("plain") || path.contains("meadow") || path.contains("cherry")) return Kind.PLAINS;
        if (path.contains("cave") || path.contains("deep_dark") || path.contains("lush")) return Kind.CAVE;
        return Kind.WILD;
    }

    /** The biome's own name, tidied up, for the model to work from. */
    public static String biomeName(ServerLevel level, BlockPos at) {
        return level.getBiome(at).unwrapKey()
                .map(k -> k.location().getPath().replace('_', ' '))
                .orElse("unknown")
                .toLowerCase(Locale.ROOT);
    }
}
