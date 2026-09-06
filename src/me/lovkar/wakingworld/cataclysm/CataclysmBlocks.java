package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What the sky leaves behind. Starstone is the heart of a fallen star: black, heavy, still lit
 * from the inside. It only exists where a meteor came down, and it is the one source of Star Iron.
 */
public final class CataclysmBlocks {
    private CataclysmBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WakingWorld.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(Registries.ITEM, WakingWorld.MODID);

    public static final DeferredHolder<Block, Block> STARSTONE = BLOCKS.register("starstone", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.5F, 12.0F)
                    .sound(SoundType.NETHER_ORE)
                    .lightLevel(s -> 6)
                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> STARSTONE_ITEM = BLOCK_ITEMS.register("starstone",
            () -> new BlockItem(STARSTONE.get(), new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ITEMS.register(modBus);
    }
}
