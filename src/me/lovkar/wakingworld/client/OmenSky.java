package me.lovkar.wakingworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The light going wrong before a cataclysm.
 *
 * <p>Drawn over the finished frame rather than fed to the sky, for the same reason the blood moon
 * is: a shader pack draws its own sky and never asks. It is deliberately faint - a wash at the edges
 * of vision that a player notices without being able to say when it started - and it swells as the
 * warning runs out.</p>
 */
public final class OmenSky {
    private static int tint;
    private static int left;
    private static int total;

    private OmenSky() {
    }

    public static void begin(int colour, int ticks) {
        tint = colour;
        total = Math.max(1, ticks);
        left = total;
    }

    public static void clear() {
        left = 0;
    }

    public static void clientTick(ClientTickEvent.Post event) {
        if (left > 0 && !Minecraft.getInstance().isPaused()) left--;
    }

    public static void render(RenderGuiEvent.Post event) {
        if (left <= 0) return;
        float gone = 1f - left / (float) total;
        // in over the first fifth, and heaviest at the very end
        float a = Math.min(1f, gone * 5f) * (0.28f + 0.55f * gone);
        if (a <= 0.01f) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int band = Math.max(28, Math.min(w, h) / 4);
        for (int i = 0; i < band; i += 2) {
            int c = argb(a * 0.42f * (1f - i / (float) band));
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
        g.fill(0, 0, w, h, argb(a * 0.10f));
    }

    private static int argb(float a) {
        return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (tint & 0xFFFFFF);
    }
}
