package me.lovkar.wakingworld.client.gui;

import java.util.ArrayList;
import java.util.List;
import me.lovkar.wakingworld.mage.DarkRites;
import me.lovkar.wakingworld.mage.MageBlocks;
import me.lovkar.wakingworld.mage.MageEntity;
import me.lovkar.wakingworld.network.WakingNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MageScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("wakingworld", "textures/gui/mage.png");
    static final int W = 248;
    static final int H = 190;
    static final int TEXT_X = 96;
    static final int TEXT_W = 134;
    static final int TEXT_Y = 38;
    static final int TEXT_H = 96;
    static final int INK = 3024434;
    static final int HEAD = 5978744;
    static final int FADED = 7234674;
    static final int VIOLET = 9067208;
    private static final String[] TOPICS = new String[]{"stone", "meteor", "volcano", "tornado", "quake", "bloodmoon", "himself", "farewell"};
    private static final String[] ORDERS = new String[]{"follow", "hold", "range", "stance", "gather", "mend", "light", "talk", "jar", "farewell"};
    private static final String[] CAGED = new String[]{"caged", "himself", "stone", "farewell"};
    private final MageEntity mage;
    private int left;
    private int top;
    private int stance;
    private final List<MageScreen.TopicButton> buttons = new ArrayList<>();
    private List<List<PageLayout.Element>> pages = List.of();
    private int page;
    private String topic = "greeting";
    private MageScreen.Arrow prev;
    private MageScreen.Arrow next;
    private static final String[] STANCES = new String[]{"meek", "defend", "guard"};
    private ItemStack hovered = ItemStack.EMPTY;

    public MageScreen(MageEntity var1) {
        super(var1.getName());
        this.mage = var1;
    }

    protected void init() {
        this.left = (this.width - 248) / 2;
        this.top = (this.height - 190) / 2;
        this.buttons.clear();
        this.stance = this.mage.stance();
        String[] var1 = this.topics();
        boolean var2 = var1.length > 8;
        int var3 = var2 ? 46 : 56;
        int var4 = var2 ? 48 : 58;
        int var5 = var2 ? 5 : 4;
        int var6 = var2 ? 6 : 8;

        for (int var7 = 0; var7 < var1.length; var7++) {
            int var8 = this.left + var6 + var7 % var5 * var4;
            int var9 = this.top + 157 + var7 / var5 * 16;
            MageScreen.TopicButton var10 = new MageScreen.TopicButton(var8, var9, var3, var1[var7]);
            this.buttons.add(var10);
            this.addRenderableWidget(var10);
        }

        this.prev = new MageScreen.Arrow(this.left + 96, this.top + 138, false);
        this.next = new MageScreen.Arrow(this.left + 96 + 134 - 12, this.top + 138, true);
        this.addRenderableWidget(this.prev);
        this.addRenderableWidget(this.next);
        this.show(this.topic);
    }

    private String[] topics() {
        return switch (this.mage.kept()) {
            case 1 -> ORDERS;
            case 2 -> CAGED;
            default -> TOPICS;
        };
    }

    private boolean isOrder(String var1) {
        return switch (var1) {
            case "follow", "hold", "range", "stance", "gather", "mend", "light", "jar" -> true;
            default -> false;
        };
    }

    private void send(String var1) {
        String var2 = var1;
        byte var3;
        if (var1.equals("stance")) {
            this.stance = (this.stance + 1) % STANCES.length;
            var2 = STANCES[this.stance];

            var3 = switch (this.stance) {
                case 0 -> 3;
                case 2 -> 5;
                default -> 4;
            };
        } else {
            var3 = switch (var1) {
                case "hold" -> 1;
                case "range" -> 2;
                case "gather" -> 8;
                case "mend" -> 6;
                case "light" -> 7;
                case "jar" -> 9;
                default -> 0;
            };
        }

        PacketDistributor.sendToServer(new WakingNet.MageOrder(this.mage.getId(), var3), new CustomPacketPayload[0]);
        if (!var1.equals("jar") && !var1.equals("gather") && !var1.equals("mend") && !var1.equals("light")) {
            this.show("ordered." + var2);
        } else {
            this.onClose();
        }
    }

    private static ItemStack stack(Item var0, int var1) {
        return new ItemStack(var0, var1);
    }

    private boolean carryingStone() {
        return this.minecraft != null
            && this.minecraft.player != null
            && this.minecraft.player.getInventory().contains(var0 -> var0.is((Item)MageBlocks.RITE_STONE_ITEM.get()));
    }

    private void show(String var1) {
        this.topic = var1;
        PageLayout.Flow var2 = new PageLayout.Flow(this.font, 134);
        switch (var1) {
            case "greeting":
                if (this.mage.kept() != 0) {
                    String var5 = this.mage.kept() == 2 ? "mage.wakingworld.caged" : "mage.wakingworld.kept";
                    var2.heading(Component.translatable(var5 + ".title"), 5978744);
                    this.paragraphs(var2, var5);
                } else {
                    var2.paragraph(Component.translatable("mage.wakingworld.greeting.1"), 3024434);
                    var2.paragraph(Component.translatable(this.carryingStone() ? "mage.wakingworld.greeting.have" : "mage.wakingworld.greeting.given"), 9067208);
                    var2.paragraph(Component.translatable("mage.wakingworld.greeting.ask"), 7234674);
                }
                break;
            case "stone":
                this.stone(var2);
                break;
            case "himself":
                this.paragraphs(var2, this.mage.kept() == 0 ? "mage.wakingworld.himself" : "mage.wakingworld.himself.kept");
                break;
            case "kept":
            case "caged":
                var2.heading(Component.translatable("mage.wakingworld." + var1 + ".title"), 5978744);
                this.paragraphs(var2, "mage.wakingworld." + var1);
                break;
            case "talk":
                var2.heading(Component.translatable("mage.wakingworld.kept.title"), 5978744);
                this.paragraphs(var2, "mage.wakingworld.kept");
                var2.heading(Component.translatable("mage.wakingworld.topic.himself"), 5978744);
                this.paragraphs(var2, "mage.wakingworld.himself.kept");
                break;
            default:
                if (var1.startsWith("ordered.")) {
                    this.paragraphs(var2, "mage.wakingworld." + var1);
                } else {
                    this.rite(var2, var1);
                }
        }

        this.pages = PageLayout.paginate(var2.elements(), 96);
        this.page = 0;

        for (MageScreen.TopicButton var6 : this.buttons) {
            var6.selected = var6.topic.equals(var1);
        }
    }

    private void stone(PageLayout.Flow var1) {
        var1.heading(Component.translatable("mage.wakingworld.stone.title"), 5978744);
        var1.items(Component.translatable("mage.wakingworld.stone.what"), 3024434, stack((Item)MageBlocks.RITE_STONE_ITEM.get(), 1));

        for (int var2 = 1; var2 <= 4; var2++) {
            var1.paragraph(
                Component.translatable(
                    "mage.wakingworld.stone.step" + var2, new Object[]{Component.literal(String.valueOf(var2)).withStyle(ChatFormatting.BOLD)}
                ),
                3024434
            );
        }

        var1.paragraph(Component.translatable("mage.wakingworld.stone.back"), 7234674);
        var1.paragraph(Component.translatable("mage.wakingworld.stone.warn"), 5978744);
    }

    private void rite(PageLayout.Flow var1, String var2) {
        DarkRites.Rite var3 = DarkRites.byId(var2);
        if (var3 != null) {
            var1.heading(Component.translatable(var3.nameKey()), 5978744);
            var1.swatch(var3.colour(), Component.translatable("mage.wakingworld.rite.mark"), 7234674);
            var1.paragraph(Component.translatable("mage.wakingworld.rite." + var2), 3024434);
            ItemStack[] var4 = new ItemStack[var3.costs().size()];

            for (int var5 = 0; var5 < var4.length; var5++) {
                var4[var5] = stack(var3.costs().get(var5).item(), var3.costs().get(var5).count());
            }

            var1.items(Component.translatable("mage.wakingworld.rite.price"), 5978744, var4);

            for (DarkRites.Cost var6 : var3.costs()) {
                var1.paragraph(
                    Component.literal("  ").append(Component.literal(var6.count() + "x ").withStyle(ChatFormatting.BOLD)).append(var6.item().getDescription()),
                    3024434
                );
            }

            var1.paragraph(Component.translatable("mage.wakingworld.rite.ember"), 7234674);
        }
    }

    private void paragraphs(PageLayout.Flow var1, String var2) {
        for (int var3 = 1; var3 <= 6; var3++) {
            String var4 = var2 + "." + var3;
            if (!I18n.exists(var4)) {
                break;
            }

            var1.paragraph(Component.translatable(var4), 3024434);
        }
    }

    public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
        super.renderBackground(var1, var2, var3, var4);
        var1.blit(TEX, this.left, this.top, 0.0F, 0.0F, 248, 190, 256, 256);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            var1, this.left + 9, this.top + 12, this.left + 77, this.top + 146, 50, 0.0625F, (float)var2, (float)var3, this.mage
        );
        var1.blit(TEX, this.left + 90, this.top + 12, 90.0F, 200.0F, 12, 9, 256, 256);
        byte var5 = 122;
        var1.drawString(
            this.font, this.fit(Component.literal(this.mage.mageName()).withStyle(ChatFormatting.BOLD), var5), this.left + 106, this.top + 12, 5978744, false
        );
        var1.drawString(
            this.font,
            this.fit(Component.translatable("mage.wakingworld.of").withStyle(ChatFormatting.ITALIC), var5),
            this.left + 106,
            this.top + 23,
            7234674,
            false
        );
        PageLayout.Hover var6 = new PageLayout.Hover();
        if (!this.pages.isEmpty()) {
            PageLayout.render(var1, this.font, this.pages.get(Math.min(this.page, this.pages.size() - 1)), this.left + 96, this.top + 38, 134, var2, var3, var6);
        }

        boolean var7 = this.pages.size() > 1;
        this.prev.visible = var7 && this.page > 0;
        this.next.visible = var7 && this.page < this.pages.size() - 1;
        if (var7) {
            String var8 = this.page + 1 + " / " + this.pages.size();
            var1.drawString(this.font, var8, this.left + 96 + (134 - this.font.width(var8)) / 2, this.top + 139, 7234674, false);
        }

        this.hovered = var6.stack;
    }

    private FormattedCharSequence fit(Component var1, int var2) {
        return this.font.width(var1) <= var2 ? var1.getVisualOrderText() : (FormattedCharSequence)this.font.split(var1.copy().append("..."), var2).get(0);
    }

    public void render(GuiGraphics var1, int var2, int var3, float var4) {
        super.render(var1, var2, var3, var4);
        if (!this.hovered.isEmpty()) {
            var1.renderTooltip(this.font, this.hovered, var2, var3);
        }
    }

    private void turn(int var1) {
        int var2 = Math.max(0, Math.min(this.pages.size() - 1, this.page + var1));
        if (var2 != this.page) {
            this.page = var2;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    public boolean keyPressed(int var1, int var2, int var3) {
        if (var1 == 262 || var1 == 267) {
            this.turn(1);
            return true;
        } else if (var1 == 263 || var1 == 266) {
            this.turn(-1);
            return true;
        } else if (this.minecraft.options.keyInventory.matches(var1, var2)) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(var1, var2, var3);
        }
    }

    public boolean isPauseScreen() {
        return false;
    }

    private final class Arrow extends AbstractWidget {
        private final boolean right;

        Arrow(int nullx, int nullxx, boolean nullxxx) {
            super(nullx, nullxx, 12, 8, Component.empty());
            this.right = nullxxx;
        }

        protected void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
            var1.blit(MageScreen.TEX, this.getX(), this.getY(), this.right ? 74.0F : 60.0F, this.isHoveredOrFocused() ? 210.0F : 200.0F, 12, 8, 256, 256);
        }

        public void onClick(double var1, double var3) {
            MageScreen.this.turn(this.right ? 1 : -1);
        }

        protected void updateWidgetNarration(NarrationElementOutput var1) {
            this.defaultButtonNarrationText(var1);
        }
    }

    private final class TopicButton extends AbstractWidget {
        final String topic;
        final int bw;
        boolean selected;

        TopicButton(int nullx, int nullxx, int nullxxx, String nullxxxx) {
            super(nullx, nullxx, nullxxx, 14, Component.translatable("mage.wakingworld.topic." + nullxxxx));
            this.topic = nullxxxx;
            this.bw = nullxxx;
        }

        private Component label() {
            return (Component)(!this.topic.equals("stance")
                ? this.getMessage()
                : Component.translatable("mage.wakingworld.stance." + MageScreen.STANCES[MageScreen.this.stance]));
        }

        protected void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
            int var5 = this.selected ? 228 : (this.isHoveredOrFocused() ? 214 : 200);
            int var6 = this.bw / 2;
            var1.blit(MageScreen.TEX, this.getX(), this.getY(), 0.0F, (float)var5, var6, 14, 256, 256);
            var1.blit(MageScreen.TEX, this.getX() + var6, this.getY(), (float)(56 - (this.bw - var6)), (float)var5, this.bw - var6, 14, 256, 256);
            FormattedCharSequence var7 = MageScreen.this.fit(this.label(), this.bw - 4);
            int var8 = MageScreen.this.font.width(var7);
            var1.drawString(
                MageScreen.this.font,
                var7,
                this.getX() + (this.bw - var8) / 2,
                this.getY() + 3,
                this.selected ? 16777215 : (this.isHoveredOrFocused() ? 5978744 : 3024434),
                false
            );
        }

        public void onClick(double var1, double var3) {
            MageScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F));
            if (this.topic.equals("farewell")) {
                MageScreen.this.onClose();
            } else if (MageScreen.this.isOrder(this.topic)) {
                MageScreen.this.send(this.topic);
            } else {
                MageScreen.this.show(this.topic);
            }
        }

        protected void updateWidgetNarration(NarrationElementOutput var1) {
            this.defaultButtonNarrationText(var1);
        }
    }
}
