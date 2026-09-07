package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An earthquake: half a minute in which standing up is the problem.
 *
 * <p>It is not a scheduler of its own - the shower's tick starts it and it runs itself out. Three
 * things happen at once: the ground shakes hard enough to be felt through the screen and to knock
 * anything on its feet about, a handful of fissures open along one fault line, and loose blocks
 * (gravel, sand, anything already unsupported) come down.</p>
 *
 * <p>The fissures are the lasting part. Each is a narrow crack a few blocks deep along the fault,
 * never wider than three, and it stops the moment it meets anything a player built - a crack across
 * a field is a story, a crack through a bedroom is a bug report.</p>
 */
public final class Earthquake {
    private Earthquake() {
    }

    /** One quake, centred here. Returns the number of blocks the fault opened. */
    public static int shake(ServerLevel level, Vec3 at, int seconds, RandomSource rnd) {
        double angle = rnd.nextDouble() * Math.PI * 2;
        int length = 40 + rnd.nextInt(60);
        int opened = fault(level, at, angle, length, rnd);

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 220 * 220) continue;
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 3.0F, 0.25F);
        }
        WakingWorld.hooks.wave(at, 24.0, 220.0, 5.0F);
        return opened;
    }

    /**
     * The fault: a line that wanders a little, cracking the ground open behind it. It walks in the
     * heightmap, so it follows the surface up and down hills instead of cutting a straight trench
     * through them.
     */
    private static int fault(ServerLevel level, Vec3 at, double angle, int length, RandomSource rnd) {
        double x = at.x, z = at.z;
        int opened = 0;
        for (int step = 0; step < length; step++) {
            angle += (rnd.nextDouble() - 0.5) * 0.24;
            x += Math.cos(angle);
            z += Math.sin(angle);
            int half = rnd.nextInt(2);                                    // 1 to 3 blocks across
            int depth = 2 + rnd.nextInt(4);
            for (int w = -half; w <= half; w++) {
                double px = x + Math.sin(angle) * w;
                double pz = z - Math.cos(angle) * w;
                BlockPos top = Cataclysms.surface(level, px, pz).below();
                if (!natural(level, top)) continue;
                for (int d = 0; d < depth; d++) {
                    BlockPos p = top.below(d);
                    if (!natural(level, p)) break;
                    level.setBlock(p, d == depth - 1 ? Blocks.DEEPSLATE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
                    opened++;
                }
                level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, top.getY() + 1.0, pz, 2, 0.3, 0.2, 0.3, 0.01);
            }
        }
        return opened;
    }

    /**
     * Only ground the world made itself. A block with something in it, a light in the dark or a roof
     * over it belongs to somebody, and the fault goes round it.
     */
    private static boolean natural(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (state.isAir()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, at) < 0) return false;
        if (!level.getFluidState(at).isEmpty()) return false;
        if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at.above()) > 4) return false;
        return state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK)
                || state.is(net.minecraft.tags.BlockTags.TERRACOTTA);
    }

    /**
     * Every second while it lasts: the shaking, what it does to whoever is standing in it, and - the
     * part that was missing - something to look at. A camera on the ground during the first version
     * saw a still field that had already finished cracking; now the dust comes up off the faults for
     * the whole of it, stones come down, and the ground is heard as well as felt.
     */
    public static void second(ServerLevel level, Vec3 at, float strength) {
        WakingWorld.hooks.shakeAt(at, 3.5F * strength, 200);
        RandomSource rnd = level.random;
        // dust off the ground in a wide ring - thicker near the middle, thinner at the edges
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * 70;
            double px = at.x + Math.cos(a) * d, pz = at.z + Math.sin(a) * d;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) px, (int) pz);
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockState ground = level.getBlockState(BlockPos.containing(px, top - 1, pz));
            if (ground.isAir()) continue;
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                    px, top + 0.3, pz, 4, 0.6, 0.35, 0.6, 0.22 * strength);
            if (rnd.nextInt(5) == 0) {
                level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, top + 1.2, pz,
                        3, 0.5, 0.4, 0.5, 0.02);
            }
        }
        // and the deep note under it, from wherever the listener is standing
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at.x, at.y, at.z) > 200 * 200) continue;
            level.playSound(null, p.getX(), p.getY(), p.getZ(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.WEATHER, 0.9F * strength, 0.22F + rnd.nextFloat() * 0.06F);
        }
        AABB box = new AABB(at, at).inflate(90);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.onGround()) continue;
            double d = Math.sqrt(e.distanceToSqr(at));
            double push = (1.0 - d / 90.0) * strength;
            if (push <= 0) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(
                    (level.random.nextDouble() - 0.5) * 0.35 * push, 0.22 * push,
                    (level.random.nextDouble() - 0.5) * 0.35 * push));
            e.hurtMarked = true;
        }
    }

    /** How long one lasts, in seconds. */
    public static int seconds() {
        return WakingConfig.earthquakeSeconds();
    }

    /** Somewhere on the surface near a player, for the scheduler. */
    public static Vec3 site(ServerLevel level, ServerPlayer near, RandomSource rnd) {
        double angle = rnd.nextDouble() * Math.PI * 2;
        double dist = 20 + rnd.nextDouble() * 60;
        double x = near.getX() + Math.cos(angle) * dist;
        double z = near.getZ() + Math.sin(angle) * dist;
        BlockPos ground = Cataclysms.surface(level, x, z);
        return new Vec3(x, ground.getY(), z);
    }
}
