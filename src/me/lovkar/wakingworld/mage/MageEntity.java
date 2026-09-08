package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The mage in the tower.
 *
 * <p>He is the answer to a question the mod could not otherwise ask: everything in 0.2 happens to
 * you, and there is no way to be the one who decides. He will tell you the price of any of the
 * five, and he will hand you the stone to do it on. He is not friendly about it - he is bored, he
 * is old, and he thinks a great deal less of you than you would like - but he does not raise a hand
 * unless you do.</p>
 *
 * <p><b>And if you do.</b> Then he is a boss: a bar, three tricks, and a rod at the end of it. That
 * is a real choice rather than a moral one - the rod is worth having and so is the standing offer,
 * and you cannot have both, because he does not forgive and there is one of him in a tower. A
 * player who kills him has closed a door in their own world on purpose, which is the most
 * interesting kind of decision a mod can offer.</p>
 */
public class MageEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_ROUSED =
            SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.BOOLEAN);
    /** How far into a cast he is, 0-1, for the robe's light and the ring at his feet. */
    private static final EntityDataAccessor<Float> DATA_CAST =
            SynchedEntityData.defineId(MageEntity.class, EntityDataSerializers.FLOAT);

    private final ServerBossEvent bar = new ServerBossEvent(
            Component.translatable("entity.wakingworld.dark_mage"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private BlockPos tower = BlockPos.ZERO;
    /** The tower is raked once, the first time anybody comes to it (see {@link me.lovkar.wakingworld.worldgen.Tidy}). */
    private boolean tidied;
    private int castCooldown = 60;
    private int spell;
    private int castTicks;
    /** Where the spell was aimed when he began it - so the ring lands where you WERE, and can be left. */
    private Vec3 castAt = Vec3.ZERO;
    private int blinkCooldown = 120;
    private boolean second;

    public MageEntity(EntityType<? extends MageEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 120;
        this.bar.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 220.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROUSED, false);
        builder.define(DATA_CAST, 0f);
    }

    @Override
    protected void registerGoals() {
        // no attack goal: while he is quiet he only watches, and once he is roused he does not
        // walk up to anybody - he stands off and throws things, which is what a mage is for
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 24.0F, 1.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public void assign(BlockPos towerAt) {
        this.tower = towerAt;
    }

    public boolean roused() {
        return entityData.get(DATA_ROUSED);
    }

    public float castLight() {
        return entityData.get(DATA_CAST);
    }

    public String mageName() {
        return MageNames.of(tower.equals(BlockPos.ZERO) ? blockPosition() : tower);
    }

    // ---- the quiet half ----------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (roused()) return InteractionResult.PASS;                 // he is past talking
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) talk(sp);
        return InteractionResult.CONSUME;
    }

    /**
     * What he says, and what he hands over.
     *
     * <p>He speaks in the chat rather than in a window of his own on purpose. The king has a screen
     * because a king holds court and the screen IS the court; this one is a man alone in a tower who
     * would rather you left. A wall of text he says once, and a list of prices he will repeat as
     * often as you make him, is the right shape for that - and it means the prices can be read again
     * by scrolling back rather than by walking back.</p>
     */
    private void talk(ServerPlayer player) {
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_AMBIENT, SoundSource.NEUTRAL, 1.2F, 0.7F);
            Cataclysms.puff(server, ParticleTypes.WITCH, getX(), getY() + 1.8, getZ(), 8, 0.3, 0.3, 0.3, 0.01);
        }
        boolean first = !met.contains(player.getUUID());
        if (first) met.add(player.getUUID());
        player.sendSystemMessage(Component.literal("").append(
                Component.translatable("entity.wakingworld.dark_mage.name_line", mageName()).withStyle(ChatFormatting.DARK_PURPLE)));
        player.sendSystemMessage(Component.translatable(first
                ? "entity.wakingworld.dark_mage.first" : "entity.wakingworld.dark_mage.again").withStyle(ChatFormatting.GRAY));
        for (DarkRites.Rite r : DarkRites.ALL) {
            net.minecraft.network.chat.MutableComponent line = Component.literal("  ")
                    .append(Component.translatable(r.nameKey()).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            for (int i = 0; i < r.costs().size(); i++) {
                if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                DarkRites.Cost c = r.costs().get(i);
                line.append(Component.literal(c.count() + "x ").withStyle(ChatFormatting.GRAY))
                        .append(c.item().getDescription().copy().withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(line);
        }
        // the stone itself: his, and not to be had any other way
        if (!has(player)) {
            player.sendSystemMessage(Component.translatable("entity.wakingworld.dark_mage.stone").withStyle(ChatFormatting.DARK_PURPLE));
            net.minecraft.world.item.ItemStack stone =
                    new net.minecraft.world.item.ItemStack(MageBlocks.RITE_STONE_ITEM.get());
            if (!player.addItem(stone)) player.drop(stone, false);
        }
    }

    private static boolean has(ServerPlayer player) {
        for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
            if (s.is(MageBlocks.RITE_STONE_ITEM.get())) return true;
        }
        return false;
    }

    /** Who he has already introduced himself to; forgotten on a restart, which costs nothing. */
    private final java.util.Set<java.util.UUID> met = new java.util.HashSet<>();

    /**
     * The first blow is the whole of it: he is neutral until somebody decides otherwise, and after
     * that he never goes back. A mage who could be beaten to half and then bargained with would
     * make the choice free, and the choice is the point.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!level().isClientSide && !roused()) {
            // it has to be a person. A zombie that wandered past in the night must not be able to
            // take the trade away from a player who never touched him - and nor must a fall, or a fire
            if (!(source.getEntity() instanceof Player)) return false;
            rouse();
        }
        return super.hurt(source, amount);
    }

    private void rouse() {
        entityData.set(DATA_ROUSED, true);
        bar.setVisible(true);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 6.0F, 0.55F);
            WakingWorld.hooks.shakeAt(position(), 4.0F, 50);
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.2, getZ(), 90, 0.8, 1.2, 0.8, 0.25);
            Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 60, 1.0, 1.0, 1.0, 0.05);
            for (ServerPlayer p : server.getPlayers(pl -> pl.distanceToSqr(this) < 48 * 48)) {
                p.sendSystemMessage(Component.translatable("entity.wakingworld.dark_mage.roused", mageName())
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        WakingWorld.LOGGER.info("mage: {} has been struck and will not be talked to again", mageName());
    }

    // ---- the loud half -----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        // he has been ticking for five seconds, which means somebody is here and the wood round the
        // tower has finished arriving: rake it once
        if (!tidied && !tower.equals(BlockPos.ZERO) && tickCount > 100 && tickCount % 20 == 5) {
            tidied = true;
            me.lovkar.wakingworld.worldgen.Tidy.begin(server, tower, 15, 8, -10, 46);
        }
        if (roused()) {
            bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
            bar.setName(Component.translatable("entity.wakingworld.dark_mage.named", mageName()));
            for (ServerPlayer p : server.getPlayers(pl -> pl.distanceToSqr(this) < 64 * 64)) bar.addPlayer(p);
            for (ServerPlayer p : bar.getPlayers().toArray(new ServerPlayer[0])) {
                if (p.distanceToSqr(this) > 80 * 80 || p.isRemoved()) bar.removePlayer(p);
            }
            fight(server);
        } else if (tickCount % 3 == 0) {
            // the quiet aura: he is lit from underneath by something he is not showing you
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.15, getZ(), 1, 0.35, 0.05, 0.35, 0.006);
            if (tickCount % 24 == 0) {
                Cataclysms.puff(server, ParticleTypes.WITCH, getX(), getY() + 1.7, getZ(), 2, 0.25, 0.2, 0.25, 0.0);
            }
        }
    }

    /**
     * The fight.
     *
     * <p>Three tricks chosen by how close you are, on a clock rather than at random - so the rhythm
     * is learnable - and every one of them is <b>announced</b>: a second and a half of particles that
     * say exactly where it is going to land, and it lands where you were standing when he began, not
     * where you are when it arrives. That is the whole fight. He hits very hard and he never misses
     * anybody who stood still, and he never once hits anybody who moved.</p>
     *
     * <p>He does not walk. He is an old man in a tower, so instead he goes out and comes back
     * somewhere else, which keeps him from being cornered against his own wall and looks a great
     * deal better than pathfinding round furniture. Under half his blood he does all of it faster
     * and finishes each turn with a star.</p>
     */
    private void fight(ServerLevel server) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || target.distanceToSqr(this) > 64 * 64) {
            entityData.set(DATA_CAST, 0f);
            return;
        }
        getLookControl().setLookAt(target, 30f, 30f);
        if (!second && getHealth() < getMaxHealth() * 0.5f) {
            second = true;
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 6.0F, 0.45F);
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0, getZ(), 120, 0.6, 1.4, 0.6, 0.3);
            WakingWorld.hooks.shakeAt(position(), 3.0F, 60);
            for (ServerPlayer p : server.getPlayers(pl -> pl.distanceToSqr(this) < 48 * 48)) {
                p.sendSystemMessage(Component.translatable("entity.wakingworld.dark_mage.second", mageName()).withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        if (castTicks > 0) {
            castTicks--;
            entityData.set(DATA_CAST, 1f - castTicks / (float) windUp());
            telegraph(server);
            if (castTicks == 0) release(server, target);
            return;
        }
        if (--blinkCooldown <= 0 && (target.distanceTo(this) < 5.0 || target.distanceTo(this) > 26.0)) blink(server, target);
        if (--castCooldown > 0) return;

        double d = target.distanceTo(this);
        spell = d < 6 ? 0 : (d < 18 ? 1 : 2);
        castAt = target.position();
        castTicks = windUp();
        castCooldown = (second ? 40 : 70) + random.nextInt(second ? 25 : 40);
        entityData.set(DATA_CAST, 0.02f);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 4.0F, 0.7F + random.nextFloat() * 0.2F);
        Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.1, getZ(), 40, 0.9, 0.05, 0.9, 0.02);
    }

    /** How long you get to read the warning. Shorter once he is in earnest, never nothing. */
    private int windUp() {
        return second ? 22 : 30;
    }

    /** The warning: where it is going to land, drawn in the air for as long as the wind-up lasts. */
    private void telegraph(ServerLevel server) {
        float t = 1f - castTicks / (float) windUp();
        Vec3 hands = position().add(0, 1.5, 0);
        Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, hands.x, hands.y, hands.z, 4, 0.3, 0.3, 0.3, 0.02);
        switch (spell) {
            case 0 -> {                                   // it goes off round HIM: the ring closes in
                double r = 7.2 * (1.0 - t * 0.55);
                for (int i = 0; i < 18; i++) {
                    double a = i / 18.0 * Math.PI * 2 + t * 2.0;
                    Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * r, getY() + 0.25, getZ() + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            case 1 -> {                                   // it goes off THERE: the circle is drawn on the ground first
                for (int i = 0; i < 20; i++) {
                    double a = i / 20.0 * Math.PI * 2 - t * 1.6;
                    Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, castAt.x + Math.cos(a) * 3.4, castAt.y + 0.2, castAt.z + Math.sin(a) * 3.4, 1, 0.02, 0.15, 0.02, 0.02);
                }
                if (castTicks % 5 == 0) Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, castAt.x, castAt.y + 0.2, castAt.z, 3, 0.5, 0.1, 0.5, 0.01);
            }
            default -> {                                  // the line it will come down: his hands to the spot
                Vec3 step = castAt.add(0, 0.3, 0).subtract(hands).scale(1 / 12.0);
                for (int i = 1; i <= 12; i++) {
                    Vec3 p = hands.add(step.scale(i));
                    if (server.random.nextFloat() < 0.5f + t * 0.5f) {
                        Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            }
        }
    }

    /** Out here, back over there: eight tries at a spot with ground under it and sky above it. */
    private void blink(ServerLevel server, LivingEntity target) {
        blinkCooldown = 140 + random.nextInt(80);
        Vec3 from = position();
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 9 + random.nextDouble() * 6;
            double x = target.getX() + Math.cos(a) * r, z = target.getZ() + Math.sin(a) * r;
            BlockPos ground = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, target.getY(), z));
            if (Math.abs(ground.getY() - target.getY()) > 12) continue;
            if (!server.noCollision(this, getBoundingBox().move(x - getX(), ground.getY() - getY(), z - getZ()))) continue;
            server.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3.0F, 0.7F);
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y + 1.0, from.z, 60, 0.4, 0.9, 0.4, 0.2);
            Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 25, 0.5, 0.7, 0.5, 0.03);
            teleportTo(x, ground.getY(), z);
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, x, ground.getY() + 1.0, z, 60, 0.4, 0.9, 0.4, 0.2);
            server.playSound(null, x, ground.getY(), z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3.0F, 1.1F);
            return;
        }
    }

    /** What the cast turns into. Each one is a small piece of one of the five, aimed. */
    private void release(ServerLevel server, LivingEntity target) {
        entityData.set(DATA_CAST, 0f);
        Vec3 at = castAt.equals(Vec3.ZERO) ? target.position() : castAt;
        switch (spell) {
            // close: the ground under his feet turns over and throws whoever is on it off
            case 0 -> {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0F, 1.4F);
                Cataclysms.puff(server, ParticleTypes.SONIC_BOOM, getX(), getY() + 1.0, getZ(), 1, 0, 0, 0, 0);
                WakingWorld.hooks.shakeAt(position(), 2.6F, 40);
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(7.0), this::hostileTo)) {
                    e.hurt(damageSources().indirectMagic(this, this), 9.0F);
                    Vec3 push = e.position().subtract(position()).normalize().scale(1.5).add(0, 0.9, 0);
                    e.push(push.x, push.y, push.z);
                    e.hurtMarked = true;
                }
            }
            // middle: a ring of soul fire closing on where you were standing
            case 1 -> {
                server.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 4.0F, 0.6F);
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, at.x + Math.cos(a) * 3.2, at.y + 0.2, at.z + Math.sin(a) * 3.2, 3, 0.1, 0.4, 0.1, 0.03);
                }
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4.0), this::hostileTo)) {
                    e.hurt(damageSources().indirectMagic(this, this), 11.0F);
                    e.igniteForSeconds(4);
                }
                WakingWorld.hooks.shakeAt(at, 1.8F, 30);
            }
            // far: a star of his own, small and exact - the mod's own meteor, aimed at you
            default -> {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 5.0F, 0.5F);
                for (int k = 0; k < (second ? 3 : 1); k++) {
                    me.lovkar.wakingworld.cataclysm.MeteorEntity star = WakingWorld.METEOR.get().create(server);
                    if (star == null) continue;
                    star.setSize(1);
                    star.setCarriesStar(false);
                    Vec3 spread = k == 0 ? at : at.add(random.nextGaussian() * 3.5, 0, random.nextGaussian() * 3.5);
                    star.aimAt(spread, 46, 18, 1.5);
                    server.addFreshEntity(star);
                }
            }
        }
    }

    private boolean hostileTo(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof MageEntity);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            bar.removeAllPlayers();
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0, getZ(), 160, 0.7, 1.2, 0.7, 0.35);
            Cataclysms.puff(server, ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 80, 1.0, 1.0, 1.0, 0.06);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_DEATH, SoundSource.HOSTILE, 6.0F, 0.6F);
            WakingWorld.hooks.shakeAt(position(), 5.0F, 70);
            for (int i = 0; i < 44; i++) {
                double a = i / 44.0 * Math.PI * 2;
                Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * 4.5, getY() + 0.3, getZ() + Math.sin(a) * 4.5, 2, 0.1, 0.6, 0.1, 0.08);
            }
            spawnAtLocation(new net.minecraft.world.item.ItemStack(me.lovkar.wakingworld.item.WakingItems.STORM_ROD.get()));
            if (source.getEntity() instanceof ServerPlayer p) {
                me.lovkar.wakingworld.advancement.WakingTriggers.COLOSSUS_SLAIN.get().trigger(p, "dark_mage", 0);
            }
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("Tower", tower.asLong());
        tag.putBoolean("Roused", roused());
        tag.putBoolean("Tidied", tidied);
        tag.putBoolean("Second", second);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tower = BlockPos.of(tag.getLong("Tower"));
        entityData.set(DATA_ROUSED, tag.getBoolean("Roused"));
        tidied = tag.getBoolean("Tidied");
        second = tag.getBoolean("Second");
        bar.setVisible(roused());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (roused()) bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** He is not a source of light for the mob cap, and he is never afraid of the sun. */
    @Override
    public boolean isSunBurnTick() {
        return false;
    }

    public static boolean present() {
        return WakingConfig.mage();
    }
}
