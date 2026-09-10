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
import me.lovkar.wakingworld.supporter.SupporterCosmetics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class KingScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/king.png");
    static final int W = 248, H = 190;
    static final int TEXT_X = 96, TEXT_W = 134, TEXT_Y = 38, TEXT_H = 96;
    static final int INK = 0x3A2A1C, HEAD = 0x6E2A18, FADED = 0x7A6A58, GOLD = 0xC89A3C;
    private static final String[] TOPICS = new String[]{"kingdom", "charge", "sleepers", "letters", "vaults", "titan", "treasury", "news", "farewell"};
    private static final int BUTTON_W = 46;

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
            int x = left + 6 + (i % 5) * 48, y = top + 157 + (i / 5) * 16; // five to a row since "charge" joined the topics
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
                // a supporter is known here: the king greets a Waker, a friend of the colossi or the Titan's equal by name
                String tier = minecraft.player == null ? null : me.lovkar.wakingworld.supporter.SupporterCosmetics.tier(minecraft.player.getUUID());
                if (tier != null) flow.paragraph(Component.translatable("king.wakingworld.greeting." + tier), INK);
                else flow.paragraph(Component.translatable(king.generation() > 0 && Math.floorMod(king.getId(), 2) == 0 ? "king.wakingworld.greeting.new" : "king.wakingworld.greeting." + n), INK);
                flow.paragraph(Component.translatable("king.wakingworld.greeting.ask"), FADED);
            }
            case "treasury" -> {
                if (king.viewerPermitted()) flow.paragraph(Component.translatable("king.wakingworld.treasury.permitted"), INK);
                else paragraphs(flow, "king.wakingworld.treasury");
            }
            case "news" -> news(flow);
            case "charge" -> charge(flow);
            default -> paragraphs(flow, "king.wakingworld." + t);
        }
        pages = PageLayout.paginate(flow.elements(), TEXT_H);
        page = 0;
        for (TopicButton b : buttons) b.selected = b.topic.equals(t);
    }

    private FormattedCharSequence fit(Component var1, int var2) {
        return this.font.width(var1) <= var2 ? var1.getVisualOrderText() : (FormattedCharSequence)this.font.split(var1.copy().append("..."), var2).get(0);
    }

    private void paragraphs(PageLayout.Flow flow, String base) {
        for (int i = 1; i <= 6; i++) {
            String key = base + "." + i;
            if (!I18n.exists(key)) break;
            flow.paragraph(Component.translatable(key), INK);
        }
    }

    private void charge(PageLayout.Flow var1) {
        String var2 = this.king.charge();
        if (var2.isEmpty()) {
            var1.paragraph(Component.translatable("king.wakingworld.charge.none"), 3811868);
        } else {
            String[] var3 = var2.split(";", -1);
            String var4 = var3[0];
            if (var4.equals("mage")) {
                var1.heading(Component.translatable("king.wakingworld.charge.mage.title"), 7219736);
                this.paragraphs(var1, "king.wakingworld.charge.mage");
            } else {
                var1.heading(Component.translatable("king.wakingworld.charge.levy.title"), 7219736);
                int var5 = 0;
                int var6 = 0;

                try {
                    var5 = Integer.parseInt(var3[2]);
                    var6 = Integer.parseInt(var3[3]);
                } catch (RuntimeException var9) {
                }

                Item var7 = var3[1].isEmpty() ? null : (Item)BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(var3[1]));
                int var8 = Math.max(0, var5 - var6);
                if (var7 != null) {
                    var1.items(
                        Component.translatable("king.wakingworld.charge.levy.want", new Object[]{var8, var7.getDescription()}),
                        3811868,
                        new ItemStack(var7, Math.min(64, Math.max(1, var8)))
                    );
                }

                var1.paragraph(Component.translatable("king.wakingworld.charge.levy.progress", new Object[]{var6, var5}), 8022616);
                this.paragraphs(var1, "king.wakingworld.charge.levy");
            }
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
                if (f[0].equals("cataclysm")) kind = Component.translatable("cataclysm.wakingworld.name." + f[1]);
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
                    // one line per cataclysm: a king who says "an event occurred" is a noticeboard,
                    // and the whole point of him is that he is a person who lives here
                    case "cataclysm" -> "king.wakingworld.news.cataclysm." + f[1];
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

    private final class TopicButton extends AbstractWidget {
        final String topic;
        boolean selected;

        TopicButton(int nullx, int nullxx, String nullxxx) {
            super(nullx, nullxx, 46, 14, Component.translatable("king.wakingworld.topic." + nullxxx));
            this.topic = nullxxx;
        }

        protected void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
            int var5 = this.selected ? 228 : (this.isHoveredOrFocused() ? 214 : 200);
            byte var6 = 23;
            var1.blit(KingScreen.TEX, this.getX(), this.getY(), 0.0F, (float)var5, var6, 14, 256, 256);
            var1.blit(KingScreen.TEX, this.getX() + var6, this.getY(), (float)(56 - (46 - var6)), (float)var5, 46 - var6, 14, 256, 256);
            FormattedCharSequence var7 = KingScreen.this.fit(this.getMessage(), 42);
            int var8 = KingScreen.this.font.width(var7);
            var1.drawString(
                KingScreen.this.font,
                var7,
                this.getX() + (46 - var8) / 2,
                this.getY() + 3,
                this.selected ? 16777215 : (this.isHoveredOrFocused() ? 7219736 : 3811868),
                false
            );
        }

        public void onClick(double var1, double var3) {
            KingScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F));
            if (this.topic.equals("farewell")) {
                KingScreen.this.onClose();
            } else {
                KingScreen.this.show(this.topic);
            }
        }

        protected void updateWidgetNarration(NarrationElementOutput var1) {
            this.defaultButtonNarrationText(var1);
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
