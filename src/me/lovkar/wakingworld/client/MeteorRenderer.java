package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.cataclysm.MeteorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A falling star, drawn as what it is: a tumbling knot of blackened rock with magma showing
 * through the cracks. The shape is built once per entity from its own id, so every star that
 * comes down looks a little different but always the same to everyone watching it.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private record Cell(int x, int y, int z, BlockState state) {
    }

    private final BlockRenderDispatcher dispatcher;
    private final java.util.Map<Integer, List<Cell>> shapes = new java.util.HashMap<>();

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(MeteorEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        return true;   // it comes from the sky: it is out of the frustum until it is nearly down
    }

    /** A rough ball of the given radius: mostly blackstone, magma where the crust is broken. */
    private List<Cell> shape(MeteorEntity entity) {
        return shapes.computeIfAbsent(entity.getId() * 31 + entity.size(), key -> {
            RandomSource rnd = RandomSource.create(key);
            int r = entity.size();
            List<Cell> out = new ArrayList<>();
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z);
                        if (d > r + 0.35 || (d > r - 0.4 && rnd.nextInt(3) == 0)) continue;
                        BlockState state = rnd.nextInt(4) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                : (rnd.nextInt(3) == 0 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                        out.add(new Cell(x, y, z, state));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 3.5F));
        poseStack.mulPose(Axis.XP.rotationDegrees(t * 6.0F));
        for (Cell c : shape(entity)) {
            poseStack.pushPose();
            poseStack.translate(c.x() - 0.5, c.y() - 0.5, c.z() - 0.5);
            // full-bright: the thing is on fire
            dispatcher.renderSingleBlock(c.state(), poseStack, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
