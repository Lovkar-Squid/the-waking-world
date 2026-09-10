package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.mage.RiteStoneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class RiteStoneRenderer implements BlockEntityRenderer<RiteStoneEntity> {
    private final ItemRenderer items;
    private final BlockRenderDispatcher blocks;

    public RiteStoneRenderer(Context var1) {
        this.items = var1.getItemRenderer();
        this.blocks = var1.getBlockRenderDispatcher();
    }

    private static BlockState markBlock(String var0) {
        return switch (var0) {
            case "volcano" -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case "meteor" -> Blocks.GLOWSTONE.defaultBlockState();
            case "tornado" -> Blocks.QUARTZ_BLOCK.defaultBlockState();
            case "quake" -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case "bloodmoon" -> Blocks.REDSTONE_BLOCK.defaultBlockState();
            default -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
        };
    }

    public void render(RiteStoneEntity var1, float var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
        float var7 = Mth.lerp(var2, var1.spinO, var1.spin);
        long var8 = var1.getLevel() == null ? 0L : var1.getLevel().getGameTime();
        int var10 = var1.colour();
        boolean var11 = var1.burning();
        float var12 = var11 ? Mth.clamp(var1.burnProgress(), 0.0F, 1.0F) : 0.0F;
        float var13 = 0.5F + 0.5F * Mth.sin(((float)var8 + var2) * 0.09F);
        int var14 = 0;

        for (ItemStack var16 : var1.offerings()) {
            if (!var16.isEmpty()) {
                var14++;
            }
        }

        if (var14 > 0) {
            int var23 = 0;

            for (ItemStack var17 : var1.offerings()) {
                if (!var17.isEmpty()) {
                    float var18 = (float)Math.toRadians((double)(var7 + (float)var23 * 360.0F / (float)var14));
                    float var19 = 0.46F * (1.0F - var12 * var12);
                    float var20 = 0.07F * Mth.sin((var7 + (float)var23 * 40.0F) * 0.05F);
                    float var21 = 1.02F + var20 + var12 * var12 * 2.4F;
                    var3.pushPose();
                    var3.translate(0.5 + (double)(Mth.cos(var18) * var19), (double)var21, 0.5 + (double)(Mth.sin(var18) * var19));
                    var3.mulPose(Axis.YP.rotationDegrees(var7 * 2.0F + (float)var23 * 60.0F));
                    float var22 = 0.38F * (1.0F - 0.5F * var12 * var12);
                    var3.scale(var22, var22, var22);
                    this.items
                        .renderStatic(
                            var17, ItemDisplayContext.GROUND, var11 ? 15728880 : var5, var6, var3, var4, var1.getLevel(), (int)var1.getBlockPos().asLong()
                        );
                    var3.popPose();
                    var23++;
                }
            }
        }

        if (!var1.rite().isEmpty()) {
            float var24 = 0.86F + 0.05F * Mth.sin(((float)var8 + var2) * 0.055F) + var12 * 1.4F;
            float var27 = (0.24F + 0.04F * var13) * (1.0F + var12 * 0.7F);
            var3.pushPose();
            var3.translate(0.5, (double)var24, 0.5);
            var3.mulPose(Axis.YP.rotationDegrees(var7 * (var11 ? 2.6F : 0.8F)));
            var3.mulPose(Axis.XP.rotationDegrees(var11 ? var7 * 0.6F : 18.0F));
            var3.scale(var27, var27, var27);
            var3.translate(-0.5, -0.5, -0.5);
            this.blocks.renderSingleBlock(markBlock(var1.rite()), var3, var4, 15728880, var6);
            var3.popPose();
        }

        if (var11) {
            int var25 = 0xFF000000 | var10;
            float var28 = 0.1F + 0.28F * var12;
            float var29 = 0.25F + 0.5F * var12;
            BeaconRenderer.renderBeaconBeam(var3, var4, BeaconRenderer.BEAM_LOCATION, var2, 1.0F, var8, 0, 180, var25, var28, var29);
        } else if (var14 > 0) {
            BeaconRenderer.renderBeaconBeam(
                var3, var4, BeaconRenderer.BEAM_LOCATION, var2, 1.0F, var8, 0, 40, 0xFF000000 | var10, 0.045F + 0.015F * var13, 0.09F
            );
        }
    }

    public boolean shouldRenderOffScreen(RiteStoneEntity var1) {
        return true;
    }

    public int getViewDistance() {
        return 192;
    }
}
