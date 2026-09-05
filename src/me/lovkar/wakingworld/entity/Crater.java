package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.WakingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The ground gives way. A stomp, a slam, a landing or a falling boulder tears a bowl out of the
 * terrain: the blocks that were there fly outwards as real falling blocks (a share of them - the
 * rest shatter into dust) and come down as rubble around the rim. Unbreakable and blast-proof
 * blocks (bedrock, obsidian, end portal frames...) and anything with a block entity (chests,
 * spawners) stay. Everything here is server-side and can be switched off in the config.
 */
public final class Crater {
    private Crater() {
    }

    /**
     * @param center   where the impact lands (the bowl is centred a little below it)
     * @param radius   horizontal radius in blocks
     * @param flying   how many of the removed blocks become falling blocks (the rest just break)
     * @param power    outward speed of the flying blocks (0.3 - 1.0)
     */
    public static void blast(ServerLevel level, Vec3 center, double radius, int flying, double power, RandomSource rnd) {
        blast(level, center, radius, flying, power, 99, rnd);
    }

    /** The same, but the bowl goes no deeper than {@code maxDepth} blocks below the centre - a wide shallow dish rather than a pit. */
    public static void blast(ServerLevel level, Vec3 center, double radius, int flying, double power, int maxDepth, RandomSource rnd) {
        if (!WakingConfig.terrainDamage()) {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 2, radius * 0.4, 0.5, radius * 0.4, 0);
            return;
        }
        radius *= WakingConfig.craterScale();
        int r = (int) Math.ceil(radius);
        int removed = 0, flown = 0;
        int budgetFlying = Math.max(0, Math.min(flying, WakingConfig.maxFlyingBlocks()));
        // walk the bowl from the top down so falling blocks are spawned where the surface was
        int depth = Math.min(maxDepth, (int) Math.ceil(radius * 0.7));
        for (int dy = (int) Math.ceil(radius * 0.4); dy >= -depth; dy--) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dz * dz + (dy * 1.5) * (dy * 1.5);
                    double edge = radius * (0.85 + 0.25 * rnd.nextDouble());
                    if (d2 > edge * edge) continue;
                    BlockPos pos = BlockPos.containing(center.x + dx, center.y - 0.5 + dy, center.z + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!breakable(level, pos, state)) continue;
                    removed++;
                    boolean fly = flown < budgetFlying && rnd.nextInt(3) == 0 && state.canOcclude();
                    me.lovkar.wakingworld.ruin.Ruin.mark(level, pos);
                    if (fly) {
                        FallingBlockEntity fb = me.lovkar.wakingworld.ruin.Ruin.fall(level, pos, state); // clears the block too
                        double ox = dx + 0.5, oz = dz + 0.5;
                        double flat = Math.max(0.5, Math.sqrt(ox * ox + oz * oz));
                        double v = power * (0.6 + 0.6 * rnd.nextDouble());
                        fb.setDeltaMovement(ox / flat * v * 0.5, 0.45 + rnd.nextDouble() * 0.5 * power + 0.3, oz / flat * v * 0.5);
                        fb.time = 1;
                        fb.dropItem = false;
                        fb.setHurtsEntities(1.5F, 25);
                        flown++;
                    } else {
                        level.removeBlock(pos, false);
                        if (rnd.nextInt(4) == 0) level.levelEvent(2001, pos, Block.getId(state));
                    }
                }
            }
        }
        if (removed > 0) {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, Math.min(6, 1 + removed / 12), radius * 0.4, 0.6, radius * 0.4, 0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 0.5, center.z, Math.min(30, removed / 3), radius * 0.5, 0.8, radius * 0.5, 0.02);
        }
    }

    /** Puts a block back into the world if the spot is free - used to leave a boulder lying in its crater. */
    public static boolean settle(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty()) return false;
        me.lovkar.wakingworld.ruin.Ruin.mark(level, pos);
        return level.setBlock(pos, state, 3);
    }

    /** Blocks the world can lose to a giant: not bedrock, not blast-proof, not chests and machines, not fluids. */
    public static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.is(BlockTags.WITHER_IMMUNE) || state.is(BlockTags.DRAGON_IMMUNE)) return false;
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.ANCIENT_DEBRIS)) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= 6.0F;
    }

    /** Small, soft things a walking giant just goes through: plants, leaves, logs, snow, loose ground. */
    public static boolean trampleable(ServerLevel level, BlockPos pos, BlockState state) {
        if (!breakable(level, pos, state)) return false;
        if (vegetation(state)) return true;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness <= 0.7F; // dirt, sand, gravel, grass, clay, mud, hay
    }

    /** Trees, plants and snow - the things that come down anywhere along the body, not just at the feet. */
    public static boolean vegetation(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS) || state.is(BlockTags.SNOW) || state.is(BlockTags.REPLACEABLE);
    }

    /** Throws one block of the world outwards from a point - a tree coming apart, ground kicked up by a foot. */
    public static void fling(ServerLevel level, BlockPos pos, BlockState state, Vec3 from, double power, RandomSource rnd) {
        fling(level, pos, state, from, power, 0.25, rnd);
    }

    /** The same with a chosen upward kick ({@code up} = the least vertical speed; vanilla-ish 0.25). */
    public static void fling(ServerLevel level, BlockPos pos, BlockState state, Vec3 from, double power, double up, RandomSource rnd) {
        if (!WakingConfig.terrainDamage()) {
            // no flying blocks allowed: the block still has to go (a trampled tree cannot stay standing)
            if (!level.getBlockState(pos).isAir()) {
                me.lovkar.wakingworld.ruin.Ruin.mark(level, pos);
                level.destroyBlock(pos, false);
            }
            return;
        }
        FallingBlockEntity fb = me.lovkar.wakingworld.ruin.Ruin.fall(level, pos, state);
        double ox = pos.getX() + 0.5 - from.x, oz = pos.getZ() + 0.5 - from.z;
        double flat = Math.max(0.5, Math.sqrt(ox * ox + oz * oz));
        fb.setDeltaMovement(ox / flat * power * 0.4 + (rnd.nextDouble() - 0.5) * 0.2, up + rnd.nextDouble() * 0.35, oz / flat * power * 0.4 + (rnd.nextDouble() - 0.5) * 0.2);
        fb.time = 1;
        fb.dropItem = false;
        fb.setHurtsEntities(1.0F, 15);
    }

    public static void dust(ServerLevel level, BlockPos pos, BlockState state, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, count, 0.4, 0.4, 0.4, 0.15);
    }
}
