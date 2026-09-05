package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One hitbox of a colossus, the way the Ender Dragon is made of parts: the colossus itself only
 * has a small collision box at its feet (so it walks over terrain like any mob), and these parts
 * follow its torso, head and limbs so arrows, swords and eyes find the whole giant. Parts are
 * never sent to clients as entities and never saved - the parent rebuilds them.
 */
public class ColossusPart extends PartEntity<ColossusEntity> {
    /**
     * True only while the client ticks its own player (set by WakingWorldClient around that tick).
     * Then, and only then, the parts are solid: the player cannot walk through a leg. Nothing else
     * ever collides with them - not the server (which would otherwise reject the player's moves as
     * "moved wrongly" whenever its idea of the body lagged the client's), not falling rubble or
     * items on the client (which simulate their own flight between server updates).
     */
    public static boolean solidForLocalPlayer;
    /** The local player's box at the start of its tick: a part it already overlaps is never solid for it, so nothing ever traps it. */
    public static AABB localPlayerBox;

    public final PartDef.Kind kind;
    private AABB box;
    private AABB prev;

    /** Which core this box is (-1 = a slice of the body's armour). */
    public final int core;

    public ColossusPart(ColossusEntity parent, PartDef.Kind kind) {
        this(parent, kind, -1);
    }

    public ColossusPart(ColossusEntity parent, PartDef.Kind kind, int core) {
        super(parent);
        this.kind = kind;
        this.core = core;
        // Entity's constructor already asked for a bounding box while our field was still null
        // (field initialisers run after super()); give it a real one now, at the parent's feet.
        this.box = new AABB(parent.getX() - 0.5, parent.getY(), parent.getZ() - 0.5,
                parent.getX() + 0.5, parent.getY() + 1.0, parent.getZ() + 0.5);
        this.setBoundingBox(this.box);
    }

    /**
     * Places the part: the box is in world coordinates. Parts are never ticked, so the previous
     * position fields the renderer interpolates from (xo/xOld...) are kept by hand, exactly like
     * the Ender Dragon does - otherwise F3+B draws the boxes somewhere between the spawn point and
     * the giant, and every hit-test that interpolates is off.
     */
    public void place(AABB worldBox) {
        this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
        this.xOld = this.xo; this.yOld = this.yo; this.zOld = this.zo;
        this.prev = this.box;
        this.box = worldBox;
        this.setPos((worldBox.minX + worldBox.maxX) / 2.0, worldBox.minY, (worldBox.minZ + worldBox.maxZ) / 2.0);
        this.setBoundingBox(worldBox);
    }

    /** First placement (spawn/teleport): no interpolation from wherever the part was before. */
    public void snapTo(AABB worldBox) {
        this.prev = worldBox;
        this.box = worldBox;
        this.setPos((worldBox.minX + worldBox.maxX) / 2.0, worldBox.minY, (worldBox.minZ + worldBox.maxZ) / 2.0);
        this.setBoundingBox(worldBox);
        this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
        this.xOld = this.xo; this.yOld = this.yo; this.zOld = this.zo;
    }

    /** How far the box moved with the last placement - the sweep that shoves a player standing in the way. */
    public Vec3 motion() {
        return this.prev == null || this.box == null ? Vec3.ZERO : this.box.getCenter().subtract(this.prev.getCenter());
    }

    @Override
    protected AABB makeBoundingBox() {
        AABB b = this.box;
        return b != null ? b : new AABB(this.getX() - 0.5, this.getY(), this.getZ() - 0.5, this.getX() + 0.5, this.getY() + 1.0, this.getZ() + 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Not while it is still under the ground. */
    @Override
    public boolean isPickable() {
        return !this.getParent().isWaking();
    }

    /** Solid for the local player's own movement, on the client, while the giant lives - see {@link #solidForLocalPlayer}. */
    @Override
    public boolean canBeCollidedWith() {
        return core < 0 && solidForLocalPlayer && this.level().isClientSide && this.getParent().isAlive() && !this.getParent().isWaking()
                && (localPlayerBox == null || !this.getBoundingBox().intersects(localPlayerBox));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source) && this.getParent().hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
