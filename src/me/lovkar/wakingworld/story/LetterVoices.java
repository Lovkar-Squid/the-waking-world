package me.lovkar.wakingworld.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The letters' voices: when a Dead Letter is written and the server has a Gemini key, its text is
 * spoken once by Gemini's text-to-speech in the voice of the hand that wrote it - Aldo the miner
 * gravelly and tired, Wren the pilgrim quiet, Brother Halm slow and solemn, Pip young and quick,
 * Sergeant Osk clipped - and the sound is kept with the world ({@code data/wakingworld/voices/}),
 * 16 kHz 16-bit mono, deflated. The client asks for it by the letter's voice id when the letter is
 * opened ({@code WakingNet.RequestVoice}) and gets it in chunks.
 * <p>
 * Requests go one at a time with a pause between them: the free tier allows a handful a minute and a
 * day, and a 429 is retried once a minute later, then given up.
 */
public final class LetterVoices {
    private LetterVoices() {
    }

    public static final int RATE = 16000;
    /** How the client hears about a voice. */
    public static final int READY = 0, PENDING = 1, NONE = 2;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wakingworld-voices");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<UUID, byte[]> READY_AUDIO = new ConcurrentHashMap<>(); // deflated 16 kHz pcm
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile long lastCall = 0;
    private static final long GAP_MS = 7000;

    /** A hand's voice: Gemini's prebuilt voice and the direction spoken before the text. */
    private record Style(String voice, String direction) {
    }

    private static Style styleFor(String author) {
        String a = author == null ? "" : author.toLowerCase();
        if (a.contains("aldo")) return new Style("Algenib", "Read this letter aloud as an old, weary miner with a rough voice - plain and unhurried, but at a normal reading pace, never dragging - as a man who has worked all his life:");
        if (a.contains("wren")) return new Style("Sulafat", "Read this letter aloud as a quiet, thoughtful woman who has walked a very long road - calm, warm, a little tired, at a normal reading pace:");
        if (a.contains("halm")) return new Style("Charon", "Read this letter aloud as an old monk and scholar - solemn and clear, with weight on the important words, but at a normal reading pace, not slow:");
        if (a.contains("pip")) return new Style("Puck", "Read this letter aloud as an excited child of about ten, quick and bright, as if telling a secret:");
        if (a.contains("osk")) return new Style("Orus", "Read this letter aloud as a gruff sergeant dictating a report - clipped, firm, brisk, no nonsense:");
        return new Style("Charon", "Read this old letter aloud, clearly, at a normal reading pace:");
    }

    public static boolean enabled() {
        String key = WakingConfig.geminiApiKey();
        return WakingConfig.voicedLetters() && key != null && key.length() > 10;
    }

    /** A line the voice does not read: the surveyor's coordinates in the margin. */
    public static final String MARGIN = "(?i)^x\\s+-?\\d+\\s+z\\s+-?\\d+.*";

    /** Faded ink - the surveyor's notes in the margin, in grey: not the writer's words, so not read. */
    public static boolean faded(net.minecraft.network.chat.Style style) {
        net.minecraft.network.chat.TextColor c = style.getColor();
        if (c == null) return false;
        Integer dark = ChatFormatting.DARK_GRAY.getColor(), grey = ChatFormatting.GRAY.getColor();
        return (dark != null && c.getValue() == dark) || (grey != null && c.getValue() == grey);
    }

    /** A page's words without the faded margin notes. */
    public static String spoken(Component page) {
        StringBuilder sb = new StringBuilder();
        page.visit((style, str) -> {
            if (!faded(style)) sb.append(str);
            return java.util.Optional.<Object>empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return sb.toString();
    }

    /** The words the voice speaks: the title, the letter, the hint - not the faded notes or the surveyor's coordinates in the margin. */
    static String speech(WrittenBookContent book) {
        StringBuilder sb = new StringBuilder();
        sb.append(book.title().raw()).append(".\n\n");
        for (Filterable<Component> page : book.pages()) {
            String text = spoken(page.raw());
            for (String line : text.split("\n")) {
                String l = line.trim();
                if (l.isEmpty()) continue;
                if (l.matches(MARGIN)) continue; // the marks in the margin
                sb.append(l).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /** Starts making the voice for a freshly written letter; the id goes into the item, the sound comes later. */
    public static void request(UUID id, WrittenBookContent book, MinecraftServer server) {
        if (!enabled() || id == null || book == null) return;
        if (READY_AUDIO.containsKey(id) || IN_FLIGHT.contains(id)) return;
        IN_FLIGHT.add(id);
        Style style = styleFor(book.author());
        String text = speech(book);
        Path file = fileFor(server, id);
        POOL.execute(() -> {
            try {
                byte[] pcm24 = null;
                for (int attempt = 0; attempt < 2 && pcm24 == null; attempt++) {
                    long wait = lastCall + GAP_MS - System.currentTimeMillis();
                    if (wait > 0) Thread.sleep(wait);
                    lastCall = System.currentTimeMillis();
                    HttpResponse<String> res = send(style, text);
                    if (res.statusCode() == 429 && attempt == 0) {
                        WakingWorld.LOGGER.info("letter voice: rate limited, one more try in a minute");
                        Thread.sleep(65_000);
                        continue;
                    }
                    if (res.statusCode() / 100 != 2) {
                        String b = res.body();
                        WakingWorld.LOGGER.warn("letter voice: HTTP {} {}", res.statusCode(), b.length() > 240 ? b.substring(0, 240) : b);
                        break;
                    }
                    pcm24 = audioOf(res.body());
                }
                if (pcm24 == null) {
                    FAILED.add(id);
                    return;
                }
                byte[] packed = deflate(resample24to16(pcm24));
                READY_AUDIO.put(id, packed);
                try {
                    Files.createDirectories(file.getParent());
                    Files.write(file, packed);
                } catch (IOException e) {
                    WakingWorld.LOGGER.warn("letter voice: could not save {}: {}", file, e.toString());
                }
                WakingWorld.LOGGER.info("letter voice ready: {} ({} s, {} KB)", id, pcm24.length / 48000, packed.length / 1024);
            } catch (Exception e) {
                WakingWorld.LOGGER.warn("letter voice failed: {}", e.toString());
                FAILED.add(id);
            } finally {
                IN_FLIGHT.remove(id);
            }
        });
    }

    /** READY, PENDING or NONE for a voice id; loads a saved voice from the world folder when asked. */
    public static int status(UUID id, MinecraftServer server) {
        if (id == null) return NONE;
        if (READY_AUDIO.containsKey(id)) return READY;
        if (IN_FLIGHT.contains(id)) return PENDING;
        if (FAILED.contains(id)) return NONE;
        Path file = fileFor(server, id);
        if (Files.isRegularFile(file)) {
            try {
                READY_AUDIO.put(id, Files.readAllBytes(file));
                return READY;
            } catch (IOException e) {
                WakingWorld.LOGGER.warn("letter voice: could not read {}: {}", file, e.toString());
            }
        }
        return NONE;
    }

    /** The deflated 16 kHz 16-bit mono sound, or null. */
    public static byte[] packed(UUID id) {
        return READY_AUDIO.get(id);
    }

    private static Path fileFor(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("wakingworld").resolve("voices").resolve(id + ".v16");
    }

    // ---- the call ----

    private static HttpResponse<String> send(Style style, String text) throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + WakingConfig.voiceModel() + ":generateContent";
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", style.direction + "\n\n" + text);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        body.add("contents", contents);
        JsonObject gen = new JsonObject();
        JsonArray modalities = new JsonArray();
        modalities.add("AUDIO");
        gen.add("responseModalities", modalities);
        JsonObject speech = new JsonObject();
        JsonObject voiceConfig = new JsonObject();
        JsonObject prebuilt = new JsonObject();
        prebuilt.addProperty("voiceName", style.voice);
        voiceConfig.add("prebuiltVoiceConfig", prebuilt);
        speech.add("voiceConfig", voiceConfig);
        gen.add("speechConfig", speech);
        body.add("generationConfig", gen);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", WakingConfig.geminiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** The PCM (24 kHz, 16-bit, mono) out of the response, or null. */
    private static byte[] audioOf(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) return null;
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null) return null;
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < parts.size(); i++) {
            JsonObject p = parts.get(i).getAsJsonObject();
            JsonObject inline = p.getAsJsonObject("inlineData");
            if (inline == null || !inline.has("data")) continue;
            String mime = inline.has("mimeType") ? inline.get("mimeType").getAsString() : "";
            if (!mime.isEmpty() && !mime.toLowerCase().contains("l16") && !mime.toLowerCase().contains("pcm")) {
                WakingWorld.LOGGER.warn("letter voice: unexpected audio {}", mime);
                return null;
            }
            byte[] b = Base64.getDecoder().decode(inline.get("data").getAsString());
            out.write(b, 0, b.length);
        }
        return out.size() > 4800 ? out.toByteArray() : null;
    }

    // ---- the sound's shape ----

    /** 24 kHz to 16 kHz, 16-bit little-endian mono: every two output samples span three input ones. */
    static byte[] resample24to16(byte[] pcm24) {
        int n = pcm24.length / 2;
        int m = (int) ((long) n * 2 / 3);
        ByteBuffer in = ByteBuffer.wrap(pcm24).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(m * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < m; i++) {
            double pos = i * 1.5;
            int a = (int) pos;
            double frac = pos - a;
            int s0 = in.getShort(a * 2);
            int s1 = a + 1 < n ? in.getShort((a + 1) * 2) : s0;
            out.putShort((short) Math.round(s0 + (s1 - s0) * frac));
        }
        return out.array();
    }

    static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(6);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2);
        byte[] buf = new byte[65536];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    public static byte[] inflate(byte[] data) throws DataFormatException {
        Inflater inf = new Inflater();
        inf.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2);
        byte[] buf = new byte[65536];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            out.write(buf, 0, n);
        }
        inf.end();
        return out.toByteArray();
    }
}
