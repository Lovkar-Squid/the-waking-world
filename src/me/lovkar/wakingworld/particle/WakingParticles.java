package me.lovkar.wakingworld.particle;

import com.mojang.serialization.MapCodec;
import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own particles, all coloured through vanilla's ColorParticleOption (ARGB; the alpha
 * byte doubles as a size hint, 0 = default):
 * <ul>
 * <li>{@code rune} - a glowing glyph (eight shapes) that drifts up, flickers and fades;</li>
 * <li>{@code ring} - a flat ring lying on the ground that widens and fades: stomps, landings, rites;</li>
 * <li>{@code ember} - a bright spark with a little gravity and a long fade, for cores and altars.</li>
 * </ul>
 */
public final class WakingParticles {
    private WakingParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, WakingWorld.MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> RUNE = PARTICLES.register("rune", () -> colored(false));
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> RING = PARTICLES.register("ring", () -> colored(true));
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> EMBER = PARTICLES.register("ember", () -> colored(false));

    private static ParticleType<ColorParticleOption> colored(boolean overrideLimiter) {
        return new ParticleType<ColorParticleOption>(overrideLimiter) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }

    /** rgb + a size hint in the alpha byte (1..255 -> scale 0.1..10; 0 = default). */
    public static ColorParticleOption rune(int rgb, float scale) {
        return ColorParticleOption.create(RUNE.get(), pack(rgb, scale));
    }

    public static ColorParticleOption ring(int rgb, float scale) {
        return ColorParticleOption.create(RING.get(), pack(rgb, scale));
    }

    public static ColorParticleOption ember(int rgb, float scale) {
        return ColorParticleOption.create(EMBER.get(), pack(rgb, scale));
    }

    private static int pack(int rgb, float scale) {
        int a = scale <= 0 ? 0 : Math.max(1, Math.min(255, Math.round(scale * 25f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** The size hint back out of an option (1 when none was given). */
    public static float scaleOf(ColorParticleOption o) {
        float a = o.getAlpha();
        return a <= 0f ? 1f : a * 255f / 25f;
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
