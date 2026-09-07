package me.lovkar.wakingworld.client.gui;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.client.LandAtlas;
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
    private static final int MIN_BOX = 9, MAX_BOX = 46;

    private int left, top;
    /** Where the middle of the window sits, in land-cell coordinates (fractional, so panning is smooth). */
    private double centreX, centreZ;
    private int box = 22;
    private boolean framed;
    private int hereX, hereZ;

    public AtlasScreen() {
        super(Component.translatable("book.wakingworld.atlas.title"));
    }

    @Override
    protected void init() {
        left = (width - SHEET_W) / 2;
        top = (height - SHEET_H) / 2;
        if (minecraft != null && minecraft.player != null) {
            hereX = Lands.cellOf(minecraft.player.getBlockX());
            hereZ = Lands.cellOf(minecraft.player.getBlockZ());
        }
        if (!framed) {
            frame();
        }
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
            box = Mth.clamp(box + (int) Math.signum(scrollY) * Math.max(2, box / 5), MIN_BOX, MAX_BOX);
            centreX += before - cellAtX(mouseX);
            centreZ += beforeZ - cellAtZ(mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case 262 -> centreX += 1;                    // right
            case 263 -> centreX -= 1;                    // left
            case 264 -> centreZ += 1;                    // down
            case 265 -> centreZ -= 1;                    // up
            case 32, 82 -> {                             // space, R: back to where I am
                centreX = hereX + 0.5;
                centreZ = hereZ + 0.5;
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
        renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, SHEET_W, SHEET_H, TEX_W, TEX_H);

        int x0 = left + MAP_X, y0 = top + MAP_Y;
        g.enableScissor(x0, y0, x0 + MAP_W, y0 + MAP_H);
        List<LandAtlas.Entry> lands = LandAtlas.lands();
        LandAtlas.Entry hovered = null;
        for (LandAtlas.Entry e : lands) {
            int sx = screenX(e.cellX()), sy = screenY(e.cellZ());
            if (sx > x0 + MAP_W || sy > y0 + MAP_H || sx + box < x0 || sy + box < y0) continue;
            boolean over = mouseX >= sx && mouseX < sx + box - 1 && mouseY >= sy && mouseY < sy + box - 1
                    && mouseX >= x0 && mouseX < x0 + MAP_W && mouseY >= y0 && mouseY < y0 + MAP_H;
            if (over) hovered = e;
            drawLand(g, e, sx, sy, over);
        }
        // where the reader is standing, whether or not that square has a name yet
        int mx = screenX(hereX) + box / 2, my = screenY(hereZ) + box / 2;
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
        g.drawString(font, tally, x0 + 4, y0 + MAP_H - 11, INK, false);
        Component help = Component.translatable("atlas.wakingworld.help");
        g.drawString(font, help, x0 + 4, y0 + 3, FADED, false);

        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.name()).withStyle(net.minecraft.ChatFormatting.WHITE));
            lines.add(Component.translatable("atlas.wakingworld.kind." + hovered.kind())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            for (net.minecraft.util.FormattedCharSequence line : font.split(
                    Component.literal(hovered.lore()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY), 170)) {
                lines.add(Component.literal(collapse(line)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            if (hovered.cellX() == hereX && hovered.cellZ() == hereZ) {
                lines.add(Component.translatable("atlas.wakingworld.here").withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** One land: its colour, its border, and as much of its name as the square will hold. */
    private void drawLand(GuiGraphics g, LandAtlas.Entry e, int sx, int sy, boolean over) {
        int rgb = Rites.color(e.kind());
        boolean here = e.cellX() == hereX && e.cellZ() == hereZ;
        g.fill(sx, sy, sx + box - 1, sy + box - 1, (over ? 0xAA000000 : 0x7A000000) | (rgb & 0xFFFFFF));
        // a lighter top edge and a darker bottom, so the squares sit on the vellum rather than in it
        g.fill(sx, sy, sx + box - 1, sy + 1, 0x55FFFFFF);
        g.fill(sx, sy + box - 2, sx + box - 1, sy + box - 1, 0x33000000);
        g.renderOutline(sx, sy, box - 1, box - 1, here ? GOLD : (over ? 0xFF6E4A28 : 0x77402C18));
        if (box >= 14) {
            String tag = fit(e.name(), box - 4);
            g.drawString(font, tag, sx + (box - 1 - font.width(tag)) / 2, sy + (box - 1 - 8) / 2 + 1,
                    0xFF2A2018, false);
        }
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
