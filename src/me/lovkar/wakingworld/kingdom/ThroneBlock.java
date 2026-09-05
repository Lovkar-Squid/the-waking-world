package me.lovkar.wakingworld.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.serialization.MapCodec;

/**
 * The king's throne: a gold seat with a cushion, armrests and a high back that rises into the
 * block above. Faces the way the king looks; the back stands behind him.
 */
public class ThroneBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ThroneBlock> CODEC = simpleCodec(ThroneBlock::new);
    private static final VoxelShape SEAT = Block.box(0, 0, 0, 16, 8, 16);
    // the back is on the side opposite the facing; shapes for a SOUTH-facing throne (back on the north)
    private static final VoxelShape BACK_S = Block.box(0, 8, 0, 16, 16, 3);
    private static final VoxelShape ARMS_S = Shapes.or(Block.box(0, 8, 3, 3, 12, 16), Block.box(13, 8, 3, 16, 12, 16));
    private static final VoxelShape SOUTH = Shapes.or(SEAT, BACK_S, ARMS_S);
    private static final VoxelShape NORTH = Shapes.or(SEAT, Block.box(0, 8, 13, 16, 16, 16), Block.box(0, 8, 0, 3, 12, 13), Block.box(13, 8, 0, 16, 12, 13));
    private static final VoxelShape EAST = Shapes.or(SEAT, Block.box(0, 8, 0, 3, 16, 16), Block.box(3, 8, 0, 16, 12, 3), Block.box(3, 8, 13, 16, 12, 16));
    private static final VoxelShape WEST = Shapes.or(SEAT, Block.box(13, 8, 0, 16, 16, 16), Block.box(0, 8, 0, 13, 12, 3), Block.box(0, 8, 13, 13, 12, 16));

    public ThroneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> SOUTH;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
