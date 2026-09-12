package me.lovkar.wakingworld.cataclysm;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.network.WakingNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The Blood Moon: a night that does not pass quietly.
 *
 * <p>It is rolled at dusk like the shower, and once it starts the moon hangs red until dawn. What
 * comes with it is not a mob cap raised to silly numbers - it is a siege: every couple of seconds a
 * handful of the monsters the world would have made anyway are placed in the dark outside somebody's
 * light and pointed at them, faster and a little tougher than usual, and they keep coming until the
 * sun does. Whoever is still standing at first light gets the night's due.</p>
 *
 * <p>Everything it spawns is marked, so at dawn the ones nobody dealt with burn off with the night
 * rather than accumulating in the world for ever.</p>
 */
public final class BloodMoon extends SavedData {
    public static final String NAME = "wakingworld_bloodmoon";
    private static final Factory<BloodMoon> FACTORY = new Factory<>(BloodMoon::new, BloodMoon::load, null);

    /** On every monster the night puts out, so dawn can take back what it gave. */
    public static final String TAG = "WakingBloodMoon";

    private static final ResourceLocation HASTE_ID = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "blood_moon");

    /** The kinds the siege draws on - the ordinary night, in numbers. */
    private static final EntityType<?>[] HOST = {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.SKELETON,
            EntityType.SPIDER, EntityType.HUSK, EntityType.STRAY,
            EntityType.CREEPER, EntityType.ENDERMAN,
    };

    private boolean running;
    private int cooldownUntilDay;
    private int wave;                 // how many waves have come
    private int nextWave;             // ticks until the next

    private BloodMoon() {
    }

    public static BloodMoon get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static boolean running(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && get(level).running;
    }

    // ---- the tick --------------------------------------------------------------------------

    public static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (!WakingConfig.bloodMoons()) return;
        get(level).tick(level);
    }

    private void tick(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        long t = level.getDayTime() % 24000L;
        boolean night = t >= 13000 && t < 23000;

        if (!running) {
            if (players.isEmpty()) return;
            if (Cataclysms.busy(level) || Volcano.busy(level)) return;
        // nothing new starts while the camera is rolling: a world-driven cataclysm on top of a
        // scene is a ruined take, and there is no way to tell from the footage what happened
        if (me.lovkar.wakingworld.story.Cinematics.running()) return;

            int day = (int) (level.getDayTime() / 24000L);
            if (day < cooldownUntilDay) return;
            if (t < 13000 || t > 13600) return;                  // rolled once, at nightfall
            if (level.random.nextDouble() > WakingConfig.bloodMoonChance() * Unrest.factor(level)) {
                cooldownUntilDay = day + 1;
                setDirty();
                return;
            }
            // the sky is wrong for a while before it turns
            if (WakingConfig.omens() && !level.players().isEmpty()) {
                Omen.begin(level, level.players().get(0).position(), Omen.Kind.BLOOD_MOON,
                        Math.min(30, WakingConfig.omenSeconds()));
            }
            begin(level);
            return;
        }

        if (!night) {                                            // the sun came up: it is over
            end(level);
            return;
        }
        // motes in the air the whole night through, so the moon is a thing in the world and not a
        // filter over the lens
        if (level.getGameTime() % 10 == 0) {
            for (ServerPlayer p : players) {
                Cataclysms.puff(level, net.minecraft.core.particles.ParticleTypes.FALLING_LAVA,
                        p.getX(), p.getY() + 9, p.getZ(), 14, 22, 6, 22, 0.0);
                Cataclysms.puff(level, net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                        p.getX(), p.getY() + 5, p.getZ(), 8, 20, 5, 20, 0.005);
            }
        }
        if (players.isEmpty()) return;

        nextWave -= 20;
        if (nextWave <= 0) {
            for (ServerPlayer p : players) {
                if (p.isCreative() || p.isSpectator()) continue;
                siege(level, p.position(), p, Math.min(WakingConfig.bloodMoonWaveSize(), 2 + wave / 2), level.random);
            }
            wave++;
            nextWave = Math.max(60, WakingConfig.bloodMoonWaveSeconds() * 20);
            moonlit(level, true);
            setDirty();
        }
    }

    // ---- the night -------------------------------------------------------------------------

    private void begin(ServerLevel level) {
        running = true;
        wave = 0;
        nextWave = 200;
        cooldownUntilDay = (int) (level.getDayTime() / 24000L) + WakingConfig.daysBetweenBloodMoons();
        setDirty();
        WakingNet.bloodMoon(true);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.bloodmoon.warning").withStyle(ChatFormatting.DARK_RED));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.WEATHER, 0.5F, 0.5F);
        }
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            me.lovkar.wakingworld.story.Chronicle.record(level, "cataclysm", "bloodmoon", p.blockPosition(), null);
            break;                              // it is one night over the whole world, not one per player
        }
        WakingWorld.LOGGER.info("cataclysm: a blood moon rises");
    }

    /**
     * Any giant already awake is stronger while the moon is up.
     *
     * <p>Only the ones near a player, and only once a wave: a colossus nobody can see does not
     * need the modifier, and re-applying it every wave means one that was unloaded through the
     * moonrise still gets it when somebody walks back into its country. Turning it off at dawn is
     * the same walk with false.</p>
     */
    private static void moonlit(ServerLevel level, boolean on) {
        if (!WakingConfig.bloodMoonColossi()) return;
        for (ServerPlayer p : level.players()) {
            for (me.lovkar.wakingworld.entity.ColossusEntity c :
                    level.getEntitiesOfClass(me.lovkar.wakingworld.entity.ColossusEntity.class,
                            new net.minecraft.world.phys.AABB(p.position(), p.position()).inflate(160),
                            e -> e.isAlive())) {
                c.moonlit(on);
            }
        }
    }

    private void end(ServerLevel level) {
        running = false;
        wave = 0;
        setDirty();
        WakingNet.bloodMoon(false);
        // what the night made and nobody met goes out with it
        // collect first: discarding while walking the level's own entity list skips half of them
        java.util.List<Mob> theirs = new java.util.ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.getTags().contains(TAG)) theirs.add(mob);
        }
        for (Mob mob : theirs) mob.discard();
        int gone = theirs.size();
        moonlit(level, false);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("cataclysm.wakingworld.bloodmoon.over").withStyle(ChatFormatting.GOLD));
            reward(level, p);
        }
        Survived.everyone(level, Omen.Kind.BLOOD_MOON);
        WakingWorld.LOGGER.info("cataclysm: the blood moon sets ({} left over went with it)", gone);
    }

    /**
     * A handful of monsters, placed in the dark around a point and pointed at whoever is there.
     * Returns how many actually got a place to stand - the command uses that, and so does the log.
     */
    public static int siege(ServerLevel level, net.minecraft.world.phys.Vec3 around, ServerPlayer target, int want, RandomSource rnd) {
        int cap = WakingConfig.bloodMoonWaveSize() * 6;
        // do not stack them up faster than anyone could fight through them
        int already = level.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(around, around).inflate(96),
                m -> m.isAlive() && m.getTags().contains(TAG)).size();
        if (already >= cap) return 0;
        int made = 0;
        for (int i = 0; i < want; i++) {
            BlockPos at = spot(level, around, rnd);
            if (at == null) continue;
            EntityType<?> type = HOST[rnd.nextInt(HOST.length)];
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rnd.nextFloat() * 360F, 0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            mob.addTag(TAG);
            // seen in the dark. A blood moon is a night event and the mobs it brings were, on camera,
            // a black field with a few eyes in it - the outline is what makes the siege read at all,
            // and it tells a player at a glance which of the things around them came with the moon.
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 20 * 60 * 20, 0, false, false, false));
            dress(mob);
            if (target != null) mob.setTarget(target);
            level.addFreshEntity(mob);
            made++;
        }
        return made;
    }

    /** Faster, a little tougher, and it does not burn at dawn - it simply ends with the night. */
    private static void dress(Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(HASTE_ID) == null) {
            speed.addPermanentModifier(new AttributeModifier(HASTE_ID, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 20, 0, false, false));
        if (mob instanceof Monster) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 20, 0, false, false));
        }
    }

    /** Somewhere dark, on the ground, out of sight but not out of reach. */
    private static BlockPos spot(ServerLevel level, net.minecraft.world.phys.Vec3 around, RandomSource rnd) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 20 + rnd.nextDouble() * 26;
            double x = around.x + Math.cos(angle) * dist;
            double z = around.z + Math.sin(angle) * dist;
            BlockPos ground = Cataclysms.surface(level, x, z);
            if (ground.getY() <= level.getMinBuildHeight() + 1) continue;
            if (!level.getFluidState(ground.below()).isEmpty()) continue;
            if (me.lovkar.wakingworld.compat.Colonies.keepOff(level, ground)) continue;                   // not inside a colony's walls
            if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, ground) > 7) continue;   // not in someone's lit hall
            if (!level.noCollision(EntityType.ZOMBIE.getSpawnAABB(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5))) continue;
            return ground;
        }
        return null;
    }

    /** For living through it: the night's due, and a word. */
    private void reward(ServerLevel level, ServerPlayer p) {
        if (!p.isAlive()) return;
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 1));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60 * 20, 1));
    }

    // ---- saved with the world --------------------------------------------------------------

    private static BloodMoon load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoon b = new BloodMoon();
        b.running = tag.getBoolean("Running");
        b.cooldownUntilDay = tag.getInt("Cooldown");
        b.wave = tag.getInt("Wave");
        b.nextWave = tag.getInt("Next");
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Running", running);
        tag.putInt("Cooldown", cooldownUntilDay);
        tag.putInt("Wave", wave);
        tag.putInt("Next", nextWave);
        return tag;
    }

    /** For the debug command: raise it now (or put it down). */
    public static void force(ServerLevel level, boolean on) {
        BloodMoon b = get(level);
        if (on == b.running) return;
        if (on) b.begin(level);
        else b.end(level);
    }

    /**
     * One that was in an unloaded chunk when the sun came up, coming back now. The sweep at dawn can
     * only reach what is loaded, so the rest are caught here, the moment the world sees them again.
     */
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.loadedFromDisk()) return;                  // only ones coming back from a saved chunk
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getTags().contains(TAG)) return;
        if (running(level)) return;
        event.setCanceled(true);
    }

    /** Sent to a client that joins in the middle of one. */
    public static void greet(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level && running(level)) WakingNet.bloodMoon(player, true);
    }
}
