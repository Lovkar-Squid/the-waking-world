package me.lovkar.wakingworld.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.body.ColossusStyle;
import me.lovkar.wakingworld.network.WakingNet;
import me.lovkar.wakingworld.supporter.AuraStyle;
import me.lovkar.wakingworld.supporter.SupporterList;
import me.lovkar.wakingworld.supporter.SupporterTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The in-game side of the supporter perks, all under one client command:
 * <ul>
 * <li>{@code /wwpatreon} - link (or re-link) this Minecraft account to a Patreon: opens the supporter
 * service's link page in the browser. Before it does, the client tells Mojang's session server it is
 * "joining" a one-off server id - the same handshake every multiplayer login does - and the service
 * asks Mojang whether this account really did. So a Patreon can only ever be linked to the account
 * that is actually logged in here, not to a name typed into a URL.</li>
 * <li>{@code /wwpatreon aura <name>}, {@code /wwpatreon colossus <name>} - change a look right here,
 * with the same proof of ownership; the service checks the choice against the tier and answers, the
 * change shows at once, and the server tells every other client to look again.</li>
 * <li>{@code /wwpatreon credits on|off} - put one's own name in (or take it out of) the Hall of Wakers in the Almanac.</li>
 * <li>{@code /wwpatreon status} - what is on file; {@code /wwpatreon refresh} - fetch the list now.</li>
 * </ul>
 * Tab completion only ever offers what the tier has unlocked - and the service would refuse anything
 * else anyway.
 */
public final class SupporterLink {
    private SupporterLink() {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("wwpatreon")
                .executes(ctx -> {
                    start(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("refresh").executes(ctx -> {
                    // fetch the list again right now (it refreshes on its own every five minutes and on every login)
                    SupporterList.refreshAsync();
                    ctx.getSource().sendSuccess(() -> Component.literal("Refreshing the supporter list...").withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    change(ctx.getSource(), null, null);
                    return 1;
                }))
                .then(Commands.literal("credits")
                        .then(Commands.literal("on").executes(ctx -> {
                            change(ctx.getSource(), "credits", "true");
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            change(ctx.getSource(), "credits", "false");
                            return 1;
                        })))
                .then(Commands.literal("aura").then(Commands.argument("style", StringArgumentType.word())
                        .suggests((ctx, b) -> suggestUnlocked(b, true))
                        .executes(ctx -> {
                            change(ctx.getSource(), "aura", StringArgumentType.getString(ctx, "style"));
                            return 1;
                        })))
                .then(Commands.literal("colossus").then(Commands.argument("style", StringArgumentType.word())
                        .suggests((ctx, b) -> suggestUnlocked(b, false))
                        .executes(ctx -> {
                            change(ctx.getSource(), "colossus", StringArgumentType.getString(ctx, "style"));
                            return 1;
                        }))));
    }

    /** The ids the local player's tier unlocks (nothing at all for a non-supporter). */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestUnlocked(SuggestionsBuilder b, boolean auras) {
        LocalPlayer p = Minecraft.getInstance().player;
        SupporterList.Entry e = p == null ? null : SupporterList.entry(p.getUUID());
        List<String> ids = new ArrayList<>();
        if (e != null) {
            int rank = SupporterTiers.rank(e.tier());
            if (auras) {
                for (AuraStyle s : AuraStyle.values()) if (s.rank <= rank) ids.add(s.id);
            } else {
                for (ColossusStyle s : ColossusStyle.values()) if (s.rank <= rank) ids.add(s.id);
            }
        }
        return SharedSuggestionProvider.suggest(ids, b);
    }

    /** One-off proof for the service: "join" a random server id at Mojang and hand the id over. Null when Mojang would not have it. */
    private static String joinMojang(Minecraft mc) {
        try {
            User user = mc.getUser();
            byte[] b = new byte[16];
            RANDOM.nextBytes(b);
            StringBuilder sb = new StringBuilder(32);
            for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
            String serverId = sb.toString();
            mc.getMinecraftSessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
            return serverId;
        } catch (Exception e) {
            WakingWorld.LOGGER.debug("supporters: session join failed: {}", e.toString());
            return null;
        }
    }

    private static void start(CommandSourceStack src) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        src.sendSuccess(() -> Component.literal("Checking your Minecraft account with Mojang...").withStyle(ChatFormatting.GRAY), false);
        SupporterList.POOL.execute(() -> {
            String sid = joinMojang(mc);
            String url = SupporterList.BASE_URL + "/link/start?uuid=" + id + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + (sid != null ? "&sid=" + sid : "");
            boolean verified = sid != null;
            mc.execute(() -> open(src, url, verified));
        });
    }

    private static void open(CommandSourceStack src, String url, boolean verified) {
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (Exception ignore) {
        }
        Component link = Component.literal("open the link page").withStyle(s -> s
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
        if (!verified) {
            src.sendSuccess(() -> Component.literal("Mojang could not confirm your login (offline mode?) - the page may refuse the link. ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("If nothing opened, ").withStyle(ChatFormatting.GRAY)).append(link), false);
            return;
        }
        src.sendSuccess(() -> Component.literal("Opening Patreon in your browser to link your account. If nothing opened, ").withStyle(ChatFormatting.GRAY).append(link), false);
    }

    /**
     * Ask the service about, or to change, one's own perks: {@code field} is "aura" or "colossus" (an id),
     * "credits" ("true"/"false" - the name in the Hall of Wakers), or null to only read the status back.
     */
    private static void change(CommandSourceStack src, String field, String value) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        String wanted = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (field != null && !field.equals("credits")) {
            boolean known = field.equals("aura") ? AuraStyle.byId(wanted) != null : ColossusStyle.byId(wanted) != null;
            if (!known) {
                src.sendFailure(Component.literal("No such " + field + " style: " + wanted));
                return;
            }
        }
        src.sendSuccess(() -> Component.literal("Asking the supporter service...").withStyle(ChatFormatting.GRAY), false);
        SupporterList.POOL.execute(() -> {
            String sid = joinMojang(mc);
            JsonObject body = new JsonObject();
            body.addProperty("uuid", id.toString());
            body.addProperty("name", name);
            if (sid != null) body.addProperty("sid", sid);
            if ("credits".equals(field)) body.addProperty("credits", "true".equals(wanted));
            else if (field != null) body.addProperty(field, wanted);
            Component result;
            try {
                SupporterList.Response res = SupporterList.post("/api/me/cosmetics", body.toString());
                JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
                if (res.status() == 200 && j.has("ok") && j.get("ok").getAsBoolean()) {
                    String tier = j.get("tier").getAsString();
                    String aura = j.has("aura") && !j.get("aura").isJsonNull() ? j.get("aura").getAsString() : null;
                    String colossus = j.has("colossus") && !j.get("colossus").isJsonNull() ? j.get("colossus").getAsString() : null;
                    boolean credits = j.has("credits") && j.get("credits").getAsBoolean();
                    SupporterList.updateOwn(id, new SupporterList.Entry(tier, aura, colossus));
                    if (field == null) {
                        result = Component.literal("Tier ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(tier.toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("  aura ").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.valueOf(aura)).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("  colossi ").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.valueOf(colossus)).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("  credits ").withStyle(ChatFormatting.GRAY)).append(Component.literal(credits ? "on" : "off").withStyle(ChatFormatting.AQUA));
                    } else if (field.equals("credits")) {
                        result = Component.literal(credits ? "Your name now stands in the Hall of Wakers (in the Almanac, for everyone)."
                                : "Your name is out of the Hall of Wakers.").withStyle(ChatFormatting.GREEN);
                        SupporterList.refreshAsync(); // the Almanac reads the credits with the list
                    } else {
                        result = Component.literal(field.equals("aura") ? "Aura set to " : "Your colossi will rise as: ").withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(field.equals("aura") ? String.valueOf(aura) : String.valueOf(colossus)).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(field.equals("aura") ? "." : " (from the next one you wake).").withStyle(ChatFormatting.GREEN));
                        mc.execute(WakingNet::cosmeticsChanged); // the server tells everyone else to look again
                    }
                } else {
                    String err = j.has("error") ? j.get("error").getAsString() : "HTTP " + res.status();
                    result = Component.literal(err).withStyle(ChatFormatting.RED);
                }
            } catch (Exception e) {
                WakingWorld.LOGGER.debug("supporters: change failed: {}", e.toString());
                result = Component.literal("Could not reach the supporter service - try again in a moment.").withStyle(ChatFormatting.RED);
            }
            Component shown = result;
            mc.execute(() -> src.sendSuccess(() -> shown, false));
        });
    }
}
