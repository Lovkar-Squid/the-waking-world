package me.lovkar.wakingworld.land;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.story.GeminiLetters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A land's name, written by Gemini from what is actually on the ground there.
 *
 * <p>Same shape as the Dead Letters: the key never leaves the server, the request runs off-thread,
 * and if the model is slow, down or not configured the templates take over without anybody
 * noticing. One request per cell, ever - the answer is then saved with the world.</p>
 */
public final class GeminiLands {
    private GeminiLands() {
    }

    public record Result(String name, String lore, boolean ok) {
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "wakingworld-lands");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<Long, CompletableFuture<Result>> PENDING = new ConcurrentHashMap<>();
    private static long lastFailure = 0;
    /** Why the last land fell back to the templates, so the log can say rather than stay silent. */
    private static volatile String why = "not asked yet";
    private static volatile String successor;

    public static boolean enabled() {
        String key = WakingConfig.geminiApiKey();
        if (!WakingConfig.geminiLands()) { why = "geminiLands is off"; return false; }
        if (key == null || key.length() <= 10) { why = "no geminiApiKey is set"; return false; }
        if (System.currentTimeMillis() - lastFailure <= 60_000) { why = "the model failed a moment ago"; return false; }
        return true;
    }

    /** What the last fallback was about. */
    public static String why() {
        return why;
    }

    /** Ask about one cell. Returns false when one is already in flight for it. */
    public static boolean start(long cell, ServerLevel level, BlockPos middle, LandNames.Kind kind) {
        if (PENDING.containsKey(cell)) return true;
        String facts = facts(level, middle, kind);
        PENDING.put(cell, CompletableFuture.supplyAsync(() -> call(facts), POOL)
                .orTimeout(25, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    why = "the request did not come back in time";
                    WakingWorld.LOGGER.warn("Gemini land name failed: {}", ex.toString());
                    lastFailure = System.currentTimeMillis();
                    return new Result(null, null, false);
                }));
        return true;
    }

    /** Null while it is still being written; the answer once, and then the slot is cleared. */
    public static Result poll(long cell) {
        CompletableFuture<Result> f = PENDING.get(cell);
        if (f == null || !f.isDone()) return null;
        PENDING.remove(cell);
        try {
            return f.get();
        } catch (Exception e) {
            return new Result(null, null, false);
        }
    }

    /** What the model is told: the ground, and nothing about the player. */
    private static String facts(ServerLevel level, BlockPos middle, LandNames.Kind kind) {
        StringBuilder sb = new StringBuilder();
        sb.append("kind: ").append(kind.english).append('\n');
        sb.append("biome in the middle: ").append(LandNames.biomeName(level, middle)).append('\n');
        // four corners of the cell, so a mixed country reads as mixed
        int size = WakingConfig.landSize();
        int cx = Lands.cellOf(middle.getX()), cz = Lands.cellOf(middle.getZ());
        java.util.LinkedHashSet<String> around = new java.util.LinkedHashSet<>();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos c = new BlockPos(cx * size + dx * (size - 1), middle.getY(), cz * size + dz * (size - 1));
                around.add(LandNames.biomeName(level, c));
            }
        }
        sb.append("biomes across it: ").append(String.join(", ", around)).append('\n');
        sb.append("height in the middle: ").append(middle.getY()).append(" (sea level is ").append(level.getSeaLevel()).append(")\n");
        sb.append("dimension: ").append(level.dimension().location().getPath()).append('\n');
        return sb.toString();
    }

    private static Result call(String facts) {
        try {
            String configured = WakingConfig.geminiModel();
            String model = successor != null ? successor : configured;
            HttpResponse<String> res = send(model, facts);
            if (res.statusCode() == 404) {
                String next = GeminiLetters.successorIn(res.body(), model);
                if (next != null) {
                    successor = next;
                    res = send(next, facts);
                }
            }
            if (res.statusCode() / 100 != 2) {
                // busy is not broken: 429 and 503 mean the model is loaded and will not be shortly
                if (res.statusCode() == 429 || res.statusCode() == 503) {
                    why = "the model is busy (HTTP " + res.statusCode() + ")";
                    WakingWorld.LOGGER.info("Gemini land name: the model is busy right now (HTTP {}); this land is named from the templates. Nothing is wrong.", res.statusCode());
                } else {
                    why = "HTTP " + res.statusCode();
                    WakingWorld.LOGGER.warn("Gemini land name: HTTP {}", res.statusCode());
                }
                lastFailure = System.currentTimeMillis();
                return new Result(null, null, false);
            }
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            String text = GeminiLetters.answerText(root);
            if (text == null) return new Result(null, null, false);
            JsonObject out = JsonParser.parseString(text).getAsJsonObject();
            String name = out.has("name") ? out.get("name").getAsString().trim() : "";
            String lore = out.has("lore") ? out.get("lore").getAsString().trim() : "";
            if (name.isBlank() || name.length() > 32 || lore.isBlank() || lore.length() > 160) {
                why = "the model's answer did not fit (name " + name.length() + ", lore " + lore.length() + ")";
                return new Result(null, null, false);
            }
            return new Result(name, lore, true);
        } catch (Exception e) {
            why = e.toString();
            WakingWorld.LOGGER.warn("Gemini land name failed: {}", e.toString());
            lastFailure = System.currentTimeMillis();
            return new Result(null, null, false);
        }
    }

    private static HttpResponse<String> send(String model, String facts) throws java.io.IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt(facts));
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        body.add("contents", contents);
        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", 1.1);
        gen.addProperty("responseMimeType", "application/json");
        gen.add("responseSchema", schema());
        body.add("generationConfig", gen);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", WakingConfig.geminiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonObject schema() {
        JsonObject props = new JsonObject();
        JsonObject name = new JsonObject();
        name.addProperty("type", "string");
        JsonObject lore = new JsonObject();
        lore.addProperty("type", "string");
        props.add("name", name);
        props.add("lore", lore);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("name");
        required.add("lore");
        schema.add("required", required);
        return schema;
    }

    private static String prompt(String facts) {
        return """
                You name places in a Minecraft world for a mod called The Waking World, in which the
                land itself is old, remembers things, and occasionally gets up and walks.

                Name the stretch of country described below.

                The name:
                - two to four words, in English, the sort of thing on an old map
                - it must fit THIS ground, not any ground: a name for a frozen coast must not work
                  for a jungle
                - no apostrophes, no invented proper nouns nobody could pronounce, no "of the"
                  constructions longer than three words, and never the word "Minecraft"
                - do not use the biome's own name as a word in it

                The lore: ONE sentence, under 140 characters, plain and a little grim, the way a
                surveyor writes when they have been out too long. No second person, no "you".

                The ground:
                """ + facts;
    }
}
