package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.ritual.Rites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.UUID;

/**
 * The colossus's own boss bar in place of the vanilla strip: a carved stone frame, the health a
 * strip of glowing runes in the kind's colour with a bright leading edge, the phase in a cartouche
 * at the right, and one socket per core set into the base - lit while the core beats, dark and
 * cracked once it is broken. The Titan has a bar of its own ({@link #titan}). Drawn from
 * {@code textures/gui/colossus_bar.png} (tools/textures/gui.py bossbar()); the giant is found by the
 * boss bar's id, which it syncs.
 */
public final class ColossusBossBar {
    private ColossusBossBar() {
    }

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/colossus_bar.png");
    private static final int TEX_W = 256, TEX_H = 256;
    private static final int FRAME_W = 212, FRAME_H = 26, FILL_X = 6, FILL_Y = 7, FILL_W = 200, FILL_H = 12;
    private static final int SOCKET = 11, SOCKET_STEP = 13, GLOW = 15;
    // the Titan's bar
    private static final int T_FRAME_W = 232, T_FRAME_H = 34, T_FILL_X = 6, T_FILL_Y = 10, T_FILL_W = 220, T_FILL_H = 14, T_STRIP = 128;
    private static final int T_SOCKET = 13, T_SOCKET_STEP = 16, T_CROWN_W = 44, T_CROWN_H = 16;

    /** The last giant found for a bar id, so the entities are not searched every frame. */
    private static UUID cachedId;
    private static int cachedEntity = -1;

    private static ColossusEntity giantFor(UUID barId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (barId.equals(cachedId)) {
            Entity e = mc.level.getEntity(cachedEntity);
            if (e instanceof ColossusEntity c && c.bossId().map(barId::equals).orElse(false)) return c;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ColossusEntity c && c.bossId().map(barId::equals).orElse(false)) {
                cachedId = barId;
                cachedEntity = c.getId();
                return c;
            }
        }
        return null;
    }

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        ColossusEntity giant = giantFor(event.getBossEvent().getId());
        if (giant == null) return;
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int screenW = event.getWindow().getGuiScaledWidth();
        int color = Rites.color(giant.palette().kind);
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        float t = time + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.12f);
        int top = event.getY() + Cinematic.letterbox(); // under the director's letterbox when a scene plays
        if (giant.isTitan()) {
            event.setIncrement(T_FRAME_H + 14 + T_SOCKET + 6);
            titan(g, mc, giant, event.getBossEvent().getProgress(), screenW, top, color, t, pulse);
            return;
        }
        event.setIncrement(FRAME_H + 9 + SOCKET + 4);
        int x = (screenW - FRAME_W) / 2, y = top;
        float r = ((color >> 16) & 0xFF) / 255f, gr = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        // the name, above, in the kind's colour
        Component name = giant.getDisplayName();
        int nameW = mc.font.width(name);
        g.drawString(mc.font, name, (screenW - nameW) / 2, y - 10, lighten(color, 0.35f), true);

        // the frame, the empty track, the runes as far as the health goes, the bright edge
        g.blit(TEX, x, y, 0, 0, FRAME_W, FRAME_H, TEX_W, TEX_H);
        g.blit(TEX, x + FILL_X, y + FILL_Y, 0, 48, FILL_W, FILL_H, TEX_W, TEX_H);
        float progress = event.getBossEvent().getProgress();
        int w = Mth.clamp((int) (FILL_W * progress), 0, FILL_W);
        if (w > 0) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(r, gr, b, 1f);
            g.blit(TEX, x + FILL_X, y + FILL_Y, 0, 32, w, FILL_H, TEX_W, TEX_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 0.6f + 0.4f * pulse);
            if (w < FILL_W) g.blit(TEX, x + FILL_X + w - 1, y + FILL_Y, 24, 64, Math.min(4, FILL_W - w + 1), FILL_H, TEX_W, TEX_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        // the phase, cut into a cartouche at the right end of the window
        String phase = switch (giant.phase()) { case 2 -> "II"; case 3 -> "III"; default -> "I"; };
        int cx = x + FILL_X + FILL_W - 24, cy = y + FILL_Y;
        g.blit(TEX, cx, cy, 48, 80, 22, 12, TEX_W, TEX_H);
        g.drawString(mc.font, phase, cx + 11 - mc.font.width(phase) / 2, cy + 2, lighten(color, 0.5f), false);
        // the cores, set into the base: lit while they beat
        int cores;
        try {
            cores = giant.body().cores.size();
        } catch (RuntimeException ex) {
            cores = 0;
        }
        if (cores > 0) {
            int total = cores * SOCKET_STEP - (SOCKET_STEP - SOCKET);
            int sx = (screenW - total) / 2, sy = y + FRAME_H - 3;
            for (int i = 0; i < cores; i++) {
                boolean broken = giant.isCoreBroken(i);
                if (!broken) {
                    int ox = sx + i * SOCKET_STEP;
                    // a glow in the kind's colour behind the socket, the ring with its empty seat, the gem lit in the colour, a spark that pulses
                    glow(g, ox + SOCKET / 2, sy + SOCKET / 2, color, 0.75f + 0.25f * pulse, 1);
                    g.blit(TEX, ox, sy, 36, 64, SOCKET, SOCKET, TEX_W, TEX_H);
                    tinted(g, lighten(color, 0.25f), 1f, ox + 3, sy + 3, 3, 67, 5, 5);
                    int spark = 0x70 + (int) (0x8F * pulse);
                    g.fill(ox + 4, sy + 4, ox + 6, sy + 6, (spark << 24) | (lighten(color, 0.75f) & 0xFFFFFF));
                } else {
                    g.blit(TEX, sx + i * SOCKET_STEP, sy, 12, 64, SOCKET, SOCKET, TEX_W, TEX_H);
                }
            }
        }
    }

    /** A blit in a colour: the texture's greys become shades of it. */
    private static void tinted(GuiGraphics g, int rgb, float alpha, int x, int y, int u, int v, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        g.blit(TEX, x, y, u, v, w, h, TEX_W, TEX_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** The soft radial glow, centred, in a colour; {@code size} 1 = 15 px, 2 = 30 px. */
    private static void glow(GuiGraphics g, int cx, int cy, int rgb, float alpha, int size) {
        int d = GLOW * size;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        g.blit(TEX, cx - d / 2, cy - d / 2, d, d, 72f, 64f, GLOW, GLOW, TEX_W, TEX_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * The Titan's bar: a slab of the End's obsidian eaten by the void at both ends, veined with light,
     * a dragon's egg on a purpur bracket crowning it, the health a scrolling river of the void in
     * violet with a burning edge, the phase on a purpur plate, six sockets of black glass under it each
     * lit violet while its core beats - and the name over it all in large letters with a violet glow.
     */
    private static void titan(GuiGraphics g, Minecraft mc, ColossusEntity giant, float progress, int screenW, int y, int color, float t, float pulse) {
        int x = (screenW - T_FRAME_W) / 2;
        int violet = 0xC070FF, pale = 0xEAD0FF;
        y += 6; // room for the crown above the frame
        // the name, larger, glowing
        Component name = giant.getDisplayName();
        int nameW = mc.font.width(name);
        g.pose().pushPose();
        g.pose().translate(screenW / 2f, y - 13, 0);
        g.pose().scale(1.3f, 1.3f, 1f);
        glow(g, 0, 4, violet, 0.35f + 0.25f * pulse, 3);
        g.drawString(mc.font, name, -nameW / 2, 0, pale, true);
        g.pose().popPose();
        // the frame and its crown
        g.blit(TEX, x, y, 0, 128, T_FRAME_W, T_FRAME_H, TEX_W, TEX_H);
        glow(g, screenW / 2, y - 2, violet, 0.5f + 0.3f * pulse, 2);
        g.blit(TEX, (screenW - T_CROWN_W) / 2, y - 12, 0, 196, T_CROWN_W, T_CROWN_H, TEX_W, TEX_H);
        // the track, then the void river as far as the health goes, scrolling
        int fx = x + T_FILL_X, fy = y + T_FILL_Y;
        for (int ox = 0; ox < T_FILL_W; ox += T_STRIP) g.blit(TEX, fx + ox, fy, 0, 180, Math.min(T_STRIP, T_FILL_W - ox), T_FILL_H, TEX_W, TEX_H);
        int w = Mth.clamp((int) (T_FILL_W * progress), 0, T_FILL_W);
        if (w > 0) {
            int scroll = (int) (t * 1.5f) % T_STRIP;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(((violet >> 16) & 0xFF) / 255f, ((violet >> 8) & 0xFF) / 255f, (violet & 0xFF) / 255f, 1f);
            int drawn = 0;
            while (drawn < w) {
                int u = (scroll + drawn) % T_STRIP;
                int seg = Math.min(T_STRIP - u, w - drawn);
                g.blit(TEX, fx + drawn, fy, u, 164, seg, T_FILL_H, TEX_W, TEX_H);
                drawn += seg;
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            // the burning edge
            glow(g, fx + w, fy + T_FILL_H / 2, pale, 0.7f + 0.3f * pulse, 2);
            if (w < T_FILL_W) tinted(g, pale, 0.8f + 0.2f * pulse, fx + w - 1, fy, 24, 64, Math.min(4, T_FILL_W - w + 1), 12);
        }
        // the phase on its plate
        String phase = switch (giant.phase()) { case 2 -> "II"; case 3 -> "III"; default -> "I"; };
        int cx = fx + T_FILL_W - 32, cy = fy - 1;
        g.blit(TEX, cx, cy, 128, 164, 30, 16, TEX_W, TEX_H);
        g.drawString(mc.font, phase, cx + 15 - mc.font.width(phase) / 2, cy + 4, pale, true);
        // motes of the void drifting along the frame
        for (int i = 0; i < 7; i++) {
            float ph = (t * 0.02f + i * 0.143f) % 1f;
            int mx = x + 8 + (int) (ph * (T_FRAME_W - 16));
            int my = y + 2 + (int) (2.5f + 2.5f * Mth.sin(t * 0.15f + i * 1.7f)) + (i % 2 == 0 ? 0 : T_FRAME_H - 9);
            int a = (int) (120 + 120 * Mth.sin(ph * (float) Math.PI));
            g.fill(mx, my, mx + 1, my + 1, (a << 24) | (pale & 0xFFFFFF));
        }
        // the cores in black glass
        int cores;
        try {
            cores = giant.body().cores.size();
        } catch (RuntimeException ex) {
            cores = 0;
        }
        if (cores > 0) {
            int total = cores * T_SOCKET_STEP - (T_SOCKET_STEP - T_SOCKET);
            int sx = (screenW - total) / 2, sy = y + T_FRAME_H - 2;
            for (int i = 0; i < cores; i++) {
                int ox = sx + i * T_SOCKET_STEP;
                boolean broken = giant.isCoreBroken(i);
                if (!broken) {
                    float beat = 0.5f + 0.5f * Mth.sin(t * 0.18f + i * 0.9f);
                    glow(g, ox + T_SOCKET / 2, sy + T_SOCKET / 2, violet, 0.7f + 0.3f * beat, 2);
                    g.blit(TEX, ox, sy, 160, 164, T_SOCKET, T_SOCKET, TEX_W, TEX_H);
                    tinted(g, lighten(violet, 0.3f), 1f, ox + 3, sy + 3, 176, 164, 7, 7);
                    int spark = 0x80 + (int) (0x7F * beat);
                    g.fill(ox + 5, sy + 5, ox + 7, sy + 7, (spark << 24) | 0xFFFFFF);
                } else {
                    g.blit(TEX, ox, sy, 160, 164, T_SOCKET, T_SOCKET, TEX_W, TEX_H);
                    g.blit(TEX, ox + 3, sy + 3, 192, 164, 7, 7, TEX_W, TEX_H);
                }
            }
        }
    }

    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
