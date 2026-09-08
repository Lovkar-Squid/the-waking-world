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

/**
 * The mage on screen: the built model, plus everything about him that gives off light.
 *
 * <p>The glow is a second pass over the same geometry through {@code RenderType.eyes}, which ignores
 * the world's lighting entirely - so the lights inside the hood, the runes down the robe, the
 * crystal and the orbiting stone burn at full strength in a black tower at midnight, which is where
 * he is going to be seen. He grows very slightly and lifts as he winds a spell up, so the silhouette
 * changes before the spell lands rather than after it.</p>
 */
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
        });
    }

    @Override
    public ResourceLocation getTextureLocation(MageEntity entity) {
        return SKIN;
    }

    @Override
    protected void scale(MageEntity mage, PoseStack pose, float partialTick) {
        float cast = mage.castLight();
        float s = 1.08F + cast * 0.05F;
        pose.scale(s, s, s);
        pose.translate(0.0, -cast * 0.07, 0.0);
    }
}
