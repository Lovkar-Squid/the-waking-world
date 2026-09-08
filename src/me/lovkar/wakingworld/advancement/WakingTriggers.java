package me.lovkar.wakingworld.advancement;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's advancement criteria. */
public final class WakingTriggers {
    private WakingTriggers() {
    }

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, WakingWorld.MODID);

    /** A colossus rose (the player was near: within its music range) - {"kind": ...}. */
    public static final DeferredHolder<CriterionTrigger<?>, KindTrigger> COLOSSUS_WOKEN = TRIGGERS.register("colossus_woken", KindTrigger::new);
    /** A colossus fell to the player (they dealt it damage in the fight) - {"kind": ..., "min_height": ...}. */
    public static final DeferredHolder<CriterionTrigger<?>, KindTrigger> COLOSSUS_SLAIN = TRIGGERS.register("colossus_slain", KindTrigger::new);
    /** A core broke to the player's hit. */
    public static final DeferredHolder<CriterionTrigger<?>, KindTrigger> CORE_BROKEN = TRIGGERS.register("core_broken", KindTrigger::new);
    /** A hammer dive landed from this many blocks - {"min": ...}. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> DIVE_SLAM = TRIGGERS.register("dive_slam", ValueTrigger::new);
    /** An Hourglass turned over: this many blocks came back - {"min": ...}. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> LAND_RESTORED = TRIGGERS.register("land_restored", ValueTrigger::new);
    /** A Heart of the End eaten: hearts gained so far - {"min": ...}. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> HEART_EATEN = TRIGGERS.register("heart_eaten", ValueTrigger::new);
    /** Thrown by a colossus and lived: {"min": distance}. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> THROWN = TRIGGERS.register("thrown", ValueTrigger::new);

    /** A rite was begun at an altar - {"kind": ...}. */
    public static final DeferredHolder<CriterionTrigger<?>, KindTrigger> RITE = TRIGGERS.register("rite", KindTrigger::new);

    // ---- 0.2, the cataclysms ---------------------------------------------------------------

    /**
     * A cataclysm ended with the player still standing - {"kind": "volcano"|"tornado"|"earthquake"
     * |"meteor"|"bloodmoon"}.
     *
     * <p>Five separate criteria on one advancement is how "live through all five" is remembered:
     * vanilla keeps each criterion for ever once it is met, so nothing here has to store which of
     * them a player has seen.</p>
     */
    public static final DeferredHolder<CriterionTrigger<?>, KindTrigger> SURVIVED = TRIGGERS.register("survived", KindTrigger::new);
    /** A blow from the sky landed on a full suit of star iron - {"min": the damage it took away}. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> SHELTERED = TRIGGERS.register("sheltered", ValueTrigger::new);
    /** Walked into a named land - {"min": how many they have walked into now}. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> LAND_WALKED = TRIGGERS.register("land_walked", ValueTrigger::new);
    /** Stood inside a tornado and lived - {"min": ...} is seconds spent in it, not blocks. */
    public static final DeferredHolder<CriterionTrigger<?>, ValueTrigger> IN_THE_EYE = TRIGGERS.register("in_the_eye", ValueTrigger::new);

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
