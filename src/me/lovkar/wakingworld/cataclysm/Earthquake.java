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
import me.lovkar.wakingworld.WakingSounds;
import me.lovkar.wakingworld.ruin.Ruin;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import me.lovkar.wakingworld.compat.Colonies;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class Earthquake {
    /** Which second of the shaking this is, so the loop is restarted and not stacked. */
    private static int beat;

    private Earthquake() {
    }

    /**
     * One quake, centred here. Returns the number of blocks the fault opened.
     *
     * <p>The whole fault used to be cut in this one call, which meant it was already open before
     * anybody could look at it: a camera on the ground during a quake saw a still field with a
     * crack in it and nothing moving for the next twenty-six seconds. The fault is a live thing
     * now - {@link #crack} walks a length of it every second while the shaking lasts - and this
     * only opens the first stretch and starts the wave.</p>
     */
    public static int shake(ServerLevel level, Vec3 at, int seconds, RandomSource rnd) {
        heading = rnd.nextDouble() * Math.PI * 2;
        headX = at.x;
        headZ = at.z;
        int opened = fault(level, at, heading, 8, rnd, Shelter.of(level));

        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 220 * 220) continue;
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 3.0F, 0.25F);
        }
        WakingWorld.hooks.wave(at, 24.0, 220.0, 5.0F);
        return opened;
    }

    /** Where the fault has got to, so each second carries on from the last. */
    private static double heading, headX, headZ;

    /**
     * A ridge thrown up along one side of the fault.
     *
     * <p>A crack in the ground is a hole, and a hole is a thing you look down into rather than a
     * thing that happens to you. Ground that has been PUSHED is what an earthquake actually leaves:
     * one side of the line a metre or two higher than the other, a scarp you can walk along. Every
     * few steps the fault heaves one bank up, taking whatever was standing on it with it.</p>
     */
    private static void heave(ServerLevel level, double px, double pz, double along, RandomSource rnd, Shelter home) {
        double nx = Math.sin(along), nz = -Math.cos(along);
        for (int side = 2; side <= 4; side++) {
            int ix = (int) Math.floor(px + nx * side), iz = (int) Math.floor(pz + nz * side);
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockPos on = new BlockPos(ix, top - 1, iz);
            if (!natural(level, on)) continue;
            if (home.holds(level, on)) continue;                          // nobody's garden is pushed up into a bank
            BlockState state = level.getBlockState(on);
            int lift = 1 + rnd.nextInt(2);
            for (int k = 1; k <= lift; k++) {
                BlockPos up = on.above(k);
                if (!level.getBlockState(up).isAir() && level.getFluidState(up).isEmpty()) break;
                Scars.set(level, up, state);
            }
        }
    }

    /**
     * The moment it stops: one hard shock, a ring of dust going out, and a last stretch of fault torn
     * open all at once. An event that simply fades out has no end - this gives it one.
     *
     * <p>The blight comes with it now. It used to be laid by the scheduler after this, knowing
     * nothing of anybody's home, so a farm the fault had been kept off was ploughed under anyway;
     * here it is spared the same ground as the fault.</p>
     */
    public static void climax(ServerLevel level, Vec3 at, RandomSource rnd) {
        Shelter home = Shelter.of(level);
        WakingWorld.hooks.shakeAt(at, 9.0F, 260);
        WakingWorld.hooks.wave(at, 30.0, 260.0, 7.0F);
        for (int i = 0; i < 3; i++) crack(level, at, 1.4F, rnd, home);
        for (int ring = 0; ring < 3; ring++) {
            double r = 10 + ring * 16;
            for (int i = 0; i < 44; i++) {
                double a = i / 44.0 * Math.PI * 2;
                double px = at.x + Math.cos(a) * r, pz = at.z + Math.sin(a) * r;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) px, (int) pz);
                if (top <= level.getMinBuildHeight() + 1) continue;
                Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, top + 0.5, pz, 5, 0.6, 0.5, 0.6, 0.05);
                BlockState g = level.getBlockState(new BlockPos((int) px, top - 1, (int) pz));
                if (!g.isAir()) {
                    Cataclysms.puff(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, g),
                            px, top + 0.4, pz, 6, 0.5, 0.4, 0.5, 0.45);
                }
            }
        }
        level.playSound(null, at.x, at.y, at.z, me.lovkar.wakingworld.WakingSounds.QUAKE_RUMBLE.get(),
                SoundSource.WEATHER, 10.0F, 0.62F);
        // until now the quake was the only one of the five that left nothing at all
        if (WakingConfig.blight()) {
            Aftermath.blight(level, BlockPos.containing(at.x, at.y, at.z), 34, 0.45, rnd, p -> home.holds(level, p));
        }
    }

    /**
     * The next length of fault, opened while somebody is watching, and the ground thrown up along
     * it. Called once a second for as long as the quake lasts.
     *
     * <p>It stops short of sheltered ground, and where it runs within a throw of it the turf goes
     * straight up instead of out to the side, so it comes down in its own crack rather than in
     * somebody's garden.</p>
     */
    private static void crack(ServerLevel level, Vec3 at, float strength, RandomSource rnd, Shelter home) {
        int length = 3 + (int) (5 * strength);
        double x = headX, z = headZ;
        for (int step = 0; step < length; step++) {
            heading += (rnd.nextDouble() - 0.5) * 0.24;
            x += Math.cos(heading);
            z += Math.sin(heading);
            int half = rnd.nextInt(2);
            int depth = 2 + rnd.nextInt(4);
            for (int w = -half; w <= half; w++) {
                double px = x + Math.sin(heading) * w;
                double pz = z - Math.cos(heading) * w;
                BlockPos top = new BlockPos((int) Math.floor(px),
                        level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                (int) Math.floor(px), (int) Math.floor(pz)) - 1, (int) Math.floor(pz));
                if (!natural(level, top)) continue;
                if (home.holds(level, top)) continue;             // the fault stops at a colony's border, and short of anybody's home
                // the top of it is thrown into the air rather than deleted: that is the whole
                // difference between ground that cracked and ground that was always cracked
                BlockState surface = level.getBlockState(top);
                Ruin.mark(level, top);
                level.removeBlock(top, false);
                if (rnd.nextDouble() < 0.5) {
                    net.minecraft.world.entity.item.FallingBlockEntity fb =
                            net.minecraft.world.entity.item.FallingBlockEntity.fall(level, top, surface);
                    fb.setHurtsEntities(1.0F, 6);
                    double sx = (rnd.nextDouble() - 0.5) * 0.28;
                    double up = 0.42 + rnd.nextDouble() * 0.35;
                    double sz = (rnd.nextDouble() - 0.5) * 0.28;
                    if (home.holds(level, top, Shelter.THROW)) sx = sz = 0;
                    fb.setDeltaMovement(sx, up, sz);
                }
                for (int d = 1; d < depth; d++) {
                    BlockPos p = top.below(d);
                    if (!natural(level, p)) break;
                    Scars.set(level, p, d == depth - 1 ? Blocks.DEEPSLATE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
                Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, top.getY() + 1.0, pz,
                        6, 0.4, 0.5, 0.4, 0.03);
                Cataclysms.puff(level, new net.minecraft.core.particles.BlockParticleOption(
                                ParticleTypes.BLOCK, surface), px, top.getY() + 0.6, pz,
                        10, 0.5, 0.4, 0.5, 0.3);
            }
            // and one bank of it pushed up, every few paces
            if (step % 3 == 0) heave(level, x, z, heading, rnd, home);
        }
        headX = x;
        headZ = z;
    }

    /**
     * The fault: a line that wanders a little, cracking the ground open behind it. It walks in the
     * heightmap, so it follows the surface up and down hills instead of cutting a straight trench
     * through them.
     */
    private static int fault(ServerLevel level, Vec3 at, double angle, int length, RandomSource rnd, Shelter home) {
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
                if (home.holds(level, top)) continue;
                for (int d = 0; d < depth; d++) {
                    BlockPos p = top.below(d);
                    if (!natural(level, p)) break;
                    Scars.set(level, p, d == depth - 1 ? Blocks.DEEPSLATE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                    opened++;
                }
                Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, top.getY() + 1.0, pz, 2, 0.3, 0.2, 0.3, 0.01);
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
        Shelter home = Shelter.of(level);                       // once a second, not once a block
        if (strength > 0.2F) crack(level, at, strength, rnd, home);   // the fault opens while it shakes, not while it holds its breath
        // dust off the ground in a wide ring - thicker near the middle, thinner at the edges
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * 70;
            double px = at.x + Math.cos(a) * d, pz = at.z + Math.sin(a) * d;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) px, (int) pz);
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockState ground = level.getBlockState(BlockPos.containing(px, top - 1, pz));
            if (ground.isAir()) continue;
            Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, top + 0.8, pz,
                    3, 0.6, 0.5, 0.6, 0.02);
            Cataclysms.puff(level, new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                    px, top + 0.3, pz, 4, 0.6, 0.35, 0.6, 0.22 * strength);
        }
        // the wave: a ring going out, one step further every second, restarting every seventh
        if (strength > 0.45F) {
            ripple(level, at, 7 + (beat % 7) * 13, strength, rnd, home, true);
            // The vents go near the middle rather than at the fault's head. The head walks a few
            // blocks a second and is a long way outside the frame by the time anybody looks;
            // the middle is where the camera is pointed and where somebody standing in it is.
            if (beat % 2 == 0) jets(level, at.x, at.z, strength, rnd);
        }
        // and the ground's own note under it. The loop is 4.5 s and this runs once a second, so it
        // is started every fourth pass - often enough to be unbroken, rarely enough not to stack.
        if (++beat % 4 == 0) {
            for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                if (p.distanceToSqr(at.x, at.y, at.z) > 260 * 260) continue;
                level.playSound(null, at.x, at.y, at.z, me.lovkar.wakingworld.WakingSounds.QUAKE_RUMBLE.get(),
                        net.minecraft.sounds.SoundSource.WEATHER, 6.5F * strength, 0.92F + rnd.nextFloat() * 0.1F);
                break;
            }
        }
        AABB box = new AABB(at, at).inflate(90);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.onGround()) continue;
            // a colonist in the town, or anybody at their own door, feels it and is not thrown about
            if (home.holds(level, e.blockPosition())) continue;
            double d = Math.sqrt(e.distanceToSqr(at));
            double push = (1.0 - d / 90.0) * strength;
            if (push <= 0) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(
                    (level.random.nextDouble() - 0.5) * 0.35 * push, 0.22 * push,
                    (level.random.nextDouble() - 0.5) * 0.35 * push));
            e.hurtMarked = true;
        }
    }

    /**
     * The shape of a quake in time.
     *
     * <p>A tremor that is equally strong for twenty-six seconds is not frightening, it is weather.
     * Every real quake has a shape: a foreshock that makes you look up, a few seconds of nothing at
     * all - which is the part people remember - then the main shock, then aftershocks that fall
     * away with a couple of kicks left in them. Weather passes the progress and this decides how
     * hard the ground is working.</p>
     */
    public static float envelope(float progress) {
        if (progress < 0.12F) return 0.32F + progress / 0.12F * 0.28F;      // the foreshock, building
        if (progress < 0.21F) return 0.06F;                                  // the held breath
        if (progress < 0.55F) return 1.0F;                                   // the main shock
        float t = (progress - 0.55F) / 0.45F;
        float kick = (float) Math.max(0.0, Math.sin(t * Math.PI * 3.0)) * 0.42F;
        return Math.max(0.16F, (1.0F - t) * 0.72F + kick);
    }

    /**
     * The ground wave: a ring travelling outwards in which the surface itself lifts and drops back.
     *
     * <p>This is the one thing that makes a quake look like a quake from outside it. The blocks on
     * the arc are thrown up a few tenths of a block as falling blocks and land back where they came
     * from, so a hillside visibly rolls and is still a hillside afterwards. Only ground the world
     * made itself is moved, only near somebody who can see it, and only a few dozen blocks a second
     * - it is a ripple, not a demolition. Over sheltered ground the smoke and the dust still go by,
     * and the ground stays where it is.</p>
     *
     * @return how many blocks it threw
     */
    private static int ripple(ServerLevel level, Vec3 at, double radius, float strength, RandomSource rnd,
                              Shelter home, boolean onlyWatched) {
        int spawned = 0;
        int samples = (int) Math.min(120, Math.max(36, radius * 2.2));
        double start = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < samples && spawned < 34; i++) {
            double a = start + i / (double) samples * Math.PI * 2;
            double px = at.x + Math.cos(a) * (radius + rnd.nextDouble() * 1.6 - 0.8);
            double pz = at.z + Math.sin(a) * (radius + rnd.nextDouble() * 1.6 - 0.8);
            if (onlyWatched && !watched(level, px, pz, 96)) continue;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(px), (int) Math.floor(pz));
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockPos on = new BlockPos((int) Math.floor(px), top - 1, (int) Math.floor(pz));
            BlockState ground = level.getBlockState(on);
            if (ground.isAir()) continue;
            // Smoke first and block dust second, which is the other way round from how this
            // started. Block dust off white sand, over white sand, under a white sky, is
            // invisible - the whole quake came back from a take looking like a still photograph.
            // Grey smoke reads against anything, so the wave is smoke with dust thrown through it.
            for (int k = 0; k < 3; k++) {
                Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        px, top + 0.4 + k * 1.5, pz, 2, 0.5, 0.4, 0.5, 0.02 + 0.012 * k);
            }
            Cataclysms.puff(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                    px, top + 0.35, pz, 3, 0.45, 0.3, 0.45, 0.18);
            if (!natural(level, on) || rnd.nextDouble() > 0.55 * strength) continue;
            if (home.holds(level, on)) continue;
            if (!level.getBlockState(on.above()).isAir()) continue;
            net.minecraft.world.entity.item.FallingBlockEntity fb =
                    net.minecraft.world.entity.item.FallingBlockEntity.fall(level, on, ground);
            fb.setDeltaMovement(0, 0.26 + rnd.nextDouble() * 0.22 * strength, 0);
            fb.time = 1;
            spawned++;
        }
        return spawned;
    }

    /**
     * The wave with nobody watching it - for the headless probe only. The test server has no
     * players, so {@link #watched} is never true there and the real wave would throw nothing to
     * check; this throws what it would throw if somebody stood on every arc of it.
     */
    public static int rippleUnwatched(ServerLevel level, Vec3 at, double radius, float strength, RandomSource rnd) {
        return ripple(level, at, radius, strength, rnd, Shelter.of(level), false);
    }

    /**
     * Sand blows: the ground venting where the fault runs, in columns you can see from a long way
     * off. Cheap, and they are what tells a distant camera where the quake actually is.
     */
    private static void jets(ServerLevel level, double x, double z, float strength, RandomSource rnd) {
        for (int j = 0; j < 4; j++) {
            double px = x + (rnd.nextDouble() - 0.5) * 30, pz = z + (rnd.nextDouble() - 0.5) * 30;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) px, (int) pz);
            if (top <= level.getMinBuildHeight() + 1) continue;
            BlockState ground = level.getBlockState(BlockPos.containing(px, top - 1, pz));
            if (ground.isAir()) continue;
            for (int k = 0; k < 10; k++) {
                double y = top + 0.4 + k * 1.6;
                double spread = 0.35 + k * 0.40;
                Cataclysms.puff(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                        px, y, pz, 4, spread, 0.5, spread, 0.10 + 0.05 * k);
                Cataclysms.puff(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, y + 0.6, pz,
                        3, spread, 0.4, spread, 0.02 + 0.012 * k);
            }
            level.playSound(null, px, top, pz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER,
                    1.4F * strength, 0.42F + rnd.nextFloat() * 0.12F);
        }
    }

    /** Is anybody near enough to this spot for it to be worth moving blocks there? */
    private static boolean watched(ServerLevel level, double x, double z, double reach) {
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz < reach * reach) return true;
        }
        return false;
    }

    /** How long one lasts, in seconds. */
    public static int seconds() {
        return WakingConfig.earthquakeSeconds();
    }

    /**
     * Ground a quake leaves alone: a colony's land, and the country round the world spawn and round
     * every online player's bed.
     *
     * <p>A player on Discord building towns under volcanoes, in a pack with no colonies in it, asked
     * for this. A colony's land was already spared the fault; a player's own home never was, and
     * nothing at all was spared the wave or the shove. The shaking, the dust and the noise still
     * reach a player at home - a quake you cannot feel from your bed is not a quake - but the ground
     * stays as it was and nobody standing on it is thrown about.</p>
     *
     * <p>It is asked for every block the fault walks over, so the homes are gathered once for each
     * second of shaking and the question itself is a few multiplications. The distance is measured
     * across the ground and not through the air: a bed at the top of a tower keeps the tower's
     * footing as safe as its roof.</p>
     */
    public static final class Shelter {
        /** How far a block the fault throws out to the side can carry before it comes down. */
        public static final int THROW = 6;

        private final int radius;
        private final int[] xs;
        private final int[] zs;

        /** These homes, kept {@code radius} blocks round. The headless probe builds its own. */
        public Shelter(int radius, List<BlockPos> homes) {
            this.radius = radius;
            this.xs = new int[homes.size()];
            this.zs = new int[homes.size()];
            for (int i = 0; i < homes.size(); i++) {
                xs[i] = homes.get(i).getX();
                zs[i] = homes.get(i).getZ();
            }
        }

        /** What this level has to keep safe right now, {@code earthquakeSafeRadius} round each of it. */
        public static Shelter of(ServerLevel level) {
            return new Shelter(WakingConfig.earthquakeSafeRadius(), homes(level));
        }

        /**
         * The world spawn, and the bed or anchor of every player online whose respawn point is in this
         * level. A player who is offline is not asked: the server does not hold their respawn point.
         */
        public static List<BlockPos> homes(ServerLevel level) {
            return homes(level, level.players());
        }

        /**
         * The same, with the players named. They are a parameter for the headless probe, whose fake
         * players are never in {@code level.players()}.
         */
        public static List<BlockPos> homes(ServerLevel level, List<? extends ServerPlayer> players) {
            List<BlockPos> homes = new ArrayList<>();
            if (level.dimension() == Level.OVERWORLD) homes.add(level.getSharedSpawnPos());   // the world spawn is an overworld place
            for (ServerPlayer p : players) {
                BlockPos bed = p.getRespawnPosition();
                if (bed != null && p.getRespawnDimension() == level.dimension()) homes.add(bed);
            }
            return homes;
        }

        /** True where the quake must leave the ground, and whoever stands on it, alone. */
        public boolean holds(ServerLevel level, BlockPos at) {
            return holds(level, at, 0);
        }

        /** The same, with the sheltered ground reaching {@code margin} blocks further out. */
        public boolean holds(ServerLevel level, BlockPos at, int margin) {
            return nearHome(at.getX(), at.getZ(), margin) || Colonies.keepOff(level, at, margin);
        }

        /** Within the radius (and the margin) of one of the homes, across the ground. Colonies are not asked. */
        public boolean nearHome(int x, int z, int margin) {
            if (radius <= 0) return false;                    // 0 is off, whatever the margin
            long r = radius + Math.max(0, margin);
            for (int i = 0; i < xs.length; i++) {
                long dx = x - xs[i], dz = z - zs[i];
                if (dx * dx + dz * dz < r * r) return true;
            }
            return false;
        }
    }
}
