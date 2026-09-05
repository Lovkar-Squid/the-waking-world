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

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
