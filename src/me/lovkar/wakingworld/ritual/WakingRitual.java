package me.lovkar.wakingworld.ritual;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The altar block, its item and its block entity; the Titan's Gate. */
public final class WakingRitual {
    private WakingRitual() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WakingWorld.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(Registries.ITEM, WakingWorld.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WakingWorld.MODID);

    public static final DeferredHolder<Block, AltarBlock> ALTAR = BLOCKS.register("altar", () -> new AltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(50.0F, 1200.0F).sound(SoundType.DEEPSLATE_BRICKS)
                    .lightLevel(s -> 7).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> ALTAR_ITEM = BLOCK_ITEMS.register("altar", () -> new BlockItem(ALTAR.get(), new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AltarBlockEntity>> ALTAR_ENTITY = BLOCK_ENTITIES.register("altar",
            () -> BlockEntityType.Builder.of(AltarBlockEntity::new, ALTAR.get()).build(null));

    /** The Titan's Gate - the sheet of the void in the frame the arena's altar raises when the Titan falls. Not a thing to hold. */
    public static final DeferredHolder<Block, TitanGateBlock> TITAN_GATE = BLOCKS.register("titan_gate", () -> new TitanGateBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission().noOcclusion().lightLevel(s -> 15).strength(-1.0F, 3600000.0F)
                    .noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).sound(SoundType.GLASS)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
