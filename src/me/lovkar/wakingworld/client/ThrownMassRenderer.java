package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.entity.ThrownMassEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** A thrown boulder or tree: its real blocks, tumbling through the air. */
public class ThrownMassRenderer extends EntityRenderer<ThrownMassEntity> {
    private final BlockRenderDispatcher dispatcher;

    public ThrownMassRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
        this.shadowRadius = 1.5F;
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownMassEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(ThrownMassEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(ThrownMassEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(entity.getX(), entity.getY() + 1.0, entity.getZ()));
        float t = entity.tickCount + partialTick;
        boolean tree = entity.scatters();
        poseStack.pushPose();
        poseStack.translate(0.0, 1.1, 0.0);
        if (tree) {
            // a tree tumbles slowly end over end
            poseStack.mulPose(Axis.YP.rotationDegrees(t * 2.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(t * 4.5F));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(t * 5.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(t * 9.0F));
        }
        for (ThrownMassEntity.Piece p : entity.pieces()) {
            poseStack.pushPose();
            poseStack.translate(p.dx() - 0.5, p.dy() - 0.5, p.dz() - 0.5);
            dispatcher.renderSingleBlock(p.state(), poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
