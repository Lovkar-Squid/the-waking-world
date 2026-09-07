package me.lovkar.wakingworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What a blood moon looks like from inside it.
 *
 * <p>Two things, because one of them is not enough. The fog colour is pulled towards a deep red,
 * which carries the horizon, the water and the distance with it - that is the good-looking half, and
 * it is what a player with no shader pack sees.</p>
 *
 * <p>The other half is a red wash over the finished frame, heavier at the edges than in the middle.
 * A shader pack draws its own sky and its own fog and never asks the game what colour they should
 * be, so under Complementary - which is what this mod is shot and played with - the fog tint alone
 * did precisely nothing, and two takes of a blood moon came back with an ordinary blue night in
 * them. The wash is drawn after everything and cannot be overridden by anybody.</p>
 */
public final class RedSky {
    private static boolean on;
    private static float blend;              // 0 = an ordinary night, 1 = fully red

    private RedSky() {
    }

    public static void set(boolean red) {
        on = red;
    }

    /** True while there is any of it on screen - other client code can ask. */
    public static boolean showing() {
        return blend > 0.01F;
    }

    public static void clientTick(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide()) return;
        float want = on && Minecraft.getInstance().level != null ? 1.0F : 0.0F;
        blend = Mth.approach(blend, want, 0.008F);          // ~4 seconds either way
    }

    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (blend <= 0.01F) return;
        float k = blend * 0.75F;
        event.setRed(Mth.lerp(k, event.getRed(), 0.42F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.045F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.055F));
    }

    private static final int RED = 0x8E0E14;

    /** The wash: light over the middle of the frame, heavy round the edges. */
    public static void render(RenderGuiEvent.Post event) {
        if (blend <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui && !me.lovkar.wakingworld.client.Cinematic.active()) {
            // the HUD being off is the player's business; a cinematic still wants the moon
            if (!Cinematic.active()) return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        // a slow heartbeat in it, so the night is doing something rather than sitting behind a filter
        float beat = 1f + 0.16f * (float) Math.sin((mc.level.getGameTime() % 24000L) * 0.035);
        int centre = alpha(0.19F * beat);
        g.fill(0, 0, w, h, centre);
        int band = Math.max(24, Math.min(w, h) / 4);
        for (int i = 0; i < band; i += 2) {
            int a = alphaAt(i / (float) band, beat);
            g.fill(0, i, w, i + 2, a);
            g.fill(0, h - i - 2, w, h - i, a);
            g.fill(i, 0, i + 2, h, a);
            g.fill(w - i - 2, 0, w - i, h, a);
        }
    }

    private static int alpha(float a) {
        return ((int) (Mth.clamp(a * blend, 0f, 1f) * 255) << 24) | RED;
    }

    /** Edge to middle: strongest at the very edge, gone by the end of the band. */
    private static int alphaAt(float t, float beat) {
        float a = (0.46F - 0.19F) * (1f - t) * (1f - t) * beat;
        return ((int) (Mth.clamp(a * blend, 0f, 1f) * 255) << 24) | RED;
    }
}
