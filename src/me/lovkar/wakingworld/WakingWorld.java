package me.lovkar.wakingworld;

import me.lovkar.wakingworld.client.WakingWorldClient;
import me.lovkar.wakingworld.command.WakingCommands;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.entity.ThrownMassEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Waking World - the world has an old, forgotten history, and it is waking up.
 *
 * Phase 0 (this build): the Colossus - a giant built from the blocks of the land, rendered
 * as baked block geometry, walking on vanilla AI, hittable through Ender-Dragon-style parts.
 * Summon one with /wakingworld summon [variant] [height].
 *
 * Made by Lovkar & Claude for NeoForge 1.21.1.
 */
@Mod(WakingWorld.MODID)
public class WakingWorld {
    public static final String MODID = "wakingworld";
    public static final Logger LOGGER = LoggerFactory.getLogger("The Waking World");
    /** The client's side of things (camera shake); a no-op on a dedicated server. */
    public static ClientHooks hooks = ClientHooks.NONE;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ColossusEntity>> COLOSSUS = ENTITIES.register("colossus",
            () -> EntityType.Builder.of(ColossusEntity::new, MobCategory.MONSTER)
                    .sized(3.0F, 6.0F)
                    .eyeHeight(5.5F)
                    .clientTrackingRange(24)
                    .updateInterval(2)
                    .fireImmune()
                    .build("colossus"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownMassEntity>> THROWN_MASS = ENTITIES.register("thrown_mass",
            () -> EntityType.Builder.<ThrownMassEntity>of(ThrownMassEntity::new, MobCategory.MISC)
                    .sized(2.2F, 2.2F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("thrown_mass"));

    /** The vaults' keeper: a stone-bodied undead (see StoneThrallEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.entity.StoneThrallEntity>> STONE_THRALL = ENTITIES.register("stone_thrall",
            () -> EntityType.Builder.<me.lovkar.wakingworld.entity.StoneThrallEntity>of(me.lovkar.wakingworld.entity.StoneThrallEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.95F)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("stone_thrall"));

    /** A block a colossus sent flying (vanilla's falling block that remembers its fight - see RubbleEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.entity.RubbleEntity>> RUBBLE = ENTITIES.register("rubble",
            () -> EntityType.Builder.<me.lovkar.wakingworld.entity.RubbleEntity>of(me.lovkar.wakingworld.entity.RubbleEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("rubble"));

    /** The dungeons' keepers: the Ember Wraith of the forges, the Rune Sentinel of the deeper vaults, the Drowned Keeper of the cisterns. */
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.entity.EmberWraithEntity>> EMBER_WRAITH = ENTITIES.register("ember_wraith",
            () -> EntityType.Builder.<me.lovkar.wakingworld.entity.EmberWraithEntity>of(me.lovkar.wakingworld.entity.EmberWraithEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).fireImmune().build("ember_wraith"));
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.entity.RuneSentinelEntity>> RUNE_SENTINEL = ENTITIES.register("rune_sentinel",
            () -> EntityType.Builder.<me.lovkar.wakingworld.entity.RuneSentinelEntity>of(me.lovkar.wakingworld.entity.RuneSentinelEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).clientTrackingRange(8).build("rune_sentinel"));
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.entity.DrownedKeeperEntity>> DROWNED_KEEPER = ENTITIES.register("drowned_keeper",
            () -> EntityType.Builder.<me.lovkar.wakingworld.entity.DrownedKeeperEntity>of(me.lovkar.wakingworld.entity.DrownedKeeperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("drowned_keeper"));

    /** The kingdom's people: guards (archers, knights, spearmen), the traders, the king. */
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.kingdom.GuardEntity>> GUARD = ENTITIES.register("guard",
            () -> EntityType.Builder.<me.lovkar.wakingworld.kingdom.GuardEntity>of(me.lovkar.wakingworld.kingdom.GuardEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("guard"));
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.kingdom.TownsfolkEntity>> TOWNSFOLK = ENTITIES.register("townsfolk",
            () -> EntityType.Builder.<me.lovkar.wakingworld.kingdom.TownsfolkEntity>of(me.lovkar.wakingworld.kingdom.TownsfolkEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("townsfolk"));
    public static final DeferredHolder<EntityType<?>, EntityType<me.lovkar.wakingworld.kingdom.KingEntity>> KING = ENTITIES.register("king",
            () -> EntityType.Builder.<me.lovkar.wakingworld.kingdom.KingEntity>of(me.lovkar.wakingworld.kingdom.KingEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("king"));

    public WakingWorld(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, WakingConfig.SPEC);
        ENTITIES.register(modBus);
        me.lovkar.wakingworld.worldgen.WakingStructures.register(modBus);
        WakingSounds.SOUNDS.register(modBus);
        me.lovkar.wakingworld.item.WakingItems.register(modBus);
        me.lovkar.wakingworld.advancement.WakingTriggers.register(modBus);
        me.lovkar.wakingworld.particle.WakingParticles.register(modBus);
        me.lovkar.wakingworld.ritual.WakingRitual.register(modBus);
        me.lovkar.wakingworld.kingdom.KingdomBlocks.register(modBus);
        modBus.addListener(WakingWorld::registerAttributes);
        modBus.addListener(me.lovkar.wakingworld.network.WakingNet::register);
        modBus.addListener(me.lovkar.wakingworld.entity.WakingSpawns::register);
        NeoForge.EVENT_BUS.addListener(WakingCommands::register);
        NeoForge.EVENT_BUS.addListener(WakingWorld::onMount);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.ruin.RuinLedger::onLevelTick);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.ruin.RuinLedger::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.entity.VoidGuard::onLevelTick);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.item.HeartOfTheEndItem::onClone);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.item.HeartOfTheEndItem::onRespawn);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.item.HeartOfTheEndItem::onJoin);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.ritual.DragonEggGuard::onJoin);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.ritual.DragonEggGuard::onLeave);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.story.Cinematics::onLevelTick);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.kingdom.KingdomEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.kingdom.KingdomEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.kingdom.KingdomEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.supporter.SupporterList::onServerStarted);
        NeoForge.EVENT_BUS.addListener(me.lovkar.wakingworld.supporter.SupporterList::onServerTick);
        if (FMLEnvironment.dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, WakingConfig.CLIENT_SPEC);
            WakingWorldClient.init(modBus, container);
        }
        LOGGER.info("The Waking World 0.1.0-beta.5 - the world is waking. /wakingworld for the tools.");
    }

    /** Nobody sneaks out of a colossus' fist: a dismount is refused while it holds you (it lets go when it throws). */
    private static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof ColossusEntity colossus
                && colossus.isHolding(event.getEntityMounting()) && colossus.isAlive()
                && !(event.getEntityMounting() instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected())) {
            event.setCanceled(true);
        }
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(COLOSSUS.get(), ColossusEntity.createAttributes().build());
        event.put(STONE_THRALL.get(), me.lovkar.wakingworld.entity.StoneThrallEntity.createAttributes().build());
        event.put(EMBER_WRAITH.get(), me.lovkar.wakingworld.entity.EmberWraithEntity.createAttributes().build());
        event.put(RUNE_SENTINEL.get(), me.lovkar.wakingworld.entity.RuneSentinelEntity.createAttributes().build());
        event.put(DROWNED_KEEPER.get(), me.lovkar.wakingworld.entity.DrownedKeeperEntity.createAttributes().build());
        event.put(GUARD.get(), me.lovkar.wakingworld.kingdom.GuardEntity.createAttributes().build());
        event.put(TOWNSFOLK.get(), me.lovkar.wakingworld.kingdom.TownsfolkEntity.createAttributes().build());
        event.put(KING.get(), me.lovkar.wakingworld.kingdom.KingEntity.createAttributes().build());
    }
}
