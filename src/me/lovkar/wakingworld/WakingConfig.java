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
    private static final ModConfigSpec.BooleanValue METEOR_SHOWERS;
    private static final ModConfigSpec.DoubleValue METEOR_CHANCE;
    private static final ModConfigSpec.IntValue DAYS_BETWEEN_SHOWERS;
    private static final ModConfigSpec.IntValue METEORS_PER_SHOWER;
    private static final ModConfigSpec.IntValue SHOWER_LENGTH;
    private static final ModConfigSpec.IntValue METEOR_SAFE_RADIUS;
    private static final ModConfigSpec.BooleanValue VOLCANOES;
    private static final ModConfigSpec.DoubleValue VOLCANO_CHANCE;
    private static final ModConfigSpec.IntValue DAYS_BETWEEN_VOLCANOES;
    private static final ModConfigSpec.IntValue VOLCANO_HEIGHT;
    private static final ModConfigSpec.IntValue VOLCANO_RADIUS;
    private static final ModConfigSpec.IntValue VOLCANO_MINUTES;
    private static final ModConfigSpec.BooleanValue BLOOD_MOONS;
    private static final ModConfigSpec.DoubleValue BLOOD_MOON_CHANCE;
    private static final ModConfigSpec.IntValue DAYS_BETWEEN_BLOOD_MOONS;
    private static final ModConfigSpec.IntValue BLOOD_MOON_WAVE;
    private static final ModConfigSpec.IntValue BLOOD_MOON_WAVE_SECONDS;
    private static final ModConfigSpec.BooleanValue TORNADOES;
    private static final ModConfigSpec.DoubleValue TORNADO_CHANCE;
    private static final ModConfigSpec.IntValue DAYS_BETWEEN_TORNADOES;
    private static final ModConfigSpec.IntValue TORNADO_SECONDS;
    private static final ModConfigSpec.BooleanValue EARTHQUAKES;
    private static final ModConfigSpec.DoubleValue EARTHQUAKE_CHANCE;
    private static final ModConfigSpec.IntValue DAYS_BETWEEN_EARTHQUAKES;
    private static final ModConfigSpec.IntValue EARTHQUAKE_SECONDS;
    private static final ModConfigSpec.BooleanValue OMENS;
    private static final ModConfigSpec.IntValue OMEN_SECONDS;
    private static final ModConfigSpec.BooleanValue UNREST;
    private static final ModConfigSpec.IntValue UNREST_DAYS;
    private static final ModConfigSpec.DoubleValue UNREST_FACTOR;
    private static final ModConfigSpec.DoubleValue ANSWER_CHANCE;
    private static final ModConfigSpec.BooleanValue MOON_COLOSSI;
    private static final ModConfigSpec.IntValue VOLCANO_COOL_MINUTES;
    private static final ModConfigSpec.BooleanValue BLIGHT;
    private static final ModConfigSpec.BooleanValue GREETING;
    private static final ModConfigSpec.BooleanValue NAMED_LANDS;
    private static final ModConfigSpec.BooleanValue F_COLOSSI;
    private static final ModConfigSpec.BooleanValue F_TITAN;
    private static final ModConfigSpec.BooleanValue F_CATACLYSMS;
    private static final ModConfigSpec.BooleanValue F_KINGDOMS;
    private static final ModConfigSpec.BooleanValue F_RUINS;
    private static final ModConfigSpec.IntValue LAND_SIZE;
    private static final ModConfigSpec.BooleanValue GEMINI_LANDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The parts of the mod. Switch one off and it is not there at all: nothing of it generates,",
                        "nothing of it runs, and the rest of the mod carries on without it. Take only the giants,",
                        "or only the cataclysms, or everything but the kingdoms.",
                        "In a new world a part switched off was never there at all. In a world that has already",
                        "been played, what it built is already standing and stays: nothing new of it is placed,",
                        "nothing of it begins again, and the buildings and people already in the world go on as they were.",
                        "Everything below this section is the finer grain: which of the five cataclysms, how big a crater.")
                .push("features");
        F_COLOSSI = b.comment("The giants: the six shrines that hold them, the Sleeper's Vaults the offerings lie in,",
                        "the rites that wake them, the fight, the hourglass that puts the ground back.",
                        "Off: no shrine and no vault generates, an altar stays quiet, and nothing rises.",
                        "The /wakingworld summon commands still work, so an operator can still show somebody.")
                .define("colossi", true);
        F_TITAN = b.comment("The seventh and last of them, in the End: the void reliquaries, the arena and the Titan itself.",
                        "Off with colossi on: the six sleepers are still in the world, the ending is not.",
                        "This does nothing with colossi off - there is no rite to finish.")
                .define("titan", true);
        F_CATACLYSMS = b.comment("The five: the Falling Sky, the Rising Mountain, the Blood Moon, the Wandering Column,",
                        "the Turning Ground - with their omens, the unrest a giant leaves behind, and the blight.",
                        "Off: the weather is Minecraft's again. The /wakingworld commands still work.")
                .define("cataclysms", true);
        F_KINGDOMS = b.comment("The living towns: their kings, guards, townsfolk and traders, the permits and the treasury.",
                        "Off: no kingdom generates and none of its people are in the world.")
                .define("kingdoms", true);
        F_RUINS = b.comment("The dead world you walk through: the ruins, the empty hamlets, the ember forges and the",
                        "drowned cisterns, the Dead Letters in them, and the thralls, wraiths and keepers that wander.",
                        "Off: the country is empty of the old people. The vaults stay - they belong to the giants.")
                .define("ruins", true);
        NAMED_LANDS = b.comment("The Named Lands: the world is divided into squares, and each one is named the first time somebody walks into it.",
                        "The name is shown once, as a title card, and then lives in /wakingworld lands and on the Wayfarer's Chart.",
                        "Off: the Chart is blank, and a land is just ground again.")
                .define("namedLands", true);
        b.pop();
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
        b.push("cataclysms");
        METEOR_SHOWERS = b.comment("The Falling Sky: on a rare night the stars come down around whoever is out in the open.",
                "Off: no meteor showers at all (the /wakingworld meteor commands still work).").define("meteorShowers", true);
        METEOR_CHANCE = b.comment("The chance, rolled once at dusk on any night that is allowed one, that a shower starts (0.1 = one night in ten).")
                .defineInRange("meteorChance", 0.10, 0.0, 1.0);
        DAYS_BETWEEN_SHOWERS = b.comment("Days that must pass after a shower before another may start.")
                .defineInRange("daysBetweenShowers", 8, 0, 1000);
        METEORS_PER_SHOWER = b.comment("How many stars fall in one shower (they come 5-13 seconds apart).")
                .defineInRange("meteorsPerShower", 14, 1, 200);
        SHOWER_LENGTH = b.comment("How long a shower may last, in seconds, however many stars are left.")
                .defineInRange("showerLengthSeconds", 180, 20, 3600);
        METEOR_SAFE_RADIUS = b.comment("No star falls this close to the world spawn or to any player's bed (0 = nowhere is safe).")
                .defineInRange("meteorSafeRadius", 64, 0, 512);
        VOLCANOES = b.comment("The Rising Mountain: a volcano that builds itself in front of you over a few minutes and leaves a real mountain.",
                "Off: no volcanoes (the /wakingworld volcano command still works).").define("volcanoes", true);
        VOLCANO_CHANCE = b.comment("The chance, rolled once in the morning on any day that is allowed one, that a volcano opens.")
                .defineInRange("volcanoChance", 0.06, 0.0, 1.0);
        DAYS_BETWEEN_VOLCANOES = b.comment("Days that must pass after a volcano before another may open.")
                .defineInRange("daysBetweenVolcanoes", 14, 0, 1000);
        VOLCANO_HEIGHT = b.comment("How many courses tall the cone grows - roughly its height in blocks above the ground it stands on.")
                .defineInRange("volcanoHeight", 28, 6, 120);
        VOLCANO_RADIUS = b.comment("The radius of the foot of the cone, in blocks. The cone tapers as it climbs.")
                .defineInRange("volcanoRadius", 16, 6, 48);
        VOLCANO_MINUTES = b.comment("How long the mountain takes to rise, in minutes. Longer is calmer on the server and better to watch.")
                .defineInRange("volcanoMinutes", 1, 1, 60);
        BLOOD_MOONS = b.comment("The Blood Moon: a night that keeps sending monsters at you until the sun comes up.",
                "Off: no blood moons (the /wakingworld bloodmoon command still works).").define("bloodMoons", true);
        BLOOD_MOON_CHANCE = b.comment("The chance, rolled once at nightfall on any night that is allowed one, that the moon turns.")
                .defineInRange("bloodMoonChance", 0.08, 0.0, 1.0);
        DAYS_BETWEEN_BLOOD_MOONS = b.comment("Days that must pass after a blood moon before another may rise.")
                .defineInRange("daysBetweenBloodMoons", 10, 0, 1000);
        BLOOD_MOON_WAVE = b.comment("How many monsters a wave may place around one player (it starts smaller and works up).")
                .defineInRange("bloodMoonWaveSize", 6, 1, 40);
        BLOOD_MOON_WAVE_SECONDS = b.comment("Seconds between waves.")
                .defineInRange("bloodMoonWaveSeconds", 12, 3, 300);
        TORNADOES = b.comment("The Wandering Column: a tornado that walks across the country, lifting what is loose.",
                "It never takes a block that is under a roof, in a lit room, or holds anything.",
                "Off: no tornadoes (the /wakingworld tornado command still works).").define("tornadoes", true);
        TORNADO_CHANCE = b.comment("The chance, rolled once in the afternoon on any day that is allowed one, that a tornado forms.")
                .defineInRange("tornadoChance", 0.07, 0.0, 1.0);
        DAYS_BETWEEN_TORNADOES = b.comment("Days that must pass after a tornado before another may form.")
                .defineInRange("daysBetweenTornadoes", 9, 0, 1000);
        TORNADO_SECONDS = b.comment("How long a tornado lives, in seconds.")
                .defineInRange("tornadoSeconds", 90, 10, 1200);
        EARTHQUAKES = b.comment("The Turning Ground: half a minute of shaking, and a fault opened across the country.",
                "Off: no earthquakes (the /wakingworld earthquake command still works).").define("earthquakes", true);
        EARTHQUAKE_CHANCE = b.comment("The chance, rolled once a day on any day that is allowed one, that the ground turns.")
                .defineInRange("earthquakeChance", 0.07, 0.0, 1.0);
        DAYS_BETWEEN_EARTHQUAKES = b.comment("Days that must pass after an earthquake before another may come.")
                .defineInRange("daysBetweenEarthquakes", 11, 0, 1000);
        EARTHQUAKE_SECONDS = b.comment("How long the shaking lasts, in seconds.")
                .defineInRange("earthquakeSeconds", 26, 5, 300);
        OMENS = b.comment("Warn the world before a cataclysm: a low note out of the ground, the light going wrong,",
                "the animals leaving, and a line in the chat. Off: they simply begin.").define("omens", true);
        OMEN_SECONDS = b.comment("How long the warning runs before the cataclysm itself, in seconds.")
                .defineInRange("omenSeconds", 40, 5, 600);
        UNREST = b.comment("The ground does not settle where a giant rose. Waking one, and killing one, leave the land",
                "around it unquiet for a while: cataclysms are likelier there, and they aim at it.",
                "Off: the two halves of the mod ignore each other, as they did in 0.1.").define("unrest", true);
        UNREST_DAYS = b.comment("How many days the land stays unquiet after a giant rose or fell there.")
                .defineInRange("unrestDays", 12, 1, 400);
        UNREST_FACTOR = b.comment("How much likelier a cataclysm is over the most unquiet ground (1 = no difference,",
                "2 = up to three times as likely where a giant has just died).")
                .defineInRange("unrestFactor", 2.0, 0.0, 20.0);
        ANSWER_CHANCE = b.comment("The chance that a cataclysm breaking over a sleeping giant's shrine wakes it by itself,",
                "with no rite and nobody's leave. 0 = a shrine is only ever opened by a player.")
                .defineInRange("answerChance", 0.25, 0.0, 1.0);
        MOON_COLOSSI = b.comment("A giant already awake under a blood moon is stronger and faster while it lasts.")
                .define("bloodMoonColossi", true);
        VOLCANO_COOL_MINUTES = b.comment("How long the flow takes to set into rock after the mountain is finished.",
                "It crusts over from the bottom up; the crater pool is left glowing for good.")
                .defineInRange("volcanoCoolMinutes", 8, 1, 240);
        BLIGHT = b.comment("A cataclysm flattens what is growing where it passes: crops, flowers, saplings, and",
                "the farmland under them in patches. It never breaks a block a player laid.",
                "Off: fields are the one thing in the world a cataclysm walks straight over.").define("blight", true);
        b.pop();
        b.push("story");
        GREETING = b.comment("Say once, the first time somebody joins, what this mod is and where to read the rest.",
                "Off: they find out on their own, which for the first few days looks like nothing happening.")
                .define("greeting", true);
        b.pop();
        b.push("lands");
        LAND_SIZE = b.comment("How wide a named land is, in blocks. Smaller means more names and more title cards.")
                .defineInRange("landSize", 384, 96, 4096);
        GEMINI_LANDS = b.comment("Let Gemini name the lands from what is actually on the ground there (needs geminiApiKey).",
                "Off or without a key: the built-in names, which are chosen by the terrain the same way.").define("geminiLands", true);
        b.pop();
        SPEC = b.build();
    }

    // ---- client ----------------------------------------------------------------------------

    public static final ModConfigSpec CLIENT_SPEC;
    private static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    private static final ModConfigSpec.BooleanValue BOSS_MUSIC;
    private static final ModConfigSpec.BooleanValue READ_LETTERS;
    private static final ModConfigSpec.BooleanValue SHOW_AURAS;
    private static final ModConfigSpec.BooleanValue LAND_WAYPOINTS;

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
        b.push("lands");
        LAND_WAYPOINTS = b.comment("Drop a waypoint on your map when you walk into a named land (JourneyMap and Xaero's, if you have one).",
                "Nothing happens without a map mod, and this changes nothing on the server.")
                .define("landWaypoints", true);
        b.pop();
        b.push("supporters");
        SHOW_AURAS = b.comment("Draw the supporters' auras (yours and other players'). Off hides them on this client only; it changes nothing about who has one.")
                .define("showAuras", true);
        b.pop();
        CLIENT_SPEC = b.build();
    }

    // ---- the parts of the mod ---------------------------------------------------------------
    // A part switched off is not there: its structures never find a spot, its ticks return at once,
    // and everything under it answers as though it had been switched off one by one.

    /** The giants: shrines, vaults, rites, the fight, the hourglass. */
    public static boolean colossi() {
        return loaded() && F_COLOSSI.get();
    }

    /** The Titan in the End: reliquaries, arena, the last rite. Needs {@link #colossi()}. */
    public static boolean titan() {
        return colossi() && F_TITAN.get();
    }

    /** The five, their omens, the unrest and the blight. */
    public static boolean cataclysms() {
        return loaded() && F_CATACLYSMS.get();
    }

    /** The living towns and their people. */
    public static boolean kingdoms() {
        return loaded() && F_KINGDOMS.get();
    }

    /** The ruins, the hamlets, the two dungeons, the Dead Letters and the wanderers. */
    public static boolean ruins() {
        return loaded() && F_RUINS.get();
    }

    public static boolean namedLands() {
        return loaded() && NAMED_LANDS.get();
    }

    public static int landSize() {
        return loaded() ? LAND_SIZE.get() : 384;
    }

    public static boolean geminiLands() {
        return loaded() && GEMINI_LANDS.get();
    }

    public static boolean tornadoes() {
        return cataclysms() && TORNADOES.get();
    }

    public static double tornadoChance() {
        return loaded() ? TORNADO_CHANCE.get() : 0.07;
    }

    public static int daysBetweenTornadoes() {
        return loaded() ? DAYS_BETWEEN_TORNADOES.get() : 9;
    }

    public static int tornadoSeconds() {
        return loaded() ? TORNADO_SECONDS.get() : 90;
    }

    public static boolean earthquakes() {
        return cataclysms() && EARTHQUAKES.get();
    }

    public static double earthquakeChance() {
        return loaded() ? EARTHQUAKE_CHANCE.get() : 0.07;
    }

    public static int daysBetweenEarthquakes() {
        return loaded() ? DAYS_BETWEEN_EARTHQUAKES.get() : 11;
    }

    public static boolean omens() {
        return cataclysms() && OMENS.get();
    }

    public static int omenSeconds() {
        return loaded() ? OMEN_SECONDS.get() : 40;
    }

    public static int earthquakeSeconds() {
        return loaded() ? EARTHQUAKE_SECONDS.get() : 26;
    }

    public static boolean unrest() {
        return cataclysms() && colossi() && UNREST.get();
    }

    public static int unrestDays() {
        return loaded() ? UNREST_DAYS.get() : 12;
    }

    public static double unrestFactor() {
        return loaded() ? UNREST_FACTOR.get() : 2.0;
    }

    public static double answerChance() {
        return cataclysms() && colossi() ? ANSWER_CHANCE.get() : 0.0;
    }

    public static boolean bloodMoonColossi() {
        return cataclysms() && colossi() && MOON_COLOSSI.get();
    }

    public static int volcanoCoolMinutes() {
        return loaded() ? VOLCANO_COOL_MINUTES.get() : 8;
    }

    public static boolean blight() {
        return cataclysms() && BLIGHT.get();
    }

    public static boolean greeting() {
        return loaded() && GREETING.get();
    }

    public static boolean bloodMoons() {
        return cataclysms() && BLOOD_MOONS.get();
    }

    public static double bloodMoonChance() {
        return loaded() ? BLOOD_MOON_CHANCE.get() : 0.08;
    }

    public static int daysBetweenBloodMoons() {
        return loaded() ? DAYS_BETWEEN_BLOOD_MOONS.get() : 10;
    }

    public static int bloodMoonWaveSize() {
        return loaded() ? BLOOD_MOON_WAVE.get() : 6;
    }

    public static int bloodMoonWaveSeconds() {
        return loaded() ? BLOOD_MOON_WAVE_SECONDS.get() : 12;
    }

    public static boolean volcanoes() {
        return cataclysms() && VOLCANOES.get();
    }

    public static double volcanoChance() {
        return loaded() ? VOLCANO_CHANCE.get() : 0.06;
    }

    public static int daysBetweenVolcanoes() {
        return loaded() ? DAYS_BETWEEN_VOLCANOES.get() : 14;
    }

    public static int volcanoHeight() {
        return loaded() ? VOLCANO_HEIGHT.get() : 28;
    }

    public static int volcanoRadius() {
        return loaded() ? VOLCANO_RADIUS.get() : 16;
    }

    public static int volcanoMinutes() {
        return loaded() ? VOLCANO_MINUTES.get() : 4;
    }

    public static boolean meteorShowers() {
        return cataclysms() && METEOR_SHOWERS.get();
    }

    public static double meteorChance() {
        return loaded() ? METEOR_CHANCE.get() : 0.10;
    }

    public static int daysBetweenShowers() {
        return loaded() ? DAYS_BETWEEN_SHOWERS.get() : 8;
    }

    public static int meteorsPerShower() {
        return loaded() ? METEORS_PER_SHOWER.get() : 14;
    }

    public static int showerLength() {
        return loaded() ? SHOWER_LENGTH.get() : 180;
    }

    public static int meteorSafeRadius() {
        return loaded() ? METEOR_SAFE_RADIUS.get() : 64;
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

    public static boolean landWaypoints() {
        return CLIENT_SPEC.isLoaded() ? LAND_WAYPOINTS.get() : true;
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
