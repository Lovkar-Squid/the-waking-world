package me.lovkar.wakingworld.client.particle;

import me.lovkar.wakingworld.particle.WakingParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ColorParticleOption;

/** A bright spark: flies with its velocity, a little gravity, shrinks and fades over a second or two. Full-bright. */
public class EmberParticle extends TextureSheetParticle {
    private final float baseSize;

    EmberParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, ColorParticleOption color, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = color.getRed();
        this.gCol = color.getGreen();
        this.bCol = color.getBlue();
        this.baseSize = 0.12f * WakingParticles.scaleOf(color);
        this.quadSize = baseSize;
        this.lifetime = 25 + this.random.nextInt(30);
        this.gravity = 0.06f;
        this.friction = 0.97f;
        this.hasPhysics = true;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = this.age / (float) this.lifetime;
        this.alpha = 1f - t * t;
        this.quadSize = baseSize * (1f - 0.6f * t);
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
        public EmberParticle createParticle(ColorParticleOption type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new EmberParticle(level, x, y, z, vx, vy, vz, type, sprites);
        }
    }
}
