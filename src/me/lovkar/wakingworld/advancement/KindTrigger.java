package me.lovkar.wakingworld.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * A criterion about a kind of colossus: {@code {"kind": "ice"}} matches only ice colossi, no kind
 * matches any. Used for waking one and for slaying one (two registered instances).
 */
public class KindTrigger extends SimpleCriterionTrigger<KindTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String kind, int height) {
        this.trigger(player, i -> i.matches(kind, height));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> kind, Optional<Integer> minHeight) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.optionalFieldOf("kind").forGetter(Instance::kind),
                Codec.INT.optionalFieldOf("min_height").forGetter(Instance::minHeight)
        ).apply(i, Instance::new));

        public boolean matches(String k, int height) {
            return (kind.isEmpty() || kind.get().equals(k)) && (minHeight.isEmpty() || height >= minHeight.get());
        }
    }
}
