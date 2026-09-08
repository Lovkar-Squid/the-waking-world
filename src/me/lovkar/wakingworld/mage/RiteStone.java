package me.lovkar.wakingworld.mage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Asking Stone: where a cataclysm is ordered.
 *
 * <p>It comes from the mage and from nowhere else - there is no recipe, and there is no second one
 * unless he gives you a second one. Set it down where you want the thing to happen, name which of
 * the five you are asking for (an empty hand turns it), and lay what he told you it costs. When the
 * last of the price is on it, it takes a little while, and then the sky or the ground answers.</p>
 *
 * <p>Put down and picked up freely: the price is the cost, not the walk. What it must never do is
 * work without him, which is why it is not craftable and why the stone remembers nothing about who
 * placed it - the gate is that you had to go and ask.</p>
 */
public class RiteStone extends BaseEntityBlock {
    public static final MapCodec<RiteStone> CODEC = simpleCodec(RiteStone::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 11, 15);

    public RiteStone(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public RiteStone() {
        this(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(6.0F, 1200.0F)
                .lightLevel(s -> s.getValue(LIT) ? 10 : 3)
                .sound(SoundType.DEEPSLATE_BRICKS)
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiteStoneEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, MageBlocks.RITE_STONE_ENTITY.get(), RiteStoneEntity::serverTick);
    }

    /** An empty hand turns it to the next of the five. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RiteStoneEntity stone)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            stone.giveBack(player);
        } else {
            stone.turn(player);
        }
        return InteractionResult.CONSUME;
    }

    /** Anything it wants, it takes. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RiteStoneEntity stone)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
        return stone.offer(player, hand, stack)
                ? ItemInteractionResult.sidedSuccess(false)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RiteStoneEntity stone) stone.dropAll();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
