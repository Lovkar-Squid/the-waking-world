package me.lovkar.wakingworld.client.gui;

import com.mojang.math.Axis;
import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.client.LetterReading;
import me.lovkar.wakingworld.client.LetterVoicePlayer;
import me.lovkar.wakingworld.story.DeadLetterItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * A Dead Letter unfolded: a torn, water-stained sheet with the writer's title and hand, the
 * letter laid out over as many leaves as it needs, the wax seal - and, when the letter points
 * somewhere, a compass rose whose needle swings towards that place from wherever the reader
 * stands, with the distance in paces. The letter's text is the vanilla written-book content on
 * the item; the target is in the item's custom data (see {@link DeadLetterItem}).
 */
public class LetterScreen extends Screen {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/gui/letter.png");
    private static final int PAPER_W = 176, PAPER_H = 232, TEXT_X = 14, TEXT_W = 148, TEXT_Y = 14, TEXT_H = 136;
    private static final int INK = 0x3B2A1A, TITLE = 0x6B1F1F, FADED = 0x7A6A58;

    private static final int KEY_RIGHT = 262, KEY_LEFT = 263;
    private final ItemStack stack;
    private final List<List<PageLayout.Element>> pages = new ArrayList<>();
    private final List<Arrow> arrows = new ArrayList<>();
    private int page;
    private int left, top;
    private me.lovkar.wakingworld.story.Letters.Target target;
    private String title = "", author = "";
    private java.util.UUID voice;
    private Speaker speaker;
    /** Lines the voice does not read (the "by" line), by identity. */
    private final java.util.Set<PageLayout.Element> unspoken = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private LetterReading reading;
    private java.util.concurrent.Future<LetterReading> aligning;
    private int readPage = -1;

    public LetterScreen(ItemStack stack) {
        super(Component.translatable("item.wakingworld.dead_letter"));
        this.stack = stack;
    }

    @Override
    protected void init() {
        left = (width - PAPER_W) / 2;
        top = (height - PAPER_H) / 2;
        boolean first = pages.isEmpty();
        if (first) build();
        arrows.clear();
        if (pages.size() > 1) {
            arrows.add(addRenderableWidget(new Arrow(false, left + 58, top + 156)));
            arrows.add(addRenderableWidget(new Arrow(true, left + 106, top + 156)));
        }
        voice = DeadLetterItem.voiceOf(stack);
        if (voice != null) {
            speaker = addRenderableWidget(new Speaker(left + PAPER_W - 42 - 20, top + PAPER_H - 46 + 8));
            if (first) {
                // the letter reads itself when it is opened, if the player wants that; else we only ask whether it could
                if (WakingConfig.readLettersAloud()) LetterVoicePlayer.play(voice);
                else LetterVoicePlayer.ask(voice);
            }
        }
    }

    private void build() {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        target = DeadLetterItem.targetOf(stack);
        PageLayout.Flow f = new PageLayout.Flow(font, TEXT_W);
        if (content == null) {
            f.paragraph(Component.translatable("item.wakingworld.dead_letter.blank"), FADED);
        } else {
            title = content.title().raw();
            author = content.author();
            f.heading(Component.literal(title), TITLE);
            int from = f.elements().size();
            f.paragraph(Component.translatable("book.byAuthor", author).withStyle(ChatFormatting.ITALIC), FADED);
            unspoken.addAll(f.elements().subList(from, f.elements().size()));
            for (Filterable<Component> chapter : content.pages()) {
                f.paragraph(chapter.raw(), INK);
                f.gap(3);
            }
        }
        pages.addAll(PageLayout.paginate(f.elements(), TEXT_H));
        if (pages.isEmpty()) pages.add(new ArrayList<>());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, PAPER_W, PAPER_H, 256, 256);
        follow(g);
        PageLayout.Hover hover = new PageLayout.Hover();
        PageLayout.render(g, font, pages.get(page), left + TEXT_X, top + TEXT_Y, TEXT_W, mouseX, mouseY, hover);
        // the seal
        g.blit(TEX, left + PAPER_W - 42, top + PAPER_H - 46, 200, 0, 26, 26, 256, 256);
        // the compass, when the letter points somewhere the reader can walk to
        if (target != null && target.pos() != null) compass(g, left + 14, top + 176, mouseX, mouseY);
        if (pages.size() > 1) {
            String n = (page + 1) + " / " + pages.size();
            g.drawString(font, n, left + PAPER_W / 2 - font.width(n) / 2, top + 156, FADED, false);
        }
        for (Arrow a : arrows) a.render(g, mouseX, mouseY, partialTick);
        if (speaker != null) {
            speaker.render(g, mouseX, mouseY, partialTick);
            if (speaker.making()) making(g);
        }
    }

    /**
     * Marks the word the voice is reading - a wash of amber under it - and turns the leaf when the
     * reading goes on to the next one (once; the reader may leaf back while it goes on).
     */
    private void follow(GuiGraphics g) {
        if (voice == null) return;
        if (reading == null) {
            // the words are laid against the sound once, off the render thread
            if (aligning == null) {
                byte[] pcm = LetterVoicePlayer.pcm(voice);
                if (pcm == null) return;
                List<LetterReading.Word> words = words();
                aligning = java.util.concurrent.CompletableFuture.supplyAsync(() -> LetterReading.align(pcm, words));
                return;
            }
            if (!aligning.isDone()) return;
            try {
                reading = aligning.get();
            } catch (Exception e) {
                WakingWorld.LOGGER.warn("letter voice: could not lay the words against the sound: {}", e.toString());
                reading = LetterReading.align(new byte[0], List.of());
            }
        }
        double t = LetterVoicePlayer.position(voice);
        if (t < 0) return;
        int i = reading.wordAt(t);
        if (i < 0) return;
        LetterReading.Word w = reading.words().get(i);
        if (w.page() != readPage) {
            readPage = w.page();
            if (page != readPage) flip(readPage - page);
        }
        if (w.page() != page) return;
        int x = left + TEXT_X, y = top + TEXT_Y + w.y();
        g.fill(x + w.x0() - 1, y - 1, x + w.x1() + 1, y + PageLayout.LINE, 0x66D9A441);
    }

    /**
     * The letter's words as the pages draw them, in reading order - the title and the letter, not the
     * "by" line, the margin's coordinates or (for voices made since) the faded notes in grey.
     */
    private List<LetterReading.Word> words() {
        List<LetterReading.Word> out = new ArrayList<>();
        boolean skipFaded = DeadLetterItem.voiceVersionOf(stack) >= 2;
        for (int p = 0; p < pages.size(); p++) {
            int y = 0;
            for (PageLayout.Element e : pages.get(p)) {
                net.minecraft.util.FormattedCharSequence seq = e instanceof PageLayout.Heading h ? h.line() : e instanceof PageLayout.TextLine t ? t.line() : null;
                if (seq != null && !unspoken.contains(e)) {
                    List<Integer> cps = new ArrayList<>();
                    List<net.minecraft.network.chat.Style> styles = new ArrayList<>();
                    List<Boolean> silent = new ArrayList<>();
                    seq.accept((idx, style, cp) -> {
                        cps.add(cp);
                        styles.add(style);
                        silent.add(skipFaded && me.lovkar.wakingworld.story.LetterVoices.faded(style));
                        return true;
                    });
                    StringBuilder all = new StringBuilder();
                    for (int cp : cps) all.appendCodePoint(cp);
                    if (!all.toString().trim().matches(me.lovkar.wakingworld.story.LetterVoices.MARGIN)) {
                        int i = 0, n = cps.size();
                        while (i < n) {
                            while (i < n && (Character.isWhitespace(cps.get(i)) || silent.get(i))) i++;
                            int j = i;
                            while (j < n && !Character.isWhitespace(cps.get(j)) && !silent.get(j)) j++;
                            if (j > i) {
                                StringBuilder word = new StringBuilder();
                                for (int k = i; k < j; k++) word.appendCodePoint(cps.get(k));
                                out.add(new LetterReading.Word(p, width(cps, styles, i), width(cps, styles, j), y, word.toString()));
                            }
                            i = j;
                        }
                    }
                }
                y += e.height();
            }
        }
        return out;
    }

    /** The drawn width of the first {@code to} characters of a line, styles and all. */
    private int width(List<Integer> cps, List<net.minecraft.network.chat.Style> styles, int to) {
        if (to == 0) return 0;
        List<net.minecraft.util.FormattedCharSequence> parts = new ArrayList<>(to);
        for (int i = 0; i < to; i++) parts.add(net.minecraft.util.FormattedCharSequence.codepoint(cps.get(i), styles.get(i)));
        return font.width(net.minecraft.util.FormattedCharSequence.composite(parts));
    }

    /** A line under the page while the letter's voice is still being made, its dots walking. */
    private void making(GuiGraphics g) {
        String s = Component.translatable("letter.wakingworld.voice.making").getString();
        String base = s.replaceAll("\\.+$", "");
        int dots = (int) ((System.currentTimeMillis() / 400) % 3) + 1;
        int x = left + PAPER_W / 2 - font.width(base + "...") / 2;
        int y = pages.size() > 1 ? top + 166 : top + 158;
        g.drawString(font, base + ".".repeat(dots), x, y, FADED, false);
    }

    private void compass(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        g.blit(TEX, x, y, 200, 30, 34, 34, 256, 256);
        if (player == null) return;
        double dx = target.pos().getX() + 0.5 - player.getX(), dz = target.pos().getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        // the needle: which way the place lies relative to where the reader looks
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        float rel = (float) (bearing - player.getYRot());
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 17, y + 17, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rel));
        g.blit(TEX, -1, -7, 240, 30, 3, 14, 256, 256);
        pose.popPose();
        // the distance and where it is
        String paces = dist < 40 ? "here" : String.format("%,d paces", Math.round(dist / 10) * 10);
        g.drawString(font, paces, x + 40, y + 8, INK, false);
        Component what = Component.translatable("letter.wakingworld.target." + (target.type().equals("vault") ? "vault" : target.kind()));
        g.drawString(font, what, x + 40, y + 19, FADED, false);
        if (mouseX >= x && mouseX < x + 34 && mouseY >= y && mouseY < y + 34) {
            g.renderTooltip(font, Component.translatable("letter.wakingworld.compass", target.pos().getX(), target.pos().getZ()), mouseX, mouseY);
        }
    }

    private void flip(int dir) {
        int n = page + dir;
        if (n < 0 || n >= pages.size()) return;
        page = n;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_LEFT) { flip(-1); return true; }
        if (keyCode == KEY_RIGHT) { flip(1); return true; }
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

    @Override
    public void removed() {
        super.removed();
        // the letter folded, the voice stops with it
        if (voice != null && LetterVoicePlayer.isPlaying(voice)) LetterVoicePlayer.stop();
    }

    /**
     * The speaker on the seal's left: the letter's voice. A click reads, pauses and goes on; a right
     * click stops. While the voice is still being made the sound pulses and the letter says so below
     * the page; a letter with no voice shows the horn struck through.
     */
    private class Speaker extends AbstractWidget {
        Speaker(int x, int y) {
            super(x, y, 14, 12, Component.empty());
        }

        private LetterVoicePlayer.State state() {
            return LetterVoicePlayer.state(voice);
        }

        /** True while the server is still making this voice (or we have not heard yet). */
        boolean making() {
            if (LetterVoicePlayer.isPlaying(voice)) return false;
            var st = state();
            return st == LetterVoicePlayer.State.PENDING || st == LetterVoicePlayer.State.LOADING;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean loaded = LetterVoicePlayer.isPlaying(voice);
            boolean paused = LetterVoicePlayer.isPaused(voice);
            var st = state();
            boolean none = !loaded && st == LetterVoicePlayer.State.NONE;
            int color = loaded && !paused ? TITLE : none ? FADED : INK;
            int a = 0xFF000000 | color;
            int x = getX(), y = getY();
            // the horn: a box and a widening cone
            g.fill(x, y + 4, x + 3, y + 8, a);
            g.fill(x + 3, y + 3, x + 5, y + 9, a);
            g.fill(x + 5, y + 2, x + 6, y + 10, a);
            g.fill(x + 6, y + 1, x + 7, y + 11, a);
            if (paused) {
                // held: two bars where the sound was
                g.fill(x + 9, y + 3, x + 10, y + 9, a);
                g.fill(x + 12, y + 3, x + 13, y + 9, a);
            } else if (none) {
                g.fill(x + 9, y + 5, x + 13, y + 6, 0x99000000 | color); // struck through: no voice
                g.fill(x + 9, y + 6, x + 13, y + 7, 0x99000000 | color);
            } else {
                // the sound: two arcs - full while it plays, faint when it waits, walking out while it is made
                int phase = making() ? (int) ((System.currentTimeMillis() / 400) % 3) : 2;
                int w = loaded ? a : (0x99000000 | color);
                if (phase >= 1) {
                    g.fill(x + 9, y + 4, x + 10, y + 8, w);
                    g.fill(x + 8, y + 3, x + 9, y + 4, w);
                    g.fill(x + 8, y + 8, x + 9, y + 9, w);
                }
                if (phase >= 2) {
                    g.fill(x + 12, y + 3, x + 13, y + 9, w);
                    g.fill(x + 11, y + 2, x + 12, y + 3, w);
                    g.fill(x + 11, y + 9, x + 12, y + 10, w);
                }
            }
            if (isHovered()) {
                List<Component> lines = new ArrayList<>();
                if (loaded) {
                    lines.add(Component.translatable(paused ? "letter.wakingworld.voice.resume" : "letter.wakingworld.voice.pause"));
                    lines.add(Component.translatable("letter.wakingworld.voice.stop_hint").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.translatable(switch (st) {
                        case NONE -> "letter.wakingworld.voice.none";
                        case PENDING, LOADING -> "letter.wakingworld.voice.pending";
                        default -> "letter.wakingworld.voice.play";
                    }));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && active && visible && isMouseOver(mouseX, mouseY)) {
                if (LetterVoicePlayer.isPlaying(voice)) {
                    LetterVoicePlayer.stop();
                    playDownSound(Minecraft.getInstance().getSoundManager());
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (LetterVoicePlayer.isPlaying(voice)) {
                if (LetterVoicePlayer.isPaused(voice)) LetterVoicePlayer.resume();
                else LetterVoicePlayer.pause();
            } else if (state() != LetterVoicePlayer.State.NONE) {
                LetterVoicePlayer.play(voice);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
        }
    }

    private class Arrow extends AbstractWidget {
        final boolean right;

        Arrow(boolean right, int x, int y) {
            super(x, y, 12, 8, Component.empty());
            this.right = right;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blit(TEX, getX(), getY(), right ? 214 : 200, isHovered() ? 80 : 70, 12, 8, 256, 256);
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
