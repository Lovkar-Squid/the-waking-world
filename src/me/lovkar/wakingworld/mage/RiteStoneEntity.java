package me.lovkar.wakingworld.mage;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.cataclysm.BloodMoon;
import me.lovkar.wakingworld.cataclysm.Cataclysms;
import me.lovkar.wakingworld.cataclysm.TornadoEntity;
import me.lovkar.wakingworld.cataclysm.Volcano;
import me.lovkar.wakingworld.cataclysm.Weather;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public class RiteStoneEntity extends BlockEntity {
    /** How long the stone burns before the thing arrives: long enough to run, short enough to watch. */
    private static final int BURN = 14 * 20;

    private String rite = "";
    private final List<ItemStack> laid = new ArrayList<>();
    private int burning;
    private Vec3 aim = Vec3.ZERO;
    public float spin;
    public float spinO;

    public RiteStoneEntity(BlockPos var1, BlockState var2) {
        super((BlockEntityType)MageBlocks.RITE_STONE_ENTITY.get(), var1, var2);
    }

    public String rite() {
        return rite;
    }

    public boolean burning() {
        return burning > 0;
    }

    public float burnProgress() {
        return this.burning <= 0 ? 0.0F : 1.0F - (float)this.burning / 280.0F;
    }

    public int burnTicks() {
        return this.burning;
    }

    public static int burnTotal() {
        return 280;
    }

    public List<ItemStack> offerings() {
        return this.laid;
    }

    public int colour() {
        DarkRites.Rite var1 = DarkRites.byId(this.rite);
        return var1 == null ? 12615935 : var1.colour();
    }

    public static void clientTick(Level var0, BlockPos var1, BlockState var2, RiteStoneEntity var3) {
        var3.spinO = var3.spin;
        var3.spin = var3.spin + (var3.burning > 0 ? 5.0F + 26.0F * var3.burnProgress() : 1.1F);
        if (var3.burning > 0) {
            var3.burning--;
        }
    }

    // ---- what it wants -----------------------------------------------------------------------

    /** What is still owed, after everything laid on it. */
    public List<DarkRites.Cost> missing() {
        List<DarkRites.Cost> out = new ArrayList<>();
        DarkRites.Rite r = DarkRites.byId(rite);
        if (r == null) return out;
        for (DarkRites.Cost c : r.costs()) {
            int have = 0;
            for (ItemStack s : laid) if (s.is(c.item())) have += s.getCount();
            if (have < c.count()) out.add(new DarkRites.Cost(c.item(), c.count() - have));
        }
        return out;
    }

    private static Component describe(List<DarkRites.Cost> costs) {
        Component out = Component.empty();
        for (int i = 0; i < costs.size(); i++) {
            if (i > 0) out = ((net.minecraft.network.chat.MutableComponent) out).append(Component.literal(", "));
            DarkRites.Cost c = costs.get(i);
            out = ((net.minecraft.network.chat.MutableComponent) out)
                    .append(Component.literal(c.count() + "x ").append(c.item().getDescription()));
        }
        return out;
    }

    public void turn(Player var1) {
        if (this.burning()) {
            var1.displayClientMessage(Component.translatable("rite.wakingworld.already").withStyle(ChatFormatting.GRAY), true);
        } else if (!this.laid.isEmpty()) {
            var1.displayClientMessage(Component.translatable("rite.wakingworld.laid_already").withStyle(ChatFormatting.GRAY), true);
        } else {
            this.rite = DarkRites.byIndex(this.rite.isEmpty() ? 0 : DarkRites.indexOf(this.rite) + 1).id();
            this.sync();
            DarkRites.Rite var2 = DarkRites.byId(this.rite);
            var1.displayClientMessage(
                Component.translatable("rite.wakingworld.turned", new Object[]{Component.translatable(var2.nameKey()), describe(var2.costs())})
                    .withStyle(ChatFormatting.LIGHT_PURPLE),
                false
            );
            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F);
            }
        }
    }

    public boolean offer(Player var1, InteractionHand var2, ItemStack var3) {
        if (this.burning()) {
            return false;
        } else if (this.rite.isEmpty()) {
            var1.displayClientMessage(Component.translatable("rite.wakingworld.unset").withStyle(ChatFormatting.GRAY), true);
            return true;
        } else {
            List<DarkRites.Cost> var4 = this.missing();
            DarkRites.Cost var5 = null;

            for (DarkRites.Cost var7 : var4) {
                if (var3.is(var7.item())) {
                    var5 = var7;
                }
            }

            if (var5 == null) {
                var1.displayClientMessage(Component.translatable("rite.wakingworld.wants", new Object[]{describe(var4)}).withStyle(ChatFormatting.GRAY), true);
                return true;
            } else {
                int var9 = Math.min(var5.count(), var3.getCount());
                this.laid.add(new ItemStack(var3.getItem(), var9));
                if (!var1.isCreative()) {
                    var3.shrink(var9);
                }

                this.sync();
                if (this.level != null) {
                    this.level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
                    if (this.level instanceof ServerLevel var10) {
                        Cataclysms.puff(
                            var10,
                            ParticleTypes.SOUL_FIRE_FLAME,
                            (double)this.worldPosition.getX() + 0.5,
                            (double)this.worldPosition.getY() + 0.9,
                            (double)this.worldPosition.getZ() + 0.5,
                            14,
                            0.3,
                            0.2,
                            0.3,
                            0.02
                        );
                    }
                }

                List var11 = this.missing();
                if (var11.isEmpty()) {
                    this.light(var1);
                } else {
                    var1.displayClientMessage(
                        Component.translatable("rite.wakingworld.wants", new Object[]{describe(var11)}).withStyle(ChatFormatting.GRAY), true
                    );
                }

                return true;
            }
        }
    }

    public void giveBack(Player var1) {
        if (this.burning()) {
            var1.displayClientMessage(Component.translatable("rite.wakingworld.already").withStyle(ChatFormatting.GRAY), true);
        } else if (this.laid.isEmpty()) {
            this.rite = "";
            this.sync();
            var1.displayClientMessage(Component.translatable("rite.wakingworld.cleared").withStyle(ChatFormatting.GRAY), true);
        } else {
            for (ItemStack var3 : this.laid) {
                if (!var1.addItem(var3.copy())) {
                    var1.drop(var3.copy(), false);
                }
            }

            this.laid.clear();
            this.sync();
            var1.displayClientMessage(Component.translatable("rite.wakingworld.taken_back").withStyle(ChatFormatting.GRAY), true);
        }
    }

    public void dropAll() {
        if (level == null || level.isClientSide) return;
        for (ItemStack s : laid) {
            net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(
                    level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, s.copy());
            level.addFreshEntity(e);
        }
        laid.clear();
    }

    private void light(Player var1) {
        Vec3 var2 = var1.getLookAngle();
        Vec3 aimed = new Vec3(var2.x, 0.0, var2.z);
        if (aimed.lengthSqr() < 1.0E-4) {
            aimed = new Vec3(1.0, 0.0, 0.0);
        }
        aimed = aimed.normalize();
        // where the answer would land: a colony's land is not somewhere the stone will send one
        if (this.level instanceof ServerLevel here) {
            DarkRites.Rite want = DarkRites.byId(this.rite);
            int off = reach(want == null ? "meteor" : want.id());
            BlockPos there = off == 0 ? this.worldPosition
                    : Cataclysms.surface(here, this.worldPosition.getX() + 0.5 + aimed.x * off, this.worldPosition.getZ() + 0.5 + aimed.z * off);
            if (me.lovkar.wakingworld.compat.Colonies.keepOff(here, there, 48)) {
                var1.displayClientMessage(Component.translatable("rite.wakingworld.colony").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        this.burning = 280;
        this.aim = aimed;
        this.sync();
        if (this.level != null) {
            this.level.setBlock(this.worldPosition, (BlockState)this.getBlockState().setValue(RiteStone.LIT, true), 3);
            this.level.playSound(null, this.worldPosition, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 4.0F, 0.6F);
        }

        DarkRites.Rite var3 = DarkRites.byId(this.rite);
        var1.displayClientMessage(
            Component.translatable(
                    "rite.wakingworld.lit", new Object[]{Component.translatable(var3 == null ? "cataclysm.wakingworld.name.meteor" : var3.nameKey())}
                )
                .withStyle(ChatFormatting.DARK_PURPLE),
            false
        );
    }

    public static void serverTick(Level var0, BlockPos var1, BlockState var2, RiteStoneEntity var3) {
        if (var3.burning > 0 && var0 instanceof ServerLevel var4) {
            var3.burning--;
            DarkRites.Rite var5 = DarkRites.byId(var3.rite);
            int var6 = var5 == null ? 12615935 : var5.colour();
            double var7 = (double)var1.getX() + 0.5;
            double var9 = (double)var1.getY() + 0.8;
            double var11 = (double)var1.getZ() + 0.5;
            float var13 = 1.0F - (float)var3.burning / 280.0F;
            double var14 = 4.0 * (1.0 - (double)var13) + 0.4;

            for (int var16 = 0; var16 < 3; var16++) {
                double var17 = var4.random.nextDouble() * Math.PI * 2.0;
                Cataclysms.embers(var4, var6, 0.8F, var7 + Math.cos(var17) * var14, var9, var11 + Math.sin(var17) * var14, 1, 0.02, 0.02, 0.02, 0.01);
            }

            if (var3.burning % 8 == 0) {
                Cataclysms.ring(var4, var6, (float)(4.5 * (1.0 - (double)var13) + 0.8), var7, var9 - 0.7, var11, 0.35);
            }

            Cataclysms.runes(var4, var6, 0.9F + var13, var7, var9 + (double)var13 * 3.0, var11, 2, 0.15, 0.3, 0.15);
            int var27 = reach(DarkRites.byId(var3.rite) == null ? "meteor" : var3.rite);
            if (var27 > 0 && var3.burning % 4 == 0) {
                Vec3 var28 = var3.aim.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : var3.aim.normalize();

                for (int var18 = 1; var18 <= 7; var18++) {
                    double var19 = (double)var27 * ((double)var18 / 7.0);
                    double var21 = var7 + var28.x * var19;
                    double var23 = var11 + var28.z * var19;
                    if (!(var4.random.nextFloat() > 0.25F + var13 * 0.75F)) {
                        double var25 = (double)var4.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(var21), (int)Math.floor(var23)) + 0.4;
                        Cataclysms.runes(var4, var6, 1.1F, var21, var25, var23, 1, 0.05, 0.05, 0.05);
                        if (var18 == 7) {
                            Cataclysms.ring(var4, var6, 3.0F, var21, var25, var23, 0.4);
                        }
                    }
                }
            }

            if (var3.burning % 20 == 0) {
                var4.playSound(null, var1, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.5F + var13);
            }

            if (var3.burning <= 0) {
                var3.laid.clear();
                var3.burning = 0;
                var4.setBlock(var1, (BlockState)var2.setValue(RiteStone.LIT, false), 3);
                var3.rite = "";
                var3.sync();
                var4.playSound(null, var1, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0F, 0.6F);
                Cataclysms.puff(var4, ParticleTypes.FLASH, var7, var9 + 1.0, var11, 4, 0.3, 0.3, 0.3, 0.0);
                call(var4, var1, var5, var3.aim);
            }
        }
    }

    private static int reach(String var0) {
        return switch (var0) {
            case "volcano" -> 96;
            case "meteor" -> 58;
            case "tornado" -> 44;
            case "quake" -> 36;
            default -> 0;
        };
    }

    private static void call(ServerLevel var0, BlockPos var1, DarkRites.Rite var2, Vec3 var3) {
        String var4 = var2 == null ? "meteor" : var2.id();
        Vec3 var5 = var3.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : var3.normalize();
        int var6 = reach(var4);
        double var7 = (double)var1.getX() + 0.5 + var5.x * (double)var6;
        double var9 = (double)var1.getZ() + 0.5 + var5.z * (double)var6;
        BlockPos var11 = var6 == 0 ? var1 : Cataclysms.surface(var0, var7, var9);
        Vec3 var12 = new Vec3((double)var11.getX() + 0.5, (double)var11.getY(), (double)var11.getZ() + 0.5);
        WakingWorld.LOGGER
            .info(
                "mage: an asking stone at {} {} {} calls {} at {} {} {} ({} blocks off)",
                new Object[]{var1.getX(), var1.getY(), var1.getZ(), var4, var11.getX(), var11.getY(), var11.getZ(), var6}
            );
        switch (var4) {
            case "volcano":
                Volcano.force(var0, var11, 0, 0);
                break;
            case "tornado":
                TornadoEntity.spawn(var0, var12, 0);
                break;
            case "quake":
                Weather.forceQuake(var0, var12);
                break;
            case "bloodmoon":
                BloodMoon.force(var0, true);
                break;
            default:
                Cataclysms.fall(var0, var12, 2, true);
        }

        for (ServerPlayer var15 : var0.getPlayers(
            var1x -> var1x.distanceToSqr((double)var1.getX() + 0.5, (double)var1.getY(), (double)var1.getZ() + 0.5) < 25600.0
        )) {
            var15.sendSystemMessage(Component.translatable("rite.wakingworld.answered").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private void sync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public CompoundTag getUpdateTag(Provider var1) {
        return this.saveWithoutMetadata(var1);
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected void loadAdditional(CompoundTag var1, Provider var2) {
        super.loadAdditional(var1, var2);
        this.rite = var1.getString("Rite");
        this.burning = var1.getInt("Burning");
        this.aim = new Vec3(var1.getDouble("AimX"), 0.0, var1.getDouble("AimZ"));
        this.laid.clear();
        ListTag var3 = var1.getList("Laid", 10);

        for (int var4 = 0; var4 < var3.size(); var4++) {
            ItemStack.parse(var2, var3.getCompound(var4)).ifPresent(this.laid::add);
        }
    }

    protected void saveAdditional(CompoundTag var1, Provider var2) {
        super.saveAdditional(var1, var2);
        var1.putString("Rite", this.rite);
        var1.putInt("Burning", this.burning);
        var1.putDouble("AimX", this.aim.x);
        var1.putDouble("AimZ", this.aim.z);
        ListTag var3 = new ListTag();

        for (ItemStack var5 : this.laid) {
            var3.add(var5.save(var2));
        }

        var1.put("Laid", var3);
    }
}
