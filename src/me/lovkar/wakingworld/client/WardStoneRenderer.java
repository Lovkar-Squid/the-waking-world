package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.mage.WardStoneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class WardStoneRenderer extends EntityRenderer<WardStoneEntity> {
    private final BlockRenderDispatcher blocks;

    public WardStoneRenderer(Context var1) {
        super(var1);
        this.blocks = var1.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    public void render(WardStoneEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
        boolean var7 = var1.keystone();
        float var8 = (float)var1.tickCount + var3;
        float var9 = var7 ? Mth.clamp(var1.left(), 0.0F, 1.0F) : 1.0F;
        float var10 = 0.5F + 0.5F * Mth.sin(var8 * 0.16F);
        var4.pushPose();
        var4.translate(0.0, var7 ? 0.05 : 0.0, 0.0);
        var4.mulPose(Axis.YP.rotationDegrees(var8 * (var7 ? 3.2F : 1.1F)));
        var4.mulPose(Axis.XP.rotationDegrees(var7 ? var8 * 1.9F : 14.0F));
        if (var7 && var9 < 1.0F) {
            float var11 = (1.0F - var9) * 0.09F;
            var4.translate(Mth.sin(var8 * 1.7F) * var11, Mth.cos(var8 * 2.1F) * var11, 0.0F);
        }

        float var12 = var7 ? 0.78F * (0.72F + 0.28F * var9) + 0.03F * var10 : 0.5F;
        var4.scale(var12, var12, var12);
        var4.translate(-0.5, -0.5, -0.5);
        this.blocks.renderSingleBlock(var1.state(), var4, var5, var7 ? 15728880 : var6, OverlayTexture.NO_OVERLAY);
        var4.popPose();
        super.render(var1, var2, var3, var4, var5, var6);
    }

    public ResourceLocation getTextureLocation(WardStoneEntity var1) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
