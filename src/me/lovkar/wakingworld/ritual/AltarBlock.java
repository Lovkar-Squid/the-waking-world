package me.lovkar.wakingworld.ritual;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The altar at the heart of every shrine and of the arena. Right-click it with an offering to
 * lay it down (it floats above the stone), right-click it empty-handed to take the last one back,
 * sound the Horn of Waking over it once every offering is there. It cannot be broken by a
 * colossus and is never restored away.
 */
public class AltarBlock extends BaseEntityBlock {
    public static final MapCodec<AltarBlock> CODEC = simpleCodec(AltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 12, 14), Block.box(0, 12, 0, 16, 15, 16));

    public AltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<AltarBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, WakingRitual.ALTAR_ENTITY.get(), level.isClientSide ? AltarBlockEntity::clientTick : AltarBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity altar)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof me.lovkar.wakingworld.item.HornOfWakingItem) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // the horn is blown, not laid down
        if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
        return altar.offer(player, hand, stack) ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity altar)) return InteractionResult.PASS;
        // a horn in hand is blown, not laid down and not a reason to take anything back
        if (player.getMainHandItem().getItem() instanceof me.lovkar.wakingworld.item.HornOfWakingItem || player.getOffhandItem().getItem() instanceof me.lovkar.wakingworld.item.HornOfWakingItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return altar.takeBack(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AltarBlockEntity altar) altar.dropAll();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
