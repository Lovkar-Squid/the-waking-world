package me.lovkar.wakingworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * The card shown when somebody walks into a named land.
 *
 * <p>This used to be the vanilla title and subtitle. The subtitle is drawn as one line and is never
 * wrapped, so a sentence of lore ran off both edges of the screen and the middle of it was all
 * anybody ever saw. It is drawn here instead: the name over a rule that opens out of the centre,
 * the lore wrapped under it to a readable measure, and the whole thing on a soft scrim so it reads
 * over a bright sky as well as over a dark one.</p>
 *
 * <p>It sits low in the frame on purpose. A card across the middle covers the very thing it is
 * naming; down here the land stays in shot behind it, which is also what makes it worth
 * recording.</p>
 */
public final class LandCard {
    /** in, hold, out - about seven seconds all told. */
    private static final int IN = 18, HOLD = 96, OUT = 30;
    private static final int LIFE = IN + HOLD + OUT;

    private static final int GOLD = 0xE8BC57;
    private static final int KIND_GOLD = 0xC9A24B;
    private static final int PALE = 0xE4DCCD;
    private static final int DARK = 0x0B0B0F;
    private static final float NAME_SCALE = 2.6f;

    private static String name = "";
    private static String lore = "";
    private static String kind = "";
    private static int age = -1;

    private LandCard() {
    }

    /** Show a land. A second land while the first is up replaces it rather than queueing. */
    public static void show(String landName, String landLore, String landKind) {
        name = landName == null ? "" : landName;
        lore = landLore == null ? "" : landLore;
        kind = landKind == null ? "" : landKind;
        age = 0;
    }

    public static void clear() {
        age = -1;
    }

    public static void clientTick(ClientTickEvent.Post event) {
        if (age < 0) return;
        if (Minecraft.getInstance().isPaused()) return;
        if (++age > LIFE) age = -1;
    }

    /** 0 while it is not up, 1 at full strength. */
    private static float alpha(float partial) {
        if (age < 0) return 0f;
        float t = age + partial;
        if (t < IN) return t / IN;
        if (t > IN + HOLD) return Math.max(0f, 1f - (t - IN - HOLD) / OUT);
        return 1f;
    }

    private static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(RenderGuiEvent.Post event) {
        if (age < 0 || name.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float a = alpha(partial);
        if (a <= 0.01f) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = age + partial;

        // the lore is wrapped to a measure rather than to the screen: a line of text as wide as a
        // monitor is not readable, and this is the thing that used to run off both edges
        int measure = Mth.clamp((int) (w * 0.42f), 200, 460);
        List<FormattedCharSequence> body = font.split(Component.literal(lore), measure);
        if (body.size() > 3) body = body.subList(0, 3);

        int lineH = font.lineHeight;
        int nameW = (int) (font.width(name) * NAME_SCALE);
        int nameH = (int) (lineH * NAME_SCALE);
        int bodyH = body.size() * (lineH + 2);
        int kindH = kind.isEmpty() ? 0 : lineH + 7;

        int blockH = kindH + 1 + 8 + nameH + 8 + 1 + 6 + bodyH;
        // the whole block rises a few pixels as it arrives and settles - a card that simply appears
        // reads as a notification, one that moves reads as a title
        float rise = (1f - ease(Mth.clamp(t / (float) (IN + 6), 0f, 1f))) * 7f;
        int top = (int) (h * 0.60f - blockH / 2f + rise);

        // the scrim. It has to be dark enough to hold gold text over a noon sky - the first version
        // was polite and vanished over bright grass - and it is faded top and bottom so it reads as
        // light rather than as a box.
        int pad = 24;
        int sTop = top - pad, sBot = top + blockH + pad;
        int band = argb(DARK, 0.72f * a);
        g.fillGradient(0, sTop, w, sTop + pad, argb(DARK, 0f), band);
        g.fill(0, sTop + pad, w, sBot - pad, band);
        g.fillGradient(0, sBot - pad, w, sBot, band, argb(DARK, 0f));

        int cx = w / 2;
        int y = top;

        // the kind, in spaced letters over the name ("STEPPE", "HIGHLAND")
        if (!kind.isEmpty()) {
            String label = spaced(kind.replace('_', ' ').toUpperCase(java.util.Locale.ROOT));
            g.drawString(font, label, cx - font.width(label) / 2, y, argb(KIND_GOLD, a * 0.95f), true);
            y += lineH + 7;
        }

        // the rules run with the name, not with the text measure, and open out of the middle as the
        // card arrives
        float open = ease(Mth.clamp((t - 2) / (float) IN, 0f, 1f));
        int half = (int) ((nameW / 2 + 30) * open);
        rule(g, cx, y, half, a * 0.9f);
        y += 8;

        g.pose().pushPose();
        g.pose().translate(cx - nameW / 2f, y, 0);
        g.pose().scale(NAME_SCALE, NAME_SCALE, 1f);
        g.drawString(font, name, 0, 0, argb(GOLD, a), true);
        g.pose().popPose();
        y += nameH + 8;

        rule(g, cx, y, half, a * 0.7f);
        y += 7;

        for (FormattedCharSequence line : body) {
            g.drawString(font, line, cx - font.width(line) / 2, y, argb(PALE, a * 0.95f), true);
            y += lineH + 2;
        }
    }

    /**
     * A gold hairline centred on x: solid in the middle, fading at both ends, with a small diamond
     * closing each end so it reads as an ornament rather than as a scratch across the screen.
     */
    private static void rule(GuiGraphics g, int cx, int y, int half, float a) {
        if (half <= 4) return;
        int c = argb(GOLD, a);
        int third = Math.max(1, half / 3);
        g.fillGradient(cx - half, y, cx - third, y + 1, argb(GOLD, 0f), c);
        g.fill(cx - third, y, cx + third, y + 1, c);
        g.fillGradient(cx + third, y, cx + half, y + 1, c, argb(GOLD, 0f));
        for (int i = 0; i < 3; i++) {                 // a two-pixel diamond at either end
            int r = 2 - i;
            g.fill(cx - half - r, y - 1 + i, cx - half + r + 1, y + i, argb(GOLD, a * 0.9f));
            g.fill(cx + half - r, y - 1 + i, cx + half + r + 1, y + i, argb(GOLD, a * 0.9f));
        }
    }

    /** Letter-spacing, which the font has no notion of: a space between every character. */
    private static String spaced(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static float ease(float x) {
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
