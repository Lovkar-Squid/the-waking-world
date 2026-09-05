package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.WakingConfig;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.ColossusPose;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.body.PartDef;
import me.lovkar.wakingworld.body.PosedBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A colossus: a giant built from the blocks of the land it slept in. Three synced values
 * (palette, seed, height) describe the whole body - both sides rebuild it deterministically
 * (see ColossusShapes). The entity's own collision box is a small footprint; 27 {@link ColossusPart}
 * hit boxes hug its torso, head and limbs. It walks straight at you through trees and loose ground
 * (trample), fights in three phases with ten different moves (see {@link ColossusCombatGoal}),
 * tears craters where it stomps and where its boulders land, its cores are the weak points, and
 * when it dies it collapses into the real blocks it was made of.
 */
public class ColossusEntity extends Monster {
    private static final EntityDataAccessor<String> DATA_PALETTE =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEIGHT =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_ATTACK =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_CORES =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.BYTE);
    /** The id of this giant's boss bar, so the client can draw the bar it belongs to in the giant's own stone. */
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> DATA_BOSS_ID =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> DATA_FOOT =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.BYTE);
    /** When the server cuts an attack short (a run into a cliff, a leap that lands), the client's animation jumps here too. */
    private static final EntityDataAccessor<Byte> DATA_SKIP =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.BYTE);
    /** Ticks left of the awakening (rising out of the ground); 0 = awake. */
    private static final EntityDataAccessor<Integer> DATA_WAKE =
            SynchedEntityData.defineId(ColossusEntity.class, EntityDataSerializers.INT);

    /** How long the rise out of the ground takes. */
    public static final int WAKE_TICKS = 120;
    /** The Titan takes its time: eleven seconds of the arena cracking open before it stands. */
    public static final int TITAN_WAKE_TICKS = 220;

    // entity events, server -> every client that sees the giant: the ground shakes
    private static final byte EV_STOMP = 70, EV_SLAM = 71, EV_LAND = 72, EV_CRASH = 73, EV_ROAR = 74,
            EV_COLLAPSE = 75, EV_CORE = 76, EV_PHASE = 77, EV_EMERGE = 78, EV_HEAVE = 79,
            EV_DEATH_START = 80, EV_DEATH_KNEE = 81, EV_DEATH_FALL = 82, EV_DEATH_CRUMBLE = 83,
            EV_CORE_HIT = 84 /* + core index, 84..88 */, EV_SHIELD = 90, EV_BLOW = 92 /* + 0..2: a blow it feels, light to heavy */;
    /** Client: ticks left of the white flash on each core (a hit), and on all of them (the shield). */
    private final int[] coreFlash = new int[ColossusBody.CORE_BOXES];
    /** Server: the last time the shield told the attacker to break the cores, per attacker id. */
    private final Map<Integer, Long> shieldTold = new java.util.HashMap<>();
    /** Server: everyone who hurt it (advancements go to them when it falls). */
    private final Set<java.util.UUID> attackers = new HashSet<>();

    private static final ResourceLocation PHASE_SPEED = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "phase_speed");
    private static final ResourceLocation PHASE_DAMAGE = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "phase_damage");

    public static final int DEFAULT_HEIGHT = 40;
    private static final float ARMOR_FACTOR = 0.3F;
    private static final float CORE_FACTOR = 1.5F;

    private ColossusBody body;
    private Palette palette;
    private final ColossusPart[] parts;
    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(false);

    // combat (server)
    private Attack attack = Attack.NONE;
    private int attackTicks;
    private int attackCooldown = 30;
    private final Map<Attack, Integer> cooldowns = new EnumMap<>(Attack.class);
    private final Deque<Attack> recent = new ArrayDeque<>();
    final List<Shockwave> waves = new ArrayList<>();
    private final float[] coreDamage = new float[8];
    private int stompChain;
    private ThrownMassEntity held;
    private boolean leftGround;
    private int stuckTicks;
    /** Ticks it has wanted to move towards its target and not got anywhere. */
    private int noHeadway;
    // signature moves that run along a line from the fists (quake, spikes)
    Vec3 lineStart, lineDir = Vec3.ZERO;
    final Set<Integer> lineHit = new HashSet<>();
    // blocks a move raised that come down again: pos, what was there, when
    private final List<TempBlock> tempBlocks = new ArrayList<>();

    private record TempBlock(BlockPos pos, BlockState before, long expires) {
    }

    /** Puts a temporary block into the world; whatever was there comes back after {@code ticks}. */
    void tempBlock(ServerLevel server, BlockPos pos, BlockState state, int ticks) {
        BlockState before = server.getBlockState(pos);
        if (!before.isAir() && !before.canBeReplaced() && before.getFluidState().isEmpty()) return;
        if (server.setBlock(pos, state, 3)) tempBlocks.add(new TempBlock(pos.immutable(), before, server.getGameTime() + ticks));
    }

    /** A temporary block laid over a solid one (a crack of crying obsidian in the floor): the original comes back when it expires. */
    void tempOverlay(ServerLevel server, BlockPos pos, BlockState state, int ticks) {
        BlockState before = server.getBlockState(pos);
        if (before.isAir() || !before.canOcclude() || server.getBlockEntity(pos) != null) return;
        if (server.setBlock(pos, state, 3)) tempBlocks.add(new TempBlock(pos.immutable(), before, server.getGameTime() + ticks));
    }

    private void tickTempBlocks(ServerLevel server, boolean all) {
        if (tempBlocks.isEmpty()) return;
        long now = server.getGameTime();
        Iterator<TempBlock> it = tempBlocks.iterator();
        while (it.hasNext()) {
            TempBlock tb = it.next();
            if (!all && tb.expires() > now) continue;
            it.remove();
            BlockState there = server.getBlockState(tb.pos());
            if (there.isAir()) continue; // somebody already broke it
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, there), tb.pos().getX() + 0.5, tb.pos().getY() + 0.5, tb.pos().getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
            // what was there comes back: air, a plant or water where a spike stood, the floor block under a crack
            server.setBlock(tb.pos(), tb.before().isAir() || tb.before().canBeReplaced() || tb.before().canOcclude() ? tb.before() : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // the run: where the last stride started, how many strides in a row went nowhere, who was hit
    private Vec3 chargeLast = Vec3.ZERO;
    private int chargeBlocked;
    private final Set<Integer> chargeHit = new HashSet<>();

    // animation (client)
    private Attack clientAttack = Attack.NONE;
    private int clientAttackTicks;
    private int clientWake, clientWakeO;

    // the walk cycle's foot plants (both sides): which half-stride we last thudded on
    private int lastPlant = Integer.MIN_VALUE;
    // ground covered (both sides) - drives the walk cycle so the feet stay planted
    private float stride, strideO;

    public ColossusEntity(EntityType<? extends ColossusEntity> type, Level level) {
        super(type, level);
        this.parts = new ColossusPart[ColossusBody.hitBoxCount()];
        int i = 0;
        for (PartDef.Kind k : PartDef.Kind.values()) {
            for (int j = 0; j < ColossusBody.sliceCount(k); j++) {
                this.parts[i++] = new ColossusPart(this, k);
            }
        }
        for (int c = 0; c < ColossusBody.CORE_BOXES; c++) this.parts[i++] = new ColossusPart(this, PartDef.Kind.TORSO, c);
        this.moveControl = new GiantMoveControl(this);
        this.noCulling = true;
        this.xpReward = 0; // the experience comes with the loot, at the end of the collapse (see loot())
        updateParts(true);
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.STEP_HEIGHT, 8.0)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new ColossusCombatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Like the Ender Dragon: parts take the ids right after ours, so client and server agree on them. */
    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(id + i + 1);
        }
    }

    /**
     * A mountain does not spin on its heel: the heading turns at {@link #turnRate()} degrees a tick,
     * so it walks in wide arcs, and it slows down while it is still facing the wrong way instead of
     * strafing sideways towards where it wants to go.
     */
    static final class GiantMoveControl extends MoveControl {
        private final ColossusEntity giant;

        GiantMoveControl(ColossusEntity mob) {
            super(mob);
            this.giant = mob;
        }

        @Override
        protected float rotlerp(float from, float to, float max) {
            return super.rotlerp(from, to, Math.min(max, giant.turnRate()));
        }

        @Override
        public void tick() {
            Operation op = this.operation;
            super.tick();
            if (op == Operation.MOVE_TO && this.mob.zza > 0) {
                // still turning towards the wanted point? walk slower, not sideways
                double dx = this.wantedX - this.mob.getX(), dz = this.wantedZ - this.mob.getZ();
                if (dx * dx + dz * dz > 1.0) {
                    float wanted = (float) (Mth.atan2(dz, dx) * (180F / (float) Math.PI)) - 90.0F;
                    float off = Math.abs(Mth.wrapDegrees(wanted - this.mob.getYRot()));
                    float f = off < 15F ? 1F : off > 100F ? 0.05F : Mth.clamp(1F - (off - 15F) / 85F, 0.05F, 1F);
                    this.mob.setSpeed(this.mob.getSpeed() * f);
                    this.mob.setZza(this.mob.zza * f);
                }
                // the edge of the world: it does not walk off into the void (the End's islands end somewhere)
                Vec3 ahead = this.mob.position().add(giant.facing().scale(Math.max(4.0, giant.bodyHeight() * 0.12)));
                if (!giant.safeToGo(ahead)) {
                    this.mob.setSpeed(0F);
                    this.mob.setZza(0F);
                }
            }
        }
    }

    /**
     * The body never snaps round: it turns towards where it walks, or (standing) towards where it
     * looks, at {@link #turnRate()} degrees a tick - vanilla's control jumps the body by up to the
     * whole head range in one tick, which on a forty-block giant reads as a spin.
     */
    static final class GiantBodyControl extends net.minecraft.world.entity.ai.control.BodyRotationControl {
        private final ColossusEntity giant;

        GiantBodyControl(ColossusEntity mob) {
            super(mob);
            this.giant = mob;
        }

        @Override
        public void clientTick() {
            double dx = giant.getX() - giant.xo, dz = giant.getZ() - giant.zo;
            boolean moving = dx * dx + dz * dz > 2.5E-7;
            float target = moving ? giant.getYRot() : giant.yHeadRot;
            float diff = Mth.wrapDegrees(target - giant.yBodyRot);
            if (!moving && Math.abs(diff) < 30F) diff = 0F; // the head alone covers small glances
            float rate = giant.turnRate() * (moving ? 1F : 0.8F);
            giant.yBodyRot += Mth.clamp(diff, -rate, rate);
            giant.yHeadRot = Mth.rotateIfNecessary(giant.yHeadRot, giant.yBodyRot, giant.getMaxHeadYRot());
        }
    }

    @Override
    protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new GiantBodyControl(this);
    }

    /** The head turns slowly too. */
    @Override
    public int getHeadRotSpeed() {
        return 4;
    }

    // ---- body parameters -------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PALETTE, Palette.STONE.serialize());
        builder.define(DATA_SEED, 0);
        builder.define(DATA_HEIGHT, DEFAULT_HEIGHT);
        builder.define(DATA_ATTACK, (byte) 0);
        builder.define(DATA_PHASE, (byte) 1);
        builder.define(DATA_CORES, (byte) 0);
        builder.define(DATA_FOOT, (byte) 1);
        builder.define(DATA_SKIP, (byte) 0);
        builder.define(DATA_WAKE, 0);
        builder.define(DATA_BOSS_ID, java.util.Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PALETTE.equals(key) || DATA_SEED.equals(key) || DATA_HEIGHT.equals(key)) {
            this.body = null;
            this.palette = null;
            if (this.parts != null) updateParts(true);
            if (this.bossEvent != null) refreshBossBar();
        } else if (DATA_ATTACK.equals(key)) {
            this.clientAttack = Attack.byId(this.entityData.get(DATA_ATTACK));
            this.clientAttackTicks = 0;
        } else if (DATA_SKIP.equals(key)) {
            int to = this.entityData.get(DATA_SKIP);
            if (to > 0) this.clientAttackTicks = Math.max(this.clientAttackTicks, to);
        } else if (DATA_WAKE.equals(key)) {
            int w = this.entityData.get(DATA_WAKE);
            if (Math.abs(w - this.clientWake) > 3) this.clientWakeO = w;
            this.clientWake = w;
        }
    }

    public void setBodyParams(Palette palette, int seed, int height) {
        this.entityData.set(DATA_PALETTE, palette.serialize());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_HEIGHT, Mth.clamp(height, 8, 96));
        this.body = null;
        this.palette = null;
        AttributeInstance step = this.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.setBaseValue(Math.max(4.0, bodyHeight() * 0.2));
        // the Titan is the last fight: a third again the health, harder blows, thicker hide (set once; a
        // saved Titan keeps its wounds - its attributes come back from the save before this runs)
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        double wantHp = isTitan() ? TITAN_HEALTH : BASE_HEALTH;
        if (hp != null && hp.getBaseValue() != wantHp) {
            hp.setBaseValue(wantHp);
            if (!this.level().isClientSide) this.setHealth((float) wantHp);
        }
        AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(isTitan() ? 22.0 : 18.0);
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(isTitan() ? 9.0 : 6.0);
        updateParts(true);
        refreshBossBar();
    }

    static final double BASE_HEALTH = 600.0, TITAN_HEALTH = 800.0;

    /** Client: the boss bar with this id is this giant's. */
    public java.util.Optional<java.util.UUID> bossId() {
        return this.entityData.get(DATA_BOSS_ID);
    }

    private void refreshBossBar() {
        if (!this.level().isClientSide && this.entityData.get(DATA_BOSS_ID).isEmpty()) this.entityData.set(DATA_BOSS_ID, java.util.Optional.of(this.bossEvent.getId()));
        this.bossEvent.setName(bossBarName());
        this.bossEvent.setColor(palette().barColor());
        this.bossEvent.setDarkenScreen(isTitan()); // the Titan darkens the sky like the Wither does
    }

    /**
     * The bar reads the fight: the name, the phase in Roman numerals, and one glyph per core - lit
     * while it beats, hollow once it is broken. "Stone Colossus  II  \u2726\u2726\u2727\u2727\u2727"
     */
    private Component bossBarName() {
        if (this.level().isClientSide) return this.getDisplayName();
        int cores;
        try {
            cores = body().cores.size();
        } catch (RuntimeException ex) {
            return this.getDisplayName();
        }
        StringBuilder glyphs = new StringBuilder();
        int broken = brokenCores();
        for (int i = 0; i < cores; i++) glyphs.append((broken & (1 << i)) != 0 ? '\u2727' : '\u2726');
        String phase = switch (phase()) { case 2 -> "II"; case 3 -> "III"; default -> "I"; };
        return Component.empty().append(this.getDisplayName())
                .append(Component.literal("  " + phase + "  ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(glyphs.toString()).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.wakingworld.colossus." + palette().kind);
    }

    public Palette palette() {
        Palette p = this.palette;
        if (p == null) {
            p = Palette.parse(this.entityData.get(DATA_PALETTE));
            this.palette = p;
        }
        return p;
    }

    public int seed() {
        return this.entityData.get(DATA_SEED);
    }

    public int bodyHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int brokenCores() {
        return this.entityData.get(DATA_CORES);
    }

    public boolean isCoreBroken(int core) {
        return core >= 0 && (brokenCores() & (1 << core)) != 0;
    }

    public boolean stompRightFoot() {
        return this.entityData.get(DATA_FOOT) != 0;
    }

    public ColossusBody body() {
        ColossusBody b = this.body;
        if (b == null) {
            b = ColossusBody.build(palette(), seed(), bodyHeight());
            this.body = b;
        }
        return b;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Palette", palette().serialize());
        tag.putInt("Seed", seed());
        tag.putInt("Height", bodyHeight());
        tag.putByte("Cores", (byte) brokenCores());
        if (altarPos != null) tag.put("Altar", net.minecraft.nbt.NbtUtils.writeBlockPos(altarPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Palette p = tag.contains("Palette") ? Palette.parse(tag.getString("Palette")) : Palette.STONE;
        int seed = tag.contains("Seed") ? tag.getInt("Seed") : this.random.nextInt();
        int height = tag.contains("Height") ? tag.getInt("Height") : DEFAULT_HEIGHT;
        setBodyParams(p, seed, height);
        if (tag.contains("Cores")) this.entityData.set(DATA_CORES, tag.getByte("Cores"));
        altarPos = tag.contains("Altar") ? net.minecraft.nbt.NbtUtils.readBlockPos(tag, "Altar").orElse(null) : null;
        refreshBossBar();
    }

    /** The altar whose rite woke it (null: summoned some other way). The Titan's altar raises the gate home when it falls. */
    private BlockPos altarPos;

    public void setAltar(BlockPos pos) {
        this.altarPos = pos == null ? null : pos.immutable();
    }

    // ---- parts -----------------------------------------------------------------------------

    /** Where it last stood on something: the way back if it ever goes over the edge of the world. */
    private Vec3 lastGround = null;

    /**
     * True when there is ground somewhere under a point (false over the void of the End). Land that
     * is not loaded counts as none: a giant does not walk, run or jump off the edge of the loaded
     * world, and asking the heightmap there would make the server generate the chunk on the spot.
     */
    boolean groundUnder(Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        if (!this.level().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return false;
        BlockPos g = this.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        return g.getY() > this.level().getMinBuildHeight() + 1;
    }

    /** A step, a run or a leap towards a point over the void is not taken. */
    boolean safeToGo(Vec3 at) {
        return groundUnder(at);
    }

    // ---- the edge of the world -------------------------------------------------------------

    /** > 0 while it hauls itself out of the void; the hang, then the climb. */
    private int climbTicks;
    private Vec3 climbFrom, climbTo, climbGrab;
    private static final int CLIMB_HANG = 18, CLIMB_TOTAL = 78;
    /** This climb's timing: the void climb hangs and hauls for the full count, a ledge in its way takes less. */
    private int climbHang = CLIMB_HANG, climbTotal = CLIMB_TOTAL;

    public boolean isClimbing() {
        return climbTicks > 0;
    }

    /**
     * A mountain does not fall out of the world. Over the edge with nothing under it, it catches the
     * island: the fall stops, a hand closes on the rim, and the body hauls itself up the cliff and
     * onto the ground. Whoever it held is set down on the island first. If there is no island within
     * reach at all, the last ground it stood on takes it back.
     */
    private void tickVoid(ServerLevel server) {
        if (climbTicks > 0) {
            tickClimb(server);
            return;
        }
        if (!this.isAlive() || isWaking()) return;
        int floor = this.level().getMinBuildHeight();
        Vec3 here = this.position();
        boolean overVoid = !groundUnder(here);
        double refY = lastGround != null ? lastGround.y : here.y;
        // a hop across the gap is meant to be over the void: it is caught only once it has clearly missed the stone
        if (attack == Attack.LEAP && leftGround && leapAim != null && this.getY() > leapAim.y - 30) return;
        if (overVoid && !this.onGround() && this.getDeltaMovement().y < -0.25 && this.getY() < refY - 6) {
            Vec3 grab = VoidGuard.nearestGround(server, here, 40, lastGround);
            if (grab != null) startClimb(server, grab); // nothing anywhere? the hard floor below takes over
        }
        // the hard floor: whatever the climb could not do, this does
        if (lastGround != null && this.getY() < floor - 12) {
            climbTicks = 0;
            this.noPhysics = false;
            this.setNoGravity(false);
            this.teleportTo(lastGround.x, lastGround.y + 1, lastGround.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0;
            if (attack != Attack.NONE) endAttack();
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, lastGround.x, lastGround.y + bodyHeight() * 0.3, lastGround.z, 200, bodyHeight() * 0.2, bodyHeight() * 0.3, bodyHeight() * 0.2, 0.3);
            server.playSound(null, lastGround.x, lastGround.y, lastGround.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 8.0F, 0.3F);
            server.playSound(null, lastGround.x, lastGround.y, lastGround.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6.0F, 0.35F);
        }
    }

    private void startClimb(ServerLevel server, Vec3 edge) {
        startClimb(server, edge, CLIMB_HANG, CLIMB_TOTAL);
    }

    /**
     * A wall in its way it cannot step: the cliff a hill makes, the side of the Titan's arena from
     * below, the island above the platform. Higher than a stride, lower than half its height, and
     * ground on top - it reaches up, takes hold and clambers over, the way it hauls itself out of
     * the void, only quicker. Called when it has been pushing at the rock for a while.
     */
    private boolean clamber(ServerLevel server) {
        double h = bodyHeight();
        Vec3 ahead = null;
        BlockPos top = null;
        double rise = 0;
        for (double reach : new double[]{Math.max(3.0, h * 0.06), Math.max(6.0, h * 0.13), Math.max(9.0, h * 0.2)}) {
            Vec3 p = this.position().add(facing().scale(reach));
            if (!groundUnder(p)) return false;
            BlockPos t = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p));
            double r = t.getY() - this.getY();
            if (r > this.maxUpStep() + 0.5) {
                ahead = p;
                top = t;
                rise = r;
                break;
            }
        }
        if (ahead == null || rise > h * 0.55) return false;
        // the top must be open enough to stand on: not a lone pillar
        int room = 0;
        for (int i = 1; i <= 4; i++) {
            Vec3 p = ahead.add(facing().scale(i * 2));
            if (!groundUnder(p)) break;
            BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p));
            if (Math.abs(g.getY() - top.getY()) > 6) break;
            room++;
        }
        if (room < 2) return false;
        int total = 10 + (int) Mth.clamp(rise * 2.2, 26, 56);
        startClimb(server, new Vec3(ahead.x, top.getY(), ahead.z), 10, total);
        stuckTicks = 0;
        WakingWorld.LOGGER.info("colossus {} clambers up {} blocks at {}", this.getId(), rise, top);
        return true;
    }

    private void startClimb(ServerLevel server, Vec3 edge, int hang, int total) {
        this.climbHang = hang;
        this.climbTotal = total;
        double h = bodyHeight();
        // the rim it grabs, and the place it will stand: from the rim on inland while there is ground
        Vec3 dir = new Vec3(edge.x - this.getX(), 0, edge.z - this.getZ());
        dir = dir.lengthSqr() < 1.0E-4 ? facing() : dir.normalize();
        Vec3 stand = edge;
        int inland = Math.max(4, (int) (h * 0.14));
        for (int i = 1; i <= inland; i++) {
            Vec3 p = edge.add(dir.scale(i));
            if (!groundUnder(p)) break;
            BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p));
            if (g.getY() > edge.y + 8) break; // a wall, not the island's top
            stand = new Vec3(p.x, g.getY(), p.z);
        }
        // whoever is in its fist is set down on the island before the climb - the void gets nothing
        Entity held = grabbed();
        if (held != null) {
            grabbedId = -1;
            held.stopRiding();
            held.teleportTo(stand.x, stand.y + 1.0, stand.z);
            held.setDeltaMovement(Vec3.ZERO);
            held.hurtMarked = true;
            held.fallDistance = 0;
            server.sendParticles(ParticleTypes.CLOUD, stand.x, stand.y + 1, stand.z, 20, 0.8, 0.5, 0.8, 0.1);
        }
        if (attack != Attack.NONE) endAttack();
        dropHeld();
        this.getNavigation().stop();
        climbFrom = this.position();
        climbGrab = edge;
        climbTo = new Vec3(stand.x, stand.y + 1, stand.z);
        climbTicks = 1;
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
        // the hand closes on the rim
        BlockPos rim = BlockPos.containing(edge.x, edge.y - 1, edge.z);
        BlockState rimState = server.getBlockState(rim);
        if (rimState.isAir()) rimState = Blocks.END_STONE.defaultBlockState();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rimState), edge.x, edge.y + 0.5, edge.z, 80, 2.5, 1.0, 2.5, 0.3);
        server.playSound(null, edge.x, edge.y, edge.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 9.0F, 0.25F);
        server.playSound(null, edge.x, edge.y, edge.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 7.0F, 0.3F);
        server.playSound(null, edge.x, edge.y, edge.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 5.0F, 0.4F);
        this.level().broadcastEntityEvent(this, EV_ROAR);
        WakingWorld.LOGGER.debug("colossus {} caught the edge at {} from {}", this.getId(), edge, climbFrom);
    }

    private void tickClimb(ServerLevel server) {
        climbTicks++;
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
        double h = bodyHeight();
        // face the island the whole way
        float yaw = (float) (Mth.atan2(climbTo.z - this.getZ(), climbTo.x - this.getX()) * (180F / (float) Math.PI)) - 90F;
        this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 6F));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        if (climbTicks <= climbHang) {
            // hanging from the rim: a little sag, then the pull
            double sag = climbTicks < climbHang / 2 ? 0.06 : -0.03;
            this.setPos(this.getX(), this.getY() - sag, this.getZ());
            if (climbTicks % 6 == 0) {
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, climbGrab.x, climbGrab.y + 0.5, climbGrab.z, 6, 1.5, 0.3, 1.5, 0.02);
                server.playSound(null, climbGrab.x, climbGrab.y, climbGrab.z, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 5.0F, 0.3F);
            }
            return;
        }
        double p = Mth.clamp((climbTicks - climbHang) / (double) (climbTotal - climbHang), 0.0, 1.0);
        double ph = p * p * (3 - 2 * p); // smooth in and out
        // up first, then in: the body rises along the cliff face and rolls over the edge at the end
        double up = Math.min(1.0, p * 1.35);
        double upE = up * up * (3 - 2 * up);
        double in = Math.max(0.0, (p - 0.35) / 0.65);
        double inE = in * in * (3 - 2 * in);
        double x = Mth.lerp(inE, climbFrom.x, climbTo.x);
        double z = Mth.lerp(inE, climbFrom.z, climbTo.z);
        double y = Mth.lerp(upE, climbFrom.y, climbTo.y + 0.5) + Math.sin(p * Math.PI) * 1.5;
        this.setPos(x, y, z);
        this.hasImpulse = true;
        if (climbTicks % 5 == 0) {
            // stone grinding on stone, the cliff shedding under the grip
            Vec3 hand = this.position().add(0, h * 0.55, 0).add(facing().scale(h * 0.12));
            BlockPos at = BlockPos.containing(hand.x, hand.y, hand.z);
            BlockState s = server.getBlockState(at);
            if (s.isAir()) s = Blocks.END_STONE.defaultBlockState();
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), hand.x, hand.y, hand.z, 30, 2.0, 2.0, 2.0, 0.25);
            server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 5.0F, 0.3F + (float) p * 0.2F);
            server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 4.0F, 0.35F);
        }
        if (climbTicks >= climbTotal) {
            climbTicks = 0;
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setPos(climbTo.x, climbTo.y, climbTo.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            lastGround = climbTo;
            this.attackCooldown = Math.max(this.attackCooldown, 30);
            land(server, h);
            WakingWorld.LOGGER.debug("colossus {} climbed out onto {}", this.getId(), climbTo);
        }
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel server) {
            me.lovkar.wakingworld.ruin.RuinLedger ledger = me.lovkar.wakingworld.ruin.RuinLedger.get(server);
            me.lovkar.wakingworld.ruin.Ruin.begin(ledger, ledger.open(this.getUUID(), this.blockPosition(), server.getGameTime()));
            try {
                tickInner();
            } finally {
                me.lovkar.wakingworld.ruin.Ruin.end();
            }
        } else {
            tickInner();
        }
    }

    /** The record of what this fight did to the land, opened on first use. */
    public me.lovkar.wakingworld.ruin.FightRecord fightRecord() {
        if (!(this.level() instanceof ServerLevel server)) return null;
        me.lovkar.wakingworld.ruin.RuinLedger ledger = me.lovkar.wakingworld.ruin.RuinLedger.get(server);
        return ledger.open(this.getUUID(), this.blockPosition(), server.getGameTime());
    }

    private void tickInner() {
        updateParts(false);
        super.tick();
        if (!this.level().isClientSide && this.level() instanceof ServerLevel server) {
            if (this.onGround() && this.isAlive() && climbTicks == 0) lastGround = this.position();
            tickVoid(server);
        }
        this.strideO = this.stride;
        if (climbTicks == 0) this.stride += (float) Math.sqrt((this.getX() - this.xo) * (this.getX() - this.xo) + (this.getZ() - this.zo) * (this.getZ() - this.zo));
        if (!this.level().isClientSide && this.isAlive()) {
            if (isWaking()) tickWake((ServerLevel) this.level());
            else if (climbTicks == 0) unbury();
        }
        if (!this.level().isClientSide && this.level() instanceof ServerLevel server && climbTicks == 0 && (this.isAlive() || this.deathTime < ColossusPose.DEATH_FINAL)) {
            sweepBody(server);
        }
        if (!this.level().isClientSide && this.level() instanceof ServerLevel server) tickTempBlocks(server, false);
        updateParts(false);
        if (this.isAlive() && !isWaking() && climbTicks == 0) footsteps();
        if (this.level().isClientSide) {
            if (this.clientAttack != Attack.NONE) this.clientAttackTicks++;
            this.clientWakeO = this.clientWake;
            if (this.clientWake > 0) {
                this.clientWake--;
                // the ground trembles more and more as it comes up, and the earth it slept in
                // streams off every surface that has cleared the ground
                WakingWorld.hooks.shakeAt(this.position(), 0.06F + 0.4F * wakeProgress(1f), bodyHeight() * 2.2);
                shed(18 + (int) (wakeProgress(1f) * 20));
            }
            if (this.isAlive() && !isWaking()) pushLocalPlayer();
            for (int i = 0; i < coreFlash.length; i++) if (coreFlash[i] > 0) coreFlash[i]--;
            this.flinchTicksO = this.flinchTicks;
            if (this.flinchTicks > 0) this.flinchTicks--;
        }
    }

    /** The horizontal speed of the jump in the air, held against the drag. */
    private double leapVx, leapVz;
    /** Where a hop is going: the stone across the void this leap was started for (null: the leap goes for the target). */
    private Vec3 leapAim;
    /** Ticks a hop's flight has been held past the leap's own length. */
    private int leapHold;

    /** How far a leap carries, flat: the Titan crosses the End's gaps, the others jump at what they can see. */
    double leapReach() {
        return isTitan() ? 66.0 : 48.0;
    }

    /** True when the straight way from here to there has the void under it somewhere (the End's islands). */
    boolean gapBetween(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double flat = d.horizontalDistance();
        if (flat < 6) return false;
        int steps = (int) Math.ceil(Math.min(flat, 120.0) / 5.0);
        for (int i = 1; i < steps; i++) {
            Vec3 p = from.add(d.x * i / steps, 0, d.z * i / steps);
            if (!groundUnder(p)) return true;
        }
        return false;
    }

    /**
     * The Titan on one island and its quarry on another: the next stone across. Around it, as far
     * out as a leap carries - first the wedge towards the target, then all round - it looks for
     * ground with room to come down on (not a spire, not a lone pillar, not a wall higher than the
     * jump) with the void somewhere between here and there (the same island it could simply walk),
     * and takes the one that brings it nearest its quarry, straight ahead over off to the side.
     */
    Vec3 steppingStone(ServerLevel server, Vec3 target) {
        Vec3 here = this.position();
        Vec3 to = target.subtract(here);
        double dist = to.horizontalDistance();
        if (dist < 8) return null;
        Vec3 dir = new Vec3(to.x / dist, 0, to.z / dist);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double reach = leapReach();
        List<Vec3> candidates = new ArrayList<>();
        for (double ahead = Math.min(dist, reach); ahead >= 14; ahead -= 4) {
            for (double lat : new double[]{0, 8, -8, 16, -16, 24, -24, 32, -32}) {
                if (Math.abs(lat) > ahead * 0.8) continue;
                candidates.add(here.add(dir.scale(ahead)).add(side.scale(lat)));
            }
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            for (double r : new double[]{reach, reach - 8, reach - 16, reach - 26, reach - 38, 20}) candidates.add(here.add(d.scale(r)));
        }
        Vec3 best = null;
        double bestScore = -1e9;
        for (Vec3 p : candidates) {
            if (!groundUnder(p)) continue;
            int top = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p)).getY();
            double rise = top - here.y;
            if (rise > 44 || rise < -70) continue;
            double remaining = Math.sqrt((target.x - p.x) * (target.x - p.x) + (target.z - p.z) * (target.z - p.z));
            double progress = dist - remaining;
            if (progress < 8) continue; // no nearer its quarry than it stands
            double turn = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-(p.x - here.x), p.z - here.z)) - this.yBodyRot));
            double score = progress - 0.08 * turn - Math.max(0, rise) * 0.15;
            if (score <= bestScore) continue; // the cheap tests first; the ground is asked about only for a better one
            int room = 0;
            for (Vec3 n : new Vec3[]{p.add(4, 0, 0), p.add(-4, 0, 0), p.add(0, 0, 4), p.add(0, 0, -4)}) {
                if (!groundUnder(n)) continue;
                int ny = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(n)).getY();
                if (Math.abs(ny - top) <= 6) room++;
            }
            if (room < 3) continue;
            if (!gapBetween(here, p)) continue; // its own island: it walks there
            bestScore = score;
            best = new Vec3(p.x, top, p.z);
        }
        return best;
    }

    /** The stone the Titan is turning to face before it hops for it (null: none). The combat goal looks that way. */
    private Vec3 hopTurn;

    public Vec3 hopTurn() {
        return hopTurn;
    }

    /**
     * Called by the combat goal when a move is ready: if the target stands across the void, the Titan
     * hops for the next island towards it instead of picking a move it cannot land. True when a hop
     * was started.
     */
    public boolean hop(LivingEntity target) {
        if (!isTitan() || !(this.level() instanceof ServerLevel server) || !attackReady() || !canUse(Attack.LEAP)) {
            hopTurn = null;
            return false;
        }
        if (climbTicks > 0 || !this.onGround()) return false;
        if (this.tickCount % 8 != 0) return hopTurn != null; // the scan is not free: twice a second (turning to face a stone in between)
        Vec3 goal = target.position();
        if (!groundUnder(goal) || !gapBetween(this.position(), goal)) {
            hopTurn = null;
            return false;
        }
        Vec3 stone = steppingStone(server, goal);
        if (stone == null) {
            hopTurn = null;
            return false;
        }
        // a stone well off to the side: it turns to face it first (the goal looks that way, the body follows), then jumps
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-(stone.x - this.getX()), stone.z - this.getZ()));
        if (Math.abs(Mth.wrapDegrees(wantedYaw - this.yBodyRot)) > 40F) {
            hopTurn = stone;
            return true;
        }
        hopTurn = null;
        leapAim = stone;
        startAttack(Attack.LEAP);
        WakingWorld.LOGGER.info("colossus {} hops for {} (target {} blocks off)", this.getId(), stone, (int) this.distanceTo(target));
        return true;
    }

    /** How many ticks a body thrown up at {@code vy} is in the air before it comes down {@code rise} above where it left (living-entity gravity 0.08, drag 0.98 a tick). */
    static int leapTicks(double vy, double rise) {
        double y = 0, v = vy;
        int t = 0;
        boolean up = true;
        while (t < 400) {
            y += v;
            v = (v - 0.08) * 0.98;
            t++;
            if (v < 0) up = false;
            if (!up && y <= rise) break;
        }
        return Math.max(8, t);
    }

    // ---- being hit -------------------------------------------------------------------------

    /** Client: the recoil from a blow that was worth feeling, counting down from {@link #FLINCH_TICKS}. */
    private int flinchTicks, flinchTicksO;
    private float flinchPower;
    private static final int FLINCH_TICKS = 9;

    /** Client: 0..1 through the recoil, 0 when there is none. */
    public float flinch(float partialTick) {
        if (flinchTicks <= 0 && flinchTicksO <= 0) return 0f;
        float t = Mth.lerp(partialTick, flinchTicksO, flinchTicks);
        return Mth.clamp(1f - t / FLINCH_TICKS, 0f, 1f);
    }

    public float flinchPower() {
        return flinchPower;
    }

    /**
     * How much a blow is worth to a mountain: nothing under 1.5 % of its health (an arrow, a sword),
     * a twitch to 4 %, a proper recoil to 8 %, a stagger beyond (the hammer's dive, a core going).
     */
    private int blowLevel(float amount) {
        float r = amount / Math.max(1f, this.getMaxHealth());
        return r < 0.015f ? 0 : r < 0.04f ? 1 : r < 0.08f ? 2 : 3;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean did = super.hurt(source, amount);
        if (did && !this.level().isClientSide && amount > 0 && this.isAlive()) {
            int level = blowLevel(amount);
            if (level > 0) this.level().broadcastEntityEvent(this, (byte) (EV_BLOW + level - 1));
        }
        return did;
    }

    /**
     * Vanilla's answer to any hit is to throw the limbs into a full walk swing for a few ticks - on
     * a forty-block giant a single arrow made the arms fly. The red flash and the hurt sound stay;
     * the recoil is our own, and only for blows that would move a mountain ({@link #EV_BLOW}).
     */
    @Override
    public void handleDamageEvent(DamageSource source) {
        float speed = this.walkAnimation.speed();
        super.handleDamageEvent(source);
        this.walkAnimation.setSpeed(speed);
    }

    /** Client: > 0 while a core flashes white from a hit. */
    public int coreFlash(int core) {
        return core >= 0 && core < coreFlash.length ? coreFlash[core] : 0;
    }

    /** Cores still beating. */
    public int intactCores() {
        int n = 0, broken = brokenCores();
        for (int i = 0; i < body().cores.size(); i++) if ((broken & (1 << i)) == 0) n++;
        return n;
    }

    /**
     * The cores hold it up: while more than two still beat, its health cannot fall below a tenth
     * of the maximum for each core beyond those two (five cores: 30 %, four: 20 %, three: 10 %).
     * Break the cores - the arrows and the swords only take it so far.
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && amount > 0 && !this.isInvulnerableTo(source)) {
            int extra = intactCores() - 2;
            if (extra > 0) {
                float floor = this.getMaxHealth() * 0.10F * extra;
                if (this.getHealth() - amount < floor) {
                    float allowed = Math.max(0F, this.getHealth() - floor);
                    if (allowed < amount && this.level() instanceof ServerLevel server) shielded(server, source);
                    amount = allowed;
                }
            }
        }
        super.actuallyHurt(source, amount);
    }

    private void shielded(ServerLevel server, DamageSource source) {
        this.level().broadcastEntityEvent(this, EV_SHIELD);
        Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.55);
        server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4.0F, 0.7F);
        server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 4.0F, 0.5F);
        if (source.getEntity() instanceof ServerPlayer player) {
            long now = server.getGameTime();
            Long last = shieldTold.get(player.getId());
            if (last == null || now - last > 80) {
                shieldTold.put(player.getId(), now);
                player.displayClientMessage(Component.translatable("entity.wakingworld.colossus.shielded", intactCores()).withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
        }
    }

    // ---- waking up: rising out of the ground ----------------------------------------------

    /** Starts the rise: {@link #WAKE_TICKS} of coming up out of the ground, untouchable, boss bar filling. */
    public void setWake(int ticks) {
        this.entityData.set(DATA_WAKE, Math.max(0, ticks));
        this.clientWake = this.clientWakeO = ticks;
        this.wakeBaseY = this.getY();
    }

    /** Where the ground was when the rise began: the crust above it is what bursts off the climbing body. */
    private double wakeBaseY = Double.NaN;

    public boolean isWaking() {
        return this.level().isClientSide ? this.clientWake > 0 : this.entityData.get(DATA_WAKE) > 0;
    }

    public boolean isTitan() {
        return "titan".equals(palette().kind);
    }

    /** How long this one's rise takes, ticks. */
    public int wakeTotal() {
        return isTitan() ? TITAN_WAKE_TICKS : WAKE_TICKS;
    }

    /** Ground covered so far, blocks, interpolated - the walk cycle runs on it. */
    public float stride(float partialTick) {
        return Mth.lerp(partialTick, this.strideO, this.stride);
    }

    /** How fast the body may turn, degrees a tick: a mountain does not spin on its heel; it gets a little quicker with each phase. */
    public float turnRate() {
        float base = 1.2F + 28F / Math.max(12, bodyHeight());
        int phase = phase();
        return base * (phase >= 3 ? 1.35F : phase == 2 ? 1.15F : 1F);
    }

    /** 0 = still fully under the ground, 1 = standing (client, interpolated). */
    public float wakeProgress(float partialTick) {
        float w = Mth.lerp(partialTick, this.clientWakeO, this.clientWake);
        return Mth.clamp(1f - w / wakeTotal(), 0f, 1f);
    }

    /** How far below its standing position the body is right now because it is still rising (blocks, >= 0); both sides. */
    public double riseOffset() {
        float p = this.level().isClientSide ? wakeProgress(1f) : Mth.clamp(1f - this.entityData.get(DATA_WAKE) / (float) wakeTotal(), 0f, 1f);
        if (p >= 1f) return 0.0;
        float s = p * p * (3f - 2f * p);
        return (1f - s) * (bodyHeight() + 2.0);
    }

    /**
     * Nothing stands where the body is. Every tick the posed surface of the body - as it walks,
     * rises out of the ground, kneels and falls - is checked against the world, and blocks found
     * inside it come out: a share fly off as real blocks, the rest turn to dust. Only at and above
     * the level of the feet, so the ground it stands on (and sleeps in) stays; anything unbreakable
     * or with a block entity stays too. That is the crust bursting off it as it climbs out, the
     * tree that no longer pokes through its chest when it falls, the hillside its shoulder cuts.
     */
    private void sweepBody(ServerLevel server) {
        if (!WakingConfig.trample()) return;
        boolean hard = WakingConfig.terrainDamage();
        List<Cell> cells = surfaceCells();
        if (cells.isEmpty()) return;
        ColossusBody b = body();
        ColossusPose pose = currentPose(b.height);
        PosedBody posed = new PosedBody(b, pose);
        double sunk = riseOffset();
        boolean waking = sunk > 0;
        // the ground it stands on stays - except while it climbs out, when the top three blocks of
        // the ground it was buried under come up with it
        double floor = waking && !Double.isNaN(wakeBaseY) ? wakeBaseY - 3.5 : this.getY() - 0.5;
        double theta = Math.toRadians(180.0 - this.yBodyRot);
        double cos = Math.cos(theta), sin = Math.sin(theta);
        double fc = Math.cos(pose.fall), fs = Math.sin(pose.fall);
        int cleared = 0, flung = 0, budget = waking ? 160 : 110, flingBudget = waking ? 40 : 24;
        BlockState first = null;
        BlockPos firstPos = null;
        Vec3 center = bodyPoint(0, b.height * 0.5, 0);
        int n = cells.size(), start = this.random.nextInt(n);
        double[] v = new double[3];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n && cleared < budget; i++) {
            Cell c = cells.get((start + i) % n);
            if (this.deathTime > 0 && ColossusPose.crumbled(c.def().kind, this.deathTime)) continue;
            v[0] = c.x(); v[1] = c.y(); v[2] = c.z();
            posed.apply(c.def(), v);
            double y = v[1] + pose.drop, z = v[2];
            double ry = y * fc - z * fs + pose.lift - sunk, rz = y * fs + z * fc;
            double wx = this.getX() + v[0] * cos + rz * sin;
            double wy = this.getY() + ry;
            double wz = this.getZ() - v[0] * sin + rz * cos;
            if (wy < floor) continue;
            pos.set(Mth.floor(wx), Mth.floor(wy), Mth.floor(wz));
            BlockState state = server.getBlockState(pos);
            if (state.isAir()) continue;
            boolean soft = Crater.vegetation(state) || Crater.trampleable(server, pos, state);
            if (!soft && (!hard || !Crater.breakable(server, pos, state))) continue;
            cleared++;
            if (first == null) { first = state; firstPos = pos.immutable(); }
            if (hard && flung < flingBudget && state.canOcclude() && this.random.nextInt(waking ? 2 : 4) == 0) {
                Crater.fling(server, pos.immutable(), state, waking ? this.position() : center, waking ? 0.9 : 0.6, waking ? 0.75 : 0.25, this.random);
                flung++;
            } else {
                me.lovkar.wakingworld.ruin.Ruin.mark(server, pos);
                server.removeBlock(pos, false);
                if (cleared % 2 == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), wx, wy, wz, 5, 0.5, 0.5, 0.5, 0.12);
            }
        }
        if (first != null && this.tickCount % 3 == 0) {
            server.playSound(null, firstPos.getX(), firstPos.getY(), firstPos.getZ(), first.getSoundType().getBreakSound(),
                    SoundSource.HOSTILE, Math.min(4.0F, 1.0F + cleared * 0.08F), 0.45F + this.random.nextFloat() * 0.2F);
        }
    }

    /**
     * The Titan's rise, on top of the usual one: the void bleeds up through the floor - a ring of
     * crying-obsidian cracks spreads out across the arena (temporary, they seal again after it
     * stands), reverse-portal light streams up out of them, dragon's breath pools, end-rod sparks
     * climb, and in the last second everyone near is lifted off their feet as it breaks out.
     */
    private void tickTitanWake(ServerLevel server, Vec3 at, double h, float p, int wake) {
        double ring = h * (0.12 + 0.55 * p);
        if (wake % 6 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = this.random.nextDouble() * Math.PI * 2, r = ring * (0.5 + 0.5 * this.random.nextDouble());
                BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r)).below();
                BlockState was = server.getBlockState(g);
                if (was.isAir() || !was.canOcclude() || was.is(Blocks.CRYING_OBSIDIAN) || server.getBlockEntity(g) != null) continue;
                tempOverlay(server, g, Blocks.CRYING_OBSIDIAN.defaultBlockState(), wake + 200 + this.random.nextInt(200));
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, g.getX() + 0.5, g.getY() + 1.2, g.getZ() + 0.5, 12, 0.4, 0.6, 0.4, 0.1);
                server.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.0F, 0.5F + this.random.nextFloat() * 0.3F);
            }
        }
        if (wake % 2 == 0) {
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 10 + (int) (p * 20), ring * 0.7, 0.5, ring * 0.7, 0.15);
            server.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y + 0.8, at.z, 4 + (int) (p * 6), ring * 0.6, 0.3, ring * 0.6, 0.02);
        }
        if (wake % 3 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + h * p * 0.6, at.z, 3 + (int) (p * 6), h * 0.2, h * 0.3 * p + 1, h * 0.2, 0.05);
        }
        if (wake % 40 == 0 && wake > 0) {
            server.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 6.0F, 0.5F);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 6.0F, 0.4F);
        }
        if (wake == 20) {
            // the last second: the void pulls everyone near it off the ground
            for (Player pl : server.getEntitiesOfClass(Player.class, new AABB(at.x - h, at.y - 10, at.z - h, at.x + h, at.y + h, at.z + h))) {
                if (pl.isSpectator() || pl.isCreative()) continue;
                pl.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 1));
            }
            server.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 8.0F, 0.6F);
        }
        if (wake == 0) {
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + h * 0.5, at.z, 400, h * 0.3, h * 0.5, h * 0.3, 0.4);
            server.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y + 1, at.z, 150, h * 0.5, 1, h * 0.5, 0.05);
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + h * 0.8, at.z, 120, h * 0.25, h * 0.3, h * 0.25, 0.15);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + this.random.nextDouble() * 0.4;
                lightning(server, at.add(Math.cos(a) * h * 0.55, 0, Math.sin(a) * h * 0.55), 1);
            }
            server.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 9.0F, 0.3F);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6.0F, 0.35F);
        }
    }

    /**
     * The earth heaves, cracks and spits out its stones in a widening ring; a rumble grows under
     * everything; the boss bar fills as the body comes up. At the end the last heave throws the
     * ground up all around and the giant roars for the first time.
     */
    private void tickWake(ServerLevel server) {
        int wake = this.entityData.get(DATA_WAKE) - 1;
        this.entityData.set(DATA_WAKE, wake);
        double h = bodyHeight();
        int total = wakeTotal();
        float p = 1f - wake / (float) total;
        this.bossEvent.setProgress(Mth.clamp(p, 0f, 1f));
        boolean titan = isTitan();
        // held at the level the ground had: the crust it throws off may open a pit under it, and it
        // drops into that with the last heave, not halfway up
        this.setDeltaMovement(Vec3.ZERO);
        if (!Double.isNaN(wakeBaseY)) this.setPos(this.getX(), wakeBaseY, this.getZ());
        Vec3 at = this.position();
        if (wake == total - 1) {
            server.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 8.0F, 0.55F);
            if (titan) {
                // the whole End hears it: the Wither's birth-cry, heard everywhere, and the dragon's growl
                server.globalLevelEvent(1023, BlockPos.containing(at), 0);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.4F);
            }
        }
        if (titan) tickTitanWake(server, at, h, p, wake);
        if (wake % 25 == 0) {
            server.playSound(null, at.x, at.y, at.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 5.0F, 0.3F);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 6.0F, 0.4F);
        }
        // a ring of dust of the real ground, widening with the rise
        double ring = h * (0.12 + 0.38 * p);
        int points = 6 + (int) (p * 8);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points + wake * 0.23;
            double px = at.x + Math.cos(a) * ring * (0.7 + 0.3 * this.random.nextDouble());
            double pz = at.z + Math.sin(a) * ring * (0.7 + 0.3 * this.random.nextDouble());
            BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(px, at.y, pz));
            BlockState below = server.getBlockState(g.below());
            if (below.isAir()) continue;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), px, g.getY() + 0.3, pz, 3, 0.4, 0.5 + p, 0.4, 0.25);
            if (i % 3 == 0) server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, g.getY() + 0.5, pz, 1, 0.3, 0.3, 0.3, 0.02);
        }
        // the heaving earth: the odd loose block jumps up and lands back
        if (wake % 3 == 0 && WakingConfig.terrainDamage()) {
            heave(server, at, h * (0.15 + 0.35 * p), 1 + (int) (p * 2), 0.25);
        }
        // the kind shows itself: snow, sand, spores, bubbles or sparks drifting up out of the ground
        if (wake % 2 == 0) {
            double above = h * p * 0.9;
            server.sendParticles(themeParticle(), at.x, at.y + above * 0.5, at.z, 4 + (int) (p * 8), h * 0.2, Math.max(1.0, above * 0.5), h * 0.2, 0.05);
            server.sendParticles(themeParticle2(), at.x, at.y + above * 0.5, at.z, 2 + (int) (p * 4), h * 0.25, Math.max(1.0, above * 0.5), h * 0.25, 0.02);
        }
        if (wake == 0) {
            // up and out: one big heave all around, dust, its element bursting off it, lightning, and the first roar
            heave(server, at, h * 0.5, 14, 0.45);
            ring(server, at, h * 1.2, 1.4);
            for (int i = 0; i < 40; i++) {
                double a = this.random.nextDouble() * Math.PI * 2, rr = h * (0.2 + 0.4 * this.random.nextDouble());
                server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.rune(kindColor(), 1.5f), at.x + Math.cos(a) * rr, at.y + 1 + this.random.nextDouble() * 3, at.z + Math.sin(a) * rr, 1, 0, 0.12, 0, 0.02);
            }
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2, h * 0.15, 1, h * 0.15, 0);
            server.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 2, at.z, 60, h * 0.2, 2, h * 0.2, 0.03);
            server.sendParticles(themeParticle(), at.x, at.y + h * 0.5, at.z, 160, h * 0.25, h * 0.4, h * 0.25, 0.12);
            server.sendParticles(themeParticle2(), at.x, at.y + h * 0.5, at.z, 80, h * 0.3, h * 0.45, h * 0.3, 0.05);
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + h * 0.7, at.z, 40, h * 0.15, h * 0.2, h * 0.15, 0.08);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0F, 0.35F);
            lightning(server, at.add(facing().scale(-0.1 * h)), 2);
            this.level().broadcastEntityEvent(this, EV_EMERGE);
            double heard = Math.max(120.0, h * 4.0);
            for (ServerPlayer sp : server.getPlayers(pl -> pl.distanceToSqr(this) < heard * heard)) {
                me.lovkar.wakingworld.advancement.WakingTriggers.COLOSSUS_WOKEN.get().trigger(sp, palette().kind, bodyHeight());
            }
            me.lovkar.wakingworld.story.Chronicle.record(server, "woken", palette().kind, blockPosition(), null);
            this.attackCooldown = 0;
            startAttack(Attack.ROAR);
            this.attackCooldown = 30;
        }
    }

    /** A particle in the kind's spirit: snow for ice, sand for sandstone, spores for moss, bubbles for prismarine, sparks for stone and earth. */
    public net.minecraft.core.particles.ParticleOptions themeParticle() {
        return switch (palette().kind) {
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "sandstone" -> new BlockParticleOption(ParticleTypes.FALLING_DUST, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
            case "moss" -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case "prismarine" -> ParticleTypes.BUBBLE_POP;
            default -> ParticleTypes.LAVA;
        };
    }

    /** Its quieter companion: white ash, ash, green sparkles, nautilus shimmer, smoke. */
    public net.minecraft.core.particles.ParticleOptions themeParticle2() {
        return switch (palette().kind) {
            case "ice" -> ParticleTypes.WHITE_ASH;
            case "sandstone" -> ParticleTypes.ASH;
            case "moss" -> ParticleTypes.HAPPY_VILLAGER;
            case "prismarine" -> ParticleTypes.NAUTILUS;
            default -> ParticleTypes.SMOKE;
        };
    }

    /**
     * Client: earth falling off the body as it rises - block dust of its own palette (and now and
     * then its element) from random surface cells that are already above the ground.
     */
    private void shed(int count) {
        List<Cell> cells = surfaceCells();
        if (cells.isEmpty()) return;
        ColossusBody b = body();
        ColossusPose pose = currentPose(b.height);
        PartDef torso = b.part(PartDef.Kind.TORSO);
        float rise = wakeProgress(1f);
        double sunk = (1f - rise * rise * (3f - 2f * rise)) * (b.height + 2.0);
        for (int i = 0; i < count; i++) {
            Cell c = cells.get(this.random.nextInt(cells.size()));
            double[] v = {c.x(), c.y(), c.z()};
            pose.transform(v, c.def(), torso);
            double y = v[1] - sunk;
            if (y < 0.3) continue;
            Vec3 w = bodyPoint(v[0], y, v[2]);
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, palette().pick(this.random)),
                    w.x, w.y, w.z, (this.random.nextDouble() - 0.5) * 0.25, -0.05 - this.random.nextDouble() * 0.15, (this.random.nextDouble() - 0.5) * 0.25);
            if (i % 5 == 0) this.level().addParticle(themeParticle(), w.x, w.y, w.z, 0, 0.05, 0);
        }
    }

    /** Throws a few loose surface blocks within {@code radius} of {@code at} a little way into the air. */
    private void heave(ServerLevel server, Vec3 at, double radius, int count, double power) {
        for (int i = 0; i < count; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = radius * (0.3 + 0.7 * this.random.nextDouble());
            BlockPos top = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r)).below();
            if (Math.abs(top.getY() - at.y) > 6) continue;
            BlockState s = server.getBlockState(top);
            if (!Crater.trampleable(server, top, s) || !s.canOcclude()) continue;
            Crater.fling(server, top, s, at, power, this.random);
        }
        this.level().broadcastEntityEvent(this, EV_HEAVE);
    }

    /**
     * Foot plants of the walk cycle, on both sides from the same numbers the pose uses: the
     * server thuds and throws dust at the foot that just came down, the client shakes the camera
     * by how close and how big the giant is.
     */
    private void footsteps() {
        float speed = this.walkAnimation.speed();
        double h = bodyHeight();
        int k = Mth.floor(this.stride * 2f / ColossusPose.cycleLength((int) h)); // one foot plants every half cycle
        if (lastPlant == Integer.MIN_VALUE || speed < 0.12f) {
            lastPlant = k;
            return;
        }
        if (k == lastPlant) return;
        lastPlant = k;
        boolean right = (k & 1) != 0;
        Vec3 foot = bodyPoint(right ? 0.13 * h : -0.13 * h, 0, -0.10 * h);
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, foot.x, foot.y, foot.z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 4.0F, 0.3F);
            server.playSound(null, foot.x, foot.y, foot.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 5.0F, 0.55F);
            groundBurst(server, foot, 14 + (int) (h / 4), 1.5 + h * 0.03);
        } else {
            WakingWorld.hooks.shakeAt(foot, 0.3F + (float) h / 55F, h * 0.9);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        double h = bodyHeight();
        int phase = phase();
        switch (id) {
            case EV_STOMP -> {
                Vec3 foot = bodyPoint(stompRightFoot() ? 0.13 * h : -0.13 * h, 0, -0.06 * h);
                WakingWorld.hooks.shakeAt(foot, 2.2F + (float) h / 40F, h * 1.3);
                WakingWorld.hooks.wave(foot, 1.3, 0.9 * h + phase * 6, 1.4F);
            }
            case EV_SLAM -> {
                Vec3 at = bodyPoint(0, 0, -0.30 * h);
                WakingWorld.hooks.shakeAt(at, 3.2F + (float) h / 40F, h * 1.5);
                WakingWorld.hooks.wave(at, 1.5, 1.2 * h + phase * 6, 1.8F);
            }
            case EV_LAND -> {
                WakingWorld.hooks.shakeAt(this.position(), 4.0F + (float) h / 30F, h * 2.0);
                WakingWorld.hooks.wave(this.position(), 1.6, 1.5 * h, 2.2F);
            }
            case EV_CRASH -> {
                Vec3 at = this.position().add(facing().scale(0.14 * h));
                WakingWorld.hooks.shakeAt(at, 2.0F, h * 1.2);
                WakingWorld.hooks.wave(at, 1.4, 0.45 * h, 1.0F);
            }
            case EV_ROAR -> WakingWorld.hooks.shakeAt(bodyPoint(0, 0.7 * h, 0), 2.0F, h * 1.6);
            case EV_COLLAPSE -> {
                WakingWorld.hooks.shakeAt(this.position(), 5.5F + (float) h / 30F, h * 2.5);
                // the rubble keeps coming down for a while
                for (int i = 1; i <= 6; i++) WakingWorld.hooks.wave(this.position(), 8.0 / i, h * 2.0, 0.9F);
            }
            case EV_CORE -> WakingWorld.hooks.shakeAt(this.position(), 1.2F, h * 1.2);
            case EV_CORE_HIT, EV_CORE_HIT + 1, EV_CORE_HIT + 2, EV_CORE_HIT + 3, EV_CORE_HIT + 4 -> {
                int c = id - EV_CORE_HIT;
                if (c < coreFlash.length) coreFlash[c] = 8;
                WakingWorld.hooks.shake(0.3F);
            }
            case EV_SHIELD -> {
                java.util.Arrays.fill(coreFlash, 14);
                WakingWorld.hooks.shake(0.35F);
            }
            case EV_BLOW, EV_BLOW + 1, EV_BLOW + 2 -> {
                int level = id - EV_BLOW;
                this.flinchPower = level == 0 ? 0.35F : level == 1 ? 0.7F : 1.0F;
                this.flinchTicks = FLINCH_TICKS;
                this.flinchTicksO = FLINCH_TICKS;
                if (level == 2) WakingWorld.hooks.shake(0.2F);
            }
            case EV_PHASE -> WakingWorld.hooks.shakeAt(this.position(), 2.5F, h * 1.8);
            case EV_EMERGE -> {
                WakingWorld.hooks.shakeAt(this.position(), 4.5F + (float) h / 30F, h * 2.5);
                WakingWorld.hooks.wave(this.position(), 1.5, 1.2 * h, 1.5F);
            }
            case EV_HEAVE -> WakingWorld.hooks.shakeAt(this.position(), 0.5F, h * 1.5);
            case EV_DEATH_START -> WakingWorld.hooks.shakeAt(this.position(), 1.5F, h * 1.8);
            case EV_DEATH_KNEE -> {
                WakingWorld.hooks.shakeAt(this.position(), 3.0F + (float) h / 40F, h * 1.6);
                WakingWorld.hooks.wave(this.position(), 1.3, 0.7 * h, 1.0F);
            }
            case EV_DEATH_FALL -> {
                WakingWorld.hooks.shakeAt(this.position().add(facing().scale(0.4 * h)), 5.0F + (float) h / 30F, h * 2.5);
                WakingWorld.hooks.wave(this.position().add(facing().scale(0.4 * h)), 1.5, 1.3 * h, 2.0F);
            }
            case EV_DEATH_CRUMBLE -> WakingWorld.hooks.shakeAt(this.position().add(facing().scale(0.3 * h)), 1.6F, h * 1.6);
            default -> super.handleEntityEvent(id);
        }
    }

    /**
     * The body is solid for the player looking at it (see {@link ColossusPart#canBeCollidedWith}).
     * That stops a player walking into a leg - it does not stop a leg walking into a player. When
     * a part has swept into the local player, it shoves them along with it, the way a shulker
     * pushes what its shell opens onto; if they are simply inside (blocked against a wall, or the
     * part appeared around them) they go out the shortest way sideways. Standing on a part does
     * not last: the player slides off it.
     */
    private void pushLocalPlayer() {
        for (Player p : this.level().players()) {
            if (!p.isLocalPlayer() || p.isSpectator() || p.noPhysics || p.isPassenger()) continue;
            for (ColossusPart part : this.parts) {
                if (part.core >= 0) continue; // the core boxes are targets, not walls
                AABB pb = p.getBoundingBox();
                AABB box = part.getBoundingBox();
                if (!box.intersects(pb)) {
                    boolean overlapsXZ = pb.maxX > box.minX && pb.minX < box.maxX && pb.maxZ > box.minZ && pb.minZ < box.maxZ;
                    // standing on it? nobody stays on a giant for long - the tops are round, you slide
                    // off (and no server has to wonder why a player is floating in mid-air)
                    if (overlapsXZ && pb.minY >= box.maxY - 0.02 && pb.minY <= box.maxY + 0.06) {
                        Vec3 d = pb.getCenter().subtract(box.getCenter());
                        double toEdgeX = box.getXsize() / 2 - Math.abs(d.x), toEdgeZ = box.getZsize() / 2 - Math.abs(d.z);
                        Vec3 slide = toEdgeX <= toEdgeZ
                                ? new Vec3(Math.copySign(0.2, d.x == 0 ? 1 : d.x), 0, 0)
                                : new Vec3(0, 0, Math.copySign(0.2, d.z == 0 ? 1 : d.z));
                        p.move(MoverType.SHULKER, slide);
                    }
                    // caught under the body with no headroom (between the legs, under a kneeling torso):
                    // it eases you out from under it, out the shorter way - front or back for choice, the
                    // legs are at the sides
                    if (overlapsXZ && part.kind == PartDef.Kind.TORSO && pb.maxY <= box.minY + 0.01 && box.minY - pb.maxY < 1.5) {
                        double cx = (pb.minX + pb.maxX) / 2, cz = (pb.minZ + pb.maxZ) / 2;
                        double ex0 = (cx - box.minX) * 1.6, ex1 = (box.maxX - cx) * 1.6, ez0 = cz - box.minZ, ez1 = box.maxZ - cz;
                        double m = Math.min(Math.min(ex0, ex1), Math.min(ez0, ez1));
                        Vec3 slide = m == ez0 ? new Vec3(0, 0, -0.3) : m == ez1 ? new Vec3(0, 0, 0.3) : m == ex0 ? new Vec3(-0.3, 0, 0) : new Vec3(0.3, 0, 0);
                        p.move(MoverType.SHULKER, slide);
                    }
                    continue;
                }
                double ox = Math.min(pb.maxX, box.maxX) - Math.max(pb.minX, box.minX);
                double oz = Math.min(pb.maxZ, box.maxZ) - Math.max(pb.minZ, box.minZ);
                Vec3 m = part.motion();
                Vec3 d = pb.getCenter().subtract(box.getCenter());
                boolean alongX = Math.abs(m.x) >= Math.abs(m.z);
                Vec3 push;
                if ((Math.abs(m.x) > 0.02 || Math.abs(m.z) > 0.02) && (alongX ? d.x * m.x : d.z * m.z) > 0) {
                    // shoved by the sweep: along the part's own motion, just far enough to clear it
                    push = alongX ? new Vec3(Math.copySign(ox + 0.01, m.x), 0, 0) : new Vec3(0, 0, Math.copySign(oz + 0.01, m.z));
                } else {
                    // otherwise out the shortest way sideways - never down into the ground, never up
                    // onto a rising foot
                    push = ox <= oz ? new Vec3(Math.copySign(ox + 0.01, d.x), 0, 0) : new Vec3(0, 0, Math.copySign(oz + 0.01, d.z));
                }
                p.move(MoverType.SHULKER, push);
            }
        }
    }

    /**
     * Rubble raining back into the footprint, a hop of the ground it stands on, a run into a
     * hillside: whatever leaves the feet inside solid blocks, the giant climbs out on top of it
     * instead of standing buried. Vanilla never pushes a mob out of a block it is inside.
     */
    private void unbury() {
        AABB box = this.getBoundingBox().deflate(0.05);
        if (this.level().noCollision(this, box)) return;
        int limit = Math.max(6, bodyHeight() / 2);
        for (int dy = 1; dy <= limit; dy++) {
            if (this.level().noCollision(this, box.move(0, dy, 0))) {
                this.setPos(this.getX(), this.getY() + dy, this.getZ());
                Vec3 v = this.getDeltaMovement();
                this.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
                this.resetFallDistance();
                this.hasImpulse = true;
                return;
            }
        }
    }

    /**
     * The pose the body is in right now, from the same ingredients the renderer uses: the walk
     * cycle, where the head looks, the running attack (server: the real ticks; client: what it has
     * been told) and death. Both sides can compute it, so the hit boxes follow the animation on
     * both.
     */
    private ColossusPose currentPose(int height) {
        float headYaw = Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
        ColossusPose pose = ColossusPose.walking(this.stride, this.walkAnimation.speed(), headYaw, this.getXRot(), height);
        Attack a = this.level().isClientSide ? clientAttack : attack;
        if (a != Attack.NONE) {
            int t = this.level().isClientSide ? clientAttackTicks : attackTicks;
            pose.attack(a.id, Mth.clamp(t / (float) a.duration, 0f, 1f), a.impact / (float) a.duration, stompRightFoot());
        }
        if (this.deathTime > 0) pose.dying(this.deathTime, height);
        if (isWaking()) {
            float p = this.level().isClientSide ? wakeProgress(1f) : Mth.clamp(1f - this.entityData.get(DATA_WAKE) / (float) wakeTotal(), 0f, 1f);
            pose.rising(p);
        }
        return pose;
    }

    /**
     * Places the hit boxes where the posed body actually is: every slice box is carried through
     * the same transform chain the renderer applies to that part (limb swing about its pivot, the
     * torso's pitch and twist for everything riding on it, the vertical bob, the body yaw), and the
     * box around the eight moved corners becomes the part's world box. A swinging leg is hit - and
     * bumped into - where it is seen.
     */
    private void updateParts(boolean snap) {
        ColossusBody b = body();
        List<ColossusBody.HitBox> boxes = b.hitBoxes();
        ColossusPose pose = currentPose(b.height);
        PartDef torso = b.part(PartDef.Kind.TORSO);
        double theta = Math.toRadians(180.0 - this.yBodyRot);
        double cos = Math.cos(theta), sin = Math.sin(theta);
        double ex = this.getX(), ey = this.getY(), ez = this.getZ();
        int n = Math.min(boxes.size(), this.parts.length);
        double[] c = new double[3];
        for (int p = 0; p < n; p++) {
            ColossusBody.HitBox hb = boxes.get(p);
            PartDef def = b.part(hb.kind());
            AABB box = hb.box();
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                c[0] = (i & 1) == 0 ? box.minX : box.maxX;
                c[1] = (i & 2) == 0 ? box.minY : box.maxY;
                c[2] = (i & 4) == 0 ? box.minZ : box.maxZ;
                if (def != null) pose.transform(c, def, torso);
                double rx = c[0] * cos + c[2] * sin;
                double rz = -c[0] * sin + c[2] * cos;
                minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
                minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
                minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
            }
            AABB world = new AABB(ex + minX, ey + minY, ez + minZ, ex + maxX, ey + maxY, ez + maxZ);
            if (snap) this.parts[p].snapTo(world); else this.parts[p].place(world);
        }
    }


    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        super.moveTo(x, y, z, yRot, xRot);
        if (this.parts != null) updateParts(true);
    }

    /** NeoForge multipart hook - dispatched virtually at runtime (the method lives in the patched Entity). */
    public boolean isMultipartEntity() {
        return true;
    }

    /** NeoForge multipart hook. */
    public PartEntity<?>[] getParts() {
        return this.parts;
    }

    /** A point of the body (body space, blocks) in the world, rotated by the body yaw. */
    public Vec3 bodyPoint(double bx, double by, double bz) {
        double theta = Math.toRadians(180.0 - this.yBodyRot);
        double cos = Math.cos(theta), sin = Math.sin(theta);
        return new Vec3(this.getX() + bx * cos + bz * sin, this.getY() + by, this.getZ() - bx * sin + bz * cos);
    }

    /** Unit vector the body faces along the ground. */
    public Vec3 facing() {
        double yaw = Math.toRadians(this.yBodyRot);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    // ---- moving through the world ----------------------------------------------------------

    /**
     * A giant does not walk around a tree. Everything soft in the space its legs and belly sweep
     * through - trees, bushes, crops, snow - comes down; logs are flung aside, the rest breaks to
     * dust. Loose earth is only kicked up where it is a bump at the feet: the layer the soles are
     * in, and open to the sky. Anything higher is a hill, and hills are stepped over (step height =
     * a fifth of the body), never tunnelled into - that is how a charge used to end up buried.
     */
    private void trample(ServerLevel server, double ahead, int budget) {
        if (!WakingConfig.trample()) return;
        Vec3 v = this.getDeltaMovement();
        double speed = v.horizontalDistance();
        if (speed < 0.02 && attack != Attack.CHARGE) return;
        Vec3 dir = speed > 0.01 ? new Vec3(v.x, 0, v.z).normalize() : facing();
        double h = bodyHeight();
        int feet = Mth.floor(this.getY());
        AABB box = this.getBoundingBox().inflate(1.2, 0, 1.2).expandTowards(dir.x * ahead, 0, dir.z * ahead);
        box = new AABB(box.minX, this.getY(), box.minZ, box.maxX, this.getY() + h * 0.45, box.maxZ);
        int flung = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (budget <= 0) break;
            BlockState state = server.getBlockState(pos);
            if (state.isAir() || !Crater.trampleable(server, pos, state)) continue;
            if (!Crater.vegetation(state)) {
                if (pos.getY() > feet) continue;
                BlockState above = server.getBlockState(pos.above());
                if (!above.isAir() && !Crater.vegetation(above)) continue;
            }
            budget--;
            if (state.is(BlockTags.LOGS) && flung < 5) {
                Crater.fling(server, pos.immutable(), state, this.position(), 0.6, this.random);
                flung++;
            } else {
                server.removeBlock(pos, false);
                if (this.random.nextInt(3) == 0) server.levelEvent(2001, pos, Block.getId(state));
            }
        }
    }

    /** True when the giant has been pushing against something it cannot break for a while. */
    public boolean isStuck() {
        return stuckTicks > 20;
    }

    // ---- damage: cores are the way in ------------------------------------------------------

    public boolean hurtPart(ColossusPart part, DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (source.getEntity() instanceof ServerPlayer sp) attackers.add(sp.getUUID());
        // the Colossus Hammer is the one thing they really feel
        if (source.getEntity() instanceof LivingEntity striker && striker.getMainHandItem().is(me.lovkar.wakingworld.item.WakingItems.COLOSSUS_HAMMER.get())
                && source.is(DamageTypes.PLAYER_ATTACK)) {
            amount *= me.lovkar.wakingworld.item.ColossusHammerItem.VS_COLOSSUS;
        }
        int index = -1;
        for (int i = 0; i < parts.length; i++) if (parts[i] == part) { index = i; break; }
        List<ColossusBody.HitBox> boxes = body().hitBoxes();
        ColossusBody.HitBox hb = index >= 0 && index < boxes.size() ? boxes.get(index) : null;
        int core = hb == null ? -1 : hb.core();
        Vec3 at = part.getBoundingBox().getCenter();
        if (core >= 0 && !isCoreBroken(core)) {
            if (this.level() instanceof ServerLevel server) {
                coreDamage[core] += amount;
                float coreHp = this.getMaxHealth() * 0.08F;
                float progress = Mth.clamp(coreDamage[core] / coreHp, 0f, 1f);
                // unmistakable: a flash of light, sparks and embers, a crystal crack that rises in pitch as the core gives way
                server.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.5, 0.5, 0.5, 0.25);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 20, 0.8, 0.8, 0.8, 0.4);
                server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.6, 0.6, 0.6, 0.0);
                server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.9, 0.9, 0.9, 0.35);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 5.0F, 0.55F + progress * 0.5F);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 4.0F, 0.6F + progress * 0.8F);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.0F, 1.2F + progress * 0.6F);
                this.level().broadcastEntityEvent(this, (byte) (EV_CORE_HIT + core));
                if (coreDamage[core] >= coreHp) {
                    breakCore(server, core, at);
                    if (source.getEntity() instanceof ServerPlayer sp) me.lovkar.wakingworld.advancement.WakingTriggers.CORE_BROKEN.get().trigger(sp, palette().kind, bodyHeight());
                }
            }
            return this.hurt(source, amount * CORE_FACTOR);
        }
        if (this.level() instanceof ServerLevel server) {
            BlockState dust = palette().pick(this.random);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), at.x, at.y, at.z, 10, 0.7, 0.7, 0.7, 0.1);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        return this.hurt(source, amount * ARMOR_FACTOR);
    }

    private void breakCore(ServerLevel server, int core, Vec3 at) {
        this.entityData.set(DATA_CORES, (byte) (brokenCores() | (1 << core)));
        refreshBossBar();
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.8, 0.8, 0.8, 0.0);
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 30, 1.2, 1.2, 1.2, 0.0);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.9F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 5.0F, 0.4F);
        this.setHealth(Math.max(1.0F, this.getHealth() - this.getMaxHealth() * 0.10F));
        if (attack != Attack.NONE && attack != Attack.ROAR && attack != Attack.LEAP) endAttack();
        attackCooldown = Math.max(attackCooldown, 40);
        // rocks come off the body around the wound
        for (int i = 0; i < 6; i++) {
            BlockPos p = BlockPos.containing(at.x + (this.random.nextDouble() - 0.5) * 3, at.y + (this.random.nextDouble() - 0.5) * 3, at.z + (this.random.nextDouble() - 0.5) * 3);
            if (server.isEmptyBlock(p)) Crater.fling(server, p, palette().pick(this.random), this.position(), 0.9, this.random);
        }
        this.level().broadcastEntityEvent(this, EV_CORE);
    }

    // ---- combat ----------------------------------------------------------------------------

    public boolean isAttacking() {
        return this.level().isClientSide ? clientAttack != Attack.NONE : attack != Attack.NONE;
    }

    public Attack currentAttack() {
        return attack;
    }

    public boolean attackReady() {
        return attack == Attack.NONE && attackCooldown <= 0 && !isWaking();
    }

    public boolean canUse(Attack a) {
        return cooldowns.getOrDefault(a, 0) <= 0;
    }

    /** Is there a tree within reach to rip out? Cached briefly by the goal. */
    public boolean treeNearby() {
        return this.level() instanceof ServerLevel server && findTree(server) != null;
    }

    /**
     * Picks the next move for the situation. Every usable move gets a weight; moves used in the last
     * three turns are nearly excluded, so the fight does not settle into a pattern.
     */
    public Attack chooseAttack(double distance, boolean treeNear) {
        double h = bodyHeight();
        int phase = phase();
        Map<Attack, Integer> w = new EnumMap<>(Attack.class);
        if (distance <= h * 0.36) {
            w.put(Attack.SWIPE, 30);
            w.put(Attack.STOMP, 22);
            if (phase >= 2) w.put(Attack.SLAM, 22);
            if (phase >= 2) w.put(Attack.LEAP, 8);
            if (phase >= 2) w.put(Attack.RUBBLE, 10);
            w.put(Attack.GRAB, 6);
            if (treeNear) w.put(Attack.UPROOT, 10);
        } else if (distance <= h * 0.7) {
            w.put(Attack.STOMP, 28);
            w.put(Attack.GRAB, 16); // the hand comes down about this far out
            w.put(Attack.BOULDER, phase >= 2 ? 18 : 10);
            if (treeNear) w.put(Attack.UPROOT, 22);
            if (phase >= 2) w.put(Attack.LEAP, 16);
            if (phase >= 2) w.put(Attack.SLAM, 10);
            if (phase >= 2) w.put(Attack.CHARGE, 6);
        } else if (distance <= 80) {
            // the run is the rare one: a long way off, and not again for a good while (see endAttack)
            w.put(Attack.BOULDER, 30);
            w.put(Attack.CHARGE, 14);
            w.put(Attack.STOMP, 10); // the wave carries this far
            if (treeNear) w.put(Attack.UPROOT, 28);
            if (phase >= 2) w.put(Attack.LEAP, 16);
        }
        // the kind's own moves
        Signature.weights(palette().kind, w, distance <= h * 0.36 ? 0 : distance <= h * 0.7 ? 1 : 2, phase);
        // up on a hill, higher than a stride can take it? no running into the hillside - it jumps up there
        LivingEntity target = this.getTarget();
        if (target != null) {
            double rise = target.getY() - this.getY();
            if (rise > this.maxUpStep() && rise > distance * 0.4) {
                w.remove(Attack.CHARGE);
                if (distance <= 48) w.merge(Attack.LEAP, 30, Integer::sum);
            }
            // nothing under the target? no run and no jump that way - the void gets nothing. Nothing halfway
            // (the target on the next island): no run and no jump either - the Titan's hop ({@link #hop}) is what crosses the void
            if (!safeToGo(target.position()) || gapBetween(this.position(), target.position())) {
                w.remove(Attack.CHARGE);
                w.remove(Attack.LEAP);
            }
        }
        int total = 0;
        for (Map.Entry<Attack, Integer> e : w.entrySet()) {
            if (!canUse(e.getKey())) { e.setValue(0); continue; }
            if (recent.contains(e.getKey())) e.setValue(Math.max(1, e.getValue() / 6));
            total += e.getValue();
        }
        if (total <= 0) return Attack.NONE;
        int roll = this.random.nextInt(total);
        for (Map.Entry<Attack, Integer> e : w.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return Attack.NONE;
    }

    public void startAttack(Attack a) {
        if (this.level().isClientSide || a == Attack.NONE || climbTicks > 0) return;
        this.attack = a;
        this.attackTicks = 0;
        this.leftGround = false;
        if (a != Attack.LEAP) this.leapAim = null;
        else this.setYRot(this.yBodyRot); // the crouch turns from where the body faces, not from where the feet last pointed
        this.leapHold = 0;
        this.hopTurn = null;
        this.entityData.set(DATA_ATTACK, (byte) a.id);
        this.entityData.set(DATA_SKIP, (byte) 0);
        if (a == Attack.STOMP) this.entityData.set(DATA_FOOT, (byte) (stompRightFoot() ? 0 : 1));
        if (a != Attack.CHARGE) this.getNavigation().stop();
        recent.addLast(a);
        while (recent.size() > 3) recent.removeFirst();
        switch (a) {
            case STOMP -> playSound(SoundEvents.RAVAGER_STEP, 3.0F, 0.3F);
            case SWIPE, BOULDER, UPROOT -> playSound(SoundEvents.RAVAGER_ATTACK, 4.0F, 0.4F);
            case SLAM, LEAP -> playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.5F);
            case ROAR -> playSound(SoundEvents.WARDEN_ROAR, 5.0F, 0.6F);
            case CHARGE -> playSound(SoundEvents.RAVAGER_ROAR, 5.0F, 0.35F);
            case RUBBLE -> playSound(SoundEvents.DEEPSLATE_BREAK, 4.0F, 0.4F);
            case GRAB -> playSound(SoundEvents.RAVAGER_ATTACK, 4.0F, 0.3F);
            default -> { }
        }
    }

    private void endAttack() {
        Attack done = this.attack;
        boolean hopped = done == Attack.LEAP && this.leapAim != null;
        this.leapAim = null;
        if (done == Attack.GRAB) release(false);
        this.attack = Attack.NONE;
        this.attackTicks = 0;
        this.entityData.set(DATA_ATTACK, (byte) 0);
        dropHeld();
        int phase = phase();
        this.attackCooldown = isTitan() && phase >= 3 ? 7 + this.random.nextInt(8) // the Titan at bay gives no rest
                : (phase >= 3 ? 12 : phase == 2 ? 22 : 34) + this.random.nextInt(14);
        switch (done) {
            case STOMP -> cooldowns.put(Attack.STOMP, 40);
            case SWIPE -> cooldowns.put(Attack.SWIPE, 24);
            case SLAM -> cooldowns.put(Attack.SLAM, 120);
            case BOULDER -> cooldowns.put(Attack.BOULDER, phase >= 3 ? 50 : 80);
            case UPROOT -> cooldowns.put(Attack.UPROOT, 120);
            case CHARGE -> cooldowns.put(Attack.CHARGE, phase >= 3 ? 220 : 300);
            case LEAP -> cooldowns.put(Attack.LEAP, hopped ? 20 : 160); // island to island it goes on at once
            case RUBBLE -> cooldowns.put(Attack.RUBBLE, 100);
            case GRAB -> cooldowns.put(Attack.GRAB, 320);
            case FROST_BREATH, ICE_SPIKES, SANDSTORM, SAND_GEYSER, TIDAL_WAVE, WATER_JET, GRASPING_ROOTS, SPORE_CLOUD, ROCKFALL, QUAKE ->
                    cooldowns.put(done, Signature.cooldown(done));
            default -> { }
        }
        // phase III: stomps come in threes
        if (done == Attack.STOMP && phase >= 3 && stompChain < 2 && this.random.nextInt(100) < 65) {
            stompChain++;
            cooldowns.put(Attack.STOMP, 0);
            this.attackCooldown = 2;
        } else if (done == Attack.STOMP) {
            stompChain = 0;
        }
    }

    /** Jumps the running attack ahead to a tick (never back) - and tells the clients so the animation jumps with it. */
    void skipTo(int tick) {
        if (tick <= attackTicks) return;
        attackTicks = tick;
        this.entityData.set(DATA_SKIP, (byte) Math.min(127, tick));
    }

    private void dropHeld() {
        if (held != null && held.isAlive() && held.isNoGravity()) {
            held.setNoGravity(false);
            held.setDeltaMovement(0, -0.1, 0);
        }
        held = null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        updatePhase();
        if (climbTicks > 0) return; // hanging off the edge of the world: nothing else happens
        if (attackCooldown > 0) attackCooldown--;
        for (Map.Entry<Attack, Integer> e : cooldowns.entrySet()) if (e.getValue() > 0) e.setValue(e.getValue() - 1);
        ServerLevel server = (ServerLevel) this.level();

        // stuck against rock? the combat goal switches to pathfinding for a while
        if (this.horizontalCollision && this.getDeltaMovement().horizontalDistance() < 0.04 && attack != Attack.LEAP) stuckTicks++;
        else stuckTicks = Math.max(0, stuckTicks - 2);
        // a wall it has been pushing at, with somewhere to go beyond it: it climbs. Pushing shows as a
        // collision, or simply as no headway for a while with the target well out of reach.
        LivingEntity want = this.getTarget();
        boolean wantsToMove = want != null && attack == Attack.NONE && this.distanceTo(want) > bodyHeight() * 0.36;
        double headway = Math.sqrt((this.getX() - this.xo) * (this.getX() - this.xo) + (this.getZ() - this.zo) * (this.getZ() - this.zo));
        if (wantsToMove && headway < 0.03 && this.onGround()) noHeadway++;
        else noHeadway = 0;
        if (wantsToMove && this.onGround() && ((stuckTicks >= 10 && stuckTicks % 10 == 0) || (noHeadway >= 15 && noHeadway % 15 == 0)) && clamber(server)) return;

        trample(server, attack == Attack.CHARGE ? 4.0 : 2.0, attack == Attack.CHARGE ? 80 : 40);

        if (attack != Attack.NONE) {
            attackTicks++;
            tickAttack(server);
            if (attackTicks == attack.impact) impact(server, attack);
            if (attackTicks >= attack.duration) endAttack();
        }
        Iterator<Shockwave> it = waves.iterator();
        while (it.hasNext()) if (!it.next().tick(server)) it.remove();
    }

    /** Per-tick behaviour of the running attack (holding a rock, charging, leaping, shaking). */
    private void tickAttack(ServerLevel server) {
        double h = bodyHeight();
        LivingEntity target = this.getTarget();
        if (attack.id >= 11) {
            Signature.tick(this, server, attack, attackTicks);
            return;
        }
        switch (attack) {
            case BOULDER -> {
                if (attackTicks == 8) {
                    // tear it off the shoulder
                    Vec3 shoulder = bodyPoint(0.30 * h, 0.66 * h, 0.0);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, palette().pick(this.random)), shoulder.x, shoulder.y, shoulder.z, 40, 1.5, 1.5, 1.5, 0.15);
                    server.playSound(null, shoulder.x, shoulder.y, shoulder.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F);
                    int size = phase() >= 2 ? 2 : 1;
                    holdMass(server, ThrownMassEntity.boulder(palette().pick(this.random), size), false, 14.0F + phase() * 3, 3.5 + phase(), 2.6 + 0.7 * phase());
                }
                carryHeld(h, Math.min(1.0, attackTicks / 22.0));
            }
            case UPROOT -> {
                if (attackTicks == 12) ripTree(server);
                carryHeld(h, Math.min(1.0, Math.max(0.0, (attackTicks - 12) / 14.0)));
            }
            case CHARGE -> {
                if (attackTicks == 16) {
                    chargeBlocked = 0;
                    chargeLast = this.position();
                    chargeHit.clear();
                }
                if (attackTicks >= 16 && attackTicks < 60) {
                    // did the last stride get anywhere? three strides into rock and the run ends in a crash
                    if (attackTicks > 17) {
                        if (this.position().subtract(chargeLast).horizontalDistance() < 0.2) chargeBlocked++;
                        else chargeBlocked = 0;
                    }
                    chargeLast = this.position();
                    boolean arrived = false;
                    if (target != null) {
                        Vec3 to = target.position().subtract(this.position());
                        double flat = to.horizontalDistance();
                        if (flat > 3.0) {
                            // it runs where it faces and can only bend the run so much - step aside and it thunders past
                            float wantedYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                            float turn = turnRate() * 2.2F;
                            this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(wantedYaw - this.getYRot()), -turn, turn));
                            this.yBodyRot = this.getYRot();
                            this.yHeadRot = this.getYRot();
                            Vec3 dir = facing();
                            double speed = 0.7 + 0.1 * phase();
                            // the run goes over the ground, not through it: gravity keeps the feet down,
                            // the step height (a fifth of the body) takes it up slopes
                            this.setDeltaMovement(dir.x * speed, this.getDeltaMovement().y, dir.z * speed);
                            this.hasImpulse = true;
                        } else if (attackTicks > 24) {
                            arrived = true;
                        }
                    }
                    // the legs bowl over whatever they run into - each of them once per run
                    float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    Vec3 fwd = facing();
                    for (LivingEntity t : server.getEntitiesOfClass(LivingEntity.class, chargeFront(h),
                            e -> e != this && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity) && !chargeHit.contains(e.getId()))) {
                        chargeHit.add(t.getId());
                        t.hurt(this.damageSources().mobAttack(this), dmg * 0.8F);
                        Vec3 push = t.position().subtract(this.position());
                        double f = Math.max(0.01, push.horizontalDistance());
                        t.setDeltaMovement(t.getDeltaMovement().add(push.x / f * 1.2 + fwd.x, 0.9, push.z / f * 1.2 + fwd.z));
                        t.hurtMarked = true;
                    }
                    if (attackTicks % 6 == 0) groundBurst(server, this.position(), 16, 2.0);
                    // into a cliff, on top of the target, or at the edge of the world: plant the feet next tick
                    if (chargeBlocked >= 3 || arrived || !safeToGo(this.position().add(facing().scale(6)))) skipTo(59);
                }
                if (attackTicks == 60) chargeCrash(server, h);
            }
            case LEAP -> {
                Vec3 aimAt = leapAim != null ? leapAim : target != null ? target.position() : null;
                if (attackTicks <= 14 && aimAt != null) {
                    // the crouch: it turns towards where it will jump, as far as a giant can in that time
                    Vec3 to = aimAt.subtract(this.position());
                    if (to.horizontalDistance() > 1.0) {
                        float wantedYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                        float turn = turnRate() * 3F;
                        this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(wantedYaw - this.getYRot()), -turn, turn));
                        this.yBodyRot = this.getYRot();
                        this.yHeadRot = this.getYRot();
                    }
                }
                if (attackTicks > 14 && !this.onGround()) leftGround = true;
                if (leftGround && !this.onGround()) {
                    // in the air the run of the jump is kept: the drag on a living body would stop it in three blocks
                    Vec3 d = this.getDeltaMovement();
                    this.setDeltaMovement(leapVx, d.y, leapVz);
                    this.hasImpulse = true;
                    // a hop's long flight (an island far off, or far below) does not end in mid-air: the landing
                    // waits for the ground - for six seconds at the most, in case there is none
                    if (leapAim != null && attackTicks >= attack.duration - 16 && leapHold++ < 120) attackTicks = attack.duration - 16;
                }
                if (attackTicks > 18 && leftGround && this.onGround()) {
                    land(server, h);
                    skipTo(attack.duration - 14);
                    leftGround = false;
                }
            }
            case GRAB -> {
                if (attackTicks < Attack.impactOf(Attack.GRAB) && target != null) {
                    // the right hand comes down 37 degrees to the right of where it faces, some 24 blocks
                    // out (a 40-block body): it turns so that spot is the target, as fast as a giant can
                    Vec3 to = target.position().subtract(this.position());
                    if (to.horizontalDistance() > 1.0) {
                        float wantedYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)) - 37F;
                        float turn = turnRate() * 3F;
                        this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(wantedYaw - this.getYRot()), -turn, turn));
                        this.yBodyRot = this.getYRot();
                    }
                }
                Entity held = grabbed();
                if (held != null) {
                    Vec3 hand = handPoint();
                    if (attackTicks < Attack.GRAB_THROW) {
                        // the squeeze
                        if (attackTicks == 36 || attackTicks == 52) {
                            held.hurt(this.damageSources().mobAttack(this), 3.0F);
                            server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.DEEPSLATE_STEP, SoundSource.HOSTILE, 4.0F, 0.3F);
                        }
                        if (attackTicks % 4 == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, palette().pick(this.random)), hand.x, hand.y, hand.z, 3, 1.0, 1.0, 1.0, 0.05);
                    } else if (attackTicks == Attack.GRAB_THROW) {
                        release(true);
                    }
                } else if (attackTicks > Attack.impactOf(Attack.GRAB) && attackTicks < Attack.GRAB_THROW) {
                    skipTo(Attack.GRAB_THROW + 4); // caught nothing: no point holding an empty fist up
                }
            }
            case RUBBLE -> {
                if (attackTicks >= 8 && attackTicks <= 30 && attackTicks % 3 == 0) {
                    Vec3 at = bodyPoint(0, 0.5 * h, 0);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, palette().pick(this.random)), at.x, at.y, at.z, 12, 0.3 * h, 0.35 * h, 0.3 * h, 0.1);
                }
            }
            default -> { }
        }
    }

    /** The space the legs and belly sweep through during a run - a little ahead of the feet, up to the waist. */
    private AABB chargeFront(double h) {
        Vec3 c = this.position().add(facing().scale(0.05 * h));
        double r = 0.13 * h + 1.5;
        return new AABB(c.x - r, this.getY() - 1.0, c.z - r, c.x + r, this.getY() + 0.35 * h, c.z + r);
    }

    /**
     * The end of a run: the feet dig in and the ground in front of them bursts. In front - never
     * under the feet themselves, so the giant does not drop into its own crater.
     */
    private void chargeCrash(ServerLevel server, double h) {
        double radius = 0.06 * h;
        double ahead = Math.max(0.14 * h, 2.2 + radius * 1.1);
        Vec3 at = this.position().add(facing().scale(ahead));
        // on the ground in front, whether that is up or down the slope
        int ground = server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        if (Math.abs(ground - at.y) <= 6) at = new Vec3(at.x, ground, at.z);
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Crater.blast(server, at, radius, 14, 0.6, 2, this.random);
        waves.add(new Shockwave(this, at, 0.45 * h, 1.4, dmg * 0.45F, 0.75));
        groundBurst(server, at, 60, 2.5);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 5.0F, 0.3F);
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
        this.level().broadcastEntityEvent(this, EV_CRASH);
    }

    // ---- the grab: a player in the fist ------------------------------------------------------

    private int grabbedId = -1;

    /** The right hand, as posed right now. */
    Vec3 handPoint() {
        return partPoint(PartDef.Kind.RIGHT_ARM, 0.06);
    }

    /** Whoever is in the fist right now, or null. */
    private Entity grabbed() {
        if (grabbedId < 0) return null;
        for (Entity e : this.getPassengers()) if (e.getId() == grabbedId) return e;
        return null;
    }

    /** True while {@code entity} is held: the NeoForge mount event uses this to refuse a sneak-dismount. */
    public boolean isHolding(Entity entity) {
        return attack == Attack.GRAB && attackTicks < Attack.GRAB_THROW && entity.getId() == grabbedId;
    }

    /** Lets go - hurled a long way if {@code throwIt}, otherwise just dropped. */
    private void release(boolean throwIt) {
        Entity held = grabbed();
        grabbedId = -1;
        if (held == null) return;
        Vec3 hand = handPoint();
        held.stopRiding();
        held.setPos(hand.x, hand.y, hand.z);
        if (throwIt) {
            Vec3 dir = facing();
            // in the End the throw goes up more than out: you come down inside the arena, not in the void
            boolean end = this.level().dimension() == Level.END;
            held.setDeltaMovement(dir.x * (end ? 2.6 : 4.5), end ? 2.8 : 1.8, dir.z * (end ? 2.6 : 4.5));
            held.hurtMarked = true;
            held.hurt(this.damageSources().mobAttack(this), 4.0F);
            if (held instanceof ServerPlayer sp) me.lovkar.wakingworld.advancement.WakingTriggers.THROWN.get().trigger(sp, 1);
            if (this.level() instanceof ServerLevel server) {
                server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 5.0F, 0.35F);
                server.sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, 20, 1.0, 1.0, 1.0, 0.15);
            }
        } else {
            held.setDeltaMovement(0, 0.2, 0);
            held.hurtMarked = true;
        }
    }

    /** The one in the fist rides the hand. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        Vec3 hand = handPoint();
        callback.accept(passenger, hand.x, hand.y - 0.4, hand.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** NeoForge hook (the patched Entity implements it; dispatched virtually at runtime): the one in the fist stands, not sits. */
    public boolean shouldRiderSit() {
        return false;
    }

    /** No steering from the fist. */
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    private void holdMass(ServerLevel server, List<ThrownMassEntity.Piece> pieces, boolean scatter, float damage, double radius, double crater) {
        ThrownMassEntity m = WakingWorld.THROWN_MASS.get().create(server);
        if (m == null) return;
        m.setOwner(this);
        m.setPieces(pieces, scatter);
        m.setPower(damage, radius, crater);
        m.setNoGravity(true);
        Vec3 hand = bodyPoint(0.30 * bodyHeight(), 0.30 * bodyHeight(), -0.25 * bodyHeight());
        m.moveTo(hand.x, hand.y, hand.z, 0, 0);
        m.setDeltaMovement(Vec3.ZERO);
        server.addFreshEntity(m);
        held = m;
    }

    /** The held mass rides the right hand as the arm winds up over the shoulder. */
    private void carryHeld(double h, double lift) {
        if (held == null || !held.isAlive()) return;
        // hand path: from the hip-height grab position up and back over the shoulder
        double bx = 0.30 * h, by = 0.30 * h + 0.45 * h * lift, bz = -0.25 * h + 0.35 * h * lift;
        Vec3 hand = bodyPoint(bx, by, bz);
        held.setPos(hand.x, hand.y - 1.0, hand.z);
        held.setDeltaMovement(Vec3.ZERO);
        held.hasImpulse = true;
    }

    private void throwHeld(ServerLevel server, double h) {
        LivingEntity target = this.getTarget();
        if (held == null || !held.isAlive()) return;
        ThrownMassEntity m = held;
        held = null;
        m.setNoGravity(false);
        Vec3 from = m.position();
        Vec3 aim = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : from.add(facing().scale(30)).add(0, -5, 0);
        double speed = 1.45;
        double dist = aim.subtract(from).horizontalDistance();
        double flight = dist / speed;
        if (target != null) aim = aim.add(target.getDeltaMovement().scale(flight * 0.8));
        Vec3 delta = aim.subtract(from);
        double flat = Math.max(0.01, delta.horizontalDistance());
        double t = flat / speed;
        double g = 0.035;
        double vy = delta.y / t + 0.5 * g * t;
        m.setDeltaMovement(delta.x / flat * speed, vy, delta.z / flat * speed);
        m.hasImpulse = true;
        server.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 4.0F, 0.4F);
    }

    /** Finds a tree (a log with leaves somewhere above it) within reach, or null. */
    private BlockPos findTree(ServerLevel server) {
        double h = bodyHeight();
        int reach = (int) Math.ceil(h * 0.55);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx += 2) {
            for (int dz = -reach; dz <= reach; dz += 2) {
                if (dx * dx + dz * dz > reach * reach || dx * dx + dz * dz < 9) continue;
                int x = (int) Math.floor(this.getX()) + dx, z = (int) Math.floor(this.getZ()) + dz;
                int ground = server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = ground - 3; y <= ground + 2; y++) {
                    pos.set(x, y, z);
                    BlockState s = server.getBlockState(pos);
                    if (!s.is(BlockTags.LOGS)) continue;
                    // a log with leaves within a few blocks above = a tree, not a house
                    boolean leaves = false;
                    for (int k = 2; k <= 12 && !leaves; k++) {
                        BlockState above = server.getBlockState(pos.above(k));
                        if (above.is(BlockTags.LEAVES)) leaves = true;
                        else if (!above.is(BlockTags.LOGS) && !above.isAir()) break;
                    }
                    if (!leaves) continue;
                    double d = dx * dx + dz * dz;
                    if (d < bestD) { bestD = d; best = pos.immutable(); }
                    break;
                }
            }
        }
        return best;
    }

    /** Rips a tree out of the world (logs and leaves, up to ~90 blocks) into the hand. */
    private void ripTree(ServerLevel server) {
        BlockPos base = findTree(server);
        if (base == null) return;
        // flood-fill the trunk and the leaves around it
        Set<BlockPos> tree = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(base);
        int logs = 0;
        while (!open.isEmpty() && tree.size() < 90) {
            BlockPos p = open.poll();
            if (tree.contains(p)) continue;
            BlockState s = server.getBlockState(p);
            boolean log = s.is(BlockTags.LOGS), leaf = s.is(BlockTags.LEAVES);
            if (!log && !leaf) continue;
            if (Math.abs(p.getX() - base.getX()) > 7 || Math.abs(p.getZ() - base.getZ()) > 7 || p.getY() - base.getY() > 24 || p.getY() < base.getY() - 1) continue;
            tree.add(p);
            if (log) logs++;
            if (log || tree.size() < 60) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos n = p.offset(dx, dy, dz);
                            if (!tree.contains(n)) open.add(n);
                        }
            }
        }
        if (logs == 0) return;
        // centre of mass, pieces relative to it, then take the blocks out of the world
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : tree) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= tree.size(); cy /= tree.size(); cz /= tree.size();
        List<ThrownMassEntity.Piece> pieces = new ArrayList<>();
        for (BlockPos p : tree) {
            BlockState s = server.getBlockState(p);
            pieces.add(new ThrownMassEntity.Piece((int) Math.round(p.getX() - cx), (int) Math.round(p.getY() - cy), (int) Math.round(p.getZ() - cz), s));
            if (s.is(BlockTags.LEAVES) && this.random.nextInt(3) == 0) server.levelEvent(2001, p, Block.getId(s));
            me.lovkar.wakingworld.ruin.Ruin.mark(server, p);
            server.removeBlock(p, false);
        }
        Vec3 at = new Vec3(cx, cy, cz);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, server.getBlockState(base.below())), base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 60, 2.5, 1.0, 2.5, 0.2);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 6.0F, 0.4F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 6.0F, 0.5F);
        holdMass(server, pieces, true, 12.0F + phase() * 2, 4.0 + phase() * 0.5, 3.0);
        if (held != null) held.setPos(at.x, at.y, at.z);
    }

    private void updatePhase() {
        float f = this.getHealth() / this.getMaxHealth();
        int wanted = f > 0.6F ? 1 : f > 0.25F ? 2 : 3;
        int current = phase();
        if (wanted > current) {
            this.entityData.set(DATA_PHASE, (byte) wanted);
            refreshBossBar();
            AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(PHASE_SPEED);
                speed.addTransientModifier(new AttributeModifier(PHASE_SPEED, wanted == 3 ? 0.35 : 0.15,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            // the Titan at bay hits harder: a fifth more in the last phase
            AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null && isTitan()) {
                dmg.removeModifier(PHASE_DAMAGE);
                dmg.addTransientModifier(new AttributeModifier(PHASE_DAMAGE, wanted == 3 ? 0.2 : wanted == 2 ? 0.1 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (attack != Attack.ROAR && attack != Attack.LEAP) {
                dropHeld();
                this.attack = Attack.NONE;
                startAttack(Attack.ROAR);
            }
            // the cracks open: fire along the body, rocks shaken loose, a dust ring at the feet
            if (this.level() instanceof ServerLevel server) {
                double h = bodyHeight();
                Vec3 mid = bodyPoint(0, 0.5 * h, 0);
                server.sendParticles(ParticleTypes.LAVA, mid.x, mid.y, mid.z, 40 + wanted * 20, 0.25 * h, 0.4 * h, 0.25 * h, 0.0);
                server.sendParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, 60, 0.25 * h, 0.4 * h, 0.25 * h, 0.05);
                server.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 4 + wanted * 2, 0.2 * h, 0.3 * h, 0.2 * h, 0.0);
                rubble(server, h, 6 + wanted * 4);
                server.sendParticles(themeParticle(), mid.x, mid.y, mid.z, 120, 0.3 * h, 0.4 * h, 0.3 * h, 0.15);
                waves.add(new Shockwave(this, this.position(), 0.6 * h, 1.4, 0.0F, 0.25));
                server.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6.0F, 0.6F);
                server.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.4F);
                this.level().broadcastEntityEvent(this, EV_PHASE);
            }
        }
    }

    private void impact(ServerLevel server, Attack a) {
        double h = bodyHeight();
        int phase = phase();
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (a.id >= 11) {
            Signature.impact(this, server, a);
            this.level().broadcastEntityEvent(this, EV_STOMP);
            return;
        }
        switch (a) {
            case STOMP -> {
                double side = stompRightFoot() ? 0.13 : -0.13;
                Vec3 foot = bodyPoint(side * h, 0, -0.06 * h);
                Crater.blast(server, foot, 0.075 * h, 20, 0.7, 2, this.random);
                waves.add(new Shockwave(this, foot, 0.9 * h + phase * 6, 1.3, dmg * 0.7F, 0.9));
                groundBurst(server, foot, 40, 1.5);
                ring(server, foot, 0.9 * h + phase * 6, 1.3);
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, foot.x, foot.y + 0.5, foot.z, 16, 2.0, 0.6, 2.0, 0.03);
                server.playSound(null, foot.x, foot.y, foot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.45F);
                server.playSound(null, foot.x, foot.y, foot.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 7.0F, 0.4F);
                this.level().broadcastEntityEvent(this, EV_STOMP);
            }
            case SWIPE -> {
                Vec3 f = facing();
                double reach = 0.42 * h;
                AABB area = this.getBoundingBox().inflate(reach, 0.5 * h, reach);
                boolean any = false;
                for (LivingEntity t : server.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !(e instanceof ColossusEntity))) {
                    Vec3 to = t.position().subtract(this.position());
                    double flat = Math.sqrt(to.x * to.x + to.z * to.z);
                    if (flat > reach || flat < 0.01) continue;
                    double dot = (to.x * f.x + to.z * f.z) / flat;
                    if (dot < 0.42) continue;
                    t.hurt(this.damageSources().mobAttack(this), dmg);
                    Vec3 push = new Vec3(to.x / flat, 0, to.z / flat).scale(2.4).add(0, 0.8, 0);
                    t.setDeltaMovement(t.getDeltaMovement().add(push));
                    t.hurtMarked = true;
                    any = true;
                }
                Vec3 hand = bodyPoint(0.30 * h, 0.12 * h, -0.30 * h);
                server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 4.0F, 0.35F);
                if (any) server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.5F);
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, hand.x, hand.y, hand.z, 6, 2.5, 1.0, 2.5, 0.0);
                // the hand rakes through anything soft in its arc
                trampleArc(server, hand, 4.0);
            }
            case SLAM -> {
                Vec3 at = bodyPoint(0, 0, -0.30 * h);
                double r = 0.18 * h;
                for (LivingEntity t : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 4, r),
                        e -> e != this && e.isAlive() && !(e instanceof ColossusEntity))) {
                    t.hurt(this.damageSources().mobAttack(this), dmg * 1.3F);
                    Vec3 push = t.position().subtract(at);
                    double flat = Math.max(0.01, Math.sqrt(push.x * push.x + push.z * push.z));
                    t.setDeltaMovement(t.getDeltaMovement().add(push.x / flat * 1.4, 1.1, push.z / flat * 1.4));
                    t.hurtMarked = true;
                }
                Crater.blast(server, at, 0.14 * h, 45, 0.9, 3, this.random);
                waves.add(new Shockwave(this, at, 1.2 * h + phase * 6, 1.5, dmg * 0.6F, 0.75));
                groundBurst(server, at, 120, 3.0);
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                server.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, 30, 3.0, 1.0, 3.0, 0.04);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 7.0F, 0.4F);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 6.0F, 0.4F);
                this.level().broadcastEntityEvent(this, EV_SLAM);
            }
            case BOULDER, UPROOT -> throwHeld(server, h);
            case ROAR -> {
                Vec3 mouth = bodyPoint(0, 0.70 * h, -0.28 * h);
                double r = 0.8 * h;
                for (LivingEntity t : server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(r, r, r),
                        e -> e != this && e.isAlive() && !(e instanceof ColossusEntity))) {
                    Vec3 push = t.position().subtract(this.position());
                    double flat = Math.max(0.01, Math.sqrt(push.x * push.x + push.z * push.z));
                    t.setDeltaMovement(t.getDeltaMovement().add(push.x / flat * 1.4, 0.4, push.z / flat * 1.4));
                    t.hurtMarked = true;
                    t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
                server.sendParticles(ParticleTypes.SONIC_BOOM, mouth.x, mouth.y, mouth.z, 3, 1.5, 0.5, 1.5, 0.0);
                server.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y - 1, mouth.z, 40, 2.0, 1.5, 2.0, 0.25);
                server.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8.0F, 0.45F);
                server.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0F, 0.7F);
                server.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.5F);
                this.level().broadcastEntityEvent(this, EV_ROAR);
            }
            case LEAP -> {
                LivingEntity target = this.getTarget();
                Vec3 aim = leapAim != null ? leapAim : target != null ? target.position() : this.position().add(facing().scale(20));
                Vec3 to = aim.subtract(this.position());
                double flat = Math.min(leapAim != null ? leapReach() : 48.0, to.horizontalDistance());
                // it jumps the way it faces (the crouch turned it as far as it could) - no spin in the air; the
                // air's drag would eat the run of the jump within a few blocks, so the flight keeps its own speed
                Vec3 dir = facing();
                double rise = Math.max(0, to.y);
                double drop = 0; // a hop may come down below where it left
                if (leapAim != null) {
                    // the stone across the void: where it comes down, the way it faces, must be ground with a top the
                    // jump clears - the aim's distance first, then shorter, until something will do; nothing: a stamp
                    double landAt = -1;
                    for (double f = flat; f >= 12; f -= 3) {
                        Vec3 p = this.position().add(dir.scale(f));
                        if (!groundUnder(p)) continue;
                        int top = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p)).getY();
                        double r = top - this.getY();
                        if (r > 44 || r < -70) continue;
                        landAt = f;
                        rise = Math.max(0, r);
                        drop = Math.min(0, r);
                        break;
                    }
                    flat = Math.max(0, landAt);
                } else if (!safeToGo(this.position().add(dir.scale(flat)))) {
                    // it would come down in the void: the crouch becomes a stamp instead
                    flat = 0;
                }
                double vy = 1.7 + rise * 0.045; // enough to clear the hill or the ledge the target is standing on
                int t = leapTicks(vy, flat > 0 ? rise + drop : 0); // ticks in the air, as the game's gravity and drag will have it
                leapVx = dir.x * flat / t;
                leapVz = dir.z * flat / t;
                this.setDeltaMovement(leapVx, vy, leapVz);
                this.hasImpulse = true;
                Vec3 feet = this.position();
                groundBurst(server, feet, 60, 3.0);
                server.playSound(null, feet.x, feet.y, feet.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6.0F, 0.4F);
            }
            case RUBBLE -> rubble(server, h, 14 + phase * 4);
            case GRAB -> {
                Vec3 hand = handPoint();
                double reach = 0.08 * h + 2.5;
                LivingEntity catch_ = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity t : server.getEntitiesOfClass(LivingEntity.class, new AABB(hand, hand).inflate(reach, reach * 0.8, reach),
                        e -> e != this && e.isAlive() && !e.isSpectator() && !(e instanceof ColossusEntity) && e.getBbHeight() < 3.0F && !e.isPassenger())) {
                    double d = t.position().distanceTo(hand) - (t instanceof Player ? 2.0 : 0.0); // prefers the player
                    if (d < best) { best = d; catch_ = t; }
                }
                groundBurst(server, hand, 30, 2.0);
                if (catch_ != null && catch_.startRiding(this, true)) {
                    grabbedId = catch_.getId();
                    catch_.hurt(this.damageSources().mobAttack(this), 4.0F);
                    server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 4.0F, 0.35F);
                    server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.6F);
                } else {
                    // the fist hits the ground
                    Crater.blast(server, hand, 0.035 * h, 6, 0.4, 1, this.random);
                    server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
                }
            }
            default -> { }
        }
    }

    /** The landing after a leap: the biggest crater the giant makes short of dying. */
    private void land(ServerLevel server, double h) {
        Vec3 at = this.position();
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double r = 0.3 * h;
        for (LivingEntity t : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 6, r),
                e -> e != this && e.isAlive() && !(e instanceof ColossusEntity))) {
            t.hurt(this.damageSources().mobAttack(this), dmg * 1.6F);
            Vec3 push = t.position().subtract(at);
            double flat = Math.max(0.01, push.horizontalDistance());
            t.setDeltaMovement(t.getDeltaMovement().add(push.x / flat * 1.8, 1.3, push.z / flat * 1.8));
            t.hurtMarked = true;
        }
        Crater.blast(server, at, 0.22 * h, 45, 0.9, 3, this.random); // a wide dish, never a pit it cannot step out of
        waves.add(new Shockwave(this, at, 1.5 * h, 1.6, dmg * 0.8F, 1.0));
        groundBurst(server, at, 160, 4.0);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2, 2, 0.5, 2, 0);
        server.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, 50, 0.15 * h, 1.5, 0.15 * h, 0.05);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 9.0F, 0.35F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 8.0F, 0.3F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 8.0F, 0.3F);
        this.level().broadcastEntityEvent(this, EV_LAND);
    }

    /** Rocks shaken loose from the body rain down around the giant - off the body as it is posed right now. */
    void rubble(ServerLevel server, double h, int count) {
        List<Cell> surface = surfaceCells();
        if (surface.isEmpty()) return;
        ColossusBody b = body();
        ColossusPose pose = currentPose(b.height);
        PartDef torso = b.part(PartDef.Kind.TORSO);
        for (int i = 0; i < count; i++) {
            Cell c = surface.get(this.random.nextInt(surface.size()));
            if (this.deathTime == 0 && c.y() < h * 0.3) continue;
            Vec3 w = cellPoint(c, pose, torso);
            BlockPos pos = BlockPos.containing(w);
            if (!server.isEmptyBlock(pos)) continue;
            Crater.fling(server, pos, palette().pick(this.random), this.position(), 1.1, this.random);
        }
        Vec3 at = bodyPoint(0, 0.6 * h, 0);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 6.0F, 0.4F);
    }

    /** Soft blocks in a disc around a hand strike come apart. */
    private void trampleArc(ServerLevel server, Vec3 hand, double radius) {
        if (!WakingConfig.trample()) return;
        int budget = 60;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(hand.subtract(radius, 2, radius)), BlockPos.containing(hand.add(radius, 3, radius)))) {
            if (budget-- <= 0) break;
            BlockState s = server.getBlockState(pos);
            if (s.isAir() || !Crater.trampleable(server, pos, s)) continue;
            if (s.is(BlockTags.LOGS)) Crater.fling(server, pos.immutable(), s, this.position(), 0.8, this.random);
            else { me.lovkar.wakingworld.ruin.Ruin.mark(server, pos); server.removeBlock(pos, false); if (this.random.nextInt(3) == 0) server.levelEvent(2001, pos, Block.getId(s)); }
        }
    }

    /** A surface cell of the body: its part and its centre in body space. */
    private record Cell(PartDef def, double x, double y, double z) {
    }

    private List<Cell> surfaceCache;
    private ColossusBody surfaceCacheBody;

    /** Every surface cell of the body (cached per body). */
    private List<Cell> surfaceCells() {
        ColossusBody b = body();
        if (surfaceCache != null && surfaceCacheBody == b) return surfaceCache;
        List<Cell> out = new ArrayList<>();
        for (PartDef part : b.parts) out.addAll(surfaceCellsOf(part));
        surfaceCache = out;
        surfaceCacheBody = b;
        return out;
    }

    private static List<Cell> surfaceCellsOf(PartDef part) {
        List<Cell> out = new ArrayList<>();
        for (int x = 0; x < part.sx; x++)
            for (int y = 0; y < part.sy; y++)
                for (int z = 0; z < part.sz; z++) {
                    if (part.get(x, y, z) == null || !part.isSurface(x, y, z)) continue;
                    out.add(new Cell(part, part.ox + x + 0.5, part.oy + y + 0.5, part.oz + z + 0.5));
                }
        return out;
    }

    /** Where a surface cell is in the world right now: posed, and (when dying) dropped and fallen. */
    private Vec3 cellPoint(Cell c, ColossusPose pose, PartDef torso) {
        double[] v = {c.x(), c.y(), c.z()};
        pose.transform(v, c.def(), torso);
        return this.deathTime > 0 ? deathPoint(v, pose) : bodyPoint(v[0], v[1], v[2]);
    }

    /** Dust of the actual ground thrown up around a point. */
    /** The kind's colour for rings, runes and sparks (0xRRGGBB). */
    public int kindColor() {
        return switch (palette().kind) {
            case "earth" -> 0xB8843C;
            case "sandstone" -> 0xF2D27A;
            case "ice" -> 0xA6E6FF;
            case "prismarine" -> 0x62D8C8;
            case "moss" -> 0x8CE664;
            case "titan" -> 0xB266FF;
            default -> 0xD8CFC0;
        };
    }

    /** A flat ring of light racing out along the ground: a stomp, a landing, an emergence. radius in blocks. */
    void ring(ServerLevel server, Vec3 at, double radius, double speed) {
        BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
        double y = Math.abs(g.getY() - at.y) < 6 ? g.getY() : at.y;
        server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.ring(kindColor(), (float) (radius / 3.0)), at.x, y + 0.05, at.z, 0, 0, speed, 0, 1.0);
    }

    void groundBurst(ServerLevel server, Vec3 at, int count, double spread) {
        ring(server, at, Math.max(4.0, spread * 4.0), 0.6);
        BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
        BlockState ground = server.getBlockState(under);
        if (ground.isAir()) ground = server.getBlockState(under.below());
        if (!ground.getFluidState().isEmpty() || this.isInWater()) {
            // wading: spray instead of dust
            server.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, count * 2, spread, 0.6, spread, 0.4);
        } else if (!ground.isAir()) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.5, at.z, count, spread, 0.8, spread, 0.3);
        }
        server.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, Math.max(1, count / 4), spread, 0.5, spread, 0.05);
    }

    // ---- client animation state ------------------------------------------------------------

    public Attack clientAttack() {
        return clientAttack;
    }

    public float attackProgress(float partialTick) {
        if (clientAttack == Attack.NONE) return 0f;
        return Mth.clamp((clientAttackTicks + partialTick) / clientAttack.duration, 0f, 1f);
    }

    // ---- death: the mountain comes down -----------------------------------------------------

    /**
     * The death takes {@link ColossusPose#DEATH_TICKS} ticks - the length of the victory music -
     * and every stage of the pose has its consequences in the world: the cores go out one by one
     * while it staggers; the knee comes down and cracks the ground; it braces on a hand; it topples
     * face-down and the impact craters the earth under its chest and head; lying there it comes
     * apart limb by limb into real falling blocks exactly where each limb lies, so the rubble keeps
     * the outline of the body; the torso goes last, in a burst, and heaps into the mound.
     */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel server)) return;
        int t = this.deathTime;
        double h = bodyHeight();
        if (t == 1) {
            dropHeld();
            release(false);
            this.getNavigation().stop();
            playSound(SoundEvents.RAVAGER_DEATH, 6.0F, 0.35F);
            playSound(SoundEvents.DEEPSLATE_BREAK, 6.0F, 0.3F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.35F);
            if (isTitan()) {
                playSound(SoundEvents.ENDERMAN_SCREAM, 8.0F, 0.3F);
                playSound(SoundEvents.WITHER_DEATH, 6.0F, 0.4F);
            }
            String slayer = null;
            for (java.util.UUID id : attackers) {
                if (server.getEntity(id) instanceof ServerPlayer sp && sp.distanceToSqr(this) < 300 * 300) {
                    me.lovkar.wakingworld.advancement.WakingTriggers.COLOSSUS_SLAIN.get().trigger(sp, palette().kind, bodyHeight());
                    if (slayer == null) slayer = sp.getGameProfile().getName();
                }
            }
            me.lovkar.wakingworld.story.Chronicle.record(server, "slain", palette().kind, blockPosition(), slayer);
            this.level().broadcastEntityEvent(this, EV_DEATH_START);
        }
        // stagger: the cores go out, one every twelve ticks, each with a burst of its own fire
        if (t <= ColossusPose.DEATH_STAGGER_END && t % 12 == 10) {
            int i = (t - 10) / 12;
            List<double[]> cores = body().cores;
            if (i < cores.size() && !isCoreBroken(i)) {
                this.entityData.set(DATA_CORES, (byte) (brokenCores() | (1 << i)));
                double[] c = cores.get(i);
                Vec3 at = deathPoint(new double[]{c[0], c[1], c[2]}, null);
                server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.5, 0.5, 0.5, 0);
                server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 25, 1.0, 1.0, 1.0, 0);
                server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 1.2, 1.2, 1.2, 0.08);
                server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 30, 1.0, 1.0, 1.0, 0.05);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.8F + i * 0.05F);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 4.0F, 0.5F);
                if (isTitan()) {
                    // the Titan's cores go out into the void: a burst of it pours out of each
                    server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 80, 1.5, 1.5, 1.5, 0.3);
                    server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 1.0, 1.0, 1.0, 0.12);
                    server.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 5.0F, 0.5F);
                }
            }
        }
        // dust and sparks keep streaming off the body the whole way down
        if (t % 4 == 0 && t < ColossusPose.DEATH_FINAL) {
            List<Cell> cells = surfaceCells();
            if (!cells.isEmpty()) {
                ColossusBody b = body();
                ColossusPose pose = currentPose(b.height);
                PartDef torso = b.part(PartDef.Kind.TORSO);
                for (int i = 0; i < 3; i++) {
                    Cell c = cells.get(this.random.nextInt(cells.size()));
                    if (ColossusPose.crumbled(c.def().kind, t)) continue;
                    Vec3 at = cellPoint(c, pose, torso);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, palette().pick(this.random)), at.x, at.y, at.z, 4, 0.5, 0.5, 0.5, 0.12);
                    if (this.random.nextInt(3) == 0) server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0);
                }
            }
        }
        if (t % 40 == 20 && t < ColossusPose.DEATH_FINAL) {
            playSound(SoundEvents.DEEPSLATE_BREAK, 5.0F, 0.25F + this.random.nextFloat() * 0.1F); // the body creaks and cracks
        }
        // the knee comes down
        if (t == ColossusPose.DEATH_KNEE_HIT) {
            Vec3 knee = partPoint(PartDef.Kind.RIGHT_LEG, 0.5);
            Crater.blast(server, knee, 0.05 * h, 12, 0.5, 2, this.random);
            waves.add(new Shockwave(this, knee, 0.7 * h, 1.3, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3F, 0.7));
            groundBurst(server, knee, 60, 2.5);
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, knee.x, knee.y + 0.5, knee.z, 20, 2.5, 0.6, 2.5, 0.03);
            rubble(server, h, 8);
            server.playSound(null, knee.x, knee.y, knee.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6.0F, 0.4F);
            server.playSound(null, knee.x, knee.y, knee.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 7.0F, 0.35F);
            this.level().broadcastEntityEvent(this, EV_DEATH_KNEE);
        }
        // the hand catches it
        if (t == ColossusPose.DEATH_KNEE_END + 40) {
            Vec3 hand = partPoint(PartDef.Kind.RIGHT_ARM, 0.0);
            Crater.blast(server, hand, 0.035 * h, 6, 0.4, this.random);
            groundBurst(server, hand, 30, 1.8);
            server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 5.0F, 0.35F);
            server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 5.0F, 0.5F);
            this.level().broadcastEntityEvent(this, EV_HEAVE);
        }
        // the fall: face-down, the chest and the head crater the ground
        if (t == ColossusPose.DEATH_FALL_START) {
            playSound(SoundEvents.RAVAGER_DEATH, 6.0F, 0.3F);
        }
        if (t == ColossusPose.DEATH_FALL_HIT) {
            Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.55);
            Vec3 head = partPoint(PartDef.Kind.HEAD, 0.5);
            Crater.blast(server, chest, 0.10 * h, 30, 0.7, 3, this.random);
            Crater.blast(server, head, 0.07 * h, 20, 0.7, 2, this.random);
            waves.add(new Shockwave(this, chest, 1.3 * h, 1.5, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F, 0.9));
            for (double f = 0.0; f <= 1.0; f += 0.2) {
                Vec3 p = partPoint(PartDef.Kind.TORSO, f);
                groundBurst(server, p, 50, 3.0);
                server.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, p.x, p.y + 1, p.z, 12, 3, 1, 3, 0.04);
            }
            groundBurst(server, head, 60, 3.0);
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, chest.x, chest.y + 0.5, chest.z, 2, 3, 0.5, 3, 0);
            rubble(server, h, 24);
            server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 9.0F, 0.3F);
            server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 8.0F, 0.25F);
            server.playSound(null, head.x, head.y, head.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 8.0F, 0.3F);
            this.level().broadcastEntityEvent(this, EV_DEATH_FALL);
            if (isTitan()) {
                // the void cracks open under it: a spray of crying-obsidian fissures around where it lies, sealing again later
                for (int i = 0; i < 40; i++) {
                    Vec3 p = partPoint(PartDef.Kind.TORSO, this.random.nextDouble());
                    double a = this.random.nextDouble() * Math.PI * 2, r = h * (0.05 + 0.2 * this.random.nextDouble());
                    BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            BlockPos.containing(p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r)).below();
                    tempOverlay(server, g, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 400 + this.random.nextInt(400));
                }
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, chest.x, chest.y + 1, chest.z, 300, h * 0.25, 2, h * 0.25, 0.3);
                server.sendParticles(ParticleTypes.DRAGON_BREATH, chest.x, chest.y + 1, chest.z, 120, h * 0.3, 1, h * 0.3, 0.05);
                server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 8.0F, 0.5F);
            }
        }
        // lying there, it comes apart limb by limb where each limb lies
        int max = WakingConfig.collapseBlocks();
        for (PartDef.Kind kind : PartDef.Kind.values()) {
            if (kind == PartDef.Kind.TORSO || t != ColossusPose.crumbleTick(kind)) continue;
            double share = kind == PartDef.Kind.HEAD ? 0.14 : 0.12;
            crumble(server, kind, (int) (max * share), 0.25);
            this.level().broadcastEntityEvent(this, EV_DEATH_CRUMBLE);
        }
        if (t == ColossusPose.DEATH_FINAL && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, EV_COLLAPSE);
            collapse(server);
            if (isTitan()) {
                // the whole End hears the Titan go: the dragon's own death-cry, for everyone; and the first ring of lightning
                Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.5);
                server.globalLevelEvent(1028, BlockPos.containing(chest), 0);
                titanLightningRing(server, chest, h * 0.45, 8);
            }
        }
        // the Titan's ruin: a pillar of light climbs out of the heap for the rest of the song, the void
        // streams into it, and lightning walks the ring of the arena
        if (isTitan() && t > ColossusPose.DEATH_FINAL) {
            Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.5);
            int e = t - ColossusPose.DEATH_FINAL;
            server.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y + 2, chest.z, 14, 1.2, 0.5, 1.2, 0.0);
            server.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y + 3 + (e % 40) * 1.6, chest.z, 6, 0.8, 0.8, 0.8, 0.02);
            server.sendParticles(ParticleTypes.PORTAL, chest.x, chest.y + 4, chest.z, 40, h * 0.3, 6, h * 0.3, 0.5);
            if (e % 22 == 11 && e < 120) titanLightningRing(server, chest, h * (0.3 + 0.03 * e), 2 + e / 30);
            if (e == 60) server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 8.0F, 0.5F);
        }
        // afterwards: the boom rolls away into the distance - each echo quieter, deeper and further off
        if (t > ColossusPose.DEATH_FINAL) {
            int e = t - ColossusPose.DEATH_FINAL;
            int[] at = {12, 30, 52, 80, 112};
            for (int i = 0; i < at.length; i++) {
                if (e != at[i]) continue;
                Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.5);
                double a = this.random.nextDouble() * Math.PI * 2, far = 25 + i * 30;
                double x = chest.x + Math.cos(a) * far, z = chest.z + Math.sin(a) * far;
                float vol = 9.0F / (1 + i * 1.1F), pitch = 0.45F - i * 0.05F;
                server.playSound(null, x, chest.y + 10, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, vol, pitch);
                if (i < 3) server.playSound(null, x, chest.y + 5, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, vol * 0.5F, pitch - 0.1F);
                this.level().broadcastEntityEvent(this, EV_HEAVE);
            }
            if (e % 5 == 0 && e < 110) {
                Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.5);
                server.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y + 2, chest.z, 6, h * 0.15, 1.5, h * 0.15, 0.04);
                server.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, chest.x, chest.y + 2, chest.z, 4, h * 0.12, 1, h * 0.12, 0.02);
            }
        }
        if (t >= ColossusPose.DEATH_TICKS && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    /** A body-space point of the posed body carried through the death pose's drop and fall, into the world. */
    private Vec3 deathPoint(double[] v, ColossusPose pose) {
        if (pose == null) pose = currentPose(body().height);
        double y = v[1] + pose.drop, z = v[2];
        double c = Math.cos(pose.fall), s = Math.sin(pose.fall);
        double ry = y * c - z * s, rz = y * s + z * c;
        return bodyPoint(v[0], ry + pose.lift, rz);
    }

    /** A point along a part (0 = its bottom, 1 = its top, in body space), as posed and fallen right now. */
    Vec3 partPoint(PartDef.Kind kind, double along) {
        ColossusBody b = body();
        PartDef def = b.part(kind);
        if (def == null) return this.position();
        double[] v = {def.ox + def.sx * 0.5, def.oy + def.sy * along, def.oz + def.sz * 0.5};
        ColossusPose pose = currentPose(b.height);
        pose.transform(v, def, b.part(PartDef.Kind.TORSO));
        return deathPoint(v, pose);
    }

    /**
     * One part turns to rubble where it lies: its surface cells become falling blocks at their
     * posed, fallen positions (a cell inside the ground surfaces first), with just enough tumble
     * that the heap is ragged rather than a cast of the limb.
     */
    private void crumble(ServerLevel server, PartDef.Kind kind, int budget, double power) {
        ColossusBody b = body();
        PartDef def = b.part(kind);
        if (def == null) return;
        PartDef torso = b.part(PartDef.Kind.TORSO);
        ColossusPose pose = currentPose(b.height);
        List<Cell> cells = surfaceCellsOf(def);
        double keep = Math.min(1.0, (double) budget / Math.max(1, cells.size()));
        Vec3 center = partPoint(kind, 0.5);
        int spawned = 0;
        for (Cell c : cells) {
            if (spawned >= budget || this.random.nextDouble() > keep) continue;
            double[] v = {c.x(), c.y(), c.z()};
            pose.transform(v, def, torso);
            Vec3 w = deathPoint(v, pose);
            BlockPos pos = BlockPos.containing(w);
            if (!server.isEmptyBlock(pos)) {
                BlockPos top = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
                if (top.getY() - pos.getY() > 6 || !server.isEmptyBlock(top)) continue;
                pos = top;
            }
            FallingBlockEntity fb = me.lovkar.wakingworld.ruin.Ruin.fall(server, pos, palette().pick(this.random));
            fb.time = 1;
            fb.dropItem = false;
            fb.setHurtsEntities(1.0F, 8);
            Vec3 out = w.subtract(center);
            double flat = Math.max(0.5, out.horizontalDistance());
            double sp = power * (0.4 + 0.8 * this.random.nextDouble());
            fb.setDeltaMovement(out.x / flat * sp + (this.random.nextDouble() - 0.5) * 0.15, 0.12 + this.random.nextDouble() * 0.25, out.z / flat * sp + (this.random.nextDouble() - 0.5) * 0.15);
            spawned++;
        }
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, palette().pick(this.random)), center.x, center.y, center.z, 60, def.sx * 0.5, def.sy * 0.3, def.sz * 0.5, 0.15);
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 1, center.z, 25, def.sx * 0.5, 1.0, def.sz * 0.5, 0.03);
        server.playSound(null, center.x, center.y, center.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 7.0F, 0.3F);
        server.playSound(null, center.x, center.y, center.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 6.0F, 0.4F);
    }

    /**
     * The end: the torso bursts where it lies, the heap forms under it with the cores' embers
     * inside, smoke climbs, sparks drift up out of the ruin, and the sky answers once.
     */
    private void collapse(ServerLevel server) {
        double h = bodyHeight();
        Vec3 chest = partPoint(PartDef.Kind.TORSO, 0.5);
        crumble(server, PartDef.Kind.TORSO, (int) (WakingConfig.collapseBlocks() * 0.38), 0.6);
        Crater.blast(server, chest, h * 0.08, 20, 0.6, 2, this.random);
        if (WakingConfig.deathMound()) mound(server, chest, h);
        server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0F, 0.3F);
        server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 8.0F, 0.25F);
        server.playSound(null, chest.x, chest.y, chest.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 3.0F, 0.6F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, chest.x, chest.y + 2, chest.z, 3, h * 0.1, 2, h * 0.1, 0);
        server.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, chest.x, chest.y + 3, chest.z, 120, h * 0.15, 3, h * 0.15, 0.03);
        server.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y + 4, chest.z, 80, h * 0.12, 2, h * 0.12, 0.06);
        server.sendParticles(ParticleTypes.LAVA, chest.x, chest.y + 2, chest.z, 60, h * 0.12, 1.5, h * 0.12, 0);
        server.sendParticles(themeParticle(), chest.x, chest.y + 3, chest.z, 200, h * 0.2, 3, h * 0.2, 0.2);
        server.sendParticles(themeParticle2(), chest.x, chest.y + 3, chest.z, 100, h * 0.25, 4, h * 0.25, 0.05);
        lightning(server, chest, 2);
        loot(server, chest);
        // the Titan down: the arena's altar raises the gate home
        if (isTitan() && altarPos != null && server.isLoaded(altarPos) && server.getBlockEntity(altarPos) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity altar) {
            altar.openGate();
        }
    }

    /**
     * What it leaves on the heap: its Heart, the Sigil of its kind (the Titan, which has no sigil,
     * leaves three Hearts and the Heart of the End), and a great deal of experience. The items glow so they
     * can be found in the ruin and never despawn.
     */
    private void loot(ServerLevel server, Vec3 chest) {
        Vec3 at;
        if (groundUnder(chest)) {
            BlockPos top = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(chest.x, chest.y, chest.z));
            at = new Vec3(chest.x, Math.max(chest.y, top.getY()) + 1.5, chest.z);
        } else {
            // it died over the void: the hoard lands on the nearest island, never in the dark
            Vec3 safe = VoidGuard.nearestGround(server, chest, 48, lastGround);
            if (safe == null) safe = chest;
            at = new Vec3(safe.x, safe.y + 1.5, safe.z);
        }
        Vec3 ground = new Vec3(at.x, at.y - 1.5, at.z);
        boolean titan = "titan".equals(palette().kind);
        List<net.minecraft.world.item.ItemStack> drops = new ArrayList<>();
        drops.add(new net.minecraft.world.item.ItemStack(me.lovkar.wakingworld.item.WakingItems.COLOSSUS_HEART.get(), titan ? 3 : 1));
        net.minecraft.world.item.Item sigil = me.lovkar.wakingworld.item.WakingItems.sigilFor(palette().kind);
        if (sigil != null) drops.add(new net.minecraft.world.item.ItemStack(sigil));
        if (titan) {
            drops.add(new net.minecraft.world.item.ItemStack(me.lovkar.wakingworld.item.WakingItems.HEART_OF_THE_END.get()));
            drops.add(new net.minecraft.world.item.ItemStack(me.lovkar.wakingworld.item.WakingItems.TITAN_KEY.get())); // the Key comes back to whoever offered it
            if (WakingConfig.titanNeedsEgg()) drops.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_EGG)); // and the dragon's egg, cold again: there is only the one, and the next Titan wants it too
        }
        for (net.minecraft.world.item.ItemStack stack : drops) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(server, at.x, at.y, at.z, stack);
            item.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.3, 0.45 + this.random.nextDouble() * 0.2, (this.random.nextDouble() - 0.5) * 0.3);
            item.setUnlimitedLifetime();
            item.setGlowingTag(true);
            item.setInvulnerable(true);
            server.addFreshEntity(item);
            VoidGuard.watch(item, ground);
        }
        net.minecraft.world.entity.ExperienceOrb.award(server, at, titan ? 3000 : 600 + (int) (bodyHeight() * 8));
    }

    /** Visual-only lightning on a ring around a point. */
    private void titanLightningRing(ServerLevel server, Vec3 at, double radius, int count) {
        double offset = this.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = offset + i * Math.PI * 2 / count;
            Vec3 p = at.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            BlockPos g = server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p));
            if (g.getY() <= server.getMinBuildHeight()) continue; // over the void: nothing to strike
            net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt == null) return;
            bolt.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
    }

    /** Visual-only lightning (no fire, no damage) at a point, count bolts spread around it. */
    void lightning(ServerLevel server, Vec3 at, int count) {
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt == null) return;
            double a = this.random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 3 + this.random.nextDouble() * 6;
            bolt.moveTo(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
    }

    /**
     * The heap where it fell: a dome of the palette's blocks, radius about a sixth of the body and
     * a tenth of it high, rising from whatever the ground is now (it fills the death crater first).
     * Only air, plants and fluids are built over - the world around is left alone. A handful of the
     * glow blocks sit inside as the dead giant's embers.
     */
    private void mound(ServerLevel server, Vec3 at, double h) {
        double radius = Math.max(3.0, h * 0.18);
        double height = Math.max(2.0, h * 0.12);
        int r = (int) Math.ceil(radius);
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z);
        int placed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / radius;
                if (d > 1.0) continue;
                double top = height * Math.sqrt(1.0 - d * d) * (0.8 + 0.4 * this.random.nextDouble());
                if (top < 0.5) continue;
                int x = cx + dx, z = cz + dz;
                int ground = server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                // the ground here should be roughly where the giant stood; a cliff face next to it is not part of the heap
                if (Math.abs(ground - at.y) > height + 4) continue;
                int base = Math.min(ground, Mth.floor(at.y) + 1);
                int columns = (int) Math.round(top);
                for (int y = 0; y < columns && placed < 4000; y++) {
                    BlockPos pos = new BlockPos(x, base + y, z);
                    BlockState there = server.getBlockState(pos);
                    if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty() && !Crater.vegetation(there)) continue;
                    boolean ember = this.random.nextInt(28) == 0 && y > 0;
                    me.lovkar.wakingworld.ruin.Ruin.mark(server, pos);
                    server.setBlock(pos, ember ? palette().core.defaultBlockState() : palette().pick(this.random), 3);
                    placed++;
                }
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        dropHeld();
        if (this.level() instanceof ServerLevel server) {
            tickTempBlocks(server, true);
            if (reason.shouldDestroy()) me.lovkar.wakingworld.ruin.RuinLedger.get(server).finish(this.getUUID(), server.getGameTime());
        }
        super.remove(reason);
    }

    // ---- boss bar --------------------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(bossBarName());
    }

    // ---- being a giant ---------------------------------------------------------------------

    @Override
    protected AABB getAttackBoundingBox() {
        double h = bodyHeight();
        return this.getBoundingBox().inflate(h * 0.30, h * 0.20, h * 0.30);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        ColossusBody b = body();
        double r = b.halfWidth();
        AABB box = this.getBoundingBox();
        return new AABB(box.minX - r, box.minY - 1, box.minZ - r, box.maxX + r, box.minY + b.height + 2, box.maxZ + r);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** Nothing moves it while it is still coming out of the ground. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isWaking();
    }

    /** Made of the ground: its own rubble raining down, standing inside blocks, falling, water and cold do nothing to it. Nor does anything while it rises. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isWaking() && !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) return true;
        // only people can bring one down: endermen, skeletons and the rest may swarm it, they cannot hurt it
        if (source.getEntity() instanceof Mob && !(source.getEntity() instanceof Player)) return true;
        if (source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FREEZE)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * A mountain does not swim. Vanilla treats any mob whose box touches water as swimming - a
     * crawl of 0.03 blocks a tick, no pathfinding out, and for a giant that meant standing in a
     * lake for minutes. Water and lava are air to it: it sinks like the stone it is and wades
     * across the bottom at walking pace, legs in the water, shoulders above it.
     */
    @Override
    public boolean isAffectedByFluids() {
        return false;
    }

    /** No current moves it either. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** The combat goal gave up on the wall and is walking around it - forget the collision count. */
    public void clearStuck() {
        this.stuckTicks = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public int getMaxHeadYRot() {
        return 50;
    }

    @Override
    public int getMaxHeadXRot() {
        return 35;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // ---- sounds ----------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DEEPSLATE_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.35F + this.random.nextFloat() * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    /** Vanilla would thud once per block moved; the giant's strides are ten blocks long - see footsteps(). */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    /** Wading: every stride throws up a wall of spray instead of vanilla's little swimming sound. */
    @Override
    protected void playSwimSound(float volume) {
        this.playSound(SoundEvents.GENERIC_SPLASH, 3.0F, 0.4F + this.random.nextFloat() * 0.2F);
        if (this.level() instanceof ServerLevel server) {
            groundBurst(server, this.position(), 24, 1.8);
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }
}
