package me.lovkar.wakingworld.client.gui;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.kingdom.TownsfolkEntity;
import me.lovkar.wakingworld.network.WakingNet;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A townsfolk's stall. The trader stands in the nook at the left under the awning and follows the
 * mouse; the ledger at the right lists what they sell - what it costs, what you get, whether they
 * have any left - with a brass button to buy; the shelf below shows what the trade needs of yours
 * and a word about the trader. Buying asks the server ({@link WakingNet.Buy}); the server sends the
 * offers back afresh so stock and prices follow.
 */
public class TradeScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/trade.png");
    static final int W = 248, H = 200;
    static final int ROWS = 5, ROW_H = 20, LIST_X = 90, LIST_Y = 36, ROW_W = 150;
    static final int INK = 0x3A2A1C, HEAD = 0x6E2A18, FADED = 0x7A6A58, PALE = 0xE8DCC0, RED = 0xA02828, GREEN = 0x3C7A3C;

    private final TownsfolkEntity trader;
    private MerchantOffers offers;
    private int left, top, scroll;
    private final List<BuyButton> buttons = new ArrayList<>();
    private Arrow up, down;

    public TradeScreen(TownsfolkEntity trader, MerchantOffers offers) {
        super(trader.getDisplayName());
        this.trader = trader;
        this.offers = offers;
    }

    public TownsfolkEntity trader() {
        return trader;
    }

    /** The server's fresh offers after a purchase. */
    public void refresh(MerchantOffers offers) {
        this.offers = offers;
        scroll = Math.max(0, Math.min(scroll, offers.size() - ROWS));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        buttons.clear();
        for (int i = 0; i < ROWS; i++) {
            BuyButton b = new BuyButton(left + LIST_X + 106, top + LIST_Y + i * ROW_H + 3, i);
            buttons.add(b);
            addRenderableWidget(b);
        }
        up = new Arrow(left + LIST_X + 60, top + LIST_Y + ROWS * ROW_H + 2, true);
        down = new Arrow(left + LIST_X + 80, top + LIST_Y + ROWS * ROW_H + 2, false);
        addRenderableWidget(up);
        addRenderableWidget(down);
    }

    private MerchantOffer offerAt(int row) {
        int i = scroll + row;
        return i >= 0 && i < offers.size() ? offers.get(i) : null;
    }

    private boolean canAfford(MerchantOffer offer) {
        if (minecraft.player == null) return false;
        if (!WakingNet.has(minecraft.player, offer.getCostA())) return false;
        return offer.getItemCostB().map(c -> WakingNet.has(minecraft.player, c.itemStack())).orElse(true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, W, H, 256, 256);
        // the trader in the nook, following the mouse
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 9, top + 22, left + 77, top + 134, 40, 0.0625F, mouseX, mouseY, trader);
        // the ledger's head: who they are and what they do
        Component name = trader.getDisplayName().copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, left + 96, top + 12, HEAD, false);
        String kind = I18n.get("trade.wakingworld.title." + profession());
        g.drawString(font, Component.literal(kind).withStyle(ChatFormatting.ITALIC), left + 96, top + 22, FADED, false);
        // the rows
        for (int r = 0; r < ROWS; r++) {
            MerchantOffer offer = offerAt(r);
            BuyButton b = buttons.get(r);
            int y = top + LIST_Y + r * ROW_H;
            b.visible = offer != null;
            if (offer == null) continue;
            boolean hover = mouseX >= left + LIST_X && mouseX < left + LIST_X + ROW_W && mouseY >= y && mouseY < y + ROW_H;
            g.blit(TEX, left + LIST_X, y, 0, hover ? 230 : 208, ROW_W, ROW_H, 256, 256);
            ItemStack a = offer.getCostA();
            ItemStack bStack = offer.getItemCostB().map(c -> c.itemStack()).orElse(ItemStack.EMPTY);
            ItemStack result = offer.getResult();
            drawItem(g, a, left + LIST_X + 4, y + 2, mouseX, mouseY);
            if (!bStack.isEmpty()) drawItem(g, bStack, left + LIST_X + 25, y + 2, mouseX, mouseY);
            drawItem(g, result, left + LIST_X + 67, y + 2, mouseX, mouseY);
            // what is left of the stock, before the button
            int leftInStock = offer.getMaxUses() - offer.getUses();
            String stock = offer.isOutOfStock() ? "-" : "x" + leftInStock;
            g.drawString(font, stock, left + LIST_X + 103 - font.width(stock), y + 6, offer.isOutOfStock() ? RED : FADED, false);
            if (offer.isOutOfStock()) g.blit(TEX, left + LIST_X + 70, y + 6, 222, 208, 9, 9, 256, 256);
            b.active = !offer.isOutOfStock() && canAfford(offer);
            b.offer = offer;
        }
        // the shelf: the purse, and a word about the trader
        if (minecraft.player != null) {
            int emeralds = WakingNet.count(minecraft.player, new ItemStack(Items.EMERALD));
            g.blit(TEX, left + 14, top + 162, 210, 208, 9, 9, 256, 256);
            g.drawString(font, I18n.get("trade.wakingworld.purse", emeralds), left + 26, top + 163, PALE, true);
        }
        String about = I18n.get("trade.wakingworld.about." + profession());
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(about).withStyle(ChatFormatting.ITALIC), 210);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), left + 14, top + 174 + i * 9, 0xC8B890, true);
        // the list's own arrows
        up.visible = scroll > 0;
        down.visible = scroll + ROWS < offers.size();
        if (offers.isEmpty()) g.drawString(font, I18n.get("trade.wakingworld.nothing"), left + LIST_X + 6, top + LIST_Y + 6, FADED, false);
    }

    private void drawItem(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = stack;
    }

    private ItemStack hovered = ItemStack.EMPTY;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hovered = ItemStack.EMPTY;
        super.render(g, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private String profession() {
        return TownsfolkEntity.PROFESSIONS[Math.floorMod(trader.profession(), TownsfolkEntity.PROFESSIONS.length)];
    }

    private void buy(BuyButton b) {
        MerchantOffer offer = offerAt(b.row);
        if (offer == null || offer.isOutOfStock() || !canAfford(offer)) return;
        PacketDistributor.sendToServer(new WakingNet.Buy(trader.getId(), scroll + b.row));
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int before = scroll;
        scroll = Math.max(0, Math.min(Math.max(0, offers.size() - ROWS), scroll - (int) Math.signum(dy)));
        return before != scroll || super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new WakingNet.Leave(trader.getId()));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!trader.isAlive() || minecraft.player == null || trader.distanceToSqr(minecraft.player) > 64) onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The brass "Buy" button at the end of a row. */
    private final class BuyButton extends AbstractWidget {
        final int row;
        MerchantOffer offer;

        BuyButton(int x, int y, int row) {
            super(x, y, 40, 14, Component.translatable("trade.wakingworld.buy"));
            this.row = row;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int v = !active ? 240 : isHoveredOrFocused() ? 224 : 208;
            g.blit(TEX, getX(), getY(), 160, v, 40, 14, 256, 256);
            String label = I18n.get("trade.wakingworld.buy");
            g.drawString(font, label, getX() + (40 - font.width(label)) / 2, getY() + 3, active ? 0x2A1C0C : 0x505050, false);
            if (isHoveredOrFocused() && offer != null && !active && !offer.isOutOfStock()) {
                g.renderTooltip(font, Component.translatable("trade.wakingworld.cannot_afford"), mouseX, mouseY);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            buy(this);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** The little up/down arrows beside the ledger. */
    private final class Arrow extends AbstractWidget {
        private final boolean upward;

        Arrow(int x, int y, boolean upward) {
            super(x, y, 6, 8, Component.empty());
            this.upward = upward;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int c = isHoveredOrFocused() ? HEAD : FADED;
            for (int i = 0; i < 4; i++) {
                int w = upward ? i * 2 + 1 : 7 - i * 2;
                g.fill(getX() + 3 - w / 2, getY() + i * 2, getX() + 3 - w / 2 + w, getY() + i * 2 + 2, 0xFF000000 | c);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int before = scroll;
            scroll = Math.max(0, Math.min(Math.max(0, offers.size() - ROWS), scroll + (upward ? -1 : 1)));
            if (before != scroll) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
        }
    }
}
