package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.cataclysm.TornadoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * <p>There is no funnel model: a tornado is only visible because of the dirt in it, so that is what
 * is drawn - a few dozen small blocks arranged on a helix that widens as it climbs, the whole thing
 * spinning fast and each block tumbling on its own. The helix is generated once per entity from its
 * id, so it is the same column for everybody, and it is scaled by the tornado's own strength, so it
 * grows as the thing spins up and thins out as it dies.</p>
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

    @Override
    public void render(TornadoEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float s = entity.strength();
        if (s <= 0.02F) return;
        float t = entity.tickCount + partialTick;
        double r = entity.radius();

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
