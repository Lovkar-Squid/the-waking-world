package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.ClientHooks;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Client-only registration. Loaded only when FMLEnvironment says we are a client. */
public final class WakingWorldClient {
    private WakingWorldClient() {
    }

    public static void init(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        // the Config button in the mod list: NeoForge's generated screen over both config files
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (c, parent) -> new net.neoforged.neoforge.client.gui.ConfigurationScreen(c, parent));
        WakingWorld.hooks = new ClientHooks() {
            @Override
            public void shakeAt(Vec3 at, float strength, double range) {
                ScreenShake.impact(at, strength, range);
            }

            @Override
            public void shake(float strength) {
                ScreenShake.add(strength);
            }

            @Override
            public void wave(Vec3 from, double speed, double maxRadius, float strength) {
                ScreenShake.wave(from, speed, maxRadius, strength);
            }

            @Override
            public void openAlmanac() {
                net.minecraft.client.Minecraft.getInstance().setScreen(new me.lovkar.wakingworld.client.gui.AlmanacScreen());
            }

            @Override
            public void openLetter(net.minecraft.world.item.ItemStack stack) {
                net.minecraft.client.Minecraft.getInstance().setScreen(new me.lovkar.wakingworld.client.gui.LetterScreen(stack));
            }

            @Override
            public void openKing(me.lovkar.wakingworld.kingdom.KingEntity king) {
                net.minecraft.client.Minecraft.getInstance().setScreen(new me.lovkar.wakingworld.client.gui.KingScreen(king));
            }

            @Override
            public void openTrade(me.lovkar.wakingworld.kingdom.TownsfolkEntity trader, net.minecraft.world.item.trading.MerchantOffers offers) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof me.lovkar.wakingworld.client.gui.TradeScreen open && open.trader() == trader) open.refresh(offers);
                else mc.setScreen(new me.lovkar.wakingworld.client.gui.TradeScreen(trader, offers));
            }

            @Override
            public void cineSetup(int renderDistance) {
                Cinematic.setup(renderDistance);
            }

            @Override
            public void cineStart(java.util.List<me.lovkar.wakingworld.story.Cinematics.Key> keys, int fadeIn, int fadeOut) {
                Cinematic.start(keys, fadeIn, fadeOut);
            }

            @Override
            public void cineStop() {
                Cinematic.stop();
            }

            @Override
            public void voiceData(java.util.UUID id, int status, int index, int total, byte[] data) {
                LetterVoicePlayer.receive(id, status, index, total, data);
            }

            @Override
            public String voiceState(java.util.UUID id) {
                LetterVoicePlayer.ask(id);
                return switch (LetterVoicePlayer.state(id)) {
                    case READY -> "ready";
                    case PENDING, LOADING, UNKNOWN -> "pending";
                    default -> null;
                };
            }
        };
        modBus.addListener(WakingWorldClient::registerRenderers);
        modBus.addListener(WakingWorldClient::registerLayers);
        modBus.addListener(WakingWorldClient::registerParticles);
        modBus.addListener(WakingWorldClient::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(WakingWorldClient::localPlayerTickStart);
        NeoForge.EVENT_BUS.addListener(WakingWorldClient::localPlayerTickEnd);
        NeoForge.EVENT_BUS.addListener(ScreenShake::clientTick);
        NeoForge.EVENT_BUS.addListener(ScreenShake::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(BossMusic::clientTick);
        NeoForge.EVENT_BUS.addListener(BossMusic::onSelectMusic);
        NeoForge.EVENT_BUS.addListener(ColossusBossBar::onBossBar);
        // the tick handler runs before other mods' (HIGHEST) so the crosshair target is gone before Jade & co
        // read it; the letterbox and fades draw after everyone else (LOWEST) so nothing sits on top of them
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, Cinematic::clientTick);
        NeoForge.EVENT_BUS.addListener(Cinematic::renderFrame);
        NeoForge.EVENT_BUS.addListener(Cinematic::computeFov);
        NeoForge.EVENT_BUS.addListener(Cinematic::guiLayer);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, Cinematic::guiPre);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, Cinematic::guiPost);
        NeoForge.EVENT_BUS.addListener(LetterVoicePlayer::clientTick);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (GarbModel.Kind k : GarbModel.Kind.values()) event.registerLayerDefinition(k.layer, () -> GarbModel.createLayer(k));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WakingWorld.COLOSSUS.get(), ColossusRenderer::new);
        event.registerEntityRenderer(WakingWorld.THROWN_MASS.get(), ThrownMassRenderer::new);
        event.registerEntityRenderer(WakingWorld.RUBBLE.get(), net.minecraft.client.renderer.entity.FallingBlockRenderer::new);
        event.registerBlockEntityRenderer(me.lovkar.wakingworld.ritual.WakingRitual.ALTAR_ENTITY.get(), AltarRenderer::new);
        event.registerEntityRenderer(WakingWorld.STONE_THRALL.get(), StoneThrallRenderer::new);
        event.registerEntityRenderer(WakingWorld.EMBER_WRAITH.get(), EmberWraithRenderer::new);
        event.registerEntityRenderer(WakingWorld.RUNE_SENTINEL.get(), RuneSentinelRenderer::new);
        event.registerEntityRenderer(WakingWorld.DROWNED_KEEPER.get(), DrownedKeeperRenderer::new);
        event.registerEntityRenderer(WakingWorld.GUARD.get(), ctx -> new KingdomHumanRenderer<>(ctx, false));
        event.registerEntityRenderer(WakingWorld.TOWNSFOLK.get(), ctx -> new KingdomHumanRenderer<>(ctx, false));
        event.registerEntityRenderer(WakingWorld.KING.get(), ctx -> new KingdomHumanRenderer<>(ctx, true));
    }

    private static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(me.lovkar.wakingworld.particle.WakingParticles.RUNE.get(), me.lovkar.wakingworld.client.particle.RuneParticle.Provider::new);
        event.registerSpriteSet(me.lovkar.wakingworld.particle.WakingParticles.RING.get(), me.lovkar.wakingworld.client.particle.RingParticle.Provider::new);
        event.registerSpriteSet(me.lovkar.wakingworld.particle.WakingParticles.EMBER.get(), me.lovkar.wakingworld.client.particle.EmberParticle.Provider::new);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        // baked quads hold atlas sprites - throw them away when resources reload
        event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> BakedBody.clear());
    }

    // The giant's body is solid exactly while the client moves its own player: that player bumps
    // into legs and arms, while the server's movement checks, falling rubble and items never see
    // the boxes (see ColossusPart.solidForLocalPlayer).
    private static void localPlayerTickStart(PlayerTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide && event.getEntity().isLocalPlayer()) {
            ColossusPart.solidForLocalPlayer = true;
            ColossusPart.localPlayerBox = event.getEntity().getBoundingBox();
        }
    }

    private static void localPlayerTickEnd(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide && event.getEntity().isLocalPlayer()) ColossusPart.solidForLocalPlayer = false;
    }
}
