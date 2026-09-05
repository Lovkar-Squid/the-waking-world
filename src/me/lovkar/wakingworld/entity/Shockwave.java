package me.lovkar.wakingworld.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * A ring of force racing outwards along the ground from a stomp, a slam or a landing. Server-side
 * only: each tick it grows, throws up the ground it crosses (dust of whatever is actually there,
 * and now and then a block that hops), and launches every living thing standing in the band it
 * just swept - hardest near the centre, gentler far out. Jump: if your feet are more than a block
 * and a half off the ground when it passes, it goes under you.
 */
public final class Shockwave {
    private final ColossusEntity owner;
    private final Vec3 center;
    private final double maxRadius;
    private final double speed;
    private final float damage;
    private final double launch;
    private double radius;
    private final Set<Integer> hit = new HashSet<>();
    // an arc instead of a full ring: unit direction and the cosine of the half-angle (null = ring)
    private double arcX, arcZ, arcCos = -2;
    private net.minecraft.core.particles.ParticleOptions particle;
    private boolean water;

    public Shockwave(ColossusEntity owner, Vec3 center, double maxRadius, double speed, float damage, double launch) {
        this.owner = owner;
        this.center = center;
        this.maxRadius = maxRadius;
        this.speed = speed;
        this.damage = damage;
        this.launch = launch;
        this.radius = 0.5;
    }

    /** Only the part of the ring within halfAngleDeg of the direction acts. */
    public Shockwave arc(Vec3 dir, double halfAngleDeg) {
        double f = Math.max(1e-6, dir.horizontalDistance());
        this.arcX = dir.x / f;
        this.arcZ = dir.z / f;
        this.arcCos = Math.cos(Math.toRadians(halfAngleDeg));
        return this;
    }

    /** A wave of water rather than of the ground: spray instead of dust, no blocks hop, fire goes out. */
    public Shockwave water(net.minecraft.core.particles.ParticleOptions particle) {
        this.water = true;
        this.particle = particle;
        return this;
    }

    private boolean inArc(double dx, double dz) {
        if (arcCos <= -1.5) return true;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.5) return true;
        return (dx / d) * arcX + (dz / d) * arcZ >= arcCos;
    }

    /** @return false when the wave has run its course. */
    public boolean tick(ServerLevel level) {
        double inner = radius;
        radius = Math.min(maxRadius, radius + speed);
        double outer = radius;
        double strength = Math.max(0.4, 1.0 - outer / (maxRadius + 1.0));

        // the ground it crosses: dust along the ring, denser and taller near the centre
        int points = (int) Math.max(12, outer * 2.6);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points + (outer * 0.37);
            double px = center.x + Math.cos(a) * outer, pz = center.z + Math.sin(a) * outer;
            if (!inArc(px - center.x, pz - center.z)) continue;
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(px, center.y, pz));
            if (Math.abs(ground.getY() - center.y) > 8) continue;
            BlockState below = level.getBlockState(ground.below());
            if (below.isAir()) continue;
            if (water) {
                level.sendParticles(particle != null ? particle : ParticleTypes.SPLASH, px, ground.getY() + 0.3, pz, (int) Math.ceil(12 * strength), 0.5, 0.8 + strength, 0.5, 0.35);
                if (i % 3 == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, px, ground.getY() + 0.8, pz, 4, 0.5, 0.6, 0.5, 0.1);
                BlockState top = level.getBlockState(ground);
                if (top.is(net.minecraft.world.level.block.Blocks.FIRE)) level.removeBlock(ground, false);
                continue;
            }
            if (!below.getFluidState().isEmpty()) {
                // across water the wave is a ring of spray
                level.sendParticles(ParticleTypes.SPLASH, px, ground.getY() + 0.2, pz, (int) Math.ceil(14 * strength), 0.5, 0.6 + strength, 0.5, 0.3);
                continue;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), px, ground.getY() + 0.4, pz,
                    (int) Math.ceil(4 * strength), 0.4, 0.9 + strength, 0.4, 0.35);
            if (i % 2 == 0) level.sendParticles(ParticleTypes.POOF, px, ground.getY() + 0.5, pz, 2, 0.3, 0.4, 0.3, 0.05);
            if (i % 7 == 0) level.sendParticles(ParticleTypes.CLOUD, px, ground.getY() + 0.6, pz, 1, 0.3, 0.3, 0.3, 0.08);
            // the odd block hops with the wave (lands back where it was, or next to it) - but not the
            // ground under the giant's own feet
            boolean underOwner = Math.abs(px - owner.getX()) < 3.5 && Math.abs(pz - owner.getZ()) < 3.5;
            if (!underOwner && level.random.nextInt(14) == 0 && Crater.trampleable(level, ground.below(), below) && below.canOcclude()) {
                Crater.fling(level, ground.below(), below, center, 0.15 * strength, level.random);
            }
        }

        // everything standing in the band between the old and the new radius
        AABB area = new AABB(center.x - outer, center.y - 4, center.z - outer, center.x + outer, center.y + 6, center.z + outer);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
            if (hit.contains(target.getId())) continue;
            double dx = target.getX() - center.x, dz = target.getZ() - center.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < inner - 1.0 || d > outer + target.getBbWidth()) continue;
            if (!inArc(dx, dz)) continue;
            // airborne? the wave passes underneath
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, target.blockPosition());
            if (!target.onGround() && target.getY() > ground.getY() + 1.5) continue;
            hit.add(target.getId());
            if (damage > 0) target.hurt(level.damageSources().mobAttack(owner), (float) (damage * strength));
            double nx = d < 0.01 ? 0 : dx / d, nz = d < 0.01 ? 0 : dz / d;
            target.setDeltaMovement(target.getDeltaMovement().add(nx * 0.9 * strength, launch * (0.6 + 0.4 * strength), nz * 0.9 * strength));
            target.hurtMarked = true;
        }
        return radius < maxRadius - 0.01;
    }
}
