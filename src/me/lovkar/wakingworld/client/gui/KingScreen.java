package me.lovkar.wakingworld.client.gui;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.kingdom.KingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * An audience with the king: he sits in his alcove on the left (the living entity, turned to
 * follow the mouse), what he says is set on the parchment on the right, and the things one may
 * ask about are scrolls along the bottom - the kingdom, the sleepers, the letters, the vaults,
 * the Titan, the treasury, the news. What he knows of recent events comes from the chronicle.
 */
public class KingScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/king.png");
    static final int W = 248, H = 190;
    static final int TEXT_X = 96, TEXT_W = 134, TEXT_Y = 38, TEXT_H = 96;
    static final int INK = 0x3A2A1C, HEAD = 0x6E2A18, FADED = 0x7A6A58, GOLD = 0xC89A3C;
    private static final String[] TOPICS = {"kingdom", "sleepers", "letters", "vaults", "titan", "treasury", "news", "farewell"};

    private final KingEntity king;
    private int left, top;
    private final List<TopicButton> buttons = new ArrayList<>();
    private List<List<PageLayout.Element>> pages = List.of();
    private int page;
    private String topic = "greeting";
    private Arrow prev, next;

    public KingScreen(KingEntity king) {
        super(king.getName());
        this.king = king;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        buttons.clear();
        for (int i = 0; i < TOPICS.length; i++) {
            int x = left + 8 + (i % 4) * 58, y = top + 157 + (i / 4) * 16;
            TopicButton b = new TopicButton(x, y, TOPICS[i]);
            buttons.add(b);
            addRenderableWidget(b);
        }
        prev = new Arrow(left + TEXT_X, top + 138, false);
        next = new Arrow(left + TEXT_X + TEXT_W - 12, top + 138, true);
        addRenderableWidget(prev);
        addRenderableWidget(next);
        show(topic);
    }

    private void show(String t) {
        topic = t;
        PageLayout.Flow flow = new PageLayout.Flow(font, TEXT_W);
        switch (t) {
            case "greeting" -> {
                int n = Math.floorMod(king.getId(), 3) + 1;
                flow.paragraph(Component.translatable(king.generation() > 0 && Math.floorMod(king.getId(), 2) == 0 ? "king.wakingworld.greeting.new" : "king.wakingworld.greeting." + n), INK);
                flow.paragraph(Component.translatable("king.wakingworld.greeting.ask"), FADED);
            }
            case "treasury" -> {
                if (king.viewerPermitted()) flow.paragraph(Component.translatable("king.wakingworld.treasury.permitted"), INK);
                else paragraphs(flow, "king.wakingworld.treasury");
            }
            case "news" -> news(flow);
            default -> paragraphs(flow, "king.wakingworld." + t);
        }
        pages = PageLayout.paginate(flow.elements(), TEXT_H);
        page = 0;
        for (TopicButton b : buttons) b.selected = b.topic.equals(t);
    }

    private void paragraphs(PageLayout.Flow flow, String base) {
        for (int i = 1; i <= 6; i++) {
            String key = base + "." + i;
            if (!I18n.exists(key)) break;
            flow.paragraph(Component.translatable(key), INK);
        }
    }

    /** {@code type;kind;paces;direction;daysAgo|...} from the king's synced data into sentences. */
    private void news(PageLayout.Flow flow) {
        String raw = king.news();
        int told = 0;
        if (!raw.isEmpty()) {
            for (String item : raw.split("\\|")) {
                String[] f = item.split(";");
                if (f.length < 5) continue;
                Component kind = Component.translatable("entity.wakingworld.colossus." + f[1]);
                if (f[0].equals("rite")) kind = Component.translatable(f[1].equals("titan") ? "structure.wakingworld.titan_arena" : "structure.wakingworld.shrine_" + f[1]);
                int days;
                try {
                    days = Integer.parseInt(f[4]);
                } catch (NumberFormatException e) {
                    days = 0;
                }
                Component when = days <= 0 ? Component.translatable("king.wakingworld.news.today") : days == 1 ? Component.translatable("king.wakingworld.news.yesterday") : Component.translatable("king.wakingworld.news.days", days);
                String key = switch (f[0]) {
                    case "woken" -> "king.wakingworld.news.woken";
                    case "slain" -> "king.wakingworld.news.slain";
                    default -> "king.wakingworld.news.rite";
                };
                flow.paragraph(Component.translatable(key, kind, f[2], f[3], when), INK);
                told++;
            }
        }
        if (told == 0) flow.paragraph(Component.translatable("king.wakingworld.news.none"), INK);
        else flow.paragraph(Component.translatable("king.wakingworld.news.end"), FADED);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, W, H, 256, 256);
        // the king in his alcove, following the mouse
        // the king seated on the painted throne: the seated model's hips sit at the seat (y 112 in the texture)
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 9, top + 12, left + 77, top + 146, 46, 0.0625F, mouseX, mouseY, king);
        // the crest and the titles on the parchment
        g.blit(TEX, left + 90, top + 12, 90, 200, 12, 9, 256, 256);
        Component name = Component.literal(king.kingName()).withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, left + 106, top + 12, HEAD, false);
        g.drawString(font, Component.translatable("king.wakingworld.of", king.kingdomName()).withStyle(ChatFormatting.ITALIC), left + 106, top + 23, FADED, false);
        // his words
        if (!pages.isEmpty()) {
            PageLayout.Hover hover = new PageLayout.Hover();
            PageLayout.render(g, font, pages.get(Math.min(page, pages.size() - 1)), left + TEXT_X, top + TEXT_Y, TEXT_W, mouseX, mouseY, hover);
        }
        boolean many = pages.size() > 1;
        prev.visible = many && page > 0;
        next.visible = many && page < pages.size() - 1;
        if (many) {
            String ind = (page + 1) + " / " + pages.size();
            g.drawString(font, ind, left + TEXT_X + (TEXT_W - font.width(ind)) / 2, top + 139, FADED, false);
        }
    }

    private void turn(int dir) {
        int np = Math.max(0, Math.min(pages.size() - 1, page + dir));
        if (np != page) {
            page = np;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 262 || keyCode == 267) { // right, page down
            turn(1);
            return true;
        }
        if (keyCode == 263 || keyCode == 266) { // left, page up
            turn(-1);
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A scroll along the bottom: one thing to ask about. */
    private final class TopicButton extends AbstractWidget {
        final String topic;
        boolean selected;

        TopicButton(int x, int y, String topic) {
            super(x, y, 56, 14, Component.translatable("king.wakingworld.topic." + topic));
            this.topic = topic;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int v = selected ? 228 : isHoveredOrFocused() ? 214 : 200;
            g.blit(TEX, getX(), getY(), 0, v, 56, 14, 256, 256);
            Component msg = getMessage();
            int w = font.width(msg);
            g.drawString(font, msg, getX() + (56 - w) / 2, getY() + 3, selected ? 0xFFFFFF : isHoveredOrFocused() ? HEAD : INK, false);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F));
            if (topic.equals("farewell")) {
                onClose();
                return;
            }
            show(topic);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class Arrow extends AbstractWidget {
        private final boolean right;

        Arrow(int x, int y, boolean right) {
            super(x, y, 12, 8, Component.empty());
            this.right = right;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blit(TEX, getX(), getY(), right ? 74 : 60, isHoveredOrFocused() ? 210 : 200, 12, 8, 256, 256);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            turn(right ? 1 : -1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
