package me.lovkar.wakingworld.ritual;

import com.mojang.serialization.MapCodec;
import me.lovkar.wakingworld.particle.WakingParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Titan's Gate: the sheet of the void that hangs in the arch the arena's altar raises when
 * the Titan falls - the way home. Step into it and the End lets you go: to your bed, or where
 * you first woke, the way the End's own portal does (and with its poem, the first time). The
 * altar takes the gate down again when the next rite begins. A plain block with its own animated
 * texture (threads of light crawling through a dark purple void, tools/textures/gate.py) on a
 * standing sheet six sixteenths thick - so it looks the same under every shader pack.
 */
public class TitanGateBlock extends Block implements Portal {
    public static final MapCodec<TitanGateBlock> CODEC = simpleCodec(TitanGateBlock::new);
    /** The sheet across the middle of the block, facing north and south (the block model matches). */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 5.0, 16.0, 16.0, 11.0);

    public TitanGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** One sheet, not a hundred: no seams drawn where one gate block meets the next. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.canUsePortal(false) || entity.isSpectator()) return; // a spectator (the director's camera among them) drifts through
        if (!Shapes.joinIsNotEmpty(Shapes.create(entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ())), state.getShape(level, pos), BooleanOp.AND)) return;
        // leaving the End the first time: the poem, as through the End's own portal
        if (!level.isClientSide && level.dimension() == Level.END && entity instanceof ServerPlayer player && !player.seenCredits) {
            player.showEndCredits();
            return;
        }
        entity.setAsInsidePortal(this, pos);
    }

    /** A breath inside the sheet before it takes you - long enough for the void to close in at the edges of your sight. */
    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player ? 24 : 0;
    }

    @Override
    public Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return null;
        // players go where they would wake: the bed or anchor, else the world's spawn; anything else to the spawn
        if (entity instanceof ServerPlayer player) return player.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.PLAY_PORTAL_SOUND);
        return new DimensionTransition(overworld, entity, DimensionTransition.PLAY_PORTAL_SOUND);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // the void breathing in the sheet: a rune drifting off it now and then, motes of the End streaming off both faces
        if (random.nextInt(5) == 0) {
            level.addParticle(WakingParticles.rune(Rites.color("titan"), 0.6f + random.nextFloat() * 0.5f),
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + 0.5 + (random.nextBoolean() ? 0.45 : -0.45),
                    0, 0.01 + random.nextDouble() * 0.02, 0);
        }
        if (random.nextInt(2) == 0) {
            double side = random.nextBoolean() ? 1 : -1;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + 0.5 + side * 0.4,
                    (random.nextDouble() - 0.5) * 0.08, (random.nextDouble() - 0.5) * 0.08, side * (0.05 + random.nextDouble() * 0.15));
        }
        if (random.nextInt(90) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, me.lovkar.wakingworld.WakingSounds.GATE_AMBIENT.get(), SoundSource.BLOCKS, 0.5F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }
}
