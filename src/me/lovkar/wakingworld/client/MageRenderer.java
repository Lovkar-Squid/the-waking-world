package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.mage.MageEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor.ARGB32;

public class MageRenderer extends MobRenderer<MageEntity, MageModel> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/mage/dark_mage.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/mage/dark_mage_glow.png");
    private static final RenderType GLOW_TYPE = RenderType.eyes(GLOW);

    public MageRenderer(EntityRendererProvider.Context context) {
        super(context, new MageModel(context.bakeLayer(MageModel.LAYER)), 0.55F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW_TYPE;
            }

            /** The glow takes the colour of his stage, and flares white while he comes apart between stages. */
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, MageEntity mage,
                               float limbSwing, float limbSwingAmount, float partialTick, float age, float netHeadYaw, float headPitch) {
                int colour = MageEntity.stageColour(Math.max(1, Math.min(4, mage.stage())));
                float changing = mage.changing();
                float r = ((colour >> 16) & 0xFF) / 255.0F, g = ((colour >> 8) & 0xFF) / 255.0F, b = (colour & 0xFF) / 255.0F;
                // the texture itself is the stage-1 violet: divide it out so the tint lands on the right hue
                r /= 0.54F;
                g /= 0.35F;
                b /= 0.78F;
                float flare = changing > 0 ? Mth.sin(changing * (float) Math.PI) : 0.0F;
                r = Math.min(2.2F, r + flare * 1.4F);
                g = Math.min(2.2F, g + flare * 1.4F);
                b = Math.min(2.2F, b + flare * 1.4F);
                VertexConsumer buffer = buffers.getBuffer(renderType());
                getParentModel().renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                        ARGB32.colorFromFloat(1.0F, Math.min(1.0F, r), Math.min(1.0F, g), Math.min(1.0F, b)));
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(MageEntity entity) {
        return SKIN;
    }

    @Override
    protected void scale(MageEntity mage, PoseStack pose, float partialTick) {
        float cast = mage.castLight();
        // the four forms: each stage a little larger, the later ones lifted off the floor
        int stage = Math.max(1, Math.min(4, mage.stage()));
        float changing = mage.changing();          // 0..1 while he comes apart between stages
        float base = 1.08F + (stage - 1) * 0.045F;
        float lift = stage >= 3 ? 0.18F + (stage - 3) * 0.22F : 0.0F;
        float swell = changing > 0 ? Mth.sin(changing * (float) Math.PI) * 0.32F : 0.0F;
        float shrinking = mage.shrinking();        // 0..1 as he is drawn into the jar
        float s = (base + cast * 0.05F + swell) * (1.0F - shrinking * 0.97F);
        pose.scale(s, s * (1.0F + swell * 0.35F), s);
        pose.translate(0.0, -cast * 0.07 - lift - changing * 0.9F + shrinking * 0.55F, 0.0);
        if (shrinking > 0) pose.mulPose(Axis.YP.rotationDegrees(shrinking * shrinking * 1400.0F));   // spun down into the glass
        if (changing > 0) {
            float spin = changing < 0.75F ? changing * 900.0F : 675.0F + (changing - 0.75F) * 120.0F;
            pose.mulPose(Axis.YP.rotationDegrees(spin));
        }
    }

    protected int getBlockLightLevel(MageEntity var1, BlockPos var2) {
        return var1.roused() ? 12 : 7;
    }
}
