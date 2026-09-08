package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * The music director. Every client tick it looks for the nearest colossus and decides what should
 * be playing: the awakening piece while one rises (its last hit lands as the giant clears the
 * ground), that kind's battle theme while it lives and the player is within a few body lengths,
 * the victory piece when it falls. Themes loop seamlessly (streamed, so nothing big sits in
 * memory) and cross-fade into each other; vanilla's own music is told to stay quiet the whole
 * time and for a while after. Everything plays on the MUSIC channel, so the game's music slider
 * governs it; the client config can switch it off.
 */
public final class BossMusic {
    private enum State { NONE, WAKING, BATTLE, VICTORY, AFTER }

    private static State state = State.NONE;
    private static ColossusEntity boss;
    private static Track theme;    // the looping battle theme
    private static Track sting;    // awakening or victory, plays once
    private static String themeKind = "";
    private static int afterTicks;
    private static int themeDelay;
    private static int themeRetry;
    private static Object lastLevel;
    /** The last thing said about the music, so the log carries a change and not a stream. */
    private static String said = "";

    private BossMusic() {
    }

    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            if (mc.level == null) reset();
            return;
        }
        if (!WakingConfig.bossMusic()) {
            if (state != State.NONE) reset();
            return;
        }
        SoundManager sounds = mc.getSoundManager();
        if (theme != null && !sounds.isActive(theme) && !theme.starting()) theme = null;
        if (sting != null && !sounds.isActive(sting) && !sting.starting()) sting = null;

        Player player = mc.player;
        // a new level (respawn after dying, another dimension, another world): the old fight is
        // gone with it - forget it without any fanfare; a boss that is still there is found again
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (theme != null) { theme.fadeOutAndStop(20); theme = null; }
            if (sting != null) { sting.fadeOutAndStop(20); sting = null; }
            boss = null;
            state = State.NONE;
            themeKind = "";
        }
        // the player is the one dying: the music goes quiet, nothing is celebrated
        if (player.isDeadOrDying()) {
            if (state == State.BATTLE || state == State.WAKING) fadeOut();
            return;
        }
        ColossusEntity nearest = null;
        double nearestD = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ColossusEntity c && !c.isRemoved()) {
                double d = c.distanceTo(player);
                if (d < nearestD) { nearestD = d; nearest = c; }
            }
        }
        // the old boss died? (its health hits zero and it lies there dying for half a minute; a boss
        // that simply vanished - unloaded, killed by a command - just stops the music)
        if (boss != null && (state == State.BATTLE || state == State.WAKING)) {
            if (boss.isDeadOrDying() && !boss.isRemoved()) {
                victory();
                return;
            }
            if (boss.isRemoved()) {
                fadeOut();
                return;
            }
        }
        double range = nearest == null ? 0 : Math.max(120.0, nearest.bodyHeight() * 4.0);
        boolean near = nearest != null && nearest.isAlive() && nearestD < range * (state == State.BATTLE || state == State.WAKING ? 1.2 : 1.0);

        switch (state) {
            case NONE, AFTER -> {
                if (state == State.AFTER && --afterTicks <= 0) state = State.NONE;
                if (near) {
                    boss = nearest;
                    say("a colossus is near (" + (int) nearestD + " blocks, reach " + (int) range
                            + ", " + (nearest.isWaking() ? "rising" : "up") + ", kind " + nearest.palette().kind + ")");
                    if (nearest.isWaking()) {
                        state = State.WAKING;
                        stopSting();
                        sting = start(available(nearest.isTitan() ? WakingSounds.MUSIC_TITAN_AWAKENING.get() : null, WakingSounds.MUSIC_AWAKENING.get()), false, 1f);
                        themeDelay = -1;
                    } else {
                        state = State.BATTLE;
                        playTheme(nearest, 40);
                    }
                }
            }
            case WAKING -> {
                if (!near) { fadeOut(); return; }
                boss = nearest;
                if (!nearest.isWaking()) {
                    // out of the ground: let the last hit ring, then the theme comes in under the dust
                    state = State.BATTLE;
                    themeDelay = 30;
                }
            }
            case BATTLE -> {
                if (!near) { fadeOut(); return; }
                boss = nearest;
                if (themeDelay > 0) {
                    if (--themeDelay == 0) playTheme(nearest, 60);
                } else if (theme == null) {
                    if (--themeRetry <= 0) { playTheme(nearest, 50); themeRetry = 100; } // (a stream that failed to start is retried, not spammed)
                } else if (!themeKind.equals(nearest.palette().kind)) {
                    playTheme(nearest, 50);
                }
            }
            case VICTORY -> {
                if (sting == null) {
                    state = State.AFTER;
                    afterTicks = 20 * 45; // a while of nothing before vanilla's music is allowed back
                }
            }
        }
    }

    /** Keeps vanilla's music out of the way while ours plays, and for a while after. */
    public static void onSelectMusic(SelectMusicEvent event) {
        if (state != State.NONE) event.setMusic(null);
    }

    /**
     * Say what the director just decided, once per decision.
     *
     * <p>Music that does not play is the hardest kind of bug to report: there is nothing on screen,
     * nothing in the log, and no way for the person hearing the silence to tell whether the giant
     * was too far away, the track was refused a channel, or the whole thing was switched off. One
     * line per state change costs nothing and answers all three.</p>
     */
    private static void say(String what) {
        if (what.equals(said)) return;
        said = what;
        me.lovkar.wakingworld.WakingWorld.LOGGER.info("music: {}", what);
    }

    private static void playTheme(ColossusEntity boss, int fadeTicks) {
        String kind = boss.palette().kind;
        if (theme != null) {
            if (themeKind.equals(kind)) return;
            theme.fadeOutAndStop(fadeTicks);
        }
        SoundEvent event = available(WakingSounds.battleTheme(kind), WakingSounds.MUSIC_STONE.get());
        theme = start(event, true, 0f);
        theme.fadeTo(1f, fadeTicks);
        themeKind = kind;
        say("battle theme " + event.getLocation());
    }

    private static void victory() {
        state = State.VICTORY;
        say("victory");
        boolean titan = boss != null && boss.isTitan();
        boss = null;
        if (theme != null) { theme.fadeOutAndStop(25); theme = null; }
        stopSting();
        sting = start(available(titan ? WakingSounds.MUSIC_TITAN_VICTORY.get() : null, WakingSounds.MUSIC_VICTORY.get()), false, 1f);
    }

    /** The wanted piece if this client's resources define it (sounds.json), else the fallback. */
    private static SoundEvent available(SoundEvent wanted, SoundEvent fallback) {
        if (wanted == null) return fallback;
        return Minecraft.getInstance().getSoundManager().getSoundEvent(wanted.getLocation()) != null ? wanted : fallback;
    }

    private static void fadeOut() {
        if (theme != null) { theme.fadeOutAndStop(60); theme = null; }
        if (sting != null) { sting.fadeOutAndStop(40); sting = null; }
        boss = null;
        state = State.AFTER;
        afterTicks = 20 * 20;
        themeRetry = 0;      // the next fight retries at once, not after the old countdown
        say("quiet: nothing near");
    }

    private static void stopSting() {
        if (sting != null) { sting.fadeOutAndStop(10); sting = null; }
    }

    private static void reset() {
        // Cut, do not fade. A fade is driven by the track's own tick(), and a track the engine
        // never started is never ticked - so a fade-out on one of those would leave it holding a
        // streaming channel for ever, and the streaming pool is small enough that a few of them
        // would leave the next fight with nowhere to play.
        if (theme != null) { theme.cut(); theme = null; }
        if (sting != null) { sting.cut(); sting = null; }
        boss = null;
        state = State.NONE;
        themeKind = "";
        themeRetry = 0;
    }

    private static Track start(SoundEvent event, boolean loop, float volume) {
        Track t = new Track(event, loop, volume);
        Minecraft.getInstance().getSoundManager().play(t);
        return t;
    }

    /** A music track: non-positional, on the music channel, with fades handled per tick. */
    static final class Track extends AbstractTickableSoundInstance {
        private float target;
        private float step;
        private boolean stopping;
        /**
         * When this track was handed to the sound engine. Wall clock on purpose: {@link #tick()}
         * only runs for sounds the engine actually started, so a tick counter stands still in
         * exactly the case it is meant to detect.
         */
        private final long born = System.currentTimeMillis();

        Track(SoundEvent event, boolean loop, float volume) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = loop;
            this.delay = 0;
            this.volume = volume;
            this.target = volume;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0; this.y = 0; this.z = 0;
        }

        /**
         * The sound engine reports a just-started stream as inactive for a moment, so a track is
         * given a second before it is believed to be gone.
         *
         * <p>This has to be measured against the clock rather than against ticks of this instance.
         * A sound the engine declined to start - the engine not loaded yet, no free streaming
         * channel - is never added to the ticking set, so its own tick counter never moves; asking
         * it how old it is would get "just born" forever, the director would never notice the
         * silence, and the fight would play out with no music and no retry.</p>
         */
        boolean starting() {
            return System.currentTimeMillis() - born < 1000L;
        }

        /** Themes fade in from nothing; the engine would otherwise skip a sound that starts at zero volume. */
        @Override
        public boolean canStartSilent() {
            return true;
        }

        void fadeTo(float v, int ticks) {
            target = v;
            step = Math.abs(v - volume) / Math.max(1, ticks);
        }

        void fadeOutAndStop(int ticks) {
            fadeTo(0f, ticks);
            stopping = true;
        }

        /** Stop now, and take the channel back with it. */
        void cut() {
            this.stop();
            Minecraft.getInstance().getSoundManager().stop(this);
        }

        @Override
        public void tick() {
            if (volume < target) volume = Math.min(target, volume + step);
            else if (volume > target) volume = Math.max(target, volume - step);
            volume = Mth.clamp(volume, 0f, 1f);
            if (stopping && volume <= 0.001f) this.stop();
        }
    }
}
