package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.WakingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The signature moves - what makes an ice giant fight differently from a moss giant. Server-side
 * effects only; the poses live in ColossusPose, the choice and the cooldowns in ColossusEntity.
 * Blocks these moves raise (spikes, roots, sand) are temporary: the giant takes them away again
 * ({@link ColossusEntity#tempBlock}).
 */
final class Signature {
    private Signature() {
    }

    /** Which signature moves a kind has, with their weights in the close / mid / long bands. */
    static void weights(String kind, java.util.Map<Attack, Integer> w, int band, int phase) {
        // band 0 = close (<= 0.36 H), 1 = mid (<= 0.7 H), 2 = long
        switch (kind) {
            case "ice" -> {
                if (band >= 1) w.put(Attack.FROST_BREATH, band == 1 ? 26 : 20);
                if (band <= 1) w.put(Attack.ICE_SPIKES, band == 0 ? 16 : 24);
            }
            case "sandstone" -> {
                if (band >= 1) w.put(Attack.SANDSTORM, band == 1 ? 26 : 22);
                if (band <= 1) w.put(Attack.SAND_GEYSER, band == 0 ? 14 : 24);
            }
            case "prismarine" -> {
                if (band <= 1) w.put(Attack.TIDAL_WAVE, band == 0 ? 26 : 20);
                if (band >= 1) w.put(Attack.WATER_JET, band == 1 ? 24 : 26);
            }
            case "moss" -> {
                if (band <= 1) w.put(Attack.GRASPING_ROOTS, band == 0 ? 18 : 26);
                if (band <= 1) w.put(Attack.SPORE_CLOUD, band == 0 ? 20 : 12);
            }
            case "titan" -> {
                if (band >= 1) w.put(Attack.ROCKFALL, 20);
                if (band <= 1) w.put(Attack.QUAKE, 22);
                if (band <= 1) w.put(Attack.ICE_SPIKES, 18);
                if (band >= 1) w.put(Attack.WATER_JET, 22);
                if (band <= 1) w.put(Attack.SPORE_CLOUD, 16);
            }
            default -> { // stone, earth
                if (band >= 1) w.put(Attack.ROCKFALL, band == 1 ? 16 : 24);
                if (band <= 1) w.put(Attack.QUAKE, band == 0 ? 12 : 24);
            }
        }
    }

    static int cooldown(Attack a) {
        return switch (a) {
            case FROST_BREATH, SANDSTORM, WATER_JET -> 200;
            case ICE_SPIKES, QUAKE, SAND_GEYSER -> 180;
            case TIDAL_WAVE, GRASPING_ROOTS -> 220;
            case SPORE_CLOUD, ROCKFALL -> 240;
            default -> 100;
        };
    }

    /** Runs every tick of a signature attack. */
    static void tick(ColossusEntity g, ServerLevel server, Attack a, int t) {
        double h = g.bodyHeight();
        LivingEntity target = g.getTarget();
        switch (a) {
            case FROST_BREATH -> breath(g, server, t, h, target, false);
            case WATER_JET -> breath(g, server, t, h, target, true);
            case SANDSTORM -> sandstorm(g, server, t, h, target);
            case ROCKFALL -> rockfall(g, server, t, h, target);
            case QUAKE -> quake(g, server, t, h);
            case ICE_SPIKES -> spikes(g, server, t, h);
            default -> { }
        }
    }

    /** The impact tick of a signature attack. */
    static void impact(ColossusEntity g, ServerLevel server, Attack a) {
        double h = g.bodyHeight();
        LivingEntity target = g.getTarget();
        float dmg = (float) g.getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (a) {
            case SAND_GEYSER -> geyser(g, server, h, target, dmg);
            case TIDAL_WAVE -> tidalWave(g, server, h, dmg);
            case GRASPING_ROOTS -> roots(g, server, h, target, dmg);
            case SPORE_CLOUD -> spores(g, server, h);
            case QUAKE, ICE_SPIKES -> {
                // the fists / the fist land: the line starts here (see tick)
                Vec3 at = g.bodyPoint(0, 0, -0.22 * h);
                Crater.blast(server, at, 0.05 * h, 10, 0.5, 2, g.getRandom());
                g.groundBurst(server, at, 50, 2.5);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.45F);
                g.lineStart = at;
                g.lineDir = target != null ? target.position().subtract(at) : g.facing();
                g.lineDir = new Vec3(g.lineDir.x, 0, g.lineDir.z);
                if (g.lineDir.horizontalDistance() < 0.01) g.lineDir = g.facing();
                g.lineDir = g.lineDir.normalize();
                g.lineHit.clear();
            }
            default -> { }
        }
    }

    // ---- ice / prismarine / titan: a stream from the mouth --------------------------------------

    /** A cone of frost (or a jet of water / of the void) from the mouth, ticks 20-56: slows and freezes, or hoses you away. */
    private static void breath(ColossusEntity g, ServerLevel server, int t, double h, LivingEntity target, boolean jet) {
        if (t < 20 || t > 56) return;
        Vec3 mouth = g.bodyPoint(0, 0.70 * h, -0.30 * h);
        Vec3 dir;
        if (target != null) {
            dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mouth).normalize();
        } else {
            dir = g.facing().add(0, -0.25, 0).normalize();
        }
        double reach = jet ? 1.1 * h : 0.9 * h;
        String kind = g.palette().kind;
        boolean titan = kind.equals("titan");
        ParticleOptions p1 = jet ? (titan ? ParticleTypes.DRAGON_BREATH : ParticleTypes.SPLASH) : ParticleTypes.SNOWFLAKE;
        ParticleOptions p2 = jet ? (titan ? ParticleTypes.PORTAL : ParticleTypes.BUBBLE_POP) : ParticleTypes.CLOUD;
        // the stream: particles along the line, spreading with distance
        for (int i = 0; i < 18; i++) {
            double d = g.getRandom().nextDouble() * reach;
            double spread = (jet ? 0.04 : 0.16) * d + 0.6;
            Vec3 at = mouth.add(dir.scale(d)).add((g.getRandom().nextDouble() - 0.5) * spread, (g.getRandom().nextDouble() - 0.5) * spread, (g.getRandom().nextDouble() - 0.5) * spread);
            server.sendParticles(i % 3 == 0 ? p2 : p1, at.x, at.y, at.z, 1, 0, 0, 0, jet ? 0.5 : 0.15);
        }
        if (t % 8 == 0) server.playSound(null, mouth.x, mouth.y, mouth.z, jet ? SoundEvents.PLAYER_SPLASH_HIGH_SPEED : SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 4.0F, jet ? 0.5F : 0.4F);
        if (t == 20) server.playSound(null, mouth.x, mouth.y, mouth.z, jet ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5.0F, jet ? 0.8F : 0.35F);
        // who is in the stream
        double halfAngle = jet ? 0.08 : 0.30;
        AABB box = new AABB(mouth, mouth.add(dir.scale(reach))).inflate(h * 0.3);
        float dmg = (float) g.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != g && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(mouth);
            double d = to.length();
            if (d < 1 || d > reach) continue;
            double off = to.normalize().subtract(dir).length(); // chord ~ angle for small angles
            if (off > halfAngle + 1.5 / d) continue;
            if (jet) {
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.55, 0.08 + Math.max(0, dir.y) * 0.3, dir.z * 0.55));
                e.hurtMarked = true;
                if (t % 5 == 0) e.hurt(g.damageSources().mobAttack(g), titan ? dmg * 0.25F : dmg * 0.12F);
                if (titan && t % 10 == 0) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + 12));
                if (t % 10 == 0) e.hurt(g.damageSources().freeze(), dmg * 0.15F);
            }
        }
        // the world it touches: frost freezes water and lays snow, the jet splashes
        if (!jet && t % 2 == 0 && WakingConfig.terrainDamage()) {
            for (int i = 0; i < 4; i++) {
                double d = 0.2 * h + g.getRandom().nextDouble() * (reach - 0.2 * h);
                Vec3 at = mouth.add(dir.scale(d)).add((g.getRandom().nextDouble() - 0.5) * d * 0.5, 0, (g.getRandom().nextDouble() - 0.5) * d * 0.5);
                BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
                BlockPos below = top.below();
                BlockState bs = server.getBlockState(below);
                if (!bs.getFluidState().isEmpty() && bs.getFluidState().isSource() && bs.is(Blocks.WATER)) {
                    g.tempBlock(server, below, Blocks.ICE.defaultBlockState(), 600);
                } else if (server.getBlockState(top).isAir() && bs.isFaceSturdy(server, below, net.minecraft.core.Direction.UP) && Blocks.SNOW.defaultBlockState().canSurvive(server, top)) {
                    g.tempBlock(server, top, Blocks.SNOW.defaultBlockState(), 900);
                }
            }
        }
    }

    // ---- sandstone ------------------------------------------------------------------------------

    /** A wall of sand rolling out from the giant for two seconds: blinding, choking, shoving. */
    private static void sandstorm(ColossusEntity g, ServerLevel server, int t, double h, LivingEntity target) {
        if (t < 20 || t > 64) return;
        double front = 0.15 * h + (t - 20) * 0.9;   // the front of the wall rolls out
        Vec3 dir = g.facing();
        Vec3 origin = g.bodyPoint(0, 0.35 * h, 0);
        double width = 0.25 * h + (t - 20) * 0.5;
        ParticleOptions sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 0; i < 26; i++) {
            double along = front - g.getRandom().nextDouble() * 6;
            double across = (g.getRandom().nextDouble() - 0.5) * 2 * width;
            double up = g.getRandom().nextDouble() * (0.35 * h);
            Vec3 at = origin.add(dir.scale(along)).add(side.scale(across));
            BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
            server.sendParticles(i % 4 == 0 ? ParticleTypes.CLOUD : sand, at.x, ground.getY() + up, at.z, 1, 0, 0, 0, 0.0);
        }
        if (t % 6 == 0) server.playSound(null, origin.x, origin.y, origin.z, SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 5.0F, 0.3F);
        if (t == 20) server.playSound(null, origin.x, origin.y, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5.0F, 0.5F);
        float dmg = (float) g.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB box = new AABB(origin, origin.add(dir.scale(front))).inflate(width, 0.4 * h, width);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != g && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
            Vec3 to = e.position().subtract(origin);
            double along = to.x * dir.x + to.z * dir.z;
            double across = Math.abs(to.x * side.x + to.z * side.z);
            if (along < 0 || along > front || across > width) continue;
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.12, 0.02, dir.z * 0.12));
            e.hurtMarked = true;
            if (t % 10 == 0) e.hurt(g.damageSources().mobAttack(g), dmg * 0.12F);
        }
    }

    /** Geysers of sand under the target and two more spots nearby: everything on them goes up, and the sand comes down as real blocks. */
    private static void geyser(ColossusEntity g, ServerLevel server, double h, LivingEntity target, float dmg) {
        Vec3 aim = target != null ? target.position() : g.position().add(g.facing().scale(0.5 * h));
        List<Vec3> spots = new ArrayList<>();
        spots.add(aim);
        for (int i = 0; i < 2; i++) {
            double a = g.getRandom().nextDouble() * Math.PI * 2, r = 5 + g.getRandom().nextDouble() * 8;
            spots.add(aim.add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        boolean titan = g.palette().kind.equals("titan");
        BlockState sand = titan ? Blocks.END_STONE.defaultBlockState() : Blocks.SAND.defaultBlockState();
        for (Vec3 s : spots) {
            BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(s));
            Vec3 at = new Vec3(s.x, ground.getY(), s.z);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5, 4, 3.5), e -> e != g && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
                e.hurt(g.damageSources().mobAttack(g), dmg * 0.6F);
                e.setDeltaMovement(e.getDeltaMovement().add((g.getRandom().nextDouble() - 0.5) * 0.4, 1.6, (g.getRandom().nextDouble() - 0.5) * 0.4));
                e.hurtMarked = true;
            }
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand), at.x, at.y + 1, at.z, 80, 1.5, 3.0, 1.5, 0.4);
            server.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 2, at.z, 20, 1.0, 2.0, 1.0, 0.1);
            if (WakingConfig.terrainDamage()) {
                for (int i = 0; i < 14; i++) {
                    BlockPos p = BlockPos.containing(at.x + (g.getRandom().nextDouble() - 0.5) * 3, at.y + 1 + g.getRandom().nextInt(2), at.z + (g.getRandom().nextDouble() - 0.5) * 3);
                    if (!server.isEmptyBlock(p)) continue;
                    FallingBlockEntity fb = me.lovkar.wakingworld.ruin.Ruin.fall(server, p, sand);
                    fb.time = 1;
                    fb.dropItem = false;
                    fb.setHurtsEntities(1.0F, 6);
                    fb.setDeltaMovement((g.getRandom().nextDouble() - 0.5) * 0.5, 0.9 + g.getRandom().nextDouble() * 0.6, (g.getRandom().nextDouble() - 0.5) * 0.5);
                }
            }
            server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 5.0F, 0.4F);
        }
    }

    // ---- prismarine ----------------------------------------------------------------------------

    /** Both arms sweep forward: a wave of water races out in the front arc. */
    private static void tidalWave(ColossusEntity g, ServerLevel server, double h, float dmg) {
        Vec3 at = g.bodyPoint(0, 0, -0.25 * h);
        boolean titan = g.palette().kind.equals("titan");
        g.waves.add(new Shockwave(g, at, 1.3 * h, 1.6, dmg * 0.7F, 1.1).arc(g.facing(), 70).water(titan ? ParticleTypes.DRAGON_BREATH : ParticleTypes.SPLASH));
        server.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 200, 0.2 * h, 2.0, 0.2 * h, 0.5);
        server.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 1, at.z, 60, 0.2 * h, 2.0, 0.2 * h, 0.2);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 7.0F, 0.4F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 7.0F, 0.35F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    // ---- moss ----------------------------------------------------------------------------------

    /** Roots burst out of the ground around the target and hold it for four seconds. */
    private static void roots(ColossusEntity g, ServerLevel server, double h, LivingEntity target, float dmg) {
        Vec3 aim = target != null ? target.position() : g.position().add(g.facing().scale(0.4 * h));
        BlockPos center = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(aim));
        boolean titan = g.palette().kind.equals("titan");
        BlockState root = titan ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.MANGROVE_ROOTS.defaultBlockState();
        // a ring two wide and three high, the inside left open; the roots come down again after 90 ticks
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < 1) continue;
                int height = ring == 1 ? 3 : 1 + g.getRandom().nextInt(2);
                if (ring == 2 && g.getRandom().nextInt(3) == 0) continue;
                BlockPos col = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
                if (Math.abs(col.getY() - center.getY()) > 3) continue;
                for (int y = 0; y < height; y++) {
                    BlockPos p = col.above(y);
                    BlockState there = server.getBlockState(p);
                    if (!there.isAir() && !there.canBeReplaced()) break;
                    g.tempBlock(server, p, root, 90 + y * 4);
                }
            }
        }
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(3.0, 3.0, 3.0), e -> e != g && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
            e.hurt(g.damageSources().mobAttack(g), dmg * 0.5F);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 4));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 0));
            if (titan) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
        }
        Vec3 at = Vec3.atCenterOf(center);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, root), at.x, at.y, at.z, 80, 2.5, 1.5, 2.5, 0.2);
        server.sendParticles(g.themeParticle(), at.x, at.y + 1, at.z, 40, 2.5, 1.5, 2.5, 0.1);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 5.0F, 0.4F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 5.0F, 0.3F);
        Vec3 hand = g.handPoint();
        Crater.blast(server, hand, 0.035 * h, 6, 0.4, 1, g.getRandom());
        g.groundBurst(server, hand, 30, 2.0);
    }

    /** Spore clouds (poison, nausea) - or for the Titan clouds of the End (levitation, wither). */
    private static void spores(ColossusEntity g, ServerLevel server, double h) {
        boolean titan = g.palette().kind.equals("titan");
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4 + g.getRandom().nextDouble(), r = 0.2 * h + g.getRandom().nextDouble() * 0.25 * h;
            Vec3 at = g.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
            AreaEffectCloud cloud = new AreaEffectCloud(server, at.x, ground.getY() + 0.2, at.z);
            cloud.setOwner(g);
            cloud.setRadius(5.5F);
            cloud.setRadiusOnUse(-0.3F);
            cloud.setWaitTime(10);
            cloud.setDuration(160);
            cloud.setRadiusPerTick(-5.5F / 160F);
            cloud.setParticle(titan ? ParticleTypes.DRAGON_BREATH : ParticleTypes.SPORE_BLOSSOM_AIR);
            if (titan) {
                cloud.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1));
                cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
            } else {
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                cloud.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            }
            server.addFreshEntity(cloud);
            server.sendParticles(g.themeParticle(), at.x, ground.getY() + 1.5, at.z, 40, 2.0, 1.5, 2.0, 0.05);
        }
        Vec3 mid = g.bodyPoint(0, 0.5 * h, 0);
        server.sendParticles(g.themeParticle(), mid.x, mid.y, mid.z, 150, 0.3 * h, 0.35 * h, 0.3 * h, 0.15);
        server.playSound(null, mid.x, mid.y, mid.z, SoundEvents.MOSS_BREAK, SoundSource.HOSTILE, 6.0F, 0.4F);
        server.playSound(null, mid.x, mid.y, mid.z, SoundEvents.SPORE_BLOSSOM_BREAK, SoundSource.HOSTILE, 6.0F, 0.5F);
    }

    // ---- stone / earth / titan -----------------------------------------------------------------

    /** For two seconds rocks of the giant's own kind fall out of the sky around the target. */
    private static void rockfall(ColossusEntity g, ServerLevel server, int t, double h, LivingEntity target) {
        if (t == 20) {
            Vec3 chest = g.bodyPoint(0, 0.62 * h, -0.14 * h);
            server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 7.0F, 0.3F);
            server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 6.0F, 0.3F);
        }
        if (t < 20 || t > 60) return;
        if (t % 8 == 0) {
            Vec3 chest = g.bodyPoint(0, 0.62 * h, -0.14 * h);
            server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 5.0F, 0.5F);
            g.groundBurst(server, chest, 10, 2.0);
        }
        if (!WakingConfig.terrainDamage()) return;
        Vec3 aim = target != null ? target.position() : g.position().add(g.facing().scale(0.5 * h));
        for (int i = 0; i < 2; i++) {
            double a = g.getRandom().nextDouble() * Math.PI * 2, r = g.getRandom().nextDouble() * 9;
            double x = aim.x + Math.cos(a) * r, z = aim.z + Math.sin(a) * r;
            BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, aim.y, z));
            BlockPos spawn = new BlockPos(ground.getX(), ground.getY() + 24 + g.getRandom().nextInt(8), ground.getZ());
            if (!server.isEmptyBlock(spawn)) continue;
            BlockState rock = g.palette().pick(g.getRandom());
            FallingBlockEntity fb = me.lovkar.wakingworld.ruin.Ruin.fall(server, spawn, rock);
            fb.time = 1;
            fb.dropItem = false;
            fb.setHurtsEntities(2.0F, 18);
            fb.setDeltaMovement((g.getRandom().nextDouble() - 0.5) * 0.1, -0.4, (g.getRandom().nextDouble() - 0.5) * 0.1);
            // a shadow of dust on the ground where it will land
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, ground.getY() + 0.2, z, 3, 0.6, 0.1, 0.6, 0.0);
        }
    }

    /** A fissure racing from the fists towards the target: craters, flying earth, and anyone on it goes up. */
    private static void quake(ColossusEntity g, ServerLevel server, int t, double h) {
        if (t <= 22 || t > 52 || g.lineStart == null) return;
        double d = (t - 22) * 2.2;
        if (d > 1.3 * h) return;
        Vec3 at = g.lineStart.add(g.lineDir.scale(d));
        BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        Vec3 p = new Vec3(at.x, ground.getY(), at.z);
        Crater.blast(server, p, 2.2, 6, 0.6, 2, g.getRandom());
        g.groundBurst(server, p, 20, 1.5);
        float dmg = (float) g.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(3.0, 3.0, 3.0), e -> e != g && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
            if (!g.lineHit.add(e.getId())) continue;
            e.hurt(g.damageSources().mobAttack(g), dmg * 0.6F);
            e.setDeltaMovement(e.getDeltaMovement().add(g.lineDir.x * 0.5, 1.1, g.lineDir.z * 0.5));
            e.hurtMarked = true;
        }
        if (t % 4 == 0) server.playSound(null, p.x, p.y, p.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 4.0F, 0.35F);
    }

    /** A line of spikes (ice, or obsidian for the Titan) erupting towards the target, each one launching whoever stands on it. */
    private static void spikes(ColossusEntity g, ServerLevel server, int t, double h) {
        if (t <= 24 || t > 50 || g.lineStart == null) return;
        int i = t - 25;
        double d = 3 + i * 2.4;
        if (d > 1.1 * h) return;
        boolean titan = g.palette().kind.equals("titan");
        BlockState spike = titan ? Blocks.OBSIDIAN.defaultBlockState() : (i % 3 == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState();
        Vec3 side = new Vec3(-g.lineDir.z, 0, g.lineDir.x);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 at = g.lineStart.add(g.lineDir.scale(d)).add(side.scale(s * (1.0 + g.getRandom().nextDouble() * 1.5)));
            BlockPos ground = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
            int height = 2 + g.getRandom().nextInt(4);
            for (int y = 0; y < height; y++) {
                BlockPos p = ground.above(y);
                BlockState there = server.getBlockState(p);
                if (!there.isAir() && !there.canBeReplaced()) break;
                g.tempBlock(server, p, spike, 140 - i * 2 + y * 3);
            }
            Vec3 c = Vec3.atCenterOf(ground);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, spike), c.x, c.y + 1, c.z, 20, 0.8, 1.5, 0.8, 0.2);
            server.sendParticles(g.themeParticle(), c.x, c.y + 2, c.z, 8, 0.8, 1.5, 0.8, 0.05);
            float dmg = (float) g.getAttributeValue(Attributes.ATTACK_DAMAGE);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(1.6, 4.0, 1.6), e -> e != g && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity))) {
                if (!g.lineHit.add(e.getId())) continue;
                e.hurt(g.damageSources().mobAttack(g), dmg * 0.7F);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
                e.hurtMarked = true;
                if (!titan) e.setTicksFrozen(e.getTicksFrozen() + 100);
            }
        }
        server.playSound(null, g.lineStart.x, g.lineStart.y, g.lineStart.z, titan ? SoundEvents.DEEPSLATE_BREAK : SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, titan ? 0.4F : 0.6F);
    }
}
