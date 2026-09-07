package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.cataclysm.TornadoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The column, drawn as what is caught in it.
 *
 * <p>Two things, drawn together. The first is the debris: a few dozen small blocks on a helix that
 * widens as it climbs, spinning fast, each tumbling on its own. The helix is generated once per
 * entity from its id, so it is the same column for everybody.</p>
 *
 * <p>The second is the funnel itself, and it was missing for three takes of the trailer. Debris
 * alone reads as litter blowing about - what makes a tornado a tornado is a dark BODY standing on
 * the ground, and against a bright sky that body is the only thing that reads at two hundred
 * blocks. So there is a lathe here now: rings of quads wrapped in a sheet of dust streaks
 * (tools/textures/funnel.py), dark, half transparent and full of holes, turning at the same rate
 * as the debris, with a second skin inside it turning the other way so the column has depth.</p>
 */
public class TornadoRenderer extends EntityRenderer<TornadoEntity> {
    private record Mote(double angle, double height, double out, float spin, BlockState state) {
    }

    private static final BlockState[] DEBRIS = {
            Blocks.DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(), Blocks.SAND.defaultBlockState(),
            Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
            Blocks.STONE.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
    };

    /** The dust the funnel is wrapped in. Tiles twice around the column and three times up it. */
    private static final ResourceLocation FUNNEL = ResourceLocation.fromNamespaceAndPath(
            me.lovkar.wakingworld.WakingWorld.MODID, "textures/entity/tornado_funnel.png");
    private static final int SEGMENTS = 24;
    private static final int RINGS = 14;
    /** How tall the column is at full strength, which is what the helix above is built against. */
    private static final double HEIGHT = 30.0;

    private final BlockRenderDispatcher dispatcher;
    private final java.util.Map<Integer, List<Mote>> columns = new java.util.HashMap<>();

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TornadoEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(TornadoEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        return true;                      // thirty blocks tall: its box is nearly always off screen somewhere
    }

    private List<Mote> column(TornadoEntity entity) {
        return columns.computeIfAbsent(entity.getId(), key -> {
            RandomSource rnd = RandomSource.create(key * 7919L);
            List<Mote> out = new ArrayList<>();
            for (int i = 0; i < 90; i++) {
                double h = Math.pow(rnd.nextDouble(), 0.7) * 30.0;             // packed toward the ground
                double widen = 0.30 + 0.75 * (h / 30.0);
                out.add(new Mote(
                        rnd.nextDouble() * Math.PI * 2,
                        h,
                        widen * (0.55 + rnd.nextDouble() * 0.65),
                        rnd.nextFloat() * 360F,
                        DEBRIS[rnd.nextInt(DEBRIS.length)]));
            }
            return out;
        });
    }

    /** The funnel's radius a fraction of the way up it: tight at the ground, flaring near the top. */
    private static double flare(double f) {
        return 0.26 + 0.80 * Math.pow(f, 1.15);
    }

    /**
     * One skin of the funnel, as a lathe of quads.
     *
     * @param scale  how far out this skin sits (1.0 is the outside, less is the core inside it)
     * @param turn   radians per tick, negative for the inner skin so the two shear against each other
     * @param dark   0..1, how much darker this skin is than the dust texture
     * @param alpha  the skin's opacity at the ground, falling away towards the top
     */
    private void skin(PoseStack poseStack, VertexConsumer vc, int packedLight,
                      double r, float s, float t, double scale, double turn, float dark, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        double top = HEIGHT * s;
        for (int ring = 0; ring < RINGS; ring++) {
            double f0 = ring / (double) RINGS, f1 = (ring + 1) / (double) RINGS;
            double y0 = f0 * top, y1 = f1 * top;
            double r0 = flare(f0) * r * s * scale, r1 = flare(f1) * r * s * scale;
            // the dust thins out with height, and the last ring fades away entirely into the sky
            float a0 = alpha * (1.0F - 0.62F * (float) f0) * (1.0F - (float) Math.pow(f0, 6));
            float a1 = alpha * (1.0F - 0.62F * (float) f1) * (1.0F - (float) Math.pow(f1, 6));
            // the foot turns fastest, the same way the debris does
            double spin0 = t * turn * (1.0 - 0.45 * f0), spin1 = t * turn * (1.0 - 0.45 * f1);
            float v0 = (float) (f0 * 3.0 - t * 0.012), v1 = (float) (f1 * 3.0 - t * 0.012);
            for (int seg = 0; seg < SEGMENTS; seg++) {
                double a = seg / (double) SEGMENTS * Math.PI * 2, b = (seg + 1) / (double) SEGMENTS * Math.PI * 2;
                float u0 = (float) (seg / (double) SEGMENTS * 2.0), u1 = (float) ((seg + 1) / (double) SEGMENTS * 2.0);
                float[][] q = {
                        {(float) (Math.cos(a + spin0) * r0), (float) y0, (float) (Math.sin(a + spin0) * r0), u0, v0, a0},
                        {(float) (Math.cos(b + spin0) * r0), (float) y0, (float) (Math.sin(b + spin0) * r0), u1, v0, a0},
                        {(float) (Math.cos(b + spin1) * r1), (float) y1, (float) (Math.sin(b + spin1) * r1), u1, v1, a1},
                        {(float) (Math.cos(a + spin1) * r1), (float) y1, (float) (Math.sin(a + spin1) * r1), u0, v1, a1},
                };
                // both windings: the far wall of the funnel has to show through the near one
                emit(vc, pose, packedLight, dark, q, false);
                emit(vc, pose, packedLight, dark, q, true);
            }
        }
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, int packedLight, float dark,
                             float[][] q, boolean reversed) {
        for (int i = 0; i < 4; i++) {
            float[] v = q[reversed ? 3 - i : i];
            int grey = (int) (255 * dark);
            vc.addVertex(pose, v[0], v[1], v[2])
                    .setColor(grey, (int) (grey * 0.95F), (int) (grey * 0.88F), (int) (255 * v[5]))
                    .setUv(v[3], v[4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    @Override
    public void render(TornadoEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float s = entity.strength();
        if (s <= 0.02F) return;
        float t = entity.tickCount + partialTick;
        double r = entity.radius();

        // the body first, so the debris is drawn over it
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(FUNNEL));
        skin(poseStack, vc, packedLight, r, s, t, 1.00, 0.052, 0.30F, 0.86F);
        skin(poseStack, vc, packedLight, r, s, t, 0.66, -0.038, 0.17F, 0.78F);

        for (Mote m : column(entity)) {
            double a = m.angle() + t * (0.24 - 0.11 * (m.height() / 30.0));    // the foot turns fastest
            double out = m.out() * r * s;
            double x = Math.cos(a) * out;
            double z = Math.sin(a) * out;
            double y = m.height() * s;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            float size = (float) Mth.lerp(m.height() / 30.0, 0.85, 0.45);
            poseStack.scale(size, size, size);
            poseStack.mulPose(Axis.YP.rotationDegrees(m.spin() + t * 11F));
            poseStack.mulPose(Axis.XP.rotationDegrees(m.spin() * 1.7F + t * 8F));
            poseStack.translate(-0.5, -0.5, -0.5);
            dispatcher.renderSingleBlock(m.state(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
