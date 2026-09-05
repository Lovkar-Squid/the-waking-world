package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground shakes. A single amplitude (degrees of camera wobble) that impacts add to and that
 * dies away over about a second; every frame the camera's yaw, pitch and roll get a little of
 * it, on three incommensurate frequencies so it reads as rumble rather than a metronome. Impacts
 * arrive as entity events from the server (stomps, slams, landings, a collapse) and as things the
 * client can see for itself (a foot coming down in the walk cycle, a giant rising out of the
 * ground). Waves reach the player a moment after the impact, so a shake can also be scheduled.
 */
public final class ScreenShake {
    private static final float MAX = 7.0F;
    private static float amp, ampO;
    private static float time;
    private static final List<float[]> pending = new ArrayList<>(); // {ticks left, strength}

    private ScreenShake() {
    }

    /** Adds directly, in degrees. */
    public static void add(float strength) {
        if (strength <= 0) return;
        amp = Math.min(MAX, amp + strength);
    }

    /** An impact at a point: full strength at the point, gone at {@code range} blocks (steep near, gentle far). */
    public static void impact(Vec3 at, float strength, double range) {
        Entity cam = Minecraft.getInstance().getCameraEntity();
        if (cam == null || range <= 0) return;
        double d = cam.position().distanceTo(at);
        double f = Mth.clamp(1.0 - d / range, 0.0, 1.0);
        add((float) (strength * f * f));
    }

    /** The same, a number of ticks from now (a wave that has yet to arrive). */
    public static void schedule(int ticks, float strength) {
        if (strength > 0.01F) pending.add(new float[]{ticks, strength});
    }

    /** The point at which a wave leaving {@code from} at {@code speed} blocks a tick reaches the camera. */
    public static void wave(Vec3 from, double speed, double maxRadius, float strength) {
        Entity cam = Minecraft.getInstance().getCameraEntity();
        if (cam == null) return;
        double d = cam.position().distanceTo(from);
        if (d > maxRadius) return;
        double f = Math.max(0.35, 1.0 - d / (maxRadius + 1.0));
        schedule((int) (d / speed), (float) (strength * f));
    }

    public static void clientTick(ClientTickEvent.Post event) {
        ampO = amp;
        amp *= 0.80F;
        if (amp < 0.005F) amp = 0;
        time += 1;
        if (!pending.isEmpty()) {
            for (int i = pending.size() - 1; i >= 0; i--) {
                float[] p = pending.get(i);
                if (--p[0] <= 0) {
                    add(p[1]);
                    pending.remove(i);
                }
            }
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float scale = (float) WakingConfig.cameraShake();
        if (scale <= 0) return;
        float partial = (float) event.getPartialTick();
        float a = Mth.lerp(partial, ampO, amp) * scale;
        if (a < 0.01F) return;
        float t = time + partial;
        float yaw = a * (Mth.sin(t * 2.9F) * 0.55F + Mth.sin(t * 6.1F + 1.3F) * 0.45F);
        float pitch = a * (Mth.sin(t * 3.7F + 0.7F) * 0.6F + Mth.sin(t * 7.3F + 2.1F) * 0.4F);
        float roll = a * 0.35F * Mth.sin(t * 4.3F + 0.4F);
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }
}
