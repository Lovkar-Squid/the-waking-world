package me.lovkar.wakingworld.ritual;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.VoidGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * The End has one egg and the Titan's rite wants it, so the egg is not allowed to be lost. A
 * dropped egg does not burn, blow up or despawn, and one that goes over the edge of an island is
 * set back where it fell from ({@link VoidGuard}); an egg block that loses its footing and falls
 * is watched the same way. And a dragon that dies leaves an egg on the podium whenever none lies
 * there - not only the first one, as vanilla has it - so a lost egg can be won back. Both are
 * server config switches ({@code dragonEggIndestructible}, {@code dragonEggEveryDragon}).
 */
public final class DragonEggGuard {
    private DragonEggGuard() {
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !WakingConfig.dragonEggIndestructible()) return;
        Entity e = event.getEntity();
        if (e instanceof ItemEntity item && item.getItem().is(Items.DRAGON_EGG)) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime();
            if (level.dimension() == Level.END) watch(level, item);
        } else if (e instanceof FallingBlockEntity falling && falling.getBlockState().is(Blocks.DRAGON_EGG) && level.dimension() == Level.END) {
            watch(level, falling);
        }
    }

    /** Over the void the egg would be gone for good: it is pulled back onto the nearest ground if it falls. */
    private static void watch(ServerLevel level, Entity e) {
        Vec3 safe = VoidGuard.nearestGround(level, e.position(), 48, null);
        if (safe == null) safe = new Vec3(e.getX(), Math.max(e.getY(), level.getMinBuildHeight() + 60), e.getZ());
        VoidGuard.watch(e, safe);
    }

    /** A dragon has gone (dead, not despawned): if the podium stands bare, it leaves an egg there. */
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.END || !WakingConfig.dragonEggEveryDragon()) return;
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.dragonDeathTime <= 0 || dragon.getDragonFight() == null) return;
        BlockPos podium = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(BlockPos.ZERO));
        if (level.getBlockState(podium).is(Blocks.DRAGON_EGG) || level.getBlockState(podium.below()).is(Blocks.DRAGON_EGG)) return;
        if (!level.getBlockState(podium.below()).is(Blocks.BEDROCK)) return; // the podium is not there yet? then the first kill's own egg is coming
        level.setBlockAndUpdate(podium, Blocks.DRAGON_EGG.defaultBlockState());
        Vec3 c = Vec3.atCenterOf(podium);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.5, c.z, 60, 0.6, 0.8, 0.6, 0.2);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 2.0F, 0.7F);
        WakingWorld.LOGGER.info("the dragon left another egg at {}", podium);
    }
}
