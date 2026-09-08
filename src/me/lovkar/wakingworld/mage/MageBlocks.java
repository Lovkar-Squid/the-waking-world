package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The one block the mage's half of the mod adds, and the thing you carry it as. */
public final class MageBlocks {
    private MageBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WakingWorld.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WakingWorld.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WakingWorld.MODID);

    public static final DeferredHolder<Block, RiteStone> RITE_STONE = BLOCKS.register("asking_stone", () -> new RiteStone());

    public static final DeferredHolder<Item, BlockItem> RITE_STONE_ITEM = ITEMS.register("asking_stone",
            () -> new BlockItem(RITE_STONE.get(), new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiteStoneEntity>> RITE_STONE_ENTITY =
            BLOCK_ENTITIES.register("asking_stone",
                    () -> BlockEntityType.Builder.of(RiteStoneEntity::new, RITE_STONE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
