package me.lovkar.wakingworld.ritual;

import me.lovkar.wakingworld.particle.WakingParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The ceremonies - what the land does while a rite runs, kind by kind, on the server (particles,
 * sounds, blocks laid down for a while). The beam over the altar and the floating offerings are
 * the client's part (AltarRenderer). p = 0 at the start, 1 at the climax.
 */
public final class Ceremony {
    private Ceremony() {
    }

    /** Pillars of the Titan's arena: this far from its altar, at these angles (TitanArenaPiece). */
    static final double PILLAR_RING = 42;
    /** The first pillar lights at this tick of the rite, the rest every 12 - after the six lesser altars have answered. */
    public static final int PILLAR_LIGHT = 116;

    static void tick(ServerLevel server, AltarBlockEntity altar, String kind, BlockPos pos, int elapsed, int total) {
        RandomSource rnd = server.random;
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        float p = elapsed / (float) total;
        int color = Rites.color(kind);

        // what every rite shares: runes climbing in a widening ring, sparks off the altar, a chime that rises
        if (elapsed % 2 == 0) {
            double r = 2.5 + 3.0 * p;
            for (int i = 0; i < 2; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                server.sendParticles(WakingParticles.rune(color, 1f + p), c.x + Math.cos(a) * r, c.y - 0.3 + rnd.nextDouble() * 0.5, c.z + Math.sin(a) * r, 1, 0, 0.04 + 0.06 * p, 0, 0.01);
            }
            server.sendParticles(WakingParticles.ember(color, 1f), c.x, c.y + 0.4, c.z, 3, 0.2, 0.1, 0.2, 0.06 + 0.1 * p);
        }
        if (elapsed % 8 == 0) sound(server, c, SoundEvents.AMETHYST_BLOCK_CHIME, 2.5F, 0.5F + p * 1.0F);
        if (elapsed % 40 == 0) sound(server, c, SoundEvents.BEACON_AMBIENT, 3.0F, 0.6F + p * 0.6F);
        if (elapsed % 25 == 0) ring(server, c, color, 4 + 10 * p, 0.5);
        if (p > 0.8 && elapsed % 5 == 0) server.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 6, 0.4, 1.5, 0.4, 0.05);

        switch (kind) {
            case "stone" -> stone(server, altar, pos, c, elapsed, p, color, rnd);
            case "earth" -> earth(server, altar, pos, c, elapsed, p, color, rnd);
            case "sandstone" -> sand(server, c, elapsed, p, rnd);
            case "ice" -> ice(server, altar, pos, c, elapsed, p, rnd);
            case "prismarine" -> sea(server, c, elapsed, p, rnd);
            case "moss" -> moss(server, altar, pos, c, elapsed, p, rnd);
            case "titan" -> titan(server, altar, pos, c, elapsed, p, rnd);
            default -> {
            }
        }
    }

    static void climax(ServerLevel server, AltarBlockEntity altar, String kind, BlockPos pos, boolean woken) {
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        int color = Rites.color(kind);
        RandomSource rnd = server.random;
        ring(server, c, color, 18, 1.2);
        server.sendParticles(WakingParticles.rune(color, 2f), c.x, c.y + 1, c.z, 60, 4, 2, 4, 0.1);
        server.sendParticles(WakingParticles.ember(color, 1.4f), c.x, c.y + 1, c.z, 120, 0.5, 0.5, 0.5, 0.6);
        server.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2, c.z, 80, 1.0, 6.0, 1.0, 0.1);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        sound(server, c, SoundEvents.BEACON_POWER_SELECT, 6.0F, 0.7F);
        sound(server, c, SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.4F);
        switch (kind) {
            case "stone" -> {
                lightningRing(server, c, 7, 3, rnd);
                sound(server, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.6F);
                sound(server, c, SoundEvents.DEEPSLATE_BREAK, 6.0F, 0.3F);
            }
            case "earth" -> {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), c.x, c.y, c.z, 150, 5, 1, 5, 0.3);
                server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 2, c.z, 100, 6, 2, 6, 0.05);
                sound(server, c, SoundEvents.ROOTED_DIRT_BREAK, 6.0F, 0.5F);
            }
            case "sandstone" -> {
                for (int i = 0; i < 14; i++) server.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), c.x, c.y + i, c.z, 20, 1.2, 0.3, 1.2, 0.2);
                sound(server, c, SoundEvents.SAND_BREAK, 6.0F, 0.5F);
                sound(server, c, SoundEvents.ELYTRA_FLYING, 5.0F, 0.6F);
            }
            case "ice" -> {
                server.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1, c.z, 200, 6, 3, 6, 0.15);
                sound(server, c, SoundEvents.GLASS_BREAK, 6.0F, 0.5F);
                sound(server, c, SoundEvents.PLAYER_HURT_FREEZE, 5.0F, 0.5F);
            }
            case "prismarine" -> {
                server.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 1, c.z, 200, 5, 3, 5, 0.1);
                server.sendParticles(ParticleTypes.NAUTILUS, c.x, c.y + 1, c.z, 120, 5, 3, 5, 0.3);
                sound(server, c, SoundEvents.CONDUIT_ACTIVATE, 6.0F, 0.7F);
            }
            case "moss" -> {
                server.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1, c.z, 150, 6, 3, 6, 0.1);
                server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 2, c.z, 100, 6, 2, 6, 0.05);
                sound(server, c, SoundEvents.AZALEA_LEAVES_PLACE, 6.0F, 0.5F);
                sound(server, c, SoundEvents.SPORE_BLOSSOM_PLACE, 6.0F, 0.4F);
            }
            case "titan" -> {
                lightningRing(server, c, 30, 8, rnd);
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 2, c.z, 400, 6, 4, 6, 0.5);
                server.sendParticles(ParticleTypes.DRAGON_BREATH, c.x, c.y + 1, c.z, 150, 8, 1, 8, 0.05);
                sound(server, c, SoundEvents.END_PORTAL_SPAWN, 8.0F, 0.5F);
                sound(server, c, SoundEvents.WITHER_SPAWN, 6.0F, 0.3F);
            }
            default -> {
            }
        }
    }

    // ---- the kinds ----------------------------------------------------------------------------

    /** Standing stones: the monoliths take light one after another, the ground shudders, thunder at the end. */
    private static void stone(ServerLevel server, AltarBlockEntity altar, BlockPos pos, Vec3 c, int elapsed, float p, int color, RandomSource rnd) {
        for (int i = 0; i < 8; i++) {
            if (elapsed != 20 + i * 20) continue;
            double a = i * Math.PI / 4;
            int mx = (int) Math.round(Math.cos(a) * 7), mz = (int) Math.round(Math.sin(a) * 7);
            BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos.offset(mx, 0, mz));
            altar.placeTemp(server, top, Blocks.END_ROD.defaultBlockState(), 600);
            Vec3 t = Vec3.atCenterOf(top);
            server.sendParticles(ParticleTypes.END_ROD, t.x, t.y, t.z, 25, 0.3, 0.6, 0.3, 0.08);
            server.sendParticles(WakingParticles.rune(color, 1.5f), t.x, t.y - 1, t.z, 10, 0.4, 1.0, 0.4, 0.02);
            sound(server, t, SoundEvents.AMETHYST_CLUSTER_PLACE, 4.0F, 0.6F + i * 0.07F);
            sound(server, t, SoundEvents.DEEPSLATE_BREAK, 3.0F, 0.3F);
        }
        if (p > 0.5 && elapsed % 20 == 0) {
            sound(server, c, SoundEvents.DEEPSLATE_BREAK, 4.0F, 0.25F);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), c.x, c.y - 0.5, c.z, 30, 5, 0.3, 5, 0.15);
        }
    }

    /** The barrow: moss and grass creep out over the mound, spores drift, roots rustle. */
    private static void earth(ServerLevel server, AltarBlockEntity altar, BlockPos pos, Vec3 c, int elapsed, float p, int color, RandomSource rnd) {
        if (elapsed > 20 && elapsed % 5 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble() * (3 + 7 * p);
            BlockPos g = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
            BlockState below = server.getBlockState(g.below());
            if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.COARSE_DIRT) || below.is(Blocks.ROOTED_DIRT) || below.is(Blocks.MOSS_BLOCK)) {
                int h = rnd.nextInt(10);
                BlockState plant = h < 5 ? Blocks.MOSS_CARPET.defaultBlockState() : h < 8 ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.FERN.defaultBlockState();
                altar.placeTemp(server, g, plant, 1200);
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (elapsed % 3 == 0) server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 2, c.z, 3, 4 + 4 * p, 1.5, 4 + 4 * p, 0.02);
        if (elapsed % 30 == 15) sound(server, c, SoundEvents.ROOTED_DIRT_PLACE, 3.0F, 0.5F + p * 0.4F);
    }

    /** The tomb: a whirl of sand tightens around the altar and climbs. */
    private static void sand(ServerLevel server, Vec3 c, int elapsed, float p, RandomSource rnd) {
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
        double r = 7 - 4.5 * p;
        for (int k = 0; k < 6; k++) {
            double a = elapsed * 0.32 + k * Math.PI / 3;
            double h = ((elapsed * 0.18 + k * 1.1) % 7);
            server.sendParticles(dust, c.x + Math.cos(a) * r, c.y - 0.5 + h, c.z + Math.sin(a) * r, 2, 0.2, 0.2, 0.2, 0.02);
        }
        if (elapsed % 2 == 0) server.sendParticles(ParticleTypes.ASH, c.x, c.y + 2, c.z, 4, r, 3, r, 0.02);
        if (elapsed % 6 == 0) sound(server, c, SoundEvents.SAND_BREAK, 1.5F, 0.6F + p * 0.6F);
        if (p > 0.6 && elapsed % 10 == 0) sound(server, c, SoundEvents.ELYTRA_FLYING, 2.0F, 0.5F + p * 0.5F);
    }

    /** The cairn: frost spreads over the ground, spikes of ice come up, snow whirls. */
    private static void ice(ServerLevel server, AltarBlockEntity altar, BlockPos pos, Vec3 c, int elapsed, float p, RandomSource rnd) {
        if (elapsed % 4 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 1 + rnd.nextDouble() * (1 + 8 * p);
            BlockPos g = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
            if (server.getBlockState(g.below()).isSolidRender(server, g.below())) altar.placeTemp(server, g, Blocks.SNOW.defaultBlockState(), 900);
        }
        if (elapsed > 50 && elapsed % 35 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 3 + rnd.nextDouble() * 4;
            BlockPos g = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
            int h = 2 + rnd.nextInt(3);
            for (int i = 0; i < h; i++) altar.placeTemp(server, g.above(i), (i == h - 1 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 800);
            sound(server, Vec3.atCenterOf(g), SoundEvents.GLASS_PLACE, 3.0F, 0.5F);
            server.sendParticles(ParticleTypes.SNOWFLAKE, g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5, 20, 0.5, 1.0, 0.5, 0.05);
        }
        double r = 6 - 3 * p;
        for (int k = 0; k < 3; k++) {
            double a = -elapsed * 0.25 + k * Math.PI * 2 / 3;
            server.sendParticles(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * r, c.y + (elapsed * 0.1 + k * 2) % 6, c.z + Math.sin(a) * r, 2, 0.2, 0.2, 0.2, 0.01);
        }
        if (elapsed % 2 == 0) server.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 2, c.z, 3, 5, 2, 5, 0.01);
        if (elapsed % 20 == 10) sound(server, c, SoundEvents.POWDER_SNOW_STEP, 2.5F, 0.4F + p * 0.5F);
    }

    /** The sunken shrine: a column of bubbles, shells of light spinning up, the sea humming. */
    private static void sea(ServerLevel server, Vec3 c, int elapsed, float p, RandomSource rnd) {
        server.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x, c.y, c.z, 4, 0.4, 0.5, 0.4, 0.1);
        server.sendParticles(ParticleTypes.BUBBLE, c.x, c.y + 1, c.z, 3, 0.6, 1.5, 0.6, 0.05);
        double r = 5 - 3 * p;
        for (int k = 0; k < 4; k++) {
            double a = elapsed * 0.2 + k * Math.PI / 2;
            server.sendParticles(ParticleTypes.NAUTILUS, c.x + Math.cos(a) * r, c.y + (elapsed * 0.12 + k) % 5, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (elapsed % 3 == 0) server.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1.5, c.z, 2, 4, 2, 4, 0.02);
        if (elapsed % 20 == 0) sound(server, c, SoundEvents.CONDUIT_AMBIENT, 3.0F, 0.6F + p * 0.5F);
        if (elapsed % 45 == 20) sound(server, c, SoundEvents.CONDUIT_ATTACK_TARGET, 2.0F, 0.5F);
    }

    /** The sanctum: flowers open across the floor, fireflies gather, spores drift. */
    private static void moss(ServerLevel server, AltarBlockEntity altar, BlockPos pos, Vec3 c, int elapsed, float p, RandomSource rnd) {
        if (elapsed > 10 && elapsed % 5 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 1.5 + rnd.nextDouble() * (2 + 7 * p);
            BlockPos g = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
            BlockState below = server.getBlockState(g.below());
            if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.MOSSY_COBBLESTONE) || below.is(Blocks.MOSSY_STONE_BRICKS)) {
                BlockState[] flowers = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.BLUE_ORCHID.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(),
                        Blocks.PINK_TULIP.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZALEA.defaultBlockState(), Blocks.FLOWERING_AZALEA.defaultBlockState(), Blocks.MOSS_CARPET.defaultBlockState()};
                BlockState plant = flowers[rnd.nextInt(flowers.length)];
                if (below.is(Blocks.MOSSY_COBBLESTONE) || below.is(Blocks.MOSSY_STONE_BRICKS)) plant = Blocks.MOSS_CARPET.defaultBlockState();
                altar.placeTemp(server, g, plant, 1500);
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, g.getX() + 0.5, g.getY() + 0.4, g.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.05);
            }
        }
        if (elapsed % 2 == 0) server.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1.5, c.z, 2, 3 + 4 * p, 1.5, 3 + 4 * p, 0.02);
        if (elapsed % 3 == 0) server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 2.5, c.z, 2, 5, 1.5, 5, 0.01);
        if (elapsed % 24 == 12) sound(server, c, SoundEvents.AZALEA_LEAVES_PLACE, 2.5F, 0.6F + p * 0.5F);
    }

    /** When the lesser altar of land {@code i} answers the horn, in ticks of the rite (and the renderer's beam with it). */
    public static int lesserAnswer(int i) {
        return 30 + i * 14;
    }

    /**
     * The arena: the six lesser altars answer one after another - each in the colour of its land, its
     * runes streaming to the great altar - then the pillars take light one by one, the void pools
     * and spins, the End holds its breath.
     */
    private static void titan(ServerLevel server, AltarBlockEntity altar, BlockPos pos, Vec3 c, int elapsed, float p, RandomSource rnd) {
        for (int i = 0; i < Rites.LANDS.length; i++) {
            int[] o = me.lovkar.wakingworld.worldgen.TitanArenaPiece.lesserOffset(i);
            Vec3 l = new Vec3(c.x + o[0], c.y + me.lovkar.wakingworld.worldgen.TitanArenaPiece.LESSER_DY + 1.2, c.z + o[1]);
            int color = Rites.color(Rites.LANDS[i]);
            if (elapsed == lesserAnswer(i)) {
                server.sendParticles(WakingParticles.rune(color, 1.6f), l.x, l.y, l.z, 30, 1.0, 1.5, 1.0, 0.05);
                server.sendParticles(WakingParticles.ember(color, 1.2f), l.x, l.y, l.z, 40, 0.4, 0.4, 0.4, 0.3);
                ring(server, l.add(0, -0.7, 0), color, 5, 0.6);
                sound(server, l, SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 0.6F + i * 0.1F);
                sound(server, l, SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.8F + i * 0.08F);
            }
            // lit: its runes stream across to the great altar, thicker as the rite climbs
            if (elapsed > lesserAnswer(i) && elapsed % 3 == 0) {
                double f = rnd.nextDouble();
                Vec3 at = l.add(c.subtract(l).scale(f)).add(0, 0.8 + f * 2.5, 0);
                server.sendParticles(WakingParticles.rune(color, 0.8f + p * 0.8f), at.x, at.y, at.z, 1, 0.15, 0.15, 0.15, 0.01);
            }
        }
        for (int i = 0; i < 8; i++) {
            if (elapsed != PILLAR_LIGHT + i * 12) continue;
            double a = Math.toRadians(22.5 + 45 * i);
            BlockPos column = BlockPos.containing(c.x + Math.cos(a) * PILLAR_RING, c.y, c.z + Math.sin(a) * PILLAR_RING);
            BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            Vec3 t = Vec3.atCenterOf(top);
            server.sendParticles(ParticleTypes.END_ROD, t.x, t.y, t.z, 40, 0.4, 1.0, 0.4, 0.12);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, t.x, t.y, t.z, 60, 0.6, 1.5, 0.6, 0.2);
            sound(server, t, SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.5F + i * 0.08F);
            sound(server, t, SoundEvents.RESPAWN_ANCHOR_CHARGE, 4.0F, 0.6F);
        }
        double r = 20 - 16 * p;
        for (int k = 0; k < 5; k++) {
            double a = elapsed * 0.15 + k * Math.PI * 2 / 5;
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + (elapsed * 0.1 + k * 1.5) % 8, c.z + Math.sin(a) * r, 3, 0.3, 0.3, 0.3, 0.05);
        }
        if (elapsed % 2 == 0) server.sendParticles(ParticleTypes.DRAGON_BREATH, c.x, c.y, c.z, 2, r * 0.6, 0.3, r * 0.6, 0.01);
        if (elapsed % 30 == 0) sound(server, c, SoundEvents.ENDERMAN_STARE, 4.0F, 0.5F + p * 0.3F);
        if (elapsed == 100) sound(server, c, SoundEvents.PORTAL_TRIGGER, 6.0F, 0.5F);
        if (p > 0.7 && elapsed % 12 == 0) sound(server, c, SoundEvents.WARDEN_HEARTBEAT, 5.0F, 0.6F + p * 0.6F);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static void sound(ServerLevel server, Vec3 at, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void ring(ServerLevel server, Vec3 c, int color, double radius, double speed) {
        server.sendParticles(WakingParticles.ring(color, (float) (radius / 3.0)), c.x, c.y - 0.45, c.z, 0, 0, speed, 0, 1.0);
    }

    private static void lightningRing(ServerLevel server, Vec3 c, double radius, int count, RandomSource rnd) {
        double offset = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = offset + i * Math.PI * 2 / count;
            BlockPos g = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius));
            if (g.getY() <= server.getMinBuildHeight()) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt == null) return;
            bolt.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
    }
}
