package me.lovkar.wakingworld.network;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.kingdom.TownsfolkEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

/**
 * The kingdom's own trading, without the villagers' counter: the server sends a townsfolk's offers
 * ({@link OpenTrade}) and the client opens the mod's trade screen; a purchase comes back as
 * {@link Buy} - the server checks the offer is still there and the price is in the inventory, takes
 * it, hands over the wares and lets the trader take note; {@link Leave} ends the conversation.
 */
public final class WakingNet {
    private WakingNet() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, path);
    }

    /** Server -> client: this trader's offers; open the screen. */
    public record OpenTrade(int entityId, MerchantOffers offers) implements CustomPacketPayload {
        public static final Type<OpenTrade> TYPE = new Type<>(id("open_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTrade> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenTrade::entityId, MerchantOffers.STREAM_CODEC, OpenTrade::offers, OpenTrade::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: buy offer {@code index} from trader {@code entityId}. */
    public record Buy(int entityId, int index) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(id("buy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Buy> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Buy::entityId, ByteBufCodecs.VAR_INT, Buy::index, Buy::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the screen closed. */
    public record Leave(int entityId) implements CustomPacketPayload {
        public static final Type<Leave> TYPE = new Type<>(id("leave_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Leave> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Leave::entityId, Leave::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the director's camera path; play it. */
    public record CineStart(java.util.List<me.lovkar.wakingworld.story.Cinematics.Key> keys, int fadeIn, int fadeOut) implements CustomPacketPayload {
        public static final Type<CineStart> TYPE = new Type<>(id("cine_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CineStart> CODEC = StreamCodec.composite(
                me.lovkar.wakingworld.story.Cinematics.Key.CODEC.apply(ByteBufCodecs.list()).cast(), CineStart::keys,
                ByteBufCodecs.VAR_INT, CineStart::fadeIn, ByteBufCodecs.VAR_INT, CineStart::fadeOut, CineStart::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: cut. */
    public record CineStop() implements CustomPacketPayload {
        public static final Type<CineStop> TYPE = new Type<>(id("cine_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CineStop> CODEC = StreamCodec.unit(new CineStop());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: a run begins - draw the world at this render distance until the cut. */
    public record CineSetup(int renderDistance) implements CustomPacketPayload {
        public static final Type<CineSetup> TYPE = new Type<>(id("cine_setup"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CineSetup> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CineSetup::renderDistance, CineSetup::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the stage is drawn, the camera rolls now. */
    public record CineReady() implements CustomPacketPayload {
        public static final Type<CineReady> TYPE = new Type<>(id("cine_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CineReady> CODEC = StreamCodec.unit(new CineReady());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: send me this letter's voice. */
    public record RequestVoice(java.util.UUID id) implements CustomPacketPayload {
        public static final Type<RequestVoice> TYPE = new Type<>(WakingNet.id("request_voice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestVoice> CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, RequestVoice::id, RequestVoice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server -> client: a letter's voice, one piece of it (deflated 16 kHz 16-bit mono PCM, up to
     * {@link #VOICE_CHUNK} bytes a piece), or its status when there is nothing to send yet or ever.
     */
    public record VoiceData(java.util.UUID id, int status, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<VoiceData> TYPE = new Type<>(WakingNet.id("voice_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VoiceData> CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, VoiceData::id,
                ByteBufCodecs.VAR_INT, VoiceData::status,
                ByteBufCodecs.VAR_INT, VoiceData::index,
                ByteBufCodecs.VAR_INT, VoiceData::total,
                ByteBufCodecs.byteArray(VOICE_CHUNK + 16), VoiceData::data, VoiceData::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static final int VOICE_CHUNK = 700_000;

    /** Server -> client: open the Dead Letter in this hand (0 main, 1 off) - the server has checked its voice is not still being made. */
    public record OpenLetter(int hand) implements CustomPacketPayload {
        public static final Type<OpenLetter> TYPE = new Type<>(WakingNet.id("open_letter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenLetter> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenLetter::hand, OpenLetter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void openLetter(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        PacketDistributor.sendToPlayer(player, new OpenLetter(hand == net.minecraft.world.InteractionHand.OFF_HAND ? 1 : 0));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestVoice.TYPE, RequestVoice.CODEC, WakingNet::handleRequestVoice);
        registrar.playToClient(VoiceData.TYPE, VoiceData.CODEC, (p, ctx) -> WakingWorld.hooks.voiceData(p.id(), p.status(), p.index(), p.total(), p.data()));
        registrar.playToClient(OpenLetter.TYPE, OpenLetter.CODEC, (p, ctx) -> {
            ItemStack stack = ctx.player().getItemInHand(p.hand() == 1 ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
            if (stack.getItem() instanceof me.lovkar.wakingworld.story.DeadLetterItem && me.lovkar.wakingworld.story.DeadLetterItem.isWritten(stack)) WakingWorld.hooks.openLetter(stack);
        });
        registrar.playToClient(OpenTrade.TYPE, OpenTrade.CODEC, WakingNet::handleOpen);
        registrar.playToServer(Buy.TYPE, Buy.CODEC, WakingNet::handleBuy);
        registrar.playToServer(Leave.TYPE, Leave.CODEC, WakingNet::handleLeave);
        registrar.playToClient(CineSetup.TYPE, CineSetup.CODEC, (p, ctx) -> WakingWorld.hooks.cineSetup(p.renderDistance()));
        registrar.playToClient(CineStart.TYPE, CineStart.CODEC, (p, ctx) -> WakingWorld.hooks.cineStart(p.keys(), p.fadeIn(), p.fadeOut()));
        registrar.playToClient(CineStop.TYPE, CineStop.CODEC, (p, ctx) -> WakingWorld.hooks.cineStop());
        registrar.playToServer(CineReady.TYPE, CineReady.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) me.lovkar.wakingworld.story.Cinematics.ready(player);
        });
    }

    public static void cineSetup(ServerPlayer player, int renderDistance) {
        PacketDistributor.sendToPlayer(player, new CineSetup(renderDistance));
    }

    public static void cineStart(ServerPlayer player, java.util.List<me.lovkar.wakingworld.story.Cinematics.Key> keys, int fadeIn, int fadeOut) {
        PacketDistributor.sendToPlayer(player, new CineStart(keys, fadeIn, fadeOut));
    }

    public static void cineStop(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CineStop());
    }

    /** Client: tells the server the stage is drawn. */
    public static void cineReady() {
        PacketDistributor.sendToServer(new CineReady());
    }

    /** Client: asks for a letter's voice. */
    public static void requestVoice(java.util.UUID id) {
        PacketDistributor.sendToServer(new RequestVoice(id));
    }

    /** Server: answers with the voice in pieces, or with its status. */
    private static void handleRequestVoice(RequestVoice payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        java.util.UUID id = payload.id();
        int status = me.lovkar.wakingworld.story.LetterVoices.status(id, player.server);
        byte[] packed = status == me.lovkar.wakingworld.story.LetterVoices.READY ? me.lovkar.wakingworld.story.LetterVoices.packed(id) : null;
        if (packed == null) {
            PacketDistributor.sendToPlayer(player, new VoiceData(id, status == me.lovkar.wakingworld.story.LetterVoices.READY ? me.lovkar.wakingworld.story.LetterVoices.NONE : status, 0, 0, new byte[0]));
            return;
        }
        int total = (packed.length + VOICE_CHUNK - 1) / VOICE_CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * VOICE_CHUNK, to = Math.min(packed.length, from + VOICE_CHUNK);
            PacketDistributor.sendToPlayer(player, new VoiceData(id, me.lovkar.wakingworld.story.LetterVoices.READY, i, total, java.util.Arrays.copyOfRange(packed, from, to)));
        }
    }

    /** Server: begins the trade - the trader turns to the player and the client gets the offers. */
    public static void openTrade(ServerPlayer player, TownsfolkEntity trader) {
        trader.setTradingPlayer(player);
        PacketDistributor.sendToPlayer(player, new OpenTrade(trader.getId(), trader.getOffers()));
    }

    private static void handleOpen(OpenTrade payload, IPayloadContext ctx) {
        Entity e = ctx.player().level().getEntity(payload.entityId());
        if (e instanceof TownsfolkEntity trader) WakingWorld.hooks.openTrade(trader, payload.offers());
    }

    private static TownsfolkEntity traderFor(IPayloadContext ctx, int entityId) {
        if (!(ctx.player() instanceof ServerPlayer player)) return null;
        Entity e = player.serverLevel().getEntity(entityId);
        if (!(e instanceof TownsfolkEntity trader) || !trader.isAlive() || trader.distanceToSqr(player) > 100) return null;
        return trader;
    }

    private static void handleBuy(Buy payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        TownsfolkEntity trader = traderFor(ctx, payload.entityId());
        if (trader == null) return;
        MerchantOffers offers = trader.getOffers();
        if (payload.index() < 0 || payload.index() >= offers.size()) return;
        MerchantOffer offer = offers.get(payload.index());
        if (offer.isOutOfStock()) return;
        ItemStack costA = offer.getCostA();
        Optional<ItemCost> costB = offer.getItemCostB();
        if (!has(player, costA) || (costB.isPresent() && !has(player, costB.get().itemStack()))) return;
        take(player, costA);
        costB.ifPresent(c -> take(player, c.itemStack()));
        ItemStack result = offer.assemble();
        if (!player.getInventory().add(result)) player.drop(result, false);
        offer.increaseUses();
        trader.notifyTrade(offer);
        player.awardStat(Stats.TRADED_WITH_VILLAGER);
        // the screen keeps its own copy of the offers: send the fresh ones so the stock and prices follow
        PacketDistributor.sendToPlayer(player, new OpenTrade(trader.getId(), trader.getOffers()));
    }

    private static void handleLeave(Leave payload, IPayloadContext ctx) {
        TownsfolkEntity trader = traderFor(ctx, payload.entityId());
        if (trader != null && trader.getTradingPlayer() == ctx.player()) trader.setTradingPlayer(null);
    }

    /** Whether the inventory holds at least the stack's count of the same item (components included). */
    public static boolean has(net.minecraft.world.entity.player.Player player, ItemStack want) {
        if (want.isEmpty()) return true;
        return count(player, want) >= want.getCount();
    }

    public static int count(net.minecraft.world.entity.player.Player player, ItemStack want) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (ItemStack.isSameItemSameComponents(s, want)) n += s.getCount();
        for (ItemStack s : player.getInventory().offhand) if (ItemStack.isSameItemSameComponents(s, want)) n += s.getCount();
        return n;
    }

    private static void take(ServerPlayer player, ItemStack want) {
        int left = want.getCount();
        for (java.util.List<ItemStack> list : java.util.List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (left <= 0) return;
                if (ItemStack.isSameItemSameComponents(s, want)) {
                    int n = Math.min(left, s.getCount());
                    s.shrink(n);
                    left -= n;
                }
            }
        }
    }
}
