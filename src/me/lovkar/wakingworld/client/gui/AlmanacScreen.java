package me.lovkar.wakingworld.client.gui;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.item.WakingItems;
import me.lovkar.wakingworld.ritual.Rites;
import me.lovkar.wakingworld.ritual.WakingRitual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The Waker's Almanac: an open leather book with a tab per chapter down its left edge, two pages
 * of laid-out text, item icons with tooltips where an item is spoken of, colour swatches for the
 * kinds, and page arrows. The chapters come from lang keys ({@code almanac.<chapter>.<n>}), so
 * the text can be translated and touched up without touching code.
 */
public class AlmanacScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/almanac.png");
    private static final int TEX_W = 512, TEX_H = 256;
    private static final int BOOK_W = 292, BOOK_H = 192;
    private static final int PAGE_W = 102, PAGE_H = 146, PAGE_Y = 32;
    private static final int LEFT_X = 24, RIGHT_X = 166;
    static final int INK = 0x3A2A1C, HEAD = 0x6E2A18, CAPTION = 0x4A3A2A, FADED = 0x7A6A58;

    private static final int KEY_RIGHT = 262, KEY_LEFT = 263, KEY_PAGE_UP = 266, KEY_PAGE_DOWN = 267;

    private record Chapter(String id, ItemStack icon, List<List<PageLayout.Element>> pages) {
    }

    private static int lastChapter = 0, lastSpread = 0;

    private final List<Chapter> chapters = new ArrayList<>();
    private int chapter, spread;
    private int left, top;
    private final PageLayout.Hover hover = new PageLayout.Hover();
    private final List<Tab> tabs = new ArrayList<>();
    private final List<Arrow> arrows = new ArrayList<>();

    public AlmanacScreen() {
        super(Component.translatable("book.wakingworld.almanac.title"));
    }

    @Override
    protected void init() {
        left = (width - BOOK_W) / 2;
        top = (height - BOOK_H) / 2;
        if (chapters.isEmpty()) build();
        chapter = Math.min(lastChapter, chapters.size() - 1);
        spread = lastSpread;
        clampSpread();
        tabs.clear();
        arrows.clear();
        for (int i = 0; i < chapters.size(); i++) tabs.add(addRenderableWidget(new Tab(i, left - 27, top + 5 + i * 23)));
        arrows.add(addRenderableWidget(new Arrow(false, left + 22, top + 176)));
        arrows.add(addRenderableWidget(new Arrow(true, left + BOOK_W - 40, top + 176)));
    }

    private void clampSpread() {
        int max = Math.max(0, (chapters.get(chapter).pages.size() - 1) / 2);
        spread = Math.max(0, Math.min(spread, max));
        lastChapter = chapter;
        lastSpread = spread;
    }

    // ------------------------------------------------------------ content

    private Component t(String key) {
        return Component.translatable("almanac.wakingworld." + key);
    }

    private PageLayout.Flow flow() {
        return new PageLayout.Flow(font, PAGE_W);
    }

    private void add(String id, ItemStack icon, PageLayout.Flow f) {
        chapters.add(new Chapter(id, icon, PageLayout.paginate(f.elements(), PAGE_H)));
    }

    private static ItemStack of(net.minecraft.world.item.Item item) {
        return new ItemStack(item);
    }

    private void build() {
        // I. welcome + advice
        add("welcome", of(WakingItems.ALMANAC.get()), flow()
                .centered(t("welcome.sub"), FADED)
                .paragraph(t("welcome.1"), INK)
                .paragraph(t("welcome.2"), INK)
                .paragraph(t("welcome.3"), INK)
                .heading(t("advice.title"), HEAD)
                .paragraph(t("advice.1"), INK)
                .paragraph(t("advice.2"), INK)
                .paragraph(t("advice.3"), INK)
                .centered(t("advice.sign"), FADED));
        // II. the sleepers
        PageLayout.Flow s = flow().paragraph(t("sleepers.1"), INK).paragraph(t("sleepers.2"), INK);
        String[] kinds = {"stone", "earth", "sandstone", "ice", "prismarine", "moss"};
        for (String k : kinds) {
            s.items(t("kind." + k), CAPTION, of(WakingItems.sigilFor(k)), of(runeFor(k)));
            s.swatch(Rites.color(k), t("kind." + k + ".where"), FADED);
            s.gap(4);
        }
        s.paragraph(t("sleepers.3"), INK);
        add("sleepers", of(WakingItems.COLOSSUS_HEART.get()), s);
        // III. shrines and altars
        add("shrines", of(WakingRitual.ALTAR_ITEM.get()), flow()
                .paragraph(t("shrines.1"), INK)
                .items(t("shrines.altar"), CAPTION, of(WakingRitual.ALTAR_ITEM.get()))
                .paragraph(t("shrines.2"), INK)
                .paragraph(t("shrines.3"), INK)
                .paragraph(t("shrines.4"), INK));
        // IV. the rite
        add("rite", of(WakingItems.HORN_OF_WAKING.get()), flow()
                .paragraph(t("rite.1"), INK)
                .items(t("rite.ember"), CAPTION, of(WakingItems.SLEEPERS_EMBER.get()))
                .items(t("rite.runes"), CAPTION, of(WakingItems.RUNE_STONE.get()), of(WakingItems.RUNE_EARTH.get()), of(WakingItems.RUNE_SANDSTONE.get()),
                        of(WakingItems.RUNE_ICE.get()), of(WakingItems.RUNE_PRISMARINE.get()), of(WakingItems.RUNE_MOSS.get()))
                .paragraph(t("rite.2"), INK)
                .items(t("rite.horn"), CAPTION, of(WakingItems.HORN_OF_WAKING.get()))
                .paragraph(t("rite.3"), INK)
                .paragraph(t("rite.4"), INK));
        // V. the vaults
        add("vaults", of(WakingItems.SLEEPERS_EMBER.get()), flow()
                .paragraph(t("vaults.1"), INK)
                .paragraph(t("vaults.2"), INK)
                .items(t("vaults.stores"), CAPTION, of(WakingItems.SLEEPERS_EMBER.get()), of(WakingItems.RUNE_EARTH.get()), of(WakingItems.HORN_OF_WAKING.get()))
                .paragraph(t("vaults.3"), INK)
                .paragraph(t("vaults.4"), INK)
                .paragraph(t("vaults.5"), INK));
        // VI. letters and ruins
        add("letters", of(WakingItems.DEAD_LETTER.get()), flow()
                .paragraph(t("letters.1"), INK)
                .items(t("letters.letter"), CAPTION, of(WakingItems.DEAD_LETTER.get()))
                .paragraph(t("letters.2"), INK)
                .heading(t("ruins.title"), HEAD)
                .paragraph(t("ruins.1"), INK)
                .paragraph(t("ruins.2"), INK));
        // VII. the fight
        add("fight", of(WakingItems.COLOSSUS_HAMMER.get()), flow()
                .paragraph(t("fight.1"), INK)
                .paragraph(t("fight.2"), INK)
                .items(t("fight.hammer"), CAPTION, of(WakingItems.COLOSSUS_HAMMER.get()))
                .paragraph(t("fight.3"), INK)
                .paragraph(t("fight.4"), INK));
        // VIII. what it leaves, and the key
        add("spoils", of(WakingItems.HOURGLASS.get()), flow()
                .paragraph(t("spoils.1"), INK)
                .items(t("spoils.heart"), CAPTION, of(WakingItems.COLOSSUS_HEART.get()))
                .items(t("spoils.sigils"), CAPTION, of(WakingItems.SIGIL_STONE.get()), of(WakingItems.SIGIL_EARTH.get()), of(WakingItems.SIGIL_SANDSTONE.get()),
                        of(WakingItems.SIGIL_ICE.get()), of(WakingItems.SIGIL_PRISMARINE.get()), of(WakingItems.SIGIL_MOSS.get()))
                .items(t("spoils.forged"), CAPTION, of(WakingItems.COLOSSUS_HAMMER.get()), of(WakingItems.HOURGLASS.get()))
                .paragraph(t("spoils.2"), INK)
                .heading(t("key.title"), HEAD)
                .paragraph(t("key.1"), INK)
                .items(t("key.key"), CAPTION, of(WakingItems.TITAN_KEY.get()))
                .items(t("key.sigil"), CAPTION, of(WakingItems.VOID_SIGIL.get()))
                .items(t("key.egg"), CAPTION, of(net.minecraft.world.item.Items.DRAGON_EGG))
                .items(t("key.runes"), CAPTION, of(WakingItems.RUNE_STONE.get()), of(WakingItems.RUNE_EARTH.get()), of(WakingItems.RUNE_SANDSTONE.get()),
                        of(WakingItems.RUNE_ICE.get()), of(WakingItems.RUNE_PRISMARINE.get()), of(WakingItems.RUNE_MOSS.get()))
                .paragraph(t("key.gate"), INK)
                .paragraph(t("key.2"), INK));
    }

    private static net.minecraft.world.item.Item runeFor(String kind) {
        return switch (kind) {
            case "earth" -> WakingItems.RUNE_EARTH.get();
            case "sandstone" -> WakingItems.RUNE_SANDSTONE.get();
            case "ice" -> WakingItems.RUNE_ICE.get();
            case "prismarine" -> WakingItems.RUNE_PRISMARINE.get();
            case "moss" -> WakingItems.RUNE_MOSS.get();
            default -> WakingItems.RUNE_STONE.get();
        };
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hover.stack = ItemStack.EMPTY;
        // the tabs first, tucked under the cover
        for (Tab tab : tabs) tab.render(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, BOOK_W, BOOK_H, TEX_W, TEX_H);
        Chapter ch = chapters.get(chapter);
        // the chapter title over the left page, the book's name over the right
        Component title = t(ch.id + ".title");
        g.drawString(font, title, left + LEFT_X + (PAGE_W - font.width(title)) / 2, top + 13, HEAD, false);
        Component name = Component.translatable("book.wakingworld.almanac.title");
        g.drawString(font, name, left + RIGHT_X + (PAGE_W - font.width(name)) / 2, top + 13, FADED, false);
        g.blit(TEX, left + LEFT_X + (PAGE_W - 96) / 2, top + 22, 300, 130, 96, 7, TEX_W, TEX_H);
        g.blit(TEX, left + RIGHT_X + (PAGE_W - 96) / 2, top + 22, 300, 130, 96, 7, TEX_W, TEX_H);
        int pl = spread * 2, pr = pl + 1;
        if (pl < ch.pages.size()) PageLayout.render(g, font, ch.pages.get(pl), left + LEFT_X, top + PAGE_Y, PAGE_W, mouseX, mouseY, hover);
        if (pr < ch.pages.size()) PageLayout.render(g, font, ch.pages.get(pr), left + RIGHT_X, top + PAGE_Y, PAGE_W, mouseX, mouseY, hover);
        // page numbers
        String nl = String.valueOf(pl + 1), nr = String.valueOf(pr + 1);
        g.drawString(font, nl, left + LEFT_X + PAGE_W / 2 - font.width(nl) / 2, top + 179, FADED, false);
        if (pr < ch.pages.size()) g.drawString(font, nr, left + RIGHT_X + PAGE_W / 2 - font.width(nr) / 2, top + 179, FADED, false);
        // the ribbon, on the cover's edge
        g.blit(TEX, left + BOOK_W - 12, top - 2, 300, 90, 10, 34, TEX_W, TEX_H);
        for (Arrow a : arrows) a.render(g, mouseX, mouseY, partialTick);
        if (!hover.stack.isEmpty()) g.renderTooltip(font, hover.stack, mouseX, mouseY);
        for (Tab tab : tabs) if (tab.isHovered()) g.renderTooltip(font, t(chapters.get(tab.index).id + ".title"), mouseX, mouseY);
    }

    private void flip(int dir) {
        Chapter ch = chapters.get(chapter);
        int max = Math.max(0, (ch.pages.size() - 1) / 2);
        if (dir > 0) {
            if (spread < max) spread++;
            else if (chapter + 1 < chapters.size()) { chapter++; spread = 0; }
            else return;
        } else {
            if (spread > 0) spread--;
            else if (chapter > 0) { chapter--; spread = Math.max(0, (chapters.get(chapter).pages.size() - 1) / 2); }
            else return;
        }
        clampSpread();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    private void open(int index) {
        if (index == chapter) return;
        chapter = index;
        spread = 0;
        clampSpread();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9F));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_LEFT || keyCode == KEY_PAGE_UP) { flip(-1); return true; }
        if (keyCode == KEY_RIGHT || keyCode == KEY_PAGE_DOWN) { flip(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) { flip(scrollY < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A chapter tab down the left edge of the cover; the open chapter's is wider and paper-coloured. */
    private class Tab extends AbstractWidget {
        final int index;

        Tab(int index, int x, int y) {
            super(x, y, 28, 24, Component.empty());
            this.index = index;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean sel = index == chapter;
            int x = getX() - (sel ? 4 : 0) - (isHovered() && !sel ? 2 : 0);
            g.blit(TEX, x, getY(), sel ? 300 : 300, sel ? 26 : 0, sel ? 32 : 28, 24, TEX_W, TEX_H);
            g.renderItem(chapters.get(index).icon, x + 5, getY() + 4);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            open(index);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, t(chapters.get(index).id + ".title"));
        }
    }

    /** A page arrow, warm when hovered. */
    private class Arrow extends AbstractWidget {
        final boolean right;

        Arrow(boolean right, int x, int y) {
            super(x, y, 18, 10, Component.empty());
            this.right = right;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blit(TEX, getX(), getY(), isHovered() ? 320 : 300, right ? 72 : 60, 18, 10, TEX_W, TEX_H);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            flip(right ? 1 : -1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
        }
    }
}
