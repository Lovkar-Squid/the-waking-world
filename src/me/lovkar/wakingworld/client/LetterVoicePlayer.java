package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.network.WakingNet;
import me.lovkar.wakingworld.story.LetterVoices;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plays a letter's voice: asks the server for it ({@link WakingNet#requestVoice}), puts the pieces
 * together, inflates them and plays the 16 kHz mono PCM straight through OpenAL on the game's own
 * device - one source, no game-side sound event needed. The Voice/Speech slider is the volume.
 * One voice plays at a time; the letter's speaker button starts and stops it.
 */
public final class LetterVoicePlayer {
    private LetterVoicePlayer() {
    }

    /** What we know about a voice on this client. */
    public enum State { UNKNOWN, LOADING, READY, PENDING, NONE }

    private static final Map<UUID, byte[]> VOICES = new HashMap<>(); // inflated pcm, 16 kHz s16le mono
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, byte[][]> PARTS = new HashMap<>();
    private static final Map<UUID, Long> ASKED = new HashMap<>();
    private static UUID wanted; // the voice to play as soon as it is here
    private static UUID playing;
    private static boolean paused;
    private static int source = -1, buffer = -1;
    private static int checkTicks;

    public static State state(UUID id) {
        return id == null ? State.NONE : STATES.getOrDefault(id, State.UNKNOWN);
    }

    /** True while this voice is loaded in the source - playing or paused. */
    public static boolean isPlaying(UUID id) {
        return id != null && id.equals(playing) && source >= 0;
    }

    public static boolean isPaused(UUID id) {
        return isPlaying(id) && paused;
    }

    /** The voice's sound (16 kHz 16-bit mono) once it is here, else null. */
    public static byte[] pcm(UUID id) {
        return id == null ? null : VOICES.get(id);
    }

    /** Seconds into the voice while it plays or is held, else -1. */
    public static double position(UUID id) {
        if (!isPlaying(id)) return -1;
        try {
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) return -1;
            return AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Holds the voice where it is; {@link #resume} goes on from there. */
    public static void pause() {
        if (source < 0 || paused) return;
        try {
            AL10.alSourcePause(source);
            paused = true;
        } catch (Throwable ignored) {
        }
    }

    public static void resume() {
        if (source < 0 || !paused) return;
        try {
            AL10.alSourcePlay(source);
            paused = false;
        } catch (Throwable ignored) {
        }
    }

    /** Plays the voice now if it is here, else asks for it and plays it when it comes. */
    public static void play(UUID id) {
        if (id == null) return;
        stop();
        wanted = id;
        byte[] pcm = VOICES.get(id);
        if (pcm != null) {
            start(id, pcm);
            return;
        }
        State s = STATES.getOrDefault(id, State.UNKNOWN);
        if (s == State.UNKNOWN || s == State.PENDING) {
            STATES.put(id, State.LOADING);
            PARTS.remove(id);
            WakingNet.requestVoice(id);
        }
    }

    /**
     * Asks about a voice without playing it (for the button's and the tooltip's look); a voice still
     * being made is asked about again every five seconds, so the answer follows the server.
     */
    public static void ask(UUID id) {
        if (id == null || VOICES.containsKey(id)) return;
        State s = STATES.get(id);
        long now = System.currentTimeMillis();
        if (s == null) {
            STATES.put(id, State.LOADING);
        } else if (s == State.PENDING && now - ASKED.getOrDefault(id, 0L) > 5000) {
            // ask again
        } else {
            return;
        }
        ASKED.put(id, now);
        WakingNet.requestVoice(id);
    }

    public static void stop() {
        wanted = null;
        release();
    }

    /** A piece from the server (see WakingNet.VoiceData). */
    public static void receive(UUID id, int status, int index, int total, byte[] data) {
        if (status != LetterVoices.READY) {
            STATES.put(id, status == LetterVoices.PENDING ? State.PENDING : State.NONE);
            PARTS.remove(id);
            return;
        }
        if (total <= 0 || index < 0 || index >= total) return;
        byte[][] parts = PARTS.computeIfAbsent(id, k -> new byte[total][]);
        if (parts.length != total) {
            parts = new byte[total][];
            PARTS.put(id, parts);
        }
        parts[index] = data;
        for (byte[] p : parts) if (p == null) return; // more to come
        PARTS.remove(id);
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] packed = new byte[len];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, packed, at, p.length);
            at += p.length;
        }
        try {
            byte[] pcm = LetterVoices.inflate(packed);
            VOICES.put(id, pcm);
            STATES.put(id, State.READY);
            if (id.equals(wanted)) start(id, pcm);
        } catch (Exception e) {
            WakingWorld.LOGGER.warn("letter voice: could not unpack {}: {}", id, e.toString());
            STATES.put(id, State.NONE);
        }
    }

    private static void start(UUID id, byte[] pcm) {
        release();
        Minecraft mc = Minecraft.getInstance();
        try {
            AL10.alGetError();
            buffer = AL10.alGenBuffers();
            ByteBuffer data = MemoryUtil.memAlloc(pcm.length);
            try {
                data.put(pcm).flip();
                AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, LetterVoices.RATE);
            } finally {
                MemoryUtil.memFree(data);
            }
            source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
            AL10.alSourcef(source, AL10.AL_GAIN, gain(mc));
            AL10.alSourcePlay(source);
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                WakingWorld.LOGGER.warn("letter voice: OpenAL error {}", err);
                release();
                return;
            }
            playing = id;
            paused = false;
        } catch (Throwable t) {
            WakingWorld.LOGGER.warn("letter voice: could not play: {}", t.toString());
            release();
        }
    }

    private static float gain(Minecraft mc) {
        return mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.VOICE);
    }

    private static void release() {
        try {
            if (source >= 0) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
            }
            if (buffer >= 0) AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {
        }
        source = -1;
        buffer = -1;
        playing = null;
        paused = false;
    }

    /** Frees the source when the voice has finished; follows the volume sliders while it plays. */
    public static void clientTick(ClientTickEvent.Post event) {
        if (source < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop();
            return;
        }
        if (++checkTicks % 5 != 0) return;
        try {
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                release();
                return;
            }
            AL10.alSourcef(source, AL10.AL_GAIN, gain(mc));
        } catch (Throwable t) {
            release();
        }
    }
}
