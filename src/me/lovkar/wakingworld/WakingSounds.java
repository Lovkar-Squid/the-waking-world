package me.lovkar.wakingworld;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;

/**
 * The music - one battle theme per kind of colossus (looping, streamed), a short awakening piece
 * cut so its last hit lands the moment the giant is out of the ground, and a victory piece for
 * the collapse; composed by Lovkar (generated), cut and loop-matched in tools/music - the Horn's
 * voice, and the creatures' own sounds, synthesized in tools/sfx.
 */
public final class WakingSounds {
    private WakingSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WakingWorld.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_STONE = music("stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EARTH = music("earth");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SANDSTONE = music("sandstone");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ICE = music("ice");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_PRISMARINE = music("prismarine");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MOSS = music("moss");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_AWAKENING = music("awakening");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VICTORY = music("victory");
    // the Titan has its own three: only used when the client actually has the files (see BossMusic)
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_TITAN = music("titan");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_TITAN_AWAKENING = music("titan_awakening");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_TITAN_VICTORY = music("titan_victory");

    /** The Horn of Waking's own voice, and the answer from under the altar when the rite begins. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HORN_BLOW = sound("item.horn_of_waking.blow");
    public static final DeferredHolder<SoundEvent, SoundEvent> HORN_ANSWER = sound("item.horn_of_waking.answer");

    // ---- the creatures' own voices (tools/sfx/mobs.py) ----
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_WRAITH_AMBIENT = sound("entity.ember_wraith.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_WRAITH_HURT = sound("entity.ember_wraith.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_WRAITH_DEATH = sound("entity.ember_wraith.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_WRAITH_STEP = sound("entity.ember_wraith.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_WRAITH_FLARE = sound("entity.ember_wraith.flare");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNE_SENTINEL_AMBIENT = sound("entity.rune_sentinel.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNE_SENTINEL_HURT = sound("entity.rune_sentinel.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNE_SENTINEL_DEATH = sound("entity.rune_sentinel.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNE_SENTINEL_STEP = sound("entity.rune_sentinel.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNE_SENTINEL_SHOOT = sound("entity.rune_sentinel.shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_AMBIENT = sound("entity.drowned_keeper.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_AMBIENT_WATER = sound("entity.drowned_keeper.ambient_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_HURT = sound("entity.drowned_keeper.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_DEATH = sound("entity.drowned_keeper.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_STEP = sound("entity.drowned_keeper.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_SWIM = sound("entity.drowned_keeper.swim");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_KEEPER_ATTACK = sound("entity.drowned_keeper.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_THRALL_AMBIENT = sound("entity.stone_thrall.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_THRALL_HURT = sound("entity.stone_thrall.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_THRALL_DEATH = sound("entity.stone_thrall.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_THRALL_STEP = sound("entity.stone_thrall.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_THRALL_AMBIENT = sound("entity.stone_thrall.hollow_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_THRALL_HURT = sound("entity.stone_thrall.hollow_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_THRALL_DEATH = sound("entity.stone_thrall.hollow_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_HURT = sound("entity.guard.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_DEATH = sound("entity.guard.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_ATTACK = sound("entity.guard.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_SHOOT = sound("entity.guard.shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_STEP = sound("entity.guard.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWNSFOLK_AMBIENT = sound("entity.townsfolk.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWNSFOLK_HURT = sound("entity.townsfolk.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWNSFOLK_DEATH = sound("entity.townsfolk.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWNSFOLK_YES = sound("entity.townsfolk.yes");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWNSFOLK_NO = sound("entity.townsfolk.no");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWNSFOLK_GREET = sound("entity.townsfolk.greet");
    public static final DeferredHolder<SoundEvent, SoundEvent> KING_AMBIENT = sound("entity.king.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> KING_HURT = sound("entity.king.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> KING_DEATH = sound("entity.king.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> KING_GREET = sound("entity.king.greet");
    public static final DeferredHolder<SoundEvent, SoundEvent> KING_ANGRY = sound("entity.king.angry");
    // the Titan's Gate: the frame rising out of the arena floor, the sheet of void closing, and its breathing
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_OPEN = sound("block.titan_gate.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_CLOSE = sound("block.titan_gate.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_AMBIENT = sound("block.titan_gate.ambient");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_KIND = Map.of(
            "stone", MUSIC_STONE, "earth", MUSIC_EARTH, "sandstone", MUSIC_SANDSTONE,
            "ice", MUSIC_ICE, "prismarine", MUSIC_PRISMARINE, "moss", MUSIC_MOSS, "titan", MUSIC_TITAN);

    /** The battle theme for a palette kind (stone for anything unknown). */
    public static SoundEvent battleTheme(String kind) {
        return BY_KIND.getOrDefault(kind, MUSIC_STONE).get();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> music(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "music.colossus." + name);
        return SOUNDS.register("music.colossus." + name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
