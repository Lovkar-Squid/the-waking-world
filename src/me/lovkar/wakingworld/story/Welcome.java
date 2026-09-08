package me.lovkar.wakingworld.story;

import me.lovkar.wakingworld.WakingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The first minute.
 *
 * <p>Everything this mod does happens to a player rather than being switched on by one, and the
 * first of it can be days away: a shrine has to be walked into, a cataclysm has to be rolled for.
 * A player who installs it, plays an evening and sees nothing does not think "what a patient
 * mod" - they think it is broken, and they are being reasonable.</p>
 *
 * <p>So it says, once, what it is and where to read the rest. Three lines, no tutorial, nothing
 * that has to be dismissed. The Almanac itself arrives on its own from the root advancement; this
 * is only what tells somebody it is worth opening.</p>
 */
public final class Welcome extends SavedData {
    public static final String NAME = "wakingworld_welcome";
    private static final Factory<Welcome> FACTORY = new Factory<>(Welcome::new, Welcome::load, null);

    /** Who has already been told. Saved with the world, so it is said once and not once a session. */
    private final Set<UUID> greeted = new HashSet<>();

    private Welcome() {
    }

    public static Welcome get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!WakingConfig.greeting()) return;
        ServerLevel home = player.server.getLevel(Level.OVERWORLD);
        if (home == null) return;
        Welcome book = get(home);
        if (!book.greeted.add(player.getUUID())) return;
        book.setDirty();

        // a beat after the join, or it is the first thing on screen and lands under the world's
        // own "loading terrain" and whatever else a modpack has to say for itself
        player.server.execute(() -> {
            if (!player.isAlive()) return;
            player.sendSystemMessage(Component.empty());
            player.sendSystemMessage(Component.translatable("welcome.wakingworld.1")
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("welcome.wakingworld.2")
                    .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.translatable("welcome.wakingworld.3")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        });
    }

    private static Welcome load(CompoundTag tag, HolderLookup.Provider registries) {
        Welcome w = new Welcome();
        ListTag list = tag.getList("Greeted", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) w.greeted.add(net.minecraft.nbt.NbtUtils.loadUUID(list.get(i)));
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID id : greeted) list.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        tag.put("Greeted", list);
        return tag;
    }
}
