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

    private static final int TAB_W = 28, TAB_H = 24, TAB_STEP = 23, TAB_OUT = 4;

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
        layOutTabs();
        arrows.add(addRenderableWidget(new Arrow(false, left + 22, top + 176)));
        arrows.add(addRenderableWidget(new Arrow(true, left + BOOK_W - 40, top + 176)));
    }

    /**
     * The chapter tabs. They used to run down the left edge in one column, which was fine at six
     * chapters and wrong at ten: the last of them hung below the book and off the bottom of the
     * screen. They are split down BOTH edges now - the first half on the left, the rest on the
     * right - which halves the column and keeps the book itself in the middle of the window.
     *
     * <p>Two columns want {@code BOOK_W + 60} of width. A window too narrow for that (the game
     * guarantees only 320) keeps them all on the left and closes the spacing up until they fit the
     * cover instead, which is worse-looking and still readable, rather than off the screen.</p>
     */
    private void layOutTabs() {
        int n = chapters.size();
        boolean bothEdges = width >= BOOK_W + 60;
        int perSide = bothEdges ? (n + 1) / 2 : n;
        int step = perSide > 1 ? Math.min(TAB_STEP, (BOOK_H - 10 - TAB_H) / (perSide - 1)) : TAB_STEP;
        for (int i = 0; i < n; i++) {
            boolean right = bothEdges && i >= perSide;
            int row = right ? i - perSide : i;
            int x = right ? left + BOOK_W - 1 : left - TAB_W + 1;
            tabs.add(addRenderableWidget(new Tab(i, x, top + 5 + row * step, right)));
        }
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
        // I. welcome + advice, and the Hall of Wakers at the back of the chapter
        PageLayout.Flow w = flow()
                .centered(t("welcome.sub"), FADED)
                .paragraph(t("welcome.1"), INK)
                .paragraph(t("welcome.2"), INK)
                .paragraph(t("welcome.3"), INK)
                .heading(t("advice.title"), HEAD)
                .paragraph(t("advice.1"), INK)
                .paragraph(t("advice.2"), INK)
                .paragraph(t("advice.3"), INK)
                .centered(t("advice.sign"), FADED);
        if (me.lovkar.wakingworld.supporter.SupporterList.ENABLED) hall(w);
        add("welcome", of(WakingItems.ALMANAC.get()), w);
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
        // IX. the atlas of the lands walked
        atlas();
        // X. what the world does back
        cataclysms();
        // XI. and the one man who will sell you a piece of it
        if (me.lovkar.wakingworld.WakingConfig.mage()) mage();
    }

    /**
     * The chapter on the mage.
     *
     * <p>It is deliberately short and deliberately unhelpful about where he is, because finding the
     * tower is the good part. What it does say is the two things a player cannot work out by
     * looking: that he will not start it, and that the stone comes from him and from nowhere else -
     * without those two sentences somebody kills him on sight and never learns there was a trade.</p>
     */
    private void mage() {
        add("mage", of(me.lovkar.wakingworld.mage.MageBlocks.RITE_STONE_ITEM.get()), flow()
                .paragraph(t("mage.1"), INK)
                .paragraph(t("mage.2"), INK)
                .items(t("mage.stone"), CAPTION, of(me.lovkar.wakingworld.mage.MageBlocks.RITE_STONE_ITEM.get()))
                .paragraph(t("mage.3"), INK)
                .heading(t("mage.price.title"), HEAD)
                .paragraph(t("mage.price.1"), INK)
                .items(t("mage.price.ember"), CAPTION, of(WakingItems.SLEEPERS_EMBER.get()))
                .heading(t("mage.fight.title"), HEAD)
                .paragraph(t("mage.fight.1"), INK)
                .items(t("mage.fight.rod"), CAPTION, of(WakingItems.STORM_ROD.get()))
                .paragraph(t("mage.fight.2"), FADED));
    }

    /**
     * The chapter on the cataclysms, and on the one thing worth making out of them.
     *
     * <p>Everything in 0.2 happens to the player rather than being sought out by them, which makes
     * it the content most likely to be misread as the mod misbehaving: a village flattened with no
     * explanation is a bug report. So this says plainly what the five are, what each one leaves
     * behind that is worth having, and that the warning is always there before any of them.</p>
     *
     * <p>Star Iron shares the chapter because it belongs to it: it comes out of a crater, and what
     * it is good for is surviving the next one.</p>
     */
    private void cataclysms() {
        PageLayout.Flow c = flow()
                .paragraph(t("cataclysms.1"), INK)
                .paragraph(t("cataclysms.2"), INK)
                .heading(t("cataclysms.five"), HEAD)
                .paragraph(t("cataclysms.meteor"), INK)
                .paragraph(t("cataclysms.volcano"), INK)
                .paragraph(t("cataclysms.tornado"), INK)
                .paragraph(t("cataclysms.earthquake"), INK)
                .paragraph(t("cataclysms.bloodmoon"), INK)
                .heading(t("cataclysms.unrest"), HEAD)
                .paragraph(t("cataclysms.unrest1"), INK)
                .paragraph(t("cataclysms.unrest2"), INK)
                .paragraph(t("cataclysms.unrest3"), INK)
                .heading(t("star.title"), HEAD)
                .paragraph(t("star.1"), INK)
                .items(t("star.stone"), CAPTION,
                        of(me.lovkar.wakingworld.cataclysm.CataclysmBlocks.STARSTONE_ITEM.get()),
                        of(WakingItems.STAR_IRON.get()))
                .paragraph(t("star.2"), INK)
                .items(t("star.suit"), CAPTION,
                        of(WakingItems.STAR_IRON_HELMET.get()), of(WakingItems.STAR_IRON_CHESTPLATE.get()),
                        of(WakingItems.STAR_IRON_LEGGINGS.get()), of(WakingItems.STAR_IRON_BOOTS.get()))
                .paragraph(t("star.3"), INK)
                .items(t("star.tools"), CAPTION,
                        of(WakingItems.STAR_IRON_SWORD.get()), of(WakingItems.STAR_IRON_PICKAXE.get()),
                        of(WakingItems.STAR_IRON_AXE.get()), of(WakingItems.STAR_IRON_SHOVEL.get()),
                        of(WakingItems.STAR_IRON_HOE.get()))
                .paragraph(t("star.4"), FADED);
        add("cataclysms", of(me.lovkar.wakingworld.cataclysm.CataclysmBlocks.STARSTONE_ITEM.get()), c);
    }

    /**
     * The chapter on the lands. It used to hold the atlas itself; the atlas is the Wayfarer's Chart
     * now, which is a thing you hold in your hand and can look at while you are deciding where to
     * walk. What is left here is what a book is actually good for: telling you the chart exists.
     */
    private void atlas() {
        add("atlas", of(WakingItems.LAND_ATLAS.get()), flow()
                .paragraph(t("atlas.1"), INK)
                .items(t("atlas.chart"), CAPTION, of(WakingItems.LAND_ATLAS.get()))
                .paragraph(t("atlas.2"), INK)
                .paragraph(t("atlas.3"), FADED));
    }

    /**
     * The Hall of Wakers: the supporters of the mod who chose to be named, in their tiers' colours,
     * from the supporter service's opt-in credits. Nobody is named who did not tick the box.
     */
    private void hall(PageLayout.Flow f) {
        f.heading(t("hall.title"), HEAD).paragraph(t("hall.1"), INK);
        int named = 0;
        String[] tiers = {"titan", "colossus", "waker"};
        int[] colors = {0xB266FF, 0xFF9628, 0x5CFF3C};
        for (int i = 0; i < tiers.length; i++) {
            java.util.List<String> names = me.lovkar.wakingworld.supporter.SupporterList.credits(tiers[i]);
            if (names.isEmpty()) continue;
            f.centered(t("hall." + tiers[i]), CAPTION);
            for (String n : names) f.swatch(colors[i], Component.literal(n), INK);
            f.gap(4);
            named += names.size();
        }
        if (named == 0) f.centered(t("hall.empty"), FADED);
        f.paragraph(t("hall.2"), FADED);
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

    /**
     * A chapter tab on one edge of the cover; the open chapter's is wider and paper-coloured, and a
     * hovered one leans a little further out.
     *
     * <p>A tab on the right edge is the same picture drawn backwards - the sheet has one tab in it,
     * and mirroring it under the pose is cheaper and truer than painting a second one that has to be
     * kept in step with the first. The icon is drawn outside the mirror, or it would be backwards
     * too.</p>
     */
    private class Tab extends AbstractWidget {
        final int index;
        final boolean right;

        Tab(int index, int x, int y, boolean right) {
            super(x, y, TAB_W, TAB_H, Component.empty());
            this.index = index;
            this.right = right;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean sel = index == chapter;
            int out = sel ? TAB_OUT : (isHovered() ? 2 : 0);      // how far it leans clear of the cover
            int w = sel ? TAB_W + 4 : TAB_W;
            if (right) {
                // the sheet carries a mirrored copy at u=360. Flipping the pose with a negative
                // scale reverses the quad's winding, which the GUI pipeline culls: the tabs on this
                // side drew nothing at all and their icons hung in the air.
                int x = getX() + out;
                g.blit(TEX, x, getY(), 360, sel ? 26 : 0, w, TAB_H, TEX_W, TEX_H);
                g.renderItem(chapters.get(index).icon, x + w - 21, getY() + 4);
            } else {
                int x = getX() - out;
                g.blit(TEX, x, getY(), 300, sel ? 26 : 0, w, TAB_H, TEX_W, TEX_H);
                g.renderItem(chapters.get(index).icon, x + 5, getY() + 4);
            }
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
