package me.lovkar.wakingworld.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.lovkar.wakingworld.particle.WakingParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A ring lying flat on the ground that widens from nothing to its full radius (the size hint,
 * in blocks) and fades as it goes. Drawn as one horizontal quad, so it reads from any angle.
 * The velocity's y is used as the speed of the spread (blocks/tick), default 0.6.
 */
public class RingParticle extends TextureSheetParticle {
    private final float radius;
    private final float speed;

    RingParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, ColorParticleOption color, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.xd = this.yd = this.zd = 0;
        this.rCol = color.getRed();
        this.gCol = color.getGreen();
        this.bCol = color.getBlue();
        this.radius = 3f * WakingParticles.scaleOf(color);
        this.speed = vy > 0.01 ? (float) vy : 0.6f;
        this.lifetime = Math.max(8, (int) (radius / speed) + 6);
        this.gravity = 0f;
        this.hasPhysics = false;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) this.remove();
        float t = this.age / (float) this.lifetime;
        this.alpha = 0.85f * (1f - t * t);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
        float cy = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y) + 0.06f;
        float cz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
        float r = Math.min(radius, (this.age + partialTick) * speed);
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = LightTexture.FULL_BRIGHT;
        // two triangles' worth of a quad, both windings so it shows from below as well
        buffer.addVertex(cx - r, cy, cz - r).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx - r, cy, cz + r).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx + r, cy, cz + r).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx + r, cy, cz - r).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx + r, cy, cz - r).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx + r, cy, cz + r).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx - r, cy, cz + r).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(cx - r, cy, cz - r).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ColorParticleOption> {
        @Override
        public RingParticle createParticle(ColorParticleOption type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new RingParticle(level, x, y, z, vx, vy, vz, type, sprites);
        }
    }
}
