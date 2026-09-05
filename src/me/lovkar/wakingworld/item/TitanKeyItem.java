package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import me.lovkar.wakingworld.worldgen.TitanArenaPiece;
import me.lovkar.wakingworld.worldgen.WakingStructures;

import java.util.List;

/**
 * Six Sigils and a Heart, forged into one. In the End it pulls towards the nearest Titan Arena;
 * laid on the arena's altar and the horn sounded over it, the Titan rises - the biggest and
 * oldest of them, built of obsidian and end stone, with every other kind's moves. The Titan
 * gives the Key back when it falls.
 */
public class TitanKeyItem extends Item {

    public TitanKeyItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.dimension() != Level.END) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("item.wakingworld.titan_key.not_here").withStyle(ChatFormatting.DARK_PURPLE), true);
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.5F);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.sidedSuccess(stack, true);

        // inside an arena? then it belongs on the altar
        StructureStart start = server.structureManager().getStructureWithPieceAt(player.blockPosition(), WakingStructures.TITAN_ARENA_TAG);
        if (start != null && start.isValid()) {
            player.getCooldowns().addCooldown(this, 40);
            player.displayClientMessage(Component.translatable("item.wakingworld.titan_key.here").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.8F);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        // not here: the Key pulls towards the nearest arena
        player.getCooldowns().addCooldown(this, 40);
        BlockPos nearest = server.findNearestMapStructure(WakingStructures.TITAN_ARENA_TAG, player.blockPosition(), 40, false); // placement cells, not chunks
        if (nearest == null) {
            player.displayClientMessage(Component.translatable("item.wakingworld.titan_key.no_arena").withStyle(ChatFormatting.DARK_PURPLE), true);
        } else {
            double dx = nearest.getX() - player.getX(), dz = nearest.getZ() - player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            player.displayClientMessage(Component.translatable("item.wakingworld.titan_key.pulls", dist, compass(dx, dz)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            Vec3 dir = new Vec3(dx, 0, dz).normalize();
            for (int i = 1; i <= 12; i++) {
                Vec3 at = player.getEyePosition().add(dir.scale(i * 0.8));
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y - 0.3, at.z, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.6F);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static Component compass(double dx, double dz) {
        // Minecraft: south = +z, east = +x
        double a = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        if (a < 0) a += 360;
        String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        int i = (int) Math.round(a / 45.0) % 8;
        return Component.translatable("item.wakingworld.titan_key.dir." + names[i]);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wakingworld.titan_key.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wakingworld.titan_key.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
