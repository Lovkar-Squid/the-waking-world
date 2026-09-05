package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.item.WakingItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The king on his throne. He does not move; he talks (right-click opens the audience screen with
 * what he knows of the sleepers, the letters and the vaults), and he can be won over: a Colossus
 * Heart laid before him grants the freedom of the treasury. Strike him and the kingdom is angry
 * for two days; kill him and it never forgets.
 */
public class KingEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_ANGRY = SynchedEntityData.defineId(KingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_PERMITTED = SynchedEntityData.defineId(KingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_KINGDOM = SynchedEntityData.defineId(KingEntity.class, EntityDataSerializers.STRING);
    /** What the king has heard lately, for the audience screen: {@code type;kind;paces;direction;daysAgo|...}. */
    private static final EntityDataAccessor<String> DATA_NEWS = SynchedEntityData.defineId(KingEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_GENERATION = SynchedEntityData.defineId(KingEntity.class, EntityDataSerializers.INT);

    private BlockPos center = BlockPos.ZERO;

    public KingEntity(EntityType<? extends KingEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 50;
        // he sits a little into his seat and never moves: no pushing out of blocks, no falling
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANGRY, false);
        builder.define(DATA_PERMITTED, false);
        builder.define(DATA_KINGDOM, "");
        builder.define(DATA_NEWS, "");
        builder.define(DATA_GENERATION, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public void assign(BlockPos kingdomCenter) {
        this.center = kingdomCenter;
        entityData.set(DATA_KINGDOM, Kingdoms.name(kingdomCenter));
        if (level() instanceof ServerLevel server) entityData.set(DATA_GENERATION, KingdomData.get(server).generation(kingdomCenter));
        setNoAi(false);
    }

    /** Which king of the line this is: 0 the first, then his successors. */
    public int generation() {
        return entityData.get(DATA_GENERATION);
    }

    public BlockPos center() {
        return center;
    }

    public String kingdomName() {
        String n = entityData.get(DATA_KINGDOM);
        return n.isEmpty() ? Kingdoms.name(center) : n;
    }

    public String kingName() {
        return Kingdoms.kingName(center, generation());
    }

    public String news() {
        return entityData.get(DATA_NEWS);
    }

    /** The three nearest things the chronicle knows, as the screen's parser wants them. */
    private String gatherNews(ServerLevel server) {
        StringBuilder sb = new StringBuilder();
        long today = server.getDayTime() / 24000L;
        for (me.lovkar.wakingworld.story.Chronicle.Event e : me.lovkar.wakingworld.story.Chronicle.get(server).near(center, null, 3)) {
            int dx = e.x() - center.getX(), dz = e.z() - center.getZ();
            double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
            if (dist > 5000) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(e.type()).append(';').append(e.kind()).append(';').append(me.lovkar.wakingworld.story.Letters.paces(dist)).append(';')
                    .append(me.lovkar.wakingworld.story.Letters.direction(dx, dz)).append(';').append(Math.max(0, today - e.day()));
        }
        return sb.toString();
    }

    /** Synced for the client's audience screen: how the king feels about the viewer (the nearest player, refreshed each second). */
    public boolean angryWithViewer() {
        return entityData.get(DATA_ANGRY);
    }

    public boolean viewerPermitted() {
        return entityData.get(DATA_PERMITTED);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && tickCount % 20 == 0) {
            Player near = server.getNearestPlayer(this, 12);
            KingdomData data = KingdomData.get(server);
            entityData.set(DATA_ANGRY, near != null && data.isAngry(server, center, near.getUUID()));
            entityData.set(DATA_PERMITTED, near != null && data.isPermitted(center, near.getUUID()));
            if (near != null && tickCount % 100 == 0) entityData.set(DATA_NEWS, gatherNews(server));
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level() instanceof ServerLevel server) {
            KingdomData data = KingdomData.get(server);
            if (data.isAngry(server, center, player.getUUID())) {
                player.displayClientMessage(Component.translatable("entity.wakingworld.king.angry", kingdomName()).withStyle(ChatFormatting.RED), false);
                playSound(me.lovkar.wakingworld.WakingSounds.KING_ANGRY.get(), 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            if (stack.is(WakingItems.COLOSSUS_HEART.get()) && !data.isPermitted(center, player.getUUID())) {
                if (!player.isCreative()) stack.shrink(1);
                data.permit(center, player.getUUID());
                player.displayClientMessage(Component.translatable("entity.wakingworld.king.permit", kingdomName()).withStyle(ChatFormatting.GOLD), false);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 0.8F);
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5, getZ(), 20, 0.6, 0.6, 0.6, 0.1);
                entityData.set(DATA_PERMITTED, true);
                return InteractionResult.CONSUME;
            }
            playSound(me.lovkar.wakingworld.WakingSounds.KING_GREET.get(), 1.0F, 1.0F); // the audience begins
            return InteractionResult.SUCCESS;
        }
        // the client opens the audience - unless the hand holds the heart that buys the treasury (the server takes that)
        if (!angryWithViewer() && !(stack.is(WakingItems.COLOSSUS_HEART.get()) && !viewerPermitted())) WakingWorld.hooks.openKing(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean did = super.hurt(source, amount);
        if (did && source.getEntity() instanceof Player player && level() instanceof ServerLevel server && !player.isCreative()) {
            Kingdoms.offend(server, center, player, KingdomData.ANGER_TICKS * 2, "king");
        }
        return did;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            // the throne stays empty for two minutes; then the council crowns one of the townsfolk
            KingdomData.get(server).kingDied(center, server.getGameTime() + 20 * 120);
            if (source.getEntity() instanceof Player player) Kingdoms.offend(server, center, player, KingdomData.ANGER_TICKS * 3, "king_dead");
            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayers(p -> p.distanceToSqr(this) < 200 * 200)) {
                sp.displayClientMessage(Component.translatable("kingdom.wakingworld.king_dead", kingdomName()).withStyle(ChatFormatting.DARK_RED), false);
            }
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Center")) NbtUtils.readBlockPos(tag, "Center").ifPresent(p -> {
            center = p;
            entityData.set(DATA_KINGDOM, Kingdoms.name(p));
        });
        if (tag.contains("Generation")) entityData.set(DATA_GENERATION, tag.getInt("Generation"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Center", NbtUtils.writeBlockPos(center));
        tag.putInt("Generation", generation());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return me.lovkar.wakingworld.WakingSounds.KING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return me.lovkar.wakingworld.WakingSounds.KING_DEATH.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return me.lovkar.wakingworld.WakingSounds.KING_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 320;
    }

    @Override
    public Component getName() {
        if (hasCustomName()) return super.getName();
        return Component.translatable("entity.wakingworld.king.named", kingName(), kingdomName());
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
