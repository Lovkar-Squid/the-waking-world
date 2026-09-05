package me.lovkar.wakingworld.kingdom;

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

/** The kingdom's blocks: the throne. */
public final class KingdomBlocks {
    private KingdomBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WakingWorld.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WakingWorld.MODID);

    public static final DeferredHolder<Block, ThroneBlock> THRONE = BLOCKS.register("throne", () -> new ThroneBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> THRONE_ITEM = ITEMS.register("throne", () -> new BlockItem(THRONE.get(), new Item.Properties().rarity(Rarity.RARE)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
