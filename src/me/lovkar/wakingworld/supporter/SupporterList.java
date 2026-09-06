package me.lovkar.wakingworld.supporter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.lovkar.wakingworld.WakingWorld;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The supporter list, on either side. Fetched from the supporter service, cached in memory and
 * refreshed every few minutes: the client reads it to draw the auras, the server reads it when a
 * rite is performed, to dress the colossus in the waker's chosen style. Everything here is
 * cosmetic: a Patreon tier (waker / colossus / titan) and the cosmetics the supporter chose on
 * the service, nothing that touches gameplay.
 *
 * The service address is fixed on purpose - it is not a config option, so nobody can point the
 * mod at a list of their own. The service is also the only place perks are decided: it checks
 * every choice against the tier that paid for it, and the mod just applies what it is told (and
 * checks the tier once more, for good measure). The list it publishes names nobody: each entry is
 * a salted SHA-256 of a player's UUID, so the mod can recognise the supporters it meets, but the
 * list cannot be read as a roll of names.
 *
 * All failures are swallowed - if the service is down or the machine is offline, there simply
 * are no perks.
 */
public final class SupporterList {
    private SupporterList() {
    }

    /** Where the supporter service lives. Deliberately a constant. */
    public static final String BASE_URL = "https://supporters.lovkarsquid.com";

    public static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wakingworld-supporters");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final long INTERVAL_MS = 5 * 60 * 1000L;
    private static final long RETRY_MS = 60 * 1000L;

    /** A supporter as the list describes them: the tier, and the ids of the cosmetics they chose (null = the tier's default). */
    public record Entry(String tier, String aura, String colossus) {
        static final Entry NONE = new Entry("", null, null);

        public int rank() {
            return SupporterTiers.rank(tier);
        }
    }

    /** One fetched list: the salt it was hashed with, the entries by hash, and the players already looked up in it. */
    private static final class Listing {
        static final Listing EMPTY = new Listing("", Map.of());
        final String salt;
        final Map<String, Entry> byHash;
        final Map<UUID, Entry> seen = new ConcurrentHashMap<>();

        Listing(String salt, Map<String, Entry> byHash) {
            this.salt = salt;
            this.byHash = byHash;
        }
    }

    private static volatile Listing listing = Listing.EMPTY;
    /** The supporters who chose to be named, by tier (titan, colossus, waker) - the Hall of Wakers in the Almanac. */
    private static volatile Map<String, java.util.List<String>> credits = Map.of();
    private static volatile long lastFetch = 0;
    private static volatile boolean fetching = false;
    private static volatile boolean everLoaded = false;
    private static int serverTicks = 0;

    /** The supporter entry for a player, or null if they are not one. Cheap after the first look at each player. */
    public static Entry entry(UUID id) {
        if (id == null) return null;
        Listing l = listing;
        Entry e = l.seen.get(id);
        if (e == null) {
            if (l.byHash.isEmpty()) return null;
            e = l.byHash.getOrDefault(hash(l.salt, id), Entry.NONE);
            l.seen.put(id, e);
        }
        return e == Entry.NONE ? null : e;
    }

    /** The client changed its own cosmetics on the service: show them at once, ahead of the next fetch. */
    public static void updateOwn(UUID id, Entry e) {
        if (id == null || e == null) return;
        Listing l = listing;
        if (l == Listing.EMPTY) listing = l = new Listing("", Map.of());
        l.seen.put(id, e);
    }

    /** A reply from the service. */
    public record Response(int status, String body) {
    }

    /** Blocking POST of a JSON body to the service (call from the pool, never from a game thread). */
    public static Response post(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(12)).header("content-type", "application/json").header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return new Response(res.statusCode(), res.body());
    }

    /** Names of the supporters who opted into the credits, for one tier ("titan", "colossus", "waker"); never null. */
    public static java.util.List<String> credits(String tier) {
        return credits.getOrDefault(tier, java.util.List.of());
    }

    /** The tier for a player, or null if they are not a supporter. */
    public static String tier(UUID id) {
        Entry e = entry(id);
        return e == null ? null : e.tier();
    }

    public static boolean isEmpty() {
        Listing l = listing;
        return l.byHash.isEmpty() && l.seen.isEmpty();
    }

    /** What the service publishes for a player: hex SHA-256 of "salt:uuid" (lower-case, dashed uuid). */
    public static String hash(String salt, UUID id) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + id.toString().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Refresh if enough time has passed since the last try (called from the client tick, and from the server tick). */
    public static void maybeRefresh() {
        long wait = everLoaded ? INTERVAL_MS : RETRY_MS;
        if (fetching || System.currentTimeMillis() - lastFetch < wait) return;
        refreshAsync();
    }

    /** Server side: fetch at start, and keep the list fresh while the server runs (a dedicated server has no client tick). */
    public static void onServerStarted(ServerStartedEvent event) {
        refreshAsync();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++serverTicks % 100 == 0) maybeRefresh();
    }

    public static void refreshAsync() {
        if (fetching) return;
        fetching = true;
        lastFetch = System.currentTimeMillis();
        POOL.execute(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/supporters.json"))
                        .timeout(Duration.ofSeconds(10)).header("accept", "application/json").GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    WakingWorld.LOGGER.debug("supporters: HTTP {}", res.statusCode());
                    return;
                }
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                String salt = root.has("salt") ? root.get("salt").getAsString() : "";
                JsonArray arr = root.has("supporters") ? root.getAsJsonArray("supporters") : null;
                Map<String, Entry> next = new HashMap<>();
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject o = arr.get(i).getAsJsonObject();
                        if (!o.has("h") || !o.has("tier")) continue;
                        String h = o.get("h").getAsString().toLowerCase(Locale.ROOT);
                        String tier = o.get("tier").getAsString().toLowerCase(Locale.ROOT);
                        if (h.length() != 64 || SupporterTiers.rank(tier) <= 0) continue;
                        next.put(h, new Entry(tier, str(o, "aura"), str(o, "colossus")));
                    }
                }
                listing = new Listing(salt, Map.copyOf(next));
                everLoaded = true;
                WakingWorld.LOGGER.debug("supporters: loaded {}", next.size());
                fetchCredits();
            } catch (Exception e) {
                WakingWorld.LOGGER.debug("supporters: fetch failed: {}", e.toString());
            } finally {
                fetching = false;
            }
        });
    }

    /** The opt-in credits, fetched right after the list (a failure here just leaves the hall as it was). */
    private static void fetchCredits() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/credits.json"))
                    .timeout(Duration.ofSeconds(10)).header("accept", "application/json").GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonObject c = root.has("credits") && root.get("credits").isJsonObject() ? root.getAsJsonObject("credits") : new JsonObject();
            Map<String, java.util.List<String>> next = new HashMap<>();
            for (String tier : new String[]{"titan", "colossus", "waker"}) {
                java.util.List<String> names = new java.util.ArrayList<>();
                if (c.has(tier) && c.get(tier).isJsonArray()) {
                    JsonArray arr = c.getAsJsonArray(tier);
                    for (int i = 0; i < arr.size() && names.size() < 200; i++) {
                        String n = arr.get(i).getAsString();
                        if (n.matches("[A-Za-z0-9_]{1,16}")) names.add(n); // Minecraft names only - nothing else gets drawn in the book
                    }
                }
                next.put(tier, java.util.List.copyOf(names));
            }
            credits = Map.copyOf(next);
        } catch (Exception e) {
            WakingWorld.LOGGER.debug("supporters: credits fetch failed: {}", e.toString());
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().toLowerCase(Locale.ROOT) : null;
    }
}
