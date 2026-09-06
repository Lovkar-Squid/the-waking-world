package me.lovkar.wakingworld;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config. Giants tear up the ground - server owners get to say how much.
 */
public final class WakingConfig {
    private WakingConfig() {
    }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue TERRAIN_DAMAGE;
    private static final ModConfigSpec.BooleanValue TRAMPLE;
    private static final ModConfigSpec.DoubleValue CRATER_SCALE;
    private static final ModConfigSpec.IntValue MAX_FLYING_BLOCKS;
    private static final ModConfigSpec.IntValue COLLAPSE_BLOCKS;
    private static final ModConfigSpec.BooleanValue DEATH_MOUND;
    private static final ModConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    private static final ModConfigSpec.ConfigValue<String> GEMINI_MODEL;
    private static final ModConfigSpec.BooleanValue GEMINI_LETTERS;
    private static final ModConfigSpec.ConfigValue<String> LETTER_LANGUAGE;
    private static final ModConfigSpec.BooleanValue VOICED_LETTERS;
    private static final ModConfigSpec.ConfigValue<String> VOICE_MODEL;
    private static final ModConfigSpec.BooleanValue DRAGON_EGG_INDESTRUCTIBLE;
    private static final ModConfigSpec.BooleanValue DRAGON_EGG_EVERY_DRAGON;
    private static final ModConfigSpec.BooleanValue TITAN_NEEDS_SIGIL;
    private static final ModConfigSpec.BooleanValue TITAN_NEEDS_EGG;
    private static final ModConfigSpec.IntValue RITE_EMBERS;
    private static final ModConfigSpec.IntValue RITE_RUNES;
    private static final ModConfigSpec.DoubleValue RITE_GIFT_MULTIPLIER;
    private static final ModConfigSpec.IntValue LESSER_ALTAR_RUNES;
    private static final ModConfigSpec.DoubleValue RITE_COST_MULTIPLIER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("colossi");
        TERRAIN_DAMAGE = b.comment("Stomps, slams, landings and thrown boulders tear craters into the ground and fling the blocks around.",
                "Off: only particles, the world stays as it was.").define("terrainDamage", true);
        TRAMPLE = b.comment("A walking colossus breaks through trees, plants, snow and loose ground instead of walking around them.")
                .define("trample", true);
        CRATER_SCALE = b.comment("Multiplier on crater sizes (0.25 = small dents, 1 = default, 2 = mountains fall).")
                .defineInRange("craterScale", 1.0, 0.0, 3.0);
        MAX_FLYING_BLOCKS = b.comment("Upper bound on falling-block entities one impact may spawn (performance).")
                .defineInRange("maxFlyingBlocks", 80, 0, 400);
        COLLAPSE_BLOCKS = b.comment("How many blocks of a dying colossus come down as real falling blocks (the rest is dust). Performance.")
                .defineInRange("collapseBlocks", 450, 0, 1500);
        DEATH_MOUND = b.comment("A dead colossus leaves a mound of its own blocks where it fell (its rubble lands on top of it).")
                .define("deathMound", true);
        b.pop();
        b.push("letters");
        GEMINI_LETTERS = b.comment("Let Gemini write the Dead Letters for your world (needs geminiApiKey). Off or without a key: the built-in letters.")
                .define("geminiLetters", true);
        GEMINI_API_KEY = b.comment("Your Google AI Studio API key (https://aistudio.google.com/apikey). Stays on the server; never shown to players.")
                .define("geminiApiKey", "");
        GEMINI_MODEL = b.comment("The Gemini model to write with. A retired model's error names its successor; the mod follows that by itself, but set it here to keep it.").define("geminiModel", "gemini-3.6-flash");
        LETTER_LANGUAGE = b.comment("The language the AI writes the letters in (item names stay English).").define("letterLanguage", "English");
        VOICED_LETTERS = b.comment("Have Gemini read every Dead Letter aloud in its writer's voice (needs geminiApiKey; the voice is made once,",
                "when the letter is written, and kept with the world). A free key allows only a handful of voices a day; the rest stay silent.").define("voicedLetters", true);
        VOICE_MODEL = b.comment("The Gemini text-to-speech model for the letters' voices.").define("voiceModel", "gemini-2.5-flash-preview-tts");
        b.pop();
        b.push("rites");
        RITE_COST_MULTIPLIER = b.comment("Multiplies every count below (0.5 = half price, 2 = double); each offering still needs at least one.")
                .defineInRange("riteCostMultiplier", 1.0, 0.0, 10.0);
        RITE_EMBERS = b.comment("Sleeper's Embers a shrine's altar wants for its rite.").defineInRange("riteEmbers", 1, 1, 64);
        RITE_RUNES = b.comment("Runes of the shrine's own kind its altar wants.").defineInRange("riteRunes", 1, 1, 64);
        RITE_GIFT_MULTIPLIER = b.comment("Multiplies the land's gift (amethyst, rooted dirt, gold, blue ice, prismarine crystals, glow berries) - the defaults are 4 or 8.")
                .defineInRange("riteGiftMultiplier", 1.0, 0.0, 10.0);
        LESSER_ALTAR_RUNES = b.comment("Runes of its land each of the six lesser altars round the Titan's wants (0 = the lesser altars are not needed).")
                .defineInRange("lesserAltarRunes", 2, 0, 64);
        TITAN_NEEDS_SIGIL = b.comment("The Titan's altar wants the Void Sigil out of a reliquary besides the Key.").define("titanNeedsSigil", true);
        TITAN_NEEDS_EGG = b.comment("The Titan's altar wants the Dragon Egg besides the Key (it is given back when the Titan falls).").define("titanNeedsEgg", true);
        b.pop();
        b.push("titan");
        DRAGON_EGG_INDESTRUCTIBLE = b.comment("The Titan's rite needs the Dragon Egg, and the End has only the one. On: a dropped egg cannot burn, blow up or despawn,",
                "and one that falls into the void is set back on the island it fell from.").define("dragonEggIndestructible", true);
        DRAGON_EGG_EVERY_DRAGON = b.comment("On: every Ender Dragon that dies leaves an egg on the podium if none lies there, not only the first (vanilla) - so a lost egg can be replaced.")
                .define("dragonEggEveryDragon", true);
        b.pop();
        SPEC = b.build();
    }

    // ---- client ----------------------------------------------------------------------------

    public static final ModConfigSpec CLIENT_SPEC;
    private static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    private static final ModConfigSpec.BooleanValue BOSS_MUSIC;
    private static final ModConfigSpec.BooleanValue READ_LETTERS;
    private static final ModConfigSpec.BooleanValue SHOW_AURAS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("colossi");
        CAMERA_SHAKE = b.comment("How much the camera shakes when a colossus stomps, lands or walks nearby (0 = off, 1 = default, 2 = double).")
                .defineInRange("cameraShake", 1.0, 0.0, 2.0);
        BOSS_MUSIC = b.comment("Play the colossus battle themes (awakening, one theme per kind, victory). Uses the Music volume slider.")
                .define("bossMusic", true);
        b.pop();
        b.push("letters");
        READ_LETTERS = b.comment("Read a Dead Letter aloud when it is opened, if the server made a voice for it (the speaker on the letter starts and stops it either way). Uses the Voice/Speech volume slider.")
                .define("readLettersAloud", true);
        b.pop();
        b.push("supporters");
        SHOW_AURAS = b.comment("Draw the supporters' auras (yours and other players'). Off hides them on this client only; it changes nothing about who has one.")
                .define("showAuras", true);
        b.pop();
        CLIENT_SPEC = b.build();
    }

    public static double cameraShake() {
        return CLIENT_SPEC.isLoaded() ? CAMERA_SHAKE.get() : 1.0;
    }

    public static boolean bossMusic() {
        return CLIENT_SPEC.isLoaded() ? BOSS_MUSIC.get() : true;
    }

    public static boolean readLettersAloud() {
        return CLIENT_SPEC.isLoaded() ? READ_LETTERS.get() : true;
    }

    public static boolean showAuras() {
        return CLIENT_SPEC.isLoaded() ? SHOW_AURAS.get() : true;
    }

    public static String geminiApiKey() {
        return loaded() ? GEMINI_API_KEY.get() : "";
    }

    public static String geminiModel() {
        return loaded() ? GEMINI_MODEL.get() : "gemini-3.6-flash";
    }

    public static boolean geminiLetters() {
        return loaded() && GEMINI_LETTERS.get();
    }

    public static boolean voicedLetters() {
        return loaded() && VOICED_LETTERS.get();
    }

    public static String voiceModel() {
        return loaded() ? VOICE_MODEL.get() : "gemini-2.5-flash-preview-tts";
    }

    public static String letterLanguage() {
        return loaded() ? LETTER_LANGUAGE.get() : "English";
    }

    /** A rite count after the multiplier: never below one. */
    private static int scaled(int count) {
        double m = loaded() ? RITE_COST_MULTIPLIER.get() : 1.0;
        return Math.max(1, (int) Math.round(count * m));
    }

    public static int riteEmbers() {
        return scaled(loaded() ? RITE_EMBERS.get() : 1);
    }

    public static int riteRunes() {
        return scaled(loaded() ? RITE_RUNES.get() : 1);
    }

    /** The land's gift, from its default count. */
    public static int riteGift(int base) {
        double g = loaded() ? RITE_GIFT_MULTIPLIER.get() : 1.0;
        return scaled(Math.max(1, (int) Math.round(base * g)));
    }

    /** Runes for each lesser altar of the arena; 0 = none wanted. */
    public static int lesserAltarRunes() {
        int n = loaded() ? LESSER_ALTAR_RUNES.get() : 2;
        return n == 0 ? 0 : scaled(n);
    }

    public static boolean titanNeedsSigil() {
        return loaded() ? TITAN_NEEDS_SIGIL.get() : true;
    }

    public static boolean titanNeedsEgg() {
        return loaded() ? TITAN_NEEDS_EGG.get() : true;
    }

    public static boolean dragonEggIndestructible() {
        return loaded() ? DRAGON_EGG_INDESTRUCTIBLE.get() : true;
    }

    public static boolean dragonEggEveryDragon() {
        return loaded() ? DRAGON_EGG_EVERY_DRAGON.get() : true;
    }

    public static int collapseBlocks() {
        return loaded() ? COLLAPSE_BLOCKS.get() : 450;
    }

    public static boolean deathMound() {
        return loaded() ? DEATH_MOUND.get() : true;
    }

    public static boolean terrainDamage() {
        return loaded() ? TERRAIN_DAMAGE.get() : true;
    }

    public static boolean trample() {
        return loaded() ? TRAMPLE.get() : true;
    }

    public static double craterScale() {
        return loaded() ? CRATER_SCALE.get() : 1.0;
    }

    public static int maxFlyingBlocks() {
        return loaded() ? MAX_FLYING_BLOCKS.get() : 80;
    }

    private static boolean loaded() {
        return SPEC.isLoaded();
    }
}
