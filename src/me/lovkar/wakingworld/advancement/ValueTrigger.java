package me.lovkar.wakingworld.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** A criterion with one number: {@code {"min": 15}} - a dive from at least that height, a restoration of at least that many blocks. */
public class ValueTrigger extends SimpleCriterionTrigger<ValueTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, double value) {
        this.trigger(player, i -> i.matches(value));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<Double> min) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.DOUBLE.optionalFieldOf("min").forGetter(Instance::min)
        ).apply(i, Instance::new));

        public boolean matches(double v) {
            return min.isEmpty() || v >= min.get();
        }
    }
}
