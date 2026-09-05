package me.lovkar.wakingworld.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Letters written by Gemini, when the server owner has put an API key in the server config. The
 * key never leaves the server; the request runs off-thread; the item polls for the result and falls
 * back to the template letter if the model is slow, down or misconfigured. The model gets the
 * facts ({@link Letters.Facts}) and a voice to write in; the exact offerings line is added by the
 * code, so the AI can never get the rite wrong.
 */
public final class GeminiLetters {
    private GeminiLetters() {
    }

    public record Result(Letters.Written written, boolean ok) {
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "wakingworld-letters");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<UUID, CompletableFuture<Result>> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Letters.Facts> FACTS = new ConcurrentHashMap<>();
    private static long lastFailure = 0;

    public static boolean enabled() {
        String key = WakingConfig.geminiApiKey();
        return WakingConfig.geminiLetters() && key != null && key.length() > 10 && System.currentTimeMillis() - lastFailure > 60_000;
    }

    /** Starts writing; the facts are kept so the template can stand in if the model fails. */
    public static UUID request(Letters.Facts facts) {
        UUID id = UUID.randomUUID();
        FACTS.put(id, facts);
        CompletableFuture<Result> f = CompletableFuture.supplyAsync(() -> call(facts), POOL)
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    WakingWorld.LOGGER.warn("Gemini letter failed: {}", ex.toString());
                    lastFailure = System.currentTimeMillis();
                    return new Result(null, false);
                });
        PENDING.put(id, f);
        return id;
    }

    /** The facts a pending letter was started from, or null if this server never saw the request (restart). */
    public static Letters.Facts facts(UUID id) {
        return FACTS.get(id);
    }

    /** Null while the model is still writing; afterwards the result (ok=false: use the template). */
    public static Result poll(UUID id) {
        CompletableFuture<Result> f = PENDING.get(id);
        if (f == null) return new Result(null, false);
        if (!f.isDone()) return null;
        PENDING.remove(id);
        FACTS.remove(id);
        try {
            return f.join();
        } catch (Exception e) {
            return new Result(null, false);
        }
    }

    // ------------------------------------------------------------------ the call

    /**
     * The model the API told us to write with instead of the configured one: Google retires models,
     * and a retired model's 404 names its successor ("...use models/gemini-x-flash..."). Followed once
     * found, for the rest of the session, so the letters keep coming while the config still says the
     * old name.
     */
    private static volatile String successor;

    private static final java.util.regex.Pattern MODEL_NAME = java.util.regex.Pattern.compile("models/([A-Za-z0-9._-]+)");

    /** The model a 404 body points to, if it points to one other than {@code model}. */
    static String successorIn(String body, String model) {
        if (body == null) return null;
        java.util.regex.Matcher m = MODEL_NAME.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            if (!name.equals(model)) return name;
        }
        return null;
    }

    private static Result call(Letters.Facts f) {
        try {
            String configured = WakingConfig.geminiModel();
            String model = successor != null ? successor : configured;
            HttpResponse<String> res = send(model, f);
            if (res.statusCode() == 404) {
                String next = successorIn(res.body(), model);
                if (next != null) {
                    WakingWorld.LOGGER.info("Gemini letter: the model {} is gone; the API points to {} - writing with that. Set geminiModel = \"{}\" in the config to keep it.", model, next, next);
                    successor = next;
                    res = send(next, f);
                }
            }
            if (res.statusCode() / 100 != 2) {
                WakingWorld.LOGGER.warn("Gemini letter: HTTP {} {}", res.statusCode(), res.body().length() > 300 ? res.body().substring(0, 300) : res.body());
                lastFailure = System.currentTimeMillis();
                return new Result(null, false);
            }
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            String text = answerText(root);
            if (text == null) return new Result(null, false);
            JsonObject letter = JsonParser.parseString(text).getAsJsonObject();
            String title = str(letter, "title"), bodyText = str(letter, "letter"), before = str(letter, "hint_before"), after = str(letter, "hint_after"), margin = str(letter, "margin_hand");
            if (title.isBlank() || bodyText.length() < 80) return new Result(null, false);
            return new Result(Letters.assemble(f, title, bodyText, before, after, margin), true);
        } catch (Exception e) {
            WakingWorld.LOGGER.warn("Gemini letter failed: {}", e.toString());
            lastFailure = System.currentTimeMillis();
            return new Result(null, false);
        }
    }

    /** One generateContent request to {@code model} for the letter's facts. */
    private static HttpResponse<String> send(String model, Letters.Facts f) throws java.io.IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt(f));
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        body.add("contents", contents);
        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", 1.0);
        gen.addProperty("responseMimeType", "application/json");
        gen.add("responseSchema", schema());
        body.add("generationConfig", gen);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", WakingConfig.geminiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** The first candidate's first written part - the thinking models' thought parts (no text, or marked "thought") are skipped. */
    static String answerText(JsonObject root) {
        if (!root.has("candidates")) return null;
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates.isEmpty()) return null;
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || !content.has("parts")) return null;
        for (com.google.gson.JsonElement e : content.getAsJsonArray("parts")) {
            JsonObject part = e.getAsJsonObject();
            if (part.has("thought") && part.get("thought").isJsonPrimitive() && part.get("thought").getAsBoolean()) continue;
            if (part.has("text") && !part.get("text").isJsonNull()) return part.get("text").getAsString();
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static JsonObject schema() {
        JsonObject s = new JsonObject();
        s.addProperty("type", "OBJECT");
        JsonObject props = new JsonObject();
        for (String k : new String[]{"title", "letter", "hint_before", "hint_after", "margin_hand"}) {
            JsonObject p = new JsonObject();
            p.addProperty("type", "STRING");
            props.add(k, p);
        }
        s.add("properties", props);
        JsonArray req = new JsonArray();
        for (String k : new String[]{"title", "letter", "hint_before", "hint_after", "margin_hand"}) req.add(k);
        s.add("required", req);
        return s;
    }

    private static String voiceBrief(Letters.Voice v) {
        return switch (v) {
            case MINER -> "Aldo, an old miner: blunt, practical, superstitious about rock, fond of his pick, signs 'Aldo, who dug'";
            case PILGRIM -> "Wren, a pilgrim on the long road: gentle, wondering, addresses the next traveller, signs 'Wren of the long road'";
            case SCHOLAR -> "Brother Halm, an elderly scholar-monk: precise, numbered observations, dry humour, signs 'Brother Halm'";
            case CHILD -> "Pip, a child of about nine: excited, misspells a little (its/dont), capitals for emphasis, mentions mum and dad, signs 'Pip'";
            case SOLDIER -> "Sergeant Osk: terse military report, ranks and orders, dark humour, signs 'Sergeant Osk'";
        };
    }

    private static String prompt(Letters.Facts f) {
        Letters.Target t = f.target();
        StringBuilder sb = new StringBuilder();
        sb.append("You write in-game lore letters for a Minecraft mod called The Waking World. Long ago the people of this land built giants ('colossi') out of the land itself - ")
                .append("stone, earth, sand, ice, sea-stone and living moss - and laid them down to sleep under shrines with an altar at the heart of each. The people have since fled; ")
                .append("their letters are found in ruins and vaults. One walled town still stands with a king in it. A letter is written by one of the people who lived here, before they left, and it points the reader towards a place.\n\n");
        sb.append("Write ONE letter in the voice of ").append(voiceBrief(f.voice())).append(".\n");
        sb.append("Language: ").append(WakingConfig.letterLanguage()).append(". Keep names and item names as given.\n\n");
        sb.append("FACTS (use them, do not contradict them):\n");
        sb.append("- The letter is found in ").append(f.foundIn()).append(", in a ").append(f.biome()).append(" landscape.\n");
        if (!f.around().isEmpty()) sb.append("- Nearby country: ").append(String.join("; ", f.around())).append(".\n");
        if (t.type().equals("shrine")) {
            sb.append("- It points to ").append(Letters.shrineSight(t.kind())).append(", ").append(f.dist()).append(" to the ").append(f.dir()).append(" of where the letter lies.\n");
            sb.append("- Under it sleeps ").append(Letters.colossusOf(t.kind())).append(".\n");
            sb.append("- The altar there wants three offerings: ").append(Letters.exactHintPlain(t)).append(". The ember, the rune and a horn are kept in the old vaults under fallen watchtowers, guarded by stone thralls. When all is laid down, the Horn of Waking is sounded over the altar and the giant rises facing away from whoever blows.\n");
        } else if (t.type().equals("dungeon")) {
            boolean forge = "forge".equals(t.kind());
            sb.append("- It points to ").append(forge ? "the Ember Forge: a roofless ruined smithy with an anvil and a lone smoking chimney, a stair behind the anvil down to a hall where lava runs in channels, a great furnace burns and the burnt dead still work"
                    : "the Drowned Cistern: a mossy pump house by the water with a hatch and a ladder down to a flooded pillared hall with green lamps under the water, walkways and an island with the keepers' hoard").append(", ").append(f.dist()).append(" to the ").append(f.dir()).append(" of where the letter lies.\n");
            sb.append("- Inside: ").append(Letters.exactHintPlain(t)).append(".\n");
        } else if (t.type().equals("kingdom")) {
            sb.append("- It points to the last walled town, where people still live: a ring wall with eight towers and a moat, two gatehouses with portcullis and drawbridge, a keep with a great hall where a king holds audience, a market square, ").append(f.dist()).append(" to the ").append(f.dir()).append(" of where the letter lies.\n");
            sb.append("- There: ").append(Letters.exactHintPlain(t)).append(".\n");
        } else {
            sb.append("- It points to one of the old vaults: the round stump of a watchtower on level ground with a shaft and a ladder in its middle, ").append(f.dist()).append(" to the ").append(f.dir()).append(" of where the letter lies.\n");
            sb.append("- Inside: a pillared hall, four rooms, a crypt under the hall. ").append(Letters.exactHintPlain(t)).append(".\n");
        }
        sb.append("- It is day ").append(f.day()).append(" of the reader's time here, weather: ").append(f.weather()).append(". The reader is called ").append(f.player()).append(" (the writer may address 'whoever finds this' - the writer never met them).\n");
        if (!f.events().isEmpty()) {
            sb.append("- Things that have happened in this world lately (the writer may allude to them as rumours, omens or things seen):\n");
            for (String e : f.events()) sb.append("  * ").append(e).append("\n");
        }
        sb.append("\nRULES: 140-220 words for the letter body, vivid and specific, in character, with a reason the writer knows the place and a reason they are leaving or cannot go. ");
        sb.append("Mention the distance and direction in words as given (no numbers other than paces). Never give coordinates. Never mention Minecraft, mods, players, blocks or game terms. ");
        sb.append("Then: hint_before = one or two sentences in the same voice leading into a list of what the place wants (the list itself is added by the game, do not write it); ");
        sb.append("hint_after = one or two sentences of practical advice or warning in the same voice; margin_hand = a short label for a note in a different hand, like 'Added later, in charcoal:'. ");
        sb.append("title = a title of at most 30 characters, evocative, in the writer's words.");
        return sb.toString();
    }
}
