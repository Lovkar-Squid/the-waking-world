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

/** What the Asking Stone is holding, what it still wants, and the moment it is paid in full. */
public class RiteStoneEntity extends BlockEntity {
    /** How long the stone burns before the thing arrives: long enough to run, short enough to watch. */
    private static final int BURN = 14 * 20;

    private String rite = "";
    private final List<ItemStack> laid = new ArrayList<>();
    private int burning;

    public RiteStoneEntity(BlockPos pos, BlockState state) {
        super(MageBlocks.RITE_STONE_ENTITY.get(), pos, state);
    }

    public String rite() {
        return rite;
    }

    public boolean burning() {
        return burning > 0;
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

    // ---- using it ----------------------------------------------------------------------------

    /** Turn it to the next of the five, and say what that one costs. */
    public void turn(Player player) {
        if (burning()) {
            player.displayClientMessage(Component.translatable("rite.wakingworld.already").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!laid.isEmpty()) {
            player.displayClientMessage(Component.translatable("rite.wakingworld.laid_already").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        rite = DarkRites.byIndex(rite.isEmpty() ? 0 : DarkRites.indexOf(rite) + 1).id();
        setChanged();
        DarkRites.Rite r = DarkRites.byId(rite);
        player.displayClientMessage(Component.translatable("rite.wakingworld.turned",
                Component.translatable(r.nameKey()), describe(r.costs())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F);
        }
    }

    /** Lay one of the things it is owed. */
    public boolean offer(Player player, InteractionHand hand, ItemStack stack) {
        if (burning()) return false;
        if (rite.isEmpty()) {
            player.displayClientMessage(Component.translatable("rite.wakingworld.unset").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        List<DarkRites.Cost> want = missing();
        DarkRites.Cost taking = null;
        for (DarkRites.Cost c : want) if (stack.is(c.item())) taking = c;
        if (taking == null) {
            player.displayClientMessage(Component.translatable("rite.wakingworld.wants", describe(want)).withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        int n = Math.min(taking.count(), stack.getCount());
        laid.add(new ItemStack(stack.getItem(), n));
        if (!player.isCreative()) stack.shrink(n);
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
            if (level instanceof ServerLevel server) {
                Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5, 14, 0.3, 0.2, 0.3, 0.02);
            }
        }
        List<DarkRites.Cost> left = missing();
        if (left.isEmpty()) light(player);
        else player.displayClientMessage(Component.translatable("rite.wakingworld.wants", describe(left)).withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    /** Everything back, and the stone forgets what it was asked. */
    public void giveBack(Player player) {
        if (burning()) {
            player.displayClientMessage(Component.translatable("rite.wakingworld.already").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (laid.isEmpty()) {
            rite = "";
            setChanged();
            player.displayClientMessage(Component.translatable("rite.wakingworld.cleared").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        for (ItemStack s : laid) if (!player.addItem(s.copy())) player.drop(s.copy(), false);
        laid.clear();
        setChanged();
        player.displayClientMessage(Component.translatable("rite.wakingworld.taken_back").withStyle(ChatFormatting.GRAY), true);
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

    // ---- the asking --------------------------------------------------------------------------

    private void light(Player player) {
        burning = BURN;
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(RiteStone.LIT, true), 3);
            level.playSound(null, worldPosition, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 4.0F, 0.6F);
        }
        DarkRites.Rite r = DarkRites.byId(rite);
        player.displayClientMessage(Component.translatable("rite.wakingworld.lit",
                Component.translatable(r == null ? "cataclysm.wakingworld.name.meteor" : r.nameKey()))
                .withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, RiteStoneEntity stone) {
        if (stone.burning <= 0 || !(level instanceof ServerLevel server)) return;
        stone.burning--;
        DarkRites.Rite r = DarkRites.byId(stone.rite);
        int colour = r == null ? 0xC080FF : r.colour();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.8, cz = pos.getZ() + 0.5;

        // it winds up: a ring that closes, a column that climbs, and the last two seconds are loud
        float t = 1f - stone.burning / (float) BURN;
        double ring = 4.0 * (1.0 - t) + 0.4;
        for (int i = 0; i < 3; i++) {
            double a = server.random.nextDouble() * Math.PI * 2;
            Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, cx + Math.cos(a) * ring, cy, cz + Math.sin(a) * ring, 1, 0.02, 0.02, 0.02, 0.01);
        }
        Cataclysms.puff(server, ParticleTypes.SOUL_FIRE_FLAME, cx, cy + t * 3.0, cz, 2, 0.15, 0.3, 0.15, 0.02);
        if (stone.burning % 20 == 0) {
            server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.5F + t);
        }
        if (stone.burning > 0) return;

        stone.laid.clear();
        stone.burning = 0;
        server.setBlock(pos, state.setValue(RiteStone.LIT, false), 3);
        stone.rite = "";
        stone.setChanged();
        server.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0F, 0.6F);
        Cataclysms.puff(server, ParticleTypes.FLASH, cx, cy + 1.0, cz, 4, 0.3, 0.3, 0.3, 0);
        call(server, pos, r);
    }

    /** The thing itself, here rather than wherever the world felt like putting it. */
    private static void call(ServerLevel server, BlockPos pos, DarkRites.Rite r) {
        String id = r == null ? "meteor" : r.id();
        Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        WakingWorld.LOGGER.info("mage: an asking stone at {} {} {} calls {}", pos.getX(), pos.getY(), pos.getZ(), id);
        switch (id) {
            case "volcano" -> Volcano.force(server, Cataclysms.surface(server, at.x, at.z), 0, 0);
            case "tornado" -> TornadoEntity.spawn(server, at, 0);
            case "quake" -> Weather.forceQuake(server, at);
            case "bloodmoon" -> BloodMoon.force(server, true);
            default -> Cataclysms.force(server);
        }
        for (ServerPlayer p : server.getPlayers(pl -> pl.distanceToSqr(at.x, at.y, at.z) < 160 * 160)) {
            p.sendSystemMessage(Component.translatable("rite.wakingworld.answered").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    // ---- saved -------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rite = tag.getString("Rite");
        burning = tag.getInt("Burning");
        laid.clear();
        net.minecraft.nbt.ListTag list = tag.getList("Laid", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(laid::add);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Rite", rite);
        tag.putInt("Burning", burning);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack s : laid) list.add(s.save(registries));
        tag.put("Laid", list);
    }
}
