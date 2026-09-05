package me.lovkar.wakingworld.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a flow of elements (headings, paragraphs, rows of item icons, colour swatches) out onto
 * fixed-size pages with the real font - the same engine draws the almanac's pages and the letters.
 * Every element knows its height and how to draw itself; the flow is cut greedily, headings are
 * never left alone at the bottom of a page.
 */
public final class PageLayout {
    private PageLayout() {
    }

    public static final int LINE = 9;

    public interface Element {
        int height();

        void render(GuiGraphics g, Font font, int x, int y, int width, int mouseX, int mouseY, Hover hover);

        default boolean keepWithNext() {
            return false;
        }
    }

    /** Collects the item under the mouse so the screen can draw its tooltip last. */
    public static final class Hover {
        public ItemStack stack = ItemStack.EMPTY;
    }

    public record TextLine(FormattedCharSequence line, int color, boolean centered) implements Element {
        @Override
        public int height() {
            return LINE;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int width, int mouseX, int mouseY, Hover hover) {
            int dx = centered ? (width - font.width(line)) / 2 : 0;
            g.drawString(font, line, x + dx, y, color, false);
        }
    }

    public record Gap(int h) implements Element {
        @Override
        public int height() {
            return h;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int width, int mouseX, int mouseY, Hover hover) {
        }
    }

    /** A heading: bold text with a thin rule under it. */
    public record Heading(FormattedCharSequence line, int color) implements Element {
        @Override
        public int height() {
            return LINE + 5;
        }

        @Override
        public boolean keepWithNext() {
            return true;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int width, int mouseX, int mouseY, Hover hover) {
            g.drawString(font, line, x, y, color, false);
            int w = Math.min(width, font.width(line) + 6);
            g.fill(x, y + LINE + 1, x + w, y + LINE + 2, (color & 0xFFFFFF) | 0x60000000);
        }
    }

    /**
     * A row of item icons (18 px apart) with a caption beside them - or, when the row is wide, no caption
     * of its own: the caption then follows as ordinary text lines that paginate (a caption fused to the
     * row once made one 160 px block that ran off the bottom of the almanac's page).
     */
    public record ItemRow(List<ItemStack> items, List<FormattedCharSequence> caption, int color, boolean below) implements Element {
        @Override
        public int height() {
            return below ? 18 + 2 + caption.size() * LINE + (caption.isEmpty() ? 1 : 3) : Math.max(18, caption.size() * LINE) + 3;
        }

        @Override
        public boolean keepWithNext() {
            return below && caption.isEmpty(); // the icons stay with the first line of their caption
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int width, int mouseX, int mouseY, Hover hover) {
            int ix = x;
            for (ItemStack s : items) {
                g.fill(ix - 1, y - 1, ix + 17, y + 17, 0x22000000);
                g.renderItem(s, ix, y);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= y && mouseY < y + 16) hover.stack = s;
                ix += 18;
            }
            int tx = below ? x : ix + 3;
            int ty = below ? y + 20 : y + (caption.size() == 1 ? 4 : 0);
            for (FormattedCharSequence c : caption) {
                g.drawString(font, c, tx, ty, color, false);
                ty += LINE;
            }
        }
    }

    /** A colour swatch with a label - the kinds' colours. */
    public record Swatch(int rgb, FormattedCharSequence label, int color) implements Element {
        @Override
        public int height() {
            return LINE + 2;
        }

        @Override
        public void render(GuiGraphics g, Font font, int x, int y, int width, int mouseX, int mouseY, Hover hover) {
            g.fill(x, y, x + 8, y + 8, 0xFF000000 | rgb);
            g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF000000 | brighten(rgb));
            g.drawString(font, label, x + 12, y, color, false);
        }

        private static int brighten(int rgb) {
            int r = Math.min(255, ((rgb >> 16) & 0xFF) + 30), gg = Math.min(255, ((rgb >> 8) & 0xFF) + 30), b = Math.min(255, (rgb & 0xFF) + 30);
            return (r << 16) | (gg << 8) | b;
        }
    }

    /** Builds a flow: text is split to the width here, so the caller only hands over components. */
    public static final class Flow {
        private final Font font;
        private final int width;
        private final List<Element> elements = new ArrayList<>();

        public Flow(Font font, int width) {
            this.font = font;
            this.width = width;
        }

        public Flow heading(Component text, int color) {
            for (FormattedCharSequence l : font.split(text, width)) elements.add(new Heading(l, color));
            return this;
        }

        public Flow paragraph(Component text, int color) {
            for (FormattedCharSequence l : font.split(text, width)) elements.add(new TextLine(l, color, false));
            elements.add(new Gap(4));
            return this;
        }

        public Flow centered(Component text, int color) {
            for (FormattedCharSequence l : font.split(text, width)) elements.add(new TextLine(l, color, true));
            elements.add(new Gap(4));
            return this;
        }

        public Flow items(Component caption, int color, ItemStack... stacks) {
            int used = stacks.length * 18 + 3;
            boolean below = width - used < 48;
            if (!below && font.split(caption, width - used).size() * LINE + 3 > 80) below = true; // a long caption beside a few icons: same treatment
            if (below) {
                // a wide row: the icons alone, then the caption as text lines that can break across pages
                elements.add(new ItemRow(List.of(stacks), List.of(), color, true));
                for (FormattedCharSequence l : font.split(caption, width)) elements.add(new TextLine(l, color, false));
                elements.add(new Gap(3));
                return this;
            }
            List<FormattedCharSequence> lines = font.split(caption, width - used);
            elements.add(new ItemRow(List.of(stacks), lines, color, false));
            elements.add(new Gap(3));
            return this;
        }

        public Flow swatch(int rgb, Component label, int color) {
            elements.add(new Swatch(rgb, font.split(label, width - 12).get(0), color));
            return this;
        }

        public Flow gap(int h) {
            elements.add(new Gap(h));
            return this;
        }

        public Flow raw(Element e) {
            elements.add(e);
            return this;
        }

        public List<Element> elements() {
            return elements;
        }
    }

    /** Cuts a flow into pages of {@code pageHeight} pixels. */
    public static List<List<Element>> paginate(List<Element> flow, int pageHeight) {
        List<List<Element>> pages = new ArrayList<>();
        List<Element> page = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < flow.size(); i++) {
            Element e = flow.get(i);
            if (used == 0 && e instanceof Gap) continue; // no blank space at the top of a page
            int need = e.height();
            if (e.keepWithNext() && i + 1 < flow.size()) need += flow.get(i + 1).height();
            if (used + need > pageHeight && used > 0) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
                if (e instanceof Gap) continue;
            }
            page.add(e);
            used += e.height();
        }
        if (!page.isEmpty()) pages.add(page);
        return pages;
    }

    public static void render(GuiGraphics g, Font font, List<Element> page, int x, int y, int width, int mouseX, int mouseY, Hover hover) {
        int yy = y;
        for (Element e : page) {
            e.render(g, font, x, yy, width, mouseX, mouseY, hover);
            yy += e.height();
        }
    }
}
