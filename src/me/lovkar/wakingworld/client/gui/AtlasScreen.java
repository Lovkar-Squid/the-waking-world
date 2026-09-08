package me.lovkar.wakingworld.client.gui;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.client.LandAtlas;
import me.lovkar.wakingworld.client.LandMap;
import me.lovkar.wakingworld.land.Lands;
import me.lovkar.wakingworld.ritual.Rites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wayfarer's Chart: every named land its owner has walked into, drawn where it lies.
 *
 * <p>This used to be a page of the Almanac, and a page is the wrong shape for a map - it could only
 * ever show a country small enough to fit a book, and it could not be looked at while the reader
 * was thinking about where to go next. Here it gets the whole screen: drag to move, scroll to
 * close in, and hovering a square tells you what the land is and what was written about it.</p>
 */
public class AtlasScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/atlas.png");
    private static final int TEX_W = 512, TEX_H = 256;
    private static final int SHEET_W = 372, SHEET_H = 226;
    /** The window inside the battens and the torn edges - everything drawn is clipped to this. */
    private static final int MAP_X = 16, MAP_Y = 8, MAP_W = 340, MAP_H = 210;
    private static final int CART_U = 0, CART_V = 232, CART_W = 133, CART_H = 20;
    private static final int ROSE_U = 380, ROSE_V = 0, ROSE_S = 34;
    private static final int MARK_U = 380, MARK_V = 40, MARK_S = 12;

    private static final int INK = 0xFF3A2A1C, FADED = 0xFF7A6A58, GOLD = 0xFFE2B24A;
    // The chart used to stop at nine pixels a square, which with the smaller squares of a region
    // map was barely a screenful of country. It goes down to three now (about eighteen thousand
    // blocks across the window) and up to eighty, where one square fills a quarter of the sheet and
    // the ground under it can actually be read.
    private static final int MIN_BOX = 3, MAX_BOX = 80;
    private static final int PIN_W = 150;

    private int left, top;
    /** Where the middle of the window sits, in land-cell coordinates (fractional, so panning is smooth). */
    private double centreX, centreZ;
    private int box = 22;
    private boolean framed;
    private int hereX, hereZ;                    // the square, for "this is the land you are in"
    private int meX, meZ;                        // and where I actually am, in blocks
    /** The pins panel, and the box that is open while one is being named. */
    private boolean panel;
    private net.minecraft.client.gui.components.EditBox naming;

    public AtlasScreen() {
        super(Component.translatable("book.wakingworld.atlas.title"));
    }

    @Override
    protected void init() {
        left = (width - SHEET_W) / 2;
        top = (height - SHEET_H) / 2;
        me();
        if (!framed) {
            frame();
        }
    }

    /**
     * Where the reader is, now.
     *
     * <p>This used to be read once when the chart was opened and kept as a SQUARE, and the mark was
     * drawn in the middle of that square - so standing anywhere in a town put the mark up to eighty
     * blocks from where you were, and walking with the chart open did not move it at all. A chart
     * you are holding should show where you are holding it.</p>
     */
    private void me() {
        if (minecraft == null || minecraft.player == null) return;
        meX = minecraft.player.getBlockX();
        meZ = minecraft.player.getBlockZ();
        hereX = Lands.cellOf(meX);
        hereZ = Lands.cellOf(meZ);
    }

    /** Open on everything at once if it fits, and on the reader if it does not. */
    private void frame() {
        List<LandAtlas.Entry> lands = LandAtlas.lands();
        centreX = hereX + 0.5;
        centreZ = hereZ + 0.5;
        if (!lands.isEmpty()) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (LandAtlas.Entry e : lands) {
                minX = Math.min(minX, e.cellX());
                maxX = Math.max(maxX, e.cellX());
                minZ = Math.min(minZ, e.cellZ());
                maxZ = Math.max(maxZ, e.cellZ());
            }
            minX = Math.min(minX, hereX);
            maxX = Math.max(maxX, hereX);
            minZ = Math.min(minZ, hereZ);
            maxZ = Math.max(maxZ, hereZ);
            centreX = (minX + maxX + 1) / 2.0;
            centreZ = (minZ + maxZ + 1) / 2.0;
            int fit = Math.min((MAP_W - 16) / Math.max(1, maxX - minX + 1), (MAP_H - 26) / Math.max(1, maxZ - minZ + 1));
            box = Mth.clamp(fit, MIN_BOX, 30);
        }
        framed = true;
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            centreX -= dragX / box;
            centreZ -= dragY / box;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            // zoom about the pointer, so the square under it stays under it
            double before = cellAtX(mouseX), beforeZ = cellAtZ(mouseY);
            // a fifth either way, never less than one pixel: an additive step is a crawl when the
            // squares are big and a leap when they are small
            int next = scrollY > 0 ? Math.max(box + 1, (int) Math.round(box * 1.22))
                    : Math.min(box - 1, (int) Math.round(box * 0.82));
            box = Mth.clamp(next, MIN_BOX, MAX_BOX);
            centreX += before - cellAtX(mouseX);
            centreZ += beforeZ - cellAtZ(mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (naming != null) {
            if (key == 257 || key == 335) { confirmPin(); return true; }        // enter
            if (key == 256) { naming = null; setFocused(null); return true; }   // escape
            if (naming.keyPressed(key, scan, mods)) return true;
            return true;                                                        // the map's own keys wait
        }
        switch (key) {
            case 262 -> centreX += 1;                    // right
            case 263 -> centreX -= 1;                    // left
            case 264 -> centreZ += 1;                    // down
            case 265 -> centreZ -= 1;                    // up
            case 32, 82 -> {                             // space, R: back to where I am
                me();
                centreX = meX / (double) LandAtlas.size();
                centreZ = meZ / (double) LandAtlas.size();
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    private double cellAtX(double screenX) {
        return centreX + (screenX - (left + MAP_X + MAP_W / 2.0)) / box;
    }

    private double cellAtZ(double screenY) {
        return centreZ + (screenY - (top + MAP_Y + MAP_H / 2.0)) / box;
    }

    private int screenX(int cellX) {
        return (int) Math.round(left + MAP_X + MAP_W / 2.0 + (cellX - centreX) * box);
    }

    private int screenY(int cellZ) {
        return (int) Math.round(top + MAP_Y + MAP_H / 2.0 + (cellZ - centreZ) * box);
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        me();
        renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, SHEET_W, SHEET_H, TEX_W, TEX_H);

        int x0 = left + MAP_X, y0 = top + MAP_Y;
        g.enableScissor(x0, y0, x0 + MAP_W, y0 + MAP_H);
        drawGround(g, x0, y0);
        List<LandAtlas.Entry> lands = LandAtlas.lands();
        LandAtlas.Entry hovered = null;
        boolean inWindow = mouseX >= x0 && mouseX < x0 + MAP_W && mouseY >= y0 && mouseY < y0 + MAP_H;
        for (LandAtlas.Entry e : lands) {
            boolean over = inWindow && holds(e, cellAtX(mouseX), cellAtZ(mouseY));
            if (over) hovered = e;
            drawLand(g, e, x0, y0, over);
        }
        for (LandAtlas.Entry e : lands) label(g, e, x0, y0);
        LandAtlas.Pin overPin = drawPins(g, x0, y0, mouseX, mouseY, inWindow);
        // where the reader is standing - the spot, not the square
        int mx = pinX(meX), my = pinY(meZ);
        g.blit(TEX, mx - MARK_S / 2, my - MARK_S / 2, MARK_U, MARK_V, MARK_S, MARK_S, TEX_W, TEX_H);
        g.disableScissor();

        // the rose sits in the corner of the window, over the vellum
        g.blit(TEX, x0 + MAP_W - ROSE_S - 6, y0 + MAP_H - ROSE_S - 6, ROSE_U, ROSE_V, ROSE_S, ROSE_S, TEX_W, TEX_H);

        // the title, on its cartouche, hung over the top edge
        int cx = left + (SHEET_W - CART_W) / 2;
        g.blit(TEX, cx, top - 8, CART_U, CART_V, CART_W, CART_H, TEX_W, TEX_H);
        Component title = getTitle();
        g.drawString(font, title, cx + (CART_W - font.width(title)) / 2, top - 2, INK, false);

        // the tally, and how to work it, along the bottom
        Component tally = lands.isEmpty()
                ? Component.translatable("atlas.wakingworld.empty")
                : Component.translatable("atlas.wakingworld.walked", lands.size());
        // the help runs right across the top, so the tally sits along the foot, clear of the rose
        Component help = Component.translatable("atlas.wakingworld.help");
        g.drawString(font, help, x0 + 4, y0 + 3, FADED, false);
        g.drawString(font, tally, x0 + MAP_W - ROSE_S - 12 - font.width(tally), y0 + MAP_H - 11, INK, false);
        controls(g, x0, y0, mouseX, mouseY);

        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.name()).withStyle(net.minecraft.ChatFormatting.WHITE));
            lines.add(Component.translatable("atlas.wakingworld.kind." + hovered.kind())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            for (net.minecraft.util.FormattedCharSequence line : font.split(
                    Component.literal(hovered.lore()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY), 170)) {
                lines.add(Component.literal(collapse(line)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            if (here(hovered)) {
                lines.add(Component.translatable("atlas.wakingworld.here").withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /**
     * The country itself, under everything else: the tiles {@link LandMap} has read off the world,
     * laid out in world coordinates and scaled to whatever the chart is zoomed to.
     *
     * <p>Each tile's edges are worked out from the same formula, so two tiles that touch in the
     * world touch on the sheet: rounding each tile's width on its own leaves hairlines of vellum
     * between them at most zooms, which reads as a broken map rather than a stylish one.</p>
     */
    private void drawGround(GuiGraphics g, int x0, int y0) {
        double perBlock = box / (double) LandAtlas.size();
        double ox = left + MAP_X + MAP_W / 2.0 - centreX * box;
        double oz = top + MAP_Y + MAP_H / 2.0 - centreZ * box;
        for (LandMap.Drawn t : LandMap.drawable()) {
            int lx = (int) Math.floor(ox + (double) t.tileX() * LandMap.TILE * perBlock);
            int rx = (int) Math.floor(ox + (t.tileX() + 1.0) * LandMap.TILE * perBlock);
            int ty = (int) Math.floor(oz + (double) t.tileZ() * LandMap.TILE * perBlock);
            int by = (int) Math.floor(oz + (t.tileZ() + 1.0) * LandMap.TILE * perBlock);
            if (rx <= x0 || lx >= x0 + MAP_W || by <= y0 || ty >= y0 + MAP_H) continue;
            if (rx - lx <= 0 || by - ty <= 0) continue;
            g.blit(t.texture(), lx, ty, rx - lx, by - ty, 0.0F, 0.0F, LandMap.N, LandMap.N, LandMap.N, LandMap.N);
        }
    }

    // ------------------------------------------------------------ pins

    private int addW() {
        return font.width(Component.translatable("atlas.wakingworld.pin.add")) + 10;
    }

    private int listW() {
        return font.width(Component.translatable("atlas.wakingworld.pin.list", LandAtlas.pins().size())) + 10;
    }

    private boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The two buttons along the foot of the sheet, and the list of pins when it is open. */
    private void controls(GuiGraphics g, int x0, int y0, int mouseX, int mouseY) {
        int by = y0 + MAP_H - 13;
        int ax = x0 + 4, aw = addW();
        int lx = ax + aw + 4, lw = listW();
        button(g, ax, by, aw, Component.translatable("atlas.wakingworld.pin.add"), in(mouseX, mouseY, ax, by, aw, 11), false);
        button(g, lx, by, lw, Component.translatable("atlas.wakingworld.pin.list", LandAtlas.pins().size()),
                in(mouseX, mouseY, lx, by, lw, 11), panel);

        if (naming != null) {
            int nx = x0 + 4, ny = by - 16;
            g.fill(nx - 1, ny - 1, nx + PIN_W + 1, ny + 13, 0xE6EDE3CB);
            g.renderOutline(nx - 1, ny - 1, PIN_W + 2, 14, 0xFF6E4A28);
            naming.render(g, mouseX, mouseY, 0f);
            return;
        }
        if (!panel) return;

        List<LandAtlas.Pin> marks = LandAtlas.pins();
        int rows = Math.max(1, marks.size());
        int h = rows * 11 + 8;
        int py = Math.max(y0 + 14, by - 4 - h);
        g.fill(x0 + 4, py, x0 + 4 + PIN_W, py + h, 0xE6EDE3CB);
        g.renderOutline(x0 + 4, py, PIN_W, h, 0xFF6E4A28);
        if (marks.isEmpty()) {
            g.drawString(font, Component.translatable("atlas.wakingworld.pin.none"), x0 + 9, py + 5, FADED, false);
            return;
        }
        for (int i = 0; i < marks.size(); i++) {
            LandAtlas.Pin pin = marks.get(i);
            int ry = py + 4 + i * 11;
            boolean hot = in(mouseX, mouseY, x0 + 5, ry, PIN_W - 2, 11);
            if (hot) g.fill(x0 + 5, ry, x0 + 3 + PIN_W, ry + 11, 0x33402C18);
            g.drawString(font, trim(pin.name(), PIN_W - 62), x0 + 9, ry + 2, INK, false);
            String at = pin.x() + ", " + pin.z();
            g.drawString(font, at, x0 + 1 + PIN_W - font.width(at) - 5, ry + 2, FADED, false);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, Component label, boolean hot, boolean on) {
        g.fill(x, y, x + w, y + 11, on ? 0xCC402C18 : (hot ? 0xB3EDE3CB : 0x80EDE3CB));
        g.renderOutline(x, y, w, 11, hot || on ? 0xFF6E4A28 : 0x77402C18);
        g.drawString(font, label, x + 5, y + 2, on ? 0xFFEDE3CB : INK, false);
    }

    private String trim(String text, int room) {
        if (font.width(text) <= room) return text;
        String t = text;
        while (t.length() > 1 && font.width(t + "\u2026") > room) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    /** A pin under the pointer on the sheet itself. */
    private LandAtlas.Pin pinAt(double mouseX, double mouseY) {
        for (LandAtlas.Pin pin : LandAtlas.pins()) {
            int px = pinX(pin.x()), py = pinY(pin.z());
            if (Math.abs(mouseX - px) <= 4 && mouseY >= py - 11 && mouseY <= py + 1) return pin;
        }
        return null;
    }

    private void jumpTo(LandAtlas.Pin pin) {
        centreX = pin.x() / (double) LandAtlas.size();
        centreZ = pin.z() / (double) LandAtlas.size();
        panel = false;
    }

    private void startNaming(int x0, int y0) {
        String suggestion = "";
        if (minecraft != null && minecraft.player != null) {
            int cx = Lands.cellOf(minecraft.player.getBlockX()), cz = Lands.cellOf(minecraft.player.getBlockZ());
            for (LandAtlas.Entry e : LandAtlas.lands()) {
                if (holds(e, cx, cz)) { suggestion = e.name(); break; }
            }
        }
        if (suggestion.isEmpty() && minecraft != null && minecraft.player != null) {
            suggestion = minecraft.player.getBlockX() + ", " + minecraft.player.getBlockZ();
        }
        naming = new net.minecraft.client.gui.components.EditBox(font, x0 + 6, y0 + MAP_H - 27, PIN_W - 4, 12,
                Component.translatable("atlas.wakingworld.pin.add"));
        naming.setMaxLength(40);
        naming.setValue(suggestion);
        naming.moveCursorToEnd(false);
        naming.setHighlightPos(0);
        naming.setFocused(true);
        setFocused(naming);
    }

    private void confirmPin() {
        if (naming == null || minecraft == null || minecraft.player == null) return;
        String name = naming.getValue().trim();
        naming = null;
        setFocused(null);
        me.lovkar.wakingworld.network.WakingNet.pin(name,
                minecraft.player.getBlockX(), minecraft.player.getBlockZ(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x0 = left + MAP_X, y0 = top + MAP_Y;
        if (naming != null) {
            if (naming.mouseClicked(mouseX, mouseY, button)) return true;
            confirmPin();
            return true;
        }
        int by = y0 + MAP_H - 13;
        int ax = x0 + 4, aw = addW();
        int lx = ax + aw + 4, lw = listW();
        if (in((int) mouseX, (int) mouseY, ax, by, aw, 11)) {
            startNaming(x0, y0);
            return true;
        }
        if (in((int) mouseX, (int) mouseY, lx, by, lw, 11)) {
            panel = !panel;
            return true;
        }
        List<LandAtlas.Pin> marks = LandAtlas.pins();
        if (panel && !marks.isEmpty()) {
            int h = marks.size() * 11 + 8;
            int py = Math.max(y0 + 14, by - 4 - h);
            for (int i = 0; i < marks.size(); i++) {
                int ry = py + 4 + i * 11;
                if (!in((int) mouseX, (int) mouseY, x0 + 5, ry, PIN_W - 2, 11)) continue;
                if (button == 1) me.lovkar.wakingworld.network.WakingNet.pin("", marks.get(i).x(), marks.get(i).z(), true);
                else jumpTo(marks.get(i));
                return true;
            }
        }
        LandAtlas.Pin on = pinAt(mouseX, mouseY);
        if (on != null) {
            if (button == 1) me.lovkar.wakingworld.network.WakingNet.pin("", on.x(), on.z(), true);
            else jumpTo(on);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (naming != null && naming.charTyped(c, mods)) return true;
        return super.charTyped(c, mods);
    }

    /** Where a world position falls on the sheet. */
    private int pinX(int blockX) {
        return (int) Math.round(left + MAP_X + MAP_W / 2.0 + (blockX / (double) LandAtlas.size() - centreX) * box);
    }

    private int pinY(int blockZ) {
        return (int) Math.round(top + MAP_Y + MAP_H / 2.0 + (blockZ / (double) LandAtlas.size() - centreZ) * box);
    }

    /** The player's own marks, over everything: a nail, a head, and a name when there is room. */
    private LandAtlas.Pin drawPins(GuiGraphics g, int x0, int y0, int mouseX, int mouseY, boolean inWindow) {
        LandAtlas.Pin over = null;
        for (LandAtlas.Pin pin : LandAtlas.pins()) {
            int px = pinX(pin.x()), py = pinY(pin.z());
            if (px < x0 - 40 || px > x0 + MAP_W + 40 || py < y0 - 20 || py > y0 + MAP_H + 20) continue;
            boolean hot = inWindow && Math.abs(mouseX - px) <= 4 && mouseY >= py - 11 && mouseY <= py + 1;
            if (hot) over = pin;
            int ink = hot ? 0xFFF0C864 : GOLD;
            g.fill(px, py - 9, px + 1, py, 0xFF2A2018);                 // the nail
            g.fill(px - 3, py - 12, px + 4, py - 7, ink);               // the head
            g.fill(px - 2, py - 11, px + 3, py - 8, 0xFF2A2018);
            if (box >= 10 || hot) {
                int w = font.width(pin.name());
                int tx = px + 6, ty = py - 12;
                g.fill(tx - 2, ty - 1, tx + w + 2, ty + 9, 0xB3EDE3CB);
                g.drawString(font, pin.name(), tx, ty, 0xFF2A2018, false);
            }
        }
        return over;
    }

    /** Is this land the one under a point, in square coordinates? */
    private static boolean holds(LandAtlas.Entry e, double cellX, double cellZ) {
        int cx = (int) Math.floor(cellX), cz = (int) Math.floor(cellZ);
        for (int i = 0; i < e.count(); i++) if (e.x(i) == cx && e.z(i) == cz) return true;
        return false;
    }

    /**
     * One land: a wash of its own colour over the ground, and a border drawn only where the land
     * actually ends. A land is a region of squares now, so an outline round every square would
     * draw a grid over the country instead of a coastline.
     */
    private void drawLand(GuiGraphics g, LandAtlas.Entry e, int x0, int y0, boolean over) {
        int rgb = Rites.color(e.kind()) & 0xFFFFFF;
        int wash = (over ? 0x66000000 : 0x3C000000) | rgb;
        int edge = here(e) ? GOLD : (over ? 0xFF6E4A28 : 0xAA402C18);
        for (int i = 0; i < e.count(); i++) {
            int sx = screenX(e.x(i)), sy = screenY(e.z(i));
            if (sx > x0 + MAP_W || sy > y0 + MAP_H || sx + box < x0 || sy + box < y0) continue;
            g.fill(sx, sy, sx + box, sy + box, wash);
            // a side is a border only when the land does not carry on across it
            if (!owns(e, e.x(i), e.z(i) - 1)) g.fill(sx, sy, sx + box, sy + 1, edge);
            if (!owns(e, e.x(i), e.z(i) + 1)) g.fill(sx, sy + box - 1, sx + box, sy + box, edge);
            if (!owns(e, e.x(i) - 1, e.z(i))) g.fill(sx, sy, sx + 1, sy + box, edge);
            if (!owns(e, e.x(i) + 1, e.z(i))) g.fill(sx + box - 1, sy, sx + box, sy + box, edge);
        }
    }

    private static boolean owns(LandAtlas.Entry e, int cx, int cz) {
        for (int i = 0; i < e.count(); i++) if (e.x(i) == cx && e.z(i) == cz) return true;
        return false;
    }

    private boolean here(LandAtlas.Entry e) {
        return owns(e, hereX, hereZ);
    }

    /** The land's name, once, in the middle of the region rather than once per square. */
    private void label(GuiGraphics g, LandAtlas.Entry e, int x0, int y0) {
        long sx = 0, sz = 0;
        for (int i = 0; i < e.count(); i++) {
            sx += e.x(i);
            sz += e.z(i);
        }
        int n = Math.max(1, e.count());
        double mx = screenX(0) + (sx / (double) n + 0.5) * box;
        double my = screenY(0) + (sz / (double) n + 0.5) * box;
        int room = (int) Math.round(Math.sqrt(e.count()) * box) - 4;
        if (room < 12) return;
        String tag = fit(e.name(), room);
        int tx = (int) Math.round(mx - font.width(tag) / 2.0), ty = (int) Math.round(my - 4);
        if (tx > x0 + MAP_W || ty > y0 + MAP_H || tx + font.width(tag) < x0 || ty + 8 < y0) return;
        // a hairline of vellum behind the words: block colours are strong and ink alone is lost on them
        g.fill(tx - 2, ty - 1, tx + font.width(tag) + 2, ty + 9, 0x99EDE3CB);
        g.drawString(font, tag, tx, ty, 0xFF2A2018, false);
    }

    /** The name if it fits the square, else its initials, else the first letter. */
    private String fit(String name, int room) {
        if (font.width(name) <= room) return name;
        StringBuilder sb = new StringBuilder();
        for (String w : name.split("\\s+")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        String initials = sb.toString();
        if (font.width(initials) <= room) return initials;
        return initials.isEmpty() ? "?" : initials.substring(0, 1);
    }

    private static String collapse(net.minecraft.util.FormattedCharSequence line) {
        StringBuilder sb = new StringBuilder();
        line.accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
