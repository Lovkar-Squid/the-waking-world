package me.lovkar.wakingworld.story;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * A Dead Letter. Found blank in the ruins and the vaults; the moment a player carries one it is
 * written for the place it was found in (see {@link Letters}) - by Gemini when the server has a
 * key, by the templates otherwise or when the model is slow. The text lives in the vanilla
 * written-book component; where the letter points lives in the item's custom data, so the reading
 * screen can swing a compass needle towards it. Right-click to unfold it.
 */
public class DeadLetterItem extends Item {
    private static final String TARGET = "LetterTarget", PENDING = "LetterPending", VOICE = "LetterVoice", VOICE_V = "LetterVoiceV";
    /** What the voice reads, by version: 1 = everything on the pages, 2 = not the faded margin notes. */
    public static final int VOICE_VERSION = 2;

    public DeadLetterItem(Item.Properties properties) {
        super(properties);
    }

    public static boolean isWritten(ItemStack stack) {
        return stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    /** Where the letter points, or null. */
    public static Letters.Target targetOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TARGET)) return null;
        CompoundTag t = data.copyTag().getCompound(TARGET);
        return new Letters.Target(t.getString("Type"), t.getString("Kind"), new BlockPos(t.getInt("X"), 0, t.getInt("Z")));
    }

    /** The id of the letter's spoken voice (see {@link LetterVoices}), or null when it has none. */
    public static UUID voiceOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(VOICE)) return null;
        try {
            return UUID.fromString(data.copyTag().getString(VOICE));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Which words the letter's voice reads (see {@link #VOICE_VERSION}); 1 for voices made before the margin fell silent. */
    public static int voiceVersionOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(VOICE_V)) return 1;
        return data.copyTag().getInt(VOICE_V);
    }

    private static void apply(ItemStack stack, Letters.Written written, ServerPlayer player, ServerLevel server) {
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, written.book());
        UUID voice = LetterVoices.enabled() ? UUID.randomUUID() : null;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(PENDING);
            if (voice != null) {
                tag.putString(VOICE, voice.toString());
                tag.putInt(VOICE_V, VOICE_VERSION);
            }
            if (written.target() != null) {
                CompoundTag t = new CompoundTag();
                t.putString("Type", written.target().type());
                t.putString("Kind", written.target().kind());
                t.putInt("X", written.target().pos().getX());
                t.putInt("Z", written.target().pos().getZ());
                tag.put(TARGET, t);
            }
        });
        if (voice != null) LetterVoices.request(voice, written.book(), server.getServer());
        player.displayClientMessage(Component.translatable("item.wakingworld.dead_letter.written", Component.literal(written.book().title().raw()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY), true);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || isWritten(stack) || !(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(PENDING)) {
            // the model is writing this one - see whether it has finished
            if ((player.tickCount + slot) % 10 != 0) return;
            UUID id;
            try {
                id = UUID.fromString(data.copyTag().getString(PENDING));
            } catch (IllegalArgumentException e) {
                id = null;
            }
            GeminiLetters.Result r = id == null ? new GeminiLetters.Result(null, false) : GeminiLetters.poll(id);
            if (r == null) return; // still writing
            if (r.ok() && r.written() != null) {
                apply(stack, r.written(), player, server);
            } else {
                Letters.Facts facts = id == null ? null : GeminiLetters.facts(id);
                if (facts == null) facts = Letters.gather(server, player, player.blockPosition(), server.getRandom());
                apply(stack, Letters.compose(facts, server.getRandom()), player, server);
            }
            return;
        }
        if ((player.tickCount + slot) % 7 != 0) return; // spread the (one-off) structure searches out a little
        Letters.Facts facts = Letters.gather(server, player, player.blockPosition(), server.getRandom());
        if (GeminiLetters.enabled() && facts.pointsSomewhere()) {
            UUID id = GeminiLetters.request(facts);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PENDING, id.toString()));
            player.displayClientMessage(Component.translatable("item.wakingworld.dead_letter.fading_in").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        apply(stack, Letters.compose(facts, server.getRandom()), player, server);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isWritten(stack)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.wakingworld.dead_letter.blank").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // the letter opens when its voice is ready to read it - or at once when it has none, or none is coming
            UUID voice = voiceOf(stack);
            if (voice != null && LetterVoices.status(voice, sp.server) == LetterVoices.PENDING) {
                sp.displayClientMessage(Component.translatable("item.wakingworld.dead_letter.voice_pending").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                return InteractionResultHolder.fail(stack);
            }
            me.lovkar.wakingworld.network.WakingNet.openLetter(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            tooltip.add(Component.literal(content.title().raw()).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            tooltip.add(Component.translatable("book.byAuthor", content.author()).withStyle(ChatFormatting.GRAY));
            Letters.Target t = targetOf(stack);
            if (t != null) tooltip.add(Component.translatable("item.wakingworld.dead_letter.points", Component.translatable("letter.wakingworld.target." + (t.type().equals("vault") ? "vault" : t.kind()))).withStyle(ChatFormatting.DARK_GRAY));
            UUID voice = voiceOf(stack);
            if (voice != null) {
                String state = WakingWorld.hooks.voiceState(voice); // "ready", "pending" or null
                if ("pending".equals(state)) tooltip.add(Component.translatable("item.wakingworld.dead_letter.voice_pending").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                else if ("ready".equals(state)) tooltip.add(Component.translatable("item.wakingworld.dead_letter.voiced").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("item.wakingworld.dead_letter.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
