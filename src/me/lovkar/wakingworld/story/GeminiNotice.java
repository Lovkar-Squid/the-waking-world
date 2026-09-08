package me.lovkar.wakingworld.story;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Saying, in the game, that the Gemini key has run out.
 *
 * <p>A key's free allowance is counted per model and per day, and on the bigger models it is twenty
 * requests - which one evening of walking into new country spends before you have read a letter.
 * When it goes, everything quietly falls back to the built-in names and letters, which is the right
 * behaviour and is <b>invisible</b>: the game carries on looking exactly as it should, and the only
 * sign is a line in a log nobody opens. So somebody who paid for a key and set it up correctly has
 * no way of learning that it stopped being used.</p>
 *
 * <p>So it says so once, to whoever could actually do something about it - operators - and then it
 * is quiet for half an hour, because the whole point is that this must not become the noise it is
 * warning about. Ordinary players on a server are told nothing: the state of somebody's API key is
 * not their business and there is nothing they can do.</p>
 */
public final class GeminiNotice {
    /** Long enough that a spent key does not nag; short enough that a fresh session says it again. */
    private static final long QUIET = 30 * 60_000L;

    private static long lastSaid = 0;

    private GeminiNotice() {
    }

    /** The day's allowance for {@code model} is gone. Told once; the fallback is already in hand. */
    public static void quotaSpent(String model) {
        if (System.currentTimeMillis() - lastSaid < QUIET) return;
        lastSaid = System.currentTimeMillis();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.hasPermissions(2)) continue;
                p.sendSystemMessage(Component.translatable("gemini.wakingworld.quota", model).withStyle(ChatFormatting.GRAY));
                p.sendSystemMessage(Component.translatable("gemini.wakingworld.quota.what").withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        WakingWorld.LOGGER.info("gemini: the key's free allowance for {} is spent; names and letters come from the built-in ones until it resets", model);
    }
}
