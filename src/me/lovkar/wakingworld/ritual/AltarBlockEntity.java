package me.lovkar.wakingworld.ritual;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.ColossusEntity;
import me.lovkar.wakingworld.item.Waker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The altar's memory: which kind of shrine it belongs to, what has been laid on it, and - while
 * the rite runs - how far along it is. Server side it runs the ceremony ({@link Ceremony}) and
 * wakes the colossus at the end; the client gets the offerings and the rite's timer through the
 * usual block-entity sync and draws them ({@link me.lovkar.wakingworld.client.AltarRenderer}).
 */
public class AltarBlockEntity extends BlockEntity {
    public static final int SLOTS = 6;

    private String kind = "stone";
    private final NonNullList<ItemStack> offerings = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** Ticks of the rite still to run (0 = idle). */
    private int rite;
    private int riteTotal;
    private UUID starter;
    /** Blocks the ceremony placed for a while - {pos, before, expires}. */
    private final List<Object[]> temp = new ArrayList<>();
    /** Client: the spin of the floating offerings. */
    public float spin, spinO;
    /** The Titan's Gate (great altar only): standing, and how far along its raising is (0 = not being raised). */
    private boolean gateOpen;
    private int gateAnim;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(WakingRitual.ALTAR_ENTITY.get(), pos, state);
    }

    public String kind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
        setChanged();
        sync();
    }

    public NonNullList<ItemStack> offerings() {
        return offerings;
    }

    public int rite() {
        return rite;
    }

    public int riteTotal() {
        return riteTotal;
    }

    public boolean running() {
        return rite > 0;
    }

    // ---- offerings ----------------------------------------------------------------------

    /** What the altar still wants: the offerings not yet fully laid down. */
    public List<Rites.Offering> missing() {
        List<Rites.Offering> out = new ArrayList<>();
        for (Rites.Offering o : Rites.offerings(kind)) {
            int have = 0;
            for (ItemStack s : offerings) if (o.matches(s)) have += s.getCount();
            if (have < o.count()) out.add(new Rites.Offering(o.item(), o.count() - have));
        }
        return out;
    }

    public boolean complete() {
        return missing().isEmpty();
    }

    /** The great altar of the arena: the one the horn is sounded over, with the six lesser ones round it. */
    public boolean great() {
        return "titan".equals(kind);
    }

    /** The lesser altars round the great one that exist (an arena from before they were built has none). */
    public List<AltarBlockEntity> lesserAltars() {
        List<AltarBlockEntity> out = new ArrayList<>();
        if (!great() || level == null) return out;
        for (int i = 0; i < Rites.LANDS.length; i++) {
            int[] o = me.lovkar.wakingworld.worldgen.TitanArenaPiece.lesserOffset(i);
            BlockPos p = worldPosition.offset(o[0], me.lovkar.wakingworld.worldgen.TitanArenaPiece.LESSER_DY, o[1]);
            if (level.getBlockEntity(p) instanceof AltarBlockEntity a && Rites.lesser(a.kind)) out.add(a);
        }
        return out;
    }

    /** What the lesser altars still want, land by land - empty when all six burn (or there are none). */
    public List<Rites.Offering> missingLesser() {
        List<Rites.Offering> out = new ArrayList<>();
        for (AltarBlockEntity a : lesserAltars()) out.addAll(a.missing());
        return out;
    }

    /** Lays an offering down if the altar wants it (the required count at once, from the stack in hand). */
    public boolean offer(Player player, InteractionHand hand, ItemStack stack) {
        if (running() || stack.isEmpty()) return false;
        for (Rites.Offering want : missing()) {
            if (!want.matches(stack)) continue;
            if (stack.getCount() < want.count()) {
                player.displayClientMessage(Component.translatable("altar.wakingworld.needs_more", want.count(), stack.getHoverName()).withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            for (int i = 0; i < SLOTS; i++) {
                if (offerings.get(i).isEmpty()) {
                    offerings.set(i, stack.copyWithCount(want.count()));
                    if (!player.getAbilities().instabuild) stack.shrink(want.count());
                    Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 0.9, 0);
                    if (level instanceof ServerLevel server) {
                        server.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.5F, 0.7F);
                        server.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.8F, 1.4F);
                        server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.rune(Rites.color(kind), 1.2f), c.x, c.y, c.z, 12, 0.5, 0.4, 0.5, 0.03);
                        server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.ember(Rites.color(kind), 1f), c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.15);
                    }
                    setChanged();
                    sync();
                    List<Rites.Offering> left = missing();
                    if (left.isEmpty() && Rites.lesser(kind)) {
                        player.displayClientMessage(Component.translatable("altar.wakingworld.lesser_lit").withStyle(ChatFormatting.GOLD), true);
                    } else if (left.isEmpty()) {
                        List<Rites.Offering> unlit = missingLesser();
                        player.displayClientMessage(unlit.isEmpty() ? Component.translatable("altar.wakingworld.ready").withStyle(ChatFormatting.GOLD)
                                : Component.translatable("altar.wakingworld.ready_unlit", describe(unlit)).withStyle(ChatFormatting.GOLD), true);
                    } else {
                        player.displayClientMessage(Component.translatable("altar.wakingworld.wants", describe(left)).withStyle(ChatFormatting.GRAY), true);
                    }
                    return true;
                }
            }
            return false;
        }
        // not wanted: say what is
        List<Rites.Offering> left = missing();
        player.displayClientMessage(Component.translatable(left.isEmpty() ? "altar.wakingworld.ready" : "altar.wakingworld.wants", describe(left)).withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    /** Hands the last offering back. */
    public boolean takeBack(Player player) {
        if (running()) return false;
        for (int i = SLOTS - 1; i >= 0; i--) {
            ItemStack s = offerings.get(i);
            if (s.isEmpty()) continue;
            if (!player.getInventory().add(s)) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, s);
            offerings.set(i, ItemStack.EMPTY);
            if (level instanceof ServerLevel server) server.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.8F);
            setChanged();
            sync();
            return true;
        }
        return false;
    }

    /** The rite took what was laid here. */
    void consume() {
        for (int i = 0; i < SLOTS; i++) offerings.set(i, ItemStack.EMPTY);
        setChanged();
        sync();
    }

    public void dropAll() {
        if (level == null) return;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = offerings.get(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, s);
            offerings.set(i, ItemStack.EMPTY);
        }
        restoreTemp(true);
    }

    public static Component describe(List<Rites.Offering> list) {
        Component out = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            Rites.Offering o = list.get(i);
            if (i > 0) out = out.copy().append(", ");
            out = out.copy().append(Component.literal(o.count() + "x ").append(new ItemStack(o.item()).getHoverName()));
        }
        return out;
    }

    // ---- the rite --------------------------------------------------------------------------

    /** The horn was sounded over the altar. */
    public void blow(ServerPlayer player) {
        if (Rites.lesser(kind)) {
            // a lesser altar only answers the great one in the middle
            player.displayClientMessage(Component.translatable(complete() ? "altar.wakingworld.lesser_lit" : "altar.wakingworld.lesser", describe(missing())).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (running()) {
            player.displayClientMessage(Component.translatable("altar.wakingworld.running").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<Rites.Offering> left = missing();
        if (!left.isEmpty()) {
            player.displayClientMessage(Component.translatable("altar.wakingworld.wants", describe(left)).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<Rites.Offering> unlit = missingLesser();
        if (!unlit.isEmpty()) {
            player.displayClientMessage(Component.translatable("altar.wakingworld.unlit", describe(unlit)).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!(level instanceof ServerLevel server)) return;
        for (ColossusEntity other : server.getEntitiesOfClass(ColossusEntity.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(160))) {
            if (other.isAlive()) {
                player.displayClientMessage(Component.translatable("altar.wakingworld.another").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        closeGate(server); // the way home shuts while the Titan is up
        rite = riteTotal = Rites.TICKS;
        starter = player.getUUID();
        setChanged();
        sync();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4.0F, 0.6F);
        server.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 4.0F, 0.5F);
        // the ground answers the horn from far below
        server.playSound(null, c.x, c.y - 6, c.z, me.lovkar.wakingworld.WakingSounds.HORN_ANSWER.get(), SoundSource.BLOCKS, 10.0F, 0.9F);
        player.displayClientMessage(Component.translatable("altar.wakingworld.begins").withStyle(ChatFormatting.GOLD), true);
        me.lovkar.wakingworld.advancement.WakingTriggers.RITE.get().trigger(player, kind, Rites.height(kind));
        me.lovkar.wakingworld.story.Chronicle.record(player.serverLevel(), "rite", kind, worldPosition, player.getGameProfile().getName());
    }

    /** Debug/admin: begins the rite regardless of offerings. */
    public void forceStart() {
        if (running()) return;
        if (level instanceof ServerLevel server) closeGate(server);
        rite = riteTotal = Rites.TICKS;
        setChanged();
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity altar) {
        if (!(level instanceof ServerLevel server)) return;
        altar.restoreTemp(false);
        if (altar.gateAnim > 0) altar.tickGate(server);
        if (altar.rite <= 0) return;
        int elapsed = altar.riteTotal - altar.rite;
        Ceremony.tick(server, altar, altar.kind, pos, elapsed, altar.riteTotal);
        altar.rite--;
        if (altar.rite == 0) altar.climax(server);
        else if (altar.rite % 40 == 0) altar.sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity altar) {
        altar.spinO = altar.spin;
        altar.spin += altar.rite > 0 ? 6f + 24f * (1f - altar.rite / (float) Math.max(1, altar.riteTotal)) : 1.2f;
        if (altar.rite > 0) {
            altar.rite--; // the client counts along between syncs
            float p = 1f - altar.rite / (float) Math.max(1, altar.riteTotal);
            if (altar.rite % 10 == 0) WakingWorld.hooks.shakeAt(Vec3.atCenterOf(pos), 0.15f + 0.9f * p * p, 48);
            return;
        }
        // idle: the altar breathes - a rune glyph now and then, embers when it is ready and waiting for the horn
        net.minecraft.util.RandomSource r = level.getRandom();
        int laid = 0;
        for (net.minecraft.world.item.ItemStack st : altar.offerings) if (!st.isEmpty()) laid++;
        boolean ready = altar.complete();
        int color = Rites.color(altar.kind);
        if (r.nextInt(ready ? 4 : 14 - Math.min(8, laid * 2)) == 0) {
            double a = r.nextDouble() * Math.PI * 2, rad = 0.8 + r.nextDouble() * 1.6;
            level.addParticle(me.lovkar.wakingworld.particle.WakingParticles.rune(color, 0.5f + r.nextFloat() * 0.5f),
                    pos.getX() + 0.5 + Math.cos(a) * rad, pos.getY() + 0.2 + r.nextDouble() * 0.6, pos.getZ() + 0.5 + Math.sin(a) * rad, 0, 0.02 + r.nextDouble() * 0.03, 0);
        }
        if (ready && r.nextInt(3) == 0) {
            level.addParticle(me.lovkar.wakingworld.particle.WakingParticles.ember(color, 0.6f + r.nextFloat() * 0.6f),
                    pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.6, pos.getY() + 1.4 + r.nextDouble() * 0.8, pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.02, 0.04 + r.nextDouble() * 0.04, (r.nextDouble() - 0.5) * 0.02);
        }
        if (ready && !Rites.lesser(altar.kind) && level.getGameTime() % 40 == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.7f, false);
        }
    }

    private void climax(ServerLevel server) {
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Player who = starter == null ? null : server.getPlayerByUUID(starter);
        if (who == null) who = server.getNearestPlayer(c.x, c.y, c.z, 64, false);
        // the giant rises on the far side of the altar from whoever called it, out past the shrine
        Vec3 dir = who == null ? new Vec3(0, 0, 1) : c.subtract(who.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        double out = "titan".equals(kind) ? 24 : 36;
        Vec3 spot = c.add(dir.scale(out));
        ColossusEntity woken = Waker.wakeAt(server, who == null ? c.subtract(dir.scale(20)) : who.position(), Rites.palette(kind), Rites.height(kind), spot);
        Ceremony.climax(server, this, kind, worldPosition, woken != null);
        for (int i = 0; i < SLOTS; i++) offerings.set(i, ItemStack.EMPTY);
        for (AltarBlockEntity lesser : lesserAltars()) lesser.consume(); // the six runes burn with the rite
        if (woken != null) woken.setAltar(worldPosition);
        riteTotal = 0;
        starter = null;
        setChanged();
        sync();
    }

    /** A block laid down by the ceremony that goes back to what it was after a while. */
    void placeTemp(ServerLevel server, BlockPos pos, BlockState state, int ticks) {
        BlockState before = server.getBlockState(pos);
        if (!before.isAir() && !before.canBeReplaced() && before.getFluidState().isEmpty()) return;
        if (server.setBlock(pos, state, 3)) {
            temp.add(new Object[]{pos.immutable(), before, server.getGameTime() + ticks});
            setChanged();
        }
    }

    private void restoreTemp(boolean all) {
        if (temp.isEmpty() || !(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        for (int i = temp.size() - 1; i >= 0; i--) {
            Object[] t = temp.get(i);
            if (!all && (Long) t[2] > now) continue;
            temp.remove(i);
            BlockPos p = (BlockPos) t[0];
            BlockState before = (BlockState) t[1];
            if (server.getBlockState(p).isAir()) continue;
            server.setBlock(p, before.isAir() || before.canBeReplaced() ? before : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        setChanged();
    }

    // ---- the Titan's Gate --------------------------------------------------------------------

    /**
     * The gate stands at the arena's rim, south of the altar, facing it: an arch of the End's own
     * stone, thirteen wide and seventeen tall, with the sheet of the void hanging in it. Its plane
     * lies across x at {@code z + GATE_DZ}; the arena's parapet begins three blocks behind it.
     */
    public static final int GATE_DZ = 44, GATE_FLOOR_DY = -4;
    /** Rows of the sheet (0 = on the floor) and each row's half-width: a nave of nine, then the arch closes in. */
    static final int SHEET_ROWS = 13;
    static int sheetHalf(int row) {
        return row < 10 ? 4 : row == 10 ? 3 : row == 11 ? 2 : 1;
    }
    /** Ticks: a frame row rises every {@code GATE_ROW}; the sheet fills a row every {@code GATE_FILL} after the frame stands. */
    private static final int GATE_ROW = 5, GATE_FILL = 3, FRAME_ROWS = 16;
    private static final int GATE_FRAME_DONE = GATE_ROW * FRAME_ROWS + 6, GATE_TOTAL = GATE_FRAME_DONE + GATE_FILL * SHEET_ROWS + 12;

    public boolean gateOpen() {
        return gateOpen;
    }

    private int gateFloor() {
        return worldPosition.getY() + GATE_FLOOR_DY;
    }

    private BlockPos gatePos(int dx, int row) {
        return new BlockPos(worldPosition.getX() + dx, gateFloor() + 1 + row, worldPosition.getZ() + GATE_DZ);
    }

    private static final BlockState OBSIDIAN = net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState CRYING = net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState PILLAR = net.minecraft.world.level.block.Blocks.PURPUR_PILLAR.defaultBlockState();
    private static final BlockState ROD = net.minecraft.world.level.block.Blocks.END_ROD.defaultBlockState();

    /**
     * The frame, row by row from the floor: the blocks of row {@code k} and what each is made of.
     * Rows 0-12 flank the sheet (two thick beside the nave, following the arch above it); 13-15 close
     * it - the cap, the crown, the keystone; the rods stand on the shoulders and the keystone.
     */
    private java.util.Map<BlockPos, BlockState> frameRow(int k) {
        java.util.Map<BlockPos, BlockState> out = new java.util.LinkedHashMap<>();
        if (k < SHEET_ROWS) {
            int hw = sheetHalf(k);
            for (int side = -1; side <= 1; side += 2) {
                // the inner block of the jamb glows; the outer is dark obsidian, banded with a glow every fourth row
                out.put(gatePos(side * (hw + 1), k), k == 0 ? PILLAR : CRYING);
                out.put(gatePos(side * (hw + 2), k), k == 0 ? PILLAR : k % 4 == 0 ? CRYING : OBSIDIAN);
            }
            if (k == 10) for (int side = -1; side <= 1; side += 2) out.put(gatePos(side * 6, k), PILLAR); // the shoulders' caps
            if (k == 11) for (int side = -1; side <= 1; side += 2) out.put(gatePos(side * 6, k), ROD);    // and their lights
        } else if (k == 13) {
            for (int dx = -3; dx <= 3; dx++) out.put(gatePos(dx, k), Math.abs(dx) == 3 ? PILLAR : CRYING);
        } else if (k == 14) {
            for (int dx = -2; dx <= 2; dx++) out.put(gatePos(dx, k), Math.abs(dx) == 2 ? OBSIDIAN : CRYING);
        } else if (k == 15) {
            out.put(gatePos(0, k), CRYING); // the keystone
            out.put(gatePos(-1, k), ROD);
            out.put(gatePos(1, k), ROD);
            out.put(gatePos(0, k + 1), ROD);
        }
        return out;
    }

    /** The sheet's blocks in row {@code j} (0 = on the floor), between the jambs. */
    private List<BlockPos> sheetRow(int j) {
        List<BlockPos> out = new ArrayList<>();
        int hw = sheetHalf(j);
        for (int dx = -hw; dx <= hw; dx++) out.add(gatePos(dx, j));
        return out;
    }

    /** Every block the gate is made of, frame and sheet. */
    private List<BlockPos> gateBlocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int k = 0; k < FRAME_ROWS; k++) out.addAll(frameRow(k).keySet());
        for (int j = 0; j < SHEET_ROWS; j++) out.addAll(sheetRow(j));
        return out;
    }

    /**
     * The Titan has fallen: the arena raises the way home. Row by row the arch comes up out of
     * the floor at the rim, the keystone closes it, and the void fills the arch from the ground
     * up; then the gate breathes there until the next rite takes it down. The fight's record
     * forgets the gate's ground, so the Hourglass of Restoration leaves the gate standing.
     */
    public void openGate() {
        if (!great() || gateOpen || gateAnim > 0) return;
        gateAnim = 1;
        setChanged();
        sync();
        if (level instanceof ServerLevel server) {
            Vec3 c = gateCenter();
            me.lovkar.wakingworld.ruin.RuinLedger.get(server).forget(gateBlocks());
            server.playSound(null, c.x, c.y, c.z, me.lovkar.wakingworld.WakingSounds.GATE_OPEN.get(), SoundSource.BLOCKS, 8.0F, 1.0F);
            server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 5.0F, 0.5F);
        }
    }

    private Vec3 gateCenter() {
        return new Vec3(worldPosition.getX() + 0.5, gateFloor() + 1 + SHEET_ROWS / 2.0, worldPosition.getZ() + GATE_DZ + 0.5);
    }

    private void tickGate(ServerLevel server) {
        int t = gateAnim - 1; // gateAnim counts from 1 (0 = not being raised)
        gateAnim++;
        int color = Rites.color("titan");
        // the frame, a row at a time
        if (t % GATE_ROW == 0 && t / GATE_ROW < FRAME_ROWS) {
            int k = t / GATE_ROW;
            java.util.Map<BlockPos, BlockState> row = frameRow(k);
            for (java.util.Map.Entry<BlockPos, BlockState> e : row.entrySet()) {
                server.setBlock(e.getKey(), e.getValue(), 3);
                Vec3 c = Vec3.atCenterOf(e.getKey());
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, CRYING), c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.1);
                server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.rune(color, 1f), c.x, c.y + 0.5, c.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            if (!row.isEmpty()) {
                Vec3 c = Vec3.atCenterOf(row.keySet().iterator().next());
                server.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 3.5F, 0.35F + k * 0.03F);
                server.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 2.0F, 0.5F + k * 0.04F);
                if (k == FRAME_ROWS - 1) server.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 4.0F, 0.5F);
            }
        }
        // the sheet, from the ground up
        if (t >= GATE_FRAME_DONE && (t - GATE_FRAME_DONE) % GATE_FILL == 0) {
            int j = (t - GATE_FRAME_DONE) / GATE_FILL;
            if (j < SHEET_ROWS) {
                for (BlockPos p : sheetRow(j)) {
                    server.setBlock(p, WakingRitual.TITAN_GATE.get().defaultBlockState(), 3);
                    Vec3 c = Vec3.atCenterOf(p);
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 8, 0.4, 0.4, 0.3, 0.15);
                }
                Vec3 c = Vec3.atCenterOf(gatePos(0, j));
                server.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 2.5F, 0.55F + j * 0.05F);
            }
        }
        if (t >= GATE_TOTAL) {
            gateAnim = 0;
            gateOpen = true;
            Vec3 c = gateCenter();
            me.lovkar.wakingworld.ruin.RuinLedger.get(server).forget(gateBlocks()); // again: the fight may have marked the ground since
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 4.5, 6.0, 0.8, 0.08);
            server.sendParticles(me.lovkar.wakingworld.particle.WakingParticles.rune(color, 1.6f), c.x, c.y, c.z, 60, 5, 7, 1, 0.05);
            server.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 6.0F, 0.8F);
            server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 5.0F, 0.6F);
            WakingWorld.LOGGER.info("the Titan's Gate stands at {}", BlockPos.containing(c));
        }
        setChanged();
    }

    /** The next rite takes the gate down: the sheet goes out, the frame sinks back into the floor. */
    private void closeGate(ServerLevel server) {
        if (!great() || (!gateOpen && gateAnim == 0)) return;
        boolean any = false;
        for (int j = 0; j < SHEET_ROWS; j++) {
            for (BlockPos p : sheetRow(j)) {
                if (server.getBlockState(p).is(WakingRitual.TITAN_GATE.get())) {
                    server.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    any = true;
                }
            }
        }
        for (int k = 0; k < FRAME_ROWS; k++) {
            for (java.util.Map.Entry<BlockPos, BlockState> e : frameRow(k).entrySet()) {
                if (server.getBlockState(e.getKey()).is(e.getValue().getBlock())) server.setBlock(e.getKey(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        gateOpen = false;
        gateAnim = 0;
        setChanged();
        sync();
        Vec3 c = gateCenter();
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, any ? 300 : 60, 4.5, 6.0, 1.0, 0.4);
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, CRYING), c.x, c.y, c.z, 100, 5, 7, 0.8, 0.15);
        server.playSound(null, c.x, c.y, c.z, me.lovkar.wakingworld.WakingSounds.GATE_CLOSE.get(), SoundSource.BLOCKS, 8.0F, 1.0F);
        server.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 5.0F, 0.3F);
    }

    // ---- sync + nbt --------------------------------------------------------------------------

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Kind", kind);
        ContainerHelper.saveAllItems(tag, offerings, registries);
        tag.putInt("Rite", rite);
        tag.putInt("RiteTotal", riteTotal);
        if (gateOpen) tag.putBoolean("GateOpen", true);
        if (gateAnim > 0) tag.putInt("GateAnim", gateAnim);
        if (starter != null) tag.putUUID("Starter", starter);
        if (!temp.isEmpty()) {
            ListTag list = new ListTag();
            for (Object[] t : temp) {
                CompoundTag c = new CompoundTag();
                c.put("Pos", NbtUtils.writeBlockPos((BlockPos) t[0]));
                c.put("Before", NbtUtils.writeBlockState((BlockState) t[1]));
                c.putLong("Expires", (Long) t[2]);
                list.add(c);
            }
            tag.put("Temp", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kind = tag.contains("Kind") ? tag.getString("Kind") : "stone";
        offerings.clear();
        ContainerHelper.loadAllItems(tag, offerings, registries);
        rite = tag.getInt("Rite");
        riteTotal = tag.getInt("RiteTotal");
        gateOpen = tag.getBoolean("GateOpen");
        gateAnim = tag.getInt("GateAnim");
        starter = tag.hasUUID("Starter") ? tag.getUUID("Starter") : null;
        temp.clear();
        if (tag.contains("Temp")) {
            ListTag list = tag.getList("Temp", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                BlockPos p = NbtUtils.readBlockPos(c, "Pos").orElse(null);
                if (p == null) continue;
                temp.add(new Object[]{p, NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), c.getCompound("Before")), c.getLong("Expires")});
            }
        }
    }
}
