package me.lovkar.wakingworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What a blood moon looks like from inside it.
 *
 * <p>No shader and no resource pack: the fog colour is pulled towards a deep red, which carries the
 * horizon, the water and the distance with it, and the sky colour follows. It fades in over a few
 * seconds when the night turns and back out at dawn, so it never snaps.</p>
 */
public final class RedSky {
    private static boolean on;
    private static float blend;              // 0 = an ordinary night, 1 = fully red

    private RedSky() {
    }

    public static void set(boolean red) {
        on = red;
    }

    /** True while there is any of it on screen - other client code can ask. */
    public static boolean showing() {
        return blend > 0.01F;
    }

    public static void clientTick(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide()) return;
        float want = on && Minecraft.getInstance().level != null ? 1.0F : 0.0F;
        blend = Mth.approach(blend, want, 0.008F);          // ~4 seconds either way
    }

    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (blend <= 0.01F) return;
        float k = blend * 0.75F;
        event.setRed(Mth.lerp(k, event.getRed(), 0.42F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.045F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.055F));
    }
}
