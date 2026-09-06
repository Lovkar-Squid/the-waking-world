package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.supporter.AuraStyle;
import me.lovkar.wakingworld.supporter.SupporterList;
import me.lovkar.wakingworld.particle.WakingParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Random;

/**
 * The supporters' auras - drawn in the mod's own sigil language: rune glyphs, the flat pulse ring
 * of a colossus' stomp, and embers. Purely cosmetic and client-side: every client that has the
 * mod draws them for anyone the supporter list knows, so they show in single-player and on
 * servers alike (for viewers who have the mod). Which aura a supporter wears comes from the
 * supporter service, which checked it against their tier; see {@link AuraStyle}. This class also
 * drives the periodic refresh of the list.
 */
public final class SupporterAura {
    private SupporterAura() {
    }

    private static final double TAU = Math.PI * 2;
    private static final double MAX_DIST_SQ = 48 * 48;
    private static final Random RANDOM = new Random();
    private static int ticks = 0;

    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        SupporterList.maybeRefresh();
        if (SupporterList.isEmpty() || !WakingConfig.showAuras()) return;
        ticks++;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Player p : level.players()) {
            if (p == null || p.isInvisible() || p.isSpectator() || !p.isAlive()) continue;
            SupporterList.Entry e = SupporterList.entry(p.getUUID());
            if (e == null) continue;
            AuraStyle style = AuraStyle.resolve(e.tier(), e.aura());
            if (style == null || style.pattern == AuraStyle.Pattern.NONE) continue;
            if (p.distanceToSqr(cam) > MAX_DIST_SQ) continue;
            boolean ownEyes = p == mc.player && mc.options.getCameraType().isFirstPerson();
            int phase = (p.getId() & 0x7fff) * 37;   // so two supporters side by side are not in step
            switch (style.pattern) {
                case RUNES -> runes(level, p, style, phase);
                case SIGIL -> sigil(level, p, style, phase, 0.62, 0.55f, 50, false);
                case VOID -> voidSigil(level, p, style, phase);
                case CROWN -> crown(level, p, style, phase, ownEyes);
                default -> {
                }
            }
        }
    }

    /** Waker: a slow drift of small glyphs rising around the feet. */
    private static void runes(ClientLevel level, Player p, AuraStyle style, int phase) {
        if ((ticks + phase) % 6 != 0) return;
        double a = RANDOM.nextDouble() * TAU;
        double r = 0.42 + RANDOM.nextDouble() * 0.22;
        level.addParticle(WakingParticles.rune(style.rgb, 0.5f),
                p.getX() + Math.cos(a) * r, p.getY() + 0.08 + RANDOM.nextDouble() * 0.45, p.getZ() + Math.sin(a) * r,
                0.0, 0.016, 0.0);
    }

    /** Colossus and the lands: glyphs orbiting the feet, and every few seconds a ring of light racing out along the ground. */
    private static void sigil(ClientLevel level, Player p, AuraStyle style, int phase, double radius, float glyph, int pulseEvery, boolean twin) {
        int t = ticks + phase;
        if (t % 3 == 0) {
            double a = t * 0.105 + ((t / 3) % 2 == 0 ? 0 : Math.PI);
            level.addParticle(WakingParticles.rune(style.rgb, glyph),
                    p.getX() + Math.cos(a) * radius, p.getY() + 0.12, p.getZ() + Math.sin(a) * radius, 0.0, 0.011, 0.0);
        }
        if (t % pulseEvery == 0) pulse(level, p, style.rgb, 0.36f, 0.05);
        if (twin && t % pulseEvery == 6) pulse(level, p, style.rgb, 0.5f, 0.06);
    }

    /** A flat expanding ring at the feet (radius = 3 * scale blocks, spreading at `speed` blocks per tick). */
    private static void pulse(ClientLevel level, Player p, int rgb, float scale, double speed) {
        ParticleOptions ring = WakingParticles.ring(rgb, scale);
        level.addParticle(ring, p.getX(), p.getY() + 0.03, p.getZ(), 0.0, speed, 0.0);
    }

    /** Titan: the sigil in void purple, a twin pulse, and embers boiling up from the ground. */
    private static void voidSigil(ClientLevel level, Player p, AuraStyle style, int phase) {
        sigil(level, p, style, phase, 0.66, 0.6f, 40, true);
        if ((ticks + phase) % 2 != 0) return;
        double a = RANDOM.nextDouble() * TAU;
        double r = 0.25 + RANDOM.nextDouble() * 0.4;
        int rgb = RANDOM.nextBoolean() ? style.rgb : 0x6A2BB8;
        level.addParticle(WakingParticles.ember(rgb, 0.7f),
                p.getX() + Math.cos(a) * r, p.getY() + 0.05, p.getZ() + Math.sin(a) * r,
                Math.cos(a) * 0.012, 0.035 + RANDOM.nextDouble() * 0.04, Math.sin(a) * 0.012);
    }

    /** Titan's other aura: a halo of gold glyphs turning above the head, shedding the odd ember; a faint ring at the feet. */
    private static void crown(ClientLevel level, Player p, AuraStyle style, int phase, boolean ownEyes) {
        int t = ticks + phase;
        if (!ownEyes) {   // in first person the halo would sit in your own eyes; the rest still shows to everyone else
            double hy = p.getEyeY() + 0.42;
            if (t % 2 == 0) {
                double a = t * 0.13;
                level.addParticle(WakingParticles.rune(style.rgb, 0.4f),
                        p.getX() + Math.cos(a) * 0.32, hy, p.getZ() + Math.sin(a) * 0.32, 0.0, 0.003, 0.0);
            }
            if (t % 5 == 0) {
                double a = RANDOM.nextDouble() * TAU;
                level.addParticle(WakingParticles.ember(style.rgb, 0.45f),
                        p.getX() + Math.cos(a) * 0.3, hy - 0.05, p.getZ() + Math.sin(a) * 0.3, 0.0, -0.01, 0.0);
            }
        }
        if (t % 60 == 0) pulse(level, p, style.rgb, 0.3f, 0.04);
    }
}
