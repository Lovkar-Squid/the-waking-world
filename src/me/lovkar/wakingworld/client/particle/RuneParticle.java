package me.lovkar.wakingworld.client.particle;

import me.lovkar.wakingworld.particle.WakingParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;

/** A glowing rune glyph: one of eight shapes, drifts with its velocity, flickers, fades out. Full-bright. */
public class RuneParticle extends TextureSheetParticle {
    private final float baseSize;

    RuneParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, ColorParticleOption color, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = color.getRed();
        this.gCol = color.getGreen();
        this.bCol = color.getBlue();
        this.baseSize = 0.28f * WakingParticles.scaleOf(color);
        this.quadSize = baseSize;
        this.lifetime = 30 + this.random.nextInt(25);
        this.gravity = 0f;
        this.friction = 0.96f;
        this.hasPhysics = false;
        this.pickSprite(sprites);
        this.roll = this.random.nextFloat() * 0.4f - 0.2f;
    }

    @Override
    public void tick() {
        super.tick();
        float t = this.age / (float) this.lifetime;
        this.alpha = t < 0.15f ? t / 0.15f : t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;
        // a flicker, like a candle behind the glyph
        this.quadSize = baseSize * (0.85f + 0.15f * Mth.sin(this.age * 0.9f + this.roll * 40f));
        this.oRoll = this.roll;
        this.roll += 0.02f;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ColorParticleOption> {
        @Override
        public RuneParticle createParticle(ColorParticleOption type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new RuneParticle(level, x, y, z, vx, vy, vz, type, sprites);
        }
    }
}
